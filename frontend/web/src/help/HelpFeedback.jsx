import { useEffect, useRef, useState } from "react";
import {
  ArrowClockwise,
  ArrowLeft,
  CheckCircle,
  ClipboardText,
  Info,
  MagnifyingGlass,
  PaperPlaneTilt,
  ShieldCheck,
} from "@phosphor-icons/react";
import { paths } from "../router.js";
import { helpCategories } from "./helpData.js";
import {
  getSavedFeedbackReceipts,
  isFeedbackUserSignedIn,
  loadMyHelpFeedbacks,
  queryHelpFeedback,
  submitHelpFeedback,
} from "./helpApi.js";
import { handleHelpLinkClick } from "./helpUtils.js";

const initialForm = {
  categoryId: "ai-training",
  title: "",
  description: "",
  environment: "",
};

const statusMeta = {
  SUBMITTED: { label: "已提交", description: "反馈已进入处理队列。" },
  IN_PROGRESS: { label: "处理中", description: "我们正在核对你提供的信息。" },
  RESOLVED: { label: "已解决", description: "反馈已经处理并给出答复。" },
  CLOSED: { label: "已关闭", description: "本次反馈处理已结束。" },
};

function categoryTitle(categoryId) {
  return helpCategories.find((item) => item.id === categoryId)?.title || "其他问题";
}

function formatDate(value) {
  if (!value) return "—";
  return new Intl.DateTimeFormat("zh-CN", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(value));
}

function FeedbackCard({ feedback, lookupCode, busy, onRefresh }) {
  const status = statusMeta[feedback.status] || statusMeta.SUBMITTED;
  return (
    <article className="help-feedback-card">
      <header>
        <div>
          <small>{categoryTitle(feedback.categoryId)} · {feedback.feedbackNo}</small>
          <h3>{feedback.title}</h3>
        </div>
        <span className={`help-feedback-status is-${feedback.status?.toLowerCase()}`}>{status.label}</span>
      </header>
      <p>{status.description}</p>
      {feedback.reply && (
        <blockquote>
          <strong>处理答复</strong>
          <p>{feedback.reply}</p>
          <time dateTime={feedback.repliedAt}>{formatDate(feedback.repliedAt)}</time>
        </blockquote>
      )}
      <footer>
        <time dateTime={feedback.updatedAt}>最近更新：{formatDate(feedback.updatedAt)}</time>
        {lookupCode && onRefresh && (
          <button type="button" disabled={busy} onClick={() => onRefresh(feedback.feedbackNo, lookupCode)}>
            <ArrowClockwise weight="bold" />{busy ? "查询中" : "刷新进度"}
          </button>
        )}
      </footer>
    </article>
  );
}

export function HelpFeedback({ onNavigate }) {
  const [form, setForm] = useState(initialForm);
  const [receipt, setReceipt] = useState(null);
  const [savedReceipts, setSavedReceipts] = useState(() => getSavedFeedbackReceipts());
  const [mine, setMine] = useState([]);
  const [lookup, setLookup] = useState({ feedbackNo: "", lookupCode: "" });
  const [lookupResult, setLookupResult] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [lookupBusy, setLookupBusy] = useState("");
  const [historyBusy, setHistoryBusy] = useState(false);
  const [formError, setFormError] = useState("");
  const [lookupError, setLookupError] = useState("");
  const [historyError, setHistoryError] = useState("");
  const [copyStatus, setCopyStatus] = useState("");
  const [signedIn, setSignedIn] = useState(() => isFeedbackUserSignedIn());
  const receiptRef = useRef(null);

  const loadMine = async () => {
    if (!isFeedbackUserSignedIn()) {
      setSignedIn(false);
      return;
    }
    setHistoryBusy(true);
    setHistoryError("");
    try {
      setMine(await loadMyHelpFeedbacks());
    } catch (error) {
      if (!isFeedbackUserSignedIn()) setSignedIn(false);
      setHistoryError(error.message || "暂时无法加载反馈记录，请稍后重试。");
    } finally {
      setHistoryBusy(false);
    }
  };

  useEffect(() => {
    if (signedIn) void loadMine();
  }, []);

  useEffect(() => {
    if (receipt) receiptRef.current?.focus();
  }, [receipt]);

  const updateField = (field) => (event) => {
    setForm((current) => ({ ...current, [field]: event.target.value }));
    setFormError("");
  };

  const submit = async (event) => {
    event.preventDefault();
    setSubmitting(true);
    setFormError("");
    setCopyStatus("");
    try {
      const nextReceipt = await submitHelpFeedback(form);
      setReceipt(nextReceipt);
      setSavedReceipts(getSavedFeedbackReceipts());
      setForm(initialForm);
      if (signedIn) void loadMine();
    } catch (error) {
      setFormError(error.message || "反馈提交失败，请检查网络后重试。");
    } finally {
      setSubmitting(false);
    }
  };

  const copyReceipt = async () => {
    if (!receipt) return;
    const text = `UniSpeaking 反馈编号：${receipt.feedback.feedbackNo}\n查询码：${receipt.lookupCode}`;
    try {
      await navigator.clipboard.writeText(text);
      setCopyStatus("反馈编号和查询码已复制，请妥善保存。");
    } catch {
      setCopyStatus(`请手动保存查询码：${receipt.lookupCode}`);
    }
  };

  const runLookup = async (feedbackNo, lookupCode) => {
    const normalizedNo = feedbackNo.trim().toUpperCase();
    if (!normalizedNo || !lookupCode.trim()) {
      setLookupError("请输入完整的反馈编号和查询码。");
      return;
    }
    setLookupBusy(normalizedNo);
    setLookupError("");
    try {
      const feedback = await queryHelpFeedback(normalizedNo, lookupCode);
      setLookupResult(feedback);
      setSavedReceipts(getSavedFeedbackReceipts());
    } catch (error) {
      setLookupError(error.message || "没有找到对应反馈，请核对编号和查询码。");
    } finally {
      setLookupBusy("");
    }
  };

  const submitLookup = (event) => {
    event.preventDefault();
    void runLookup(lookup.feedbackNo, lookup.lookupCode);
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
        <p>提交使用中遇到的问题，并通过反馈编号持续查看处理进度和答复。</p>
      </header>

      <aside className="help-feedback-notice" role="note">
        <Info weight="fill" />
        <p><strong>无需登录也可以提交和查询反馈。</strong><span>提交后请自行保存反馈编号和查询码；页面也会尝试将它们保存在当前浏览器中。</span></p>
      </aside>

      <div className="help-feedback-layout">
        <form className="help-feedback-form" onSubmit={submit} aria-describedby="feedback-form-error">
          <label>
            问题分类
            <select value={form.categoryId} disabled={submitting} onChange={updateField("categoryId")}>
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
              disabled={submitting}
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
              disabled={submitting}
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
              disabled={submitting}
            />
          </label>
          {formError && <p id="feedback-form-error" className="help-feedback-error" role="alert">{formError}</p>}
          <button type="submit" disabled={submitting}>
            <PaperPlaneTilt weight="bold" />{submitting ? "正在提交" : "提交反馈"}
          </button>
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

      {receipt && (
        <section ref={receiptRef} className="help-feedback-receipt" tabIndex="-1" aria-labelledby="feedback-receipt-title">
          <CheckCircle weight="fill" />
          <div>
            <h2 id="feedback-receipt-title">反馈提交成功</h2>
            <p>请保存下面两项信息。未登录时需要同时提供它们才能查询进度。</p>
            <dl>
              <div><dt>反馈编号</dt><dd>{receipt.feedback.feedbackNo}</dd></div>
              <div><dt>查询码</dt><dd>{receipt.lookupCode}</dd></div>
            </dl>
            <button type="button" onClick={copyReceipt}><ClipboardText weight="bold" />复制编号和查询码</button>
            <span aria-live="polite">{copyStatus}</span>
          </div>
        </section>
      )}

      <section className="help-feedback-tracker" aria-labelledby="feedback-tracker-title">
        <div className="help-list-heading">
          <div><p className="eyebrow">TRACKING</p><h2 id="feedback-tracker-title">查询反馈进度</h2></div>
        </div>
        <form className="help-feedback-lookup" onSubmit={submitLookup}>
          <label>反馈编号<input value={lookup.feedbackNo} onChange={(event) => setLookup((current) => ({ ...current, feedbackNo: event.target.value }))} placeholder="FB-20260804-XXXXXXXXXXXX" autoComplete="off" required /></label>
          <label>查询码<input type="password" value={lookup.lookupCode} onChange={(event) => setLookup((current) => ({ ...current, lookupCode: event.target.value }))} placeholder="提交成功时获得的查询码" autoComplete="off" required /></label>
          <button type="submit" disabled={Boolean(lookupBusy)}><MagnifyingGlass weight="bold" />{lookupBusy ? "查询中" : "查询进度"}</button>
        </form>
        {lookupError && <p className="help-feedback-error" role="alert">{lookupError}</p>}
        {lookupResult && <FeedbackCard feedback={lookupResult} />}
      </section>

      {signedIn ? (
        <section className="help-feedback-history" aria-labelledby="my-feedback-title">
          <div className="help-list-heading">
            <div><p className="eyebrow">MY FEEDBACK</p><h2 id="my-feedback-title">我的反馈</h2></div>
            <button type="button" disabled={historyBusy} onClick={loadMine}><ArrowClockwise weight="bold" />{historyBusy ? "加载中" : "刷新"}</button>
          </div>
          {historyError && <p className="help-feedback-error" role="alert">{historyError}</p>}
          {!historyBusy && !historyError && mine.length === 0 && <p className="help-feedback-empty">当前账号还没有提交过反馈。</p>}
          <div className="help-feedback-cards">{mine.map((feedback) => <FeedbackCard key={feedback.feedbackNo} feedback={feedback} />)}</div>
        </section>
      ) : savedReceipts.length > 0 && (
        <section className="help-feedback-history" aria-labelledby="saved-feedback-title">
          <div className="help-list-heading"><div><p className="eyebrow">SAVED</p><h2 id="saved-feedback-title">本机保存的反馈</h2></div></div>
          <p className="help-feedback-history__lead">这些查询凭据仅保存在当前浏览器。你可以随时刷新单条反馈的处理进度。</p>
          <div className="help-feedback-cards">
            {savedReceipts.map(({ feedback, lookupCode }) => (
              <FeedbackCard key={feedback.feedbackNo} feedback={feedback} lookupCode={lookupCode} busy={lookupBusy === feedback.feedbackNo} onRefresh={runLookup} />
            ))}
          </div>
        </section>
      )}
    </main>
  );
}
