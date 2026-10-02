import { NextRequest } from "next/server";
import stripe from "@/lib/stripe";
import { AuthError, getAuthenticatedUser } from "@/lib/auth/supabase-user-auth";
import { createSupabaseAdminClient, createSupabaseServerClient } from "@/lib/supabase/server";

export async function GET(request: NextRequest) {
  try {
    const user = await getAuthenticatedUser(request);
    const supabase = createSupabaseServerClient(request);
    const { data: subscription, error } = await supabase
      .from("subscriptions")
      .select("id, user_id, plan_id, stripe_merchant_id, payment_intent_id, start_date, end_date, plans(*)")
      .eq("user_id", user.id)
      .order("start_date", { ascending: false })
      .limit(1)
      .maybeSingle();

    if (error) throw error;
    if (subscription) return Response.json({ subscription }, { status: 200 });

    const { data: freePlan, error: planError } = await supabase
      .from("plans")
      .select("id, name, storage_limit_bytes, price")
      .eq("name", "Free")
      .single();
    if (planError) throw planError;

    return Response.json({
      subscription: {
        user_id: user.id,
        plan_id: freePlan.id,
        plans: freePlan,
        start_date: null,
        end_date: null,
      },
    }, { status: 200 });
  } catch (error) {
    if (error instanceof AuthError) {
      return Response.json({ error: error.message }, { status: error.status });
    }
    const message = error instanceof Error ? error.message : "Internal server error";
    return Response.json({ error: message }, { status: 500 });
  }
}

export async function PUT(request: NextRequest) {
  try {
    const user = await getAuthenticatedUser(request);
    const supabase = createSupabaseServerClient(request);
    const adminSupabase = createSupabaseAdminClient();

    if (!stripe) {
      return Response.json({ error: "Stripe is not configured" }, { status: 500 });
    }

    const { planId, stripeMerchantId, paymentIntentId } = await request.json();
    const effectivePaymentIntentId = paymentIntentId || stripeMerchantId;
    if (!planId) return Response.json({ error: "Missing planId in request body" }, { status: 400 });
    if (!effectivePaymentIntentId) {
      return Response.json({ error: "Missing paymentIntentId in request body" }, { status: 400 });
    }

    const { data: newPlan, error: planError } = await supabase
      .from("plans")
      .select("id, name, storage_limit_bytes, price")
      .eq("id", planId)
      .single();
    if (planError || !newPlan) return Response.json({ error: "Plan not found" }, { status: 404 });

    const paymentIntent = await stripe.paymentIntents.retrieve(effectivePaymentIntentId);
    if (paymentIntent.status !== "succeeded") {
      return Response.json({ error: "Payment not successful for the new plan" }, { status: 402 });
    }
    if (paymentIntent.metadata?.supabaseUserId !== user.id) {
      return Response.json({ error: "PaymentIntent does not belong to this user" }, { status: 403 });
    }
    if (String(paymentIntent.metadata?.planId || "") !== newPlan.id) {
      return Response.json({ error: "PaymentIntent plan does not match selected plan" }, { status: 400 });
    }

    const { error: subscriptionError } = await adminSupabase
      .from("subscriptions")
      .upsert({
        user_id: user.id,
        plan_id: newPlan.id,
        stripe_merchant_id: stripeMerchantId || null,
        payment_intent_id: effectivePaymentIntentId,
        start_date: new Date().toISOString(),
        end_date: new Date(Date.now() + 30 * 24 * 60 * 60 * 1000).toISOString(),
      }, { onConflict: "user_id" });
    if (subscriptionError) throw subscriptionError;

    const { error: storageError } = await adminSupabase
      .from("user_storage")
      .upsert({ user_id: user.id, plan_id: newPlan.id }, { onConflict: "user_id" });
    if (storageError) throw storageError;

    return Response.json({ message: "Subscription updated successfully" }, { status: 200 });
  } catch (error) {
    if (error instanceof AuthError) {
      return Response.json({ error: error.message }, { status: error.status });
    }
    const message = error instanceof Error ? error.message : "Internal server error";
    return Response.json({ error: message }, { status: 500 });
  }
}
