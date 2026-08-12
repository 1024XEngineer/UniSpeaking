import { useEffect, useState } from 'react';
import { Animated, Easing, Pressable, StyleSheet, Text, View } from 'react-native';
import { ArrowLeftIcon } from 'phosphor-react-native/src/icons/ArrowLeft';
import { ArrowRightIcon } from 'phosphor-react-native/src/icons/ArrowRight';
import { PauseIcon } from 'phosphor-react-native/src/icons/Pause';
import { PlayIcon } from 'phosphor-react-native/src/icons/Play';
import Svg, { Circle, Line, Path, Text as SvgText } from 'react-native-svg';

import { LearningAssetsHeader } from '@/components/LearningAssetsHeader';
import { AppButton, AppScreen, Card, PageHeader, ProgressBar, SectionTitle } from '@/components/ui';
import type { IeltsLearningRecord, InterviewLearningRecord } from '@/data/learningAssets';
import { useIeltsFlowController } from '@/features/ielts/useIeltsFlowController';
import { useRecordingPlayback } from '@/features/ielts/useRecordingPlayback';
import { useAppModel } from '@/model/AppModel';
import { rememberSpecialty } from '@/navigation/specialtyMemory';
import { colors } from '@/theme/tokens';

export type SpecialtyAssetKind = 'ielts' | 'interview';
export type SpecialtyAssetTab = 'overview' | 'history' | 'trends';
const PAGE_SIZE = 8;

const assetPalettes = {
  ielts: {
    canvas: '#FCFAFF',
    paper: '#FFFFFF',
    soft: '#F3EEFF',
    border: '#E6DBFF',
    accent: '#8060E8',
    text: '#171323',
    muted: '#847D92',
  },
  interview: {
    canvas: '#DCEBFA',
    paper: '#F7FBFF',
    soft: '#EAF4FF',
    border: '#B9D3EC',
    accent: '#2875C8',
    text: '#123255',
    muted: '#5D7896',
  },
} as const;

type AssetPalette = (typeof assetPalettes)[SpecialtyAssetKind];

const scoreLabels = {
  ielts: ['流利与连贯', '词汇资源', '语法范围', '发音'],
  interview: ['内容结构', '表达清晰', '语言准确', '沟通自然'],
} as const;

function AssetTabs({ palette, tab, onChange }: { palette: AssetPalette; tab: SpecialtyAssetTab; onChange: (tab: SpecialtyAssetTab) => void }) {
  return (
    <View style={[styles.tabs, { backgroundColor: palette.soft }]}>
      {(['overview', 'history', 'trends'] as const).map((item) => (
        <Pressable key={item} onPress={() => onChange(item)} style={[styles.tab, tab === item && styles.tabActive, tab === item && { backgroundColor: palette.accent }]}>
          <Text style={[styles.tabText, { color: palette.muted }, tab === item && styles.tabTextActive]}>{item === 'overview' ? '概览' : item === 'history' ? '训练记录' : '能力趋势'}</Text>
        </Pressable>
      ))}
    </View>
  );
}

function themedCard(palette: AssetPalette) {
  return { borderColor: palette.border, backgroundColor: palette.paper, shadowColor: palette.accent };
}

function buildIeltsWeeklyTraining(records: readonly IeltsLearningRecord[]) {
  const now = new Date();
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const values = Array.from({ length: 7 }, () => 0);
  const activeParts = new Set<string>();
  let completed = 0;
  for (const record of records) {
    if (!record.startedAt) continue;
    const startedAt = new Date(record.startedAt);
    if (Number.isNaN(startedAt.getTime())) continue;
    const day = new Date(startedAt.getFullYear(), startedAt.getMonth(), startedAt.getDate());
    const daysAgo = Math.round((today.getTime() - day.getTime()) / 86_400_000);
    if (daysAgo < 0 || daysAgo > 6) continue;
    const endedAt = record.endedAt ? new Date(record.endedAt) : null;
    const duration = endedAt && !Number.isNaN(endedAt.getTime())
      ? Math.max(1, Math.round((endedAt.getTime() - startedAt.getTime()) / 60_000))
      : 0;
    values[6 - daysAgo] += duration;
    completed += 1;
    if (record.part) activeParts.add(record.part);
    for (const evaluation of record.partEvaluations ?? []) activeParts.add(evaluation.part);
  }
  const total = values.reduce((sum, value) => sum + value, 0);
  const activeDays = values.filter((value) => value > 0).length;
  return { values, total, completed, activeDays, dailyAverage: activeDays ? Math.round(total / activeDays) : 0, coverage: activeParts.size };
}

function WeeklyTrainingChart({ kind, palette, records = [] }: { kind: SpecialtyAssetKind; palette: AssetPalette; records?: readonly IeltsLearningRecord[] }) {
  const chart = kind === 'ielts'
    ? buildIeltsWeeklyTraining(records)
    : { values: [6, 7, 0, 12, 5, 8, 4], total: 42, completed: 3, activeDays: 6, dailyAverage: 7, coverage: 2 };
  const maxValue = Math.max(...chart.values, 1);
  const dayLabels = ['周四', '周五', '周六', '周日', '周一', '周二', '今天'];
  return (
    <Card style={[styles.weeklyChartCard, themedCard(palette)]}>
      <View style={styles.weeklyChartSummary}>
        <Text style={[styles.cardLabel, { color: palette.muted }]}>近七天训练时长</Text>
        <View style={styles.weeklyTotalRow}>
          <Text style={[styles.weeklyTotal, { color: palette.accent }]}>{chart.total}</Text>
          <Text style={[styles.weeklyTotalSuffix, { color: palette.muted }]}>分钟</Text>
        </View>
        <Text style={[styles.weeklyCopy, { color: palette.muted }]}>共完成 {chart.completed} 次训练</Text>
        <View style={styles.weeklyStats}>
          <View style={styles.weeklyStat}><Text style={[styles.weeklyStatValue, { color: palette.text }]}>{chart.activeDays}</Text><Text style={[styles.weeklyStatLabel, { color: palette.muted }]}>活跃天数</Text></View>
          <View style={styles.weeklyStat}><Text style={[styles.weeklyStatValue, { color: palette.text }]}>{chart.dailyAverage}</Text><Text style={[styles.weeklyStatLabel, { color: palette.muted }]}>日均分钟</Text></View>
          <View style={styles.weeklyStat}><Text style={[styles.weeklyStatValue, { color: palette.text }]}>{chart.coverage}</Text><Text style={[styles.weeklyStatLabel, { color: palette.muted }]}>专项覆盖</Text></View>
        </View>
      </View>
      <View style={styles.weeklyBars}>
        {chart.values.map((value, index) => (
          <View key={dayLabels[index]} style={styles.weeklyBarColumn}>
            <View style={styles.weeklyBarTrack}>
              <View style={[styles.weeklyBar, { height: Math.max((value / maxValue) * 100, 6), backgroundColor: palette.accent, opacity: value === 0 ? 0.2 : 0.82 }]} />
            </View>
            <Text style={[styles.weeklyBarValue, { color: palette.muted }]}>{value}</Text>
            <Text style={[styles.weeklyBarLabel, { color: palette.muted }]}>{dayLabels[index]}</Text>
          </View>
        ))}
      </View>
    </Card>
  );
}

function IeltsOverview({ palette, onOpenRecord }: { palette: AssetPalette; onOpenRecord: (id: string) => void }) {
  const ielts = useIeltsFlowController();
  const refreshHistory = ielts.refreshHistory;

  useEffect(() => {
    void refreshHistory();
  }, [refreshHistory]);

  const records = ielts.historyRecords;
  const latest = records.find((record) => record.mode === 'MOCK_TEST');
  const targetScore = ielts.settings?.targetScore ?? 7.0;
  const latestBand = latest?.estimatedBand ?? ielts.settings?.latestEstimatedScore;

  return (
    <View style={styles.sectionStack}>
      <Card style={[styles.heroCard, themedCard(palette)]}>
        <Text style={[styles.cardLabel, { color: palette.muted }]}>最近一次完整模考</Text>
        <Text style={[styles.heroScore, { color: palette.accent }]}>{latestBand != null ? latestBand.toFixed(1) : '—'}</Text>
        <Text style={[styles.heroCopy, { color: palette.muted }]}>合理波动范围以 AI 训练评估为准，并非官方考试成绩</Text>
        <View style={[styles.targetRow, { borderTopColor: palette.border }]}><Text style={[styles.targetLabel, { color: palette.muted }]}>目标分数</Text><Text style={[styles.targetValue, { color: palette.accent }]}>{targetScore}</Text><Text style={[styles.targetNote, { color: palette.muted }]}>{latestBand != null ? `当前预估 ${latestBand.toFixed(1)}` : '暂无评估'}</Text></View>
      </Card>
      <WeeklyTrainingChart kind="ielts" palette={palette} records={records} />
      <SectionTitle title="最近训练" />
      <Card style={[styles.listCard, themedCard(palette)]}>{records.slice(0, 3).map((item) => <AssetListRow key={item.id} title={item.title} subtitle={`${item.type} · ${item.date} · ${item.duration}`} meta={item.result} onPress={() => onOpenRecord(item.id)} />)}</Card>
    </View>
  );
}

function InterviewOverview({ palette, onOpenRecord }: { palette: AssetPalette; onOpenRecord: (id: string) => void }) {
  const { interviewRecords } = useAppModel();
  const latest = interviewRecords[0];
  return (
    <View style={styles.sectionStack}>
      <Card style={[styles.heroCard, themedCard(palette)]}>
        <Text style={[styles.cardLabel, { color: palette.muted }]}>最近一次完整面试</Text>
        <Text style={[styles.heroScore, { color: palette.accent }]}>{latest?.score ?? '—'}</Text>
        <Text style={[styles.heroCopy, { color: palette.muted }]}>{latest?.role} · {latest?.company}</Text>
        <View style={[styles.targetRow, { borderTopColor: palette.border }]}><Text style={[styles.targetLabel, { color: palette.muted }]}>优先提升</Text><Text style={[styles.targetStrong, { color: palette.text }]}>回答深度</Text></View>
      </Card>
      <WeeklyTrainingChart kind="interview" palette={palette} />
      <SectionTitle title="最近面试" />
      <Card style={[styles.listCard, themedCard(palette)]}>{interviewRecords.slice(0, 3).map((item) => <AssetListRow key={item.id} title={item.role} subtitle={`${item.company} · ${item.date}`} meta={item.score === null ? '部分结果' : `${item.score} 分`} onPress={() => onOpenRecord(item.id)} />)}</Card>
    </View>
  );
}

function AssetListRow({ title, subtitle, meta, onPress }: { title: string; subtitle: string; meta: string; onPress?: () => void }) {
  return (
    <Pressable onPress={onPress} style={styles.listRow}>
      <View style={styles.flex}><Text style={styles.listTitle}>{title}</Text><Text style={styles.listSubtitle}>{subtitle}</Text></View>
      <Text style={styles.listMeta}>{meta}</Text><ArrowRightIcon color={colors.subtle} size={18} weight="bold" />
    </Pressable>
  );
}

function RecordPagination({ page, pageCount, palette, onPageChange }: { page: number; pageCount: number; palette: AssetPalette; onPageChange: (page: number) => void }) {
  if (pageCount <= 1) return null;
  return (
    <View style={styles.pagination}>
      <Pressable accessibilityRole="button" accessibilityLabel="上一页" disabled={page === 0} onPress={() => onPageChange(Math.max(0, page - 1))} style={[styles.paginationButton, { borderColor: palette.border, backgroundColor: palette.paper }, page === 0 && styles.paginationButtonDisabled]}>
        <ArrowLeftIcon color={page === 0 ? palette.border : palette.text} size={18} weight="bold" />
      </Pressable>
      <Text style={[styles.paginationLabel, { color: palette.muted }]}>{page + 1} / {pageCount}</Text>
      <Pressable accessibilityRole="button" accessibilityLabel="下一页" disabled={page === pageCount - 1} onPress={() => onPageChange(Math.min(pageCount - 1, page + 1))} style={[styles.paginationButton, { borderColor: palette.border, backgroundColor: palette.paper }, page === pageCount - 1 && styles.paginationButtonDisabled]}>
        <ArrowRightIcon color={page === pageCount - 1 ? palette.border : palette.text} size={18} weight="bold" />
      </Pressable>
    </View>
  );
}

function IeltsHistory({ palette, onOpenRecord }: { palette: AssetPalette; onOpenRecord: (id: string) => void }) {
  const ielts = useIeltsFlowController();
  const refreshHistory = ielts.refreshHistory;
  const [page, setPage] = useState(0);

  useEffect(() => {
    void refreshHistory();
  }, [refreshHistory]);

  const records = ielts.historyRecords;
  const pageCount = Math.max(1, Math.ceil(records.length / PAGE_SIZE));
  const currentPage = Math.min(page, pageCount - 1);
  const visibleRecords = records.slice(currentPage * PAGE_SIZE, (currentPage + 1) * PAGE_SIZE);
  return (
    <View style={styles.sectionStack}>
      <SectionTitle title="训练记录" action={<Text style={[styles.count, { color: palette.muted }]}>{records.length} 条</Text>} />
      <Card style={[styles.listCard, themedCard(palette)]}>{visibleRecords.map((item) => <AssetListRow key={item.id} title={item.title} subtitle={`${item.date} · ${item.type} · ${item.duration}`} meta={item.result} onPress={() => onOpenRecord(item.id)} />)}</Card>
      <RecordPagination page={currentPage} pageCount={pageCount} palette={palette} onPageChange={setPage} />
    </View>
  );
}

function InterviewHistory({ onOpenRecord, palette }: { onOpenRecord: (id: string) => void; palette: AssetPalette }) {
  const { interviewRecords } = useAppModel();
  const [page, setPage] = useState(0);
  const pageCount = Math.max(1, Math.ceil(interviewRecords.length / PAGE_SIZE));
  const currentPage = Math.min(page, pageCount - 1);
  const visibleRecords = interviewRecords.slice(currentPage * PAGE_SIZE, (currentPage + 1) * PAGE_SIZE);
  return (
    <View style={styles.sectionStack}>
      <SectionTitle title="面试记录" action={<Text style={[styles.count, { color: palette.muted }]}>{interviewRecords.length} 条</Text>} />
      <Card style={[styles.listCard, themedCard(palette)]}>{visibleRecords.map((item) => <AssetListRow key={item.id} title={item.role} subtitle={`${item.company} · ${item.date} · ${item.duration}`} meta={item.score === null ? '部分结果' : `${item.score} 分`} onPress={() => onOpenRecord(item.id)} />)}</Card>
      <RecordPagination page={currentPage} pageCount={pageCount} palette={palette} onPageChange={setPage} />
    </View>
  );
}

function ScoreRow({ label, value }: { label: string; value: number }) {
  return <View style={styles.scoreRow}><Text style={styles.scoreLabel}>{label}</Text><View style={styles.scoreProgress}><ProgressBar value={value} /></View><Text style={styles.scoreValue}>{value}</Text></View>;
}

function IeltsTrendLineChart({ values, palette }: { values: number[]; palette: AssetPalette }) {
  const [width, setWidth] = useState(0);
  const chartWidth = Math.max(width, 270);
  const height = 154;
  const padding = { top: 14, right: 14, bottom: 31, left: 14 };
  const chartWidthInner = chartWidth - padding.left - padding.right;
  const chartHeight = height - padding.top - padding.bottom;
  const minValue = Math.min(...values);
  const maxValue = Math.max(...values);
  const min = Math.max(0, Math.floor((minValue - 0.5) * 2) / 2);
  const max = Math.min(9, Math.max(min + 1, Math.ceil((maxValue + 0.5) * 2) / 2));
  const points = values.map((value, index) => ({
    x: values.length === 1 ? chartWidth / 2 : padding.left + (chartWidthInner * index) / (values.length - 1),
    y: padding.top + ((max - value) / (max - min)) * chartHeight,
  }));
  const linePath = points.map((point, index) => `${index === 0 ? 'M' : 'L'} ${point.x} ${point.y}`).join(' ');
  const areaPath = `${linePath} L ${points[points.length - 1].x} ${padding.top + chartHeight} L ${points[0].x} ${padding.top + chartHeight} Z`;
  return (
    <View style={styles.trendChartFrame} onLayout={(event) => setWidth(event.nativeEvent.layout.width)}>
      <Svg accessibilityLabel={`最近五次模考成绩：${values.join('、')}`} width={chartWidth} height={height}>
        {[0, 0.5, 1].map((progress) => {
          const y = padding.top + chartHeight * progress;
          return <Line key={progress} x1={padding.left} x2={chartWidth - padding.right} y1={y} y2={y} stroke={palette.border} strokeWidth={1} />;
        })}
        <Path d={areaPath} fill={palette.soft} opacity={0.8} />
        <Path d={linePath} fill="none" stroke={palette.text} strokeWidth={2.5} strokeLinecap="round" strokeLinejoin="round" />
        {points.map((point, index) => <Circle key={index} cx={point.x} cy={point.y} r={4} fill={palette.paper} stroke={palette.text} strokeWidth={2.5} />)}
        {values.map((value, index) => <SvgText key={`label-${index}`} x={points[index].x} y={height - 6} fill={palette.muted} fontSize="10" fontWeight="600" textAnchor="middle">{value.toFixed(1)}</SvgText>)}
      </Svg>
    </View>
  );
}

function IeltsTrends({ palette }: { palette: AssetPalette }) {
  const ielts = useIeltsFlowController();
  const refreshHistory = ielts.refreshHistory;
  useEffect(() => {
    void refreshHistory();
  }, [refreshHistory]);

  const records = ielts.historyRecords;
  const mockValues = records
    .filter((record) => record.mode === 'MOCK_TEST' && record.estimatedBand != null)
    .slice(0, 5)
    .reverse()
    .map((record) => Number(record.estimatedBand));
  const latest = mockValues.at(-1) ?? null;
  const change = mockValues.length >= 2
    ? Number((mockValues[mockValues.length - 1] - mockValues[0]).toFixed(1))
    : null;
  const recent = records.slice(0, 10);
  const dimensions = scoreLabels.ielts.map((label, index) => {
    const scores = recent
      .map((record) => record.bandScores?.[index])
      .filter((score): score is number => score != null && Number.isFinite(Number(score)))
      .map(Number);
    const band = scores.length ? scores.reduce((sum, score) => sum + score, 0) / scores.length : null;
    return { label, percent: band == null ? 0 : Math.round((band / 9) * 100) };
  });
  const available = dimensions.map((item) => item.percent).filter((value) => value > 0);
  const highest = available.length ? Math.max(...available) : 0;
  const lowest = available.length ? Math.min(...available) : 0;
  const average = available.length ? available.reduce((sum, value) => sum + value, 0) / available.length : 0;
  const dimensionRows = dimensions.map((item) => ({
    ...item,
    status: item.percent === 0
      ? '暂无数据'
      : highest > lowest && item.percent === highest
        ? '相对优势'
        : highest > lowest && item.percent === lowest
          ? '重点提升'
          : item.percent >= average
            ? '表现稳定'
            : '继续提升',
  }));
  const evaluatedParts = new Set(
    records.flatMap((record) => [
      ...(record.part ? [record.part] : []),
      ...(record.partEvaluations ?? []).map((evaluation) => evaluation.part),
    ]),
  );
  const partAdvice = [
    { part: 'PART_1', label: 'Part 1', title: '回答长度更稳定', detail: '保持完整作答，减少过短回答。' },
    { part: 'PART_2', label: 'Part 2', title: '内容组织正在改善', detail: '加强要点展开与句间连接。' },
    { part: 'PART_3', label: 'Part 3', title: '观点深度需要加强', detail: '增加原因、影响与对比结构。' },
  ] as const;
  return (
    <View style={styles.sectionStack}>
      <Card style={[styles.trendSummaryCard, themedCard(palette)]}>
        <View style={styles.trendSummaryTop}>
          <View style={styles.trendMetric}><Text style={[styles.cardLabel, { color: palette.muted }]}>模考趋势</Text><Text style={[styles.trendValue, { color: palette.accent }]}>{latest == null ? '—' : latest.toFixed(1)}</Text><Text style={[styles.trendNote, { color: palette.muted }]}>{change == null ? '至少完成两次模考后显示趋势' : `最近 ${mockValues.length} 次变化 ${change >= 0 ? '+' : ''}${change.toFixed(1)} 分`}</Text></View>
          <View style={[styles.trendGoal, { borderLeftColor: palette.border }]}><Text style={[styles.cardLabel, { color: palette.muted }]}>目标进度</Text><Text style={[styles.trendGoalValue, { color: palette.text }]}>{ielts.settings?.targetScore == null ? '—' : Number(ielts.settings.targetScore).toFixed(1)}</Text><Text style={[styles.trendNote, { color: palette.muted }]}>已连续打卡 {ielts.settings?.currentStreakDays ?? 0} 天</Text></View>
        </View>
        {mockValues.length > 0
          ? <IeltsTrendLineChart values={mockValues} palette={palette} />
          : <View style={styles.trendEmpty}><Text style={[styles.partTitle, { color: palette.text }]}>暂无模考趋势</Text><Text style={[styles.partCopy, { color: palette.muted }]}>完成完整模考后生成折线图。</Text></View>}
      </Card>
      <View style={styles.trendSectionHeading}><Text style={[styles.trendSectionTitle, { color: palette.text }]}>四项能力平均分 · 最近 {recent.length} 次训练</Text></View>
      <Card style={[styles.reportCard, themedCard(palette)]}>{dimensionRows.map((item) => <View key={item.label} style={styles.dimensionRow}><Text style={[styles.dimensionLabel, { color: palette.text }]}>{item.label}</Text><Text style={[styles.dimensionValue, { color: palette.text }]}>{item.percent || '—'}{item.percent ? <Text style={[styles.dimensionSuffix, { color: palette.muted }]}>/100</Text> : null}</Text><View style={styles.dimensionProgress}><ProgressBar value={item.percent} /></View><Text style={[styles.dimensionStatus, { color: palette.text }]}>{item.status}</Text></View>)}</Card>
      <View style={styles.trendPartGrid}>
        {partAdvice.map((item) => {
          const availablePart = evaluatedParts.has(item.part);
          return <Card key={item.part} style={[styles.partFeedbackCard, themedCard(palette)]}><Text style={[styles.partLabel, { color: palette.muted }]}>{item.label}</Text><Text style={[styles.partTitle, { color: palette.text }]}>{availablePart ? item.title : '暂无专项评分'}</Text><Text style={[styles.partCopy, { color: palette.muted }]}>{availablePart ? item.detail : '完成有效训练后生成建议。'}</Text></Card>;
        })}
      </View>
    </View>
  );
}

function InterviewTrends({ kind, palette }: { kind: SpecialtyAssetKind; palette: AssetPalette }) {
  const labels = scoreLabels[kind];
  const values = kind === 'ielts' ? [68, 70, 64, 71] : [82, 86, 76, 81];
  return (
    <View style={styles.sectionStack}>
      <Card style={[styles.trendHero, themedCard(palette)]}>
        <Text style={[styles.cardLabel, { color: palette.muted }]}>{kind === 'ielts' ? '预估分数趋势' : '面试表现趋势'}</Text>
        <Text style={[styles.trendValue, { color: palette.accent }]}>{kind === 'ielts' ? '6.0 → 6.5' : '76 → 82'}</Text>
        <Text style={[styles.heroCopy, { color: palette.muted }]}>最近三次训练保持上升，重点能力正在形成稳定改善。</Text>
      </Card>
      <Card style={[styles.reportCard, themedCard(palette)]}><Text style={[styles.reportTitle, { color: palette.text }]}>能力平均表现</Text>{labels.map((label, index) => <ScoreRow key={label} label={label} value={values[index]} />)}</Card>
      <Card style={themedCard(palette)}><Text style={[styles.reportTitle, { color: palette.text }]}>下一阶段建议</Text><Text style={[styles.reportCopy, { color: palette.muted }]}>{kind === 'ielts' ? '优先练习观点展开与段落衔接，让 Part 2 的长回答更加稳定。' : '使用 STAR 结构组织案例，并用具体数字说明个人贡献与业务影响。'}</Text></Card>
    </View>
  );
}

export function SpecialtyAssetsScreen({ kind, tab, onTabChange, onScenes, onIelts, onInterview, onOpenRecord }: { kind: SpecialtyAssetKind; tab: SpecialtyAssetTab; onTabChange?: (tab: SpecialtyAssetTab) => void; onScenes: () => void; onIelts: () => void; onInterview: () => void; onOpenRecord?: (id: string) => void }) {
  const palette = assetPalettes[kind];
  const [activeTab, setActiveTab] = useState(tab);
  const [contentOpacity] = useState(() => new Animated.Value(1));
  const [contentTranslateX] = useState(() => new Animated.Value(0));
  useEffect(() => {
    void rememberSpecialty(kind);
  }, [kind]);
  const changeTab = (nextTab: SpecialtyAssetTab) => {
    if (nextTab === activeTab) return;
    const direction = ['overview', 'history', 'trends'].indexOf(nextTab) > ['overview', 'history', 'trends'].indexOf(activeTab) ? -1 : 1;
    Animated.parallel([
      Animated.timing(contentOpacity, { toValue: 0, duration: 120, easing: Easing.out(Easing.quad), useNativeDriver: true }),
      Animated.timing(contentTranslateX, { toValue: direction * 24, duration: 120, easing: Easing.out(Easing.quad), useNativeDriver: true }),
    ]).start(() => {
      setActiveTab(nextTab);
      contentTranslateX.setValue(direction * -24);
      Animated.parallel([
        Animated.timing(contentOpacity, { toValue: 1, duration: 220, easing: Easing.out(Easing.cubic), useNativeDriver: true }),
        Animated.timing(contentTranslateX, { toValue: 0, duration: 220, easing: Easing.out(Easing.cubic), useNativeDriver: true }),
      ]).start();
    });
    onTabChange?.(nextTab);
  };

  const content = activeTab === 'overview'
    ? (kind === 'ielts' ? <IeltsOverview palette={palette} onOpenRecord={onOpenRecord ?? (() => undefined)} /> : <InterviewOverview palette={palette} onOpenRecord={onOpenRecord ?? (() => undefined)} />)
    : activeTab === 'history'
      ? (kind === 'ielts' ? <IeltsHistory palette={palette} onOpenRecord={onOpenRecord ?? (() => undefined)} /> : <InterviewHistory onOpenRecord={onOpenRecord ?? (() => undefined)} palette={palette} />)
      : (kind === 'ielts' ? <IeltsTrends palette={palette} /> : <InterviewTrends kind={kind} palette={palette} />);

  return (
    <AppScreen
      contentStyle={[styles.assetsContent, { backgroundColor: palette.canvas }]}
      fixedHeader={(
        <View style={[styles.assetFixedHeader, { backgroundColor: palette.canvas, borderBottomColor: palette.border }]}>
          <LearningAssetsHeader current={kind} onScenes={onScenes} onIelts={onIelts} onInterview={onInterview} />
          <View style={styles.assetTabsDock}><AssetTabs palette={palette} tab={activeTab} onChange={changeTab} /></View>
        </View>
      )}
    >
      <Animated.View style={[styles.assetTabContent, { opacity: contentOpacity, transform: [{ translateX: contentTranslateX }] }]}>
        {content}
      </Animated.View>
    </AppScreen>
  );
}

export function IeltsAssetReport({ record, onBack }: { record: IeltsLearningRecord; onBack: () => void }) {
  const palette = assetPalettes.ielts;
  const playback = useRecordingPlayback(record.recordingUrls ?? []);
  const reportHeading = record.type === '完整模考'
    ? `完整模考 · ${record.title}`
    : `${record.type} · ${record.title}`;
  return (
    <AppScreen
      contentStyle={[styles.assetsContent, { backgroundColor: palette.canvas }]}
      fixedHeader={<PageHeader fixed onBack={onBack} title="雅思报告" style={{ backgroundColor: palette.canvas, borderBottomColor: palette.border }} />}
    >
      <View style={styles.detailHeading}>
        <Text style={[styles.reportTitle, { color: palette.text }]}>{reportHeading}</Text>
        <Text style={[styles.reportCopy, { color: palette.muted }]}>{record.date} · {record.duration}</Text>
      </View>
      <Card style={[styles.heroCard, themedCard(palette)]}>
        <Text style={[styles.reportTitle, { color: palette.text }]}>总体报告</Text>
        {record.estimatedBand != null ? <Text style={[styles.heroScore, { color: palette.accent }]}>{record.estimatedBand.toFixed(1)}</Text> : null}
        <Text style={[styles.heroCopy, { color: palette.muted }]}>{record.summary || '本次报告已生成，下面展示四项能力诊断和针对性建议。'}</Text>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel={playback.playing ? '暂停录音' : '播放原始录音'}
          disabled={!playback.canPlay}
          onPress={playback.toggle}
          style={[styles.recordingToggle, { borderColor: palette.border, opacity: playback.canPlay ? 1 : 0.45 }]}
        >
          {playback.playing ? (
            <PauseIcon color={palette.accent} size={18} weight="fill" />
          ) : (
            <PlayIcon color={palette.accent} size={18} weight="fill" />
          )}
          <Text style={[styles.recordingToggleText, { color: palette.text }]}>
            {playback.canPlay
              ? playback.playing
                ? '暂停录音'
                : '播放原始录音'
              : '暂无录音'}
          </Text>
        </Pressable>
        {playback.error ? <Text style={[styles.recordingError, { color: palette.muted }]}>{playback.error}</Text> : null}
      </Card>
      <Card style={themedCard(palette)}>
        <Text style={[styles.reportTitle, { color: palette.text }]}>表达优势</Text>
        {(record.strengths?.length ? record.strengths : ['本次报告暂无单独保存的优势说明。']).map((item, index) => (
          <Text key={`${item}-${index}`} style={[styles.reportBullet, { color: palette.muted }]}>• {item}</Text>
        ))}
      </Card>
      <Card style={[styles.reportCard, themedCard(palette)]}>
        <Text style={[styles.reportTitle, { color: palette.text }]}>四项能力评分</Text>
        {scoreLabels.ielts.map((label, index) => (
          <View key={label} style={styles.bandDetailRow}>
            <View style={styles.bandDetailHeader}>
              <Text style={[styles.dimensionLabel, { color: palette.text }]}>{label}</Text>
              <Text style={[styles.bandDetailValue, { color: palette.accent }]}>{record.bandScores?.[index] == null ? '—' : Number(record.bandScores[index]).toFixed(1)}<Text style={[styles.dimensionSuffix, { color: palette.muted }]}>/9</Text></Text>
            </View>
            {record.scoreReasons?.[index] ? <Text style={[styles.reportCopy, { color: palette.muted }]}>{record.scoreReasons[index]}</Text> : null}
          </View>
        ))}
      </Card>
      <Card style={themedCard(palette)}>
        <Text style={[styles.reportTitle, { color: palette.text }]}>优化改进</Text>
        {(record.improvements?.length ? record.improvements : ['本次报告暂无单独保存的改进建议。']).map((item, index) => (
          <Text key={`${item}-${index}`} style={[styles.reportBullet, { color: palette.muted }]}>{index + 1}. {item}</Text>
        ))}
      </Card>
      <Card style={themedCard(palette)}>
        <Text style={[styles.reportTitle, { color: palette.text }]}>推荐表达</Text>
        {(record.recommendedExpressions?.length ? record.recommendedExpressions : ['本次报告暂无推荐表达。']).map((item, index) => (
          <Text key={`${item}-${index}`} style={[styles.reportBullet, { color: palette.muted }]}>• {item}</Text>
        ))}
      </Card>
    </AppScreen>
  );
}

export function InterviewAssetReport({ record, onBack }: { record: InterviewLearningRecord; onBack: () => void }) {
  const palette = assetPalettes.interview;
  return (
    <AppScreen
      contentStyle={[styles.assetsContent, { backgroundColor: palette.canvas }]}
      fixedHeader={<PageHeader fixed onBack={onBack} title="面试报告" style={{ backgroundColor: palette.canvas, borderBottomColor: palette.border }} />}
    >
      <View style={styles.detailHeading}>
        <Text style={[styles.reportTitle, { color: palette.text }]}>{record.role}</Text>
        <Text style={[styles.reportCopy, { color: palette.muted }]}>{record.company} · {record.date} · {record.duration}</Text>
      </View>
      <Card style={[styles.heroCard, themedCard(palette)]}><Text style={[styles.cardLabel, { color: palette.muted }]}>综合表现</Text><Text style={[styles.heroScore, { color: palette.accent }]}>{record.score ?? '—'}</Text><Text style={[styles.heroCopy, { color: palette.muted }]}>{record.summary}</Text></Card>
      <Card style={[styles.reportCard, themedCard(palette)]}><Text style={[styles.reportTitle, { color: palette.text }]}>四项能力评分</Text>{scoreLabels.interview.map((label, index) => <ScoreRow key={label} label={label} value={record.scores[index]} />)}</Card>
      <Card style={themedCard(palette)}><Text style={[styles.reportTitle, { color: palette.text }]}>下一次重点</Text><Text style={[styles.reportCopy, { color: palette.muted }]}>让案例结果更具体，并在回答结尾明确总结你的个人贡献。</Text></Card>
      <AppButton title="快速复练" icon="arrow-right" />
    </AppScreen>
  );
}

const styles = StyleSheet.create({
  assetsContent: { flexGrow: 1, paddingBottom: 104 },
  assetFixedHeader: { borderBottomWidth: StyleSheet.hairlineWidth },
  assetTabsDock: { paddingHorizontal: 22, paddingVertical: 12 },
  assetTabContent: { flexGrow: 1 },
  flex: { flex: 1 },
  detailHeading: { gap: 4 },
  tabs: { padding: 4, flexDirection: 'row', borderRadius: 15, backgroundColor: colors.soft },
  tab: { minHeight: 42, flex: 1, alignItems: 'center', justifyContent: 'center', borderRadius: 12 },
  tabActive: { backgroundColor: colors.ink },
  tabText: { color: colors.muted, fontSize: 12, fontWeight: '500' },
  tabTextActive: { color: colors.white },
  sectionStack: { gap: 18 },
  heroCard: { gap: 9 },
  cardLabel: { color: colors.subtle, fontSize: 11, fontWeight: '500', letterSpacing: 1.2 },
  heroScore: { color: colors.ink, fontSize: 50, lineHeight: 57, fontWeight: '600', letterSpacing: -2 },
  heroCopy: { color: colors.muted, fontSize: 13, lineHeight: 20, fontWeight: '300' },
  reportBullet: { marginTop: 8, fontSize: 13, lineHeight: 21, fontWeight: '300' },
  bandDetailRow: { paddingVertical: 13, gap: 6, borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: colors.line },
  bandDetailHeader: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 12 },
  bandDetailValue: { fontSize: 22, lineHeight: 28, fontWeight: '600', fontVariant: ['tabular-nums'] },
  targetRow: { marginTop: 5, paddingTop: 15, flexDirection: 'row', alignItems: 'center', gap: 10, borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: colors.line },
  targetLabel: { color: colors.muted, fontSize: 12, fontWeight: '300' },
  targetValue: { color: colors.ink, fontSize: 23, fontWeight: '600' },
  targetStrong: { color: colors.ink, fontSize: 16, fontWeight: '500' },
  targetNote: { color: colors.subtle, fontSize: 11, fontWeight: '300' },
  weeklyChartCard: { minHeight: 190, flexDirection: 'row', alignItems: 'stretch', gap: 12 },
  weeklyChartSummary: { width: 108, justifyContent: 'center' },
  weeklyTotalRow: { marginTop: 3, flexDirection: 'row', alignItems: 'flex-end', gap: 4 },
  weeklyTotal: { fontSize: 38, lineHeight: 43, fontWeight: '600', fontVariant: ['tabular-nums'] },
  weeklyTotalSuffix: { marginBottom: 5, fontSize: 10, lineHeight: 16, fontWeight: '300' },
  weeklyCopy: { marginTop: 1, fontSize: 10, lineHeight: 15, fontWeight: '300' },
  weeklyStats: { marginTop: 12, flexDirection: 'row', gap: 8 },
  weeklyStat: { minWidth: 0, flex: 1 },
  weeklyStatValue: { fontSize: 17, lineHeight: 21, fontWeight: '600', fontVariant: ['tabular-nums'] },
  weeklyStatLabel: { marginTop: 1, fontSize: 8, lineHeight: 11, fontWeight: '300' },
  weeklyBars: { minWidth: 0, flex: 1, height: 150, flexDirection: 'row', alignItems: 'flex-end', justifyContent: 'space-between', gap: 3 },
  weeklyBarColumn: { minWidth: 0, flex: 1, alignItems: 'center', justifyContent: 'flex-end' },
  weeklyBarTrack: { width: '100%', height: 100, alignItems: 'center', justifyContent: 'flex-end' },
  weeklyBar: { width: '72%', minHeight: 6, borderRadius: 5 },
  weeklyBarValue: { marginTop: 4, fontSize: 8, lineHeight: 11, fontWeight: '500', fontVariant: ['tabular-nums'] },
  weeklyBarLabel: { marginTop: 1, fontSize: 7, lineHeight: 10, fontWeight: '300' },
  pagination: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 16 },
  paginationButton: { width: 40, height: 40, alignItems: 'center', justifyContent: 'center', borderWidth: 1, borderRadius: 20 },
  paginationButtonDisabled: { opacity: 0.5 },
  paginationLabel: { minWidth: 42, fontSize: 12, textAlign: 'center', fontWeight: '300' },
  listCard: { paddingHorizontal: 16, paddingVertical: 2 },
  listRow: { minHeight: 82, paddingVertical: 14, flexDirection: 'row', alignItems: 'center', gap: 10, borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: colors.line },
  listTitle: { color: colors.ink, fontSize: 15, fontWeight: '500' },
  listSubtitle: { marginTop: 5, color: colors.muted, fontSize: 11, fontWeight: '300' },
  listMeta: { color: colors.subtle, fontSize: 11, fontWeight: '300' },
  count: { color: colors.muted, fontSize: 12, fontWeight: '300' },
  reportCard: { gap: 13 },
  reportTitle: { color: colors.ink, fontSize: 19, lineHeight: 25, fontWeight: '500' },
  reportCopy: { marginTop: 7, color: colors.muted, fontSize: 13, lineHeight: 21, fontWeight: '300' },
  scoreRow: { minHeight: 38, flexDirection: 'row', alignItems: 'center', gap: 10 },
  scoreLabel: { width: 68, color: colors.muted, fontSize: 11, fontWeight: '300' },
  scoreProgress: { flex: 1 },
  scoreValue: { width: 28, color: colors.ink, fontSize: 13, textAlign: 'right', fontWeight: '500' },
  trendHero: { gap: 8 },
  trendValue: { color: colors.ink, fontSize: 31, fontWeight: '600', letterSpacing: -1 },
  trendSummaryCard: { gap: 18 },
  trendSummaryTop: { flexDirection: 'row', justifyContent: 'space-between', gap: 16 },
  trendMetric: { flex: 1 },
  trendNote: { marginTop: 4, fontSize: 11, lineHeight: 16, fontWeight: '300' },
  trendGoal: { minWidth: 102, paddingLeft: 16, borderLeftWidth: StyleSheet.hairlineWidth },
  trendGoalValue: { marginTop: 4, fontSize: 30, lineHeight: 34, fontWeight: '600' },
  trendChartFrame: { width: '100%', minHeight: 154, overflow: 'hidden' },
  trendEmpty: { minHeight: 154, alignItems: 'center', justifyContent: 'center', gap: 5 },
  trendSectionHeading: { marginTop: 2 },
  trendSectionTitle: { fontSize: 17, lineHeight: 23, fontWeight: '600' },
  dimensionRow: { minHeight: 54, flexDirection: 'row', alignItems: 'center', gap: 8, borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: colors.line },
  dimensionLabel: { width: 72, fontSize: 11, lineHeight: 16, fontWeight: '500' },
  dimensionValue: { width: 42, fontSize: 17, lineHeight: 21, fontWeight: '600', fontVariant: ['tabular-nums'] },
  dimensionSuffix: { fontSize: 9, fontWeight: '300' },
  dimensionProgress: { flex: 1 },
  dimensionStatus: { width: 44, fontSize: 9, textAlign: 'right', fontWeight: '500' },
  trendPartGrid: { gap: 12 },
  partFeedbackCard: { gap: 5 },
  partLabel: { fontSize: 11, lineHeight: 15, fontWeight: '500' },
  partTitle: { fontSize: 17, lineHeight: 23, fontWeight: '600' },
  partCopy: { fontSize: 12, lineHeight: 18, fontWeight: '300' },
  recordingToggle: {
    marginTop: 12,
    minHeight: 42,
    paddingHorizontal: 14,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    borderWidth: StyleSheet.hairlineWidth,
    borderRadius: 12,
  },
  recordingToggleText: { fontSize: 13, fontWeight: '500' },
  recordingError: { marginTop: 8, fontSize: 12, lineHeight: 18, fontWeight: '300' },
});
