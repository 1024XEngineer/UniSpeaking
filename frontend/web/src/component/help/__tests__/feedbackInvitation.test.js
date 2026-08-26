import { afterEach, describe, expect, it, vi } from "vitest";
import { markFeedbackStarted, recordFeedbackPractice } from "../feedbackInvitation.js";

function storage(initial = {}) {
  const values = new Map(Object.entries(initial));
  return {
    getItem: (key) => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, value),
    dump: () => Object.fromEntries(values),
  };
}

function throwingStorage() {
  return {
    getItem: vi.fn(() => null),
    setItem: vi.fn(() => { throw new Error("storage unavailable"); }),
  };
}

afterEach(() => vi.restoreAllMocks());

describe("feedback invitation progress", () => {
  it("rejects incomplete practice records and feedback starts", () => {
    const store = storage();
    expect(recordFeedbackPractice(null, { userId: "user-1", practiceType: "free", sessionId: "s-1" })).toBe(false);
    expect(recordFeedbackPractice(store, { userId: "", practiceType: "free", sessionId: "s-2" })).toBe(false);
    expect(recordFeedbackPractice(store, { userId: "user-1", practiceType: "free", sessionId: "" })).toBe(false);
    expect(recordFeedbackPractice(store, { userId: "user-1", practiceType: "other", sessionId: "s-3" })).toBe(false);
    expect(markFeedbackStarted(null, "user-1")).toBe(false);
    expect(markFeedbackStarted(store, "")).toBe(false);
  });

  it("recovers from malformed progress and normalizes legacy stored values", () => {
    const corrupt = storage({ "unispeaking.feedbackInvitation.v1.user-1": "not-json" });
    expect(recordFeedbackPractice(corrupt, { userId: "user-1", practiceType: "scene", sessionId: "s-1" })).toBe(false);

    const legacy = storage({
      "unispeaking.feedbackInvitation.v1.user-2": JSON.stringify({
        freeConversationCount: -4,
        scenePracticeCount: "2",
        prompted: true,
        practicesSinceLastInvitation: -1,
        recordedSessionIds: ["old", 123, "new"],
        feedbackStarted: 0,
      }),
    });
    expect(recordFeedbackPractice(legacy, { userId: "user-2", practiceType: "scene", sessionId: "s-2" })).toBe(false);
    const saved = JSON.parse(legacy.dump()["unispeaking.feedbackInvitation.v1.user-2"]);
    expect(saved).toMatchObject({
      freeConversationCount: 0,
      scenePracticeCount: 3,
      invitationCount: 1,
      practicesSinceLastInvitation: 1,
      recordedSessionIds: ["old", "new", "scene:s-2"],
      feedbackStarted: false,
    });
  });

  it("invites after three scene practices and ignores duplicate session ids", () => {
    const store = storage();
    expect(recordFeedbackPractice(store, { userId: "scene-user", practiceType: "scene", sessionId: "scene-1" })).toBe(false);
    expect(recordFeedbackPractice(store, { userId: "scene-user", practiceType: "scene", sessionId: "scene-1" })).toBe(false);
    expect(recordFeedbackPractice(store, { userId: "scene-user", practiceType: "scene", sessionId: "scene-2" })).toBe(false);
    expect(recordFeedbackPractice(store, { userId: "scene-user", practiceType: "scene", sessionId: "scene-3" })).toBe(true);
  });

  it("supports the follow-up invitation threshold and caps session history", () => {
    const store = storage();
    expect(recordFeedbackPractice(store, { userId: "follow-up", practiceType: "free", sessionId: "free-1" })).toBe(false);
    expect(recordFeedbackPractice(store, { userId: "follow-up", practiceType: "free", sessionId: "free-2" })).toBe(false);
    expect(recordFeedbackPractice(store, { userId: "follow-up", practiceType: "free", sessionId: "free-3" })).toBe(true);
    for (let index = 4; index <= 22; index += 1) {
      expect(recordFeedbackPractice(store, {
        userId: "follow-up",
        practiceType: index % 2 === 0 ? "scene" : "free",
        sessionId: `session-${index}`,
      })).toBe(index === 13);
    }
    const saved = JSON.parse(store.dump()["unispeaking.feedbackInvitation.v1.follow-up"]);
    expect(saved.invitationCount).toBe(2);
    expect(saved.practicesSinceLastInvitation).toBe(9);
    expect(saved.recordedSessionIds).toHaveLength(20);
    expect(saved.recordedSessionIds[0]).toBe("free:free-3");
  });

  it("returns false when storage cannot be written", () => {
    const store = throwingStorage();
    expect(recordFeedbackPractice(store, { userId: "user-1", practiceType: "free", sessionId: "s-1" })).toBe(false);
    expect(markFeedbackStarted(store, "user-1")).toBe(false);
  });

  it("stops recording after feedback has started", () => {
    const store = storage({
      "unispeaking.feedbackInvitation.v1.started": JSON.stringify({
        feedbackStarted: true,
        recordedSessionIds: [],
      }),
    });
    expect(recordFeedbackPractice(store, { userId: "started", practiceType: "free", sessionId: "s-1" })).toBe(false);
  });
});
