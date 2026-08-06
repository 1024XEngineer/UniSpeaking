# UniSpeaking production deployment

This runbook targets the single-server Docker deployment for `unispeaking.cn`.
It assumes Ubuntu 24.04, Docker Compose, PostgreSQL 17, and public TCP ports
80 and 443 mapped to the server.

## Prepare the server

Clone the merged `main` branch into `/opt/unispeaking`, then create the runtime
directories and install the Alibaba Cloud certificate files:

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

## Initialize a new database

The consolidated baseline is for an empty database only. Start PostgreSQL by
itself, import the baseline, and only then start the backend:

```bash
cd /opt/unispeaking
docker compose --env-file deploy/env/.env \
  -f deploy/docker-compose.prod.yml up -d postgres

docker compose --env-file deploy/env/.env \
  -f deploy/docker-compose.prod.yml exec postgres \
  pg_isready -U unispeaking -d unispeaking

docker compose --env-file deploy/env/.env \
  -f deploy/docker-compose.prod.yml exec -T postgres \
  psql -v ON_ERROR_STOP=1 -U unispeaking -d unispeaking \
  < deploy/postgres/unispeaking-baseline.sql
```

The runtime environment template sets `SPRING_FLYWAY_BASELINE_VERSION=8`.
This tells Flyway that the imported schema already contains V1 through V8.
Verify the import before starting the application:

```bash
docker compose --env-file deploy/env/.env \
  -f deploy/docker-compose.prod.yml exec postgres \
  psql -U unispeaking -d unispeaking -c \
  "SELECT COUNT(*) AS topics FROM ielts_topic; SELECT COUNT(*) AS questions FROM ielts_question;"
```

## Start and verify the application

```bash
docker compose --env-file deploy/env/.env \
  -f deploy/docker-compose.prod.yml up -d --build

docker compose --env-file deploy/env/.env \
  -f deploy/docker-compose.prod.yml ps

docker compose --env-file deploy/env/.env \
  -f deploy/docker-compose.prod.yml logs --tail=200 backend
```

The first backend startup creates the Flyway history table with baseline
version 8. It must not execute V1 through V8 again. Check the recorded state:

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

New Flyway migrations such as V9 run normally after the baseline at version 8.
Do not re-import `unispeaking-baseline.sql` into an existing production
database.
