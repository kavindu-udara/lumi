import { NextRequest } from "next/server";
import { AuthError, getAuthenticatedUser } from "@/lib/auth/supabase-user-auth";
import { createSupabaseServerClient } from "@/lib/supabase/server";

export async function POST(
  request: NextRequest,
  { params }: { params: Promise<{ id: string }> },
) {
  try {
    const user = await getAuthenticatedUser(request);
    const { id } = await params;
    const supabase = createSupabaseServerClient(request);
    const { data, error } = await supabase.rpc("mark_notification_read", {
      notification_id: id,
    });
    if (error) {
      if (error.code === "P0002") return Response.json({ error: "Notification not found" }, { status: 404 });
      throw error;
    }
    return Response.json({ notification: data, userId: user.id }, { status: 200 });
  } catch (error) {
    if (error instanceof AuthError) return Response.json({ error: error.message }, { status: error.status });
    console.error("Error marking notification read:", error);
    return Response.json({ error: "Failed to mark notification as read" }, { status: 500 });
  }
}
