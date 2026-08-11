create table if not exists admin_accounts (
    id uuid primary key,
    login varchar(320) not null unique,
    password_hash varchar(1000) not null,
    role varchar(64) not null,
    enabled boolean not null,
    created_at timestamp with time zone not null
);

create table if not exists admin_sessions (
    token_hash varchar(128) primary key,
    admin_id uuid not null,
    created_at timestamp with time zone not null,
    last_seen_at timestamp with time zone not null,
    expires_at timestamp with time zone not null,
    revoked boolean not null
);

create table if not exists app_users (
    id uuid primary key,
    email varchar(320) not null unique,
    password_hash varchar(1000) not null,
    created_at timestamp with time zone not null
);

create table if not exists user_entitlements (
    user_id uuid primary key,
    plan_code varchar(64) not null,
    plan_name varchar(128) not null,
    quota_date date not null,
    quota_seconds numeric(12,3) not null,
    used_seconds numeric(12,3) not null,
    status varchar(32) not null,
    updated_at timestamp with time zone not null
);
