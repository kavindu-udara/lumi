# Supabase broadcast notifications feature plan

## Objective

Add an authenticated admin web page that lets authorized administrators compose and send broadcast notifications to Android users of Lumi. Use Supabase Auth, Postgres, and Realtime throughout. Do not add Firebase, FCM, Firebase service accounts, or Firebase messaging dependencies.

The backend owns authorization, audience selection, persistence, audit history, retry-safe fan-out, and notification state. The Android client subscribes to a user-scoped Supabase Realtime channel and displays received notifications.

## Important delivery constraint

Supabase Realtime provides authenticated in-app/reconnect delivery, but it does not provide native OS push notifications when an Android app is fully stopped. The first version should guarantee delivery while the app has an active Realtime subscription and provide persisted catch-up after reconnect. OS-level push while the app is stopped requires a separate provider and is explicitly out of scope for this Supabase-only version.

## Current baseline

- Admin login and role checks already exist through Supabase Auth and `user_roles`.
- `/admin/*` is protected by middleware, and the dashboard uses `useAdminAuth` plus authenticated admin API helpers.
- Admin CRUD routes currently manage selected Supabase resources through `app/api/v1/admin/manage`.
- The Android project has authenticated API access but no Supabase Realtime notification subscription.
- No notification schema or backend notification action currently exists.

## Decisions and assumptions

1. Use Supabase Postgres as the source of truth and Supabase Realtime as the delivery transport.
2. A broadcast targets all eligible authenticated users by default. Arbitrary audience filters are a later feature.
3. A broadcast has a required title and body plus optional allowlisted deep-link/data fields. Enforce server-side length and JSON-size limits.
4. Delivery is asynchronous when necessary. The API may create a queued broadcast and a worker/Edge Function may perform bounded fan-out.
5. Notifications are persisted per user so clients can catch up after reconnecting without duplicate rows.
6. Sending is restricted to administrators verified by the existing Supabase role guard. Sender identity and status transitions are audited.
7. Manual broadcasts and delivery history are in scope. Scheduling, rich media, personalization, and OS-level push are out of scope.

## Phase 1: Define the Supabase data model

Create a new migration rather than modifying applied migrations.

1. Add `broadcast_notifications`:
   - `id`, sender/admin user ID, title, body, optional data/deep-link JSON, status (`queued`, `sending`, `sent`, `partial`, `failed`), recipient count, delivered count, read count, failure count, timestamps, and safe error summary.
   - Add an optional idempotency key unique per sender.
2. Add `user_notifications`:
   - Broadcast ID, user ID, status (`available`, `read`), delivered/read timestamps, and created timestamp.
   - Add a unique `(broadcast_id, user_id)` constraint so retries cannot duplicate a notification.
   - Add indexes for `(user_id, created_at)` and broadcast history.
3. Add `notification_read_state` or an equivalent per-user cursor table for reconnect/catch-up bookkeeping.
4. Add constraints for title/body/data sizes, valid status values, and allowlisted payload shape.
5. Enable Supabase Realtime for `user_notifications`. Clients must subscribe with a filter for their authenticated user ID; no global notification channel is exposed.
6. Add RLS:
   - Users can select/update only their own `user_notifications` and read state.
   - Admins can manage and inspect broadcasts and aggregate delivery history.
   - Server-side fan-out uses the existing service-role Supabase client only where required.
7. Verify RLS, publication setup, uniqueness, and idempotent retry behavior with SQL tests or migration checks.

## Phase 2: Implement Supabase notification delivery

1. Add a server-only notification action under `actions/notifications/` or `lib/notifications.ts`, following the existing Supabase server/admin client patterns.
2. Create the broadcast and determine eligible authenticated recipients in a transaction-safe workflow.
3. Insert `user_notifications` rows in bounded batches. The inserts are the durable delivery events that Supabase Realtime publishes to connected clients.
4. For larger audiences, use a Supabase Edge Function or scheduled worker. It must claim queued work safely, retry transient database failures with a bounded policy, and update aggregate counts only after work is accounted for.
5. Use `ON CONFLICT DO NOTHING` or the equivalent Supabase operation so worker retries are idempotent.
6. Normalize database/worker failures into safe application error codes. Do not log full notification payloads when they may contain user data.

## Phase 3: Add authenticated notification APIs

1. `GET /api/v1/notifications`
   - Derive the user from the authenticated Supabase session.
   - Return recent notifications and read state using a bounded cursor/limit.
   - Never return another user's rows.
2. `POST /api/v1/notifications/[id]/read`
   - Mark only the authenticated user's notification as read.
   - Make the operation idempotent.
3. Add a server helper/documented client flow for creating a user-scoped Realtime subscription. The Android app uses its own Supabase session and never receives a service-role key.
4. Ensure logout removes the Realtime subscription and account deletion/read-state cleanup follows the existing lifecycle.

## Phase 4: Add admin broadcast APIs

1. `POST /api/v1/admin/notifications/broadcasts`:
   - Require `getAdminFromRequest`.
   - Validate title, body, allowlisted data/deep link, and optional idempotency key.
   - Create the broadcast and queue/fan out recipients durably.
   - Return the broadcast ID and queued status only after persistence succeeds.
2. `GET /api/v1/admin/notifications/broadcasts`:
   - Add pagination, status filters, sender, timestamps, and aggregate counts.
3. `GET /api/v1/admin/notifications/broadcasts/[id]`:
   - Return details and aggregate delivery/read summary without exposing unrelated user data or secrets.
4. Add a protected worker/action endpoint or scheduled job for queued fan-out. It must be safe under duplicate execution.
5. Add bounded retry support for failed database/worker operations and an admin-visible failure summary.
6. Add rate limiting or a cooldown to prevent accidental duplicate broadcasts.

## Phase 5: Build the admin web page

1. Keep the existing `/login` and admin role flow. Add a notifications section to the dashboard or a dedicated `/admin/notifications` page.
2. Provide:
   - Compose form for title, body, optional deep link/data.
   - Character/size validation and a confirmation that all eligible authenticated Android users will be targeted through Supabase Realtime.
   - Send, loading, success, and explicit error states.
   - Paginated broadcast history with sender, created time, status, recipient count, delivered/read/failure counts.
   - Detail view without exposing user IDs unnecessarily or any service credentials.
3. Reuse `useAdminAuth`, `adminGetRequest`, `adminPostRequest`, existing styling, and the protected route pattern.
4. Do not add notifications to the generic CRUD resource mapping; broadcast creation needs domain-specific validation and confirmation.
5. Update admin documentation with the new routes, Supabase configuration, Realtime limitation, and operational expectations.

## Phase 6: Integrate Android Supabase Realtime behavior

1. Add/configure the Supabase Android client using the existing project URL and publishable key. Never ship the Supabase service-role key.
2. After authentication, subscribe to a Realtime `postgres_changes` channel filtered to the current user's `user_id` on `user_notifications`.
3. On a received row, fetch the associated broadcast through an authorized API path and render an in-app notification. A local Android notification may be shown while the app process is running.
4. On app start or Realtime reconnect, call `GET /api/v1/notifications` with a cursor to catch up on missed rows.
5. Mark notifications read through the authenticated read endpoint.
6. Validate deep links against an allowlist and avoid duplicates when a Realtime event is also returned by catch-up.
7. Document that fully stopped-app OS push is not supported by this version and must not be implemented by adding Firebase dependencies.

## Phase 7: Security, reliability, and observability

- Protect every admin route with the existing server-side role check; middleware alone is not authorization.
- Use the Supabase service-role key only in server-side transaction/worker code.
- Validate payloads at the API boundary and allowlist data keys and deep-link routes.
- Redact notification contents from logs where they could contain user data.
- Record audit fields for admin ID, broadcast ID, status transitions, and safe worker/database error codes.
- Ensure duplicate admin requests and worker retries do not create duplicate broadcasts or user notifications.
- Define retention for broadcast content, user notification rows, and read state before production rollout.

## Phase 8: Tests and verification gates

### Database and action tests

- Users can read/update only their own notifications and read state.
- Admin-only broadcast access is enforced.
- Duplicate idempotency keys do not create two broadcasts.
- A broadcast/user row is unique and stable across worker retries.
- Realtime publication is enabled for the intended table without exposing global data.

### API tests

- Unauthenticated and non-admin broadcast requests return 401/403.
- Invalid title/body/data/deep-link payloads are rejected.
- Broadcast creation returns queued status only after durable persistence.
- History/detail endpoints paginate and do not leak unrelated user data or secrets.
- Notification reads derive the user from the session and reject forged ownership.

### Supabase worker/Realtime tests

- Successful fan-out updates notification and aggregate counts.
- Realtime-triggering inserts are idempotent.
- Transient database/worker errors retry with a bounded attempt count.
- Duplicate worker execution is safe.
- Failed fan-out remains retryable and produces an operationally useful error.

### Android verification

- Realtime subscription starts after login and is scoped to the current user.
- Reconnect catch-up returns missed notifications without duplicates.
- In-app/local notification display and allowlisted deep links work while the app process is active.
- Logout removes the Realtime subscription and prevents future authenticated reads.

## Rollout order

1. Apply the broadcast, user-notification, read-state, RLS, and Realtime migration.
2. Configure Supabase Realtime/publication and add server-side delivery tests.
3. Ship Android Realtime subscription and receive/display behavior.
4. Deploy admin APIs and worker in a disabled/observe-only mode, verifying row counts and retry behavior.
5. Ship the protected admin compose/history page and enable manual broadcasts.
6. Monitor fan-out success, reconnect catch-up, read rates, retry volume, and duplicate-send protections.
7. Add scheduling/segmentation only after the manual Realtime path is stable.

## Open decisions before implementation

- Should fan-out use a Supabase Edge Function/worker, or is a bounded in-request insert acceptable for the first deployment?
- Which deep-link routes and custom data keys should be allowlisted?
- What retention period should apply to broadcast content and per-user notification history?
- Should administrators have a separate `notification_sender` role, or should every existing `admin` role be allowed to send?
- Is Supabase Realtime/in-app delivery sufficient, or must the product support OS-level notifications while Android is fully stopped? The latter requires a separate push provider and cannot be provided by Supabase Realtime alone.
