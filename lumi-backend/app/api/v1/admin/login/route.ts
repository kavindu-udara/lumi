import { NextRequest, NextResponse } from "next/server";
import bcrypt from "bcrypt";
import Admin from "@/models/admin.model";
import connectDB from "@/lib/db";
import { generateToken } from "@/lib/jwt";

export const runtime = "nodejs";

interface LoginRequest {
  username: string;
  password: string;
}

export async function POST(request: NextRequest) {
  try {
    // Connect to database
    await connectDB();

    // Parse request body
    const body: LoginRequest = await request.json();
    const username = body.username?.trim();
    const password = body.password;

    // Validate input
    if (!username || !password) {
      return NextResponse.json(
        { error: "Username and password are required" },
        { status: 400 }
      );
    }

    // Find admin by username
    const admin = await Admin.findOne({ username });

    if (!admin) {
      return NextResponse.json(
        { error: "Invalid username or password" },
        { status: 401 }
      );
    }

    // Compare passwords (supports legacy plain-text records and upgrades them)
    const storedPassword = String(admin.password);
    const isBcryptHash = storedPassword.startsWith("$2a$")
      || storedPassword.startsWith("$2b$")
      || storedPassword.startsWith("$2y$");

    let isPasswordValid = false;

    if (isBcryptHash) {
      isPasswordValid = await bcrypt.compare(password, storedPassword);
    } else {
      isPasswordValid = password === storedPassword;
      if (isPasswordValid) {
        const hashedPassword = await bcrypt.hash(password, 10);
        admin.password = hashedPassword;
        await admin.save();
      }
    }

    if (!isPasswordValid) {
      return NextResponse.json(
        { error: "Invalid username or password" },
        { status: 401 }
      );
    }

    // Generate JWT token
    const token = generateToken({
      adminId: admin._id.toString(),
      username: admin.username,
    });

    const response = NextResponse.json(
      {
        message: "Login successful",
        token,
        admin: {
          id: admin._id.toString(),
          username: admin.username,
        },
      },
      { status: 200 }
    );

    response.cookies.set("adminToken", token, {
      httpOnly: true,
      secure: process.env.NODE_ENV === "production",
      sameSite: "lax",
      path: "/",
      maxAge: 60 * 60 * 24 * 7,
    });

    return response;
  } catch (error) {
    console.error("Login error:", error);
    return NextResponse.json(
      { error: "Internal server error" },
      { status: 500 }
    );
  }
}
