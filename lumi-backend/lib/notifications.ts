import { createSupabaseAdminClient } from "@/lib/supabase/server";

const MAX_RECIPIENTS = 10000;
const INSERT_BATCH_SIZE = 500;

export type BroadcastInput = {
  title: string;
  body: string;
  data?: Record<string, unknown>;
  idempotencyKey?: string;
};

export class NotificationValidationError extends Error {}

export const validateBroadcastInput = (input: unknown): BroadcastInput => {
  if (!input || typeof input !== "object") {
    throw new NotificationValidationError("A notification object is required");
  }

  const value = input as Record<string, unknown>;
  const title = typeof value.title === "string" ? value.title.trim() : "";
  const body = typeof value.body === "string" ? value.body.trim() : "";
  const idempotencyKey =
    typeof value.idempotencyKey === "string" ? value.idempotencyKey.trim() : undefined;
  const data = value.data === undefined ? {} : value.data;

  if (!title || title.length > 120) {
    throw new NotificationValidationError("title is required and must be 120 characters or fewer");
  }
  if (!body || body.length > 2000) {
    throw new NotificationValidationError("body is required and must be 2000 characters or fewer");
  }
  if (!data || typeof data !== "object" || Array.isArray(data)) {
    throw new NotificationValidationError("data must be a JSON object");
  }
  if (JSON.stringify(data).length > 4096) {
    throw new NotificationValidationError("data must be 4096 characters or fewer");
  }
  const notificationData = data as Record<string, unknown>;
  const dataKeys = Object.keys(notificationData);
  if (dataKeys.some((key) => key !== "deepLink")) {
    throw new NotificationValidationError("data may only contain the deepLink key");
  }
  if (notificationData.deepLink !== undefined &&
      (typeof notificationData.deepLink !== "string" ||
        notificationData.deepLink.length > 500 ||
        !notificationData.deepLink.startsWith("lumi://"))) {
    throw new NotificationValidationError("deepLink must be a lumi:// URL of 500 characters or fewer");
  }
  if (idempotencyKey && (idempotencyKey.length < 1 || idempotencyKey.length > 100)) {
    throw new NotificationValidationError("idempotencyKey must be 100 characters or fewer");
  }

  return { title, body, data: notificationData, idempotencyKey };
};

export async function createBroadcast(
  senderAdminId: string,
  input: BroadcastInput,
) {
  const supabase = createSupabaseAdminClient();
  const { data: existing, error: existingError } = input.idempotencyKey
    ? await supabase
        .from("broadcast_notifications")
        .select("*")
        .eq("sender_admin_id", senderAdminId)
        .eq("idempotency_key", input.idempotencyKey)
        .maybeSingle()
    : { data: null, error: null };

  if (existingError) throw existingError;
  if (existing) return { broadcast: existing, duplicate: true };

  const { data: broadcast, error: broadcastError } = await supabase
    .from("broadcast_notifications")
    .insert({
      sender_admin_id: senderAdminId,
      title: input.title,
      body: input.body,
      data: input.data ?? {},
      idempotency_key: input.idempotencyKey ?? null,
      status: "sending",
    })
    .select("*")
    .single();

  if (broadcastError) {
    if (broadcastError.code === "23505" && input.idempotencyKey) {
      const { data: duplicate, error: duplicateError } = await supabase
        .from("broadcast_notifications")
        .select("*")
        .eq("sender_admin_id", senderAdminId)
        .eq("idempotency_key", input.idempotencyKey)
        .single();
      if (duplicateError || !duplicate) throw duplicateError ?? broadcastError;
      return { broadcast: duplicate, duplicate: true };
    }
    throw broadcastError;
  }
  if (!broadcast) throw new Error("Failed to create broadcast");

  try {
    const users: string[] = [];
    for (let page = 1; users.length < MAX_RECIPIENTS; page += 1) {
      const { data, error } = await supabase.auth.admin.listUsers({
        page,
        perPage: 1000,
      });
      if (error) throw error;
      const pageUsers = data.users
        .filter((user) => !user.banned_until && user.last_sign_in_at)
        .map((user) => user.id);
      users.push(...pageUsers);
      if (data.users.length < 1000) break;
    }

    if (users.length >= MAX_RECIPIENTS) {
      throw new Error(`Broadcast audience exceeds the ${MAX_RECIPIENTS} user limit`);
    }

    for (let index = 0; index < users.length; index += INSERT_BATCH_SIZE) {
      const rows = users.slice(index, index + INSERT_BATCH_SIZE).map((userId) => ({
        broadcast_id: broadcast.id,
        user_id: userId,
      }));
      if (rows.length === 0) continue;
      const { error } = await supabase
        .from("user_notifications")
        .upsert(rows, { onConflict: "broadcast_id,user_id", ignoreDuplicates: true });
      if (error) throw error;
    }

    const { data: updated, error: updateError } = await supabase
      .from("broadcast_notifications")
      .update({
        status: "sent",
        recipient_count: users.length,
        delivered_count: users.length,
        failure_count: 0,
      })
      .eq("id", broadcast.id)
      .select("*")
      .single();
    if (updateError || !updated) throw updateError ?? new Error("Failed to finalize broadcast");
    return { broadcast: updated, duplicate: false };
  } catch (error) {
    await supabase
      .from("broadcast_notifications")
      .update({
        status: "failed",
        error_message: error instanceof Error ? error.message.slice(0, 500) : "Fan-out failed",
      })
      .eq("id", broadcast.id);
    throw error;
  }
}
