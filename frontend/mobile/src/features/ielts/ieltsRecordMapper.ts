import type { IeltsLearningRecord } from '@/data/learningAssets';

import type { IeltsEvaluationHistoryItem, IeltsEvaluationResult, IeltsMode, IeltsPart } from './types';
import { formatBand } from './ieltsMappings';

function bandToChartScore(score: number | null | undefined): number {
  if (score == null || Number.isNaN(Number(score))) return 0;
  return Math.round((Number(score) / 9) * 100);
}

function formatRelativeDate(iso: string | null | undefined): string {
  if (!iso) return '刚刚';
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return '刚刚';
  const now = new Date();
  const sameDay =
    date.getFullYear() === now.getFullYear() &&
    date.getMonth() === now.getMonth() &&
    date.getDate() === now.getDate();
  if (sameDay) return '刚刚';
  return `${date.getMonth() + 1}/${date.getDate()}`;
}

function formatDuration(startedAt: string | null | undefined, endedAt: string | null | undefined): string {
  if (!startedAt || !endedAt) return '—';
  const start = new Date(startedAt).getTime();
  const end = new Date(endedAt).getTime();
  if (!Number.isFinite(start) || !Number.isFinite(end) || end <= start) return '—';
  const minutes = Math.max(1, Math.round((end - start) / 60_000));
  return `${minutes} 分钟`;
}

function recordType(mode: IeltsMode, part: IeltsPart | null): IeltsLearningRecord['type'] {
  if (mode === 'MOCK_TEST') return '完整模考';
  switch (part) {
    case 'PART_1':
      return 'Part 1';
    case 'PART_2':
      return 'Part 2';
    case 'PART_3':
      return 'Part 3';
    default:
      return 'Part 1';
  }
}

function recordTitle(item: IeltsEvaluationHistoryItem): string {
  if (item.mode === 'MOCK_TEST') return '完整口语模拟';
  const titles = item.topicTitles ?? {};
  return (
    titles[item.part ?? 'PART_1'] ??
    titles.PART_1 ??
    titles.PART_2 ??
    titles.PART_3 ??
    'IELTS 专项练习'
  );
}

export function mapEvaluationToRecord(
  item: IeltsEvaluationHistoryItem | IeltsEvaluationResult,
  meta?: Pick<IeltsEvaluationHistoryItem, 'sessionId' | 'mode' | 'part' | 'topicTitles' | 'startedAt' | 'endedAt'>,
): IeltsLearningRecord {
  const sessionId =
    'sessionId' in item ? item.sessionId : meta?.sessionId ?? `ielts-${Date.now()}`;
  const mode = 'mode' in item ? item.mode : meta?.mode ?? 'PART_PRACTICE';
  const part = item.part ?? meta?.part ?? null;
  const startedAt = 'startedAt' in item ? item.startedAt : meta?.startedAt;
  const endedAt = 'endedAt' in item ? item.endedAt : meta?.endedAt;
  const topicTitles = 'topicTitles' in item ? item.topicTitles : meta?.topicTitles;

  return {
    id: sessionId,
    type: recordType(mode, part),
    title: topicTitles ? recordTitle({ ...item, mode, part, topicTitles } as IeltsEvaluationHistoryItem) : 'IELTS 专项练习',
    date: formatRelativeDate(endedAt ?? startedAt),
    duration: formatDuration(startedAt, endedAt),
    result: `预估 ${formatBand(item.overallBandScore)}`,
    estimatedBand: Number(item.overallBandScore),
    scores: [
      bandToChartScore(item.fluencyCoherenceScore),
      bandToChartScore(item.lexicalResourceScore),
      bandToChartScore(item.grammaticalRangeAccuracyScore),
      bandToChartScore(item.pronunciationScore),
    ],
    recordingUrls:
      'recordingUrls' in item && item.recordingUrls?.length
        ? item.recordingUrls
        : undefined,
  };
}
