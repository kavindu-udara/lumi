import { NextRequest, NextResponse } from "next/server";
import { getAdminFromRequest } from "@/lib/admin-guard";
import { createSupabaseAdminClient } from "@/lib/supabase/server";

export async function GET(
  request: NextRequest,
  { params }: { params: Promise<{ id: string }> },
) {
  const admin = await getAdminFromRequest(request);
  if (!admin) return NextResponse.json({ error: "Unauthorized" }, { status: 401 });
  const { id } = await params;
  const supabase = createSupabaseAdminClient();

  const [{ data: broadcast, error: broadcastError }, { count: availableCount }, { count: readCount }] =
    await Promise.all([
      supabase
        .from("broadcast_notifications")
        .select("id, sender_admin_id, title, body, data, status, recipient_count, delivered_count, read_count, failure_count, error_message, created_at, updated_at")
        .eq("id", id)
        .maybeSingle(),
      supabase.from("user_notifications").select("id", { count: "exact", head: true }).eq("broadcast_id", id).eq("status", "available"),
      supabase.from("user_notifications").select("id", { count: "exact", head: true }).eq("broadcast_id", id).eq("status", "read"),
    ]);
  if (broadcastError) {
    console.error("Error fetching notification broadcast:", broadcastError);
    return NextResponse.json({ error: "Failed to fetch broadcast" }, { status: 500 });
  }
  if (!broadcast) return NextResponse.json({ error: "Broadcast not found" }, { status: 404 });
  return NextResponse.json({
    broadcast,
    delivery: { availableCount: availableCount ?? 0, readCount: readCount ?? 0 },
  });
}
