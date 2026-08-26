import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

import { flushTelemetry, isOptionalTelemetryResource, recordTelemetry } from "../src/telemetry/clientTelemetry.js";
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

test("optional analytics failures are distinguishable from application resources", () => {
  assert.equal(isOptionalTelemetryResource({ hasAttribute: (name) => name === "data-umami-tracker" }), true);
  assert.equal(isOptionalTelemetryResource({ hasAttribute: () => false }), false);
});

test("web root prevents automatic translation from mutating React-owned DOM", async () => {
  const html = await readFile(new URL("../index.html", import.meta.url), "utf8");
  assert.match(html, /<html[^>]*translate="no"[^>]*class="notranslate"/);
  assert.match(html, /<div id="root"[^>]*class="notranslate"[^>]*translate="no"/);
});

test("production web sources avoid Array.at for legacy browser compatibility", async () => {
  const sourceFiles = [
    "../src/telemetry/clientTelemetry.js",
    "../src/analytics/pageCatalog.js",
    "../src/component/ielts/IeltsModule.jsx",
    "../src/component/interview/InterviewModule.jsx",
  ];
  for (const sourceFile of sourceFiles) {
    const source = await readFile(new URL(sourceFile, import.meta.url), "utf8");
    assert.doesNotMatch(source, /\.at\s*\(/, `${sourceFile} must not require Array.prototype.at`);
  }
});

test("web nginx serves teacher previews with registered audio MIME types", async () => {
  const nginx = await readFile(new URL("../nginx.conf", import.meta.url), "utf8");
  assert.match(nginx, /include \/etc\/nginx\/mime\.types;/);
  assert.match(nginx, /audio\/wav\s+wav;/);
  assert.match(nginx, /wav\|mp3\|m4a\|aac\|ogg/);
});
