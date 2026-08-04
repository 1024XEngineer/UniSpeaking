import { useRef, useState } from "react";
import { ArrowLeft, CheckCircle, ClipboardText, Info, ShieldCheck } from "@phosphor-icons/react";
import { paths } from "../router.js";
import { helpCategories } from "./helpData.js";
import { handleHelpLinkClick } from "./helpUtils.js";

const initialForm = {
  categoryId: "ai-training",
  title: "",
  description: "",
  environment: "",
};

export function HelpFeedback({ onNavigate }) {
  const [form, setForm] = useState(initialForm);
  const [summary, setSummary] = useState("");
  const [copyStatus, setCopyStatus] = useState("");
  const summaryRef = useRef(null);

  const updateField = (field) => (event) => {
    setForm((current) => ({ ...current, [field]: event.target.value }));
    setSummary("");
    setCopyStatus("");
  };

  const buildSummary = (event) => {
    event.preventDefault();
    const category = helpCategories.find((item) => item.id === form.categoryId);
    setSummary([
      `问题分类：${category?.title || "其他"}`,
      `问题标题：${form.title.trim()}`,
      "问题描述与复现步骤：",
      form.description.trim(),
      `设备与浏览器：${form.environment.trim() || "未填写"}`,
      `发生时间：${new Date().toLocaleString("zh-CN", { timeZone: "Asia/Shanghai" })}`,
    ].join("\n"));
    setCopyStatus("反馈摘要已生成，确认内容后可以复制保存。");
  };

  const copySummary = async () => {
    try {
      await navigator.clipboard.writeText(summary);
      setCopyStatus("反馈摘要已复制。当前阶段不会自动上传，请保存到安全位置。");
    } catch {
      summaryRef.current?.focus();
      summaryRef.current?.select();
      setCopyStatus("无法自动复制，已选中摘要，请使用系统复制快捷键。");
    }
  };

  return (
    <main className="help-page help-feedback-page">
      <a
        className="help-back-link"
        href={paths.help.root}
        onClick={(event) => handleHelpLinkClick(event, paths.help.root, onNavigate)}
      >
        <ArrowLeft weight="bold" />返回帮助中心
      </a>

      <header className="help-section-header">
        <p className="eyebrow">FEEDBACK</p>
        <h1>问题反馈</h1>
        <p>描述发生了什么，我们会帮助你把问题整理得更清楚。</p>
      </header>

      <aside className="help-feedback-notice" role="note">
        <Info weight="fill" />
        <p><strong>第一阶段不会自动提交或上传反馈。</strong><span>填写内容只保留在当前页面，用于生成可复制摘要；正式支持渠道接入后会另行说明。</span></p>
      </aside>

      <div className="help-feedback-layout">
        <form className="help-feedback-form" onSubmit={buildSummary}>
          <label>
            问题分类
            <select value={form.categoryId} onChange={updateField("categoryId")}>
              {helpCategories.map((category) => <option key={category.id} value={category.id}>{category.title}</option>)}
            </select>
          </label>
          <label>
            问题标题
            <input
              type="text"
              value={form.title}
              onChange={updateField("title")}
              maxLength={80}
              placeholder="用一句话概括问题"
              required
            />
          </label>
          <label>
            问题描述与复现步骤
            <textarea
              value={form.description}
              onChange={updateField("description")}
              maxLength={2000}
              rows={8}
              placeholder="例如：进入自由对话 → 允许麦克风 → 点击开始后看到什么提示；你原本期望发生什么。"
              required
            />
            <small>{form.description.length} / 2000</small>
          </label>
          <label>
            设备与浏览器（选填）
            <input
              type="text"
              value={form.environment}
              onChange={updateField("environment")}
              maxLength={120}
              placeholder="例如：macOS 15，Chrome"
            />
          </label>
          <button type="submit"><ClipboardText weight="bold" />生成反馈摘要</button>
        </form>

        <aside className="help-feedback-guide">
          <ShieldCheck weight="duotone" />
          <h2>请勿填写敏感信息</h2>
          <ul>
            <li>账号密码、验证码或登录令牌</li>
            <li>API Key、密钥或完整后台响应</li>
            <li>身份证明或与问题无关的个人信息</li>
          </ul>
          <p>提供页面路径、发生时间、复现步骤和错误提示通常已经足够。</p>
        </aside>
      </div>

      {summary && (
        <section className="help-feedback-summary" aria-labelledby="feedback-summary-title">
          <div><h2 id="feedback-summary-title">反馈摘要</h2><span><CheckCircle weight="fill" />仅保存在当前页面</span></div>
          <textarea ref={summaryRef} readOnly value={summary} aria-label="生成的反馈摘要" />
          <button type="button" onClick={copySummary}><ClipboardText weight="bold" />复制摘要</button>
        </section>
      )}
      <p className="help-copy-status" aria-live="polite">{copyStatus}</p>
    </main>
  );
}
