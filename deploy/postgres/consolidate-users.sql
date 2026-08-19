BEGIN;

-- PostgreSQL reserves USER. The canonical unquoted account table is users.
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(320) NOT NULL UNIQUE,
    password_hash VARCHAR(1000) NOT NULL,
    nickname VARCHAR(32),
    avatar_object_key VARCHAR(512),
    role VARCHAR(16) NOT NULL DEFAULT 'USER',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    auth_version BIGINT NOT NULL DEFAULT 0,
    last_login_at TIMESTAMPTZ,
    email_verified_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT user_role_check CHECK (role IN ('USER', 'ADMIN')),
    CONSTRAINT user_status_check CHECK (status IN ('ACTIVE', 'DISABLED', 'LOCKED')),
    CONSTRAINT user_auth_version_check CHECK (auth_version >= 0),
    CONSTRAINT user_avatar_object_key_check
        CHECK (avatar_object_key IS NULL OR BTRIM(avatar_object_key) <> '')
);

LOCK TABLE users IN ACCESS EXCLUSIVE MODE;

DO $migration$
BEGIN
    IF to_regclass('"user"') IS NOT NULL THEN
        LOCK TABLE "user" IN ACCESS EXCLUSIVE MODE;

        IF EXISTS (
            SELECT LOWER(username)
            FROM "user"
            GROUP BY LOWER(username)
            HAVING COUNT(DISTINCT id) > 1
        ) THEN
            RAISE EXCEPTION 'Cannot merge legacy user table: duplicate case-insensitive usernames have different IDs';
        END IF;

        IF EXISTS (
            SELECT 1
            FROM users unified_user
            JOIN "user" legacy_user
              ON LOWER(unified_user.username) = LOWER(legacy_user.username)
             AND unified_user.id <> legacy_user.id
        ) THEN
            RAISE EXCEPTION 'Cannot merge legacy user table: one username belongs to different user IDs';
        END IF;

        INSERT INTO users (
            id, username, password_hash, nickname, avatar_object_key, role, status,
            auth_version, last_login_at, email_verified_at, created_at, updated_at
        )
        SELECT
            legacy_user.id,
            legacy_user.username,
            legacy_user.password_hash,
            NULLIF(to_jsonb(legacy_user)->>'nickname', ''),
            NULLIF(to_jsonb(legacy_user)->>'avatar_object_key', ''),
            COALESCE(NULLIF(to_jsonb(legacy_user)->>'role', ''), 'USER'),
            COALESCE(NULLIF(to_jsonb(legacy_user)->>'status', ''), 'ACTIVE'),
            COALESCE((to_jsonb(legacy_user)->>'auth_version')::BIGINT, 0),
            (to_jsonb(legacy_user)->>'last_login_at')::TIMESTAMPTZ,
            (to_jsonb(legacy_user)->>'email_verified_at')::TIMESTAMPTZ,
            COALESCE((to_jsonb(legacy_user)->>'created_at')::TIMESTAMPTZ, CURRENT_TIMESTAMP),
            COALESCE((to_jsonb(legacy_user)->>'updated_at')::TIMESTAMPTZ, CURRENT_TIMESTAMP)
        FROM "user" legacy_user
        ON CONFLICT (id) DO UPDATE SET
            username = EXCLUDED.username,
            password_hash = EXCLUDED.password_hash,
            nickname = COALESCE(EXCLUDED.nickname, users.nickname),
            avatar_object_key = COALESCE(EXCLUDED.avatar_object_key, users.avatar_object_key),
            role = EXCLUDED.role,
            status = EXCLUDED.status,
            auth_version = GREATEST(users.auth_version, EXCLUDED.auth_version),
            last_login_at = COALESCE(EXCLUDED.last_login_at, users.last_login_at),
            email_verified_at = COALESCE(users.email_verified_at, EXCLUDED.email_verified_at),
            created_at = LEAST(users.created_at, EXCLUDED.created_at),
            updated_at = GREATEST(users.updated_at, EXCLUDED.updated_at);
    END IF;

    IF to_regclass('app_users') IS NOT NULL THEN
        LOCK TABLE app_users IN ACCESS EXCLUSIVE MODE;

        IF EXISTS (
            SELECT LOWER(email)
            FROM app_users
            GROUP BY LOWER(email)
            HAVING COUNT(DISTINCT id) > 1
        ) THEN
            RAISE EXCEPTION 'Cannot merge app_users: duplicate case-insensitive emails have different IDs';
        END IF;

        IF EXISTS (
            SELECT 1
            FROM users unified_user
            JOIN app_users legacy_identity
              ON LOWER(unified_user.username) = LOWER(legacy_identity.email)
             AND unified_user.id <> legacy_identity.id
        ) OR EXISTS (
            SELECT 1
            FROM users unified_user
            JOIN app_users legacy_identity ON unified_user.id = legacy_identity.id
            WHERE LOWER(unified_user.username) <> LOWER(legacy_identity.email)
        ) THEN
            RAISE EXCEPTION 'Cannot merge app_users: username/email and user ID mappings disagree';
        END IF;

        INSERT INTO users (
            id, username, password_hash, role, status, auth_version,
            email_verified_at, created_at, updated_at
        )
        SELECT
            legacy_identity.id,
            legacy_identity.email,
            legacy_identity.password_hash,
            'USER',
            'ACTIVE',
            0,
            (to_jsonb(legacy_identity)->>'email_verified_at')::TIMESTAMPTZ,
            legacy_identity.created_at,
            legacy_identity.created_at
        FROM app_users legacy_identity
        WHERE NOT EXISTS (SELECT 1 FROM users unified_user WHERE unified_user.id = legacy_identity.id);

        UPDATE users unified_user
        SET email_verified_at = COALESCE(
                unified_user.email_verified_at,
                (to_jsonb(legacy_identity)->>'email_verified_at')::TIMESTAMPTZ)
        FROM app_users legacy_identity
        WHERE unified_user.id = legacy_identity.id;
    END IF;
END
$migration$;

CREATE UNIQUE INDEX IF NOT EXISTS users_username_ci_uq
ON users (LOWER(username));

DO $migration$
DECLARE
    legacy_fk RECORD;
BEGIN
    IF to_regclass('app_users') IS NOT NULL AND EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE contype = 'f'
          AND confrelid = to_regclass('app_users')
          AND conrelid NOT IN (
              COALESCE(to_regclass('user_sessions')::OID, 0::OID),
              COALESCE(to_regclass('user_entitlements')::OID, 0::OID)
          )
    ) THEN
        RAISE EXCEPTION 'Cannot drop app_users: an unexpected table still has a foreign key to it';
    END IF;

    FOR legacy_fk IN
        SELECT conrelid::REGCLASS AS table_name, conname
        FROM pg_constraint
        WHERE contype = 'f'
          AND confrelid IN (
              COALESCE(to_regclass('app_users')::OID, 0::OID),
              COALESCE(to_regclass('"user"')::OID, 0::OID)
          )
    LOOP
        EXECUTE FORMAT('ALTER TABLE %s DROP CONSTRAINT %I', legacy_fk.table_name, legacy_fk.conname);
    END LOOP;

    IF to_regclass('app_users') IS NOT NULL THEN
        DROP TABLE app_users;
    END IF;
    IF to_regclass('"user"') IS NOT NULL THEN
        DROP TABLE "user";
    END IF;

    IF to_regclass('user_sessions') IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE contype = 'f'
          AND conrelid = to_regclass('user_sessions')
          AND confrelid = to_regclass('users')
    ) THEN
        ALTER TABLE user_sessions
        ADD CONSTRAINT user_sessions_user_id_fkey
        FOREIGN KEY (user_id) REFERENCES users(id);
    END IF;

    IF to_regclass('user_entitlements') IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE contype = 'f'
          AND conrelid = to_regclass('user_entitlements')
          AND confrelid = to_regclass('users')
    ) THEN
        ALTER TABLE user_entitlements
        ADD CONSTRAINT user_entitlements_user_id_fkey
        FOREIGN KEY (user_id) REFERENCES users(id);
    END IF;
END
$migration$;

COMMIT;
