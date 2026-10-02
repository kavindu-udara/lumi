import { NextRequest } from "next/server";
import { AuthError, getAuthenticatedUser } from "@/lib/auth/supabase-user-auth";
import { getOrCreateStripeCustomerId, requireStripe } from "@/lib/billing";
import { createSupabaseServerClient } from "@/lib/supabase/server";

export const runtime = "nodejs";

function errorMessage(error: unknown) {
  if (error instanceof Error) return error.message;
  if (error && typeof error === "object" && "message" in error) return String(error.message);
  return "Failed to create checkout session";
}

export async function POST(request: NextRequest) {
  try {
    const user = await getAuthenticatedUser(request);
    const supabase = createSupabaseServerClient(request);
    const stripeClient = requireStripe();
    const payload = await request.json() as { planId?: string; successUrl?: string; cancelUrl?: string };

    if (!payload.planId) return Response.json({ error: "Missing planId" }, { status: 400 });

    const { data: plan, error: planError } = await supabase
      .from("plans")
      .select("id, name, stripe_price_id, is_active")
      .eq("id", payload.planId)
      .eq("is_active", true)
      .single();
    if (planError || !plan) return Response.json({ error: "Plan not found" }, { status: 404 });
    if (plan.name === "Free") return Response.json({ error: "Free plan does not require checkout" }, { status: 400 });
    if (!plan.stripe_price_id) return Response.json({ error: "Stripe price is not configured for this plan" }, { status: 503 });

    const customerId = await getOrCreateStripeCustomerId(user.id, user.email);
    const origin = request.nextUrl.origin;
    const session = await stripeClient.checkout.sessions.create({
      mode: "subscription",
      customer: customerId,
      line_items: [{ price: plan.stripe_price_id, quantity: 1 }],
      success_url: payload.successUrl || `${origin}/?billing=success`,
      cancel_url: payload.cancelUrl || `${origin}/?billing=cancelled`,
      metadata: { supabaseUserId: user.id, planId: plan.id },
      subscription_data: {
        metadata: { supabaseUserId: user.id, planId: plan.id },
      },
    });

    return Response.json({ sessionId: session.id, url: session.url }, { status: 200 });
  } catch (error) {
    if (error instanceof AuthError) return Response.json({ error: error.message }, { status: error.status });
    const message = errorMessage(error);
    console.error("Stripe checkout session creation failed", { message });
    return Response.json({ error: message }, { status: 500 });
  }
}
