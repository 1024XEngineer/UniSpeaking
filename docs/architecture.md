# Backend architecture

This document describes the structure that the code currently follows. It is a
maintenance guide, not a reason to add layers that the product does not need.

## Dependency direction

```text
access/web
    |
    v
service -------------> domain
    |
    v
service/port/out      <--------- infrastructure
```

- `access` translates HTTP into service inputs and translates results back
  into HTTP responses.
- `service` coordinates complete use cases.
- `domain` owns business concepts, state, and rules that remain meaningful
  without Spring, HTTP, Qwen, or a database.
- `service/port/out` describes capabilities required from external systems.
- `infrastructure` implements those capabilities using a concrete technology or
  vendor.

`domain` must not import `access`, `service`, or `infrastructure`.
`service` must not import `access` or `infrastructure`.

## Directory responsibilities

```text
com/unispeaking/
├── access/
│   └── web/
│       ├── advice/       HTTP exception mapping
│       ├── assembler/    request/response and service/domain conversion
│       ├── controller/   routes, validation, authentication context
│       ├── request/      inbound JSON contracts
│       └── response/     outbound JSON contracts and response envelope
│
├── service/
│   ├── auth/             authentication use cases
│   ├── conversation/     conversation use cases
│   ├── evaluation/       evaluation and practice-audio use cases
│   ├── exception/        errors raised while executing use cases
│   ├── learning/         learning flow, records, reports, and mistakes
│   ├── memory/           session summary and user-memory update workflow
│   ├── membership/       membership use cases
│   ├── profile/          profile use cases
│   ├── prompt/           prompt construction use cases
│   ├── quota/            usage quota use cases
│   ├── realtime/         realtime connection and event orchestration
│   ├── scene/            scene use cases and their command/result types
│   ├── session/          session use cases and their command/result types
│   └── port/
│       └── out/
│           ├── ai/       AI and realtime provider contracts
│           ├── cache/    transient state contracts
│           ├── persistence/ durable domain persistence contracts
│           └── storage/  binary/object storage contracts
│
├── domain/
│   ├── conversation/     messages and speaker concepts
│   ├── learning/         evaluation, progress, mistakes, and reports
│   ├── profile/          user profile model
│   ├── prompt/           provider-independent prompt concepts
│   ├── realtime/         realtime events and connection concepts
│   ├── scene/            scenes, configuration, content, and status
│   └── session/          session model, status, and lifecycle behavior
│
└── infrastructure/
    ├── ai/
    │   ├── local/        local fallback for summary and memory merging
    │   └── qwen/         Qwen adapters, HTTP protocol, credentials, properties
    ├── config/           shared Spring and JDK client configuration
    ├── persistence/
    │   ├── inmemory/     current non-durable development adapters
    │   └── mybatisplus/
    │       ├── entity/   reserved table mappings
    │       └── mapper/   reserved BaseMapper and business query methods
    └── serialization/    technical serialization helpers
```

An `impl` package under a service feature contains the current use-case
implementation. Do not create an interface and `impl` class automatically:
keep the interface only when it is a useful inbound boundary, has multiple
implementations, or is deliberately substituted in tests.

## Shared service contracts

Inheritance is used only where specialized services share the same contract and
implementation skeleton:

```text
SessionService
├── FreeChatSessionService
└── CustomSceneSessionService
    implementations -> AbstractSceneSessionService

SceneService
├── FreeChatSceneService
└── CustomSceneService
    implementations -> AbstractSceneService

ScenePromptService<T>
├── FreeChatPromptService
└── CustomScenePromptService
    implementations -> AbstractScenePromptService<T>

ConversationService
├── FreeChatConversationService
└── CustomSceneConversationService
    implementations -> AbstractConversationService

RealtimeEventService
├── FreeChatRealtimeEventService
└── CustomSceneRealtimeEventService
    implementations -> AbstractRealtimeEventService
```

The abstract implementations contain shared algorithms; the specialized
interfaces expose feature-specific extension points. Services such as auth,
quota, evaluation, profile, scene content, and learning records remain
independent because they do not represent specializations of one another.

## Service versus domain

A service answers “in what order does this use case run?” For example:

```text
authenticate user
-> check quota
-> load scene
-> build prompt
-> create session
-> call realtime provider
-> save session state
```

A domain object answers “what is valid for this business concept?” For example,
a session owns its lifecycle state and should eventually enforce which state
transitions are legal.

If a concept only carries request or result data, it belongs in its service
feature rather than in `domain`. If an object has no stable business meaning and
only mirrors Qwen, HTTP, Redis, or a database schema, it belongs in an adapter.

## Adapter naming

Names must describe the implementation that actually exists:

- an adapter backed by `ConcurrentHashMap` belongs in `persistence/inmemory` and
  starts with `InMemory`;
- a `redis` package is created only after the implementation uses Redis;
- a `mybatis` package is created only after MyBatis entities and mappers are
  connected to repository adapters;
- vendor-specific code such as Qwen never appears in `service` or general
  domain packages.

The current service uses in-memory persistence. Data is lost on restart.

MyBatis-Plus is reserved through its core API only. The mapper interfaces are
not scanned and no datasource is initialized. When a database is available:

1. replace `mybatis-plus-core` with `mybatis-plus-spring-boot4-starter`;
2. add the JDBC driver and datasource properties;
3. enable `@MapperScan` for `infrastructure.persistence.mybatisplus.mapper`;
4. implement mapper SQL for the declared business methods;
5. add repository adapters that translate between domain models and entities;
6. select the database adapters instead of the `inmemory` adapters.

The provisional table names and fields must be aligned with the final database
schema before mapper scanning is enabled.

## Development mode

Login, registration, database persistence, Redis, and production authorization
are not enabled yet.

- When a request provides `userId`, the service uses that value.
- When `userId` is absent, the service uses `local-demo-user`.
- Scene access checks currently do not reject requests.
- Profiles, sessions, conversations, scenes, learning data, and memory are held
  in process memory and are lost when the backend restarts.

These are explicit development fallbacks. Production authentication and
persistence should replace their adapters without changing the session use
cases.

## User memory flow

```text
session completes
-> load the session transcript
-> create a bounded session summary
-> merge the summary with the user's existing memory
-> save the updated in-memory profile
-> include that memory in prompts for later sessions
```

The first profile starts with an empty memory. A session with no transcript does
not create artificial memory. Completion is idempotent, so duplicate completion
events do not append the same summary twice.

`ConversationMemoryProvider` is the replaceable boundary. The current local
adapter creates a deterministic bounded transcript summary and retains at most
4,000 characters of merged memory. It can later be replaced by a Qwen
summarization adapter.

## Realtime flow

The browser creates a WebRTC offer and submits it to the backend. The
service selects an `AiProvider` through `AiProviderRegistry`; the Qwen infrastructure provider
exchanges the SDP with Qwen signaling. After negotiation, browser audio and
DataChannel traffic flow directly between the browser and Qwen Realtime.

Qwen credentials remain in the backend and are never returned to the browser.
For Alibaba WebRTC, the backend uses the permanent API key directly as the
Bearer credential of the SDP request. It does not manufacture a temporary
expiry time or return a temporary token to the browser. AOQ is a separate
protocol whose gateway response includes a temporary client token.
