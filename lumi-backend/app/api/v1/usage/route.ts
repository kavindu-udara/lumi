import { NextRequest } from "next/server";
import { AuthError, getAuthenticatedUser } from "@/lib/auth/supabase-user-auth";
import { createSupabaseServerClient } from "@/lib/supabase/server";

export async function GET(request: NextRequest) {
  try {
    const user = await getAuthenticatedUser(request);
    const supabase = createSupabaseServerClient(request);
    const [{ data: subscription, error: subscriptionError }, { data: storage, error: storageError }] =
      await Promise.all([
        supabase
          .from("subscriptions")
          .select("id, user_id, plan_id, stripe_merchant_id, payment_intent_id, start_date, end_date, plans(*)")
          .eq("user_id", user.id)
          .order("start_date", { ascending: false })
          .limit(1)
          .maybeSingle(),
        supabase
          .from("user_storage")
          .select("user_id, plan_id, used_bytes, plans(*)")
          .eq("user_id", user.id)
          .maybeSingle(),
      ]);

    if (subscriptionError) throw subscriptionError;
    if (storageError) throw storageError;
    if (subscription) return Response.json({ subscription, storage }, { status: 200 });

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
      storage,
    }, { status: 200 });
  } catch (error) {
    if (error instanceof AuthError) {
      return Response.json({ error: error.message }, { status: error.status });
    }
    const message = error instanceof Error ? error.message : "Internal server error";
    return Response.json({ error: message }, { status: 500 });
  }
}
