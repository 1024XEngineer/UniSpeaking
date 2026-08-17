import type { Teacher } from '@/theme/tokens';

import { TeacherPreviewPlayer } from '../TeacherPreviewPlayer';

const teacher = { id: 'clara', audio: 101 } as Teacher;

describe('TeacherPreviewPlayer', () => {
  it('replaces the current preview and starts the selected teacher', async () => {
    const player = {
      pause: jest.fn(),
      play: jest.fn(),
      release: jest.fn(),
      replace: jest.fn(),
      seekTo: jest.fn(async () => undefined),
    };
    const preview = new TeacherPreviewPlayer(player);

    await preview.play(teacher);

    expect(player.pause).toHaveBeenCalledTimes(1);
    expect(player.replace).toHaveBeenCalledWith(101);
    expect(player.seekTo).toHaveBeenCalledWith(0);
    expect(player.play).toHaveBeenCalledTimes(1);
  });

  it('does not start an older preview after a newer preview is selected', async () => {
    let resolveFirstSeek: () => void = () => undefined;
    const firstSeek = new Promise<void>((resolve) => {
      resolveFirstSeek = resolve;
    });
    const player = {
      pause: jest.fn(),
      play: jest.fn(),
      release: jest.fn(),
      replace: jest.fn(),
      seekTo: jest.fn()
        .mockReturnValueOnce(firstSeek)
        .mockResolvedValueOnce(undefined),
    };
    const preview = new TeacherPreviewPlayer(player);

    const firstPlay = preview.play(teacher);
    await preview.play({ ...teacher, id: 'james', audio: 202 });
    resolveFirstSeek();
    await firstPlay;

    expect(player.replace).toHaveBeenNthCalledWith(1, 101);
    expect(player.replace).toHaveBeenNthCalledWith(2, 202);
    expect(player.play).toHaveBeenCalledTimes(1);
  });

  it('stops playback and cancels a pending preview', async () => {
    let resolveSeek: () => void = () => undefined;
    const seek = new Promise<void>((resolve) => {
      resolveSeek = resolve;
    });
    const player = {
      pause: jest.fn(),
      play: jest.fn(),
      release: jest.fn(),
      replace: jest.fn(),
      seekTo: jest.fn(() => seek),
    };
    const preview = new TeacherPreviewPlayer(player);
    const playPromise = preview.play(teacher);

    preview.stop();
    resolveSeek();
    await playPromise;

    expect(player.pause).toHaveBeenCalledTimes(2);
    expect(player.play).not.toHaveBeenCalled();
  });

  it('releases the native player when disposed', () => {
    const player = {
      pause: jest.fn(),
      play: jest.fn(),
      release: jest.fn(),
      replace: jest.fn(),
      seekTo: jest.fn(async () => undefined),
    };
    const preview = new TeacherPreviewPlayer(player);

    preview.dispose();

    expect(player.pause).toHaveBeenCalledTimes(1);
    expect(player.release).toHaveBeenCalledTimes(1);
  });
});
