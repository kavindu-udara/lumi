import { NextRequest } from "next/server";
import { createClient } from "@supabase/supabase-js";

export type AdminUser = {
  id: string;
  email?: string;
};

const getToken = (request: NextRequest) => {
  const header = request.headers.get("authorization") ?? "";
  return header.match(/^Bearer\s+(.+)$/i)?.[1]?.trim() ?? request.cookies.get("adminToken")?.value;
};

export async function getAdminFromRequest(request: NextRequest): Promise<AdminUser | null> {
  const token = getToken(request);
  const url = process.env.NEXT_PUBLIC_SUPABASE_URL;
  const publishableKey = process.env.SUPABASE_PUBLISHABLE_KEY;
  if (!token || !url || !publishableKey) return null;

  const supabase = createClient(url, publishableKey, {
    auth: { autoRefreshToken: false, persistSession: false },
    global: { headers: { Authorization: `Bearer ${token}` } },
  });
  const { data: authData, error: authError } = await supabase.auth.getUser();
  if (authError || !authData.user) return null;

  const { data: role, error: roleError } = await supabase
    .from("user_roles")
    .select("role")
    .eq("user_id", authData.user.id)
    .eq("role", "admin")
    .maybeSingle();
  if (roleError || !role) return null;

  return { id: authData.user.id, email: authData.user.email };
}
