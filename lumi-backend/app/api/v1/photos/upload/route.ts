import { randomUUID } from "node:crypto";
import { NextRequest, NextResponse } from "next/server";
import { AuthError, getAuthenticatedUser } from "@/lib/auth/supabase-user-auth";
import { createSupabaseServerClient } from "@/lib/supabase/server";

type Metadata = {
  name?: string;
  type?: "image" | "video";
  capturedAt?: string;
  date?: string;
  latitude?: number;
  longitude?: number;
  [key: string]: unknown;
};

const MAX_FILE_SIZE = 50 * 1024 * 1024;
const RESERVED_FORM_FIELDS = new Set(["image", "file", "imageFile", "metadata", "meta", "data"]);

function isUploadedFile(value: FormDataEntryValue | null): value is File {
  return !!value && typeof value === "object" && "name" in value && "type" in value && "size" in value && "arrayBuffer" in value;
}

function parseMetadataFromFormData(formData: FormData): Metadata {
  const metadata: Metadata = {};
  for (const [key, value] of formData.entries()) {
    if (!RESERVED_FORM_FIELDS.has(key) && typeof value === "string") metadata[key] = value;
  }
  if (typeof metadata.latitude === "string") metadata.latitude = Number(metadata.latitude);
  if (typeof metadata.longitude === "string") metadata.longitude = Number(metadata.longitude);
  return metadata;
}

function resolveTimestamp(metadata: Metadata): string {
  const raw = typeof metadata.capturedAt === "string" ? metadata.capturedAt : metadata.date;
  if (!raw) return new Date().toISOString();
  if (/^\d+$/.test(raw)) {
    const numeric = Number(raw);
    const parsed = new Date(raw.length <= 10 ? numeric * 1000 : numeric);
    if (!Number.isNaN(parsed.getTime())) return parsed.toISOString();
  }
  const parsed = new Date(raw);
  return Number.isNaN(parsed.getTime()) ? new Date().toISOString() : parsed.toISOString();
}

function safeFileName(name: string) {
  return name.replace(/[^a-zA-Z0-9._-]/g, "_").slice(0, 160) || "upload";
}

export async function POST(request: NextRequest) {
  const contentType = request.headers.get("content-type") ?? "";
  if (!contentType.includes("multipart/form-data")) {
    return NextResponse.json({ message: "Content-Type must be multipart/form-data", success: false }, { status: 400 });
  }

  let storagePath: string | null = null;
  let reservedBytes = 0;
  try {
    const user = await getAuthenticatedUser(request);
    const supabase = createSupabaseServerClient(request);
    const albumId = (request.nextUrl.searchParams.get("albumId") ?? "").replace(/^"|"$/g, "").trim();
    const formData = await request.formData();
    const imageFile = formData.get("image") ?? formData.get("file") ?? formData.get("imageFile");
    const metadataRaw = formData.get("metadata") ?? formData.get("meta") ?? formData.get("data");

    if (!isUploadedFile(imageFile)) {
      return NextResponse.json({ message: "Media file is required", success: false }, { status: 400 });
    }
    if (imageFile.size <= 0 || imageFile.size > MAX_FILE_SIZE) {
      return NextResponse.json({ message: "Media file exceeds the 50 MB limit", success: false }, { status: 400 });
    }
    if (!imageFile.type.startsWith("image/") && !imageFile.type.startsWith("video/")) {
      return NextResponse.json({ message: "Only images and videos are allowed", success: false }, { status: 400 });
    }

    let metadata: Metadata;
    if (metadataRaw == null) {
      metadata = parseMetadataFromFormData(formData);
    } else {
      try {
        const raw = typeof metadataRaw === "string" ? metadataRaw : await metadataRaw.text();
        metadata = JSON.parse(raw) as Metadata;
      } catch {
        return NextResponse.json({ message: "Invalid metadata JSON", success: false }, { status: 400 });
      }
    }
    if (!metadata || typeof metadata !== "object" || Array.isArray(metadata)) {
      return NextResponse.json({ message: "Metadata must be a valid JSON object", success: false }, { status: 400 });
    }

    const { data: album, error: albumError } = albumId
      ? await supabase.from("albums").select("id").eq("id", albumId).eq("user_id", user.id).maybeSingle()
      : await supabase.from("albums").select("id").eq("user_id", user.id).eq("name", "Recent").maybeSingle();
    if (albumError) throw albumError;

    let selectedAlbumId = album?.id;
    if (!selectedAlbumId) {
      if (albumId) return NextResponse.json({ message: "Album not found for user", success: false }, { status: 404 });
      const { data: createdAlbum, error: createAlbumError } = await supabase
        .from("albums")
        .insert({ user_id: user.id, name: "Recent" })
        .select("id")
        .single();
      if (createAlbumError) throw createAlbumError;
      selectedAlbumId = createdAlbum.id;
    }

    reservedBytes = imageFile.size;
    const { error: reserveError } = await supabase.rpc("reserve_storage", { required_bytes: reservedBytes });
    if (reserveError) throw reserveError;

    const imageId = randomUUID();
    storagePath = `${user.id}/${selectedAlbumId}/${imageId}-${safeFileName(imageFile.name)}`;
    const { error: uploadError } = await supabase.storage
      .from("photos")
      .upload(storagePath, imageFile, { contentType: imageFile.type, upsert: false });
    if (uploadError) throw uploadError;

    const normalizedMetadata: Metadata = {
      ...metadata,
      name: typeof metadata.name === "string" ? metadata.name : imageFile.name,
      type: imageFile.type.startsWith("video/") ? "video" : "image",
    };
    const { data: createdImage, error: imageError } = await supabase
      .from("images")
      .insert({
        id: imageId,
        user_id: user.id,
        album_id: selectedAlbumId,
        storage_path: storagePath,
        original_name: imageFile.name,
        mime_type: imageFile.type,
        size_bytes: imageFile.size,
        latitude: typeof normalizedMetadata.latitude === "number" && Number.isFinite(normalizedMetadata.latitude) ? normalizedMetadata.latitude : null,
        longitude: typeof normalizedMetadata.longitude === "number" && Number.isFinite(normalizedMetadata.longitude) ? normalizedMetadata.longitude : null,
        metadata: normalizedMetadata,
        captured_at: resolveTimestamp(normalizedMetadata),
      })
      .select("id, album_id, storage_path, original_name, mime_type, size_bytes, metadata, captured_at")
      .single();
    if (imageError) throw imageError;

    return NextResponse.json({
      message: "File and metadata received successfully",
      success: true,
      data: {
        albumId: createdImage.album_id,
        filename: createdImage.original_name,
        mimeType: createdImage.mime_type,
        size: createdImage.size_bytes,
        storagePath: createdImage.storage_path,
        metadata: createdImage.metadata,
      },
    }, { status: 200 });
  } catch (error) {
    if (error instanceof AuthError) {
      return NextResponse.json({ error: error.message }, { status: error.status });
    }

    const supabase = createSupabaseServerClient(request);
    if (storagePath) await supabase.storage.from("photos").remove([storagePath]);
    if (reservedBytes) await supabase.rpc("release_storage", { released_bytes: reservedBytes });
    console.error("Error uploading photo:", error);
    return NextResponse.json({ message: "Failed to upload photo", success: false }, { status: 500 });
  }
}
