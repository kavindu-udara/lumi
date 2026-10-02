import type Stripe from "stripe";
import { NextRequest } from "next/server";
import { requireStripe, stripePeriodDate, stripePriceId } from "@/lib/billing";
import { createSupabaseAdminClient } from "@/lib/supabase/server";

export const runtime = "nodejs";

function stripeStatus(status: Stripe.Subscription.Status) {
  return status;
}

function getSubscriptionObject(event: Stripe.Event) {
  if (event.type.startsWith("customer.subscription.")) return event.data.object as Stripe.Subscription;
  return null;
}

function errorMessage(error: unknown) {
  if (error instanceof Error) return error.message;
  if (error && typeof error === "object" && "message" in error) return String(error.message);
  return "Webhook processing failed";
}

async function resolvePlanId(
  adminSupabase: ReturnType<typeof createSupabaseAdminClient>,
  subscription: Stripe.Subscription,
) {
  const priceId = stripePriceId(subscription);
  if (!priceId) throw new Error("Stripe subscription has no recurring price");
  const { data: plan, error } = await adminSupabase
    .from("plans")
    .select("id, name")
    .eq("stripe_price_id", priceId)
    .single();
  if (error || !plan) throw new Error(`No local plan configured for Stripe price ${priceId}`);
  return plan;
}

async function syncSubscription(adminSupabase: ReturnType<typeof createSupabaseAdminClient>, subscription: Stripe.Subscription) {
  const plan = await resolvePlanId(adminSupabase, subscription);
  const userId = subscription.metadata?.supabaseUserId;
  if (!userId) throw new Error("Stripe subscription is missing supabaseUserId metadata");

  const periodStart = stripePeriodDate(subscription.items.data[0]?.current_period_start);
  const periodEnd = stripePeriodDate(subscription.items.data[0]?.current_period_end);
  const { data: localSubscription, error: subscriptionError } = await adminSupabase
    .from("subscriptions")
    .upsert({
      user_id: userId,
      plan_id: plan.id,
      stripe_subscription_id: subscription.id,
      stripe_customer_id: typeof subscription.customer === "string" ? subscription.customer : subscription.customer.id,
      status: stripeStatus(subscription.status),
      current_period_start: periodStart,
      current_period_end: periodEnd,
      cancel_at_period_end: subscription.cancel_at_period_end,
      cancelled_at: subscription.canceled_at ? new Date(subscription.canceled_at * 1000).toISOString() : null,
      ended_at: subscription.ended_at ? new Date(subscription.ended_at * 1000).toISOString() : null,
      start_date: periodStart || new Date().toISOString(),
      end_date: periodEnd,
    }, { onConflict: "stripe_subscription_id" })
    .select("id, user_id, plan_id")
    .single();
  if (subscriptionError || !localSubscription) throw subscriptionError || new Error("Failed to save subscription");

  const shouldApplyPlan = subscription.status === "active" || subscription.status === "trialing";
  if (shouldApplyPlan) {
    const { error: storageError } = await adminSupabase.rpc("apply_storage_plan", {
      target_user_id: userId,
      target_plan_id: plan.id,
    });
    if (storageError) throw storageError;
  }

  return { userId, planId: plan.id, localSubscriptionId: localSubscription.id };
}

async function applyFreePlan(adminSupabase: ReturnType<typeof createSupabaseAdminClient>, userId: string) {
  const { data: freePlan, error: planError } = await adminSupabase
    .from("plans")
    .select("id")
    .eq("name", "Free")
    .eq("is_active", true)
    .single();
  if (planError || !freePlan) throw planError || new Error("Free plan is not configured");

  const { error } = await adminSupabase.rpc("apply_storage_plan", {
    target_user_id: userId,
    target_plan_id: freePlan.id,
  });
  if (error) throw error;
}

async function handleInvoicePaid(adminSupabase: ReturnType<typeof createSupabaseAdminClient>, invoice: Stripe.Invoice) {
  const subscription = invoice.parent?.type === "subscription_details" && invoice.parent.subscription_details
    ? invoice.parent.subscription_details.subscription
    : null;
  const subscriptionId = typeof subscription === "string" ? subscription : subscription?.id;
  if (!subscriptionId) return;

  const { data: localSubscription, error: subscriptionError } = await adminSupabase
    .from("subscriptions")
    .select("id, user_id, plan_id, stripe_subscription_id")
    .eq("stripe_subscription_id", subscriptionId)
    .single();
  if (subscriptionError || !localSubscription) throw subscriptionError || new Error("Local subscription not found");

  const line = invoice.lines.data[0];
  const periodStart = line?.period?.start ? new Date(line.period.start * 1000).toISOString() : new Date().toISOString();
  const periodEnd = line?.period?.end ? new Date(line.period.end * 1000).toISOString() : periodStart;
  const { error } = await adminSupabase.from("subscription_renewals").upsert({
    user_id: localSubscription.user_id,
    subscription_id: localSubscription.id,
    plan_id: localSubscription.plan_id,
    stripe_subscription_id: subscriptionId,
    stripe_invoice_id: invoice.id,
    stripe_payment_intent_id: invoice.payments?.data[0]?.payment.payment_intent
      ? typeof invoice.payments.data[0].payment.payment_intent === "string"
        ? invoice.payments.data[0].payment.payment_intent
        : invoice.payments.data[0].payment.payment_intent.id
      : null,
    period_start: periodStart,
    period_end: periodEnd,
    amount_paid: invoice.amount_paid / 100,
    currency: invoice.currency,
    status: "paid",
    paid_at: new Date().toISOString(),
  }, { onConflict: "stripe_invoice_id" });
  if (error) throw error;
}

export async function POST(request: NextRequest) {
  const stripeClient = requireStripe();
  const signature = request.headers.get("stripe-signature");
  const webhookSecret = process.env.STRIPE_WEBHOOK_SECRET;
  if (!signature || !webhookSecret) return Response.json({ error: "Stripe webhook is not configured" }, { status: 500 });

  const payload = await request.text();
  let event: Stripe.Event;
  try {
    event = stripeClient.webhooks.constructEvent(payload, signature, webhookSecret);
  } catch {
    return Response.json({ error: "Invalid Stripe signature" }, { status: 400 });
  }

  const adminSupabase = createSupabaseAdminClient();
  let { data: claimedEvent, error: claimError } = await adminSupabase
    .from("billing_events")
    .insert({
      stripe_event_id: event.id,
      event_type: event.type,
      stripe_object_id: typeof event.data.object === "object" && "id" in event.data.object ? event.data.object.id : null,
      status: "processing",
    })
    .select("id")
    .maybeSingle();

  if (claimError) {
    if (claimError.code === "23505") {
      const { data: existingEvent, error: existingEventError } = await adminSupabase
        .from("billing_events")
        .select("id, status")
        .eq("stripe_event_id", event.id)
        .single();
      if (existingEventError || !existingEvent) {
        return Response.json({ error: "Failed to inspect webhook event" }, { status: 500 });
      }
      if (existingEvent.status === "processed") {
        return Response.json({ received: true, duplicate: true }, { status: 200 });
      }
      const { data: retriedEvent, error: retryError } = await adminSupabase
        .from("billing_events")
        .update({ status: "processing", error_message: null, processed_at: null })
        .eq("id", existingEvent.id)
        .select("id")
        .single();
      if (retryError || !retriedEvent) {
        return Response.json({ error: "Failed to retry webhook event" }, { status: 500 });
      }
      claimedEvent = retriedEvent;
    } else {
      return Response.json({ error: "Failed to claim webhook event" }, { status: 500 });
    }
  }
  if (!claimedEvent) return Response.json({ error: "Failed to claim webhook event" }, { status: 500 });

  try {
    const subscription = getSubscriptionObject(event);
    if (subscription) {
      const result = await syncSubscription(adminSupabase, subscription);
      if (event.type === "customer.subscription.deleted") await applyFreePlan(adminSupabase, result.userId);
    }
    if (event.type === "checkout.session.completed") {
      const session = event.data.object as Stripe.Checkout.Session;
      const subscriptionId = typeof session.subscription === "string" ? session.subscription : session.subscription?.id;
      if (subscriptionId) {
        const completedSubscription = await stripeClient.subscriptions.retrieve(subscriptionId);
        await syncSubscription(adminSupabase, completedSubscription);
      }
    }
    if (event.type === "invoice.paid") await handleInvoicePaid(adminSupabase, event.data.object as Stripe.Invoice);
    if (event.type === "invoice.payment_failed") {
      const invoice = event.data.object as Stripe.Invoice;
      const subscription = invoice.parent?.type === "subscription_details" && invoice.parent.subscription_details
        ? invoice.parent.subscription_details.subscription
        : null;
      const subscriptionId = typeof subscription === "string" ? subscription : subscription?.id;
      if (subscriptionId) {
        const { error } = await adminSupabase
          .from("subscriptions")
          .update({ status: "past_due" })
          .eq("stripe_subscription_id", subscriptionId);
        if (error) throw error;
      }
    }

    const { error: processedError } = await adminSupabase
      .from("billing_events")
      .update({ status: "processed", processed_at: new Date().toISOString(), error_message: null })
      .eq("id", claimedEvent.id);
    if (processedError) throw processedError;

    return Response.json({ received: true }, { status: 200 });
  } catch (error) {
    const message = errorMessage(error);
    await adminSupabase.from("billing_events").update({ status: "failed", error_message: message }).eq("id", claimedEvent.id);
    console.error("Stripe webhook processing failed", { eventId: event.id, eventType: event.type, message });
    return Response.json({ error: "Webhook processing failed" }, { status: 500 });
  }
}
