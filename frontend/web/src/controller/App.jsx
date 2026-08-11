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
  Lifebuoy,
  LockKey,
  Medal,
  Microphone,
  MicrophoneSlash,
  PaperPlaneTilt,
  Pause,
  PencilSimple,
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
  Info,
  MessagesSquare,
  PackageCheck,
  Sparkles,
  Target,
  ChartLine,
} from "lucide-react";
import { learningItems, levels, plans, recommendations, sceneCategories, teachers } from "../domain/content/data.js";
import {
  AUTH_SESSION_EXPIRED_EVENT,
  changePassword,
  clearAuthSession,
  advanceCustomSceneFlow,
  createCustomSceneFlow,
  evaluateSentenceReading,
  generateCustomScene,
  getAchievementOverview,
  getCurrentUser,
  getLearningAsset,
  getLearningAssets,
  getProfileOverview,
  getUserPreference,
  hasAuthSession,
  login,
  register,
  synthesizeSpeech,
  translateSceneText,
  translateSessionText,
  updateProfile,
  updateUserPreference,
  uploadProfileAvatar,
} from "../infrastructure/http/apiClient.js";
import { createPcmWavRecorder } from "../infrastructure/audio/audioRecorder.js";
import { useAchievementNotifications } from "../component/achievement/AchievementNotifications.jsx";
import { createRealtimeClient, realtimeFailureMessage } from "../websocket/realtimeClient.js";
import { IeltsAssets, IeltsTrainingCenter } from "../component/ielts/IeltsModule.jsx";
import { InterviewAssets, InterviewModule } from "../component/interview/InterviewModule.jsx";
import { HelpCenter } from "../component/help/HelpCenter.jsx";
import { HelpLayout } from "../component/help/HelpLayout.jsx";
import { LandingPage } from "../component/landing/LandingPage.jsx";
import { NewtonsCradle } from "../component/common/NewtonsCradle.jsx";
import { Modal } from "../component/common/Modal.jsx";
import { LearningInsights } from "../component/profile/LearningInsights.jsx";
import { AccountSecurity } from "../component/profile/AccountSecurity.jsx";
import { AboutProduct } from "../component/profile/AboutProduct.jsx";
import { ProductLegalDocument } from "../component/profile/ProductLegalDocument.jsx";
import { hrefForPage, paths, resolveRoute, sidebarPageTarget } from "./router.js";

const cx = (...parts) => parts.filter(Boolean).join(" ");
const pronunciationAudioCache = new Map();
const maxPronunciationAudioCacheEntries = 128;
const chineseCharacterPattern = /[\u3400-\u9fff]/;
const sceneCachePrefix = "unispeaking.scene.";
const authReturnPathKey = "unispeaking.authReturnPath";

function cacheGeneratedScene(scene) {
  if (!scene?.sceneId) return;
  const { scenePrompt: _scenePrompt, ...cacheableScene } = scene;
  try {
    window.sessionStorage.setItem(
      `${sceneCachePrefix}${scene.sceneId}`,
      JSON.stringify(cacheableScene),
    );
  } catch {
    // The live in-memory scene remains available when session storage is unavailable.
  }
}

function cachedGeneratedScene(sceneId) {
  if (!sceneId) return null;
  try {
    const value = window.sessionStorage.getItem(`${sceneCachePrefix}${sceneId}`);
    return value ? JSON.parse(value) : null;
  } catch {
    return null;
  }
}

function compactSceneText(value, maxLength) {
  const normalized = String(value || "")
    .replace(/\s+/g, " ")
    .replace(/^["'“”]+|["'“”]+$/g, "")
    .trim();
  if (!normalized) return "暂未提供";
  const firstSentence = normalized.split(/(?<=[。！？!?；;])\s*/)[0] || normalized;
  return firstSentence.length > maxLength
    ? `${firstSentence.slice(0, maxLength).trim()}…`
    : firstSentence;
}

async function sceneDisplayValue(sceneId, value, maxLength) {
  const source = String(value || "").trim();
  if (!source) return "暂未提供";
  if (chineseCharacterPattern.test(source)) return compactSceneText(source, maxLength);
  try {
    const translated = await translateSceneText(sceneId, source);
    return compactSceneText(translated?.translatedText || source, maxLength);
  } catch {
    return compactSceneText(source, maxLength);
  }
}

async function buildSceneDisplaySummary(scene) {
  const [title, background, aiRole, userRole, learningGoal] = await Promise.all([
    sceneDisplayValue(scene.sceneId, scene.title, 18),
    sceneDisplayValue(scene.sceneId, scene.background, 58),
    sceneDisplayValue(scene.sceneId, scene.aiRole, 22),
    sceneDisplayValue(scene.sceneId, scene.userRole, 22),
    sceneDisplayValue(scene.sceneId, scene.learningGoal, 42),
  ]);
  return { title, background, aiRole, userRole, learningGoal };
}

function cachedPronunciationAudio(sceneId, text) {
  const key = `${sceneId}:${text}`;
  if (!pronunciationAudioCache.has(key)) {
    if (pronunciationAudioCache.size >= maxPronunciationAudioCacheEntries) {
      pronunciationAudioCache.delete(pronunciationAudioCache.keys().next().value);
    }
    pronunciationAudioCache.set(
      key,
      synthesizeSpeech(sceneId, text).catch((error) => {
        pronunciationAudioCache.delete(key);
        throw error;
      }),
    );
  }
  return pronunciationAudioCache.get(key);
}

function prefetchPronunciationAudio(sceneId, items, limit = 2) {
  if (!sceneId || !Array.isArray(items) || limit <= 0) return;
  items
    .map((item) => String(item?.englishText || item?.en || "").trim())
    .filter(Boolean)
    .slice(0, limit)
    .forEach((text) => {
      void cachedPronunciationAudio(sceneId, text).catch(() => undefined);
    });
}

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

function StaticAudioToggle({ src, label = "播放试听音频", mini = false }) {
  const audioRef = useRef(null);
  const [playing, setPlaying] = useState(false);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    const audio = new Audio(src);
    audio.preload = "auto";
    const handleEnded = () => setPlaying(false);
    const handleError = () => {
      setPlaying(false);
      setFailed(true);
    };
    audio.addEventListener("ended", handleEnded);
    audio.addEventListener("error", handleError);
    audioRef.current = audio;
    setPlaying(false);
    setFailed(false);
    return () => {
      audio.pause();
      audio.removeEventListener("ended", handleEnded);
      audio.removeEventListener("error", handleError);
      if (audioRef.current === audio) audioRef.current = null;
    };
  }, [src]);

  const togglePlayback = async () => {
    const audio = audioRef.current;
    if (!audio || failed) return;
    if (!audio.paused) {
      audio.pause();
      audio.currentTime = 0;
      setPlaying(false);
      return;
    }
    try {
      audio.currentTime = 0;
      await audio.play();
      setPlaying(true);
    } catch {
      setPlaying(false);
      setFailed(true);
    }
  };

  return (
    <button
      type="button"
      className={cx("audio-toggle", mini && "audio-toggle--mini", playing && "is-playing", failed && "has-error")}
      aria-label={failed ? `${label}不可用` : playing ? `停止${label}` : label}
      aria-pressed={playing}
      disabled={failed}
      title={failed ? "试听音频加载失败" : undefined}
      onClick={() => { void togglePlayback(); }}
    >
      {playing ? <Pause weight="fill" /> : <SpeakerHigh weight="fill" />}
    </button>
  );
}

function PronunciationAudioButton({ sceneId, text, label = "播放发音" }) {
  const audioRef = useRef(null);
  const objectUrlRef = useRef("");
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setFailed(false);
    audioRef.current?.pause();
    if (objectUrlRef.current) URL.revokeObjectURL(objectUrlRef.current);
    objectUrlRef.current = "";
    audioRef.current = null;

    cachedPronunciationAudio(sceneId, text)
      .then((blob) => {
        if (cancelled) return;
        const objectUrl = URL.createObjectURL(blob);
        const audio = new Audio(objectUrl);
        objectUrlRef.current = objectUrl;
        audioRef.current = audio;
        setLoading(false);
        audio.play().catch(() => undefined);
      })
      .catch(() => {
        if (!cancelled) {
          setLoading(false);
          setFailed(true);
        }
      });

    return () => {
      cancelled = true;
      audioRef.current?.pause();
      audioRef.current = null;
      if (objectUrlRef.current) URL.revokeObjectURL(objectUrlRef.current);
      objectUrlRef.current = "";
    };
  }, [sceneId, text, reloadKey]);

  const replay = () => {
    const audio = audioRef.current;
    if (!audio) {
      setReloadKey((current) => current + 1);
      return;
    }
    audio.currentTime = 0;
    audio.play().catch(() => setFailed(true));
  };

  return (
    <button
      type="button"
      className={cx("audio-toggle", "audio-toggle--mini", loading && "is-loading", failed && "has-error")}
      aria-label={failed ? `${label}失败，点击重试` : label}
      title={failed ? "语音生成失败，点击重试" : label}
      disabled={loading}
      onClick={replay}
    >
      <span className="audio-toggle__speaker"><SpeakerHigh weight="fill" /></span>
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

const resolveTranscriptTranslation = (line) => {
  if (line.translationStatus === "loading") return "正在翻译…";
  if (line.translationError) return line.translationError;
  return line.zh || transcriptTranslationLookup[line.en?.trim()] || "本句中文翻译暂未生成。";
};

function CallTranscript({ lines, translated, onToggleTranslation, transcriptRef, onScroll, className, emptyStatus }) {
  return (
    <div ref={transcriptRef} className={cx("transcript", className)} onScroll={onScroll} tabIndex="0" aria-label="对话字幕，可滚动查看历史内容">
      {lines.length === 0
        ? <article className="transcript__line"><small>字幕</small><p>{emptyStatus}</p></article>
        : lines.map((line, index) => {
          const isTranslated = translated.includes(index);
          const canTranslate = line.final !== false;
          return <article key={line.id || index} className={cx("transcript__line", line.who === "你" && "is-user")}><small>{line.who}</small><p>{line.en}</p>{canTranslate && <button type="button" aria-label={`${isTranslated ? "收起" : "查看"}${line.who}这句字幕的翻译`} disabled={line.translationStatus === "loading"} onClick={() => onToggleTranslation(index)}><Translate />{line.translationStatus === "loading" ? "翻译中" : isTranslated ? "收起翻译" : "翻译"}</button>}{isTranslated && <span>{resolveTranscriptTranslation(line)}</span>}</article>;
        })}
    </div>
  );
}

function useTranscriptAutoFollow({ enabled = true, lines, translated }) {
  const transcriptRef = useRef(null);
  const transcriptPinnedRef = useRef(true);

  const handleTranscriptScroll = () => {
    const transcript = transcriptRef.current;
    if (!transcript) return;
    transcriptPinnedRef.current = transcript.scrollHeight
      - transcript.scrollTop
      - transcript.clientHeight < 48;
  };

  useEffect(() => {
    if (!enabled) return undefined;
    transcriptPinnedRef.current = true;
    const frame = requestAnimationFrame(() => {
      const transcript = transcriptRef.current;
      if (transcript) transcript.scrollTop = transcript.scrollHeight;
    });
    return () => cancelAnimationFrame(frame);
  }, [enabled]);

  useEffect(() => {
    if (!enabled || !transcriptPinnedRef.current) return undefined;
    const frame = requestAnimationFrame(() => {
      const transcript = transcriptRef.current;
      if (transcript && transcriptPinnedRef.current) {
        transcript.scrollTop = transcript.scrollHeight;
      }
    });
    return () => cancelAnimationFrame(frame);
  }, [enabled, lines, translated]);

  return { transcriptRef, handleTranscriptScroll };
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

function Auth({ mode: initialMode, onBack, onSuccess }) {
  const [mode, setMode] = useState(initialMode || "signup");
  const [showPassword, setShowPassword] = useState(false);
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");
  const submit = async (event) => {
    event.preventDefault();
    setSubmitting(true);
    setError("");
    try {
      const auth = mode === "signup"
        ? await register({ username, password })
        : await login({ username, password });
      await onSuccess(auth, mode);
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "认证请求失败");
    } finally {
      setSubmitting(false);
    }
  };
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
        <form onSubmit={submit}>
          <label>邮箱<input type="email" value={username} onChange={(event) => setUsername(event.target.value)} placeholder="name@example.com" maxLength="254" required /></label>
          <label>密码<span className="password-field"><input type={showPassword ? "text" : "password"} value={password} onChange={(event) => setPassword(event.target.value)} placeholder="至少 6 位字符" minLength="6" maxLength="72" required /><button type="button" aria-label={showPassword ? "隐藏密码" : "显示密码"} onClick={() => setShowPassword(!showPassword)}>{showPassword ? <EyeSlash /> : <Eye />}</button></span></label>
          {mode === "login" && <button type="button" className="forgot-link">忘记密码？</button>}
          {error && <p className="call-error">{error}</p>}
          <Button className="auth-submit" type="submit" disabled={submitting}>{submitting ? "请稍候" : mode === "signup" ? "注册" : "登录"}</Button>
        </form>
        <p className="auth-switch">{mode === "signup" ? "已经有账号？" : "还没有账号？"}<button onClick={() => { setMode(mode === "signup" ? "login" : "signup"); setError(""); }}>{mode === "signup" ? "直接登录" : "创建账号"}</button></p>
        <div className="auth-help">
          <span>登录或注册遇到问题？</span>
          <a href={paths.help.root}><Lifebuoy weight="bold" />访问帮助中心</a>
        </div>
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
        <div className="teacher-detail__audition"><StaticAudioToggle mini src={active.previewAudio} label={`播放 ${active.name} 的自我介绍`} /><p>“{active.intro}”</p></div>
        <ExpandingCta className="teacher-cta teacher-gradient-cta" onClick={onFinish}>选择这位老师</ExpandingCta>
      </section>
    </main>
  );
}

function AppShell({ page, setPage, teacher, avatarUrl, children }) {
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const items = [
    { id: "conversation", label: "自由对话", icon: Waveform },
    { id: "scenes", label: "场景广场", icon: SquaresFour },
    { id: "assets", label: "学习资产", icon: BookOpenText },
  ];
  const activePage = page === "ielts" || page === "interview" ? "scenes" : page === "ielts-assets" || page === "interview-assets" ? "assets" : page;
  const navigateSidebar = (destination) => {
    const targetPage = sidebarPageTarget(page, destination);
    // Keep the current specialty page selected when clicking its active sidebar section.
    if (activePage === destination && targetPage === destination) return;
    if (targetPage !== page) setPage(targetPage);
  };
  return (
    <div className={cx("app-shell", sidebarOpen && "is-sidebar-open")}>
      <aside className={cx("sidebar", sidebarOpen && "is-open")} onMouseEnter={() => setSidebarOpen(true)} onMouseLeave={() => setSidebarOpen(false)}>
        <Brand compact={!sidebarOpen} />
        <nav>{items.map(({ id, label, icon: Icon }) => <button key={id} className={cx("sidebar__item", activePage === id && "is-active")} onClick={() => navigateSidebar(id)} aria-label={label} title={label}><Icon weight={activePage === id ? "bold" : "regular"} /><span className="sidebar__label"><span>{label}</span></span></button>)}</nav>
        <button className={cx("sidebar__avatar", ["profile", "insights", "membership", "settings", "help", "about"].includes(page) && "is-active")} onClick={() => setPage("profile")}><img src={avatarUrl || teacher.image} alt="个人中心" /></button>
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
const speedCodeByLabel = {
  "慢一些": "SLOWER",
  "适中": "MODERATE",
  "自然": "NATURAL",
  "快一些": "FASTER",
};
const speedLabelByCode = Object.fromEntries(
  Object.entries(speedCodeByLabel).map(([label, code]) => [code, label]),
);

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
  const [saving, setSaving] = useState(false);
  const save = async () => {
    if (saving) return;
    setSaving(true);
    try {
      await onSave({
        speed: draftSpeed,
        level: draftLevel,
        teacher: teachers.find((item) => item.id === draftTeacherId),
      });
    } finally {
      setSaving(false);
    }
  };
  return (
    <section className="conversation-settings" role="dialog" aria-modal="false" aria-labelledby="conversation-settings-title">
      <button className="conversation-settings__close" aria-label="关闭对话设置" disabled={saving} onClick={onClose}><X /></button>
      <div className="conversation-settings__heading"><h2 id="conversation-settings-title">对话设置</h2><p>调整后会从下一次对话开始生效。</p></div>
      <div className="conversation-settings__group"><label>对话语速</label><SpeedSelector value={draftSpeed} onChange={setDraftSpeed} /></div>
      <div className="conversation-settings__group"><label htmlFor="conversation-level">英语水平</label><LevelSelect value={draftLevel} onChange={setDraftLevel} /></div>
      <div className="conversation-settings__group"><label>AI 老师</label><TeacherSelector selectedId={draftTeacherId} onSelect={(item) => setDraftTeacherId(item.id)} /></div>
      <div className="conversation-settings__actions"><button disabled={saving} onClick={onClose}>取消</button><button className="is-primary" disabled={saving} onClick={save}>{saving ? "保存中…" : "保存设置"}</button></div>
    </section>
  );
}

function Conversation({ teacher, speed, level, onSettingsChange, onBeforeStart, onSessionStarted, onSessionEnded }) {
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
  const sessionIdRef = useRef("");
  const clientGenerationRef = useRef(0);
  const remoteAudioRef = useRef(null);
  const { transcriptRef, handleTranscriptScroll } = useTranscriptAutoFollow({
    enabled: subtitles,
    lines,
    translated,
  });
  const toggleTranslation = async (index) => {
    const line = lines[index];
    if (!line) return;
    if (translated.includes(index)) {
      setTranslated((current) => current.filter((item) => item !== index));
      return;
    }
    setTranslated((current) => [...current, index]);
    if (line.zh || line.translationStatus === "loading") return;
    const lineId = line.id;
    setLines((current) => current.map((item) => item.id === lineId
      ? { ...item, translationStatus: "loading", translationError: "" }
      : item));
    try {
      if (!sessionIdRef.current) throw new Error("会话尚未建立");
      const result = await translateSessionText(sessionIdRef.current, line.en);
      setLines((current) => current.map((item) => item.id === lineId
        ? { ...item, zh: result.translatedText, translationStatus: "done", translationError: "" }
        : item));
    } catch (error) {
      setLines((current) => current.map((item) => item.id === lineId
        ? {
          ...item,
          translationStatus: "failed",
          translationError: error instanceof Error ? error.message : "翻译失败，请稍后重试。",
        }
        : item));
    }
  };
  const updateRealtimeTranscript = ({ id, who, delta = "", text = "", final = false }) => {
    const content = String(text || delta || "");
    if (!content) return;
    setLines((current) => {
      const lineId = id || `${who}-live`;
      const exactIndex = current.findIndex((line) => line.id === lineId);
      const fallbackIndex = final
        ? current.findLastIndex((line) => line.who === who && !line.final)
        : -1;
      const index = exactIndex >= 0 ? exactIndex : fallbackIndex;
      if (index < 0) {
        return [...current, { id: lineId, who, en: content, final }];
      }
      const next = [...current];
      next[index] = {
        ...next[index],
        id: lineId,
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
    if (event.type === "local.greeting_timeout") {
      setCallState("active");
      setCallStatus("可以开始说了");
      return;
    }
    if (event.type === "local.provider_warning") {
      setCallState("active");
      setCallStatus("可以继续对话");
      return;
    }
    if (event.type === "local.transcript.final") {
      updateRealtimeTranscript({
        id: event.itemId,
        who: event.owner === 1 ? "你" : teacher.name,
        text: event.text,
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
      onSessionEnded?.();
      return;
    }
    if (event.type === "local.mic_error") {
      setCallState("error");
      setCallError(event.message || "无法访问麦克风");
      setCallStatus("麦克风不可用，请检查权限");
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
      const generation = ++clientGenerationRef.current;
      let client;
      client = createRealtimeClient({
        onEvent: (event) => {
          if (clientRef.current !== client || clientGenerationRef.current !== generation) return;
          handleRealtimeEvent(event);
        },
        onRemoteStream: (stream) => {
          if (clientRef.current !== client || clientGenerationRef.current !== generation) return;
          if (!remoteAudioRef.current) return;
          remoteAudioRef.current.srcObject = stream;
          void remoteAudioRef.current.play().catch(() => {
            setCallStatus("点击页面后可播放 AI 声音");
          });
        },
      });
      clientRef.current = client;
    }
    return clientRef.current;
  };

  const startConversation = async () => {
    await onBeforeStart?.();
    setInCall(true);
    setPaused(false);
    setSubtitles(true);
    setLines([]);
    setTranslated([]);
    setCallError("");
    setCallState("connecting");
    setCallStatus("正在请求麦克风");
    const client = getClient();
    try {
      const startedSession = await client.start({
        voice: teacher.voiceId,
        speechSpeed: speedCodeByLabel[speed] || "NATURAL",
      });
      if (startedSession?.sessionId) {
        sessionIdRef.current = startedSession.sessionId;
        onSessionStarted?.(startedSession.sessionId);
      }
    } catch (error) {
      if (clientRef.current !== client) return;
      setCallState("error");
      setCallError(realtimeFailureMessage(error));
      setCallStatus("连接失败");
    }
  };

  const togglePaused = async () => {
    if (callState === "ended") return;
    const client = clientRef.current;
    if (!client) return;
    const next = !paused;
    setPaused(next);
    if (next) await client.pause();
    else await client.resume();
  };

  const stopConversation = async () => {
    const client = clientRef.current;
    clientRef.current = null;
    sessionIdRef.current = "";
    clientGenerationRef.current += 1;
    setInCall(false);
    setSubtitles(false);
    setPaused(false);
    setCallState("idle");
    setCallStatus("准备开始");
    await client?.stop({ reason: "user_stop" });
    onSessionEnded?.();
  };

  useEffect(() => () => {
    const client = clientRef.current;
    clientRef.current = null;
    clientGenerationRef.current += 1;
    void client?.stop({ notifyBackend: false, reason: "component_unmount" });
  }, []);

  if (!inCall) return (
    <main className="conversation standby">
      <div className="conversation__top conversation__top--standby"><button className="dialog-settings-button" aria-expanded={settingsOpen} onClick={() => setSettingsOpen(!settingsOpen)}><GearSix className="dialog-settings-button__icon" /><span>对话设置</span></button></div>
      {settingsOpen && <ConversationSettings speed={speed} level={level} teacher={teacher} onClose={() => setSettingsOpen(false)} onSave={async (settings) => { if (await onSettingsChange(settings)) setSettingsOpen(false); }} />}
      <section className="standby__center">
        <div className="portrait portrait--large"><img src={teacher.image} alt={teacher.name} /></div>
        <p className="eyebrow">{teacher.name.toUpperCase()} · {teacher.accent}</p>
        <h1>想聊什么都可以</h1>
        <p>像打电话一样自然开口</p>
        {callError && <p className="call-error">{callError}</p>}
        <ExpandingCta className="standby__cta" onClick={startConversation}>开始对话</ExpandingCta>
      </section>
	      <p className="privacy-note"><ShieldCheck />字幕文本将暂存 24 小时</p>
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

function SceneCategoryTag({ category = "other", subtle = true }) {
  const palette = sceneCategories[category] || sceneCategories.other;
  const backgroundColor = subtle ? palette.subtleBackgroundColor : palette.backgroundColor;
  const color = subtle ? palette.subtleTextColor || palette.textColor : palette.textColor;
  return <span className="scene-category-tag" style={{ backgroundColor, color }}>{palette.label}</span>;
}

function Scenes({ onStartTraining, onIelts, onInterview }) {
  const [prompt, setPrompt] = useState("");
  const promptRef = useRef(null);
  const previewSceneIdRef = useRef("");
  const [preview, setPreview] = useState(null);
  const [previewDisplay, setPreviewDisplay] = useState(null);
  const [generationSource, setGenerationSource] = useState(null);
  const [startingTraining, setStartingTraining] = useState(false);
  const [generationError, setGenerationError] = useState("");
  const examples = ["餐厅点餐并说明忌口", "商场退换一件商品", "问路并确认交通方式", "预约理发并说明需求"];
  const syncPrompt = (event) => {
    setPrompt(event.currentTarget.value.slice(0, 200));
  };
  const generating = generationSource !== null;
  const generate = async (requestedInput, recommendationId = null) => {
    const fromRecommendation = typeof requestedInput === "string";
    const currentInput = fromRecommendation
      ? requestedInput
      : promptRef.current?.value ?? prompt;
    const sceneInput = currentInput.trim();
    if (!sceneInput || generating) return;
    if (!fromRecommendation) setPrompt(sceneInput);
    setGenerationError("");
    setGenerationSource(fromRecommendation ? `recommendation:${recommendationId}` : "manual");
    try {
      const scene = await generateCustomScene(sceneInput);
      previewSceneIdRef.current = scene.sceneId;
      setPreview(scene);
      setPreviewDisplay(null);
      prefetchPronunciationAudio(scene.sceneId, scene.wordList, 2);
      void buildSceneDisplaySummary(scene).then((display) => {
        if (previewSceneIdRef.current === scene.sceneId) setPreviewDisplay(display);
      });
    } catch (error) {
      setGenerationError(error instanceof Error ? error.message : "场景生成失败，请稍后重试");
    } finally {
      setGenerationSource(null);
    }
  };
  const startGeneratedTraining = async () => {
    if (!preview || startingTraining) return;
    setGenerationError("");
    setStartingTraining(true);
    try {
      await createCustomSceneFlow(preview.sceneId);
      onStartTraining(preview);
    } catch (error) {
      setGenerationError(error instanceof Error ? error.message : "学习流程创建失败，请稍后重试");
      previewSceneIdRef.current = "";
      setPreview(null);
      setPreviewDisplay(null);
    } finally {
      setStartingTraining(false);
    }
  };
  return (
    <main className="page page--scenes">
      <PageHeader title="场景广场" subtitle="把真实生活中的需求，变成高质量的口语练习。" />
      <div className="scene-plaza-content section-block">
        <section className="scene-builder scene-builder--featured scene-module">
          <div className="scene-section-heading scene-section-heading--primary">
            <div><p className="eyebrow">CREATE YOUR OWN</p><h2>创建专属场景</h2><p>用一句话描述你想练习的真实情境，AI 会为你整理角色、目标与表达任务。</p></div>
          </div>
          <div className={cx("scene-input", prompt.trim() && "has-content")}>
            <textarea ref={promptRef} value={prompt} maxLength={200} onChange={syncPrompt} onInput={syncPrompt} onCompositionEnd={syncPrompt} placeholder="你今天想练习什么？例如：第一次去健身房，咨询设施、开放时间和会员体验" />
            <div className="scene-input__footer">
              <div className="example-chips"><small>快速开始</small>{examples.map((example) => <button key={example} onClick={() => setPrompt(example)}>{example}</button>)}</div>
              <div className="scene-input__controls"><span>{prompt.length}/200</span><ExpandingCta className={cx("scene-generate", generationSource === "manual" && "is-generating")} disabled={!prompt.trim() || generating} onClick={() => void generate()}><span className="generation-button-state notranslate" translate="no"><span className={cx("generation-button-state__idle", generationSource === "manual" && "is-hidden")}>生成练习场景</span><span className={cx("generation-button-state__loading", generationSource !== "manual" && "is-hidden")}><NewtonsCradle size={22} className="newtons-cradle--inline" label="正在生成练习场景" /><span>正在生成</span></span></span></ExpandingCta></div>
            </div>
            {generationError && <p className="scene-generation-error" role="alert">{generationError}</p>}
          </div>
        </section>

        <section className="specialty-training scene-module">
          <div className="scene-section-heading"><div><p className="eyebrow">SPECIALTY TRAINING</p><h2>专项训练</h2></div><p>围绕明确目标，进入完整的专项练习流程。</p></div>
          <div className="specialty-training__grid">
            <button type="button" className="specialty-card specialty-card--ielts" onClick={onIelts}>
              <span className="specialty-card__art"><img src="/specialty/ielts.png" alt="" /></span>
              <span className="specialty-card__copy"><small>IELTS SPEAKING</small><strong>雅思口语</strong><span>Part 1 / 2 / 3 专项练习与全真模考</span></span>
              <span className="specialty-card__action" aria-hidden="true"><ArrowRight weight="bold" /></span>
            </button>
            <button type="button" className="specialty-card specialty-card--interview" onClick={onInterview}>
              <span className="specialty-card__art"><img src="/specialty/interview.png" alt="" /></span>
              <span className="specialty-card__copy"><small>ENGLISH INTERVIEW</small><strong>英文面试</strong><span>结合 JD 与简历，完成岗位模拟追问</span></span>
              <span className="specialty-card__action" aria-hidden="true"><ArrowRight weight="bold" /></span>
            </button>
          </div>
        </section>

        <section className="recommendations scene-module">
          <div className="scene-section-heading"><div><p className="eyebrow">DAILY PICKS</p><h2>每日推荐</h2></div><p>选择一个常用场景，直接开始今天的练习。</p></div>
          <div className="recommendation-list">
            {recommendations.map((item) => {
              const loading = generationSource === `recommendation:${item.id}`;
              const category = sceneCategories[item.category] || sceneCategories.other;
              return (
                <article key={item.id} style={{ "--scene-category-bg": category.subtleBackgroundColor, "--scene-category-accent": category.subtleTextColor || category.textColor }}>
                  <span className="recommendation__number">{item.number}</span>
                  <span className="recommendation__title"><SceneCategoryTag category={item.category} subtle /><strong>{item.title}</strong></span>
                  <small>{item.duration}<i>·</i>{item.level}</small>
                  <p>{item.goal}</p>
                  <button
                    type="button"
                    className={cx("scene-card-cta", loading && "is-generating")}
                    disabled={generating}
                    onClick={() => void generate(item.title, item.id)}
                    aria-label={loading ? `正在生成 ${item.title} 场景` : `开始练习 ${item.title} 场景`}
                  >
                    {loading
                      ? <NewtonsCradle size={18} className="newtons-cradle--inline" label={`正在生成${item.title}场景`} />
                      : <ArrowRight weight="bold" />}
                  </button>
                </article>
              );
            })}
          </div>
        </section>
      </div>
      {preview && <Modal onClose={() => { previewSceneIdRef.current = ""; setPreview(null); setPreviewDisplay(null); }}><p className="eyebrow">场景已准备好</p><h2>{previewDisplay?.title || (chineseCharacterPattern.test(preview.title || "") ? compactSceneText(preview.title, 18) : "正在整理场景…")}</h2><p className="modal-lead">确认场景信息，然后开始学习。</p><dl className="scene-summary"><div><dt>场景简介</dt><dd>{previewDisplay?.background || (chineseCharacterPattern.test(preview.background || "") ? compactSceneText(preview.background, 58) : "正在整理中文摘要…" )}</dd></div><div><dt>AI 扮演</dt><dd>{previewDisplay?.aiRole || (chineseCharacterPattern.test(preview.aiRole || "") ? compactSceneText(preview.aiRole, 22) : "正在整理…" )}</dd></div><div><dt>你将扮演</dt><dd>{previewDisplay?.userRole || (chineseCharacterPattern.test(preview.userRole || "") ? compactSceneText(preview.userRole, 22) : "正在整理…" )}</dd></div><div><dt>练习重点</dt><dd>{previewDisplay?.learningGoal || (chineseCharacterPattern.test(preview.learningGoal || "") ? compactSceneText(preview.learningGoal, 42) : "正在整理中文摘要…" )}</dd></div><div><dt>预计用时</dt><dd>{preview.estimatedMinutes} 分钟</dd></div></dl><div className="modal-actions"><Button variant="secondary" disabled={startingTraining} onClick={() => { previewSceneIdRef.current = ""; setPreview(null); setPreviewDisplay(null); }}>返回修改</Button><Button disabled={startingTraining} onClick={() => void startGeneratedTraining()} icon={<ArrowRight />}>{startingTraining ? "正在进入" : "确认进入"}</Button></div></Modal>}
    </main>
  );
}

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

function ResultModal({ completed, evaluation, onBack, onAssets }) {
  const persistedMetrics = evaluation ? [
    { label: "准确", value: Math.round(Number(evaluation.accuracyScore)) },
    { label: "流利", value: Math.round(Number(evaluation.fluencyScore)) },
    { label: "语法", value: Math.round(Number(evaluation.grammarScore)) },
    { label: "词汇", value: Math.round(Number(evaluation.vocabularyScore)) },
    { label: "自然", value: Math.round(Number(evaluation.naturalnessScore)) },
  ] : null;
  const metrics = persistedMetrics || [
    { label: "准确", value: 0 },
    { label: "流利", value: 0 },
    { label: "语法", value: 0 },
    { label: "词汇", value: 0 },
    { label: "自然", value: 0 },
  ];
  const totalScore = evaluation ? Math.round(Number(evaluation.finalScore)) : 0;
  return (
    <Modal wide dismissible={false} className="result-modal">
      <header className="result-modal__header">
        <div><p className="eyebrow">SIMULATION COMPLETE</p><h2>{completed ? "模拟完成" : "本次模拟已结束"}</h2>{evaluation && <span className="result-checkin"><CalendarCheck2 />今日已自动打卡</span>}<p className="result-modal__lead">{evaluation?.summary || "会话已经结束，但评分报告暂未返回。请稍后在学习资产中查看。"}</p></div>
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

function CustomSceneConversation({
  sceneId,
  teacher,
  speed,
  ended = false,
  onSessionStarted,
  onComplete,
}) {
  const [status, setStatus] = useState("正在连接场景");
  const [error, setError] = useState("");
  const [paused, setPaused] = useState(false);
  const [ending, setEnding] = useState(false);
  const [lines, setLines] = useState([]);
  const [translated, setTranslated] = useState([]);
  const clientRef = useRef(null);
  const sessionIdRef = useRef("");
  const remoteAudioRef = useRef(null);
  const endingRef = useRef(false);
  const scenarioCompletedRef = useRef(false);
  const { transcriptRef, handleTranscriptScroll } = useTranscriptAutoFollow({
    lines,
    translated,
  });

  const updateLine = ({ id, who, text = "", delta = "", final = false }) => {
    const content = String(text || delta || "");
    if (!content) return;
    setLines((current) => {
      const lineId = id || `${who}-live`;
      const exact = current.findIndex((line) => line.id === lineId);
      const fallback = final
        ? current.findLastIndex((line) => line.who === who && !line.final)
        : -1;
      const index = exact >= 0 ? exact : fallback;
      if (index < 0) return [...current, { id: lineId, who, en: content, final }];
      const next = [...current];
      next[index] = {
        ...next[index],
        id: lineId,
        en: text || `${next[index].en}${delta}`,
        final,
      };
      return next;
    });
  };

  const handleEvent = (event) => {
    if (event.type === "local.connecting") setStatus("正在连接模型");
    else if (event.type === "local.connected") {
      setStatus("正在建立模型会话");
      sessionIdRef.current = event.sessionId || "";
      onSessionStarted?.(event.sessionId);
    } else if (event.type === "session.updated" || event.type === "local.greeting_timeout") {
      setStatus("可以开始说了");
    } else if (event.type === "input_audio_buffer.speech_started") {
      setStatus("正在听你说话");
    } else if (event.type === "response.audio.delta") {
      setStatus(`${teacher.name} 正在回应`);
    } else if (event.type === "local.transcript.final") {
      updateLine({
        id: event.itemId,
        who: event.owner === 1 ? "你" : teacher.name,
        text: event.text,
        final: true,
      });
    } else if (
      event.type === "conversation.item.input_audio_transcription.delta"
      || event.type === "conversation.item.input_audio_transcription.text"
    ) {
      updateLine({
        id: event.item_id || event.item?.id || "user-live",
        who: "你",
        text: `${event.text || ""}${event.stash || ""}`,
        delta: event.delta || "",
      });
    } else if (event.type === "response.audio_transcript.delta" || event.type === "response.text.delta") {
      updateLine({
        id: event.item_id || event.response_id || "assistant-live",
        who: teacher.name,
        delta: event.delta || event.text || "",
      });
    } else if (event.type === "local.turn_evaluation") {
      const state = event.scenarioState;
      const completedOutcomes = state?.outcomes?.filter((item) => item.satisfied).length;
      const totalOutcomes = state?.outcomes?.length;
      const progress = state
        ? ` · 目标 ${completedOutcomes}/${totalOutcomes} · ${state.effectiveUserTurns}/${state.maximumUserTurns} 轮`
        : "";
      setStatus(`第 ${event.evaluation.turnNo} 轮评分 ${Math.round(Number(event.evaluation.overallScore))} 分${progress}`);
    } else if (event.type === "local.scenario_state") {
      scenarioCompletedRef.current = Boolean(event.state?.completed);
      if (event.state?.stage === "CONFIRMATION") {
        setStatus("场景目标已覆盖，正在确认");
      } else if (event.state?.completed) {
        setStatus(event.state.completionReason === "MAX_TURNS_REACHED"
          ? "已完成 10 轮练习，老师正在收尾"
          : "场景目标已完成，老师正在收尾");
      }
    } else if (event.type === "local.scenario_completed") {
      setStatus("场景对话完成，正在生成报告");
      endingRef.current = true;
      setEnding(true);
    } else if (event.type === "local.ended" && event.reason === "state_machine") {
      clientRef.current = null;
      onComplete(
        true,
        event.completion?.evaluation || null,
        sessionIdRef.current,
      );
    } else if (event.type === "local.scenario_completion_error") {
      clientRef.current = null;
      setError(event.message || "场景自动结束失败");
      onComplete(true, null, sessionIdRef.current);
    } else if (event.type === "local.scenario_state_error") {
      setStatus("本轮状态同步失败，已继续对话");
    } else if (event.type === "local.turn_evaluation_error") {
      setError(event.message);
    } else if (event.type === "local.mic_error") {
      setError(event.message || "无法访问麦克风");
      setStatus("麦克风不可用，请检查权限");
    } else if (event.type === "error" || event.type === "local.error") {
      setError(event.message || event.error?.message || "实时会话发生错误");
      setStatus("连接异常");
    }
  };

  useEffect(() => {
    if (ended) {
      setEnding(true);
      setStatus("模拟对话已结束");
      remoteAudioRef.current?.pause();
      if (remoteAudioRef.current) remoteAudioRef.current.srcObject = null;
      return undefined;
    }
    let cancelled = false;
    const client = createRealtimeClient({
      sceneId,
      sceneType: "custom",
      onEvent: (event) => {
        if (!cancelled) handleEvent(event);
      },
      onRemoteStream: (stream) => {
        if (cancelled || !remoteAudioRef.current) return;
        remoteAudioRef.current.srcObject = stream;
        void remoteAudioRef.current.play().catch(() => setStatus("点击页面后可播放 AI 声音"));
      },
    });
    clientRef.current = client;
    void client.start({
      voice: teacher.voiceId,
      speechSpeed: speedCodeByLabel[speed] || "NATURAL",
    }).catch((startError) => {
      if (!cancelled) setError(startError instanceof Error ? startError.message : "无法开始场景对话");
    });
    return () => {
      cancelled = true;
      clientRef.current = null;
      void client.stop({ notifyBackend: false, reason: "component_unmount", emitEnded: false });
    };
  }, [sceneId, ended]);

  const togglePaused = async () => {
    const next = !paused;
    setPaused(next);
    if (next) await clientRef.current?.pause();
    else await clientRef.current?.resume();
  };

  const endConversation = async (reason = "user_stop") => {
    if (endingRef.current) return;
    endingRef.current = true;
    setEnding(true);
    setStatus("正在生成本次报告");
    try {
      const completion = await clientRef.current?.stop({ reason });
      clientRef.current = null;
      onComplete(
        reason === "state_machine" || scenarioCompletedRef.current,
        completion?.evaluation || null,
        sessionIdRef.current,
      );
    } catch (stopError) {
      setError(stopError instanceof Error ? stopError.message : "会话结束失败");
      clientRef.current = null;
      onComplete(
        reason === "state_machine" || scenarioCompletedRef.current,
        null,
        sessionIdRef.current,
      );
    }
  };

  const toggleTranslation = async (index) => {
    const line = lines[index];
    if (!line) return;
    if (translated.includes(index)) {
      setTranslated((current) => current.filter((item) => item !== index));
      return;
    }
    setTranslated((current) => [...current, index]);
    if (line.zh) return;
    try {
      if (!sessionIdRef.current) throw new Error("会话尚未建立");
      const result = await translateSceneText(sceneId, line.en);
      setLines((current) => current.map((item) => item.id === line.id
        ? { ...item, zh: result.translatedText }
        : item));
    } catch (translationError) {
      setLines((current) => current.map((item) => item.id === line.id
        ? { ...item, translationError: translationError.message }
        : item));
    }
  };

  return (
    <section className="simulation call call--subtitles">
      <audio ref={remoteAudioRef} className="remote-audio" autoPlay playsInline />
      <section className="call__stage">
        <div className="call-presence call-presence--compact">
          <div className="portrait portrait--small"><img src={teacher.image} alt={teacher.name} /></div>
          <div className="listening-state listening-state--compact">
            <VoiceWaveform active={!ended && !paused && !ending && !error} compact />
            <CallTimer paused={paused} state={ended || error ? "ended" : "active"} />
            <span>{status}</span>
          </div>
        </div>
        <CallTranscript
          lines={lines}
          translated={translated}
          onToggleTranslation={toggleTranslation}
          transcriptRef={transcriptRef}
          onScroll={handleTranscriptScroll}
          className="simulation__transcript"
          emptyStatus={status}
        />
        {error && <p className="call-error" role="alert">{error}</p>}
      </section>
      <CallControls
        paused={paused}
        onToggleMicrophone={togglePaused}
        onEnd={() => { void endConversation("user_stop"); }}
        disabled={ended || ending}
        showSubtitles={false}
      />
    </section>
  );
}

const sentenceWordPattern = /[A-Za-z]+(?:['’-][A-Za-z]+)*/g;
const normalizeScoredWord = (value) => String(value || "")
  .toLowerCase()
  .replace(/[’]/g, "'")
  .replace(/[^a-z'-]/g, "");

function ScoredSentence({ sentence, words = [] }) {
  if (!words.length) return sentence;
  const parts = [];
  let cursor = 0;
  let resultCursor = 0;
  for (const match of sentence.matchAll(sentenceWordPattern)) {
    const offset = match.index ?? 0;
    if (offset > cursor) parts.push(sentence.slice(cursor, offset));
    const expectedWord = normalizeScoredWord(match[0]);
    const resultIndex = words.findIndex(
      (candidate, index) => index >= resultCursor
        && normalizeScoredWord(candidate?.word) === expectedWord,
    );
    const result = resultIndex >= 0 ? words[resultIndex] : null;
    if (resultIndex >= 0) resultCursor = resultIndex + 1;
    const score = Number(result?.wordScore);
    parts.push(
      <mark
        key={`${offset}-${match[0]}`}
        className={cx(
          "sentence-score-word",
          Number.isFinite(score) && score >= 80 ? "is-correct" : "is-incorrect",
        )}
        title={Number.isFinite(score) ? `${score.toFixed(0)} 分` : "未正确识别"}
      >
        {match[0]}
      </mark>,
    );
    cursor = offset + match[0].length;
  }
  if (cursor < sentence.length) parts.push(sentence.slice(cursor));
  return parts;
}

function SentenceRecorder({ sentenceId, busy, onSubmit, onError }) {
  const recorderRef = useRef(null);
  const [recording, setRecording] = useState(false);
  const [finishing, setFinishing] = useState(false);

  useEffect(() => {
    setRecording(false);
    setFinishing(false);
    return () => {
      recorderRef.current?.cancel();
      recorderRef.current = null;
    };
  }, [sentenceId]);

  const toggleRecording = async () => {
    if (busy || finishing) return;
    if (!recording) {
      try {
        onError?.("");
        recorderRef.current = await createPcmWavRecorder();
        setRecording(true);
      } catch (error) {
        onError?.(error instanceof Error ? error.message : "无法开启麦克风");
      }
      return;
    }

    const recorder = recorderRef.current;
    recorderRef.current = null;
    setRecording(false);
    setFinishing(true);
    try {
      if (!recorder) throw new Error("录音状态已失效，请重新开始");
      const wavAudio = await recorder.stop();
      await onSubmit(wavAudio);
    } catch (error) {
      onError?.(error instanceof Error ? error.message : "朗读评分失败");
    } finally {
      setFinishing(false);
    }
  };

  return (
    <button
      type="button"
      className={cx("microphone-toggle", recording && "is-active", (busy || finishing) && "is-busy")}
      aria-label={recording ? "结束朗读并评分" : "开始朗读录音"}
      aria-pressed={recording}
      disabled={busy || finishing}
      onClick={toggleRecording}
    >
      <MicrophoneSlash className="microphone-toggle__slash" weight="fill" />
      <Microphone className="microphone-toggle__active" weight="fill" />
    </button>
  );
}

function ReadScoreModal({ feedback, item, onClose }) {
  return (
    <Modal dismissible={false} className="read-score-modal">
      <div className="read-score-modal__score"><strong>{feedback.score}</strong><span>/100</span></div>
      <h2>本句发音评估</h2>
      <p className="read-score-modal__lead">{feedback.passed ? "本句已达到 80 分，可以进入下一句；红色部分仍可继续练习。" : "本句未达到 80 分，请听示范后再次朗读。"}</p>
      <div className="read-score-modal__focus"><small>逐词结果</small><p><ScoredSentence sentence={item.en} words={feedback.words} /></p></div>
      <button type="button" className="read-score-modal__confirm" onClick={onClose}>知道了</button>
    </Modal>
  );
}

function Training({ sceneId, sessionId, sceneTitle, sceneContent, teacher, speed, initialStep = "learn", initialStage = "word", standaloneSpeak = false, result, onExit, onComplete, onBack, onAssets, onStageChange }) {
  const generatedWordItems = useMemo(() => (sceneContent?.wordList || []).map((entry) => ({ id: entry.contentId, type: "单词", en: entry.englishText, zh: entry.chineseText, phonetic: entry.phonetic })), [sceneContent]);
  const generatedPhraseItems = useMemo(() => (sceneContent?.phraseList || []).map((entry) => ({ id: entry.contentId, type: "词组", en: entry.englishText, zh: entry.chineseText, phonetic: entry.phonetic })), [sceneContent]);
  const generatedSentenceItems = useMemo(() => (sceneContent?.sentenceList || []).map((entry) => ({ id: entry.contentId, type: "句子", en: entry.englishText, zh: entry.chineseText, phonetic: entry.phonetic })), [sceneContent]);
  const generatedMode = Boolean(sceneId) || generatedWordItems.length > 0 || generatedPhraseItems.length > 0;
  const steps = [
    { id: "learn", label: "学" },
    { id: "read", label: "读" },
    { id: "speak", label: "说" },
  ];
  const initialStepIndex = Math.max(0, steps.findIndex((item) => item.id === initialStep));
  const [step, setStep] = useState(initialStep);
  const [unlockedStepIndex, setUnlockedStepIndex] = useState(initialStepIndex);
  const [completedSteps, setCompletedSteps] = useState(() => steps.slice(0, initialStepIndex).map((item) => item.id));
  const [learningGroup, setLearningGroup] = useState(initialStage === "phrase" ? "phrases" : "words");
  const [learnIndex, setLearnIndex] = useState(0);
  const [readIndex, setReadIndex] = useState(0);
  const [learnedItems, setLearnedItems] = useState([]);
  const [readScores, setReadScores] = useState({});
  const [readEvaluations, setReadEvaluations] = useState({});
  const [heardReadDemos, setHeardReadDemos] = useState([]);
  const [readFeedback, setReadFeedback] = useState(null);
  const [readSubmitting, setReadSubmitting] = useState(false);
  const [readError, setReadError] = useState("");
  const [flowAdvancing, setFlowAdvancing] = useState(false);
  const [flowError, setFlowError] = useState("");
  const lessonItems = generatedMode
    ? (learningGroup === "words" ? generatedWordItems : generatedPhraseItems)
    : learningItems;
  const readItems = generatedMode ? generatedSentenceItems : learningItems;
  const displayedStep = result ? "speak" : step;
  const itemIndex = displayedStep === "read" ? readIndex : learnIndex;
  const item = displayedStep === "read" ? readItems[itemIndex] : lessonItems[itemIndex];
  const score = readScores[readIndex] ?? null;
  const readEvaluation = readEvaluations[readIndex] ?? null;
  const completeStep = (id) => setCompletedSteps((current) => current.includes(id) ? current : [...current, id]);
  const goToStep = (id) => {
    const targetIndex = steps.findIndex((item) => item.id === id);
    if (targetIndex > unlockedStepIndex) return;
    setStep(id);
    if (!generatedMode) return;
    if (id === "learn") onStageChange?.(learningGroup === "phrases" ? "phrase" : "word");
    if (id === "read") onStageChange?.("sentence");
    if (id === "speak" && sessionId) onStageChange?.("session", sessionId);
  };
  const nextLearn = async () => {
    if (flowAdvancing) return;
    const itemId = item.id || `${learningGroup}-${learnIndex}-${item.en}`;
    setLearnedItems((current) => current.includes(itemId) ? current : [...current, itemId]);
    if (learnIndex < lessonItems.length - 1) setLearnIndex(learnIndex + 1);
    else if (generatedMode && learningGroup === "words" && generatedPhraseItems.length > 0) {
      setFlowAdvancing(true);
      setFlowError("");
      try {
        await advanceCustomSceneFlow(sceneId, "WORD_LEARNING");
        setLearningGroup("phrases");
        setLearnIndex(0);
        onStageChange?.("phrase");
      } catch (error) {
        setFlowError(error instanceof Error ? error.message : "无法进入词组学习");
      } finally {
        setFlowAdvancing(false);
      }
    }
    else {
      if (generatedMode) {
        setFlowAdvancing(true);
        setFlowError("");
        try {
          await advanceCustomSceneFlow(sceneId, "PHRASE_LEARNING");
        } catch (error) {
          setFlowError(error instanceof Error ? error.message : "无法进入句子学习");
          setFlowAdvancing(false);
          return;
        }
        setFlowAdvancing(false);
      }
      completeStep("learn");
      setUnlockedStepIndex((current) => Math.max(current, 1));
      setReadIndex(0);
      setStep("read");
      if (generatedMode) onStageChange?.("sentence");
    }
  };
  const previousLearn = () => {
    if (learnIndex > 0) {
      setLearnIndex(learnIndex - 1);
    } else if (generatedMode && learningGroup === "phrases" && generatedWordItems.length > 0) {
      setLearningGroup("words");
      setLearnIndex(generatedWordItems.length - 1);
      onStageChange?.("word");
    }
  };
  useEffect(() => {
    if (!generatedMode) return;
    if (initialStage === "word") {
      setStep("learn");
      setLearningGroup("words");
    } else if (initialStage === "phrase") {
      setStep("learn");
      setLearningGroup("phrases");
    } else if (initialStage === "sentence") {
      setStep("read");
      setUnlockedStepIndex((current) => Math.max(current, 1));
    } else if (initialStage === "session") {
      setStep("speak");
      setUnlockedStepIndex((current) => Math.max(current, 2));
    }
  }, [generatedMode, initialStage]);
  useEffect(() => {
    setReadError("");
  }, [readIndex]);
  useEffect(() => {
    if (!generatedMode || !sceneId || displayedStep === "speak") return;
    if (displayedStep === "read") {
      prefetchPronunciationAudio(sceneId, readItems.slice(readIndex), 3);
      return;
    }
    const upcoming = lessonItems.slice(learnIndex);
    if (learningGroup === "words" && upcoming.length < 3) {
      upcoming.push(...generatedPhraseItems.slice(0, 3 - upcoming.length));
    }
    prefetchPronunciationAudio(sceneId, upcoming, 3);
  }, [
    displayedStep,
    generatedMode,
    generatedPhraseItems,
    learnIndex,
    learningGroup,
    lessonItems,
    readIndex,
    readItems,
    sceneId,
  ]);
  const submitRead = () => {
    const nextScore = readIndex === 1 && score === null ? 68 : 86;
    setReadScores((current) => ({ ...current, [readIndex]: nextScore }));
    setReadFeedback({ index: readIndex, score: nextScore, passed: nextScore >= 80, words: [] });
  };
  const submitGeneratedRead = async (wavAudio) => {
    if (!sceneId || !item?.id) throw new Error("当前句子缺少评分标识");
    setReadSubmitting(true);
    setReadError("");
    try {
      const evaluation = await evaluateSentenceReading(sceneId, item.id, wavAudio);
      const nextScore = Math.round(Number(evaluation.overallScore));
      const normalized = {
        ...evaluation,
        overallScore: nextScore,
        words: Array.isArray(evaluation.words) ? evaluation.words : [],
      };
      setReadScores((current) => ({ ...current, [readIndex]: nextScore }));
      setReadEvaluations((current) => ({ ...current, [readIndex]: normalized }));
      setReadFeedback({
        index: readIndex,
        score: nextScore,
        passed: Boolean(normalized.passed),
        words: normalized.words,
      });
    } catch (error) {
      const message = error instanceof Error ? error.message : "朗读评分失败";
      setReadError(message);
      throw error;
    } finally {
      setReadSubmitting(false);
    }
  };
  const playReadDemo = () => {
    setHeardReadDemos((current) => current.includes(readIndex) ? current : [...current, readIndex]);
  };
  const nextRead = async () => {
    if (flowAdvancing) return;
    const passed = generatedMode
      ? Boolean(readEvaluations[readIndex]?.passed)
      : (readScores[readIndex] ?? 0) >= 80;
    if (!passed) return;
    if (readIndex < readItems.length - 1) setReadIndex(readIndex + 1);
    else {
      if (generatedMode) {
        setFlowAdvancing(true);
        setFlowError("");
        try {
          await advanceCustomSceneFlow(sceneId, "SENTENCE_LEARNING");
        } catch (error) {
          setFlowError(error instanceof Error ? error.message : "无法进入场景对话");
          setFlowAdvancing(false);
          return;
        }
        setFlowAdvancing(false);
      }
      completeStep("read");
      setUnlockedStepIndex((current) => Math.max(current, 2));
      setStep("speak");
    }
  };
  if (!item && displayedStep !== "speak") {
    return (
      <main className="training-page">
        <header className="training-header"><div><strong>{sceneTitle || "自定义场景"}</strong><span>场景内容加载失败</span></div><button className="training-exit" aria-label="关闭训练" onClick={onExit}><span><X weight="bold" /></span></button></header>
        <section className="training-empty" role="alert">
          <h1>场景学习内容为空</h1>
          <p>后端没有返回当前阶段需要的单词、词组或句子，请返回场景广场重新生成。</p>
          <ExpandingCta direction="back" onClick={onBack}>返回场景广场</ExpandingCta>
        </section>
      </main>
    );
  }
  return (
    <main className={cx("training-page", standaloneSpeak && "training-page--standalone")}>
      <header className="training-header"><div><strong>{sceneTitle}</strong><span>从语言到真实表达</span></div><button className="training-exit" aria-label="关闭训练" onClick={onExit}><span><X weight="bold" /></span></button></header>
      {!standaloneSpeak && <nav className="stepper" aria-label="练习进度">
        <span className="stepper__track" aria-hidden="true"><span style={{ width: `${unlockedStepIndex * 50}%` }} /></span>
        {steps.map((stepItem, index) => {
          const done = completedSteps.includes(stepItem.id);
          return <button key={stepItem.id} className={cx("stepper__item", displayedStep === stepItem.id && "is-active", done && "is-done")} disabled={Boolean(result) || index > unlockedStepIndex} onClick={() => goToStep(stepItem.id)}><span className="stepper__check">{done ? <Check weight="bold" /> : index + 1}</span><span className="stepper__copy"><strong>{stepItem.label}</strong></span></button>;
        })}
      </nav>}
      {flowError && <p className="call-error" role="alert">{flowError}</p>}
      {!result && <>
      {displayedStep === "learn" && <section className="training-workspace"><aside className="lesson-list"><div><span>{generatedMode ? (learningGroup === "words" ? "场景单词" : "场景词组") : "本组语言"}</span><small>{learnIndex + 1} / {lessonItems.length}</small></div>{lessonItems.map((learningItem, index) => { const itemId = learningItem.id || `${learningGroup}-${index}-${learningItem.en}`; return <button key={itemId} type="button" disabled className={cx(index === learnIndex && "is-active", learnedItems.includes(itemId) && "is-done")}><small>{learningItem.type}</small><strong>{learningItem.en}</strong><span>{learnedItems.includes(itemId) ? <Check weight="bold" /> : index + 1}</span></button>; })}</aside><article className="learn-stage"><small>{item.type}</small><h1>{item.en}</h1><div className="pronunciation"><span>{item.phonetic || ""}</span>{generatedMode ? <PronunciationAudioButton sceneId={sceneId} text={item.en} label={`播放 ${item.en} 的发音`} /> : <AudioToggle mini label={`${item.en} 的发音`} />}</div><p>{item.zh}</p><div className="stage-footer"><ExpandingCta direction="back" disabled={learnIndex === 0 && (!generatedMode || learningGroup === "words")} onClick={previousLearn}>上一个</ExpandingCta><ExpandingCta onClick={nextLearn}>{learnIndex < lessonItems.length - 1 ? "下一个" : generatedMode && learningGroup === "words" && generatedPhraseItems.length > 0 ? "进入词组" : "进入朗读"}</ExpandingCta></div></article></section>}
      {step === "read" && generatedMode && <section className="training-workspace"><aside className="lesson-list"><div><span>场景句子</span><small>{readIndex + 1} / {readItems.length}</small></div>{readItems.map((readItem, index) => <button key={readItem.id || readItem.en} type="button" disabled className={cx(index === readIndex && "is-active", readEvaluations[index]?.passed && "is-done")}><small>句子</small><strong>{readItem.en}</strong><span>{readEvaluations[index]?.passed ? <Check weight="bold" /> : index + 1}</span></button>)}</aside><article className="read-stage"><h1 className={cx(readEvaluation && "sentence-score-text")}><ScoredSentence sentence={item.en} words={readEvaluation?.words} /></h1><p>{item.zh}</p><SentenceRecorder sentenceId={item.id} busy={readSubmitting} onSubmit={submitGeneratedRead} onError={setReadError} /><h3>{readSubmitting ? "正在评分" : score === null ? "点击麦克风开始朗读" : readEvaluation?.passed ? "朗读通过" : "再试一次"}</h3>{score === null && !readError && <p>再次点击麦克风结束录音并提交评分。</p>}{readError && <p className="sentence-reading-error" role="alert">{readError}</p>}<div className="read-demo"><span>听标准示范</span><PronunciationAudioButton sceneId={sceneId} text={item.en} label={`播放 ${item.en} 的标准发音`} /></div>{score !== null && <div className="sentence-score-summary"><strong>{score}</strong><span>/100</span></div>}<div className="stage-footer read-stage-footer"><ExpandingCta direction="back" disabled={readIndex === 0 || readSubmitting} onClick={() => setReadIndex(readIndex - 1)}>上一句</ExpandingCta><ExpandingCta disabled={!readEvaluation?.passed || readSubmitting} onClick={nextRead}>{readIndex === readItems.length - 1 ? "进入模拟" : "下一句"}</ExpandingCta></div></article></section>}
      {step === "read" && !generatedMode && <section className="training-workspace"><aside className="lesson-list"><div><span>完整表达</span><small>{readIndex + 1} / {learningItems.length}</small></div>{learningItems.map((learningItem, index) => <button key={learningItem.en} className={cx(index === readIndex && "is-active", (readScores[index] ?? 0) >= 80 && "is-done")} onClick={() => setReadIndex(index)}><small>句子</small><strong>{learningItem.en}</strong><span>{(readScores[index] ?? 0) >= 80 ? <Check weight="bold" /> : index + 1}</span></button>)}</aside><article className="read-stage"><h1>{item.en}</h1><p>{item.zh}</p><div className="rhythm"><span>节奏重点</span><strong>{item.en.split(" ").slice(0, 4).join(" · ")}</strong></div><MicrophoneToggle label="朗读麦克风" onActivate={submitRead} /><h3>{score === null ? "轮到你说" : score >= 80 ? "朗读通过" : "再试一次"}</h3>{score === null && <p>尽量完整、连贯地说出整句话。</p>}<button type="button" className="read-replay" onClick={playReadDemo}><SpeakerHigh weight="fill" />{heardReadDemos.includes(readIndex) ? "再听一次标准示范" : "听标准示范"}</button>{score >= 80 && <div className="stage-footer read-stage-footer"><span /><ExpandingCta onClick={nextRead}>{readIndex === learningItems.length - 1 ? "进入模拟" : "下一句"}</ExpandingCta></div>}</article></section>}
      </>}
      {(step === "speak" || result) && generatedMode && <CustomSceneConversation sceneId={sceneId} teacher={teacher} speed={speed} ended={Boolean(result)} onSessionStarted={(startedSessionId) => onStageChange?.("session", startedSessionId)} onComplete={onComplete} />}
      {(step === "speak" || result) && !generatedMode && <CustomSceneConversation sceneId={sceneId} teacher={teacher} speed={speed} ended={Boolean(result)} onSessionStarted={(startedSessionId) => onStageChange?.("session", startedSessionId)} onComplete={onComplete} />}
      {result && <ResultModal completed={result.completed} evaluation={result.evaluation} onBack={onBack} onAssets={onAssets} />}
      {!result && readFeedback && <ReadScoreModal feedback={readFeedback} item={readItems[readFeedback.index]} onClose={() => setReadFeedback(null)} />}
    </main>
  );
}

function AssetModuleMenu({ onIelts, onInterview }) {
  return (
    <div className="asset-module-menu">
      <button className="asset-module-menu__trigger" type="button" aria-label="切换学习资产模块" aria-haspopup="menu">
        <SquaresFour weight="bold" />
        <span>其他资产</span>
        <CaretDown weight="bold" />
      </button>
      <div className="asset-module-menu__popover" role="menu">
        <button type="button" role="menuitem" onClick={onIelts}><span className="asset-module-ielts-mark">IELTS</span><span><strong>IELTS 学习资产</strong><small>评分、建议与今日复习</small></span><CaretRight /></button>
        <button type="button" role="menuitem" onClick={onInterview}><Briefcase /><span><strong>面试学习资产</strong><small>面试报告与同岗位复练</small></span><CaretRight /></button>
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
  const suggestedExpression = String(feedback.suggestedExpression || "").trim();
  const unavailable = !suggestedExpression
    && feedback.feedbackSummary === "本轮评分暂不可用，已保留对话内容";
  return (
    <section className={cx("asset-feedback", unavailable && "is-unavailable")} aria-label={unavailable ? "本轮评分未完成" : "AI 表达评价"}>
      <header>{unavailable ? <Clock weight="fill" /> : <CheckCircle weight="fill" />}<strong>{unavailable ? "本轮评分未完成" : "AI 表达评价"}</strong></header>
      {suggestedExpression && <div className="asset-feedback__correction"><span>推荐表达</span><strong>{suggestedExpression}</strong></div>}
      <p><span>{unavailable ? "说明" : "本轮总结"}</span>{feedback.feedbackSummary}</p>
    </section>
  );
}

function AssetPracticeMenu({ scene, onPractice, onRestart }) {
  return (
    <div className="asset-practice-menu">
      <button className="asset-practice-menu__primary" type="button" onClick={() => onPractice(scene)}><span className="asset-practice-menu__icon"><Play weight="fill" /></span><strong>复练场景</strong><CaretDown weight="bold" /></button>
      <div className="asset-practice-menu__popover">
        <button type="button" onClick={() => onRestart(scene)}><ArrowLeft weight="bold" /><span><strong>重新学习</strong><small>从学、读、说第一步开始</small></span></button>
      </div>
    </div>
  );
}

function AssetConversationDetail({ record, onBack, onPractice, onRestart }) {
  const evaluationByTurn = new Map(
    (record.dialogueEvaluation?.turnEvaluation || [])
      .map((evaluation) => [evaluation.turnNo, evaluation]),
  );
  let userTurn = 0;
  const conversation = (record.dialogueEvaluation?.dialogue || []).map((message, index) => {
    const isUser = Number(message.owner) === 1;
    if (isUser) userTurn += 1;
    return {
      id: `${record.latestSessionId || "session"}-${index}`,
      role: isUser ? "user" : "assistant",
      speaker: isUser ? "你" : record.aiRole,
      text: message.content,
      feedback: isUser ? evaluationByTurn.get(userTurn) : null,
    };
  });
  return (
    <main className="page assets-page asset-conversation-page">
      <PageHeader
        title={`${record.title} · 语境复现`}
        subtitle="最近一次模拟对话已完整保留，每句表达都附有 AI 评价与更自然的说法。"
        action={<div className="asset-conversation-actions"><AssetPracticeMenu scene={record} onPractice={onPractice} onRestart={onRestart} /><button className="training-exit asset-conversation-exit" type="button" aria-label="退出当前学习资产" onClick={onBack}><span><X weight="bold" /></span></button></div>}
      />
      <section className="asset-conversation-card">
        <div className="asset-conversation-card__label"><Translate weight="bold" />对话语境下的纠错与地道表达</div>
        <div className="asset-conversation-thread">
          {conversation.map((message) => (
            <article key={message.id} className={cx("asset-message", message.role === "user" && "is-user")}>
              <small>{message.speaker}</small>
              <p>{message.text}</p>
              {message.feedback && <AssetFeedback feedback={message.feedback} />}
            </article>
          ))}
          {!conversation.length && <div className="asset-list__empty">该场景还没有已完成的模拟对话</div>}
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

function Assets({ sceneId, onPractice, onRestart, onIelts, onInterview, onOpenRecord, onCloseRecord, initialView = "home", initialRecordTitle }) {
  const [records, setRecords] = useState([]);
  const [selectedId, setSelectedId] = useState(sceneId || "");
  const [detail, setDetail] = useState(null);
  const [loading, setLoading] = useState(true);
  const [assetError, setAssetError] = useState("");
  const [deleted, setDeleted] = useState([]);
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [reservedModule, setReservedModule] = useState(null);
  const visibleRecords = records.filter((record) => !deleted.includes(record.sceneId));
  const selected = visibleRecords.find((record) => record.sceneId === selectedId)
    || visibleRecords.find((record) => record.title === initialRecordTitle)
    || visibleRecords[0]
    || null;

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    getLearningAssets()
      .then((items) => {
        if (cancelled) return;
        const next = Array.isArray(items) ? items : [];
        setRecords(next);
        setSelectedId((current) => sceneId || current || next[0]?.sceneId || "");
        setAssetError("");
      })
      .catch((error) => {
        if (!cancelled) setAssetError(error instanceof Error ? error.message : "学习资产加载失败");
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [sceneId]);

  useEffect(() => {
    const targetSceneId = sceneId || selected?.sceneId;
    if (!targetSceneId) {
      setDetail(null);
      return undefined;
    }
    let cancelled = false;
    getLearningAsset(targetSceneId)
      .then((asset) => {
        if (!cancelled) {
          setDetail(asset);
          setAssetError("");
        }
      })
      .catch((error) => {
        if (!cancelled) setAssetError(error instanceof Error ? error.message : "学习资产详情加载失败");
      });
    return () => {
      cancelled = true;
    };
  }, [sceneId, selected?.sceneId]);

  if (reservedModule) return <AssetModulePlaceholder module={reservedModule} onBack={() => setReservedModule(null)} />;
  if (initialView === "detail" && detail) return <AssetConversationDetail record={detail} onBack={onCloseRecord} onPractice={onPractice} onRestart={onRestart} />;
  if (initialView === "detail" && (loading || !detail)) {
    return <main className="page assets-page"><PageHeader title="学习资产" subtitle="正在读取最近一次对话与评分。" />{assetError ? <p className="call-error" role="alert">{assetError}</p> : <NewtonsCradle label="正在加载学习资产" />}</main>;
  }

  const deleteSelected = () => {
    if (!selected) return;
    const remaining = visibleRecords.filter((record) => record.sceneId !== selected.sceneId);
    setDeleted((current) => [...current, selected.sceneId]);
    setDeleteOpen(false);
    setSelectedId(remaining[0]?.sceneId || "");
  };

  const items = detail ? [
    ...(detail.wordList || []).map((item) => ({ ...item, type: "单词" })),
    ...(detail.phraseList || []).map((item) => ({ ...item, type: "词组" })),
    ...(detail.sentenceList || []).map((item) => ({ ...item, type: "句子" })),
  ] : [];

  return (
    <main className="page assets-page">
      <PageHeader title="学习资产" subtitle="把场景练习中真正用过的表达，留在这里继续复习。" action={<AssetModuleMenu onIelts={onIelts} onInterview={onInterview} />} />
      {assetError && <p className="call-error" role="alert">{assetError}</p>}
      <section className="asset-layout">
        <aside className="asset-list asset-list--history" aria-label="场景训练历史">
          <div className="asset-list__heading"><strong>训练记录</strong><span>{visibleRecords.length} 条</span></div>
          {visibleRecords.map((record) => <button key={record.sceneId} className={selected?.sceneId === record.sceneId ? "is-active" : ""} onClick={() => setSelectedId(record.sceneId)}><small>{record.latestPracticedAt ? new Date(record.latestPracticedAt).toLocaleDateString("zh-CN") : "尚未练习"} · 普通场景</small><strong>{record.title}</strong><em>{record.wordCount + record.phraseCount + record.sentenceCount} 个语言资产 · {record.practiceCount ? `已练习 ${record.practiceCount} 次` : "待练习"}{record.latestScore !== null && record.latestScore !== undefined && ` · ${Math.round(Number(record.latestScore))}`}</em></button>)}
          {!visibleRecords.length && <div className="asset-list__empty">{loading ? "正在加载学习资产" : "暂无场景学习资产"}</div>}
        </aside>
        <article className="asset-detail">
          {selected && <header>
            <div><p className="eyebrow">普通场景</p><h2>{selected.title}</h2><p>{selected.latestPracticedAt ? `${new Date(selected.latestPracticedAt).toLocaleDateString("zh-CN")} · 已完成 ${selected.practiceCount} 次模拟` : "尚未完成模拟对话"}</p></div>
            <div className="asset-detail__actions"><AnimatedDeleteButton onClick={() => setDeleteOpen(true)} /><ExpandingCta className="teacher-cta asset-open-button" disabled={!selected.latestSessionId} onClick={() => onOpenRecord(selected.sceneId)}>打开当前学习资产</ExpandingCta></div>
          </header>}
          <div className="asset-items" aria-label="已保存的单词、短语和句子">
            {items.map((item) => <div key={`${item.type}-${item.contentId}`}><span className="tag">{item.type}</span><p><strong>{item.englishText}</strong><small>{item.chineseText}</small></p><ScenePlaybackToggle label={`播放 ${item.englishText} 的发音`} /></div>)}
            {selected && !items.length && <div className="asset-list__empty">正在读取该场景的语言资产</div>}
          </div>
        </article>
      </section>
      {deleteOpen && <Modal onClose={() => setDeleteOpen(false)}><p className="eyebrow">DELETE ASSET</p><h2>删除当前学习资产？</h2><p className="modal-lead">这条场景记录、对话和评分将一起删除，且无法恢复。</p><div className="modal-actions"><Button variant="secondary" onClick={() => setDeleteOpen(false)}>取消</Button><Button onClick={deleteSelected}>确认删除</Button></div></Modal>}
    </main>
  );
}

function ProfileEditModal({ account, user, avatarUrl, onClose, onNicknameChange, onAvatarChange }) {
  const currentNickname = account?.nickname || user?.nickname || "";
  const [nickname, setNickname] = useState(currentNickname);
  const [avatar, setAvatar] = useState(null);
  const [previewUrl, setPreviewUrl] = useState(avatarUrl);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!avatar) {
      setPreviewUrl(avatarUrl);
      return undefined;
    }
    const objectUrl = URL.createObjectURL(avatar);
    setPreviewUrl(objectUrl);
    return () => URL.revokeObjectURL(objectUrl);
  }, [avatar, avatarUrl]);

  const selectAvatar = (event) => {
    const file = event.target.files?.[0] || null;
    event.target.value = "";
    if (!file) return;
    if (!["image/jpeg", "image/png"].includes(file.type) || file.size > 2 * 1024 * 1024) {
      setError("请选择不超过 2 MiB 的 JPEG 或 PNG 图片");
      return;
    }
    setError("");
    setAvatar(file);
  };

  const submit = async (event) => {
    event.preventDefault();
    const normalizedNickname = nickname.trim();
    if (!normalizedNickname) {
      setError("用户名不能为空");
      return;
    }
    const nicknameChanged = normalizedNickname !== currentNickname;
    if (!nicknameChanged && !avatar) {
      onClose();
      return;
    }
    setSubmitting(true);
    setError("");
    if (nicknameChanged && !(await onNicknameChange(normalizedNickname))) {
      setError("用户名修改失败，请稍后重试");
      setSubmitting(false);
      return;
    }
    if (avatar && !(await onAvatarChange(avatar))) {
      setError("头像修改失败，请稍后重试");
      setSubmitting(false);
      return;
    }
    onClose();
  };

  return <Modal onClose={submitting ? undefined : onClose} className="profile-edit-modal"><p className="eyebrow">EDIT PROFILE</p><h2>编辑个人资料</h2><p className="modal-lead">修改你的展示用户名或个人头像。</p><form className="profile-edit-form" onSubmit={submit}><div className="profile-edit-avatar"><img src={previewUrl} alt="头像预览" /><div><strong>个人头像</strong><small>支持 JPEG、PNG，文件不超过 2 MiB</small><label className="profile-avatar-picker">选择新头像<input type="file" accept="image/jpeg,image/png" disabled={submitting} onChange={selectAvatar} /></label></div></div><label className="profile-edit-name">用户名<input type="text" minLength={1} maxLength={80} required disabled={submitting} value={nickname} onChange={(event) => setNickname(event.target.value)} /></label>{error && <p className="form-error" role="alert">{error}</p>}<div className="modal-actions"><Button type="button" variant="secondary" disabled={submitting} onClick={onClose}>取消</Button><Button type="submit" disabled={submitting}>{submitting ? "正在保存" : "保存修改"}</Button></div></form></Modal>;
}

function Profile({ section, setSection, helpRoute, aboutRoute, onHelpNavigate, onAboutNavigate, user, profile, teacher, speed, level, onSettingsChange, onMonthChange, onNicknameChange, onAvatarChange, onPasswordChange, onAssets, onLogout }) {
  const account = profile?.account;
  const displayName = account?.displayName || user?.nickname || user?.username?.split("@")[0] || "UniSpeaking User";
  const email = account?.email || user?.username || "";
  const avatarUrl = account?.avatarUrl || teacher.image;
  const [profileEditOpen, setProfileEditOpen] = useState(false);
  const [passwordOpen, setPasswordOpen] = useState(false);
  const startRecommendedTraining = (trainingType) => {
    const destination = trainingType === "FREE_CHAT"
      ? "conversation"
      : trainingType === "CUSTOM_SCENE" ? "scenes" : null;
    if (destination) setSection(destination);
  };
  return (
    <main className="profile-layout">
      <aside className="profile-nav">
        <div className="profile-user">
          <div className="profile-user__avatar">
            <img src={avatarUrl} alt={displayName} />
            <button type="button" className="profile-user__edit" aria-label="编辑用户名和头像" title="编辑用户名和头像" onClick={() => setProfileEditOpen(true)}><PencilSimple weight="bold" /></button>
          </div>
          <span><strong>{displayName}</strong><small>{email}</small></span>
        </div>
        <nav aria-label="个人中心导航">
          <button className={section === "profile" ? "is-active" : ""} onClick={() => setSection("profile")}><User />个人概览</button>
          <button className={section === "insights" ? "is-active" : ""} onClick={() => setSection("insights")}><ChartLine />学习目标与洞察</button>
          <button className={section === "membership" ? "is-active" : ""} onClick={() => setSection("membership")}><Crown />会员权益</button>
          <button className={section === "settings" ? "is-active" : ""} onClick={() => setSection("settings")}><SlidersHorizontal />助手设置</button>
          <button className={section === "security" ? "is-active" : ""} onClick={() => setSection("security")}><ShieldCheck />账号与安全</button>
          <button className={section === "help" ? "is-active" : ""} onClick={() => setSection("help")}><Lifebuoy />帮助中心</button>
          <button className={section === "about" ? "is-active" : ""} onClick={() => setSection("about")}><Info />关于产品</button>
        </nav>
        <button className="logout" onClick={onLogout}><SignOut />退出登录</button>
      </aside>
      <section className={cx("profile-content", section === "help" && "profile-content--help")}>
        {section === "profile" && <Overview calendar={profile?.calendar} statistics={profile?.statistics} onMonthChange={onMonthChange} onAssets={onAssets} />}
        {section === "insights" && <LearningInsights onStartTraining={startRecommendedTraining} />}
        {section === "membership" && <Membership />}
        {section === "settings" && <Settings teacher={teacher} speed={speed} level={level} onSettingsChange={onSettingsChange} />}
        {section === "security" && <AccountSecurity email={email} onOpenPassword={() => setPasswordOpen(true)} onLogout={onLogout} />}
        {section === "help" && <HelpCenter route={helpRoute} onNavigate={onHelpNavigate} />}
        {section === "about" && (aboutRoute?.screen === "document"
          ? <ProductLegalDocument documentId={aboutRoute.documentId} onNavigate={onAboutNavigate} />
          : <AboutProduct onNavigate={onAboutNavigate} onHelpNavigate={onHelpNavigate} />)}
      </section>
      {profileEditOpen && <ProfileEditModal account={account} user={user} avatarUrl={avatarUrl} onClose={() => setProfileEditOpen(false)} onNicknameChange={onNicknameChange} onAvatarChange={onAvatarChange} />}
      {passwordOpen && <PasswordChangeModal onClose={() => setPasswordOpen(false)} onSubmit={onPasswordChange} />}
    </main>
  );
}

const achievementSeriesIcons = {
  conversation: MessagesSquare,
  streak: Footprints,
  "scene-exploration": Compass,
  "expression-score": Sparkles,
  "pronunciation-attempt": AudioLines,
  "asset-collection": PackageCheck,
  "monthly-checkin": CalendarCheck2,
  "practice-duration": Clock,
  "active-days": Fire,
  "quality-sessions": Target,
};

function formatAchievementValue(value) {
  const numericValue = Number(value ?? 0);
  if (!Number.isFinite(numericValue)) return "0";
  return new Intl.NumberFormat("zh-CN", { maximumFractionDigits: 1 }).format(numericValue);
}

function LearningCalendar({ calendar, onMonthChange }) {
  const monthKey = calendar?.month || new Date().toLocaleDateString("sv-SE", { timeZone: "Asia/Shanghai" }).slice(0, 7);
  const [year, monthNumber] = monthKey.split("-").map(Number);
  const checkedDays = new Set((calendar?.checkedDates || []).map((date) => Number(date.slice(-2))));
  const today = new Date().toLocaleDateString("sv-SE", { timeZone: "Asia/Shanghai" });
  const todayDay = today.startsWith(monthKey) ? Number(today.slice(-2)) : null;
  const latestCheckedDay = Math.max(0, ...checkedDays);
  const [selectedDay, setSelectedDay] = useState(todayDay || latestCheckedDay || 1);
  useEffect(() => {
    setSelectedDay(todayDay || latestCheckedDay || 1);
  }, [monthKey]);
  const daysInMonth = new Date(year, monthNumber, 0).getDate();
  const leadingDays = (new Date(year, monthNumber - 1, 1).getDay() + 6) % 7;
  const label = `${year} 年 ${monthNumber} 月`;
  const selectedRecord = checkedDays.has(selectedDay);
  const currentMonth = today.slice(0, 7);
  const shiftMonth = (offset) => {
    const shifted = new Date(Date.UTC(year, monthNumber - 1 + offset, 1));
    return `${shifted.getUTCFullYear()}-${String(shifted.getUTCMonth() + 1).padStart(2, "0")}`;
  };
  return (
    <article className="calendar-card">
      <header className="calendar-card__header">
        <div><p className="eyebrow">LEARNING CALENDAR</p><h2>学习日历</h2></div>
        <div className="calendar-month-switcher">
          <button type="button" aria-label="查看上一个月" onClick={() => onMonthChange(shiftMonth(-1))}><ChevronLeft /></button>
          <strong aria-live="polite">{label}</strong>
          <button type="button" aria-label="查看下一个月" disabled={monthKey >= currentMonth} onClick={() => onMonthChange(shiftMonth(1))}><ChevronRight /></button>
        </div>
      </header>
      <div className="calendar-weekdays" aria-hidden="true">{["一", "二", "三", "四", "五", "六", "日"].map((day) => <span key={day}>周{day}</span>)}</div>
      <div className="calendar-days" role="grid" aria-label={`${label}学习记录`}>
        {Array.from({ length: leadingDays }, (_, index) => <span key={`blank-${index}`} className="calendar-blank" />)}
        {Array.from({ length: daysInMonth }, (_, index) => {
          const day = index + 1;
          const record = checkedDays.has(day);
          const isToday = todayDay === day;
          return (
            <button key={day} type="button" role="gridcell" aria-label={`${label}${day}日${record ? "，已自动打卡" : "，无打卡记录"}`} className={cx(record && "is-practiced", isToday && "is-today", selectedDay === day && "is-selected")} onClick={() => setSelectedDay(day)}>
              <span>{day}</span>{record && <i aria-hidden="true" />}{isToday && <small>今天</small>}
            </button>
          );
        })}
      </div>
      <div className={cx("calendar-summary", selectedRecord && "is-complete")}>
        <span className="calendar-summary__status"><CalendarCheck2 />{selectedRecord ? "已打卡" : "未打卡"}</span>
        <div><strong>{monthNumber} 月 {selectedDay} 日</strong><small>{selectedRecord ? "已生成五维评分报告，自动打卡完成" : "这一天还没有五维评分报告"}</small></div>
      </div>
    </article>
  );
}

function AchievementSystem() {
  const [filter, setFilter] = useState("全部");
  const [expandedSeriesId, setExpandedSeriesId] = useState(null);
  const [series, setSeries] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [retryVersion, setRetryVersion] = useState(0);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError("");
    getAchievementOverview()
      .then((overview) => {
        if (cancelled) return;
        setSeries(Array.isArray(overview?.series) ? overview.series : []);
      })
      .catch((requestError) => {
        if (cancelled) return;
        setError(requestError instanceof Error ? requestError.message : "成就数据加载失败");
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [retryVersion]);

  const categories = ["全部", ...new Set(series.map((item) => item.category).filter(Boolean))];
  const filtered = filter === "全部"
    ? series
    : series.filter((item) => item.category === filter);
  const milestones = series.flatMap((item) => Array.isArray(item.milestones) ? item.milestones : []);
  const unlockedCount = milestones.filter((item) => item.unlocked).length;
  const categoryCount = (category) => category === "全部"
    ? series.length
    : series.filter((item) => item.category === category).length;

  return (
    <section className="achievement-system">
      <header className="achievement-system__header">
        <div><p className="eyebrow">ACHIEVEMENTS</p><h2>成就图鉴</h2><p>每一级进步，都由你真实的练习记录点亮。</p></div>
        <div className="achievement-overall">
          <span><strong>{loading ? "—" : unlockedCount}</strong><small>/ {loading ? "—" : milestones.length} 已获得</small></span>
          <progress value={loading ? 0 : unlockedCount} max={Math.max(1, milestones.length)} />
        </div>
      </header>
      {!loading && !error && series.length > 0 && (
        <nav className="achievement-filters" aria-label="成就分类">
          {categories.map((item) => <button key={item} type="button" aria-pressed={filter === item} className={filter === item ? "is-active" : ""} onClick={() => { setFilter(item); setExpandedSeriesId(null); }}>{item}<small>{categoryCount(item)}</small></button>)}
        </nav>
      )}
      {loading && (
        <div className="achievement-loading" aria-live="polite" aria-busy="true">
          <span>正在计算你的成就进度…</span>
          <div>{Array.from({ length: 4 }, (_, index) => <i key={index} />)}</div>
        </div>
      )}
      {!loading && error && (
        <div className="achievement-error" role="alert">
          <Target />
          <div><strong>成就进度暂时无法加载</strong><p>{error}</p></div>
          <button type="button" onClick={() => setRetryVersion((current) => current + 1)}>重新加载</button>
        </div>
      )}
      {!loading && !error && series.length === 0 && (
        <div className="achievement-empty">成就目录暂时为空</div>
      )}
      {!loading && !error && filtered.length > 0 && (
        <div className="achievement-series-grid" aria-live="polite">
          {filtered.map((item) => {
            const Icon = achievementSeriesIcons[item.seriesId] || Target;
            const itemMilestones = Array.isArray(item.milestones) ? item.milestones : [];
            const lastMilestone = itemMilestones[itemMilestones.length - 1];
            const currentMilestone = itemMilestones.find((milestone) => milestone.level === item.currentLevel) || null;
            const progressMaximum = Number(item.nextThreshold ?? lastMilestone?.threshold ?? 1);
            const progressValue = Number(item.currentValue ?? 0);
            const expanded = expandedSeriesId === item.seriesId;
            const detailId = `achievement-series-${item.seriesId}-levels`;
            const levelClass = `achievement-level-${Math.min(5, Math.max(0, Number(item.currentLevel) || 0))}`;
            return (
              <div key={item.seriesId} className="achievement-series-entry">
                <article className={cx("achievement-series-card", levelClass, item.completed && "is-completed", expanded && "is-expanded")}>
                  <button
                    type="button"
                    className="achievement-series-card__trigger"
                    aria-label={`${expanded ? "收起" : "查看"}${item.title}全部等级`}
                    aria-expanded={expanded}
                    aria-controls={detailId}
                    onClick={() => setExpandedSeriesId((current) => current === item.seriesId ? null : item.seriesId)}
                  />
                  <header>
                    <div className="achievement-series-card__icon"><Icon strokeWidth={1.8} /></div>
                    <div><span>{item.category}</span><h3>{item.title}</h3></div>
                    <em>{item.completed ? "全部达成" : `Lv.${item.currentLevel || 0}`}</em>
                  </header>
                  <div className="achievement-series-current">
                    <span>{currentMilestone ? "当前最高等级" : "当前等级"}</span>
                    <strong>{currentMilestone?.title || "尚未解锁"}</strong>
                    <p>{currentMilestone?.description || `完成“${item.nextTitle || "第一阶段"}”后即可点亮该系列`}</p>
                  </div>
                  <div className="achievement-series-progress">
                    <div>
                      <span><small>当前进度</small><strong>{formatAchievementValue(item.currentValue)} <i>{item.unit}</i></strong></span>
                      <span><small>{item.completed ? "完成状态" : "下一阶段"}</small><strong>{item.completed ? "已完成全部等级" : item.nextTitle || "待解锁"}</strong></span>
                    </div>
                    <progress aria-label={`${item.title}当前进度`} value={Math.min(progressValue, progressMaximum)} max={Math.max(1, progressMaximum)} />
                    <small>{item.completed ? "该系列所有成就已解锁" : `距离 ${item.nextTitle} · ${formatAchievementValue(item.nextThreshold)} ${item.unit}`}</small>
                  </div>
                  <footer><span>查看全部 {itemMilestones.length} 个等级</span><CaretDown weight="bold" /></footer>
                </article>
                {expanded && (
                  <section id={detailId} className="achievement-level-panel" aria-label={`${item.title}全部等级`}>
                    <header>
                      <div><span>{item.category} · {item.title}</span><h3>等级进阶路径</h3><p>按等级顺序查看全部成就节点。</p></div>
                      <button type="button" aria-label={`收起${item.title}等级`} onClick={() => setExpandedSeriesId(null)}><X weight="bold" /></button>
                    </header>
                    <div className="achievement-level-track">
                      <ol>
                        {itemMilestones.map((milestone) => {
                          const current = milestone.level === item.currentLevel;
                          const next = !milestone.unlocked && milestone.level === item.nextLevel;
                          const status = current ? "当前等级" : milestone.unlocked ? "已获得" : next ? "下一目标" : "待解锁";
                          return (
                            <li
                              key={milestone.achievementId}
                              className={cx(
                                `achievement-level-${Math.min(5, Math.max(1, Number(milestone.level) || 1))}`,
                                milestone.unlocked && "is-unlocked",
                                current && "is-current",
                                next && "is-next",
                                !milestone.unlocked && !next && "is-locked",
                              )}
                              aria-current={current ? "step" : undefined}
                            >
                              <div className="achievement-level-marker"><span>Lv.{milestone.level}</span>{milestone.unlocked ? <Check weight="bold" /> : null}</div>
                              <div className="achievement-level-copy"><em>{status}</em><strong>{milestone.title}</strong><p>{milestone.description}</p><small>{formatAchievementValue(milestone.threshold)} {item.unit}</small></div>
                            </li>
                          );
                        })}
                      </ol>
                    </div>
                  </section>
                )}
              </div>
            );
          })}
        </div>
      )}
    </section>
  );
}

function Overview({ calendar, statistics, onMonthChange, onAssets }) {
  const weeklyMinutes = Math.ceil((statistics?.weeklyPracticeSeconds || 0) / 60);
  const trainingRecordCount = statistics?.trainingRecordCount || 0;
  const consecutiveLearningDays = statistics?.consecutiveLearningDays || 0;
  const days = statistics?.lastSevenDays || [];
  const maximumSeconds = Math.max(0, ...days.map((day) => day.practiceSeconds || 0));
  return <><PageHeader eyebrow="PERSONAL OVERVIEW" title="你的学习空间" subtitle="把每一次开口变成看得见、可继续的成长记录。" action={calendar?.checkedInToday ? <span className="today-checkin"><CalendarCheck2 />今日已打卡</span> : null} /><div className="stat-grid"><article><Clock /><span><small>本周学习时长</small><strong>{weeklyMinutes} <em>分钟</em></strong></span></article><button type="button" className="stat-card-action" aria-label={`查看已保存的 ${trainingRecordCount} 项学习资产`} onClick={onAssets}><BookOpenText /><span><small>已保存学习资产</small><strong>{trainingRecordCount} <em>项</em></strong></span></button><article><Fire /><span><small>连续学习天数</small><strong>{consecutiveLearningDays} <em>天</em></strong></span></article></div><section className="overview-grid"><LearningCalendar calendar={calendar} onMonthChange={onMonthChange} /><article className="rhythm-card"><p className="eyebrow">LAST SEVEN DAYS</p><h2>练习节奏</h2><div className={cx("bars", !days.length && "is-empty")}>{days.length ? days.map((day) => { const seconds = day.practiceSeconds || 0; const minutes = Math.ceil(seconds / 60); const height = maximumSeconds ? Math.max(3, (seconds / maximumSeconds) * 100) : 3; const weekday = new Date(`${day.date}T00:00:00+08:00`).toLocaleDateString("zh-CN", { weekday: "short", timeZone: "Asia/Shanghai" }); return <span key={day.date} className={seconds ? "" : "is-zero"} style={{ height: `${height}%` }} aria-label={`${day.date}，练习 ${minutes} 分钟`} title={`${day.date} · ${minutes} 分钟`}><small>{minutes}m</small><em>{weekday}</em></span>; }) : <p>完成一次至少 30 秒的练习后，这里会显示你的节奏。</p>}</div></article></section><AchievementSystem /></>;
}

function Membership() {
  const [checkout, setCheckout] = useState(null);
  return <><PageHeader eyebrow="MEMBERSHIP & PRICING" title="会员与订阅中心" subtitle="练习额度平时不会打扰你，只会在不足 20% 或无法开始时提醒。" /><div className="plan-grid">{plans.map((plan) => <article key={plan.id} className={cx("plan-card", plan.id === "pro" && "is-featured")}><div>{plan.id === "free" && <span className="plan-label">当前方案</span>}{plan.id === "pro" && <span className="plan-label">推荐</span>}<h2>{plan.name}</h2><p>{plan.desc}</p></div><p className="price"><small>¥</small><strong>{plan.price}</strong><span>{plan.suffix}</span></p><ul>{plan.features.map((feature) => <li key={feature}><Check />{feature}</li>)}</ul>{plan.id === "free" ? <div className="quota"><span><small>今日自由对话</small><strong>3 / 5 分钟</strong></span><progress value="3" max="5" /><span><small>今日普通场景</small><strong>0 / 1 次</strong></span></div> : <Button variant={plan.id === "pro" ? "primary" : "secondary"} onClick={() => setCheckout(plan)}>升级{plan.name}</Button>}</article>)}</div>{checkout && <Modal onClose={() => setCheckout(null)}><p className="eyebrow">MOCK PAYMENT</p><h2>确认升级至{checkout.name}</h2><p className="modal-lead">首版为支付演示。确认后仅展示成功状态，不会产生真实扣款。</p><dl className="checkout-summary"><div><dt>订阅方案</dt><dd>{checkout.name}</dd></div><div><dt>订阅金额</dt><dd>¥{checkout.price} / 月</dd></div><div><dt>生效时间</dt><dd>立即生效</dd></div></dl><Button onClick={() => setCheckout(null)}>模拟支付并完成</Button></Modal>}</>;
}

function PasswordChangeModal({ onClose, onSubmit }) {
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");
  const submit = async (event) => {
    event.preventDefault();
    if (newPassword !== confirmPassword) {
      setError("两次输入的新密码不一致");
      return;
    }
    setSubmitting(true);
    setError("");
    try {
      await onSubmit({ currentPassword, newPassword });
    } catch (submitError) {
      setError(submitError instanceof Error ? submitError.message : "密码修改失败");
      setSubmitting(false);
    }
  };
  return <Modal onClose={submitting ? undefined : onClose}><p className="eyebrow">ACCOUNT SECURITY</p><h2>修改密码</h2><p className="modal-lead">修改成功后，所有已登录设备都需要重新登录。</p><form className="password-form" onSubmit={submit}><label>当前密码<input type="password" autoComplete="current-password" minLength={6} maxLength={72} required value={currentPassword} onChange={(event) => setCurrentPassword(event.target.value)} /></label><label>新密码<input type="password" autoComplete="new-password" minLength={6} maxLength={72} required value={newPassword} onChange={(event) => setNewPassword(event.target.value)} /></label><label>确认新密码<input type="password" autoComplete="new-password" minLength={6} maxLength={72} required value={confirmPassword} onChange={(event) => setConfirmPassword(event.target.value)} /></label>{error && <p className="form-error" role="alert">{error}</p>}<div className="modal-actions"><Button type="button" variant="secondary" disabled={submitting} onClick={onClose}>取消</Button><Button type="submit" disabled={submitting}>{submitting ? "正在修改" : "确认修改"}</Button></div></form></Modal>;
}

function Settings({ teacher, speed, level, onSettingsChange }) {
  const [syncPulse, setSyncPulse] = useState(0);
  const updateSettings = async (next) => {
    if (await onSettingsChange(next)) {
      setSyncPulse((current) => current + 1);
    }
  };
  return <div className="assistant-settings-page"><PageHeader eyebrow="ASSISTANT SETTINGS" title="AI 助手设置" subtitle="只调整真正影响对话体验的选项。" action={<span key={syncPulse} className="sync-state"><CheckCircle />设置已同步</span>} /><section className="settings-list"><article><div><h2>对话语速</h2><p>选择更舒适的回应节奏。</p></div><SpeedSelector className="assistant-settings__speed" value={speed} onChange={(nextSpeed) => updateSettings({ speed: nextSpeed })} /></article><article><div><h2>英语水平</h2><p>新对话会按照该难度调整表达。</p></div><LevelSelect value={level} onChange={(nextLevel) => updateSettings({ level: nextLevel })} /></article><article className="teacher-settings"><div><h2>AI 老师</h2><p>每位老师有固定口音和陪练方式。</p></div><TeacherSelector className="assistant-settings__teachers" selectedId={teacher.id} onSelect={(nextTeacher) => updateSettings({ teacher: nextTeacher })} /></article></section></div>;
}

function Paywall({ title, onClose, onMembership }) {
  return <Modal onClose={onClose}><div className="paywall-icon"><LockKey /></div><p className="eyebrow">SPECIAL TRAINING</p><h2>开始“{title}”需要特训版</h2><p className="modal-lead">你可以自由查看介绍；只有正式开始训练时才会检查权益。</p><ul className="paywall-list"><li><Check />IELTS 全真模拟与预估分数</li><li><Check />上传 PDF / DOCX 或粘贴面试材料</li><li><Check />雅思与面试共用 5 次/天</li></ul><div className="modal-actions"><Button variant="secondary" onClick={onClose}>稍后再说</Button><Button onClick={onMembership}>查看特训版</Button></div></Modal>;
}

export function App() {
  const initialRoute = useMemo(() => resolveRoute(window.location), []);
  const {
    synchronizeAchievements,
    clearAchievementNotifications,
  } = useAchievementNotifications();
  const [authReady, setAuthReady] = useState(false);
  const [user, setUser] = useState(null);
  const [profileOverview, setProfileOverview] = useState(null);
  const [flow, setFlow] = useState(initialRoute.flow);
  const [authMode, setAuthMode] = useState(initialRoute.authMode);
  const [level, setLevel] = useState("");
  const [conversationSpeed, setConversationSpeed] = useState("自然");
  const [teacher, setTeacher] = useState(teachers[0]);
  const [page, setPage] = useState(initialRoute.page);
  const [sceneTitle, setSceneTitle] = useState("咖啡店点单");
  const [generatedScene, setGeneratedScene] = useState(
    () => cachedGeneratedScene(initialRoute.sceneId || initialRoute.assetSceneId),
  );
  const [training, setTraining] = useState(initialRoute.training);
  const [result, setResult] = useState(initialRoute.result);
  const [assetView, setAssetView] = useState(initialRoute.assetView || "home");
  const [assetSceneId, setAssetSceneId] = useState(initialRoute.assetSceneId || null);
  const [ieltsRoute, setIeltsRoute] = useState(initialRoute.ieltsRoute || null);
  const [interviewRoute, setInterviewRoute] = useState(initialRoute.interviewRoute || null);
  const [helpRoute, setHelpRoute] = useState(initialRoute.helpRoute || null);
  const [aboutRoute, setAboutRoute] = useState(initialRoute.aboutRoute || null);
  const [paywall, setPaywall] = useState(null);
  const preferenceWriteChainRef = useRef(Promise.resolve());
  const preferenceWriteVersionRef = useRef(0);

  const applyRoute = (route) => {
    setFlow(route.flow);
    setAuthMode(route.authMode);
    setPage(route.page);
    setTraining(route.training);
    setResult(route.result);
    setAssetView(route.assetView || "home");
    setAssetSceneId(route.assetSceneId || null);
    setIeltsRoute(route.ieltsRoute || null);
    setInterviewRoute(route.interviewRoute || null);
    setHelpRoute(route.helpRoute || null);
    setAboutRoute(route.aboutRoute || null);
    setPaywall(null);
    const routeSceneId = route.sceneId || route.assetSceneId;
    if (routeSceneId) {
      const cachedScene = cachedGeneratedScene(routeSceneId);
      if (cachedScene) {
        setGeneratedScene(cachedScene);
        setSceneTitle(cachedScene.title || "自定义场景");
      }
    }
  };

  const navigate = (path, overrides = {}, replace = false) => {
    const url = new URL(path, window.location.origin);
    if (window.location.pathname !== url.pathname || window.location.search !== url.search) {
      window.history[replace ? "replaceState" : "pushState"]({}, "", `${url.pathname}${url.search}`);
    }
    applyRoute({ ...resolveRoute(url), ...overrides, canonicalPath: undefined });
  };

  const applyPreference = (preference) => {
    if (!preference) return;
    const nextLevel = levels.find((item) => item.cefrLevel === preference.cefrLevel);
    const nextTeacher = teachers.find((item) => item.voiceId === preference.preferredVoice);
    if (nextLevel) setLevel(nextLevel.id);
    if (nextTeacher) setTeacher(nextTeacher);
    if (preference.preferredAiSpeechSpeed) {
      setConversationSpeed(speedLabelByCode[preference.preferredAiSpeechSpeed] || "自然");
    }
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

  useEffect(() => {
    const handleExpiredSession = () => {
      const currentPath = `${window.location.pathname}${window.location.search}`;
      if (![paths.auth.login, paths.auth.signup].includes(window.location.pathname)) {
        try { window.sessionStorage.setItem(authReturnPathKey, currentPath); } catch { /* Login still proceeds when storage is unavailable. */ }
      }
      clearAchievementNotifications();
      setUser(null);
      setProfileOverview(null);
      navigate(paths.auth.login, { authMode: "login" }, true);
    };
    window.addEventListener(AUTH_SESSION_EXPIRED_EVENT, handleExpiredSession);
    return () => window.removeEventListener(AUTH_SESSION_EXPIRED_EVENT, handleExpiredSession);
  }, []);

  useEffect(() => {
    let cancelled = false;
    const bootstrapAuth = async () => {
      if (!hasAuthSession()) {
        if (!["splash", "auth"].includes(initialRoute.flow) && !initialRoute.publicAccess) {
          const returnPath = `${window.location.pathname}${window.location.search}`;
          try { window.sessionStorage.setItem(authReturnPathKey, returnPath); } catch { /* Login still proceeds when storage is unavailable. */ }
          navigate(paths.auth.login, { authMode: "login" }, true);
        }
        if (!cancelled) setAuthReady(true);
        return;
      }
      try {
        const [currentUser, preference, profile] = await Promise.all([
          getCurrentUser(),
          getUserPreference(),
          getProfileOverview(),
        ]);
        if (cancelled) return;
        setUser(currentUser);
        setProfileOverview(profile);
        applyPreference(preference);
        await synchronizeAchievements();
        if (cancelled) return;
        if (initialRoute.flow === "app" && !preference.cefrLevel) {
          navigate(paths.auth.level, {}, true);
        } else if (initialRoute.flow === "app" && !preference.preferredVoice) {
          navigate(paths.auth.teacher, {}, true);
        }
      } catch {
        clearAuthSession();
        if (!cancelled && !["splash", "auth"].includes(initialRoute.flow) && !initialRoute.publicAccess) {
          navigate(paths.auth.login, { authMode: "login" }, true);
        }
      } finally {
        if (!cancelled) setAuthReady(true);
      }
    };
    void bootstrapAuth();
    return () => {
      cancelled = true;
    };
  }, []);

  const goSplash = () => navigate(paths.root);
  const goAuth = (mode) => navigate(mode === "login" ? paths.auth.login : paths.auth.signup);
  const openLandingStart = () => user ? setMainPage("conversation") : goAuth("signup");
  const openWebApp = () => user ? setMainPage("conversation") : goAuth("login");
  const goLevel = () => navigate(paths.auth.level, { authMode });
  const goTeacher = () => navigate(paths.auth.teacher, { authMode });
  const enterApp = () => {
    let returnPath = "";
    try {
      returnPath = window.sessionStorage.getItem(authReturnPathKey) || "";
      if (returnPath) window.sessionStorage.removeItem(authReturnPathKey);
    } catch { /* Fall back to the default authenticated page. */ }
    if (returnPath.startsWith("/") && !returnPath.startsWith("//")) {
      navigate(returnPath, {}, true);
    } else {
      setMainPage("conversation");
    }
  };
  const completeAuthentication = async (auth, mode) => {
    setUser(auth.user);
    const [preference, profile] = await Promise.all([
      getUserPreference(),
      getProfileOverview(),
    ]);
    setProfileOverview(profile);
    applyPreference(preference);
    await synchronizeAchievements();
    if (mode === "signup" || !preference.cefrLevel) {
      goLevel();
    } else if (!preference.preferredVoice) {
      goTeacher();
    } else {
      enterApp();
    }
  };
  const saveLevelAndContinue = async () => {
    const selectedLevel = levels.find((item) => item.id === level);
    if (!selectedLevel) return;
    try {
      const preference = await updateUserPreference({ cefrLevel: selectedLevel.cefrLevel });
      applyPreference(preference);
      goTeacher();
    } catch (error) {
      window.alert(error instanceof Error ? error.message : "英语水平保存失败");
    }
  };
  const saveTeacherAndEnter = async () => {
    try {
      const preference = await updateUserPreference({ preferredVoice: teacher.voiceId });
      applyPreference(preference);
      enterApp();
    } catch (error) {
      window.alert(error instanceof Error ? error.message : "AI 老师保存失败");
    }
  };
  const persistSettings = async (settings) => {
    const patch = {};
    if (settings.teacher) {
      setTeacher(settings.teacher);
      patch.preferredVoice = settings.teacher.voiceId;
    }
    if (settings.speed) {
      setConversationSpeed(settings.speed);
      patch.preferredAiSpeechSpeed = speedCodeByLabel[settings.speed] || "NATURAL";
    }
    if (settings.level) {
      const selectedLevel = levels.find((item) => item.id === settings.level);
      if (selectedLevel) {
        setLevel(selectedLevel.id);
        patch.cefrLevel = selectedLevel.cefrLevel;
      }
    }
    if (!Object.keys(patch).length) return true;

    const writeVersion = ++preferenceWriteVersionRef.current;
    const write = preferenceWriteChainRef.current
      .catch(() => undefined)
      .then(() => updateUserPreference(patch));
    preferenceWriteChainRef.current = write;
    try {
      const preference = await write;
      if (writeVersion === preferenceWriteVersionRef.current) {
        applyPreference(preference);
      }
      return true;
    } catch (error) {
      window.alert(error instanceof Error ? error.message : "设置保存失败");
      return false;
    }
  };
  const logout = () => {
    clearAchievementNotifications();
    clearAuthSession();
    setUser(null);
    setProfileOverview(null);
    goSplash();
  };
  const updateNickname = async (nickname) => {
    if (!nickname) {
      window.alert("用户名不能为空");
      return false;
    }
    try {
      const updated = await updateProfile({ nickname });
      setUser((current) => current ? { ...current, nickname: updated.nickname } : current);
      setProfileOverview((current) => current ? {
        ...current,
        account: { ...current.account, nickname: updated.nickname, displayName: updated.displayName },
      } : current);
      return true;
    } catch (error) {
      window.alert(error instanceof Error ? error.message : "用户名修改失败");
      return false;
    }
  };
  const loadProfileMonth = async (month) => {
    try {
      setProfileOverview(await getProfileOverview(month));
    } catch (error) {
      window.alert(error instanceof Error ? error.message : "学习日历加载失败");
    }
  };
  const updateAvatar = async (file) => {
    if (!["image/jpeg", "image/png"].includes(file.type) || file.size > 2 * 1024 * 1024) {
      window.alert("请选择不超过 2 MiB 的 JPEG 或 PNG 图片");
      return false;
    }
    try {
      const updated = await uploadProfileAvatar(file);
      setProfileOverview((current) => current ? {
        ...current,
        account: {
          ...current.account,
          avatarUrl: updated.avatarUrl,
          avatarUrlExpiresAt: updated.avatarUrlExpiresAt,
        },
      } : current);
      return true;
    } catch (error) {
      window.alert(error instanceof Error ? error.message : "头像上传失败");
      return false;
    }
  };
  const updatePassword = async (passwords) => {
    await changePassword(passwords);
    clearAchievementNotifications();
    clearAuthSession();
    setUser(null);
    setProfileOverview(null);
    navigate(paths.auth.login, { authMode: "login" }, true);
  };
  const startTraining = (sceneOrTitle, initialStep = "learn", options = {}) => {
    const scene = typeof sceneOrTitle === "object" ? sceneOrTitle : null;
    const title = scene?.title || sceneOrTitle;
    if (scene?.sceneId) cacheGeneratedScene(scene);
    setGeneratedScene(scene);
    setSceneTitle(title);
    const stage = initialStep === "read" ? "sentence" : initialStep === "speak" ? "session" : "word";
    const targetPath = scene?.sceneId && stage !== "session"
      ? paths.scenes[stage](scene.sceneId)
      : paths.scenes.training;
    navigate(targetPath, {
      page: options.returnPage || "scenes",
      authMode,
      sceneId: scene?.sceneId || null,
      training: {
        sceneId: scene?.sceneId || null,
        stage,
        initialStep,
        standaloneSpeak: Boolean(options.standaloneSpeak),
        returnPage: options.returnPage || "scenes",
      },
      result: null,
    });
  };
  const navigateSceneStage = (stage, sessionId = null) => {
    const sceneId = training?.sceneId || generatedScene?.sceneId;
    if (!sceneId) return;
    const targetPath = stage === "session"
      ? paths.scenes.session(sceneId, sessionId)
      : paths.scenes[stage](sceneId);
    navigate(targetPath, {
      page: "scenes",
      authMode,
      sceneId,
      sessionId,
      training: {
        ...training,
        sceneId,
        sessionId,
        stage,
        initialStep: stage === "sentence" ? "read" : stage === "session" ? "speak" : "learn",
      },
    });
  };
  const showResult = (completed, evaluation = null, completedSessionId = null) => {
    const sceneId = training?.sceneId || generatedScene?.sceneId;
    const sessionId = completedSessionId || training?.sessionId;
    const targetPath = sceneId && sessionId
      ? paths.scenes.sessionResult(sceneId, sessionId)
      : paths.scenes.result;
    navigate(targetPath, {
      page: training?.returnPage || "scenes",
      authMode,
      sceneId,
      sessionId,
      training: {
        ...training,
        sceneId,
        sessionId,
        stage: "session",
        initialStep: "speak",
      },
      result: { completed, evaluation },
    });
    if (evaluation) {
      void getProfileOverview().then(setProfileOverview).catch(() => undefined);
    }
    void synchronizeAchievements({ revealNotifications: true });
  };
  const setMainPage = (next) => navigate(hrefForPage(next), { page: next, authMode, training: null, result: null });
  const navigateIelts = (path) => navigate(path, { authMode });
  const navigateInterview = (path) => navigate(path, { authMode });
  const navigateHelp = (path) => navigate(path, { authMode });
  const navigateAbout = (path) => navigate(path, { authMode });
  const openCompletedAssetDetail = (requestedSceneId = null) => {
    const explicitSceneId = typeof requestedSceneId === "string" ? requestedSceneId : null;
    const sceneId = explicitSceneId || training?.sceneId || generatedScene?.sceneId || assetSceneId;
    const targetPath = sceneId ? paths.scenes.assets(sceneId) : paths.assets.latest;
    navigate(targetPath, { page: "assets", assetView: "detail", assetSceneId: sceneId || null, sceneId: sceneId || null, authMode });
  };

  if (!authReady) return <main className="splash" aria-busy="true" />;
  if (flow === "splash") return <LandingPage onStart={openLandingStart} onLogin={() => goAuth("login")} onWeb={openWebApp} />;
  if (flow === "auth") return <Auth mode={authMode} onBack={goSplash} onSuccess={completeAuthentication} />;
  if (flow === "level") return <LevelSetup selected={level} onSelect={setLevel} onNext={saveLevelAndContinue} />;
  if (flow === "teacher") return <TeacherSetup selectedId={teacher.id} onSelect={(id) => setTeacher(teachers.find((item) => item.id === id))} onFinish={saveTeacherAndEnter} />;
  if (page === "help" && !user) {
    return (
      <HelpLayout onNavigate={navigate}>
        <HelpCenter route={helpRoute} onNavigate={navigateHelp} />
      </HelpLayout>
    );
  }
  if (page === "about" && !user) {
    return (
      <main className="public-about-shell">
        <header className="public-about-shell__header"><Brand /><a href={paths.root}>返回首页 <ArrowRight weight="bold" /></a></header>
        {aboutRoute?.screen === "document"
          ? <ProductLegalDocument documentId={aboutRoute.documentId} onNavigate={navigateAbout} />
          : <AboutProduct onNavigate={navigateAbout} onHelpNavigate={navigateHelp} />}
      </main>
    );
  }
  let content;
  if (training) content = <Training sceneId={training.sceneId} sessionId={training.sessionId} sceneTitle={sceneTitle} sceneContent={generatedScene} teacher={teacher} speed={conversationSpeed} initialStep={training.initialStep} initialStage={training.stage} standaloneSpeak={training.standaloneSpeak} result={result} onExit={() => setMainPage(training.returnPage || "scenes")} onComplete={showResult} onBack={() => setMainPage(training.returnPage || "scenes")} onAssets={openCompletedAssetDetail} onStageChange={navigateSceneStage} />;
  else if (page === "conversation") content = <Conversation teacher={teacher} speed={conversationSpeed} level={level} onSettingsChange={persistSettings} onBeforeStart={() => preferenceWriteChainRef.current.catch(() => undefined)} onSessionStarted={(sessionId) => navigate(paths.conversation.session(sessionId), { page: "conversation", conversationSessionId: sessionId, authMode })} onSessionEnded={() => { navigate(paths.conversation.root, { page: "conversation", conversationSessionId: null, authMode }, true); void synchronizeAchievements({ revealNotifications: true }); }} />;
  else if (page === "scenes") content = <Scenes onStartTraining={startTraining} onLocked={setPaywall} onIelts={() => setMainPage("ielts")} onInterview={() => setMainPage("interview")} />;
  else if (page === "assets") content = <Assets sceneId={assetSceneId} initialView={assetView} initialRecordTitle={sceneTitle} onOpenRecord={openCompletedAssetDetail} onCloseRecord={() => navigate(paths.assets.root, { assetView: "home", assetSceneId: null, authMode })} onIelts={() => setMainPage("ielts-assets")} onInterview={() => setMainPage("interview-assets")} onPractice={(scene) => startTraining(scene, "speak", { standaloneSpeak: true, returnPage: "assets" })} onRestart={(scene) => startTraining(scene, "learn", { returnPage: "assets" })} />;
  else if (page === "ielts") content = <IeltsTrainingCenter route={ieltsRoute} onNavigate={navigateIelts} onExit={() => setMainPage("scenes")} onAssets={() => navigateIelts(paths.ielts.assets.root)} />;
  else if (page === "ielts-assets") content = <IeltsAssets route={ieltsRoute} onNavigate={navigateIelts} onBack={() => setMainPage("scenes")} onBackToAssets={() => setMainPage("assets")} onBackToInterview={() => setMainPage("interview-assets")} onTraining={() => navigateIelts(paths.ielts.root)} />;
  else if (page === "interview") content = <InterviewModule route={interviewRoute} teacher={teacher} speed={conversationSpeed} onNavigate={navigateInterview} onBack={() => setMainPage("scenes")} />;
  else if (page === "interview-assets") content = <InterviewAssets route={interviewRoute} onNavigate={navigateInterview} onBack={() => setMainPage("scenes")} onBackToAssets={() => setMainPage("assets")} onBackToIelts={() => setMainPage("ielts-assets")} onTraining={() => navigateInterview(paths.interview.root)} onPractice={(sceneId) => navigateInterview(paths.interview.session(sceneId))} />;
  else content = <Profile section={page} setSection={setMainPage} helpRoute={helpRoute} aboutRoute={aboutRoute} onHelpNavigate={navigateHelp} onAboutNavigate={navigateAbout} user={user} profile={profileOverview} teacher={teacher} speed={conversationSpeed} level={level} onSettingsChange={persistSettings} onMonthChange={loadProfileMonth} onNicknameChange={updateNickname} onAvatarChange={updateAvatar} onPasswordChange={updatePassword} onAssets={() => setMainPage("assets")} onLogout={logout} />;
  return <AppShell page={page} setPage={setMainPage} teacher={teacher} avatarUrl={profileOverview?.account?.avatarUrl}>{content}{paywall && <Paywall title={paywall} onClose={() => setPaywall(null)} onMembership={() => { setPaywall(null); setMainPage("membership"); }} />}</AppShell>;
}
