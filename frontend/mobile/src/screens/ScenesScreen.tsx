import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { ActivityIndicator, Animated, Image, PanResponder, Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import { LinearGradient } from 'expo-linear-gradient';
import Reanimated, {
  cancelAnimation,
  Easing as ReanimatedEasing,
  useAnimatedStyle,
  useSharedValue,
  withRepeat,
  withTiming,
} from 'react-native-reanimated';
import Svg, { Circle, Line, Polygon, Text as SvgText } from 'react-native-svg';

import {
  AppIcon,
  AppScreen,
  EvaluationPendingOverlay,
  MainModuleHeader,
} from '@/components/ui';
import { SceneCategoryTag } from '@/components/SceneCategoryTag';
import {
  getDailyScenePromptExample,
  learningItems,
  recommendations,
  type ScenePromptExample,
} from '@/data/content';
import { SceneService, type GeneratedScene } from '@/features/scenes/SceneService';
import { SceneTrainingController, type SceneTrainingSnapshot } from '@/features/scenes/SceneTrainingController';
import { WavRecorder } from '@/features/audio/WavRecorder';
import { createTurnAudioCapture } from '@/features/audio/TurnAudioCapture';
import { SceneSpeechClient, TtsPlayer } from '@/features/audio/TtsPlayer';
import {
  useFreeChatSession,
  type FreeChatConfig,
  type FreeChatControllerPort,
} from '@/features/conversation/useFreeChatSession';
import { ReactNativeWebRTCTransport } from '@/features/realtime/ReactNativeWebRTCTransport';
import { RealtimeSessionApi } from '@/features/realtime/RealtimeSessionApi';
import { RealtimeSessionController } from '@/features/realtime/RealtimeSessionController';
import { SessionMessageSocket } from '@/features/realtime/SessionMessageSocket';
import {
  SceneDialogueApi,
  type DialogueCompletion,
  type DialogueReport,
} from '@/features/scenes/SceneDialogueApi';
import { speedCodeForLabel } from '@/features/auth/preferenceMappings';
import { SecureTokenStore } from '@/infrastructure/auth/SecureTokenStore';
import { getRuntimeConfig } from '@/infrastructure/config/runtimeConfig';
import { ApiClient } from '@/infrastructure/http/ApiClient';
import { sceneCategoryForLabel } from '@/data/sceneCategories';
import { useAppModel } from '@/model/AppModel';
import { useLearningStage } from '@/navigation/learningStage';
import { forgetSpecialty } from '@/navigation/specialtyMemory';
import { createTranscriptTranslationApi } from '@/features/conversation/TranscriptTranslationApi';
import { colors } from '@/theme/tokens';
import { CallExperience, selectCallCaption } from './ConversationScreen';
import { IeltsFlow, InterviewFlow } from './SpecialtyFlows';

export type SceneRoute =
  | { name: 'home' }
  | { name: 'training'; scene: GeneratedScene }
  | { name: 'ielts' }
  | { name: 'interview' };

const stages = [
  { key: 'learn', label: '学', note: '积累表达' },
  { key: 'read', label: '读', note: '开口朗读' },
  { key: 'speak', label: '说', note: '场景对话' },
] as const;

type TrainingStage = (typeof stages)[number]['key'];

function StageProgressRail({
  stage,
  unlockedStage,
  onSelect,
  onCollapsedChange,
}: {
  stage: TrainingStage;
  unlockedStage: number;
  onSelect: (stage: TrainingStage) => void;
  onCollapsedChange?: (collapsed: boolean) => void;
}) {
  const [railWidth, setRailWidth] = useState(0);
  const [collapsed, setCollapsed] = useState(false);
  const [collapseProgress] = useState(() => new Animated.Value(0));
  const activeIndex = stages.findIndex((item) => item.key === stage);

  useEffect(() => {
    onCollapsedChange?.(collapsed);
    Animated.spring(collapseProgress, {
      toValue: collapsed ? 1 : 0,
      damping: 19,
      stiffness: 185,
      mass: 0.78,
      useNativeDriver: true,
    }).start();
  }, [collapseProgress, collapsed, onCollapsedChange]);

  const panResponder = useMemo(
    () =>
      PanResponder.create({
        onMoveShouldSetPanResponder: (_, gesture) =>
          Math.abs(gesture.dx) > 10 && Math.abs(gesture.dx) > Math.abs(gesture.dy),
        onPanResponderRelease: (_, gesture) => {
          if (gesture.dx > 42) setCollapsed(true);
          if (gesture.dx < -42) setCollapsed(false);
        },
      }),
    [],
  );

  const progressWidth =
    unlockedStage === 0 ? 0 : unlockedStage === 1 ? Math.max(0, railWidth / 2 - 39) : Math.max(0, railWidth - 39);
  const fullRailStyle = {
    opacity: collapseProgress.interpolate({ inputRange: [0, 0.7], outputRange: [1, 0] }),
    transform: [
      {
        translateX: collapseProgress.interpolate({
          inputRange: [0, 1],
          outputRange: [0, Math.max(0, railWidth - 42)],
        }),
      },
    ],
  };
  const collapsedStyle = {
    opacity: collapseProgress,
    transform: [{ scale: collapseProgress.interpolate({ inputRange: [0, 1], outputRange: [0.72, 1] }) }],
  };

  return (
    <View
      accessibilityLabel={`训练进度，当前${stages[activeIndex].label}阶段。向右滑动可收起，向左滑动可展开。`}
      onLayout={(event) => setRailWidth(event.nativeEvent.layout.width)}
      style={styles.stepperViewport}
      {...panResponder.panHandlers}
    >
      <Animated.View accessibilityElementsHidden={collapsed} pointerEvents={collapsed ? 'none' : 'auto'} style={[styles.learningStepper, fullRailStyle]}>
        <View style={styles.stepperLine} />
        <View style={[styles.stepperProgress, { width: progressWidth }]} />
        {stages.map((item, index) => {
          const active = stage === item.key;
          const done = index < unlockedStage && !active;
          const available = index <= unlockedStage;
          return (
            <Pressable
              accessibilityRole="tab"
              accessibilityState={{ selected: active, disabled: !available }}
              disabled={!available}
              key={item.key}
              onPress={() => onSelect(item.key)}
              style={styles.stepItem}
            >
              <View style={[styles.stepDot, (active || done) && styles.stepDotActive, active && styles.stepDotCurrent]}>
                {done ? <AppIcon name="check" size={15} color={colors.white} /> : <Text style={[styles.stepNumber, active && styles.stepNumberActive]}>{index + 1}</Text>}
              </View>
              <Text style={[styles.stepLabel, (active || done) && styles.stepLabelActive]}>{item.label}</Text>
              <Text style={styles.stepNote}>{item.note}</Text>
            </Pressable>
          );
        })}
      </Animated.View>
      <Animated.View pointerEvents={collapsed ? 'auto' : 'none'} style={[styles.collapsedStep, collapsedStyle]}>
        <Pressable accessibilityRole="button" accessibilityLabel="展开训练进度" onPress={() => setCollapsed(false)} style={styles.collapsedStepButton}>
          <Text style={styles.collapsedStepText}>{stages[activeIndex].label}</Text>
        </Pressable>
      </Animated.View>
    </View>
  );
}

type SceneMetric = { label: string; value: number };

const defaultSceneMetrics: readonly SceneMetric[] = [
  { label: '准确', value: 86 },
  { label: '流利', value: 88 },
  { label: '语法', value: 82 },
  { label: '词汇', value: 84 },
  { label: '自然', value: 87 },
];

export function sceneMetricsForReport(
  report?: DialogueReport,
): readonly SceneMetric[] {
  if (!report) return defaultSceneMetrics;
  return [
    { label: '准确', value: report.accuracyScore },
    { label: '流利', value: report.fluencyScore },
    { label: '语法', value: report.grammarScore },
    { label: '词汇', value: report.vocabularyScore },
    { label: '自然', value: report.naturalnessScore },
  ];
}

function SceneRadar({ metrics }: { metrics: readonly SceneMetric[] }) {
  const size = 208;
  const center = size / 2;
  const radius = 64;
  const pointAt = (index: number, pointRadius: number) => {
    const angle = -Math.PI / 2 + index * (Math.PI * 2 / metrics.length);
    return { x: center + Math.cos(angle) * pointRadius, y: center + Math.sin(angle) * pointRadius };
  };
  const pointsAt = (pointRadius: number) => metrics.map((_, index) => pointAt(index, pointRadius)).map((point) => `${point.x},${point.y}`).join(' ');
  const resultPoints = metrics.map((metric, index) => pointAt(index, radius * metric.value / 100));

  return (
    <Svg accessibilityLabel={`五维评分：${metrics.map((metric) => `${metric.label}${metric.value}分`).join('，')}`} height={size} width={size}>
      {[1, 2, 3, 4].map((level) => <Polygon key={level} fill="none" points={pointsAt(radius * level / 4)} stroke="#DEDED9" strokeWidth={1} />)}
      {metrics.map((_, index) => {
        const point = pointAt(index, radius);
        return <Line key={index} stroke="#DEDED9" strokeWidth={1} x1={center} x2={point.x} y1={center} y2={point.y} />;
      })}
      <Polygon fill="rgba(21,21,21,0.15)" points={resultPoints.map((point) => `${point.x},${point.y}`).join(' ')} stroke="#151515" strokeWidth={2} />
      {resultPoints.map((point, index) => <Circle key={index} cx={point.x} cy={point.y} fill="#151515" r={2.5} />)}
      {metrics.map((metric, index) => {
        const point = pointAt(index, 87);
        return <SvgText key={metric.label} fill="#777773" fontSize="11" fontWeight="700" textAnchor="middle" x={point.x} y={point.y + 4}>{metric.label}</SvgText>;
      })}
    </Svg>
  );
}

type SceneControllerFactory = (
  sceneId: string,
  config: FreeChatConfig,
) => FreeChatControllerPort;

function createDefaultSceneController(
  sceneId: string,
  config: FreeChatConfig,
): FreeChatControllerPort {
  const tokenStore = new SecureTokenStore();
  const { backendUrl } = getRuntimeConfig();
  const apiClient = new ApiClient({ baseUrl: backendUrl, tokenStore });
  return new RealtimeSessionController(
    {
      transport: new ReactNativeWebRTCTransport(),
      sessionApi: new RealtimeSessionApi(apiClient),
      sessionSocket: new SessionMessageSocket({ baseUrl: backendUrl, tokenStore }),
      sceneDialogue: new SceneDialogueApi(apiClient, sceneId),
      turnAudioCapture: createTurnAudioCapture(new WavRecorder()),
    },
    {
      mode: 'scene',
      sceneId,
      ...config,
    },
  );
}

export function SceneCallStage({
  scene,
  progressCollapsed,
  onComplete,
  createController = createDefaultSceneController,
}: {
  scene: GeneratedScene;
  progressCollapsed: boolean;
  onComplete: (completion: DialogueCompletion) => void;
  createController?: SceneControllerFactory;
}) {
  const { teacher, speed } = useAppModel();
  const deliveredCompletion = useRef<DialogueCompletion | null>(null);
  const autoEnding = useRef(false);
  const [evaluationPending, setEvaluationPending] = useState(false);
  const [translationApi] = useState(createTranscriptTranslationApi);
  const session = useFreeChatSession(
    {
      voice: teacher.voiceId,
      model: 'qwen3.5-omni-flash-realtime',
      speechSpeed: speedCodeForLabel(speed),
    },
    (config) => createController(scene.sceneId, config),
  );

  useEffect(() => {
    if (
      session.completion &&
      session.completion !== deliveredCompletion.current
    ) {
      deliveredCompletion.current = session.completion;
      onComplete(session.completion);
    }
  }, [onComplete, session.completion]);

  const translate = useCallback(
    (text: string) => translationApi.translateScene(scene.sceneId, text),
    [scene.sceneId, translationApi],
  );

  const caption = selectCallCaption(session, teacher.name, session.statusLabel);
  return (
    <View style={styles.sceneCallExperience}>
      <CallExperience
        allowSubtitleToggle={false}
        compactTranscriptLayout
        elapsed={session.elapsed}
        muted={session.muted}
        onEnd={() => {
          if (autoEnding.current) return;
          autoEnding.current = true;
          setEvaluationPending(true);
          void session.end().catch(() => {
            autoEnding.current = false;
            setEvaluationPending(false);
          });
        }}
        onMutedChange={() => session.toggleMuted()}
        onTranslate={translate}
        progressCollapsed={progressCollapsed}
        statusLabel={session.statusLabel}
        transcriptSpeaker={caption.speaker}
        transcriptEnglish={caption.text}
        userTranscript={session.userTranscript}
        transcriptHistory={session.transcriptHistory}
      />
      {(evaluationPending || session.state === 'ending') ? (
        <EvaluationPendingOverlay copy="正在整理本次对话与五项能力表现…" />
      ) : null}
    </View>
  );
}

export function Training({ id, scene, trainingController: injectedTrainingController, wavRecorder: injectedWavRecorder, ttsPlayer: injectedTtsPlayer, initialStage = 'learn', onBack, onFinish, onViewDetails }: { id?: string; scene?: GeneratedScene; trainingController?: SceneTrainingController; wavRecorder?: Pick<WavRecorder, 'start' | 'stop' | 'cancel'>; ttsPlayer?: Pick<TtsPlayer, 'play' | 'stop'>; initialStage?: TrainingStage; onBack: () => void; onFinish: () => void; onViewDetails?: (id: string) => void }) {
  const { setImmersiveLearning } = useLearningStage();
  const sceneId = scene?.sceneId ?? id ?? recommendations[0].id;
  const scenario = scene
    ? { id: scene.sceneId, title: scene.title }
    : recommendations.find((item) => item.id === sceneId) ?? recommendations[0];
  const wordItems = learningItems.filter((item) => item.type === '单词');
  const phraseItems = learningItems.filter((item) => item.type === '短语');
  const readItems = learningItems.filter((item) => item.type === '句子');
  const [stage, setStage] = useState<TrainingStage>(initialStage);
  const [unlockedStage, setUnlockedStage] = useState(() => (initialStage === 'speak' ? 2 : initialStage === 'read' ? 1 : 0));
  const [learningGroup, setLearningGroup] = useState<'words' | 'phrases'>('words');
  const [learnIndex, setLearnIndex] = useState(0);
  const readIndex = 0;
  const [readPassed, setReadPassed] = useState(false);
  const [readFeedbackOpen, setReadFeedbackOpen] = useState(false);
  const [demoPlaying, setDemoPlaying] = useState(false);
  const demoActive = useRef(false);
  const [recording, setRecording] = useState(false);
  const [audioError, setAudioError] = useState<string | null>(null);
  const [completionOpen, setCompletionOpen] = useState(false);
  const [dialogueCompletion, setDialogueCompletion] = useState<DialogueCompletion | null>(null);
  const [progressCollapsed, setProgressCollapsed] = useState(false);
  const readRecordingTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const [trainingController] = useState<SceneTrainingController | null>(() =>
    scene
      ? injectedTrainingController ?? new SceneTrainingController(createDefaultSceneService())
      : null,
  );
  const [wavRecorder] = useState<Pick<WavRecorder, 'start' | 'stop' | 'cancel'> | null>(
    () => (scene ? injectedWavRecorder ?? new WavRecorder() : null),
  );
  const [ttsPlayer] = useState<Pick<TtsPlayer, 'play' | 'stop'> | null>(
    () => (scene ? injectedTtsPlayer ?? createDefaultTtsPlayer() : null),
  );
  const [trainingSnapshot, setTrainingSnapshot] = useState<SceneTrainingSnapshot | null>(
    () => trainingController?.getSnapshot() ?? null,
  );

  useEffect(() => {
    setImmersiveLearning(true);
    return () => setImmersiveLearning(false);
  }, [setImmersiveLearning]);

  useEffect(() => {
    if (!scene || !trainingController) return;
    const unsubscribe = trainingController.subscribe(setTrainingSnapshot);
    void trainingController.start(scene, initialStage).catch(() => undefined);
    return unsubscribe;
  }, [initialStage, scene, trainingController]);

  useEffect(
    () => () => {
      if (readRecordingTimer.current) {
        clearTimeout(readRecordingTimer.current);
        readRecordingTimer.current = null;
      }
      void wavRecorder?.cancel().catch(() => undefined);
      ttsPlayer?.stop();
    },
    [ttsPlayer, wavRecorder],
  );

  const displayedStage = scene && trainingSnapshot ? trainingSnapshot.stage : stage;
  const displayedLearningGroup = scene && trainingSnapshot
    ? trainingSnapshot.learningGroup
    : learningGroup;
  const displayedUnlockedStage = scene && trainingSnapshot
    ? trainingSnapshot.unlockedStage
    : unlockedStage;
  const displayedIndex = scene && trainingSnapshot ? trainingSnapshot.index : learnIndex;
  const backendItems = trainingSnapshot?.items.map((item) => ({
    type: displayedStage === 'read' ? '句子' : displayedLearningGroup === 'words' ? '单词' : '短语',
    en: item.englishText,
    zh: item.chineseText,
    phonetic: item.phonetic ?? '',
  })) ?? [];
  const learnItems = scene && displayedStage === 'learn'
    ? backendItems
    : displayedLearningGroup === 'words' ? wordItems : phraseItems;
  const displayedReadItems = scene && displayedStage === 'read' ? backendItems : readItems;
  const learnItem = learnItems[displayedIndex] ?? learnItems[0] ?? learningItems[0];
  const displayedReadIndex = scene && trainingSnapshot ? trainingSnapshot.index : readIndex;
  const readItem = displayedReadItems[displayedReadIndex] ?? displayedReadItems[0] ?? learningItems[3];
  const displayedReadPassed = scene && trainingSnapshot
    ? Boolean(trainingSnapshot.readingResult?.passed)
    : readPassed;
  const isLastReadItem = displayedReadIndex >= displayedReadItems.length - 1;
  const readingResult = trainingSnapshot?.readingResult;
  const trainingTransitioning = trainingSnapshot?.status === 'loading';
  const completionMetrics = sceneMetricsForReport(dialogueCompletion?.evaluation);

  const toggleDemo = async (text: string) => {
    if (!scene || !ttsPlayer) {
      setDemoPlaying((current) => !current);
      return;
    }
    if (demoActive.current) {
      ttsPlayer.stop();
      demoActive.current = false;
      setDemoPlaying(false);
      return;
    }
    demoActive.current = true;
    setDemoPlaying(true);
    setAudioError(null);
    try {
      await ttsPlayer.play(scene.sceneId, text);
    } catch (error) {
      demoActive.current = false;
      setDemoPlaying(false);
      setAudioError(
        error instanceof Error ? error.message : '标准发音播放失败',
      );
    }
  };

  const nextLearn = () => {
    ttsPlayer?.stop();
    demoActive.current = false;
    setDemoPlaying(false);
    if (scene && trainingController) {
      void trainingController.next().catch(() => undefined);
      return;
    }
    if (learnIndex < learnItems.length - 1) setLearnIndex((current) => current + 1);
    else if (learningGroup === 'words' && phraseItems.length > 0) {
      setLearningGroup('phrases');
      setLearnIndex(0);
    }
    else {
      setUnlockedStage((current) => Math.max(current, 1));
      setStage('read');
    }
  };
  const previousLearn = () => {
    ttsPlayer?.stop();
    demoActive.current = false;
    setDemoPlaying(false);
    if (scene && trainingController) {
      trainingController.previous();
      return;
    }
    if (learnIndex > 0) setLearnIndex((current) => current - 1);
    else if (learningGroup === 'phrases' && wordItems.length > 0) {
      setLearningGroup('words');
      setLearnIndex(wordItems.length - 1);
    }
  };
  const clearReadRecordingTimer = () => {
    if (!readRecordingTimer.current) return;
    clearTimeout(readRecordingTimer.current);
    readRecordingTimer.current = null;
  };
  const submitReadRecording = async () => {
    clearReadRecordingTimer();
    setRecording(false);
    if (!scene || !trainingController || !wavRecorder) return;
    try {
      const wavUri = await wavRecorder.stop();
      await trainingController.scoreReading(wavUri);
      setReadFeedbackOpen(true);
    } catch (error) {
      setAudioError(
        error instanceof Error ? error.message : '朗读录音或评分失败',
      );
    }
  };
  const toggleReadRecording = async () => {
    if (scene && trainingController && wavRecorder) {
      setAudioError(null);
      if (recording) {
        await submitReadRecording();
        return;
      }
      try {
        await wavRecorder.start();
        setRecording(true);
        readRecordingTimer.current = setTimeout(() => {
          void submitReadRecording();
        }, 30_000);
      } catch (error) {
        setRecording(false);
        setAudioError(
          error instanceof Error ? error.message : '朗读录音或评分失败',
        );
      }
      return;
    }
    if (recording) {
      setRecording(false);
      setReadPassed(true);
      setReadFeedbackOpen(true);
    } else {
      setReadPassed(false);
      setRecording(true);
    }
  };
  const closeCompletion = () => {
    setCompletionOpen(false);
  };
  const finishTraining = () => {
    closeCompletion();
    onFinish();
  };
  const viewTrainingDetails = () => {
    closeCompletion();
    if (onViewDetails) requestAnimationFrame(() => onViewDetails(sceneId));
    else onFinish();
  };

  return (
    <View style={styles.trainingRoot}>
    <AppScreen scrollEnabled={false} contentStyle={styles.trainingScreen}>
      <View style={styles.trainingHeader}>
        <View style={styles.trainingHeaderCopy}>
          <Text style={styles.trainingEyebrow}>SCENARIO PRACTICE</Text>
          <Text style={styles.trainingTitle}>{scenario.title}</Text>
          <Text style={styles.trainingSubtitle}>从语言到真实表达</Text>
        </View>
        <Pressable accessibilityRole="button" accessibilityLabel="取消训练" onPress={onBack} style={styles.trainingCancel}>
          <AppIcon name="close" size={19} color={colors.muted} />
        </Pressable>
      </View>

      <StageProgressRail
        stage={displayedStage}
        unlockedStage={displayedUnlockedStage}
        onCollapsedChange={setProgressCollapsed}
        onSelect={(nextStage) => {
          setRecording(false);
          setDemoPlaying(false);
          if (scene) return;
          setStage(nextStage);
        }}
      />

      {trainingSnapshot?.error ? <Text style={styles.generationError}>{trainingSnapshot.error}</Text> : null}

      {displayedStage === 'learn' ? (
        <View style={styles.stageShell}>
          <View style={styles.stageMetaRow}>
            <Text style={styles.stageHeading}>{displayedLearningGroup === 'words' ? '场景单词' : '场景短语'}</Text>
          </View>
          <View style={styles.languageCard}>
            <Text style={styles.languageCount}>{displayedIndex + 1} / {learnItems.length}</Text>
            <Text style={styles.languageType}>{learnItem.type}</Text>
            <Text style={styles.languageEnglish}>{learnItem.en}</Text>
            <View style={styles.pronunciationRow}>
              <Text style={styles.phoneticText}>{learnItem.phonetic}</Text>
              <Pressable
                accessibilityRole="button"
                accessibilityLabel="播放发音"
                onPress={() => void toggleDemo(learnItem.en)}
                style={({ pressed }) => [styles.speakerButton, (pressed || demoPlaying) && styles.speakerButtonActive]}
              >
                <AppIcon name="volume" size={21} color={colors.subtle} />
              </Pressable>
            </View>
            <Text style={styles.languageChinese}>{learnItem.zh}</Text>
          </View>
          <View style={styles.stageFooterRow}>
            <Pressable
              accessibilityRole="button"
              accessibilityLabel="上一个表达"
              disabled={displayedIndex === 0 && displayedLearningGroup === 'words'}
              onPress={previousLearn}
              style={[styles.roundNavButton, displayedIndex === 0 && displayedLearningGroup === 'words' && styles.roundNavDisabled]}
            >
              <AppIcon name="arrow-left" size={20} />
            </Pressable>
            <Pressable
              accessibilityRole="button"
              disabled={trainingTransitioning}
              onPress={nextLearn}
              style={[styles.primaryPillButton, trainingTransitioning && styles.primaryPillDisabled]}
            >
              <Text style={styles.primaryPillText}>
                {displayedIndex < learnItems.length - 1 ? '下一个' : displayedLearningGroup === 'words' ? '进入词组' : '进入朗读'}
              </Text>
              <AppIcon name="arrow-right" size={18} color={colors.white} />
            </Pressable>
          </View>
        </View>
      ) : null}

      {displayedStage === 'read' ? (
        <View style={styles.stageShell}>
          <View style={styles.stageMetaRow}>
            <Text style={styles.stageHeading}>场景句子</Text>
            <Text style={styles.stageCount}>{displayedReadIndex + 1} / {displayedReadItems.length}</Text>
          </View>
          <View style={styles.readCard}>
            {displayedReadPassed ? <View style={styles.scoreBadge}><Text style={styles.scoreBadgeValue}>{Math.round(trainingSnapshot?.readingResult?.overallScore ?? 86)}</Text><Text style={styles.scoreBadgeMax}>/100</Text></View> : null}
            <Text style={styles.readSentence}>
              {displayedReadPassed ? readItem.en : readItem.en}
            </Text>
            <Text style={styles.readTranslation}>{readItem.zh}</Text>
            <Pressable accessibilityRole="button" accessibilityLabel={recording ? '结束朗读' : '开始朗读'} onPress={() => void toggleReadRecording()} style={[styles.readRecordButton, recording && styles.readRecordButtonActive]}>
              <AppIcon name={recording ? 'microphone' : 'microphone-off'} size={28} color={recording ? '#B94D44' : colors.subtle} />
            </Pressable>
            <Text style={styles.readStatus}>{recording ? '正在听你朗读' : displayedReadPassed ? '朗读通过' : '点击麦克风开始朗读'}</Text>
            <Text style={styles.readInstruction}>{recording ? '再次点击麦克风结束录音并提交评分，最长录制 30 秒。' : displayedReadPassed ? '本句已达到通过标准，可以进入下一步。' : '再次点击麦克风结束录音并提交评分，最长录制 30 秒。'}</Text>
            {audioError ? <Text style={styles.generationError}>{audioError}</Text> : null}
            <Pressable accessibilityRole="button" accessibilityLabel="听标准示范" onPress={() => void toggleDemo(readItem.en)} style={styles.readDemoButton}>
              <Text style={styles.readDemoText}>{demoPlaying ? '正在播放' : '听标准示范'}</Text>
              <AppIcon name="volume" size={18} color={colors.subtle} />
            </Pressable>
          </View>
          <View style={styles.stageFooterRow}>
            <Pressable accessibilityRole="button" onPress={() => setStage('learn')} style={styles.roundNavButton}>
              <AppIcon name="arrow-left" size={20} />
            </Pressable>
            <Pressable
              accessibilityRole="button"
              disabled={!displayedReadPassed || trainingTransitioning}
              onPress={() => {
                if (scene && trainingController) void trainingController.next().catch(() => undefined);
                else { setUnlockedStage(2); setStage('speak'); }
                setRecording(false);
                setDemoPlaying(false);
              }}
              style={[styles.primaryPillButton, (!displayedReadPassed || trainingTransitioning) && styles.primaryPillDisabled]}
            >
              <Text style={styles.primaryPillText}>{isLastReadItem ? '进入模拟' : '下一句'}</Text>
              <AppIcon name="arrow-right" size={18} color={colors.white} />
            </Pressable>
          </View>
        </View>
      ) : null}

      {displayedStage === 'speak' ? (
        <View style={styles.sceneCallStage}>
          {scene ? (
            <SceneCallStage
              scene={scene}
              progressCollapsed={progressCollapsed}
              onComplete={(completion) => {
                setDialogueCompletion(completion);
                setCompletionOpen(true);
              }}
            />
          ) : (
            <CallExperience
              allowSubtitleToggle={false}
              compactTranscriptLayout
              onEnd={() => setCompletionOpen(true)}
              progressCollapsed={progressCollapsed}
              transcriptEnglish="Good morning! What can I get started for you today?"
              transcriptChinese="早上好！今天想点些什么？"
            />
          )}
        </View>
      ) : null}
    </AppScreen>
    {readFeedbackOpen ? (
      <View style={styles.scoreModalBackdrop}>
        <View style={styles.scoreModal}>
          <View style={styles.scoreModalValueRow}><Text style={styles.scoreModalValue}>{Math.round(readingResult?.overallScore ?? 86)}</Text><Text style={styles.scoreModalMax}>/100</Text></View>
          <Text style={styles.scoreModalTitle}>本句发音评估</Text>
          <Text style={styles.scoreModalLead}>{readingResult?.passed ? `本句已达到通过标准，可以${isLastReadItem ? '进入模拟' : '进入下一句'}；低分词仍可继续练习。` : '本句尚未达到通过标准，请根据逐词结果再次朗读。'}</Text>
          <View style={styles.scoreFocusCard}>
            <Text style={styles.scoreFocusLabel}>逐词结果</Text>
            <Text style={styles.scoreFocusSentence}>
              {readingResult?.words.length
                ? readingResult.words.map((word, index) => (
                    <Text key={`${word.word}-${index}`} style={word.wordScore >= 80 ? styles.scoreCorrect : styles.scoreIncorrect}>{word.word}{index < readingResult.words.length - 1 ? ' ' : ''}</Text>
                  ))
                : <Text style={styles.scoreCorrect}>{readItem.en}</Text>}
            </Text>
          </View>
          <Pressable accessibilityRole="button" onPress={() => setReadFeedbackOpen(false)} style={styles.scoreConfirmButton}>
            <Text style={styles.scoreConfirmText}>知道了</Text>
          </Pressable>
        </View>
      </View>
    ) : null}
    {completionOpen ? (
      <View style={styles.completionBackdrop}>
        <View accessibilityRole="summary" style={styles.completionModal}>
          <View style={styles.completionHeader}>
            <View style={styles.completionHeaderCopy}>
              <Text style={styles.completionEyebrow}>SIMULATION COMPLETE</Text>
              <Text style={styles.completionTitle}>模拟完成</Text>
              <Text style={styles.completionLead}>本次场景对话已结束，下面是你的五维表现。</Text>
            </View>
            <View style={styles.completionScoreRow}>
              <Text style={styles.completionScore}>{Math.round(dialogueCompletion?.evaluation?.finalScore ?? 86)}</Text>
              <Text style={styles.completionScoreMax}>/100</Text>
            </View>
          </View>
          <View style={styles.completionOverview}>
            <SceneRadar metrics={completionMetrics} />
            <View style={styles.completionMetrics}>
              {completionMetrics.map((metric) => (
                <View key={metric.label} style={styles.completionMetricRow}>
                  <Text style={styles.completionMetricLabel}>{metric.label}</Text>
                  <Text style={styles.completionMetricValue}>{Math.round(metric.value)}</Text>
                </View>
              ))}
            </View>
          </View>
          <View style={styles.completionActions}>
            <Pressable accessibilityRole="button" onPress={finishTraining} style={[styles.completionActionButton, styles.completionBackButton]}>
              <Text style={styles.completionBackText}>返回场景广场</Text>
            </Pressable>
            <Pressable accessibilityRole="button" onPress={viewTrainingDetails} style={[styles.completionActionButton, styles.completionDetailsButton]}>
              <Text style={styles.completionDetailsText}>查看详情</Text>
              <AppIcon name="arrow-right" size={18} color={colors.white} />
            </Pressable>
          </View>
        </View>
      </View>
    ) : null}
    </View>
  );
}

function createDefaultSceneService() {
  const tokenStore = new SecureTokenStore();
  return new SceneService(
    new ApiClient({
      baseUrl: getRuntimeConfig().backendUrl,
      tokenStore,
    }),
  );
}

function createDefaultTtsPlayer() {
  const tokenStore = new SecureTokenStore();
  return new TtsPlayer({
    speechClient: new SceneSpeechClient({
      baseUrl: getRuntimeConfig().backendUrl,
      tokenStore,
    }),
  });
}

function ScenePromptInput({
  value,
  onChangeText,
  placeholder,
  placeholderTextColor,
}: {
  value: string;
  onChangeText: (value: string) => void;
  placeholder: string;
  placeholderTextColor: string;
}) {
  const [focused, setFocused] = useState(false);
  const hasInput = value.length > 0;
  const active = focused || hasInput;
  const rotation = useSharedValue(0);

  useEffect(() => {
    cancelAnimation(rotation);
    if (active) {
      rotation.value = 0;
      return;
    }
    rotation.value = withRepeat(
      withTiming(360, {
        duration: 8500,
        easing: ReanimatedEasing.linear,
      }),
      -1,
      false,
    );
    return () => cancelAnimation(rotation);
  }, [active, rotation]);

  const gradientStyle = useAnimatedStyle(() => ({
    transform: [{ rotate: `${rotation.value}deg` }],
  }));

  return (
    <View style={[styles.builderInputFrame, active && styles.builderInputFrameFocused]}>
      {!active ? (
        <Reanimated.View pointerEvents="none" style={[styles.builderInputGradientLayer, gradientStyle]}>
          <LinearGradient
            colors={['#DCE3ED', '#BFD9F8', '#EAE4F8', '#C8E0FA', '#DCE3ED']}
            end={{ x: 1, y: 1 }}
            start={{ x: 0, y: 0 }}
            style={styles.builderInputGradient}
          />
        </Reanimated.View>
      ) : null}
      <TextInput
        accessibilityLabel="描述想练习的场景"
        multiline
        maxLength={200}
        onBlur={() => setFocused(false)}
        onChangeText={onChangeText}
        onFocus={() => setFocused(true)}
        placeholder={placeholder}
        placeholderTextColor={placeholderTextColor}
        style={styles.builderInput}
        textAlignVertical="top"
        value={value}
      />
    </View>
  );
}

export function ScenesHome({
  onOpen,
  onStartScene,
  promptExample = getDailyScenePromptExample(),
  sceneService: injectedSceneService,
}: {
  onOpen: (route: SceneRoute) => void;
  onStartScene?: () => void;
  promptExample?: ScenePromptExample;
  sceneService?: Pick<SceneService, 'generate'>;
}) {
  const [sceneService] = useState(
    () => injectedSceneService ?? createDefaultSceneService(),
  );
  const [prompt, setPrompt] = useState('');
  const [preview, setPreview] = useState<GeneratedScene | null>(null);
  const [previewDisplay, setPreviewDisplay] = useState<Partial<GeneratedScene> | null>(null);
  const [translationApi] = useState(createTranscriptTranslationApi);
  const [generatingSource, setGeneratingSource] = useState<'custom' | string | null>(null);
  const [generationError, setGenerationError] = useState<string | null>(null);
  const generating = generatingSource !== null;
  const generatePreview = async (sceneInput: string, source: 'custom' | string) => {
    if (!sceneInput.trim() || generating) return;
    setGeneratingSource(source);
    setGenerationError(null);
    try {
      const scene = await sceneService.generate(sceneInput.trim());
      setPreview(scene);
      setPreviewDisplay(null);
      const translate = async (value: string, maxLength: number) => {
        const source = String(value ?? '').trim();
        if (!source || !/[A-Za-z]/.test(source)) return source;
        try {
          const translated = await translationApi.translateScene(scene.sceneId, source);
          return String(translated || source).slice(0, maxLength);
        } catch {
          return source.slice(0, maxLength);
        }
      };
      const display = await Promise.all([
        translate(scene.title, 18),
        translate(scene.background, 58),
        translate(scene.aiRole, 22),
        translate(scene.userRole, 22),
        translate(scene.learningGoal, 42),
      ]);
      setPreviewDisplay({
        title: display[0],
        background: display[1],
        aiRole: display[2],
        userRole: display[3],
        learningGoal: display[4],
      });
    } catch (error) {
      setPreview(null);
      setGenerationError(
        error instanceof Error ? error.message : '场景生成失败，请重试',
      );
    } finally {
      setGeneratingSource(null);
    }
  };

  return (
    <View style={[styles.sceneHomeRoot, preview && styles.sceneHomeRootModal]}>
      <AppScreen
        contentStyle={styles.sceneHomeContent}
        fixedHeader={<MainModuleHeader englishTitle="SCENARIO MARKETPLACE" title="场景广场" />}
      >
        <Text style={styles.sceneHeadingSubtitle}>把真实生活中的需求，变成高质量的口语练习。</Text>

        <View style={styles.customBuilder}>
          <LinearGradient
            colors={['#F4F8FF', '#FBF9FF']}
            end={{ x: 1, y: 0 }}
            pointerEvents="none"
            start={{ x: 0, y: 0 }}
            style={styles.customBuilderTint}
          />
          <View style={styles.builderTopRow}>
            <View style={styles.builderHeadingCopy}>
              <Text style={[styles.builderEyebrow, styles.customBuilderEyebrow]}>CREATE YOUR OWN</Text>
              <Text style={styles.builderTitle}>创建专属场景</Text>
            </View>
          </View>
          <ScenePromptInput
            onChangeText={setPrompt}
            placeholder={`你今天想练习什么？例如：${promptExample.prompt}`}
            placeholderTextColor="#8D8D88"
            value={prompt}
          />
          <View style={styles.builderFooter}>
            <Text style={styles.characterCount}>{prompt.length}/200</Text>
            <Pressable
              accessibilityRole="button"
              accessibilityLabel="生成练习场景"
              disabled={!prompt.trim() || generating}
              onPress={() => void generatePreview(prompt, 'custom')}
              style={({ pressed }) => [
                styles.generateButton,
                prompt.trim() && !generating ? styles.generateButtonReady : styles.generateButtonDisabled,
                pressed && prompt.trim() && !generating && styles.generateButtonPressed,
              ]}
            >
              <Text style={[styles.generateButtonText, prompt.trim() && !generating ? styles.generateButtonTextActive : styles.generateButtonTextDisabled]}>{generatingSource === 'custom' ? '正在生成…' : '生成练习场景'}</Text>
              <AppIcon name="arrow-right" size={18} color={prompt.trim() && !generating ? colors.white : '#7896B8'} />
            </Pressable>
          </View>
        </View>

        <View style={styles.specialtySection}>
          <View style={styles.specialtySectionHeader}>
            <Text style={styles.builderEyebrow}>SPECIALTY TRAINING</Text>
            <Text style={styles.recommendationHeading}>专项训练</Text>
          </View>
          <View style={styles.specialtyOptions}>
            <Pressable
              accessibilityRole="button"
              accessibilityLabel="进入雅思口语"
              onPress={() => onOpen({ name: 'ielts' })}
              style={({ pressed }) => [
                styles.specialtyOption,
                styles.specialtyIeltsOption,
                pressed && styles.specialtyIeltsOptionPressed,
              ]}
            >
              <Image source={require('../../assets/images/specialty/ielts.png')} style={[styles.specialtyOptionIcon, styles.specialtyIeltsIcon]} />
              <View style={styles.specialtyOptionCopy}>
                <Text style={[styles.specialtyOptionTitle, styles.specialtyIeltsTitle]}>雅思口语</Text>
                <Text numberOfLines={1} style={[styles.specialtyOptionNote, styles.specialtyIeltsNote]}>模考与评分</Text>
              </View>
            </Pressable>
            <Pressable
              accessibilityRole="button"
              accessibilityLabel="进入英文面试"
              onPress={() => onOpen({ name: 'interview' })}
              style={({ pressed }) => [
                styles.specialtyOption,
                styles.specialtyInterviewOption,
                pressed && styles.specialtyInterviewOptionPressed,
              ]}
            >
              <Image source={require('../../assets/images/specialty/interview.png')} style={[styles.specialtyOptionIcon, styles.specialtyInterviewIcon]} />
              <View style={styles.specialtyOptionCopy}>
                <Text style={[styles.specialtyOptionTitle, styles.specialtyInterviewTitle]}>英文面试</Text>
                <Text numberOfLines={1} style={[styles.specialtyOptionNote, styles.specialtyInterviewNote]}>岗位模拟追问</Text>
              </View>
            </Pressable>
          </View>
          {generationError ? <Text style={styles.generationError}>{generationError}</Text> : null}
        </View>

        <View style={styles.recommendationHeader}>
          <View>
            <Text style={styles.builderEyebrow}>DAILY PICKS</Text>
            <Text style={styles.recommendationHeading}>每日推荐</Text>
          </View>
        </View>
        <View style={styles.recommendationList}>
          {recommendations.map((item) => (
            <Pressable
              accessibilityRole="button"
              accessibilityLabel={`生成每日推荐：${item.title}`}
              key={item.id}
              disabled={generating}
              onPress={() => void generatePreview(`${item.title}：${item.goal}`, item.id)}
              style={({ pressed }) => [styles.recommendation, pressed && styles.compactPressed]}
            >
              <View style={styles.recommendationCopy}>
                <View style={styles.recommendationTitleRow}>
                  <Text style={styles.recommendationTitle}>{item.title}</Text>
                  <SceneCategoryTag category={item.category} subtle />
                </View>
                <Text numberOfLines={1} style={styles.recommendationMeta}>{item.goal} · {item.duration}</Text>
              </View>
              <View style={styles.recommendationArrow}>
              {generatingSource === item.id
                ? <ActivityIndicator accessibilityLabel="正在生成推荐场景" color={colors.white} size="small" />
                : <AppIcon name="arrow-right" size={17} color={colors.white} />}
              </View>
            </Pressable>
          ))}
        </View>
      </AppScreen>

      {preview ? (
        <View style={styles.previewBackdrop}>
          <View style={styles.previewModal}>
            <Pressable accessibilityRole="button" accessibilityLabel="关闭场景确认" onPress={() => setPreview(null)} style={styles.previewClose}>
              <AppIcon name="close" size={21} />
            </Pressable>
            <Text style={styles.previewEyebrow}>场景已准备好</Text>
            <View style={styles.previewTitleRow}>
            <Text style={styles.previewTitle}>{previewDisplay?.title || preview.title}</Text>
              <SceneCategoryTag category={sceneCategoryForLabel(preview.label)} />
            </View>
            <Text style={styles.previewLead}>场景已生成，确认后即可开始练习。</Text>
            <View style={styles.previewSummary}>
              {[
                ['场景简介', previewDisplay?.background || preview.background],
                ['AI 扮演', previewDisplay?.aiRole || preview.aiRole],
                ['你将扮演', previewDisplay?.userRole || preview.userRole],
                ['练习重点', previewDisplay?.learningGoal || preview.learningGoal],
                ['预计用时', `${preview.estimatedMinutes} 分钟`],
              ].map(([label, value]) => (
                <View key={label} style={styles.previewSummaryRow}>
                  <Text style={styles.previewSummaryLabel}>{label}</Text>
                  <Text style={styles.previewSummaryValue}>{value}</Text>
                </View>
              ))}
            </View>
            <View style={styles.previewActions}>
              <Pressable accessibilityRole="button" onPress={() => setPreview(null)} style={[styles.previewButton, styles.previewButtonSecondary]}>
                <Text style={styles.previewButtonSecondaryText}>返回修改</Text>
              </Pressable>
              <Pressable
                accessibilityRole="button"
                onPress={() => {
                  onStartScene?.();
                  onOpen({ name: 'training', scene: preview });
                }}
                style={[styles.previewButton, styles.previewButtonPrimary]}
              >
                <Text style={styles.previewButtonPrimaryText}>开始练习</Text>
                <AppIcon name="arrow-right" size={18} color={colors.white} />
              </Pressable>
            </View>
          </View>
        </View>
      ) : null}
    </View>
  );
}

export function ScenesScreen({
  onIeltsViewDetails,
  onSceneViewDetails,
  onOpenIelts,
  onOpenInterview,
  onStartScene,
}: {
  onIeltsViewDetails?: (recordId: string) => void;
  onSceneViewDetails?: (sceneId: string) => void;
  onOpenIelts?: () => void;
  onOpenInterview?: () => void;
  onStartScene?: () => void;
} = {}) {
  const [route, setRoute] = useState<SceneRoute>({ name: 'home' });
  const openRoute = (nextRoute: SceneRoute) => {
    if (nextRoute.name === 'ielts' && onOpenIelts) {
      onOpenIelts();
      return;
    }
    if (nextRoute.name === 'interview' && onOpenInterview) {
      onOpenInterview();
      return;
    }
    setRoute(nextRoute);
  };
  useEffect(() => {
    if (route.name === 'home') void forgetSpecialty();
  }, [route.name]);
  if (route.name === 'training') {
    return <Training scene={route.scene} onBack={() => setRoute({ name: 'home' })} onFinish={() => setRoute({ name: 'home' })} onViewDetails={onSceneViewDetails} />;
  }
  if (route.name === 'ielts') return <IeltsFlow onExit={() => setRoute({ name: 'home' })} onViewDetails={onIeltsViewDetails} />;
  if (route.name === 'interview') return <InterviewFlow onExit={() => setRoute({ name: 'home' })} />;
  return <ScenesHome onOpen={openRoute} onStartScene={onStartScene} />;
}

const styles = StyleSheet.create({
  sceneHomeRoot: { flex: 1, backgroundColor: colors.canvas },
  sceneHomeRootModal: { position: 'relative', zIndex: 100, elevation: 100 },
  sceneHomeContent: { paddingHorizontal: 18, paddingBottom: 82, gap: 0 },
  sceneHeadingSubtitle: { marginTop: 8, marginBottom: 14, color: colors.muted, fontSize: 15, lineHeight: 23, fontWeight: '300' },
  customBuilder: {
    zIndex: 2,
    height: 254,
    padding: 15,
    borderWidth: 1,
    borderColor: '#DDE7F7',
    borderRadius: 22,
    backgroundColor: colors.white,
    shadowColor: '#5B8DEF',
    shadowOffset: { width: 0, height: 5 },
    shadowOpacity: 0.1,
    shadowRadius: 18,
    elevation: 3,
    boxShadow: '0px 7px 22px rgba(91, 141, 239, 0.1)',
  },
  customBuilderTint: {
    position: 'absolute',
    top: 0,
    right: 0,
    left: 0,
    height: 82,
    borderTopLeftRadius: 21,
    borderTopRightRadius: 21,
  },
  builderTopRow: { flexDirection: 'row', alignItems: 'flex-start', justifyContent: 'space-between', gap: 12 },
  builderHeadingCopy: { flex: 1, gap: 2 },
  builderEyebrow: { color: colors.subtle, fontSize: 10, fontWeight: '600', letterSpacing: 1.6 },
  customBuilderEyebrow: { color: '#5B8DEF' },
  builderTitle: { color: colors.ink, fontSize: 23, lineHeight: 29, fontWeight: '600', letterSpacing: -0.7 },
  builderInput: {
    flex: 1,
    margin: 2,
    position: 'relative',
    zIndex: 1,
    paddingHorizontal: 13,
    paddingVertical: 11,
    color: colors.ink,
    fontSize: 15,
    lineHeight: 22,
    fontWeight: '300',
    outlineWidth: 0,
    borderRadius: 14,
    backgroundColor: colors.white,
  },
  builderInputFrame: {
    height: 120,
    marginTop: 11,
    position: 'relative',
    overflow: 'hidden',
    borderWidth: 1,
    borderColor: '#DCE3ED',
    borderRadius: 15,
    backgroundColor: colors.white,
  },
  builderInputFrameFocused: {
    borderColor: '#6EA8FF',
    shadowColor: '#5B8DEF',
    shadowOffset: { width: 0, height: 0 },
    shadowOpacity: 0.14,
    shadowRadius: 10,
    elevation: 3,
    boxShadow: '0px 0px 12px rgba(91, 141, 239, 0.14)',
  },
  builderInputGradientLayer: {
    position: 'absolute',
    zIndex: 0,
    top: '-42%',
    right: '-42%',
    bottom: '-42%',
    left: '-42%',
  },
  builderInputGradient: { flex: 1 },
  builderFooter: { marginTop: 14, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  characterCount: { color: colors.subtle, fontSize: 12, fontWeight: '300' },
  generateButton: {
    height: 44,
    paddingHorizontal: 17,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 7,
    borderRadius: 22,
    backgroundColor: '#DCEBFA',
  },
  generateButtonDisabled: { backgroundColor: '#DCEBFA' },
  generateButtonReady: { backgroundColor: '#5B8DEF' },
  generateButtonPressed: { backgroundColor: '#356FD6', transform: [{ scale: 0.98 }] },
  generateButtonText: { fontSize: 14, fontWeight: '600' },
  generateButtonTextActive: { color: colors.white },
  generateButtonTextDisabled: { color: '#7896B8' },
  generationError: { marginTop: 6, color: '#B94D44', fontSize: 11, lineHeight: 16 },
  compactPressed: { opacity: 0.72, transform: [{ scale: 0.98 }] },
  specialtySection: {
    marginTop: 22,
  },
  specialtySectionHeader: { marginBottom: 12, paddingHorizontal: 15 },
  specialtyOptions: { flexDirection: 'row', gap: 9 },
  specialtyOption: {
    minWidth: 0,
    minHeight: 88,
    paddingHorizontal: 10,
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 9,
    borderWidth: 1,
    borderColor: colors.line,
    borderRadius: 15,
    backgroundColor: colors.white,
    shadowColor: '#1A1A18',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.04,
    shadowRadius: 11,
    elevation: 2,
    boxShadow: '0px 4px 13px rgba(21, 21, 20, 0.04)',
  },
  specialtyIeltsOption: { borderColor: '#E5DCF9', backgroundColor: '#FBF9FF' },
  specialtyIeltsOptionPressed: { borderColor: '#8B6FE8' },
  specialtyInterviewOption: { borderColor: '#D9E8FA', backgroundColor: '#F7FBFF' },
  specialtyInterviewOptionPressed: { borderColor: '#5B8DEF' },
  specialtyOptionIcon: { width: 58, height: 58, flexShrink: 0, borderRadius: 13, opacity: 0.88 },
  specialtyIeltsIcon: { backgroundColor: '#E8DEFF' },
  specialtyInterviewIcon: { backgroundColor: '#DCEEFF' },
  specialtyOptionCopy: { minWidth: 0, flex: 1 },
  specialtyOptionTitle: { color: colors.ink, fontSize: 19, lineHeight: 24, fontWeight: '600' },
  specialtyOptionNote: { marginTop: 4, color: colors.subtle, fontSize: 12, lineHeight: 17, fontWeight: '300' },
  specialtyIeltsTitle: { color: '#292331' },
  specialtyIeltsNote: { color: '#8C8498' },
  specialtyInterviewTitle: { color: '#202833' },
  specialtyInterviewNote: { color: '#83909E' },
  recommendationHeader: { marginTop: 22, marginBottom: 9, paddingHorizontal: 15, flexDirection: 'row', alignItems: 'flex-end', justifyContent: 'space-between' },
  recommendationHeading: { marginTop: 3, color: colors.ink, fontSize: 23, fontWeight: '600', letterSpacing: -0.7 },
  recommendationList: { gap: 8 },
  recommendation: {
    minHeight: 70,
    paddingHorizontal: 13,
    paddingVertical: 9,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 11,
    borderWidth: 1,
    borderColor: colors.line,
    borderRadius: 15,
    backgroundColor: colors.white,
    shadowColor: '#1A1A18',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.04,
    shadowRadius: 11,
    elevation: 2,
    boxShadow: '0px 4px 13px rgba(21, 21, 20, 0.04)',
  },
  recommendationCopy: { flex: 1, gap: 4 },
  recommendationTitleRow: { flexDirection: 'row', alignItems: 'center', gap: 7 },
  recommendationTitle: { color: colors.ink, fontSize: 17, fontWeight: '600' },
  recommendationMeta: { color: colors.muted, fontSize: 12, fontWeight: '300' },
  recommendationArrow: { width: 35, height: 35, alignItems: 'center', justifyContent: 'center', borderRadius: 18, backgroundColor: '#151514' },
  previewBackdrop: {
    position: 'absolute',
    zIndex: 100,
    top: 0,
    right: 0,
    bottom: 0,
    left: 0,
    paddingHorizontal: 18,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(20,20,19,0.38)',
  },
  previewModal: {
    width: '100%',
    maxWidth: 414,
    padding: 24,
    borderWidth: 1,
    borderColor: '#E1E1DC',
    borderRadius: 22,
    backgroundColor: colors.white,
    shadowColor: '#000000',
    shadowOffset: { width: 0, height: 24 },
    shadowOpacity: 0.2,
    shadowRadius: 38,
    elevation: 18,
  },
  previewClose: { position: 'absolute', zIndex: 2, top: 14, right: 14, width: 38, height: 38, alignItems: 'center', justifyContent: 'center', borderRadius: 19, backgroundColor: colors.soft },
  previewEyebrow: { paddingRight: 44, color: colors.subtle, fontSize: 10, fontWeight: '600', letterSpacing: 1.5 },
  previewTitleRow: { marginTop: 12, paddingRight: 44, flexDirection: 'row', alignItems: 'center', gap: 5 },
  previewTitle: { flexShrink: 1, color: colors.ink, fontSize: 28, lineHeight: 35, fontWeight: '600', letterSpacing: -0.9 },
  previewLead: { marginTop: 12, color: colors.muted, fontSize: 14, lineHeight: 21, fontWeight: '300' },
  previewSummary: { marginTop: 20, borderTopWidth: 1, borderTopColor: colors.line },
  previewSummaryRow: { minHeight: 56, paddingVertical: 12, flexDirection: 'row', alignItems: 'flex-start', gap: 16, borderBottomWidth: 1, borderBottomColor: colors.line },
  previewSummaryLabel: { width: 82, color: colors.muted, fontSize: 13, lineHeight: 21, fontWeight: '300' },
  previewSummaryValue: { flex: 1, color: colors.ink, fontSize: 14, lineHeight: 21, fontWeight: '500' },
  previewActions: { marginTop: 20, flexDirection: 'row', justifyContent: 'flex-end', gap: 9 },
  previewButton: { height: 46, paddingHorizontal: 17, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8, borderWidth: 1, borderRadius: 13 },
  previewButtonSecondary: { flex: 0.86, borderColor: colors.line, backgroundColor: colors.white },
  previewButtonPrimary: { flex: 1.14, borderColor: colors.ink, backgroundColor: colors.ink },
  previewButtonSecondaryText: { color: colors.ink, fontSize: 13, fontWeight: '600' },
  previewButtonPrimaryText: { color: colors.white, fontSize: 13, fontWeight: '600' },
  trainingRoot: { flex: 1, position: 'relative', backgroundColor: colors.white },
  trainingScreen: { paddingHorizontal: 18, paddingTop: 56, paddingBottom: 22, gap: 15 },
  trainingHeader: { minHeight: 48, flexDirection: 'row', alignItems: 'flex-start', justifyContent: 'space-between', gap: 13 },
  trainingHeaderCopy: { flex: 1 },
  trainingEyebrow: { color: colors.subtle, fontSize: 9, fontWeight: '600', letterSpacing: 1.5 },
  trainingTitle: { marginTop: 2, color: colors.ink, fontSize: 23, lineHeight: 28, fontWeight: '600', letterSpacing: -0.7 },
  trainingSubtitle: { marginTop: 1, color: colors.muted, fontSize: 11, fontWeight: '300' },
  trainingCancel: { width: 38, height: 38, alignItems: 'center', justifyContent: 'center', borderRadius: 19, backgroundColor: colors.soft },
  stepperViewport: { height: 82, marginRight: -18, position: 'relative', overflow: 'hidden' },
  learningStepper: { height: 82, flexDirection: 'row', alignItems: 'flex-start', justifyContent: 'space-between', position: 'relative' },
  stepperLine: { position: 'absolute', top: 18, left: 39, right: 0, height: 2, borderRadius: 1, backgroundColor: '#E4E4E0' },
  stepperProgress: { position: 'absolute', top: 18, left: 39, height: 2, borderRadius: 1, backgroundColor: colors.ink },
  collapsedStep: { position: 'absolute', top: 0, right: 0 },
  collapsedStepButton: { width: 42, height: 42, alignItems: 'center', justifyContent: 'center', borderWidth: 1, borderColor: colors.ink, borderTopLeftRadius: 21, borderBottomLeftRadius: 21, backgroundColor: colors.ink },
  collapsedStepText: { color: colors.white, fontSize: 14, fontWeight: '600' },
  stepItem: { zIndex: 1, width: 78, alignItems: 'center' },
  stepDot: { width: 38, height: 38, alignItems: 'center', justifyContent: 'center', borderWidth: 1, borderColor: colors.line, borderRadius: 19, backgroundColor: colors.white },
  stepDotActive: { borderColor: colors.ink, backgroundColor: colors.ink },
  stepDotCurrent: { shadowColor: '#111111', shadowOffset: { width: 0, height: 3 }, shadowOpacity: 0.12, shadowRadius: 7, elevation: 3 },
  stepNumber: { color: colors.subtle, fontSize: 11, fontWeight: '600' },
  stepNumberActive: { color: colors.white },
  stepLabel: { marginTop: 5, color: colors.subtle, fontSize: 14, fontWeight: '600' },
  stepLabelActive: { color: colors.ink },
  stepNote: { marginTop: 1, color: colors.subtle, fontSize: 8, fontWeight: '300' },
  stageShell: { flex: 1, minHeight: 0, gap: 12 },
  stageMetaRow: { minHeight: 34, flexDirection: 'row', alignItems: 'flex-end', justifyContent: 'space-between' },
  stageHeading: { marginTop: 3, color: colors.ink, fontSize: 20, lineHeight: 25, fontWeight: '600', letterSpacing: -0.5 },
  stageCount: { color: colors.subtle, fontSize: 11, fontWeight: '500' },
  languageCard: { flex: 1, minHeight: 250, position: 'relative', paddingHorizontal: 24, alignItems: 'center', justifyContent: 'center', borderWidth: 1, borderColor: colors.line, borderRadius: 22, backgroundColor: colors.white },
  languageCount: { position: 'absolute', top: 17, right: 18, color: colors.subtle, fontSize: 11, fontWeight: '500' },
  languageType: { color: colors.subtle, fontSize: 10, fontWeight: '600', letterSpacing: 1.4 },
  languageEnglish: { marginTop: 14, color: colors.ink, fontSize: 34, lineHeight: 40, fontWeight: '600', textAlign: 'center', letterSpacing: -1.2 },
  pronunciationRow: { minHeight: 34, marginTop: 14, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 10 },
  phoneticText: { color: colors.muted, fontSize: 16, lineHeight: 24, fontWeight: '300' },
  speakerButton: { width: 32, height: 32, alignItems: 'center', justifyContent: 'center', borderRadius: 16 },
  speakerButtonActive: { opacity: 0.58, transform: [{ scale: 0.94 }] },
  languageChinese: { marginTop: 12, color: colors.muted, fontSize: 16, lineHeight: 24, fontWeight: '300', textAlign: 'center' },
  stageFooterRow: { minHeight: 52, paddingTop: 10, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: colors.line },
  roundNavButton: { width: 46, height: 46, alignItems: 'center', justifyContent: 'center', borderWidth: 1, borderColor: colors.line, borderRadius: 23, backgroundColor: colors.white },
  roundNavDisabled: { opacity: 0.28 },
  primaryPillButton: { minWidth: 132, height: 46, paddingHorizontal: 20, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8, borderRadius: 23, backgroundColor: colors.ink },
  primaryPillDisabled: { opacity: 0.28 },
  primaryPillText: { color: colors.white, fontSize: 14, fontWeight: '600' },
  readCard: { flex: 1, minHeight: 350, paddingHorizontal: 22, paddingVertical: 24, alignItems: 'center', justifyContent: 'center', borderWidth: 1, borderColor: colors.line, borderRadius: 22, backgroundColor: colors.white },
  scoreBadge: { position: 'absolute', top: 16, right: 17, flexDirection: 'row', alignItems: 'baseline' },
  scoreBadgeValue: { color: colors.ink, fontSize: 24, fontWeight: '600' },
  scoreBadgeMax: { color: colors.subtle, fontSize: 9, fontWeight: '500' },
  readSentence: { maxWidth: 350, color: colors.ink, fontSize: 29, lineHeight: 37, fontWeight: '600', textAlign: 'center', letterSpacing: -1 },
  readTranslation: { marginTop: 12, color: colors.muted, fontSize: 14, fontWeight: '300', textAlign: 'center' },
  scoreCorrect: { color: '#278B5B' },
  scoreIncorrect: { color: '#D65349' },
  readRecordButton: { width: 62, height: 62, marginTop: 28, alignItems: 'center', justifyContent: 'center', borderWidth: 1, borderColor: colors.line, borderRadius: 31, backgroundColor: colors.white },
  readRecordButtonActive: { borderColor: '#E2AAA5', backgroundColor: '#FFF7F6', shadowColor: '#C75950', shadowOffset: { width: 0, height: 2 }, shadowOpacity: 0.12, shadowRadius: 10, elevation: 3 },
  readStatus: { marginTop: 15, color: colors.ink, fontSize: 15, fontWeight: '600' },
  readInstruction: { marginTop: 7, color: colors.muted, fontSize: 11, lineHeight: 17, fontWeight: '300', textAlign: 'center' },
  readDemoButton: { minHeight: 36, marginTop: 18, paddingHorizontal: 10, flexDirection: 'row', alignItems: 'center', gap: 7 },
  readDemoText: { color: colors.subtle, fontSize: 12, fontWeight: '500' },
  sceneCallStage: { flex: 1, minHeight: 0, marginHorizontal: -2 },
  sceneCallExperience: { flex: 1, position: 'relative' },
  scoreModalBackdrop: { position: 'absolute', zIndex: 200, top: 0, right: 0, bottom: 0, left: 0, paddingHorizontal: 18, alignItems: 'center', justifyContent: 'center', backgroundColor: 'rgba(20,20,19,0.32)' },
  scoreModal: { width: '100%', maxWidth: 420, padding: 24, borderRadius: 24, backgroundColor: colors.white, shadowColor: '#000000', shadowOffset: { width: 0, height: 24 }, shadowOpacity: 0.2, shadowRadius: 36, elevation: 18 },
  scoreModalValueRow: { flexDirection: 'row', alignItems: 'flex-end' },
  scoreModalValue: { color: colors.ink, fontSize: 74, lineHeight: 74, fontWeight: '600', letterSpacing: -4 },
  scoreModalMax: { marginBottom: 8, marginLeft: 5, color: colors.subtle, fontSize: 17, fontWeight: '600' },
  scoreModalTitle: { marginTop: 22, color: colors.ink, fontSize: 27, lineHeight: 34, fontWeight: '600', letterSpacing: -0.8 },
  scoreModalLead: { marginTop: 12, color: colors.muted, fontSize: 14, lineHeight: 22, fontWeight: '300' },
  scoreFocusCard: { marginTop: 22, padding: 18, borderWidth: 1, borderColor: colors.line, borderRadius: 17, backgroundColor: '#FDFDFC' },
  scoreFocusLabel: { color: colors.subtle, fontSize: 11, fontWeight: '600' },
  scoreFocusSentence: { marginTop: 13, color: colors.ink, fontSize: 20, lineHeight: 29, fontWeight: '600' },
  scoreConfirmButton: { minWidth: 102, height: 44, marginTop: 22, paddingHorizontal: 22, alignSelf: 'flex-end', alignItems: 'center', justifyContent: 'center', borderWidth: 1, borderColor: colors.line, borderRadius: 22, backgroundColor: colors.white },
  scoreConfirmText: { color: colors.ink, fontSize: 14, fontWeight: '600' },
  completionBackdrop: { position: 'absolute', zIndex: 300, top: 0, right: 0, bottom: 0, left: 0, paddingHorizontal: 16, alignItems: 'center', justifyContent: 'center', backgroundColor: 'rgba(20,20,19,0.36)' },
  completionModal: { width: '100%', maxWidth: 420, padding: 22, borderWidth: 1, borderColor: '#E1E1DC', borderRadius: 24, backgroundColor: colors.white, shadowColor: '#000', shadowOffset: { width: 0, height: 24 }, shadowOpacity: 0.2, shadowRadius: 36, elevation: 18 },
  completionHeader: { flexDirection: 'row', alignItems: 'flex-start', gap: 12, paddingBottom: 18, borderBottomWidth: 1, borderBottomColor: colors.line },
  completionHeaderCopy: { flex: 1 },
  completionEyebrow: { color: colors.subtle, fontSize: 9, fontWeight: '600', letterSpacing: 1.5 },
  completionTitle: { marginTop: 8, color: colors.ink, fontSize: 27, lineHeight: 34, fontWeight: '600', letterSpacing: -0.8 },
  completionLead: { marginTop: 8, color: colors.muted, fontSize: 12, lineHeight: 18, fontWeight: '300' },
  completionScoreRow: { flexDirection: 'row', alignItems: 'flex-end' },
  completionScore: { color: colors.ink, fontSize: 52, lineHeight: 52, fontWeight: '600', letterSpacing: -3 },
  completionScoreMax: { marginBottom: 5, marginLeft: 3, color: colors.subtle, fontSize: 12, fontWeight: '500' },
  completionOverview: { paddingVertical: 14, flexDirection: 'row', alignItems: 'center', gap: 10 },
  completionMetrics: { flex: 1, borderTopWidth: 1, borderTopColor: colors.line },
  completionMetricRow: { minHeight: 35, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', borderBottomWidth: 1, borderBottomColor: colors.line },
  completionMetricLabel: { color: colors.muted, fontSize: 12, fontWeight: '300' },
  completionMetricValue: { color: colors.ink, fontSize: 15, fontWeight: '600' },
  completionActions: { flexDirection: 'row', gap: 10 },
  completionActionButton: { minWidth: 0, height: 48, paddingHorizontal: 14, flex: 1, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 7, borderWidth: 1, borderRadius: 24 },
  completionBackButton: { borderColor: colors.line, backgroundColor: colors.white },
  completionDetailsButton: { borderColor: colors.ink, backgroundColor: colors.ink },
  completionBackText: { color: colors.ink, fontSize: 13, fontWeight: '600' },
  completionDetailsText: { color: colors.white, fontSize: 13, fontWeight: '600' },
});
