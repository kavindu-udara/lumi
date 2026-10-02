import type { NextRequest } from "next/server";
import { createSupabaseServerClient } from "@/lib/supabase/server";

export type AuthenticatedUser = {
  id: string;
  email?: string;
  name?: string;
};

export class AuthError extends Error {
  status: number;

  constructor(message: string, status = 401) {
    super(message);
    this.status = status;
  }
}

export async function getAuthenticatedUser(
  request: NextRequest,
): Promise<AuthenticatedUser> {
  const authorization = request.headers.get("authorization") || "";
  const token = authorization.match(/^Bearer\s+(.+)$/i)?.[1]?.trim();
  if (!token) {
    throw new AuthError("Missing Bearer token");
  }

  const supabase = createSupabaseServerClient(request);
  const { data, error } = await supabase.auth.getUser(token);

  if (error || !data.user) {
    console.warn("Supabase authentication failed", {
      status: error?.status,
      code: error?.code,
      message: error?.message,
    });
    throw new AuthError("Invalid or expired Supabase token");
  }

  return {
    id: data.user.id,
    email: data.user.email,
    name:
      data.user.user_metadata?.full_name ??
      data.user.user_metadata?.name,
  };
}
