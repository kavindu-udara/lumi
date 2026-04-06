import { verifyFirebaseUser } from "@/lib/auth/firebase-user-auth";
import connectDB from "@/lib/db";
import { minioClient } from "@/lib/minio-client";
import Album from "@/models/album.model";
import Image from "@/models/image.model";
import Storage from "@/models/storage.model";
import { NextRequest } from "next/server";

export async function GET(request: NextRequest) {
  try {
    await connectDB();

    const firebaseUser = await verifyFirebaseUser(request);

    if (!firebaseUser) {
      return Response.json({ error: "Unauthorized" }, { status: 401 });
    }

    const { searchParams } = new URL(request.url);
    const rawAlbumId = searchParams.get("albumId") ?? "";
    const albumId = rawAlbumId.replace(/^"|"$/g, "").trim();
    const isAllAlbums = albumId.toLowerCase() === "all";

    const { uid } = firebaseUser;

    let photos;
    if (isAllAlbums) {
      photos = await Image.find({ userId: uid }).exec();
    } else {
      let foundAlbum;

      if (albumId) {
        foundAlbum = await Album.findOne({ _id: albumId, userId: uid }).exec();
        if (!foundAlbum) {
          return new Response(
            JSON.stringify({ error: "Album not found for user" }),
            {
              status: 404,
              headers: { "Content-Type": "application/json" },
            },
          );
        }
      } else {
        foundAlbum = await Album.findOne({
          name: "Recent",
          userId: uid,
        }).exec();
        if (!foundAlbum) {
          foundAlbum = await Album.create({ name: "Recent", userId: uid });
        }
      }

      photos = await Image.find({
        userId: uid,
        albumId: foundAlbum._id,
      }).exec();
    }

    if (!photos) {
      return new Response(
        JSON.stringify({ error: "No photos found for user" }),
        {
          status: 404,
          headers: { "Content-Type": "application/json" },
        },
      );
    }

    return new Response(JSON.stringify(photos), {
      headers: {
        "Content-Type": "application/json",
      },
    });
  } catch (error) {
    console.error("Error fetching photos:", error);
    return new Response("Internal Server Error", { status: 500 });
  }
}

export async function DELETE(request: NextRequest) {
  const { photoId } = await request.json();

  if (!photoId) {
    return Response.json({ error: "Missing photoId parameter" }, { status: 400 });
  }

  try {
    await connectDB();

    const firebaseUser = await verifyFirebaseUser(request);
    if (!firebaseUser) {
      return Response.json({ error: "Unauthorized" }, { status: 401 });
    }

    const {uid} = firebaseUser;

    // find the photo in db
    const image = await Image.findOne({ imageId: photoId, userId : uid  }).exec();

    if (!image) {
      return Response.json({ error: "Photo not found for user" }, { status: 404 });
    }

    const result = await Image.deleteOne({ imageId: photoId, userId : uid }).exec();

    if (result.deletedCount === 0) {
      return Response.json({ error: "Failed to delete photo" }, { status: 500 });
    }

    // reduce storage usage for the user in the database (optional, depending on your implementation)
    await Storage.findOneAndUpdate(
      { userId : uid },
      { $inc: { usedStorage: -image.size } },
    ).exec();

    // delete from minio
    await minioClient.removeObject(
      "user-newminiouser-8aa9e5bc-ef69-4c49-b2bc-f3538cd5bb44-bucket",
      photoId,
    );

    return Response.json({ message: "Photo deleted successfully" }, { status: 200 });
  } catch (error) {
    console.error("Error deleting photo:", error);
    return new Response("Internal Server Error", { status: 500 });
  }
}
