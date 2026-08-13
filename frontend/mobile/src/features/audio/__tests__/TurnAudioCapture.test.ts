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
});
