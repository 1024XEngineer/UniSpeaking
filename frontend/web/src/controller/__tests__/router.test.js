import { describe, expect, it } from "vitest";
import {
  hrefForPage,
  normalizePath,
  parseAboutRoute,
  parseHelpRoute,
  parseIeltsRoute,
  parseInterviewRoute,
  parseSceneRoute,
  paths,
  resolveRoute,
  sidebarPageTarget,
} from "../router.js";

describe("router", () => {
  it("normalizes missing and repeated slashes", () => {
    expect(normalizePath()).toBe("/");
    expect(normalizePath("ielts///part1/")).toBe("/ielts/part1");
    expect(normalizePath("///")).toBe("/");
  });

  it("resolves auth, splash, preview, and unknown routes", () => {
    expect(resolveRoute({ pathname: "/", search: "" })).toMatchObject({ flow: "splash", page: "conversation" });
    expect(resolveRoute({ pathname: "/login", search: "?voice=Tina" })).toMatchObject({ flow: "auth", authMode: "login", selectedVoice: "Tina" });
    expect(resolveRoute({ pathname: "/signup", search: "?voice=Harvey" })).toMatchObject({ flow: "auth", authMode: "signup", selectedVoice: "Harvey" });
    expect(resolveRoute({ pathname: "/", search: "?preview=training" })).toMatchObject({ flow: "app", page: "scenes", training: { initialStep: "learn" } });
    expect(resolveRoute({ pathname: "/unknown", search: "" })).toMatchObject({ flow: "splash", canonicalPath: "/" });
  });

  it("parses encoded scene, help, and about routes", () => {
    expect(parseSceneRoute(paths.scenes.session("scene one", "session/1"))).toMatchObject({
      page: "scenes",
      sceneId: "scene one",
      sessionId: "session/1",
      result: null,
    });
    expect(parseHelpRoute(paths.help.article("change password"))).toMatchObject({
      page: "help",
      publicAccess: true,
      helpRoute: { screen: "article", articleId: "change password" },
    });
    expect(parseHelpRoute("/help/unknown/path")).toMatchObject({ canonicalPath: "/help" });
    expect(parseAboutRoute(paths.about.privacyPolicy)).toMatchObject({
      page: "about",
      publicAccess: true,
      aboutRoute: { screen: "document", documentId: "privacy-policy" },
    });
  });

  it("handles IELTS and interview fallback routes", () => {
    expect(parseIeltsRoute(paths.ielts.step("part1", "daily topic", "report"))).toMatchObject({
      page: "ielts",
      ieltsRoute: { part: "p1", selection: "daily topic", screen: "report" },
    });
    expect(parseIeltsRoute("/ielts/mock/not-a-screen")).toMatchObject({
      ieltsRoute: { part: "mock", selection: "random", screen: "setup" },
    });
    expect(parseIeltsRoute("/ielts/assets/review")).toMatchObject({ ieltsRoute: { tab: "history" } });
    expect(parseInterviewRoute(paths.interview.report("interview one", "session one"))).toMatchObject({
      page: "interview",
      interviewRoute: { screen: "report", sceneId: "interview one", sessionId: "session one" },
    });
    expect(parseInterviewRoute("/interview/invalid")).toMatchObject({ canonicalPath: "/interview" });
  });

  it("maps pages and preserves specialty asset navigation", () => {
    expect(hrefForPage("profile")).toBe("/profile");
    expect(hrefForPage("not-a-page")).toBe("/conversation");
    expect(sidebarPageTarget("ielts", "assets")).toBe("ielts-assets");
    expect(sidebarPageTarget("interview-assets", "scenes")).toBe("interview");
    expect(sidebarPageTarget("scenes", "assets")).toBe("assets");
  });

  it("covers every public route parser branch and canonical fallback", () => {
    expect(parseIeltsRoute("/conversation")).toBeNull();
    expect(parseIeltsRoute("/ielts")).toMatchObject({ ieltsRoute: { screen: "home" } });
    expect(parseIeltsRoute("/ielts/assets/trends")).toMatchObject({
      ieltsRoute: { tab: "trends" },
      canonicalPath: paths.ielts.assets.trends,
    });
    expect(parseIeltsRoute("/ielts/assets/unknown")).toMatchObject({
      ieltsRoute: { tab: "overview" },
      canonicalPath: paths.ielts.assets.root,
    });
    expect(parseIeltsRoute("/ielts/not-a-part")).toMatchObject({ canonicalPath: paths.ielts.root });
    expect(parseIeltsRoute("/ielts/part1")).toMatchObject({ ieltsRoute: { part: "p1", screen: "topics" } });
    expect(parseIeltsRoute("/ielts/part2/%E0%A4%A/setup")).toMatchObject({
      ieltsRoute: { part: "p2", selection: "%E0%A4%A", screen: "setup" },
    });
    expect(parseIeltsRoute("/ielts/part3/topic/not-a-screen")).toMatchObject({
      ieltsRoute: { part: "p3", screen: "setup" },
    });
    expect(parseIeltsRoute("/ielts/mock/report")).toMatchObject({ ieltsRoute: { screen: "report" } });

    expect(parseSceneRoute("/conversation")).toBeNull();
    expect(parseSceneRoute("/scenes/training")).toBeNull();
    expect(parseSceneRoute("/scenes/scene/word")).toMatchObject({ training: { initialStep: "learn" } });
    expect(parseSceneRoute("/scenes/scene/phrase")).toMatchObject({ training: { initialStep: "learn" } });
    expect(parseSceneRoute("/scenes/scene/sentence")).toMatchObject({ training: { initialStep: "read" } });
    expect(parseSceneRoute("/scenes/scene/session/session-1")).toMatchObject({ result: null });
    expect(parseSceneRoute("/scenes/scene/session/session-1/result")).toMatchObject({ result: { completed: true } });
    expect(parseSceneRoute("/scenes/scene/assets")).toMatchObject({ page: "assets", assetView: "detail" });
    expect(parseSceneRoute("/scenes/scene/other")).toMatchObject({ canonicalPath: paths.app.scenes });

    expect(parseHelpRoute("/about")).toBeNull();
    expect(parseHelpRoute("/help")).toMatchObject({ helpRoute: { screen: "home" } });
    expect(parseHelpRoute(paths.help.category("billing question"))).toMatchObject({ helpRoute: { screen: "category" } });
    expect(parseHelpRoute("/help/invalid")).toMatchObject({ canonicalPath: paths.help.root });
    expect(parseAboutRoute("/help")).toBeNull();
    expect(parseAboutRoute(paths.about.root)).toMatchObject({ aboutRoute: { screen: "home" } });
    expect(parseAboutRoute(paths.about.userAgreement)).toMatchObject({ aboutRoute: { documentId: "user-agreement" } });
    expect(parseAboutRoute(paths.about.aiService)).toMatchObject({ aboutRoute: { documentId: "ai-service" } });
    expect(parseAboutRoute("/about/unknown")).toMatchObject({ canonicalPath: paths.about.root });
  });

  it("covers interview, preview, legacy, and shared page resolution", () => {
    expect(parseInterviewRoute("/about")).toBeNull();
    expect(parseInterviewRoute(paths.interview.root)).toMatchObject({ interviewRoute: { screen: "home" } });
    expect(parseInterviewRoute(paths.interview.assets.history)).toMatchObject({ interviewRoute: { tab: "history" } });
    expect(parseInterviewRoute(paths.interview.assets.trends)).toMatchObject({ interviewRoute: { tab: "trends" } });
    expect(parseInterviewRoute("/interview/assets/review")).toMatchObject({ interviewRoute: { tab: "overview" } });
    expect(parseInterviewRoute(paths.interview.session("scene one"))).toMatchObject({ interviewRoute: { screen: "session" } });
    expect(parseInterviewRoute("/interview/scenes/scene/other")).toMatchObject({ canonicalPath: paths.interview.root });

    expect(resolveRoute({ pathname: "/", search: "?preview=teacher" })).toMatchObject({ flow: "teacher" });
    expect(resolveRoute({ pathname: "/", search: "?preview=result" })).toMatchObject({ result: { completed: true } });
    expect(resolveRoute({ pathname: "/", search: "?preview=profile" })).toMatchObject({ page: "profile" });
    expect(resolveRoute({ pathname: "/", search: "?preview=not-real" })).toMatchObject({ flow: "splash" });
    expect(resolveRoute({ pathname: "/level", search: "" })).toMatchObject({ flow: "level" });
    expect(resolveRoute({ pathname: "/teacher", search: "" })).toMatchObject({ flow: "teacher" });
    expect(resolveRoute({ pathname: paths.conversation.session("session one"), search: "" })).toMatchObject({ conversationSessionId: "session one" });
    expect(resolveRoute({ pathname: "/conversation/too/many", search: "" })).toMatchObject({ flow: "splash" });
    expect(resolveRoute({ pathname: "/training", search: "" })).toMatchObject({ canonicalPath: paths.scenes.training });
    expect(resolveRoute({ pathname: "/result", search: "" })).toMatchObject({ canonicalPath: paths.scenes.result });
    expect(resolveRoute({ pathname: paths.assets.latest, search: "" })).toMatchObject({ assetView: "detail" });
    expect(resolveRoute({ pathname: "/ielts-assets", search: "" })).toMatchObject({ canonicalPath: paths.ielts.assets.root });
    expect(resolveRoute({ pathname: "/assets", search: "?view=detail" })).toMatchObject({ page: "assets", assetView: "detail" });
    expect(resolveRoute({ pathname: "/assets", search: "?view=list" })).toMatchObject({ page: "assets" });
    expect(resolveRoute({ pathname: "/settings", search: "" })).toMatchObject({ page: "settings" });
  });

  it("exposes all path builders and sidebar mappings", () => {
    expect(paths.conversation.session("a b")).toBe("/conversation/a%20b");
    expect(paths.scenes.word("a b")).toBe("/scenes/a%20b/word");
    expect(paths.scenes.phrase("a b")).toBe("/scenes/a%20b/phrase");
    expect(paths.scenes.sentence("a b")).toBe("/scenes/a%20b/sentence");
    expect(paths.scenes.session("a b", "s/1")).toBe("/scenes/a%20b/session/s%2F1");
    expect(paths.scenes.sessionResult("a b", "s/1")).toContain("/result");
    expect(paths.scenes.assets("a b")).toBe("/scenes/a%20b/assets");
    expect(paths.ielts.part("part1")).toBe("/ielts/part1");
    expect(paths.ielts.topic("part1", "daily topic")).toBe("/ielts/part1/daily%20topic");
    expect(paths.ielts.step("mock", "ignored", "session")).toBe("/ielts/mock/session");
    expect(paths.interview.session("a b")).toBe("/interview/scenes/a%20b/session");
    expect(paths.help.category("a b")).toBe("/help/category/a%20b");
    expect(paths.help.article("a b")).toBe("/help/article/a%20b");
    expect(sidebarPageTarget("ielts-assets", "scenes")).toBe("ielts");
    expect(sidebarPageTarget("interview", "assets")).toBe("interview-assets");
    expect(sidebarPageTarget("scenes", "profile")).toBe("profile");
  });
});
