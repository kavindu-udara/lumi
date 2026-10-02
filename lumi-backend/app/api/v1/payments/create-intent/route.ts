import stripe from "@/lib/stripe";
import { NextRequest } from "next/server";
import { AuthError, getAuthenticatedUser } from "@/lib/auth/supabase-user-auth";

export const runtime = "nodejs";

async function getOrCreateStripeCustomerId(
  stripeClient: NonNullable<typeof stripe>,
  userId: string,
) {
  const customers = await stripeClient.customers.list({ limit: 100 });
  const matchedCustomer = customers.data.find(
    (customer) => customer.metadata?.supabaseUserId === userId,
  );
  if (matchedCustomer) return matchedCustomer.id;

  const createdCustomer = await stripeClient.customers.create({
    metadata: { supabaseUserId: userId },
  });
  return createdCustomer.id;
}

export async function POST(request: NextRequest) {
  if (!stripe) return Response.json({ error: "Stripe is not configured" }, { status: 500 });

  try {
    const user = await getAuthenticatedUser(request);
    const payload = await request.json() as {
      amount?: number | string;
      currency?: string;
      planId?: string;
    };
    const { amount, currency, planId } = payload;
    if (!amount || !currency || !planId) {
      return Response.json({ error: "Missing required fields" }, { status: 400 });
    }

    const normalizedAmount = Number(amount);
    if (!Number.isFinite(normalizedAmount) || normalizedAmount <= 0) {
      return Response.json({ error: "Invalid amount" }, { status: 400 });
    }

    const stripeCustomerId = await getOrCreateStripeCustomerId(stripe, user.id);
    const paymentIntent = await stripe.paymentIntents.create({
      amount: Math.round(normalizedAmount),
      currency: String(currency).trim().toLowerCase(),
      customer: stripeCustomerId,
      metadata: { planId: String(planId), supabaseUserId: user.id },
      automatic_payment_methods: { enabled: true },
    });

    return Response.json({
      clientSecret: paymentIntent.client_secret,
      paymentIntentId: paymentIntent.id,
    });
  } catch (error) {
    if (error instanceof AuthError) return Response.json({ error: error.message }, { status: error.status });
    const message = error instanceof Error ? error.message : "Failed to create payment intent";
    return Response.json({ error: message }, { status: 500 });
  }
}
