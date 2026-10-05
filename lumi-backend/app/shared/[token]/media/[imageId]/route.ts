import { NextResponse } from "next/server";
import { getPublicImage, resolvePublicAlbum } from "@/lib/sharing/public-album";
import { createSupabaseAdminClient } from "@/lib/supabase/server";

export const runtime = "nodejs";

type RouteContext = { params: Promise<{ token: string; imageId: string }> };

export async function GET(_request: Request, { params }: RouteContext) {
  try {
    const { token, imageId } = await params;
    const resolved = await resolvePublicAlbum(token);
    if (!resolved) return NextResponse.json({ error: "Not found" }, { status: 404, headers: { "Cache-Control": "no-store" } });

    const image = await getPublicImage(resolved.album.id, imageId);
    if (!image) return NextResponse.json({ error: "Not found" }, { status: 404, headers: { "Cache-Control": "no-store" } });

    const { data, error } = await createSupabaseAdminClient().storage.from("photos").download(image.storage_path);
    if (error || !data) return NextResponse.json({ error: "Media is unavailable" }, { status: 404, headers: { "Cache-Control": "no-store" } });

    return new Response(data, {
      headers: {
        "Content-Type": image.mime_type || data.type || "application/octet-stream",
        "Cache-Control": "private, no-store",
        "X-Content-Type-Options": "nosniff",
      },
    });
  } catch (error) {
    console.error("Error serving shared media:", error instanceof Error ? error.message : String(error));
    return NextResponse.json({ error: "Failed to load media" }, { status: 500 });
  }
}
