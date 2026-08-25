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

  it('covers mock-test titles, every part label, and metadata fallback paths', () => {
    const base = {
      part: null,
      assessmentType: 'DIAGNOSTIC',
      overallBandScore: null,
      fluencyCoherenceScore: null,
      lexicalResourceScore: Number.NaN,
      grammaticalRangeAccuracyScore: null,
      pronunciationScore: 0,
      summary: '',
      strengths: [],
      improvements: [],
      recommendedExpressions: [],
    };

    expect(mapEvaluationToRecord({ ...base, part: 'PART_1' }, {
      sessionId: 'part-1',
      mode: 'PART_PRACTICE',
      part: 'PART_1',
      topicTitles: { PART_1: 'Part one' },
      startedAt: 'invalid',
      endedAt: 'invalid',
    })).toEqual(expect.objectContaining({
      id: 'part-1',
      type: 'Part 1',
      title: 'Part one',
      date: '刚刚',
      duration: '—',
      scores: [0, 0, 0, 0],
    }));

    expect(mapEvaluationToRecord({ ...base, part: 'PART_3' }, {
      sessionId: 'part-3',
      mode: 'PART_PRACTICE',
      part: 'PART_3',
      topicTitles: { PART_2: 'Part two fallback' },
      startedAt: '2026-01-01T00:00:00Z',
      endedAt: '2025-01-01T00:00:00Z',
    })).toEqual(expect.objectContaining({
      type: 'Part 3',
      title: 'Part two fallback',
      duration: '—',
    }));

    expect(mapEvaluationToRecord({ ...base, part: 'PART_2', mode: 'MOCK_TEST' }, {
      sessionId: 'mock',
      mode: 'PART_PRACTICE',
      part: 'PART_2',
      topicTitles: {},
      startedAt: '2026-01-01T00:00:00Z',
      endedAt: '2026-01-01T00:00:00Z',
    })).toEqual(expect.objectContaining({
      type: '完整模考',
      title: '完整口语模拟',
    }));
  });

  it('uses result metadata and defaults when a result has no history fields', () => {
    jest.spyOn(Date, 'now').mockReturnValue(123);
    const record = mapEvaluationToRecord({
      part: null,
      assessmentType: 'DIAGNOSTIC',
      overallBandScore: 6,
      fluencyCoherenceScore: 6,
      lexicalResourceScore: 6,
      grammaticalRangeAccuracyScore: 6,
      pronunciationScore: 6,
      summary: 'summary',
      strengths: [],
      improvements: [],
      recommendedExpressions: [],
    }, {
      sessionId: 'history-missing-fields',
      mode: 'PART_PRACTICE',
      part: null,
      topicTitles: {},
      startedAt: '2026-01-01T00:00:00Z',
      endedAt: '2026-01-01T00:00:00Z',
    });

    expect(record).toEqual(expect.objectContaining({
      id: 'history-missing-fields',
      type: 'Part 1',
      title: 'IELTS 专项练习',
      result: '预估 6.0',
      estimatedBand: 6,
    }));
    jest.restoreAllMocks();
  });

  it('covers current-day, absent timing, title, session, and mode fallbacks', () => {
    jest.useFakeTimers().setSystemTime(new Date('2026-08-24T12:00:00Z'));
    const base = {
      part: null, assessmentType: 'DIAGNOSTIC', overallBandScore: null,
      fluencyCoherenceScore: null, lexicalResourceScore: null,
      grammaticalRangeAccuracyScore: null, pronunciationScore: null,
      summary: '', strengths: [], improvements: [], recommendedExpressions: [],
    } as const;
    expect(mapEvaluationToRecord(base as any)).toEqual(expect.objectContaining({
      id: expect.stringMatching(/^ielts-/), type: 'Part 1', title: 'IELTS 专项练习', date: '刚刚', duration: '—', mode: 'PART_PRACTICE',
    }));
    expect(mapEvaluationToRecord({
      ...base, sessionId: 'today', mode: 'PART_PRACTICE', startedAt: '2026-08-24T10:00:00Z', endedAt: '2026-08-24T11:00:00Z',
    } as any)).toEqual(expect.objectContaining({ date: '刚刚', duration: '60 分钟' }));
    expect(mapEvaluationToRecord({ ...base } as any, { sessionId: 'missing-end', mode: 'PART_PRACTICE', part: null, startedAt: '2026-08-24T10:00:00Z', endedAt: undefined } as any)).toEqual(expect.objectContaining({ duration: '—' }));
    jest.useRealTimers();
  });
});
