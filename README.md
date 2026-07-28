# UniSpeaking

UniSpeaking is organized as a monorepo:

```text
backend/unispeaking-server    Spring Boot business and realtime control plane
frontend/unispeaking-web      React web client
deploy                        Compose and nginx configuration
docs                          Architecture and business-flow documentation
```

## Backend structure

The backend uses four practical boundaries:

```text
access/web -> service -> domain
                 |
                 v
       service/port/out <- infrastructure
```

Services orchestrate use cases. Domain objects contain business
concepts and lifecycle behavior. Infrastructure contains all concrete storage
and vendor integrations. See [architecture](docs/architecture.md) for directory
responsibilities and dependency rules.

The current backend runs in development mode: missing users fall back to
`local-demo-user`, persistence is in memory, and completed-session transcripts
are summarized locally and merged into the user's memory for future prompts.
MyBatis-Plus entities and mapper methods are reserved but intentionally not
activated until a datasource is configured.

## Realtime flow

The browser creates a WebRTC offer and submits it to Spring Boot. Spring selects a
`RealtimeProvider`; the Qwen implementation obtains a credential and exchanges the
offer with Qwen signaling. Spring returns the answer SDP to the browser.

```text
Browser -> Spring Boot -> RealtimeProvider port -> Qwen adapter
Browser <- Spring Boot <- answer SDP               <- Qwen signaling
Browser <===============================> Qwen Realtime
```

## Run locally

```bash
cp deploy/env/.env.example deploy/env/.env
# Fill REALTIME_QWEN_API_KEY in deploy/env/.env
cd backend/unispeaking-server
./mvnw spring-boot:run
```

```bash
cd frontend/unispeaking-web
npm install
npm run dev
```

See [architecture](docs/architecture.md), [realtime sequence](docs/realtime-sequence.md),
and [deployment](docs/deployment.md).
