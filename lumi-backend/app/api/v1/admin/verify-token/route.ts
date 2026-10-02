import { NextRequest, NextResponse } from "next/server";
import { getAdminFromRequest } from "@/lib/admin-guard";

export const runtime = "nodejs";

export async function GET(request: NextRequest) {
  try {
    const admin = await getAdminFromRequest(request);
    if (!admin) return NextResponse.json({ error: "Invalid or expired token" }, { status: 401 });
    return NextResponse.json({
      message: "Token is valid",
      admin: { adminId: admin.id, username: admin.email },
    });
  } catch (error) {
    console.error("Verify token error:", error);
    return NextResponse.json({ error: "Internal server error" }, { status: 500 });
  }
}
