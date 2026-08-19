import { summarizeRtcStats } from '../RtcStatsTelemetry';

describe('summarizeRtcStats', () => {
  it('reports RTT, interval packet loss, and TURN candidate usage', () => {
    const report = new Map([
      ['transport-1', { id: 'transport-1', type: 'transport', selectedCandidatePairId: 'pair-1' }],
      ['pair-1', {
        id: 'pair-1',
        type: 'candidate-pair',
        currentRoundTripTime: 0.125,
        availableOutgoingBitrate: 64000,
        localCandidateId: 'local-1',
        remoteCandidateId: 'remote-1',
      }],
      ['local-1', { id: 'local-1', type: 'local-candidate', candidateType: 'relay', networkType: 'wifi' }],
      ['remote-1', { id: 'remote-1', type: 'remote-candidate', candidateType: 'srflx' }],
      ['inbound-1', {
        id: 'inbound-1',
        type: 'inbound-rtp',
        kind: 'audio',
        jitter: 0.02,
        packetsReceived: 190,
        packetsLost: 10,
      }],
    ]);

    const result = summarizeRtcStats(report, { packetsReceived: 100, packetsLost: 5 });

    expect(result.attributes).toMatchObject({
      rtt_ms: 125,
      jitter_ms: 20,
      packet_loss_pct: 5.263157894736842,
      turn_used: true,
      local_candidate_type: 'relay',
      network_type: 'wifi',
    });
  });
});
