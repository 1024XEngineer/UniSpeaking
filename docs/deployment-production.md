# UniSpeaking production deployment

This runbook targets the single-server Docker deployment for `unispeaking.cn`.
It assumes Ubuntu 24.04, Docker Compose, PostgreSQL 17, and public TCP ports
80 and 443 mapped to the server.

## Prepare the server

Clone the merged `main` branch into `/opt/unispeaking`, then create the runtime
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
both `unispeaking.cn` and `www.unispeaking.cn`.

Create the runtime environment file:

```bash
cd /opt/unispeaking
cp deploy/env/.env.prod.example deploy/env/.env
chmod 600 deploy/env/.env
nano deploy/env/.env
```

Replace every credential placeholder. Keep `DATABASE_URL` pointed at the
Compose service name `postgres`, not `localhost`.

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
Then verify Pages and Events for the four anonymous training modes: `SCENE`,
`FREE_CHAT`, `INTERVIEW`, and `IELTS`. Learning assets are reported separately;
no real user ID, transcript, audio, resume, job description, or credential is
sent to Umami.

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

Visit `https://unispeaking.cn` and verify registration, login, microphone
permission, WebSocket sessions, IELTS topics, and audio features.

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

Pull a reviewed commit from `main`, then rebuild the application:

```bash
cd /opt/unispeaking
git pull --ff-only origin main
docker compose --env-file deploy/env/.env \
  -f deploy/docker-compose.prod.yml up -d --build
```

New Flyway migrations run automatically during backend startup. Never edit a
migration that has already run in production, and never reinitialize an
existing production database with the `V1` baseline.
