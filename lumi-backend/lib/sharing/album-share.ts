import {
  createHash,
  createHmac,
  randomBytes,
  timingSafeEqual,
} from "node:crypto";

const getShareTokenSecret = () => {
  const secret = process.env.SHARE_TOKEN_SECRET;
  if (!secret) {
    throw new Error("SHARE_TOKEN_SECRET is required");
  }
  return secret;
};

const base64Url = (value: Buffer) => value.toString("base64url");

export const hashShareToken = (token: string) =>
  createHash("sha256").update(token, "utf8").digest("hex");

export const createShareToken = (shareId: string) => {
  const signature = createHmac("sha256", getShareTokenSecret())
    .update(shareId, "utf8")
    .digest();
  return `${base64Url(Buffer.from(shareId, "utf8"))}.${base64Url(signature)}`;
};

export const verifyShareToken = (token: string) => {
  const [encodedId, encodedSignature] = token.split(".");
  if (!encodedId || !encodedSignature) return null;

  let shareId: string;
  let providedSignature: Buffer;
  try {
    shareId = Buffer.from(encodedId, "base64url").toString("utf8");
    providedSignature = Buffer.from(encodedSignature, "base64url");
  } catch {
    return null;
  }

  if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(shareId)) {
    return null;
  }

  const expectedSignature = createHmac("sha256", getShareTokenSecret())
    .update(shareId, "utf8")
    .digest();
  if (
    providedSignature.length !== expectedSignature.length ||
    !timingSafeEqual(providedSignature, expectedSignature)
  ) {
    return null;
  }

  return shareId;
};

export const createShareId = () => {
  const bytes = randomBytes(16);
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = bytes.toString("hex");
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
};

export const getShareUrl = (token: string) => {
  const origin = process.env.PUBLIC_APP_URL?.trim().replace(/\/+$/, "");
  if (!origin) throw new Error("PUBLIC_APP_URL is required");
  return `${origin}/shared/${encodeURIComponent(token)}`;
};
