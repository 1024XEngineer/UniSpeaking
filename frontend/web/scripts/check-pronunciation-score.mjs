import assert from "node:assert/strict";
import test from "node:test";
import { classifyScoredWord } from "../src/domain/pronunciationScore.js";

test("treats a matching weak form with an anomalous zero score as review", () => {
  assert.equal(classifyScoredWord("a", {
    wordScore: 0.3,
    phonemes: [{ expectedPhoneme: "eɪ", actualPhoneme: "eɪ" }],
  }), "is-review");
});

test("keeps genuine mismatches red and strong scores green", () => {
  assert.equal(classifyScoredWord("a", {
    wordScore: 0.3,
    phonemes: [{ expectedPhoneme: "eɪ", actualPhoneme: "ʌ" }],
  }), "is-incorrect");
  assert.equal(classifyScoredWord("contract", {
    wordScore: 54.5,
    phonemes: [{ expectedPhoneme: "k", actualPhoneme: "k" }],
  }), "is-incorrect");
  assert.equal(classifyScoredWord("for", { wordScore: 99.1 }), "is-correct");
});
