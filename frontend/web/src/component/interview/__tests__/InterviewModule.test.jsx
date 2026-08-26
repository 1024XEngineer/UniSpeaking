import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("../../../infrastructure/http/apiClient.js", () => ({
  generateInterviewScene: vi.fn(),
  getInterviewAssets: vi.fn(),
  getInterviewOcrAvailability: vi.fn(),
  getInterviewReport: vi.fn(),
  prepareInterviewMaterials: vi.fn(),
  retryInterviewReport: vi.fn(),
}));
vi.mock("../../../websocket/realtimeClient.js", () => ({ createRealtimeClient: vi.fn() }));
vi.mock("../../../analytics/analyticsClient.js", () => ({
  analytics: { training: vi.fn(() => ({ attempt: vi.fn(), started: vi.fn(), complete: vi.fn(), abandon: vi.fn(), fail: vi.fn(), setVisible: vi.fn(), pause: vi.fn(), resume: vi.fn() })) },
}));

import {
  generateInterviewScene,
  getInterviewAssets,
  getInterviewOcrAvailability,
  getInterviewReport,
  prepareInterviewMaterials,
  retryInterviewReport,
} from "../../../infrastructure/http/apiClient.js";
import { createRealtimeClient } from "../../../websocket/realtimeClient.js";
import { InterviewAssets, InterviewModule } from "../InterviewModule.jsx";

afterEach(() => cleanup());
beforeEach(() => {
  vi.clearAllMocks();
  createRealtimeClient.mockReset();
  getInterviewOcrAvailability.mockResolvedValue({ available: false });
  getInterviewAssets.mockResolvedValue([]);
  getInterviewReport.mockResolvedValue({ status: "PROCESSING", report: null });
  prepareInterviewMaterials.mockResolvedValue({ material: { jobTitle: "工程师", responsibilities: ["开发"], qualificationRequirements: ["沟通"], finalText: "工程师 · 开发 · 沟通" } });
  generateInterviewScene.mockResolvedValue({ sceneId: "scene-1" });
  retryInterviewReport.mockResolvedValue({ status: "PROCESSING", report: null });
});

function mockInterviewRealtimeClient(overrides = {}) {
  const client = {
    start: vi.fn().mockResolvedValue({}),
    stop: vi.fn().mockResolvedValue({ reportStatus: "PROCESSING" }),
    pause: vi.fn().mockResolvedValue(),
    resume: vi.fn().mockResolvedValue(),
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
}

const completedReport = {
  overallScore: 78,
  summary: "回答结构清晰",
  dimensions: [
    { dimension: "STRUCTURE", score: 82, evaluation: "结构完整", advice: "补充结果" },
    { dimension: "RELEVANCE", score: 55, evaluation: "相关性一般" },
    { dimension: "COMMUNICATION", score: 78, evaluation: "表达自然" },
    { dimension: "DEPTH", score: 70, evaluation: "可以深入" },
    { dimension: "VOCABULARY_EXPRESSION", score: 80, evaluation: "词汇丰富" },
  ],
};

const assetItems = [
  { sceneId: "scene-complete", jobTitle: "前端工程师", difficulty: "STANDARD", practiceCount: 3, latestSessionId: "session-1", latestReportStatus: "COMPLETED", latestOverallScore: 78, latestPracticedAt: "2026-08-25T10:00:00Z" },
  { sceneId: "scene-processing", jobTitle: "产品经理", difficulty: "HARD", practiceCount: 1, latestSessionId: "session-2", latestReportStatus: "PROCESSING", latestPracticedAt: "2026-08-24T10:00:00Z" },
  { sceneId: "scene-new", jobTitle: "设计师", difficulty: "EASY", practiceCount: 0, latestReportStatus: "", latestPracticedAt: null },
];

describe("InterviewModule home and reports", () => {
  it("renders the home, validates JD, prepares materials and generates a session", async () => {
    const onNavigate = vi.fn();
    render(<InterviewModule route={{ screen: "home" }} onNavigate={onNavigate} onBack={vi.fn()} />);
    await waitFor(() => expect(screen.getByRole("heading", { name: "模拟面试" })).toBeInTheDocument());
    fireEvent.click(screen.getByRole("button", { name: "整理材料" }));
    expect(screen.getByRole("alert")).toHaveTextContent("请输入 JD 文本");
    fireEvent.change(screen.getByPlaceholderText(/招聘 JD/), { target: { value: "负责前端开发" } });
    fireEvent.click(screen.getByRole("button", { name: /困难 每主题/ }));
    fireEvent.click(screen.getByRole("button", { name: "整理材料" }));
    await waitFor(() => expect(screen.getByRole("dialog")).toBeInTheDocument());
    expect(prepareInterviewMaterials).toHaveBeenCalledTimes(1);
    fireEvent.click(screen.getByRole("button", { name: "确认并生成面试" }));
    await waitFor(() => expect(generateInterviewScene).toHaveBeenCalledWith(expect.objectContaining({ difficulty: "HARD" })));
    expect(onNavigate).toHaveBeenCalledWith("/interview/scenes/scene-1/session");
  });

  it("covers OCR unavailable and material preparation errors", async () => {
    getInterviewOcrAvailability.mockRejectedValue(new Error("ocr unavailable"));
    prepareInterviewMaterials.mockRejectedValue(new Error("prepare failed"));
    render(<InterviewModule route={{ screen: "home" }} onNavigate={vi.fn()} onBack={vi.fn()} />);
    await waitFor(() => expect(screen.getByRole("button", { name: "上传图片" })).toBeDisabled());
    fireEvent.change(screen.getByPlaceholderText(/招聘 JD/), { target: { value: "岗位职责" } });
    fireEvent.click(screen.getByRole("button", { name: "整理材料" }));
    await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent("prepare failed"));
  });

  it("covers file validation, OCR image input, draft editing, and generation errors", async () => {
    getInterviewOcrAvailability.mockResolvedValue({ available: true });
    prepareInterviewMaterials.mockResolvedValue({
      material: {
        jobTitle: "工程师", responsibilities: "开发；测试", qualificationRequirements: "沟通;协作",
        requiredSkills: ["React"], otherJobInformation: "上海", education: ["本科"],
        finalText: "工程师 · 开发 · 沟通",
      },
    });
    const onNavigate = vi.fn();
    render(<InterviewModule route={{ screen: "home" }} onNavigate={onNavigate} onBack={vi.fn()} />);
    await waitFor(() => expect(screen.getByRole("button", { name: "上传图片" })).not.toBeDisabled());
    fireEvent.click(screen.getByRole("button", { name: "上传文件" }));
    const resumeInput = document.querySelector('input[type="file"]');
    fireEvent.change(resumeInput, { target: { files: [new File(["legacy"], "resume.doc", { type: "application/msword" })] } });
    expect(screen.getByText(/\.doc 简历暂不支持/)).toBeInTheDocument();
    fireEvent.change(resumeInput, { target: { files: [new File(["pdf"], "resume.pdf", { type: "application/pdf" })] } });
    expect(screen.getByText("resume.pdf")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "上传图片" }));
    const imageInput = document.querySelector('input[accept="image/*"]');
    fireEvent.click(screen.getByRole("button", { name: "整理材料" }));
    expect(screen.getByRole("alert")).toHaveTextContent("请选择一张包含 JD 的图片");
    fireEvent.change(imageInput, { target: { files: [new File(["image"], "jd.png", { type: "image/png" })] } });
    fireEvent.click(screen.getByRole("button", { name: "整理材料" }));
    await waitFor(() => expect(screen.getByRole("dialog")).toBeInTheDocument());
    const responsibilities = screen.getAllByRole("textbox").find((field) => field.value === "开发\n测试");
    fireEvent.change(responsibilities, { target: { value: "开发\n测试\n交付" } });
    fireEvent.click(screen.getByRole("button", { name: "重新整理" }));
    await waitFor(() => expect(prepareInterviewMaterials).toHaveBeenCalledTimes(2));
    generateInterviewScene.mockRejectedValueOnce(new Error("generation failed"));
    fireEvent.click(screen.getByRole("button", { name: "确认并生成面试" }));
    await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent("generation failed"));
    generateInterviewScene.mockResolvedValueOnce({});
    fireEvent.click(screen.getByRole("button", { name: "确认并生成面试" }));
    await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent("缺少 sceneId"));
  });

  it("renders processing, failed/retry, and completed interview reports", async () => {
    const onNavigate = vi.fn();
    render(<InterviewModule route={{ screen: "report", sceneId: "scene", sessionId: "session" }} onNavigate={onNavigate} />);
    await waitFor(() => expect(screen.getByRole("heading", { name: "正在生成面试报告" })).toBeInTheDocument());
    fireEvent.click(screen.getByRole("button", { name: "返回" }));
    expect(onNavigate).toHaveBeenCalledWith("/interview");

    cleanup();
    getInterviewReport.mockResolvedValue({ status: "FAILED", failureReason: "服务异常", report: null });
    retryInterviewReport.mockResolvedValue({ status: "COMPLETED", report: completedReport });
    render(<InterviewModule route={{ screen: "report", sceneId: "scene", sessionId: "session" }} onNavigate={onNavigate} />);
    await waitFor(() => expect(screen.getByRole("heading", { name: "报告生成失败" })).toBeInTheDocument());
    fireEvent.click(screen.getByRole("button", { name: "重新生成" }));
    await waitFor(() => expect(screen.getByRole("heading", { name: "面试表现报告" })).toBeInTheDocument());
    expect(screen.getAllByText("78").length).toBeGreaterThan(0);
    fireEvent.click(screen.getByRole("button", { name: "返回训练中心" }));
    expect(onNavigate).toHaveBeenCalledWith("/interview");
  });

  it("retries report failures and keeps polling after transient report errors", async () => {
    getInterviewReport.mockRejectedValueOnce(new Error("report temporarily unavailable"));
    vi.useFakeTimers();
    render(<InterviewModule route={{ screen: "report", sceneId: "scene", sessionId: "session" }} onNavigate={vi.fn()} />);
    await vi.waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent("report temporarily unavailable"));
    retryInterviewReport.mockRejectedValueOnce(new Error("retry failed"));
    getInterviewReport.mockResolvedValueOnce({ status: "FAILED", report: null });
    await vi.advanceTimersByTimeAsync(2000);
    await vi.waitFor(() => expect(screen.getByRole("heading", { name: "报告生成失败" })).toBeInTheDocument());
    fireEvent.click(screen.getByRole("button", { name: "重新生成" }));
    await vi.waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent("retry failed"));
    vi.useRealTimers();
  });

  it("covers interview realtime lifecycle, transcript, pause, finish, errors, and exit", async () => {
    const client = mockInterviewRealtimeClient();
    const onNavigate = vi.fn();
    render(<InterviewModule route={{ screen: "session", sceneId: "scene-1" }} teacher={{ name: "Alex", voiceId: "Aiden", image: "/alex.png" }} speed="自然" onNavigate={onNavigate} onBack={vi.fn()} />);
    await waitFor(() => expect(client.options).toBeDefined());
    client.options.onEvent({ type: "local.connecting" });
    client.options.onEvent({ type: "local.connected", sessionId: "session-1" });
    client.options.onEvent({ type: "session.updated" });
    client.options.onEvent({ type: "input_audio_buffer.speech_started" });
    client.options.onEvent({ type: "response.audio.delta" });
    client.options.onEvent({ type: "conversation.item.input_audio_transcription.delta", item_id: "u1", delta: "hello" });
    client.options.onEvent({ type: "conversation.item.input_audio_transcription.text", item_id: "u1", text: "hello world" });
    client.options.onEvent({ type: "response.audio_transcript.delta", item_id: "a1", delta: "Tell me more" });
    client.options.onEvent({ type: "local.transcript.final", owner: 0, itemId: "a1", text: "Tell me more" });
    await waitFor(() => expect(screen.getByText("Tell me more")).toBeInTheDocument());
    fireEvent.click(screen.getByRole("button", { name: "暂停会话" }));
    await waitFor(() => expect(client.pause).toHaveBeenCalled());
    fireEvent.click(screen.getByRole("button", { name: "恢复会话" }));
    await waitFor(() => expect(client.resume).toHaveBeenCalled());
    client.options.onEvent({ type: "local.interview_state", state: { currentTopic: "行为问题" } });
    client.options.onEvent({ type: "local.interview_closing" });
    client.options.onEvent({ type: "local.interview_state", state: { currentTopic: "ignored" } });
    client.options.onEvent({ type: "local.interview_end_error", message: "end warning" });
    client.options.onEvent({ type: "local.backend_warning", message: "backend warning" });
    client.options.onEvent({ type: "local.mic_error", message: "mic failed" });
    client.options.onEvent({ type: "error", error: { message: "socket failed" } });
    await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent("socket failed"));

    cleanup();
    const exitClient = mockInterviewRealtimeClient({ stop: vi.fn().mockResolvedValue({}) });
    render(<InterviewModule route={{ screen: "session", sceneId: "scene-2" }} teacher={{ name: "Alex" }} speed="慢一些" onNavigate={onNavigate} onBack={vi.fn()} />);
    await waitFor(() => expect(exitClient.options).toBeDefined());
    fireEvent.click(screen.getByRole("button", { name: "退出面试" }));
    fireEvent.click(screen.getByRole("button", { name: "确认退出" }));
    await waitFor(() => expect(onNavigate).toHaveBeenCalledWith("/interview"));

    cleanup();
    const stopClient = mockInterviewRealtimeClient({ stop: vi.fn().mockResolvedValue({ reportStatus: "COMPLETED" }) });
    render(<InterviewModule route={{ screen: "session", sceneId: "scene-3" }} teacher={{ name: "Alex" }} speed="快一些" onNavigate={onNavigate} onBack={vi.fn()} />);
    await waitFor(() => expect(stopClient.options).toBeDefined());
    stopClient.options.onEvent({ type: "local.connected", sessionId: "session-3" });
    fireEvent.click(screen.getByRole("button", { name: "结束面试" }));
    await waitFor(() => expect(onNavigate).toHaveBeenLastCalledWith("/interview/scenes/scene-3/session/session-3/report"));
  });
});

describe("InterviewAssets", () => {
  it("covers loading, overview, history, trends and asset navigation", async () => {
    let resolveAssets;
    getInterviewAssets.mockReturnValueOnce(new Promise((resolve) => { resolveAssets = resolve; }));
    render(<InterviewAssets route={{ tab: "overview" }} onNavigate={vi.fn()} onBack={vi.fn()} />);
    expect(screen.getByRole("status", { name: /正在读取面试学习资产/ })).toBeInTheDocument();
    resolveAssets([]);
    cleanup();

    getInterviewAssets.mockResolvedValue(assetItems);
    getInterviewReport.mockImplementation((sceneId, sessionId) => sceneId === "scene-complete" && sessionId === "session-1"
      ? Promise.resolve({ status: "COMPLETED", report: completedReport })
      : Promise.resolve({ status: "PROCESSING", report: null }));
    const onNavigate = vi.fn();
    const callbacks = { onBack: vi.fn(), onBackToAssets: vi.fn(), onBackToIelts: vi.fn(), onTraining: vi.fn(), onPractice: vi.fn() };
    render(<InterviewAssets route={{ tab: "overview" }} onNavigate={onNavigate} {...callbacks} />);
    await waitFor(() => expect(screen.getByRole("heading", { name: "面试学习资产" })).toBeInTheDocument());
    await waitFor(() => expect(screen.getByText("前端工程师")).toBeInTheDocument());
    expect(screen.getByText("优先提升")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: /查看能力趋势/ }));
    expect(onNavigate).toHaveBeenCalledWith("/interview/assets/trends");

    cleanup();
    render(<InterviewAssets route={{ tab: "history" }} onNavigate={onNavigate} {...callbacks} />);
    await waitFor(() => expect(screen.getByRole("heading", { name: "面试记录" })).toBeInTheDocument());
    expect(screen.getAllByText(/累计练习 3 次/).length).toBeGreaterThan(0);
    await waitFor(() => expect(screen.getByText("结构完整")).toBeInTheDocument());
    fireEvent.click(screen.getByRole("button", { name: /复练本岗位/ }));
    expect(callbacks.onPractice).toHaveBeenCalledWith("scene-complete");
    fireEvent.click(screen.getByRole("button", { name: /产品经理/ }));
    expect(screen.getByText("报告生成中")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: /能力趋势/ }));
    expect(onNavigate).toHaveBeenCalledWith("/interview/assets/trends");

    cleanup();
    mockCanvas();
    render(<InterviewAssets route={{ tab: "trends" }} onNavigate={onNavigate} {...callbacks} />);
    await waitFor(() => expect(screen.getByText("最近五次评分")).toBeInTheDocument());
    expect(screen.getByText("78")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "切换学习资产模块" }));
    fireEvent.click(screen.getByRole("menuitem", { name: /IELTS 学习资产/ }));
    expect(callbacks.onBackToIelts).toHaveBeenCalled();
    fireEvent.click(screen.getByRole("button", { name: /返回训练中心/ }));
    expect(callbacks.onTraining).toHaveBeenCalled();
  });

  it("shows API errors and empty history state", async () => {
    getInterviewAssets.mockRejectedValue(new Error("interview assets down"));
    render(<InterviewAssets route={{ tab: "overview" }} onNavigate={vi.fn()} onBack={vi.fn()} />);
    await waitFor(() => expect(screen.getByRole("heading", { name: "学习资产加载失败" })).toBeInTheDocument());
    expect(screen.getByText("interview assets down")).toBeInTheDocument();
    cleanup();
    getInterviewAssets.mockResolvedValue([]);
    render(<InterviewAssets route={{ tab: "history" }} onNavigate={vi.fn()} onBack={vi.fn()} />);
    await waitFor(() => expect(screen.getByText("暂无面试学习资产")).toBeInTheDocument());
  });

  it("covers report fallbacks, unknown dimensions, and retrying processing reports", async () => {
    getInterviewReport.mockResolvedValue({ status: "COMPLETED", report: { overallScore: "not-a-score", summary: "", dimensions: [] } });
    render(<InterviewModule route={{ screen: "report", sceneId: "scene-fallback", sessionId: "session-fallback" }} onNavigate={vi.fn()} />);
    await waitFor(() => expect(screen.getByRole("heading", { name: "面试表现报告" })).toBeInTheDocument());
    expect(screen.getByText("本次面试已结束，暂无文字总结。")).toBeInTheDocument();
    expect(screen.getByText("报告暂未包含分维度评分。")).toBeInTheDocument();
    fireEvent.click(screen.getAllByRole("button", { name: "返回训练中心" })[0]);

    cleanup();
    getInterviewReport.mockResolvedValue({ status: "FAILED", failureReason: "首次失败", report: null });
    retryInterviewReport.mockResolvedValue({ status: "PROCESSING", report: null });
    render(<InterviewModule route={{ screen: "report", sceneId: "scene-retry", sessionId: "session-retry" }} onNavigate={vi.fn()} />);
    await waitFor(() => expect(screen.getByRole("heading", { name: "报告生成失败" })).toBeInTheDocument());
    fireEvent.click(screen.getByRole("button", { name: "重新生成" }));
    await waitFor(() => expect(screen.getByRole("heading", { name: "正在生成面试报告" })).toBeInTheDocument());
    expect(retryInterviewReport).toHaveBeenCalledWith("scene-retry", "session-retry");
  });

  it("handles report polling errors, report retrieval errors in assets, and empty trends", async () => {
    getInterviewReport.mockRejectedValue(new Error("asset report unavailable"));
    getInterviewAssets.mockResolvedValue([
      { sceneId: "scene-failed", jobTitle: "数据工程师", difficulty: "UNKNOWN", practiceCount: 2, latestSessionId: "session-failed", latestReportStatus: "FAILED", latestPracticedAt: null },
      { sceneId: "scene-new", jobTitle: "未命名岗位", difficulty: "", practiceCount: 0, latestSessionId: null, latestReportStatus: "" },
    ]);
    const onPractice = vi.fn();
    render(<InterviewAssets route={{ tab: "history" }} onNavigate={vi.fn()} onBack={vi.fn()} onPractice={onPractice} />);
    await waitFor(() => expect(screen.getByRole("heading", { name: "报告暂不可用" })).toBeInTheDocument());
    fireEvent.click(screen.getByRole("button", { name: "复练本岗位" }));
    expect(onPractice).toHaveBeenCalledWith("scene-failed");
    fireEvent.click(screen.getByRole("button", { name: /未命名岗位/ }));
    expect(screen.getByRole("heading", { name: "尚未开始面试" })).toBeInTheDocument();

    cleanup();
    getInterviewAssets.mockResolvedValue([]);
    render(<InterviewAssets route={{ tab: "trends" }} onNavigate={vi.fn()} onBack={vi.fn()} />);
    await waitFor(() => expect(screen.getByText("暂无评分趋势")).toBeInTheDocument());
    expect(screen.getByText("暂无能力评分")).toBeInTheDocument();
    expect(screen.getByText("等待报告")).toBeInTheDocument();
  });

  it("covers interview session start/stop failures, quota completion, and the remote audio fallback", async () => {
    const startClient = mockInterviewRealtimeClient({ start: vi.fn().mockRejectedValue(new Error("interview start failed")) });
    render(<InterviewModule route={{ screen: "session", sceneId: "scene-start-fail" }} teacher={{ name: "Alex" }} speed="未知" onNavigate={vi.fn()} onBack={vi.fn()} />);
    await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent("interview start failed"));
    expect(startClient.options).toBeDefined();

    cleanup();
    const stopClient = mockInterviewRealtimeClient({ stop: vi.fn().mockRejectedValue(new Error("interview stop failed")) });
    const onNavigate = vi.fn();
    render(<InterviewModule route={{ screen: "session", sceneId: "scene-stop-fail" }} teacher={{ name: "Alex" }} speed="自然" onNavigate={onNavigate} onBack={vi.fn()} />);
    await waitFor(() => expect(stopClient.options).toBeDefined());
    stopClient.options.onEvent({ type: "local.connected", sessionId: "session-stop" });
    fireEvent.click(screen.getByRole("button", { name: "结束面试" }));
    await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent("interview stop failed"));
    expect(onNavigate).not.toHaveBeenCalled();

    cleanup();
    const quotaClient = mockInterviewRealtimeClient({ stop: vi.fn().mockResolvedValue({ reportStatus: null }) });
    render(<InterviewModule route={{ screen: "session", sceneId: "scene-quota" }} teacher={{ name: "Alex" }} speed="适中" onNavigate={onNavigate} onBack={vi.fn()} />);
    await waitFor(() => expect(quotaClient.options).toBeDefined());
    quotaClient.options.onEvent({ type: "local.connected", sessionId: "session-quota" });
    quotaClient.options.onEvent({ type: "local.quota_exhausted", message: "quota exhausted" });
    await waitFor(() => expect(quotaClient.stop).toHaveBeenCalledWith({ reason: "quota_exhausted" }));
    await waitFor(() => expect(onNavigate).toHaveBeenLastCalledWith("/interview/scenes/scene-quota/session/session-quota/report"));

    cleanup();
    const audioClient = mockInterviewRealtimeClient();
    render(<InterviewModule route={{ screen: "session", sceneId: "scene-audio" }} teacher={{ name: "Alex" }} speed="慢一些" onNavigate={vi.fn()} onBack={vi.fn()} />);
    await waitFor(() => expect(audioClient.options).toBeDefined());
    await audioClient.options.onRemoteAudioDrain({ fallbackMs: 1 });
    expect(audioClient.options.onRemoteAudioDrain).toBeTypeOf("function");
  });
});
