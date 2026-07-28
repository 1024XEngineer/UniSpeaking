import { useEffect, useMemo, useRef, useState } from "react";
import {
  ArrowLeft,
  ArrowRight,
  BookOpenText,
  Briefcase,
  CalendarBlank,
  CaretDown,
  CaretRight,
  Subtitles,
  Check,
  CheckCircle,
  Clock,
  Crown,
  EnvelopeSimple,
  Eye,
  EyeSlash,
  Fire,
  GearSix,
  Headphones,
  LockKey,
  Medal,
  Microphone,
  MicrophoneSlash,
  PaperPlaneTilt,
  Pause,
  Password,
  PhoneDisconnect,
  Play,
  Plus,
  ShieldCheck,
  SignOut,
  SlidersHorizontal,
  SpeakerHigh,
  SpeakerSlash,
  SquaresFour,
  Trash,
  Translate,
  UploadSimple,
  User,
  Waveform,
  X,
} from "@phosphor-icons/react";
import {
  AudioLines,
  CalendarCheck2,
  ChevronLeft,
  ChevronRight,
  Compass,
  Footprints,
  Headphones as LucideHeadphones,
  Languages,
  MessageCircleMore,
  MessagesSquare,
  PackageCheck,
  Sparkles,
  Target,
  Trophy,
} from "lucide-react";
import { assetRecords, learningItems, levels, plans, recommendations, teachers } from "./data.js";
import { createRealtimeClient } from "./realtimeClient.js";
import { IeltsAssets, IeltsTrainingCenter } from "./IeltsModule.jsx";
import { InterviewAssets, InterviewTrainingCenter } from "./InterviewModule.jsx";
import { NewtonsCradle } from "./NewtonsCradle.jsx";
import { hrefForPage, paths, resolveRoute } from "./router.js";

const cx = (...parts) => parts.filter(Boolean).join(" ");

function Brand({ compact = false }) {
  return (
    <div className={cx("brand", compact && "brand--compact")}>
      <span className="brand__mark"><img src="/brand/unispeaking-mark-user.jpg" alt="" /></span>
      {!compact && <img className="brand__wordmark" src="/brand/unispeaking-wordmark.png" alt="UniSpeaking" />}
    </div>
  );
}

function AudioToggle({ label = "播放声音", compact = false, mini = false }) {
  const [muted, setMuted] = useState(false);
  return (
    <button
      type="button"
      className={cx("audio-toggle", compact && "audio-toggle--compact", mini && "audio-toggle--mini", muted && "is-muted")}
      aria-label={muted ? `开启${label}` : `关闭${label}`}
      aria-pressed={!muted}
      onClick={() => setMuted(!muted)}
    >
      <span className="audio-toggle__speaker"><SpeakerHigh weight="fill" /></span>
      <span className="audio-toggle__muted"><SpeakerSlash weight="fill" /></span>
    </button>
  );
}

function ScenePlaybackToggle({ label = "播放发音" }) {
  const [playing, setPlaying] = useState(false);
  return (
    <label className="scene-playback-toggle" title={playing ? "暂停发音" : label}>
      <input type="checkbox" checked={playing} onChange={(event) => setPlaying(event.target.checked)} />
      <Play className="play" weight="fill" />
      <Pause className="pause" weight="fill" />
    </label>
  );
}

function MicrophoneToggle({ label = "麦克风", className, onActivate }) {
  const [active, setActive] = useState(false);
  const toggle = () => {
    const nextActive = !active;
    setActive(nextActive);
    if (nextActive) onActivate?.();
  };
  return (
    <button
      type="button"
      className={cx("microphone-toggle", active && "is-active", className)}
      aria-label={active ? `关闭${label}` : `开启${label}`}
      aria-pressed={active}
      onClick={toggle}
    >
      <MicrophoneSlash className="microphone-toggle__slash" weight="fill" />
      <Microphone className="microphone-toggle__active" weight="fill" />
    </button>
  );
}

const formatCallDuration = (totalSeconds) => {
  const minutes = Math.floor(totalSeconds / 60).toString().padStart(2, "0");
  const seconds = (totalSeconds % 60).toString().padStart(2, "0");
  return `${minutes}:${seconds}`;
};

function CallTimer({ state = "active", paused = false, className }) {
  const [elapsedSeconds, setElapsedSeconds] = useState(0);

  useEffect(() => {
    const startedAt = Date.now();
    const updateElapsed = () => setElapsedSeconds(Math.floor((Date.now() - startedAt) / 1000));
    updateElapsed();
    const interval = window.setInterval(updateElapsed, 1000);
    return () => window.clearInterval(interval);
  }, []);

  const duration = formatCallDuration(elapsedSeconds);
  const label = state === "connecting"
    ? "连接中"
    : state === "ended"
      ? "已结束"
      : paused
        ? `已暂停 · ${duration}`
        : duration;

  return <time className={cx("call-presence__time", className)}>{label}</time>;
}

function CallControls({ paused, onToggleMicrophone, onEnd, disabled = false, subtitles = false, onToggleSubtitles, showSubtitles = true, className }) {
  return (
    <div className={cx("call-controls", className)}>
      <button className={cx("round-control", paused && "is-on")} aria-label={paused ? "恢复会话" : "暂停会话"} disabled={disabled} onClick={onToggleMicrophone}>{paused ? <MicrophoneSlash /> : <Microphone />}</button>
      {showSubtitles && <button className={cx("round-control", subtitles && "is-on")} aria-label={subtitles ? "关闭字幕" : "打开字幕"} onClick={onToggleSubtitles}><Subtitles /></button>}
      <button className="round-control round-control--end" aria-label="结束当前会话" disabled={disabled} onClick={onEnd}><PhoneDisconnect weight="fill" /></button>
    </div>
  );
}

const transcriptTranslationLookup = {
  "Hey there, I'm Clara. So good to meet you. How's your day going so far?": "嗨，我是 Clara。很高兴认识你，今天过得怎么样？",
  "Hi! What can I get started for you today?": "你好！今天想先来点什么？",
  "Could you recommend something less sweet?": "你能推荐一些不太甜的吗？",
  "Sure — how about a medium oat milk latte?": "当然，中杯燕麦奶拿铁怎么样？",
  "That sounds great. I’ll have that, thank you.": "听起来不错，我就要这个，谢谢。",
};

const resolveTranscriptTranslation = (line) => line.zh || transcriptTranslationLookup[line.en?.trim()] || "本句中文翻译暂未生成。";

function CallTranscript({ lines, translated, onToggleTranslation, transcriptRef, onScroll, className, emptyStatus }) {
  return (
    <div ref={transcriptRef} className={cx("transcript", className)} onScroll={onScroll} tabIndex="0" aria-label="对话字幕，可滚动查看历史内容">
      {lines.length === 0
        ? <article className="transcript__line"><small>字幕</small><p>{emptyStatus}</p></article>
        : lines.map((line, index) => {
          const isTranslated = translated.includes(index);
          return <article key={line.id || index} className={cx("transcript__line", line.who === "你" && "is-user")}><small>{line.who}</small><p>{line.en}</p><button type="button" aria-label={`${isTranslated ? "收起" : "查看"}${line.who}这句字幕的翻译`} onClick={() => onToggleTranslation(index)}><Translate />{isTranslated ? "收起翻译" : "翻译"}</button>{isTranslated && <span>{resolveTranscriptTranslation(line)}</span>}</article>;
        })}
    </div>
  );
}

const voiceWaveRestingLevels = [.28, .52, .78, 1, .72, .48, .3];

function VoiceWaveform({ active, compact = false }) {
  const waveformRef = useRef(null);

  useEffect(() => {
    const waveform = waveformRef.current;
    if (!waveform) return undefined;
    const bars = [...waveform.querySelectorAll(".voice-wave__bar")];
    const resetBars = () => bars.forEach((bar) => { bar.style.transform = ""; });

    if (!active) {
      waveform.classList.remove("is-fallback");
      resetBars();
      return undefined;
    }

    let cancelled = false;
    let animationFrame;
    let audioContext;
    let stream;

    const startListening = async () => {
      try {
        if (!navigator.mediaDevices?.getUserMedia) throw new Error("microphone unavailable");
        stream = await navigator.mediaDevices.getUserMedia({
          audio: { echoCancellation: true, noiseSuppression: true, autoGainControl: true },
        });
        if (cancelled) {
          stream.getTracks().forEach((track) => track.stop());
          return;
        }

        const AudioContext = window.AudioContext || window.webkitAudioContext;
        audioContext = new AudioContext();
        const analyser = audioContext.createAnalyser();
        analyser.fftSize = 256;
        analyser.smoothingTimeConstant = .76;
        audioContext.createMediaStreamSource(stream).connect(analyser);
        const frequencies = new Uint8Array(analyser.frequencyBinCount);

        const animate = () => {
          analyser.getByteFrequencyData(frequencies);
          bars.forEach((bar, index) => {
            const bin = 2 + index * 2;
            const energy = Math.max(0, (frequencies[bin] - 12) / 118);
            const scale = Math.min(1, .18 + energy * (1.05 + voiceWaveRestingLevels[index] * .35));
            bar.style.transform = `scaleY(${scale})`;
          });
          animationFrame = requestAnimationFrame(animate);
        };
        animate();
      } catch {
        waveform.classList.add("is-fallback");
      }
    };

    startListening();
    return () => {
      cancelled = true;
      cancelAnimationFrame(animationFrame);
      stream?.getTracks().forEach((track) => track.stop());
      audioContext?.close();
      waveform.classList.remove("is-fallback");
      resetBars();
    };
  }, [active]);

  return (
    <div ref={waveformRef} className={cx("voice-wave", active && "is-active", compact && "voice-wave--compact")} aria-hidden="true">
      {voiceWaveRestingLevels.map((level, index) => <span key={index} className="voice-wave__bar" style={{ "--rest-level": level }} />)}
    </div>
  );
}

function ExpandingCta({ children, className, direction = "forward", ...props }) {
  const Arrow = direction === "back" ? ArrowLeft : ArrowRight;
  return <button className={cx("expanding-cta", direction === "back" && "expanding-cta--back", className)} {...props}><span>{children}</span><Arrow weight="bold" /></button>;
}

function Button({ children, variant = "primary", icon, className, ...props }) {
  return (
    <button className={cx("button", `button--${variant}`, className)} {...props}>
      <span>{children}</span>{icon}
    </button>
  );
}

function SplashStartButton({ onClick }) {
  return (
    <button className="splash-start-button" type="button" onClick={onClick}>
      <span>开始练习</span>
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <polygon points="4,4 12,12 4,20" />
        <polygon points="9,4 17,12 9,20" />
        <polygon points="14,4 22,12 14,20" />
      </svg>
    </button>
  );
}

function Splash({ onStart, onLogin }) {
  return (
    <main className="splash">
      <header className="splash__header">
        <Brand />
        <button className="text-button" onClick={onLogin}>登录</button>
      </header>
      <section className="splash__hero">
        <p className="eyebrow">AI ENGLISH SPEAKING PARTNER</p>
        <h1>Speak More,<br />Speak Better.</h1>
        <p className="splash__cn">越说，越会说。</p>
        <p className="splash__copy">和懂你的 AI 老师自然对话，在低压力的练习中慢慢建立表达自信。</p>
        <SplashStartButton onClick={onStart} />
      </section>
      <footer className="splash__footer"><span>语你说</span><span>Web · Desktop</span></footer>
    </main>
  );
}

function Auth({ mode: initialMode, onBack, onSuccess }) {
  const [mode, setMode] = useState(initialMode || "signup");
  const [showPassword, setShowPassword] = useState(false);
  const [sent, setSent] = useState(false);
  if (sent) {
    return (
      <main className="auth-layout">
        <aside className="auth-layout__aside"><Brand /><div><p className="eyebrow">ONE STEP LEFT</p><h2>先验证邮箱，<br />再开始第一次对话。</h2></div><p>语你说 · UniSpeaking</p></aside>
        <section className="auth-panel verify-panel">
          <div className="verify-icon"><EnvelopeSimple /></div>
          <p className="eyebrow">CHECK YOUR INBOX</p>
          <h1>验证你的邮箱</h1>
          <p>验证邮件已发送至 <strong>hello@example.com</strong>。完成验证后即可进入产品。</p>
          <Button onClick={onSuccess}>我已完成验证</Button>
          <button className="text-button">重新发送邮件</button>
        </section>
      </main>
    );
  }
  return (
    <main className="auth-layout">
      <aside className="auth-layout__aside">
        <Brand />
        <div><p className="eyebrow">SPEAK WITH EASE</p><h2>不用准备好，<br />也可以先开口。</h2><p>每一次自然表达，都是进步。</p></div>
        <p>语你说 · UniSpeaking</p>
      </aside>
      <section className="auth-panel">
        <button className="back-link" onClick={onBack}><ArrowLeft />返回</button>
        <div className="auth-panel__heading"><h1>{mode === "signup" ? "创建账号" : "欢迎回来"}</h1><p>{mode === "signup" ? "用邮箱注册，开始你的口语练习。" : "继续上一次的学习进度。"}</p></div>
        <form onSubmit={(event) => { event.preventDefault(); mode === "signup" ? setSent(true) : onSuccess(); }}>
          <label>邮箱<input type="email" placeholder="name@example.com" required /></label>
          <label>密码<span className="password-field"><input type={showPassword ? "text" : "password"} placeholder="至少 8 位字符" required /><button type="button" aria-label={showPassword ? "隐藏密码" : "显示密码"} onClick={() => setShowPassword(!showPassword)}>{showPassword ? <EyeSlash /> : <Eye />}</button></span></label>
          {mode === "login" && <button type="button" className="forgot-link">忘记密码？</button>}
          <Button className="auth-submit" type="submit">{mode === "signup" ? "注册并验证邮箱" : "登录"}</Button>
        </form>
        <p className="auth-switch">{mode === "signup" ? "已经有账号？" : "还没有账号？"}<button onClick={() => setMode(mode === "signup" ? "login" : "signup")}>{mode === "signup" ? "直接登录" : "创建账号"}</button></p>
      </section>
    </main>
  );
}

function LevelSetup({ selected, onSelect, onNext }) {
  return (
    <main className="setup-page">
      <header><Brand /><span>1 / 2</span></header>
      <section className="setup-card">
        <p className="eyebrow">A SIMPLE START</p><h1>你现在说英语时，<br />更接近哪种状态？</h1><p className="setup-lead">没有测试，也没有标准答案。这个选择只用于匹配对话难度。</p>
        <div className="level-options">
          {levels.map((level, index) => <button key={level.id} className={cx("level-option", selected === level.id && "is-selected")} onClick={() => onSelect(level.id)}><span className="level-option__number">0{index + 1}</span><span><strong>{level.title}</strong><small>{level.note}</small></span>{selected === level.id && <Check weight="bold" />}</button>)}
        </div>
        <ExpandingCta className="setup-next" disabled={!selected} onClick={onNext}>下一步</ExpandingCta>
      </section>
    </main>
  );
}

function TeacherSetup({ selectedId, onSelect, onFinish }) {
  const activeIndex = teachers.findIndex((teacher) => teacher.id === selectedId);
  const active = teachers[activeIndex];
  return (
    <main className="teacher-setup">
      <header><Brand /><span>2 / 2</span></header>
      <section className="teacher-heading"><p className="eyebrow">CHOOSE YOUR PARTNER</p><h1>选择一位 AI 老师</h1><p>每位老师都有固定口音和陪练方式，之后可在设置中更换。</p></section>
      <div className="coverflow" aria-label="AI 老师选择">
        {teachers.map((teacher, index) => {
          let distance = index - activeIndex;
          if (distance > teachers.length / 2) distance -= teachers.length;
          if (distance < -teachers.length / 2) distance += teachers.length;
          const absDistance = Math.abs(distance);
          const visible = absDistance <= 2;
          return (
            <button
              key={teacher.id}
              className={cx("teacher-card", distance === 0 && "is-active", !visible && "is-hidden")}
              style={{ "--distance": distance, "--abs-distance": absDistance }}
              onClick={() => onSelect(teacher.id)}
              aria-label={`选择 ${teacher.name}`}
              aria-hidden={!visible}
              tabIndex={visible ? 0 : -1}
            >
              <img src={teacher.image} alt={teacher.name} />
              <span className="teacher-card__meta"><strong>{teacher.name}</strong><small>{teacher.accent} · {teacher.personality}</small></span>
            </button>
          );
        })}
      </div>
      <section className="teacher-detail">
        <span className="teacher-detail__spacer" aria-hidden="true" />
        <div className="teacher-detail__audition"><AudioToggle mini label={`${active.name} 的自我介绍`} /><p>“{active.intro}”</p></div>
        <ExpandingCta className="teacher-cta teacher-gradient-cta" onClick={onFinish}>选择这位老师</ExpandingCta>
      </section>
    </main>
  );
}

function AppShell({ page, setPage, teacher, children }) {
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const items = [
    { id: "conversation", label: "自由对话", icon: Waveform },
    { id: "scenes", label: "场景广场", icon: SquaresFour },
    { id: "assets", label: "学习资产", icon: BookOpenText },
  ];
  const activePage = ["ielts", "interview"].includes(page) ? "scenes" : ["ielts-assets", "interview-assets"].includes(page) ? "assets" : page;
  return (
    <div className={cx("app-shell", sidebarOpen && "is-sidebar-open")}>
      <aside className={cx("sidebar", sidebarOpen && "is-open")} onMouseEnter={() => setSidebarOpen(true)} onMouseLeave={() => setSidebarOpen(false)}>
        <Brand compact={!sidebarOpen} />
        <nav>{items.map(({ id, label, icon: Icon }) => <button key={id} className={cx("sidebar__item", activePage === id && "is-active")} onClick={() => setPage(id)} aria-label={label} title={label}><Icon weight={activePage === id ? "bold" : "regular"} /><span className="sidebar__label"><span>{label}</span></span></button>)}</nav>
        <button className={cx("sidebar__avatar", ["profile", "membership", "settings"].includes(page) && "is-active")} onClick={() => setPage("profile")}><img src={teacher.image} alt="个人中心" /></button>
      </aside>
      <div className="app-main">{children}</div>
    </div>
  );
}

function PageHeader({ eyebrow, title, subtitle, action }) {
  return <header className="page-header"><div>{eyebrow && <p className="eyebrow">{eyebrow}</p>}<h1>{title}</h1>{subtitle && <p>{subtitle}</p>}</div>{action}</header>;
}

function LevelSelect({ value, onChange }) {
  const [open, setOpen] = useState(false);
  const selectRef = useRef(null);
  const closeTimerRef = useRef(null);
  const selectedLevel = levels.find((item) => item.id === value) || levels[0];

  useEffect(() => {
    const handlePointerDown = (event) => {
      if (!selectRef.current?.contains(event.target)) setOpen(false);
    };
    const handleKeyDown = (event) => {
      if (event.key === "Escape") setOpen(false);
    };
    document.addEventListener("pointerdown", handlePointerDown);
    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("pointerdown", handlePointerDown);
      document.removeEventListener("keydown", handleKeyDown);
      window.clearTimeout(closeTimerRef.current);
    };
  }, []);

  const selectLevel = (levelId) => {
    onChange(levelId);
    window.clearTimeout(closeTimerRef.current);
    closeTimerRef.current = window.setTimeout(() => setOpen(false), 180);
  };

  return (
    <div ref={selectRef} className={cx("level-select", open && "is-open")}>
      <button id="conversation-level" className="level-select__trigger" type="button" aria-haspopup="listbox" aria-expanded={open} onClick={() => setOpen(!open)}>
        <span><strong>{selectedLevel.title}</strong><small>{selectedLevel.note}</small></span>
        <CaretDown weight="bold" />
      </button>
      {open && (
        <div className="level-select__menu" role="listbox" aria-label="英语水平">
          {levels.map((item, index) => (
            <button key={item.id} type="button" role="option" aria-selected={item.id === value} className={cx("level-select__option", item.id === value && "is-selected")} style={{ "--option-index": index }} onClick={() => selectLevel(item.id)}>
              <span><strong>{item.title}</strong><small>{item.note}</small></span>
              {item.id === value && <Check weight="bold" />}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}

const speedOptions = ["慢一些", "适中", "自然", "快一些"];

function SpeedSelector({ value, onChange, className }) {
  const speedIndex = Math.max(0, speedOptions.indexOf(value));
  return (
    <div className={cx("conversation-settings__segment", className)} style={{ "--speed-index": speedIndex }}>
      <span className="conversation-settings__segment-indicator" aria-hidden="true" />
      {speedOptions.map((item) => (
        <button key={item} type="button" className={value === item ? "is-active" : ""} onClick={() => onChange(item)}>{item}</button>
      ))}
    </div>
  );
}

function TeacherSelector({ selectedId, onSelect, className }) {
  return (
    <div className={cx("conversation-settings__teachers", className)}>
      {teachers.map((item) => (
        <button key={item.id} type="button" className={selectedId === item.id ? "is-active" : ""} onClick={() => onSelect(item)}>
          <img src={item.image} alt="" />
          <span><strong>{item.name}</strong><small>{item.accent} · {item.personality}</small></span>
          <Headphones aria-hidden="true" />
        </button>
      ))}
    </div>
  );
}

function ConversationSettings({ speed, level, teacher, onSave, onClose }) {
  const [draftSpeed, setDraftSpeed] = useState(speed);
  const [draftLevel, setDraftLevel] = useState(level || "basic");
  const [draftTeacherId, setDraftTeacherId] = useState(teacher.id);
  return (
    <section className="conversation-settings" role="dialog" aria-modal="false" aria-labelledby="conversation-settings-title">
      <button className="conversation-settings__close" aria-label="关闭对话设置" onClick={onClose}><X /></button>
      <div className="conversation-settings__heading"><h2 id="conversation-settings-title">对话设置</h2><p>调整后会从下一次对话开始生效。</p></div>
      <div className="conversation-settings__group"><label>对话语速</label><SpeedSelector value={draftSpeed} onChange={setDraftSpeed} /></div>
      <div className="conversation-settings__group"><label htmlFor="conversation-level">英语水平</label><LevelSelect value={draftLevel} onChange={setDraftLevel} /></div>
      <div className="conversation-settings__group"><label>AI 老师</label><TeacherSelector selectedId={draftTeacherId} onSelect={(item) => setDraftTeacherId(item.id)} /></div>
      <div className="conversation-settings__actions"><button onClick={onClose}>取消</button><button className="is-primary" onClick={() => onSave({ speed: draftSpeed, level: draftLevel, teacher: teachers.find((item) => item.id === draftTeacherId) })}>保存设置</button></div>
    </section>
  );
}

function Conversation({ teacher, speed, level, onSettingsChange }) {
  const [inCall, setInCall] = useState(false);
  const [callState, setCallState] = useState("idle");
  const [callStatus, setCallStatus] = useState("准备开始");
  const [callError, setCallError] = useState("");
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [subtitles, setSubtitles] = useState(true);
  const [paused, setPaused] = useState(false);
  const [translated, setTranslated] = useState([]);
  const [lines, setLines] = useState([]);
  const clientRef = useRef(null);
  const remoteAudioRef = useRef(null);
  const transcriptRef = useRef(null);
  const transcriptPinnedRef = useRef(true);
  const toggleTranslation = (index) => setTranslated((current) => current.includes(index) ? current.filter((item) => item !== index) : [...current, index]);
  const handleTranscriptScroll = () => {
    const transcript = transcriptRef.current;
    if (!transcript) return;
    transcriptPinnedRef.current = transcript.scrollHeight - transcript.scrollTop - transcript.clientHeight < 48;
  };

  const updateRealtimeTranscript = ({ id, who, delta = "", text = "", final = false }) => {
    const content = String(text || delta || "");
    if (!content) return;
    setLines((current) => {
      const lineId = id || `${who}-live`;
      const index = current.findIndex((line) => line.id === lineId && !line.final);
      if (index < 0) {
        const idSuffix = final ? `-${Date.now()}-${Math.random().toString(36).slice(2, 6)}` : "";
        return [...current, { id: `${lineId}${idSuffix}`, who, en: content, final }];
      }
      const next = [...current];
      next[index] = {
        ...next[index],
        id: final ? `${next[index].id}-final-${Date.now()}` : next[index].id,
        en: text || `${next[index].en}${delta}`,
        final,
      };
      return next;
    });
  };

  const handleRealtimeEvent = (event) => {
    if (event.type === "local.connecting") {
      setCallState("connecting");
      setCallStatus("正在连接模型");
      return;
    }
    if (event.type === "local.connected") {
      setCallState("connected");
      setCallStatus("正在等待模型会话");
      return;
    }
    if (event.type === "session.created") {
      setCallState("connected");
      setCallStatus("模型会话已建立");
      return;
    }
    if (event.type === "session.updated") {
      setCallState("active");
      setCallStatus("可以开始说了");
      return;
    }
    if (event.type === "conversation.item.input_audio_transcription.completed") {
      updateRealtimeTranscript({
        id: event.item_id || event.item?.id || "user-live",
        who: "你",
        text: event.transcript || event.text || "",
        final: true,
      });
      return;
    }
    if (
      event.type === "conversation.item.input_audio_transcription.delta"
      || event.type === "conversation.item.input_audio_transcription.text"
    ) {
      const preview = `${event.text || ""}${event.stash || ""}`;
      updateRealtimeTranscript({
        id: event.item_id || event.item?.id || "user-live",
        who: "你",
        ...(preview ? { text: preview } : { delta: event.delta || "" }),
      });
      return;
    }
    if (event.type === "response.audio_transcript.delta" || event.type === "response.text.delta") {
      updateRealtimeTranscript({
        id: event.item_id || event.response_id || "assistant-live",
        who: teacher.name,
        delta: event.delta || event.text || "",
      });
      return;
    }
    if (event.type === "response.audio_transcript.done") {
      updateRealtimeTranscript({
        id: event.item_id || event.response_id || "assistant-live",
        who: teacher.name,
        text: event.transcript || event.text || "",
        final: true,
      });
      return;
    }
    if (event.type === "input_audio_buffer.speech_started") {
      setCallStatus("正在听你说话");
      return;
    }
    if (event.type === "response.audio.delta") {
      setCallStatus(`${teacher.name} 正在回应`);
      return;
    }
    if (event.type === "local.paused") {
      setCallState("paused");
      setCallStatus("会话已暂停");
      return;
    }
    if (event.type === "local.resumed") {
      setCallState("active");
      setCallStatus("会话已恢复");
      return;
    }
    if (event.type === "local.interrupted") {
      setCallStatus("已打断当前回应");
      return;
    }
    if (event.type === "local.ended") {
      setInCall(false);
      setSubtitles(false);
      setPaused(false);
      setCallState("idle");
      setCallStatus("准备开始");
      clientRef.current = null;
      return;
    }
    if (event.type === "error" || event.type === "local.error") {
      setCallState("error");
      setCallError(event.message || event.error?.message || "实时会话发生错误");
      setCallStatus("连接异常");
    }
  };

  const getClient = () => {
    if (!clientRef.current) {
      clientRef.current = createRealtimeClient({
        onEvent: handleRealtimeEvent,
        onRemoteStream: (stream) => {
          if (!remoteAudioRef.current) return;
          remoteAudioRef.current.srcObject = stream;
          void remoteAudioRef.current.play().catch(() => {
            setCallStatus("点击页面后可播放 AI 声音");
          });
        },
      });
    }
    return clientRef.current;
  };

  const startConversation = async () => {
    setInCall(true);
    setPaused(false);
    setSubtitles(true);
    setLines([]);
    setTranslated([]);
    setCallError("");
    setCallState("connecting");
    setCallStatus("正在请求麦克风");
    try {
      await getClient().start({
        topic: `自由对话。老师：${teacher.name}。语速：${speed}。水平：${level || "basic"}。`,
      });
    } catch (error) {
      setCallState("error");
      setCallError(error instanceof Error ? error.message : "无法开始实时对话");
      setCallStatus("连接失败");
    }
  };

  const togglePaused = async () => {
    if (callState === "ended") return;
    const next = !paused;
    setPaused(next);
    if (next) await getClient().pause();
    else await getClient().resume();
  };

  const stopConversation = async () => {
    const client = clientRef.current;
    clientRef.current = null;
    setInCall(false);
    setSubtitles(false);
    setPaused(false);
    setCallState("idle");
    setCallStatus("准备开始");
    await client?.stop({ reason: "user_stop" });
  };

  useEffect(() => () => {
    void clientRef.current?.stop({ notifyBackend: false, reason: "component_unmount" });
  }, []);

  useEffect(() => {
    if (!subtitles) return undefined;
    transcriptPinnedRef.current = true;
    const frame = requestAnimationFrame(() => {
      const transcript = transcriptRef.current;
      if (transcript) transcript.scrollTop = transcript.scrollHeight;
    });
    return () => cancelAnimationFrame(frame);
  }, [subtitles]);

  useEffect(() => {
    if (!subtitles || !transcriptPinnedRef.current) return undefined;
    const frame = requestAnimationFrame(() => {
      const transcript = transcriptRef.current;
      if (transcript && transcriptPinnedRef.current) transcript.scrollTop = transcript.scrollHeight;
    });
    return () => cancelAnimationFrame(frame);
  }, [subtitles, lines]);

  if (!inCall) return (
    <main className="conversation standby">
      <div className="conversation__top conversation__top--standby"><button className="dialog-settings-button" aria-expanded={settingsOpen} onClick={() => setSettingsOpen(!settingsOpen)}><GearSix className="dialog-settings-button__icon" /><span>对话设置</span></button></div>
      {settingsOpen && <ConversationSettings speed={speed} level={level} teacher={teacher} onClose={() => setSettingsOpen(false)} onSave={(settings) => { onSettingsChange(settings); setSettingsOpen(false); }} />}
      <section className="standby__center">
        <div className="portrait portrait--large"><img src={teacher.image} alt={teacher.name} /></div>
        <p className="eyebrow">{teacher.name.toUpperCase()} · {teacher.accent}</p>
        <h1>想聊什么都可以</h1>
        <p>像打电话一样自然开口</p>
        {callError && <p className="call-error">{callError}</p>}
        <ExpandingCta className="standby__cta" onClick={startConversation}>开始对话</ExpandingCta>
      </section>
      <p className="privacy-note"><ShieldCheck />自由对话内容不会保存</p>
    </main>
  );
  return (
    <main className={cx("conversation call", subtitles && "call--subtitles")}>
      <audio ref={remoteAudioRef} className="remote-audio" autoPlay playsInline />
      <div className="conversation__top conversation__top--empty" />
      <section className="call__stage">
        <div className={cx("call-presence", subtitles && "call-presence--compact")}>
          <div className={cx("portrait", subtitles ? "portrait--small" : "portrait--call")}><img src={teacher.image} alt={teacher.name} /></div>
          <div className={cx("listening-state", subtitles && "listening-state--compact")}>
            <VoiceWaveform active={!paused && callState !== "connecting" && callState !== "ended"} compact={subtitles} />
            <CallTimer state={callState} paused={paused} />
            {!subtitles && <span>{callStatus}</span>}
          </div>
        </div>
        {subtitles && <CallTranscript lines={lines} translated={translated} onToggleTranslation={toggleTranslation} transcriptRef={transcriptRef} onScroll={handleTranscriptScroll} emptyStatus={callStatus} />}
        {callError && <p className="call-error">{callError}</p>}
      </section>
      <CallControls paused={paused} onToggleMicrophone={togglePaused} onEnd={stopConversation} disabled={callState === "ended"} subtitles={subtitles} onToggleSubtitles={() => setSubtitles(!subtitles)} />
    </main>
  );
}

function Scenes({ onStartTraining, onIelts, onInterview }) {
  const [prompt, setPrompt] = useState("");
  const [preview, setPreview] = useState(null);
  const [generating, setGenerating] = useState(false);
  const [specialtyOpen, setSpecialtyOpen] = useState(false);
  const examples = ["餐厅点餐并说明忌口", "商场退换一件商品", "问路并确认交通方式", "预约理发并说明需求"];
  const generate = () => {
    if (!prompt.trim() || generating) return;
    setGenerating(true);
    window.setTimeout(() => {
      setPreview({ title: "我的专属练习", task: prompt.trim(), duration: "约 10 分钟", role: "你与场景角色" });
      setGenerating(false);
    }, 700);
  };
  return (
    <main className="page page--scenes">
      <PageHeader title="场景广场" subtitle="把真实生活中的需求，变成高质量的口语练习。" />
      <div className="scene-plaza-content section-block">
        <section className="scene-builder scene-builder--featured scene-module">
          <div className="scene-section-heading scene-section-heading--primary">
            <div><p className="eyebrow">CREATE YOUR OWN</p><h2>创建专属场景</h2><p>用一句话描述你想练习的真实情境，AI 会为你整理角色、目标与表达任务。</p></div>
            <div className={cx("scene-specialty-menu", specialtyOpen && "is-open")}>
              <button type="button" className="scene-specialty-menu__trigger" aria-haspopup="menu" aria-expanded={specialtyOpen} onClick={() => setSpecialtyOpen(!specialtyOpen)}>专项训练<CaretDown weight="bold" /></button>
              {specialtyOpen && <div className="scene-specialty-menu__popover" role="menu">
                <button type="button" role="menuitem" onClick={() => { setSpecialtyOpen(false); onIelts(); }}><span className="scene-specialty-menu__icon"><BookOpenText /></span><span><strong>雅思口语</strong><small>Part 1 / 2 / 3 与全真模考</small></span><ArrowRight /></button>
                <button type="button" role="menuitem" onClick={() => { setSpecialtyOpen(false); onInterview(); }}><span className="scene-specialty-menu__icon"><Briefcase /></span><span><strong>英文面试</strong><small>根据简历与岗位进行模拟</small></span><ArrowRight /></button>
              </div>}
            </div>
          </div>
          <div className={cx("scene-input", prompt.trim() && "has-content")}>
            <textarea value={prompt} maxLength={200} onChange={(event) => setPrompt(event.target.value)} placeholder="你今天想练习什么？例如：第一次去健身房，咨询设施、开放时间和会员体验" />
            <div className="scene-input__footer">
              <div className="example-chips"><small>快速开始</small>{examples.map((example) => <button key={example} onClick={() => setPrompt(example)}>{example}</button>)}</div>
              <div className="scene-input__controls"><span>{prompt.length}/200</span><ExpandingCta className={cx("scene-generate", generating && "is-generating")} disabled={!prompt.trim() || generating} onClick={generate}>{generating ? <><NewtonsCradle size={22} className="newtons-cradle--inline" label="正在生成练习场景" />正在生成</> : "生成练习场景"}</ExpandingCta></div>
            </div>
          </div>
        </section>

        <section className="recommendations scene-module">
          <div className="scene-section-heading"><div><p className="eyebrow">NEED INSPIRATION?</p><h2>从推荐场景开始</h2></div><p>还没想好？选择一个常用场景直接练习。</p></div>
          <div className="recommendation-list">{recommendations.map((item) => <article key={item.id}><span className="recommendation__number">{item.number}</span><span className="recommendation__title"><span className="tag">{item.tag}</span><strong>{item.title}</strong></span><small>{item.duration}<i>·</i>{item.level}</small><p>{item.goal}</p><ExpandingCta className="scene-card-cta" onClick={() => onStartTraining(item.title)}>开始练习</ExpandingCta></article>)}</div>
        </section>
      </div>
      {preview && <Modal onClose={() => setPreview(null)}><p className="eyebrow">SCENE READY</p><h2>练习场景已生成</h2><p className="modal-lead">确认信息后进入“学—读—说”训练流程。</p><dl className="scene-summary"><div><dt>场景</dt><dd>{preview.title}</dd></div><div><dt>你的任务</dt><dd>{preview.task}</dd></div><div><dt>角色</dt><dd>{preview.role}</dd></div><div><dt>预计时间</dt><dd>{preview.duration}</dd></div></dl><div className="modal-actions"><Button variant="secondary" onClick={() => setPreview(null)}>返回修改</Button><Button onClick={() => onStartTraining(preview.title)} icon={<ArrowRight />}>确认并开始</Button></div></Modal>}
    </main>
  );
}

function Modal({ children, onClose, wide = false, dismissible = true, className }) {
  return <div className="modal-backdrop" onMouseDown={dismissible ? onClose : undefined}><div className={cx("modal", wide && "modal--wide", className)} role="dialog" aria-modal="true" onMouseDown={(event) => event.stopPropagation()}>{dismissible && <button className="modal__close" aria-label="关闭" onClick={onClose}><X /></button>}{children}</div></div>;
}

const completedSimulationMetrics = [
  { label: "发音清晰度", value: 84 },
  { label: "流利度", value: 78 },
  { label: "表达完整度", value: 91 },
  { label: "互动回应", value: 86 },
  { label: "自然度", value: 80 },
];

const incompleteSimulationMetrics = [
  { label: "发音清晰度", value: 72 },
  { label: "流利度", value: 64 },
  { label: "表达完整度", value: 70 },
  { label: "互动回应", value: 62 },
  { label: "自然度", value: 68 },
];

function RadarChart({ metrics }) {
  const canvasRef = useRef(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const size = 320;
    const density = window.devicePixelRatio || 1;
    canvas.width = size * density;
    canvas.height = size * density;
    const context = canvas.getContext("2d");
    context.scale(density, density);
    context.clearRect(0, 0, size, size);

    const center = size / 2;
    const radius = 92;
    const angleAt = (index) => -Math.PI / 2 + index * (Math.PI * 2 / metrics.length);
    const pointAt = (index, pointRadius) => ({
      x: center + Math.cos(angleAt(index)) * pointRadius,
      y: center + Math.sin(angleAt(index)) * pointRadius,
    });
    const traceShape = (pointRadius) => {
      context.beginPath();
      metrics.forEach((_, index) => {
        const point = pointAt(index, pointRadius);
        if (index === 0) context.moveTo(point.x, point.y);
        else context.lineTo(point.x, point.y);
      });
      context.closePath();
    };

    context.lineWidth = 1;
    context.strokeStyle = "#deded9";
    for (let level = 1; level <= 4; level += 1) {
      traceShape(radius * level / 4);
      context.stroke();
    }
    metrics.forEach((_, index) => {
      const point = pointAt(index, radius);
      context.beginPath();
      context.moveTo(center, center);
      context.lineTo(point.x, point.y);
      context.stroke();
    });

    context.beginPath();
    metrics.forEach((metric, index) => {
      const point = pointAt(index, radius * metric.value / 100);
      if (index === 0) context.moveTo(point.x, point.y);
      else context.lineTo(point.x, point.y);
    });
    context.closePath();
    context.fillStyle = "rgba(21, 21, 21, .15)";
    context.strokeStyle = "#151515";
    context.lineWidth = 2;
    context.fill();
    context.stroke();

    context.fillStyle = "#151515";
    metrics.forEach((metric, index) => {
      const point = pointAt(index, radius * metric.value / 100);
      context.beginPath();
      context.arc(point.x, point.y, 3.5, 0, Math.PI * 2);
      context.fill();
    });

    context.font = '600 14px Inter, "PingFang SC", sans-serif';
    context.textBaseline = "middle";
    metrics.forEach((metric, index) => {
      const labelPoint = pointAt(index, 126);
      context.textAlign = Math.abs(labelPoint.x - center) < 10 ? "center" : labelPoint.x < center ? "right" : "left";
      context.fillText(metric.label, labelPoint.x, labelPoint.y);
    });
  }, [metrics]);

  return <canvas ref={canvasRef} role="img" aria-label={`五维雷达图：${metrics.map((metric) => `${metric.label} ${metric.value} 分`).join("，")}`} />;
}

function ResultModal({ completed, onBack, onAssets }) {
  const metrics = completed ? completedSimulationMetrics : incompleteSimulationMetrics;
  const totalScore = completed ? 84 : 68;
  return (
    <Modal wide dismissible={false} className="result-modal">
      <header className="result-modal__header">
        <div><p className="eyebrow">SIMULATION COMPLETE</p><h2>{completed ? "模拟完成" : "本次模拟已结束"}</h2><p className="result-modal__lead">{completed ? "你顺利完成了本次模拟，五个维度的表现如下。" : "本次对话已保存，下面是截至结束时的五维表现。"}</p></div>
        <div className="result-modal__score"><strong>{totalScore}</strong><span>/100</span></div>
      </header>
      <section className="result-modal__overview">
        <div className="result-radar"><RadarChart metrics={metrics} /></div>
        <ul className="result-metrics">{metrics.map((metric) => <li key={metric.label}><span>{metric.label}</span><strong>{metric.value}</strong></li>)}</ul>
      </section>
      <div className="result-modal__actions"><ExpandingCta direction="back" className="result-action result-action--light" onClick={onBack}>返回场景广场</ExpandingCta><ExpandingCta className="result-action result-action--dark" onClick={onAssets}>前往学习资产</ExpandingCta></div>
    </Modal>
  );
}

const readFocusWords = ["recommend", "trying", "oat", "recommend"];

function HighlightedReadSentence({ sentence, focus }) {
  const start = sentence.toLowerCase().indexOf(focus.toLowerCase());
  if (start < 0) return sentence;
  return <>{sentence.slice(0, start)}<mark>{sentence.slice(start, start + focus.length)}</mark>{sentence.slice(start + focus.length)}</>;
}

function ReadScoreModal({ feedback, onClose }) {
  const feedbackItem = learningItems[feedback.index];
  return (
    <Modal dismissible={false} className="read-score-modal">
      <div className="read-score-modal__score"><strong>{feedback.score}</strong><span>/100</span></div>
      <h2>本句发音评估</h2>
      <p className="read-score-modal__lead">{feedback.score >= 70 ? "整体清晰，再留意标记部分的重音与连读。" : "再留意标记部分的重音与连读，然后重新朗读。"}</p>
      <div className="read-score-modal__focus"><small>发音定位</small><p><HighlightedReadSentence sentence={feedbackItem.en} focus={readFocusWords[feedback.index]} /></p></div>
      <button type="button" className="read-score-modal__confirm" onClick={onClose}>知道了</button>
    </Modal>
  );
}

function Training({ sceneTitle, teacher, initialStep = "learn", standaloneSpeak = false, result, onExit, onComplete, onBack, onAssets }) {
  const steps = [
    { id: "learn", label: "学" },
    { id: "read", label: "读" },
    { id: "speak", label: "说" },
  ];
  const initialStepIndex = Math.max(0, steps.findIndex((item) => item.id === initialStep));
  const [step, setStep] = useState(initialStep);
  const [unlockedStepIndex, setUnlockedStepIndex] = useState(initialStepIndex);
  const [completedSteps, setCompletedSteps] = useState(() => steps.slice(0, initialStepIndex).map((item) => item.id));
  const [learnIndex, setLearnIndex] = useState(0);
  const [readIndex, setReadIndex] = useState(0);
  const [learnedItems, setLearnedItems] = useState([]);
  const [readScores, setReadScores] = useState({});
  const [heardReadDemos, setHeardReadDemos] = useState([]);
  const [readFeedback, setReadFeedback] = useState(null);
  const [speakingLines, setSpeakingLines] = useState(2);
  const [simulationPaused, setSimulationPaused] = useState(false);
  const [simulationTranslated, setSimulationTranslated] = useState([]);
  const itemIndex = step === "read" ? readIndex : learnIndex;
  const item = learningItems[itemIndex];
  const score = readScores[readIndex] ?? null;
  const completeStep = (id) => setCompletedSteps((current) => current.includes(id) ? current : [...current, id]);
  const goToStep = (id) => {
    const targetIndex = steps.findIndex((item) => item.id === id);
    if (targetIndex <= unlockedStepIndex) setStep(id);
  };
  const nextLearn = () => {
    setLearnedItems((current) => current.includes(learnIndex) ? current : [...current, learnIndex]);
    if (learnIndex < learningItems.length - 1) setLearnIndex(learnIndex + 1);
    else {
      completeStep("learn");
      setUnlockedStepIndex((current) => Math.max(current, 1));
      setStep("read");
    }
  };
  const submitRead = () => {
    const nextScore = readIndex === 1 && score === null ? 68 : 86;
    setReadScores((current) => ({ ...current, [readIndex]: nextScore }));
    setReadFeedback({ index: readIndex, score: nextScore });
  };
  const playReadDemo = () => {
    setHeardReadDemos((current) => current.includes(readIndex) ? current : [...current, readIndex]);
  };
  const nextRead = () => {
    if ((readScores[readIndex] ?? 0) < 70) return;
    if (readIndex < learningItems.length - 1) setReadIndex(readIndex + 1);
    else {
      completeStep("read");
      setUnlockedStepIndex((current) => Math.max(current, 2));
      setStep("speak");
    }
  };
  const simulationTranscript = [
    { id: "simulation-assistant-1", who: teacher.name, en: "Hi! What can I get started for you today?", zh: "你好！今天想先来点什么？" },
    { id: "simulation-user-1", who: "你", en: "Could you recommend something less sweet?", zh: "你能推荐一些不太甜的吗？" },
    ...(speakingLines >= 3 ? [{ id: "simulation-assistant-2", who: teacher.name, en: "Sure — how about a medium oat milk latte?", zh: "当然，中杯燕麦奶拿铁怎么样？" }] : []),
    ...(speakingLines >= 4 ? [{ id: "simulation-user-2", who: "你", en: "That sounds great. I’ll have that, thank you.", zh: "听起来不错，我就要这个，谢谢。" }] : []),
  ];
  return (
    <main className={cx("training-page", standaloneSpeak && "training-page--standalone")}>
      <header className="training-header"><div><strong>{sceneTitle}</strong><span>从语言到真实表达</span></div><button className="training-exit" aria-label="关闭训练" onClick={onExit}><span><X weight="bold" /></span></button></header>
      {!standaloneSpeak && <nav className="stepper" aria-label="练习进度">
        <span className="stepper__track" aria-hidden="true"><span style={{ width: `${unlockedStepIndex * 50}%` }} /></span>
        {steps.map((stepItem, index) => {
          const done = completedSteps.includes(stepItem.id);
          return <button key={stepItem.id} className={cx("stepper__item", step === stepItem.id && "is-active", done && "is-done")} disabled={index > unlockedStepIndex} onClick={() => goToStep(stepItem.id)}><span className="stepper__check">{done ? <Check weight="bold" /> : index + 1}</span><span className="stepper__copy"><strong>{stepItem.label}</strong></span></button>;
        })}
      </nav>}
      {step === "learn" && <section className="training-workspace"><aside className="lesson-list"><div><span>本组语言</span><small>{learnIndex + 1} / {learningItems.length}</small></div>{learningItems.map((learningItem, index) => <button key={learningItem.en} className={cx(index === learnIndex && "is-active", learnedItems.includes(index) && "is-done")} onClick={() => setLearnIndex(index)}><small>{learningItem.type}</small><strong>{learningItem.en}</strong><span>{learnedItems.includes(index) ? <Check weight="bold" /> : index + 1}</span></button>)}</aside><article className="learn-stage"><small>{item.type}</small><h1>{item.en}</h1><div className="pronunciation"><span>/ˌrekəˈmend/</span><AudioToggle mini label={`${item.en} 的发音`} /></div><p>{item.zh}</p><div className="stage-footer"><ExpandingCta direction="back" disabled={learnIndex === 0} onClick={() => setLearnIndex(learnIndex - 1)}>上一个</ExpandingCta><ExpandingCta onClick={nextLearn}>{learnIndex === learningItems.length - 1 ? "进入朗读" : "下一个"}</ExpandingCta></div></article></section>}
      {step === "read" && <section className="training-workspace"><aside className="lesson-list"><div><span>完整表达</span><small>{readIndex + 1} / {learningItems.length}</small></div>{learningItems.map((learningItem, index) => <button key={learningItem.en} className={cx(index === readIndex && "is-active", (readScores[index] ?? 0) >= 70 && "is-done")} onClick={() => setReadIndex(index)}><small>句子</small><strong>{learningItem.en}</strong><span>{(readScores[index] ?? 0) >= 70 ? <Check weight="bold" /> : index + 1}</span></button>)}</aside><article className="read-stage"><h1>{item.en}</h1><p>{item.zh}</p><div className="rhythm"><span>节奏重点</span><strong>{item.en.split(" ").slice(0, 4).join(" · ")}</strong></div><MicrophoneToggle label="朗读麦克风" onActivate={submitRead} /><h3>{score === null ? "轮到你说" : score >= 70 ? "朗读通过" : "再试一次"}</h3>{score === null && <p>尽量完整、连贯地说出整句话。</p>}<button type="button" className="read-replay" onClick={playReadDemo}><SpeakerHigh weight="fill" />{heardReadDemos.includes(readIndex) ? "再听一次标准示范" : "听标准示范"}</button>{score >= 70 && <div className="stage-footer read-stage-footer"><span /><ExpandingCta onClick={nextRead}>{readIndex === learningItems.length - 1 ? "进入模拟" : "下一句"}</ExpandingCta></div>}</article></section>}
      {step === "speak" && <section className="simulation call call--subtitles"><section className="call__stage"><div className="call-presence call-presence--compact"><div className="portrait portrait--small"><img src={teacher.image} alt={teacher.name} /></div><div className="listening-state listening-state--compact"><VoiceWaveform active={!simulationPaused && !result} compact /><CallTimer paused={simulationPaused} /></div></div><CallTranscript lines={simulationTranscript} translated={simulationTranslated} onToggleTranslation={(index) => setSimulationTranslated((current) => current.includes(index) ? current.filter((item) => item !== index) : [...current, index])} className="simulation__transcript" /></section><CallControls paused={simulationPaused} onToggleMicrophone={() => { setSimulationPaused((current) => !current); setSpeakingLines((current) => Math.min(4, current + 1)); }} onEnd={() => onComplete(speakingLines >= 4)} showSubtitles={false} /></section>}
      {result && <ResultModal completed={result.completed} onBack={onBack} onAssets={onAssets} />}
      {readFeedback && <ReadScoreModal feedback={readFeedback} onClose={() => setReadFeedback(null)} />}
    </main>
  );
}

const assetConversation = [
  { id: 1, role: "assistant", speaker: "店员", text: "Would you like me to recommend something?" },
  {
    id: 2,
    role: "user",
    speaker: "你",
    text: "I feel like to try something different today.",
    feedback: {
      correction: "I feel like trying something different today.",
      note: "feel like 后面需要接动名词，而不是不定式。",
      suggestion: "I'd love to try something new today.",
    },
  },
  { id: 3, role: "assistant", speaker: "店员", text: "Sure — how about a medium oat milk latte?" },
  {
    id: 4,
    role: "user",
    speaker: "你",
    text: "Could you recommend me something less sweet?",
    feedback: {
      correction: "Could you recommend something less sweet?",
      note: "表达已经很清楚。这里省略 me 会更自然。",
      suggestion: "Could you suggest something that isn't too sweet?",
    },
  },
];

function AssetModuleMenu({ onSelect, onIelts, onInterview }) {
  return (
    <div className="asset-module-menu">
      <button className="asset-module-menu__trigger" type="button" aria-label="切换学习资产模块" aria-haspopup="menu">
        <SquaresFour weight="bold" />
        <span>其他资产</span>
        <CaretDown weight="bold" />
      </button>
      <div className="asset-module-menu__popover" role="menu">
        <button type="button" role="menuitem" onClick={onIelts}><span className="asset-module-ielts-mark">IELTS</span><span><strong>IELTS 学习资产</strong><small>评分、建议与今日复习</small></span><CaretRight /></button>
        <button type="button" role="menuitem" onClick={onInterview}><Briefcase /><span><strong>英文面试学习资产</strong><small>历史报告与口语复盘</small></span><CaretRight /></button>
      </div>
    </div>
  );
}

function AnimatedDeleteButton({ onClick }) {
  return (
    <button className="asset-delete-button" type="button" aria-label="删除当前学习资产" onClick={onClick}>
      <Trash weight="bold" />
      <span>删除</span>
    </button>
  );
}

function AssetFeedback({ feedback }) {
  return (
    <section className="asset-feedback" aria-label="AI 表达评价">
      <header><CheckCircle weight="fill" /><strong>AI 表达评价</strong></header>
      <div className="asset-feedback__correction"><span>建议表达</span><strong>{feedback.correction}</strong></div>
      <p><span>表达提示</span>{feedback.note}</p>
      <p><span>地道说法</span><em>{feedback.suggestion}</em></p>
    </section>
  );
}

function AssetPracticeMenu({ title, onPractice, onRestart }) {
  return (
    <div className="asset-practice-menu">
      <button className="asset-practice-menu__primary" type="button" onClick={() => onPractice(title)}><span className="asset-practice-menu__icon"><Play weight="fill" /></span><strong>复练场景</strong><CaretDown weight="bold" /></button>
      <div className="asset-practice-menu__popover">
        <button type="button" onClick={() => onRestart(title)}><ArrowLeft weight="bold" /><span><strong>重新学习</strong><small>从学、读、说第一步开始</small></span></button>
      </div>
    </div>
  );
}

function AssetConversationDetail({ record, onBack, onPractice, onRestart }) {
  return (
    <main className="page assets-page asset-conversation-page">
      <PageHeader
        title={`${record.title} · 语境复现`}
        subtitle="最近一次模拟对话已完整保留，每句表达都附有 AI 评价与更自然的说法。"
        action={<div className="asset-conversation-actions"><AssetPracticeMenu title={record.title} onPractice={onPractice} onRestart={onRestart} /><button className="training-exit asset-conversation-exit" type="button" aria-label="退出当前学习资产" onClick={onBack}><span><X weight="bold" /></span></button></div>}
      />
      <section className="asset-conversation-card">
        <div className="asset-conversation-card__label"><Translate weight="bold" />对话语境下的纠错与地道表达</div>
        <div className="asset-conversation-thread">
          {assetConversation.map((message) => (
            <article key={message.id} className={cx("asset-message", message.role === "user" && "is-user")}>
              <small>{message.speaker}</small>
              <p>{message.text}</p>
              {message.feedback && <AssetFeedback feedback={message.feedback} />}
            </article>
          ))}
        </div>
      </section>
    </main>
  );
}

function AssetModulePlaceholder({ module, onBack }) {
  return (
    <main className="page assets-page asset-module-placeholder">
      <PageHeader title={`${module} 学习资产`} subtitle="该模块将与专属学习路径保持一致，目前已预留独立入口。" action={<Button variant="secondary" onClick={onBack}>返回场景资产</Button>} />
      <section><div><LockKey weight="bold" /></div><p className="eyebrow">MODULE RESERVED</p><h2>专属资产模块已预留</h2><p>后续确定 {module} 的学习路径和资产结构后，将在这里直接接入。</p></section>
    </main>
  );
}

function Assets({ onPractice, onRestart, onIelts, onInterview, onOpenRecord, onCloseRecord, initialView = "home", initialRecordTitle }) {
  const sceneRecords = assetRecords.filter((record) => record.category === "普通场景");
  const initialRecord = sceneRecords.find((record) => record.title === initialRecordTitle) || sceneRecords[0];
  const [selected, setSelected] = useState(initialRecord);
  const [deleted, setDeleted] = useState([]);
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [reservedModule, setReservedModule] = useState(null);
  const records = sceneRecords.filter((record) => !deleted.includes(record.id));

  if (reservedModule) return <AssetModulePlaceholder module={reservedModule} onBack={() => setReservedModule(null)} />;
  if (initialView === "detail") return <AssetConversationDetail record={selected} onBack={onCloseRecord} onPractice={onPractice} onRestart={onRestart} />;

  const deleteSelected = () => {
    const remaining = records.filter((record) => record.id !== selected.id);
    setDeleted((current) => [...current, selected.id]);
    setDeleteOpen(false);
    setSelected(remaining[0] || sceneRecords[0]);
  };

  return (
    <main className="page assets-page">
      <PageHeader title="学习资产" subtitle="把场景练习中真正用过的表达，留在这里继续复习。" action={<AssetModuleMenu onSelect={setReservedModule} onIelts={onIelts} onInterview={onInterview} />} />
      <section className="asset-layout">
        <aside className="asset-list asset-list--history" aria-label="场景训练历史">
          <div className="asset-list__heading"><strong>训练记录</strong><span>{records.length} 条</span></div>
          {records.map((record) => <button key={record.id} className={selected.id === record.id ? "is-active" : ""} onClick={() => setSelected(record)}><small>{record.date} · {record.category}</small><strong>{record.title}</strong><em>{record.items} 个语言资产 · {record.status}{record.score && ` · ${record.score}`}</em></button>)}
          {!records.length && <div className="asset-list__empty">暂无场景学习资产</div>}
        </aside>
        <article className="asset-detail">
          <header>
            <div><p className="eyebrow">{selected.category}</p><h2>{selected.title}</h2><p>{selected.date} · {selected.status}</p></div>
            <div className="asset-detail__actions"><AnimatedDeleteButton onClick={() => setDeleteOpen(true)} /><ExpandingCta className="teacher-cta asset-open-button" onClick={onOpenRecord}>打开当前学习资产</ExpandingCta></div>
          </header>
          <div className="asset-items" aria-label="已保存的单词、短语和句子">
            {learningItems.map((item) => <div key={item.en}><span className="tag">{item.type}</span><p><strong>{item.en}</strong><small>{item.zh}</small></p><ScenePlaybackToggle label={`播放 ${item.en} 的发音`} /></div>)}
          </div>
        </article>
      </section>
      {deleteOpen && <Modal onClose={() => setDeleteOpen(false)}><p className="eyebrow">DELETE ASSET</p><h2>删除当前学习资产？</h2><p className="modal-lead">这条场景记录、对话和评分将一起删除，且无法恢复。</p><div className="modal-actions"><Button variant="secondary" onClick={() => setDeleteOpen(false)}>取消</Button><Button onClick={deleteSelected}>确认删除</Button></div></Modal>}
    </main>
  );
}

function Profile({ section, setSection, teacher, speed, level, onSettingsChange, onLogout }) {
  return (
    <main className="profile-layout"><aside className="profile-nav"><div className="profile-user"><img src={teacher.image} alt="Yufan" /><span><strong>Yufan</strong><small>yufan@example.com</small></span></div><nav><button className={section === "profile" ? "is-active" : ""} onClick={() => setSection("profile")}><User />个人概览</button><button className={section === "membership" ? "is-active" : ""} onClick={() => setSection("membership")}><Crown />会员权益</button><button className={section === "settings" ? "is-active" : ""} onClick={() => setSection("settings")}><SlidersHorizontal />助手设置</button></nav><button className="logout" onClick={onLogout}><SignOut />退出登录</button></aside><section className="profile-content">{section === "profile" && <Overview />}{section === "membership" && <Membership />}{section === "settings" && <Settings teacher={teacher} speed={speed} level={level} onSettingsChange={onSettingsChange} />}</section></main>
  );
}

const learningMonths = [
  {
    key: "2026-05", year: 2026, month: 4, label: "2026 年 5 月",
    records: { 2: [14, 1, 2], 5: [22, 2, 3], 8: [18, 1, 2], 12: [31, 2, 4], 13: [12, 1, 1], 17: [26, 2, 3], 20: [35, 3, 5], 24: [19, 1, 2], 27: [28, 2, 4], 30: [16, 1, 2] },
  },
  {
    key: "2026-06", year: 2026, month: 5, label: "2026 年 6 月",
    records: { 1: [20, 1, 2], 3: [28, 2, 3], 4: [16, 1, 2], 7: [34, 2, 4], 9: [15, 1, 2], 10: [25, 2, 3], 14: [38, 3, 5], 15: [18, 1, 2], 18: [31, 2, 4], 19: [12, 1, 1], 21: [27, 2, 3], 23: [35, 3, 5], 24: [22, 2, 3], 28: [29, 2, 4], 30: [17, 1, 2] },
  },
  {
    key: "2026-07", year: 2026, month: 6, label: "2026 年 7 月",
    records: { 1: [18, 1, 2], 2: [26, 2, 3], 4: [34, 2, 4], 5: [12, 1, 1], 7: [40, 3, 5], 8: [31, 2, 4], 9: [22, 2, 3], 11: [24, 2, 3], 12: [16, 1, 2], 14: [33, 2, 4], 15: [20, 1, 2], 16: [29, 2, 4], 17: [37, 3, 5], 18: [34, 2, 4], 19: [18, 1, 2], 20: [28, 2, 4] },
  },
];

const achievements = [
  { id: "first-talk", title: "初次开口", desc: "完成首次自由对话", category: "开口", progress: 1, total: 1, icon: MessageCircleMore },
  { id: "seven-days", title: "七日同行", desc: "连续学习 7 天", category: "连续", progress: 7, total: 7, icon: Footprints },
  { id: "scene-explorer", title: "场景探索者", desc: "完成 5 个不同场景", category: "场景", progress: 5, total: 5, icon: Compass },
  { id: "expression-star", title: "表达新星", desc: "单次表达评分达到 90", category: "成长", progress: 90, total: 90, icon: Sparkles },
  { id: "pronunciation", title: "发音校准师", desc: "完成 30 次跟读评分", category: "成长", progress: 18, total: 30, icon: AudioLines },
  { id: "asset-keeper", title: "资产收藏家", desc: "保存 20 个学习资产", category: "成长", progress: 12, total: 20, icon: PackageCheck },
  { id: "conversation-regular", title: "对话常客", desc: "累计完成 20 次对话", category: "开口", progress: 9, total: 20, icon: MessagesSquare },
  { id: "goal-hitter", title: "目标命中", desc: "连续 4 周完成周目标", category: "连续", progress: 2, total: 4, icon: Target },
  { id: "language-builder", title: "表达拓荒者", desc: "掌握 100 个实用表达", category: "成长", progress: 46, total: 100, icon: Languages },
  { id: "listener", title: "听力捕手", desc: "收听示范音频 50 次", category: "开口", progress: 32, total: 50, icon: LucideHeadphones },
  { id: "full-month", title: "月度全勤", desc: "单月完成 20 天打卡", category: "连续", progress: 16, total: 20, icon: CalendarCheck2 },
  { id: "speaking-master", title: "口语大师", desc: "解锁其余全部成就", category: "场景", progress: 4, total: 11, icon: Trophy },
];

function LearningCalendar() {
  const [monthIndex, setMonthIndex] = useState(learningMonths.length - 1);
  const [selectedDay, setSelectedDay] = useState(20);
  const month = learningMonths[monthIndex];
  const daysInMonth = new Date(month.year, month.month + 1, 0).getDate();
  const leadingDays = (new Date(month.year, month.month, 1).getDay() + 6) % 7;
  const selectedRecord = month.records[selectedDay];
  const changeMonth = (nextIndex) => {
    const nextMonth = learningMonths[nextIndex];
    const latestRecordedDay = Math.max(...Object.keys(nextMonth.records).map(Number));
    setMonthIndex(nextIndex);
    setSelectedDay(latestRecordedDay);
  };
  return (
    <article className="calendar-card">
      <header className="calendar-card__header">
        <div><p className="eyebrow">LEARNING CALENDAR</p><h2>学习日历</h2></div>
        <div className="calendar-month-switcher">
          <button type="button" aria-label="查看上一个月" disabled={monthIndex === 0} onClick={() => changeMonth(monthIndex - 1)}><ChevronLeft /></button>
          <strong aria-live="polite">{month.label}</strong>
          <button type="button" aria-label="查看下一个月" disabled={monthIndex === learningMonths.length - 1} onClick={() => changeMonth(monthIndex + 1)}><ChevronRight /></button>
        </div>
      </header>
      <div className="calendar-weekdays" aria-hidden="true">{["一", "二", "三", "四", "五", "六", "日"].map((day) => <span key={day}>周{day}</span>)}</div>
      <div className="calendar-days" role="grid" aria-label={`${month.label}学习记录`}>
        {Array.from({ length: leadingDays }, (_, index) => <span key={`blank-${index}`} className="calendar-blank" />)}
        {Array.from({ length: daysInMonth }, (_, index) => {
          const day = index + 1;
          const record = month.records[day];
          const isToday = month.key === "2026-07" && day === 20;
          return (
            <button key={day} type="button" role="gridcell" aria-label={`${month.label}${day}日${record ? `，已打卡 ${record[0]} 分钟` : "，无学习记录"}`} className={cx(record && "is-practiced", isToday && "is-today", selectedDay === day && "is-selected")} onClick={() => setSelectedDay(day)}>
              <span>{day}</span>{record && <i aria-hidden="true" />}{isToday && <small>今天</small>}
            </button>
          );
        })}
      </div>
      <div className={cx("calendar-summary", selectedRecord && "is-complete")}>
        <span className="calendar-summary__status"><CalendarCheck2 />{selectedRecord ? "已打卡" : "未打卡"}</span>
        <div><strong>{month.month + 1} 月 {selectedDay} 日</strong><small>{selectedRecord ? `${selectedRecord[0]} 分钟 · ${selectedRecord[1]} 次练习 · ${selectedRecord[2]} 个学习资产` : "这一天还没有学习记录"}</small></div>
      </div>
    </article>
  );
}

function AchievementSystem() {
  const [filter, setFilter] = useState("全部");
  const filtered = filter === "全部" ? achievements : achievements.filter((item) => item.category === filter);
  const unlockedCount = achievements.filter((item) => item.progress >= item.total).length;
  return (
    <section className="achievement-system">
      <header className="achievement-system__header">
        <div><p className="eyebrow">ACHIEVEMENTS</p><h2>成就图鉴</h2><p>每一种进步，都值得被单独记录。</p></div>
        <div className="achievement-overall"><span><strong>{unlockedCount}</strong><small>/ {achievements.length} 已获得</small></span><progress value={unlockedCount} max={achievements.length} /></div>
      </header>
      <nav className="achievement-filters" aria-label="成就分类">{["全部", "开口", "连续", "场景", "成长"].map((item) => <button key={item} type="button" className={filter === item ? "is-active" : ""} onClick={() => setFilter(item)}>{item}<small>{item === "全部" ? achievements.length : achievements.filter((achievement) => achievement.category === item).length}</small></button>)}</nav>
      <div className="achievement-grid" aria-live="polite">
        {filtered.map(({ icon: Icon, ...item }) => {
          const unlocked = item.progress >= item.total;
          const percentage = Math.min(100, Math.round((item.progress / item.total) * 100));
          return <article key={item.id} className={cx("achievement-card", unlocked && "is-unlocked")}><div className="achievement-card__icon"><Icon strokeWidth={1.8} /></div><div className="achievement-card__copy"><span><strong>{item.title}</strong><em>{unlocked ? "已获得" : `${percentage}%`}</em></span><p>{item.desc}</p><div><progress value={item.progress} max={item.total} /><small>{item.progress} / {item.total}</small></div></div></article>;
        })}
      </div>
    </section>
  );
}

function Overview() {
  return <><PageHeader eyebrow="PERSONAL OVERVIEW" title="你的学习空间" subtitle="把每一次开口变成看得见、可继续的成长记录。" /><div className="stat-grid"><article><Clock /><span><small>本周学习时长</small><strong>183 <em>分钟</em></strong></span></article><article><BookOpenText /><span><small>已保存学习资产</small><strong>12 <em>项</em></strong></span></article><article><Fire /><span><small>连续学习天数</small><strong>7 <em>天</em></strong></span></article></div><section className="overview-grid"><LearningCalendar /><article className="rhythm-card"><p className="eyebrow">LAST SEVEN DAYS</p><h2>练习节奏</h2><div className="bars">{[38, 62, 78, 26, 92, 70, 48].map((height, index) => <span key={index} style={{ height: `${height}%` }}><small>{[18, 26, 34, 12, 40, 31, 22][index]}m</small></span>)}</div></article></section><AchievementSystem /></>;
}

function Membership() {
  const [checkout, setCheckout] = useState(null);
  return <><PageHeader eyebrow="MEMBERSHIP & PRICING" title="会员与订阅中心" subtitle="练习额度平时不会打扰你，只会在不足 20% 或无法开始时提醒。" /><div className="plan-grid">{plans.map((plan) => <article key={plan.id} className={cx("plan-card", plan.id === "pro" && "is-featured")}><div>{plan.id === "free" && <span className="plan-label">当前方案</span>}{plan.id === "pro" && <span className="plan-label">推荐</span>}<h2>{plan.name}</h2><p>{plan.desc}</p></div><p className="price"><small>¥</small><strong>{plan.price}</strong><span>{plan.suffix}</span></p><ul>{plan.features.map((feature) => <li key={feature}><Check />{feature}</li>)}</ul>{plan.id === "free" ? <div className="quota"><span><small>今日自由对话</small><strong>3 / 5 分钟</strong></span><progress value="3" max="5" /><span><small>今日普通场景</small><strong>0 / 1 次</strong></span></div> : <Button variant={plan.id === "pro" ? "primary" : "secondary"} onClick={() => setCheckout(plan)}>升级{plan.name}</Button>}</article>)}</div>{checkout && <Modal onClose={() => setCheckout(null)}><p className="eyebrow">MOCK PAYMENT</p><h2>确认升级至{checkout.name}</h2><p className="modal-lead">首版为支付演示。确认后仅展示成功状态，不会产生真实扣款。</p><dl className="checkout-summary"><div><dt>订阅方案</dt><dd>{checkout.name}</dd></div><div><dt>订阅金额</dt><dd>¥{checkout.price} / 月</dd></div><div><dt>生效时间</dt><dd>立即生效</dd></div></dl><Button onClick={() => setCheckout(null)}>模拟支付并完成</Button></Modal>}</>;
}

function Settings({ teacher, speed, level, onSettingsChange }) {
  const [syncPulse, setSyncPulse] = useState(0);
  const updateSettings = (next) => {
    onSettingsChange(next);
    setSyncPulse((current) => current + 1);
  };
  return <div className="assistant-settings-page"><PageHeader eyebrow="ASSISTANT SETTINGS" title="AI 助手设置" subtitle="只调整真正影响对话体验的选项。" action={<span key={syncPulse} className="sync-state"><CheckCircle />设置已同步</span>} /><section className="settings-list"><article><div><h2>对话语速</h2><p>选择更舒适的回应节奏。</p></div><SpeedSelector className="assistant-settings__speed" value={speed} onChange={(nextSpeed) => updateSettings({ speed: nextSpeed, level, teacher })} /></article><article><div><h2>英语水平</h2><p>新对话会按照该难度调整表达。</p></div><LevelSelect value={level} onChange={(nextLevel) => updateSettings({ speed, level: nextLevel, teacher })} /></article><article className="teacher-settings"><div><h2>AI 老师</h2><p>每位老师有固定口音和陪练方式。</p></div><TeacherSelector className="assistant-settings__teachers" selectedId={teacher.id} onSelect={(nextTeacher) => updateSettings({ speed, level, teacher: nextTeacher })} /></article><article><div><h2>账户与隐私</h2><p>管理密码与账户数据。</p></div><div className="account-actions"><button><Password />修改密码<CaretRight /></button><button className="danger"><Trash />删除账户<CaretRight /></button></div></article></section></div>;
}

function Paywall({ title, onClose, onMembership }) {
  return <Modal onClose={onClose}><div className="paywall-icon"><LockKey /></div><p className="eyebrow">SPECIAL TRAINING</p><h2>开始“{title}”需要特训版</h2><p className="modal-lead">你可以自由查看介绍；只有正式开始训练时才会检查权益。</p><ul className="paywall-list"><li><Check />IELTS 全真模拟与预估分数</li><li><Check />上传 PDF / DOCX 或粘贴面试材料</li><li><Check />雅思与面试共用 5 次/天</li></ul><div className="modal-actions"><Button variant="secondary" onClick={onClose}>稍后再说</Button><Button onClick={onMembership}>查看特训版</Button></div></Modal>;
}

export function App() {
  const initialRoute = useMemo(() => resolveRoute(window.location), []);
  const [flow, setFlow] = useState(initialRoute.flow);
  const [authMode, setAuthMode] = useState(initialRoute.authMode);
  const [level, setLevel] = useState("");
  const [conversationSpeed, setConversationSpeed] = useState("自然");
  const [teacher, setTeacher] = useState(teachers[0]);
  const [page, setPage] = useState(initialRoute.page);
  const [sceneTitle, setSceneTitle] = useState("咖啡店点单");
  const [training, setTraining] = useState(initialRoute.training);
  const [result, setResult] = useState(initialRoute.result);
  const [assetView, setAssetView] = useState(initialRoute.assetView || "home");
  const [ieltsRoute, setIeltsRoute] = useState(initialRoute.ieltsRoute || null);
  const [interviewRoute, setInterviewRoute] = useState(initialRoute.interviewRoute || null);
  const [paywall, setPaywall] = useState(null);

  const applyRoute = (route) => {
    setFlow(route.flow);
    setAuthMode(route.authMode);
    setPage(route.page);
    setTraining(route.training);
    setResult(route.result);
    setAssetView(route.assetView || "home");
    setIeltsRoute(route.ieltsRoute || null);
    setInterviewRoute(route.interviewRoute || null);
    setPaywall(null);
  };

  const navigate = (path, overrides = {}, replace = false) => {
    const url = new URL(path, window.location.origin);
    if (window.location.pathname !== url.pathname || window.location.search !== url.search) {
      window.history[replace ? "replaceState" : "pushState"]({}, "", `${url.pathname}${url.search}`);
    }
    applyRoute({ ...resolveRoute(url), ...overrides, canonicalPath: undefined });
  };

  useEffect(() => {
    if (initialRoute.canonicalPath && window.location.pathname !== initialRoute.canonicalPath) {
      window.history.replaceState({}, "", initialRoute.canonicalPath);
    }
    const handlePopState = () => {
      const nextRoute = resolveRoute(window.location);
      if (nextRoute.canonicalPath && window.location.pathname !== nextRoute.canonicalPath) {
        window.history.replaceState({}, "", nextRoute.canonicalPath);
      }
      applyRoute(nextRoute);
    };
    window.addEventListener("popstate", handlePopState);
    return () => window.removeEventListener("popstate", handlePopState);
  }, [initialRoute]);

  const goSplash = () => navigate(paths.root);
  const goAuth = (mode) => navigate(mode === "login" ? paths.auth.login : paths.auth.signup);
  const goLevel = () => navigate(paths.auth.level, { authMode });
  const goTeacher = () => navigate(paths.auth.teacher, { authMode });
  const enterApp = () => setMainPage("conversation");
  const startTraining = (title, initialStep = "learn", options = {}) => {
    setSceneTitle(title);
    navigate(paths.scenes.training, { page: options.returnPage || "scenes", authMode, training: { initialStep, standaloneSpeak: Boolean(options.standaloneSpeak), returnPage: options.returnPage || "scenes" }, result: null });
  };
  const showResult = (completed) => navigate(paths.scenes.result, { page: training?.returnPage || "scenes", authMode, training, result: { completed } });
  const setMainPage = (next) => navigate(hrefForPage(next), { page: next, authMode, training: null, result: null });
  const navigateIelts = (path) => navigate(path, { authMode });
  const navigateInterview = (path) => navigate(path, { authMode });
  const openCompletedAssetDetail = () => navigate(paths.assets.latest, { assetView: "detail", authMode });

  if (flow === "splash") return <Splash onStart={() => goAuth("signup")} onLogin={() => goAuth("login")} />;
  if (flow === "auth") return <Auth mode={authMode} onBack={goSplash} onSuccess={goLevel} />;
  if (flow === "level") return <LevelSetup selected={level} onSelect={setLevel} onNext={goTeacher} />;
  if (flow === "teacher") return <TeacherSetup selectedId={teacher.id} onSelect={(id) => setTeacher(teachers.find((item) => item.id === id))} onFinish={enterApp} />;
  let content;
  if (training) content = <Training sceneTitle={sceneTitle} teacher={teacher} initialStep={training.initialStep} standaloneSpeak={training.standaloneSpeak} result={result} onExit={() => setMainPage(training.returnPage || "scenes")} onComplete={showResult} onBack={() => setMainPage(training.returnPage || "scenes")} onAssets={openCompletedAssetDetail} />;
  else if (page === "conversation") content = <Conversation teacher={teacher} speed={conversationSpeed} level={level} onSettingsChange={(settings) => { setConversationSpeed(settings.speed); setLevel(settings.level); setTeacher(settings.teacher); }} />;
  else if (page === "scenes") content = <Scenes onStartTraining={startTraining} onLocked={setPaywall} onIelts={() => setMainPage("ielts")} onInterview={() => setMainPage("interview")} />;
  else if (page === "assets") content = <Assets initialView={assetView} initialRecordTitle={sceneTitle} onOpenRecord={openCompletedAssetDetail} onCloseRecord={() => navigate(paths.assets.root, { assetView: "home", authMode })} onIelts={() => setMainPage("ielts-assets")} onInterview={() => setMainPage("interview-assets")} onPractice={(title) => startTraining(title, "speak", { standaloneSpeak: true, returnPage: "assets" })} onRestart={(title) => startTraining(title, "learn", { returnPage: "assets" })} />;
  else if (page === "ielts") content = <IeltsTrainingCenter route={ieltsRoute} onNavigate={navigateIelts} onExit={() => setMainPage("scenes")} onAssets={() => navigateIelts(paths.ielts.assets.root)} />;
  else if (page === "ielts-assets") content = <IeltsAssets route={ieltsRoute} onNavigate={navigateIelts} onBackToAssets={() => setMainPage("assets")} onInterviewAssets={() => setMainPage("interview-assets")} onTraining={() => navigateIelts(paths.ielts.root)} />;
  else if (page === "interview") content = <InterviewTrainingCenter route={interviewRoute} onNavigate={navigateInterview} onExit={() => setMainPage("scenes")} onAssets={() => navigateInterview(paths.interview.assets.root)} />;
  else if (page === "interview-assets") content = <InterviewAssets route={interviewRoute} onNavigate={navigateInterview} onBackToAssets={() => setMainPage("assets")} onIeltsAssets={() => setMainPage("ielts-assets")} onTraining={() => navigateInterview(paths.interview.root)} />;
  else content = <Profile section={page} setSection={setMainPage} teacher={teacher} speed={conversationSpeed} level={level} onSettingsChange={(settings) => { setConversationSpeed(settings.speed); setLevel(settings.level); setTeacher(settings.teacher); }} onLogout={goSplash} />;
  return <AppShell page={page} setPage={setMainPage} teacher={teacher}>{content}{paywall && <Paywall title={paywall} onClose={() => setPaywall(null)} onMembership={() => { setPaywall(null); setMainPage("membership"); }} />}</AppShell>;
}
