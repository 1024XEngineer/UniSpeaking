import {
  ArrowRight,
  Minus,
  Target,
  TrendingDown,
  TrendingUp,
} from "lucide-react";

const DIMENSION_META = {
  accuracy: { label: "准确度", color: "#287057" },
  fluency: { label: "流利度", color: "#3468a0" },
  grammar: { label: "语法", color: "#c65d43" },
  vocabulary: { label: "词汇", color: "#a67a24" },
  naturalness: { label: "自然度", color: "#57545f" },
};
const TRAINING_TYPE_META = {
  FREE_CHAT: { label: "自由对话", supported: true },
  CUSTOM_SCENE: { label: "情景口语", supported: true },
  IELTS_SCENE: { label: "雅思口语", supported: false },
};
const UNKNOWN_DIMENSION = { label: "综合能力", color: "#74746f" };
const UNKNOWN_TRAINING = { label: "推荐训练", supported: false };

function formatScore(value) {
  const score = Number(value) || 0;
  return Number.isInteger(score) ? String(score) : score.toFixed(1);
}

function ChangeIndicator({ value }) {
  const change = Number(value) || 0;
  const Icon = change > 0 ? TrendingUp : change < 0 ? TrendingDown : Minus;
  const tone = change > 0 ? "positive" : change < 0 ? "negative" : "neutral";
  const sign = change > 0 ? "+" : "";
  return (
    <span className={`weakness-card__change is-${tone}`}>
      <Icon aria-hidden="true" />较最早一次 {sign}{formatScore(change)} 分
    </span>
  );
}

export function WeaknessRecommendations({
  analysis,
  weaknesses,
  recommendations,
  onStartTraining,
}) {
  const sampleCount = Math.max(0, Number(analysis?.sampleCount) || 0);
  const minimumSampleCount = Math.max(1, Number(analysis?.minimumSampleCount) || 3);
  const reliable = Boolean(analysis?.reliable);
  const items = Array.isArray(weaknesses) ? weaknesses : [];
  const recommendationItems = Array.isArray(recommendations) ? recommendations : [];
  const remaining = Math.max(0, minimumSampleCount - sampleCount);

  return (
    <section className="weakness-insights" aria-labelledby="weakness-insights-title">
      <header>
        <div>
          <p>FOCUS NEXT</p>
          <h2 id="weakness-insights-title">薄弱项与推荐训练</h2>
        </div>
        <span>基于最近 10 次有效评分</span>
      </header>

      {!reliable ? (
        <div className="weakness-insights__empty">
          <Target aria-hidden="true" />
          <div>
            <strong>评分样本积累中</strong>
            <p>{remaining > 0 ? `还需 ${remaining} 次有效评分` : "正在准备分析结果"}</p>
          </div>
          <div className="weakness-insights__sample-progress">
            <progress
              value={Math.min(sampleCount, minimumSampleCount)}
              max={minimumSampleCount}
              aria-label={`已有 ${sampleCount} 次有效评分，至少需要 ${minimumSampleCount} 次`}
            />
            <span>{sampleCount} / {minimumSampleCount}</span>
          </div>
        </div>
      ) : (
        <div className="weakness-insights__grid">
          {items.map((weakness) => {
            const dimension = DIMENSION_META[weakness.dimension] || UNKNOWN_DIMENSION;
            const recommendation = recommendationItems.find(
              (item) => item.dimension === weakness.dimension,
            );
            const training = TRAINING_TYPE_META[recommendation?.trainingType]
              || UNKNOWN_TRAINING;
            const canStart = training.supported && typeof onStartTraining === "function";
            return (
              <article
                key={`${weakness.dimension}-${weakness.rank}`}
                className="weakness-card"
                style={{ "--weakness-color": dimension.color }}
              >
                <header>
                  <div>
                    <span>{weakness.rank === 1 ? "主要薄弱项" : "次要薄弱项"}</span>
                    <h3>{dimension.label}</h3>
                  </div>
                  <div className="weakness-card__score">
                    <strong>{formatScore(weakness.averageScore)}</strong>
                    <span>平均分</span>
                  </div>
                </header>
                <p className="weakness-card__basis">{weakness.basis}</p>
                <ChangeIndicator value={weakness.recentChange} />
                <footer>
                  <div>
                    <span>{training.label}</span>
                    <p>{recommendation?.reason || "该训练入口正在建设中"}</p>
                  </div>
                  <button
                    type="button"
                    disabled={!canStart}
                    onClick={() => canStart && onStartTraining(recommendation.trainingType)}
                  >
                    {canStart ? "开始训练" : "建设中"}<ArrowRight aria-hidden="true" />
                  </button>
                </footer>
              </article>
            );
          })}
        </div>
      )}
    </section>
  );
}
