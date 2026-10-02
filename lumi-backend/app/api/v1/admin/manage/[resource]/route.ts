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
  if (resource === "plans") {
    result.storageLimit = row.storage_limit_bytes;
    delete result.storage_limit_bytes;
  } else if (resource === "albums") {
    result.userId = row.user_id;
    result.coverPhotoUrl = row.cover_photo_path;
    delete result.user_id;
    delete result.cover_photo_path;
  } else if (resource === "images") {
    result.userId = row.user_id;
    result.imageId = row.storage_path;
    result.albumId = row.album_id;
    result.size = row.size_bytes;
    result.timestamp = row.captured_at;
  } else if (resource === "subscriptions") {
    result.userId = row.user_id;
    result.planId = row.plan_id;
    result.stripeMerchantId = row.stripe_merchant_id;
    result.paymentIntentId = row.payment_intent_id;
    result.startDate = row.start_date;
    result.endDate = row.end_date;
  } else if (resource === "storage") {
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
  if (resource === "plans") {
    if (payload.storageLimit !== undefined) payload.storage_limit_bytes = payload.storageLimit;
    delete payload.storageLimit;
  } else if (resource === "albums") {
    if (payload.userId !== undefined) payload.user_id = payload.userId;
    if (payload.coverPhotoUrl !== undefined) payload.cover_photo_path = payload.coverPhotoUrl;
    delete payload.userId;
    delete payload.coverPhotoUrl;
  } else if (resource === "images") {
    if (payload.userId !== undefined) payload.user_id = payload.userId;
    if (payload.imageId !== undefined) payload.storage_path = payload.imageId;
    if (payload.albumId !== undefined) payload.album_id = payload.albumId;
    if (payload.size !== undefined) payload.size_bytes = payload.size;
    if (payload.timestamp !== undefined) payload.captured_at = payload.timestamp;
    delete payload.userId;
    delete payload.imageId;
    delete payload.albumId;
    delete payload.size;
    delete payload.timestamp;
  } else if (resource === "subscriptions") {
    if (payload.userId !== undefined) payload.user_id = payload.userId;
    if (payload.planId !== undefined) payload.plan_id = payload.planId;
    if (payload.stripeMerchantId !== undefined) payload.stripe_merchant_id = payload.stripeMerchantId;
    if (payload.paymentIntentId !== undefined) payload.payment_intent_id = payload.paymentIntentId;
    if (payload.startDate !== undefined) payload.start_date = payload.startDate;
    if (payload.endDate !== undefined) payload.end_date = payload.endDate;
    delete payload.userId;
    delete payload.planId;
    delete payload.stripeMerchantId;
    delete payload.paymentIntentId;
    delete payload.startDate;
    delete payload.endDate;
  } else if (resource === "storage") {
    if (payload.userId !== undefined) payload.user_id = payload.userId;
    if (payload.planId !== undefined) payload.plan_id = payload.planId;
    if (payload.usedStorage !== undefined) payload.used_bytes = payload.usedStorage;
    delete payload.userId;
    delete payload.planId;
    delete payload.usedStorage;
  }
  return payload;
};

export async function GET(request: NextRequest, { params }: { params: Promise<{ resource: string }> }) {
  const admin = await getAdminFromRequest(request);
  if (!admin) return NextResponse.json({ error: "Unauthorized" }, { status: 401 });
  const { resource } = await params;
  if (!isResource(resource)) return NextResponse.json({ error: "Invalid resource" }, { status: 400 });

  const limitParam = Number(request.nextUrl.searchParams.get("limit") || "50");
  const pageParam = Number(request.nextUrl.searchParams.get("page") || "1");
  const limit = Number.isFinite(limitParam) ? Math.min(Math.max(limitParam, 1), 200) : 50;
  const page = Number.isFinite(pageParam) ? Math.max(pageParam, 1) : 1;
  const from = (page - 1) * limit;
  const supabase = createSupabaseServerClient(request);
  const { data, error, count } = await supabase
    .from(tableByResource[resource])
    .select("*", { count: "exact" })
    .order("created_at", { ascending: false })
    .range(from, from + limit - 1);
  if (error) return NextResponse.json({ error: error.message }, { status: 500 });

  return NextResponse.json({ resource, page, limit, total: count ?? 0, items: (data ?? []).map((row) => toLegacyRow(resource, row)) });
}

export async function POST(request: NextRequest, { params }: { params: Promise<{ resource: string }> }) {
  const admin = await getAdminFromRequest(request);
  if (!admin) return NextResponse.json({ error: "Unauthorized" }, { status: 401 });
  const { resource } = await params;
  if (!isResource(resource)) return NextResponse.json({ error: "Invalid resource" }, { status: 400 });

  const payload = toDbPayload(resource, await request.json());
  const supabase = createSupabaseServerClient(request);
  const { data, error } = await supabase.from(tableByResource[resource]).insert(payload).select().single();
  if (error) return NextResponse.json({ error: error.message }, { status: 400 });
  return NextResponse.json({ item: toLegacyRow(resource, data) }, { status: 201 });
}
