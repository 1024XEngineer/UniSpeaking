import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { AchievementNotificationProvider, useAchievementNotifications } from "../AchievementNotifications.jsx";

const mockApi = vi.hoisted(() => ({
  acknowledgeAchievementUnlock: vi.fn(),
  syncAchievementUnlocks: vi.fn(),
}));

vi.mock("../../../infrastructure/http/apiClient.js", () => mockApi);

function Harness() {
  const { synchronizeAchievements, clearAchievementNotifications } = useAchievementNotifications();
  return (
    <div>
      <button onClick={() => void synchronizeAchievements({ revealNotifications: true })}>同步并展示</button>
      <button onClick={() => void synchronizeAchievements()}>后台同步</button>
      <button onClick={clearAchievementNotifications}>清空通知</button>
    </div>
  );
}

const notification = {
  achievementId: "achievement-1",
  title: "连续开口",
  description: "完成连续练习",
  category: "练习习惯",
  seriesTitle: "坚持练习",
  level: 2,
};

describe("AchievementNotificationProvider", () => {
  beforeEach(() => {
    vi.useRealTimers();
    mockApi.acknowledgeAchievementUnlock.mockReset();
    mockApi.syncAchievementUnlocks.mockReset();
    mockApi.acknowledgeAchievementUnlock.mockResolvedValue(undefined);
    mockApi.syncAchievementUnlocks.mockResolvedValue({ pendingNotifications: [notification] });
  });

  afterEach(() => {
    cleanup();
    vi.useRealTimers();
  });

  it("reveals queued achievements, shows remaining count, and acknowledges dismissal", async () => {
    const second = { ...notification, achievementId: "achievement-2", title: "第二个成就" };
    mockApi.syncAchievementUnlocks.mockResolvedValue({ pendingNotifications: [notification, second] });
    render(<AchievementNotificationProvider><Harness /></AchievementNotificationProvider>);

    fireEvent.click(screen.getAllByRole("button", { name: "同步并展示" })[0]);
    expect(await screen.findByRole("status", { name: "成就已达成：连续开口" })).toBeInTheDocument();
    expect(screen.getByText("还有 1 个成就待展示")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "关闭成就通知" }));
    await waitFor(() => expect(mockApi.acknowledgeAchievementUnlock).toHaveBeenCalledWith("achievement-1"));
    expect(await screen.findByRole("status", { name: "成就已达成：第二个成就" })).toBeInTheDocument();

    fireEvent.keyDown(window, { key: "Escape" });
    await waitFor(() => expect(mockApi.acknowledgeAchievementUnlock).toHaveBeenCalledWith("achievement-2"));
  });

  it("deduplicates notifications already shown in the current session", async () => {
    render(<AchievementNotificationProvider><Harness /></AchievementNotificationProvider>);
    fireEvent.click(screen.getAllByRole("button", { name: "同步并展示" })[0]);
    expect(await screen.findByRole("status", { name: "成就已达成：连续开口" })).toBeInTheDocument();
    const syncCountAfterReveal = mockApi.syncAchievementUnlocks.mock.calls.length;
    fireEvent.click(screen.getAllByRole("button", { name: "同步并展示" })[0]);
    await waitFor(() => expect(mockApi.syncAchievementUnlocks.mock.calls.length).toBeGreaterThan(syncCountAfterReveal));
    expect(screen.getAllByRole("status", { name: "成就已达成：连续开口" })).toHaveLength(1);
    fireEvent.click(screen.getByRole("button", { name: "关闭成就通知" }));
    await waitFor(() => expect(mockApi.acknowledgeAchievementUnlock).toHaveBeenCalledWith("achievement-1"));
  });

  it("auto dismisses a popup after five seconds", async () => {
    vi.useFakeTimers();
    render(<AchievementNotificationProvider><Harness /></AchievementNotificationProvider>);
    await act(async () => {
      fireEvent.click(screen.getByRole("button", { name: "同步并展示" }));
      await Promise.resolve();
    });
    expect(screen.getByRole("status", { name: "成就已达成：连续开口" })).toBeInTheDocument();
    await act(async () => {
      await vi.advanceTimersByTimeAsync(5_000);
    });
    expect(screen.queryByRole("status", { name: "成就已达成：连续开口" })).not.toBeInTheDocument();
  });

  it("retries failed acknowledgements on the next synchronization and clears stale state", async () => {
    mockApi.acknowledgeAchievementUnlock
      .mockRejectedValueOnce(new Error("offline"))
      .mockResolvedValue(undefined);
    render(<AchievementNotificationProvider><Harness /></AchievementNotificationProvider>);
    fireEvent.click(screen.getAllByRole("button", { name: "同步并展示" })[0]);
    expect(await screen.findByRole("status", { name: "成就已达成：连续开口" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "关闭成就通知" }));
    await waitFor(() => expect(mockApi.acknowledgeAchievementUnlock).toHaveBeenCalled());
    const acknowledgementCount = mockApi.acknowledgeAchievementUnlock.mock.calls.length;
    fireEvent.click(screen.getByRole("button", { name: "后台同步" }));
    await waitFor(() => expect(mockApi.acknowledgeAchievementUnlock.mock.calls.length).toBeGreaterThan(acknowledgementCount));
    fireEvent.click(screen.getByRole("button", { name: "清空通知" }));
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
  });

  it("does not render a popup before synchronization and rejects missing provider context", () => {
    render(<AchievementNotificationProvider><Harness /></AchievementNotificationProvider>);
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
    expect(() => render(<ProbeOutsideProvider />)).toThrow("useAchievementNotifications must be used within AchievementNotificationProvider");
  });
});

function ProbeOutsideProvider() {
  useAchievementNotifications();
  return null;
}
