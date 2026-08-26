import { describe, expect, it, vi } from "vitest";
import { resolvePublicCaptchaEnv, getAliyunCaptchaCdnServers, getAliyunCaptchaScriptUrl } from "../captchaConfig.js";
import { findHelpArticle, getRelatedHelpArticles, searchHelpArticles } from "../component/help/helpUtils.js";
import { markFeedbackStarted, recordFeedbackPractice } from "../component/help/feedbackInvitation.js";
import { classifyScoredWord } from "../domain/pronunciationScore.js";
import { summarizeRtcStats } from "../telemetry/rtcTelemetry.js";

function storage(initial = {}) {
  const values = new Map(Object.entries(initial));
  return {
    getItem: (key) => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, value),
    removeItem: (key) => values.delete(key),
    dump: () => Object.fromEntries(values),
  };
}

describe("basic web modules", () => {
  it("resolves public captcha settings without leaking private backend values", () => {
    const result = resolvePublicCaptchaEnv(
      { VITE_AUTH_CAPTCHA_PROVIDER: "", VITE_ALIYUN_CAPTCHA_REGION: "cn" },
      {
        AUTH_CAPTCHA_PROVIDER: "aliyun",
        ALIYUN_CAPTCHA_SCENE_ID: "scene-1",
        ALIYUN_CAPTCHA_ACCESS_KEY_ID: "private",
      },
    );
    expect(result).toMatchObject({
      VITE_AUTH_CAPTCHA_PROVIDER: "aliyun",
      VITE_ALIYUN_CAPTCHA_SCENE_ID: "scene-1",
      VITE_ALIYUN_CAPTCHA_REGION: "cn",
    });
    expect(Object.keys(result).some((key) => key.includes("ACCESS_KEY"))).toBe(false);
    expect(getAliyunCaptchaCdnServers("https://cdn.example.com/")).toEqual(["https://cdn.example.com"]);
    expect(getAliyunCaptchaScriptUrl(" ")).toMatch(/^https:\/\/o\.alicdn\.com\//);
  });

  it("searches help content and bounds related results", () => {
    const article = findHelpArticle("start-free-conversation");
    expect(article).not.toBeNull();
    expect(searchHelpArticles("  ")).toEqual([]);
    expect(searchHelpArticles(article.title.slice(0, 3))).toContainEqual(article);
    expect(getRelatedHelpArticles(null)).toEqual([]);
    expect(getRelatedHelpArticles(article, 0)).toEqual([]);
    expect(getRelatedHelpArticles(article, 1)).toHaveLength(1);
  });

  it("records invitations once per session and stops after feedback starts", () => {
    const store = storage();
    expect(recordFeedbackPractice(store, { userId: "user/1", practiceType: "free", sessionId: "s-1" })).toBe(false);
    expect(recordFeedbackPractice(store, { userId: "user/1", practiceType: "free", sessionId: "s-1" })).toBe(false);
    expect(recordFeedbackPractice(store, { userId: "user/1", practiceType: "invalid", sessionId: "s-2" })).toBe(false);
    expect(recordFeedbackPractice(store, { userId: "user/1", practiceType: "free", sessionId: "s-2" })).toBe(false);
    expect(recordFeedbackPractice(store, { userId: "user/1", practiceType: "free", sessionId: "s-3" })).toBe(true);
    expect(markFeedbackStarted(store, "user/1")).toBe(true);
    expect(recordFeedbackPractice(store, { userId: "user/1", practiceType: "scene", sessionId: "s-4" })).toBe(false);
  });

  it("classifies strong, weak-form, malformed, and missing pronunciation results", () => {
    expect(classifyScoredWord("the", { wordScore: 80 })).toBe("is-correct");
    expect(classifyScoredWord("a", { wordScore: 0, phonemes: [{ expectedPhoneme: "eɪ", actualPhoneme: " Eɪ " }] })).toBe("is-review");
    expect(classifyScoredWord("a", { wordScore: 0, phonemes: [{ expectedPhoneme: "eɪ", actualPhoneme: "ʌ" }] })).toBe("is-incorrect");
    expect(classifyScoredWord("contract", null)).toBe("is-incorrect");
    expect(classifyScoredWord("for", { wordScore: "not-a-number" })).toBe("is-incorrect");
  });

  it("summarizes RTC stats from selected transport and safe fallbacks", () => {
    const report = new Map([
      ["transport", { id: "transport", type: "transport", selectedCandidatePairId: "pair" }],
      ["pair", { id: "pair", type: "candidate-pair", currentRoundTripTime: 0.08, localCandidateId: "local", remoteCandidateId: "remote", availableOutgoingBitrate: 1000 }],
      ["local", { id: "local", type: "local-candidate", candidateType: "relay", networkType: "wifi" }],
      ["remote", { id: "remote", type: "remote-candidate", candidateType: "srflx" }],
      ["audio", { id: "audio", type: "inbound-rtp", kind: "audio", jitter: 0.01, packetsReceived: 195, packetsLost: 5 }],
    ]);
    expect(summarizeRtcStats(report, { packetsReceived: 100, packetsLost: 0 })).toMatchObject({
      attributes: { rtt_ms: 80, jitter_ms: 10, packet_loss_pct: 5, turn_used: true, local_candidate_type: "relay" },
      totals: { packetsReceived: 195, packetsLost: 5 },
    });
    expect(summarizeRtcStats(null)).toMatchObject({
      attributes: { packet_loss_pct: 0, local_candidate_type: "unknown", remote_candidate_type: "unknown", turn_used: false },
      totals: { packetsReceived: 0, packetsLost: 0 },
    });
  });

  it("keeps the test environment free of accidental timers", () => {
    expect(vi.isMockFunction(setTimeout)).toBe(false);
  });
});
