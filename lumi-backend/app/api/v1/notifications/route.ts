import { NextRequest } from "next/server";
import { AuthError, getAuthenticatedUser } from "@/lib/auth/supabase-user-auth";
import { createSupabaseServerClient } from "@/lib/supabase/server";

export async function GET(request: NextRequest) {
  try {
    const user = await getAuthenticatedUser(request);
    const limitValue = Number(request.nextUrl.searchParams.get("limit") ?? "50");
    const limit = Number.isFinite(limitValue) ? Math.min(Math.max(Math.floor(limitValue), 1), 100) : 50;
    const before = request.nextUrl.searchParams.get("before");
    const supabase = createSupabaseServerClient(request);

    let query = supabase
      .from("user_notifications")
      .select("id, status, delivered_at, read_at, created_at, broadcast:broadcast_notifications(id, title, body, data, created_at)")
      .eq("user_id", user.id)
      .order("created_at", { ascending: false })
      .limit(limit);
    if (before) query = query.lt("created_at", before);

    const [{ data: notifications, error }, { data: readState, error: readStateError }] =
      await Promise.all([
        query,
        supabase.from("notification_read_state").select("last_seen_at").eq("user_id", user.id).maybeSingle(),
      ]);
    if (error) throw error;
    if (readStateError) throw readStateError;
    return Response.json({ notifications: notifications ?? [], readState }, { status: 200 });
  } catch (error) {
    if (error instanceof AuthError) return Response.json({ error: error.message }, { status: error.status });
    console.error("Error fetching notifications:", error);
    return Response.json({ error: "Failed to fetch notifications" }, { status: 500 });
  }
}
