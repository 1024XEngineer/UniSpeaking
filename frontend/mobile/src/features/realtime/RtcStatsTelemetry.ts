import type { TelemetryAttributes } from '@/infrastructure/telemetry/MobileTelemetry';

type RtcStatsItem = Record<string, unknown> & { id?: string; type?: string };

function numeric(value: unknown) {
  const parsed = Number(value || 0);
  return Number.isFinite(parsed) ? parsed : 0;
}

export function rtcStatsItems(report: unknown): RtcStatsItem[] {
  const items: RtcStatsItem[] = [];
  if (report && typeof (report as { forEach?: unknown }).forEach === 'function') {
    (report as { forEach(callback: (item: RtcStatsItem) => void): void })
      .forEach((item) => items.push(item));
  }
  return items;
}

export function summarizeRtcStats(
  report: unknown,
  previous: { packetsReceived?: number; packetsLost?: number } = {},
): { attributes: TelemetryAttributes; totals: { packetsReceived: number; packetsLost: number } } {
  const items = rtcStatsItems(report);
  const stats = new Map(items.filter((item) => item.id).map((item) => [item.id as string, item]));
  const transport = items.find((item) => item.type === 'transport' && item.selectedCandidatePairId);
  let pair = transport?.selectedCandidatePairId
    ? stats.get(String(transport.selectedCandidatePairId))
    : null;
  if (!pair) {
    pair = items.find((item) => item.type === 'candidate-pair'
      && (item.selected || (item.nominated && item.state === 'succeeded')));
  }
  const localCandidate = pair?.localCandidateId
    ? stats.get(String(pair.localCandidateId))
    : null;
  const remoteCandidate = pair?.remoteCandidateId
    ? stats.get(String(pair.remoteCandidateId))
    : null;
  const inbound = items.find((item) => item.type === 'inbound-rtp'
    && (item.kind === 'audio' || item.mediaType === 'audio'));
  const packetsReceived = numeric(inbound?.packetsReceived);
  const packetsLost = numeric(inbound?.packetsLost);
  const receivedDelta = Math.max(0, packetsReceived - (previous.packetsReceived || 0));
  const lostDelta = Math.max(0, packetsLost - (previous.packetsLost || 0));
  const packetTotal = receivedDelta + lostDelta;
  const localCandidateType = String(localCandidate?.candidateType || 'unknown');
  const remoteCandidateType = String(remoteCandidate?.candidateType || 'unknown');

  return {
    attributes: {
      rtt_ms: Math.max(0, numeric(pair?.currentRoundTripTime) * 1_000),
      jitter_ms: Math.max(0, numeric(inbound?.jitter) * 1_000),
      packets_received: packetsReceived,
      packets_lost: packetsLost,
      packet_loss_pct: packetTotal ? (lostDelta / packetTotal) * 100 : 0,
      available_outgoing_bitrate: Math.max(0, numeric(pair?.availableOutgoingBitrate)),
      local_candidate_type: localCandidateType,
      remote_candidate_type: remoteCandidateType,
      network_type: String(localCandidate?.networkType || 'unknown'),
      turn_used: localCandidateType === 'relay' || remoteCandidateType === 'relay',
    },
    totals: { packetsReceived, packetsLost },
  };
}
