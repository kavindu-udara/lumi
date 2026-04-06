import { NextRequest, NextResponse } from "next/server";
import bcrypt from "bcrypt";
import mongoose from "mongoose";
import connectDB from "@/lib/db";
import Admin from "@/models/admin.model";
import Plan from "@/models/plans.model";
import Album from "@/models/album.model";
import Image from "@/models/image.model";
import Subscription from "@/models/subcription.model";
import Storage from "@/models/storage.model";
import { getAdminFromRequest } from "@/lib/admin-guard";

export const runtime = "nodejs";

type AdminPayload = {
  username?: string;
  password?: string;
};

const resourceMap = {
  admins: Admin,
  plans: Plan,
  albums: Album,
  images: Image,
  subscriptions: Subscription,
  storage: Storage,
} as const;

type ResourceKey = keyof typeof resourceMap;

const getModelByResource = (resource: string) => {
  if (!(resource in resourceMap)) {
    return null;
  }
  return resourceMap[resource as ResourceKey];
};

const isAdminResource = (resource: string) => resource === "admins";

const sanitizeDataForCreate = async (resource: string, body: Record<string, unknown>) => {
  const payload = { ...body };

  if (isAdminResource(resource)) {
    const adminPayload = payload as AdminPayload;
    if (!adminPayload.username || !adminPayload.password) {
      throw new Error("username and password are required for admins");
    }

    adminPayload.username = String(adminPayload.username).trim();
    adminPayload.password = await bcrypt.hash(String(adminPayload.password), 10);
  }

  return payload;
};

const sanitizeList = (resource: string, docs: Array<Record<string, unknown>>) => {
  if (!isAdminResource(resource)) {
    return docs;
  }

  return docs.map((doc) => {
    const safeDoc = { ...doc };
    delete safeDoc.password;
    return safeDoc;
  });
};

export async function GET(
  request: NextRequest,
  { params }: { params: Promise<{ resource: string }> }
) {
  const admin = getAdminFromRequest(request);
  if (!admin) {
    return NextResponse.json({ error: "Unauthorized" }, { status: 401 });
  }

  const { resource } = await params;
  const Model = getModelByResource(resource);

  if (!Model) {
    return NextResponse.json({ error: "Invalid resource" }, { status: 400 });
  }

  try {
    await connectDB();

    const limitParam = Number(request.nextUrl.searchParams.get("limit") || "50");
    const pageParam = Number(request.nextUrl.searchParams.get("page") || "1");
    const limit = Number.isFinite(limitParam) ? Math.min(Math.max(limitParam, 1), 200) : 50;
    const page = Number.isFinite(pageParam) ? Math.max(pageParam, 1) : 1;
    const skip = (page - 1) * limit;

    const [items, total] = await Promise.all([
      Model.find().sort({ createdAt: -1, _id: -1 }).skip(skip).limit(limit).lean(),
      Model.countDocuments(),
    ]);

    return NextResponse.json(
      {
        resource,
        page,
        limit,
        total,
        items: sanitizeList(resource, items as Array<Record<string, unknown>>),
      },
      { status: 200 }
    );
  } catch (error) {
    const message = error instanceof Error ? error.message : "Internal server error";
    return NextResponse.json({ error: message }, { status: 500 });
  }
}

export async function POST(
  request: NextRequest,
  { params }: { params: Promise<{ resource: string }> }
) {
  const admin = getAdminFromRequest(request);
  if (!admin) {
    return NextResponse.json({ error: "Unauthorized" }, { status: 401 });
  }

  const { resource } = await params;
  const Model = getModelByResource(resource);

  if (!Model) {
    return NextResponse.json({ error: "Invalid resource" }, { status: 400 });
  }

  try {
    await connectDB();
    const body = (await request.json()) as Record<string, unknown>;

    const payload = await sanitizeDataForCreate(resource, body);
    const created = await Model.create(payload);
    const createdObj = created.toObject() as Record<string, unknown>;

    if (isAdminResource(resource)) {
      delete createdObj.password;
    }

    return NextResponse.json({ item: createdObj }, { status: 201 });
  } catch (error) {
    if (error instanceof mongoose.Error.ValidationError) {
      return NextResponse.json({ error: error.message }, { status: 400 });
    }

    const message = error instanceof Error ? error.message : "Internal server error";
    return NextResponse.json({ error: message }, { status: 500 });
  }
}
