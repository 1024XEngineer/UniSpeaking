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

The environment template selects the official Debian, Maven Central, PyPI, and
npm sources for the Singapore server. These values affect image builds only
and can be overridden when another deployment region needs different mirrors.
The Docker daemon registry mirror remains configured separately in
`/etc/docker/daemon.json` on the server. PaddleOCR uses its supported `bos`
model source by default.

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
committed migration in version order, currently `V1`, `V2`, then `V9`.

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
