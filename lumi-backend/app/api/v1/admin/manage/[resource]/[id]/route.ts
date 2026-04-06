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

const sanitizeItem = (resource: string, item: Record<string, unknown>) => {
  if (!isAdminResource(resource)) {
    return item;
  }

  const safeItem = { ...item };
  delete safeItem.password;
  return safeItem;
};

const getSanitizedUpdatePayload = async (
  resource: string,
  payload: Record<string, unknown>
) => {
  const updated = { ...payload };

  if (isAdminResource(resource)) {
    if (typeof updated.password === "string" && updated.password.trim().length > 0) {
      updated.password = await bcrypt.hash(updated.password, 10);
    } else {
      delete updated.password;
    }

    if (typeof updated.username === "string") {
      updated.username = updated.username.trim();
    }
  }

  return updated;
};

const assertValidObjectId = (id: string) => mongoose.Types.ObjectId.isValid(id);

export async function GET(
  request: NextRequest,
  { params }: { params: Promise<{ resource: string; id: string }> }
) {
  const admin = getAdminFromRequest(request);
  if (!admin) {
    return NextResponse.json({ error: "Unauthorized" }, { status: 401 });
  }

  const { resource, id } = await params;
  const Model = getModelByResource(resource);

  if (!Model) {
    return NextResponse.json({ error: "Invalid resource" }, { status: 400 });
  }

  if (!assertValidObjectId(id)) {
    return NextResponse.json({ error: "Invalid document id" }, { status: 400 });
  }

  try {
    await connectDB();
    const item = await Model.findById(id).lean();

    if (!item) {
      return NextResponse.json({ error: "Item not found" }, { status: 404 });
    }

    return NextResponse.json(
      { item: sanitizeItem(resource, item as Record<string, unknown>) },
      { status: 200 }
    );
  } catch (error) {
    const message = error instanceof Error ? error.message : "Internal server error";
    return NextResponse.json({ error: message }, { status: 500 });
  }
}

export async function PUT(
  request: NextRequest,
  { params }: { params: Promise<{ resource: string; id: string }> }
) {
  const admin = getAdminFromRequest(request);
  if (!admin) {
    return NextResponse.json({ error: "Unauthorized" }, { status: 401 });
  }

  const { resource, id } = await params;
  const Model = getModelByResource(resource);

  if (!Model) {
    return NextResponse.json({ error: "Invalid resource" }, { status: 400 });
  }

  if (!assertValidObjectId(id)) {
    return NextResponse.json({ error: "Invalid document id" }, { status: 400 });
  }

  try {
    await connectDB();
    const body = (await request.json()) as Record<string, unknown>;
    const payload = await getSanitizedUpdatePayload(resource, body);

    const updated = await Model.findByIdAndUpdate(id, payload, {
      new: true,
      runValidators: true,
    }).lean();

    if (!updated) {
      return NextResponse.json({ error: "Item not found" }, { status: 404 });
    }

    return NextResponse.json(
      { item: sanitizeItem(resource, updated as Record<string, unknown>) },
      { status: 200 }
    );
  } catch (error) {
    if (error instanceof mongoose.Error.ValidationError) {
      return NextResponse.json({ error: error.message }, { status: 400 });
    }

    const message = error instanceof Error ? error.message : "Internal server error";
    return NextResponse.json({ error: message }, { status: 500 });
  }
}

export async function DELETE(
  request: NextRequest,
  { params }: { params: Promise<{ resource: string; id: string }> }
) {
  const admin = getAdminFromRequest(request);
  if (!admin) {
    return NextResponse.json({ error: "Unauthorized" }, { status: 401 });
  }

  const { resource, id } = await params;
  const Model = getModelByResource(resource);

  if (!Model) {
    return NextResponse.json({ error: "Invalid resource" }, { status: 400 });
  }

  if (!assertValidObjectId(id)) {
    return NextResponse.json({ error: "Invalid document id" }, { status: 400 });
  }

  try {
    await connectDB();
    const deleted = await Model.findByIdAndDelete(id).lean();

    if (!deleted) {
      return NextResponse.json({ error: "Item not found" }, { status: 404 });
    }

    return NextResponse.json({ success: true }, { status: 200 });
  } catch (error) {
    const message = error instanceof Error ? error.message : "Internal server error";
    return NextResponse.json({ error: message }, { status: 500 });
  }
}
