const weakFormWords = new Set(["a", "an", "the", "to", "of", "for"]);

const normalizePhoneme = (value) => String(value || "")
  .trim()
  .toLowerCase();

export function classifyScoredWord(expectedWord, result) {
  const score = Number(result?.wordScore);
  if (Number.isFinite(score) && score >= 80) return "is-correct";

  const phonemes = Array.isArray(result?.phonemes) ? result.phonemes : [];
  const phonemesMatch = phonemes.length > 0 && phonemes.every((phoneme) => (
    normalizePhoneme(phoneme?.expectedPhoneme)
      === normalizePhoneme(phoneme?.actualPhoneme)
  ));
  if (
    weakFormWords.has(String(expectedWord || "").toLowerCase())
    && Number.isFinite(score)
    && score < 10
    && phonemesMatch
  ) {
    return "is-review";
  }
  return "is-incorrect";
}
