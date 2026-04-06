import { NextRequest } from "next/server";
import { TokenPayload, verifyToken } from "@/lib/jwt";

export const getAdminFromRequest = (request: NextRequest): TokenPayload | null => {
  const cookieToken = request.cookies.get("adminToken")?.value;
  const authHeader = request.headers.get("authorization") || "";
  const bearerToken = authHeader.match(/^Bearer\s+(.+)$/i)?.[1]?.trim();

  const token = cookieToken || bearerToken;
  if (!token) {
    return null;
  }

  return verifyToken(token);
};
