# Deployment and configuration

The local deploy stack contains five services:

- `postgres`: local PostgreSQL database for development;
- `backend`: Spring Boot API with Qiniu RTI primary and Qwen fallback signaling;
- `frontend`: React client;
- `admin`: React/Vite governance console served at `/admin/`;
- `nginx`: reverse proxy for both frontends and `/backend/`/`/api/` API routes.

## Secret file

The repository contains `deploy/env/.env.example`. The working copy also uses
`deploy/env/.env`, which is ignored by Git.

Set the following variables in `deploy/env/.env` before starting a realtime
session or using profile avatars:

```properties
DASHSCOPE_API_KEY=replace-with-your-real-key
BAILIAN_WORKSPACE_ID=replace-with-your-workspace-id
BAILIAN_MODEL=qwen3.5-omni-flash-realtime
QINIU_RTI_API_KEY=replace-with-your-qiniu-rti-api-key
QINIU_RTI_APP_ID=unispeaking_001
QINIU_ACCESS_KEY=replace-with-your-access-key
QINIU_SECRET_KEY=replace-with-your-secret-key
QINIU_BUCKET=replace-with-your-private-bucket
QINIU_DOMAIN=https://replace-with-your-https-download-domain
QINIU_AVATAR_PREFIX=avatars
QINIU_SIGNED_URL_TTL=1h
PROFILE_TIME_ZONE=Asia/Shanghai
```

Do not put API keys in a `VITE_` variable because Vite embeds those values in
browser assets.

The Qiniu Kodo bucket must remain private. `QINIU_DOMAIN` must be the bucket's
HTTPS download domain and must not contain a path, query, or fragment. The
backend stores only the object key in PostgreSQL and returns a short-lived
signed download URL to the browser. Grant the configured Qiniu key only the
upload, download, and delete permissions required for this bucket.

## Profile database migration

Before deploying the profile feature to an existing database, apply:

```bash
psql "$DATABASE_URL" -f deploy/postgres/profile.sql
```

The migration adds `user.avatar_object_key` and the `practice_session` session
fact table. Check-in dates continue to be derived from persisted
`session_evaluation` reports, so no check-in table and no Redis data structure
are required. Learning duration is calculated from completed practice sessions;
sessions shorter than 30 seconds are excluded at query time. New databases use
the Flyway migrations in
`backend/unispeaking-server/src/main/resources/db/migration`.

The consolidated `V1__baseline.sql` creates `practice_session` automatically
for a fresh database. Environments that used the former multi-version migration
history must be rebuilt before adopting the consolidated baseline. Verify with:

```sql
SELECT column_name, data_type
FROM information_schema.columns
WHERE table_name = 'practice_session'
ORDER BY ordinal_position;
```

## Achievement database migration

The dynamic achievement system uses the existing practice, evaluation, and
scene tables as progress sources. It adds only the server-owned unlock and
notification state required to make the center-screen achievement notification
idempotent across refreshes and devices.

Spring Boot applies the achievement section in
`backend/unispeaking-server/src/main/resources/db/migration/V1__baseline.sql`
automatically for a fresh database. The section creates:

- `user_achievement_unlock`, keyed by `user_id + achievement_id`, containing
  the first unlock time and optional notification acknowledgement time;
- `user_achievement_state`, keyed by `user_id`, containing the first
  achievement initialization time.

The achievement section does not rewrite practice sessions, evaluation reports, scenes, or user
accounts. It does not add Redis, a message queue, an achievement definition
table, or a client-controlled progress field.

Before deploying V4 to an existing environment:

1. Back up PostgreSQL using the environment's normal backup procedure.
2. Deploy the backend and allow Flyway to finish before deploying the frontend.
3. Verify that Flyway reports V4 as successful and that both tables and the
   pending-notification index exist.
4. Deploy the frontend only after `GET /api/achievements` and
   `POST /api/achievement-unlocks` are available.

Example verification queries:

```sql
SELECT version, description, success
FROM flyway_schema_history
WHERE version = '4';

SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'public'
  AND table_name IN (
      'user_achievement_unlock',
      'user_achievement_state'
  )
ORDER BY table_name;

SELECT indexname
FROM pg_indexes
WHERE schemaname = 'public'
  AND tablename = 'user_achievement_unlock'
ORDER BY indexname;
```

Historical achievement initialization is application-driven rather than a
bulk DDL backfill. On a user's first authenticated unlock synchronization, the
backend calculates already-satisfied milestones, stores them with both
`unlocked_at` and `acknowledged_at`, and creates the user's initialization
state. Those historical milestones do not generate a notification burst.
Achievements reached after initialization are stored with a null
`acknowledged_at` until the browser confirms that the notification was shown.

Newly registered users must complete one unlock synchronization before they can
begin a learning flow. That initial synchronization creates an initialization
state even when the user has not unlocked a milestone.

The V4 schema is backward compatible with the pre-achievement application: an
older backend ignores the new tables. If an application rollback is necessary,
roll back the frontend and backend binaries but retain the V4 tables so unlock
and acknowledgement history is not lost. Drop the tables only through a later,
explicit Flyway migration after the feature has been permanently retired.

## Available settings

The Spring Boot defaults live in
`backend/unispeaking-server/src/main/resources/application.yaml`. Local secrets
and machine-specific values live in `deploy/env/.env`.

```yaml
realtime:
  qiniu:
    base-url: ${QINIU_RTI_BASE_URL:https://miku-rtic.qiniuapi.com}
    api-key: ${QINIU_RTI_API_KEY:}
    app-id: ${QINIU_RTI_APP_ID:unispeaking_001}
    model-profile: ${QINIU_RTI_MODEL_PROFILE:qwen3.5-omni-plus-realtime}
    role-profile: ${QINIU_RTI_ROLE_PROFILE:default_assistant}
    voice-profile: ${QINIU_RTI_VOICE_PROFILE:Tina}
    voice-mappings:
      Katerina: ${QINIU_RTI_VOICE_KATERINA:Tina}
      Aiden: ${QINIU_RTI_VOICE_AIDEN:Ethan}
      Raymond: ${QINIU_RTI_VOICE_RAYMOND:Ethan}
      Tina: ${QINIU_RTI_VOICE_TINA:Tina}
      Harvey: ${QINIU_RTI_VOICE_HARVEY:Ethan}
      Dolce: ${QINIU_RTI_VOICE_DOLCE:Cherry}
      Mione: ${QINIU_RTI_VOICE_MIONE:Cindy}
      Maia: ${QINIU_RTI_VOICE_MAIA:Serena}
    client-transport: ${QINIU_RTI_CLIENT_TRANSPORT:platform_rtc}
    region: ${QINIU_RTI_REGION:cn-east}
    read-timeout: ${QINIU_RTI_READ_TIMEOUT:20s}
    max-response-bytes: ${QINIU_RTI_MAX_RESPONSE_BYTES:1048576}
  qwen:
    api-key: ${DASHSCOPE_API_KEY:}
    workspace-id: ${BAILIAN_WORKSPACE_ID:}
    model: ${BAILIAN_MODEL:qwen3.5-omni-flash-realtime}
    region: ${BAILIAN_REGION:cn-beijing}
    temporary-key-endpoint: ${REALTIME_QWEN_TEMPORARY_KEY_ENDPOINT:https://dashscope.aliyuncs.com/api/v1/tokens}
    temporary-key-ttl-seconds: ${REALTIME_QWEN_TEMPORARY_KEY_TTL_SECONDS:300}
    connect-timeout: ${REALTIME_QWEN_CONNECT_TIMEOUT:10s}
    read-timeout: ${REALTIME_QWEN_READ_TIMEOUT:20s}
    max-answer-bytes: ${REALTIME_QWEN_MAX_ANSWER_BYTES:1048576}

profile:
  time-zone: ${PROFILE_TIME_ZONE:Asia/Shanghai}

object-storage:
  qiniu:
    access-key: ${QINIU_ACCESS_KEY:}
    secret-key: ${QINIU_SECRET_KEY:}
    bucket: ${QINIU_BUCKET:}
    domain: ${QINIU_DOMAIN:}
    avatar-prefix: ${QINIU_AVATAR_PREFIX:avatars}
    signed-url-ttl: ${QINIU_SIGNED_URL_TTL:1h}
```

The default realtime route is:

```properties
AI_PROVIDER_ROUTE_REALTIME=qwen3.5-omni-plus-realtime,qwen3.5-omni-flash-realtime
```

For Qiniu RTI, the backend maps the selected UniSpeaking teacher or examiner
voice to a Qiniu profile voice, validates the model, role, resolved voice, and
transport against Profiles, creates an RTI Session, and submits the browser SDP
as JSON to the returned HTTPS media endpoint. Qiniu-native voice IDs can also be
used directly when Profiles exposes them. It stores only the provider
`session_id`, model, provider, and `trace_id`; neither the permanent API key nor
the short-lived media token is persisted or returned to the client. Terminal
session paths call Qiniu Stop on a best-effort basis.

The fallback Qwen WebRTC SDP endpoint is derived automatically:

```text
https://{BAILIAN_WORKSPACE_ID}.{BAILIAN_REGION}.maas.aliyuncs.com/api/v1/webrtc/realtime?model={BAILIAN_MODEL}
```

Spring validates durations, temporary-key TTL, endpoint format, and maximum
response size during startup. The API key and workspace may remain empty while
running non-realtime tests or development features. Starting a realtime session
without them returns `QWEN_CREDENTIAL_MISSING` or `QWEN_SIGNALING_URL_MISSING`.

For the fallback route, the backend requests a short-lived DashScope token with `DASHSCOPE_API_KEY`,
then uses that temporary Bearer credential for the Qwen Offer SDP to Answer SDP
exchange.

## Local backend

When Maven is run from `backend/unispeaking-server`, Spring automatically loads:

```text
../../deploy/env/.env
```

The path can be overridden:

```bash
UNISPEAKING_ENV_FILE=/absolute/path/to/runtime.env ./mvnw spring-boot:run
```

Environment variables exported by the shell override values from the file.

## Docker Compose

Run:

```bash
cd deploy
docker compose --env-file env/.env up --build
```

Compose passes `env/.env` into the backend container. `VITE_BACKEND_URL` is a
frontend build-time value and defaults to `/backend`; it is used by both the
user authentication flow and the admin console. Both REST requests and
the authenticated session WebSocket preserve this prefix, so nginx routes them
to the backend service.

After Spring returns the answer SDP, the browser establishes its WebRTC peer
connection directly with Qwen.
