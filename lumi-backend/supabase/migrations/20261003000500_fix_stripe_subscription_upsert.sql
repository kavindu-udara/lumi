-- Allow webhook upserts to target stripe_subscription_id directly.
-- A partial unique index cannot be inferred by ON CONFLICT (stripe_subscription_id).

drop index if exists public.subscriptions_stripe_subscription_id_idx;

alter table public.subscriptions
  drop constraint if exists subscriptions_stripe_subscription_id_key;

alter table public.subscriptions
  add constraint subscriptions_stripe_subscription_id_key
  unique (stripe_subscription_id);
