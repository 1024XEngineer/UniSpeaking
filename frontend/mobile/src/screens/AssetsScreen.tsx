import { useEffect, useState } from 'react';
import { Modal, Pressable, StyleSheet, Text, View } from 'react-native';
import { ArrowLeftIcon } from 'phosphor-react-native/src/icons/ArrowLeft';
import { ArrowRightIcon } from 'phosphor-react-native/src/icons/ArrowRight';
import { BookOpenTextIcon } from 'phosphor-react-native/src/icons/BookOpenText';
import { CheckCircleIcon } from 'phosphor-react-native/src/icons/CheckCircle';
import { PlayIcon } from 'phosphor-react-native/src/icons/Play';
import { TranslateIcon } from 'phosphor-react-native/src/icons/Translate';

import { LearningAssetsHeader } from '@/components/LearningAssetsHeader';
import { AppButton, AppScreen, Card, HeaderIconButton, PageHeader, Pill } from '@/components/ui';
import { SceneCategoryTag } from '@/components/SceneCategoryTag';
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

const PAGE_SIZE = 8;

function SceneRecordRow({ record, onPress }: { record: SceneLearningRecord; onPress: () => void }) {
  return (
    <Pressable accessibilityRole="button" onPress={onPress} style={({ pressed }) => [styles.recordRow, pressed && styles.pressed]}>
      <View style={styles.flex}>
        <View style={styles.recordTitleRow}>
          <Text style={styles.recordTitle}>{record.title}</Text>
          <SceneCategoryTag category={record.category ?? 'other'} />
        </View>
        <Text style={styles.recordMeta}>{record.date} · {record.status}</Text>
      </View>
      <Text style={styles.recordScore}>{record.score === null ? '待练习' : `${record.score} 分`}</Text>
      <ArrowRightIcon color={colors.subtle} size={18} weight="bold" />
    </Pressable>
  );
}

export function AssetsScreen({
  onOpenRecord,
  onOpenIelts,
  onOpenInterview,
  assetService: injectedAssetService,
}: {
  onOpenRecord: (record: SceneLearningRecord) => void;
  onOpenIelts: () => void;
  onOpenInterview?: () => void;
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

  const records = sceneRecords ?? [];
  const [page, setPage] = useState(0);
  const pageCount = Math.max(1, Math.ceil(records.length / PAGE_SIZE));
  const currentPage = Math.min(page, pageCount - 1);
  const visibleRecords = records.slice(currentPage * PAGE_SIZE, (currentPage + 1) * PAGE_SIZE);

  return (
    <AppScreen
      contentStyle={styles.mainContent}
      fixedHeader={<LearningAssetsHeader current="scenes" onIelts={onOpenIelts} onInterview={onOpenInterview ?? (() => undefined)} />}
    >
      <View style={styles.assetIntro}>
        <Text style={styles.assetHeadingSubtitle}>把场景练习中真正用过的表达，留在这里继续复习。</Text>
      </View>
      <Card style={styles.recordsCard}>
        {visibleRecords.map((record) => <SceneRecordRow key={record.id} record={record} onPress={() => onOpenRecord(record)} />)}
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
      {pageCount > 1 ? (
        <View style={styles.pagination}>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="上一页"
            disabled={currentPage === 0}
            onPress={() => setPage((value) => Math.max(0, value - 1))}
            style={({ pressed }) => [styles.paginationButton, currentPage === 0 && styles.paginationButtonDisabled, pressed && styles.pressed]}
          >
            <ArrowLeftIcon color={currentPage === 0 ? colors.line : colors.ink} size={19} weight="bold" />
          </Pressable>
          <Text style={styles.paginationLabel}>{currentPage + 1} / {pageCount}</Text>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="下一页"
            disabled={currentPage === pageCount - 1}
            onPress={() => setPage((value) => Math.min(pageCount - 1, value + 1))}
            style={({ pressed }) => [styles.paginationButton, currentPage === pageCount - 1 && styles.paginationButtonDisabled, pressed && styles.pressed]}
          >
            <ArrowRightIcon color={currentPage === pageCount - 1 ? colors.line : colors.ink} size={19} weight="bold" />
          </Pressable>
        </View>
      ) : null}
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
      <AppScreen
        fixedHeader={
          <PageHeader
            fixed
            onBack={onBack}
            title="详情"
            action={<HeaderIconButton icon="delete" accessibilityLabel="删除当前学习资产" onPress={() => setConfirmDelete(true)} color={colors.muted} />}
          />
        }
      >
        <View style={styles.detailHeading}>
          <Text style={styles.eyebrow}>普通场景</Text>
          <Text style={styles.detailTitle}>{record.title}</Text>
          <Text style={styles.detailMeta}>{record.date} · 已完成 {record.practiceCount} 次模拟 · {record.score ?? '—'} 分</Text>
          <AppButton title="复练场景" variant="secondary" icon="play" onPress={onPractice} style={styles.practiceButton} />
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
    <AppScreen fixedHeader={<PageHeader fixed onBack={onBack} title="详情" />}>
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
  mainContent: {},
  assetIntro: { alignItems: 'flex-start' },
  assetHeadingSubtitle: { color: colors.muted, fontSize: 15, lineHeight: 23, fontWeight: '300' },
  recordsCard: { paddingHorizontal: 16, paddingVertical: 2 },
  pagination: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 16 },
  paginationButton: { width: 40, height: 40, alignItems: 'center', justifyContent: 'center', borderWidth: 1, borderColor: colors.line, borderRadius: 20, backgroundColor: colors.white },
  paginationButtonDisabled: { opacity: 0.55 },
  paginationLabel: { minWidth: 42, color: colors.muted, fontSize: 12, textAlign: 'center', fontWeight: '300' },
  recordRow: { minHeight: 88, paddingVertical: 15, flexDirection: 'row', alignItems: 'center', gap: 12, borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: colors.line },
  recordTitleRow: { flexDirection: 'row', alignItems: 'center', gap: 7 },
  recordTitle: { color: colors.ink, fontSize: 16, fontWeight: '500' },
  recordMeta: { marginTop: 5, color: colors.muted, fontSize: 12, fontWeight: '300' },
  recordScore: { color: colors.subtle, fontSize: 12, fontWeight: '300' },
  empty: { minHeight: 220, alignItems: 'center', justifyContent: 'center', gap: 9 },
  emptyTitle: { color: colors.ink, fontSize: 17, fontWeight: '500' },
  emptyText: { maxWidth: 250, color: colors.muted, fontSize: 13, lineHeight: 20, textAlign: 'center', fontWeight: '300' },
  detailHeading: { gap: 7 },
  practiceButton: { alignSelf: 'flex-start', marginTop: 5 },
  eyebrow: { color: colors.subtle, fontSize: 11, fontWeight: '500', letterSpacing: 1.4 },
  detailTitle: { color: colors.ink, fontSize: 27, lineHeight: 34, fontWeight: '600' },
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
