create table public.album_shares (
  id uuid primary key default gen_random_uuid(),
  album_id uuid not null references public.albums(id) on delete cascade,
  token_hash text not null unique,
  created_at timestamptz not null default now(),
  revoked_at timestamptz
);

create unique index album_shares_one_active_per_album
  on public.album_shares (album_id)
  where revoked_at is null;

create index album_shares_album_idx on public.album_shares (album_id);
create index album_shares_token_hash_idx on public.album_shares (token_hash);

alter table public.album_shares enable row level security;

create policy album_shares_select_own on public.album_shares
for select to authenticated
using (
  exists (
    select 1 from public.albums
    where albums.id = album_shares.album_id
      and albums.user_id = auth.uid()
  )
);

create policy album_shares_insert_own on public.album_shares
for insert to authenticated
with check (
  exists (
    select 1 from public.albums
    where albums.id = album_shares.album_id
      and albums.user_id = auth.uid()
  )
);

create policy album_shares_update_own on public.album_shares
for update to authenticated
using (
  exists (
    select 1 from public.albums
    where albums.id = album_shares.album_id
      and albums.user_id = auth.uid()
  )
)
with check (
  exists (
    select 1 from public.albums
    where albums.id = album_shares.album_id
      and albums.user_id = auth.uid()
  )
);
