import { useEffect, useRef, useState } from "react";
import {
  ArrowLeft,
  ArrowRight,
  Briefcase,
  BookOpenText,
  CaretDown,
  CaretRight,
  Check,
  MagnifyingGlass,
  NotePencil,
  Pause,
  Play,
  Shuffle,
  SquaresFour,
  Subtitles,
  X,
} from "@phosphor-icons/react";
import { NewtonsCradle } from "./NewtonsCradle.jsx";
import {
  createIeltsSceneFlow,
  fetchAuthenticatedMedia,
  generateIeltsScene,
  generateIeltsEvaluation,
  getIeltsEvaluationHistory,
  getIeltsSettings,
  getIeltsTopics,
  getIeltsTraining,
  updateIeltsSettings,
} from "./apiClient.js";
import { createRealtimeClient } from "./realtimeClient.js";
import { paths } from "./router.js";

const cx = (...parts) => parts.filter(Boolean).join(" ");

const apiPart = {
  p1: "PART_1",
  p2: "PART_2",
  p3: "PART_3",
};

const partMeta = {
  p1: { number: "01", label: "Part 1", title: "日常问答", duration: "2–4 分钟", note: "单话题 · 4–5 道问题" },
  p2: { number: "02", label: "Part 2", title: "长陈述", duration: "3–4 分钟", note: "1 分钟准备 · 2 分钟陈述" },
  p3: { number: "03", label: "Part 3", title: "深入讨论", duration: "4–5 分钟", note: "关联话题与独立分类" },
  mock: { number: "模考", label: "全真模考", title: "全真模考", duration: "11–14 分钟", note: "随机考官 · 随机题目" },
};

const ieltsExaminers = [
  { id: "daniel", voiceId: "Harvey", name: "Daniel", accent: "英式口音", personality: "严谨沉稳", image: "/examiner/daniel.png", offsetX: 0, intro: "节奏稳定，追问清晰，适合提前熟悉正式考场氛围。" },
  { id: "marcus", voiceId: "Aiden", name: "Marcus", accent: "美式口音", personality: "清晰直接", image: "/examiner/marcus.png", offsetX: 5.2, intro: "表达清楚有力，会用自然追问帮助你快速进入回答状态。" },
  { id: "margaret", voiceId: "Mione", name: "Margaret", accent: "英式口音", personality: "从容细致", image: "/examiner/margaret.png", offsetX: 2.2, intro: "语速从容、停顿自然，适合练习完整展开与细节组织。" },
  { id: "sophia", voiceId: "Maia", name: "Sophia", accent: "澳式口音", personality: "自然友好", image: "/examiner/sophia.png", offsetX: 5.6, intro: "交流感自然但流程严格，适合降低紧张感并保持实战节奏。" },
];

const IELTS_INTAKE_STORAGE_KEY = "unispeaking.ielts.intake.v1";
const ieltsIntakeSteps = [
  {
    id: "target",
    eyebrow: "YOUR GOAL",
    title: "这次备考，你希望达到多少分？",
    lead: "先定一个目标，我们会据此安排专项训练与模考节奏。",
    options: [
      { id: "6.0", title: "目标 6.0", note: "优先保证回答完整、清楚" },
      { id: "6.5", title: "目标 6.5", note: "加强展开、连贯与词汇变化" },
      { id: "7.0", title: "目标 7.0", note: "提升自然度、准确性与表达深度" },
      { id: "7.5", title: "目标 7.5+", note: "追求稳定、灵活且有层次的表达" },
    ],
  },
  {
    id: "current",
    eyebrow: "YOUR STARTING POINT",
    title: "你目前的口语，大约在哪个阶段？",
    lead: "不用精确估分，选择最接近的状态即可。",
    options: [
      { id: "starter", title: "还没参加过考试", note: "从答题结构和开口习惯开始" },
      { id: "5.0", title: "约 5.0 或以下", note: "能回答问题，但容易停顿或内容较短" },
      { id: "5.5", title: "约 5.5–6.0", note: "表达基本完整，需要提升自然度" },
      { id: "6.5", title: "约 6.5 或以上", note: "重点突破准确性与观点深度" },
    ],
  },
];

function loadIeltsIntakeProfile() {
  try {
    const value = window.localStorage.getItem(IELTS_INTAKE_STORAGE_KEY);
    return value ? JSON.parse(value) : null;
  } catch {
    return null;
  }
}

export function SimpleCta({ children, onClick, className, disabled = false, type = "button" }) {
  return <button type={type} className={cx("ielts-cta", className)} onClick={onClick} disabled={disabled}><span>{children}</span><ArrowRight weight="bold" /></button>;
}

export function TrainingCta({ children, onClick, className, disabled = false, type = "button" }) {
  return <button type={type} className={cx("expanding-cta", "teacher-gradient-cta", "ielts-training-cta", className)} onClick={onClick} disabled={disabled}><span>{children}</span><ArrowRight weight="bold" /></button>;
}

export function IeltsHeader({ title, subtitle, onBack, action, leadAction }) {
  return (
    <header className="ielts-page-header">
      <div>{onBack && <button className="ielts-back" onClick={onBack}><ArrowLeft />返回</button>}{leadAction}<h1>{title}</h1>{subtitle && <p>{subtitle}</p>}</div>
      {action}
    </header>
  );
}

function IeltsIntake({ onComplete, initialProfile, onCancel = null }) {
  const [stepIndex, setStepIndex] = useState(0);
  const [answers, setAnswers] = useState(() => initialProfile || {});
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const step = ieltsIntakeSteps[stepIndex];
  const selected = answers[step.id];
  const isLastStep = stepIndex === ieltsIntakeSteps.length - 1;
  const selectOption = (value) => setAnswers((current) => ({ ...current, [step.id]: value }));
  const continueFlow = async () => {
    if (!selected) return;
    if (isLastStep) {
      setSaving(true);
      setError("");
      try {
        await onComplete(answers);
      } catch (requestError) {
        setError(requestError?.message || "目标分数保存失败");
      } finally {
        setSaving(false);
      }
    }
    else setStepIndex((value) => value + 1);
  };

  return (
    <main className="setup-page ielts-intake">
      <header><span>IELTS SPEAKING · 轻问询</span><span>{stepIndex + 1} / {ieltsIntakeSteps.length}</span></header>
      <section className="setup-card">
        <p className="eyebrow">{step.eyebrow}</p>
        <h1>{step.title}</h1>
        <p className="setup-lead">{step.lead}</p>
        <div className="level-options">
          {step.options.map((option, index) => (
            <button key={option.id} className={cx("level-option", selected === option.id && "is-selected")} onClick={() => selectOption(option.id)}>
              <span className="level-option__number">0{index + 1}</span>
              <span><strong>{option.title}</strong><small>{option.note}</small></span>
              {selected === option.id && <Check weight="bold" />}
            </button>
          ))}
        </div>
        <div className="ielts-intake-actions">
          {stepIndex > 0
            ? <button className="ielts-intake-back" onClick={() => setStepIndex((value) => value - 1)}><ArrowLeft />上一步</button>
            : onCancel && <button className="ielts-intake-back" onClick={onCancel}><ArrowLeft />返回训练中心</button>}
          <TrainingCta disabled={!selected || saving} onClick={continueFlow}>{saving ? "正在保存…" : isLastStep ? "进入训练中心" : "下一步"}</TrainingCta>
        </div>
        {error && <p className="ielts-topic-state is-error">{error}</p>}
      </section>
    </main>
  );
}

function formatBand(value) {
  if (value == null || value === "") return "--";
  const score = Number(value);
  return Number.isFinite(score) ? score.toFixed(1) : "--";
}

function IeltsHome({ onChoose, onAssets, onEditGoal, onBack, settings }) {
  const target = formatBand(settings?.targetScore);
  const latestEstimatedScore = formatBand(settings?.latestEstimatedScore);
  const todayCompletedCount = Number(settings?.todayCompletedCount || 0);
  return (
    <main className="ielts-page ielts-home">
      <IeltsHeader
        onBack={onBack}
        title="IELTS 口语特训"
        subtitle="实战训练，持续复盘，看见进步"
        action={(
          <div className="ielts-home-actions">
            <button className="ielts-report-assets" onClick={onEditGoal}><NotePencil />调整目标</button>
            <SimpleCta className="ielts-home-assets-cta" onClick={onAssets}>查看学习资产</SimpleCta>
          </div>
        )}
      />
      <section className="ielts-goal-row" aria-label="备考目标">
        <div><span>目标</span><strong>{target}</strong></div>
        <div><span>最近模考预估</span><strong>{latestEstimatedScore}</strong></div>
        <div><span>今日特训</span><strong>{todayCompletedCount} <small>/ 5</small></strong></div>
      </section>

      <section className="ielts-mock-feature">
        <div><span>全真模考</span><h2>完整模拟一场 IELTS 口语考试</h2><p>随机考官 · 随机题目 · 完整能力报告</p></div>
        <div className="ielts-mock-feature__meta"><span>预计用时</span><strong>11–14 分钟</strong><small>开始后不可暂停</small></div>
        <TrainingCta onClick={() => onChoose("mock", "browse")}>开始模考</TrainingCta>
      </section>

      <section className="ielts-quick-start">
        <p>快速开始训练</p>
        <div className="ielts-part-grid">
          {["p1", "p2", "p3"].map((id) => {
            const item = partMeta[id];
            return (
            <article key={id} className="ielts-part-card">
              <span className="ielts-part-number">{item.number}</span>
              <small>{item.label}</small>
              <h2>{item.title}</h2>
              <p>{item.duration} · {item.note}</p>
              <TrainingCta onClick={() => onChoose(id, "browse")}>开始练习</TrainingCta>
            </article>
          );})}
        </div>
      </section>

    </main>
  );
}

function TopicBrowser({ part, onBack, onStart }) {
  const pageSize = 10;
  const [category, setCategory] = useState("ALL");
  const [query, setQuery] = useState("");
  const [page, setPage] = useState(1);
  const [topics, setTopics] = useState([]);
  const [categories, setCategories] = useState([]);
  const [total, setTotal] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const filterRef = useRef(null);
  const filterButtons = useRef({});
  const [filterIndicator, setFilterIndicator] = useState({ x: 0, width: 0, ready: false });
  const filters = [{ code: "ALL", label: "全部" }, ...categories];
  const categoryKey = filters.map((item) => item.code).join("|");
  const pageNumbers = Array.from({ length: totalPages }, (_, index) => index + 1);
  const practiceTypeLabel = (value) => ({
    MOCK_TEST: "模考练习",
    RANDOM_PART_PRACTICE: "随机专项练习",
    SELECTED_PART_PRACTICE: "指定专项练习",
  }[value] || "未练习");

  useEffect(() => {
    let cancelled = false;
    const timer = window.setTimeout(async () => {
      setLoading(true);
      setError("");
      try {
        const result = await getIeltsTopics({
          part: apiPart[part],
          category,
          keyword: query,
          page,
          pageSize,
        });
        if (cancelled) return;
        setCategories(Array.isArray(result?.categories) ? result.categories : []);
        setTopics(Array.isArray(result?.topics) ? result.topics : []);
        setTotal(Number(result?.total) || 0);
        setTotalPages(Number(result?.totalPages) || 0);
      } catch (requestError) {
        if (cancelled) return;
        setTopics([]);
        setTotal(0);
        setTotalPages(0);
        setError(requestError?.message || "雅思题库加载失败");
      } finally {
        if (!cancelled) setLoading(false);
      }
    }, 250);
    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [part, category, query, page]);

  useEffect(() => {
    const updateIndicator = () => {
      const activeButton = filterButtons.current[category];
      if (!filterRef.current || !activeButton) return;
      setFilterIndicator({ x: activeButton.offsetLeft, width: activeButton.offsetWidth, ready: true });
    };
    updateIndicator();
    window.addEventListener("resize", updateIndicator);
    return () => window.removeEventListener("resize", updateIndicator);
  }, [category, categoryKey]);

  return (
    <main className="ielts-page ielts-topics">
      <IeltsHeader onBack={onBack} title={`${partMeta[part].label} · ${partMeta[part].title}`} subtitle="选择一个话题，正式开始后才会由考官揭晓具体问题。" action={<SimpleCta className="ielts-random-cta" onClick={() => onStart(null, true)}><Shuffle />随机练习</SimpleCta>} />
      <section className="ielts-topic-tools">
        <div className="ielts-topic-filters" ref={filterRef}>
          <span className={cx("ielts-topic-filter-indicator", filterIndicator.ready && "is-ready")} style={{ width: filterIndicator.width, transform: `translateX(${filterIndicator.x}px)` }} />
          {filters.map((item) => <button ref={(node) => { filterButtons.current[item.code] = node; }} key={item.code} className={category === item.code ? "is-active" : ""} onClick={() => { setCategory(item.code); setPage(1); }}>{item.label}</button>)}
        </div>
        <label className="ielts-topic-search">
          <MagnifyingGlass aria-hidden="true" />
          <input className="ielts-topic-search__input" aria-label="搜索话题" value={query} onChange={(event) => { setQuery(event.target.value); setPage(1); }} placeholder="搜索话题" />
          <button type="button" className="ielts-topic-search__reset" aria-label="清空搜索" onClick={() => { setQuery(""); setPage(1); }}><X /></button>
        </label>
      </section>
      <section className="ielts-topic-list">
        <header><span>话题</span><span>练习记录</span><span>最近表现</span><span /></header>
        {loading && <p className="ielts-topic-state">正在读取题库…</p>}
        {!loading && error && <p className="ielts-topic-state is-error">{error}</p>}
        {!loading && !error && topics.length === 0 && <p className="ielts-topic-state">没有找到相关话题</p>}
        {!loading && !error && topics.map((topic) => <button key={topic.id} onClick={() => onStart(topic, false)}><span><small>{topic.categoryLabel}</small><strong>{topic.title}</strong><em>{topic.questionCount} 道问题</em></span><span><strong>{practiceTypeLabel(topic.latestPracticeType)}</strong><em>{topic.practiceCount > 0 ? `共 ${topic.practiceCount} 次 · 模考 ${topic.mockTestCount || 0} 次` : "暂无训练记录"}</em></span><span>{topic.practiceCount > 0 ? <><strong>{topic.latestPerformanceScore == null ? "已完成" : `${formatBand(topic.latestPerformanceScore)} 分`}</strong><em>{topic.latestPerformanceSummary || reportDate(topic.lastPracticedAt)}</em></> : "未练习"}</span><CaretRight /></button>)}
      </section>
      {!loading && !error && totalPages > 0 && (
        <nav className="ielts-topic-pagination" aria-label="题库分页">
          <span>共 {total} 个话题</span>
          <div className="ielts-topic-pagination__controls">
            <button type="button" disabled={page <= 1} onClick={() => setPage((value) => Math.max(1, value - 1))}><ArrowLeft />上一页</button>
            <div className="ielts-topic-pagination__pages" aria-label={`共 ${totalPages} 页`}>
              {pageNumbers.map((pageNumber) => (
                <button
                  type="button"
                  key={pageNumber}
                  className={pageNumber === page ? "is-active" : ""}
                  aria-current={pageNumber === page ? "page" : undefined}
                  aria-label={`第 ${pageNumber} 页`}
                  onClick={() => setPage(pageNumber)}
                >
                  {pageNumber}
                </button>
              ))}
            </div>
            <button type="button" disabled={page >= totalPages} onClick={() => setPage((value) => Math.min(totalPages, value + 1))}>下一页<ArrowRight /></button>
          </div>
        </nav>
      )}
    </main>
  );
}

function ExaminerSwipeStack({ selected, onSelect }) {
  const [order, setOrder] = useState(ieltsExaminers);
  const [drag, setDrag] = useState({ active: false, startX: 0, startY: 0, x: 0, y: 0 });
  const suppressCardClick = useRef(false);

  const showNextExaminer = () => {
    const next = order[1] || order[0];
    setOrder((current) => [...current.slice(1), current[0]]);
    onSelect(next);
  };

  const selectExaminer = (id) => {
    setOrder((current) => {
      const index = current.findIndex((item) => item.id === id);
      return index <= 0 ? current : [...current.slice(index), ...current.slice(0, index)];
    });
    onSelect(ieltsExaminers.find((item) => item.id === id));
  };

  const endDrag = () => {
    const distance = Math.hypot(drag.x, drag.y);
    suppressCardClick.current = distance > 50;
    if (suppressCardClick.current) {
      showNextExaminer();
      window.setTimeout(() => { suppressCardClick.current = false; }, 0);
    }
    setDrag({ active: false, startX: 0, startY: 0, x: 0, y: 0 });
  };

  return (
    <div className="ielts-examiner-picker">
      <div className="ielts-examiner-stack-stage" aria-label="考官选择">
        <div className="ielts-examiner-stack">
          {order.map((item, index) => {
            const isTop = index === 0;
            const stackProgress = order.length > 1 ? index / (order.length - 1) : 0;
            const x = isTop ? drag.x : stackProgress * 200;
            const y = isTop ? drag.y : -index * 8;
            const rotate = isTop ? drag.x / 18 : stackProgress * -45;
            const scale = isTop && drag.active ? 1.035 : 1 - index * .05;
            return (
              <article
                key={item.id}
                className={cx("ielts-examiner-card", isTop && "is-top", isTop && drag.active && "is-dragging")}
                style={{ zIndex: order.length - index, transform: `translate3d(${x}px, ${y}px, ${index * -10}px) rotate(${rotate}deg) scale(${scale})` }}
                role="button"
                tabIndex={0}
                aria-label={isTop ? `拖动切换考官，当前 ${item.name}` : `切换到 ${item.name}`}
                onPointerDown={isTop ? (event) => { event.currentTarget.setPointerCapture(event.pointerId); setDrag({ active: true, startX: event.clientX, startY: event.clientY, x: 0, y: 0 }); } : undefined}
                onPointerMove={isTop && drag.active ? (event) => setDrag((current) => ({ ...current, x: event.clientX - current.startX, y: event.clientY - current.startY })) : undefined}
                onPointerUp={isTop ? endDrag : undefined}
                onPointerCancel={isTop ? endDrag : undefined}
                onClick={!isTop ? () => {
                  if (suppressCardClick.current) {
                    suppressCardClick.current = false;
                    return;
                  }
                  selectExaminer(item.id);
                } : undefined}
                onKeyDown={(event) => {
                  if (isTop && (event.key === "ArrowRight" || event.key === " ")) {
                    event.preventDefault();
                    showNextExaminer();
                  } else if (!isTop && (event.key === "Enter" || event.key === " ")) {
                    event.preventDefault();
                    selectExaminer(item.id);
                  }
                }}
              >
                <img src={item.image} alt={`${item.name} AI 考官`} draggable="false" style={{ "--examiner-offset-x": `${item.offsetX}%` }} />
                <footer><div><strong>{item.name}</strong><span>{item.accent} · {item.personality}</span></div>{isTop && <Check weight="bold" />}</footer>
              </article>
            );
          })}
        </div>
      </div>
      <aside className="ielts-examiner-detail">
        <p className="eyebrow">YOUR EXAMINER</p>
        <h2>{selected.name}</h2>
        <span>{selected.accent} · {selected.personality}</span>
        <p>{selected.intro}</p>
      </aside>
    </div>
  );
}

function DeviceSetup({ part, topic, random, onBack, onStart }) {
  const [examiner, setExaminer] = useState(ieltsExaminers[0]);
  const [mockExaminer] = useState(() => ieltsExaminers[Math.floor(Math.random() * ieltsExaminers.length)]);
  const [starting, setStarting] = useState(false);
  const [startError, setStartError] = useState("");
  const isMock = part === "mock";
  const startPractice = async () => {
    setStarting(true);
    setStartError("");
    try {
      await onStart(isMock ? mockExaminer : examiner);
    } catch (requestError) {
      setStartError(requestError?.message || "训练启动失败");
      setStarting(false);
    }
  };
  return (
    <main className="ielts-page ielts-setup">
      <IeltsHeader onBack={onBack} title={isMock ? "准备全真模考" : `准备 ${partMeta[part].label} 训练`} subtitle={random ? "本次题目将在正式开始后随机揭晓。" : `已选择话题：${topic?.title}`} />
      {isMock ? (
        <section className="ielts-mock-setup-overview">
          <div>
            <span>FULL MOCK TEST</span>
            <h2>完整模拟真实口试流程</h2>
            <p>开始后，系统将随机分配考官与题目，并依次完成 Part 1、Part 2 和 Part 3。</p>
          </div>
          <dl>
            <div><dt>考试环节</dt><dd>Part 1–3</dd><small>完整口试流程</small></div>
            <div><dt>预计用时</dt><dd>11–14 分钟</dd><small>开始后不可暂停</small></div>
            <div><dt>考官与题目</dt><dd>系统随机分配</dd><small>开始后正式揭晓</small></div>
          </dl>
        </section>
      ) : (
        <section className="ielts-examiner-select"><header><span>请选择考官</span><p>参考真实考试节奏，选择一位与你完成本次训练的考官。</p></header><ExaminerSwipeStack selected={examiner} onSelect={setExaminer} /></section>
      )}
      <footer className="ielts-setup-footer"><p><strong>{isMock ? "开始后不可暂停，中途退出本次模考将作废并消耗 1 次额度。" : "开始后不可暂停或静音，本次训练将消耗 1 次特训额度。"}</strong><small>每天最多完成 5 次</small>{startError && <em>{startError}</em>}</p><TrainingCta className="ielts-confirm-cta" disabled={starting} onClick={startPractice}>{starting ? "正在准备…" : "确认并开始"}</TrainingCta></footer>
    </main>
  );
}

function formatTime(seconds) {
  const safe = Math.max(0, seconds);
  return `${String(Math.floor(safe / 60)).padStart(2, "0")}:${String(safe % 60).padStart(2, "0")}`;
}

function IeltsEvaluationWaiting() {
  return (
    <section className="ielts-evaluation-waiting" role="status" aria-live="polite" aria-label="正在生成 IELTS 评分">
      <div className="ielts-evaluation-loader" aria-hidden="true">
        <span className="ielts-evaluation-loader__circle" />
        <span className="ielts-evaluation-loader__circle" />
        <span className="ielts-evaluation-loader__circle" />
        <span className="ielts-evaluation-loader__shadow" />
        <span className="ielts-evaluation-loader__shadow" />
        <span className="ielts-evaluation-loader__shadow" />
      </div>
      <p>IELTS EVALUATION</p>
      <h1>正在生成评分</h1>
      <span>正在整理本次回答与四项能力反馈，请稍候。</span>
    </section>
  );
}

function IeltsConversationSession({ part, examiner, training, generated, onExit, onComplete, deferEvaluation = false }) {
  const isPartTwo = part === "p2";
  const isPartThree = part === "p3";
  const [subtitles, setSubtitles] = useState(isPartTwo);
  const [status, setStatus] = useState("正在连接考官…");
  const [messages, setMessages] = useState([]);
  const [elapsed, setElapsed] = useState(0);
  const [error, setError] = useState("");
  const [ending, setEnding] = useState(false);
  const [exitOpen, setExitOpen] = useState(false);
  const [partTwoPhase, setPartTwoPhase] = useState(isPartTwo ? "INTRODUCTION" : null);
  const [partTwoRemaining, setPartTwoRemaining] = useState(isPartTwo ? 60 : null);
  const [partTwoNotes, setPartTwoNotes] = useState("");
  const [partThreeRemaining, setPartThreeRemaining] = useState(null);
  const remoteAudioRef = useRef(null);
  const clientRef = useRef(null);
  const finishRef = useRef(null);
  const sessionIdRef = useRef(null);
  const partTwoPhaseRef = useRef(isPartTwo ? "INTRODUCTION" : null);
  const partTwoTimerRef = useRef(null);
  const partTwoCompletionTimerRef = useRef(null);
  const partTwoSilenceTimerRef = useRef(null);
  const partThreeTimerRef = useRef(null);
  const partThreeTimerActiveRef = useRef(false);
  const transcriptRef = useRef(null);
  const subtitleQueueRef = useRef([]);
  const subtitleFrameRef = useRef(null);

  const partTwoQuestion = generated?.content?.part2?.[0] || training?.questions?.[0] || null;
  const partTwoQuestionText = partTwoQuestion?.question || partTwoQuestion?.questionText || "";
  const partTwoCuePoints = partTwoQuestion?.cue_points
    || partTwoQuestion?.cuePoints
    || [];

  const updatePartTwoPhase = (nextPhase) => {
    partTwoPhaseRef.current = nextPhase;
    setPartTwoPhase(nextPhase);
  };

  const clearPartTwoTimer = () => {
    if (partTwoTimerRef.current) {
      window.clearInterval(partTwoTimerRef.current);
      partTwoTimerRef.current = null;
    }
  };

  const clearPartTwoCompletionTimer = () => {
    if (!partTwoCompletionTimerRef.current) return;
    window.clearTimeout(partTwoCompletionTimerRef.current);
    partTwoCompletionTimerRef.current = null;
  };

  const clearPartTwoSilenceTimer = () => {
    if (!partTwoSilenceTimerRef.current) return;
    window.clearTimeout(partTwoSilenceTimerRef.current);
    partTwoSilenceTimerRef.current = null;
  };

  const schedulePartTwoFinish = (delayMs) => {
    if (!isPartTwo || partTwoPhaseRef.current !== "FINISHING") return;
    clearPartTwoCompletionTimer();
    partTwoCompletionTimerRef.current = window.setTimeout(() => {
      partTwoCompletionTimerRef.current = null;
      void finishRef.current?.();
    }, delayMs);
  };

  const runPartTwoTimer = (seconds, onElapsed) => {
    clearPartTwoTimer();
    let remaining = seconds;
    setPartTwoRemaining(remaining);
    partTwoTimerRef.current = window.setInterval(() => {
      remaining -= 1;
      setPartTwoRemaining(Math.max(0, remaining));
      if (remaining > 0) return;
      clearPartTwoTimer();
      onElapsed();
    }, 1000);
  };

  const clearPartThreeTimer = () => {
    partThreeTimerActiveRef.current = false;
    if (partThreeTimerRef.current) {
      window.clearInterval(partThreeTimerRef.current);
      partThreeTimerRef.current = null;
    }
    setPartThreeRemaining(null);
  };

  const runPartThreeTimer = (client = clientRef.current) => {
    clearPartThreeTimer();
    let remaining = 60;
    partThreeTimerActiveRef.current = true;
    setPartThreeRemaining(remaining);
    partThreeTimerRef.current = window.setInterval(() => {
      remaining -= 1;
      setPartThreeRemaining(Math.max(0, remaining));
      if (remaining > 0) return;
      clearPartThreeTimer();
      setStatus("单题回答已到 60 秒，考官正在切换下一题");
      void client?.forceIeltsPart3TurnTimeout().catch((timeoutError) => {
        setError(timeoutError?.message || "Part 3 切换下一题失败");
      });
    }, 1000);
  };

  const beginPartTwoAnswer = (client = clientRef.current) => {
    if (!client || partTwoPhaseRef.current !== "PREPARATION") return;
    clearPartTwoTimer();
    clearPartTwoSilenceTimer();
    updatePartTwoPhase("STARTING");
    setStatus("准备结束，考官即将提示开始");
    void client.transitionIeltsPart2("PREPARATION_COMPLETE")
      .catch((transitionError) => {
        setError(transitionError?.message || "无法开始 Part 2 作答");
        updatePartTwoPhase("PREPARATION");
      });
  };

  const finishPartTwoAtLimit = (client = clientRef.current) => {
    if (!client || partTwoPhaseRef.current !== "LONG_TURN") return;
    clearPartTwoTimer();
    clearPartTwoSilenceTimer();
    updatePartTwoPhase("FINISHING");
    setStatus("作答时间已到，正在结束 Part 2");
    void client.transitionIeltsPart2("LONG_TURN_TIME_LIMIT")
      .catch((transitionError) => {
        setError(transitionError?.message || "无法结束 Part 2");
      });
  };

  const finishPartTwoAfterSilence = (client = clientRef.current) => {
    if (!client || partTwoPhaseRef.current !== "LONG_TURN") return;
    clearPartTwoTimer();
    clearPartTwoSilenceTimer();
    updatePartTwoPhase("FINISHING");
    setStatus("检测到较长停顿，正在结束 Part 2");
    void client.transitionIeltsPart2("ANSWER_COMPLETE")
      .catch((transitionError) => {
        setError(transitionError?.message || "无法结束 Part 2");
        updatePartTwoPhase("LONG_TURN");
      });
  };

  const schedulePartTwoSilenceFinish = (client = clientRef.current) => {
    if (!client || partTwoPhaseRef.current !== "LONG_TURN") return;
    clearPartTwoSilenceTimer();
    // Provider VAD emits speech_stopped after roughly three seconds of
    // silence. Waiting three more seconds yields an effective six-second
    // continuous-silence threshold while still allowing natural pauses.
    partTwoSilenceTimerRef.current = window.setTimeout(() => {
      partTwoSilenceTimerRef.current = null;
      finishPartTwoAfterSilence(client);
    }, 3_000);
  };

  const updateLiveMessage = ({ id, owner, delta = "", text = "", final = false }) => {
    if (owner !== 0) return;
    const content = String(text || delta || "");
    if (!content) return;
    setMessages((current) => {
      const messageId = id || `${owner}-live`;
      const exactIndex = current.findIndex((message) => message.id === messageId);
      const fallbackIndex = final
        ? current.findLastIndex((message) => message.owner === owner && !message.final)
        : -1;
      const index = exactIndex >= 0 ? exactIndex : fallbackIndex;
      if (index < 0) return [{ id: messageId, owner, text: content, final }];
      const next = current.filter((message) => message.owner === 0).slice(-1);
      const nextIndex = next.findIndex((message) => message.id === current[index].id);
      if (nextIndex < 0) return [{ id: messageId, owner, text: content, final }];
      next[nextIndex] = {
        ...next[nextIndex],
        id: messageId,
        text: text || `${next[nextIndex].text}${delta}`,
        final,
      };
      return next;
    });
  };

  const queueLiveMessage = (message) => {
    subtitleQueueRef.current.push(message);
    if (subtitleFrameRef.current != null) return;
    subtitleFrameRef.current = window.requestAnimationFrame(() => {
      subtitleFrameRef.current = null;
      const queued = subtitleQueueRef.current.splice(0);
      queued.forEach(updateLiveMessage);
    });
  };

  const flushSubtitleQueue = () => {
    if (subtitleFrameRef.current != null) {
      window.cancelAnimationFrame(subtitleFrameRef.current);
      subtitleFrameRef.current = null;
    }
    const queued = subtitleQueueRef.current.splice(0);
    queued.forEach(updateLiveMessage);
  };

  useEffect(() => {
    const frame = window.requestAnimationFrame(() => {
      if (transcriptRef.current) {
        transcriptRef.current.scrollTop = transcriptRef.current.scrollHeight;
      }
    });
    return () => window.cancelAnimationFrame(frame);
  }, [messages]);

  useEffect(() => {
    if (!generated?.ieltsId) return undefined;
    let cancelled = false;
    const client = createRealtimeClient({
      sceneId: generated.ieltsId,
      sceneType: "ielts",
      onEvent: (event) => {
        if (cancelled) return;
        if (event.type === "local.connecting") setStatus("正在连接考官…");
        else if (event.type === "local.connected") {
          setStatus(isPartTwo ? "考官正在说明 Part 2 准备要求" : "考试进行中");
          if (isPartTwo) client.setMuted(true);
        }
        else if (event.type === "input_audio_buffer.speech_started") {
          clearPartTwoSilenceTimer();
          setStatus("正在聆听你的回答");
        }
        else if (event.type === "input_audio_buffer.speech_stopped") {
          if (isPartTwo) schedulePartTwoSilenceFinish(client);
        }
        else if (event.type === "response.created") {
          setMessages([]);
          setStatus(`${examiner.name} 正在提问`);
        }
        else if (event.type === "local.ielts_input_ready") {
          setStatus("请开始回答");
          if (isPartTwo && partTwoPhaseRef.current === "STARTING") {
            updatePartTwoPhase("LONG_TURN");
            runPartTwoTimer(120, () => finishPartTwoAtLimit(client));
          } else if (isPartThree && !partThreeTimerActiveRef.current) {
            runPartThreeTimer(client);
          }
        }
        else if (event.type === "response.done") {
          if (isPartTwo && partTwoPhaseRef.current === "INTRODUCTION") {
            updatePartTwoPhase("PREPARATION");
            setStatus("准备时间 · 60 秒");
            runPartTwoTimer(60, () => beginPartTwoAnswer(client));
          } else if (!isPartTwo) {
            setStatus("请开始回答");
          }
        }
        else if (event.type === "local.transcript.final") {
          flushSubtitleQueue();
          const suppressPartTwoClosingFragment = isPartTwo
            && partTwoPhaseRef.current === "FINISHING"
            && event.owner === 0;
          if (!suppressPartTwoClosingFragment && event.owner === 0) {
            updateLiveMessage({
              id: event.itemId,
              owner: event.owner,
              text: event.text,
              final: true,
            });
          }
          if (event.owner === 0
            && /that is the end of (part 1|part 2|the speaking test)/i.test(event.text || "")) {
            if (isPartTwo) schedulePartTwoFinish(1_800);
            else window.setTimeout(() => { void finishRef.current?.(); }, 1_800);
          }
        } else if (event.type === "response.audio_transcript.delta") {
          if (!(isPartTwo && partTwoPhaseRef.current === "FINISHING")) {
            queueLiveMessage({
              id: event.item_id || event.response_id || "ielts-assistant-live",
              owner: 0,
              delta: event.delta || event.text || "",
            });
          }
        } else if (event.type === "conversation.item.input_audio_transcription.completed") {
          if (isPartThree) clearPartThreeTimer();
        } else if (event.type === "local.ielts_part2_state") {
          if (event.state?.completed) {
            clearPartTwoTimer();
            clearPartTwoSilenceTimer();
            updatePartTwoPhase("FINISHING");
            setStatus("Part 2 已完成，考官正在结束本部分");
            updateLiveMessage({
              id: "ielts-part-two-closing",
              owner: 0,
              text: "Thank you. That is the end of Part 2.",
              final: true,
            });
            // Provider output may be interrupted or may not include the full
            // closing transcript. The state machine remains the source of
            // truth and this fallback prevents FINISHING from hanging.
            schedulePartTwoFinish(10_000);
          }
        } else if (event.type === "local.ielts_part2_completion_ready") {
          schedulePartTwoFinish(1_400);
        } else if (event.type === "local.ielts_state") {
          const state = event.state;
          if (state?.completed) {
            clearPartThreeTimer();
            client.setMuted(true);
          }
          setStatus(state?.completed
            ? `${partMeta[part].label} 已完成，考官正在结束本部分`
            : `已完成 ${state?.answeredQuestions || 0} / ${state?.totalQuestions || 0} 题`);
        } else if (event.type === "local.ielts_state_error") {
          setError(event.message || "IELTS 题目状态推进失败");
        } else if (event.type === "local.backend_warning") {
          setError("会话记录保存失败，请稍后重试");
        } else if (event.type === "error" || event.type === "local.error") {
          setError(event.message || event.error?.message || "实时会话发生错误");
          setStatus("连接异常");
        }
      },
      onRemoteStream: (stream) => {
        if (cancelled || !remoteAudioRef.current) return;
        remoteAudioRef.current.srcObject = stream;
        void remoteAudioRef.current.play().catch(() => setStatus("点击页面后可播放考官声音"));
      },
    });
    clientRef.current = client;
    void client.start({ voice: generated.voiceId || examiner.voiceId })
      .then((started) => {
        sessionIdRef.current = started?.sessionId || null;
      })
      .catch((startError) => {
        if (!cancelled) setError(startError?.message || "无法开始 IELTS 实时会话");
      });
    return () => {
      cancelled = true;
      clearPartTwoTimer();
      clearPartTwoCompletionTimer();
      clearPartTwoSilenceTimer();
      clearPartThreeTimer();
      if (subtitleFrameRef.current != null) {
        window.cancelAnimationFrame(subtitleFrameRef.current);
        subtitleFrameRef.current = null;
      }
      subtitleQueueRef.current = [];
      clientRef.current = null;
      void client.stop({ notifyBackend: false, reason: "component_unmount", emitEnded: false });
    };
  }, [generated?.ieltsId, generated?.voiceId, examiner.id]);

  useEffect(() => {
    if (ending) return undefined;
    const timer = window.setInterval(() => setElapsed((value) => value + 1), 1000);
    return () => window.clearInterval(timer);
  }, [ending]);

  const finish = async () => {
    if (ending) return;
    setEnding(true);
    setError("");
    clearPartTwoTimer();
    clearPartTwoCompletionTimer();
    clearPartTwoSilenceTimer();
    clearPartThreeTimer();
    try {
      setStatus("正在结束本次练习…");
      const client = clientRef.current;
      const backgroundEvaluationReady = deferEvaluation
        ? client?.waitForEvaluations()
        : null;
      await client?.stop({
        reason: "user_stop",
        awaitEvaluations: !deferEvaluation,
      });
      clientRef.current = null;
      if (deferEvaluation) {
        const completedSessionId = sessionIdRef.current;
        void Promise.resolve(backgroundEvaluationReady)
          .then(() => completedSessionId
            ? generateIeltsEvaluation(generated.ieltsId, completedSessionId)
            : null)
          .catch((evaluationError) => {
            console.warn("Background IELTS Part evaluation failed", evaluationError);
          });
        onComplete(null);
        return;
      }
      let evaluation = null;
      if (sessionIdRef.current) {
        setStatus("正在生成 IELTS 评分…");
        try {
          evaluation = await generateIeltsEvaluation(
            generated.ieltsId,
            sessionIdRef.current,
          );
        } catch (evaluationError) {
          // Ending a session is valid even when the candidate stops during
          // preparation or has not produced enough speech to be scored.
          console.warn("IELTS evaluation was unavailable after session end", evaluationError);
        }
      }
      onComplete(evaluation);
    } catch (stopError) {
      console.error("Failed to end IELTS session", stopError);
      setStatus("结束失败");
      setError("结束练习失败，请稍后重试");
      setEnding(false);
    }
  };
  finishRef.current = finish;

  const abandon = async () => {
    const client = clientRef.current;
    clientRef.current = null;
    await client?.stop({ notifyBackend: false, reason: "user_exit", emitEnded: false });
    onExit();
  };

  const latestExaminerMessage = [...messages].reverse().find((message) => message.owner === 0)?.text;
  const visibleMessages = messages.filter((message) => message.owner === 0).slice(-1);
  const partTwoNotesEditable = isPartTwo
    && ["INTRODUCTION", "PREPARATION"].includes(partTwoPhase);
  const partTwoPhaseLabel = partTwoPhase === "LONG_TURN"
    ? `作答时间 ${formatTime(partTwoRemaining)}`
    : partTwoPhase === "PREPARATION" || partTwoPhase === "INTRODUCTION"
      ? `准备时间 ${formatTime(partTwoRemaining)}`
      : partTwoPhase === "STARTING"
        ? "考官正在提示开始"
        : "正在结束 Part 2";
  const exitDialog = exitOpen && <div className="ielts-dialog-backdrop"><section className="ielts-dialog"><h2>退出当前训练？</h2><p>本次未完成训练不会计入今日完成次数，也不会生成专项报告。</p><div><button onClick={() => setExitOpen(false)}>继续训练</button><button onClick={() => void abandon()}>确认退出</button></div></section></div>;

  return (
    <main className={cx("conversation", "call", "ielts-call", subtitles && "call--subtitles", isPartTwo && "ielts-call--part-two")}>
      <audio ref={remoteAudioRef} autoPlay />
      <div className="conversation__top ielts-call-top">
        <div><strong>{`${partMeta[part].label} · ${partMeta[part].title}`}</strong><span>{ending ? "考试计时已停止，正在生成评分" : isPartTwo ? partTwoPhaseLabel : "考官会根据你的回答自动推进考试"}</span></div>
        <button className="round-control ielts-call-exit" disabled={ending} onClick={() => setExitOpen(true)} aria-label="退出训练"><X /></button>
      </div>
      <section className="call__stage">
        <div className={cx("call-presence", subtitles && "call-presence--compact")}>
          <div className={cx("portrait", subtitles ? "portrait--small" : "portrait--call", "ielts-call-portrait")}><img src={examiner.image} alt={examiner.name} style={{ "--examiner-offset-x": `${examiner.offsetX}%` }} /></div>
          <div className={cx("listening-state", subtitles && "listening-state--compact")}>
            <span className={cx("voice-wave", subtitles && "voice-wave--compact", "is-fallback")} aria-hidden="true">
              {[.28, .52, .78, 1, .72, .48, .3].map((level, index) => <i key={index} className="voice-wave__bar" style={{ "--rest-level": level }} />)}
            </span>
            <time className="call-presence__time">{isPartTwo ? formatTime(partTwoRemaining) : isPartThree && partThreeRemaining != null ? formatTime(partThreeRemaining) : formatTime(elapsed)}</time>
            {!subtitles && <span>{status}</span>}
          </div>
        </div>
        {subtitles && <div ref={transcriptRef} className="transcript ielts-call-transcript" aria-label="实时会话字幕">
          {visibleMessages.length ? visibleMessages.map((message) => <article key={message.id} className="transcript__line"><small>{examiner.name}</small><p>{message.text}</p></article>) : <article className="transcript__line"><small>{examiner.name}</small><p>{latestExaminerMessage || status}</p></article>}
        </div>}
        {isPartTwo && <section className="ielts-part-two-compact-material" aria-label="Part 2 题卡与笔记">
          <article className="ielts-part-two-compact-cue">
            <span>PART 2 · CUE CARD</span>
            <h1>{partTwoQuestionText}</h1>
            {partTwoCuePoints.length > 0 && <><p>You should say:</p><ul>{partTwoCuePoints.map((point) => <li key={point}>{point}</li>)}</ul></>}
          </article>
          <section className="ielts-part-two-compact-notes">
            <header><span><NotePencil />答题笔记</span><small>{partTwoNotesEditable ? "准备结束后自动锁定" : "已锁定，不可修改"}</small></header>
            <textarea
              value={partTwoNotes}
              onChange={(event) => setPartTwoNotes(event.target.value)}
              readOnly={!partTwoNotesEditable}
              placeholder="记录关键词、人物、地点、原因或例子……"
              aria-label="Part 2 答题笔记"
            />
          </section>
        </section>}
      </section>
      <footer className={cx("call-controls", "ielts-call-controls", isPartTwo && "ielts-call-controls--part-two")}>
        <span className="ielts-recording-label">{partTwoPhase === "LONG_TURN" && <i className="recording-dot" />}{error || status}</span>
        <div className="ielts-call-control-buttons">
          {!isPartTwo && <button className={cx("round-control", subtitles && "is-on")} onClick={() => setSubtitles(!subtitles)} aria-label={subtitles ? "关闭字幕" : "开启字幕"}><Subtitles /></button>}
          {isPartTwo
            ? partTwoPhase === "PREPARATION" && <button className="round-control round-control--end" disabled={ending} onClick={() => beginPartTwoAnswer()} aria-label="结束准备并开始作答"><ArrowRight weight="bold" /></button>
            : <button className="round-control round-control--end" disabled={ending} onClick={() => void finish()} aria-label="结束本次训练"><X weight="bold" /></button>}
        </div>
        <p className="ielts-call-hint">{isPartTwo
          ? partTwoPhase === "PREPARATION"
            ? "准备好后可点击下一步；进入作答后笔记将锁定。"
            : partTwoPhase === "LONG_TURN"
              ? "你有两分钟作答；时间结束后系统会立即闭麦。"
              : "请按照考官提示完成 Part 2。"
          : "无需手动切题；考官会按照 IELTS 节奏判断回答结束并继续。"}</p>
      </footer>
      {exitDialog}
      {ending && !deferEvaluation && <IeltsEvaluationWaiting />}
    </main>
  );
}

function PracticeSession(props) {
  if (props.part === "p1" || props.part === "p2" || props.part === "p3") {
    return <IeltsConversationSession {...props} />;
  }
  if (props.part === "mock") {
    return <IeltsMockSession {...props} />;
  }
  return null;
}

function IeltsMockSession({ onComplete, ...props }) {
  const parts = ["p1", "p2", "p3"];
  const [partIndex, setPartIndex] = useState(0);
  const activePart = parts[partIndex];
  const completePart = (evaluation) => {
    if (partIndex >= parts.length - 1) onComplete(evaluation);
    else setPartIndex((value) => value + 1);
  };
  return <IeltsConversationSession
    key={activePart}
    {...props}
    part={activePart}
    deferEvaluation={partIndex < parts.length - 1}
    onComplete={completePart}
  />;
}

function AnalysisPending({ evaluation, onHome, onReport }) {
  const available = Boolean(evaluation);
  const scores = evaluation ? [
    ["流利度与连贯性", evaluation.fluencyCoherenceScore],
    ["词汇资源", evaluation.lexicalResourceScore],
    ["语法多样性与准确性", evaluation.grammaticalRangeAccuracyScore],
    ["发音", evaluation.pronunciationScore],
  ] : [];
  const isFinal = evaluation?.assessmentType === "FINAL";
  return (
    <main className="ielts-page ielts-score-result-page">
      <section className="ielts-score-result-background"><span>IELTS SPEAKING</span><h1>本次练习已结束</h1><p>评分完成后会自动保存到学习资产。</p></section>
      <div className="ielts-dialog-backdrop ielts-score-dialog-backdrop">
        <section className="ielts-dialog ielts-score-dialog">
          <p className="eyebrow">{available ? "EVALUATION COMPLETE" : "INSUFFICIENT SPEECH"}</p>
          <h2>{available ? "本次评分已完成" : "有效回答不足，暂时无法评分"}</h2>
          {available ? <>
            {isFinal && <div className="ielts-score-dialog__overall"><span>完整模考预估</span><strong>{formatBand(evaluation.overallBandScore)}</strong><small>/ 9</small></div>}
            <div className="ielts-score-dialog__dimensions">{scores.map(([label, score]) => <article key={label}><span>{label}</span><strong>{formatBand(score)}<small>/9</small></strong></article>)}</div>
          </> : <p>练习已经正常保存，但需要至少完成一轮有效英文回答才能生成评分报告。</p>}
          <div><button onClick={onHome}>返回训练中心</button>{available && <button onClick={onReport}>查看详细报告</button>}</div>
        </section>
      </div>
    </main>
  );
}

function PracticeReport({ part, evaluation, onHome, onRetry, onAssets }) {
  const isMock = part === "mock";
  const reportTitle = isMock ? "全真模考 · 完整表现报告" : `${partMeta[part].label} · 本次专项表现`;
  const reportSubtitle = isMock
    ? "预估成绩仅用于训练反馈，并非雅思官方考试成绩。"
    : `这不是完整雅思口语预估分；报告只反映本次${partMeta[part].title}表现。`;
  if (!evaluation) {
    return (
      <main className={cx("ielts-page", "ielts-report", "ielts-report--single")}>
        <IeltsHeader onBack={onHome} title={reportTitle} subtitle={reportSubtitle} />
        <section className="ielts-report-summary"><div><span>暂无评分</span><h2>本次有效英文回答不足</h2><p>后端没有生成评分结果，因此这里不会展示示例分数或虚构报告。</p></div><div><span>下一步</span><ol><li>至少完成一轮完整英文回答</li><li>结束前等待最后一轮转写完成</li></ol><TrainingCta onClick={onRetry}>重新练习</TrainingCta></div></section>
      </main>
    );
  }
  const savedReason = (reason) => reason?.trim() || "该历史记录生成时尚未保存本项的具体评分理由。";
  const scoreRows = [
    { label: "流利度与连贯性", score: evaluation.fluencyCoherenceScore, note: savedReason(evaluation.fluencyCoherenceReason) },
    { label: "词汇资源", score: evaluation.lexicalResourceScore, note: savedReason(evaluation.lexicalResourceReason) },
    { label: "语法多样性与准确性", score: evaluation.grammaticalRangeAccuracyScore, note: savedReason(evaluation.grammaticalRangeAccuracyReason) },
    { label: "发音", score: evaluation.pronunciationScore, note: savedReason(evaluation.pronunciationReason) },
  ];
  const strengths = evaluation.strengths || [];
  const improvements = evaluation.improvements || [];
  const partEvaluations = evaluation.partEvaluations || [];
  const expressions = evaluation.recommendedExpressions || [];
  return (
    <main className={cx("ielts-page", "ielts-report", !isMock && "ielts-report--single")}>
      <IeltsHeader onBack={onHome} title={reportTitle} subtitle={reportSubtitle} action={<button className="ielts-report-assets" onClick={onAssets}><BookOpenText />查看学习资产</button>} />
      {isMock && <section className="ielts-mock-score"><span>本次预估</span><strong>{formatBand(evaluation.overallBandScore)}</strong><p>完整模考综合评分</p></section>}
      {isMock && <section className="ielts-report-summary"><div><span>本次结论</span><p>{evaluation.summary}</p>{strengths.length > 0 && <><span>表现优势</span><ul>{strengths.map((item) => <li key={item}>{item}</li>)}</ul></>}</div><div><span>优先改进</span>{improvements.length > 0 ? <ol>{improvements.map((item) => <li key={item}>{item}</li>)}</ol> : <p>本次评分没有返回额外改进项。</p>}</div></section>}
      <section className="ielts-score-list"><h2>四项能力反馈</h2>{scoreRows.map((row) => <article key={row.label}><strong>{row.label}</strong><span className="ielts-score-value">{formatBand(row.score)}<small>/9</small></span><p>{row.note}</p></article>)}</section>
      {isMock && partEvaluations.length > 0 && <section className="ielts-part-evaluation-list"><h2>各 Part 四项评分</h2><div>{partEvaluations.map((item) => <article key={item.part}><span>{String(item.part).replace("PART_", "Part ")}</span><div className="ielts-part-evaluation-dimensions"><p><small>流利连贯</small><strong>{formatBand(item.fluencyCoherenceScore)}</strong></p><p><small>词汇</small><strong>{formatBand(item.lexicalResourceScore)}</strong></p><p><small>语法</small><strong>{formatBand(item.grammaticalRangeAccuracyScore)}</strong></p><p><small>发音</small><strong>{formatBand(item.pronunciationScore)}</strong></p></div></article>)}</div></section>}
      {expressions.length > 0 && <section className="ielts-recommended-expression-list"><h2>本次推荐表达</h2><ol>{expressions.map((item) => <li key={item}>{item}</li>)}</ol></section>}
    </main>
  );
}

function IeltsTrainingDataState({ error, onBack, onRetry }) {
  return (
    <main className="ielts-page ielts-pending">
      <section>
        {!error && <NewtonsCradle label="正在读取雅思题库" />}
        <h1>{error ? "题目加载失败" : "正在准备本次训练"}</h1>
        <p>{error || "正在从题库读取所选话题的真实题目。"}</p>
        <div className="ielts-pending-actions">
          <button className="ielts-pending-home" onClick={onBack}>返回题库</button>
          {error && <button className="ielts-pending-report" onClick={onRetry}>重新加载</button>}
        </div>
      </section>
    </main>
  );
}

export function IeltsTrainingCenter({ route, onNavigate, onExit, onAssets }) {
  const screen = route?.screen || "home";
  const part = route?.part || "p2";
  const random = route?.selection === "random" || part === "mock";
  const [examiner, setExaminer] = useState(ieltsExaminers[0]);
  const [savedIntakeProfile] = useState(loadIeltsIntakeProfile);
  const [intakeProfile, setIntakeProfile] = useState(savedIntakeProfile);
  const [training, setTraining] = useState(null);
  const [generated, setGenerated] = useState(null);
  const [settings, setSettings] = useState(null);
  const [settingsLoading, setSettingsLoading] = useState(true);
  const [showIntake, setShowIntake] = useState(false);
  const [latestEvaluation, setLatestEvaluation] = useState(null);
  const [trainingLoading, setTrainingLoading] = useState(false);
  const [trainingError, setTrainingError] = useState("");
  const [trainingReload, setTrainingReload] = useState(0);
  const trainingKey = `${part}:${route?.selection || ""}`;

  const partSegment = (nextPart) => nextPart === "mock" ? "mock" : `part${nextPart.slice(1)}`;
  const partPath = (nextPart) => paths.ielts.part(partSegment(nextPart));
  const stepPath = (nextPart, selection, step) => paths.ielts.step(partSegment(nextPart), selection, step);

  const choosePart = (nextPart, mode) => {
    if (nextPart === "mock") { onNavigate(paths.ielts.step("mock", "random", "setup")); return; }
    if (mode === "recommended") { onNavigate(stepPath("p2", "random", "setup")); return; }
    onNavigate(partPath(nextPart));
  };
  const openSetup = (nextTopic, isRandom) => onNavigate(stepPath(part, isRandom ? "random" : nextTopic.id, "setup"));
  const start = async (nextExaminer) => {
    setLatestEvaluation(null);
    setExaminer(nextExaminer);
    await updateIeltsSettings({ examinerId: nextExaminer.id });
    const scene = await generateIeltsScene({
      mode: part === "mock" ? "MOCK_TEST" : "PART_PRACTICE",
      part: part === "mock" ? null : apiPart[part],
      topicId: part === "mock" ? null : training?.topicId || null,
    });
    await createIeltsSceneFlow(scene.ieltsId);
    setGenerated(scene);
    onNavigate(stepPath(part, route?.selection || "random", "session"));
  };
  const completeIntake = async (profile) => {
    const updatedSettings = await updateIeltsSettings({ targetScore: Number(profile.target) });
    setSettings(updatedSettings);
    setIntakeProfile(profile);
    setShowIntake(false);
    try { window.localStorage.setItem(IELTS_INTAKE_STORAGE_KEY, JSON.stringify(profile)); } catch { /* Local-only preference may be unavailable. */ }
  };

  useEffect(() => {
    let cancelled = false;
    let refreshTimer = null;
    const refreshSettings = () => getIeltsSettings().then((nextSettings) => {
      if (cancelled) return;
      setSettings(nextSettings);
      if (nextSettings?.targetScore != null) {
        setIntakeProfile((current) => ({
          target: String(nextSettings.targetScore),
          current: current?.current || "starter",
        }));
      }
      if (nextSettings?.examinerId) {
        const savedExaminer = ieltsExaminers.find((item) => item.id === nextSettings.examinerId);
        if (savedExaminer) setExaminer(savedExaminer);
      }
    }).catch(() => {
      // The intake form will surface persistence errors when the user submits it.
    }).finally(() => {
      if (!cancelled) setSettingsLoading(false);
    });
    const refreshWhenVisible = () => {
      if (document.visibilityState === "visible") void refreshSettings();
    };

    void refreshSettings();
    if (screen === "home") {
      window.addEventListener("focus", refreshSettings);
      document.addEventListener("visibilitychange", refreshWhenVisible);
      refreshTimer = window.setInterval(() => { void refreshSettings(); }, 5_000);
    }
    return () => {
      cancelled = true;
      if (refreshTimer) window.clearInterval(refreshTimer);
      window.removeEventListener("focus", refreshSettings);
      document.removeEventListener("visibilitychange", refreshWhenVisible);
    };
  }, [screen]);

  useEffect(() => {
    if (part === "mock" || !["setup", "session"].includes(screen)) return undefined;
    let cancelled = false;
    setTrainingLoading(true);
    setTrainingError("");
    getIeltsTraining(apiPart[part], random ? null : route?.selection)
      .then((result) => {
        if (!cancelled) setTraining(result);
      })
      .catch((requestError) => {
        if (cancelled) return;
        setTraining(null);
        setTrainingError(requestError?.message || "训练题目加载失败");
      })
      .finally(() => {
        if (!cancelled) setTrainingLoading(false);
      });
    return () => { cancelled = true; };
  }, [trainingKey, trainingReload]);

  const completeTraining = async (evaluation) => {
    setLatestEvaluation(evaluation || null);
    try {
      const refreshed = await getIeltsSettings();
      setSettings(refreshed);
    } catch {
      // The completed report remains available even if overview refresh fails.
    }
    onNavigate(stepPath(part, route?.selection || "random", "analysis"));
  };

  if (screen === "home" && settingsLoading) {
    return <IeltsTrainingDataState error="" onBack={onExit} onRetry={() => {}} />;
  }
  const targetConfigured = settings?.targetScore != null;
  if (screen === "home" && (showIntake || !targetConfigured)) {
    const initialProfile = {
      ...((intakeProfile?.current || savedIntakeProfile?.current)
        ? { current: intakeProfile?.current || savedIntakeProfile.current }
        : {}),
      ...(targetConfigured ? { target: String(settings.targetScore) } : {}),
    };
    return (
      <IeltsIntake
        key={`${showIntake}:${settings?.targetScore ?? "unset"}`}
        initialProfile={initialProfile}
        onComplete={completeIntake}
        onCancel={targetConfigured ? () => setShowIntake(false) : null}
      />
    );
  }
  if (screen === "topics") return <TopicBrowser part={part} onBack={() => onNavigate(paths.ielts.root)} onStart={openSetup} />;
  if (part !== "mock" && ["setup", "session"].includes(screen) && (trainingLoading || trainingError || !training)) {
    return <IeltsTrainingDataState error={trainingError} onBack={() => onNavigate(partPath(part))} onRetry={() => setTrainingReload((value) => value + 1)} />;
  }
  if (screen === "setup") return <DeviceSetup part={part} topic={training} random={random} onBack={() => onNavigate(part === "mock" ? paths.ielts.root : partPath(part))} onStart={start} />;
  if (screen === "session") return <PracticeSession part={part} examiner={examiner} training={training} generated={generated} onExit={() => onNavigate(paths.ielts.root)} onComplete={completeTraining} />;
  if (screen === "analysis") return <AnalysisPending evaluation={latestEvaluation} onHome={() => onNavigate(paths.ielts.root)} onReport={() => onNavigate(stepPath(part, route?.selection || "random", "report"))} />;
  if (screen === "report") return <PracticeReport part={part} evaluation={latestEvaluation} onHome={() => onNavigate(paths.ielts.root)} onRetry={() => onNavigate(stepPath(part, route?.selection || "random", "setup"))} onAssets={onAssets} />;
  return <IeltsHome onChoose={choosePart} onAssets={onAssets} onEditGoal={() => setShowIntake(true)} onBack={onExit} settings={settings} />;
}

function reportType(item) {
  return item.mode === "MOCK_TEST" ? "完整模考" : "专项训练";
}

function reportPartLabel(part) {
  return part ? String(part).replace("PART_", "Part ") : "Part 未知";
}

function reportTopicTitle(item, part) {
  if (item?.topicTitles?.[part]) return item.topicTitles[part];
  if (part === "PART_1") return "Everyday Topics";
  if (part === "PART_2") return "Long Turn Topic";
  if (part === "PART_3") return "Discussion Topic";
  return "IELTS Speaking";
}

function ReportTitle({ item, compact = false }) {
  const parts = item.mode === "MOCK_TEST"
    ? ["PART_1", "PART_2", "PART_3"]
    : [item.part];
  return <span className={cx("ielts-record-title", compact && "is-compact")}>{parts.map((part) => <span className="ielts-record-title__line" key={part}><span className="ielts-record-title__part">{reportPartLabel(part)} ·</span> <span className="ielts-record-title__topic">{reportTopicTitle(item, part)}</span></span>)}</span>;
}

function reportDate(value) {
  if (!value) return "时间未知";
  return new Intl.DateTimeFormat("zh-CN", { month: "short", day: "numeric", hour: "2-digit", minute: "2-digit" }).format(new Date(value));
}

function reportDuration(item) {
  const seconds = Math.max(0, Math.round((new Date(item.endedAt) - new Date(item.startedAt)) / 1000));
  if (!Number.isFinite(seconds)) return "--:--";
  return `${String(Math.floor(seconds / 60)).padStart(2, "0")}:${String(seconds % 60).padStart(2, "0")}`;
}

function reportCapabilityScore(item) {
  const values = [
    item?.fluencyCoherenceScore,
    item?.lexicalResourceScore,
    item?.grammaticalRangeAccuracyScore,
    item?.pronunciationScore,
  ].filter((value) => value != null && value !== "").map(Number).filter(Number.isFinite);
  if (!values.length) return null;
  return values.reduce((sum, value) => sum + value, 0) / values.length;
}

function reportScore(item) {
  const overall = item?.overallBandScore == null || item.overallBandScore === ""
    ? Number.NaN
    : Number(item.overallBandScore);
  return Number.isFinite(overall) ? overall : reportCapabilityScore(item);
}

function reportPerformanceLabel(item) {
  const score = reportScore(item);
  if (!Number.isFinite(score)) return "暂无评分";
  if (score >= 7) return "表现优秀";
  if (score >= 6) return "表现稳定";
  if (score >= 5) return "继续提升";
  return "重点加强";
}

function recentSevenDayActivity(reports) {
  const formatter = new Intl.DateTimeFormat("zh-CN", { weekday: "short" });
  return Array.from({ length: 7 }, (_, index) => {
    const date = new Date();
    date.setHours(0, 0, 0, 0);
    date.setDate(date.getDate() - (6 - index));
    const next = new Date(date);
    next.setDate(next.getDate() + 1);
    const minutes = reports.reduce((total, item) => {
      const endedAt = new Date(item.endedAt);
      if (endedAt < date || endedAt >= next) return total;
      const duration = (new Date(item.endedAt) - new Date(item.startedAt)) / 60_000;
      return total + (Number.isFinite(duration) ? Math.max(0, duration) : 0);
    }, 0);
    return { label: formatter.format(date), minutes: Math.round(minutes) };
  });
}

function AssetsOverview({ settings, reports, onTab }) {
  const latestMock = reports.find((item) => item.mode === "MOCK_TEST");
  const target = Number(settings?.targetScore);
  const latest = Number(latestMock?.overallBandScore);
  const gap = Number.isFinite(target) && Number.isFinite(latest) ? Math.max(0, target - latest).toFixed(1) : null;
  const activity = recentSevenDayActivity(reports);
  const maxMinutes = Math.max(1, ...activity.map((item) => item.minutes));
  const activeDays = activity.filter((item) => item.minutes > 0).length;
  const totalMinutes = activity.reduce((sum, item) => sum + item.minutes, 0);
  const partCoverage = new Set(reports.flatMap((item) => item.mode === "MOCK_TEST" ? ["PART_1", "PART_2", "PART_3"] : [item.part]).filter(Boolean)).size;
  return <section className="ielts-overview-dashboard"><section className="ielts-asset-hero"><div><span>最近一次完整模考</span><h2>{latestMock ? `预估 ${formatBand(latestMock.overallBandScore)}` : "暂无完整模考"}</h2><p>AI 训练评估，并非官方考试成绩</p></div><div><span>目标</span><strong>{formatBand(settings?.targetScore)}</strong><small>{gap == null ? "完成模考后显示差距" : gap === "0.0" ? "已达到当前目标" : `还差约 ${gap} 分`}</small></div><TrainingCta className="ielts-asset-gradient-action" onClick={() => onTab("trends")}>查看能力趋势</TrainingCta></section><section className="ielts-weekly-activity"><header><div><span>近七天训练时长</span><h2>{totalMinutes} <small>分钟</small></h2><p>今日已完成 {Number(settings?.todayCompletedCount || 0)} / 5 次 · 连续打卡 {Number(settings?.currentStreakDays || 0)} 天</p></div><div className="ielts-weekly-stats"><p><strong>{activeDays}</strong><small>活跃天数</small></p><p><strong>{activeDays ? Math.round(totalMinutes / activeDays) : 0}</strong><small>日均分钟</small></p><p><strong>{partCoverage}</strong><small>专项覆盖</small></p></div></header><div className="ielts-weekly-bars">{activity.map((item) => <span key={item.label}><i className={item.minutes ? "" : "is-empty"} style={{ height: `${Math.max(item.minutes ? 10 : 4, (item.minutes / maxMinutes) * 100)}%` }} /><strong>{item.minutes}</strong><small>{item.label}</small></span>)}</div></section><section className="ielts-asset-recent"><header><h2>最近训练</h2></header>{reports.length ? reports.slice(0, 3).map((item) => <article key={item.sessionId}><span>{reportType(item)}</span><div><strong><ReportTitle item={item} compact /></strong><small>{reportDate(item.endedAt)} · {reportDuration(item)}</small></div><p>{reportPerformanceLabel(item)}</p></article>) : <div className="ielts-history-empty"><BookOpenText /><h2>暂无评分记录</h2><p>完成一次有效训练后，后端报告会显示在这里。</p></div>}</section></section>;
}

function AssetsHistory({ items }) {
  const [selectedId, setSelectedId] = useState(items[0]?.sessionId || null);
  const [recordingPlaying, setRecordingPlaying] = useState(false);
  const recordingRef = useRef(null);
  const recordingObjectUrlRef = useRef(null);
  const recordingGenerationRef = useRef(0);
  const selected = items.find((item) => item.sessionId === selectedId) || items[0] || null;
  useEffect(() => {
    setRecordingPlaying(false);
    return () => {
      recordingGenerationRef.current += 1;
      recordingRef.current?.pause();
      recordingRef.current = null;
      if (recordingObjectUrlRef.current) {
        URL.revokeObjectURL(recordingObjectUrlRef.current);
        recordingObjectUrlRef.current = null;
      }
    };
  }, [selectedId]);
  const toggleRecording = () => {
    if (!selected?.recordingUrls?.length) return;
    if (recordingRef.current && recordingPlaying) {
      recordingGenerationRef.current += 1;
      recordingRef.current.pause();
      setRecordingPlaying(false);
      return;
    }
    const generation = recordingGenerationRef.current + 1;
    recordingGenerationRef.current = generation;
    let clipIndex = 0;
    const releaseObjectUrl = () => {
      if (!recordingObjectUrlRef.current) return;
      URL.revokeObjectURL(recordingObjectUrlRef.current);
      recordingObjectUrlRef.current = null;
    };
    const playClip = async () => {
      try {
        const blob = await fetchAuthenticatedMedia(selected.recordingUrls[clipIndex]);
        if (recordingGenerationRef.current !== generation) return;
        releaseObjectUrl();
        const objectUrl = URL.createObjectURL(blob);
        recordingObjectUrlRef.current = objectUrl;
        const audio = new Audio(objectUrl);
        recordingRef.current = audio;
        audio.onended = () => {
          if (recordingGenerationRef.current !== generation) return;
          releaseObjectUrl();
          clipIndex += 1;
          if (clipIndex < selected.recordingUrls.length) void playClip();
          else { recordingRef.current = null; setRecordingPlaying(false); }
        };
        audio.onerror = () => {
          releaseObjectUrl();
          recordingRef.current = null;
          setRecordingPlaying(false);
        };
        await audio.play();
        setRecordingPlaying(true);
      } catch {
        releaseObjectUrl();
        recordingRef.current = null;
        setRecordingPlaying(false);
      }
    };
    void playClip();
  };
  return (
    <section className="ielts-history-layout">
      <aside>
        <header><h2>训练记录</h2><span>{items.length} 条</span></header>
        {items.map((item) => <button key={item.sessionId} className={selected?.sessionId === item.sessionId ? "is-active" : ""} onClick={() => setSelectedId(item.sessionId)}><span className="ielts-history-record-meta"><time>{reportDate(item.endedAt)}</time><em>{reportType(item)}</em></span><strong><ReportTitle item={item} compact /></strong><span className="ielts-history-record-duration">{reportDuration(item)}</span></button>)}
      </aside>
      {selected ? <article>
        <header>
          <div><span className="ielts-history-report-kind">{reportType(selected)} · {selected.topicSelectionMethod === "RANDOM" ? "随机练习" : "选题练习"}</span><h2><ReportTitle item={selected} /></h2><p>{reportDate(selected.endedAt)} · 用时 {reportDuration(selected)}</p></div>
          <button className="ielts-recording-toggle" type="button" disabled={!selected.recordingUrls?.length} onClick={toggleRecording} title={selected.recordingUrls?.length ? "播放本次训练录音" : "本次训练暂无可播放录音"}>{recordingPlaying ? <Pause /> : <Play />}<span>{selected.recordingUrls?.length ? recordingPlaying ? "暂停录音" : "播放原始录音" : "暂无录音"}</span></button>
        </header>
        <section className="ielts-history-overall-report">
          <h2>总体报告</h2>
          {selected.overallBandScore != null && <h3>{formatBand(selected.overallBandScore)} 分</h3>}
          <p>{selected.summary || "本次训练暂未生成文字总结。"}</p>
        </section>
        <section className="ielts-history-detail-section">
          <h3>表达优势</h3>
          {selected.strengths?.length > 0 ? <ul>{selected.strengths.map((item) => <li key={item}>{item}</li>)}</ul> : <p>本次报告没有保存额外优势说明。</p>}
        </section>
        <section className="ielts-history-scores">
          <div><p><small>流利度与连贯性</small><strong>{formatBand(selected.fluencyCoherenceScore)}<em>/9</em></strong></p><p><small>词汇资源</small><strong>{formatBand(selected.lexicalResourceScore)}<em>/9</em></strong></p><p><small>语法多样性与准确性</small><strong>{formatBand(selected.grammaticalRangeAccuracyScore)}<em>/9</em></strong></p><p><small>发音</small><strong>{formatBand(selected.pronunciationScore)}<em>/9</em></strong></p></div>
        </section>
        <section className="ielts-history-detail-section">
          <h3>优化改进</h3>
          {selected.improvements?.length > 0 ? <ol>{selected.improvements.map((item) => <li key={item}>{item}</li>)}</ol> : <p>本次报告没有保存额外改进项。</p>}
        </section>
        <section className="ielts-history-detail-section">
          <h3>推荐表达</h3>
          {selected.recommendedExpressions?.length > 0 ? <ul>{selected.recommendedExpressions.map((item) => <li key={item}>{item}</li>)}</ul> : <p>本次报告没有保存推荐表达。</p>}
        </section>
      </article> : <div className="ielts-history-empty"><BookOpenText /><h2>暂无训练记录</h2><p>完成一次专项训练后，后端报告会保存在这里。</p></div>}
    </section>
  );
}

export function TrendLineChart({ values }) {
  const canvasRef = useRef(null);

  useEffect(() => {
    const draw = () => {
      const canvas = canvasRef.current;
      if (!canvas) return;
      const bounds = canvas.getBoundingClientRect();
      const ratio = window.devicePixelRatio || 1;
      canvas.width = Math.round(bounds.width * ratio);
      canvas.height = Math.round(bounds.height * ratio);
      const context = canvas.getContext("2d");
      context.scale(ratio, ratio);
      const width = bounds.width;
      const height = bounds.height;
      const padding = { top: 16, right: 20, bottom: 24, left: 22 };
      const chartWidth = width - padding.left - padding.right;
      const chartHeight = height - padding.top - padding.bottom;
      const scoredValues = values.filter((value) => Number.isFinite(value));
      if (!scoredValues.length) return;
      const scoreMin = Math.min(...scoredValues);
      const scoreMax = Math.max(...scoredValues);
      const min = Math.max(0, Math.floor((scoreMin - .5) * 2) / 2);
      const max = Math.min(9, Math.max(min + 1, Math.ceil((scoreMax + .5) * 2) / 2));
      const points = values.map((value, index) => ({
        x: padding.left + (chartWidth * index) / (values.length - 1),
        y: Number.isFinite(value) ? padding.top + ((max - value) / (max - min)) * chartHeight : null,
        value,
      }));
      const scoredPoints = points.filter((point) => point.y != null);

      context.clearRect(0, 0, width, height);
      context.lineWidth = 1;
      context.strokeStyle = "#e5e5e0";
      [0, .5, 1].forEach((progress) => {
        const y = padding.top + chartHeight * progress;
        context.beginPath();
        context.moveTo(padding.left, y);
        context.lineTo(width - padding.right, y);
        context.stroke();
      });

      if (scoredPoints.length >= 2) {
        const gradient = context.createLinearGradient(0, padding.top, 0, height);
        gradient.addColorStop(0, "rgba(77, 77, 73, .24)");
        gradient.addColorStop(1, "rgba(77, 77, 73, 0)");
        context.beginPath();
        context.moveTo(scoredPoints[0].x, padding.top + chartHeight);
        scoredPoints.forEach((point) => context.lineTo(point.x, point.y));
        context.lineTo(scoredPoints.at(-1).x, padding.top + chartHeight);
        context.closePath();
        context.fillStyle = gradient;
        context.fill();

        context.beginPath();
        scoredPoints.forEach((point, index) => index === 0 ? context.moveTo(point.x, point.y) : context.lineTo(point.x, point.y));
        context.strokeStyle = "#242423";
        context.lineWidth = 3;
        context.lineJoin = "round";
        context.lineCap = "round";
        context.stroke();
      }

      points.forEach((point) => {
        const pointY = point.y ?? padding.top + chartHeight;
        context.beginPath();
        context.arc(point.x, pointY, 5, 0, Math.PI * 2);
        context.fillStyle = "#fff";
        context.fill();
        context.lineWidth = point.y == null ? 2 : 3;
        context.strokeStyle = point.y == null ? "#d4d4cf" : "#242423";
        context.stroke();
        context.fillStyle = "#6f6f6a";
        context.font = "600 11px sans-serif";
        context.textAlign = "center";
        context.fillText(point.y == null ? "--" : point.value.toFixed(1), point.x, height - 5);
      });
    };
    draw();
    window.addEventListener("resize", draw);
    return () => window.removeEventListener("resize", draw);
  }, [values]);

  return <canvas ref={canvasRef} className="ielts-trend-line-chart" aria-label={`最近五次模考成绩：${values.join("、")}`} />;
}

function AssetsTrends({ settings, reports }) {
  const mocks = reports.filter((item) => item.mode === "MOCK_TEST").slice(0, 5).reverse();
  const actualValues = mocks.map((item) => item.overallBandScore).filter((value) => value != null && value !== "").map(Number).filter(Number.isFinite);
  const values = [...actualValues, ...Array(Math.max(0, 5 - actualValues.length)).fill(null)].slice(0, 5);
  const latest = actualValues.at(-1);
  const change = actualValues.length >= 2 ? (latest - actualValues[0]).toFixed(1) : null;
  const recent = reports.slice(0, 10);
  const dimensionValues = [
    ["流利度与连贯性", "fluencyCoherenceScore"],
    ["词汇资源", "lexicalResourceScore"],
    ["语法多样性与准确性", "grammaticalRangeAccuracyScore"],
    ["发音", "pronunciationScore"],
  ].map(([label, key]) => {
    const scores = recent.map((item) => item[key]).filter((value) => value != null && value !== "").map(Number).filter(Number.isFinite);
    const band = scores.length ? scores.reduce((sum, score) => sum + score, 0) / scores.length : null;
    const percent = band == null ? 0 : Math.round((band / 9) * 100);
    return { label, percent };
  });
  const availablePercents = dimensionValues.map((item) => item.percent).filter((value) => value > 0);
  const highestPercent = availablePercents.length ? Math.max(...availablePercents) : 0;
  const lowestPercent = availablePercents.length ? Math.min(...availablePercents) : 0;
  const averagePercent = availablePercents.length ? availablePercents.reduce((sum, value) => sum + value, 0) / availablePercents.length : 0;
  const dimensions = dimensionValues.map((item) => ({
    ...item,
    status: item.percent === 0 ? "暂无数据"
      : highestPercent > lowestPercent && item.percent === highestPercent ? "相对优势"
        : highestPercent > lowestPercent && item.percent === lowestPercent ? "重点提升"
          : item.percent >= averagePercent ? "表现稳定" : "继续提升",
  }));
  const allPartEvaluations = reports.flatMap((item) => Array.isArray(item.partEvaluations) ? item.partEvaluations : []);
  const adviceCopy = {
    PART_1: ["回答长度更稳定", "保持完整作答，减少过短回答。"],
    PART_2: ["内容组织正在改善", "加强要点展开与句间连接。"],
    PART_3: ["观点深度需要加强", "增加原因、影响与对比结构。"],
  };
  const partAdvice = ["PART_1", "PART_2", "PART_3"].map((partName) => {
    const evaluation = allPartEvaluations.find((item) => item.part === partName);
    return {
      part: partName.replace("PART_", "Part "),
      title: evaluation ? adviceCopy[partName][0] : "暂无专项评分",
      detail: evaluation ? adviceCopy[partName][1] : "完成有效训练后生成建议。",
    };
  });
  const hasTrainingData = recent.length > 0;
  return (
    <section className="ielts-trends-dashboard">
      <section className="ielts-trend-summary">
        <div>
          <span>模考趋势</span>
          <h2>{formatBand(latest)}</h2>
          <p>{change == null ? "至少完成两次模考后显示趋势" : `最近 ${actualValues.length} 次变化 ${Number(change) >= 0 ? "+" : ""}${change} 分`}</p>
        </div>
        {actualValues.length > 0
          ? <div className="ielts-trend-chart-wrap"><TrendLineChart values={values} /><small>较早</small><small>较近</small></div>
          : <div className="ielts-trend-empty-state"><strong>暂无模考趋势</strong><p>完成至少两次完整模考后生成折线图。</p></div>}
        <div>
          <span>目标进度</span>
          <strong>{formatBand(settings?.targetScore)}</strong>
          <p>连续打卡 {Number(settings?.currentStreakDays || 0)} 天</p>
        </div>
      </section>
      <section className={cx("ielts-dimension-trends", !hasTrainingData && "is-empty")}>
        <h2>四项能力平均分 · 最近 {recent.length} 次训练</h2>
        {hasTrainingData
          ? dimensions.map((item) => <article key={item.label}><span>{item.label}</span><strong className="ielts-dimension-score">{item.percent}<small>/100</small></strong><div><i style={{ width: `${item.percent}%` }} /></div><strong>{item.status}</strong></article>)
          : <div className="ielts-dimension-empty-state"><strong>暂无能力评分</strong><p>完成一次有效训练后，这里会展示四项能力平均分。</p></div>}
      </section>
      <section className="ielts-part-trends">
        {partAdvice.map((item) => <article key={item.part}><span>{item.part}</span><strong>{item.title}</strong><p>{item.detail}</p></article>)}
      </section>
    </section>
  );
}

export function IeltsAssets({ route, onNavigate, onBackToAssets, onInterviewAssets, onTraining }) {
  const availableTabs = ["overview", "history", "trends"];
  const tab = availableTabs.includes(route?.tab) ? route.tab : "overview";
  const setTab = (nextTab) => onNavigate(nextTab === "overview" ? paths.ielts.assets.root : paths.ielts.assets[nextTab]);
  const tabs = [{ id: "overview", label: "概览" }, { id: "history", label: "训练记录" }, { id: "trends", label: "能力趋势" }];
  const tabRef = useRef(null);
  const tabButtons = useRef({});
  const [tabIndicator, setTabIndicator] = useState({ x: 0, width: 0, ready: false });
  const [settings, setSettings] = useState(null);
  const [reports, setReports] = useState([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");

  useEffect(() => {
    let cancelled = false;
    Promise.all([getIeltsSettings(), getIeltsEvaluationHistory()])
      .then(([nextSettings, nextReports]) => {
        if (cancelled) return;
        setSettings(nextSettings);
        setReports(Array.isArray(nextReports) ? nextReports : []);
      })
      .catch((error) => {
        if (!cancelled) setLoadError(error?.message || "IELTS 学习资产加载失败");
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => { cancelled = true; };
  }, []);

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

  const otherAssetsButton = <div className="asset-module-menu ielts-other-assets"><button className="asset-module-menu__trigger" type="button" aria-label="切换学习资产模块" aria-haspopup="menu"><SquaresFour weight="bold" /><span>其他资产</span><CaretDown weight="bold" /></button><div className="asset-module-menu__popover" role="menu"><button type="button" role="menuitem" onClick={onBackToAssets}><BookOpenText /><span><strong>场景训练学习资产</strong><small>对话记录、纠错与场景复练</small></span><CaretRight /></button><button type="button" role="menuitem" onClick={onInterviewAssets}><Briefcase /><span><strong>英文面试学习资产</strong><small>历史报告与口语复盘</small></span><CaretRight /></button></div></div>;
  return <main className={cx("ielts-page", "ielts-assets", tab === "overview" && "ielts-assets--overview", tab === "trends" && "ielts-assets--trends")}><IeltsHeader title="IELTS 学习资产" subtitle="集中查看数据库中已生成的训练评分与总体报告。" action={<div className="ielts-assets-actions">{otherAssetsButton}<SimpleCta className="ielts-assets-header-cta" onClick={onTraining}>返回训练中心</SimpleCta></div>} /><nav className="ielts-asset-tabs" ref={tabRef}><span className={cx("ielts-asset-tab-indicator", tabIndicator.ready && "is-ready")} style={{ width: tabIndicator.width, transform: `translateX(${tabIndicator.x}px)` }} />{tabs.map((item) => <button ref={(node) => { tabButtons.current[item.id] = node; }} key={item.id} className={tab === item.id ? "is-active" : ""} onClick={() => setTab(item.id)}>{item.label}</button>)}</nav>{loading ? <div className="ielts-history-empty"><NewtonsCradle label="正在读取后端评分记录" /></div> : loadError ? <div className="ielts-history-empty"><h2>学习资产加载失败</h2><p>{loadError}</p></div> : tab === "overview" ? <AssetsOverview settings={settings} reports={reports} onTab={setTab} /> : tab === "history" ? <AssetsHistory items={reports} /> : <AssetsTrends settings={settings} reports={reports} />}</main>;
}
