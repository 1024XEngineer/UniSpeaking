import { recordTelemetry } from "./clientTelemetry.js";

const SAMPLE_INTERVAL_MS = 10_000;

export function summarizeRtcStats(report, previous = {}) {
  const stats = new Map();
  report?.forEach?.((item) => stats.set(item.id, item));
  let pair = [...stats.values()].find((item) => item.type === "transport" && item.selectedCandidatePairId);
  pair = pair?.selectedCandidatePairId ? stats.get(pair.selectedCandidatePairId) : pair;
  if (!pair || pair.type !== "candidate-pair") {
    pair = [...stats.values()].find((item) => item.type === "candidate-pair"
      && (item.selected || (item.nominated && item.state === "succeeded")));
  }
  const localCandidate = pair?.localCandidateId ? stats.get(pair.localCandidateId) : null;
  const remoteCandidate = pair?.remoteCandidateId ? stats.get(pair.remoteCandidateId) : null;
  const inbound = [...stats.values()].find((item) => item.type === "inbound-rtp"
    && (item.kind === "audio" || item.mediaType === "audio"));
  const packetsReceived = Number(inbound?.packetsReceived || 0);
  const packetsLost = Number(inbound?.packetsLost || 0);
  const receivedDelta = Math.max(0, packetsReceived - Number(previous.packetsReceived || 0));
  const lostDelta = Math.max(0, packetsLost - Number(previous.packetsLost || 0));
  const packetTotal = receivedDelta + lostDelta;

  return {
    attributes: {
      rtt_ms: Math.max(0, Number(pair?.currentRoundTripTime || 0) * 1_000),
      jitter_ms: Math.max(0, Number(inbound?.jitter || 0) * 1_000),
      packets_received: packetsReceived,
      packets_lost: packetsLost,
      packet_loss_pct: packetTotal ? (lostDelta / packetTotal) * 100 : 0,
      available_outgoing_bitrate: Math.max(0, Number(pair?.availableOutgoingBitrate || 0)),
      local_candidate_type: String(localCandidate?.candidateType || "unknown"),
      remote_candidate_type: String(remoteCandidate?.candidateType || "unknown"),
      network_type: String(localCandidate?.networkType || "unknown"),
      turn_used: localCandidate?.candidateType === "relay" || remoteCandidate?.candidateType === "relay",
    },
    totals: { packetsReceived, packetsLost },
  };
}

export function createRtcTelemetryMonitor(peer, { sessionId, model }) {
  const startedAt = performance.now();
  let previous = {};
  let stopped = false;
  let iceConnected = false;
  let timer = null;

  recordTelemetry("rtc.started", { attributes: { model } });

  const collect = async (eventType = "rtc.quality", severity = "INFO") => {
    if (stopped || !peer?.getStats) return;
    try {
      const summary = summarizeRtcStats(await peer.getStats(), previous);
      previous = summary.totals;
      recordTelemetry(eventType, {
        severity,
        sessionId: sessionId() || undefined,
        attributes: {
          ...summary.attributes,
          model,
          ice_state: String(peer.iceConnectionState || "unknown"),
          connection_state: String(peer.connectionState || "unknown"),
        },
      });
    } catch (error) {
      recordTelemetry("rtc.stats_failed", {
        severity: "WARN",
        sessionId: sessionId() || undefined,
        message: error instanceof Error ? error.message : "RTC getStats failed",
      });
    }
  };

  timer = window.setInterval(() => { void collect(); }, SAMPLE_INTERVAL_MS);
  return {
    stateChanged(kind, state) {
      const failed = state === "failed";
      recordTelemetry(`rtc.${kind}_state`, {
        severity: failed ? "ERROR" : state === "disconnected" ? "WARN" : "INFO",
        sessionId: sessionId() || undefined,
        message: failed ? `${kind} failed` : undefined,
        attributes: { state: String(state || "unknown"), model },
      });
	  if (kind === "ice" && !iceConnected && ["connected", "completed"].includes(state)) {
		iceConnected = true;
		recordTelemetry("rtc.ice_connected", {
		  sessionId: sessionId() || undefined,
		  attributes: { connect_duration_ms: performance.now() - startedAt, model },
		});
	  }
      if (failed) void collect("rtc.failure", "ERROR");
    },
    connected() {
      recordTelemetry("rtc.connected", {
        sessionId: sessionId() || undefined,
        attributes: { connect_duration_ms: performance.now() - startedAt, model },
      });
      void collect();
    },
    stop(reason = "unknown") {
      if (stopped) return;
      void collect("rtc.final_quality");
      stopped = true;
      if (timer) window.clearInterval(timer);
      timer = null;
      recordTelemetry("rtc.ended", {
        sessionId: sessionId() || undefined,
        attributes: { duration_ms: performance.now() - startedAt, reason, model },
      });
    },
  };
}
