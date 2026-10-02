# Supabase Migration Plan

This project currently depends on three separate persistence/authentication concerns:

- MongoDB/Mongoose for `plans`, `albums`, `images`, `storage`, `subscriptions`, and `admins`.
- MinIO for photo/video objects.
- Firebase bearer tokens for user APIs plus a custom MongoDB/JWT flow for admin APIs.

The target architecture is Supabase Auth, Supabase Postgres, and Supabase Storage. Stripe remains the billing provider.

## Goals

1. Replace Firebase user authentication and the custom admin JWT with Supabase Auth sessions/tokens.
2. Replace MongoDB collections and Mongoose queries with relational Postgres tables and the Supabase server client.
3. Replace MinIO buckets with a private Supabase Storage bucket.
4. Preserve current API behavior where practical so the frontend migration is incremental.
5. Enforce ownership in the database with Row Level Security (RLS), not only in route handlers.
6. Keep a reversible migration path until users, metadata, and objects have been verified.

## Target Architecture

### Authentication

- Use Supabase Auth for all end users and administrators.
- Use the Supabase browser client for sign-in/sign-out and session refresh.
- Use the Supabase server client in Next.js route handlers so cookies and access tokens are handled consistently.
- Use `supabase.auth.getUser()` for request authentication; do not decode or trust client-supplied JWT payloads.
- Represent admin authorization with an `is_admin` flag or role in `public.profiles`/`public.user_roles`, and enforce it server-side and with RLS.
- Do not store passwords in application tables. Supabase Auth owns password hashes and password reset flows.
- Remove Firebase Admin verification, `lib/jwt.ts`, the MongoDB `admins` model, and `adminToken`/`adminUser` local-storage authentication after cutover.

### Database

Create a Supabase migration in `supabase/migrations/` with UUID primary keys and foreign keys to `auth.users(id)`:

| Current model | Supabase table | Main columns and constraints |
| --- | --- | --- |
| `plans` | `public.plans` | `id`, `name` unique, `storage_limit_bytes`, `price`, timestamps |
| `albums` | `public.albums` | `id`, `user_id`, `name`, `description`, `cover_photo_path`, timestamps; unique `(user_id, name)` |
| `images` | `public.images` | `id`, `user_id`, `album_id`, `storage_path`, `original_name`, `mime_type`, `size_bytes`, `latitude`, `longitude`, `metadata jsonb`, `captured_at`, timestamps |
| `storage` | `public.user_storage` | `user_id` primary key, `plan_id`, `used_bytes`, timestamps |
| `subscriptions` | `public.subscriptions` | `id`, `user_id`, `plan_id`, Stripe identifiers, `start_date`, `end_date`, timestamps |
| `admins` | Auth user plus role | Auth identity and an admin role; no application password column |

Recommended relational rules:

- `user_id` columns reference `auth.users(id)` with explicit delete policies. Prefer `ON DELETE CASCADE` for albums/images/user storage and a deliberate retention policy for billing records.
- `images.album_id` references `albums.id`.
- `plans` are seeded before subscriptions and storage rows.
- Use `jsonb` for variable metadata, but keep searchable fields as typed columns.
- Store the Storage object path in `images.storage_path`; do not use an object ETag as the durable application identifier.
- Add indexes on `(user_id, name)`, `(user_id, album_id, captured_at)`, `(user_id, created_at)`, and subscription lookup by `user_id`.
- Make storage accounting updates transactional. A database function or RPC should validate the plan limit and increment/decrement `used_bytes` atomically.

### Object Storage

- Create one private bucket, for example `photos`.
- Use paths such as `{user_id}/{album_id}/{image_id}-{safe_file_name}`. Generate paths on the server.
- Add Storage RLS policies allowing a user to access only objects whose first path segment is their Auth user ID.
- Generate signed download URLs for private media, or proxy downloads through an authenticated route when additional checks are needed.
- Upload the object and insert its metadata as one workflow. If metadata insertion fails, delete the newly uploaded object; record cleanup failures for reconciliation.
- Enforce MIME type and size limits in the API and bucket configuration.

## Migration Phases

### Phase 0: Inventory and safety baseline

- Freeze the MongoDB and MinIO schema as the migration source of truth.
- Export counts and checksums for every MongoDB collection and MinIO object, grouped by user and bucket.
- Map Firebase UIDs to Supabase Auth users. Decide whether users reset passwords; Firebase password hashes are not automatically reusable by Supabase without a supported import procedure.
- Back up MongoDB, MinIO data, environment configuration, and Stripe identifiers.
- Add feature flags so reads and writes can be switched between legacy and Supabase implementations.

### Phase 1: Provision Supabase

- Create the Supabase project and configure Auth providers, redirect URLs, email settings, and administrator accounts.
- Add `@supabase/ssr` and `@supabase/supabase-js`.
- Add `NEXT_PUBLIC_SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY`, and server-only `SUPABASE_SECRET_KEY`.
- Keep the service-role key server-only; never expose it to browser code.
- Create the SQL migration, seed `plans`, create the private `photos` bucket, and apply RLS policies.
- Add local Supabase CLI configuration or document the hosted-dashboard workflow used by the team.

### Phase 2: Introduce shared clients and auth

- Add browser and server Supabase client helpers following the Next.js App Router cookie pattern.
- Replace `verifyFirebaseUser` with `getAuthenticatedUser(request)` based on `supabase.auth.getUser()`.
- Replace `useAdminAuth` with Supabase session state and an admin-role check. Remove local-storage token authority.
- Update middleware/protected routes to refresh sessions and redirect unauthenticated users.
- Migrate admin login to `supabase.auth.signInWithPassword`; authorization checks the admin role after authentication.
- During transition, log mismatches between legacy Firebase UID and Supabase user ID.

### Phase 3: Dual-read/dual-write application data

- Add a repository/data-access layer for plans, albums, images, subscriptions, and user storage.
- Convert user-facing endpoints first: plans, albums, photos, upload, usage, and subscription.
- Convert admin CRUD after the relational schema and role checks are stable. Replace Mongo ObjectId validation with UUID validation.
- For uploads, reserve storage atomically, upload to Supabase Storage, insert `images`, and compensate on failure.
- For deletes, verify ownership, delete the database row and object idempotently, then reconcile `used_bytes`.
- Use an idempotency key for uploads to prevent duplicate media on retries.

### Phase 4: Backfill and verify

1. Create/import Supabase Auth users and the Firebase UID mapping.
2. Import plans.
3. Import albums and preserve a legacy-ID mapping.
4. Import subscriptions and user storage rows.
5. Copy MinIO objects to `{user_id}/{album_id}/...` paths.
6. Import image metadata with the new Storage paths.
7. Recalculate `used_bytes` from image sizes and compare it with source records.
8. Verify counts, ownership, object existence, signed URL access, and representative downloads.

The import must be repeatable. Record source IDs, destination IDs, object paths, timestamps, and errors in migration tables or an external migration log.

### Phase 5: Cutover

- Put the application in maintenance/read-only mode for the final delta, or briefly stop legacy writes.
- Apply the final MongoDB/MinIO delta to Supabase.
- Switch feature flags to Supabase reads and writes.
- Smoke test login, admin access, album creation, upload, listing, download, delete, usage limits, and subscription updates.
- Monitor Auth, Postgres, Storage, orphaned-object, duplicate-upload, and storage-accounting errors.

### Phase 6: Decommission

- Keep MongoDB and MinIO backups read-only for the agreed retention period.
- Remove legacy dependencies and code only after rollback is no longer required: `mongoose`, `minio`, Firebase Admin, `jsonwebtoken`, bcrypt-based admin login, `lib/db.ts`, `lib/minio-client.ts`, legacy models, and Firebase helpers.
- Remove legacy environment variables and bucket credentials from deployment secrets.
- Update README and deployment documentation with Supabase setup, migrations, RLS, and backup/restore procedures.

## API Migration Map

| Area | Current implementation | Target implementation |
| --- | --- | --- |
| User auth | Firebase Bearer token | Supabase Auth session/access token |
| Admin auth | MongoDB admin + custom JWT | Supabase Auth user + admin role |
| Albums/photos | Mongoose models | Supabase Postgres queries/RPCs |
| Upload | MinIO `putObject` then MongoDB insert | Supabase Storage upload then Postgres insert with compensation |
| Delete | MongoDB delete, storage decrement, MinIO delete | Ownership check, Postgres transaction/RPC, Storage delete/reconciliation |
| Usage | MongoDB `Storage` and `Plan` documents | `user_storage`, `plans`, atomic SQL function |
| Admin CRUD | Dynamic Mongoose model map | Explicit table allowlist and typed Postgres operations |

## Security Requirements

- Enable RLS on every public table containing user data.
- Policies must use `auth.uid()` and never accept request `user_id` as proof of ownership.
- Keep service-role operations in server-only code and restrict them to admin or migration workflows.
- Validate Stripe payment metadata against the authenticated Supabase user ID before updating subscriptions.
- Use private Storage buckets and short-lived signed URLs.
- Do not expose passwords, service-role keys, or raw Auth administration responses to clients.
- Add audit logging for admin role changes and destructive admin operations.

## Verification and Rollback Gates

Before each cutover step, verify:

- Source and destination row/object counts match, with intentional differences documented.
- Every image has an existing Storage object and an owning user/album.
- RLS tests reject cross-user album, image, storage, and object access.
- Storage usage equals the sum of active image sizes.
- Admin role checks reject normal users and unauthenticated requests.
- Upload/delete workflows are idempotent and clean up partial failures.

Rollback means switching feature flags back to the legacy read/write path and preserving post-cutover changes in a delta log for replay. Do not delete MongoDB or MinIO data until the rollback window has expired.

## Immediate Next Steps

1. Confirm the Supabase project, Auth providers, user password migration policy, and whether admins use the same Auth project.
2. Add Supabase dependencies and client helpers without changing route behavior.
3. Write and apply the initial Postgres schema, seed plans, create the private bucket, and add RLS policies.
4. Build a Firebase UID to Supabase UUID mapping and a repeatable migration script.
5. Migrate authentication and one low-risk read endpoint first, then validate before converting writes and uploads.

## Android Client Integration

The Android app must send the Supabase **access token**, not the user's email, UID, or metadata as proof of identity:

```http
Authorization: Bearer <session.accessToken>
```

The backend calls `supabase.auth.getUser()` with that token. The returned `user.id` is the only trusted owner ID and is used for album, image, quota, and subscription queries. The client may send descriptive metadata such as filename, capture date, latitude, and longitude, but it must not be allowed to choose `user_id`.

### Recommended upload flow: Android to backend

Use `POST /api/v1/photos/upload?albumId=<uuid>` as multipart form data:

- Header: `Authorization: Bearer <session.accessToken>`
- File part: `image`
- Optional text part: `metadata` containing JSON

The backend validates the token, verifies album ownership, reserves quota, creates a user-scoped Storage path, uploads to the private `photos` bucket, and inserts the `images` row. It also compensates for failed uploads by removing the object and releasing reserved bytes.

Example Kotlin request shape:

```kotlin
val accessToken = supabase.auth.currentSessionOrNull()?.accessToken
	?: error("User is not signed in")

val request = MultipartBody.Builder()
	.setType(MultipartBody.FORM)
	.addFormDataPart(
		"image",
		file.name,
		file.asRequestBody("image/jpeg".toMediaType())
	)
	.addFormDataPart(
		"metadata",
		"{\"capturedAt\":\"2026-10-02T12:00:00Z\",\"latitude\":35.0,\"longitude\":139.0}"
	)
	.build()

val httpRequest = Request.Builder()
	.url("https://api.example.com/api/v1/photos/upload?albumId=$albumId")
	.header("Authorization", "Bearer $accessToken")
	.post(request)
	.build()
```

For every authenticated API request, retrieve the current session first because access tokens expire. If the client library refreshes the session, use the refreshed token for the request.

### Direct Android upload to Supabase Storage

Direct upload is also possible with the Android Supabase client and the publishable key. The client should use a path beginning with the Auth user ID, for example:

```text
<auth-user-id>/<album-id>/<generated-image-id>-<safe-file-name>
```

Storage RLS permits that path only when its first segment matches `auth.uid()`. Do not use `SUPABASE_SECRET_KEY` in Android; it is server-only and grants administrative access.

For direct uploads, use this sequence:

1. Call a backend endpoint to reserve quota and create/validate the album.
2. Upload the object from Android using the authenticated Supabase session.
3. Call a backend finalize endpoint with the generated storage path and file metadata.
4. The backend verifies the path belongs to the authenticated user, inserts `images`, and releases quota if finalization fails.

Direct upload has more moving parts and needs cleanup for abandoned objects. The backend-mediated endpoint is the preferred first implementation because quota, object creation, and metadata are coordinated in one server workflow.
