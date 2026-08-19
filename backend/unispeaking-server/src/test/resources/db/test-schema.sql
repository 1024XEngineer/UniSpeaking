create table users (
    id uuid primary key,
    username varchar(320) not null unique,
    password_hash varchar(1000) not null,
    nickname varchar(32),
    avatar_object_key varchar(512),
    role varchar(16) not null default 'USER',
    status varchar(16) not null default 'ACTIVE',
    auth_version bigint not null default 0,
    last_login_at timestamp with time zone,
    email_verified_at timestamp with time zone,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create table auth_email_challenges (
    id uuid primary key,
    email varchar(320) not null,
    code_digest varbinary(64) not null,
    expires_at timestamp with time zone not null,
    consumed_at timestamp with time zone,
    created_at timestamp with time zone not null
);

create table user_sessions (
    token_digest varchar(128) primary key,
    user_id uuid not null references users(id),
    created_at timestamp with time zone not null,
    last_seen_at timestamp with time zone not null,
    expires_at timestamp with time zone not null,
    revoked_at timestamp with time zone
);

create table user_entitlements (
    user_id uuid primary key references users(id),
    plan_code varchar(64) not null default 'free',
    plan_name varchar(128) not null default 'Free',
    quota_date date not null default current_date,
    quota_seconds numeric(12,3) not null default 600,
    used_seconds numeric(12,3) not null default 0,
    status varchar(32) not null default 'active',
    updated_at timestamp with time zone not null default current_timestamp
);
