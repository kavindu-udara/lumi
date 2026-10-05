import "dotenv/config";
import { createClient } from "@supabase/supabase-js";

const required = (name) => {
  const value = process.env[name]?.trim();
  if (!value) throw new Error(`${name} is required`);
  return value;
};

const email = required("ADMIN_EMAIL").toLowerCase();
const password = required("ADMIN_PASSWORD");
const displayName = process.env.ADMIN_DISPLAY_NAME?.trim() || email.split("@")[0];

if (!email.includes("@") || email.length > 254) {
  throw new Error("ADMIN_EMAIL must be a valid email address");
}
if (password.length < 12) {
  throw new Error("ADMIN_PASSWORD must be at least 12 characters");
}

const supabase = createClient(
  required("NEXT_PUBLIC_SUPABASE_URL"),
  required("SUPABASE_SECRET_KEY"),
  { auth: { autoRefreshToken: false, persistSession: false } },
);

const findUserByEmail = async () => {
  for (let page = 1; ; page += 1) {
    const { data, error } = await supabase.auth.admin.listUsers({ page, perPage: 1000 });
    if (error) throw error;
    const user = data.users.find((candidate) => candidate.email?.toLowerCase() === email);
    if (user) return user;
    if (data.users.length < 1000) return null;
  }
};

const existingUser = await findUserByEmail();
let user = existingUser;

if (!user) {
  const { data, error } = await supabase.auth.admin.createUser({
    email,
    password,
    email_confirm: true,
    user_metadata: { full_name: displayName },
  });
  if (error || !data.user) throw error ?? new Error("Supabase did not return the created user");
  user = data.user;
  console.log(`Created Supabase user ${user.id}`);
} else {
  console.log(`Using existing Supabase user ${user.id}`);
}

const { error: roleError } = await supabase
  .from("user_roles")
  .upsert({ user_id: user.id, role: "admin" }, { onConflict: "user_id" });
if (roleError) throw roleError;

const { error: profileError } = await supabase
  .from("profiles")
  .upsert({ id: user.id, display_name: displayName }, { onConflict: "id" });
if (profileError) throw profileError;

console.log(`Admin role granted to ${email}`);
console.log("The password is not printed. Sign in at /login.");
