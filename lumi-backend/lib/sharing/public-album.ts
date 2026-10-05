import { createSupabaseAdminClient } from "@/lib/supabase/server";
import { hashShareToken, verifyShareToken } from "@/lib/sharing/album-share";

export type PublicAlbum = {
  id: string;
  name: string;
  description: string | null;
};

export type PublicImage = {
  id: string;
  original_name: string;
  mime_type: string;
  size_bytes: number;
  storage_path: string;
};

export const resolvePublicAlbum = async (token: string) => {
  const shareId = verifyShareToken(token);
  if (!shareId) return null;

  const supabase = createSupabaseAdminClient();
  const { data: share, error: shareError } = await supabase
    .from("album_shares")
    .select("id, album_id, token_hash, revoked_at")
    .eq("id", shareId)
    .eq("token_hash", hashShareToken(token))
    .is("revoked_at", null)
    .maybeSingle();
  if (shareError) throw shareError;
  if (!share) return null;

  const { data: album, error: albumError } = await supabase
    .from("albums")
    .select("id, name, description")
    .eq("id", share.album_id)
    .maybeSingle();
  if (albumError) throw albumError;
  if (!album) return null;

  return { album: album as PublicAlbum, shareId: share.id };
};

export const getPublicImages = async (albumId: string) => {
  const supabase = createSupabaseAdminClient();
  const { data, error } = await supabase
    .from("images")
    .select("id, original_name, mime_type, size_bytes, storage_path")
    .eq("album_id", albumId)
    .order("captured_at", { ascending: false, nullsFirst: false })
    .order("created_at", { ascending: false });
  if (error) throw error;
  return (data ?? []) as PublicImage[];
};

export const getPublicImage = async (albumId: string, imageId: string) => {
  const supabase = createSupabaseAdminClient();
  const { data, error } = await supabase
    .from("images")
    .select("id, original_name, mime_type, size_bytes, storage_path")
    .eq("id", imageId)
    .eq("album_id", albumId)
    .maybeSingle();
  if (error) throw error;
  return (data as PublicImage | null) ?? null;
};
