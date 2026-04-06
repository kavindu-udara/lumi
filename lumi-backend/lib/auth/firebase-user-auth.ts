import { NextRequest } from "next/server";
import { firebaseAdminAuth } from "@/lib/firebase";

export type VerifiedFirebaseUser = {
  uid: string;
  email?: string;
  name?: string;
  picture?: string;
};

export class AuthError extends Error {
  status: number;
  constructor(message: string, status = 401) {
    super(message);
    this.status = status;
  }
}

export async function verifyFirebaseUser(
  request: NextRequest,
): Promise<VerifiedFirebaseUser> {
  const authHeader = request.headers.get("authorization") || "";
  const idToken = authHeader.match(/^Bearer\s+(.+)$/i)?.[1]?.trim();

  if (!idToken) {
    throw new AuthError("Missing Bearer token", 401);
  }

  try {
    const decoded = await firebaseAdminAuth.verifyIdToken(idToken, true);
    return {
      uid: decoded.uid,
      email: decoded.email,
      name: decoded.name,
      picture: decoded.picture,
    };
  } catch {
    throw new AuthError("Invalid or expired Firebase token", 401);
  }
}
