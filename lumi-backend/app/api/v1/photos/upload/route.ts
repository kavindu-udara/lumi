import { verifyFirebaseUser } from "@/lib/auth/firebase-user-auth";
import connectDB from "@/lib/db";
import { uploadFile } from "@/lib/minio-client";
import Album from "@/models/album.model";
import Image from "@/models/image.model";
import Plan from "@/models/plans.model";
import Storage from "@/models/storage.model";
import { NextRequest, NextResponse } from "next/server";

type MetaData = {
  name?: string;
  type?: "image" | "video";
  capturedAt?: string;
  date?: string;
  firebaseUserId?: string;
  storagePath?: string;
  latitude?: number;
  longitude?: number;
  [key: string]: unknown;
};

function isUploadedFile(value: FormDataEntryValue | null): value is File {
  return (
    !!value &&
    typeof value === "object" &&
    "name" in value &&
    "type" in value &&
    "size" in value &&
    "arrayBuffer" in value
  );
}

function parseMetadataFromFormData(formData: FormData): MetaData {
  const metadata: MetaData = {};

  for (const [key, value] of formData.entries()) {
    if (
      ["image", "file", "imageFile", "metadata", "meta", "data"].includes(key)
    ) {
      continue;
    }

    if (typeof value === "string") {
      metadata[key] = value;
    }
  }

  if (typeof metadata.latitude === "string") {
    const latitude = Number(metadata.latitude);
    if (!Number.isNaN(latitude)) metadata.latitude = latitude;
  }

  if (typeof metadata.longitude === "string") {
    const longitude = Number(metadata.longitude);
    if (!Number.isNaN(longitude)) metadata.longitude = longitude;
  }

  return metadata;
}

function resolveTimestamp(metadata: MetaData): Date {
  const rawTimestamp =
    typeof metadata.capturedAt === "string"
      ? metadata.capturedAt
      : typeof metadata.date === "string"
        ? metadata.date
        : "";

  if (!rawTimestamp) {
    return new Date();
  }

  // Support Unix timestamps sent as numeric strings (milliseconds or seconds).
  if (/^\d+$/.test(rawTimestamp)) {
    const numericTimestamp = Number(rawTimestamp);
    const timestampInMs =
      rawTimestamp.length <= 10 ? numericTimestamp * 1000 : numericTimestamp;
    const numericParsed = new Date(timestampInMs);

    if (!Number.isNaN(numericParsed.getTime())) {
      return numericParsed;
    }
  }

  const parsed = new Date(rawTimestamp);
  if (Number.isNaN(parsed.getTime())) {
    console.warn("Invalid capture timestamp in metadata, falling back to now", {
      capturedAt: metadata.capturedAt,
      date: metadata.date,
    });
    return new Date();
  }

  return parsed;
}

export async function POST(request: NextRequest) {
  const contentType = request.headers.get("content-type") ?? "";

  const { searchParams } = new URL(request.url);
  const rawAlbumId = searchParams.get("albumId") ?? "";
  const albumId = rawAlbumId.replace(/^"|"$/g, "").trim();

  console.log("Received upload request", {
    method: request.method,
    contentType,
  });

  try {
    if (!contentType.includes("multipart/form-data")) {
      return NextResponse.json(
        {
          message: "Content-Type must be multipart/form-data",
          success: false,
        },
        { status: 400 },
      );
    }

    await connectDB();

    const firebaseUser = await verifyFirebaseUser(request);

    if (!firebaseUser) {
      return NextResponse.json({ error: "Unauthorized" }, { status: 401 });
    }

    const { uid } = firebaseUser;

    let formData: FormData;
    try {
      formData = await request.formData();
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      console.error("Failed to parse multipart form-data:", message);

      return NextResponse.json(
        {
          message:
            "Invalid multipart body. Check boundary/content-type on client.",
          success: false,
          debug: {
            contentType,
            cause: message,
          },
        },
        { status: 400 },
      );
    }

    const imageFile =
      formData.get("image") ??
      formData.get("file") ??
      formData.get("imageFile");
    const metadataRaw =
      formData.get("metadata") ?? formData.get("meta") ?? formData.get("data");

    if (!isUploadedFile(imageFile)) {
      return NextResponse.json(
        { message: "Media file is required", success: false },
        { status: 400 },
      );
    }

    let metadata: MetaData = {};
    if (metadataRaw != null) {
      try {
        if (typeof metadataRaw === "string") {
          metadata = JSON.parse(metadataRaw) as MetaData;
        } else if (isUploadedFile(metadataRaw)) {
          const text = await metadataRaw.text();
          metadata = JSON.parse(text) as MetaData;
        } else {
          throw new Error("Unsupported metadata field type");
        }
      } catch {
        return NextResponse.json(
          { message: "Invalid metadata JSON", success: false },
          { status: 400 },
        );
      }
    } else {
      metadata = parseMetadataFromFormData(formData);
    }

    if (typeof metadata !== "object" || metadata === null) {
      return NextResponse.json(
        {
          message: "Metadata must be a valid JSON object",
          success: false,
        },
        { status: 400 },
      );
    }

    const normalizedMetadata: MetaData = {
      ...metadata,
      name: typeof metadata.name === "string" ? metadata.name : imageFile.name,
      type: imageFile.type.startsWith("video/") ? "video" : "image",
    };

    if (
      !imageFile.type.startsWith("image/") &&
      !imageFile.type.startsWith("video/")
    ) {
      return NextResponse.json(
        {
          message: "Invalid file format. Only images and videos are allowed.",
          success: false,
        },
        { status: 400 },
      );
    }

    let selectedAlbum = await Album.findOne({ name: "Recent", userId: uid });
    if (albumId) {
      const foundAlbum = await Album.findOne({
        _id: albumId,
        userId: uid,
      }).exec();
      if (!foundAlbum) {
        console.error("Album not found for user", { albumId, userId: uid });
        return NextResponse.json(
          { message: "Album not found for user", success: false },
          { status: 404 },
        );
      }
      selectedAlbum = foundAlbum;
    } else if (!selectedAlbum) {
      selectedAlbum = await Album.create({ name: "Recent", userId: uid });
    }

    if (!selectedAlbum || !selectedAlbum._id) {
      console.error("Failed to create or find Recent album", { selectedAlbum });
      return NextResponse.json(
        { message: "Failed to create album", success: false },
        { status: 500 },
      );
    }

    console.log("Album resolved:", { albumId: selectedAlbum._id, userId: uid });

    console.log("Album resolved:", { albumId: selectedAlbum._id, userId: uid });

    const imageSize = imageFile.size;

    // 2 - Save the file in minio and get the storage path
    const arrayBuffer = await imageFile.arrayBuffer();
    const buffer = Buffer.from(arrayBuffer);
    const minioResponse = await uploadFile(
      "user-newminiouser-8aa9e5bc-ef69-4c49-b2bc-f3538cd5bb44-bucket",
      buffer,
      imageFile.name,
    );

    console.log("File uploaded to Minio successfully", {
      filename: imageFile.name,
      mimeType: imageFile.type,
      size: imageSize,
      metadata: normalizedMetadata,
      minioResponse,
    });

    const timestamp = resolveTimestamp(normalizedMetadata);

    // check the storage
    // check there is a already a storage record for the user
    const existingStorage = await Storage.findOne({
      userId: uid || "unknown",
    });

    if (existingStorage) {
      // get the plan storage limit from Plan collection
      const userPlan = await Plan.findById(existingStorage.planId).lean();

      if (!userPlan) {
        console.warn("Plan not found for storage record", {
          storageId: existingStorage._id,
          planId: existingStorage.planId,
        });
        return NextResponse.json(
          {
            message: "Plan not found for storage record",
            success: false,
          },
          { status: 404 },
        );
      }

      if (existingStorage.usedStorage + imageSize > userPlan.storageLimit) {
        return NextResponse.json(
          {
            message: "Storage limit exceeded. Please upgrade your plan.",
            success: false,
          },
          { status: 403 },
        );
      } else {
        // update the used storage
        existingStorage.usedStorage += imageSize;
        await existingStorage.save();
      }
    } else {
      // get free plan id
      const freePlan = await Plan.findOne({ name: "Free" });
      const planId = freePlan ? freePlan._id : null;

      await Storage.create({
        userId: uid || "unknown",
        planId: planId!,
        usedStorage: imageSize,
      });
    }

    // save image details to db
    const createdImage = await Image.create({
      userId: uid || "unknown",
      imageId: minioResponse.etag,
      location: {
        latitude: normalizedMetadata.latitude,
        longitude: normalizedMetadata.longitude,
      },
      size: imageSize,
      metadata: Object.entries(normalizedMetadata).map(([key, value]) => ({
        name: key,
        type: typeof value,
      })),
      timestamp,
      albumId: selectedAlbum._id,
    });

    if (!createdImage.albumId) {
      console.error("Image saved without albumId", {
        imageId: createdImage._id,
        userId: createdImage.userId,
      });
      return NextResponse.json(
        {
          message: "Image saved without albumId",
          success: false,
        },
        { status: 500 },
      );
    }

    // update storage
    return NextResponse.json(
      {
        message: "File and metadata received successfully",
        success: true,
        data: {
          albumId: createdImage.albumId,
          filename: imageFile.name,
          mimeType: imageFile.type,
          size: imageFile.size,
          metadata: normalizedMetadata,
        },
      },
      { status: 200 },
    );
  } catch (error) {
    console.error("Error processing upload request:", error);

    const message =
      error instanceof Error
        ? error.message
        : "Failed to process upload request";

    return NextResponse.json(
      {
        message,
        success: false,
        debug: {
          contentType,
        },
      },
      { status: 500 },
    );
  }
}
