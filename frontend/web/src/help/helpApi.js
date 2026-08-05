import {
  createHelpFeedback,
  getMyHelpFeedbacks,
  hasAuthSession,
  lookupHelpFeedback,
} from "../apiClient.js";

const RECEIPTS_KEY = "unispeaking.help.feedback.receipts.v1";
const MAX_LOCAL_RECEIPTS = 10;

function readStoredReceipts() {
  try {
    const stored = JSON.parse(window.localStorage.getItem(RECEIPTS_KEY) || "[]");
    if (!Array.isArray(stored)) return [];
    return stored.filter((item) => (
      typeof item?.lookupCode === "string"
      && typeof item?.feedback?.feedbackNo === "string"
    )).slice(0, MAX_LOCAL_RECEIPTS);
  } catch {
    return [];
  }
}

function writeStoredReceipts(receipts) {
  try {
    window.localStorage.setItem(RECEIPTS_KEY, JSON.stringify(receipts.slice(0, MAX_LOCAL_RECEIPTS)));
    return true;
  } catch {
    return false;
  }
}

export function isFeedbackUserSignedIn() {
  return hasAuthSession();
}

export function getSavedFeedbackReceipts() {
  return readStoredReceipts();
}

export function saveFeedbackReceipt(receipt) {
  const existing = readStoredReceipts().filter(
    (item) => item.feedback.feedbackNo !== receipt.feedback.feedbackNo,
  );
  const next = [{ ...receipt, savedAt: new Date().toISOString() }, ...existing];
  writeStoredReceipts(next);
  return next;
}

export async function submitHelpFeedback(form) {
  const receipt = await createHelpFeedback({
    categoryId: form.categoryId,
    title: form.title.trim(),
    description: form.description.trim(),
    environment: form.environment.trim() || null,
  });
  saveFeedbackReceipt(receipt);
  return receipt;
}

export async function queryHelpFeedback(feedbackNo, lookupCode) {
  const feedback = await lookupHelpFeedback(feedbackNo.trim().toUpperCase(), lookupCode.trim());
  saveFeedbackReceipt({ feedback, lookupCode: lookupCode.trim() });
  return feedback;
}

export async function loadMyHelpFeedbacks() {
  const response = await getMyHelpFeedbacks();
  return Array.isArray(response?.feedbacks) ? response.feedbacks : [];
}
