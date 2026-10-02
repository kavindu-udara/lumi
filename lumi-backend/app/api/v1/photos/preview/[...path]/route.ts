import { NextRequest, NextResponse } from "next/server";
import { AuthError, getAuthenticatedUser } from "@/lib/auth/supabase-user-auth";
import { createSupabaseServerClient } from "@/lib/supabase/server";

export const runtime = "nodejs";

export async function GET(
  request: NextRequest,
  { params }: { params: Promise<{ path: string[] }> },
) {
  try {
    const user = await getAuthenticatedUser(request);
    const { path } = await params;

    if (!path || path.length < 2) {
      return NextResponse.json({ error: "Invalid photo path" }, { status: 400 });
    }

    const storagePath = path.join("/");
    if (path[0] !== user.id) {
      return NextResponse.json({ error: "Photo not found" }, { status: 404 });
    }

    const supabase = createSupabaseServerClient(request);
    const { data, error } = await supabase.storage
      .from("photos")
      .download(storagePath);

    if (error || !data) {
      return NextResponse.json({ error: "Photo not found" }, { status: 404 });
    }

    return new Response(data, {
      status: 200,
      headers: {
        "Content-Type": data.type || "application/octet-stream",
        "Cache-Control": "private, max-age=300",
      },
    });
  } catch (error) {
    if (error instanceof AuthError) {
      return NextResponse.json({ error: error.message }, { status: error.status });
    }

    console.error("Error previewing photo:", error);
    return NextResponse.json({ error: "Failed to preview photo" }, { status: 500 });
  }
}
