# UniSpeaking production deployment

> This document describes the legacy/manual source-build procedure. New production releases must use [`docs/deployment-cd.md`](deployment-cd.md): GitHub Actions builds private GHCR images and the server only pulls and runs a pinned SHA. Do not use `git pull` or `up -d --build` for CD releases.

This runbook targets the single-server Docker deployment for `unispeaking.qnsdk.com`.
It assumes Ubuntu 24.04, Docker Compose, PostgreSQL 17, and public TCP ports
80 and 443 mapped to the server.

## Prepare the server

For the legacy/manual procedure only, clone the merged `main` branch into `/opt/unispeaking`, then create the runtime
directories and install the certificate files:

```bash
sudo mkdir -p /opt/unispeaking/backups/postgres /etc/unispeaking/certs
sudo chmod 700 /etc/unispeaking/certs
sudo cp fullchain.pem /etc/unispeaking/certs/fullchain.pem
sudo cp privkey.pem /etc/unispeaking/certs/privkey.pem
sudo chmod 644 /etc/unispeaking/certs/fullchain.pem
sudo chmod 600 /etc/unispeaking/certs/privkey.pem
```

The certificate files must not be committed to Git. The certificate must cover
`unispeaking.qnsdk.com`.

Create the runtime environment file:

```bash
cd /opt/unispeaking
cp deploy/env/.env.prod.example deploy/env/.env
chmod 600 deploy/env/.env
nano deploy/env/.env
```

Replace every credential placeholder. Keep `DATABASE_URL` pointed at the
Compose service name `postgres`, not `localhost`.

### Configure Qiniu MaaS LLM

Set the permanent MaaS credential and keep the default two-model route in
`deploy/env/.env`:

```dotenv
QINIU_MAAS_BASE_URL=https://api.qnaigc.com/v1
QINIU_MAAS_API_KEY=replace-with-qiniu-maas-api-key
QINIU_MAAS_PRIMARY_MODEL=qwen/qwen3.5-plus
QINIU_MAAS_FALLBACK_MODEL=deepseek/deepseek-v4-flash
AI_PROVIDER_ROUTE_LLM=qwen/qwen3.5-plus,qwen3.5-plus
```

The alternative trusted base URL is `https://openai.sufy.com/v1`. Do not put
the MaaS API key in a `VITE_` variable, command output, image build argument, or
committed file. Keep the existing DashScope and DeepSeek credentials only when
their direct providers are needed for an explicit rollback.

### Enable Umami Cloud for the migrated Web domain

The Web tracker is disabled by default. For the current migrated frontend at
`https://unispeaking.qnsdk.com`, verify that the Umami Cloud Website named
`unispeaking` still uses Website ID
`3ae2dee9-d585-43a9-93f3-fcafcd14b258`, then set these build-time values in
`deploy/env/.env`:

```dotenv
VITE_UMAMI_ENABLED=true
VITE_UMAMI_SCRIPT_URL=https://cloud.umami.is/script.js
VITE_UMAMI_WEBSITE_ID=3ae2dee9-d585-43a9-93f3-fcafcd14b258
VITE_UMAMI_DOMAINS=unispeaking.qnsdk.com
```

These values are compiled into the public Web image. Do not put an Umami login
password, API token, user identifier, or any server credential in a `VITE_`
variable. Changing any value requires rebuilding the `frontend` image:

```bash
docker compose --env-file deploy/env/.env \
  -f deploy/docker-compose.prod.yml build --no-cache frontend
docker compose --env-file deploy/env/.env \
  -f deploy/docker-compose.prod.yml up -d frontend nginx
```

After release, open the migrated site and confirm the visit in Umami Realtime.
Then verify Pages and Events for the four training modes: `SCENE`, `FREE_CHAT`,
`INTERVIEW`, and `IELTS`. Learning assets are reported separately. Authenticated
Web and mobile events use the same backend user UUID as the Umami Distinct ID;
no email, nickname, transcript, audio, resume, job description, JWT, password,
or other credential is sent to Umami.

### Add the optional TURN UDP 443 relay

TURN is an optional network fallback for WebRTC. It is disabled by default and
the normal ICE policy remains `all`, so enabling the code does not change direct
connections until the operator changes the rollout settings. The relay is
useful when a campus or enterprise network blocks direct UDP candidates; it
does not solve a network that blocks both UDP and TCP/TLS, and this deployment
intentionally starts with UDP 443 only.

Before enabling it, complete these cloud-side changes without changing the
application container:

1. Create or verify DNS `turn.unispeaking.qnsdk.com` pointing to the server public IP.
2. Allow inbound **UDP 443** and **UDP 49160-49200** in the cloud security
   group. Keep TCP 443 assigned to Nginx; Coturn uses host networking and UDP
   443 only.
3. Confirm the server private IP and public IP mapping. Do not use a private IP
   in `TURN_PUBLIC_IP`.

Generate a new shared secret on the server or an operator workstation and put
it only in the untracked `deploy/env/.env`:

```bash
openssl rand -base64 48
```

Set the following values, keeping the rollout at zero initially:

```dotenv
VITE_REALTIME_ICE_TRANSPORT_POLICY=all
VITE_REALTIME_TURN_ENABLED=true
TURN_ENABLED=true
TURN_URLS=turn:turn.unispeaking.qnsdk.com:443?transport=udp
TURN_SHARED_SECRET=replace-with-a-random-secret
TURN_REALM=turn.unispeaking.qnsdk.com
TURN_PUBLIC_IP=replace-with-public-ip
TURN_PRIVATE_IP=replace-with-private-ip
TURN_CREDENTIAL_TTL=5m
TURN_ROLLOUT_PERCENTAGE=0
TURN_RELAY_TEST_USER_IDS=replace-with-an-authenticated-test-user-uuid
```

Build and start the relay through Compose so the server is changed only from a
reviewed Git commit:

```bash
docker compose --env-file deploy/env/.env \
  -f deploy/docker-compose.prod.yml --profile turn up -d turn
docker compose --env-file deploy/env/.env \
  -f deploy/docker-compose.prod.yml up -d --build backend frontend nginx
docker compose --env-file deploy/env/.env \
  -f deploy/docker-compose.prod.yml logs --tail=100 turn backend
```

For the relay-only acceptance build, temporarily set
`VITE_REALTIME_ICE_TRANSPORT_POLICY=relay` and keep the frontend TURN flag
enabled. Log in as the allowlisted test user, establish one conversation, and
confirm the browser's redacted connection diagnostic reports `relay` and UDP.
Do not copy the ICE username, credential, full SDP, or candidate IPs into an
Issue or log attachment. Restore the policy to `all` after the relay test.

Roll out gradually by changing only `TURN_ROLLOUT_PERCENTAGE` and rebuilding
the backend/frontend when required: `0 → 5 → 25 → 100`. At every step record
success/failure rates and test at least one ordinary public network, one campus
network, and one enterprise/VPN network. If TURN is unavailable, the normal
client path falls back to the existing direct ICE configuration; relay-only
test mode fails explicitly so the deployment is not mistaken for a passing
fallback.

Build the mobile app with the same public Website ID:

```dotenv
EXPO_PUBLIC_UMAMI_ENABLED=true
EXPO_PUBLIC_UMAMI_ENDPOINT=https://cloud.umami.is/api/send
EXPO_PUBLIC_UMAMI_WEBSITE_ID=3ae2dee9-d585-43a9-93f3-fcafcd14b258
```

Do not create a second Umami Website for mobile. The native client sends a
valid app User-Agent and the production hostname `unispeaking.qnsdk.com` to
`/api/send`; no Umami login or API token is embedded in the app.

The environment template selects mirrors reachable from mainland China for
Docker images, Debian packages, Maven, PyPI, and npm. The committed defaults
use DaoCloud for Docker images, Alibaba Cloud for Debian/Maven/PyPI, and
npmmirror for npm. Override an individual value in `deploy/env/.env` only when
that mirror is unavailable. PaddleOCR uses its supported `bos` model source.

The image prefix in Compose avoids direct Docker Hub access. A Docker daemon
registry mirror can additionally be configured in `/etc/docker/daemon.json`
for ad-hoc `docker pull` commands that still use Docker Hub image names:

```json
{
  "registry-mirrors": ["https://docker.m.daocloud.io"]
}
```

After changing the daemon configuration, validate and restart Docker:

```bash
sudo dockerd --validate --config-file=/etc/docker/daemon.json
sudo systemctl restart docker
docker info | sed -n '/Registry Mirrors/,+3p'
```

## Initialize a new database

Start PostgreSQL by itself and verify that the new database is healthy:

```bash
cd /opt/unispeaking
docker compose --env-file deploy/env/.env \
  -f deploy/docker-compose.prod.yml up -d postgres

docker compose --env-file deploy/env/.env \
  -f deploy/docker-compose.prod.yml exec postgres \
  pg_isready -U unispeaking -d unispeaking

docker compose --env-file deploy/env/.env \
  -f deploy/docker-compose.prod.yml exec postgres \
  psql -U unispeaking -d unispeaking -c '\dt'
```

An empty database is expected to report `Did not find any relations.` at this
point. Do not import a separate schema file and do not configure a Flyway
baseline version. The first backend startup automatically executes every
committed migration in version order, including `V10` for the shared user
identity, email sessions, entitlements, and admin sessions, `V11` for the
provider-session identifier used to bind official Alibaba SLS usage, `V12` for
official inference-usage records retained by this backend, and `V13` for the
unique provider-session binding index. Before applying `V13` to an existing
database, check for duplicate values and resolve them explicitly:

```sql
SELECT provider_session_id, COUNT(*)
FROM practice_session
WHERE provider_session_id IS NOT NULL
GROUP BY provider_session_id
HAVING COUNT(*) > 1;
```

The migration intentionally fails rather than guessing which user should own
a duplicated provider session.

## Start and verify the application

For an existing production database, create a full backup before rebuilding any
application container. Record row counts for account-owned tables before and
after the release. The `V2__ai_provider_tables.sql` migration is additive: it
creates or completes only `ai_providers`, `ai_models`, and
`ai_model_invocations`, preserves invocation history, and never reads or writes
account, session, entitlement, achievement, or profile tables.

```bash
mkdir -p backups/postgres
docker compose --env-file deploy/env/.env \
  -f deploy/docker-compose.prod.yml exec -T postgres \
  pg_dump -U unispeaking -d unispeaking -Fc \
  > "backups/postgres/pre-release-$(date -u +%Y%m%dT%H%M%SZ).dump"
```

```bash
docker compose --env-file deploy/env/.env \
  -f deploy/docker-compose.prod.yml up -d --build

docker compose --env-file deploy/env/.env \
  -f deploy/docker-compose.prod.yml ps

docker compose --env-file deploy/env/.env \
  -f deploy/docker-compose.prod.yml logs --tail=200 backend
```

The first backend startup creates the schema and Flyway history table. Check
that every committed migration succeeded:

```bash
docker compose --env-file deploy/env/.env \
  -f deploy/docker-compose.prod.yml exec postgres \
  psql -U unispeaking -d unispeaking -c \
  "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;"
```

Visit `https://unispeaking.qnsdk.com` and verify registration, login, microphone
permission, WebSocket sessions, IELTS topics, and audio features.

## Verify Java monitoring and logs

The production backend mounts the OpenTelemetry Java Agent from
`/opt/monitoring/opentelemetry-javaagent.jar`. It sends JVM, HTTP, and trace
telemetry to `otel-collector:4318` on the private `monitoring_default` network.
Application logs remain on stdout/stderr and are collected from Docker by
Grafana Alloy into Loki. The Actuator health and Prometheus endpoints are
enabled for internal diagnostics; Nginx blocks public `/backend/actuator/`
requests.

After rebuilding the backend, verify the agent, telemetry target, logs, and
metrics before opening Grafana:

```bash
docker compose --env-file deploy/env/.env \
  -f deploy/docker-compose.prod.yml logs --tail=200 backend

curl -fsS 'http://127.0.0.1:9090/api/v1/query?query=jvm_memory_used_bytes'
curl -fsS 'http://127.0.0.1:9090/api/v1/query?query=http_server_request_duration_seconds_count'
curl -fsS 'http://127.0.0.1:3001/api/health'
```

In Grafana, confirm that the UniSpeaking Java dashboard shows JVM memory,
threads, GC, and HTTP request metrics, and that the UniSpeaking Logs dashboard
returns `{service="backend"}` entries. A public request to
`https://unispeaking.qnsdk.com/backend/actuator/prometheus` must return 404.

Then verify the LLM migration with one request from each business path:

- translate a FreeChat subtitle;
- generate and translate a custom scene;
- generate IELTS text evaluation/report content while confirming that iFlytek
  pronunciation scoring is unchanged;
- prepare interview material, advance topics, and generate the report.

Confirm that Qiniu MaaS records the requests, application logs identify
`capability=LLM provider=qiniu-maas`, and the direct DashScope/DeepSeek accounts
do not record new LLM calls. Realtime voice sessions, ASR, TTS, and iFlytek
scoring must continue to use their existing routes.

## Backups

Run the backup script manually once:

```bash
/opt/unispeaking/deploy/postgres/backup-postgres.sh
```

Schedule it from root's crontab after confirming the manual backup succeeds:

```cron
0 3 * * * /opt/unispeaking/deploy/postgres/backup-postgres.sh >> /var/log/unispeaking-postgres-backup.log 2>&1
```

Copy backups to another machine or object storage. The Docker volume is not a
backup. Never use `docker compose down -v` on the production database.

## Later releases

For the CD-managed production environment, use `docs/deployment-cd.md`. The commands below are retained only for legacy/manual environments and must not be used against the CD-managed server. The CD-managed release path uses a reviewed commit SHA, GHCR `pull`, and the fixed server entry point. Do not run `git pull` or `up -d --build` on a CD-managed production server.

New Flyway migrations run automatically during backend startup. Never edit a
migration that has already run in production, and never reinitialize an
existing production database with the `V1` baseline.
