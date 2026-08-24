import { fireEvent, render } from '@testing-library/react-native';

import { useRecordingPlayback } from '@/features/ielts/useRecordingPlayback';

import { IeltsAssetReport, InterviewAssetReport } from '../SpecialtyAssetsScreen';

jest.mock('@/features/ielts/useRecordingPlayback', () => ({
  useRecordingPlayback: jest.fn(),
}));

const mockedPlayback = jest.mocked(useRecordingPlayback);

describe('specialty asset reports', () => {
  beforeEach(() => {
    mockedPlayback.mockReturnValue({
      playing: false,
      error: null,
      canPlay: true,
      toggle: jest.fn(),
      stop: jest.fn(),
    });
  });

  afterEach(() => jest.clearAllMocks());

  it('renders the real IELTS report, optional feedback, and recording control', async () => {
    const toggle = jest.fn();
    mockedPlayback.mockReturnValue({ playing: false, error: null, canPlay: true, toggle, stop: jest.fn() });
    const onBack = jest.fn();
    const screen = await render(
      <IeltsAssetReport
        onBack={onBack}
        record={{
          id: 'ielts-1', type: '完整模考', title: 'Food', date: '2026-08-24', duration: '14 分钟', result: '6.5',
          estimatedBand: 6.5, summary: '表达完整。', recordingUrls: ['/recording.wav'],
          strengths: ['观点清晰'], improvements: ['补充例子'], recommendedExpressions: ['From my perspective'],
          bandScores: [6, 6.5, 7, 6], scoreReasons: ['衔接自然', '词汇准确', '句式丰富', '发音清楚'],
        } as never}
      />,
    );

    expect(screen.getByText('完整模考 · Food')).toBeTruthy();
    expect(screen.getByText('6.5')).toBeTruthy();
    expect(screen.getByText(/观点清晰/)).toBeTruthy();
    expect(screen.getByText(/From my perspective/)).toBeTruthy();
    await fireEvent.press(screen.getByRole('button', { name: '播放原始录音' }));
    expect(toggle).toHaveBeenCalledTimes(1);
    await fireEvent.press(screen.getByRole('button', { name: '返回' }));
    expect(onBack).toHaveBeenCalledTimes(1);
    screen.unmount();
  });

  it('uses report fallbacks and disables playback when an IELTS record has no audio', async () => {
    mockedPlayback.mockReturnValue({ playing: false, error: '录音加载失败', canPlay: false, toggle: jest.fn(), stop: jest.fn() });
    const screen = await render(
      <IeltsAssetReport
        onBack={jest.fn()}
        record={{ id: 'ielts-2', type: 'Part 2', title: '旅行', date: '2026-08-23', duration: '8 分钟', result: '—' } as never}
      />,
    );

    expect(screen.getByText('暂无录音')).toBeTruthy();
    expect(screen.getByText(/本次报告暂无单独保存的优势说明。/)).toBeTruthy();
    expect(screen.getByText(/本次报告暂无单独保存的改进建议。/)).toBeTruthy();
    expect(screen.getByText('录音加载失败')).toBeTruthy();
    expect(screen.getByRole('button', { name: '播放原始录音' })).toBeDisabled();
    screen.unmount();
  });

  it('renders interview scores including a missing overall score', async () => {
    const screen = await render(
      <InterviewAssetReport
        onBack={jest.fn()}
        record={{
          role: '产品经理', company: 'UniSpeaking', date: '2026-08-22', duration: '22 分钟', score: null,
          summary: '结构清晰但需量化结果。', scores: [81, 72, 66, 78, 75, 60],
        }}
      />,
    );

    expect(screen.getByText('产品经理')).toBeTruthy();
    expect(screen.getByText('结构清晰但需量化结果。')).toBeTruthy();
    expect(screen.getByText('能力 6')).toBeTruthy();
    expect(screen.getByText('快速复练')).toBeTruthy();
    screen.unmount();
  });
});
