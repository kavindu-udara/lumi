import { NextRequest } from "next/server";
import { AuthError, getAuthenticatedUser } from "@/lib/auth/supabase-user-auth";
import { createSupabaseServerClient } from "@/lib/supabase/server";

export async function GET(request: NextRequest) {
  try {
    const user = await getAuthenticatedUser(request);
    const supabase = createSupabaseServerClient(request);
    const { data: subscription, error } = await supabase
      .from("subscriptions")
      .select("id, user_id, plan_id, stripe_subscription_id, stripe_customer_id, status, current_period_start, current_period_end, cancel_at_period_end, cancelled_at, ended_at, pending_plan_id, pending_change_effective_at, start_date, end_date, plans!subscriptions_plan_id_fkey(*)")
      .eq("user_id", user.id)
      .order("start_date", { ascending: false })
      .limit(1)
      .maybeSingle();

    if (error) throw error;
    const { data: storage, error: storageError } = await supabase
      .from("user_storage")
      .select("user_id, plan_id, used_bytes, plans(*)")
      .eq("user_id", user.id)
      .maybeSingle();
    if (storageError) throw storageError;
    if (subscription) {
      const plan = Array.isArray(subscription.plans) ? subscription.plans[0] : subscription.plans;
      const storageLimit = plan?.storage_limit_bytes ?? null;
      return Response.json({
        subscription,
        storage,
        usage: {
          usedBytes: storage?.used_bytes ?? 0,
          limitBytes: storageLimit,
          remainingBytes: storageLimit === null ? null : Math.max(storageLimit - (storage?.used_bytes ?? 0), 0),
        },
      }, { status: 200 });
    }

    const { data: freePlan, error: planError } = await supabase
      .from("plans")
      .select("id, name, storage_limit_bytes, price")
      .eq("name", "Free")
      .single();
    if (planError) throw planError;

    const storageLimit = freePlan.storage_limit_bytes;
    return Response.json({
      subscription: {
        user_id: user.id,
        plan_id: freePlan.id,
        plans: freePlan,
        start_date: null,
        end_date: null,
      },
      storage,
      usage: {
        usedBytes: storage?.used_bytes ?? 0,
        limitBytes: storageLimit,
        remainingBytes: Math.max(storageLimit - (storage?.used_bytes ?? 0), 0),
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
    await getAuthenticatedUser(request);
    return Response.json({ error: "Direct subscription activation is removed; use recurring checkout" }, { status: 410 });
  } catch (error) {
    if (error instanceof AuthError) {
      return Response.json({ error: error.message }, { status: error.status });
    }
    const message = error instanceof Error ? error.message : "Internal server error";
    return Response.json({ error: message }, { status: 500 });
  }
}
