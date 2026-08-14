export type PcmRecordingConfig = {
  sampleRate: 16_000;
  channels: 1;
  encoding: 'pcm_16bit';
  output: {
    primary: {
      enabled: true;
      format: 'wav';
    };
  };
};

export type PcmRecorderPort = {
  requestPermissionsAsync(): Promise<{ granted: boolean }>;
  startRecording(config: PcmRecordingConfig): Promise<unknown>;
  stopRecording(): Promise<{ fileUri?: string } | null>;
};

function createNativeRecorder(): PcmRecorderPort {
  // Keep unit tests independent from the Expo native module.
  // eslint-disable-next-line @typescript-eslint/no-require-imports
  const { AudioStudioModule } = require('@siteed/audio-studio') as typeof import('@siteed/audio-studio');
  return {
    requestPermissionsAsync: () => AudioStudioModule.requestPermissionsAsync(),
    startRecording: (config) => AudioStudioModule.startRecording(config),
    stopRecording: () => AudioStudioModule.stopRecording(),
  };
}

const pcm16WavConfig: PcmRecordingConfig = {
  sampleRate: 16_000,
  channels: 1,
  encoding: 'pcm_16bit',
  output: {
    primary: {
      enabled: true,
      format: 'wav',
    },
  },
};

export class WavRecorder {
  private active = false;

  constructor(private readonly nativeRecorder: PcmRecorderPort = createNativeRecorder()) {}

  async start() {
    if (this.active) return;
    const permission = await this.nativeRecorder.requestPermissionsAsync();
    if (!permission.granted) {
      throw new Error('请允许麦克风权限后再朗读');
    }
    try {
      await this.nativeRecorder.startRecording(pcm16WavConfig);
    } catch (error) {
      if (!this.isAlreadyRecordingError(error)) throw error;
      // The native module can keep recording after a previous screen or
      // realtime session disappears. Clear that orphan before retrying.
      await this.nativeRecorder.stopRecording().catch(() => null);
      await this.nativeRecorder.startRecording(pcm16WavConfig);
    }
    this.active = true;
  }

  async stop() {
    if (!this.active) throw new Error('当前没有正在进行的录音');
    const result = await this.nativeRecorder.stopRecording().finally(() => {
      this.active = false;
    });
    if (!result?.fileUri) throw new Error('录音文件生成失败，请重新朗读');
    return result.fileUri;
  }

  async cancel() {
    if (!this.active) return;
    await this.nativeRecorder.stopRecording().finally(() => {
      this.active = false;
    });
  }

  private isAlreadyRecordingError(error: unknown) {
    if (!error || typeof error !== 'object') return false;
    const value = error as { code?: unknown; message?: unknown };
    return value.code === 'ALREADY_RECORDING' ||
      /recording is already in progress/i.test(String(value.message ?? ''));
  }
}
