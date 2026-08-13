import { mapEvaluationToRecord } from '../ieltsRecordMapper';
import type { IeltsEvaluationHistoryItem } from '../types';

describe('mapEvaluationToRecord', () => {
  it('keeps real pronunciation, topic, timing and part trend metadata', () => {
    const item: IeltsEvaluationHistoryItem = {
      sessionId: 'ielts-session-1',
      ieltsId: 'ielts-1',
      mode: 'PART_PRACTICE',
      part: 'PART_2',
      assessmentType: 'DIAGNOSTIC',
      overallBandScore: null,
      fluencyCoherenceScore: 6.5,
      lexicalResourceScore: 6,
      grammaticalRangeAccuracyScore: 6.5,
      pronunciationScore: 7,
      summary: '本次回答结构清楚。',
      strengths: ['持续作答'],
      improvements: ['增加细节'],
      recommendedExpressions: ['A useful expression.'],
      partEvaluations: [{
        part: 'PART_2',
        fluencyCoherenceScore: 6.5,
        lexicalResourceScore: 6,
        grammaticalRangeAccuracyScore: 6.5,
        pronunciationScore: 7,
        summary: 'Part 2 诊断',
        strengths: [],
        improvements: [],
        recommendedExpressions: [],
      }],
      topicSelectionMethod: 'USER_SELECTED',
      topicTitles: { PART_2: 'A memorable journey' },
      recordingUrls: ['/api/ielts/recordings/session/turn-1.wav'],
      startedAt: '2026-08-12T08:00:00Z',
      endedAt: '2026-08-12T08:04:00Z',
      pronunciationReason: '基于本次有效原始语音。',
    };

    const record = mapEvaluationToRecord(item);

    expect(record).toEqual(expect.objectContaining({
      type: 'Part 2',
      title: 'A memorable journey',
      mode: 'PART_PRACTICE',
      part: 'PART_2',
      duration: '4 分钟',
      startedAt: item.startedAt,
      endedAt: item.endedAt,
      bandScores: [6.5, 6, 6.5, 7],
      scores: [72, 67, 72, 78],
      scoreReasons: [null, null, null, '基于本次有效原始语音。'],
      recordingUrls: item.recordingUrls,
    }));
    expect(record.partEvaluations).toEqual([expect.objectContaining({
      part: 'PART_2',
      pronunciationScore: 7,
    })]);
  });
});
