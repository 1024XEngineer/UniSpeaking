-- Email-session identity and admin governance tables shared by the unified backend.
-- The existing "user" table remains the canonical business identity. The
-- app_users row is a governance projection with the same UUID, never a second
-- account identity.
alter table "user" add column if not exists email_verified_at timestamptz;

create table if not exists app_users (
    id uuid primary key,
    email varchar(320) not null unique,
    password_hash varchar(1000) not null,
    created_at timestamptz not null,
    email_verified_at timestamptz
);
alter table app_users add column if not exists email_verified_at timestamptz;

insert into app_users (id, email, password_hash, created_at, email_verified_at)
select id, username, password_hash, created_at, email_verified_at
from "user"
where position('@' in username) > 1
on conflict (id) do update set
    email = excluded.email,
    password_hash = excluded.password_hash,
    email_verified_at = coalesce(app_users.email_verified_at, excluded.email_verified_at);

create table if not exists auth_email_challenges (
    id uuid primary key,
    email varchar(320) not null,
    code_digest bytea not null,
    expires_at timestamptz not null,
    consumed_at timestamptz,
    created_at timestamptz not null
);
create index if not exists idx_auth_email_challenges_email_created
    on auth_email_challenges (email, created_at desc);

create table if not exists user_sessions (
    token_digest varchar(128) primary key,
    user_id uuid not null references app_users(id),
    created_at timestamptz not null,
    last_seen_at timestamptz not null,
    expires_at timestamptz not null,
    revoked_at timestamptz
);
create index if not exists idx_user_sessions_user_id on user_sessions(user_id);

create table if not exists user_entitlements (
    user_id uuid primary key references app_users(id),
    plan_code varchar(64) not null default 'free',
    plan_name varchar(128) not null default 'Free',
    quota_date date not null default current_date,
    quota_seconds numeric(12,3) not null default 600,
    used_seconds numeric(12,3) not null default 0,
    status varchar(32) not null default 'active',
    updated_at timestamptz not null default current_timestamp
);

insert into user_entitlements (user_id, plan_code, plan_name, quota_date, quota_seconds, used_seconds, status, updated_at)
select id, 'free', 'Free', current_date, 600, 0, 'active', current_timestamp
from app_users
on conflict (user_id) do nothing;

create table if not exists admin_accounts (
    id uuid primary key,
    login varchar(320) not null unique,
    password_hash varchar(1000) not null,
    role varchar(64) not null,
    enabled boolean not null,
    created_at timestamptz not null
);

create table if not exists admin_sessions (
    token_hash varchar(128) primary key,
    admin_id uuid not null references admin_accounts(id),
    created_at timestamptz not null,
    last_seen_at timestamptz not null,
    expires_at timestamptz not null,
    revoked boolean not null default false
);
create index if not exists idx_admin_sessions_admin_id on admin_sessions(admin_id);
