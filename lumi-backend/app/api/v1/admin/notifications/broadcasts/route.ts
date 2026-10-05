import { NextRequest, NextResponse } from "next/server";
import { getAdminFromRequest } from "@/lib/admin-guard";
import { createSupabaseAdminClient } from "@/lib/supabase/server";
import {
  createBroadcast,
  NotificationValidationError,
  validateBroadcastInput,
} from "@/lib/notifications";

export async function GET(request: NextRequest) {
  const admin = await getAdminFromRequest(request);
  if (!admin) return NextResponse.json({ error: "Unauthorized" }, { status: 401 });

  const limitValue = Number(request.nextUrl.searchParams.get("limit") ?? "25");
  const pageValue = Number(request.nextUrl.searchParams.get("page") ?? "1");
  const limit = Number.isFinite(limitValue) ? Math.min(Math.max(Math.floor(limitValue), 1), 100) : 25;
  const page = Number.isFinite(pageValue) ? Math.max(Math.floor(pageValue), 1) : 1;
  const status = request.nextUrl.searchParams.get("status");
  const from = (page - 1) * limit;
  const supabase = createSupabaseAdminClient();

  let query = supabase
    .from("broadcast_notifications")
    .select("id, sender_admin_id, title, body, data, status, recipient_count, delivered_count, read_count, failure_count, error_message, created_at, updated_at", { count: "exact" })
    .order("created_at", { ascending: false })
    .range(from, from + limit - 1);
  if (status) query = query.eq("status", status);

  const { data, error, count } = await query;
  if (error) {
    console.error("Error listing notification broadcasts:", error);
    return NextResponse.json({ error: "Failed to list broadcasts" }, { status: 500 });
  }
  return NextResponse.json({ page, limit, total: count ?? 0, items: data ?? [] });
}

export async function POST(request: NextRequest) {
  const admin = await getAdminFromRequest(request);
  if (!admin) return NextResponse.json({ error: "Unauthorized" }, { status: 401 });

  try {
    const input = validateBroadcastInput(await request.json());
    const result = await createBroadcast(admin.id, input);
    return NextResponse.json(
      { broadcast: result.broadcast, duplicate: result.duplicate },
      { status: result.duplicate ? 200 : 201 },
    );
  } catch (error) {
    if (error instanceof NotificationValidationError) {
      return NextResponse.json({ error: error.message }, { status: 400 });
    }
    console.error("Error creating notification broadcast:", error);
    return NextResponse.json({ error: "Failed to create broadcast" }, { status: 500 });
  }
}
