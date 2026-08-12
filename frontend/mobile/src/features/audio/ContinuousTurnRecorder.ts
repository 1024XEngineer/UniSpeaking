import type { AudioDataEvent, AudioRecording, RecordingConfig, StartRecordingResult } from '@siteed/audio-studio';
import { Directory, File, Paths } from 'expo-file-system';

const SAMPLE_RATE = 16_000;
const CHANNELS = 1;
const BYTES_PER_SAMPLE = 2;
const BYTES_PER_MS = (SAMPLE_RATE * CHANNELS * BYTES_PER_SAMPLE) / 1_000;
const PRE_ROLL_BYTES = 200 * BYTES_PER_MS;
const POST_ROLL_BYTES = 500 * BYTES_PER_MS;
const WAV_HEADER_BYTES = 44;

type StreamRecorderPort = {
  requestPermissionsAsync(): Promise<{ granted: boolean }>;
  startRecording(config: RecordingConfig): Promise<StartRecordingResult | void>;
  stopRecording(): Promise<AudioRecording | null | void>;
};

type OutputFile = {
  uri: string;
  size: number;
  create(options?: { overwrite?: boolean; intermediates?: boolean }): void;
  write(bytes: Uint8Array): void;
  delete(): void;
};

type RecorderStorage = {
  prepare(runId: string): void;
  createFile(name: string): OutputFile;
  remove(uri: string): void;
  cleanup(): void;
};

type ActiveTurn = {
  chunks: Uint8Array[];
  remainingPostRollBytes: number | null;
  resolve: (pcm: Uint8Array) => void;
  pcm: Promise<Uint8Array>;
};

export type TurnWav = Readonly<{
  uri: string;
  name: string;
  size: number;
  durationMs: number;
}>;

function createStorage(): RecorderStorage {
  let directory: Directory | null = null;
  return {
    prepare(runId) {
      directory = new Directory(Paths.cache, 'interview-turn-audio', runId);
      directory.create({ idempotent: true, intermediates: true });
    },
    createFile(name) {
      if (!directory) throw new Error('面试录音目录尚未准备');
      return new File(directory, name);
    },
    remove(uri) {
      const file = new File(uri);
      if (file.exists) file.delete();
    },
    cleanup() {
      if (directory?.exists) directory.delete();
      directory = null;
    },
  };
}

function concat(chunks: readonly Uint8Array[]) {
  const length = chunks.reduce((sum, chunk) => sum + chunk.length, 0);
  const output = new Uint8Array(length);
  let offset = 0;
  for (const chunk of chunks) {
    output.set(chunk, offset);
    offset += chunk.length;
  }
  return output;
}

function decodeBase64(value: string) {
  const binary = globalThis.atob(value);
  const bytes = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index += 1) {
    bytes[index] = binary.charCodeAt(index);
  }
  return bytes;
}

function ascii(bytes: Uint8Array, offset: number, length: number) {
  return String.fromCharCode(...bytes.subarray(offset, offset + length));
}

function isWavHeader(bytes: Uint8Array) {
  return (
    bytes.length >= WAV_HEADER_BYTES &&
    ascii(bytes, 0, 4) === 'RIFF' &&
    ascii(bytes, 8, 4) === 'WAVE' &&
    ascii(bytes, 12, 4) === 'fmt ' &&
    ascii(bytes, 36, 4) === 'data'
  );
}

function setAscii(target: Uint8Array, offset: number, value: string) {
  for (let index = 0; index < value.length; index += 1) {
    target[offset + index] = value.charCodeAt(index);
  }
}

function buildWav(pcm: Uint8Array) {
  const header = new Uint8Array(WAV_HEADER_BYTES);
  const view = new DataView(header.buffer);
  setAscii(header, 0, 'RIFF');
  view.setUint32(4, 36 + pcm.length, true);
  setAscii(header, 8, 'WAVE');
  setAscii(header, 12, 'fmt ');
  view.setUint32(16, 16, true);
  view.setUint16(20, 1, true);
  view.setUint16(22, CHANNELS, true);
  view.setUint32(24, SAMPLE_RATE, true);
  view.setUint32(28, SAMPLE_RATE * CHANNELS * BYTES_PER_SAMPLE, true);
  view.setUint16(32, CHANNELS * BYTES_PER_SAMPLE, true);
  view.setUint16(34, BYTES_PER_SAMPLE * 8, true);
  setAscii(header, 36, 'data');
  view.setUint32(40, pcm.length, true);
  return concat([header, pcm]);
}

function alignPcm(bytes: Uint8Array): Uint8Array {
  const evenLength = bytes.length - (bytes.length % BYTES_PER_SAMPLE);
  return evenLength === bytes.length ? bytes : bytes.slice(0, evenLength);
}

export class ContinuousTurnRecorder {
  private started = false;
  private starting: Promise<void> | null = null;
  private stopping: Promise<void> | null = null;
  private inputEnabled = false;
  private sawInitialHeader = false;
  private preRoll = new Uint8Array();
  private active: ActiveTurn | null = null;
  private runId = '';
  private nativeRecordingUri: string | null = null;

  constructor(
    private readonly recorder: StreamRecorderPort,
    private readonly storage: RecorderStorage = createStorage(),
  ) {}

  async start() {
    if (this.started) return;
    if (!this.starting) {
      this.starting = (async () => {
        const permission = await this.recorder.requestPermissionsAsync();
        if (!permission.granted) throw new Error('请允许麦克风权限后再开始面试');
        this.runId = `interview-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
        this.sawInitialHeader = false;
        this.preRoll = new Uint8Array();
        this.active = null;
        this.storage.prepare(this.runId);
        const started = await this.recorder.startRecording({
          sampleRate: SAMPLE_RATE,
          channels: CHANNELS,
          encoding: 'pcm_16bit',
          streamFormat: 'raw',
          interval: 100,
          keepFullAnalysis: false,
          output: { primary: { enabled: true, format: 'wav' } },
          android: { audioFocusStrategy: 'none' },
          onAudioStream: async (event) => this.ingest(event),
        });
        this.nativeRecordingUri = started?.fileUri ?? null;
        this.started = true;
      })().finally(() => {
        this.starting = null;
      });
    }
    await this.starting;
  }

  setInputEnabled(enabled: boolean) {
    this.inputEnabled = enabled;
    if (!enabled && !this.active) this.preRoll = new Uint8Array();
  }

  speechStarted() {
    if (!this.started || !this.inputEnabled || this.active) return;
    let resolve!: (pcm: Uint8Array) => void;
    const pcm = new Promise<Uint8Array>((next) => {
      resolve = next;
    });
    this.active = {
      chunks: this.preRoll.length ? [this.preRoll.slice()] : [],
      remainingPostRollBytes: null,
      resolve,
      pcm,
    };
  }

  speechStopped() {
    if (!this.active || this.active.remainingPostRollBytes !== null) return;
    this.active.remainingPostRollBytes = POST_ROLL_BYTES;
  }

  async takeTurn(turnNo: number): Promise<TurnWav | null> {
    const turn = this.active;
    if (!turn) return null;
    if (turn.remainingPostRollBytes === null) this.speechStopped();
    const timeout = setTimeout(() => this.finalizeActive(), 1_200);
    const pcm = await turn.pcm.finally(() => clearTimeout(timeout));
    if (this.active === turn) this.active = null;
    if (pcm.length === 0) return null;
    const wav = buildWav(pcm);
    const name = `interview-turn-${turnNo}.wav`;
    const file = this.storage.createFile(name);
    file.create({ overwrite: true, intermediates: true });
    file.write(wav);
    if (file.size !== wav.length) throw new Error('面试录音文件写入不完整');
    return {
      uri: file.uri,
      name,
      size: wav.length,
      durationMs: pcm.length / BYTES_PER_MS,
    };
  }

  discard(turn: TurnWav | null) {
    if (turn) this.storage.remove(turn.uri);
  }

  close() {
    if (!this.stopping) {
      this.stopping = (async () => {
        this.finalizeActive();
        try {
          const stopped = this.started ? await this.recorder.stopRecording() : null;
          const nativeUri = stopped?.fileUri ?? this.nativeRecordingUri;
          if (nativeUri) this.storage.remove(nativeUri);
        } finally {
          this.started = false;
          this.inputEnabled = false;
          this.sawInitialHeader = false;
          this.preRoll = new Uint8Array();
          this.active = null;
          this.nativeRecordingUri = null;
          this.storage.cleanup();
        }
      })().finally(() => {
        this.stopping = null;
      });
    }
    return this.stopping;
  }

  private ingest(event: AudioDataEvent) {
    if (typeof event.data !== 'string') {
      throw new Error('Android 面试录音必须返回原始 PCM 数据');
    }
    let pcm: Uint8Array = decodeBase64(event.data);
    if (pcm.length !== event.eventDataSize) {
      throw new Error('面试录音数据块长度不一致');
    }
    if (!this.sawInitialHeader && isWavHeader(pcm)) {
      pcm = pcm.slice(WAV_HEADER_BYTES);
      this.sawInitialHeader = true;
    }
    pcm = alignPcm(pcm);
    if (!pcm.length) return;

    const active = this.active;
    if (active) {
      active.chunks.push(pcm.slice());
      if (active.remainingPostRollBytes !== null) {
        active.remainingPostRollBytes -= pcm.length;
        if (active.remainingPostRollBytes <= 0) this.finalizeActive();
      }
    }
    if (this.inputEnabled && !active) {
      const combined = concat([this.preRoll, pcm]);
      this.preRoll = combined.slice(Math.max(0, combined.length - PRE_ROLL_BYTES));
    }
  }

  private finalizeActive() {
    const active = this.active;
    if (!active) return;
    active.resolve(alignPcm(concat(active.chunks)));
  }
}

export const interviewPcmContract = {
  sampleRate: SAMPLE_RATE,
  channels: CHANNELS,
  bitsPerSample: BYTES_PER_SAMPLE * 8,
} as const;
