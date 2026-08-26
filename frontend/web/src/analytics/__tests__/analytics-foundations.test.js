import { describe, expect, it } from "vitest";
import { createActivityTimer } from "../activityTimer.js";
import { normalizeTrackedPath, pageForPath } from "../pageCatalog.js";
import { injectUmamiScript, resolveUmamiConfig } from "../umamiConfig.js";

const validEnv = {
  VITE_UMAMI_ENABLED: "true",
  VITE_UMAMI_SCRIPT_URL: "https://analytics.example.com/script.js",
  VITE_UMAMI_WEBSITE_ID: "website-123",
  VITE_UMAMI_DOMAINS: "example.com,localhost",
};

describe("analytics foundations", () => {
  it("maps known pages and protects unknown pages", () => {
    expect(pageForPath("/interview")).toMatchObject({ pageCode: "interview-training", mode: "INTERVIEW" });
    expect(pageForPath("/interview/assets/history")).toMatchObject({ pageCode: "interview-assets", assetType: "REPORT" });
    expect(pageForPath("/ielts")).toMatchObject({ pageCode: "ielts-training", mode: "IELTS" });
    expect(pageForPath("/ielts/assets/history")).toMatchObject({ pageCode: "ielts-assets", assetType: "REPORT" });
    expect(pageForPath("/assets/history")).toMatchObject({ pageCode: "scene-assets", assetType: "REPORT" });
    expect(pageForPath("/scenes/scene-1/session")).toMatchObject({ pageCode: "scene-training", mode: "SCENE" });
    expect(pageForPath("/conversation/private-session")).toMatchObject({ pageCode: "conversation", mode: "FREE_CHAT" });
    expect(pageForPath("/settings")).toEqual({ pageCode: "other" });
  });

  it("normalizes identifiers and strips query/hash values", () => {
    expect(normalizeTrackedPath("/conversation/private-session?token=secret#top")).toBe("/conversation/session");
    expect(normalizeTrackedPath("/scenes/a%20scene/session/s-1/result")).toBe("/scenes/session/result");
    expect(normalizeTrackedPath("/scenes/a%20scene/session/s-1")).toBe("/scenes/session");
    expect(normalizeTrackedPath("/scenes/a%20scene/word")).toBe("/scenes/word");
    expect(normalizeTrackedPath("/scenes/a%20scene/phrase")).toBe("/scenes/phrase");
    expect(normalizeTrackedPath("/scenes/a%20scene/sentence")).toBe("/scenes/sentence");
    expect(normalizeTrackedPath("/scenes/a%20scene/assets")).toBe("/scenes/assets");
    expect(normalizeTrackedPath("/interview/scenes/job-1/session")).toBe("/interview/session");
    expect(normalizeTrackedPath("/interview/scenes/job-1/session/s-1/report")).toBe("/interview/session/report");
    expect(normalizeTrackedPath("/ielts/part1/topic-1/setup")).toBe("/ielts/part1/selection/setup");
    expect(normalizeTrackedPath("/ielts/part2/topic-1/session")).toBe("/ielts/part2/selection/session");
    expect(normalizeTrackedPath("/ielts/part3/topic-1/analysis")).toBe("/ielts/part3/selection/analysis");
    expect(normalizeTrackedPath("/ielts/part1/topic-1/report")).toBe("/ielts/part1/selection/report");
    expect(normalizeTrackedPath("/ielts/part2/topic-1")).toBe("/ielts/part2/selection");
    expect(normalizeTrackedPath("/help/article/change-password")).toBe("/help/article");
    expect(normalizeTrackedPath("/help/category/audio")).toBe("/help/category");
    expect(normalizeTrackedPath("/unknown/?q=1")).toBe("/unknown");
    expect(normalizeTrackedPath("")).toBe("/");
  });

  it("tracks only active time and handles repeated lifecycle calls", () => {
    let current = 0;
    const timer = createActivityTimer({ now: () => current });
    timer.start();
    timer.start();
    current = 2_500;
    timer.setVisible(false);
    current = 9_500;
    timer.setVisible(true);
    current = 12_000;
    timer.pause();
    current = 20_000;
    timer.pause();
    timer.resume();
    current = 25_000;
    expect(timer.stop()).toBe(10);
    expect(timer.isStarted()).toBe(false);
  });

  it("validates Umami configuration and injects one safe script", () => {
    expect(resolveUmamiConfig({})).toEqual({ enabled: false });
    expect(resolveUmamiConfig({ ...validEnv, VITE_UMAMI_SCRIPT_URL: "http://analytics.example.com/script.js" })).toEqual({ enabled: false });
    expect(resolveUmamiConfig(validEnv)).toEqual({
      enabled: true,
      scriptUrl: validEnv.VITE_UMAMI_SCRIPT_URL,
      websiteId: validEnv.VITE_UMAMI_WEBSITE_ID,
      domains: validEnv.VITE_UMAMI_DOMAINS,
    });

    const html = "<html><head></head><body></body></html>";
    const injected = injectUmamiScript(html, validEnv);
    expect(injected).toContain('data-auto-track="false"');
    expect(injected).toContain('data-umami-tracker');
    expect(injectUmamiScript(injected, validEnv)).toBe(injected);
    expect(injectUmamiScript(html, {})).toBe(html);
  });
});
