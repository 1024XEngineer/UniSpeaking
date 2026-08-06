import { useState } from "react";
import { ChartLine } from "lucide-react";

const DIMENSIONS = [
  { id: "accuracy", label: "准确度", color: "#287057" },
  { id: "fluency", label: "流利度", color: "#3468a0" },
  { id: "grammar", label: "语法", color: "#c65d43" },
  { id: "vocabulary", label: "词汇", color: "#a67a24" },
  { id: "naturalness", label: "自然度", color: "#57545f" },
];
const TRAINING_TYPE_LABELS = {
  FREE_CHAT: "自由对话",
  CUSTOM_SCENE: "情景口语",
  IELTS_SCENE: "雅思口语",
  INTERVIEW_SCENE: "AI 面试",
};
const CHART = {
  width: 760,
  height: 280,
  left: 48,
  right: 24,
  top: 24,
  bottom: 46,
};
const Y_TICKS = [100, 75, 50, 25, 0];

function normalizedScore(point, dimension) {
  const score = Number(point?.scores?.[dimension]);
  return Number.isFinite(score) ? Math.min(100, Math.max(0, score)) : 0;
}

function pointX(index, count) {
  const plotWidth = CHART.width - CHART.left - CHART.right;
  return count <= 1
    ? CHART.left + plotWidth / 2
    : CHART.left + index * plotWidth / (count - 1);
}

function pointY(score) {
  const plotHeight = CHART.height - CHART.top - CHART.bottom;
  return CHART.top + (100 - score) * plotHeight / 100;
}

function dateFormatter(options) {
  return new Intl.DateTimeFormat("zh-CN", {
    timeZone: "Asia/Shanghai",
    ...options,
  });
}

const axisDateFormatter = dateFormatter({ month: "numeric", day: "numeric" });
const detailDateFormatter = dateFormatter({
  month: "long",
  day: "numeric",
  hour: "2-digit",
  minute: "2-digit",
  hour12: false,
});

function formatScore(score) {
  return Number.isInteger(score) ? String(score) : score.toFixed(1);
}

function trainingTypeLabel(type) {
  return TRAINING_TYPE_LABELS[type] || "其他训练";
}

export function AbilityTrendChart({ items }) {
  const points = Array.isArray(items) ? items : [];
  const [dimensionId, setDimensionId] = useState(DIMENSIONS[0].id);
  const [activeIndex, setActiveIndex] = useState(Math.max(0, points.length - 1));
  const dimension = DIMENSIONS.find((item) => item.id === dimensionId) || DIMENSIONS[0];
  const plotted = points.map((point, index) => {
    const score = normalizedScore(point, dimension.id);
    return {
      ...point,
      score,
      x: pointX(index, points.length),
      y: pointY(score),
    };
  });
  const safeActiveIndex = Math.min(activeIndex, Math.max(0, plotted.length - 1));
  const activePoint = plotted[safeActiveIndex];
  const linePath = plotted.map((point, index) => (
    `${index === 0 ? "M" : "L"} ${point.x} ${point.y}`
  )).join(" ");

  return (
    <section className="ability-trends" aria-labelledby="ability-trends-title">
      <header>
        <div>
          <p>ABILITY TREND</p>
          <h2 id="ability-trends-title">五维能力趋势</h2>
        </div>
        <span>{points.length > 0 ? `最近 ${points.length} 次有效评分` : "最近 10 次有效评分"}</span>
      </header>

      {points.length === 0 ? (
        <div className="ability-trends__empty">
          <ChartLine aria-hidden="true" />
          <strong>暂无可用的五维评分报告</strong>
        </div>
      ) : (
        <>
          <div className="ability-trends__dimensions" role="group" aria-label="选择能力维度">
            {DIMENSIONS.map((item) => (
              <button
                key={item.id}
                type="button"
                className={item.id === dimension.id ? "is-active" : ""}
                aria-pressed={item.id === dimension.id}
                style={{ "--dimension-color": item.color }}
                onClick={() => setDimensionId(item.id)}
              >
                <i aria-hidden="true" />{item.label}
              </button>
            ))}
          </div>

          <div className="ability-trends__chart-scroll">
            <svg
              className="ability-trends__chart"
              viewBox={`0 0 ${CHART.width} ${CHART.height}`}
              role="img"
              aria-label={`${dimension.label}最近 ${points.length} 次评分趋势`}
              style={{ "--trend-color": dimension.color }}
            >
              {Y_TICKS.map((tick) => {
                const y = pointY(tick);
                return (
                  <g key={tick} className="ability-trends__grid-line">
                    <line x1={CHART.left} x2={CHART.width - CHART.right} y1={y} y2={y} />
                    <text x={CHART.left - 13} y={y + 4} textAnchor="end">{tick}</text>
                  </g>
                );
              })}

              {plotted.length > 1 && <path className="ability-trends__line" d={linePath} />}

              {plotted.map((point, index) => (
                <g
                  key={point.sessionId}
                  className="ability-trends__point"
                  onMouseEnter={() => setActiveIndex(index)}
                >
                  <circle className="ability-trends__point-hit" cx={point.x} cy={point.y} r="14" />
                  <circle
                    cx={point.x}
                    cy={point.y}
                    r={index === safeActiveIndex ? 6 : 4.5}
                    tabIndex="0"
                    role="img"
                    aria-label={`${detailDateFormatter.format(new Date(point.completedAt))}，${trainingTypeLabel(point.trainingType)}，${dimension.label} ${formatScore(point.score)} 分`}
                    onFocus={() => setActiveIndex(index)}
                  />
                  <text className="ability-trends__axis-label" x={point.x} y={CHART.height - 15} textAnchor="middle">
                    {axisDateFormatter.format(new Date(point.completedAt))}
                  </text>
                </g>
              ))}

              {activePoint && (() => {
                const tooltipWidth = 178;
                const tooltipHeight = 54;
                const tooltipX = Math.min(
                  CHART.width - CHART.right - tooltipWidth,
                  Math.max(CHART.left, activePoint.x - tooltipWidth / 2),
                );
                const tooltipY = activePoint.y < 88
                  ? activePoint.y + 16
                  : activePoint.y - tooltipHeight - 15;
                return (
                  <g className="ability-trends__tooltip" aria-hidden="true">
                    <rect x={tooltipX} y={tooltipY} width={tooltipWidth} height={tooltipHeight} rx="6" />
                    <text x={tooltipX + 11} y={tooltipY + 20}>
                      {dimension.label} {formatScore(activePoint.score)} 分
                    </text>
                    <text x={tooltipX + 11} y={tooltipY + 39}>
                      {detailDateFormatter.format(new Date(activePoint.completedAt))} · {trainingTypeLabel(activePoint.trainingType)}
                    </text>
                  </g>
                );
              })()}
            </svg>
          </div>
          {points.length === 1 && <p className="ability-trends__sample-state">当前仅有 1 次有效评分，暂不计算变化趋势</p>}
        </>
      )}
    </section>
  );
}
