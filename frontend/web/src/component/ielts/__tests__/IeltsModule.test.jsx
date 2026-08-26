import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("../../../infrastructure/http/apiClient.js", () => ({
  createIeltsSceneFlow: vi.fn(),
  fetchAuthenticatedMedia: vi.fn(),
  generateIeltsScene: vi.fn(),
  generateIeltsEvaluation: vi.fn(),
  getIeltsEvaluationHistory: vi.fn(),
  getIeltsSettings: vi.fn(),
  getIeltsTopics: vi.fn(),
  getIeltsTraining: vi.fn(),
  updateIeltsSettings: vi.fn(),
}));
vi.mock("../../../websocket/realtimeClient.js", () => ({ createRealtimeClient: vi.fn() }));
vi.mock("../../../analytics/analyticsClient.js", () => ({
  analytics: { training: vi.fn(() => ({ attempt: vi.fn(), started: vi.fn(), complete: vi.fn(), abandon: vi.fn(), fail: vi.fn(), setVisible: vi.fn(), pause: vi.fn(), resume: vi.fn() })) },
}));

import {
  createIeltsSceneFlow,
  fetchAuthenticatedMedia,
  generateIeltsScene,
  generateIeltsEvaluation,
  getIeltsEvaluationHistory,
  getIeltsSettings,
  getIeltsTopics,
  getIeltsTraining,
  updateIeltsSettings,
} from "../../../infrastructure/http/apiClient.js";
import { createRealtimeClient } from "../../../websocket/realtimeClient.js";
import {
  IeltsAssets,
  IeltsHeader,
  IeltsTrainingCenter,
  SimpleCta,
  TrainingCta,
  TrendLineChart,
} from "../IeltsModule.jsx";

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
  cleanup();
});
beforeEach(() => {
  vi.clearAllMocks();
  createRealtimeClient.mockReset();
  window.localStorage.clear();
  getIeltsSettings.mockResolvedValue({ targetScore: 6.5, currentStreakDays: 3, todayCompletedCount: 2 });
  getIeltsEvaluationHistory.mockResolvedValue([]);
  getIeltsTopics.mockResolvedValue({ categories: [], topics: [], total: 0, totalPages: 0 });
  getIeltsTraining.mockResolvedValue({ topicId: "topic-1", questions: [] });
  updateIeltsSettings.mockResolvedValue({ targetScore: 7 });
  generateIeltsScene.mockResolvedValue({ ieltsId: "ielts-scene", voiceId: "Harvey" });
  createIeltsSceneFlow.mockResolvedValue({});
});

function mockIeltsRealtimeClient(overrides = {}) {
  const client = {
    start: vi.fn().mockResolvedValue({ sessionId: "ielts-session" }),
    stop: vi.fn().mockResolvedValue({}),
    setMuted: vi.fn(),
    transitionIeltsPart2: vi.fn().mockResolvedValue({}),
    forceIeltsPart3TurnTimeout: vi.fn().mockResolvedValue({}),
    waitForEvaluations: vi.fn().mockResolvedValue(),
    ...overrides,
  };
  createRealtimeClient.mockImplementation((options) => {
    client.options = options;
    return client;
  });
  return client;
}

function mockCanvas() {
  const context = {
    scale: vi.fn(), clearRect: vi.fn(), beginPath: vi.fn(), moveTo: vi.fn(), lineTo: vi.fn(),
    stroke: vi.fn(), fill: vi.fn(), closePath: vi.fn(), arc: vi.fn(), fillText: vi.fn(),
    createLinearGradient: vi.fn(() => ({ addColorStop: vi.fn() })),
  };
  vi.spyOn(HTMLCanvasElement.prototype, "getBoundingClientRect").mockReturnValue({ width: 320, height: 160 });
  vi.spyOn(HTMLCanvasElement.prototype, "getContext").mockReturnValue(context);
  return context;
}

describe("IELTS exported presentation components", () => {
  it("renders CTA and header variants and wires actions", () => {
    const onClick = vi.fn();
    const onBack = vi.fn();
    render(<><SimpleCta onClick={onClick}>普通 CTA</SimpleCta><TrainingCta onClick={onClick} disabled>训练 CTA</TrainingCta><IeltsHeader eyebrow="EYEBROW" title="标题" subtitle="副标题" onBack={onBack} leadAction={<span>lead</span>} action={<button>action</button>} /></>);
    expect(screen.getByRole("button", { name: /普通 CTA/ })).toHaveClass("ielts-cta");
    expect(screen.getByRole("button", { name: /训练 CTA/ })).toBeDisabled();
    fireEvent.click(screen.getByRole("button", { name: /普通 CTA/ }));
    fireEvent.click(screen.getByRole("button", { name: /返回/ }));
    expect(onClick).toHaveBeenCalledTimes(1);
    expect(onBack).toHaveBeenCalledTimes(1);
    expect(screen.getByText("副标题")).toBeInTheDocument();
  });

  it("draws empty, sparse and scored trend data, including resize redraw", () => {
    const context = mockCanvas();
    const { rerender } = render(<TrendLineChart values={[]} ariaLabel="空趋势" />);
    expect(screen.getByLabelText("空趋势")).toBeInTheDocument();
    rerender(<TrendLineChart values={[6, null, 7.5]} />);
    fireEvent(window, new Event("resize"));
    expect(context.getContext ? context.getContext : context.scale).toHaveBeenCalled();
    expect(context.createLinearGradient).toHaveBeenCalled();
    expect(context.fillText).toHaveBeenCalledWith("--", expect.any(Number), expect.any(Number));
  });
});

const reports = [
  {
    sessionId: "report-1", mode: "MOCK_TEST", part: "PART_1", topicTitles: { PART_1: "Daily life" },
    startedAt: "2026-08-25T10:00:00Z", endedAt: "2026-08-25T10:12:00Z", overallBandScore: 6.5,
    fluencyCoherenceScore: 6, lexicalResourceScore: 6.5, grammaticalRangeAccuracyScore: 7, pronunciationScore: 6,
    summary: "表现稳定", strengths: ["流畅"], improvements: ["展开"], recommendedExpressions: ["for example"],
    recordingUrls: [], topicSelectionMethod: "RANDOM", partEvaluations: [],
  },
];

describe("IeltsAssets", () => {
  it("covers loading, successful overview, tabs, menu navigation and trends", async () => {
    let resolveSettings;
    getIeltsSettings.mockReturnValueOnce(new Promise((resolve) => { resolveSettings = resolve; }));
    getIeltsEvaluationHistory.mockReturnValueOnce(new Promise(() => {}));
    render(<IeltsAssets route={{ tab: "overview" }} onNavigate={vi.fn()} onBack={vi.fn()} onBackToAssets={vi.fn()} onBackToInterview={vi.fn()} onTraining={vi.fn()} />);
    expect(screen.getByRole("status", { name: /正在读取后端评分记录/ })).toBeInTheDocument();
    resolveSettings({ targetScore: 7 });
    cleanup();

    const onNavigate = vi.fn();
    getIeltsSettings.mockResolvedValue({ targetScore: 7, currentStreakDays: 2, todayCompletedCount: 1 });
    getIeltsEvaluationHistory.mockResolvedValue(reports);
    const callbacks = { onBack: vi.fn(), onBackToAssets: vi.fn(), onBackToInterview: vi.fn(), onTraining: vi.fn() };
    render(<IeltsAssets route={{ tab: "overview" }} onNavigate={onNavigate} {...callbacks} />);
    await waitFor(() => expect(screen.getByText("最近一次完整模考")).toBeInTheDocument());
    expect(screen.getByText("预估 6.5")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "能力趋势" }));
    await waitFor(() => expect(onNavigate).toHaveBeenCalledWith("/ielts/assets/trends"));

    cleanup();
    render(<IeltsAssets route={{ tab: "history" }} onNavigate={onNavigate} {...callbacks} />);
    await waitFor(() => expect(screen.getByText("训练记录")).toBeInTheDocument());
    expect(screen.getAllByText("Daily life").length).toBeGreaterThan(0);
    fireEvent.click(screen.getByRole("button", { name: /能力趋势/ }));
    expect(onNavigate).toHaveBeenCalledWith("/ielts/assets/trends");
    fireEvent.click(screen.getByRole("button", { name: "切换学习资产模块" }));
    fireEvent.click(screen.getByRole("menuitem", { name: /面试学习资产/ }));
    expect(callbacks.onBackToInterview).toHaveBeenCalled();
    fireEvent.click(screen.getByRole("button", { name: /返回训练中心/ }));
    expect(callbacks.onTraining).toHaveBeenCalled();
  });

  it("shows asset loading errors", async () => {
    getIeltsSettings.mockRejectedValue(new Error("asset down"));
    getIeltsEvaluationHistory.mockResolvedValue([]);
    render(<IeltsAssets route={{ tab: "overview" }} onNavigate={vi.fn()} onBack={vi.fn()} />);
    await waitFor(() => expect(screen.getByRole("heading", { name: "学习资产加载失败" })).toBeInTheDocument());
    expect(screen.getByText("asset down")).toBeInTheDocument();
  });

  it("renders the empty trends state when there is no mock or training score", async () => {
    getIeltsSettings.mockResolvedValue({ targetScore: null, currentStreakDays: 0, todayCompletedCount: 0 });
    getIeltsEvaluationHistory.mockResolvedValue([]);
    render(<IeltsAssets route={{ tab: "trends" }} onNavigate={vi.fn()} onBack={vi.fn()} />);
    await waitFor(() => expect(screen.getByText("暂无模考趋势")).toBeInTheDocument());
    expect(screen.getByText("暂无能力评分")).toBeInTheDocument();
    expect(screen.getByText("完成至少两次完整模考后生成折线图。")).toBeInTheDocument();
  });

  it("recovers when an authenticated recording cannot be played", async () => {
    const recording = {
      sessionId: "recording-error",
      mode: "PART_PRACTICE",
      part: "PART_1",
      endedAt: "2026-08-25T10:05:00Z",
      startedAt: "2026-08-25T10:00:00Z",
      recordingUrls: ["unplayable-clip"],
      topicSelectionMethod: "SELECTED",
      strengths: [], improvements: [], recommendedExpressions: [],
    };
    const mediaUrl = { createObjectURL: vi.fn(() => "blob:unplayable"), revokeObjectURL: vi.fn() };
    vi.stubGlobal("URL", mediaUrl);
    window.URL = mediaUrl;
    vi.stubGlobal("Audio", vi.fn(() => ({
      play: vi.fn().mockRejectedValue(new Error("autoplay blocked")),
      pause: vi.fn(), onended: null, onerror: null,
    })));
    fetchAuthenticatedMedia.mockResolvedValue(new Blob(["audio"]));
    getIeltsEvaluationHistory.mockResolvedValue([recording]);
    render(<IeltsAssets route={{ tab: "history" }} onNavigate={vi.fn()} onBack={vi.fn()} />);
    await waitFor(() => expect(screen.getByText("训练记录")).toBeInTheDocument());
    fireEvent.click(screen.getByRole("button", { name: "播放原始录音" }));
    await waitFor(() => expect(fetchAuthenticatedMedia).toHaveBeenCalledWith("unplayable-clip"));
    await waitFor(() => expect(screen.getByRole("button", { name: "播放原始录音" })).toBeInTheDocument());
    expect(mediaUrl.revokeObjectURL).toHaveBeenCalledWith("blob:unplayable");
  });
});

describe("IeltsTrainingCenter", () => {
  it("loads the home, navigates to topics and supports the intake flow", async () => {
    const onNavigate = vi.fn();
    render(<IeltsTrainingCenter route={{ screen: "home" }} onNavigate={onNavigate} onExit={vi.fn()} onAssets={vi.fn()} />);
    await waitFor(() => expect(screen.getByRole("heading", { name: "雅思口语" })).toBeInTheDocument());
    fireEvent.click(screen.getByRole("button", { name: "开始模考" }));
    expect(onNavigate).toHaveBeenCalledWith("/ielts/mock/setup");
    fireEvent.click(screen.getByRole("button", { name: /Part 1/ }));
    expect(onNavigate).toHaveBeenCalledWith("/ielts/part1");
  });

  it("covers intake, topic loading/error, setup start and setup error", async () => {
    getIeltsSettings.mockResolvedValueOnce({});
    const onNavigate = vi.fn();
    render(<IeltsTrainingCenter route={{ screen: "home" }} onNavigate={onNavigate} onExit={vi.fn()} onAssets={vi.fn()} />);
    await waitFor(() => expect(screen.getByText("这次备考，你希望达到多少分？")).toBeInTheDocument());
    fireEvent.click(screen.getByRole("button", { name: /目标 6.5/ }));
    fireEvent.click(screen.getByRole("button", { name: "下一步" }));
    fireEvent.click(screen.getByRole("button", { name: /约 5.5/ }));
    fireEvent.click(screen.getByRole("button", { name: "进入训练中心" }));
    await waitFor(() => expect(updateIeltsSettings).toHaveBeenCalledWith({ targetScore: 6.5 }));

    cleanup();
    getIeltsTopics.mockResolvedValue({ categories: [{ code: "WORK", label: "工作" }], topics: [{ id: "t1", categoryLabel: "工作", title: "My work", questionCount: 3, practiceCount: 0 }], total: 1, totalPages: 1 });
    render(<IeltsTrainingCenter route={{ screen: "topics", part: "p1" }} onNavigate={onNavigate} onExit={vi.fn()} onAssets={vi.fn()} />);
    await waitFor(() => expect(screen.getByText("My work")).toBeInTheDocument(), { timeout: 1000 });
    fireEvent.click(screen.getByText("My work"));
    expect(onNavigate).toHaveBeenCalledWith("/ielts/part1/t1/setup");
    fireEvent.click(screen.getByRole("button", { name: "工作" }));
    fireEvent.change(screen.getByRole("textbox", { name: "搜索话题" }), { target: { value: "x" } });

    cleanup();
    getIeltsTopics.mockRejectedValue(new Error("topics down"));
    render(<IeltsTrainingCenter route={{ screen: "topics", part: "p1" }} onNavigate={onNavigate} onExit={vi.fn()} onAssets={vi.fn()} />);
    await waitFor(() => expect(screen.getByText("topics down")).toBeInTheDocument());
  });

  it("loads a selected topic setup, starts the session, and handles setup failures", async () => {
    const onNavigate = vi.fn();
    getIeltsTopics.mockResolvedValue({ categories: [], topics: [{ id: "t1", title: "Travel", categoryLabel: "生活", questionCount: 2 }], total: 1, totalPages: 1 });
    render(<IeltsTrainingCenter route={{ screen: "topics", part: "p1" }} onNavigate={onNavigate} onExit={vi.fn()} onAssets={vi.fn()} />);
    await waitFor(() => expect(screen.getByText("Travel")).toBeInTheDocument());
    fireEvent.click(screen.getByText("Travel"));
    expect(onNavigate).toHaveBeenCalledWith("/ielts/part1/t1/setup");

    cleanup();
    getIeltsTraining.mockResolvedValue({ topicId: "t1", questions: [{ question: "Describe a trip" }] });
    render(<IeltsTrainingCenter route={{ screen: "setup", part: "p1", selection: "t1" }} onNavigate={onNavigate} onExit={vi.fn()} onAssets={vi.fn()} />);
    await waitFor(() => expect(screen.getByRole("heading", { name: /准备 Part 1/ })).toBeInTheDocument());
    fireEvent.click(screen.getByRole("button", { name: "确认并开始" }));
    await waitFor(() => expect(generateIeltsScene).toHaveBeenCalledWith({ mode: "PART_PRACTICE", part: "PART_1", topicId: "t1" }));
    expect(createIeltsSceneFlow).toHaveBeenCalledWith("ielts-scene");
    expect(onNavigate).toHaveBeenCalledWith("/ielts/part1/t1/session");

    cleanup();
    updateIeltsSettings.mockRejectedValueOnce(new Error("settings failed"));
    render(<IeltsTrainingCenter route={{ screen: "setup", part: "p1", selection: "t1" }} onNavigate={onNavigate} onExit={vi.fn()} onAssets={vi.fn()} />);
    await waitFor(() => expect(screen.getByRole("heading", { name: /准备 Part 1/ })).toBeInTheDocument());
    fireEvent.click(screen.getByRole("button", { name: "确认并开始" }));
    await waitFor(() => expect(screen.getByText("settings failed")).toBeInTheDocument());
  });

  it("shows training loading errors and retries the topic request", async () => {
    getIeltsTraining.mockRejectedValueOnce(new Error("training unavailable"));
    render(<IeltsTrainingCenter route={{ screen: "setup", part: "p2", selection: "t2" }} onNavigate={vi.fn()} onExit={vi.fn()} onAssets={vi.fn()} />);
    await waitFor(() => expect(screen.getByRole("heading", { name: "题目加载失败" })).toBeInTheDocument());
    expect(screen.getByText("training unavailable")).toBeInTheDocument();
    getIeltsTraining.mockResolvedValueOnce({ topicId: "t2", questions: [] });
    fireEvent.click(screen.getByRole("button", { name: "重新加载" }));
    await waitFor(() => expect(screen.getByRole("heading", { name: /准备 Part 2/ })).toBeInTheDocument());
  });

  it("covers IELTS session events, part two preparation, finish, exit, and report states", async () => {
    const client = mockIeltsRealtimeClient();
    const onNavigate = vi.fn();
    generateIeltsScene.mockResolvedValue({ ieltsId: "scene-p2", voiceId: "Harvey" });
    getIeltsTraining.mockResolvedValue({ topicId: "t2", questions: [{ questionText: "Describe a book", cuePoints: ["what", "why"] }] });
    const view = render(<IeltsTrainingCenter route={{ screen: "setup", part: "p2", selection: "t2" }} onNavigate={onNavigate} onExit={vi.fn()} onAssets={vi.fn()} />);
    await waitFor(() => expect(screen.getByRole("heading", { name: /准备 Part 2/ })).toBeInTheDocument());
    fireEvent.click(screen.getByRole("button", { name: "确认并开始" }));
    await waitFor(() => expect(createIeltsSceneFlow).toHaveBeenCalledWith("scene-p2"));
    view.rerender(<IeltsTrainingCenter route={{ screen: "session", part: "p2", selection: "t2" }} onNavigate={onNavigate} onExit={vi.fn()} onAssets={vi.fn()} />);
    await waitFor(() => expect(screen.getByLabelText("Part 2 答题笔记")).toBeInTheDocument());
    await waitFor(() => expect(client.options).toBeDefined());
    client.options.onEvent({ type: "local.connecting" });
    client.options.onEvent({ type: "local.connected" });
    client.options.onEvent({ type: "response.created" });
    client.options.onEvent({ type: "response.audio_transcript.delta", item_id: "a1", delta: "Hello" });
    client.options.onEvent({ type: "local.transcript.final", owner: 0, itemId: "a1", text: "Hello there" });
    client.options.onEvent({ type: "response.done" });
    await waitFor(() => expect(screen.getByText(/准备时间/)).toBeInTheDocument());
    fireEvent.change(screen.getByLabelText("Part 2 答题笔记"), { target: { value: "key points" } });
    fireEvent.click(screen.getByRole("button", { name: "结束准备并开始作答" }));
    await waitFor(() => expect(client.transitionIeltsPart2).toHaveBeenCalledWith("PREPARATION_COMPLETE"));
    client.options.onEvent({ type: "local.ielts_input_ready" });
    await waitFor(() => expect(screen.getByText(/作答时间/)).toBeInTheDocument());
    vi.useFakeTimers();
    client.options.onEvent({ type: "input_audio_buffer.speech_stopped" });
    await act(async () => { vi.advanceTimersByTime(3000); });
    expect(client.transitionIeltsPart2).toHaveBeenCalledWith("ANSWER_COMPLETE");
    vi.useRealTimers();
    client.options.onEvent({ type: "local.ielts_part2_state", state: { completed: true } });
    fireEvent.click(screen.getByRole("button", { name: "退出训练" }));
    fireEvent.click(screen.getByRole("button", { name: "继续训练" }));
    fireEvent.click(screen.getByRole("button", { name: "退出训练" }));
    fireEvent.click(screen.getByRole("button", { name: "确认退出" }));
    await waitFor(() => expect(onNavigate).toHaveBeenCalledWith("/ielts"));

    cleanup();
    const reportNavigate = vi.fn();
    render(<IeltsTrainingCenter route={{ screen: "analysis", part: "mock", selection: "random" }} onNavigate={reportNavigate} onExit={vi.fn()} onAssets={vi.fn()} />);
    await waitFor(() => expect(screen.getByText("有效回答不足，暂时无法评分")).toBeInTheDocument());
    cleanup();
    render(<IeltsTrainingCenter route={{ screen: "report", part: "mock", selection: "random" }} onNavigate={reportNavigate} onExit={vi.fn()} onAssets={vi.fn()} />);
    expect(screen.getByText("本次有效英文回答不足")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "重新练习" }));
    expect(reportNavigate).toHaveBeenCalledWith("/ielts/mock/setup");
  });

  it("covers a Part 1 session, live subtitles, state events, and a complete analysis/report", async () => {
    const client = mockIeltsRealtimeClient();
    const onNavigate = vi.fn();
    const evaluation = {
      assessmentType: "FINAL",
      overallBandScore: 7.1,
      fluencyCoherenceScore: 7,
      lexicalResourceScore: 7.5,
      grammaticalRangeAccuracyScore: 6.5,
      pronunciationScore: 7,
      summary: "回答自然，观点展开清楚。",
      strengths: ["回答完整", "表达自然"],
      improvements: ["增加例子", "减少重复"],
      recommendedExpressions: ["from my perspective", "for instance"],
      partEvaluations: [
        { part: "PART_1", fluencyCoherenceScore: 7, lexicalResourceScore: 7, grammaticalRangeAccuracyScore: 6.5, pronunciationScore: 7 },
        { part: "PART_2", fluencyCoherenceScore: 7.5, lexicalResourceScore: 7, grammaticalRangeAccuracyScore: 7, pronunciationScore: 7 },
        { part: "PART_3", fluencyCoherenceScore: 6.5, lexicalResourceScore: 7.5, grammaticalRangeAccuracyScore: 6, pronunciationScore: 7 },
      ],
    };
    generateIeltsEvaluation.mockResolvedValueOnce(evaluation);
    getIeltsTraining.mockResolvedValue({ topicId: "t1", questions: [{ questionText: "Where do you live?" }] });
    const view = render(<IeltsTrainingCenter route={{ screen: "setup", part: "p1", selection: "t1" }} onNavigate={onNavigate} onExit={vi.fn()} onAssets={vi.fn()} />);
    await waitFor(() => expect(screen.getByRole("heading", { name: /准备 Part 1/ })).toBeInTheDocument());
    fireEvent.click(screen.getByRole("button", { name: "确认并开始" }));
    await waitFor(() => expect(generateIeltsScene).toHaveBeenCalled());

    view.rerender(<IeltsTrainingCenter route={{ screen: "session", part: "p1", selection: "t1" }} onNavigate={onNavigate} onExit={vi.fn()} onAssets={vi.fn()} />);
    await waitFor(() => expect(client.options).toBeDefined());
    fireEvent.click(screen.getByRole("button", { name: "开启字幕" }));
    await waitFor(() => expect(screen.getByLabelText("实时会话字幕")).toBeInTheDocument());
    client.options.onEvent({ type: "local.connecting" });
    client.options.onEvent({ type: "local.connected" });
    client.options.onEvent({ type: "response.created" });
    client.options.onEvent({ type: "response.audio_transcript.delta", response_id: "response-1", delta: "How are" });
    await act(async () => { await new Promise((resolve) => requestAnimationFrame(resolve)); });
    expect(screen.getByText("How are")).toBeInTheDocument();
    client.options.onEvent({ type: "response.audio_transcript.delta", response_id: "response-1", delta: " you?" });
    client.options.onEvent({ type: "local.transcript.final", owner: 0, itemId: "response-1", text: "How are you?" });
    client.options.onEvent({ type: "local.ielts_input_ready" });
    client.options.onEvent({ type: "local.ielts_state", state: { answeredQuestions: 1, totalQuestions: 4 } });
    await waitFor(() => expect(screen.getByText("已完成 1 / 4 题")).toBeInTheDocument());

    fireEvent.click(screen.getByRole("button", { name: "关闭字幕" }));
    expect(screen.getByRole("button", { name: "开启字幕" })).toBeInTheDocument();
    client.options.onEvent({ type: "local.ielts_state_error", message: "题目推进失败" });
    client.options.onEvent({ type: "local.backend_warning" });
    client.options.onEvent({ type: "local.mic_error", message: "麦克风被占用" });
    client.options.onEvent({ type: "local.error", error: { message: "socket closed" } });
    await waitFor(() => expect(screen.getByText("socket closed")).toBeInTheDocument());

    fireEvent.click(screen.getByRole("button", { name: "开启字幕" }));
    fireEvent.click(screen.getByRole("button", { name: "结束本次训练" }));
    await waitFor(() => expect(generateIeltsEvaluation).toHaveBeenCalledWith("ielts-scene", "ielts-session"));
    await waitFor(() => expect(onNavigate).toHaveBeenCalledWith("/ielts/part1/t1/analysis"));

    view.rerender(<IeltsTrainingCenter route={{ screen: "analysis", part: "p1", selection: "t1" }} onNavigate={onNavigate} onExit={vi.fn()} onAssets={vi.fn()} />);
    expect(screen.getByText("本次评分已完成")).toBeInTheDocument();
    expect(screen.getByText("完整模考预估")).toBeInTheDocument();
    expect(screen.getByText("7.1")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "查看详细报告" }));
    expect(onNavigate).toHaveBeenCalledWith("/ielts/part1/t1/report");

    view.rerender(<IeltsTrainingCenter route={{ screen: "report", part: "p1", selection: "t1" }} onNavigate={onNavigate} onExit={vi.fn()} onAssets={vi.fn()} />);
    expect(screen.getByText("from my perspective")).toBeInTheDocument();
    expect(screen.getByText("7.5")).toBeInTheDocument();
    view.rerender(<IeltsTrainingCenter route={{ screen: "report", part: "mock", selection: "random" }} onNavigate={onNavigate} onExit={vi.fn()} onAssets={vi.fn()} />);
    expect(screen.getByText("回答自然，观点展开清楚。")).toBeInTheDocument();
    expect(screen.getByText("回答完整")).toBeInTheDocument();
    expect(screen.getByText("增加例子")).toBeInTheDocument();
  });

  it("covers Part 3 turn timeout, transcription reset, completion, and realtime errors", async () => {
    const client = mockIeltsRealtimeClient();
    getIeltsTraining.mockResolvedValue({ topicId: "t3", questions: [{ questionText: "Should cities have more parks?" }] });
    const view = render(<IeltsTrainingCenter route={{ screen: "setup", part: "p3", selection: "t3" }} onNavigate={vi.fn()} onExit={vi.fn()} onAssets={vi.fn()} />);
    await waitFor(() => expect(screen.getByRole("heading", { name: /准备 Part 3/ })).toBeInTheDocument());
    fireEvent.click(screen.getByRole("button", { name: "确认并开始" }));
    await waitFor(() => expect(generateIeltsScene).toHaveBeenCalled());
    view.rerender(<IeltsTrainingCenter route={{ screen: "session", part: "p3", selection: "t3" }} onNavigate={vi.fn()} onExit={vi.fn()} onAssets={vi.fn()} />);
    await waitFor(() => expect(client.options).toBeDefined());

    vi.useFakeTimers();
    await act(async () => {
      client.options.onEvent({ type: "local.connected" });
      client.options.onEvent({ type: "local.ielts_input_ready" });
    });
    expect(screen.getByText("01:00")).toBeInTheDocument();
    await act(async () => {
      client.options.onEvent({ type: "conversation.item.input_audio_transcription.completed" });
    });
    await act(async () => { vi.advanceTimersByTime(60_000); });
    expect(client.forceIeltsPart3TurnTimeout).not.toHaveBeenCalled();

    await act(async () => { client.options.onEvent({ type: "local.ielts_input_ready" }); });
    await act(async () => { vi.advanceTimersByTime(60_000); });
    expect(client.forceIeltsPart3TurnTimeout).toHaveBeenCalledTimes(1);
    expect(screen.getAllByText("单题回答已到 60 秒，考官正在切换下一题").length).toBeGreaterThan(0);

    await act(async () => { client.options.onEvent({ type: "local.ielts_state", state: { completed: true } }); });
    expect(client.setMuted).toHaveBeenCalledWith(true);
    await act(async () => { client.options.onEvent({ type: "local.ielts_state_error", message: "状态更新失败" }); });
    await act(async () => { client.options.onEvent({ type: "local.backend_warning" }); });
    await act(async () => { client.options.onEvent({ type: "local.mic_error" }); });
    expect(screen.getByText("无法访问麦克风")).toBeInTheDocument();
    await act(async () => { client.options.onEvent({ type: "error", message: "provider error" }); });
    expect(screen.getByText("provider error")).toBeInTheDocument();
  });

  it("handles Part 2 transition retries and silence-finish failures", async () => {
    const client = mockIeltsRealtimeClient();
    const onNavigate = vi.fn();
    getIeltsTraining.mockResolvedValue({ topicId: "p2-errors", questions: [{ question: "Describe a useful object" }] });
    client.transitionIeltsPart2.mockRejectedValueOnce(new Error("preparation transition failed"));
    const view = render(<IeltsTrainingCenter route={{ screen: "setup", part: "p2", selection: "p2-errors" }} onNavigate={onNavigate} onExit={vi.fn()} onAssets={vi.fn()} />);
    await waitFor(() => expect(screen.getByRole("heading", { name: /准备 Part 2/ })).toBeInTheDocument());
    fireEvent.click(screen.getByRole("button", { name: "确认并开始" }));
    await waitFor(() => expect(generateIeltsScene).toHaveBeenCalled());
    view.rerender(<IeltsTrainingCenter route={{ screen: "session", part: "p2", selection: "p2-errors" }} onNavigate={onNavigate} onExit={vi.fn()} onAssets={vi.fn()} />);
    await waitFor(() => expect(client.options).toBeDefined());
    await act(async () => {
      client.options.onEvent({ type: "local.connected" });
      client.options.onEvent({ type: "response.done" });
    });
    await waitFor(() => expect(screen.getByRole("button", { name: "结束准备并开始作答" })).toBeInTheDocument());
    fireEvent.click(screen.getByRole("button", { name: "结束准备并开始作答" }));
    await waitFor(() => expect(screen.getByText("preparation transition failed")).toBeInTheDocument());

    client.transitionIeltsPart2.mockResolvedValueOnce({});
    fireEvent.click(screen.getByRole("button", { name: "结束准备并开始作答" }));
    await waitFor(() => expect(client.transitionIeltsPart2).toHaveBeenCalledWith("PREPARATION_COMPLETE"));
    client.options.onEvent({ type: "local.ielts_input_ready" });
    await waitFor(() => expect(screen.getByText(/作答时间/)).toBeInTheDocument());

    client.transitionIeltsPart2.mockRejectedValueOnce(new Error("silence finish failed"));
    vi.useFakeTimers();
    await act(async () => {
      client.options.onEvent({ type: "input_audio_buffer.speech_stopped" });
      vi.advanceTimersByTime(3_000);
    });
    vi.useRealTimers();
    await waitFor(() => expect(screen.getByText("silence finish failed")).toBeInTheDocument());
  });

  it("shows realtime start and stop errors and tolerates an unavailable evaluation", async () => {
    const client = mockIeltsRealtimeClient({ start: vi.fn().mockRejectedValue(new Error("realtime start failed")) });
    const onNavigate = vi.fn();
    getIeltsTraining.mockResolvedValue({ topicId: "p1-failures", questions: [{ questionText: "Tell me about your home" }] });
    const view = render(<IeltsTrainingCenter route={{ screen: "setup", part: "p1", selection: "p1-failures" }} onNavigate={onNavigate} onExit={vi.fn()} onAssets={vi.fn()} />);
    await waitFor(() => expect(screen.getByRole("heading", { name: /准备 Part 1/ })).toBeInTheDocument());
    fireEvent.click(screen.getByRole("button", { name: "确认并开始" }));
    await waitFor(() => expect(generateIeltsScene).toHaveBeenCalled());
    view.rerender(<IeltsTrainingCenter route={{ screen: "session", part: "p1", selection: "p1-failures" }} onNavigate={onNavigate} onExit={vi.fn()} onAssets={vi.fn()} />);
    await waitFor(() => expect(screen.getByText("realtime start failed")).toBeInTheDocument());

    cleanup();
    const stopClient = mockIeltsRealtimeClient({ stop: vi.fn().mockRejectedValue(new Error("stop failed")) });
    generateIeltsEvaluation.mockRejectedValueOnce(new Error("evaluation unavailable"));
    const stopView = render(<IeltsTrainingCenter route={{ screen: "setup", part: "p1", selection: "p1-failures" }} onNavigate={onNavigate} onExit={vi.fn()} onAssets={vi.fn()} />);
    await waitFor(() => expect(screen.getByRole("heading", { name: /准备 Part 1/ })).toBeInTheDocument());
    fireEvent.click(screen.getByRole("button", { name: "确认并开始" }));
    await waitFor(() => expect(generateIeltsScene).toHaveBeenCalled());
    stopView.rerender(<IeltsTrainingCenter route={{ screen: "session", part: "p1", selection: "p1-failures" }} onNavigate={onNavigate} onExit={vi.fn()} onAssets={vi.fn()} />);
    await waitFor(() => expect(stopClient.options).toBeDefined());
    stopClient.options.onEvent({ type: "local.connected" });
    fireEvent.click(screen.getByRole("button", { name: "结束本次训练" }));
    await waitFor(() => expect(screen.getByText("结束练习失败，请稍后重试")).toBeInTheDocument());
  });

  it("renders rich trends and exercises history recording playback and fallbacks", async () => {
    const trendReports = [
      { sessionId: "mock-new", mode: "MOCK_TEST", part: "PART_1", endedAt: "2026-08-25T10:12:00Z", startedAt: "2026-08-25T10:00:00Z", overallBandScore: 7, fluencyCoherenceScore: 7, lexicalResourceScore: 7, grammaticalRangeAccuracyScore: 7, pronunciationScore: 7, partEvaluations: [{ part: "PART_1" }, { part: "PART_3" }] },
      { sessionId: "mock-old", mode: "MOCK_TEST", part: "PART_2", endedAt: "2026-08-24T10:12:00Z", startedAt: "2026-08-24T10:00:00Z", overallBandScore: 6, fluencyCoherenceScore: 5, lexicalResourceScore: 6, grammaticalRangeAccuracyScore: 7, pronunciationScore: 8 },
      { sessionId: "part-one", mode: "PART_PRACTICE", part: "PART_1", topicTitles: { PART_1: "Home" }, endedAt: "2026-08-23T10:05:00Z", startedAt: "2026-08-23T10:00:00Z", recordingUrls: ["clip-1", "clip-2"], topicSelectionMethod: "SELECTED", summary: "", strengths: [], improvements: [], recommendedExpressions: [] },
      { sessionId: "empty-report", mode: "PART_PRACTICE", part: "PART_3", endedAt: "2026-08-22T10:05:00Z", startedAt: "2026-08-22T10:00:00Z", recordingUrls: [] },
    ];
    getIeltsEvaluationHistory.mockResolvedValue(trendReports);
    const onNavigate = vi.fn();
    const context = mockCanvas();
    render(<IeltsAssets route={{ tab: "trends" }} onNavigate={onNavigate} onBack={vi.fn()} onBackToAssets={vi.fn()} onBackToInterview={vi.fn()} onTraining={vi.fn()} />);
    await waitFor(() => expect(screen.getByText("最近 2 次变化 +1.0 分")).toBeInTheDocument());
    expect(screen.getByText("回答长度更稳定")).toBeInTheDocument();
    expect(screen.getByText("暂无专项评分")).toBeInTheDocument();
    expect(context.createLinearGradient).toHaveBeenCalled();

    const audios = [];
    const mediaUrl = { createObjectURL: vi.fn(() => "blob:clip"), revokeObjectURL: vi.fn() };
    vi.stubGlobal("URL", mediaUrl);
    window.URL = mediaUrl;
    vi.stubGlobal("Audio", vi.fn((url) => {
      const audio = { url, play: vi.fn().mockResolvedValue(undefined), pause: vi.fn(), onended: null, onerror: null };
      audios.push(audio);
      return audio;
    }));
    fetchAuthenticatedMedia.mockResolvedValue(new Blob(["audio"]));
    cleanup();
    render(<IeltsAssets route={{ tab: "history" }} onNavigate={onNavigate} onBack={vi.fn()} onBackToAssets={vi.fn()} onBackToInterview={vi.fn()} onTraining={vi.fn()} />);
    await waitFor(() => expect(screen.getByText("Home")).toBeInTheDocument());
    fireEvent.click(screen.getByRole("button", { name: /Part 1 · Home/ }));
    await waitFor(() => expect(screen.getByRole("button", { name: "播放原始录音" })).toBeInTheDocument());
    const playButton = screen.getByRole("button", { name: "播放原始录音" });
    fireEvent.click(playButton);
    await waitFor(() => expect(fetchAuthenticatedMedia).toHaveBeenCalledWith("clip-1"));
    await waitFor(() => expect(screen.getByRole("button", { name: "暂停录音" })).toBeInTheDocument());
    fireEvent.click(screen.getByRole("button", { name: "暂停录音" }));
    expect(audios[0].pause).toHaveBeenCalled();
    fireEvent.click(screen.getByRole("button", { name: "播放原始录音" }));
    await waitFor(() => expect(audios.length).toBeGreaterThan(1));
    audios[1].onended();
    await waitFor(() => expect(audios.length).toBeGreaterThan(2));
    audios[2].onended();
    await waitFor(() => expect(screen.getByRole("button", { name: "播放原始录音" })).toBeInTheDocument());
    const emptyReportButton = screen.getAllByRole("button", { name: /Part 3 · Discussion Topic/ }).at(-1);
    fireEvent.click(emptyReportButton);
    expect(screen.getByText("本次训练暂未生成文字总结。")).toBeInTheDocument();
    expect(screen.getByText("暂无录音")).toBeInTheDocument();
  });
});
