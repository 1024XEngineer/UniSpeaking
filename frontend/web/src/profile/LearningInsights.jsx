import { useEffect, useState } from "react";
import {
  ChartPie,
  Check,
  Clock3,
  Pencil,
  RefreshCw,
  Repeat2,
  X,
} from "lucide-react";
import { getProfileInsights, updateWeeklyLearningGoals } from "../apiClient.js";
import { AbilityTrendChart } from "./AbilityTrendChart.jsx";
import { WeaknessRecommendations } from "./WeaknessRecommendations.jsx";

const DURATION_MIN = 1;
const DURATION_MAX = 1260;
const COUNT_MIN = 1;
const COUNT_MAX = 70;
const TRAINING_TYPE_META = {
  FREE_CHAT: { label: "自由对话", color: "#287057" },
  CUSTOM_SCENE: { label: "情景口语", color: "#c65d43" },
  IELTS_SCENE: { label: "雅思口语", color: "#3468a0" },
};
const UNKNOWN_TYPE_META = { label: "其他训练", color: "#74746f" };

function minutesFromSeconds(seconds) {
  const value = Number(seconds) || 0;
  return value > 0 ? Math.ceil(value / 60) : 0;
}

function formatWeekRange(startsAt, endsAt) {
  if (!startsAt || !endsAt) return "本周";
  const formatter = new Intl.DateTimeFormat("zh-CN", {
    timeZone: "Asia/Shanghai",
    month: "long",
    day: "numeric",
  });
  const inclusiveEnd = new Date(new Date(endsAt).getTime() - 1);
  return `${formatter.format(new Date(startsAt))} 至 ${formatter.format(inclusiveEnd)}`;
}

function formatDuration(seconds) {
  const minutes = Math.max(0, Number(seconds) || 0) / 60;
  if (minutes > 0 && minutes < 1) return "< 1 分钟";
  if (Number.isInteger(minutes)) return `${minutes} 分钟`;
  return `${minutes.toFixed(1)} 分钟`;
}

function GoalCard({
  icon: Icon,
  title,
  completed,
  target,
  unit,
  progress,
  remaining,
  achieved,
  tone,
}) {
  const normalizedProgress = Math.min(100, Math.max(0, Number(progress) || 0));
  return (
    <article className={`learning-goal learning-goal--${tone}`}>
      <header>
        <span className="learning-goal__icon"><Icon aria-hidden="true" /></span>
        <div><p>{title}</p><strong>{achieved ? "已达标" : "进行中"}</strong></div>
        <em className={achieved ? "is-achieved" : ""}>{achieved && <Check aria-hidden="true" />}{Math.round(normalizedProgress * 10) / 10}%</em>
      </header>
      <div className="learning-goal__value">
        <strong>{completed}</strong><span>/ {target} {unit}</span>
      </div>
      <progress value={normalizedProgress} max="100" aria-label={`${title}完成进度 ${normalizedProgress}%`} />
      <footer>{achieved ? `本周目标已完成` : `还差 ${remaining} ${unit}`}</footer>
    </article>
  );
}

function TrainingTypeDistribution({ items }) {
  const validItems = (Array.isArray(items) ? items : [])
    .filter((item) => Number(item?.durationSeconds) > 0);
  const totalSeconds = validItems.reduce(
    (total, item) => total + Number(item.durationSeconds),
    0,
  );
  let offset = 0;
  const segments = validItems.map((item) => {
    const chartPercentage = totalSeconds > 0
      ? Number(item.durationSeconds) * 100 / totalSeconds
      : 0;
    const segment = {
      ...item,
      chartPercentage,
      offset,
      meta: TRAINING_TYPE_META[item.type] || UNKNOWN_TYPE_META,
    };
    offset += chartPercentage;
    return segment;
  });

  return (
    <section className="training-distribution" aria-labelledby="training-distribution-title">
      <header>
        <div>
          <p>TRAINING MIX</p>
          <h2 id="training-distribution-title">本周训练类型占比</h2>
        </div>
        <span>按有效训练时长统计</span>
      </header>

      {segments.length === 0 ? (
        <div className="training-distribution__empty">
          <ChartPie aria-hidden="true" />
          <strong>本周暂无有效训练记录</strong>
        </div>
      ) : (
        <div className="training-distribution__content">
          <div className="training-distribution__chart">
            <svg viewBox="0 0 120 120" role="img" aria-label="本周训练类型时长占比">
              <circle className="training-distribution__track" cx="60" cy="60" r="48" pathLength="100" />
              {segments.map((segment) => (
                <circle
                  key={segment.type}
                  className="training-distribution__segment"
                  cx="60"
                  cy="60"
                  r="48"
                  pathLength="100"
                  stroke={segment.meta.color}
                  strokeDasharray={`${segment.chartPercentage} ${100 - segment.chartPercentage}`}
                  strokeDashoffset={-segment.offset}
                />
              ))}
            </svg>
            <div><strong>{minutesFromSeconds(totalSeconds)}</strong><span>有效分钟</span></div>
          </div>
          <ul className="training-distribution__legend">
            {segments.map((segment) => (
              <li key={segment.type}>
                <i style={{ backgroundColor: segment.meta.color }} aria-hidden="true" />
                <div><strong>{segment.meta.label}</strong><span>{formatDuration(segment.durationSeconds)}</span></div>
                <em>{Math.round((Number(segment.percentage) || 0) * 10) / 10}%</em>
              </li>
            ))}
          </ul>
        </div>
      )}
    </section>
  );
}

function GoalEditor({ goals, onClose, onSave }) {
  const [duration, setDuration] = useState(String(goals.durationTargetMinutes));
  const [count, setCount] = useState(String(goals.trainingCountTarget));
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    const onKeyDown = (event) => {
      if (event.key === "Escape" && !saving) onClose();
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [onClose, saving]);

  const submit = async (event) => {
    event.preventDefault();
    const durationTargetMinutes = Number(duration);
    const trainingCountTarget = Number(count);
    if (!Number.isInteger(durationTargetMinutes)
      || durationTargetMinutes < DURATION_MIN
      || durationTargetMinutes > DURATION_MAX) {
      setError(`时长目标需在 ${DURATION_MIN}～${DURATION_MAX} 分钟之间`);
      return;
    }
    if (!Number.isInteger(trainingCountTarget)
      || trainingCountTarget < COUNT_MIN
      || trainingCountTarget > COUNT_MAX) {
      setError(`训练次数需在 ${COUNT_MIN}～${COUNT_MAX} 次之间`);
      return;
    }
    setSaving(true);
    setError("");
    try {
      await onSave({ durationTargetMinutes, trainingCountTarget });
      onClose();
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "目标保存失败");
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="learning-goal-dialog" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget && !saving) onClose(); }}>
      <section role="dialog" aria-modal="true" aria-labelledby="learning-goal-dialog-title">
        <header>
          <div><p>WEEKLY GOALS</p><h2 id="learning-goal-dialog-title">调整每周目标</h2></div>
          <button type="button" aria-label="关闭" title="关闭" disabled={saving} onClick={onClose}><X /></button>
        </header>
        <form onSubmit={submit}>
          <label>
            <span>口语时长</span>
            <div><input type="number" inputMode="numeric" min={DURATION_MIN} max={DURATION_MAX} step="1" required disabled={saving} value={duration} onChange={(event) => setDuration(event.target.value)} /><small>分钟 / 周</small></div>
          </label>
          <label>
            <span>训练次数</span>
            <div><input type="number" inputMode="numeric" min={COUNT_MIN} max={COUNT_MAX} step="1" required disabled={saving} value={count} onChange={(event) => setCount(event.target.value)} /><small>次 / 周</small></div>
          </label>
          {error && <p className="learning-goal-dialog__error" role="alert">{error}</p>}
          <footer>
            <button type="button" disabled={saving} onClick={onClose}>取消</button>
            <button type="submit" className="is-primary" disabled={saving}>{saving ? "保存中" : "保存目标"}</button>
          </footer>
        </form>
      </section>
    </div>
  );
}

export function LearningInsights({ onStartTraining }) {
  const [insights, setInsights] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [editorOpen, setEditorOpen] = useState(false);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError("");
    getProfileInsights()
      .then((response) => {
        if (!cancelled) setInsights(response);
      })
      .catch((requestError) => {
        if (!cancelled) setError(requestError instanceof Error ? requestError.message : "学习目标加载失败");
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => { cancelled = true; };
  }, [reloadKey]);

  const saveGoals = async (goals) => {
    const response = await updateWeeklyLearningGoals(goals);
    setInsights(response);
  };

  const goals = insights?.weeklyGoals;
  const completedMinutes = minutesFromSeconds(goals?.completedDurationSeconds);
  const remainingMinutes = minutesFromSeconds(goals?.remainingDurationSeconds);

  return (
    <div className="learning-insights-page">
      <header className="learning-insights-header">
        <div><p>LEARNING INSIGHTS</p><h1>学习目标与洞察</h1><span>{formatWeekRange(goals?.weekStartsAt, goals?.weekEndsAt)}</span></div>
        {goals && <button type="button" onClick={() => setEditorOpen(true)}><Pencil aria-hidden="true" />调整目标</button>}
      </header>

      {loading && (
        <section className="learning-goals-grid learning-goals-grid--loading" aria-label="正在加载学习目标" aria-busy="true">
          <i /><i />
        </section>
      )}

      {!loading && error && (
        <section className="learning-insights-error" role="alert">
          <div><strong>暂时无法加载学习目标</strong><p>{error}</p></div>
          <button type="button" onClick={() => setReloadKey((current) => current + 1)}><RefreshCw aria-hidden="true" />重新加载</button>
        </section>
      )}

      {!loading && !error && goals && (
        <>
          <section className="learning-goals-grid" aria-label="本周学习目标">
            <GoalCard
              icon={Clock3}
              title="口语时长"
              completed={completedMinutes}
              target={goals.durationTargetMinutes}
              unit="分钟"
              progress={goals.durationProgress}
              remaining={remainingMinutes}
              achieved={goals.durationAchieved}
              tone="duration"
            />
            <GoalCard
              icon={Repeat2}
              title="训练次数"
              completed={goals.completedTrainingCount}
              target={goals.trainingCountTarget}
              unit="次"
              progress={goals.countProgress}
              remaining={goals.remainingTrainingCount}
              achieved={goals.countAchieved}
              tone="count"
            />
          </section>
          <TrainingTypeDistribution items={insights.trainingTypeDistribution} />
          <AbilityTrendChart items={insights.abilityTrends} />
          <WeaknessRecommendations
            analysis={insights.weaknessAnalysis}
            weaknesses={insights.weaknesses}
            recommendations={insights.recommendations}
            onStartTraining={onStartTraining}
          />
        </>
      )}

      {editorOpen && goals && <GoalEditor goals={goals} onClose={() => setEditorOpen(false)} onSave={saveGoals} />}
    </div>
  );
}
