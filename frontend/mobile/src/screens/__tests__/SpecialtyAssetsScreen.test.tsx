import { fireEvent, render, waitFor } from '@testing-library/react-native';

import { useRecordingPlayback } from '@/features/ielts/useRecordingPlayback';

jest.mock('@/features/ielts/useRecordingPlayback', () => ({
  useRecordingPlayback: jest.fn(),
}));

const mockGetReport = jest.fn();
const mockDownloadRecording = jest.fn();
const mockAudioPlayer = { play: jest.fn(), pause: jest.fn(), remove: jest.fn() };

jest.mock('@/features/interview/InterviewAssetService', () => ({
  InterviewAssetService: jest.fn(() => ({ getReport: mockGetReport })),
  InterviewRecordingClient: jest.fn(() => ({ download: mockDownloadRecording })),
}));

jest.mock('expo-audio', () => ({ createAudioPlayer: jest.fn(() => mockAudioPlayer) }));

import { IeltsAssetReport, InterviewAssetRemoteReport, InterviewAssetReport } from '../SpecialtyAssetsScreen';

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
    mockGetReport.mockReset();
    mockDownloadRecording.mockReset();
    Object.values(mockAudioPlayer).forEach((mock) => mock.mockReset());
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

  it('renders a completed remote report and controls downloaded audio', async () => {
    mockGetReport.mockResolvedValue({ status: 'COMPLETED', report: {
      overallScore: 81.6, summary: '结构清晰。', dimensions: [
        { dimension: 'FLUENCY', score: 82.4, evaluation: '表达顺畅', advice: '增加量化结果' },
        { dimension: 'CUSTOM', score: null, evaluation: null, advice: null },
        { dimension: 'GRAMMAR_CONTROL', score: 75, evaluation: '', advice: null },
      ],
    } });
    const recordingAsset = { uri: 'file:///interview.wav', remove: jest.fn() };
    mockDownloadRecording.mockResolvedValue(recordingAsset);
    const onBack = jest.fn();
    const onPractice = jest.fn();
    const asset = {
      sceneId: 'scene-1', jobTitle: '产品经理', difficulty: 'HARD', practiceCount: 3,
      latestOverallScore: 82, latestPracticedAt: '2026-08-24T10:00:00Z', createdAt: '2026-08-01T10:00:00Z',
      latestSessionId: 'session-1', latestReportStatus: 'COMPLETED',
    } as never;
    const screen = await render(<InterviewAssetRemoteReport asset={asset} onBack={onBack} onPractice={onPractice} />);

    await waitFor(() => expect(screen.getByText('结构清晰。')).toBeTruthy());
    expect(screen.getByText('CUSTOM')).toBeTruthy();
    expect(screen.getByText('暂无法评分')).toBeTruthy();
    expect(screen.getByText('暂无评估说明。')).toBeTruthy();
    await fireEvent.press(screen.getByText('播放上一次完整录音'));
    await waitFor(() => expect(mockAudioPlayer.play).toHaveBeenCalledTimes(1));
    await fireEvent.press(screen.getByText('暂停上一次完整录音'));
    expect(mockAudioPlayer.pause).toHaveBeenCalledTimes(1);
    await fireEvent.press(screen.getByText('播放上一次完整录音'));
    await waitFor(() => expect(screen.getByText('暂停上一次完整录音')).toBeTruthy());
    expect(mockAudioPlayer.play).toHaveBeenCalledTimes(2);
    await fireEvent.press(screen.getByText('复练本岗位'));
    expect(onPractice).toHaveBeenCalled();
    await fireEvent.press(screen.getByRole('button', { name: '返回' }));
    expect(onBack).toHaveBeenCalled();
    screen.unmount();
  });

  it('shows a missing remote report state without a session', async () => {
    const withoutSession = {
      sceneId: 'scene-empty', jobTitle: '', difficulty: 'EASY', practiceCount: 0,
      latestOverallScore: null, latestPracticedAt: null, createdAt: '2026-08-01T10:00:00Z',
      latestSessionId: null, latestReportStatus: null,
    } as never;
    const empty = await render(<InterviewAssetRemoteReport asset={withoutSession} onBack={jest.fn()} onPractice={jest.fn()} />);
    expect(empty.getByText('未命名岗位')).toBeTruthy();
    expect(empty.getByText('尚未完成面试')).toBeTruthy();
    expect(empty.getByText('播放上一次完整录音')).toBeDisabled();
    empty.unmount();
  });

  it('shows a failed remote report status', async () => {
    const asset = {
      sceneId: 'scene-2', jobTitle: '', difficulty: 'EASY', practiceCount: 0,
      latestOverallScore: null, latestPracticedAt: null, createdAt: '2026-08-01T10:00:00Z',
      latestSessionId: 'session-2', latestReportStatus: 'FAILED',
    } as never;
    mockGetReport.mockResolvedValueOnce({ status: 'FAILED', failureReason: '有效音频不足' });
    const failed = await render(<InterviewAssetRemoteReport asset={asset} onBack={jest.fn()} onPractice={jest.fn()} />);
    await waitFor(() => expect(failed.getByText('有效音频不足')).toBeTruthy());
    failed.unmount();
  });

  it('shows a processing remote report status', async () => {
    const asset = {
      sceneId: 'scene-2', jobTitle: '', difficulty: 'EASY', practiceCount: 0,
      latestOverallScore: null, latestPracticedAt: null, createdAt: '2026-08-01T10:00:00Z',
      latestSessionId: 'session-2', latestReportStatus: 'PROCESSING',
    } as never;
    mockGetReport.mockResolvedValueOnce({ status: 'PROCESSING' });
    const processing = await render(<InterviewAssetRemoteReport asset={asset} onBack={jest.fn()} onPractice={jest.fn()} />);
    await waitFor(() => expect(processing.getByText('报告生成中，请稍后刷新。')).toBeTruthy());
    processing.unmount();
  });

  it('shows report and recording failures including non-Error fallbacks', async () => {
    const asset = {
      sceneId: 'scene-3', jobTitle: '工程师', difficulty: null, practiceCount: 1,
      latestOverallScore: null, latestPracticedAt: null, createdAt: '2026-08-01',
      latestSessionId: 'session-3', latestReportStatus: 'PROCESSING',
    } as never;
    mockGetReport.mockRejectedValueOnce('report failed');
    mockDownloadRecording.mockRejectedValueOnce('audio failed');
    const screen = await render(<InterviewAssetRemoteReport asset={asset} onBack={jest.fn()} onPractice={jest.fn()} />);
    await waitFor(() => expect(screen.getByText('报告读取失败')).toBeTruthy());
    await fireEvent.press(screen.getByText('播放上一次完整录音'));
    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('完整录音播放失败'));
    screen.unmount();
  });
});
