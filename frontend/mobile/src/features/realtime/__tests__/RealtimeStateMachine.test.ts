import { RealtimeStateMachine } from '../RealtimeStateMachine';

describe('RealtimeStateMachine', () => {
  it('follows the complete WebRTC connection lifecycle', () => {
    const machine = new RealtimeStateMachine();

    expect(machine.dispatch({ type: 'START' })).toBe('requesting_permission');
    expect(machine.dispatch({ type: 'PERMISSION_GRANTED' })).toBe('creating_offer');
    expect(machine.dispatch({ type: 'OFFER_CREATED' })).toBe('exchanging_sdp');
    expect(machine.dispatch({ type: 'ANSWER_APPLIED' })).toBe('connecting');
    expect(machine.dispatch({ type: 'CHANNEL_OPEN' })).toBe('ready');
  });

  it('tracks user speech, assistant speech, pause and resume', () => {
    const machine = RealtimeStateMachine.ready();

    expect(machine.dispatch({ type: 'USER_SPEECH_STARTED' })).toBe('user_speaking');
    expect(machine.dispatch({ type: 'USER_SPEECH_STOPPED' })).toBe('ready');
    expect(machine.dispatch({ type: 'ASSISTANT_SPEECH_STARTED' })).toBe('assistant_speaking');
    expect(machine.dispatch({ type: 'PAUSE' })).toBe('paused');
    expect(machine.dispatch({ type: 'RESUME' })).toBe('ready');
  });

  it('ends idempotently and ignores provider events after ending', () => {
    const machine = RealtimeStateMachine.ready();

    expect(machine.dispatch({ type: 'STOP' })).toBe('ending');
    expect(machine.dispatch({ type: 'ENDED' })).toBe('ended');
    expect(machine.dispatch({ type: 'USER_SPEECH_STARTED' })).toBe('ended');
    expect(machine.dispatch({ type: 'STOP' })).toBe('ended');
  });

  it('records a typed failure and can reset for one clean retry', () => {
    const machine = new RealtimeStateMachine();

    expect(
      machine.dispatch({
        type: 'FAIL',
        error: { code: 'MICROPHONE_DENIED', message: '请允许麦克风权限', retryable: false },
      }),
    ).toBe('error');
    expect(machine.error?.code).toBe('MICROPHONE_DENIED');
    expect(machine.dispatch({ type: 'RESET' })).toBe('idle');
    expect(machine.error).toBeNull();
  });

  it('rejects an invalid transition before a session is ready', () => {
    const machine = new RealtimeStateMachine();

    expect(() => machine.dispatch({ type: 'ASSISTANT_SPEECH_STARTED' })).toThrow(
      'INVALID_TRANSITION:idle:ASSISTANT_SPEECH_STARTED',
    );
  });
});
