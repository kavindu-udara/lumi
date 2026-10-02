import { NextRequest } from "next/server";
import { AuthError, getAuthenticatedUser } from "@/lib/auth/supabase-user-auth";
import { createSupabaseServerClient } from "@/lib/supabase/server";

type ImageRow = {
  id: string;
  user_id: string;
  album_id: string;
  storage_path: string;
  original_name: string;
  mime_type: string;
  size_bytes: number;
  latitude: number | null;
  longitude: number | null;
  metadata: Record<string, unknown>;
  captured_at: string | null;
  created_at: string;
};

const toLegacyImage = (image: ImageRow) => ({
  _id: image.id,
  userId: image.user_id,
  imageId: image.storage_path,
  albumId: image.album_id,
  size: image.size_bytes,
  location: {
    latitude: image.latitude,
    longitude: image.longitude,
  },
  metadata: Object.entries(image.metadata ?? {}).map(([name, value]) => ({
    name,
    type: typeof value,
  })),
  timestamp: image.captured_at ?? image.created_at,
  originalName: image.original_name,
  mimeType: image.mime_type,
});

export async function GET(request: NextRequest) {
  try {
    const user = await getAuthenticatedUser(request);
    const supabase = createSupabaseServerClient(request);
    const rawAlbumId = request.nextUrl.searchParams.get("albumId") ?? "";
    const albumId = rawAlbumId.replace(/^"|"$/g, "").trim();
    const isAllAlbums = albumId.toLowerCase() === "all";

    if (!isAllAlbums && albumId) {
      const { data: album, error: albumError } = await supabase
        .from("albums")
        .select("id")
        .eq("id", albumId)
        .eq("user_id", user.id)
        .maybeSingle();
      if (albumError) throw albumError;
      if (!album) return Response.json({ error: "Album not found for user" }, { status: 404 });
    }

    let selectedAlbumId = albumId;
    if (!isAllAlbums && !selectedAlbumId) {
      const { data: recentAlbum, error: recentError } = await supabase
        .from("albums")
        .select("id")
        .eq("user_id", user.id)
        .eq("name", "Recent")
        .maybeSingle();
      if (recentError) throw recentError;

      if (recentAlbum) {
        selectedAlbumId = recentAlbum.id;
      } else {
        const { data: createdAlbum, error: createError } = await supabase
          .from("albums")
          .insert({ user_id: user.id, name: "Recent" })
          .select("id")
          .single();
        if (createError) throw createError;
        selectedAlbumId = createdAlbum.id;
      }
    }

    let query = supabase
      .from("images")
      .select("id, user_id, album_id, storage_path, original_name, mime_type, size_bytes, latitude, longitude, metadata, captured_at, created_at")
      .eq("user_id", user.id)
      .order("captured_at", { ascending: false, nullsFirst: false });
    if (!isAllAlbums) query = query.eq("album_id", selectedAlbumId);

    const { data: images, error } = await query;
    if (error) throw error;
    return Response.json((images as ImageRow[]).map(toLegacyImage), { status: 200 });
  } catch (error) {
    if (error instanceof AuthError) {
      return Response.json({ error: error.message }, { status: error.status });
    }
    console.error("Error fetching photos:", error);
    return Response.json({ error: "Failed to fetch photos" }, { status: 500 });
  }
}

export async function DELETE(request: NextRequest) {
  const { photoId } = await request.json();
  if (!photoId) return Response.json({ error: "Missing photoId parameter" }, { status: 400 });

  try {
    const user = await getAuthenticatedUser(request);
    const supabase = createSupabaseServerClient(request);
    const { data: image, error: imageError } = await supabase
      .from("images")
      .select("id, storage_path, size_bytes")
      .eq("user_id", user.id)
      .eq("storage_path", String(photoId))
      .maybeSingle();
    if (imageError) throw imageError;
    if (!image) return Response.json({ error: "Photo not found for user" }, { status: 404 });

    const { error: deleteError } = await supabase
      .from("images")
      .delete()
      .eq("id", image.id)
      .eq("user_id", user.id);
    if (deleteError) throw deleteError;

    const { error: releaseError } = await supabase.rpc("release_storage", {
      released_bytes: image.size_bytes,
    });
    if (releaseError) throw releaseError;

    const { error: storageError } = await supabase.storage
      .from("photos")
      .remove([image.storage_path]);
    if (storageError) throw storageError;

    return Response.json({ message: "Photo deleted successfully" }, { status: 200 });
  } catch (error) {
    if (error instanceof AuthError) {
      return Response.json({ error: error.message }, { status: error.status });
    }
    console.error("Error deleting photo:", error);
    return Response.json({ error: "Failed to delete photo" }, { status: 500 });
  }
}
