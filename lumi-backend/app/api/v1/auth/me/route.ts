import { NextResponse } from "next/server";
import { NextRequest } from "next/server";
import prisma from "@/lib/db";
import { getRequestAuthUser } from "@/lib/auth";

export async function GET(request: NextRequest) {
  try {
    const authUser = await getRequestAuthUser(request);

    if (!authUser?.email) {
      return NextResponse.json(
        { error: "Not authenticated" },
        { status: 401 }
      );
    }

    // Get full user data including provider
    const user = await prisma.user.findUnique({
      where: { email: authUser.email },
      select: {
        id: true,
        email: true,
        name: true,
        provider: true,
        role: true,
        emailVerified: true,
      },
    });

    if (!user) {
      return NextResponse.json(
        { error: "User not found" },
        { status: 404 }
      );
    }

    return NextResponse.json({
      user,
      isSignedIn: true,
      provider: user.provider, // Returns "GOOGLE" or "CREDENTIALS"
    });
  } catch (error) {
    console.error("Session error:", error);
    return NextResponse.json(
      { error: "Internal server error" },
      { status: 500 }
    );
  }
}
