import { NextRequest } from "next/server";
import { AuthError, getAuthenticatedUser } from "@/lib/auth/supabase-user-auth";
import { createSupabaseServerClient } from "@/lib/supabase/server";

export async function GET(request: NextRequest) {
    try {
        await getAuthenticatedUser(request);
        const supabase = createSupabaseServerClient(request);
        const { data, error } = await supabase
            .from("plans")
            .select("id, name, storage_limit_bytes, price, stripe_price_id, billing_interval, is_active, created_at, updated_at")
            .eq("is_active", true)
            .order("created_at", { ascending: true });

        if (error) throw error;

        const plans = (data ?? []).map((plan) => ({
            _id: plan.id,
            name: plan.name,
            storageLimit: plan.storage_limit_bytes,
            price: plan.price,
            stripePriceId: plan.stripe_price_id,
            billingInterval: plan.billing_interval,
            createdAt: plan.created_at,
            updatedAt: plan.updated_at,
        }));

        return Response.json({ plans }, { status: 200 });
    } catch (error) {
        if (error instanceof AuthError) {
            return Response.json({ error: error.message }, { status: error.status });
        }
        const message = error instanceof Error ? error.message : "Internal server error";
        return Response.json({ error: message }, { status: 500 });
    }
}
