import { act, fireEvent, render, waitFor } from '@testing-library/react-native';

const mockIelts = { historyRecords: [] as any[], settings: { targetScore: 7, latestEstimatedScore: 6.5, currentStreakDays: 3 }, refreshHistory: jest.fn() };
const mockInterviewService = { listAssets: jest.fn(), getReport: jest.fn() };

jest.mock('@/features/ielts/useIeltsFlowController', () => ({ useIeltsFlowController: () => mockIelts }));
jest.mock('@/features/ielts/useRecordingPlayback', () => ({ useRecordingPlayback: () => ({ canPlay: true, playing: false, error: null, toggle: jest.fn() }) }));
jest.mock('@/features/interview/InterviewAssetService', () => ({
  InterviewAssetService: jest.fn(() => mockInterviewService),
  InterviewRecordingClient: jest.fn(),
}));
jest.mock('@/navigation/specialtyMemory', () => ({ rememberSpecialty: jest.fn() }));

import { assetDate, buildIeltsWeeklyTraining, difficultyLabel, SpecialtyAssetsScreen } from '../SpecialtyAssetsScreen';

const baseProps = { onScenes: jest.fn(), onIelts: jest.fn(), onInterview: jest.fn(), onOpenRecord: jest.fn() };
const ieltsRecords = Array.from({ length: 9 }, (_, index) => ({
  id: `ielts-${index + 1}`, mode: 'MOCK_TEST', type: '完整模考', title: `IELTS ${index + 1}`,
  date: `2026-08-${String(index + 10).padStart(2, '0')}`, duration: '12 分钟', result: '6.5', estimatedBand: 6.5,
  startedAt: `2026-08-${String(index + 10).padStart(2, '0')}T10:00:00.000Z`, endedAt: `2026-08-${String(index + 10).padStart(2, '0')}T10:12:00.000Z`,
  part: index % 2 ? 'PART_2' : 'PART_1', bandScores: [6, 6.5, 7, 6.5], partEvaluations: [{ part: 'PART_1' }],
}));
const interviewAssets = Array.from({ length: 9 }, (_, index) => ({
  sceneId: `scene-${index + 1}`, jobTitle: `岗位 ${index + 1}`, difficulty: 'STANDARD', practiceCount: index + 1,
  latestOverallScore: 70 + index, latestPracticedAt: `2026-08-${String(index + 10).padStart(2, '0')}T10:00:00.000Z`, createdAt: '2026-08-01T10:00:00.000Z',
  latestSessionId: `session-${index + 1}`, latestReportStatus: 'COMPLETED',
}));
const report = { status: 'COMPLETED', report: { dimensions: [
  { dimension: 'FLUENCY', score: 82 }, { dimension: 'PRONUNCIATION_INTELLIGIBILITY', score: 76 },
  { dimension: 'LOGIC_COHERENCE', score: 70 }, { dimension: 'GRAMMAR_CONTROL', score: 75 }, { dimension: 'VOCABULARY_EXPRESSION', score: 78 },
] } };

beforeEach(() => {
  jest.clearAllMocks();
  mockIelts.historyRecords = ieltsRecords;
  mockInterviewService.listAssets.mockResolvedValue(interviewAssets);
  mockInterviewService.getReport.mockResolvedValue(report);
});

describe('SpecialtyAssetsScreen tab entries', () => {
  it('classifies asset labels and weekly IELTS records across boundary inputs', () => {
    jest.useFakeTimers().setSystemTime(new Date('2026-08-24T12:00:00Z'));
    expect(assetDate(null)).toBe('待练习');
    expect(assetDate('2026-08-24T10:00:00Z')).toBe('2026-08-24');
    expect(difficultyLabel('EASY')).toBe('简单');
    expect(difficultyLabel('HARD')).toBe('困难');
    expect(difficultyLabel(null)).toBe('标准');
    const result = buildIeltsWeeklyTraining([
      { startedAt: null },
      { startedAt: 'invalid' },
      { startedAt: '2026-08-25T10:00:00Z' },
      { startedAt: '2026-08-01T10:00:00Z' },
      { startedAt: '2026-08-24T10:00:00Z', endedAt: null, part: 'PART_1', partEvaluations: [{ part: 'PART_2' }] },
      { startedAt: '2026-08-23T10:00:00Z', endedAt: 'invalid', part: null, partEvaluations: [] },
      { startedAt: '2026-08-22T10:00:00Z', endedAt: '2026-08-22T10:05:00Z', part: 'PART_3' },
    ] as any);
    expect(result).toEqual(expect.objectContaining({ completed: 3, activeDays: 1, dailyAverage: 5, coverage: 3 }));
    jest.useRealTimers();
  });

  it('renders IELTS overview and routes to a saved record', async () => {
    const view = await render(<SpecialtyAssetsScreen kind="ielts" tab="overview" {...baseProps} />);
    await waitFor(() => expect(view.getByText('最近一次完整模考')).toBeTruthy());
    await fireEvent.press(view.getByText('IELTS 1'));
    expect(baseProps.onOpenRecord).toHaveBeenCalledWith('ielts-1');
    view.unmount();
  });

  it('paginates IELTS history and opens the final record', async () => {
    const view = await render(<SpecialtyAssetsScreen kind="ielts" tab="history" {...baseProps} />);
    await waitFor(() => expect(view.getByText('9 条')).toBeTruthy());
    expect(view.getByText('1 / 2')).toBeTruthy();
    await fireEvent.press(view.getByLabelText('下一页'));
    await waitFor(() => expect(view.getByText('IELTS 9')).toBeTruthy());
    await fireEvent.press(view.getByText('IELTS 9'));
    expect(baseProps.onOpenRecord).toHaveBeenCalledWith('ielts-9');
    await fireEvent.press(view.getByLabelText('上一页'));
    await waitFor(() => expect(view.getByText('IELTS 1')).toBeTruthy());
    view.unmount();
  });

  it('animates forward and backward tab changes and ignores the active tab', async () => {
    const onTabChange = jest.fn();
    const view = await render(
      <SpecialtyAssetsScreen kind="ielts" tab="overview" onTabChange={onTabChange} {...baseProps} />,
    );
    await waitFor(() => expect(view.getByText('最近一次完整模考')).toBeTruthy());
    await fireEvent.press(view.getByText('概览'));
    expect(onTabChange).not.toHaveBeenCalled();
    await fireEvent.press(view.getByText('训练记录'));
    await waitFor(() => expect(view.getByText('9 条')).toBeTruthy());
    expect(onTabChange).toHaveBeenLastCalledWith('history');
    await fireEvent.press(view.getByText('能力趋势'));
    await waitFor(() => expect(view.getByText('模考趋势')).toBeTruthy());
    await fireEvent.press(view.getByText('概览'));
    await waitFor(() => expect(view.getByText('最近一次完整模考')).toBeTruthy());
    expect(onTabChange).toHaveBeenLastCalledWith('overview');
    view.unmount();
  });

  it('renders IELTS trend chart and per-part recommendations', async () => {
    const view = await render(<SpecialtyAssetsScreen kind="ielts" tab="trends" {...baseProps} />);
    await waitFor(() => expect(view.getByText('模考趋势')).toBeTruthy());
    expect(view.getByText('四项能力平均分 · 最近 9 次训练')).toBeTruthy();
    expect(view.getByText('回答长度更稳定')).toBeTruthy();
    expect(view.getByText('内容组织正在改善')).toBeTruthy();
    view.unmount();
  });

  it('renders IELTS trend empty states without settings or scored parts', async () => {
    mockIelts.historyRecords = [];
    mockIelts.settings = null as any;
    const view = await render(<SpecialtyAssetsScreen kind="ielts" tab="trends" {...baseProps} />);
    await waitFor(() => expect(view.getByText('暂无模考趋势')).toBeTruthy());
    expect(view.getAllByText('暂无专项评分')).toHaveLength(3);
    expect(view.getByText('至少完成两次模考后显示趋势')).toBeTruthy();
    view.unmount();
  });

  it('renders interview overview with completed report insight and opens an asset', async () => {
    const view = await render(<SpecialtyAssetsScreen kind="interview" tab="overview" {...baseProps} />);
    await waitFor(() => expect(view.getByText('最近一次完整面试')).toBeTruthy());
    expect(view.getByText('逻辑连贯')).toBeTruthy();
    await fireEvent.press(view.getByText('岗位 1'));
    expect(baseProps.onOpenRecord).toHaveBeenCalledWith('scene-1');
    view.unmount();
  });

  it('paginates interview history', async () => {
    const view = await render(<SpecialtyAssetsScreen kind="interview" tab="history" {...baseProps} />);
    await waitFor(() => expect(view.getByText('9 条')).toBeTruthy());
    await fireEvent.press(view.getByLabelText('下一页'));
    await waitFor(() => expect(view.getByText('岗位 9')).toBeTruthy());
    view.unmount();
  });

  it('renders interview trends and aggregates completed report dimensions', async () => {
    const view = await render(<SpecialtyAssetsScreen kind="interview" tab="trends" {...baseProps} />);
    await waitFor(() => expect(view.getByText('面试评分趋势')).toBeTruthy());
    expect(view.getByText('五项能力平均表现')).toBeTruthy();
    expect(view.getByText('岗位覆盖 9 个 · 已完成报告 9 个 · 累计练习 45 次')).toBeTruthy();
    view.unmount();
  });

  it('renders empty and failed interview trend data', async () => {
    mockInterviewService.listAssets.mockRejectedValueOnce('offline');
    const failed = await render(<SpecialtyAssetsScreen kind="interview" tab="trends" {...baseProps} />);
    await waitFor(() => expect(failed.getByText('面试资产加载失败')).toBeTruthy());
    expect(failed.getByText('完成面试并生成报告后显示评分趋势。')).toBeTruthy();
    failed.unmount();

    mockInterviewService.listAssets.mockResolvedValueOnce([]);
    const empty = await render(<SpecialtyAssetsScreen kind="interview" tab="trends" {...baseProps} />);
    await waitFor(() => expect(empty.getByText('岗位覆盖 0 个 · 已完成报告 0 个 · 累计练习 0 次')).toBeTruthy());
    empty.unmount();
  });

  it('ignores interview asset and report results after unmount', async () => {
    let resolveAssets!: (value: any[]) => void;
    mockInterviewService.listAssets.mockReturnValueOnce(new Promise((resolve) => { resolveAssets = resolve; }));
    const pending = await render(<SpecialtyAssetsScreen kind="interview" tab="history" {...baseProps} />);
    pending.unmount();
    await act(async () => {
      resolveAssets(interviewAssets);
      await Promise.resolve();
      await Promise.resolve();
    });

    let resolveReport!: (value: any) => void;
    mockInterviewService.listAssets.mockResolvedValueOnce(interviewAssets.slice(0, 1));
    mockInterviewService.getReport.mockReturnValueOnce(new Promise((resolve) => { resolveReport = resolve; }));
    const reportPending = await render(<SpecialtyAssetsScreen kind="interview" tab="overview" {...baseProps} />);
    await waitFor(() => expect(mockInterviewService.getReport).toHaveBeenCalled());
    reportPending.unmount();
    await act(async () => {
      resolveReport(report);
      await Promise.resolve();
      await Promise.resolve();
    });
  });

});
