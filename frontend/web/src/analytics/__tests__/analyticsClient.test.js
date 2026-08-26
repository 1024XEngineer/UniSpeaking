import { describe, expect, it, vi } from "vitest";
import { createAnalyticsClient } from "../analyticsClient.js";

function eventTarget() {
  const listeners = new Map();
  return {
    addEventListener: vi.fn((name, listener) => listeners.set(name, listener)),
    dispatch(name) { listeners.get(name)?.(); },
  };
}

function trackerRecorder() {
  const events = [];
  const identities = [];
  return {
    events,
    identities,
    tracker: {
      identify: (value) => identities.push(value),
      track: (builder) => events.push(builder({ hostname: "example.com", title: "old", url: "/old" })),
    },
  };
}

describe("analyticsClient", () => {
  it("does nothing when analytics is disabled or the tracker is missing", () => {
    const disabled = createAnalyticsClient({ enabled: false });
    expect(() => {
      disabled.trackPageView("/private");
      disabled.trackModeSelection({ mode: "SCENE" });
      disabled.trackLearningAsset({ mode: "IELTS" });
    }).not.toThrow();

    const unavailable = createAnalyticsClient({ enabled: true, tracker: () => null });
    expect(() => unavailable.trackModeSelection({ mode: "SCENE" })).not.toThrow();
  });

  it("queues events until the tracker becomes available and flushes on load", () => {
    const calls = [];
    const target = eventTarget();
    let currentTracker = null;
    const client = createAnalyticsClient({
      enabled: true,
      tracker: () => currentTracker,
      eventTarget: target,
      schedule: () => undefined,
      initialPath: "/scenes/private-scene/session/session-1/result",
    });

    client.trackPageView("/conversation/private-session");
    client.trackModeSelection({ mode: "FREE_CHAT", pageCode: "conversation", email: "secret" }, "sidebar");
    expect(calls).toEqual([]);

    currentTracker = {
      track: (builder) => calls.push(builder({ url: "/private", title: "old" })),
      identify: vi.fn(),
    };
    target.dispatch("load");
    expect(calls).toHaveLength(2);
    expect(calls[0]).toMatchObject({ url: "/conversation/session", title: "UniSpeaking" });
    expect(calls[1]).toMatchObject({
      name: "mode_selected",
      data: { mode: "FREE_CHAT", page_code: "conversation", source: "sidebar" },
    });
    expect(calls[1].data.email).toBeUndefined();
  });

  it("filters invalid identity values and tracks approved page and asset data", () => {
    const { events, identities, tracker } = trackerRecorder();
    const client = createAnalyticsClient({ enabled: true, tracker: () => tracker, initialPath: "/" });

    client.setDistinctId("user-123");
    client.setDistinctId("user-123");
    client.setDistinctId(123);
    client.trackPageView("/interview/scenes/private/session/session-1/report?token=secret");
    client.trackLearningAsset({ mode: "INTERVIEW", pageCode: "interview-assets", sessionId: "secret" }, "REPORT");
    client.trackModeSelection({ mode: "UNKNOWN", pageCode: "bad" });

    expect(identities).toEqual(["user-123", ""]);
    expect(events[0]).toMatchObject({ id: undefined, url: "/interview/session/report", title: "UniSpeaking" });
    expect(events[1]).toMatchObject({
      name: "learning_asset_view",
      data: { mode: "INTERVIEW", page_code: "interview-assets", asset_type: "REPORT" },
    });
    expect(events[1].data.sessionId).toBeUndefined();
  });

  it("records valid training lifecycle events and ignores duplicate terminal actions", () => {
    let current = 0;
    const { events, tracker } = trackerRecorder();
    const client = createAnalyticsClient({ enabled: true, tracker: () => tracker, now: () => current });
    const training = client.training({ mode: "SCENE", pageCode: "scene-training" });

    training.attempt();
    training.started();
    training.started();
    current = 3_600;
    training.setVisible(false);
    current = 9_600;
    training.setVisible(true);
    current = 12_200;
    training.pause();
    current = 20_000;
    training.resume();
    current = 24_500;
    training.heartbeat();
    training.complete();
    training.complete();
    training.abandon();

    expect(events.map(({ name, data }) => ({ name, data }))).toEqual([
      { name: "training_start_attempt", data: { mode: "SCENE", page_code: "scene-training" } },
      { name: "training_started", data: { mode: "SCENE", page_code: "scene-training" } },
      { name: "training_completed", data: { mode: "SCENE", page_code: "scene-training", effective_duration_seconds: 11 } },
    ]);
    expect(training.isStarted()).toBe(false);
  });

  it("emits failed and abandoned starts, while rejecting invalid training modes", () => {
    let current = 0;
    const { events, tracker } = trackerRecorder();
    const client = createAnalyticsClient({ enabled: true, tracker: () => tracker, now: () => current });
    const failed = client.training({ mode: "IELTS", pageCode: "ielts-training" });
    failed.attempt();
    failed.fail("NETWORK");
    failed.started();

    const invalid = client.training({ mode: "INVALID", pageCode: "other" });
    invalid.attempt();
    invalid.started();

    const abandoned = client.training({ mode: "FREE_CHAT", pageCode: "conversation" });
    abandoned.started();
    current = 4_100;
    abandoned.abandon("USER_EXIT");
    abandoned.pause();

    expect(events.map(({ name, data }) => ({ name, data }))).toEqual([
      { name: "training_start_attempt", data: { mode: "IELTS", page_code: "ielts-training" } },
      { name: "training_start_failed", data: { mode: "IELTS", page_code: "ielts-training", reason: "NETWORK" } },
      { name: "training_started", data: { mode: "FREE_CHAT", page_code: "conversation" } },
      { name: "training_abandoned", data: { mode: "FREE_CHAT", page_code: "conversation", reason: "USER_EXIT", effective_duration_seconds: 4 } },
    ]);
  });

  it("does not let tracker exceptions break product calls", () => {
    const schedule = vi.fn();
    const client = createAnalyticsClient({
      enabled: true,
      tracker: () => { throw new Error("tracker down"); },
      schedule,
    });
    expect(() => client.setDistinctId("user")).not.toThrow();
    expect(() => client.trackPageView("/conversation")).not.toThrow();
    expect(schedule).not.toHaveBeenCalled();
  });
});
