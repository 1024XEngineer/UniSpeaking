import type { IeltsPart } from './types';

export type MobileIeltsPartId = 'p1' | 'p2' | 'p3';

const partToApi: Record<MobileIeltsPartId, IeltsPart> = {
  p1: 'PART_1',
  p2: 'PART_2',
  p3: 'PART_3',
};

const apiToPart: Record<IeltsPart, MobileIeltsPartId> = {
  PART_1: 'p1',
  PART_2: 'p2',
  PART_3: 'p3',
};

export function toApiPart(part: MobileIeltsPartId): IeltsPart {
  return partToApi[part];
}

export function fromApiPart(part: IeltsPart): MobileIeltsPartId {
  return apiToPart[part];
}

export function toApiCategory(category: string): string | null {
  if (!category || category === '全部') return null;
  return category;
}

export function formatBand(score: number | null | undefined): string {
  if (score == null || Number.isNaN(Number(score))) return '—';
  return Number(score).toFixed(1);
}

export function practiceTypeLabel(value: string | null | undefined): string {
  switch (value) {
    case 'MOCK_TEST':
      return '模考练习';
    case 'RANDOM_PART_PRACTICE':
      return '随机专项练习';
    case 'SELECTED_PART_PRACTICE':
      return '指定专项练习';
    default:
      return '未练习';
  }
}

export function parseTargetScore(targetId: string): number {
  if (targetId === '7.5+') return 7.5;
  const parsed = Number(targetId);
  return Number.isFinite(parsed) ? parsed : 7.0;
}

export const ieltsExaminers = [
  { id: 'daniel', voiceId: 'Harvey', name: 'Daniel', accent: '英式' },
  { id: 'marcus', voiceId: 'Aiden', name: 'Marcus', accent: '美式' },
  { id: 'margaret', voiceId: 'Mione', name: 'Margaret', accent: '英式' },
  { id: 'sophia', voiceId: 'Maia', name: 'Sophia', accent: '澳式' },
] as const;

export type IeltsExaminer = (typeof ieltsExaminers)[number];

export function examinerById(examinerId: string | null | undefined): IeltsExaminer {
  return ieltsExaminers.find((item) => item.id === examinerId) ?? ieltsExaminers[0];
}

export const IELTS_REALTIME_MODEL = 'qwen3.5-omni-flash-realtime';
