-- Initial Supabase schema for Lumi.
-- Application users are managed by Supabase Auth in auth.users.

create extension if not exists pgcrypto;

create type public.app_role as enum ('admin', 'user');

create table public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  display_name text,
  avatar_url text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.user_roles (
  user_id uuid primary key references auth.users(id) on delete cascade,
  role public.app_role not null default 'user',
  created_at timestamptz not null default now()
);

create table public.plans (
  id uuid primary key default gen_random_uuid(),
  name text not null unique,
  storage_limit_bytes bigint not null check (storage_limit_bytes >= 0),
  price numeric(12, 2) not null default 0 check (price >= 0),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.albums (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  name text not null,
  description text,
  cover_photo_path text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint albums_user_name_unique unique (user_id, name)
);

create table public.images (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  album_id uuid not null references public.albums(id) on delete cascade,
  storage_path text not null unique,
  original_name text not null,
  mime_type text not null,
  size_bytes bigint not null check (size_bytes > 0),
  latitude double precision,
  longitude double precision,
  metadata jsonb not null default '{}'::jsonb,
  captured_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint images_metadata_object check (jsonb_typeof(metadata) = 'object')
);

create table public.user_storage (
  user_id uuid primary key references auth.users(id) on delete cascade,
  plan_id uuid not null references public.plans(id),
  used_bytes bigint not null default 0 check (used_bytes >= 0),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.subscriptions (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete restrict,
  plan_id uuid not null references public.plans(id),
  stripe_merchant_id text,
  payment_intent_id text,
  start_date timestamptz not null default now(),
  end_date timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint subscriptions_dates_valid check (end_date is null or end_date >= start_date)
);

create unique index subscriptions_one_active_per_user
  on public.subscriptions (user_id)
  where end_date is null or end_date > now();

create index albums_user_created_idx on public.albums (user_id, created_at desc);
create index images_user_album_captured_idx on public.images (user_id, album_id, captured_at desc);
create index images_user_created_idx on public.images (user_id, created_at desc);
create index subscriptions_user_idx on public.subscriptions (user_id, start_date desc);

create or replace function public.set_updated_at()
returns trigger
language plpgsql
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

create trigger profiles_set_updated_at
before update on public.profiles
for each row execute function public.set_updated_at();

create trigger plans_set_updated_at
before update on public.plans
for each row execute function public.set_updated_at();

create trigger albums_set_updated_at
before update on public.albums
for each row execute function public.set_updated_at();

create trigger images_set_updated_at
before update on public.images
for each row execute function public.set_updated_at();

create trigger user_storage_set_updated_at
before update on public.user_storage
for each row execute function public.set_updated_at();

create trigger subscriptions_set_updated_at
before update on public.subscriptions
for each row execute function public.set_updated_at();

create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer set search_path = public
as $$
begin
  insert into public.profiles (id, display_name, avatar_url)
  values (
    new.id,
    coalesce(new.raw_user_meta_data ->> 'full_name', new.raw_user_meta_data ->> 'name'),
    new.raw_user_meta_data ->> 'avatar_url'
  )
  on conflict (id) do nothing;

  insert into public.user_roles (user_id, role)
  values (new.id, 'user')
  on conflict (user_id) do nothing;

  return new;
end;
$$;

create trigger on_auth_user_created
after insert on auth.users
for each row execute function public.handle_new_user();

create or replace function public.is_admin()
returns boolean
language sql
stable
security definer set search_path = public
as $$
  select exists (
    select 1
    from public.user_roles
    where user_id = auth.uid() and role = 'admin'
  );
$$;

create or replace function public.reserve_storage(required_bytes bigint)
returns public.user_storage
language plpgsql
security invoker
set search_path = public
as $$
declare
  current_storage public.user_storage;
  plan_limit bigint;
begin
  if auth.uid() is null then
    raise exception 'Not authenticated' using errcode = '42501';
  end if;

  if required_bytes <= 0 then
    raise exception 'required_bytes must be greater than zero' using errcode = '22023';
  end if;

  select us.*
    into current_storage
    from public.user_storage us
   where us.user_id = auth.uid()
   for update;

  if current_storage.user_id is null then
    select p.id into current_storage.plan_id
      from public.plans p where p.name = 'Free';

    if current_storage.plan_id is null then
      raise exception 'Free plan not found' using errcode = 'P0002';
    end if;

    insert into public.user_storage (user_id, plan_id, used_bytes)
    values (auth.uid(), current_storage.plan_id, 0)
    returning * into current_storage;
  end if;

  select p.storage_limit_bytes into plan_limit
    from public.plans p where p.id = current_storage.plan_id;

  if plan_limit is null then
    raise exception 'Storage plan not found' using errcode = 'P0002';
  end if;

  if current_storage.used_bytes + required_bytes > plan_limit then
    raise exception 'Storage limit exceeded' using errcode = '53200';
  end if;

  update public.user_storage
     set used_bytes = used_bytes + required_bytes
   where user_id = auth.uid()
   returning * into current_storage;

  return current_storage;
end;
$$;

create or replace function public.release_storage(released_bytes bigint)
returns public.user_storage
language plpgsql
security invoker
set search_path = public
as $$
declare
  current_storage public.user_storage;
begin
  if auth.uid() is null then
    raise exception 'Not authenticated' using errcode = '42501';
  end if;

  if released_bytes <= 0 then
    raise exception 'released_bytes must be greater than zero' using errcode = '22023';
  end if;

  update public.user_storage
     set used_bytes = greatest(used_bytes - released_bytes, 0)
   where user_id = auth.uid()
   returning * into current_storage;

  if current_storage.user_id is null then
    raise exception 'Storage record not found' using errcode = 'P0002';
  end if;

  return current_storage;
end;
$$;

alter table public.profiles enable row level security;
alter table public.user_roles enable row level security;
alter table public.plans enable row level security;
alter table public.albums enable row level security;
alter table public.images enable row level security;
alter table public.user_storage enable row level security;
alter table public.subscriptions enable row level security;

create policy profiles_select_own on public.profiles
for select to authenticated using (id = auth.uid() or public.is_admin());
create policy profiles_update_own on public.profiles
for update to authenticated using (id = auth.uid()) with check (id = auth.uid());

create policy user_roles_select_own_or_admin on public.user_roles
for select to authenticated using (user_id = auth.uid() or public.is_admin());
create policy user_roles_admin_manage on public.user_roles
for all to authenticated using (public.is_admin()) with check (public.is_admin());

create policy plans_read_authenticated on public.plans
for select to authenticated using (true);
create policy plans_admin_manage on public.plans
for all to authenticated using (public.is_admin()) with check (public.is_admin());

create policy albums_select_own_or_admin on public.albums
for select to authenticated using (user_id = auth.uid() or public.is_admin());
create policy albums_insert_own_or_admin on public.albums
for insert to authenticated with check (user_id = auth.uid() or public.is_admin());
create policy albums_update_own_or_admin on public.albums
for update to authenticated using (user_id = auth.uid() or public.is_admin()) with check (user_id = auth.uid() or public.is_admin());
create policy albums_delete_own_or_admin on public.albums
for delete to authenticated using (user_id = auth.uid() or public.is_admin());

create policy images_select_own_or_admin on public.images
for select to authenticated using (user_id = auth.uid() or public.is_admin());
create policy images_insert_own_or_admin on public.images
for insert to authenticated with check (user_id = auth.uid() or public.is_admin());
create policy images_update_own_or_admin on public.images
for update to authenticated using (user_id = auth.uid() or public.is_admin()) with check (user_id = auth.uid() or public.is_admin());
create policy images_delete_own_or_admin on public.images
for delete to authenticated using (user_id = auth.uid() or public.is_admin());

create policy user_storage_select_own_or_admin on public.user_storage
for select to authenticated using (user_id = auth.uid() or public.is_admin());
create policy user_storage_admin_manage on public.user_storage
for all to authenticated using (public.is_admin()) with check (public.is_admin());

create policy subscriptions_select_own_or_admin on public.subscriptions
for select to authenticated using (user_id = auth.uid() or public.is_admin());
create policy subscriptions_admin_manage on public.subscriptions
for all to authenticated using (public.is_admin()) with check (public.is_admin());

insert into public.plans (name, storage_limit_bytes, price)
values
  ('Free', 1073741824, 0),
  ('Basic', 10737418240, 4.99),
  ('Pro', 107374182400, 14.99)
on conflict (name) do update set
  storage_limit_bytes = excluded.storage_limit_bytes,
  price = excluded.price;

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
  'photos',
  'photos',
  false,
  52428800,
  array['image/jpeg', 'image/png', 'image/webp', 'image/gif', 'video/mp4', 'video/quicktime']
)
on conflict (id) do update set
  public = excluded.public,
  file_size_limit = excluded.file_size_limit,
  allowed_mime_types = excluded.allowed_mime_types;

create policy photos_select_own_or_admin on storage.objects
for select to authenticated
using (
  bucket_id = 'photos'
  and (
    (storage.foldername(name))[1] = (auth.uid())::text
    or public.is_admin()
  )
);

create policy photos_insert_own_or_admin on storage.objects
for insert to authenticated
with check (
  bucket_id = 'photos'
  and ((storage.foldername(name))[1] = (auth.uid())::text or public.is_admin())
);

create policy photos_update_own_or_admin on storage.objects
for update to authenticated
using (
  bucket_id = 'photos'
  and ((storage.foldername(name))[1] = (auth.uid())::text or public.is_admin())
)
with check (
  bucket_id = 'photos'
  and ((storage.foldername(name))[1] = (auth.uid())::text or public.is_admin())
);

create policy photos_delete_own_or_admin on storage.objects
for delete to authenticated
using (
  bucket_id = 'photos'
  and ((storage.foldername(name))[1] = (auth.uid())::text or public.is_admin())
);
