import { afterEach, describe, expect, it, vi } from "vitest";
import { createPcmWavRecorder, createPcmWavSegmentRecorder } from "../audioRecorder.js";

const originalAudioContext = window.AudioContext;

function makeAudioContext(sampleRate = 16_000) {
  const source = { connect: vi.fn(), disconnect: vi.fn() };
  const processor = { connect: vi.fn(), disconnect: vi.fn(), onaudioprocess: null };
  const gain = { connect: vi.fn(), disconnect: vi.fn(), gain: { value: 1 } };
  const context = {
    sampleRate,
    destination: {},
    resume: vi.fn(async () => undefined),
    close: vi.fn(async () => undefined),
    createMediaStreamSource: vi.fn(() => source),
    createScriptProcessor: vi.fn(() => processor),
    createGain: vi.fn(() => gain),
  };
  return { context, source, processor, gain };
}

afterEach(() => {
  vi.restoreAllMocks();
  window.AudioContext = originalAudioContext;
  delete window.webkitAudioContext;
  delete navigator.mediaDevices;
});

describe("browser PCM WAV recorder", () => {
  it("reports unsupported microphone and audio APIs", async () => {
    Object.defineProperty(navigator, "mediaDevices", { configurable: true, value: undefined });
    await expect(createPcmWavRecorder()).rejects.toThrow("不支持麦克风录音");
    Object.defineProperty(navigator, "mediaDevices", { configurable: true, value: {} });
    await expect(createPcmWavRecorder()).rejects.toThrow("不支持麦克风录音");
    Object.defineProperty(navigator, "mediaDevices", { configurable: true, value: { getUserMedia: vi.fn() } });
    await expect(createPcmWavRecorder()).rejects.toThrow("不支持音频采集");
    await expect(createPcmWavSegmentRecorder({ getAudioTracks: () => [] })).rejects.toThrow("没有可用的麦克风音轨");
  });

  it("records, resamples, encodes a WAV, and closes the microphone exactly once", async () => {
    const track = { stop: vi.fn() };
    const stream = { getTracks: () => [track] };
    const getUserMedia = vi.fn(async () => stream);
    Object.defineProperty(navigator, "mediaDevices", { configurable: true, value: { getUserMedia } });
    const fake = makeAudioContext(8_000);
    window.AudioContext = vi.fn(() => fake.context);

    const recorder = await createPcmWavRecorder();
    expect(getUserMedia).toHaveBeenCalledWith({ audio: {
      channelCount: 1,
      echoCancellation: false,
      noiseSuppression: false,
      autoGainControl: false,
    } });
    fake.processor.onaudioprocess({ inputBuffer: { getChannelData: () => new Float32Array([0, 0.5, -0.5, 1]) } });
    const wav = await recorder.stop();
    expect(wav).toBeInstanceOf(Blob);
    expect(wav.type).toBe("audio/wav");
    expect(wav.size).toBeGreaterThan(44);
    expect(wav.size).toBeGreaterThan(44);
    expect(track.stop).toHaveBeenCalledTimes(1);
    expect(fake.context.close).toHaveBeenCalledTimes(1);
    await recorder.stop();
    expect(track.stop).toHaveBeenCalledTimes(1);

    const second = await createPcmWavRecorder();
    second.cancel();
    await Promise.resolve();
    expect(track.stop).toHaveBeenCalledTimes(2);
  });

  it("rejects empty recordings", async () => {
    const track = { stop: vi.fn() };
    const stream = { getTracks: () => [track] };
    Object.defineProperty(navigator, "mediaDevices", { configurable: true, value: { getUserMedia: vi.fn(async () => stream) } });
    const fake = makeAudioContext();
    window.AudioContext = vi.fn(() => fake.context);
    const recorder = await createPcmWavRecorder();
    await expect(recorder.stop()).rejects.toThrow("没有采集到声音");
  });

  it("captures segments only while active and trims surrounding silence", async () => {
    const track = { stop: vi.fn() };
    const stream = { getAudioTracks: () => [track] };
    const fake = makeAudioContext();
    window.AudioContext = vi.fn(() => fake.context);
    const recorder = await createPcmWavSegmentRecorder(stream);

    fake.processor.onaudioprocess({ inputBuffer: { getChannelData: () => new Float32Array([1, 1]) } });
    expect(await recorder.stopSegment()).toBeNull();
    expect(() => recorder.startSegment()).not.toThrow();
    fake.processor.onaudioprocess({ inputBuffer: { getChannelData: () => new Float32Array(16_000).fill(0.2) } });
    const wav = await recorder.stopSegment();
    expect(wav).toBeInstanceOf(Blob);
    expect(wav.size).toBeGreaterThan(44);
    await recorder.close();
    await recorder.close();
    expect(fake.context.close).toHaveBeenCalledTimes(1);
    expect(() => recorder.startSegment()).toThrow("已关闭");
    expect(track.stop).not.toHaveBeenCalled();
  });

  it("validates segment streams and audio context availability", async () => {
    await expect(createPcmWavSegmentRecorder(null)).rejects.toThrow("没有可用的麦克风音轨");
    const stream = { getAudioTracks: () => [{}] };
    await expect(createPcmWavSegmentRecorder(stream)).rejects.toThrow("不支持音频采集");
  });
});
