import { NextRequest } from "next/server";
import { createSupabaseServerClient } from "@/lib/supabase/server";

export async function GET(request : NextRequest){
    try{
        const supabase = createSupabaseServerClient(request);
        const { data, error } = await supabase
            .from("plans")
            .select("id, name, storage_limit_bytes, price, created_at, updated_at")
            .order("created_at", { ascending: true });

        if (error) {
            throw error;
        }

        const plans = (data ?? []).map((plan) => ({
            _id: plan.id,
            name: plan.name,
            storageLimit: plan.storage_limit_bytes,
            price: plan.price,
            createdAt: plan.created_at,
            updatedAt: plan.updated_at,
        }));

        return Response.json({ plans }, { status: 200 });
    }catch(error){
        const message = error instanceof Error ? error.message : "Internal server error";
        return Response.json({ error: message }, { status: 500 });
    }
}
