# Self-hosted Umami

This deployment mirrors the production service at `/opt/services/umami`.
It runs Umami v3.3.0 under `/analytics` with a dedicated PostgreSQL database
and connects only the Umami application container to `deploy_default`.

Pinned upstream source:

```text
https://github.com/umami-software/umami
commit ba2aa48546534c55e9b8174667b4f266fe9d9ea2
```

Prepare the source and configuration:

```bash
mkdir -p src
curl -fsSL https://codeload.github.com/umami-software/umami/tar.gz/ba2aa48546534c55e9b8174667b4f266fe9d9ea2 \
  | tar -xz --strip-components=1 -C src
patch -d src -p1 < Dockerfile.alpine-mirror.patch
cp .env.example .env
```

Replace both placeholders in `.env` with random values. The database password
must use URL-safe characters because it is embedded in `DATABASE_URL`.

Start Umami after the main production stack has created `deploy_default`:

```bash
docker compose --env-file .env up -d --build
docker compose --env-file .env ps
docker compose --env-file .env logs --tail=200 umami
```

Validate the public route after testing and reloading the production Nginx
configuration:

```bash
curl -fsS https://unispeaking.qnsdk.com/analytics/api/heartbeat
curl -I https://unispeaking.qnsdk.com/analytics/script.js
```
