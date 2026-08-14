import { fireEvent, render } from '@testing-library/react-native';

import { IeltsPracticeScoreDialog } from '../IeltsPracticeScoreDialog';
import type { IeltsEvaluationResult } from '../types';

const evaluation: IeltsEvaluationResult = {
  part: 'PART_1',
  assessmentType: 'PART',
  overallBandScore: null,
  fluencyCoherenceScore: 6.5,
  lexicalResourceScore: 7,
  grammaticalRangeAccuracyScore: 6,
  pronunciationScore: 6.5,
  summary: '本次专项表现稳定。',
  strengths: [],
  improvements: [],
  recommendedExpressions: [],
};

describe('IeltsPracticeScoreDialog', () => {
  it('shows only the four part-practice dimensions without an overall score', async () => {
    const screen = await render(
      <IeltsPracticeScoreDialog evaluation={evaluation} onHome={jest.fn()} onDetails={jest.fn()} />,
    );

    expect(screen.getByText('本次专项表现')).toBeTruthy();
    expect(screen.getByText('流利度与连贯性')).toBeTruthy();
    expect(screen.getByText('词汇资源')).toBeTruthy();
    expect(screen.getByText('语法多样性与准确性')).toBeTruthy();
    expect(screen.getByText('发音')).toBeTruthy();
    expect(screen.queryByText('本次模拟评分')).toBeNull();
    expect(screen.queryByText('ESTIMATED BAND')).toBeNull();
  });

  it('provides the same two completion actions as the Web dialog', async () => {
    const onHome = jest.fn();
    const onDetails = jest.fn();
    const screen = await render(
      <IeltsPracticeScoreDialog evaluation={evaluation} onHome={onHome} onDetails={onDetails} />,
    );

    await fireEvent.press(screen.getByText('返回训练中心'));
    await fireEvent.press(screen.getByText('查看详细报告'));
    expect(onHome).toHaveBeenCalledTimes(1);
    expect(onDetails).toHaveBeenCalledTimes(1);
  });
});
