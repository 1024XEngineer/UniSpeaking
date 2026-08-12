import type { ApiRequestOptions } from '@/infrastructure/http/ApiClient';

import { LearningAssetService } from '../LearningAssetService';

function createClient(responses: unknown[]) {
  return {
    request: jest.fn(
      async (_path: string, _options?: ApiRequestOptions) => responses.shift(),
    ),
  };
}

const summary = {
  sceneId: 'scene/airport',
  title: '机场行李托运',
  background: '在机场柜台办理行李托运。',
  wordCount: 1,
  phraseCount: 1,
  sentenceCount: 1,
  latestSessionId: 'session-1',
  latestScore: 88,
  latestPracticedAt: '2026-08-05T08:00:00+08:00',
  practiceCount: 2,
  createdAt: '2026-08-04T08:00:00+08:00',
};

const detail = {
  sceneId: summary.sceneId,
  title: summary.title,
  background: summary.background,
  aiRole: '航空公司工作人员',
  userRole: '乘客',
  learningGoal: '确认行李重量和登机信息。',
  wordList: [
    {
      contentId: 'word-1',
      englishText: 'baggage',
      chineseText: '行李',
      phonetic: '/ˈbæɡɪdʒ/',
    },
  ],
  phraseList: [
    {
      contentId: 'phrase-1',
      englishText: 'check in',
      chineseText: '办理托运',
      phonetic: null,
    },
  ],
  sentenceList: [
    {
      contentId: 'sentence-1',
      englishText: 'I would like to check in this bag.',
      chineseText: '我想托运这个行李。',
      phonetic: null,
    },
  ],
  latestSessionId: 'session-1',
  dialogueEvaluation: {
    dialogue: [
      { owner: 0, content: 'May I see your passport?', audio: null },
      { owner: 1, content: 'Here you are.', audio: null },
    ],
    turnEvaluation: [
      {
        turnNo: 1,
        transcript: 'Here you are.',
        overallScore: 86,
        rhythmScore: 84,
        toneScore: 85,
        integrityScore: 88,
        pronunciationScore: 87,
        fluencyScore: 86,
        feedbackSummary: '表达清楚。',
        suggestedExpression: 'Here is my passport.',
        words: [],
      },
    ],
  },
  latestReport: {
    accuracyScore: 88,
    fluencyScore: 86,
    grammarScore: 90,
    vocabularyScore: 84,
    naturalnessScore: 87,
    finalScore: 88,
    summary: '完成了托运任务。',
    strengths: ['表达清楚'],
    improvements: ['补充礼貌用语'],
  },
  reportHistory: [
    {
      sceneId: summary.sceneId,
      sessionId: 'session-1',
      report: { finalScore: 88 },
      createdAt: '2026-08-05T08:00:00+08:00',
    },
  ],
};

describe('LearningAssetService', () => {
  it('loads the authenticated asset summary list and maps display metadata', async () => {
    const client = createClient([[summary]]);
    const service = new LearningAssetService(client);

    await expect(service.listRecords()).resolves.toEqual([
      expect.objectContaining({
        id: 'scene/airport',
        title: '机场行李托运',
        date: '2026-08-05',
        status: '已完成',
        score: 88,
        practiceCount: 2,
        expressions: [],
        conversation: [],
      }),
    ]);
    expect(client.request).toHaveBeenCalledWith('/api/custom-scenes/assets');
  });

  it('loads an encoded asset detail and maps learning items and turn feedback', async () => {
    const client = createClient([detail]);
    const service = new LearningAssetService(client);

    const record = await service.getRecord('scene/airport');

    expect(client.request).toHaveBeenCalledWith(
      '/api/custom-scenes/scene%2Fairport/assets',
    );
    expect(record).toEqual(
      expect.objectContaining({
        id: 'scene/airport',
        score: 88,
        practiceCount: 1,
        expressions: [
          expect.objectContaining({ type: '单词', englishText: 'baggage' }),
          expect.objectContaining({ type: '词组', englishText: 'check in' }),
          expect.objectContaining({
            type: '句子',
            englishText: 'I would like to check in this bag.',
          }),
        ],
        conversation: [
          expect.objectContaining({
            role: 'assistant',
            speaker: '航空公司工作人员',
            text: 'May I see your passport?',
          }),
          expect.objectContaining({
            role: 'user',
            speaker: '你',
            text: 'Here you are.',
            feedback: {
              feedbackSummary: '表达清楚。',
              suggestedExpression: 'Here is my passport.',
            },
          }),
        ],
      }),
    );
  });

  it('restores the complete generated scene needed by backend repractice', async () => {
    const client = createClient([detail]);
    const service = new LearningAssetService(client);

    await expect(service.getScene('scene/airport')).resolves.toEqual(
      expect.objectContaining({
        sceneId: 'scene/airport',
        background: '在机场柜台办理行李托运。',
        aiRole: '航空公司工作人员',
        userRole: '乘客',
        learningGoal: '确认行李重量和登机信息。',
        wordList: detail.wordList,
        phraseList: detail.phraseList,
        sentenceList: detail.sentenceList,
      }),
    );
  });
});
