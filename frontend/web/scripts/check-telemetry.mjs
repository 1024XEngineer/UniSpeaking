import assert from "node:assert/strict";
import test from "node:test";

import { flushTelemetry, recordTelemetry } from "../src/telemetry/clientTelemetry.js";
import { summarizeRtcStats } from "../src/telemetry/rtcTelemetry.js";

test("client telemetry works when a test window has no location", async () => {
  const previousWindow = globalThis.window;
  const previousFetch = globalThis.fetch;
  const requests = [];
  globalThis.window = {
    localStorage: { getItem: () => null, setItem: () => {} },
    sessionStorage: { getItem: () => null, setItem: () => {} },
  };
  globalThis.fetch = async (url, options) => {
    requests.push({ url, options });
    return new Response(null, { status: 202 });
  };

  try {
    recordTelemetry("web.test");
    await flushTelemetry();

    assert.equal(requests.length, 1);
    const payload = JSON.parse(requests[0].options.body);
    assert.equal(payload.events[0].route, "/");
  } finally {
    globalThis.window = previousWindow;
    globalThis.fetch = previousFetch;
  }
});

test("RTC telemetry calculates interval loss and TURN usage", () => {
  const report = new Map([
    ["transport", { id: "transport", type: "transport", selectedCandidatePairId: "pair" }],
    ["pair", {
      id: "pair",
      type: "candidate-pair",
      currentRoundTripTime: 0.08,
      localCandidateId: "local",
      remoteCandidateId: "remote",
    }],
    ["local", { id: "local", type: "local-candidate", candidateType: "relay", networkType: "wifi" }],
    ["remote", { id: "remote", type: "remote-candidate", candidateType: "srflx" }],
    ["audio", {
      id: "audio",
      type: "inbound-rtp",
      kind: "audio",
      jitter: 0.01,
      packetsReceived: 195,
      packetsLost: 5,
    }],
  ]);

  const result = summarizeRtcStats(report, { packetsReceived: 100, packetsLost: 0 });

  assert.equal(result.attributes.rtt_ms, 80);
  assert.equal(result.attributes.jitter_ms, 10);
  assert.equal(result.attributes.turn_used, true);
  assert.equal(result.attributes.packet_loss_pct, 5);
});
