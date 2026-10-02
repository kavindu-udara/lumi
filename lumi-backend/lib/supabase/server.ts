import { createClient } from "@supabase/supabase-js";
import type { NextRequest } from "next/server";

const getSupabaseConfig = () => {
  const url = process.env.NEXT_PUBLIC_SUPABASE_URL;
  const publishableKey = process.env.SUPABASE_PUBLISHABLE_KEY;

  if (!url || !publishableKey) {
    throw new Error(
      "NEXT_PUBLIC_SUPABASE_URL and SUPABASE_PUBLISHABLE_KEY are required",
    );
  }

  return { url, publishableKey };
};

export const createSupabaseServerClient = (request?: NextRequest) => {
  const { url, publishableKey } = getSupabaseConfig();
  const authorization = request?.headers.get("authorization");

  return createClient(url, publishableKey, {
    auth: { autoRefreshToken: false, persistSession: false },
    global: authorization ? { headers: { Authorization: authorization } } : undefined,
  });
};

export const createSupabaseAdminClient = () => {
  const url = process.env.NEXT_PUBLIC_SUPABASE_URL;
  const secretKey = process.env.SUPABASE_SECRET_KEY;

  if (!url || !secretKey) {
    throw new Error(
      "NEXT_PUBLIC_SUPABASE_URL and SUPABASE_SECRET_KEY are required",
    );
  }

  return createClient(url, secretKey, {
    auth: { autoRefreshToken: false, persistSession: false },
  });
};
