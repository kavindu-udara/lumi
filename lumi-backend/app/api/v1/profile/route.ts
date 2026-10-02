import { NextRequest } from "next/server";
import { AuthError, getAuthenticatedUser } from "@/lib/auth/supabase-user-auth";
import { createSupabaseServerClient } from "@/lib/supabase/server";

const MAX_DISPLAY_NAME_LENGTH = 120;

export async function GET(request: NextRequest) {
  try {
    const user = await getAuthenticatedUser(request);
    const supabase = createSupabaseServerClient(request);
    const { data: profile, error } = await supabase
      .from("profiles")
      .select("id, display_name, avatar_url, created_at, updated_at")
      .eq("id", user.id)
      .maybeSingle();

    if (error) throw error;
    if (!profile) return Response.json({ error: "Profile not found" }, { status: 404 });
    return Response.json({ profile }, { status: 200 });
  } catch (error) {
    if (error instanceof AuthError) return Response.json({ error: error.message }, { status: error.status });
    console.error("Error fetching profile:", error);
    return Response.json({ error: "Failed to fetch profile" }, { status: 500 });
  }
}

export async function PUT(request: NextRequest) {
  try {
    const user = await getAuthenticatedUser(request);
    const payload = await request.json() as { displayName?: unknown };
    const displayName = typeof payload.displayName === "string" ? payload.displayName.trim() : "";

    if (!displayName) return Response.json({ error: "displayName is required" }, { status: 400 });
    if (displayName.length > MAX_DISPLAY_NAME_LENGTH) {
      return Response.json({ error: `displayName must be ${MAX_DISPLAY_NAME_LENGTH} characters or fewer` }, { status: 400 });
    }

    const supabase = createSupabaseServerClient(request);
    const { data: profile, error } = await supabase
      .from("profiles")
      .update({ display_name: displayName })
      .eq("id", user.id)
      .select("id, display_name, avatar_url, created_at, updated_at")
      .maybeSingle();

    if (error) throw error;
    if (!profile) return Response.json({ error: "Profile not found" }, { status: 404 });
    return Response.json({ profile }, { status: 200 });
  } catch (error) {
    if (error instanceof AuthError) return Response.json({ error: error.message }, { status: error.status });
    console.error("Error updating profile:", error);
    return Response.json({ error: "Failed to update profile" }, { status: 500 });
  }
}
