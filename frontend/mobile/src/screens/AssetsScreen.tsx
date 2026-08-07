import { useEffect, useState } from 'react';
import { Modal, Pressable, StyleSheet, Text, View } from 'react-native';
import { ArrowLeftIcon } from 'phosphor-react-native/src/icons/ArrowLeft';
import { ArrowRightIcon } from 'phosphor-react-native/src/icons/ArrowRight';
import { BookOpenTextIcon } from 'phosphor-react-native/src/icons/BookOpenText';
import { CaretDownIcon } from 'phosphor-react-native/src/icons/CaretDown';
import { ChatTextIcon } from 'phosphor-react-native/src/icons/ChatText';
import { CheckCircleIcon } from 'phosphor-react-native/src/icons/CheckCircle';
import { PlayIcon } from 'phosphor-react-native/src/icons/Play';
import { SquaresFourIcon } from 'phosphor-react-native/src/icons/SquaresFour';
import { TrashIcon } from 'phosphor-react-native/src/icons/Trash';
import { TranslateIcon } from 'phosphor-react-native/src/icons/Translate';

import { AppButton, AppScreen, Card, PageHeader, Pill, SectionTitle } from '@/components/ui';
import type { LearningExpression, SceneLearningRecord } from '@/data/learningAssets';
import { LearningAssetService } from '@/features/scenes/LearningAssetService';
import { SecureTokenStore } from '@/infrastructure/auth/SecureTokenStore';
import { getRuntimeConfig } from '@/infrastructure/config/runtimeConfig';
import { ApiClient } from '@/infrastructure/http/ApiClient';
import { colors } from '@/theme/tokens';

type LearningAssetServicePort = Pick<
  LearningAssetService,
  'listRecords' | 'getRecord'
>;

function createLearningAssetService(): LearningAssetService {
  return new LearningAssetService(
    new ApiClient({
      baseUrl: getRuntimeConfig().backendUrl,
      tokenStore: new SecureTokenStore(),
    }),
  );
}

function AssetModuleMenu({ onIelts }: { onIelts: () => void }) {
  const [open, setOpen] = useState(false);
  return (
    <>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel="切换学习资产模块"
        onPress={() => setOpen(true)}
        style={({ pressed }) => [styles.moduleTrigger, pressed && styles.pressed]}
      >
        <SquaresFourIcon color={colors.ink} size={17} weight="bold" />
        <Text style={styles.moduleTriggerText}>其他资产</Text>
        <CaretDownIcon color={colors.ink} size={14} weight="bold" />
      </Pressable>
      <Modal transparent visible={open} animationType="fade" onRequestClose={() => setOpen(false)}>
        <Pressable style={styles.menuBackdrop} onPress={() => setOpen(false)}>
          <View style={styles.modulePopover}>
            <Pressable onPress={onIelts} style={({ pressed }) => [styles.moduleRow, pressed && styles.pressed]}>
              <View style={styles.moduleIcon}><Text style={styles.ieltsMark}>IELTS</Text></View>
              <View style={styles.flex}>
                <Text style={styles.moduleTitle}>IELTS 学习资产</Text>
                <Text style={styles.moduleNote}>专项训练、模考与能力趋势</Text>
              </View>
              <ArrowRightIcon color={colors.subtle} size={17} weight="bold" />
            </Pressable>
          </View>
        </Pressable>
      </Modal>
    </>
  );
}

function SceneRecordRow({ record, onPress }: { record: SceneLearningRecord; onPress: () => void }) {
  return (
    <Pressable accessibilityRole="button" onPress={onPress} style={({ pressed }) => [styles.recordRow, pressed && styles.pressed]}>
      <View style={styles.recordIcon}><ChatTextIcon color={colors.ink} size={21} weight="fill" /></View>
      <View style={styles.flex}>
        <Text style={styles.recordTitle}>{record.title}</Text>
        <Text style={styles.recordMeta}>普通场景 · {record.date} · {record.status}</Text>
      </View>
      <Text style={styles.recordScore}>{record.score === null ? '待练习' : `${record.score} 分`}</Text>
      <ArrowRightIcon color={colors.subtle} size={18} weight="bold" />
    </Pressable>
  );
}

export function AssetsScreen({
  onOpenRecord,
  onOpenIelts,
  assetService: injectedAssetService,
}: {
  onOpenRecord: (record: SceneLearningRecord) => void;
  onOpenIelts: () => void;
  assetService?: LearningAssetServicePort;
}) {
  const [assetService] = useState<LearningAssetServicePort>(
    () => injectedAssetService ?? createLearningAssetService(),
  );
  const [sceneRecords, setSceneRecords] = useState<SceneLearningRecord[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let active = true;
    void assetService.listRecords().then(
      (records) => {
        if (active) setSceneRecords(records);
      },
      (cause: unknown) => {
        if (active) {
          setError(cause instanceof Error ? cause.message : '学习资产加载失败');
        }
      },
    );
    return () => {
      active = false;
    };
  }, [assetService, reloadKey]);

  const reload = () => {
    setError(null);
    setSceneRecords(null);
    setReloadKey((current) => current + 1);
  };

  const count = sceneRecords?.length ?? 0;
  return (
    <AppScreen>
      <PageHeader
        eyebrow="LEARNING ASSETS"
        title="学习资产"
        subtitle="把场景练习中真正用过的表达，留在这里继续复习。"
        action={<AssetModuleMenu onIelts={onOpenIelts} />}
      />
      <SectionTitle eyebrow="TRAINING HISTORY" title="场景训练记录" action={<Text style={styles.count}>{count} 条</Text>} />
      <Card style={styles.recordsCard}>
        {sceneRecords?.map((record) => <SceneRecordRow key={record.id} record={record} onPress={() => onOpenRecord(record)} />)}
        {sceneRecords === null && !error ? (
          <View style={styles.empty}>
            <BookOpenTextIcon color={colors.subtle} size={30} />
            <Text style={styles.emptyTitle}>正在同步场景学习资产…</Text>
          </View>
        ) : null}
        {error ? (
          <View style={styles.empty}>
            <Text style={styles.emptyTitle}>暂时无法加载</Text>
            <Text style={styles.emptyText}>{error}</Text>
            <AppButton title="重新加载" variant="soft" onPress={reload} />
          </View>
        ) : null}
        {sceneRecords?.length === 0 ? (
          <View style={styles.empty}>
            <BookOpenTextIcon color={colors.subtle} size={30} />
            <Text style={styles.emptyTitle}>暂无场景学习资产</Text>
            <Text style={styles.emptyText}>完成一次场景训练后，语言资产和最近对话会保存在这里。</Text>
          </View>
        ) : null}
      </Card>
    </AppScreen>
  );
}

function ExpressionRow({ item }: { item: LearningExpression }) {
  return (
    <View style={styles.expressionRow}>
      <Pill>{item.type}</Pill>
      <View style={styles.flex}>
        <Text style={styles.expressionText}>{item.englishText}</Text>
        <Text style={styles.expressionTranslation}>{item.chineseText}</Text>
      </View>
      <Pressable accessibilityRole="button" accessibilityLabel={`播放 ${item.englishText}`} style={styles.playButton}>
        <PlayIcon color={colors.subtle} size={17} weight="fill" />
      </Pressable>
    </View>
  );
}

function ConversationThread({ record }: { record: SceneLearningRecord }) {
  return (
    <Card style={styles.conversationCard}>
      <View style={styles.conversationLabel}><TranslateIcon color={colors.ink} size={16} weight="bold" /><Text>对话语境下的纠错与地道表达</Text></View>
      {record.conversation.map((message) => (
        <View key={message.id} style={[styles.message, message.role === 'user' && styles.userMessage]}>
          <Text style={styles.messageSpeaker}>{message.speaker}</Text>
          <Text style={styles.messageText}>{message.text}</Text>
          {message.feedback ? (
            <View style={styles.feedbackCard}>
              <View style={styles.feedbackHeader}><CheckCircleIcon color={colors.ink} size={17} weight="fill" /><Text style={styles.feedbackTitle}>AI 表达评价</Text></View>
              <Text style={styles.feedbackLabel}>推荐表达</Text>
              <Text style={styles.feedbackExpression}>{message.feedback.suggestedExpression}</Text>
              <Text style={styles.feedbackLabel}>本轮总结</Text>
              <Text style={styles.feedbackSummary}>{message.feedback.feedbackSummary}</Text>
            </View>
          ) : null}
        </View>
      ))}
    </Card>
  );
}

export function SceneAssetDetail({ record, onBack, onPractice, onDelete }: { record: SceneLearningRecord; onBack: () => void; onPractice: () => void; onDelete: () => void }) {
  const [view, setView] = useState<'expressions' | 'conversation'>('expressions');
  const [confirmDelete, setConfirmDelete] = useState(false);
  return (
    <>
      <AppScreen>
        <View style={styles.detailBar}>
          <Pressable accessibilityRole="button" accessibilityLabel="返回" onPress={onBack} style={styles.roundButton}><ArrowLeftIcon color={colors.ink} size={20} weight="bold" /></Pressable>
          <View style={styles.detailActions}>
            <AppButton title="复练场景" variant="soft" icon="play" onPress={onPractice} style={styles.practiceButton} />
            <Pressable accessibilityRole="button" accessibilityLabel="删除当前学习资产" onPress={() => setConfirmDelete(true)} style={styles.roundButton}><TrashIcon color={colors.muted} size={19} /></Pressable>
          </View>
        </View>
        <View style={styles.detailHeading}>
          <Text style={styles.eyebrow}>普通场景</Text>
          <Text style={styles.detailTitle}>{record.title}</Text>
          <Text style={styles.detailMeta}>{record.date} · 已完成 {record.practiceCount} 次模拟 · {record.score ?? '—'} 分</Text>
        </View>
        <View style={styles.segmented}>
          <Pressable onPress={() => setView('expressions')} style={[styles.segment, view === 'expressions' && styles.segmentActive]}><Text style={[styles.segmentText, view === 'expressions' && styles.segmentTextActive]}>学习表达</Text></Pressable>
          <Pressable onPress={() => setView('conversation')} style={[styles.segment, view === 'conversation' && styles.segmentActive]}><Text style={[styles.segmentText, view === 'conversation' && styles.segmentTextActive]}>最近对话与评价</Text></Pressable>
        </View>
        {view === 'expressions' ? (
          <Card style={styles.expressionsCard}>{record.expressions.map((item) => <ExpressionRow key={item.id} item={item} />)}</Card>
        ) : <ConversationThread record={record} />}
      </AppScreen>
      <Modal transparent visible={confirmDelete} animationType="fade" onRequestClose={() => setConfirmDelete(false)}>
        <View style={styles.confirmRoot}>
          <Pressable style={styles.confirmBackdrop} onPress={() => setConfirmDelete(false)} />
          <View style={styles.confirmCard}>
            <Text style={styles.confirmTitle}>删除当前学习资产？</Text>
            <Text style={styles.confirmCopy}>这条场景记录、最近对话和评分将一起删除，且无法恢复。</Text>
            <View style={styles.confirmActions}>
              <AppButton title="取消" variant="secondary" onPress={() => setConfirmDelete(false)} style={styles.flex} />
              <AppButton title="确认删除" variant="danger" onPress={onDelete} style={styles.flex} />
            </View>
          </View>
        </View>
      </Modal>
    </>
  );
}

export function SceneAssetDetailLoader({
  sceneId,
  onBack,
  onPractice,
  onDelete,
  assetService: injectedAssetService,
}: {
  sceneId: string;
  onBack: () => void;
  onPractice: () => void;
  onDelete: () => void;
  assetService?: LearningAssetServicePort;
}) {
  const [assetService] = useState<LearningAssetServicePort>(
    () => injectedAssetService ?? createLearningAssetService(),
  );
  const [record, setRecord] = useState<SceneLearningRecord | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let active = true;
    void assetService.getRecord(sceneId).then(
      (nextRecord) => {
        if (active) setRecord(nextRecord);
      },
      (cause: unknown) => {
        if (active) {
          setError(cause instanceof Error ? cause.message : '学习资产详情加载失败');
        }
      },
    );
    return () => {
      active = false;
    };
  }, [assetService, reloadKey, sceneId]);

  const reload = () => {
    setError(null);
    setRecord(null);
    setReloadKey((current) => current + 1);
  };

  if (record) {
    return (
      <SceneAssetDetail
        record={record}
        onBack={onBack}
        onPractice={onPractice}
        onDelete={onDelete}
      />
    );
  }

  return (
    <AppScreen>
      <View style={styles.detailBar}>
        <Pressable accessibilityRole="button" accessibilityLabel="返回" onPress={onBack} style={styles.roundButton}><ArrowLeftIcon color={colors.ink} size={20} weight="bold" /></Pressable>
      </View>
      <Card style={styles.recordsCard}>
        <View style={styles.empty}>
          <BookOpenTextIcon color={colors.subtle} size={30} />
          <Text style={styles.emptyTitle}>{error ? '暂时无法加载' : '正在加载学习资产…'}</Text>
          {error ? <Text style={styles.emptyText}>{error}</Text> : null}
          {error ? <AppButton title="重新加载" variant="soft" onPress={reload} /> : null}
        </View>
      </Card>
    </AppScreen>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  pressed: { opacity: 0.72, transform: [{ scale: 0.985 }] },
  moduleTrigger: { minHeight: 42, paddingHorizontal: 14, flexDirection: 'row', alignItems: 'center', gap: 7, borderWidth: 1, borderColor: colors.line, borderRadius: 22, backgroundColor: colors.white },
  moduleTriggerText: { color: colors.ink, fontSize: 12, fontWeight: '500' },
  menuBackdrop: { flex: 1, paddingTop: 88, paddingRight: 22, alignItems: 'flex-end', backgroundColor: 'rgba(21,21,20,0.08)' },
  modulePopover: { width: 302, padding: 10, borderWidth: 1, borderColor: colors.line, borderRadius: 18, backgroundColor: colors.white, shadowColor: colors.ink, shadowOffset: { width: 0, height: 12 }, shadowOpacity: 0.16, shadowRadius: 28, elevation: 12, boxShadow: '0px 12px 30px rgba(21,21,20,0.16)' },
  moduleRow: { minHeight: 70, padding: 10, flexDirection: 'row', alignItems: 'center', gap: 11, borderRadius: 13 },
  moduleIcon: { width: 42, height: 42, alignItems: 'center', justifyContent: 'center', borderRadius: 12, backgroundColor: colors.soft },
  ieltsMark: { color: colors.ink, fontSize: 9, fontWeight: '600' },
  moduleTitle: { color: colors.ink, fontSize: 14, fontWeight: '500' },
  moduleNote: { marginTop: 4, color: colors.muted, fontSize: 11, fontWeight: '300' },
  count: { color: colors.muted, fontSize: 12, fontWeight: '300' },
  recordsCard: { paddingHorizontal: 16, paddingVertical: 2 },
  recordRow: { minHeight: 88, paddingVertical: 15, flexDirection: 'row', alignItems: 'center', gap: 12, borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: colors.line },
  recordIcon: { width: 46, height: 46, alignItems: 'center', justifyContent: 'center', borderRadius: 14, backgroundColor: colors.soft },
  recordTitle: { color: colors.ink, fontSize: 16, fontWeight: '500' },
  recordMeta: { marginTop: 5, color: colors.muted, fontSize: 12, fontWeight: '300' },
  recordScore: { color: colors.subtle, fontSize: 12, fontWeight: '300' },
  empty: { minHeight: 220, alignItems: 'center', justifyContent: 'center', gap: 9 },
  emptyTitle: { color: colors.ink, fontSize: 17, fontWeight: '500' },
  emptyText: { maxWidth: 250, color: colors.muted, fontSize: 13, lineHeight: 20, textAlign: 'center', fontWeight: '300' },
  detailBar: { minHeight: 48, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  detailActions: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  roundButton: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center', borderWidth: 1, borderColor: colors.line, borderRadius: 22, backgroundColor: colors.white },
  practiceButton: { minHeight: 44, paddingHorizontal: 16 },
  detailHeading: { gap: 7 },
  eyebrow: { color: colors.subtle, fontSize: 11, fontWeight: '500', letterSpacing: 1.4 },
  detailTitle: { color: colors.ink, fontSize: 31, lineHeight: 38, fontWeight: '600', letterSpacing: -1 },
  detailMeta: { color: colors.muted, fontSize: 13, lineHeight: 20, fontWeight: '300' },
  segmented: { padding: 4, flexDirection: 'row', borderRadius: 15, backgroundColor: colors.soft },
  segment: { minHeight: 42, flex: 1, alignItems: 'center', justifyContent: 'center', borderRadius: 12 },
  segmentActive: { backgroundColor: colors.ink },
  segmentText: { color: colors.muted, fontSize: 12, fontWeight: '500' },
  segmentTextActive: { color: colors.white },
  expressionsCard: { paddingHorizontal: 18, paddingVertical: 0 },
  expressionRow: { minHeight: 92, paddingVertical: 16, flexDirection: 'row', alignItems: 'center', gap: 12, borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: colors.line },
  expressionText: { color: colors.ink, fontSize: 16, lineHeight: 22, fontWeight: '500' },
  expressionTranslation: { marginTop: 5, color: colors.muted, fontSize: 12, lineHeight: 18, fontWeight: '300' },
  playButton: { width: 40, height: 40, alignItems: 'center', justifyContent: 'center', borderWidth: 1, borderColor: colors.line, borderRadius: 20, backgroundColor: colors.white },
  conversationCard: { padding: 18, gap: 22 },
  conversationLabel: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  message: { maxWidth: '92%', gap: 7 },
  userMessage: { alignSelf: 'flex-end' },
  messageSpeaker: { color: colors.subtle, fontSize: 11, fontWeight: '600' },
  messageText: { color: colors.ink, fontSize: 18, lineHeight: 27, fontWeight: '500' },
  feedbackCard: { marginTop: 5, padding: 16, gap: 6, borderWidth: 1, borderColor: colors.line, borderRadius: 15, backgroundColor: colors.paper },
  feedbackHeader: { marginBottom: 4, flexDirection: 'row', alignItems: 'center', gap: 7 },
  feedbackTitle: { color: colors.ink, fontSize: 13, fontWeight: '500' },
  feedbackLabel: { marginTop: 5, color: colors.subtle, fontSize: 10, fontWeight: '500', letterSpacing: 1 },
  feedbackExpression: { color: colors.ink, fontSize: 15, lineHeight: 22, fontWeight: '500' },
  feedbackSummary: { color: colors.muted, fontSize: 13, lineHeight: 20, fontWeight: '300' },
  confirmRoot: { flex: 1, justifyContent: 'center', padding: 22 },
  confirmBackdrop: { position: 'absolute', top: 0, right: 0, bottom: 0, left: 0, backgroundColor: 'rgba(21,21,20,0.34)' },
  confirmCard: { padding: 22, gap: 11, borderRadius: 20, backgroundColor: colors.white },
  confirmTitle: { color: colors.ink, fontSize: 22, fontWeight: '600' },
  confirmCopy: { color: colors.muted, fontSize: 13, lineHeight: 20, fontWeight: '300' },
  confirmActions: { marginTop: 10, flexDirection: 'row', gap: 10 },
});
