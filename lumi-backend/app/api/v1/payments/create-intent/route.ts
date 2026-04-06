import stripe from "@/lib/stripe";
import { NextRequest } from "next/server";

export const runtime = "nodejs";

async function getOrCreateStripeCustomerId(
    stripeClient: NonNullable<typeof stripe>,
    firebaseUserId: string,
) {
    const customers = await stripeClient.customers.list({ limit: 100 });
    const matchedCustomer = customers.data.find(
        (customer) => customer.metadata?.firebaseUserId === firebaseUserId,
    );

    if (matchedCustomer) {
        return matchedCustomer.id;
    }

    const createdCustomer = await stripeClient.customers.create({
        metadata: { firebaseUserId },
    });

    return createdCustomer.id;
}

export async function POST(request : NextRequest){
    if (!stripe) {
        return Response.json(
            { error: "Stripe is not configured" },
            { status: 500 },
        );
    }

    let payload: {
        amount?: number | string;
        currency?: string;
        planId?: string;
        firebaseUserId?: string;
    };

    try {
        payload = await request.json();
    } catch (error) {
        console.error("Failed to parse JSON body:", error instanceof Error ? error.message : String(error));
        return Response.json(
            { error: "Invalid JSON body" },
            { status: 400 },
        );
    }

    // get amount, currency, planId, and firebaseUserId from request body
    const { amount, currency, planId, firebaseUserId } = payload;

    if(!amount || !currency || !planId || !firebaseUserId){
        return Response.json({ error: "Missing required fields" }, { status: 400 });
    }

    const normalizedAmount = Number(amount);

    if (!Number.isFinite(normalizedAmount) || normalizedAmount <= 0) {
        return Response.json({ error: "Invalid amount" }, { status: 400 });
    }

    const normalizedCurrency = String(currency).trim().toLowerCase();

    try {
        const stripeCustomerId = await getOrCreateStripeCustomerId(
            stripe,
            String(firebaseUserId),
        );

        const paymentIntent = await stripe.paymentIntents.create({
            amount: Math.round(normalizedAmount),
            currency: normalizedCurrency,
            customer: stripeCustomerId,
            metadata: {
                planId: String(planId),
                firebaseUserId: String(firebaseUserId),
            },
            automatic_payment_methods: {
                enabled: true,
            },
        });

        return Response.json(
            {
                clientSecret: paymentIntent.client_secret,
                paymentIntentId: paymentIntent.id,
            },
            { status: 200 },
        );
    } catch (error) {
        const message = error instanceof Error ? error.message : "Failed to create payment intent";
        return Response.json({ error: message }, { status: 500 });
    }

}