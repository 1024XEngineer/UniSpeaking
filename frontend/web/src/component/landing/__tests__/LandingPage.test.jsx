import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { LandingPage } from "../LandingPage.jsx";

afterEach(() => {
  cleanup();
  document.body.className = "";
});

describe("LandingPage", () => {
  it("renders the landing content and wires primary actions", () => {
    const onStart = vi.fn();
    const onLogin = vi.fn();
    const onWeb = vi.fn();
    const onSpecialty = vi.fn();
    render(<LandingPage onStart={onStart} onLogin={onLogin} onWeb={onWeb} onSpecialty={onSpecialty} />);
    expect(screen.getByRole("heading", { name: /越说/ })).toBeInTheDocument();
    expect(screen.getByText("实时语音对话")).toBeInTheDocument();
    fireEvent.click(screen.getAllByRole("button", { name: /开始练习/ })[0]);
    fireEvent.click(screen.getByRole("button", { name: "登录" }));
    fireEvent.click(screen.getByRole("button", { name: "进入 Web" }));
    fireEvent.click(screen.getByRole("button", { name: "开始体验 IELTS 口语" }));
    fireEvent.click(screen.getByRole("button", { name: "开始体验英文面试" }));
    expect(onStart).toHaveBeenCalled();
    expect(onLogin).toHaveBeenCalledTimes(1);
    expect(onWeb).toHaveBeenCalledTimes(1);
    expect(onSpecialty).toHaveBeenNthCalledWith(1, "ielts");
    expect(onSpecialty).toHaveBeenNthCalledWith(2, "interview");
  });

  it("opens mobile menu, selects teacher, toggles FAQ, and supports all cta paths", () => {
    const onStart = vi.fn();
    const onLogin = vi.fn();
    render(<LandingPage onStart={onStart} onLogin={onLogin} onWeb={vi.fn()} onSpecialty={vi.fn()} />);
    const menu = screen.getByRole("button", { name: "打开菜单" });
    fireEvent.click(menu);
    expect(screen.getByRole("button", { name: "关闭菜单" })).toBeInTheDocument();
    expect(document.body).toHaveClass("landing-menu-open");
    fireEvent.click(screen.getByRole("navigation", { name: "移动端主导航" }).querySelector("a"));
    expect(screen.getByRole("button", { name: "打开菜单" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "打开菜单" }));
    fireEvent.click(screen.getByRole("navigation", { name: "移动端主导航" }).parentElement.querySelector("button"));
    expect(onLogin).toHaveBeenCalled();

    const teachers = screen.getByLabelText("AI 老师列表").querySelectorAll("button");
    fireEvent.click(teachers[1]);
    expect(screen.getByRole("heading", { level: 3, name: /James|Clara|David|Emily|Leo|Arthur/ })).toBeInTheDocument();
    const faqButtons = screen.getAllByRole("button", { name: /吗？|什么？/ });
    expect(faqButtons[0]).toHaveAttribute("aria-expanded", "true");
    fireEvent.click(faqButtons[0]);
    expect(faqButtons[0]).toHaveAttribute("aria-expanded", "false");
    fireEvent.click(faqButtons[1]);
    expect(faqButtons[1]).toHaveAttribute("aria-expanded", "true");
    fireEvent.click(screen.getAllByRole("button", { name: /免费开始/ })[0]);
    expect(onStart).toHaveBeenCalled();
  });
});
