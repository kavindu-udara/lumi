import { getAuthenticatedUser, AuthError } from "@/lib/auth/supabase-user-auth";
import { createSupabaseServerClient } from "@/lib/supabase/server";
import { NextRequest } from "next/server";

export async function GET(request: NextRequest) {
    try {

        const user = await getAuthenticatedUser(request);
        const supabase = createSupabaseServerClient(request);
        const { data: albums, error } = await supabase
            .from("albums")
            .select("id, user_id, name, description, cover_photo_path, created_at, updated_at, album_shares(id, created_at, revoked_at)")
            .eq("user_id", user.id)
            .is("album_shares.revoked_at", null)
            .order("created_at", { ascending: true });

        if (error) throw error;
        return Response.json({ albums }, { status: 200 });
    } catch (error) {
        if (error instanceof AuthError) {
            return Response.json({ error: error.message }, { status: error.status });
        }
        console.error("Error fetching albums:", error instanceof Error ? error.message : String(error));
        return Response.json({ error: "Failed to fetch albums" }, { status: 500 });
    }
}

export async function POST(request: NextRequest) {
    try {
       
        const user = await getAuthenticatedUser(request);
        const supabase = createSupabaseServerClient(request);

        // create an new album for the user with the given uid
        const { name } = await request.json();
        const normalizedName = typeof name === "string" ? name.trim() : "";
        if (!normalizedName) {
            return Response.json({ error: "Missing album name" }, { status: 400 });
        }
        if (normalizedName.length > 120) {
            return Response.json({ error: "Album name must be 120 characters or fewer" }, { status: 400 });
        }

        const { data: newAlbum, error } = await supabase
            .from("albums")
            .insert({ user_id: user.id, name: normalizedName })
            .select("id, user_id, name, description, cover_photo_path, created_at, updated_at, album_shares(id, created_at, revoked_at)")
            .single();

        if (error) {
            if (error.code === "23505") {
                return Response.json({ error: "Album with the same name already exists" }, { status: 400 });
            }
            throw error;
        }

        return Response.json({ album: newAlbum }, { status: 201 });

    } catch (error) {
        if (error instanceof AuthError) {
            return Response.json({ error: error.message }, { status: error.status });
        }
        console.error("Error parsing parameters:", error instanceof Error ? error.message : String(error));
        return Response.json({ error: "Invalid parameters" }, { status: 400 });
    }
}
