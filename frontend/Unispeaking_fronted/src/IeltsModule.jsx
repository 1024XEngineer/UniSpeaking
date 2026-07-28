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
  Trash,
  X,
} from "@phosphor-icons/react";
import { NewtonsCradle } from "./NewtonsCradle.jsx";
import { paths } from "./router.js";

const cx = (...parts) => parts.filter(Boolean).join(" ");

const topicGroups = {
  p1: [
    { id: "home", category: "必考题", title: "Home & Accommodation", count: 5, state: "建议复练", last: "3 天前", performance: "回答展开不足" },
    { id: "study", category: "必考题", title: "Work or Studies", count: 5, state: "已练习", last: "昨天", performance: "表达较稳定" },
    { id: "food", category: "事物", title: "Food", count: 4, state: "未练习", last: "—", performance: "尚无记录" },
    { id: "friends", category: "人物", title: "Friends", count: 5, state: "已练习", last: "1 周前", performance: "词汇重复" },
    { id: "walking", category: "事件", title: "Walking", count: 5, state: "未练习", last: "—", performance: "尚无记录" },
    { id: "museum", category: "地点", title: "Museum", count: 4, state: "已练习", last: "2 周前", performance: "语法需巩固" },
  ],
  p2: [
    { id: "famous", category: "人物", title: "想见的名人", count: 1, state: "建议复练", last: "4 天前", performance: "内容组织不足" },
    { id: "planner", category: "人物", title: "擅长做计划的人", count: 1, state: "已练习", last: "昨天", performance: "流利度较好" },
    { id: "movie", category: "事物", title: "让你失望的电影", count: 1, state: "未练习", last: "—", performance: "尚无记录" },
    { id: "story", category: "事物", title: "最近读过的故事", count: 1, state: "未练习", last: "—", performance: "尚无记录" },
    { id: "trip", category: "事件", title: "一次难忘的旅行", count: 1, state: "已练习", last: "1 周前", performance: "时态需巩固" },
    { id: "quiet", category: "地点", title: "一个安静的地方", count: 1, state: "已练习", last: "2 周前", performance: "细节丰富" },
  ],
  p3: [
    { id: "fame", category: "关联话题", title: "名人与社会影响", count: 6, state: "建议复练", last: "4 天前", performance: "观点展开不足" },
    { id: "planning", category: "关联话题", title: "计划与未来选择", count: 6, state: "已练习", last: "昨天", performance: "逻辑较清楚" },
    { id: "education", category: "独立分类", title: "教育", count: 6, state: "未练习", last: "—", performance: "尚无记录" },
    { id: "technology", category: "独立分类", title: "科技", count: 5, state: "已练习", last: "5 天前", performance: "词汇范围有限" },
    { id: "society", category: "独立分类", title: "社会", count: 6, state: "未练习", last: "—", performance: "尚无记录" },
    { id: "environment", category: "独立分类", title: "环境", count: 5, state: "已练习", last: "1 周前", performance: "语法较稳定" },
  ],
};

const questions = {
  p1: [
    "What kind of home would you like to live in in the future?",
    "What part of your home do you like the most?",
    "Do you think it is important to live in a comfortable environment?",
    "How long have you lived in your current home?",
    "Is there anything you would like to change about your home?",
  ],
  p3: [
    "Why do some people become famous very quickly today?",
    "Do famous people have a responsibility to influence young people positively?",
    "How has social media changed the meaning of fame?",
  ],
  mock: [
    "Let's talk about your studies. What subject are you studying?",
    "What do you enjoy most about your studies?",
    "Do you think your subject will be useful in the future?",
  ],
};

const partMeta = {
  p1: { number: "01", label: "Part 1", title: "日常问答", duration: "2–4 分钟", note: "单话题 · 4–5 道问题" },
  p2: { number: "02", label: "Part 2", title: "长陈述", duration: "3–4 分钟", note: "1 分钟准备 · 2 分钟陈述" },
  p3: { number: "03", label: "Part 3", title: "深入讨论", duration: "4–5 分钟", note: "关联话题与独立分类" },
  mock: { number: "模考", label: "全真模考", title: "全真模考", duration: "11–14 分钟", note: "随机考官 · 随机题目" },
};

const ieltsExaminers = [
  { id: "daniel", name: "Daniel", accent: "英式口音", personality: "严谨沉稳", image: "/examiner/daniel.png", offsetX: 0, intro: "节奏稳定，追问清晰，适合提前熟悉正式考场氛围。" },
  { id: "marcus", name: "Marcus", accent: "美式口音", personality: "清晰直接", image: "/examiner/marcus.png", offsetX: 5.2, intro: "表达清楚有力，会用自然追问帮助你快速进入回答状态。" },
  { id: "margaret", name: "Margaret", accent: "英式口音", personality: "从容细致", image: "/examiner/margaret.png", offsetX: 2.2, intro: "语速从容、停顿自然，适合练习完整展开与细节组织。" },
  { id: "sophia", name: "Sophia", accent: "澳式口音", personality: "自然友好", image: "/examiner/sophia.png", offsetX: 5.6, intro: "交流感自然但流程严格，适合降低紧张感并保持实战节奏。" },
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

function IeltsIntake({ onComplete }) {
  const [stepIndex, setStepIndex] = useState(0);
  const [answers, setAnswers] = useState({});
  const step = ieltsIntakeSteps[stepIndex];
  const selected = answers[step.id];
  const isLastStep = stepIndex === ieltsIntakeSteps.length - 1;
  const selectOption = (value) => setAnswers((current) => ({ ...current, [step.id]: value }));
  const continueFlow = () => {
    if (!selected) return;
    if (isLastStep) onComplete(answers);
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
          {stepIndex > 0 && <button className="ielts-intake-back" onClick={() => setStepIndex((value) => value - 1)}><ArrowLeft />上一步</button>}
          <TrainingCta disabled={!selected} onClick={continueFlow}>{isLastStep ? "进入训练中心" : "下一步"}</TrainingCta>
        </div>
      </section>
    </main>
  );
}

function IeltsHome({ onChoose, onAssets, onBack, profile }) {
  const target = profile?.target || "7.0";
  return (
    <main className="ielts-page ielts-home">
      <IeltsHeader onBack={onBack} title="IELTS 口语特训" subtitle="实战训练，持续复盘，看见进步" action={<SimpleCta className="ielts-home-assets-cta" onClick={onAssets}>查看学习资产</SimpleCta>} />
      <section className="ielts-goal-row" aria-label="备考目标">
        <div><span>目标</span><strong>{target}</strong></div>
        <div><span>连续打卡</span><strong>12 <small>天</small></strong></div>
        <div><span>今日特训</span><strong>3 <small>/ 5</small></strong></div>
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
  const [category, setCategory] = useState("全部");
  const [query, setQuery] = useState("");
  const filterRef = useRef(null);
  const filterButtons = useRef({});
  const [filterIndicator, setFilterIndicator] = useState({ x: 0, width: 0, ready: false });
  const topics = topicGroups[part] || [];
  const categories = ["全部", ...new Set(topics.map((item) => item.category))];
  const filtered = topics.filter((item) => (category === "全部" || item.category === category) && item.title.toLowerCase().includes(query.toLowerCase()));
  const categoryKey = categories.join("|");

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
          {categories.map((item) => <button ref={(node) => { filterButtons.current[item] = node; }} key={item} className={category === item ? "is-active" : ""} onClick={() => setCategory(item)}>{item}</button>)}
        </div>
        <label className="ielts-topic-search">
          <MagnifyingGlass aria-hidden="true" />
          <input className="ielts-topic-search__input" aria-label="搜索话题" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="搜索话题" />
          <button type="button" className="ielts-topic-search__reset" aria-label="清空搜索" onClick={() => setQuery("")}><X /></button>
        </label>
      </section>
      <section className="ielts-topic-list">
        <header><span>话题</span><span>练习记录</span><span>最近表现</span><span /></header>
        {filtered.map((topic) => <button key={topic.id} onClick={() => onStart(topic, false)}><span><small>{topic.category}</small><strong>{topic.title}</strong><em>{topic.count} 道问题</em></span><span><strong>{topic.state}</strong><em>{topic.last}</em></span><span>{topic.performance}</span><CaretRight /></button>)}
      </section>
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
  const isMock = part === "mock";
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
      <footer className="ielts-setup-footer"><p><strong>{isMock ? "开始后不可暂停，中途退出本次模考将作废并消耗 1 次额度。" : "开始后不可暂停或静音，本次训练将消耗 1 次特训额度。"}</strong><small>今日剩余 2 次</small></p><TrainingCta className="ielts-confirm-cta" onClick={() => onStart(isMock ? mockExaminer : examiner)}>确认并开始</TrainingCta></footer>
    </main>
  );
}

function formatTime(seconds) {
  const safe = Math.max(0, seconds);
  return `${String(Math.floor(safe / 60)).padStart(2, "0")}:${String(safe % 60).padStart(2, "0")}`;
}

function PracticeSession({ part, examiner, onExit, onComplete }) {
  const [subtitles, setSubtitles] = useState(false);
  const [questionIndex, setQuestionIndex] = useState(0);
  const [stage, setStage] = useState(part === "p2" ? "prepare" : "answer");
  const [remaining, setRemaining] = useState(part === "p2" ? 60 : part === "mock" ? 0 : part === "p1" ? 240 : 300);
  const [notes, setNotes] = useState("");
  const [exitOpen, setExitOpen] = useState(false);
  const activeQuestions = questions[part] || questions.p1;

  useEffect(() => {
    if (part === "mock" || remaining <= 0) return undefined;
    const timer = window.setInterval(() => setRemaining((value) => value - 1), 1000);
    return () => window.clearInterval(timer);
  }, [part, remaining <= 0]);

  useEffect(() => {
    if (part === "p2" && stage === "prepare" && remaining <= 0) {
      setStage("answer");
      setRemaining(120);
    }
    if (part === "p2" && stage === "answer" && remaining <= 0) onComplete();
  }, [part, stage, remaining, onComplete]);

  const nextQuestion = () => {
    if (part === "p2") {
      if (stage === "prepare") { setStage("answer"); setRemaining(120); }
      else onComplete();
      return;
    }
    if (questionIndex >= activeQuestions.length - 1) onComplete();
    else setQuestionIndex((value) => value + 1);
  };

  const exitDialog = exitOpen && <div className="ielts-dialog-backdrop"><section className="ielts-dialog"><h2>{part === "mock" ? "退出并作废本次模考？" : "退出当前训练？"}</h2><p>{part === "mock" ? "已完成的内容不会保存，本次仍会消耗 1 次特训额度。" : "本次未完成训练不会生成专项报告。"}</p><div><button onClick={() => setExitOpen(false)}>继续训练</button><button onClick={onExit}>确认退出</button></div></section></div>;
  const isPartTwo = part === "p2";
  const showTranscript = isPartTwo || subtitles;
  const transcriptText = isPartTwo
    ? stage === "prepare"
      ? "You have one minute to think about and make notes for this question."
      : "You can start answering now."
    : activeQuestions[questionIndex];

  return (
    <main className={cx("conversation", "call", "ielts-call", showTranscript && "call--subtitles", isPartTwo && "ielts-call--part-two")}>
      <div className="conversation__top ielts-call-top">
        <div><strong>{part === "mock" ? "IELTS 全真模考" : `${partMeta[part].label} · ${partMeta[part].title}`}</strong><span>{part === "mock" ? "Part 1 · Introduction and interview" : isPartTwo && stage === "prepare" ? "准备阶段" : "正式作答"}</span></div>
        <button className="round-control ielts-call-exit" onClick={() => setExitOpen(true)} aria-label="退出训练"><X /></button>
      </div>
      <section className="call__stage">
        <div className={cx("call-presence", showTranscript && "call-presence--compact")}>
          <div className={cx("portrait", showTranscript ? "portrait--small" : "portrait--call", "ielts-call-portrait")}><img src={examiner.image} alt={examiner.name} style={{ "--examiner-offset-x": `${examiner.offsetX}%` }} /></div>
          <div className={cx("listening-state", showTranscript && "listening-state--compact")}>
            <span className={cx("voice-wave", showTranscript && "voice-wave--compact", "is-fallback")} aria-hidden="true">
              {[.28, .52, .78, 1, .72, .48, .3].map((level, index) => <i key={index} className="voice-wave__bar" style={{ "--rest-level": level }} />)}
            </span>
            <time className="call-presence__time">{part === "mock" ? "模拟进行中" : formatTime(remaining)}</time>
            {!showTranscript && <span>{examiner.name} 正在提问</span>}
          </div>
        </div>
        {showTranscript && <div className="transcript ielts-call-transcript" aria-label="考官问题字幕"><article className="transcript__line"><small>{examiner.name}</small><p>{transcriptText}</p></article></div>}
        {isPartTwo && (
          <section className="ielts-part-two-compact-material" aria-label="Part 2 题卡与笔记">
            <article className="ielts-part-two-compact-cue">
              <span>PART 2 · LONG TURN</span>
              <h1>Describe a famous person you would like to meet</h1>
              <p>You should say:</p>
              <ul><li>Who he or she is</li><li>How you knew about this person</li><li>How or where you would like to meet</li><li>And explain why you would like to meet this person</li></ul>
            </article>
            <section className="ielts-part-two-compact-notes">
              <header><span><NotePencil />自由笔记</span><small>{stage === "prepare" ? "准备结束后将自动锁定" : "已锁定"}</small></header>
              <textarea value={notes} onChange={(event) => setNotes(event.target.value)} readOnly={stage !== "prepare"} placeholder="在这里记录你的思路、关键词或完整句子…" />
            </section>
          </section>
        )}
      </section>
      <footer className={cx("call-controls", "ielts-call-controls", isPartTwo && "ielts-call-controls--part-two")}>
        <span className="ielts-recording-label"><i className="recording-dot" />正在录音</span>
        <div className="ielts-call-control-buttons">
          {!isPartTwo && <button className={cx("round-control", subtitles && "is-on")} onClick={() => setSubtitles(!subtitles)} aria-label={subtitles ? "关闭字幕" : "开启字幕"}><Subtitles /></button>}
          <button className="round-control round-control--end" onClick={nextQuestion} aria-label={isPartTwo ? stage === "prepare" ? "提前开始陈述" : "结束陈述" : "结束本题并进入下一题"}><ArrowRight weight="bold" /></button>
        </div>
        <p className="ielts-call-hint">{isPartTwo ? stage === "prepare" ? "提前准备好了可以点击开始陈述" : "完成陈述后，请点击按钮结束本题" : "回答完当前问题后，请点击按钮进入下一题"}</p>
      </footer>
      {exitDialog}
    </main>
  );
}

function AnalysisPending({ onHome, onReport }) {
  return (
    <main className="ielts-page ielts-pending">
      <section><NewtonsCradle label="正在分析练习报告" /><p>ANALYSIS IN PROGRESS</p><h1>本次练习已保存</h1><p>AI 正在分析四项能力与逐句表达。你可以先离开，报告完成后会出现在学习资产中。</p><div className="ielts-pending-actions"><button className="ielts-pending-home" onClick={onHome}>返回训练中心</button><button className="ielts-pending-report" onClick={onReport}>浏览本次报告<ArrowRight weight="bold" /></button></div></section>
    </main>
  );
}

const scoreRows = [
  { label: "流利度与连贯性", score: 82, note: "能够持续表达，但部分观点之间缺少自然过渡。" },
  { label: "词汇资源", score: 68, note: "表达准确，但同一形容词重复出现。" },
  { label: "语法多样性与准确性", score: 76, note: "基础结构准确，可以加入更多复合句。" },
  { label: "发音", score: 86, note: "整体易于理解，个别词尾需要更完整。" },
];

function PracticeReport({ part, onHome, onRetry, onAssets }) {
  const isMock = part === "mock";
  const reportTitle = isMock ? "全真模考 · 完整表现报告" : `${partMeta[part].label} · 本次专项表现`;
  const reportSubtitle = isMock
    ? "预估成绩仅用于训练反馈，并非雅思官方考试成绩。"
    : `这不是完整雅思口语预估分；报告只反映本次${partMeta[part].title}表现。`;
  return (
    <main className={cx("ielts-page", "ielts-report", !isMock && "ielts-report--single")}>
      <IeltsHeader onBack={onHome} title={reportTitle} subtitle={reportSubtitle} action={<button className="ielts-report-assets" onClick={onAssets}><BookOpenText />查看学习资产</button>} />
      {isMock && <section className="ielts-mock-score"><span>本次预估</span><strong>6.5</strong><p>合理波动范围 6.0–6.5</p></section>}
      <section className="ielts-report-summary"><div><span>本次结论</span><h2>内容清楚，但需要更自然地组织细节</h2><p>{part === "p2" ? "你已经能够持续完成两分钟陈述。" : "你能够直接回应问题并保持表达。"}下一步优先减少重复词，并让每个细节更好地服务核心观点。</p></div><div><span>优先改进</span><ol><li>用更具体的细节替代重复形容词</li><li>使用自然连接表达推进叙述</li><li>注意过去时与现在完成时的切换</li></ol></div></section>
      <section className="ielts-score-list"><h2>四项能力反馈</h2>{scoreRows.map((row) => <article key={row.label}><strong>{row.label}</strong><span className="ielts-score-value">{row.score}<small>/100</small></span><p>{row.note}</p></article>)}</section>
    </main>
  );
}

export function IeltsTrainingCenter({ route, onNavigate, onExit, onAssets }) {
  const screen = route?.screen || "home";
  const part = route?.part || "p2";
  const random = route?.selection === "random" || part === "mock";
  const topic = part !== "mock" && !random ? topicGroups[part]?.find((item) => item.id === route?.selection) || null : null;
  const [examiner, setExaminer] = useState(ieltsExaminers[0]);
  const [intakeProfile, setIntakeProfile] = useState(loadIeltsIntakeProfile);

  const partSegment = (nextPart) => nextPart === "mock" ? "mock" : `part${nextPart.slice(1)}`;
  const partPath = (nextPart) => paths.ielts.part(partSegment(nextPart));
  const stepPath = (nextPart, selection, step) => paths.ielts.step(partSegment(nextPart), selection, step);

  const choosePart = (nextPart, mode) => {
    if (nextPart === "mock") { onNavigate(paths.ielts.step("mock", "random", "setup")); return; }
    if (mode === "recommended") { onNavigate(stepPath("p2", topicGroups.p2[0].id, "setup")); return; }
    onNavigate(partPath(nextPart));
  };
  const openSetup = (nextTopic, isRandom) => onNavigate(stepPath(part, isRandom ? "random" : nextTopic.id, "setup"));
  const start = (nextExaminer) => { setExaminer(nextExaminer); onNavigate(stepPath(part, route?.selection || "random", "session")); };
  const completeIntake = (profile) => {
    setIntakeProfile(profile);
    try { window.localStorage.setItem(IELTS_INTAKE_STORAGE_KEY, JSON.stringify(profile)); } catch { /* Local-only preference may be unavailable. */ }
  };

  if (screen === "home" && !intakeProfile) return <IeltsIntake onComplete={completeIntake} />;
  if (screen === "topics") return <TopicBrowser part={part} onBack={() => onNavigate(paths.ielts.root)} onStart={openSetup} />;
  if (screen === "setup") return <DeviceSetup part={part} topic={topic} random={random} onBack={() => onNavigate(part === "mock" ? paths.ielts.root : partPath(part))} onStart={start} />;
  if (screen === "session") return <PracticeSession part={part} examiner={examiner} onExit={() => onNavigate(paths.ielts.root)} onComplete={() => onNavigate(stepPath(part, route?.selection || "random", "analysis"))} />;
  if (screen === "analysis") return <AnalysisPending onHome={() => onNavigate(paths.ielts.root)} onReport={() => onNavigate(stepPath(part, route?.selection || "random", "report"))} />;
  if (screen === "report") return <PracticeReport part={part} onHome={() => onNavigate(paths.ielts.root)} onRetry={() => onNavigate(stepPath(part, route?.selection || "random", "setup"))} onAssets={onAssets} />;
  return <IeltsHome onChoose={choosePart} onAssets={onAssets} onBack={onExit} profile={intakeProfile} />;
}

const historyItems = [
  { id: 1, type: "模考", title: "全真模考 · 第 3 次", date: "今天 14:20", result: "预估 6.5 · 波动 6.0–6.5", status: "已完成", duration: "13:42", scores: [78, 72, 75, 84], practicePath: null, reportPath: paths.ielts.step("mock", "random", "report") },
  { id: 2, type: "Part 2", title: "擅长做计划的人", date: "昨天 20:12", result: "内容组织较清晰", status: "已完成", duration: "03:16", scores: [82, 68, 76, 86], practicePath: paths.ielts.step("part2", "planner", "setup"), reportPath: paths.ielts.step("part2", "planner", "report") },
  { id: 3, type: "Part 1", title: "Work or Studies", date: "7 月 20 日", result: "回答完整度需要提升", status: "已完成", duration: "03:48", scores: [71, 74, 77, 83], practicePath: paths.ielts.step("part1", "study", "setup"), reportPath: paths.ielts.step("part1", "study", "report") },
  { id: 4, type: "Part 3", title: "科技与社会", date: "7 月 18 日", result: "观点有深度，连接仍可加强", status: "已完成", duration: "04:35", scores: [76, 70, 73, 81], practicePath: paths.ielts.step("part3", "technology", "setup"), reportPath: paths.ielts.step("part3", "technology", "report") },
];

function AssetsOverview({ onTab }) {
  const activity = [
    { day: "周四", minutes: 8 },
    { day: "周五", minutes: 16 },
    { day: "周六", minutes: 0 },
    { day: "周日", minutes: 24 },
    { day: "周一", minutes: 12 },
    { day: "周二", minutes: 21 },
    { day: "今天", minutes: 15 },
  ];
  return <section className="ielts-overview-dashboard"><section className="ielts-asset-hero"><div><span>最近一次完整模考</span><h2>预估 6.5</h2><p>合理波动范围 6.0–6.5 · AI 训练评估，并非官方考试成绩</p></div><div><span>目标</span><strong>7.0</strong><small>还差约 0.5 分</small></div><TrainingCta className="ielts-asset-gradient-action" onClick={() => onTab("trends")}>查看能力趋势</TrainingCta></section><section className="ielts-weekly-activity"><header><div><span>近七天训练时长</span><h2>96 <small>分钟</small></h2><p>共完成 6 次训练，较上周增加 18 分钟</p></div><div className="ielts-weekly-stats"><p><strong>4</strong><small>活跃天数</small></p><p><strong>16</strong><small>日均分钟</small></p><p><strong>3</strong><small>专项覆盖</small></p></div></header><div className="ielts-weekly-bars">{activity.map((item) => <span key={item.day}><i style={{ height: `${Math.max(6, (item.minutes / 24) * 100)}%` }} className={item.minutes === 0 ? "is-empty" : ""} /><strong>{item.minutes}</strong><small>{item.day}</small></span>)}</div></section><section className="ielts-asset-recent"><header><h2>最近训练</h2></header>{historyItems.slice(0, 3).map((item) => <article key={item.id}><span>{item.type}</span><div><strong>{item.title}</strong><small>{item.date} · {item.duration}</small></div><p>{item.result}</p></article>)}</section></section>;
}

function RecordingToggle() {
  const [playing, setPlaying] = useState(false);
  return <label className="ielts-recording-toggle" title={playing ? "暂停录音" : "播放录音"}>
    <input type="checkbox" checked={playing} onChange={(event) => setPlaying(event.target.checked)} />
    <Play className="play" weight="fill" />
    <Pause className="pause" weight="fill" />
    <span>{playing ? "暂停录音" : "播放录音"}</span>
  </label>;
}

function AssetsHistory({ onNavigate }) {
  const [items, setItems] = useState(historyItems);
  const [selected, setSelected] = useState(historyItems[0]);
  const deleteSelected = () => {
    const remaining = items.filter((item) => item.id !== selected.id);
    setItems(remaining);
    setSelected(remaining[0] || null);
  };
  return <section className="ielts-history-layout"><aside><header><h2>训练记录</h2><span>{items.length} 条</span></header>{items.map((item) => <button key={item.id} className={selected?.id === item.id ? "is-active" : ""} onClick={() => setSelected(item)}><small>{item.date} · {item.type}</small><strong>{item.title}</strong><span>{item.duration}</span></button>)}</aside>{selected ? <article><header><div><span>{selected.type}</span><h2>{selected.title}</h2><p>{selected.date} · 用时 {selected.duration}</p></div><div className="ielts-history-media-actions"><RecordingToggle key={selected.id} /><button className="asset-delete-button" type="button" aria-label="删除当前训练记录" onClick={deleteSelected}><Trash weight="bold" /><span>删除</span></button></div></header><section className="ielts-history-summary"><span>总体报告</span><h3>{selected.result}</h3><p>本次表达整体清楚，优先改善观点之间的过渡，并在回答中保持稳定、完整的展开。</p></section><section className="ielts-history-scores"><span>四项能力评分</span><div>{scoreRows.map((row, index) => <p key={row.label}><small>{row.label}</small><strong>{selected.scores[index]}<em>/100</em></strong></p>)}</div></section><footer><small>录音与本次总体报告将保留在训练记录中</small><button className="ielts-history-report" onClick={() => onNavigate(selected.reportPath)}>查看总体报告</button>{selected.practicePath && <TrainingCta className="ielts-history-practice" onClick={() => onNavigate(selected.practicePath)}>快速复练</TrainingCta>}</footer></article> : <div className="ielts-history-empty"><BookOpenText /><h2>暂无训练记录</h2><p>完成一次专项训练后，报告和录音会保存在这里。</p></div>}</section>;
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
      const min = 5;
      const max = 7;
      const points = values.map((value, index) => ({
        x: padding.left + (chartWidth * index) / (values.length - 1),
        y: padding.top + ((max - value) / (max - min)) * chartHeight,
      }));

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

      const gradient = context.createLinearGradient(0, padding.top, 0, height);
      gradient.addColorStop(0, "rgba(77, 77, 73, .24)");
      gradient.addColorStop(1, "rgba(77, 77, 73, 0)");
      context.beginPath();
      context.moveTo(points[0].x, padding.top + chartHeight);
      points.forEach((point) => context.lineTo(point.x, point.y));
      context.lineTo(points.at(-1).x, padding.top + chartHeight);
      context.closePath();
      context.fillStyle = gradient;
      context.fill();

      context.beginPath();
      points.forEach((point, index) => index === 0 ? context.moveTo(point.x, point.y) : context.lineTo(point.x, point.y));
      context.strokeStyle = "#242423";
      context.lineWidth = 3;
      context.lineJoin = "round";
      context.lineCap = "round";
      context.stroke();

      points.forEach((point, index) => {
        context.beginPath();
        context.arc(point.x, point.y, 5, 0, Math.PI * 2);
        context.fillStyle = "#fff";
        context.fill();
        context.lineWidth = 3;
        context.strokeStyle = "#242423";
        context.stroke();
        context.fillStyle = "#6f6f6a";
        context.font = "600 11px sans-serif";
        context.textAlign = "center";
        context.fillText(values[index].toFixed(1), point.x, height - 5);
      });
    };
    draw();
    window.addEventListener("resize", draw);
    return () => window.removeEventListener("resize", draw);
  }, [values]);

  return <canvas ref={canvasRef} className="ielts-trend-line-chart" aria-label={`最近五次模考成绩：${values.join("、")}`} />;
}

function AssetsTrends() {
  const values = [5.5, 6, 6, 6.5, 6.5];
  const averages = [78, 72, 76, 84];
  return <section className="ielts-trends-dashboard"><section className="ielts-trend-summary"><div><span>模考趋势</span><h2>6.5</h2><p>最近 5 次模考提升 1.0 分</p></div><div className="ielts-trend-chart-wrap"><TrendLineChart values={values} /><small>第 1 次</small><small>最近一次</small></div><div><span>目标进度</span><strong>7.0</strong><p>已连续打卡 12 天</p></div></section><section className="ielts-dimension-trends"><h2>四项能力平均分</h2>{scoreRows.map((row, index) => <article key={row.label}><span>{row.label}</span><strong className="ielts-dimension-score">{averages[index]}<small>/100</small></strong><div><i style={{ width: `${averages[index]}%` }} /></div><strong>{["稳定", "优先提升", "稳定", "优势"][index]}</strong></article>)}</section><section className="ielts-part-trends"><article><span>Part 1</span><strong>回答长度更稳定</strong><p>近 4 次练习中，过短回答减少 38%。</p></article><article><span>Part 2</span><strong>内容组织正在改善</strong><p>仍需减少重复并加强细节连接。</p></article><article><span>Part 3</span><strong>观点深度不足</strong><p>建议增加原因、影响与对比结构。</p></article></section></section>;
}

export function IeltsAssets({ route, onNavigate, onBackToAssets, onInterviewAssets, onTraining }) {
  const availableTabs = ["overview", "history", "trends"];
  const tab = availableTabs.includes(route?.tab) ? route.tab : "overview";
  const setTab = (nextTab) => onNavigate(nextTab === "overview" ? paths.ielts.assets.root : paths.ielts.assets[nextTab]);
  const tabs = [{ id: "overview", label: "概览" }, { id: "history", label: "训练记录" }, { id: "trends", label: "能力趋势" }];
  const tabRef = useRef(null);
  const tabButtons = useRef({});
  const [tabIndicator, setTabIndicator] = useState({ x: 0, width: 0, ready: false });

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
  return <main className={cx("ielts-page", "ielts-assets", tab === "overview" && "ielts-assets--overview", tab === "trends" && "ielts-assets--trends")}><IeltsHeader title="IELTS 学习资产" subtitle="集中查看每次训练记录、总体报告与原始录音。" action={<div className="ielts-assets-actions">{otherAssetsButton}<SimpleCta className="ielts-assets-header-cta" onClick={onTraining}>返回训练中心</SimpleCta></div>} /><nav className="ielts-asset-tabs" ref={tabRef}><span className={cx("ielts-asset-tab-indicator", tabIndicator.ready && "is-ready")} style={{ width: tabIndicator.width, transform: `translateX(${tabIndicator.x}px)` }} />{tabs.map((item) => <button ref={(node) => { tabButtons.current[item.id] = node; }} key={item.id} className={tab === item.id ? "is-active" : ""} onClick={() => setTab(item.id)}>{item.label}</button>)}</nav>{tab === "overview" && <AssetsOverview onTab={setTab} />}{tab === "history" && <AssetsHistory onNavigate={onNavigate} />}{tab === "trends" && <AssetsTrends />}</main>;
}
