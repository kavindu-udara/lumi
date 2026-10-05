-- Supabase-only broadcast notifications delivered through Realtime.

create table if not exists public.broadcast_notifications (
  id uuid primary key default gen_random_uuid(),
  sender_admin_id uuid not null references auth.users(id) on delete restrict,
  title text not null check (char_length(title) between 1 and 120),
  body text not null check (char_length(body) between 1 and 2000),
  data jsonb not null default '{}'::jsonb,
  status text not null default 'queued'
    check (status in ('queued', 'sending', 'sent', 'partial', 'failed')),
  idempotency_key text,
  recipient_count integer not null default 0 check (recipient_count >= 0),
  delivered_count integer not null default 0 check (delivered_count >= 0),
  read_count integer not null default 0 check (read_count >= 0),
  failure_count integer not null default 0 check (failure_count >= 0),
  error_message text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint broadcast_notifications_data_object
    check (jsonb_typeof(data) = 'object'),
  constraint broadcast_notifications_counts_valid
    check (delivered_count + failure_count <= recipient_count)
);

create unique index if not exists broadcast_notifications_sender_idempotency_idx
  on public.broadcast_notifications (sender_admin_id, idempotency_key)
  where idempotency_key is not null;

create index if not exists broadcast_notifications_created_idx
  on public.broadcast_notifications (created_at desc);

create table if not exists public.user_notifications (
  id uuid primary key default gen_random_uuid(),
  broadcast_id uuid not null references public.broadcast_notifications(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  status text not null default 'available'
    check (status in ('available', 'read')),
  delivered_at timestamptz not null default now(),
  read_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint user_notifications_read_state_valid
    check ((status = 'read' and read_at is not null) or
           (status = 'available' and read_at is null)),
  constraint user_notifications_broadcast_user_unique
    unique (broadcast_id, user_id)
);

create index if not exists user_notifications_user_created_idx
  on public.user_notifications (user_id, created_at desc);

create index if not exists user_notifications_broadcast_idx
  on public.user_notifications (broadcast_id, status);

create table if not exists public.notification_read_state (
  user_id uuid primary key references auth.users(id) on delete cascade,
  last_seen_at timestamptz,
  updated_at timestamptz not null default now()
);

drop trigger if exists broadcast_notifications_set_updated_at on public.broadcast_notifications;
create trigger broadcast_notifications_set_updated_at
before update on public.broadcast_notifications
for each row execute function public.set_updated_at();

drop trigger if exists user_notifications_set_updated_at on public.user_notifications;
create trigger user_notifications_set_updated_at
before update on public.user_notifications
for each row execute function public.set_updated_at();

drop trigger if exists notification_read_state_set_updated_at on public.notification_read_state;
create trigger notification_read_state_set_updated_at
before update on public.notification_read_state
for each row execute function public.set_updated_at();

alter table public.broadcast_notifications enable row level security;
alter table public.user_notifications enable row level security;
alter table public.notification_read_state enable row level security;

create policy broadcast_notifications_admin_select
on public.broadcast_notifications for select to authenticated
using (public.is_admin());

create policy broadcast_notifications_recipient_select
on public.broadcast_notifications for select to authenticated
using (
  exists (
    select 1
    from public.user_notifications
    where user_notifications.broadcast_id = broadcast_notifications.id
      and user_notifications.user_id = auth.uid()
  )
);

create policy broadcast_notifications_admin_insert
on public.broadcast_notifications for insert to authenticated
with check (public.is_admin() and sender_admin_id = auth.uid());

create policy broadcast_notifications_admin_update
on public.broadcast_notifications for update to authenticated
using (public.is_admin())
with check (public.is_admin());

create policy user_notifications_select_own
on public.user_notifications for select to authenticated
using (user_id = auth.uid());

create policy notification_read_state_own
on public.notification_read_state for all to authenticated
using (user_id = auth.uid())
with check (user_id = auth.uid());

do $$
begin
  alter publication supabase_realtime add table public.user_notifications;
exception
  when duplicate_object then null;
end;
$$;

create or replace function public.mark_notification_read(notification_id uuid)
returns public.user_notifications
language plpgsql
security definer
set search_path = public
as $$
declare
  updated_notification public.user_notifications;
begin
  if auth.uid() is null then
    raise exception 'Not authenticated' using errcode = '42501';
  end if;

  update public.user_notifications
  set status = 'read', read_at = coalesce(read_at, now())
  where id = notification_id
    and user_id = auth.uid()
  returning * into updated_notification;

  if updated_notification.id is null then
    raise exception 'Notification not found' using errcode = 'P0002';
  end if;

  update public.broadcast_notifications
  set read_count = (
    select count(*) from public.user_notifications
    where broadcast_id = updated_notification.broadcast_id and status = 'read'
  )
  where id = updated_notification.broadcast_id;

  return updated_notification;
end;
$$;

revoke all on function public.mark_notification_read(uuid) from public;
grant execute on function public.mark_notification_read(uuid) to authenticated;
revoke update on public.user_notifications from authenticated;
