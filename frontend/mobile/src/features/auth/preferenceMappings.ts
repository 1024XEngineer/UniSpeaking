import type { Teacher } from '@/theme/tokens';

import type {
  CefrLevel,
  PreferredAiSpeechSpeed,
  PreferredVoice,
} from './AuthService';

type LevelOption = {
  id: string;
  cefrLevel: CefrLevel;
};

const speedCodeByLabel = {
  '慢一些': 'SLOWER',
  '适中': 'MODERATE',
  '自然': 'NATURAL',
  '快一些': 'FASTER',
} as const satisfies Record<string, PreferredAiSpeechSpeed>;

const speedLabelByCode = Object.fromEntries(
  Object.entries(speedCodeByLabel).map(([label, code]) => [code, label]),
) as Record<PreferredAiSpeechSpeed, keyof typeof speedCodeByLabel>;

export function levelForCefrLevel<T extends LevelOption>(
  cefrLevel: CefrLevel | null,
  options: readonly T[],
) {
  return options.find((option) => option.cefrLevel === cefrLevel) ?? options[0];
}

export function cefrLevelForLevel(level: LevelOption) {
  return level.cefrLevel;
}

export function teacherForVoice(
  voice: PreferredVoice | null,
  options: readonly Teacher[],
) {
  return options.find((teacher) => teacher.voiceId === voice) ?? options[0];
}

export function voiceForTeacher(teacher: Teacher) {
  return teacher.voiceId;
}

export function speedLabelForCode(code: PreferredAiSpeechSpeed | null) {
  return code ? speedLabelByCode[code] : '自然';
}

export function speedCodeForLabel(label: string): PreferredAiSpeechSpeed {
  return speedCodeByLabel[label as keyof typeof speedCodeByLabel] ?? 'NATURAL';
}
