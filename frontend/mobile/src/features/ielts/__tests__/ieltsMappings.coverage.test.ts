import { examinerById, formatBand, fromApiPart, parseTargetScore, practiceTypeLabel, toApiCategory, toApiPart } from '../ieltsMappings';

describe('IELTS mapping helpers', () => {
  it('maps parts, categories, scores and practice labels', () => {
    expect(toApiPart('p1')).toBe('PART_1');
    expect(fromApiPart('PART_3')).toBe('p3');
    expect(toApiCategory('全部')).toBeNull();
    expect(toApiCategory('人物')).toBe('人物');
    expect(formatBand(null)).toBe('—');
    expect(formatBand(6.5)).toBe('6.5');
    expect(parseTargetScore('7.5+')).toBe(7.5);
    expect(parseTargetScore('bad')).toBe(7);
    expect(practiceTypeLabel('MOCK_TEST')).toBe('模考练习');
    expect(practiceTypeLabel('RANDOM_PART_PRACTICE')).toBe('随机专项练习');
    expect(practiceTypeLabel('SELECTED_PART_PRACTICE')).toBe('指定专项练习');
    expect(examinerById('unknown').id).toBe('daniel');
  });
});
