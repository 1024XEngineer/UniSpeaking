import { levels, speedOptions, teachers } from '@/theme/tokens';

import {
  cefrLevelForLevel,
  levelForCefrLevel,
  speedCodeForLabel,
  speedLabelForCode,
  teacherForVoice,
  voiceForTeacher,
} from '../preferenceMappings';

describe('preference mappings', () => {
  it('maps backend preference codes to the finalized mobile choices', () => {
    expect(levelForCefrLevel('B', levels).id).toBe('basic');
    expect(teacherForVoice('Harvey', teachers).id).toBe('james');
    expect(speedLabelForCode('NATURAL')).toBe('自然');
  });

  it('maps mobile choices back to backend enum codes', () => {
    expect(cefrLevelForLevel(levels[1])).toBe('B');
    expect(voiceForTeacher(teachers[1])).toBe('Harvey');
    expect(speedCodeForLabel(speedOptions[0])).toBe('SLOWER');
  });

  it('uses safe UI defaults for missing backend preferences', () => {
    expect(levelForCefrLevel(null, levels).id).toBe('starter');
    expect(teacherForVoice(null, teachers).id).toBe('clara');
    expect(speedLabelForCode(null)).toBe('自然');
  });
});
