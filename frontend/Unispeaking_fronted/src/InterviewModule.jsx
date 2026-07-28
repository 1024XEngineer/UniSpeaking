import { useEffect, useRef, useState } from "react";
import {
  ArrowLeft,
  ArrowRight,
  BookOpenText,
  Briefcase,
  CaretDown,
  CaretRight,
  Check,
  CheckCircle,
  FilePdf,
  Pause,
  Play,
  SquaresFour,
  Subtitles,
  UploadSimple,
  WarningCircle,
  X,
} from "@phosphor-icons/react";
import { teachers } from "./data.js";
import { NewtonsCradle } from "./NewtonsCradle.jsx";
import { IeltsHeader, SimpleCta, TrainingCta, TrendLineChart } from "./IeltsModule.jsx";
import { paths } from "./router.js";

const cx = (...parts) => parts.filter(Boolean).join(" ");

const interviewHistory = [
  { id: "product-manager", role: "Product Manager", company: "Consumer Technology", date: "今天 14:20", duration: "18:42", score: 82, status: "报告已生成", focus: "结构化表达" },
  { id: "growth", role: "Growth Strategy", company: "SaaS", date: "7 月 19 日", duration: "14:08", score: 76, status: "报告已生成", focus: "语法控制" },
  { id: "operations", role: "Global Operations", company: "E-commerce", date: "7 月 16 日", duration: "20:00", score: null, status: "部分结果", focus: "发音可懂度" },
];

const dimensions = [
  { label: "流利度", weight: "30%", score: 84, note: "大部分回答衔接自然，个别长句出现自我修正。" },
  { label: "逻辑与连贯", weight: "25%", score: 86, note: "能够先给结论，再用背景、行动和结果展开。" },
  { label: "语法控制", weight: "20%", score: 76, note: "时态整体稳定，复杂句中的冠词仍需留意。" },
  { label: "发音可懂度", weight: "15%", score: 82, note: "关键词重音清楚，语速加快时尾音略弱。" },
  { label: "词汇与面试表达", weight: "10%", score: 80, note: "业务词汇准确，可以进一步减少重复使用 “help”。" },
];

const transcript = [
  { who: "AI 面试官", text: "Could you walk me through a product decision you made with incomplete information?" },
  { who: "你", text: "In my last internship, our activation rate dropped after a redesign. I first separated new and returning users, then interviewed five users before deciding what to change." },
  { who: "AI 面试官", text: "What trade-off did you have to make, and how did you communicate it to the team?" },
  { who: "你", text: "We chose to postpone a visual improvement and fix onboarding clarity first. I showed the team the funnel data and explained why this was the fastest way to reduce risk." },
];

const interviewQuestions = [
  "Could you walk me through a product decision you made with incomplete information?",
  "What trade-off did you have to make, and how did you communicate it to the team?",
  "What did you learn from the result, and what would you do differently next time?",
];

function InterviewButton({ children, onClick, variant = "primary", disabled = false, type = "button", icon }) {
  return <button type={type} className={cx("interview-button", `interview-button--${variant}`)} onClick={onClick} disabled={disabled}><span>{children}</span>{icon || (variant === "primary" && <ArrowRight weight="bold" />)}</button>;
}

function InterviewGradientButton({ children, className, ...props }) {
  return <button className={cx("expanding-cta", "teacher-gradient-cta", "ielts-training-cta", "interview-gradient-cta", className)} {...props}><span>{children}</span><ArrowRight weight="bold" /></button>;
}

function InterviewHeader({ title, subtitle, onBack, action, compact = false, hideEyebrow = false }) {
  return <header className={cx("interview-header", compact && "is-compact")}><div>{onBack && <button className="interview-back" onClick={onBack}><ArrowLeft />返回</button>}{!hideEyebrow && <p className="interview-eyebrow">REALTIME INTERVIEW</p>}<h1>{title}</h1>{subtitle && <p>{subtitle}</p>}</div>{action}</header>;
}

function InterviewInput({ onStart, onAssets, onBack }) {
  const [resume, setResume] = useState(null);
  const [jd, setJd] = useState("");
  const [duration, setDuration] = useState("15");
  const [error, setError] = useState("");
  const valid = resume && jd.trim().length >= 30;
  const chooseFile = (event) => {
    const file = event.target.files?.[0];
    if (!file) return;
    const extension = file.name.split(".").pop()?.toLowerCase();
    if (!["pdf", "doc", "docx"].includes(extension) || file.size > 10 * 1024 * 1024) {
      setResume(null);
      setError("请上传 10MB 以内的 PDF、DOC 或 DOCX 文件。");
      return;
    }
    setResume(file);
    setError("");
  };
  const submit = (event) => {
    event.preventDefault();
    if (!valid) { setError("请上传简历，并补充完整的目标岗位说明。"); return; }
    onStart();
  };
  return <main className="interview-page interview-input-page">
    <InterviewHeader hideEyebrow onBack={onBack} title="英文模拟面试" subtitle="用真实经历回答真实问题，只评估你的英语口语表现。" action={<SimpleCta className="ielts-home-assets-cta" onClick={onAssets}>查看学习资产</SimpleCta>} />
    <section className="interview-input-intro"><div><span>一次完整模拟</span><h2>把你的经历，练成清楚可信的英文回答</h2></div><dl><div><dt>01</dt><dd>上传简历</dd></div><div><dt>02</dt><dd>补充目标岗位</dd></div><div><dt>03</dt><dd>开始实时面试</dd></div></dl></section>
    <form className="interview-material-form" onSubmit={submit}>
      <section className="interview-form-section">
        <header><span>01</span><div><h2>上传简历</h2><p>用于生成与你真实经历相关的问题，不会在页面展示完整分析内容。</p></div></header>
        <label className={cx("interview-upload", resume && "has-file")}>
          <input type="file" accept=".pdf,.doc,.docx" onChange={chooseFile} />
          {resume ? <><span><FilePdf weight="duotone" /></span><div><strong>{resume.name}</strong><small>{(resume.size / 1024 / 1024).toFixed(1)} MB · 已准备</small></div><button type="button" aria-label="移除简历" onClick={(event) => { event.preventDefault(); setResume(null); }}><X /></button></> : <><span><UploadSimple /></span><div><strong>选择简历文件</strong><small>支持 PDF、DOC、DOCX，最大 10MB</small></div><em>浏览文件</em></>}
        </label>
      </section>
      <section className="interview-form-section">
        <header><span>02</span><div><h2>目标岗位 JD</h2><p>粘贴岗位职责和任职要求，至少 30 个字符。</p></div></header>
        <div className="interview-jd-field"><textarea value={jd} onChange={(event) => setJd(event.target.value)} maxLength={5000} placeholder="粘贴目标岗位的职位描述，例如岗位职责、核心能力和团队背景……" /><span>{jd.length} / 5000</span></div>
      </section>
      <section className="interview-form-section interview-duration-section">
        <header><span>03</span><div><h2>面试时长</h2><p>到达时长后，系统会自然结束当前问题并生成报告。</p></div></header>
        <div className="interview-duration-options">{["10", "15", "20"].map((item) => <button type="button" key={item} className={duration === item ? "is-active" : ""} onClick={() => setDuration(item)}><strong>{item}</strong><span>分钟</span>{item === "15" && <small>推荐</small>}</button>)}</div>
      </section>
      {error && <p className="interview-form-error"><WarningCircle weight="fill" />{error}</p>}
      <footer className="interview-form-footer"><p><CheckCircle weight="fill" />仅用于本次面试训练；结束后按服务规则清理临时材料。</p><InterviewGradientButton type="submit" disabled={!valid}>开始模拟面试</InterviewGradientButton></footer>
    </form>
  </main>;
}

function InterviewPreparing({ onReady, onBack }) {
  const steps = ["正在读取简历", "正在生成面试场景", "正在连接面试官", "准备完成"];
  const [index, setIndex] = useState(0);
  useEffect(() => {
    if (index >= steps.length - 1) { const done = window.setTimeout(onReady, 900); return () => window.clearTimeout(done); }
    const timer = window.setTimeout(() => setIndex((value) => value + 1), 850);
    return () => window.clearTimeout(timer);
  }, [index, onReady]);
  return <main className="interview-page interview-preparing-page"><button className="interview-back" onClick={onBack}><ArrowLeft />返回修改材料</button><section><NewtonsCradle size={64} label={steps[index]} className="interview-main-loader" /><p className="interview-eyebrow">PREPARING YOUR INTERVIEW</p><h1>{steps[index]}</h1><p>正在安全地准备本次实时口语面试，请保持当前页面打开。</p><ol>{steps.map((step, stepIndex) => <li key={step} className={cx(stepIndex < index && "is-done", stepIndex === index && "is-active")}><span>{stepIndex < index ? <Check weight="bold" /> : stepIndex + 1}</span><strong>{step}</strong>{stepIndex === index && <NewtonsCradle size={24} label={`${step}中`} />}</li>)}</ol><small>不会在页面显示简历分析、内部提示词或评分规则</small></section></main>;
}

function InterviewLive({ onEnd }) {
  const [questionIndex, setQuestionIndex] = useState(0);
  const [subtitles, setSubtitles] = useState(true);
  const [exitOpen, setExitOpen] = useState(false);
  const [remaining, setRemaining] = useState(120);
  const interviewer = teachers[3];

  useEffect(() => {
    const timer = window.setInterval(() => setRemaining((value) => Math.max(0, value - 1)), 1000);
    return () => window.clearInterval(timer);
  }, [questionIndex]);

  const nextQuestion = () => {
    if (questionIndex >= interviewQuestions.length - 1) onEnd();
    else {
      setQuestionIndex((value) => value + 1);
      setRemaining(120);
    }
  };
  const time = `${String(Math.floor(remaining / 60)).padStart(2, "0")}:${String(remaining % 60).padStart(2, "0")}`;

  return <main className={cx("conversation", "call", "ielts-call", subtitles && "call--subtitles", "interview-live-unified")}>
    <div className="conversation__top ielts-call-top">
      <div><strong>英文模拟面试</strong><span>产品经理 · 第 {questionIndex + 1} / {interviewQuestions.length} 题</span></div>
      <button className="round-control ielts-call-exit" onClick={() => setExitOpen(true)} aria-label="退出面试"><X /></button>
    </div>
    <section className="call__stage">
      <div className={cx("call-presence", subtitles && "call-presence--compact")}>
        <div className={cx("portrait", subtitles ? "portrait--small" : "portrait--call", "ielts-call-portrait")}><img src={interviewer.image} alt={interviewer.name} /></div>
        <div className={cx("listening-state", subtitles && "listening-state--compact")}>
          <span className={cx("voice-wave", subtitles && "voice-wave--compact", "is-fallback")} aria-hidden="true">
            {[.28, .52, .78, 1, .72, .48, .3].map((level, index) => <i key={index} className="voice-wave__bar" style={{ "--rest-level": level }} />)}
          </span>
          <time className="call-presence__time">{time}</time>
          {!subtitles && <span>{interviewer.name} 正在提问</span>}
        </div>
      </div>
      {subtitles && <div className="transcript ielts-call-transcript" aria-label="面试官问题字幕"><article className="transcript__line"><small>{interviewer.name}</small><p>{interviewQuestions[questionIndex]}</p></article></div>}
    </section>
    <footer className="call-controls ielts-call-controls">
      <span className="ielts-recording-label"><i className="recording-dot" />正在录音</span>
      <div className="ielts-call-control-buttons">
        <button className={cx("round-control", subtitles && "is-on")} onClick={() => setSubtitles(!subtitles)} aria-label={subtitles ? "关闭字幕" : "开启字幕"}><Subtitles /></button>
        <button className="round-control round-control--end" onClick={nextQuestion} aria-label={questionIndex >= interviewQuestions.length - 1 ? "完成面试" : "结束本题并进入下一题"}><ArrowRight weight="bold" /></button>
      </div>
      <p className="ielts-call-hint">{questionIndex >= interviewQuestions.length - 1 ? "回答完成后，点击按钮结束本次面试" : "回答完当前问题后，点击按钮进入下一题"}</p>
    </footer>
    {exitOpen && <div className="ielts-dialog-backdrop"><section className="ielts-dialog"><h2>退出当前面试？</h2><p>本次未完成的面试不会生成口语表现报告。</p><div><button onClick={() => setExitOpen(false)}>继续面试</button><button onClick={onEnd}>结束并退出</button></div></section></div>}
  </main>;
}

function InterviewFinalizing({ onHome, onReport }) {
  return <main className="ielts-page ielts-pending interview-finalizing-unified">
    <section><NewtonsCradle label="正在分析面试报告" /><p>ANALYSIS IN PROGRESS</p><h1>本次面试已保存</h1><p>AI 正在分析你的回答结构、语言表达与沟通清晰度。你可以先离开，报告完成后会出现在学习资产中。</p><div className="ielts-pending-actions"><button className="ielts-pending-home" onClick={onHome}>返回训练中心</button><button className="ielts-pending-report" onClick={onReport}>浏览本次报告<ArrowRight weight="bold" /></button></div></section>
  </main>;
}

function InterviewReport({ partial = false, transcriptOnly = false, onHome, onAssets, onBack }) {
  if (transcriptOnly) return <main className="interview-page interview-report-page"><InterviewHeader compact onBack={onBack} title="完整问答记录" subtitle="本页保留面试中的原始完整字幕，不添加纠错或评分标记。" /><section className="interview-full-transcript">{transcript.map((item, index) => <article key={`${item.who}-${index}`}><span>{String(index + 1).padStart(2, "0")}</span><div><small>{item.who}</small><p>{item.text}</p></div></article>)}</section></main>;
  return <main className="ielts-page ielts-report ielts-report--single interview-report-unified">
    <IeltsHeader onBack={onHome} title="英文模拟面试 · 本次表现" subtitle="报告只反映本次英语口语表现，不代表岗位匹配或录用判断。" action={<button className="ielts-report-assets" onClick={onAssets}><BookOpenText />查看学习资产</button>} />
    <section className="ielts-report-summary interview-total-score"><div><span>口语综合分数</span><h2>{partial ? "—" : "82"}<small>{partial ? "结果收集中" : "/100"}</small></h2><p>{partial ? "综合分需等待全部维度完成。" : "表达清楚可信，能够先给结论，再用背景、行动和结果展开。"}</p></div><div><span>本次概况</span><ol><li>面试时长 18:42</li><li>完成 6 道问题</li><li>重点提升：语法控制</li></ol></div></section>
    <section className="ielts-score-list interview-five-score-list"><h2>五项能力反馈</h2>{dimensions.map((item, index) => { const missing = partial && index > 2; return <article key={item.label}><strong>{item.label}</strong><span className="ielts-score-value">{missing ? "—" : item.score}<small>{missing ? "待生成" : "/100"}</small></span><p>{missing ? "该维度仍在生成中。" : item.note}</p></article>; })}</section>
  </main>;
}

function InterviewFailure({ kind, onRetry, onHome }) {
  const reportFailed = kind === "report-failed";
  return <main className="interview-page interview-failure"><section><WarningCircle weight="duotone" /><p className="interview-eyebrow">{reportFailed ? "REPORT FAILED" : "CONNECTION INTERRUPTED"}</p><h1>{reportFailed ? "本次面试已完成，但报告生成失败" : "面试连接异常"}</h1><p>{reportFailed ? "本次会话已经安全结束，无法从原会话重新生成报告。" : "麦克风和计时已停止。原会话已进入清理流程，无法直接恢复。"}</p><div><InterviewButton variant="secondary" onClick={onHome}>返回首页</InterviewButton><InterviewButton onClick={onRetry}>重新开始一次面试</InterviewButton></div><small>错误信息不会包含简历正文、内部提示词或系统异常堆栈。</small></section></main>;
}

export function InterviewTrainingCenter({ route, onNavigate, onExit, onAssets }) {
  const screen = route?.screen || "input";
  if (screen === "preparing") return <InterviewPreparing onBack={() => onNavigate(paths.interview.root)} onReady={() => onNavigate(paths.interview.live)} />;
  if (screen === "live") return <InterviewLive onEnd={() => onNavigate(paths.interview.finalizing)} />;
  if (screen === "finalizing") return <InterviewFinalizing onHome={() => onNavigate(paths.interview.root)} onReport={() => onNavigate(paths.interview.report)} />;
  if (screen === "report") return <InterviewReport partial={route?.result === "partial"} onHome={() => onNavigate(paths.interview.root)} onAssets={onAssets} />;
  if (screen === "transcript") return <InterviewReport transcriptOnly onBack={() => onNavigate(paths.interview.report)} />;
  if (["error", "report-failed"].includes(screen)) return <InterviewFailure kind={screen} onHome={() => onNavigate(paths.interview.root)} onRetry={() => onNavigate(paths.interview.root)} />;
  return <InterviewInput onStart={() => onNavigate(paths.interview.preparing)} onAssets={onAssets} onBack={onExit} />;
}

const interviewAssetRecords = interviewHistory.map((item, index) => ({
  ...item,
  scores: index === 0 ? [84, 86, 76, 82] : index === 1 ? [78, 80, 72, 77] : [75, 73, 70, 79],
}));

function InterviewAssetsOverview({ onTab }) {
  const activity = [
    { day: "周四", minutes: 12 },
    { day: "周五", minutes: 18 },
    { day: "周六", minutes: 0 },
    { day: "周日", minutes: 20 },
    { day: "周一", minutes: 14 },
    { day: "周二", minutes: 16 },
    { day: "今天", minutes: 19 },
  ];
  return <section className="ielts-overview-dashboard">
    <section className="ielts-asset-hero">
      <div><span>最近一次完整面试</span><h2>口语表现 82</h2><p>产品经理 · AI 口语训练评估，不代表岗位匹配或录用判断</p></div>
      <div><span>重点提升项</span><strong>语法控制</strong><small>复杂句准确度</small></div>
      <TrainingCta className="ielts-asset-gradient-action" onClick={() => onTab("trends")}>查看能力趋势</TrainingCta>
    </section>
    <section className="ielts-weekly-activity">
      <header><div><span>近七天训练时长</span><h2>99 <small>分钟</small></h2><p>共完成 6 次训练，较上周增加 21 分钟</p></div><div className="ielts-weekly-stats"><p><strong>6</strong><small>活跃天数</small></p><p><strong>17</strong><small>日均分钟</small></p><p><strong>3</strong><small>岗位覆盖</small></p></div></header>
      <div className="ielts-weekly-bars">{activity.map((item) => <span key={item.day}><i style={{ height: `${Math.max(6, (item.minutes / 20) * 100)}%` }} className={item.minutes === 0 ? "is-empty" : ""} /><strong>{item.minutes}</strong><small>{item.day}</small></span>)}</div>
    </section>
    <section className="ielts-asset-recent"><header><h2>最近训练</h2></header>{interviewAssetRecords.map((item) => <article key={item.id}><span>面试</span><div><strong>{item.role}</strong><small>{item.date} · {item.duration}</small></div><p>{item.score ? `口语表现 ${item.score}` : "部分结果"}</p></article>)}</section>
  </section>;
}

function InterviewRecordingToggle() {
  const [playing, setPlaying] = useState(false);
  return <label className="ielts-recording-toggle" title={playing ? "暂停录音" : "播放录音"}>
    <input type="checkbox" checked={playing} onChange={(event) => setPlaying(event.target.checked)} />
    <Play className="play" weight="fill" />
    <Pause className="pause" weight="fill" />
    <span>{playing ? "暂停录音" : "播放录音"}</span>
  </label>;
}

function InterviewAssetsHistory({ onOpen, onTraining }) {
  const [selected, setSelected] = useState(interviewAssetRecords[0]);
  const scoreLabels = ["流利度", "逻辑与连贯", "语法控制", "发音可懂度"];
  return <section className="ielts-history-layout">
    <aside><header><h2>面试记录</h2><span>{interviewAssetRecords.length} 条</span></header>{interviewAssetRecords.map((item) => <button key={item.id} className={selected.id === item.id ? "is-active" : ""} onClick={() => setSelected(item)}><small>{item.date} · 英文面试</small><strong>{item.role}</strong><span>{item.duration}</span></button>)}</aside>
    <article>
      <header><div><span>{selected.company}</span><h2>{selected.role}</h2><p>{selected.date} · 用时 {selected.duration}</p></div><div className="ielts-history-media-actions"><InterviewRecordingToggle key={selected.id} /></div></header>
      <section className="ielts-history-summary"><span>总体报告</span><h3>{selected.score ? `口语表现 ${selected.score}` : "部分结果"}</h3><p>本次回答整体清楚，能够先给结论再解释判断依据；下一步优先改善复杂句中的语法稳定性。</p></section>
      <section className="ielts-history-scores"><span>四项能力评分</span><div>{scoreLabels.map((label, index) => <p key={label}><small>{label}</small><strong>{selected.scores[index]}<em>/100</em></strong></p>)}</div></section>
      <footer><small>录音与本次总体报告将保留在面试记录中</small><button className="ielts-history-report" onClick={() => onOpen(selected.id)}>查看总体报告</button><TrainingCta className="ielts-history-practice" onClick={onTraining}>快速复练</TrainingCta></footer>
    </article>
  </section>;
}

function InterviewAssetsTrends() {
  const scoreLabels = ["流利度", "逻辑与连贯", "语法控制", "发音可懂度"];
  const averages = [81, 84, 74, 82];
  return <section className="ielts-trends-dashboard">
    <section className="ielts-trend-summary"><div><span>面试表现趋势</span><h2>82</h2><p>最近 5 次训练提升 8 分</p></div><div className="ielts-trend-chart-wrap"><TrendLineChart values={[5.6, 5.9, 6.1, 6.3, 6.5]} /><small>第 1 次</small><small>最近一次</small></div><div><span>训练状态</span><strong>连续 6 天</strong><p>本周已完成 3 次训练</p></div></section>
    <section className="ielts-dimension-trends"><h2>四项能力平均分</h2>{scoreLabels.map((label, index) => <article key={label}><span>{label}</span><strong className="ielts-dimension-score">{averages[index]}<small>/100</small></strong><div><i style={{ width: `${averages[index]}%` }} /></div><strong>{["稳定", "优势", "优先提升", "稳定"][index]}</strong></article>)}</section>
    <section className="ielts-part-trends"><article><span>结构表达</span><strong>结论前置更稳定</strong><p>近 4 次回答中，STAR 结构完整度提升 31%。</p></article><article><span>语言控制</span><strong>复杂句仍需稳定</strong><p>建议减少过长句，并加强冠词与时态检查。</p></article><article><span>面试表达</span><strong>业务词汇更准确</strong><p>取舍、协作与结果类表达已形成可复用模板。</p></article></section>
  </section>;
}

export function InterviewAssets({ route, onNavigate, onBackToAssets, onIeltsAssets, onTraining }) {
  const availableTabs = ["overview", "history", "trends"];
  const tab = availableTabs.includes(route?.tab) ? route.tab : "overview";
  const setTab = (nextTab) => onNavigate(nextTab === "overview" ? paths.interview.assets.root : paths.interview.assets[nextTab]);
  const tabs = [{ id: "overview", label: "概览" }, { id: "history", label: "面试记录" }, { id: "trends", label: "能力趋势" }];
  const tabRef = useRef(null);
  const tabButtons = useRef({});
  const [tabIndicator, setTabIndicator] = useState({ x: 0, width: 0, ready: false });
  const openReport = (id) => onNavigate(paths.interview.assets.record(id));

  useEffect(() => {
    const updateIndicator = () => {
      const activeButton = tabButtons.current[tab];
      if (!tabRef.current || !activeButton) return;
      setTabIndicator({ x: activeButton.offsetLeft, width: activeButton.offsetWidth, ready: true });
    };
    updateIndicator();
    window.addEventListener("resize", updateIndicator);
    return () => window.removeEventListener("resize", updateIndicator);
  }, [tab]);

  if (route?.record) return <InterviewReport partial={route.record === "operations"} onHome={() => onNavigate(paths.interview.assets.history)} onAssets={() => onNavigate(paths.interview.assets.root)} />;
  const otherAssetsButton = <div className="asset-module-menu ielts-other-assets"><button className="asset-module-menu__trigger" type="button" aria-label="切换学习资产模块" aria-haspopup="menu"><SquaresFour weight="bold" /><span>其他资产</span><CaretDown weight="bold" /></button><div className="asset-module-menu__popover" role="menu"><button type="button" role="menuitem" onClick={onBackToAssets}><BookOpenText /><span><strong>场景训练学习资产</strong><small>对话记录、纠错与场景复练</small></span><CaretRight /></button><button type="button" role="menuitem" onClick={onIeltsAssets}><span className="asset-module-ielts-mark">IELTS</span><span><strong>IELTS 学习资产</strong><small>专项训练、模考与能力趋势</small></span><CaretRight /></button></div></div>;
  return <main className={cx("ielts-page", "ielts-assets", "interview-assets-unified", tab === "overview" && "ielts-assets--overview", tab === "trends" && "ielts-assets--trends")}>
    <IeltsHeader title="英文面试学习资产" subtitle="集中查看每次面试记录、总体报告与原始录音。" action={<div className="ielts-assets-actions">{otherAssetsButton}<SimpleCta className="ielts-assets-header-cta" onClick={onTraining}>返回训练中心</SimpleCta></div>} />
    <nav className="ielts-asset-tabs" ref={tabRef}><span className={cx("ielts-asset-tab-indicator", tabIndicator.ready && "is-ready")} style={{ width: tabIndicator.width, transform: `translateX(${tabIndicator.x}px)` }} />{tabs.map((item) => <button ref={(node) => { tabButtons.current[item.id] = node; }} key={item.id} className={tab === item.id ? "is-active" : ""} onClick={() => setTab(item.id)}>{item.label}</button>)}</nav>
    {tab === "overview" && <InterviewAssetsOverview onTab={setTab} />}
    {tab === "history" && <InterviewAssetsHistory onOpen={openReport} onTraining={onTraining} />}
    {tab === "trends" && <InterviewAssetsTrends />}
  </main>;
}
