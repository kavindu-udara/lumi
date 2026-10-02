import type Stripe from "stripe";
import stripe from "@/lib/stripe";
import { createSupabaseAdminClient } from "@/lib/supabase/server";

export function requireStripe() {
  if (!stripe) throw new Error("Stripe is not configured");
  return stripe;
}

export async function getOrCreateStripeCustomerId(userId: string, email?: string | null) {
  const stripeClient = requireStripe();
  const adminSupabase = createSupabaseAdminClient();
  const { data: existing } = await adminSupabase
    .from("subscriptions")
    .select("stripe_customer_id")
    .eq("user_id", userId)
    .not("stripe_customer_id", "is", null)
    .order("created_at", { ascending: false })
    .limit(1)
    .maybeSingle();

  if (existing?.stripe_customer_id) return existing.stripe_customer_id;

  const customer = await stripeClient.customers.create({
    email: email || undefined,
    metadata: { supabaseUserId: userId },
  });
  return customer.id;
}

export function stripePeriodDate(seconds: number | null | undefined) {
  return seconds ? new Date(seconds * 1000).toISOString() : null;
}

export function stripePriceId(subscription: Stripe.Subscription) {
  return subscription.items.data[0]?.price.id ?? null;
}

export function stripePriceInterval(subscription: Stripe.Subscription) {
  return subscription.items.data[0]?.price.recurring?.interval ?? null;
}
