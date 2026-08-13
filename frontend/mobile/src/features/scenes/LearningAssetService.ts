import type {
  AssetDialogueMessage,
  LearningExpression,
  SceneLearningRecord,
} from '@/data/learningAssets';
import {
  sceneCategoryForLabel,
  type SceneLabel,
} from '@/data/sceneCategories';
import type { ApiRequestOptions } from '@/infrastructure/http/ApiClient';

import type { GeneratedScene, LearningContentItem } from './SceneService';

type ApiRequester = {
  request(path: string, options?: ApiRequestOptions): Promise<unknown>;
};

type LearningAssetSummary = {
  sceneId: string;
  title: string;
  label: SceneLabel;
  latestSessionId: string | null;
  latestScore: number | null;
  latestPracticedAt: string | null;
  practiceCount: number;
  createdAt: string;
};

type DialogueMessage = {
  owner: 0 | 1;
  content: string;
};

type DialogueTurnEvaluation = {
  turnNo: number;
  transcript: string;
  feedbackSummary: string | null;
  suggestedExpression: string | null;
};

type DialogueReport = {
  finalScore: number;
};

type LearningAssetDetail = {
  sceneId: string;
  title: string;
  label: SceneLabel;
  aiRole: string;
  background: string;
  userRole: string;
  learningGoal: string;
  wordList: LearningContentItem[];
  phraseList: LearningContentItem[];
  sentenceList: LearningContentItem[];
  latestSessionId: string | null;
  dialogueEvaluation: {
    dialogue: DialogueMessage[];
    turnEvaluation: DialogueTurnEvaluation[];
  } | null;
  latestReport: DialogueReport | null;
  reportHistory: { createdAt: string }[];
};

function displayDate(value: string | null | undefined) {
  return value?.slice(0, 10) || '待练习';
}

function mapExpressions(
  items: LearningContentItem[],
  type: LearningExpression['type'],
): LearningExpression[] {
  return items.map((item) => ({
    id: item.contentId,
    type,
    englishText: item.englishText,
    chineseText: item.chineseText,
    phonetic: item.phonetic ?? undefined,
  }));
}

function mapConversation(detail: LearningAssetDetail): AssetDialogueMessage[] {
  const evaluation = detail.dialogueEvaluation;
  if (!evaluation) return [];
  let learnerTurn = 0;
  return evaluation.dialogue.map((message, index) => {
    const role = message.owner === 0 ? 'assistant' : 'user';
    const feedback =
      role === 'user' ? evaluation.turnEvaluation[learnerTurn++] : undefined;
    return {
      id: `${detail.latestSessionId ?? detail.sceneId}-${index}`,
      role,
      speaker: role === 'assistant' ? detail.aiRole : '你',
      text: message.content,
      ...(feedback?.feedbackSummary && feedback.suggestedExpression
        ? {
            feedback: {
              feedbackSummary: feedback.feedbackSummary,
              suggestedExpression: feedback.suggestedExpression,
            },
          }
        : {}),
    };
  });
}

function isSummaryList(value: unknown): value is LearningAssetSummary[] {
  return Array.isArray(value) && value.every((item) => {
    if (!item || typeof item !== 'object') return false;
    const summary = item as Partial<LearningAssetSummary>;
    return Boolean(summary.sceneId && summary.title);
  });
}

function isDetail(value: unknown): value is LearningAssetDetail {
  if (!value || typeof value !== 'object') return false;
  const detail = value as Partial<LearningAssetDetail>;
  return Boolean(
    detail.sceneId &&
      detail.title &&
      Array.isArray(detail.wordList) &&
      Array.isArray(detail.phraseList) &&
      Array.isArray(detail.sentenceList) &&
      Array.isArray(detail.reportHistory),
  );
}

export class LearningAssetService {
  constructor(private readonly client: ApiRequester) {}

  async listRecords(): Promise<SceneLearningRecord[]> {
    const summaries = await this.client.request('/api/custom-scenes/assets');
    if (!isSummaryList(summaries)) throw new Error('学习资产列表格式不正确');
    return summaries.map((summary) => ({
      id: summary.sceneId,
      title: summary.title,
      date: displayDate(summary.latestPracticedAt ?? summary.createdAt),
      status: summary.latestSessionId ? '已完成' : '待练习',
      score: summary.latestScore,
      category: sceneCategoryForLabel(summary.label),
      practiceCount: summary.practiceCount,
      expressions: [],
      conversation: [],
    }));
  }

  async getRecord(sceneId: string): Promise<SceneLearningRecord> {
    const value = await this.getDetail(sceneId);
    const latestHistory = value.reportHistory[value.reportHistory.length - 1];
    return {
      id: value.sceneId,
      title: value.title,
      date: displayDate(latestHistory?.createdAt),
      status: value.latestSessionId ? '已完成' : '待练习',
      score: value.latestReport?.finalScore ?? null,
      category: sceneCategoryForLabel(value.label),
      practiceCount: value.reportHistory.length,
      expressions: [
        ...mapExpressions(value.wordList, '单词'),
        ...mapExpressions(value.phraseList, '词组'),
        ...mapExpressions(value.sentenceList, '句子'),
      ],
      conversation: mapConversation(value),
    };
  }

  async getScene(sceneId: string): Promise<GeneratedScene> {
    const value = await this.getDetail(sceneId);
    return {
      sceneId: value.sceneId,
      title: value.title,
      label: value.label,
      background: value.background,
      aiRole: value.aiRole,
      userRole: value.userRole,
      learningGoal: value.learningGoal,
      estimatedMinutes: 8,
      wordList: value.wordList,
      phraseList: value.phraseList,
      sentenceList: value.sentenceList,
      scenePrompt: '',
    };
  }

  private async getDetail(sceneId: string): Promise<LearningAssetDetail> {
    const value = await this.client.request(
      `/api/custom-scenes/${encodeURIComponent(sceneId)}/assets`,
    );
    if (!isDetail(value)) throw new Error('学习资产详情格式不正确');
    return value;
  }
}
