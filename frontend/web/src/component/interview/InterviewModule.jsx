import { useEffect, useRef, useState } from "react";
import {
  ArrowLeft,
  ArrowRight,
  BookOpenText,
  CaretDown,
  CaretRight,
  Check,
  FileText,
  Image,
  Microphone,
  MicrophoneSlash,
  PhoneDisconnect,
  ShieldCheck,
  Sparkle,
  SquaresFour,
  X,
} from "@phosphor-icons/react";
import { NewtonsCradle } from "../common/NewtonsCradle.jsx";
import { EvaluationLoader } from "../common/EvaluationLoader.jsx";
import { Modal } from "../common/Modal.jsx";
import {
  generateInterviewScene,
  getInterviewAssets,
  getInterviewOcrAvailability,
  getInterviewReport,
  prepareInterviewMaterials,
  retryInterviewReport,
} from "../../infrastructure/http/apiClient.js";
import { createRealtimeClient } from "../../websocket/realtimeClient.js";
import { analytics } from "../../analytics/analyticsClient.js";
import { paths } from "../../controller/router.js";
import { SimpleCta, TrendLineChart } from "../ielts/IeltsModule.jsx";

const cx = (...parts) => parts.filter(Boolean).join(" ");

const DIFFICULTY_LABELS = { EASY: "简单", STANDARD: "标准", HARD: "困难" };

const speedCodeByLabel = {
  "慢一些": "SLOWER",
  "适中": "MODERATE",
  "自然": "NATURAL",
  "快一些": "FASTER",
};

const interviewDifficulties = [
  { id: "EASY", label: "简单", note: "每主题 1 个浅层追问" },
  { id: "STANDARD", label: "标准", note: "每主题 1 个中等追问" },
  { id: "HARD", label: "困难", note: "每主题 2 个深层追问" },
];

const interviewEditorGroups = [
  {
    id: "job",
    eyebrow: "JOB PROFILE",
    title: "JD 岗位信息",
    hint: "面试官会以此设定提问方向与职位背景。",
    fields: [
      { key: "jobTitle", label: "岗位名称", scalar: true },
      { key: "otherJobInformation", label: "其他信息", scalar: true },
    ],
  },
  {
    id: "requirements",
    eyebrow: "ROLE & REQUIREMENTS",
    title: "职责与要求",
    hint: "面试官将围绕这些要点逐轮展开追问。",
    fields: [
      { key: "responsibilities", label: "岗位职责", required: true },
      { key: "qualificationRequirements", label: "任职要求", required: true },
      { key: "requiredSkills", label: "必备技能" },
    ],
  },
  {
    id: "experience",
    eyebrow: "EXPERIENCE & SKILLS",
    title: "经历与技能",
    hint: "用于深挖你的个人经历，可只保留亮点。",
    fields: [
      { key: "education", label: "教育经历" },
      { key: "workExperiences", label: "工作经历" },
      { key: "projectExperiences", label: "项目经历" },
      { key: "skillsAndAbilities", label: "技能与能力" },
      { key: "interviewableExperienceClues", label: "可深挖经历线索" },
    ],
  },
];

const listToLines = (values) => (Array.isArray(values) ? values : []).join("\n");
const linesToList = (value) => String(value || "")
  .split("\n")
  .map((line) => line.trim())
  .filter(Boolean);

const materialList = (value) => {
  if (Array.isArray(value)) return value.filter(Boolean).map((item) => String(item).trim()).filter(Boolean);
  if (typeof value === "string") return linesToList(value.replaceAll("；", "\n").replaceAll(";", "\n"));
  return [];
};

const normalizeInterviewMaterial = (value) => {
  const raw = value?.material && typeof value.material === "object" ? value.material : value;
  if (!raw || typeof raw !== "object") return null;
  const material = {
    jobTitle: typeof raw.jobTitle === "string" ? raw.jobTitle.trim() : "",
    responsibilities: materialList(raw.responsibilities),
    qualificationRequirements: materialList(raw.qualificationRequirements),
    requiredSkills: materialList(raw.requiredSkills),
    otherJobInformation: typeof raw.otherJobInformation === "string" ? raw.otherJobInformation.trim() : "",
    education: materialList(raw.education),
    workExperiences: materialList(raw.workExperiences),
    projectExperiences: materialList(raw.projectExperiences),
    skillsAndAbilities: materialList(raw.skillsAndAbilities),
    interviewableExperienceClues: materialList(raw.interviewableExperienceClues),
    finalText: typeof raw.finalText === "string" ? raw.finalText.trim() : "",
  };
  if (!material.responsibilities.length || !material.qualificationRequirements.length) return null;
  if (!material.finalText) {
    material.finalText = [
      material.jobTitle,
      material.responsibilities.slice(0, 3).join("、"),
      material.qualificationRequirements.slice(0, 3).join("、"),
    ].filter(Boolean).join(" · ");
  }
  return material;
};

function AutoGrowTextarea({ value, invalid = false, onChange, ...rest }) {
  const ref = useRef(null);
  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    el.style.height = "auto";
    el.style.height = `${Math.min(el.scrollHeight, 240)}px`;
  }, [value]);
  return (
    <textarea
      ref={ref}
      rows={2}
      value={value}
      aria-invalid={invalid || undefined}
      {...rest}
      onChange={(event) => onChange(event.target.value)}
    />
  );
}

const listHasContent = (material, key) => {
  const values = Array.isArray(material?.[key]) ? material[key] : [];
  return values.some((item) => String(item || "").trim());
};

const isLegacyDocFile = (file) => {
  const name = String(file?.name || "").trim().toLowerCase();
  return name.endsWith(".doc") && !name.endsWith(".docx");
};

const interviewWaveRestingLevels = [.28, .52, .78, 1, .72, .48, .3];

function InterviewWaveform({ active = false, compact = false }) {
  return (
    <span className={cx("voice-wave", compact && "voice-wave--compact", active && "is-active")} aria-hidden="true">
      {interviewWaveRestingLevels.map((level, index) => <i key={index} className="voice-wave__bar" style={{ "--rest-level": level }} />)}
    </span>
  );
}

function InterviewTimer({ state = "active", paused = false }) {
  const [elapsedSeconds, setElapsedSeconds] = useState(0);
  useEffect(() => {
    const startedAt = Date.now();
    const updateElapsed = () => setElapsedSeconds(Math.floor((Date.now() - startedAt) / 1000));
    updateElapsed();
    const interval = window.setInterval(updateElapsed, 1000);
    return () => window.clearInterval(interval);
  }, []);
  const duration = `${String(Math.floor(elapsedSeconds / 60)).padStart(2, "0")}:${String(elapsedSeconds % 60).padStart(2, "0")}`;
  const label = state === "ended" ? "已结束" : paused ? `已暂停 · ${duration}` : duration;
  return <time className="call-presence__time">{label}</time>;
}

function InterviewTranscript({ lines, status, transcriptRef }) {
  return (
    <div ref={transcriptRef} className="transcript interview-session__transcript" aria-label="面试实时字幕" tabIndex="0">
      {lines.length === 0
        ? <article className="transcript__line"><small>字幕</small><p>{status}</p></article>
        : lines.map((line, index) => <article key={line.id || index} className={cx("transcript__line", line.who === "你" && "is-user")}><small>{line.who}</small><p>{line.en}</p></article>)}
    </div>
  );
}

function MaterialEditor({ material, onChange, compact = false }) {
  const updateScalar = (key, value) => onChange({ ...material, [key]: value });
  const updateList = (key, value) => onChange({ ...material, [key]: linesToList(value) });
  const missing = interviewEditorGroups.flatMap((group) =>
    group.fields
      .filter((field) => field.required && !listHasContent(material, field.key))
      .map((field) => ({ groupId: group.id, label: field.label })),
  );
  const missingCountByGroup = (groupId) => missing.filter((item) => item.groupId === groupId).length;

  return (
    <section className="interview-editor">
      {!compact && (
        <header className="interview-editor__heading">
          <p className="eyebrow">MATERIAL REVIEW</p>
          <h2>整理后的面试材料</h2>
          <p>AI 已从 JD 与简历中整理出岗位与经历要点，你可以直接修改，然后生成面试。</p>
        </header>
      )}
      <div className="interview-editor__sections">
        {interviewEditorGroups.map((group) => {
          const missingCount = missingCountByGroup(group.id);
          return (
            <section key={group.id} className="interview-editor__section" aria-labelledby={`interview-editor-${group.id}-title`}>
              <header className="interview-editor__section-header">
                <div>
                  <p className="eyebrow">{group.eyebrow}</p>
                  <h3 id={`interview-editor-${group.id}-title`}>{group.title}</h3>
                  <p>{group.hint}</p>
                </div>
                {group.fields.some((field) => field.required) && (
                  <span className={cx("interview-editor__section-required", missingCount > 0 && "is-missing")}>
                    {missingCount > 0 ? `还有 ${missingCount} 项必填` : "必填项已齐全"}
                  </span>
                )}
              </header>
              <div className="interview-editor__fields">
                {group.fields.map((field) => {
                  const isList = !field.scalar;
                  const value = isList ? listToLines(material[field.key]) : String(material[field.key] ?? "");
                  const missingThis = isList && field.required && !value.trim();
                  return (
                    <label key={field.key} className={cx("interview-editor__field", isList && "interview-editor__field--list", missingThis && "is-invalid")}>
                      <span>{field.label}{field.required ? <em>必填</em> : null}{isList ? <small>每行一条</small> : null}</span>
                      {isList ? (
                        <AutoGrowTextarea
                          value={value}
                          invalid={missingThis}
                          placeholder={field.required ? `请至少填写一条${field.label}` : "可留空，面试官不会追问"}
                          onChange={(next) => updateList(field.key, next)}
                        />
                      ) : (
                        <input
                          type="text"
                          value={value}
                          placeholder={field.key === "otherJobInformation" ? "薪资、地点、到岗时间等补充信息" : "如：前端工程师"}
                          onChange={(event) => updateScalar(field.key, event.target.value)}
                        />
                      )}
                      {missingThis && <span className="interview-editor__field-error">至少填写一条{field.label}后，面试才能生成。</span>}
                    </label>
                  );
                })}
              </div>
            </section>
          );
        })}
      </div>
      {missing.length > 0 && (
        <p className="interview-editor__summary-error" role="alert">
          还有必填内容未填写：{missing.map((item) => item.label).join("、")}。请补充后点击「确认并生成面试」。
        </p>
      )}
    </section>
  );
}

function InterviewHome({ onNavigate, onBack }) {
  const [ocrAvailable, setOcrAvailable] = useState(false);
  const [ocrAvailabilityLoading, setOcrAvailabilityLoading] = useState(true);
  const [jdMode, setJdMode] = useState("text");
  const [jdText, setJdText] = useState("");
  const [jdImage, setJdImage] = useState(null);
  const [resumeMode, setResumeMode] = useState("text");
  const [resumeText, setResumeText] = useState("");
  const [resumeFile, setResumeFile] = useState(null);
  const [difficulty, setDifficulty] = useState("STANDARD");
  const [preparing, setPreparing] = useState(false);
  const [draft, setDraft] = useState(null);
  const [draftOpen, setDraftOpen] = useState(false);
  const [formError, setFormError] = useState("");
  const [generating, setGenerating] = useState(false);
  const [generateError, setGenerateError] = useState("");
  const jdImageUnavailable = ocrAvailabilityLoading || !ocrAvailable;

  useEffect(() => {
    let cancelled = false;
    getInterviewOcrAvailability()
      .then((result) => {
        if (cancelled) return;
        setOcrAvailable(result?.available === true);
      })
      .catch(() => {
        if (!cancelled) setOcrAvailable(false);
      })
      .finally(() => {
        if (!cancelled) setOcrAvailabilityLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const prepareMaterials = async () => {
    if (preparing) return;
    setFormError("");
    if (jdMode === "image" && ocrAvailabilityLoading) {
      setFormError("正在检测 OCR，请稍后再试");
      return;
    }
    if (jdMode === "image" && !ocrAvailable) {
      setFormError("OCR 暂不可用，请使用粘贴文本方式上传 JD");
      return;
    }
    const formData = new FormData();
    if (jdMode === "text") {
      if (!jdText.trim()) {
        setFormError("请输入 JD 文本，或切换到图片方式上传岗位描述截图");
        return;
      }
      formData.append("jobDescriptionText", jdText);
    } else {
      if (!jdImage) {
        setFormError("请选择一张包含 JD 的图片");
        return;
      }
      formData.append("jobDescriptionImage", jdImage);
    }
    if (resumeMode === "text") {
      if (resumeText.trim()) formData.append("resumeText", resumeText);
    } else if (resumeFile) {
      if (isLegacyDocFile(resumeFile)) {
        setFormError(".doc 简历暂不支持，请上传 PDF 或 DOCX 文本简历");
        return;
      }
      formData.append("resumeFile", resumeFile);
    }
    setPreparing(true);
    try {
      const result = await prepareInterviewMaterials(formData);
      const material = normalizeInterviewMaterial(result);
      if (!material) {
        throw new Error("材料整理响应缺少结构化内容");
      }
      setDraft(material);
      setDraftOpen(true);
      setGenerateError("");
    } catch (error) {
      setFormError(error instanceof Error ? error.message : "材料整理失败，请稍后重试");
    } finally {
      setPreparing(false);
    }
  };

  const confirmAndGenerate = async () => {
    if (generating || !draft) return;
    setGenerateError("");
    const material = {
      jobTitle: draft.jobTitle || "",
      responsibilities: draft.responsibilities || [],
      qualificationRequirements: draft.qualificationRequirements || [],
      requiredSkills: draft.requiredSkills || [],
      otherJobInformation: draft.otherJobInformation || "",
      education: draft.education || [],
      workExperiences: draft.workExperiences || [],
      projectExperiences: draft.projectExperiences || [],
      skillsAndAbilities: draft.skillsAndAbilities || [],
      interviewableExperienceClues: draft.interviewableExperienceClues || [],
      finalText: draft.finalText || "",
    };
    if (!material.responsibilities.length || !material.qualificationRequirements.length) {
      setGenerateError("岗位职责与任职要求不能为空，请补充后再生成面试");
      return;
    }
    setGenerating(true);
    try {
      const result = await generateInterviewScene({ material, difficulty });
      if (!result?.sceneId) {
        throw new Error("面试场景生成响应缺少 sceneId");
      }
      try {
        window.sessionStorage.setItem(
          "unispeaking.interview.lastScene",
          JSON.stringify({ sceneId: result.sceneId, difficulty }),
        );
      } catch {
        // Session storage may be unavailable; the URL still carries the scene id.
      }
      onNavigate(paths.interview.session(result.sceneId));
    } catch (error) {
      setGenerateError(error instanceof Error ? error.message : "面试场景生成失败，请稍后重试");
    } finally {
      setGenerating(false);
    }
  };

  return (
    <main className="page page--interview">
      <div className="interview-home">
        <button className="ielts-back" onClick={onBack}><ArrowLeft />返回</button>
        <PageHeader
          eyebrow="JOB INTERVIEW"
          title="模拟面试"
          subtitle="上传 JD 与简历，AI 面试官按真实岗位流程向你提问，并在结束后生成五维报告。"
        />
        <section className="interview-builder interview-module">
          <div className="scene-section-heading scene-section-heading--primary">
            <div><p className="eyebrow">BUILD YOUR INTERVIEW</p><h2>准备面试材料</h2><p>先整理岗位描述与简历，AI 会提炼出本次面试的考察重点。</p></div>
            <div className="interview-builder__badge"><ShieldCheck weight="fill" />材料经脱敏后使用</div>
          </div>

          <div className="interview-form">
            <fieldset className="interview-form__group">
              <legend>简历 <small>选填</small></legend>
              <div className="interview-source-toggle">
                <button type="button" className={resumeMode === "text" ? "is-active" : ""} onClick={() => setResumeMode("text")}>粘贴文本</button>
                <button type="button" className={resumeMode === "file" ? "is-active" : ""} onClick={() => setResumeMode("file")}>上传文件</button>
              </div>
              {resumeMode === "text"
                ? <textarea className="interview-form__textarea" value={resumeText} maxLength={20000} onChange={(event) => setResumeText(event.target.value)} placeholder="粘贴简历中的工作与项目经历…（可留空）" />
                : <FilePicker accept=".pdf,.docx,.doc" hint="支持 PDF / DOCX 文本简历，.doc 暂不支持" file={resumeFile} onFile={setResumeFile} icon={<FileText weight="bold" />} />}
            </fieldset>

            <fieldset className="interview-form__group">
              <legend>岗位描述（JD）</legend>
              <div className="interview-source-toggle">
                <button type="button" className={jdMode === "text" ? "is-active" : ""} onClick={() => setJdMode("text")}>粘贴文本</button>
                <button type="button" className={jdMode === "image" ? "is-active" : ""} disabled={jdImageUnavailable} title={ocrAvailabilityLoading ? "正在检测 OCR" : jdImageUnavailable ? "OCR 暂不可用，请粘贴 JD 文本" : "上传岗位描述图片，由 OCR 识别文字"} onClick={() => setJdMode("image")}>上传图片</button>
              </div>
              {jdMode === "text"
                ? <textarea className="interview-form__textarea" value={jdText} maxLength={20000} onChange={(event) => setJdText(event.target.value)} placeholder="粘贴招聘 JD 的职责与任职要求文本…" />
                : jdImageUnavailable
                  ? <p className="call-error" role="alert">{ocrAvailabilityLoading ? "正在检测 OCR，请稍候…" : "OCR 暂不可用，请使用“粘贴文本”方式上传 JD。"}</p>
                  : <FilePicker accept="image/*" hint="支持单张图片，将由 OCR 识别文字" file={jdImage} onFile={setJdImage} icon={<Image weight="bold" />} />}
            </fieldset>

            <fieldset className="interview-form__group">
              <legend>面试难度</legend>
              <div className="interview-difficulty">
                {interviewDifficulties.map((item) => (
                  <button key={item.id} type="button" className={difficulty === item.id ? "is-active" : ""} onClick={() => setDifficulty(item.id)}>
                    <strong>{item.label}</strong><small>{item.note}</small>{difficulty === item.id && <Check weight="bold" />}
                  </button>
                ))}
              </div>
            </fieldset>

            {(formError || generateError) && <p className="call-error" role="alert">{formError || generateError}</p>}

            <div className="interview-form__actions">
              <button className="button button--secondary" onClick={onBack}>返回</button>
              <ExpandingCta disabled={preparing} onClick={() => (draft ? setDraftOpen(true) : void prepareMaterials())}>{preparing ? "正在整理材料" : draft ? "查看整理结果" : "整理材料"}</ExpandingCta>
            </div>
          </div>
        </section>

        {draftOpen && draft && (
          <Modal onClose={() => setDraftOpen(false)} wide className="interview-material-modal">
            <p className="eyebrow">MATERIAL REVIEW</p>
            <h2>整理后的面试材料</h2>
            <p className="modal-lead">AI 已从 JD 与简历中整理出岗位与经历要点，你可以直接修改，然后生成面试。</p>
            <MaterialEditor material={draft} onChange={setDraft} compact />
            <div className="modal-actions">
              <button className="button button--secondary" disabled={preparing} onClick={() => void prepareMaterials()}>重新整理</button>
              <ExpandingCta disabled={generating} onClick={() => void confirmAndGenerate()}>{generating ? "正在生成面试" : "确认并生成面试"}</ExpandingCta>
            </div>
          </Modal>
        )}
      </div>
    </main>
  );
}

function FilePicker({ accept, hint, file, onFile, icon }) {
  const inputRef = useRef(null);
  const [invalid, setInvalid] = useState(false);
  const select = (event) => {
    const selected = event.target.files?.[0] || null;
    event.target.value = "";
    if (!selected) return;
    if (isLegacyDocFile(selected)) {
      setInvalid(true);
      onFile(null);
      return;
    }
    setInvalid(false);
    onFile(selected);
  };
  return (
    <div className={cx("interview-file-picker", file && "has-file")} onClick={() => inputRef.current?.click()}>
      <input ref={inputRef} type="file" accept={accept} onChange={select} />
      {file
        ? <><span className="interview-file-picker__icon">{icon}</span><strong>{file.name}</strong><small>已选择，点击可更换</small><button type="button" aria-label="清除所选文件" onClick={(event) => { event.stopPropagation(); onFile(null); setInvalid(false); }}><X weight="bold" /></button></>
        : <><span className="interview-file-picker__icon">{icon}</span><strong>选择文件</strong>{invalid ? <em>.doc 简历暂不支持，请上传 PDF / DOCX</em> : <small>{hint}</small>}</>}
    </div>
  );
}

function InterviewSession({ sceneId, teacher, speed, onEndInterview, onExit }) {
  const [status, setStatus] = useState("正在连接面试官");
  const [error, setError] = useState("");
  const [paused, setPaused] = useState(false);
  const [ending, setEnding] = useState(false);
  const [closing, setClosing] = useState(false);
  const [lines, setLines] = useState([]);
  const [exitOpen, setExitOpen] = useState(false);
  const clientRef = useRef(null);
  const sessionIdRef = useRef("");
  const interviewAnalyticsRef = useRef(null);
  const remoteAudioRef = useRef(null);
  const endingRef = useRef(false);
  const transcriptRef = useRef(null);
  const onEndInterviewRef = useRef(onEndInterview);
  const teacherNameRef = useRef(teacher?.name || "面试官");
  const detachInterviewRemoteAudio = () => {
    const audio = remoteAudioRef.current;
    if (!audio) return;
    audio.pause();
    audio.srcObject = null;
  };
  useEffect(() => { onEndInterviewRef.current = onEndInterview; });
  useEffect(() => { teacherNameRef.current = teacher?.name || "面试官"; });

  const updateLine = ({ id, who, text = "", delta = "", final = false }) => {
    const content = String(text || delta || "");
    if (!content) return;
    setLines((current) => {
      const lineId = id || `${who}-live`;
      const exact = current.findIndex((line) => line.id === lineId);
      const fallback = final ? current.findLastIndex((line) => line.who === who && !line.final) : -1;
      const index = exact >= 0 ? exact : fallback;
      if (index < 0) return [...current, { id: lineId, who, en: content, final }];
      const next = [...current];
      next[index] = { ...next[index], id: lineId, en: text || `${next[index].en}${delta}`, final };
      return next;
    });
  };

  const handleEvent = (event) => {
    if (event.type === "local.connecting") setStatus("正在连接面试官");
    else if (event.type === "local.connected") {
      interviewAnalyticsRef.current?.started();
      setStatus("正在建立面试会话");
      sessionIdRef.current = event.sessionId || "";
    } else if (event.type === "session.updated" || event.type === "local.greeting_timeout") {
      setStatus("面试官正在向你提问");
    } else if (event.type === "input_audio_buffer.speech_started") {
      setStatus("正在听你回答");
    } else if (event.type === "response.audio.delta") {
      setStatus(`${teacherNameRef.current} 正在提问`);
    } else if (event.type === "response.done") {
      if (!endingRef.current) setStatus("请开始回答");
    } else if (event.type === "local.transcript.final") {
      updateLine({ id: event.itemId, who: event.owner === 1 ? "你" : teacherNameRef.current, text: event.text, final: true });
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
        who: teacherNameRef.current,
        delta: event.delta || event.text || "",
      });
    } else if (event.type === "local.interview_state") {
      const state = event.state;
      if (endingRef.current) {
        // Report recovery carries only reportStatus; keep the ending status.
      } else if (state?.shouldEnd) {
        setStatus("面试已完成，准备收尾…");
      } else if (state?.currentTopic) {
        setStatus(`正在面试 · ${state.currentTopic}`);
      } else {
        setStatus("面试官正在提问");
      }
    } else if (event.type === "local.interview_closing") {
      endingRef.current = true;
      setEnding(true);
      setClosing(true);
      setStatus("面试官正在做本次面试的收尾…");
    } else if (event.type === "local.interview_end_requested") {
      interviewAnalyticsRef.current?.complete();
      endingRef.current = true;
      setEnding(true);
      setClosing(false);
      setStatus("面试已结束，正在生成报告");
      detachInterviewRemoteAudio();
      onEndInterviewRef.current?.(sceneId, sessionIdRef.current, event.reportStatus || null);
    } else if (event.type === "local.interview_end_error") {
      setError(event.message || "面试自动结束失败");
    } else if (event.type === "local.backend_warning") {
      setError(event.message || "会话记录保存失败，请稍后重试");
    } else if (event.type === "local.mic_error") {
      setError(event.message || "无法访问麦克风");
      setStatus("麦克风不可用，请检查权限");
    } else if (event.type === "error" || event.type === "local.error") {
      setError(event.message || event.error?.message || "实时会话发生错误");
      setStatus("连接异常");
    }
  };

  useEffect(() => {
    let cancelled = false;
    interviewAnalyticsRef.current = analytics.training({ mode: "INTERVIEW", pageCode: "interview-training" });
    interviewAnalyticsRef.current.attempt();
    const client = createRealtimeClient({
      sceneId,
      sceneType: "interview",
      onEvent: (event) => {
        if (!cancelled) handleEvent(event);
      },
      onRemoteStream: (stream) => {
        if (cancelled || !remoteAudioRef.current) return;
        remoteAudioRef.current.srcObject = stream;
        void remoteAudioRef.current.play().catch(() => setStatus("点击页面后可播放面试官声音"));
      },
      onRemoteAudioDrain: ({ fallbackMs = 2_500, timeoutMs = 10_000 } = {}) => {
        const audio = remoteAudioRef.current;
        if (!audio) return new Promise((resolve) => window.setTimeout(resolve, fallbackMs));

        // WebRTC MediaStream audio has no reliable `ended` event. Wait until
        // the track has gone quiet after the provider has finished generating
        // the closing response. A conservative fallback covers browsers that
        // do not expose an analyser for a remote track.
        const stream = audio.srcObject;
        const tracks = stream?.getAudioTracks?.() || [];
        if (!tracks.length || !window.AudioContext) {
          return new Promise((resolve) => window.setTimeout(resolve, fallbackMs));
        }
        const context = new window.AudioContext();
        const source = context.createMediaStreamSource(stream);
        const analyser = context.createAnalyser();
        analyser.fftSize = 2048;
        source.connect(analyser);
        const data = new Uint8Array(analyser.fftSize);
        const startedAt = Date.now();
        const notBefore = startedAt + fallbackMs;
        let quietSince = null;
        let timer = null;
        return new Promise((resolve) => {
          const cleanup = () => {
            if (timer) window.clearInterval(timer);
            source.disconnect();
            analyser.disconnect();
            void context.close();
          };
          const finish = () => { cleanup(); resolve(); };
          timer = window.setInterval(() => {
            analyser.getByteTimeDomainData(data);
            let peak = 0;
            for (const value of data) peak = Math.max(peak, Math.abs(value - 128));
            if (peak <= 3 && Date.now() >= notBefore) {
              quietSince ??= Date.now();
              if (Date.now() - quietSince >= 350) finish();
            } else {
              quietSince = null;
            }
            if (Date.now() - startedAt >= timeoutMs) finish();
          }, 50);
        });
      },
    });
    clientRef.current = client;
    void client.start({
      voice: teacher?.voiceId || "Katerina",
      speechSpeed: speedCodeByLabel[speed] || "NATURAL",
      silenceDurationMs: 3_000,
      turnDetectionType: "semantic_vad",
      interruptResponse: true,
    }).then(() => {
      if (!cancelled) interviewAnalyticsRef.current.started();
    }).catch((startError) => {
      if (!cancelled) {
        interviewAnalyticsRef.current.fail("REALTIME_ERROR");
        setError(startError instanceof Error ? startError.message : "无法开始面试会话");
      }
    });
    const syncVisibility = () => interviewAnalyticsRef.current?.setVisible(document.visibilityState === "visible");
    document.addEventListener("visibilitychange", syncVisibility);
    syncVisibility();
    return () => {
      cancelled = true;
      document.removeEventListener("visibilitychange", syncVisibility);
      interviewAnalyticsRef.current?.abandon("COMPONENT_UNMOUNT");
      detachInterviewRemoteAudio();
      clientRef.current = null;
      void client.stop({ notifyBackend: false, reason: "component_unmount", emitEnded: false });
    };
  }, [sceneId]);

  useEffect(() => {
    const frame = window.requestAnimationFrame(() => {
      if (transcriptRef.current) transcriptRef.current.scrollTop = transcriptRef.current.scrollHeight;
    });
    return () => window.cancelAnimationFrame(frame);
  }, [lines]);

  const togglePaused = async () => {
    if (ending) return;
    const next = !paused;
    setPaused(next);
    if (next) {
      await clientRef.current?.pause();
      interviewAnalyticsRef.current?.pause();
    } else {
      await clientRef.current?.resume();
      interviewAnalyticsRef.current?.resume();
    }
  };

  const endConversation = async () => {
    if (endingRef.current) return;
    endingRef.current = true;
    setEnding(true);
    setError("");
    setStatus("正在结束面试并生成报告");
    detachInterviewRemoteAudio();
    try {
      const completion = await clientRef.current?.stop({ reason: "user_stop" });
      clientRef.current = null;
      interviewAnalyticsRef.current?.complete();
      onEndInterviewRef.current?.(sceneId, sessionIdRef.current, completion?.reportStatus || null);
    } catch (stopError) {
      endingRef.current = false;
      setEnding(false);
      setError(stopError instanceof Error ? stopError.message : "结束面试失败，请稍后重试");
      setStatus("结束失败");
    }
  };

  const abandon = async () => {
    if (endingRef.current) return;
    endingRef.current = true;
    const client = clientRef.current;
    clientRef.current = null;
    detachInterviewRemoteAudio();
    await client?.stop({ notifyBackend: false, reason: "user_exit", emitEnded: false });
    interviewAnalyticsRef.current?.abandon("USER_EXIT");
    onExit();
  };

  const exitDialog = exitOpen && (
    <div className="ielts-dialog-backdrop"><section className="ielts-dialog"><h2>退出当前面试？</h2><p>本次未完成的面试不会生成报告，也不会计入今日练习次数。</p><div><button onClick={() => setExitOpen(false)}>继续面试</button><button onClick={() => void abandon()}>确认退出</button></div></section></div>
  );

  return (
    <main className="conversation call call--subtitles interview-call">
      <audio ref={remoteAudioRef} autoPlay />
      <div className="conversation__top interview-call-top">
        <button className="ielts-back" onClick={() => setExitOpen(true)}><ArrowLeft />返回</button>
        <button className="round-control interview-call-exit" disabled={ending} onClick={() => setExitOpen(true)} aria-label="退出面试"><X /></button>
      </div>
      <section className="call__stage">
        <div className="call-presence call-presence--compact">
          <div className="portrait portrait--small interview-call-portrait"><img src={teacher?.image} alt={teacher?.name} /></div>
          <div className="listening-state listening-state--compact">
            <InterviewWaveform active={!ending && !paused && !error} compact />
            <InterviewTimer paused={paused} state={ending || error ? "ended" : "active"} />
            {(!ending || closing) && <span>{status}</span>}
          </div>
        </div>
        <InterviewTranscript lines={lines} status={status} transcriptRef={transcriptRef} />
        {error && <p className="call-error" role="alert">{error}</p>}
      </section>
      <div className="call-controls interview-call-controls">
        <button className={cx("round-control", paused && "is-on")} aria-label={paused ? "恢复会话" : "暂停会话"} disabled={ending} onClick={() => void togglePaused()}>{paused ? <MicrophoneSlash /> : <Microphone />}</button>
        <button className="round-control round-control--end" aria-label="结束面试" disabled={ending} onClick={() => void endConversation()}><PhoneDisconnect weight="fill" /></button>
      </div>
      {exitDialog}
    </main>
  );
}

const reportDimensionMeta = {
  FLUENCY: { label: "流利度", hint: "语速、停顿与表达连贯" },
  PRONUNCIATION_INTELLIGIBILITY: { label: "发音可懂度", hint: "语音清晰度与重音节奏" },
  LOGIC_COHERENCE: { label: "逻辑连贯", hint: "结构层次与衔接" },
  GRAMMAR_CONTROL: { label: "语法掌控", hint: "时态、句式与准确度" },
  VOCABULARY_EXPRESSION: { label: "词汇表达", hint: "用词丰富度与贴切度" },
};

function InterviewReport({ sceneId, sessionId, onHome }) {
  const [status, setStatus] = useState("PROCESSING");
  const [report, setReport] = useState(null);
  const [failureReason, setFailureReason] = useState("");
  const [error, setError] = useState("");
  const [retrying, setRetrying] = useState(false);
  const [pollVersion, setPollVersion] = useState(0);

  useEffect(() => {
    let cancelled = false;
    let timer = null;
    const poll = async () => {
      try {
        const response = await getInterviewReport(sceneId, sessionId);
        if (cancelled) return;
        setStatus(response.status);
        setReport(response.report || null);
        setFailureReason(response.failureReason || "");
        setError("");
        if (response.status === "PROCESSING") {
          timer = window.setTimeout(poll, 2_000);
        }
      } catch (requestError) {
        if (cancelled) return;
        setError(requestError instanceof Error ? requestError.message : "报告加载失败，正在重试");
        timer = window.setTimeout(poll, 2_000);
      }
    };
    void poll();
    return () => {
      cancelled = true;
      if (timer) window.clearTimeout(timer);
    };
  }, [sceneId, sessionId, pollVersion]);

  const retry = async () => {
    if (retrying) return;
    setRetrying(true);
    setError("");
    try {
      const response = await retryInterviewReport(sceneId, sessionId);
      setStatus(response.status);
      setReport(response.report || null);
      setFailureReason(response.failureReason || "");
      if (response.status === "PROCESSING") {
        setPollVersion((value) => value + 1);
      }
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "重新生成失败，请稍后重试");
    } finally {
      setRetrying(false);
    }
  };

  if (status === "PROCESSING") {
    return (
      <main className="page page--interview interview-report-page">
        <div className="interview-report-pending">
          <EvaluationLoader />
          <p className="eyebrow">REPORT GENERATING</p>
          <h1>正在生成面试报告</h1>
          <p>AI 正在逐维度评估你的整场回答，通常需要 1–2 分钟。报告会自动出现，无需刷新。</p>
          {error && <p className="call-error" role="alert">{error}</p>}
          <button className="button button--secondary" onClick={onHome}>返回</button>
        </div>
      </main>
    );
  }

  if (status === "FAILED") {
    return (
      <main className="page page--interview interview-report-page">
        <div className="interview-report-pending">
          <button className="ielts-back" onClick={onHome}><ArrowLeft />返回</button>
          <p className="eyebrow">REPORT FAILED</p>
          <h1>报告生成失败</h1>
          <p>{failureReason || "报告生成过程中发生异常，请重新生成一次。"}</p>
          {error && <p className="call-error" role="alert">{error}</p>}
          <div className="interview-report-pending__actions">
            <button className="button button--secondary" onClick={onHome}>返回</button>
            <ExpandingCta disabled={retrying} onClick={() => void retry()}>{retrying ? "正在重新生成" : "重新生成"}</ExpandingCta>
          </div>
        </div>
      </main>
    );
  }

  const dimensions = Array.isArray(report?.dimensions) ? report.dimensions : [];
  const hasOverall = report?.overallScore != null
      && Number.isFinite(Number(report.overallScore));
  const overallScore = hasOverall ? Number(report.overallScore) : null;

  return (
    <main className="page page--interview interview-report-page">
      <div className="interview-report">
        <button className="ielts-back" onClick={onHome}><ArrowLeft />返回</button>
        <PageHeader
          eyebrow="INTERVIEW REPORT"
          title="面试表现报告"
          subtitle="整场回答的五维评估与改进建议，已自动打卡。"
          action={<button className="button button--secondary interview-report-home" onClick={onHome}>返回训练中心</button>}
        />
        <section className="interview-report__summary">
          <div className="interview-report__score">
            <span>综合评分</span>
            <strong>{hasOverall ? Math.round(overallScore) : "—"}</strong>
            {hasOverall && <small>/ 100</small>}
          </div>
          <div className="interview-report__overview">
            <p className="eyebrow">SUMMARY</p>
            <h2>整场表现</h2>
            <p>{report?.summary || "本次面试已结束，暂无文字总结。"}</p>
          </div>
        </section>
        <section className="interview-report__dimensions">
          <h2>五维能力反馈</h2>
          <div className="interview-report__dimension-grid">
            {dimensions.map((item) => {
              const meta = reportDimensionMeta[item.dimension] || { label: item.dimension, hint: "" };
              const score = item.score == null ? null : Number(item.score);
              const hasScore = score != null && Number.isFinite(score);
              return (
                <article key={item.dimension} className="interview-report__dimension">
                  <header><span>{meta.label}<small>{meta.hint}</small></span><strong className={cx(hasScore && score < 60 && "is-low")}>{hasScore ? Math.round(score) : "—"}</strong></header>
                  <p>{item.evaluation || "该维度暂无可用的评分说明。"}</p>
                  {item.advice && <div className="interview-report__advice"><Sparkle weight="fill" />{item.advice}</div>}
                </article>
              );
            })}
            {!dimensions.length && <p className="interview-report__empty">报告暂未包含分维度评分。</p>}
          </div>
        </section>
        <div className="interview-report__footer">
          <button className="button button--secondary" onClick={onHome}>返回</button>
        </div>
      </div>
    </main>
  );
}

const interviewAssetTabs = [
  { id: "overview", label: "概览" },
  { id: "history", label: "训练记录" },
  { id: "trends", label: "能力趋势" },
];

function interviewAssetDate(value, withTime = false) {
  if (!value) return "尚未练习";
  const options = withTime
    ? { month: "short", day: "numeric", hour: "2-digit", minute: "2-digit" }
    : { year: "numeric", month: "numeric", day: "numeric" };
  return new Intl.DateTimeFormat("zh-CN", options).format(new Date(value));
}

function interviewAssetStatus(item) {
  if (item.latestReportStatus === "COMPLETED") {
    const score = Number(item.latestOverallScore);
    return Number.isFinite(score) ? `${Math.round(score)} 分` : "已出报告";
  }
  if (item.latestReportStatus === "PROCESSING") return "报告生成中";
  if (item.latestReportStatus === "FAILED") return "报告生成失败";
  return "待练习";
}

function recentInterviewActivity(items) {
  const formatter = new Intl.DateTimeFormat("zh-CN", { weekday: "short" });
  return Array.from({ length: 7 }, (_, index) => {
    const date = new Date();
    date.setHours(0, 0, 0, 0);
    date.setDate(date.getDate() - (6 - index));
    const next = new Date(date);
    next.setDate(next.getDate() + 1);
    const count = items.filter((item) => {
      const practicedAt = new Date(item.latestPracticedAt);
      return Number.isFinite(practicedAt.getTime()) && practicedAt >= date && practicedAt < next;
    }).length;
    return { label: formatter.format(date), count };
  });
}

function reportWeakestDimension(report) {
  const dimensions = Array.isArray(report?.dimensions) ? report.dimensions : [];
  return dimensions
    .map((item) => ({ ...item, scoreValue: Number(item.score) }))
    .filter((item) => Number.isFinite(item.scoreValue))
    .sort((left, right) => left.scoreValue - right.scoreValue)[0] || null;
}

function InterviewAssetsOverview({ items, reportsByScene, onTab }) {
  const completed = items.filter((item) => item.latestReportStatus === "COMPLETED");
  const latest = completed[0] || items[0] || null;
  const latestReport = latest ? reportsByScene[latest.sceneId]?.report : null;
  const weakest = reportWeakestDimension(latestReport);
  const weakestMeta = weakest ? reportDimensionMeta[weakest.dimension] : null;
  const activity = recentInterviewActivity(items);
  const maxCount = Math.max(1, ...activity.map((item) => item.count));
  const activeDays = activity.filter((item) => item.count > 0).length;
  const weeklyCount = activity.reduce((sum, item) => sum + item.count, 0);
  const totalPracticeCount = items.reduce((sum, item) => sum + Number(item.practiceCount || 0), 0);
  const recentSlots = Array.from({ length: 3 }, (_, index) => items[index] || null);
  const latestScore = Number(latest?.latestOverallScore);

  return (
    <section className="interview-assets-overview">
      <section className="interview-assets-hero">
        <div>
          <span>最近一次完整面试</span>
          <h2>{Number.isFinite(latestScore) ? Math.round(latestScore) : "—"}<small>/100</small></h2>
          <p>{latest ? `${latest.jobTitle || "未命名岗位"} · ${interviewAssetDate(latest.latestPracticedAt, true)}` : "完成面试后显示最近表现"}</p>
        </div>
        <div>
          <span>优先提升</span>
          <strong>{weakestMeta?.label || "等待五维报告"}</strong>
          <small>{weakest ? `当前 ${Math.round(weakest.scoreValue)} 分` : "生成报告后自动识别弱项"}</small>
        </div>
        <button className="button button--primary" onClick={() => onTab("trends")}>查看能力趋势<ArrowRight weight="bold" /></button>
      </section>

      <section className="interview-assets-weekly">
        <header>
          <div><span>近七天面试活跃</span><h2>{weeklyCount} <small>次</small></h2><p>累计完成 {totalPracticeCount} 次面试练习</p></div>
          <div className="interview-assets-weekly__stats"><p><strong>{activeDays}</strong><small>活跃天数</small></p><p><strong>{items.length}</strong><small>岗位覆盖</small></p><p><strong>{completed.length}</strong><small>有效报告</small></p></div>
        </header>
        <div className="interview-assets-weekly__bars">{activity.map((item) => <span key={item.label}><i className={item.count ? "" : "is-empty"} style={{ height: `${Math.max(item.count ? 14 : 5, (item.count / maxCount) * 100)}%` }} /><strong>{item.count}</strong><small>{item.label}</small></span>)}</div>
      </section>

      <section className="interview-assets-recent">
        <header><h2>最近面试</h2><span>最近 3 个岗位</span></header>
        <div>
          {recentSlots.map((item, index) => item ? (
            <button key={item.sceneId} onClick={() => onTab("history")}>
              <span>{DIFFICULTY_LABELS[item.difficulty] || item.difficulty || "标准"}难度</span>
              <strong>{item.jobTitle || "未命名岗位"}</strong>
              <small>{interviewAssetDate(item.latestPracticedAt, true)}</small>
              <em>{interviewAssetStatus(item)}</em>
            </button>
          ) : (
            <article className="is-empty" key={`empty-${index}`}>
              <span>记录 {index + 1}</span><strong>暂无面试记录</strong><small>完成面试后显示</small><em>待生成</em>
            </article>
          ))}
        </div>
      </section>
    </section>
  );
}

function InterviewAssetsHistory({ items, reportsByScene, reportsLoading, onPractice }) {
  const [selectedId, setSelectedId] = useState(items[0]?.sceneId || "");
  useEffect(() => {
    setSelectedId((current) => items.some((item) => item.sceneId === current) ? current : items[0]?.sceneId || "");
  }, [items]);
  const selected = items.find((item) => item.sceneId === selectedId) || null;
  const payload = selected ? reportsByScene[selected.sceneId] : null;
  const report = payload?.report || null;
  const reportStatus = payload?.status || selected?.latestReportStatus || "";
  const dimensions = Array.isArray(report?.dimensions) ? report.dimensions : [];
  const hasOverall = report?.overallScore != null && Number.isFinite(Number(report.overallScore));
  const waitingForReport = Boolean(selected?.latestSessionId && reportsLoading && !payload);

  return (
    <section className="ielts-history-layout interview-assets-layout">
      <aside className="interview-assets-list" aria-label="面试训练记录">
        <header><h2>面试记录</h2><span>{items.length} 条</span></header>
        {items.map((item) => (
          <button key={item.sceneId} className={selected?.sceneId === item.sceneId ? "is-active" : ""} onClick={() => setSelectedId(item.sceneId)}>
            <div className="ielts-history-record-meta"><time>{interviewAssetDate(item.latestPracticedAt)}</time><em>{DIFFICULTY_LABELS[item.difficulty] || item.difficulty || "标准"} · {interviewAssetStatus(item)}</em></div>
            <strong>{item.jobTitle || "未命名岗位"}</strong>
            <span className="ielts-history-record-duration">累计练习 {Number(item.practiceCount || 0)} 次</span>
          </button>
        ))}
        {!items.length && <div className="ielts-history-empty">暂无面试学习资产</div>}
      </aside>
      <article className="interview-assets-detail">
        {selected && reportStatus === "COMPLETED" && report && (
          <>
            <header>
              <div><p className="eyebrow">INTERVIEW ASSET</p><h2>{selected.jobTitle || "未命名岗位"}</h2><p>{interviewAssetDate(selected.latestPracticedAt, true)} · {DIFFICULTY_LABELS[selected.difficulty] || "标准"}难度 · 累计练习 {Number(selected.practiceCount || 0)} 次</p></div>
              <div className="interview-assets-detail__actions"><button className="button button--secondary" onClick={() => onPractice(selected.sceneId)}>复练本岗位</button></div>
            </header>
            <section className="interview-assets-report">
              <div className="interview-assets-report__score"><span>综合评分</span><strong>{hasOverall ? Math.round(Number(report.overallScore)) : "—"}</strong><small>/ 100</small></div>
              <div className="interview-assets-report__summary"><p className="eyebrow">SUMMARY</p><p>{report.summary || "本次面试已结束，暂无文字总结。"}</p></div>
            </section>
            <section className="interview-assets-dimensions">
              <h3>五维能力反馈</h3>
              <div className="interview-assets-dimension-grid">
                {dimensions.map((item) => {
                  const meta = reportDimensionMeta[item.dimension] || { label: item.dimension, hint: "" };
                  const score = item.score == null ? null : Number(item.score);
                  const hasScore = score != null && Number.isFinite(score);
                  return <article key={item.dimension} className="interview-assets-dimension"><header><span>{meta.label}<small>{meta.hint}</small></span><strong className={hasScore && score < 60 ? "is-low" : ""}>{hasScore ? Math.round(score) : "—"}</strong></header><p>{item.evaluation || "该维度暂无可用的评分说明。"}</p></article>;
                })}
                {!dimensions.length && <p className="interview-assets-empty">报告暂未包含分维度评分。</p>}
              </div>
            </section>
          </>
        )}
        {selected && waitingForReport && <div className="ielts-history-empty interview-assets-state"><NewtonsCradle label="正在读取面试报告" /></div>}
        {selected && !waitingForReport && selected.latestSessionId && (reportStatus !== "COMPLETED" || !report) && (
          <div className="ielts-history-empty interview-assets-state"><h2>{reportStatus === "PROCESSING" ? "报告生成中" : "报告暂不可用"}</h2><p>{reportStatus === "FAILED" ? "上一次报告生成失败，可直接复练本岗位重新面试。" : "报告仍在生成或读取失败，稍后回到这里即可查看五维反馈。"}</p><button className="button button--primary" onClick={() => onPractice(selected.sceneId)}>复练本岗位</button></div>
        )}
        {selected && !selected.latestSessionId && <div className="ielts-history-empty interview-assets-state"><h2>尚未开始面试</h2><p>该岗位已生成场景，完成一次面试后将在这里展示五维报告。</p><button className="button button--primary" onClick={() => onPractice(selected.sceneId)}>开始面试</button></div>}
        {!selected && <div className="ielts-history-empty"><BookOpenText /><h2>暂无面试记录</h2><p>创建岗位并完成面试后，报告会保存在这里。</p></div>}
      </article>
    </section>
  );
}

function InterviewAssetsTrends({ items, reportsByScene, reportsLoading }) {
  const scoredItems = items
    .filter((item) => Number.isFinite(Number(item.latestOverallScore)))
    .slice(0, 5)
    .reverse();
  const scores = scoredItems.map((item) => Math.round(Number(item.latestOverallScore)));
  const latestScore = scores.at(-1);
  const change = scores.length >= 2 ? latestScore - scores[0] : null;
  const reports = Object.values(reportsByScene).map((item) => item?.report).filter(Boolean);
  const dimensions = Object.entries(reportDimensionMeta).map(([dimension, meta]) => {
    const values = reports.flatMap((report) => Array.isArray(report.dimensions) ? report.dimensions : [])
      .filter((item) => item.dimension === dimension)
      .map((item) => Number(item.score))
      .filter(Number.isFinite);
    const score = values.length ? Math.round(values.reduce((sum, value) => sum + value, 0) / values.length) : 0;
    return { dimension, ...meta, score };
  });
  const available = dimensions.filter((item) => item.score > 0);
  const highest = available.length ? Math.max(...available.map((item) => item.score)) : 0;
  const lowest = available.length ? Math.min(...available.map((item) => item.score)) : 0;
  const average = available.length ? Math.round(available.reduce((sum, item) => sum + item.score, 0) / available.length) : 0;
  const weakness = available.find((item) => item.score === lowest);
  const totalPracticeCount = items.reduce((sum, item) => sum + Number(item.practiceCount || 0), 0);

  return (
    <section className="interview-assets-trends">
      <section className="interview-assets-trend-summary">
        <div><span>最近五次评分</span><h2>{latestScore ?? "—"}</h2><p>{change == null ? "至少完成两次面试后显示变化" : `最近 ${scores.length} 次变化 ${change >= 0 ? "+" : ""}${change} 分`}</p></div>
        {scores.length ? (
          <div className="ielts-trend-chart-wrap">
            <TrendLineChart
              values={scores}
              maxScore={100}
              lineColor="#2875c8"
              gridColor="#dbe8f7"
              fillStart="rgba(40, 117, 200, .24)"
              fillEnd="rgba(40, 117, 200, 0)"
              pointColor="#1f5798"
              ariaLabel={`最近五次面试评分：${scores.join("、")}`}
            />
            <small>较早</small><small>较近</small>
          </div>
        ) : <div className="interview-assets-trend-empty"><strong>{reportsLoading ? "正在计算趋势" : "暂无评分趋势"}</strong><p>完成面试并生成报告后显示。</p></div>}
        <div><span>训练积累</span><strong>{totalPracticeCount} 次</strong><p>覆盖 {items.length} 个岗位</p></div>
      </section>

      <section className={cx("interview-assets-dimension-trends", !available.length && "is-empty")}>
        <h2>五项能力平均分</h2>
        {available.length ? dimensions.map((item) => (
          <article key={item.dimension}><span>{item.label}<small>{item.hint}</small></span><strong>{item.score || "—"}<small>/100</small></strong><div><i style={{ width: `${item.score}%` }} /></div><em>{item.score === 0 ? "暂无数据" : highest > lowest && item.score === highest ? "相对优势" : highest > lowest && item.score === lowest ? "重点提升" : item.score >= average ? "表现稳定" : "继续提升"}</em></article>
        )) : <div className="interview-assets-trend-empty"><strong>{reportsLoading ? "正在读取五维报告" : "暂无能力评分"}</strong><p>完成一次有效面试后，这里会展示五维平均表现。</p></div>}
      </section>

      <section className="interview-assets-next-step">
        <article><span>优先能力</span><strong>{weakness?.label || "等待报告"}</strong><p>{weakness ? `当前平均 ${weakness.score} 分，下一次复练优先观察${weakness.label}。` : "生成五维报告后自动定位当前弱项。"}</p></article>
        <article><span>回答结构</span><strong>强化 STAR 叙述</strong><p>用情境、任务、行动和结果组织案例，减少无关铺垫。</p></article>
        <article><span>结果表达</span><strong>量化个人贡献</strong><p>用数字和业务影响说明成果，让回答更具体可信。</p></article>
      </section>
    </section>
  );
}

export function InterviewAssets({ route, onNavigate, onBack, onBackToAssets, onBackToIelts, onTraining, onPractice }) {
  const availableTabs = interviewAssetTabs.map((item) => item.id);
  const tab = availableTabs.includes(route?.tab) ? route.tab : "overview";
  const setTab = (nextTab) => onNavigate(nextTab === "overview" ? paths.interview.assets.root : paths.interview.assets[nextTab]);
  const tabRef = useRef(null);
  const tabButtons = useRef({});
  const [tabIndicator, setTabIndicator] = useState({ x: 0, width: 0, ready: false });
  const [items, setItems] = useState([]);
  const [reportsByScene, setReportsByScene] = useState({});
  const [reportsLoading, setReportsLoading] = useState(false);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    getInterviewAssets()
      .then((data) => {
        if (cancelled) return;
        const next = (Array.isArray(data) ? data : []).slice().sort((left, right) => {
          const leftTime = new Date(left.latestPracticedAt || left.createdAt || 0).getTime();
          const rightTime = new Date(right.latestPracticedAt || right.createdAt || 0).getTime();
          return rightTime - leftTime;
        });
        setItems(next);
        setLoadError("");
      })
      .catch((error) => {
        if (!cancelled) setLoadError(error instanceof Error ? error.message : "面试学习资产加载失败");
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => { cancelled = true; };
  }, []);

  useEffect(() => {
    const candidates = items.filter((item) => item.latestSessionId);
    if (!candidates.length) {
      setReportsByScene({});
      setReportsLoading(false);
      return undefined;
    }
    let cancelled = false;
    setReportsLoading(true);
    Promise.all(candidates.map(async (item) => {
      try {
        const payload = await getInterviewReport(item.sceneId, item.latestSessionId);
        return [item.sceneId, { status: payload?.status || item.latestReportStatus || "", report: payload?.report || null }];
      } catch {
        return [item.sceneId, { status: "ERROR", report: null }];
      }
    })).then((entries) => {
      if (!cancelled) setReportsByScene(Object.fromEntries(entries));
    }).finally(() => {
      if (!cancelled) setReportsLoading(false);
    });
    return () => { cancelled = true; };
  }, [items]);

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

  const otherAssetsMenu = (
    <div className="asset-module-menu interview-other-assets">
      <button className="asset-module-menu__trigger" type="button" aria-label="切换学习资产模块" aria-haspopup="menu"><SquaresFour weight="bold" /><span>其他资产</span><CaretDown weight="bold" /></button>
      <div className="asset-module-menu__popover" role="menu">
        <button type="button" role="menuitem" onClick={onBackToAssets}><BookOpenText /><span><strong>场景训练学习资产</strong><small>对话记录、纠错与场景复练</small></span><CaretRight /></button>
        <button type="button" role="menuitem" onClick={onBackToIelts}><span className="asset-module-ielts-mark">IELTS</span><span><strong>IELTS 学习资产</strong><small>评分、建议与今日复习</small></span><CaretRight /></button>
      </div>
    </div>
  );

  return (
    <main className={cx("page", "page--interview", "interview-assets-page", tab === "overview" && "interview-assets-page--overview", tab === "trends" && "interview-assets-page--trends")}>
      <button className="ielts-back" onClick={onBack}><ArrowLeft />返回</button>
      <PageHeader title="面试学习资产" action={<div className="ielts-assets-actions">{otherAssetsMenu}<SimpleCta className="ielts-assets-header-cta" onClick={onTraining}>返回训练中心</SimpleCta></div>} />
      <nav className="interview-assets-tabs" ref={tabRef} aria-label="面试学习资产视图">
        <span className={cx("interview-assets-tab-indicator", tabIndicator.ready && "is-ready")} style={{ width: tabIndicator.width, transform: `translateX(${tabIndicator.x}px)` }} />
        {interviewAssetTabs.map((item) => <button ref={(node) => { tabButtons.current[item.id] = node; }} key={item.id} className={tab === item.id ? "is-active" : ""} onClick={() => setTab(item.id)}>{item.label}</button>)}
      </nav>
      {loading ? <div className="ielts-history-empty"><NewtonsCradle label="正在读取面试学习资产" /></div>
        : loadError ? <div className="ielts-history-empty"><h2>学习资产加载失败</h2><p>{loadError}</p></div>
          : tab === "overview" ? <InterviewAssetsOverview items={items} reportsByScene={reportsByScene} onTab={setTab} />
            : tab === "history" ? <InterviewAssetsHistory items={items} reportsByScene={reportsByScene} reportsLoading={reportsLoading} onPractice={onPractice} />
              : <InterviewAssetsTrends items={items} reportsByScene={reportsByScene} reportsLoading={reportsLoading} />}
    </main>
  );
}

function PageHeader({ eyebrow, title, subtitle, action }) {
  return <header className="page-header"><div>{eyebrow && <p className="eyebrow">{eyebrow}</p>}<h1>{title}</h1>{subtitle && <p>{subtitle}</p>}</div>{action}</header>;
}

function ExpandingCta({ children, className, direction = "forward", disabled = false, onClick }) {
  const Arrow = direction === "back" ? ArrowLeft : ArrowRight;
  return <button type="button" className={cx("expanding-cta", direction === "back" && "expanding-cta--back", className)} disabled={disabled} onClick={onClick}><span>{children}</span><Arrow weight="bold" /></button>;
}

export function InterviewModule({ route, teacher, speed, onNavigate, onBack }) {
  const screen = route?.screen || "home";
  const navigate = (path) => onNavigate(path);
  const returnHome = () => navigate(paths.interview.root);
  if (screen === "session") {
    return (
      <InterviewSession
        sceneId={route.sceneId}
        teacher={teacher}
        speed={speed}
        onEndInterview={(sceneId, sessionId, reportStatus) => {
          if (sessionId) navigate(paths.interview.report(sceneId, sessionId));
          else returnHome();
        }}
        onExit={returnHome}
      />
    );
  }
  if (screen === "report") {
    return (
      <InterviewReport
        sceneId={route.sceneId}
        sessionId={route.sessionId}
        onHome={returnHome}
      />
    );
  }
  return <InterviewHome onNavigate={navigate} onBack={onBack} />;
}
