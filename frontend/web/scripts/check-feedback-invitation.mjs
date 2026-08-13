import assert from "node:assert/strict";
import { markFeedbackStarted, recordFeedbackPractice } from "../src/component/help/feedbackInvitation.js";

function memoryStorage() {
  const values = new Map();
  return {
    getItem: (key) => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, value),
  };
}

const freeStorage = memoryStorage();
assert.equal(recordFeedbackPractice(freeStorage, { userId: "user-1", practiceType: "free", sessionId: "free-1" }), false);
assert.equal(recordFeedbackPractice(freeStorage, { userId: "user-1", practiceType: "free", sessionId: "free-2" }), false);
assert.equal(recordFeedbackPractice(freeStorage, { userId: "user-1", practiceType: "free", sessionId: "free-3" }), true);
assert.equal(recordFeedbackPractice(freeStorage, { userId: "user-1", practiceType: "free", sessionId: "free-4" }), false);
for (let index = 5; index < 13; index += 1) {
  assert.equal(recordFeedbackPractice(freeStorage, { userId: "user-1", practiceType: index % 2 ? "free" : "scene", sessionId: `follow-up-${index}` }), false);
}
assert.equal(recordFeedbackPractice(freeStorage, { userId: "user-1", practiceType: "scene", sessionId: "follow-up-13" }), true);
assert.equal(recordFeedbackPractice(freeStorage, { userId: "user-1", practiceType: "free", sessionId: "follow-up-14" }), false);
for (let index = 15; index < 23; index += 1) {
  assert.equal(recordFeedbackPractice(freeStorage, { userId: "user-1", practiceType: "free", sessionId: `follow-up-${index}` }), false);
}
assert.equal(recordFeedbackPractice(freeStorage, { userId: "user-1", practiceType: "scene", sessionId: "follow-up-23" }), true);

const sceneStorage = memoryStorage();
assert.equal(recordFeedbackPractice(sceneStorage, { userId: "user-2", practiceType: "scene", sessionId: "scene-1" }), false);
assert.equal(recordFeedbackPractice(sceneStorage, { userId: "user-2", practiceType: "scene", sessionId: "scene-1" }), false);
assert.equal(recordFeedbackPractice(sceneStorage, { userId: "user-2", practiceType: "scene", sessionId: "scene-2" }), false);
assert.equal(recordFeedbackPractice(sceneStorage, { userId: "user-2", practiceType: "scene", sessionId: "scene-3" }), true);
assert.equal(markFeedbackStarted(sceneStorage, "user-2"), true);
for (let index = 4; index <= 15; index += 1) {
  assert.equal(recordFeedbackPractice(sceneStorage, { userId: "user-2", practiceType: "scene", sessionId: `scene-${index}` }), false);
}

const mixedStorage = memoryStorage();
assert.equal(recordFeedbackPractice(mixedStorage, { userId: "user-3", practiceType: "free", sessionId: "mixed-1" }), false);
assert.equal(recordFeedbackPractice(mixedStorage, { userId: "user-3", practiceType: "scene", sessionId: "mixed-2" }), false);
assert.equal(recordFeedbackPractice(mixedStorage, { userId: "user-3", practiceType: "free", sessionId: "mixed-3" }), false);
assert.equal(recordFeedbackPractice(mixedStorage, { userId: "user-3", practiceType: "scene", sessionId: "mixed-4" }), false);

assert.equal(recordFeedbackPractice(mixedStorage, { userId: "user-4", practiceType: "free", sessionId: "other-1" }), false);
assert.equal(recordFeedbackPractice(mixedStorage, { userId: "user-4", practiceType: "free", sessionId: "other-2" }), false);
assert.equal(recordFeedbackPractice(mixedStorage, { userId: "user-4", practiceType: "free", sessionId: "other-3" }), true);

console.log("Feedback invitation contract passed: 47 assertions");
