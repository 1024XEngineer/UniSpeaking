const feedbackInvitationStoragePrefix = "unispeaking.feedbackInvitation.v1";
const initialInvitationThreshold = 3;
const followUpInvitationThreshold = 10;
const sessionHistoryLimit = followUpInvitationThreshold * 2;

function emptyProgress() {
  return {
    freeConversationCount: 0,
    scenePracticeCount: 0,
    invitationCount: 0,
    practicesSinceLastInvitation: 0,
    recordedSessionIds: [],
    feedbackStarted: false,
  };
}

function storageKey(userId) {
  return `${feedbackInvitationStoragePrefix}.${encodeURIComponent(String(userId))}`;
}

function readProgress(storage, userId) {
  if (!storage || !userId) return emptyProgress();
  try {
    const stored = JSON.parse(storage.getItem(storageKey(userId)) || "null");
    return {
      freeConversationCount: Math.max(0, Number(stored?.freeConversationCount) || 0),
      scenePracticeCount: Math.max(0, Number(stored?.scenePracticeCount) || 0),
      invitationCount: Math.max(0, Number(stored?.invitationCount) || (stored?.prompted ? 1 : 0)),
      practicesSinceLastInvitation: Math.max(0, Number(stored?.practicesSinceLastInvitation) || 0),
      recordedSessionIds: Array.isArray(stored?.recordedSessionIds)
        ? stored.recordedSessionIds.filter((item) => typeof item === "string").slice(-sessionHistoryLimit)
        : [],
      feedbackStarted: Boolean(stored?.feedbackStarted),
    };
  } catch {
    return emptyProgress();
  }
}

export function recordFeedbackPractice(storage, { userId, practiceType, sessionId }) {
  if (!storage || !userId || !sessionId || !["free", "scene"].includes(practiceType)) return false;
  const progress = readProgress(storage, userId);
  const recordedSessionId = `${practiceType}:${sessionId}`;
  if (progress.feedbackStarted || progress.recordedSessionIds.includes(recordedSessionId)) return false;

  const countKey = practiceType === "free" ? "freeConversationCount" : "scenePracticeCount";
  progress[countKey] += 1;
  progress.recordedSessionIds = [...progress.recordedSessionIds, recordedSessionId].slice(-sessionHistoryLimit);

  let shouldInvite = false;
  if (progress.invitationCount === 0) {
    shouldInvite = progress.freeConversationCount >= initialInvitationThreshold
      || progress.scenePracticeCount >= initialInvitationThreshold;
  } else {
    progress.practicesSinceLastInvitation += 1;
    shouldInvite = progress.practicesSinceLastInvitation >= followUpInvitationThreshold;
  }
  if (shouldInvite) {
    progress.invitationCount += 1;
    progress.practicesSinceLastInvitation = 0;
  }

  try {
    storage.setItem(storageKey(userId), JSON.stringify(progress));
  } catch {
    return false;
  }
  return shouldInvite;
}

export function markFeedbackStarted(storage, userId) {
  if (!storage || !userId) return false;
  const progress = readProgress(storage, userId);
  progress.feedbackStarted = true;
  try {
    storage.setItem(storageKey(userId), JSON.stringify(progress));
    return true;
  } catch {
    return false;
  }
}
