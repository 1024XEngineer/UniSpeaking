# Deployment and configuration

The deploy stack contains three services:

- `backend`: Spring Boot API and Qwen signaling integration;
- `frontend`: React client;
- `nginx`: reverse proxy for the frontend and `/backend/` API route.

## Secret file

The repository contains `deploy/env/.env.example`. The working copy also uses
`deploy/env/.env`, which is ignored by Git.

Set the following variables in `deploy/env/.env` before starting a realtime
session:

```properties
DASHSCOPE_API_KEY=replace-with-your-real-key
BAILIAN_WORKSPACE_ID=replace-with-your-workspace-id
BAILIAN_MODEL=qwen3.5-omni-flash-realtime
```

Do not put API keys in a `VITE_` variable because Vite embeds those values in
browser assets.

## Available settings

The Spring Boot defaults live in
`backend/unispeaking-server/src/main/resources/application.yaml`. Local secrets
and machine-specific values live in `deploy/env/.env`.

```yaml
realtime:
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
```

The WebRTC SDP endpoint is derived automatically:

```text
https://{BAILIAN_WORKSPACE_ID}.{BAILIAN_REGION}.maas.aliyuncs.com/api/v1/webrtc/realtime?model={BAILIAN_MODEL}
```

Spring validates durations, temporary-key TTL, endpoint format, and maximum
response size during startup. The API key and workspace may remain empty while
running non-realtime tests or development features. Starting a realtime session
without them returns `QWEN_CREDENTIAL_MISSING` or `QWEN_SIGNALING_URL_MISSING`.

The backend requests a short-lived DashScope token with `DASHSCOPE_API_KEY`,
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
frontend build-time value and defaults to `/backend`.

After Spring returns the answer SDP, the browser establishes its WebRTC peer
connection directly with Qwen.
