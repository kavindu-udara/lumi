import { NextRequest, NextResponse } from "next/server";
import { findUserByEmail } from "@/actions/user-actions";
import bcrypt from "bcrypt";
import { Provider } from "@prisma/client";
import jwt from "jsonwebtoken";
import { JWT_SECRET } from "@/lib/auth";

export async function POST(request: NextRequest) {
  try {
    const body = await request.json();
    const { email, password } = body;

    // Validation
    if (!email || !password) {
      return NextResponse.json(
        { message: "Email and password are required", success : false },
        { status: 400 }
      );
    }

    // Find user
    const user = await findUserByEmail(email);

    console.log("User found:", user);
    console.log("User provider:", user?.provider);
    console.log("Provider.CREDENTIALS:", Provider.CREDENTIALS);
    console.log("Match:", user?.provider === Provider.CREDENTIALS);

    if (!user || user.provider !== Provider.CREDENTIALS) {
      return NextResponse.json(
        { message: "Invalid email or password", success : false },
        { status: 401 }
      );
    }

    // Verify password
    const isPasswordValid = await bcrypt.compare(password, user.password!);

    if (!isPasswordValid) {
      return NextResponse.json(
        { message: "Invalid email or password", success : false },
        { status: 401 }
      );
    }

    // Create JWT token for mobile apps
    const token = jwt.sign(
      {
        id: user.id,
        email: user.email,
        name: user.name,
        provider: user.provider,
      },
      JWT_SECRET,
      { expiresIn: "30d" }
    );

    return NextResponse.json(
      {
        message: "Login successful",
        success: true,
        token,
        user: {
          id: user.id,
          email: user.email,
          name: user.name,
          provider: user.provider,
        },
      },
      { status: 200 }
    );
  } catch (error) {
    console.error("Login error:", error);
    return NextResponse.json(
      { message: "Internal server error", success : false },
      { status: 500 }
    );
  }
}
