import { NextRequest } from "next/server";
import { AuthError, getAuthenticatedUser } from "@/lib/auth/supabase-user-auth";
import { requireStripe } from "@/lib/billing";
import { createSupabaseAdminClient, createSupabaseServerClient } from "@/lib/supabase/server";

export const runtime = "nodejs";

type Action = "cancel" | "resume" | "change";

export async function POST(request: NextRequest) {
  try {
    const user = await getAuthenticatedUser(request);
    const supabase = createSupabaseServerClient(request);
    const adminSupabase = createSupabaseAdminClient();
    const stripeClient = requireStripe();
    const payload = await request.json() as { action?: Action; planId?: string };
    const action = payload.action;

    if (!action || !["cancel", "resume", "change"].includes(action)) {
      return Response.json({ error: "action must be cancel, resume, or change" }, { status: 400 });
    }

    const { data: current, error: currentError } = await supabase
      .from("subscriptions")
      .select("id, plan_id, stripe_subscription_id, current_period_end, cancel_at_period_end")
      .eq("user_id", user.id)
      .not("stripe_subscription_id", "is", null)
      .order("created_at", { ascending: false })
      .limit(1)
      .maybeSingle();
    if (currentError) throw currentError;
    if (!current?.stripe_subscription_id) return Response.json({ error: "Active Stripe subscription not found" }, { status: 404 });

    if (action === "cancel") {
      const subscription = await stripeClient.subscriptions.update(current.stripe_subscription_id, { cancel_at_period_end: true });
      return Response.json({
        message: "Subscription will end at the current billing period",
        currentPeriodEnd: subscription.items.data[0]?.current_period_end ?? null,
      }, { status: 200 });
    }

    if (action === "resume") {
      await stripeClient.subscriptions.update(current.stripe_subscription_id, { cancel_at_period_end: false });
      return Response.json({ message: "Subscription cancellation was removed" }, { status: 200 });
    }

    if (!payload.planId) return Response.json({ error: "planId is required for a plan change" }, { status: 400 });
    if (payload.planId === current.plan_id) return Response.json({ error: "Selected plan is already active" }, { status: 400 });

    const { data: targetPlan, error: targetError } = await supabase
      .from("plans")
      .select("id, name, stripe_price_id, storage_limit_bytes")
      .eq("id", payload.planId)
      .eq("is_active", true)
      .single();
    if (targetError || !targetPlan?.stripe_price_id) return Response.json({ error: "Paid target plan not found" }, { status: 404 });

    const subscription = await stripeClient.subscriptions.retrieve(current.stripe_subscription_id);
    const item = subscription.items.data[0];
    if (!item || !current.current_period_end) return Response.json({ error: "Subscription period is unavailable" }, { status: 409 });

    const schedule = subscription.schedule
      ? await stripeClient.subscriptionSchedules.retrieve(typeof subscription.schedule === "string" ? subscription.schedule : subscription.schedule.id)
      : await stripeClient.subscriptionSchedules.create({ from_subscription: current.stripe_subscription_id });
    const currentPhase = schedule.phases[0];
    const scheduleUpdated = await stripeClient.subscriptionSchedules.update(schedule.id, {
      end_behavior: "release",
      phases: [
        {
          items: [{ price: item.price.id, quantity: item.quantity ?? 1 }],
          start_date: currentPhase?.start_date ?? Math.floor(Date.now() / 1000),
          end_date: Math.floor(new Date(current.current_period_end).getTime() / 1000),
        },
        {
          items: [{ price: targetPlan.stripe_price_id, quantity: item.quantity ?? 1 }],
        },
      ],
    });

    const { error: pendingError } = await adminSupabase
      .from("subscriptions")
      .update({ pending_plan_id: targetPlan.id, pending_change_effective_at: current.current_period_end })
      .eq("id", current.id);
    if (pendingError) throw pendingError;

    return Response.json({
      message: "Plan change scheduled for the end of the current billing period",
      scheduleId: scheduleUpdated.id,
      effectiveAt: current.current_period_end,
      planId: targetPlan.id,
    }, { status: 200 });
  } catch (error) {
    if (error instanceof AuthError) return Response.json({ error: error.message }, { status: error.status });
    const message = error instanceof Error ? error.message : "Failed to manage subscription";
    return Response.json({ error: message }, { status: 500 });
  }
}
