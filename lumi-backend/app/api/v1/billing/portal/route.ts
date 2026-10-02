import { NextRequest } from "next/server";
import { AuthError, getAuthenticatedUser } from "@/lib/auth/supabase-user-auth";
import { requireStripe } from "@/lib/billing";
import { createSupabaseServerClient } from "@/lib/supabase/server";

export const runtime = "nodejs";

export async function POST(request: NextRequest) {
  try {
    const user = await getAuthenticatedUser(request);
    const supabase = createSupabaseServerClient(request);
    const stripeClient = requireStripe();
    const { data: subscription, error } = await supabase
      .from("subscriptions")
      .select("stripe_customer_id")
      .eq("user_id", user.id)
      .not("stripe_customer_id", "is", null)
      .order("created_at", { ascending: false })
      .limit(1)
      .maybeSingle();
    if (error) throw error;
    if (!subscription?.stripe_customer_id) return Response.json({ error: "No Stripe customer found" }, { status: 404 });

    const session = await stripeClient.billingPortal.sessions.create({
      customer: subscription.stripe_customer_id,
      return_url: request.nextUrl.origin,
    });
    return Response.json({ url: session.url }, { status: 200 });
  } catch (error) {
    if (error instanceof AuthError) return Response.json({ error: error.message }, { status: error.status });
    const message = error instanceof Error ? error.message : "Failed to create billing portal session";
    return Response.json({ error: message }, { status: 500 });
  }
}
