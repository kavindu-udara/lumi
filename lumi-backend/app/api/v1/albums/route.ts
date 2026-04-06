import { verifyFirebaseUser } from "@/lib/auth/firebase-user-auth";
import connectDB from "@/lib/db";
import Album from "@/models/album.model";
import { NextRequest } from "next/server";

export async function GET(request: NextRequest) {
    try {

        await connectDB();
        
        const firebaseUser = await verifyFirebaseUser(request);

        if (!firebaseUser) {
            return Response.json({ error: "Unauthorized" }, { status: 401 });
        }

        const albums = await Album.find({ userId: firebaseUser.uid }).exec();
        return Response.json({ albums }, { status: 200 });
    } catch (error) {
        console.error("Error fetching albums:", error instanceof Error ? error.message : String(error));
        return Response.json({ error: "Failed to fetch albums" }, { status: 500 });
    }
}

export async function POST(request: NextRequest) {
    try {
       
        await connectDB(); 

        const firebaseUser = await verifyFirebaseUser(request);

        if(!firebaseUser){
            return Response.json({ error: "Unauthorized" }, { status: 401 });
        }

        // create an new album for the user with the given uid
        const {name} = await request.json();
        if(!name){
            return Response.json({ error: "Missing album name" }, { status: 400 });
        }

        const { uid } = firebaseUser;

        // check is the album already exists for the user
        const existingAlbum = await Album.findOne({ userId: uid, name }).exec();
        if(existingAlbum){
            return Response.json({ error: "Album with the same name already exists" }, { status: 400 });
        }

        const newAlbum = new Album({
            userId: uid,
            name,
            photos: [],
        });

        await newAlbum.save();

        return Response.json({ album: newAlbum }, { status: 201 });

    } catch (error) {
        console.error("Error parsing parameters:", error instanceof Error ? error.message : String(error));
        return Response.json({ error: "Invalid parameters" }, { status: 400 });
    }
}
