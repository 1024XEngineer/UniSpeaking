import type { Teacher } from '@/theme/tokens';

export type TeacherPreviewAudioPlayer = {
  pause(): void;
  play(): void;
  release(): void;
  replace(source: number): void;
  seekTo(seconds: number): Promise<void>;
};

/**
 * Small controller for short, local teacher introductions.
 * A monotonically increasing request id prevents a slow seek from starting
 * an older preview after the user has already selected another teacher.
 */
export class TeacherPreviewPlayer {
  private requestId = 0;

  constructor(private readonly player: TeacherPreviewAudioPlayer) {}

  async play(teacher: Teacher) {
    const requestId = ++this.requestId;
    this.player.pause();
    this.player.replace(teacher.audio);
    await this.player.seekTo(0);
    if (requestId === this.requestId) this.player.play();
  }

  stop() {
    this.requestId += 1;
    this.player.pause();
  }

  dispose() {
    this.stop();
    this.player.release();
  }
}
