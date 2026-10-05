import { NextRequest, NextResponse } from "next/server";
import { AuthError, getAuthenticatedUser } from "@/lib/auth/supabase-user-auth";
import { createSupabaseServerClient } from "@/lib/supabase/server";
import {
  createShareId,
  createShareToken,
  getShareUrl,
  hashShareToken,
} from "@/lib/sharing/album-share";

type RouteContext = { params: Promise<{ id: string }> };

const responseForShare = (share: { id: string; created_at: string }) => {
  const token = createShareToken(share.id);
  return {
    url: getShareUrl(token),
    token,
    publishedAt: share.created_at,
    revokedAt: null,
  };
};

const getOwnedAlbum = async (request: NextRequest, albumId: string, userId: string) => {
  const supabase = createSupabaseServerClient(request);
  const { data, error } = await supabase
    .from("albums")
    .select("id")
    .eq("id", albumId)
    .eq("user_id", userId)
    .maybeSingle();
  if (error) throw error;
  return { supabase, album: data };
};

export async function POST(request: NextRequest, { params }: RouteContext) {
  try {
    const user = await getAuthenticatedUser(request);
    const { id } = await params;
    const { supabase, album } = await getOwnedAlbum(request, id, user.id);
    if (!album) return NextResponse.json({ error: "Album not found for user" }, { status: 404 });

    const { data: existing, error: existingError } = await supabase
      .from("album_shares")
      .select("id, created_at")
      .eq("album_id", id)
      .is("revoked_at", null)
      .maybeSingle();
    if (existingError) throw existingError;
    if (existing) return NextResponse.json({ share: responseForShare(existing) }, { status: 200 });

    const shareId = createShareId();
    const token = createShareToken(shareId);
    const { data: created, error: createError } = await supabase
      .from("album_shares")
      .insert({ id: shareId, album_id: id, token_hash: hashShareToken(token) })
      .select("id, created_at")
      .single();

    if (createError?.code === "23505") {
      const { data: concurrent, error: concurrentError } = await supabase
        .from("album_shares")
        .select("id, created_at")
        .eq("album_id", id)
        .is("revoked_at", null)
        .single();
      if (concurrentError) throw concurrentError;
      return NextResponse.json({ share: responseForShare(concurrent) }, { status: 200 });
    }
    if (createError) throw createError;
    return NextResponse.json({ share: responseForShare(created) }, { status: 201 });
  } catch (error) {
    if (error instanceof AuthError) return NextResponse.json({ error: error.message }, { status: error.status });
    console.error("Error publishing album:", error instanceof Error ? error.message : String(error));
    return NextResponse.json({ error: "Failed to publish album" }, { status: 500 });
  }
}

export async function GET(request: NextRequest, { params }: RouteContext) {
  try {
    const user = await getAuthenticatedUser(request);
    const { id } = await params;
    const { supabase, album } = await getOwnedAlbum(request, id, user.id);
    if (!album) return NextResponse.json({ error: "Album not found for user" }, { status: 404 });
    const { data: share, error } = await supabase
      .from("album_shares")
      .select("id, created_at")
      .eq("album_id", id)
      .is("revoked_at", null)
      .maybeSingle();
    if (error) throw error;
    return NextResponse.json({ share: share ? responseForShare(share) : null }, { status: 200 });
  } catch (error) {
    if (error instanceof AuthError) return NextResponse.json({ error: error.message }, { status: error.status });
    console.error("Error fetching album share:", error instanceof Error ? error.message : String(error));
    return NextResponse.json({ error: "Failed to fetch album share" }, { status: 500 });
  }
}

export async function DELETE(request: NextRequest, { params }: RouteContext) {
  try {
    const user = await getAuthenticatedUser(request);
    const { id } = await params;
    const { supabase, album } = await getOwnedAlbum(request, id, user.id);
    if (!album) return NextResponse.json({ error: "Album not found for user" }, { status: 404 });
    const { error } = await supabase
      .from("album_shares")
      .update({ revoked_at: new Date().toISOString() })
      .eq("album_id", id)
      .is("revoked_at", null);
    if (error) throw error;
    return NextResponse.json({ message: "Album website closed" }, { status: 200 });
  } catch (error) {
    if (error instanceof AuthError) return NextResponse.json({ error: error.message }, { status: error.status });
    console.error("Error closing album share:", error instanceof Error ? error.message : String(error));
    return NextResponse.json({ error: "Failed to close album website" }, { status: 500 });
  }
}
