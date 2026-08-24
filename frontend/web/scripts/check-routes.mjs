import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { paths, resolveRoute, sidebarPageTarget } from "../src/controller/router.js";

const route = (pathname, search = "") => resolveRoute({ pathname, search });

const cases = [
  [paths.app.conversation, "conversation"],
  [paths.conversation.session("scene free/1"), "conversation"],
  [paths.app.scenes, "scenes"],
  [paths.scenes.word("custom_1"), "scenes"],
  [paths.scenes.phrase("custom_1"), "scenes"],
  [paths.scenes.sentence("custom_1"), "scenes"],
  [paths.scenes.session("custom_1", "session_1"), "scenes"],
  [paths.scenes.assets("custom_1"), "assets"],
  [paths.assets.latest, "assets"],
  [paths.ielts.root, "ielts"],
  [paths.ielts.assets.history, "ielts-assets"],
  [paths.ielts.step("part1", "home", "report"), "ielts"],
  [paths.ielts.step("mock", "random", "session"), "ielts"],
  [paths.interview.root, "interview"],
  [paths.interview.assets.root, "interview-assets"],
  [paths.interview.assets.history, "interview-assets"],
  [paths.interview.assets.trends, "interview-assets"],
  [paths.interview.session("interview_1"), "interview"],
  [paths.interview.report("interview_1", "session_1"), "interview"],
  [paths.app.insights, "insights"],
  [paths.app.security, "security"],
  [paths.help.root, "help"],
  [paths.help.category("quick-start"), "help"],
  [paths.help.article("start-free-conversation"), "help"],
  [paths.about.root, "about"],
  [paths.about.userAgreement, "about"],
  [paths.about.privacyPolicy, "about"],
  [paths.about.aiService, "about"],
];

for (const [pathname, expectedPage] of cases) {
  assert.equal(route(pathname).page, expectedPage, `${pathname} should resolve to ${expectedPage}`);
}

assert.equal(route("/training").canonicalPath, paths.scenes.training);
assert.equal(route("/result").canonicalPath, paths.scenes.result);
assert.equal(route("/ielts-assets").canonicalPath, paths.ielts.assets.root);
assert.equal(route("/unknown").canonicalPath, paths.root);
assert.equal(route(paths.assets.latest).assetView, "detail");
assert.equal(route("/assets", "?view=detail").assetView, "detail");
assert.equal(route(paths.auth.login, "?voice=Tina").selectedVoice, "Tina");
assert.equal(route(paths.auth.signup, "?voice=Harvey").selectedVoice, "Harvey");
assert.equal(route(paths.auth.login).selectedVoice, null);
assert.equal(route(paths.conversation.session("free chat/1")).conversationSessionId, "free chat/1");
assert.equal(route(paths.scenes.word("custom scene/1")).sceneId, "custom scene/1");
assert.equal(route(paths.scenes.phrase("custom_1")).training.stage, "phrase");
assert.equal(route(paths.scenes.sentence("custom_1")).training.initialStep, "read");
assert.equal(route(paths.scenes.session("custom_1", "session/1")).sessionId, "session/1");
assert.equal(route(paths.scenes.assets("custom_1")).assetSceneId, "custom_1");
assert.equal(route(paths.interview.session("interview scene/1")).interviewRoute.sceneId, "interview scene/1");
assert.equal(route(paths.interview.report("interview scene/1", "session/1")).interviewRoute.sessionId, "session/1");
assert.equal(route(paths.help.root).helpRoute.screen, "home");
assert.equal(route(paths.help.root).publicAccess, true);
assert.equal(route(paths.app.profile).publicAccess, false);
assert.equal(route(paths.app.insights).publicAccess, false);
assert.equal(route(paths.app.security).publicAccess, false);
assert.equal(route(paths.help.category("麦克风 音频")).helpRoute.categoryId, "麦克风 音频");
assert.equal(route(paths.help.article("修改 密码")).helpRoute.articleId, "修改 密码");
assert.equal(route("/help/feedback").canonicalPath, paths.help.root);
assert.equal(route("/help/unknown/path").canonicalPath, paths.help.root);
assert.equal(route(paths.about.root).aboutRoute.screen, "home");
assert.equal(route(paths.about.userAgreement).aboutRoute.documentId, "user-agreement");
assert.equal(route(paths.about.privacyPolicy).aboutRoute.documentId, "privacy-policy");
assert.equal(route(paths.about.aiService).aboutRoute.documentId, "ai-service");
assert.equal(route(paths.about.root).publicAccess, true);
assert.equal(route(paths.about.userAgreement).publicAccess, true);
assert.equal(route(paths.about.privacyPolicy).publicAccess, true);
assert.equal(route(paths.about.aiService).publicAccess, true);
assert.equal(route("/about/unknown").canonicalPath, paths.about.root);
assert.equal(route(paths.interview.root).interviewRoute.screen, "home");
assert.equal(route(paths.interview.assets.root).page, "interview-assets");
assert.equal(route(paths.interview.assets.root).interviewRoute.area, "assets");
assert.equal(route(paths.interview.assets.root).interviewRoute.tab, "overview");
assert.equal(route(paths.interview.assets.history).interviewRoute.tab, "history");
assert.equal(route(paths.interview.assets.trends).interviewRoute.tab, "trends");
assert.equal(route(paths.interview.session("interview scene/1")).interviewRoute.sceneId, "interview scene/1");
assert.equal(route(paths.interview.session("interview_1")).interviewRoute.screen, "session");
assert.equal(route(paths.interview.report("interview_1", "session/1")).interviewRoute.sessionId, "session/1");
assert.equal(route(paths.interview.report("interview_1", "session_1")).interviewRoute.screen, "report");
assert.equal(route("/interview/unknown").canonicalPath, paths.interview.root);
assert.equal(sidebarPageTarget("ielts", "assets"), "ielts-assets");
assert.equal(sidebarPageTarget("interview", "assets"), "interview-assets");
assert.equal(sidebarPageTarget("ielts-assets", "scenes"), "ielts");
assert.equal(sidebarPageTarget("interview-assets", "scenes"), "interview");
assert.equal(sidebarPageTarget("scenes", "assets"), "assets");
assert.equal(sidebarPageTarget("assets", "scenes"), "scenes");

const appSource = await readFile(
  new URL("../src/controller/App.jsx", import.meta.url),
  "utf8",
);
const appShellSource = appSource.slice(
  appSource.indexOf("function AppShell("),
  appSource.indexOf("function PageHeader("),
);
const trainingSource = appSource.slice(
  appSource.indexOf("function Training("),
  appSource.indexOf("function AssetModuleMenu("),
);

assert.match(
  appShellSource,
  /if \(navigationGuardActive\) \{\s+setPendingPage\(targetPage\);\s+return;/,
  "Active practice must defer sidebar navigation until confirmation",
);
assert.match(
  appShellSource,
  /onClick=\{\(\) => navigateSidebar\("profile"\)\}/,
  "Profile navigation must use the same practice guard as sidebar items",
);
assert.match(
  appShellSource,
  /<TrainingExitConfirmation open=\{Boolean\(pendingPage\)\}/,
  "Guarded navigation must reuse the existing training exit confirmation",
);
assert.match(
  trainingSource,
  /<TrainingExitConfirmation open=\{exitOpen\}/,
  "The custom training close button must use the shared exit confirmation",
);
assert.match(
  appSource,
  /\(training && !result\)[\s\S]*?freeConversationActive[\s\S]*?ieltsRoute\?\.screen === "session"[\s\S]*?interviewRoute\?\.screen === "session"/,
  "Navigation guard must cover unfinished custom, free, IELTS, and interview practice",
);

const interviewSource = await readFile(
  new URL("../src/component/interview/InterviewModule.jsx", import.meta.url),
  "utf8",
);
const stylesSource = await readFile(
  new URL("../src/common/styles.css", import.meta.url),
  "utf8",
);

assert.match(
  interviewSource,
  /className="interview-home-assets-cta" onClick=\{\(\) => onNavigate\(paths\.interview\.assets\.root\)\}/,
  "Interview home must link directly to interview learning assets",
);
assert.match(
  interviewSource,
  /className="ielts-assets-actions interview-assets-actions"/,
  "Interview asset header actions must have a module-specific layout hook",
);
assert.match(
  stylesSource,
  /\.interview-assets-page \.page-header \{ width: 100%;[\s\S]*?\.interview-assets-actions \{ margin-right: 0; \}/,
  "Interview asset header and actions must align with the full content width",
);

console.log(`Route contract passed: ${cases.length + 49} assertions`);
