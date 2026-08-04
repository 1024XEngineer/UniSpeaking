import { useState } from 'react';
import { Modal, Pressable, StyleSheet, Text, View } from 'react-native';
import { ArrowLeftIcon } from 'phosphor-react-native/src/icons/ArrowLeft';
import { ArrowRightIcon } from 'phosphor-react-native/src/icons/ArrowRight';
import { BookOpenTextIcon } from 'phosphor-react-native/src/icons/BookOpenText';
import { BriefcaseIcon } from 'phosphor-react-native/src/icons/Briefcase';
import { CaretDownIcon } from 'phosphor-react-native/src/icons/CaretDown';
import { PlayIcon } from 'phosphor-react-native/src/icons/Play';
import { SquaresFourIcon } from 'phosphor-react-native/src/icons/SquaresFour';

import { AppButton, AppScreen, Card, Metric, PageHeader, ProgressBar, SectionTitle } from '@/components/ui';
import type { InterviewLearningRecord } from '@/data/learningAssets';
import { useAppModel } from '@/model/AppModel';
import { colors } from '@/theme/tokens';

export type SpecialtyAssetKind = 'ielts' | 'interview';
export type SpecialtyAssetTab = 'overview' | 'history' | 'trends';

const scoreLabels = {
  ielts: ['流利与连贯', '词汇资源', '语法范围', '发音'],
  interview: ['内容结构', '表达清晰', '语言准确', '沟通自然'],
} as const;

function ModuleSwitcher({ kind, onScenes, onIelts, onInterview }: { kind: SpecialtyAssetKind; onScenes: () => void; onIelts: () => void; onInterview: () => void }) {
  const [open, setOpen] = useState(false);
  const options = [
    { id: 'scenes', title: '场景训练学习资产', note: '对话记录、纠错与场景复练', icon: <BookOpenTextIcon color={colors.ink} size={20} weight="fill" />, onPress: onScenes },
    ...(kind !== 'ielts' ? [{ id: 'ielts', title: 'IELTS 学习资产', note: '专项训练、模考与能力趋势', icon: <Text style={styles.ieltsMark}>IELTS</Text>, onPress: onIelts }] : []),
    ...(kind !== 'interview' ? [{ id: 'interview', title: '英文面试学习资产', note: '历史报告与口语复盘', icon: <BriefcaseIcon color={colors.ink} size={20} weight="fill" />, onPress: onInterview }] : []),
  ];
  return (
    <>
      <Pressable onPress={() => setOpen(true)} style={styles.switcherTrigger}>
        <SquaresFourIcon color={colors.ink} size={17} weight="bold" /><Text style={styles.switcherText}>其他资产</Text><CaretDownIcon color={colors.ink} size={14} weight="bold" />
      </Pressable>
      <Modal transparent visible={open} animationType="fade" onRequestClose={() => setOpen(false)}>
        <Pressable style={styles.menuBackdrop} onPress={() => setOpen(false)}>
          <View style={styles.menuCard}>
            {options.map((item) => (
              <Pressable key={item.id} onPress={() => { setOpen(false); item.onPress(); }} style={styles.menuRow}>
                <View style={styles.menuIcon}>{item.icon}</View>
                <View style={styles.flex}><Text style={styles.menuTitle}>{item.title}</Text><Text style={styles.menuNote}>{item.note}</Text></View>
                <ArrowRightIcon color={colors.subtle} size={17} weight="bold" />
              </Pressable>
            ))}
          </View>
        </Pressable>
      </Modal>
    </>
  );
}

function AssetTabs({ tab, onChange }: { tab: SpecialtyAssetTab; onChange: (tab: SpecialtyAssetTab) => void }) {
  return (
    <View style={styles.tabs}>
      {(['overview', 'history', 'trends'] as const).map((item) => (
        <Pressable key={item} onPress={() => onChange(item)} style={[styles.tab, tab === item && styles.tabActive]}>
          <Text style={[styles.tabText, tab === item && styles.tabTextActive]}>{item === 'overview' ? '概览' : item === 'history' ? '训练记录' : '能力趋势'}</Text>
        </Pressable>
      ))}
    </View>
  );
}

function IeltsOverview() {
  const { ieltsRecords } = useAppModel();
  return (
    <View style={styles.sectionStack}>
      <Card style={styles.heroCard}>
        <Text style={styles.cardLabel}>最近一次完整模考</Text>
        <Text style={styles.heroScore}>6.5</Text>
        <Text style={styles.heroCopy}>合理波动范围 6.0–6.5 · AI 训练评估，并非官方考试成绩</Text>
        <View style={styles.targetRow}><Text style={styles.targetLabel}>目标分数</Text><Text style={styles.targetValue}>7.0</Text><Text style={styles.targetNote}>还差约 0.5 分</Text></View>
      </Card>
      <Card>
        <Text style={styles.cardLabel}>近七天训练</Text>
        <View style={styles.metrics}><Metric label="训练时长" value="96" suffix="分钟" /><Metric label="完成训练" value="6" suffix="次" /><Metric label="专项覆盖" value="3" /></View>
      </Card>
      <SectionTitle title="最近训练" />
      <Card style={styles.listCard}>{ieltsRecords.slice(0, 3).map((item) => <AssetListRow key={item.id} title={item.title} subtitle={`${item.type} · ${item.date} · ${item.duration}`} meta={item.result} />)}</Card>
    </View>
  );
}

function InterviewOverview() {
  const { interviewRecords } = useAppModel();
  const latest = interviewRecords[0];
  return (
    <View style={styles.sectionStack}>
      <Card style={styles.heroCard}>
        <Text style={styles.cardLabel}>最近一次完整面试</Text>
        <Text style={styles.heroScore}>{latest?.score ?? '—'}</Text>
        <Text style={styles.heroCopy}>{latest?.role} · {latest?.company}</Text>
        <View style={styles.targetRow}><Text style={styles.targetLabel}>优先提升</Text><Text style={styles.targetStrong}>回答深度</Text></View>
      </Card>
      <Card>
        <Text style={styles.cardLabel}>近七天训练</Text>
        <View style={styles.metrics}><Metric label="模拟次数" value="3" /><Metric label="训练时长" value="42" suffix="分钟" /><Metric label="岗位覆盖" value="2" /></View>
      </Card>
      <SectionTitle title="最近面试" />
      <Card style={styles.listCard}>{interviewRecords.map((item) => <AssetListRow key={item.id} title={item.role} subtitle={`${item.company} · ${item.date}`} meta={item.score === null ? '部分结果' : `${item.score} 分`} />)}</Card>
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

function IeltsHistory() {
  const { ieltsRecords } = useAppModel();
  const [selectedId, setSelectedId] = useState(ieltsRecords[0]?.id);
  const selected = ieltsRecords.find((item) => item.id === selectedId) ?? ieltsRecords[0];
  return (
    <View style={styles.sectionStack}>
      <SectionTitle title="训练记录" action={<Text style={styles.count}>{ieltsRecords.length} 条</Text>} />
      <Card style={styles.listCard}>{ieltsRecords.map((item) => <AssetListRow key={item.id} title={item.title} subtitle={`${item.date} · ${item.type} · ${item.duration}`} meta={item.result} onPress={() => setSelectedId(item.id)} />)}</Card>
      {selected ? <Card style={styles.reportCard}>
        <View style={styles.reportHeader}><View style={styles.flex}><Text style={styles.cardLabel}>总体报告</Text><Text style={styles.reportTitle}>{selected.title}</Text><Text style={styles.reportCopy}>本次表达整体清楚，优先改善观点之间的过渡，并保持稳定、完整的展开。</Text></View><Pressable style={styles.playButton}><PlayIcon color={colors.ink} size={18} weight="fill" /></Pressable></View>
        {scoreLabels.ielts.map((label, index) => <ScoreRow key={label} label={label} value={selected.scores[index]} />)}
        <AppButton title="快速复练" icon="arrow-right" />
      </Card> : null}
    </View>
  );
}

function InterviewHistory({ onOpenRecord }: { onOpenRecord: (id: string) => void }) {
  const { interviewRecords } = useAppModel();
  return (
    <View style={styles.sectionStack}>
      <SectionTitle title="面试记录" action={<Text style={styles.count}>{interviewRecords.length} 条</Text>} />
      <Card style={styles.listCard}>{interviewRecords.map((item) => <AssetListRow key={item.id} title={item.role} subtitle={`${item.company} · ${item.date} · ${item.duration}`} meta={item.score === null ? '部分结果' : `${item.score} 分`} onPress={() => onOpenRecord(item.id)} />)}</Card>
    </View>
  );
}

function ScoreRow({ label, value }: { label: string; value: number }) {
  return <View style={styles.scoreRow}><Text style={styles.scoreLabel}>{label}</Text><View style={styles.scoreProgress}><ProgressBar value={value} /></View><Text style={styles.scoreValue}>{value}</Text></View>;
}

function Trends({ kind }: { kind: SpecialtyAssetKind }) {
  const labels = scoreLabels[kind];
  const values = kind === 'ielts' ? [68, 70, 64, 71] : [82, 86, 76, 81];
  return (
    <View style={styles.sectionStack}>
      <Card style={styles.trendHero}>
        <Text style={styles.cardLabel}>{kind === 'ielts' ? '预估分数趋势' : '面试表现趋势'}</Text>
        <Text style={styles.trendValue}>{kind === 'ielts' ? '6.0 → 6.5' : '76 → 82'}</Text>
        <Text style={styles.heroCopy}>最近三次训练保持上升，重点能力正在形成稳定改善。</Text>
      </Card>
      <Card style={styles.reportCard}><Text style={styles.reportTitle}>能力平均表现</Text>{labels.map((label, index) => <ScoreRow key={label} label={label} value={values[index]} />)}</Card>
      <Card><Text style={styles.reportTitle}>下一阶段建议</Text><Text style={styles.reportCopy}>{kind === 'ielts' ? '优先练习观点展开与段落衔接，让 Part 2 的长回答更加稳定。' : '使用 STAR 结构组织案例，并用具体数字说明个人贡献与业务影响。'}</Text></Card>
    </View>
  );
}

export function SpecialtyAssetsScreen({ kind, tab, onTabChange, onScenes, onIelts, onInterview, onOpenRecord }: { kind: SpecialtyAssetKind; tab: SpecialtyAssetTab; onTabChange: (tab: SpecialtyAssetTab) => void; onScenes: () => void; onIelts: () => void; onInterview: () => void; onOpenRecord?: (id: string) => void }) {
  const title = kind === 'ielts' ? 'IELTS 学习资产' : '英文面试学习资产';
  const subtitle = kind === 'ielts' ? '集中查看每次训练记录、总体报告与原始录音。' : '集中查看每次面试记录、总体报告与原始录音。';
  return (
    <AppScreen>
      <PageHeader title={title} subtitle={subtitle} action={<ModuleSwitcher kind={kind} onScenes={onScenes} onIelts={onIelts} onInterview={onInterview} />} />
      <AssetTabs tab={tab} onChange={onTabChange} />
      {tab === 'overview' ? (kind === 'ielts' ? <IeltsOverview /> : <InterviewOverview />) : null}
      {tab === 'history' ? (kind === 'ielts' ? <IeltsHistory /> : <InterviewHistory onOpenRecord={onOpenRecord ?? (() => undefined)} />) : null}
      {tab === 'trends' ? <Trends kind={kind} /> : null}
    </AppScreen>
  );
}

export function InterviewAssetReport({ record, onBack }: { record: InterviewLearningRecord; onBack: () => void }) {
  return (
    <AppScreen>
      <View style={styles.detailBar}><Pressable accessibilityRole="button" accessibilityLabel="返回" onPress={onBack} style={styles.roundButton}><ArrowLeftIcon color={colors.ink} size={20} weight="bold" /></Pressable></View>
      <PageHeader eyebrow="INTERVIEW REPORT" title={record.role} subtitle={`${record.company} · ${record.date} · ${record.duration}`} />
      <Card style={styles.heroCard}><Text style={styles.cardLabel}>综合表现</Text><Text style={styles.heroScore}>{record.score ?? '—'}</Text><Text style={styles.heroCopy}>{record.summary}</Text></Card>
      <Card style={styles.reportCard}><Text style={styles.reportTitle}>四项能力评分</Text>{scoreLabels.interview.map((label, index) => <ScoreRow key={label} label={label} value={record.scores[index]} />)}</Card>
      <Card><Text style={styles.reportTitle}>下一次重点</Text><Text style={styles.reportCopy}>让案例结果更具体，并在回答结尾明确总结你的个人贡献。</Text></Card>
      <AppButton title="快速复练" icon="arrow-right" />
    </AppScreen>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  switcherTrigger: { minHeight: 42, paddingHorizontal: 14, flexDirection: 'row', alignItems: 'center', gap: 7, borderWidth: 1, borderColor: colors.line, borderRadius: 22, backgroundColor: colors.white },
  switcherText: { color: colors.ink, fontSize: 12, fontWeight: '500' },
  menuBackdrop: { flex: 1, paddingTop: 88, paddingRight: 22, alignItems: 'flex-end', backgroundColor: 'rgba(21,21,20,0.08)' },
  menuCard: { width: 302, padding: 10, borderWidth: 1, borderColor: colors.line, borderRadius: 18, backgroundColor: colors.white, shadowColor: colors.ink, shadowOffset: { width: 0, height: 12 }, shadowOpacity: 0.16, shadowRadius: 28, elevation: 12, boxShadow: '0px 12px 30px rgba(21,21,20,0.16)' },
  menuRow: { minHeight: 70, padding: 10, flexDirection: 'row', alignItems: 'center', gap: 11, borderRadius: 13 },
  menuIcon: { width: 42, height: 42, alignItems: 'center', justifyContent: 'center', borderRadius: 12, backgroundColor: colors.soft },
  ieltsMark: { color: colors.ink, fontSize: 9, fontWeight: '600' },
  menuTitle: { color: colors.ink, fontSize: 14, fontWeight: '500' },
  menuNote: { marginTop: 4, color: colors.muted, fontSize: 11, fontWeight: '300' },
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
  targetRow: { marginTop: 5, paddingTop: 15, flexDirection: 'row', alignItems: 'center', gap: 10, borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: colors.line },
  targetLabel: { color: colors.muted, fontSize: 12, fontWeight: '300' },
  targetValue: { color: colors.ink, fontSize: 23, fontWeight: '600' },
  targetStrong: { color: colors.ink, fontSize: 16, fontWeight: '500' },
  targetNote: { color: colors.subtle, fontSize: 11, fontWeight: '300' },
  metrics: { marginTop: 14, flexDirection: 'row', gap: 8 },
  listCard: { paddingHorizontal: 16, paddingVertical: 2 },
  listRow: { minHeight: 82, paddingVertical: 14, flexDirection: 'row', alignItems: 'center', gap: 10, borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: colors.line },
  listTitle: { color: colors.ink, fontSize: 15, fontWeight: '500' },
  listSubtitle: { marginTop: 5, color: colors.muted, fontSize: 11, fontWeight: '300' },
  listMeta: { color: colors.subtle, fontSize: 11, fontWeight: '300' },
  count: { color: colors.muted, fontSize: 12, fontWeight: '300' },
  reportCard: { gap: 13 },
  reportHeader: { flexDirection: 'row', alignItems: 'flex-start', gap: 12 },
  reportTitle: { color: colors.ink, fontSize: 19, lineHeight: 25, fontWeight: '500' },
  reportCopy: { marginTop: 7, color: colors.muted, fontSize: 13, lineHeight: 21, fontWeight: '300' },
  playButton: { width: 42, height: 42, alignItems: 'center', justifyContent: 'center', borderWidth: 1, borderColor: colors.line, borderRadius: 21 },
  scoreRow: { minHeight: 38, flexDirection: 'row', alignItems: 'center', gap: 10 },
  scoreLabel: { width: 68, color: colors.muted, fontSize: 11, fontWeight: '300' },
  scoreProgress: { flex: 1 },
  scoreValue: { width: 28, color: colors.ink, fontSize: 13, textAlign: 'right', fontWeight: '500' },
  trendHero: { gap: 8 },
  trendValue: { color: colors.ink, fontSize: 31, fontWeight: '600', letterSpacing: -1 },
  detailBar: { minHeight: 48, flexDirection: 'row' },
  roundButton: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center', borderWidth: 1, borderColor: colors.line, borderRadius: 22, backgroundColor: colors.white },
});
