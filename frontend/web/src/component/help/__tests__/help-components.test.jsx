import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ExternalFeedbackLink } from "../ExternalFeedbackLink.jsx";
import { HelpArticle } from "../HelpArticle.jsx";
import { HelpCategory } from "../HelpCategory.jsx";
import { HelpCenter } from "../HelpCenter.jsx";
import { HelpLayout } from "../HelpLayout.jsx";
import { helpCategories, helpArticles } from "../helpData.js";

afterEach(() => {
  cleanup();
  vi.unstubAllEnvs();
});

const navigate = vi.fn();
const article = helpArticles[0];
const category = helpCategories[0];

describe("help center components", () => {
  it("renders the public shell and intercepts internal navigation", () => {
    render(<HelpLayout onNavigate={navigate}><p>帮助内容</p></HelpLayout>);
    expect(screen.getByRole("banner")).toBeInTheDocument();
    expect(screen.getByAltText("UniSpeaking")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("link", { name: "登录" }));
    expect(navigate).toHaveBeenCalledWith("/login");
    fireEvent.click(screen.getByRole("link", { name: /开始使用/ }));
    expect(navigate).toHaveBeenCalledWith("/signup");
  });

  it("supports help home search, clear, empty state, and result navigation", () => {
    render(<HelpCenter route={{ screen: "home" }} onNavigate={navigate} />);
    expect(screen.getByRole("heading", { name: "热门问题" })).toBeInTheDocument();
    const input = screen.getByRole("searchbox");
    fireEvent.change(input, { target: { value: "麦克风" } });
    expect(screen.getByRole("heading", { name: "搜索结果" })).toBeInTheDocument();
    expect(screen.getByText(/找到 \d+ 篇/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "清空帮助搜索" }));
    expect(screen.getByRole("heading", { name: "热门问题" })).toBeInTheDocument();
    fireEvent.change(input, { target: { value: "绝对不存在的帮助关键词" } });
    expect(screen.getByRole("status")).toHaveTextContent("暂时没有找到相关内容");
    fireEvent.click(screen.getByRole("button", { name: "浏览全部分类" }));
    expect(screen.getByRole("heading", { name: "按主题查找" })).toBeInTheDocument();
    fireEvent.change(screen.getByRole("searchbox"), { target: { value: article.title.slice(0, 4) } });
    fireEvent.click(screen.getAllByRole("link", { name: new RegExp(article.title) })[0]);
    expect(navigate).toHaveBeenCalled();
  });

  it("renders category, feedback callout, article steps and ratings", () => {
    render(<HelpCategory category={{ ...category, id: "feedback", title: "问题反馈" }} articles={[article]} onNavigate={navigate} />);
    expect(screen.getByRole("heading", { name: "问题反馈" })).toBeInTheDocument();
    expect(screen.getByText("已经完成基础排查？")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("link", { name: new RegExp(article.title) }));
    expect(navigate).toHaveBeenCalled();

    cleanup();
    render(<HelpArticle article={article} category={category} relatedArticles={[helpArticles[1]]} onNavigate={navigate} />);
    expect(screen.getByRole("heading", { name: article.title })).toBeInTheDocument();
    expect(screen.getAllByRole("listitem")).toHaveLength(article.steps.length + 1);
    fireEvent.click(screen.getByRole("button", { name: "解决了" }));
    expect(screen.getByRole("status")).toHaveTextContent("谢谢你的反馈");

    cleanup();
    render(<HelpArticle article={article} category={category} relatedArticles={[]} onNavigate={navigate} />);
    fireEvent.click(screen.getByRole("button", { name: "仍有问题" }));
    expect(screen.getByRole("status")).toHaveTextContent("我们继续帮你排查");
  });

  it("shows not-found routes and keeps external feedback link semantic", () => {
    render(<HelpCenter route={{ screen: "category", categoryId: "missing" }} onNavigate={navigate} />);
    expect(screen.getByRole("heading", { name: "没有找到这项帮助内容" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("link", { name: /返回帮助中心/ }));
    expect(navigate).toHaveBeenCalledWith("/help");

    cleanup();
    render(<ExternalFeedbackLink className="custom">反馈</ExternalFeedbackLink>);
    const link = screen.getByRole("link", { name: "反馈" });
    expect(link).toHaveAttribute("target", "_blank");
    expect(link).toHaveClass("external-feedback-link", "custom");
    cleanup();
    render(<ExternalFeedbackLink>{(configured) => configured ? "已配置" : "未配置"}</ExternalFeedbackLink>);
    expect(screen.getByText("已配置")).toBeInTheDocument();
  });

  it("renders valid category and article routes", () => {
    cleanup();
    render(<HelpCenter route={{ screen: "category", categoryId: category.id }} onNavigate={navigate} />);
    expect(screen.getByRole("heading", { name: category.title })).toBeInTheDocument();

    cleanup();
    render(<HelpCenter route={{ screen: "article", articleId: article.id }} onNavigate={navigate} />);
    expect(screen.getByRole("heading", { name: article.title })).toBeInTheDocument();
  });

  it("renders feedback callouts from the help home and article unresolved state", () => {
    cleanup();
    render(<HelpCenter route={{ screen: "home" }} onNavigate={navigate} />);
    const feedbackLinks = screen.getAllByRole("link").filter((link) => link.getAttribute("href")?.startsWith("http"));
    expect(feedbackLinks).toHaveLength(1);
    expect(feedbackLinks[0]).toHaveTextContent("问题反馈");

    cleanup();
    render(<HelpArticle article={article} category={category} relatedArticles={[]} onNavigate={navigate} />);
    fireEvent.click(screen.getByRole("button", { name: "仍有问题" }));
    expect(screen.getByRole("link", { name: "仍未解决，提交反馈" })).toHaveAttribute("target", "_blank");
  });

  it("handles keyboard search submission and Escape reset", () => {
    render(<HelpCenter route={{ screen: "home" }} onNavigate={navigate} />);
    const input = screen.getByRole("searchbox");
    fireEvent.change(input, { target: { value: "密码" } });
    fireEvent.keyDown(input, { key: "Escape" });
    expect(input).toHaveValue("");
    fireEvent.change(input, { target: { value: "麦克风" } });
    fireEvent.submit(input.closest("form"));
    expect(screen.getByRole("heading", { name: "搜索结果" })).toBeInTheDocument();
    expect(document.activeElement).toBe(screen.getByRole("region", { name: "搜索结果" }));
  });

  it("disables the feedback entry when its configured URL is invalid", async () => {
    vi.resetModules();
    vi.stubEnv("VITE_FEEDBACK_URL", "javascript:alert(1)");
    const { ExternalFeedbackLink: DisabledFeedbackLink, feedbackUrl: disabledUrl } = await import("../ExternalFeedbackLink.jsx");

    expect(disabledUrl).toBe("");
    render(<DisabledFeedbackLink className="custom">{(configured) => configured ? "已配置" : "未配置"}</DisabledFeedbackLink>);
    const disabled = screen.getByText("未配置").closest("span");
    expect(disabled).toHaveAttribute("aria-disabled", "true");
    expect(disabled).toHaveAttribute("title", "反馈问卷链接尚未配置");
    expect(disabled).toHaveClass("external-feedback-link", "custom", "is-disabled");
    expect(screen.queryByRole("link")).not.toBeInTheDocument();

    vi.resetModules();
    vi.stubEnv("VITE_FEEDBACK_URL", "not a URL");
    const { feedbackUrl: malformedUrl } = await import("../ExternalFeedbackLink.jsx");
    expect(malformedUrl).toBe("");

    vi.resetModules();
    vi.stubEnv("VITE_FEEDBACK_URL", "   ");
    const { feedbackUrl: emptyUrl } = await import("../ExternalFeedbackLink.jsx");
    expect(emptyUrl).toBe("");
  });
});
