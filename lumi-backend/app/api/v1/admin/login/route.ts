import { NextRequest, NextResponse } from "next/server";
import { createClient } from "@supabase/supabase-js";

export const runtime = "nodejs";

type LoginRequest = {
  email?: string;
  username?: string;
  password?: string;
};

export async function POST(request: NextRequest) {
  try {
    const url = process.env.NEXT_PUBLIC_SUPABASE_URL;
    const publishableKey = process.env.SUPABASE_PUBLISHABLE_KEY;
    if (!url || !publishableKey) throw new Error("Supabase environment is not configured");

    const body = await request.json() as LoginRequest;
    const email = String(body.email ?? body.username ?? "").trim();
    const password = body.password;
    if (!email || !password) {
      return NextResponse.json({ error: "Email and password are required" }, { status: 400 });
    }

    const supabase = createClient(url, publishableKey, {
      auth: { autoRefreshToken: false, persistSession: false },
    });
    const { data, error } = await supabase.auth.signInWithPassword({ email, password });
    if (error || !data.user || !data.session) {
      return NextResponse.json({ error: "Invalid email or password" }, { status: 401 });
    }

    const { data: role, error: roleError } = await supabase
      .from("user_roles")
      .select("role")
      .eq("user_id", data.user.id)
      .eq("role", "admin")
      .maybeSingle();
    if (roleError || !role) {
      await supabase.auth.signOut();
      return NextResponse.json({ error: "Admin access is required" }, { status: 403 });
    }

    const response = NextResponse.json({
      message: "Login successful",
      token: data.session.access_token,
      admin: { id: data.user.id, username: data.user.email ?? email },
    });
    response.cookies.set("adminToken", data.session.access_token, {
      httpOnly: true,
      secure: process.env.NODE_ENV === "production",
      sameSite: "lax",
      path: "/",
      maxAge: data.session.expires_in,
    });
    return response;
  } catch (error) {
    console.error("Login error:", error);
    return NextResponse.json({ error: "Internal server error" }, { status: 500 });
  }
}
