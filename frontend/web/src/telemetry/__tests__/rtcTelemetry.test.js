import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const recordTelemetry = vi.fn();
vi.mock("../clientTelemetry.js", () => ({ recordTelemetry }));

const { createRtcTelemetryMonitor, summarizeRtcStats } = await import("../rtcTelemetry.js");

function reportWithPair(overrides = {}) {
  return new Map([
    ["transport", { id: "transport", type: "transport", selectedCandidatePairId: "pair" }],
    ["pair", { id: "pair", type: "candidate-pair", currentRoundTripTime: 0.04, localCandidateId: "local", remoteCandidateId: "remote", ...overrides }],
    ["local", { id: "local", type: "local-candidate", candidateType: "host", networkType: "wifi" }],
    ["remote", { id: "remote", type: "remote-candidate", candidateType: "srflx" }],
    ["audio", { id: "audio", type: "inbound-rtp", kind: "audio", jitter: 0.002, packetsReceived: 100, packetsLost: 2 }],
  ]);
}

describe("rtcTelemetry", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(0);
    recordTelemetry.mockReset();
    vi.spyOn(performance, "now").mockReturnValue(0);
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it("summarizes selected transport stats and handles fallback candidate pairs", () => {
    const selected = summarizeRtcStats(reportWithPair({ availableOutgoingBitrate: 8000 }), { packetsReceived: 50, packetsLost: 1 });
    expect(selected.attributes).toMatchObject({
      rtt_ms: 40,
      jitter_ms: 2,
      packets_received: 100,
      packets_lost: 2,
      packet_loss_pct: 1.9607843137254901,
      available_outgoing_bitrate: 8000,
      local_candidate_type: "host",
      remote_candidate_type: "srflx",
      network_type: "wifi",
      turn_used: false,
    });

    const fallback = summarizeRtcStats(new Map([
      ["pair", { id: "pair", type: "candidate-pair", selected: true, state: "succeeded", nominated: true }],
      ["audio", { id: "audio", type: "inbound-rtp", mediaType: "audio", packetsReceived: 3, packetsLost: 0 }],
    ]));
    expect(fallback.attributes.local_candidate_type).toBe("unknown");
    expect(fallback.attributes.packet_loss_pct).toBe(0);
  });

  it("reports lifecycle events, quality collection, and ICE connection duration", async () => {
    let clock = 0;
    vi.spyOn(performance, "now").mockImplementation(() => clock);
    const sessionId = vi.fn(() => "session-1");
    const peer = {
      iceConnectionState: "connected",
      connectionState: "connected",
      getStats: vi.fn().mockResolvedValue(reportWithPair()),
    };
    const monitor = createRtcTelemetryMonitor(peer, { sessionId, model: "test-model" });
    expect(recordTelemetry).toHaveBeenCalledWith("rtc.started", { attributes: { model: "test-model" } });

    clock = 250;
    monitor.stateChanged("ice", "connected");
    monitor.stateChanged("ice", "completed");
    monitor.stateChanged("connection", "disconnected");
    monitor.connected();
    await vi.advanceTimersByTimeAsync(10_000);
    expect(recordTelemetry).toHaveBeenCalledWith("rtc.ice_connected", expect.objectContaining({
      sessionId: "session-1",
      attributes: expect.objectContaining({ connect_duration_ms: 250, model: "test-model" }),
    }));
    expect(recordTelemetry).toHaveBeenCalledWith("rtc.connection_state", expect.objectContaining({ severity: "WARN" }));
    expect(recordTelemetry).toHaveBeenCalledWith("rtc.quality", expect.objectContaining({
      sessionId: "session-1",
      attributes: expect.objectContaining({ model: "test-model", ice_state: "connected" }),
    }));

    clock = 1_250;
    monitor.stop("user_stop");
    monitor.stop("second_stop");
    await Promise.resolve();
    vi.clearAllTimers();
    expect(recordTelemetry).toHaveBeenCalledWith("rtc.final_quality", expect.anything());
    expect(recordTelemetry).toHaveBeenCalledWith("rtc.ended", expect.objectContaining({
      sessionId: "session-1",
      attributes: expect.objectContaining({ duration_ms: 1250, reason: "user_stop", model: "test-model" }),
    }));
    expect(peer.getStats).toHaveBeenCalled();
  });

  it("records failures and stats errors without throwing", async () => {
    const peer = {
      iceConnectionState: "failed",
      connectionState: "failed",
      getStats: vi.fn().mockRejectedValue(new Error("stats unavailable")),
    };
    const monitor = createRtcTelemetryMonitor(peer, { sessionId: () => "", model: "model" });
    expect(() => monitor.stateChanged("ice", "failed")).not.toThrow();
    await Promise.resolve();
    monitor.stop();
    await Promise.resolve();
    vi.clearAllTimers();
    expect(recordTelemetry).toHaveBeenCalledWith("rtc.ice_state", expect.objectContaining({ severity: "ERROR", message: "ice failed" }));
    expect(recordTelemetry).toHaveBeenCalledWith("rtc.stats_failed", expect.objectContaining({
      severity: "WARN",
      message: "stats unavailable",
    }));
  });

  it("stops sampling when the peer has no stats method", async () => {
    const monitor = createRtcTelemetryMonitor({}, { sessionId: () => "session", model: "model" });
    monitor.connected();
    monitor.stop();
    await Promise.resolve();
    vi.clearAllTimers();
    expect(recordTelemetry).toHaveBeenCalledWith("rtc.connected", expect.anything());
    expect(recordTelemetry).toHaveBeenCalledWith("rtc.ended", expect.anything());
  });
});
