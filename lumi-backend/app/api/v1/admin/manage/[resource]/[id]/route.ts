import { NextRequest, NextResponse } from "next/server";
import { getAdminFromRequest } from "@/lib/admin-guard";
import { createSupabaseServerClient } from "@/lib/supabase/server";

const tableByResource = {
  plans: "plans",
  albums: "albums",
  images: "images",
  subscriptions: "subscriptions",
  storage: "user_storage",
} as const;

type Resource = keyof typeof tableByResource;
type Row = Record<string, unknown>;
const isResource = (value: string): value is Resource => value in tableByResource;

const toLegacyRow = (resource: Resource, row: Row): Row => {
  const result: Row = { ...row, _id: row.id };
  if (resource === "plans") result.storageLimit = row.storage_limit_bytes;
  if (resource === "albums") {
    result.userId = row.user_id;
    result.coverPhotoUrl = row.cover_photo_path;
  }
  if (resource === "images") {
    result.userId = row.user_id;
    result.imageId = row.storage_path;
    result.albumId = row.album_id;
    result.size = row.size_bytes;
    result.timestamp = row.captured_at;
  }
  if (resource === "subscriptions") {
    result.userId = row.user_id;
    result.planId = row.plan_id;
    result.stripeMerchantId = row.stripe_merchant_id;
    result.paymentIntentId = row.payment_intent_id;
    result.startDate = row.start_date;
    result.endDate = row.end_date;
  }
  if (resource === "storage") {
    result.userId = row.user_id;
    result.planId = row.plan_id;
    result.usedStorage = row.used_bytes;
  }
  return result;
};

const toDbPayload = (resource: Resource, body: Row): Row => {
  const payload = { ...body };
  delete payload._id;
  delete payload.id;
  const aliases: Record<string, string> = resource === "plans"
    ? { storageLimit: "storage_limit_bytes" }
    : resource === "albums"
      ? { userId: "user_id", coverPhotoUrl: "cover_photo_path" }
      : resource === "images"
        ? { userId: "user_id", imageId: "storage_path", albumId: "album_id", size: "size_bytes", timestamp: "captured_at" }
        : resource === "subscriptions"
          ? { userId: "user_id", planId: "plan_id", stripeMerchantId: "stripe_merchant_id", paymentIntentId: "payment_intent_id", startDate: "start_date", endDate: "end_date" }
          : { userId: "user_id", planId: "plan_id", usedStorage: "used_bytes" };
  for (const [legacy, database] of Object.entries(aliases)) {
    if (payload[legacy] !== undefined) payload[database] = payload[legacy];
    delete payload[legacy];
  }
  return payload;
};

const authorize = async (request: NextRequest) => getAdminFromRequest(request);

export async function GET(request: NextRequest, { params }: { params: Promise<{ resource: string; id: string }> }) {
  if (!(await authorize(request))) return NextResponse.json({ error: "Unauthorized" }, { status: 401 });
  const { resource, id } = await params;
  if (!isResource(resource)) return NextResponse.json({ error: "Invalid resource" }, { status: 400 });
  const supabase = createSupabaseServerClient(request);
  const { data, error } = await supabase.from(tableByResource[resource]).select().eq("id", id).maybeSingle();
  if (error) return NextResponse.json({ error: error.message }, { status: 500 });
  if (!data) return NextResponse.json({ error: "Item not found" }, { status: 404 });
  return NextResponse.json({ item: toLegacyRow(resource, data) });
}

export async function PUT(request: NextRequest, { params }: { params: Promise<{ resource: string; id: string }> }) {
  if (!(await authorize(request))) return NextResponse.json({ error: "Unauthorized" }, { status: 401 });
  const { resource, id } = await params;
  if (!isResource(resource)) return NextResponse.json({ error: "Invalid resource" }, { status: 400 });
  const supabase = createSupabaseServerClient(request);
  const { data, error } = await supabase.from(tableByResource[resource]).update(toDbPayload(resource, await request.json())).eq("id", id).select().maybeSingle();
  if (error) return NextResponse.json({ error: error.message }, { status: 400 });
  if (!data) return NextResponse.json({ error: "Item not found" }, { status: 404 });
  return NextResponse.json({ item: toLegacyRow(resource, data) });
}

export async function DELETE(request: NextRequest, { params }: { params: Promise<{ resource: string; id: string }> }) {
  if (!(await authorize(request))) return NextResponse.json({ error: "Unauthorized" }, { status: 401 });
  const { resource, id } = await params;
  if (!isResource(resource)) return NextResponse.json({ error: "Invalid resource" }, { status: 400 });
  const supabase = createSupabaseServerClient(request);
  const { data, error } = await supabase.from(tableByResource[resource]).delete().eq("id", id).select("id").maybeSingle();
  if (error) return NextResponse.json({ error: error.message }, { status: 400 });
  if (!data) return NextResponse.json({ error: "Item not found" }, { status: 404 });
  return NextResponse.json({ success: true });
}
