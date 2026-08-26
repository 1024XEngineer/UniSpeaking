import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { AboutProduct } from "../AboutProduct.jsx";
import { AbilityTrendChart } from "../AbilityTrendChart.jsx";
import { AccountSecurity } from "../AccountSecurity.jsx";
import { LearningInsights } from "../LearningInsights.jsx";
import { ProductLegalDocument } from "../ProductLegalDocument.jsx";
import { WeaknessRecommendations } from "../WeaknessRecommendations.jsx";

vi.mock("../../../infrastructure/http/apiClient.js", () => ({
  getProfileInsights: vi.fn(),
  updateWeeklyLearningGoals: vi.fn(),
}));

import { getProfileInsights, updateWeeklyLearningGoals } from "../../../infrastructure/http/apiClient.js";

afterEach(() => cleanup());
beforeEach(() => vi.clearAllMocks());

const insightPayload = {
  weeklyGoals: {
    weekStartsAt: "2026-08-24T00:00:00+08:00",
    weekEndsAt: "2026-08-31T00:00:00+08:00",
    completedDurationSeconds: 90,
    remainingDurationSeconds: 120,
    durationTargetMinutes: 10,
    durationProgress: 12.5,
    durationAchieved: false,
    completedTrainingCount: 2,
    trainingCountTarget: 3,
    countProgress: 66.6,
    remainingTrainingCount: 1,
    countAchieved: false,
  },
  trainingTypeDistribution: [
    { type: "FREE_CHAT", durationSeconds: 60, percentage: 50 },
    { type: "UNKNOWN", durationSeconds: 60, percentage: 50 },
    { type: "CUSTOM_SCENE", durationSeconds: 0, percentage: 0 },
  ],
  abilityTrends: [
    { sessionId: "one", completedAt: "2026-08-24T12:00:00+08:00", trainingType: "FREE_CHAT", scores: { accuracy: 90, fluency: 30 } },
    { sessionId: "two", completedAt: "2026-08-25T12:00:00+08:00", trainingType: "CUSTOM_SCENE", scores: { accuracy: 120, fluency: -1, grammar: 50 } },
  ],
  weaknessAnalysis: { sampleCount: 3, minimumSampleCount: 3, reliable: true },
  weaknesses: [
    { dimension: "accuracy", rank: 1, averageScore: 78.5, basis: "最近准确度需要加强", recentChange: 2.5 },
    { dimension: "fluency", rank: 2, averageScore: 60, basis: "表达容易停顿", recentChange: -3 },
    { dimension: "unknown", rank: 3, averageScore: 0, basis: "综合", recentChange: 0 },
  ],
  recommendations: [
    { dimension: "accuracy", trainingType: "FREE_CHAT", reason: "通过自由对话保持准确表达" },
    { dimension: "fluency", trainingType: "IELTS_SCENE", reason: "专项入口建设中" },
  ],
};

describe("profile presentation components", () => {
  it("covers empty and interactive ability trend chart states", () => {
    const { rerender } = render(<AbilityTrendChart items={[]} />);
    expect(screen.getByText("暂无可用的五维评分报告")).toBeInTheDocument();
    rerender(<AbilityTrendChart items={insightPayload.abilityTrends} />);
    expect(screen.getByRole("img", { name: /准确度最近 2 次评分趋势/ })).toBeInTheDocument();
    expect(screen.getByText("最近 2 次有效评分")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "流利度" }));
    expect(screen.getByRole("img", { name: /流利度最近 2 次评分趋势/ })).toBeInTheDocument();
    fireEvent.focus(screen.getAllByRole("img", { name: /流利度/ })[0]);
  });

  it("renders weak sample state and supported/unsupported recommendations", () => {
    const onStart = vi.fn();
    const { rerender } = render(<WeaknessRecommendations analysis={{ sampleCount: 1, minimumSampleCount: 3, reliable: false }} />);
    expect(screen.getByText("还需 2 次有效评分")).toBeInTheDocument();
    rerender(<WeaknessRecommendations analysis={insightPayload.weaknessAnalysis} weaknesses={insightPayload.weaknesses} recommendations={insightPayload.recommendations} onStartTraining={onStart} />);
    expect(screen.getByText("准确度")).toBeInTheDocument();
    expect(screen.getAllByRole("button", { name: "建设中" })[0]).toBeDisabled();
    fireEvent.click(screen.getByRole("button", { name: "开始训练" }));
    expect(onStart).toHaveBeenCalledWith("FREE_CHAT");
    expect(screen.getAllByRole("button", { name: "建设中" })).toHaveLength(2);
  });

  it("loads learning insights, edits goals, validates and saves", async () => {
    getProfileInsights.mockResolvedValue(insightPayload);
    updateWeeklyLearningGoals.mockResolvedValue({ ...insightPayload, weeklyGoals: { ...insightPayload.weeklyGoals, durationAchieved: true, countAchieved: true } });
    render(<LearningInsights onStartTraining={vi.fn()} />);
    await waitFor(() => expect(screen.getByRole("heading", { name: "学习目标与洞察" })).toBeInTheDocument());
    expect(screen.getByText(/还差 2 分钟/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: /调整目标/ }));
    expect(screen.getByRole("dialog", { name: "调整每周目标" })).toBeInTheDocument();
    const inputs = screen.getByRole("dialog").querySelectorAll("input");
    fireEvent.change(inputs[0], { target: { value: "0" } });
    fireEvent.submit(screen.getByRole("dialog").querySelector("form"));
    expect(screen.getByRole("alert")).toHaveTextContent("时长目标需在");
    fireEvent.change(inputs[0], { target: { value: "20" } });
    fireEvent.change(inputs[1], { target: { value: "4" } });
    fireEvent.click(screen.getByRole("button", { name: "保存目标" }));
    await waitFor(() => expect(updateWeeklyLearningGoals).toHaveBeenCalledWith({ durationTargetMinutes: 20, trainingCountTarget: 4 }));
  });

  it("shows insight load errors and retry", async () => {
    getProfileInsights.mockRejectedValueOnce(new Error("网络不可用")).mockResolvedValueOnce(insightPayload);
    render(<LearningInsights />);
    await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent("网络不可用"));
    fireEvent.click(screen.getByRole("button", { name: /重新加载/ }));
    await waitFor(() => expect(getProfileInsights).toHaveBeenCalledTimes(2));
  });

  it("renders account, about, and legal pages with navigation callbacks", () => {
    const onNavigate = vi.fn();
    const onHelpNavigate = vi.fn();
    const onOpenPassword = vi.fn();
    const onLogout = vi.fn();
    render(<AccountSecurity email="person@example.com" onOpenPassword={onOpenPassword} onLogout={onLogout} />);
    expect(screen.getByText("person@example.com")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: /修改密码/ }));
    fireEvent.click(screen.getByRole("button", { name: /退出登录/ }));
    expect(onOpenPassword).toHaveBeenCalled();
    expect(onLogout).toHaveBeenCalled();
    cleanup();

    render(<AboutProduct onNavigate={onNavigate} onHelpNavigate={onHelpNavigate} />);
    expect(screen.getByRole("heading", { name: "关于 UniSpeaking" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("link", { name: /用户协议/ }));
    fireEvent.click(screen.getByRole("link", { name: /帮助中心/ }));
    expect(onNavigate).toHaveBeenCalled();
    expect(onHelpNavigate).toHaveBeenCalledWith("/help");
    cleanup();

    render(<ProductLegalDocument documentId="privacy-policy" onNavigate={onNavigate} />);
    expect(screen.getByRole("heading", { name: /隐私政策/ })).toBeInTheDocument();
    expect(screen.getByText("开发草案")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("link", { name: /返回关于产品/ }));
    expect(onNavigate).toHaveBeenCalledWith("/about");
    cleanup();
    render(<ProductLegalDocument documentId="unknown" />);
    expect(screen.getByRole("heading", { name: /用户协议/ })).toBeInTheDocument();
  });
});
