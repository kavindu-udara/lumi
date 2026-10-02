# Lumi Billing, Plans, and Storage Quota Implementation Plan

## Objective

Implement three subscription plans, including a Free plan with a 1 GiB storage quota, with Stripe as the billing source of truth. The backend must:

- Track every authenticated user's storage usage.
- Reject new uploads atomically when the effective plan quota is full.
- Apply successful plan purchases and renewals to the user's storage entitlement.
- Keep a cancelled or downgraded paid plan active until the Stripe billing period ends.
- Apply the Free or downgraded plan at the effective period end.
- Record Stripe subscription renewals and process webhook retries safely.

The existing Supabase `user_storage` row and `reserve_storage`/`release_storage` RPCs remain the accounting foundation. Stripe webhooks, not client requests, become the authoritative source for subscription lifecycle state.

## Decisions and assumptions

1. Storage units are bytes. The Free plan limit is `1,073,741,824` bytes (1 GiB), stored in `plans.storage_limit_bytes`.
2. There are three plans total: `Free` plus two paid plans. The names, paid limits, prices, billing interval, and Stripe Price IDs must be supplied/configured before seeding production. Do not accept a client-provided amount as the billing authority.
3. Paid plans use Stripe recurring Prices and Stripe Subscriptions. One-time PaymentIntents are not sufficient for renewals and will be retired from the plan-change flow.
4. A cancellation means `cancel_at_period_end=true`; access and the current quota remain until `current_period_end`. A downgrade follows the same deferred-effective-date behavior.
5. If a pending downgrade would make the user's current usage exceed the new quota, the downgrade is still scheduled, but uploads remain blocked after it becomes effective until the user deletes enough media or upgrades. Existing media is not deleted automatically.
6. A plan change must never reset `used_bytes`. Usage is the sum of stored image/video sizes and is independent of billing periods.
7. Webhook handling must be idempotent. Stripe event IDs are persisted before/with processing and duplicate deliveries must produce no second subscription or renewal record.

## Current implementation baseline

- Plan and quota schema: `supabase/migrations/20261002000100_initial_schema.sql`
- Atomic upload reservation: `public.reserve_storage(required_bytes)`
- Atomic deletion release: `public.release_storage(released_bytes)`
- Upload enforcement: `app/api/v1/photos/upload/route.ts`
- Delete accounting: `app/api/v1/photos/route.ts`
- Current subscription endpoint: `app/api/v1/subscription/route.ts`
- Current one-time payment endpoint: `app/api/v1/payments/create-intent/route.ts`
- Stripe client: `lib/stripe.ts`
- Current usage response: `app/api/v1/usage/route.ts`

The current subscription endpoint writes a synthetic 30-day subscription after retrieving a successful PaymentIntent. That path must be replaced with Stripe Checkout or a server-created recurring Subscription plus webhook confirmation. The client must not directly activate a plan after payment.

## Phase 1: Define plan configuration and database model

### Migration changes

Create a new migration rather than rewriting the applied initial migration.

1. Extend `public.plans` with billing fields:
	- `stripe_product_id` nullable for Free and unique when present.
	- `stripe_price_id` nullable for Free and unique when present.
	- `billing_interval` constrained to `month` or `year`, nullable for Free.
	- `is_active` boolean defaulting to true.
2. Extend `public.subscriptions` to represent Stripe's lifecycle:
	- `stripe_subscription_id` unique and non-null for paid subscriptions.
	- `stripe_customer_id`.
	- `status` constrained to Stripe states needed by the application (`trialing`, `active`, `past_due`, `canceled`, `unpaid`, `incomplete`, `incomplete_expired`, `paused`).
	- `current_period_start` and `current_period_end`.
	- `cancel_at_period_end`.
	- `cancelled_at` and `ended_at`.
	- `pending_plan_id` and `pending_change_effective_at` for deferred downgrades, or an equivalent subscription-change table if multiple pending changes must be supported.
	- Keep legacy payment intent fields temporarily for migration/audit compatibility.
3. Add `billing_events` with a unique `stripe_event_id`, event type, Stripe object ID, processing status, received/processed timestamps, and error details. This is the webhook idempotency and audit record.
4. Add `subscription_periods` or `subscription_renewals` keyed by Stripe invoice/subscription identifiers. Store period start/end, plan, amount, currency, invoice ID, payment intent ID, status, and paid timestamp. Add a unique constraint on the Stripe invoice ID.
5. Add indexes for `(user_id, status)`, `stripe_subscription_id`, `current_period_end`, pending effective changes, and billing event processing status.
6. Replace the current active-subscription uniqueness approach with a database-valid constraint/index. The existing partial index predicate uses `now()`, which is not suitable for a PostgreSQL index predicate; active selection should use status and application/database transaction logic instead.
7. Seed exactly three plans. Free must be 1 GiB and have no Stripe Price ID. Paid plan values must come from deployment configuration or a documented seed change, never from frontend input.

### Storage accounting functions

1. Update `reserve_storage` to resolve the effective entitlement from the active subscription/plan state, or from `user_storage.plan_id` maintained transactionally by billing workflows.
2. Keep the row lock and limit check atomic. Return a stable error code for quota exhaustion so the upload route can return HTTP `413` or `409` with a clear machine-readable error.
3. Add an admin/service-role-safe function to change `user_storage.plan_id` without changing `used_bytes`, validating the target plan and recording the change transactionally.
4. Add a reconciliation query/function that compares `user_storage.used_bytes` with `sum(images.size_bytes)` per user and reports mismatches.
5. Ensure a new user receives a Free `user_storage` row on first usage or through an Auth user trigger/backfill. The first upload must not race into duplicate storage rows.

## Phase 2: Implement Stripe customer and recurring checkout

### Server-side Stripe helpers

Update `lib/stripe.ts` or add a billing module to centralize:

- Customer lookup by stored `stripe_customer_id`, with metadata fallback only for migration.
- Plan-to-Stripe-Price validation from the database.
- Checkout Session or Subscription creation with `supabaseUserId`, internal `planId`, and application environment metadata.
- Stripe API version/types and safe error normalization.
- Webhook signature verification using the raw request body and `STRIPE_WEBHOOK_SECRET`.

Do not list up to 100 customers on every request as the long-term lookup strategy. Persist the customer ID on the user subscription/profile billing record and create it once when absent.

### API changes

1. Replace or deprecate `POST /api/v1/payments/create-intent` for subscriptions. Add a recurring checkout endpoint such as `POST /api/v1/billing/checkout` that accepts only `planId` and optional return URLs, verifies the plan server-side, and returns a Stripe Checkout URL/session ID.
2. Add `POST /api/v1/billing/portal` to create a Stripe Customer Portal session for payment method/invoice management if the client needs self-service billing.
3. Update `GET /api/v1/subscription` to return:
	- Current effective plan and quota.
	- `used_bytes` and remaining bytes.
	- Stripe status and current period end.
	- `cancel_at_period_end`.
	- Pending plan and its effective date, when present.
4. Update `GET /api/v1/usage` to return the same authoritative quota fields from `user_storage` and the effective plan. Do not calculate usage from a client-supplied value.
5. Keep plan listing public to authenticated users, but expose only active plans and billing metadata required by the client.

## Phase 3: Add the Stripe webhook lifecycle

Create `app/api/v1/webhooks/stripe/route.ts` with Node runtime behavior and raw-body signature verification.

Process these events:

- `checkout.session.completed`: validate metadata, customer, subscription, and user ownership; create/update the local subscription in a transaction-safe operation. Do not trust a plan ID sent by the browser without comparing it to the Stripe Price ID.
- `customer.subscription.created` and `customer.subscription.updated`: synchronize status, period dates, cancellation flags, current plan, and pending changes. Apply an immediately effective upgrade; schedule a downgrade/cancellation for period end.
- `customer.subscription.deleted`: mark the subscription ended and apply the Free plan if Stripe ended it earlier than expected.
- `invoice.paid`: record a successful renewal in `subscription_renewals`, extend the local period, and ensure the paid plan remains the effective storage plan.
- `invoice.payment_failed`: record the failed renewal and update status. Define the grace-period policy; until decided, do not silently delete data or immediately discard the quota.
- `invoice.finalization_failed` or `customer.subscription.paused` where applicable: record status and surface it through the subscription endpoint.

Webhook transaction sequence:

1. Verify signature.
2. Insert the event ID with a unique constraint; return success for an already-processed event.
3. Resolve the local user through Stripe customer/subscription metadata.
4. Lock/update the subscription and renewal records in an idempotent transaction.
5. Apply `user_storage.plan_id` only when the plan is effective.
6. Mark the event processed. Leave a failed event retryable and log the safe event ID/type/error, not secret payloads.

## Phase 4: Implement plan change, cancellation, downgrade, and renewal behavior

### Upgrade

1. Client requests a paid plan checkout.
2. Stripe completes payment and creates/updates the recurring subscription.
3. Webhook validates the event and immediately changes the effective plan and `user_storage.plan_id`.
4. `used_bytes` is preserved. The larger quota becomes available only after the webhook transaction succeeds.

### Downgrade

1. Client requests a lower plan.
2. Server updates Stripe with `proration_behavior` explicitly chosen and records the pending plan/effective period end locally.
3. Current plan and quota remain active until `current_period_end`.
4. At the effective date, the webhook or a reconciliation job marks the lower plan effective and updates `user_storage.plan_id` without changing usage.
5. If usage exceeds the new quota, block uploads and return the over-quota state while allowing reads and deletes.

### Cancellation

1. Server sets Stripe `cancel_at_period_end=true` and records the pending end date.
2. User retains the paid plan and quota through that date.
3. On `customer.subscription.deleted` or the confirmed period-end transition, apply Free, clear the pending paid entitlement, and retain usage.
4. A user may resume before the end date by clearing Stripe's cancellation flag; the local pending cancellation must be cleared by webhook synchronization.

### Renewal

1. Stripe `invoice.paid` is the successful renewal signal.
2. Persist a renewal row once per invoice, update current period dates, and keep the effective plan unchanged.
3. Do not reset storage usage at renewal. Storage is a capacity entitlement, not a monthly allowance.
4. Failed payment behavior must be explicit and documented, including whether the user gets a grace period, read-only access, or immediate Free quota after Stripe marks the subscription unpaid.

### Reconciliation

Add a scheduled server/admin job that periodically compares Stripe subscriptions and invoices with local records, retries failed webhook events, applies missed period-end transitions, and reports usage mismatches. This is a recovery path, not a replacement for webhooks.

## Phase 5: Security and authorization

- Webhook route is authenticated only by Stripe signature, never by Supabase user cookies.
- Checkout, portal, cancel, and change-plan routes derive the user from `getAuthenticatedUser(request)`.
- Service-role Supabase access is limited to server-side billing workflows.
- Validate that Stripe customer/subscription metadata resolves to the authenticated user before allowing a plan change.
- Never accept amount, currency, Stripe Price ID, storage quota, user ID, or subscription ID as authoritative client input.
- Keep payment card data in Stripe; store only Stripe identifiers and billing status needed by the application.
- Redact webhook payloads and payment data from logs.

## Phase 6: Tests and verification gates

### Database tests

- New users resolve to Free with a 1 GiB limit.
- Concurrent reservations cannot exceed the effective limit.
- A reservation equal to remaining capacity succeeds; one byte over fails with the quota error.
- Delete/release never makes `used_bytes` negative.
- Plan changes preserve `used_bytes` and update only the effective plan.
- A pending downgrade does not change the current quota before its effective date.
- An effective downgrade blocks over-quota uploads without deleting existing images.
- Reconciliation identifies deliberate and accidental accounting mismatches.

### API tests

- Checkout rejects inactive plans, Free checkout, forged amounts, and another user's plan metadata.
- Subscription and usage responses include effective plan, usage, remaining bytes, status, and pending change details.
- Upload returns the quota-specific response and performs no object/database leak when reservation fails.
- Cancellation and downgrade remain active until the period end.
- Resume clears a pending cancellation.

### Webhook tests

- Invalid signatures are rejected.
- Duplicate event delivery is a no-op.
- Events arriving out of order do not roll a subscription backward; compare Stripe event/object timestamps where needed.
- Checkout completion, paid invoice, failed invoice, cancellation, and deletion update the expected rows.
- A renewal is recorded once per invoice.
- A failed local transaction returns a non-2xx response so Stripe retries.

### Manual Stripe test-mode verification

Use Stripe CLI forwarding to the local webhook route. Test a new subscription, successful renewal, failed payment, cancellation at period end, resume, immediate upgrade, deferred downgrade, duplicate delivery, and webhook replay after a temporary database failure.

## Rollout order

1. Apply schema and seed/configure the three plans, with Free at 1 GiB.
2. Backfill `user_storage` from image sizes and map existing paid records to Stripe customer/subscription IDs.
3. Deploy webhook handling and reconciliation in observe-only mode where possible.
4. Deploy recurring checkout and subscription APIs.
5. Switch plan activation from the PaymentIntent PUT flow to webhook-confirmed activation.
6. Verify quota, cancellation, downgrade, renewal, and reconciliation metrics.
7. Remove the old PaymentIntent plan activation path only after all active users are migrated and Stripe test-mode/live-mode checks pass.

## Required configuration

- `STRIPE_SECRET_KEY`
- `STRIPE_WEBHOOK_SECRET`
- Stripe publishable key for the client checkout integration, if using embedded/client Stripe UI
- Stripe Product/Price IDs for the two paid plans
- Explicit currency and billing interval configuration
- A documented invoice failure/grace-period policy
- A documented proration policy for upgrades and downgrades

## Definition of done

- Every user has an authoritative `user_storage` row and usage equals stored media bytes after reconciliation.
- Free users are limited to 1 GiB and cannot reserve additional storage after reaching the limit.
- Paid plan activation, renewal, cancellation, downgrade, resume, and failure states are driven by verified, idempotent Stripe events.
- Storage usage survives plan changes and renewals.
- Downgrades and cancellations take effect at the Stripe period end, while upgrades follow the documented proration/effective-time policy.
- The API exposes enough state for the client to explain quota exhaustion and pending billing changes.
- Automated database, API, and webhook tests pass, and Stripe test-mode event flows have been manually verified.
