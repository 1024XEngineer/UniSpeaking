import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

const telemetry = vi.hoisted(() => ({
  captureException: vi.fn(),
  initializeBrowserTelemetry: vi.fn(),
}));
const root = vi.hoisted(() => ({
  createRoot: vi.fn(() => ({ render: vi.fn() })),
}));

vi.mock("react-dom/client", () => root);
vi.mock("../../telemetry/clientTelemetry.js", () => telemetry);
vi.mock("../../component/achievement/AchievementNotifications.jsx", () => ({
  AchievementNotificationProvider: ({ children }) => <div data-testid="provider">{children}</div>,
}));
vi.mock("../App.jsx", () => ({ App: () => <div data-testid="app">应用内容</div> }));

describe("controller entrypoint", () => {
  it("initializes telemetry and mounts the application tree", async () => {
    document.body.innerHTML = '<div id="root"></div>';
    await import("../main.jsx");
    expect(telemetry.initializeBrowserTelemetry).toHaveBeenCalledTimes(1);
    expect(root.createRoot).toHaveBeenCalledWith(document.getElementById("root"));
    expect(root.createRoot.mock.results[0].value.render).toHaveBeenCalledTimes(1);
  });

  it("renders the entrypoint's provider tree when the root render callback is used", async () => {
    const rendered = root.createRoot.mock.results[0]?.value.render.mock.calls[0]?.[0];
    expect(rendered).toBeTruthy();
    render(rendered);
    expect(screen.getByTestId("provider")).toBeInTheDocument();
    expect(screen.getByTestId("app")).toHaveTextContent("应用内容");
  });

  it("keeps the entrypoint testable with a browser root", () => {
    const rootElement = document.getElementById("root");
    expect(rootElement).not.toBeNull();
    fireEvent(window, new Event("load"));
    expect(telemetry.captureException).not.toHaveBeenCalled();
  });
});
