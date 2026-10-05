import { NextRequest } from "next/server";
import { AuthError, getAuthenticatedUser } from "@/lib/auth/supabase-user-auth";
import { createSupabaseServerClient } from "@/lib/supabase/server";

type RouteContext = { params: Promise<{ id: string }> };

const albumFields = "id, user_id, name, description, cover_photo_path, created_at, updated_at";

export async function PATCH(request: NextRequest, { params }: RouteContext) {
  try {
    const user = await getAuthenticatedUser(request);
    const { id } = await params;
    const body = await request.json() as { name?: unknown; description?: unknown };
    const name = typeof body.name === "string" ? body.name.trim() : "";
    const description = body.description === null
      ? null
      : typeof body.description === "string" ? body.description.trim() : undefined;

    if (!name) return Response.json({ error: "Album name is required" }, { status: 400 });
    if (name.length > 120) return Response.json({ error: "Album name must be 120 characters or fewer" }, { status: 400 });
    if (description !== undefined && description !== null && description.length > 500) {
      return Response.json({ error: "Album description must be 500 characters or fewer" }, { status: 400 });
    }

    const supabase = createSupabaseServerClient(request);
    const updates: { name: string; description?: string | null } = { name };
    if (description !== undefined) updates.description = description;
    const { data: album, error } = await supabase
      .from("albums")
      .update(updates)
      .eq("id", id)
      .eq("user_id", user.id)
      .select(albumFields)
      .maybeSingle();

    if (error) {
      if (error.code === "23505") return Response.json({ error: "Album with the same name already exists" }, { status: 400 });
      throw error;
    }
    if (!album) return Response.json({ error: "Album not found for user" }, { status: 404 });
    return Response.json({ album }, { status: 200 });
  } catch (error) {
    if (error instanceof AuthError) return Response.json({ error: error.message }, { status: error.status });
    console.error("Error updating album:", error);
    return Response.json({ error: "Failed to update album" }, { status: 500 });
  }
}

export const PUT = PATCH;

export async function DELETE(request: NextRequest, { params }: RouteContext) {
  try {
    const user = await getAuthenticatedUser(request);
    const { id } = await params;
    const supabase = createSupabaseServerClient(request);
    const { data: album, error: albumError } = await supabase
      .from("albums")
      .select("id")
      .eq("id", id)
      .eq("user_id", user.id)
      .maybeSingle();
    if (albumError) throw albumError;
    if (!album) return Response.json({ error: "Album not found for user" }, { status: 404 });

    const { data: images, error: imageError } = await supabase
      .from("images")
      .select("storage_path, size_bytes")
      .eq("album_id", id)
      .eq("user_id", user.id);
    if (imageError) throw imageError;

    const paths = (images ?? []).map((image) => image.storage_path);
    if (paths.length > 0) {
      const { error: storageError } = await supabase.storage.from("photos").remove(paths);
      if (storageError) throw storageError;
    }

    const { error: deleteError } = await supabase
      .from("albums")
      .delete()
      .eq("id", id)
      .eq("user_id", user.id);
    if (deleteError) throw deleteError;

    const releasedBytes = (images ?? []).reduce((total, image) => total + Number(image.size_bytes), 0);
    if (releasedBytes > 0) {
      const { error: releaseError } = await supabase.rpc("release_storage", {
        released_bytes: releasedBytes,
      });
      if (releaseError) throw releaseError;
    }

    return Response.json({ message: "Album deleted successfully" }, { status: 200 });
  } catch (error) {
    if (error instanceof AuthError) return Response.json({ error: error.message }, { status: error.status });
    console.error("Error deleting album:", error);
    return Response.json({ error: "Failed to delete album" }, { status: 500 });
  }
}
