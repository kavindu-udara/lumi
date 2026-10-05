# Share albums as public websites

## Objective

Add an album-level “Publish album as a website” feature. An authenticated owner can
publish an album and receive a URL in the Android client, for example:

`[backend-url]/shared/[opaque-share-token]`

Anyone with that URL can view the published album and download its media without
logging in. The owner can reopen the link from the album options menu or close
(revoke) the website from the same menu. Revoking a share must make the public URL
unusable without deleting the album or its media.

The backend remains the source of truth for publication state and ownership. The
Supabase `photos` bucket should remain private; public access must be granted only
through the revocable share-token routes.

## Existing surfaces to preserve

- Album CRUD: `app/api/v1/albums/route.ts` and
  `app/api/v1/albums/[id]/route.ts`.
- Media metadata and owner authorization: `app/api/v1/photos/route.ts`.
- Private media retrieval: `app/api/v1/photos/preview/[...path]/route.ts`.
- Storage paths are currently scoped as
  `{userId}/{albumId}/{imageId}-{safeFileName}` by
  `app/api/v1/photos/upload/route.ts`.
- Android album menu and callbacks live in
  `lumi/app/src/main/java/com/example/lumi/adapters/AlbumAdapter.java` and
  `lumi/app/src/main/java/com/example/lumi/fragments/AlbumsFragment.java`.
- Android HTTP calls use `lumi/app/src/main/java/com/example/lumi/lib/API.java`.
- The `Album` model currently contains only `id` and `name`; extend it compatibly
  rather than breaking existing album parsing.

## Proposed behavior and API contract

### Authenticated owner endpoints

Add album-scoped routes under `app/api/v1/albums/[id]/share/`:

1. `POST /api/v1/albums/:albumId/share`
   - Authenticate with `getAuthenticatedUser(request)`.
   - Verify the album belongs to the caller.
   - Create or reuse one active share for that album.
   - Return `{ share: { url, token, publishedAt, revokedAt: null } }`.
   - The URL must be constructed from a server-configured public origin, not from
     an untrusted `Host` header or a client-provided URL.
   - Repeated publish requests should be idempotent and return the same active
     share rather than creating multiple active links.

2. `GET /api/v1/albums/:albumId/share`
   - Authenticate and verify ownership.
   - Return the current active share details, or a clear `404`/`{ share: null }`
     when the album is not published.

3. `DELETE /api/v1/albums/:albumId/share`
   - Authenticate and verify ownership.
   - Revoke the active share by setting `revoked_at`.
   - Be idempotent when no active share exists.
   - Return success without deleting the album or media.

The client must send only the album ID. It must not send a user ID, token,
storage path, or public URL as authoritative input.

### Public website and downloads

Add public Next.js routes outside `/api/v1`, for example:

- `app/shared/[token]/page.tsx` — server-render the album page after resolving the
  opaque token. Show album name/description, media thumbnails, original file
  names, and a download action. Do not require Supabase cookies.
- `app/shared/[token]/download/[imageId]/route.ts` — resolve the token and image
  together, then stream the private object from Supabase with a safe
  `Content-Disposition: attachment` filename and the stored MIME type.

The public resolver must require all of the following:

- token exists and is not revoked;
- share is not expired (if expiration is introduced);
- album still exists;
- image belongs to that album.

Never query an image by an unvalidated storage path supplied by the visitor.
Use the stored image row to obtain the storage path. Return `404` for invalid,
revoked, deleted, or mismatched tokens without revealing whether an album used to
exist. Add conservative cache headers so revocation is observed promptly.

The page should handle empty albums, deleted media, missing objects, and download
errors with user-visible but non-sensitive messages. Avoid logging raw share
tokens or full public URLs.

## Database migration

Create a new Supabase migration; do not rewrite the applied initial migration.

Add `public.album_shares` with at least:

- `id uuid primary key default gen_random_uuid()`;
- `album_id uuid not null references public.albums(id) on delete cascade`;
- `token_hash text not null unique` (store only a cryptographic hash of the
  URL token);
- `created_at timestamptz not null default now()`;
- `revoked_at timestamptz`;
- optional `expires_at timestamptz` only if product requirements choose expiry;
- optional `last_accessed_at`/`download_count` only if metrics are needed.

Add a partial unique index enforcing one active share per album:

`unique (album_id) where revoked_at is null`.

Add indexes for token lookup and album ownership lookup. Enable RLS and allow
authenticated owners to read/manage their own album shares through an
`exists` check against `albums`; do not expose public token rows through RLS.
Public page/download resolution must use a narrowly scoped server-side service
client or a security-definer function that returns only the required album and
image fields. Never expose the service-role key to the client.

Add or update generated Supabase types if this repository keeps database types in
the source tree.

## Token and URL design

- Generate at least 128 bits of cryptographically secure random entropy with
  Node's `crypto.randomBytes`/`randomUUID`-equivalent secure API.
- Return the raw token only when publishing or retrieving the active share for its
  owner; persist only a one-way hash such as SHA-256.
- Use constant-time-safe comparison where applicable and normalize token input.
- Configure an explicit public origin such as `PUBLIC_APP_URL`/`NEXT_PUBLIC_APP_URL`
  for production and document it in `.env.example`; do not infer it from request
  headers.
- Decide and document whether tokens are permanent until revoked or have an
  expiry. The requested default is permanent-until-revoked.
- Do not put Supabase access tokens, user IDs, or storage paths in the URL.

## Android client changes

Update the existing album options flow:

1. Extend `AlbumAdapter.OnAlbumActionListener` with publish/open-share and
   revoke-share callbacks. Keep Edit/Delete behavior unchanged.
2. Add menu labels for:
   - “Publish as website” when no active share exists;
   - “View/copy website link” when an active share exists;
   - “Close website” when an active share exists.
3. In `AlbumsFragment`, call the new owner endpoints through `API.POST`, `API.GET`,
   and `API.DELETE` using the existing session token and background-thread pattern.
4. Show a dialog after publish or view-share with the URL, a copy button using
   Android `ClipboardManager`, and a close button. Confirm revocation before
   calling DELETE, then refresh album/share state.
5. Use string resources in `lumi/app/src/main/res/values/strings.xml`; add
   accessible labels/content descriptions and loading/error states. Do not
   silently treat malformed responses as successful.
6. Extend `Album` parsing/state with nullable share URL/status fields, or maintain
   a separate share response model if that better matches the current parser.
   Album listing must continue to work when share fields are absent.
7. Ensure the copied link is the exact server-returned URL and provide a
   user-visible confirmation after copying.

If the client needs share state without an extra request per menu open, extend the
authenticated album response with safe share metadata; otherwise use the explicit
GET share endpoint. Do not return token hashes.

## Security and abuse controls

- Public routes authenticate only with the opaque share token; they must not
  accept or trust client Supabase cookies for authorization.
- Owner routes always derive the owner from `getAuthenticatedUser`.
- Keep storage private and stream only rows reachable through a valid share.
- Prevent token enumeration with high entropy and uniform `404` responses.
- Add rate limiting or a documented hosting-level limit for public page/download
  routes if the deployment platform supports it.
- Sanitize download filenames and set safe content headers to prevent header
  injection.
- Avoid exposing GPS/location metadata, internal IDs, storage paths, or owner email
  on the public page unless explicitly approved. The default is to show only album
  name, description, media, and original names.
- Do not log raw request tokens or media contents.

## Tests and verification gates

### Database/API tests

- An unauthenticated caller cannot publish, inspect, or revoke another user's
  share.
- Publishing an owned album creates one active share and repeated publish is
  idempotent.
- A revoked share no longer resolves; revoking twice succeeds safely.
- Deleting an album cascades its share row and does not leave a usable URL.
- The token hash, not the raw token, is persisted.
- Invalid, revoked, deleted-album, deleted-image, and mismatched-image public
  requests all return safe `404` responses.
- A valid public page lists only media from the shared album.
- A valid download streams the correct object, MIME type, and safe filename.
- Missing storage objects return a controlled error and do not leak paths.

### Android tests/manual checks

- Menu text changes correctly between unpublished/published states.
- Publish opens the link dialog; Copy places the exact URL on the clipboard.
- Reopening the menu shows the existing link rather than creating a second one.
- Close website confirms, revokes, refreshes state, and makes the public URL fail.
- Network failures, expired sessions, malformed JSON, and rotation/configuration
  errors show an actionable error instead of a false success.
- Existing album create/edit/delete/photo browsing flows remain unchanged.

Run the smallest relevant backend lint/type/build checks and Android unit/build
checks available in each project. Add route tests and Android unit tests before
manual verification with a real private Supabase object.

## Rollout order

1. Add and apply the `album_shares` migration, RLS, indexes, and
   `PUBLIC_APP_URL` configuration.
2. Implement token hashing and authenticated owner share endpoints.
3. Implement public page and download streaming, then verify storage remains
   private.
4. Add Android menu, dialogs, clipboard behavior, and response parsing.
5. Run automated tests and manually verify publish, copy, anonymous view/download,
   revoke, and post-revoke failure.
6. Monitor route errors and download volume without recording raw tokens.

## Open decisions to confirm before implementation

- Whether “images” should include uploaded videos as downloadable media. The
  current `images` table stores both image and video MIME types; the default plan
  is to share both while labeling them by MIME type.
- Whether the public page needs a “download all” ZIP. The minimum scope is one
  download action per media item; ZIP generation should be a separate feature due
  to streaming, size, and abuse considerations.
- Whether published links should be permanent until manually closed (recommended)
  or automatically expire.
- Whether the public page should show descriptions and original filenames
  (recommended) while excluding location metadata and owner identity.