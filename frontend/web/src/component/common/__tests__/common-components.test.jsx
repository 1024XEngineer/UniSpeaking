import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { EvaluationLoader } from "../EvaluationLoader.jsx";
import { Modal } from "../Modal.jsx";
import { NewtonsCradle } from "../NewtonsCradle.jsx";

afterEach(() => cleanup());

describe("common loading and modal components", () => {
  it("renders modal content, wide styling, and both close paths", () => {
    const onClose = vi.fn();
    const { rerender } = render(
      <Modal onClose={onClose} wide className="custom-modal">
        <p>内容</p>
      </Modal>,
    );

    expect(screen.getByRole("dialog")).toHaveClass("modal", "modal--wide", "custom-modal");
    expect(screen.getByText("内容")).toBeInTheDocument();
    fireEvent.mouseDown(screen.getByRole("dialog"));
    expect(onClose).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole("button", { name: "关闭" }));
    expect(onClose).toHaveBeenCalledTimes(1);
    fireEvent.mouseDown(screen.getByRole("dialog").parentElement);
    expect(onClose).toHaveBeenCalledTimes(2);

    rerender(<Modal onClose={onClose} dismissible={false}>不可关闭</Modal>);
    expect(screen.queryByRole("button", { name: "关闭" })).not.toBeInTheDocument();
    fireEvent.mouseDown(screen.getByRole("dialog").parentElement);
    expect(onClose).toHaveBeenCalledTimes(2);
  });

  it("renders the evaluation loader with its six animation elements", () => {
    const { container } = render(<EvaluationLoader />);
    expect(container.firstChild).toHaveAttribute("aria-hidden", "true");
    expect(container.querySelectorAll(".ielts-evaluation-loader__circle")).toHaveLength(3);
    expect(container.querySelectorAll(".ielts-evaluation-loader__shadow")).toHaveLength(3);
  });

  it("applies Newton's cradle defaults and custom visual properties", () => {
    render(<NewtonsCradle size={72} speed="2s" color="red" label="请稍候" className="extra" />);
    const status = screen.getByRole("status", { name: "请稍候" });
    expect(status).toHaveClass("newtons-cradle", "extra");
    expect(status).toHaveStyle({ "--uib-size": "72px", "--uib-speed": "2s", "--uib-color": "red" });
    expect(status.querySelectorAll(".newtons-cradle__dot")).toHaveLength(4);
  });
});
