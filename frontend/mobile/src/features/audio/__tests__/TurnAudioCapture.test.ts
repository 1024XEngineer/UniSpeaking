import { createTurnAudioCapture } from '../TurnAudioCapture';

describe('createTurnAudioCapture', () => {
  it('captures one turn segment and resets for the next turn', async () => {
    const recorder = {
      start: jest.fn(async () => undefined),
      stop: jest.fn(async () => 'file:///turn-1.wav'),
      cancel: jest.fn(async () => undefined),
    };
    const capture = createTurnAudioCapture(recorder);

    await capture.start();
    capture.stop();
    await expect(capture.take()).resolves.toBe('file:///turn-1.wav');

    await capture.start();
    capture.stop();
    await expect(capture.take()).resolves.toBe('file:///turn-1.wav');
    expect(recorder.start).toHaveBeenCalledTimes(2);
  });

  it('honors stop while native recording is still starting', async () => {
    let finishStart!: () => void;
    const recorder = {
      start: jest.fn(() => new Promise<void>((resolve) => { finishStart = resolve; })),
      stop: jest.fn(async () => 'file:///turn-race.wav'),
      cancel: jest.fn(async () => undefined),
    };
    const capture = createTurnAudioCapture(recorder);

    const starting = capture.start();
    expect(capture.stop()).toBe(true);
    finishStart();
    await starting;

    await expect(capture.take()).resolves.toBe('file:///turn-race.wav');
    expect(recorder.stop).toHaveBeenCalledTimes(1);
  });

  it('releases an active recording when the realtime session ends', async () => {
    const recorder = {
      start: jest.fn(async () => undefined),
      stop: jest.fn(async () => 'file:///turn.wav'),
      cancel: jest.fn(async () => undefined),
    };
    const capture = createTurnAudioCapture(recorder);

    await capture.start();
    await capture.release();

    expect(recorder.cancel).toHaveBeenCalledTimes(1);
    await capture.start();
    expect(recorder.start).toHaveBeenCalledTimes(2);
  });

  it('releases a recording whose native start is still pending', async () => {
    let finishStart!: () => void;
    let finishStop!: (uri: string) => void;
    const recorder = {
      start: jest.fn(() => new Promise<void>((resolve) => { finishStart = resolve; })),
      stop: jest.fn(() => new Promise<string>((resolve) => { finishStop = resolve; })),
      cancel: jest.fn(async () => undefined),
    };
    const capture = createTurnAudioCapture(recorder);

    const starting = capture.start();
    const releasing = capture.release();
    finishStart();
    await starting;

    let released = false;
    void releasing.then(() => { released = true; });
    await Promise.resolve();
    expect(released).toBe(false);
    finishStop('file:///turn.wav');
    await releasing;

    expect(recorder.stop).toHaveBeenCalledTimes(1);
    expect(recorder.cancel).not.toHaveBeenCalled();
  });
});
