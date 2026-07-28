# Realtime sequence

```mermaid
sequenceDiagram
  participant Browser
  participant Spring as Spring Boot
  participant Registry as AiProviderRegistry
  participant Provider as QwenRealtimeProvider
  participant Qwen as Qwen Realtime

  Browser->>Browser: Create RTCPeerConnection and offer SDP
  Browser->>Spring: Start session with offerSdp
  Spring->>Spring: Read permanent API key from server environment
  Spring->>Registry: Select provider by ProviderType
  Registry->>Provider: exchangeRealtimeSdp
  Provider->>Qwen: POST offer SDP + temporary Bearer token
  Qwen-->>Spring: Answer SDP
  Spring-->>Browser: localSessionId and answerSdp
  Browser->>Browser: setRemoteDescription(answerSdp)
  Browser->>Qwen: session.update over DataChannel
  Browser<<->>Qwen: Realtime audio and events
  Browser->>Spring: Report normalized lifecycle/transcript events
```

The permanent DashScope API key stays on the Spring server. The backend first
issues a short-lived Bearer token and then passes that token to `QwenRealtimeProvider`
for the SDP exchange.
