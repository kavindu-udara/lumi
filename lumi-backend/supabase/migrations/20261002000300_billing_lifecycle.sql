-- Stripe recurring billing lifecycle, webhook idempotency, and plan metadata.

alter table public.plans
  add column if not exists stripe_product_id text,
  add column if not exists stripe_price_id text,
  add column if not exists billing_interval text,
  add column if not exists is_active boolean not null default true;

alter table public.plans
  drop constraint if exists plans_billing_interval_check;

alter table public.plans
  add constraint plans_billing_interval_check
  check (billing_interval is null or billing_interval in ('month', 'year'));

create unique index if not exists plans_stripe_product_id_idx
  on public.plans (stripe_product_id)
  where stripe_product_id is not null;

create unique index if not exists plans_stripe_price_id_idx
  on public.plans (stripe_price_id)
  where stripe_price_id is not null;

alter table public.subscriptions
  add column if not exists stripe_subscription_id text,
  add column if not exists stripe_customer_id text,
  add column if not exists status text not null default 'active',
  add column if not exists current_period_start timestamptz,
  add column if not exists current_period_end timestamptz,
  add column if not exists cancel_at_period_end boolean not null default false,
  add column if not exists cancelled_at timestamptz,
  add column if not exists ended_at timestamptz,
  add column if not exists pending_plan_id uuid references public.plans(id),
  add column if not exists pending_change_effective_at timestamptz;

alter table public.subscriptions
  drop constraint if exists subscriptions_status_check;

alter table public.subscriptions
  add constraint subscriptions_status_check
  check (status in ('trialing', 'active', 'past_due', 'canceled', 'unpaid', 'incomplete', 'incomplete_expired', 'paused'));

create unique index if not exists subscriptions_stripe_subscription_id_idx
  on public.subscriptions (stripe_subscription_id)
  where stripe_subscription_id is not null;

create index if not exists subscriptions_user_status_idx
  on public.subscriptions (user_id, status);

create index if not exists subscriptions_period_end_idx
  on public.subscriptions (current_period_end);

create index if not exists subscriptions_pending_change_idx
  on public.subscriptions (pending_change_effective_at)
  where pending_change_effective_at is not null;

-- PostgreSQL does not allow a partial-index predicate based on now().
drop index if exists public.subscriptions_one_active_per_user;

create table if not exists public.billing_events (
  id uuid primary key default gen_random_uuid(),
  stripe_event_id text not null unique,
  event_type text not null,
  stripe_object_id text,
  status text not null default 'processing',
  error_message text,
  received_at timestamptz not null default now(),
  processed_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint billing_events_status_check check (status in ('processing', 'processed', 'failed'))
);

create index if not exists billing_events_status_idx
  on public.billing_events (status, received_at);

create table if not exists public.subscription_renewals (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete restrict,
  subscription_id uuid references public.subscriptions(id) on delete set null,
  plan_id uuid not null references public.plans(id),
  stripe_subscription_id text not null,
  stripe_invoice_id text not null unique,
  stripe_payment_intent_id text,
  period_start timestamptz not null,
  period_end timestamptz not null,
  amount_paid numeric(12, 2),
  currency text,
  status text not null default 'paid',
  paid_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint subscription_renewals_status_check check (status in ('paid', 'failed', 'void', 'uncollectible')),
  constraint subscription_renewals_dates_check check (period_end >= period_start)
);

create index if not exists subscription_renewals_user_idx
  on public.subscription_renewals (user_id, period_start desc);

create index if not exists subscription_renewals_subscription_idx
  on public.subscription_renewals (stripe_subscription_id, period_start desc);

create trigger billing_events_set_updated_at
before update on public.billing_events
for each row execute function public.set_updated_at();

create trigger subscription_renewals_set_updated_at
before update on public.subscription_renewals
for each row execute function public.set_updated_at();

-- Billing workers call this with the service-role client. It deliberately preserves usage.
create or replace function public.apply_storage_plan(target_user_id uuid, target_plan_id uuid)
returns public.user_storage
language plpgsql
security definer
set search_path = public
as $$
declare
  updated_storage public.user_storage;
begin
  if target_user_id is null or target_plan_id is null then
    raise exception 'target_user_id and target_plan_id are required' using errcode = '22023';
  end if;

  if not exists (select 1 from public.plans where id = target_plan_id and is_active) then
    raise exception 'Active storage plan not found' using errcode = 'P0002';
  end if;

  insert into public.user_storage (user_id, plan_id, used_bytes)
  values (target_user_id, target_plan_id, 0)
  on conflict (user_id) do update set plan_id = excluded.plan_id
  returning * into updated_storage;

  return updated_storage;
end;
$$;

revoke all on function public.apply_storage_plan(uuid, uuid) from public;
grant execute on function public.apply_storage_plan(uuid, uuid) to service_role;

alter table public.billing_events enable row level security;
alter table public.subscription_renewals enable row level security;

create policy billing_events_admin_manage on public.billing_events
for all to authenticated using (public.is_admin()) with check (public.is_admin());

create policy subscription_renewals_select_own_or_admin on public.subscription_renewals
for select to authenticated using (user_id = auth.uid() or public.is_admin());

create policy subscription_renewals_admin_manage on public.subscription_renewals
for all to authenticated using (public.is_admin()) with check (public.is_admin());

-- Existing seed values are retained; paid Stripe IDs should be set after creating Stripe Prices.
update public.plans
set is_active = true
where name in ('Free', 'Basic', 'Pro');

update public.plans
set storage_limit_bytes = 1073741824, price = 0
where name = 'Free';
