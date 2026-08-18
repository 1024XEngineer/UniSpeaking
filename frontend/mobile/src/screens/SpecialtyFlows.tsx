import { Image } from 'expo-image';
import { useCallback, useEffect, useRef, useState } from 'react';
import { Alert, BackHandler, KeyboardAvoidingView, Platform, Pressable, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import {
  AppButton,
  AppIcon,
  AppScreen,
  Card,
  EvaluationPendingOverlay,
  HeaderIconButton,
  MainModuleHeader,
  ProgressBar,
  uiStyles,
} from '@/components/ui';
import { ieltsParts } from '@/data/content';
import { CallExperience, selectCallCaption } from '@/screens/ConversationScreen';
import { useIeltsFlowController } from '@/features/ielts/useIeltsFlowController';
import { useIeltsSession } from '@/features/ielts/useIeltsSession';
import { ieltsExaminers, toApiPart, type MobileIeltsPartId } from '@/features/ielts/ieltsMappings';
import { resolvePart2CueCard } from '@/features/ielts/part2CueCard';
import { IeltsPracticeScoreDialog } from '@/features/ielts/IeltsPracticeScoreDialog';
import type { IeltsTopicSummary } from '@/features/ielts/types';
import { compactPageNumbers } from '@/features/ielts/compactPagination';
import { createTranscriptTranslationApi } from '@/features/conversation/TranscriptTranslationApi';
import { InterviewReportView } from '@/features/interview/InterviewReportView';
import { type InterviewSessionApi } from '@/features/interview/InterviewSessionApi';
import { createInterviewApi, useInterviewSession } from '@/features/interview/useInterviewSession';
import { useInterviewPreparation, type InterviewDifficultyOption, type InterviewPreparationResult } from '@/features/interview/useInterviewPreparation';
import { useAppModel } from '@/model/AppModel';
import type { AnalyticsTrackerFactory } from '@/infrastructure/analytics/AnalyticsClient';
import { useLearningStage } from '@/navigation/learningStage';
import { rememberSpecialty } from '@/navigation/specialtyMemory';
import { colors, examinerAssets, ieltsAssets, interviewAssets, levels } from '@/theme/tokens';

type IeltsRoute =
  | 'intake'
  | 'home'
  | 'topics'
  | 'examiner'
  | 'session'
  | 'analysis'
  | 'report';

type IeltsPartId = MobileIeltsPartId;

const examiners = ieltsExaminers.map((item) => ({
  ...item,
  image: examinerAssets[item.id],
  description: item.id === 'daniel'
    ? '节奏稳定，追问清晰，适合提前熟悉正式考场氛围。'
    : item.id === 'marcus'
      ? '表达清楚直接，会用自然追问帮助你快速进入回答状态。'
      : item.id === 'margaret'
        ? '语速从容、停顿自然，适合练习完整展开与细节组织。'
        : '交流自然友好，同时保持严格的考试流程。',
}));

const ieltsPartOrder: readonly IeltsPartId[] = ['p1', 'p2', 'p3'];

function pickRandom<T>(items: readonly T[]) {
  return items[Math.floor(Math.random() * items.length)];
}

function randomExaminer() {
  return pickRandom(examiners);
}

const ieltsTargetOptions = [
  { id: '6.0', title: '目标 6.0', note: '优先保证回答完整、清楚' },
  { id: '6.5', title: '目标 6.5', note: '加强展开、连贯与词汇变化' },
  { id: '7.0', title: '目标 7.0', note: '提升自然度、准确性与表达深度' },
  { id: '7.5+', title: '目标 7.5+', note: '追求稳定、灵活且有层次的表达' },
] as const;

const ieltsPalette = {
  canvas: '#FCFAFF',
  paper: '#FFFFFF',
  border: '#E6DBFF',
  borderStrong: '#9874F2',
  purple: '#8060E8',
  purpleDark: '#5A3DBB',
  purpleSoft: '#F3EEFF',
  text: '#171323',
  muted: '#847D92',
} as const;

const interviewPalette = {
  canvas: '#DCEBFA',
  session: '#DCEBFA',
  paper: '#F7FBFF',
  paperStrong: '#EAF4FF',
  border: '#B9D3EC',
  borderStrong: '#4C91D6',
  accent: '#2875C8',
  accentBright: '#2D78C9',
  text: '#123255',
  muted: '#5D7896',
  subtle: '#7895B1',
} as const;

function IeltsSession({
  examiner,
  part,
  ieltsId,
  voiceId,
  onExit,
  autoAdvance,
  onFinish,
  analytics,
}: {
  examiner: (typeof examiners)[number];
  part: 'p1' | 'p3';
  ieltsId: string;
  voiceId: string;
  onExit: () => void;
  autoAdvance: boolean;
  analytics?: AnalyticsTrackerFactory;
  onFinish: (
    sessionId: string | null,
    ending: Promise<unknown>,
    turnEvaluations: Promise<void>,
  ) => void;
}) {
  const [trainingAnalytics] = useState(() => analytics?.training({
    mode: 'IELTS',
    pageCode: 'ielts-training',
  }));
  const session = useIeltsSession({ ieltsId, voiceId, part: toApiPart(part) }, trainingAnalytics);
  const partThreeTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const lastInputReadyTick = useRef(0);
  const finishingRef = useRef(false);
  const [translationApi] = useState(createTranscriptTranslationApi);
  const endSession = session.end;
  const caption = selectCallCaption(
    session.snapshot,
    examiner.name,
    session.statusLabel,
  );
  const dialogueState = session.snapshot.ieltsDialogueState;
  const progressLabel = dialogueState
    ? `${dialogueState.answeredQuestions} / ${dialogueState.totalQuestions} 题`
    : session.statusLabel;
  const translate = useCallback((text: string) => {
    if (!session.sessionId) return Promise.reject(new Error('会话尚未连接，暂时无法翻译'));
    return translationApi.translateFreeChat(session.sessionId, text);
  }, [session.sessionId, translationApi]);
  const confirmExit = () => {
    Alert.alert(
      '退出当前训练？',
      '退出后将返回场景广场。',
      [
        { text: '继续训练', style: 'cancel' },
        { text: '确认退出', style: 'destructive', onPress: onExit },
      ],
      { cancelable: true },
    );
  };

  useEffect(() => {
    if (part !== 'p3') return undefined;
    return () => {
      if (partThreeTimerRef.current) {
        clearInterval(partThreeTimerRef.current);
        partThreeTimerRef.current = null;
      }
    };
  }, [part]);

  useEffect(() => {
    if (part !== 'p3') return;
    const tick = session.snapshot.ieltsInputReadyTick ?? 0;
    if (tick <= lastInputReadyTick.current || session.snapshot.ieltsDialogueCompleted) return;
    lastInputReadyTick.current = tick;
    if (partThreeTimerRef.current) {
      clearInterval(partThreeTimerRef.current);
    }
    let remaining = 60;
    partThreeTimerRef.current = setInterval(() => {
      remaining -= 1;
      if (remaining > 0) return;
      if (partThreeTimerRef.current) {
        clearInterval(partThreeTimerRef.current);
        partThreeTimerRef.current = null;
      }
      void session.forcePart3Timeout().catch(() => undefined);
    }, 1000);
  }, [part, session, session.snapshot.ieltsDialogueCompleted, session.snapshot.ieltsInputReadyTick]);

  useEffect(() => {
    if (!session.snapshot.ieltsCompletionReady || finishingRef.current) return;
    if (partThreeTimerRef.current) {
      clearInterval(partThreeTimerRef.current);
      partThreeTimerRef.current = null;
    }
    finishingRef.current = true;
    const ending = endSession();
    onFinish(
      session.sessionId,
      ending,
      session.waitForTurnEvaluations(),
    );
  }, [endSession, onFinish, session, session.sessionId, session.snapshot.ieltsCompletionReady]);

  return (
    <SafeAreaView edges={['top', 'bottom']} style={styles.ieltsCallScreen}>
      <View style={styles.ieltsSessionHeader}>
        <HeaderIconButton accessibilityLabel="退出雅思训练" color={ieltsPalette.purpleDark} icon="close" onPress={confirmExit} />
      </View>
      <CallExperience
        endAccessibilityLabel="结束本题并进入下一题"
        endControlIcon="arrow"
        initialSubtitles={false}
        onEnd={() => {
          if (finishingRef.current) return;
          finishingRef.current = true;
          const ending = endSession();
          onFinish(session.sessionId, ending, session.waitForTurnEvaluations());
        }}
        participant={examiner}
        showEndControl={!autoAdvance}
        showMuteControl={false}
        onTranslate={translate}
        showUserTranscript={false}
        statusText={`${part === 'p1' ? 'Part 1' : 'Part 3'} · ${progressLabel}`}
        timerRunning={!session.snapshot.ieltsDialogueCompleted}
        transcriptEnglish={caption.text}
        transcriptSpeaker={caption.speaker}
        userTranscript={session.snapshot.userTranscript}
        transcriptHistory={session.snapshot.transcriptHistory}
      />
    </SafeAreaView>
  );
}


function formatSessionDuration(seconds: number) {
  const minutes = Math.floor(seconds / 60).toString().padStart(2, '0');
  const remainingSeconds = (seconds % 60).toString().padStart(2, '0');
  return `${minutes}:${remainingSeconds}`;
}

function compactPerformanceSummary(value: string | null | undefined) {
  const text = value?.trim();
  if (!text) return '已评分';
  const firstPhrase = text.split(/[。！？；,.!?;]/)[0]?.trim() || text;
  return firstPhrase.length > 6 ? `${firstPhrase.slice(0, 6)}…` : firstPhrase;
}

type Part2Phase = 'INTRODUCTION' | 'PREPARATION' | 'STARTING' | 'LONG_TURN' | 'FINISHING';

function IeltsPart2Session({
  examiner,
  cueCard,
  ieltsId,
  voiceId,
  onExit,
  onFinish,
  analytics,
}: {
  examiner: (typeof examiners)[number];
  cueCard: { title: string; points: string[] };
  ieltsId: string;
  voiceId: string;
  onExit: () => void;
  analytics?: AnalyticsTrackerFactory;
  onFinish: (
    sessionId: string | null,
    ending: Promise<unknown>,
    turnEvaluations: Promise<void>,
  ) => void;
}) {
  const [trainingAnalytics] = useState(() => analytics?.training({
    mode: 'IELTS',
    pageCode: 'ielts-training',
  }));
  const session = useIeltsSession({ ieltsId, voiceId, part: 'PART_2' }, trainingAnalytics);
  const [phase, setPhase] = useState<Part2Phase>('INTRODUCTION');
  const [prepRemaining, setPrepRemaining] = useState(60);
  const [longTurnRemaining, setLongTurnRemaining] = useState(120);
  const [notesLocked, setNotesLocked] = useState(false);
  const [note, setNote] = useState('');
  const preparationScrollRef = useRef<ScrollView>(null);
  const [sessionError, setSessionError] = useState<string | null>(null);
  const phaseRef = useRef<Part2Phase>('INTRODUCTION');
  const prevStateRef = useRef(session.snapshot.state);
  const prepTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const longTurnTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const silenceTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const finishTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const lastInputReadyTick = useRef(0);
  const confirmExit = () => {
    Alert.alert(
      '退出当前训练？',
      '退出后将返回场景广场。',
      [
        { text: '继续训练', style: 'cancel' },
        { text: '确认退出', style: 'destructive', onPress: onExit },
      ],
      { cancelable: true },
    );
  };

  useEffect(() => {
    phaseRef.current = phase;
  }, [phase]);

  const clearPrepTimer = () => {
    if (prepTimerRef.current) {
      clearInterval(prepTimerRef.current);
      prepTimerRef.current = null;
    }
  };

  const clearLongTurnTimer = () => {
    if (longTurnTimerRef.current) {
      clearInterval(longTurnTimerRef.current);
      longTurnTimerRef.current = null;
    }
  };

  const clearSilenceTimer = () => {
    if (silenceTimerRef.current) {
      clearTimeout(silenceTimerRef.current);
      silenceTimerRef.current = null;
    }
  };

  const clearFinishTimer = () => {
    if (finishTimerRef.current) {
      clearTimeout(finishTimerRef.current);
      finishTimerRef.current = null;
    }
  };

  const scheduleFinish = () => {
    clearFinishTimer();
    finishTimerRef.current = setTimeout(() => {
      const ending = session.end();
      onFinish(session.sessionId, ending, session.waitForTurnEvaluations());
    }, 1_800);
  };

  const runPrepTimer = (seconds: number) => {
    clearPrepTimer();
    let remaining = seconds;
    setPrepRemaining(remaining);
    prepTimerRef.current = setInterval(() => {
      remaining -= 1;
      setPrepRemaining(Math.max(0, remaining));
      if (remaining > 0) return;
      clearPrepTimer();
      beginPartTwoAnswer();
    }, 1000);
  };

  const runLongTurnTimer = (seconds: number) => {
    clearLongTurnTimer();
    let remaining = seconds;
    setLongTurnRemaining(remaining);
    longTurnTimerRef.current = setInterval(() => {
      remaining -= 1;
      setLongTurnRemaining(Math.max(0, remaining));
      if (remaining > 0) return;
      clearLongTurnTimer();
      finishPartTwoAtLimit();
    }, 1000);
  };

  const beginPartTwoAnswer = () => {
    if (phaseRef.current !== 'PREPARATION') return;
    clearPrepTimer();
    clearSilenceTimer();
    lastInputReadyTick.current = session.snapshot.ieltsInputReadyTick ?? 0;
    setNotesLocked(true);
    setPhase('STARTING');
    void session
      .transitionPart2('PREPARATION_COMPLETE')
      .catch((error: unknown) => {
        setSessionError(error instanceof Error ? error.message : '无法开始 Part 2 作答');
        setPhase('PREPARATION');
      });
  };

  const finishPartTwoAtLimit = () => {
    if (phaseRef.current !== 'LONG_TURN') return;
    clearLongTurnTimer();
    clearSilenceTimer();
    setPhase('FINISHING');
    void session
      .transitionPart2('LONG_TURN_TIME_LIMIT')
      .catch((error: unknown) => {
        setSessionError(error instanceof Error ? error.message : '无法结束 Part 2');
      });
  };

  const finishPartTwoAfterSilence = () => {
    if (phaseRef.current !== 'LONG_TURN') return;
    clearLongTurnTimer();
    clearSilenceTimer();
    setPhase('FINISHING');
    void session
      .transitionPart2('ANSWER_COMPLETE')
      .catch((error: unknown) => {
        setSessionError(error instanceof Error ? error.message : '无法结束 Part 2');
        setPhase('LONG_TURN');
      });
  };

  const scheduleSilenceFinish = () => {
    if (phaseRef.current !== 'LONG_TURN') return;
    clearSilenceTimer();
    silenceTimerRef.current = setTimeout(() => {
      silenceTimerRef.current = null;
      finishPartTwoAfterSilence();
    }, 3_000);
  };

  useEffect(() => {
    const prev = prevStateRef.current;
    const next = session.snapshot.state;
    prevStateRef.current = next;

    if (phaseRef.current === 'INTRODUCTION' && prev === 'assistant_speaking' && next === 'ready') {
      setPhase('PREPARATION');
      runPrepTimer(60);
    }

    const inputReadyTick = session.snapshot.ieltsInputReadyTick ?? 0;
    if (
      (phaseRef.current === 'STARTING' || phaseRef.current === 'LONG_TURN') &&
      inputReadyTick > lastInputReadyTick.current
    ) {
      lastInputReadyTick.current = inputReadyTick;
      if (phaseRef.current === 'STARTING') {
        setPhase('LONG_TURN');
        session.toggleMuted(false);
        runLongTurnTimer(120);
      }
    }

    if (phaseRef.current === 'LONG_TURN' && prev === 'user_speaking' && next === 'ready') {
      scheduleSilenceFinish();
    }
    if (next === 'user_speaking') {
      clearSilenceTimer();
    }
  }, [session.snapshot.ieltsInputReadyTick, session.snapshot.state, session.toggleMuted]);

  useEffect(() => {
    if (!session.snapshot.ieltsStateRestored) return;
    const backendPhase = session.snapshot.ieltsPart2State?.phase;
    if (!backendPhase || backendPhase === 'PREPARATION') return;
    if (backendPhase === 'LONG_TURN' && phaseRef.current !== 'LONG_TURN') {
      setNotesLocked(true);
      setPhase('LONG_TURN');
      session.toggleMuted(false);
      runLongTurnTimer(120);
    } else if (backendPhase === 'FINISHED' && phaseRef.current !== 'FINISHING') {
      setNotesLocked(true);
      setPhase('FINISHING');
    }
  }, [
    session.snapshot.ieltsPart2State,
    session.snapshot.ieltsStateRestored,
    session.toggleMuted,
  ]);

  useEffect(() => {
    if (!session.snapshot.ieltsPart2CompletionReady || phaseRef.current !== 'FINISHING') return;
    scheduleFinish();
  }, [session.snapshot.ieltsPart2CompletionReady]);

  useEffect(
    () => () => {
      clearPrepTimer();
      clearLongTurnTimer();
      clearSilenceTimer();
      clearFinishTimer();
    },
    [],
  );

  const statusText =
    phase === 'INTRODUCTION'
      ? '考官正在说明 Part 2 准备要求'
      : phase === 'PREPARATION'
        ? `准备时间 · ${formatSessionDuration(prepRemaining)}`
        : phase === 'LONG_TURN'
          ? `作答时间 · ${formatSessionDuration(longTurnRemaining)}`
          : phase === 'FINISHING'
            ? 'Part 2 已完成，考官正在结束本部分'
            : session.statusLabel;

  return (
    <SafeAreaView edges={['top', 'bottom']} style={styles.part2Screen}>
      <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : 'height'} keyboardVerticalOffset={8} style={styles.part2KeyboardView}>
        <ScrollView
          ref={preparationScrollRef}
          contentContainerStyle={styles.part2Content}
          keyboardDismissMode="interactive"
          keyboardShouldPersistTaps="handled"
          showsVerticalScrollIndicator={false}
        >
          <View style={styles.part2Presence}>
            <Image source={examiner.image} style={styles.part2ExaminerImage} contentFit="contain" />
            <View style={styles.part2PresenceCopy}>
              <Text style={styles.part2ExaminerName}>{examiner.name} · IELTS EXAMINER</Text>
              <Text style={styles.part2Timer}>{statusText}</Text>
              <Text style={styles.part2Instruction}>
                {phase === 'PREPARATION'
                  ? '请根据题卡记录关键词，准备结束后笔记将锁定。'
                  : phase === 'LONG_TURN'
                    ? '请持续作答，笔记内容已锁定。'
                    : phase === 'FINISHING'
                      ? '正在结束本部分并准备评分。'
                      : '请等待考官说明 Part 2 规则。'}
              </Text>
            </View>
            <HeaderIconButton accessibilityLabel="退出雅思训练" color={ieltsPalette.purpleDark} icon="close" onPress={confirmExit} />
            {sessionError ? <Text style={styles.intakeError}>{sessionError}</Text> : null}
            {session.startupError ? <Text style={styles.intakeError}>{session.startupError}</Text> : null}
          </View>

          <View style={styles.part2Workspace}>
            <View style={styles.part2CueCard}>
              <Text style={styles.part2Eyebrow}>PART 2 · CUE CARD</Text>
              <Text style={styles.part2CueTitle}>{cueCard.title}</Text>
              <Text style={styles.part2ShouldSay}>You should say:</Text>
              <View style={styles.part2CuePoints}>
                {cueCard.points.map((point) => (
                  <View key={point} style={styles.part2CuePointRow}>
                    <View style={styles.part2Bullet} />
                    <Text style={styles.part2CuePoint}>{point}</Text>
                  </View>
                ))}
              </View>
            </View>

            <View style={styles.part2NoteCard}>
              <View style={styles.part2NoteHeader}>
                <View style={styles.part2NoteTitleRow}>
                  <AppIcon name={notesLocked ? 'lock' : 'edit'} size={16} color={ieltsPalette.purpleDark} />
                  <Text style={styles.part2NoteTitle}>答题笔记</Text>
                </View>
                <Text style={styles.part2NoteHint}>{notesLocked ? '已锁定' : '可输入'}</Text>
              </View>
              {notesLocked ? (
                <Text style={styles.part2LockedNote}>{note || '准备阶段未记录笔记'}</Text>
              ) : (
                <TextInput
                  editable
                  multiline
                  onChangeText={setNote}
                  onFocus={() => requestAnimationFrame(() => preparationScrollRef.current?.scrollToEnd({ animated: true }))}
                  placeholder="记录关键词…"
                  placeholderTextColor={ieltsPalette.muted}
                  selectionColor={ieltsPalette.purple}
                  style={styles.part2NoteInput}
                  textAlignVertical="top"
                  value={note}
                />
              )}
            </View>
          </View>
        </ScrollView>

        {phase === 'PREPARATION' || phase === 'LONG_TURN' ? (
          <View style={styles.part2Footer}>
            <Pressable
              accessibilityRole="button"
              accessibilityLabel={phase === 'PREPARATION' ? '提前开始作答' : '结束 Part 2'}
              onPress={phase === 'PREPARATION' ? beginPartTwoAnswer : finishPartTwoAfterSilence}
              style={styles.part2EndButton}
            >
              <AppIcon name="arrow-right" size={25} color={colors.white} />
            </Pressable>
          </View>
        ) : null}
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

function ReportMetric({ label, value }: { label: string; value: string }) {
  return (
    <View style={styles.reportMetric}>
      <Text style={styles.reportMetricLabel}>{label}</Text>
      <Text style={styles.reportMetricValue}>{value}</Text>
    </View>
  );
}

export function IeltsFlow({ onExit, onViewDetails, analytics }: { onExit: () => void; onViewDetails?: (recordId: string) => void; analytics?: AnalyticsTrackerFactory }) {
  const { addIeltsRecord, hasCompletedOnboarding, level, saveLevel } = useAppModel();
  const { setImmersiveLearning } = useLearningStage();
  const ielts = useIeltsFlowController();
  const [route, setRoute] = useState<IeltsRoute>('intake');
  const [target, setTarget] = useState('7.0');
  const [startingLevel, setStartingLevel] = useState<string>(level);
  const [intakeStep, setIntakeStep] = useState(0);
  const [intakeSaving, setIntakeSaving] = useState(false);
  const [intakeError, setIntakeError] = useState<string | null>(null);
  const [part, setPart] = useState<IeltsPartId>('p2');
  const [topic, setTopic] = useState('');
  const [fullMock, setFullMock] = useState(false);
  const [topicCategory, setTopicCategory] = useState('ALL');
  const [topicQuery, setTopicQuery] = useState('');
  const [topicPage, setTopicPage] = useState(1);
  const [examiner, setExaminer] = useState<(typeof examiners)[number]>(() => randomExaminer());
  const [pendingSession, setPendingSession] = useState<{
    nextPart: IeltsPartId;
    topicItem: IeltsTopicSummary | null;
    random: boolean;
  } | null>(null);
  const [activeSessionId, setActiveSessionId] = useState<string | null>(null);
  const [evaluationError, setEvaluationError] = useState<string | null>(null);
  const evaluationReadyRef = useRef<Promise<unknown>>(Promise.resolve());
  const mockPartEvaluationsRef = useRef<Promise<unknown>[]>([]);
  const loadTopics = ielts.loadTopics;
  const refreshSettings = ielts.refreshSettings;
  const finalizeEvaluation = ielts.finalizeEvaluation;
  const generatedIeltsId = ielts.generated?.ieltsId;
  const shouldSkipIntake =
    !ielts.settingsLoading &&
    ielts.settings?.targetScore != null &&
    hasCompletedOnboarding;

  useEffect(() => {
    if (Platform.OS !== 'android') return;

    const subscription = BackHandler.addEventListener('hardwareBackPress', () => {
      if (route === 'intake') {
        if (intakeStep > 0) setIntakeStep(0);
        else onExit();
        return true;
      }
      if (route === 'home') {
        onExit();
      } else if (route === 'topics') {
        setRoute('home');
      } else if (route === 'examiner') {
        setRoute('topics');
      } else if (route === 'report') {
        setRoute('home');
      }
      // Do not leave the Activity while a live session or evaluation is active.
      return true;
    });
    return () => subscription.remove();
  }, [intakeStep, onExit, route]);

  useEffect(() => {
    void rememberSpecialty('ielts');
  }, []);

  useEffect(() => {
    if (route !== 'intake' || !shouldSkipIntake) return;
    const timer = setTimeout(() => setRoute('home'), 0);
    return () => clearTimeout(timer);
  }, [route, shouldSkipIntake]);

  const beginSession = async (input: {
    nextPart: IeltsPartId | 'mock';
    topicItem: IeltsTopicSummary | null;
    random: boolean;
    selectedExaminer?: (typeof examiners)[number];
  }) => {
    const nextExaminer = input.selectedExaminer ?? examiner;
    setExaminer(nextExaminer);
    const scene = await ielts.prepareSession({
      part: input.nextPart,
      topicId: input.topicItem?.id ?? null,
      random: input.random,
      examiner: nextExaminer,
    });
    setTopic(input.topicItem?.title ?? scene.title);
    setActiveSessionId(null);
    setRoute('session');
  };

  const startSinglePart = async (topicItem: IeltsTopicSummary | null, random = false) => {
    setFullMock(false);
    setPendingSession({ nextPart: part, topicItem, random });
    setRoute('examiner');
  };

  const startFullMock = async () => {
    setFullMock(true);
    setPart('p1');
    mockPartEvaluationsRef.current = [];
    evaluationReadyRef.current = Promise.resolve();
    const nextExaminer = randomExaminer();
    try {
      await beginSession({ nextPart: 'mock', topicItem: null, random: true, selectedExaminer: nextExaminer });
    } catch {
      // prepareSession 已写入 sessionError
    }
  };

  useEffect(() => {
    setImmersiveLearning(route === 'session' || route === 'analysis');
  }, [route, setImmersiveLearning]);

  useEffect(() => () => setImmersiveLearning(false), [setImmersiveLearning]);

  useEffect(() => {
    if (route !== 'topics') return;
    const timer = setTimeout(() => {
      void loadTopics(part, topicCategory, topicQuery, topicPage);
    }, 250);
    return () => clearTimeout(timer);
  }, [route, part, topicCategory, topicQuery, topicPage, loadTopics]);

  useEffect(() => {
    if (route !== 'home') return;
    void refreshSettings();
  }, [route, refreshSettings]);

  useEffect(() => {
    const timer = setTimeout(() => {
      if (ielts.settings?.targetScore != null) {
        setTarget(String(ielts.settings.targetScore));
      }
      if (ielts.settings?.examinerId) {
        const saved = examiners.find((item) => item.id === ielts.settings?.examinerId);
        if (saved) setExaminer(saved);
      }
    }, 0);
    return () => clearTimeout(timer);
  }, [ielts.settings]);

  useEffect(() => {
    if (route !== 'analysis') return;
    const ieltsId = generatedIeltsId;
    if (!ieltsId || !activeSessionId) {
      const errorTimer = setTimeout(
        () => setEvaluationError('缺少真实会话信息，无法生成评分'),
        0,
      );
      return () => clearTimeout(errorTimer);
    }
    let cancelled = false;
    void evaluationReadyRef.current
      .then(() => finalizeEvaluation(ieltsId, activeSessionId))
      .then(() => {
        if (!cancelled) setRoute('report');
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          setEvaluationError(error instanceof Error ? error.message : '评估生成失败');
        }
      });
    return () => {
      cancelled = true;
    };
  }, [route, finalizeEvaluation, generatedIeltsId, activeSessionId]);

  if (route === 'intake') {
    if (ielts.settingsLoading || shouldSkipIntake) {
      return (
        <View style={styles.ieltsEvaluationRoot}>
          <EvaluationPendingOverlay copy="正在读取你的 IELTS 学习设置…" />
        </View>
      );
    }
    const isTargetStep = intakeStep === 0;
    const selected = isTargetStep ? target : startingLevel;
    const options = isTargetStep ? ieltsTargetOptions : levels;

    return (
      <AppScreen contentStyle={styles.intakeScreen}>
        <View style={styles.intakeProgressRow}>
          <Text style={styles.intakeProgressLabel}>{intakeStep + 1} / 2</Text>
          <View style={styles.intakeProgressTrack}>
            <View style={[styles.intakeProgressFill, { width: `${((intakeStep + 1) / 2) * 100}%` }]} />
          </View>
        </View>
        <View style={styles.intakeBody}>
          <View style={styles.intakeOptions} accessibilityRole="radiogroup">
            {options.map((option, index) => {
              const optionSelected = selected === option.id;
              return (
                <Pressable
                  key={option.id}
                  accessibilityRole="radio"
                  accessibilityState={{ checked: optionSelected }}
                  onPress={() => (isTargetStep ? setTarget(option.id) : setStartingLevel(option.id))}
                  style={({ pressed }) => [styles.intakeOption, optionSelected && styles.intakeOptionSelected, pressed && styles.pressed]}
                >
                  <Text numberOfLines={1} style={styles.intakeOptionNumber}>0{index + 1}</Text>
                  <View style={uiStyles.flex}>
                    <Text style={styles.intakeOptionTitle}>{option.title}</Text>
                    <Text style={styles.intakeOptionNote}>{option.note}</Text>
                  </View>
                  {optionSelected ? (
                    <View style={styles.intakeCheck}>
                      <AppIcon name="check" size={17} color={colors.white} />
                    </View>
                  ) : <View style={styles.intakeCheckSpace} />}
                </Pressable>
              );
            })}
          </View>
          <View style={styles.intakeActions}>
            {intakeStep > 0 ? (
              <AppButton title="上一步" variant="secondary" icon="arrow-left" onPress={() => setIntakeStep(0)} style={styles.intakeBackButton} />
            ) : null}
            <AppButton
              title={isTargetStep ? '下一步' : intakeSaving ? '保存中…' : '进入 IELTS 专项'}
              icon="arrow-right"
              disabled={!selected || intakeSaving}
              onPress={() => {
                if (isTargetStep) {
                  setIntakeStep(1);
                  return;
                }
                setIntakeSaving(true);
                setIntakeError(null);
                void Promise.all([
                  ielts.saveTargetScore(target),
                  saveLevel(startingLevel),
                ])
                  .then(() => setRoute('home'))
                  .catch((error: unknown) => {
                    setIntakeError(error instanceof Error ? error.message : '目标分数保存失败');
                  })
                  .finally(() => setIntakeSaving(false));
              }}
              style={styles.intakeNextButton}
            />
          </View>
          {intakeError ? <Text style={styles.intakeError}>{intakeError}</Text> : null}
        </View>
      </AppScreen>
    );
  }

  if (route === 'home') {
    return (
      <AppScreen
        contentStyle={styles.homeScreen}
        fixedHeader={
          <MainModuleHeader
            englishTitle="IELTS SPEAKING"
            title="雅思口语"
            style={styles.ieltsHeader}
            action={
              <HeaderIconButton
                accessibilityLabel="退出雅思口语"
                icon="close"
                onPress={onExit}
                color={ieltsPalette.purpleDark}
              />
            }
          />
        }
      >
        <View style={styles.goalRow} accessibilityLabel="备考数据">
          <View style={styles.goalMetric}>
            <Text style={styles.goalLabel}>目标</Text>
            <View style={styles.goalValueRow}>
              <Text style={styles.goalValue}>{ielts.settings?.targetScore ?? target}</Text>
              <Image source={ieltsAssets.target} style={[styles.goalIcon, styles.goalIconTarget]} contentFit="contain" />
            </View>
          </View>
          <View style={styles.goalMetric}>
            <Text style={styles.goalLabel}>连续打卡</Text>
            <View style={styles.goalValueRow}>
              <Text style={styles.goalValue}>{ielts.settings?.currentStreakDays ?? 0}<Text style={styles.goalSuffix}> 天</Text></Text>
              <Image source={ieltsAssets.calendar} style={[styles.goalIcon, styles.goalIconCalendar]} contentFit="contain" />
            </View>
          </View>
          <View style={[styles.goalMetric, styles.goalMetricLast]}>
            <Text style={styles.goalLabel}>今日特训</Text>
            <View style={styles.goalValueRow}>
              <Text style={styles.goalValue}>{ielts.settings?.todayCompletedCount ?? 0}<Text style={styles.goalSuffix}> / 5</Text></Text>
              <Image source={ieltsAssets.flame} style={[styles.goalIcon, styles.goalIconFlame]} contentFit="contain" />
            </View>
          </View>
        </View>
        <Card style={styles.mockCard}>
          <View style={styles.mockTopRow}>
            <View style={uiStyles.flex}>
              <Text style={styles.ieltsMockPill}>全真模考</Text>
              <Text style={styles.mockTitle}>完整模拟一场 IELTS 口语考试</Text>
              <Text style={styles.mockCopy}>随机考官 · 随机题目 · 完整能力报告</Text>
            </View>
            <Image source={ieltsAssets.badge} style={styles.mockBadge} contentFit="contain" />
          </View>
          <View style={styles.mockFooter}>
            <View style={uiStyles.flex}>
              <Text style={styles.mockMetaLabel}>预计用时</Text>
              <Text style={styles.mockMetaValue}>11–14 分钟</Text>
              <Text style={styles.mockMetaNote}>开始后不可暂停</Text>
            </View>
            <AppButton
              title={ielts.sessionBusy ? '准备中…' : '开始模考'}
              variant="primary"
              icon="arrow-right"
              style={styles.ieltsMockButton}
              disabled={ielts.sessionBusy}
              onPress={() => {
                void startFullMock();
              }}
            />
          </View>
        </Card>
        <View style={styles.ieltsSectionTitle}>
          <View style={styles.ieltsSectionMarker} />
          <Text style={styles.ieltsSectionHeading}>快速开始训练</Text>
        </View>
        {ieltsParts.map((item) => (
          <Card
            key={item.id}
            onPress={() => {
              setPart(item.id);
              setTopicCategory('ALL');
              setTopicQuery('');
              setTopicPage(1);
              setRoute('topics');
            }}
            style={[styles.partCard, styles.ieltsPartCard]}
          >
            <Text style={styles.ieltsPartNumber}>{item.number}</Text>
            <View style={uiStyles.flex}>
              <Text style={styles.ieltsPartPill}>{item.label}</Text>
              <Text style={styles.ieltsPartTitle}>{item.title}</Text>
              <Text style={styles.ieltsPartNote}>{item.note} · {item.duration}</Text>
            </View>
            <View style={styles.ieltsPartArrow}>
              <AppIcon name="chevron-right" size={21} color={ieltsPalette.purpleDark} />
            </View>
          </Card>
        ))}
      </AppScreen>
    );
  }

  if (route === 'topics') {
    const partMeta = ieltsParts.find((item) => item.id === part) ?? ieltsParts[0];
    const filters = [{ code: 'ALL', label: '全部' }, ...ielts.categories];
    const totalTopicPages = Math.max(1, ielts.topicTotalPages || 1);
    const visibleTopics = ielts.topics;
    const startRandomTopic = () => {
      void startSinglePart(null, true);
    };

    return (
      <AppScreen
        contentStyle={styles.topicsScreen}
        fixedHeader={
          <MainModuleHeader
            englishTitle={`IELTS ${partMeta.label.toUpperCase()}`}
            title={`${partMeta.label} · ${partMeta.title}`}
            style={styles.ieltsHeader}
            action={
              <HeaderIconButton
                accessibilityLabel="返回雅思主页"
                icon="close"
                color={ieltsPalette.purpleDark}
                onPress={() => setRoute('home')}
              />
            }
          />
        }
      >
        <View style={styles.topicHero}>
          <Text style={styles.topicHeroCopy}>选择一个话题，正式开始后才会由考官揭晓具体问题。</Text>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="随机练习"
            onPress={startRandomTopic}
            style={({ pressed }) => [styles.randomTopicButton, pressed && styles.pressed]}
          >
            <AppIcon name="shuffle" size={18} color={ieltsPalette.text} />
            <Text style={styles.randomTopicButtonText}>随机练习</Text>
            <AppIcon name="arrow-right" size={18} color={ieltsPalette.text} />
          </Pressable>
        </View>

        <View style={styles.topicSearch}>
          <AppIcon name="search" size={19} color={ieltsPalette.muted} />
          <TextInput
            accessibilityLabel="搜索话题"
            value={topicQuery}
            onChangeText={(value) => {
              setTopicQuery(value);
              setTopicPage(1);
            }}
            placeholder="搜索话题"
            placeholderTextColor={ieltsPalette.muted}
            style={styles.topicSearchInput}
          />
          {topicQuery ? (
            <Pressable accessibilityRole="button" accessibilityLabel="清空搜索" onPress={() => { setTopicQuery(''); setTopicPage(1); }} style={styles.topicSearchReset}>
              <AppIcon name="close" size={17} color={ieltsPalette.muted} />
            </Pressable>
          ) : null}
        </View>

        <ScrollView
          horizontal
          contentContainerStyle={styles.topicFilters}
          showsHorizontalScrollIndicator={false}
          style={styles.topicFiltersScroll}
        >
          {filters.map((filter) => (
            <Pressable
              key={filter.code}
              onPress={() => { setTopicCategory(filter.code); setTopicPage(1); }}
              style={[styles.topicFilter, topicCategory === filter.code && styles.topicFilterActive]}
            >
              <Text style={[styles.topicFilterText, topicCategory === filter.code && styles.topicFilterTextActive]}>{filter.label}</Text>
            </Pressable>
          ))}
        </ScrollView>

        <View style={styles.topicTable}>
          <View style={styles.topicTableHeader}>
            <Text style={[styles.topicTableHeaderText, styles.topicColumnMain]}>话题</Text>
            <Text style={[styles.topicTableHeaderText, styles.topicColumnPractice]}>练习记录</Text>
            <Text style={[styles.topicTableHeaderText, styles.topicColumnResult]}>最近表现</Text>
            <View style={styles.topicColumnArrow} />
          </View>
          {ielts.topicsLoading ? (
            <View style={styles.topicEmpty}>
              <Text style={styles.topicEmptyTitle}>正在读取题库…</Text>
            </View>
          ) : null}
          {!ielts.topicsLoading && ielts.topicsError ? (
            <View style={styles.topicEmpty}>
              <Text style={styles.topicEmptyTitle}>{ielts.topicsError}</Text>
            </View>
          ) : null}
          {!ielts.topicsLoading && !ielts.topicsError && visibleTopics.map((item) => {
            const practiced = item.practiceCount > 0;
            const recentScore = item.latestPerformanceScore == null
              ? (practiced ? '已完成' : '未练习')
              : `${ielts.formatBand(item.latestPerformanceScore)} 分`;
            return (
              <Pressable
                accessibilityRole="button"
                accessibilityLabel={`${item.title}，${ielts.practiceTypeLabel(item.latestPracticeType)}`}
                key={item.id}
                onPress={() => { void startSinglePart(item, false); }}
                style={({ pressed }) => [styles.topicTableRow, pressed && styles.topicTableRowPressed]}
              >
                <View style={styles.topicColumnMain}>
                  <Text style={styles.topicCategory}>{item.categoryLabel}</Text>
                  <Text numberOfLines={2} style={styles.topicTitle}>{item.title}</Text>
                  <Text style={styles.topicQuestionCount}>{item.questionCount} 道问题</Text>
                </View>
                <View style={styles.topicColumnPractice}>
                  <Text style={styles.topicPracticeTitle}>{ielts.practiceTypeLabel(item.latestPracticeType)}</Text>
                  <Text style={styles.topicPracticeNote}>{practiced ? `共 ${item.practiceCount} 次` : '暂无记录'}</Text>
                </View>
                <View style={styles.topicColumnResult}>
                  <Text style={styles.topicResult}>{recentScore}</Text>
                  <Text numberOfLines={1} style={styles.topicResultNote}>{practiced ? compactPerformanceSummary(item.latestPerformanceSummary) : '未练习'}</Text>
                </View>
                <View style={styles.topicColumnArrow}>
                  <AppIcon name="chevron-right" size={18} color={ieltsPalette.text} />
                </View>
              </Pressable>
            );
          })}
          {!ielts.topicsLoading && !ielts.topicsError && visibleTopics.length === 0 ? (
            <View style={styles.topicEmpty}>
              <Text style={styles.topicEmptyTitle}>没有找到相关话题</Text>
              <Text style={styles.topicEmptyCopy}>调整分类或搜索关键词后再试。</Text>
            </View>
          ) : null}
        </View>
        <View style={styles.topicPagination}>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="上一页"
            disabled={topicPage <= 1}
            onPress={() => setTopicPage((page) => Math.max(1, page - 1))}
            style={[styles.topicPaginationArrow, topicPage <= 1 && styles.topicPaginationDisabled]}
          >
            <AppIcon name="arrow-left" size={18} color={topicPage <= 1 ? colors.subtle : ieltsPalette.purpleDark} />
          </Pressable>
          <View style={styles.topicPaginationPages}>
            {compactPageNumbers(topicPage, totalTopicPages).map((page) => (
              <Pressable
                accessibilityRole="button"
                accessibilityLabel={`第 ${page} 页`}
                key={page}
                onPress={() => setTopicPage(page)}
                style={[styles.topicPaginationPage, topicPage === page && styles.topicPaginationPageActive]}
              >
                <Text style={[styles.topicPaginationPageText, topicPage === page && styles.topicPaginationPageTextActive]}>{page}</Text>
              </Pressable>
            ))}
          </View>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="下一页"
            disabled={topicPage >= totalTopicPages}
            onPress={() => setTopicPage((page) => Math.min(totalTopicPages, page + 1))}
            style={[styles.topicPaginationArrow, topicPage >= totalTopicPages && styles.topicPaginationDisabled]}
          >
            <AppIcon name="arrow-right" size={18} color={topicPage >= totalTopicPages ? colors.subtle : ieltsPalette.purpleDark} />
          </Pressable>
          <Text style={styles.topicPageCount}>{topicPage} / {totalTopicPages}</Text>
        </View>
        <Text style={styles.topicCount}>共 {ielts.topicTotal} 个话题</Text>
        {ielts.sessionError ? <Text style={styles.intakeError}>{ielts.sessionError}</Text> : null}
      </AppScreen>
    );
  }

  if (route === 'examiner') {
    const selectedPart = pendingSession?.nextPart ?? part;
    const partMeta = ieltsParts.find((item) => item.id === selectedPart) ?? ieltsParts[0];
    return (
      <AppScreen
        contentStyle={styles.examinerScreen}
        fixedHeader={(
          <MainModuleHeader
            englishTitle="CHOOSE YOUR EXAMINER"
            title="选择本次考官"
            style={styles.ieltsHeader}
            action={(
              <HeaderIconButton
                accessibilityLabel="返回话题选择"
                icon="close"
                color={ieltsPalette.purpleDark}
                onPress={() => setRoute('topics')}
              />
            )}
          />
        )}
      >
        <View style={styles.examinerIntro}>
          <Text style={styles.examinerPart}>{partMeta.label} · {pendingSession?.topicItem?.title ?? '随机话题'}</Text>
          <Text style={styles.examinerIntroCopy}>选择一位考官。你的选择会保存，并用于本次实时口语训练。</Text>
        </View>
        <View style={styles.examinerGrid} accessibilityRole="radiogroup">
          {examiners.map((item) => {
            const selected = item.id === examiner.id;
            return (
              <Pressable
                key={item.id}
                accessibilityRole="radio"
                accessibilityState={{ checked: selected }}
                onPress={() => setExaminer(item)}
                style={({ pressed }) => [
                  styles.examinerCard,
                  selected && styles.examinerCardSelected,
                  pressed && styles.pressed,
                ]}
              >
                <Image source={item.image} style={styles.examinerImage} contentFit="contain" />
                <Text style={styles.examinerName}>{item.name}</Text>
                <Text style={styles.examinerAccent}>{item.accent}口音</Text>
                {selected ? <View style={styles.examinerSelected}><AppIcon name="check" size={15} color={colors.white} /></View> : null}
              </Pressable>
            );
          })}
        </View>
        <Card style={styles.examinerDetail}>
          <Text style={styles.examinerDetailTitle}>{examiner.name} · {examiner.accent}口音</Text>
          <Text style={styles.examinerDetailCopy}>{examiner.description}</Text>
        </Card>
        <AppButton
          title={ielts.sessionBusy ? '正在准备…' : '确认考官并开始'}
          icon="arrow-right"
          disabled={!pendingSession || ielts.sessionBusy}
          onPress={() => {
            if (!pendingSession) return;
            void beginSession({ ...pendingSession, selectedExaminer: examiner }).catch(() => undefined);
          }}
        />
        {ielts.sessionError ? <Text style={styles.intakeError}>{ielts.sessionError}</Text> : null}
      </AppScreen>
    );
  }

  if (route === 'session') {
    const generatedScene = ielts.generated;
    const ieltsId = generatedScene?.ieltsId;
    const voiceId = examiner.voiceId;
    const finishSession = (
      sessionId: string | null,
      ending: Promise<unknown>,
      turnEvaluations: Promise<void>,
    ) => {
      if (sessionId) setActiveSessionId(sessionId);
      const currentPartIndex = ieltsPartOrder.indexOf(part);
      const nextPart = ieltsPartOrder[currentPartIndex + 1];
      if (fullMock && nextPart && generatedScene && sessionId) {
        const completedSession = ending.then(() => undefined);
        const backgroundEvaluation = Promise.all([completedSession, turnEvaluations])
          .then(() => ielts.scoreCompletedPart(generatedScene.ieltsId, sessionId))
          .catch(() => undefined);
        mockPartEvaluationsRef.current = [
          ...mockPartEvaluationsRef.current,
          backgroundEvaluation,
        ];
        void completedSession.then(() => {
          setActiveSessionId(null);
          setPart(nextPart);
        }).catch((error: unknown) => {
          setEvaluationError(error instanceof Error ? error.message : '无法进入下一部分');
          setRoute('analysis');
        });
        return;
      }
      evaluationReadyRef.current = Promise.all([ending, turnEvaluations]).then(async () => {
        if (fullMock) {
          await Promise.all(mockPartEvaluationsRef.current);
        }
      });
      setEvaluationError(null);
      setRoute('analysis');
    };
    if (!ieltsId) {
      return (
        <AppScreen contentStyle={[styles.analysis, styles.ieltsStageScreen]} stickyHeader={false}>
          <Text style={styles.analysisTitle}>正在准备 IELTS 会话…</Text>
        </AppScreen>
      );
    }
    if (part === 'p2') {
      const cueCard = resolvePart2CueCard(generatedScene, ielts.training);
      return (
        <IeltsPart2Session
          analytics={analytics}
          cueCard={cueCard}
          examiner={examiner}
          ieltsId={ieltsId}
          onExit={onExit}
          onFinish={finishSession}
          voiceId={voiceId}
        />
      );
    }
    return (
      <IeltsSession
        analytics={analytics}
        examiner={examiner}
        part={part}
        ieltsId={ieltsId}
        autoAdvance={fullMock}
        voiceId={voiceId}
        onExit={onExit}
        onFinish={finishSession}
      />
    );
  }

  if (route === 'analysis') {
    return (
      <View style={styles.ieltsEvaluationRoot}>
        {evaluationError ? (
          <View style={styles.ieltsEvaluationError}>
            <AppIcon name="sliders" size={32} color={ieltsPalette.purpleDark} />
            <Text style={styles.analysisTitle}>评分生成失败</Text>
            <Text style={styles.intakeError}>{evaluationError}</Text>
            <AppButton title="返回主页" variant="secondary" onPress={() => setRoute('home')} />
          </View>
        ) : (
          <EvaluationPendingOverlay copy="正在整理本次对话与四项能力表现…" />
        )}
      </View>
    );
  }

  const evaluation = ielts.latestEvaluation;
  const bandScore = evaluation ? ielts.formatBand(evaluation.overallBandScore) : '—';

  const saveReport = () => {
    if (!evaluation) return null;
    const recordId = activeSessionId ?? `ielts-${Date.now()}`;
    addIeltsRecord({
      id: recordId,
      type: fullMock ? '完整模考' : part === 'p1' ? 'Part 1' : part === 'p3' ? 'Part 3' : 'Part 2',
      title: fullMock ? '完整口语模拟' : topic || ielts.generated?.title || 'IELTS 专项练习',
      date: '刚刚',
      duration: fullMock ? '14 分钟' : '4 分钟',
      result: fullMock ? `预估 ${bandScore}` : '专项诊断',
      estimatedBand: evaluation.overallBandScore == null ? null : Number(evaluation.overallBandScore),
      scores: [
        Math.round(((evaluation.fluencyCoherenceScore ?? 0) / 9) * 100),
        Math.round(((evaluation.lexicalResourceScore ?? 0) / 9) * 100),
        Math.round(((evaluation.grammaticalRangeAccuracyScore ?? 0) / 9) * 100),
        Math.round(((evaluation.pronunciationScore ?? 0) / 9) * 100),
      ],
      bandScores: [
        evaluation.fluencyCoherenceScore,
        evaluation.lexicalResourceScore,
        evaluation.grammaticalRangeAccuracyScore,
        evaluation.pronunciationScore,
      ],
      summary: evaluation.summary,
      strengths: evaluation.strengths,
      improvements: evaluation.improvements,
      recommendedExpressions: evaluation.recommendedExpressions,
      scoreReasons: [
        evaluation.fluencyCoherenceReason ?? null,
        evaluation.lexicalResourceReason ?? null,
        evaluation.grammaticalRangeAccuracyReason ?? null,
        evaluation.pronunciationReason ?? null,
      ],
      mode: fullMock ? 'MOCK_TEST' : 'PART_PRACTICE',
      part: fullMock ? null : toApiPart(part),
      endedAt: new Date().toISOString(),
      partEvaluations: evaluation.partEvaluations,
    });
    return recordId;
  };

  const returnToIeltsHome = () => {
    saveReport();
    setRoute('home');
  };
  const viewIeltsDetails = () => {
    const recordId = saveReport();
    if (onViewDetails) {
      setImmersiveLearning(false);
      if (recordId) onViewDetails(recordId);
    } else {
      onExit();
    }
  };

  if (!fullMock) {
    return (
      <AppScreen contentStyle={styles.practiceScoreScreen} stickyHeader={false}>
        <IeltsPracticeScoreDialog
          evaluation={evaluation}
          onHome={returnToIeltsHome}
          onDetails={viewIeltsDetails}
        />
      </AppScreen>
    );
  }

  return (
    <AppScreen contentStyle={[styles.ieltsStageScreen, styles.reportScreen]} stickyHeader={false}>
      <View style={styles.bandHero}>
        <Text style={styles.bandEyebrow}>本次模拟评分</Text>
        <Text style={styles.bandScore}>{bandScore}</Text>
        <Text style={styles.bandLabel}>ESTIMATED BAND</Text>
      </View>
      <View style={styles.metrics}>
        <ReportMetric label="流利与连贯" value={evaluation ? ielts.formatBand(evaluation.fluencyCoherenceScore) : '—'} />
        <ReportMetric label="词汇资源" value={evaluation ? ielts.formatBand(evaluation.lexicalResourceScore) : '—'} />
      </View>
      <View style={styles.metrics}>
        <ReportMetric label="语法范围" value={evaluation ? ielts.formatBand(evaluation.grammaticalRangeAccuracyScore) : '—'} />
        <ReportMetric label="发音" value={evaluation ? ielts.formatBand(evaluation.pronunciationScore) : '—'} />
      </View>
      <View style={styles.reportActions}>
        <AppButton
          title="返回主页"
          variant="secondary"
          onPress={returnToIeltsHome}
          style={styles.reportSecondaryButton}
        />
        <AppButton
          title="查看详情"
          onPress={viewIeltsDetails}
          style={styles.reportPrimaryButton}
        />
      </View>
    </AppScreen>
  );
}

type InterviewRoute = 'input' | 'review' | 'live' | 'finalizing';
type InterviewDifficulty = InterviewDifficultyOption;

const interviewDifficulties: readonly { id: InterviewDifficulty; title: string; note: string; recommended?: boolean }[] = [
  { id: 'easy', title: '简单', note: '基础问答' },
  { id: 'standard', title: '标准', note: '核心能力', recommended: true },
  { id: 'hard', title: '困难', note: '深入追问' },
];

function InterviewSession({ preparation, onFinished, analytics }: { preparation: InterviewPreparationResult & { scene: NonNullable<InterviewPreparationResult['scene']> }; onFinished: (sessionId: string, api: InterviewSessionApi) => void; analytics?: AnalyticsTrackerFactory }) {
  const { teacher } = useAppModel();
  const [trainingAnalytics] = useState(() => analytics?.training({
    mode: 'INTERVIEW',
    pageCode: 'interview-training',
  }));
  const session = useInterviewSession(
    { sceneId: preparation.scene.sceneId, voice: teacher.voiceId },
    trainingAnalytics,
  );
  const deliveredSession = useRef<string | null>(null);

  useEffect(() => {
    if (session.state === 'ended' && session.sessionId && deliveredSession.current !== session.sessionId) {
      deliveredSession.current = session.sessionId;
      onFinished(session.sessionId, createInterviewApi(preparation.scene.sceneId));
    }
  }, [onFinished, preparation.scene.sceneId, session.sessionId, session.state]);

  const statusText = session.error?.message
    ?? (session.state === 'starting'
      ? '正在连接 AI 面试官'
      : session.state === 'ending'
        ? '正在结束面试并生成报告'
        : session.interviewState?.currentTopic
          ? `正在面试 · ${session.interviewState.currentTopic}`
          : '正在聆听你的回答');
  return (
    <SafeAreaView edges={['top', 'bottom']} style={styles.interviewCallScreen}>
      <CallExperience
        allowSubtitleToggle={false}
        compactTranscriptLayout
        elapsed={session.elapsed}
        endAccessibilityLabel="结束面试"
        initialSubtitles
        muted={session.userMuted}
        onEnd={() => void session.end().catch(() => undefined)}
        onMutedChange={session.setMuted}
        participant={{ image: examinerAssets.sophia, name: 'AI 面试官' }}
        showTranslationControl={false}
        statusLabel={statusText}
        statusText={`${preparation.jobTitle ?? '英文面试'} · ${statusText}`}
        tone="navy"
        transcriptEnglish={session.currentQuestion || (session.state === 'starting' ? '正在连接 AI 面试官…' : 'AI 面试官正在提问…')}
        transcriptSpeaker="AI 面试官"
        userTranscript=""
      />
    </SafeAreaView>
  );
}

export function InterviewFlow({ onExit, onViewDetails, practiceScene, analytics }: { onExit: () => void; onViewDetails?: () => void; practiceScene?: { sceneId: string; jobTitle?: string }; analytics?: AnalyticsTrackerFactory }) {
  const { setImmersiveLearning } = useLearningStage();
  const [route, setRoute] = useState<InterviewRoute>(practiceScene ? 'live' : 'input');
  const [jobDescription, setJobDescription] = useState('');
  const [difficulty, setDifficulty] = useState<InterviewDifficulty | null>(null);
  const [preparation, setPreparation] = useState<InterviewPreparationResult | null>(() => practiceScene ? ({ scene: { sceneId: practiceScene.sceneId, scenePrompt: '' }, material: null, jobTitle: practiceScene.jobTitle ?? null } as unknown as InterviewPreparationResult) : null);
  const [completedSession, setCompletedSession] = useState<{ sessionId: string; api: InterviewSessionApi } | null>(null);
  const { resumeFileName, resumeText, resumeMode, setResumeText, setResumeMode, isPreparing, error, pickResume, start, confirm } = useInterviewPreparation();
  const [draft, setDraft] = useState<InterviewPreparationResult['material'] | null>(null);
  const canStart = Boolean(jobDescription.trim() && difficulty && !isPreparing);

  useEffect(() => {
    setImmersiveLearning(route !== 'input');
    void rememberSpecialty('interview');
  }, [route, setImmersiveLearning]);

  useEffect(() => () => setImmersiveLearning(false), [setImmersiveLearning]);

  const closeReport = useCallback(() => {
    setPreparation(null);
    setCompletedSession(null);
    setRoute('input');
  }, []);

  useEffect(() => {
    if (Platform.OS !== 'android') return;

    const subscription = BackHandler.addEventListener('hardwareBackPress', () => {
      if (route === 'input') onExit();
      else if (route === 'review') setRoute('input');
      else if (route === 'finalizing') closeReport();
      // A live interview must be ended from its call control.
      return true;
    });
    return () => subscription.remove();
  }, [closeReport, onExit, route]);

  if (route === 'input') {
    return (
      <AppScreen
        contentStyle={styles.interviewInputScreen}
        fixedHeader={
          <View style={styles.interviewHeroHeader}>
            <Image source={interviewAssets.hero} style={styles.interviewHero} contentFit="contain" />
            <MainModuleHeader
              englishTitle="INTERVIEW PRACTICE"
              light
              title="英文面试"
              style={styles.interviewHeader}
              action={
                <HeaderIconButton
                  accessibilityLabel="退出英文面试"
                  color="#F4F8FF"
                  icon="close"
                  onPress={onExit}
                />
              }
            />
          </View>
        }
      >
        <View style={styles.interviewPanel}>
          <View style={styles.interviewPanelHeader}>
            <View style={styles.interviewPanelCopy}>
              <Text style={styles.interviewPanelTitle}>填写简历（可选）</Text>
              <Text style={styles.interviewPanelNote}>选择一种材料来源，AI 会先整理后让你确认</Text>
            </View>
            <Image source={interviewAssets.resume} style={styles.interviewResumeAsset} contentFit="contain" />
          </View>
          <View style={styles.interviewResumeModeTabs} accessibilityRole="tablist">
            <Pressable accessibilityRole="tab" accessibilityState={{ selected: resumeMode === 'text' }} onPress={() => setResumeMode('text')} style={[styles.interviewResumeModeTab, resumeMode === 'text' && styles.interviewResumeModeTabSelected]}>
              <Text style={[styles.interviewResumeModeText, resumeMode === 'text' && styles.interviewResumeModeTextSelected]}>粘贴文本</Text>
            </Pressable>
            <Pressable accessibilityRole="tab" accessibilityState={{ selected: resumeMode === 'file' }} onPress={() => setResumeMode('file')} style={[styles.interviewResumeModeTab, resumeMode === 'file' && styles.interviewResumeModeTabSelected]}>
              <Text style={[styles.interviewResumeModeText, resumeMode === 'file' && styles.interviewResumeModeTextSelected]}>上传文件</Text>
            </Pressable>
          </View>
          {resumeMode === 'text' ? <TextInput
            accessibilityLabel="简历文本"
            multiline
            onChangeText={(value) => { setResumeMode('text'); setResumeText(value); }}
            placeholder="粘贴简历中的教育、工作与项目经历……（可留空）"
            placeholderTextColor={interviewPalette.subtle}
            style={styles.interviewResumeInput}
            textAlignVertical="top"
            value={resumeText}
          /> : <Pressable
            accessibilityRole="button"
            accessibilityLabel={resumeFileName ? '重新选择简历文件' : '上传简历文件'}
            onPress={() => void pickResume()}
            style={({ pressed }) => [styles.interviewResumeFileButton, pressed && styles.pressed]}
          >
            <AppIcon name="upload" size={18} color={interviewPalette.accentBright} />
            <Text style={styles.interviewResumeFileText}>{resumeFileName ?? '上传 PDF / DOCX（可选）'}</Text>
          </Pressable>}
        </View>

        <View style={styles.interviewPanel}>
          <View style={styles.interviewPanelHeader}>
            <View style={styles.interviewPanelCopy}>
              <Text style={styles.interviewPanelTitle}>填写岗位 JD</Text>
              <Text style={styles.interviewPanelNote}>粘贴岗位职责、任职要求和优先能力</Text>
            </View>
            <Image source={interviewAssets.company} style={styles.interviewCompanyAsset} contentFit="contain" />
          </View>
          <TextInput
            accessibilityLabel="岗位 JD"
            multiline
            onChangeText={setJobDescription}
            placeholder="在这里粘贴岗位 JD……"
            placeholderTextColor={interviewPalette.subtle}
            style={styles.interviewJdInput}
            textAlignVertical="top"
            value={jobDescription}
          />
        </View>

        <View style={styles.interviewPanel}>
          <View style={styles.interviewPanelHeader}>
            <View style={styles.interviewPanelCopy}>
              <Text style={styles.interviewPanelTitle}>选择面试难度</Text>
              <Text style={styles.interviewPanelNote}>问题深度与追问强度会随难度调整</Text>
            </View>
            <Image source={interviewAssets.briefcase} style={styles.interviewBriefcaseAsset} contentFit="contain" />
          </View>
          <View accessibilityRole="radiogroup" style={styles.interviewDifficultyOptions}>
            {interviewDifficulties.map((item) => (
              <Pressable
                accessibilityRole="radio"
                accessibilityState={{ checked: difficulty === item.id }}
                key={item.id}
                onPress={() => setDifficulty(item.id)}
                style={({ pressed }) => [styles.interviewDifficulty, difficulty === item.id && styles.interviewDifficultySelected, pressed && styles.pressed]}
              >
                <View style={styles.interviewDifficultyTitleRow}>
                  <Text style={[styles.interviewDifficultyTitle, difficulty === item.id && styles.interviewDifficultyTitleSelected]}>{item.title}</Text>
                  {item.recommended ? <Text style={styles.interviewRecommended}>★</Text> : null}
                </View>
                <Text style={[styles.interviewDifficultyNote, difficulty === item.id && styles.interviewDifficultyNoteSelected]}>{item.note}</Text>
              </Pressable>
            ))}
          </View>
        </View>

        {error ? <Text accessibilityRole="alert" style={styles.interviewError}>{error}</Text> : null}
        <AppButton
          title={isPreparing ? '正在整理材料…' : '整理面试材料'}
          icon="arrow-right"
          disabled={!canStart}
          onPress={() => {
            void start({ jobDescription, difficulty }).then((prepared) => {
              if (!prepared) return;
              setDraft(prepared.material);
              setRoute('review');
            });
          }}
          style={styles.interviewStartButton}
        />
      </AppScreen>
    );
  }

  if (route === 'review' && draft) {
    const listFields: readonly { key: keyof typeof draft; label: string; required?: boolean }[] = [
      { key: 'responsibilities', label: '岗位职责', required: true },
      { key: 'qualificationRequirements', label: '任职要求', required: true },
      { key: 'requiredSkills', label: '必备技能' },
      { key: 'education', label: '教育经历' },
      { key: 'workExperiences', label: '工作经历' },
      { key: 'projectExperiences', label: '项目经历' },
      { key: 'skillsAndAbilities', label: '技能与能力' },
      { key: 'interviewableExperienceClues', label: '可深挖经历线索' },
    ];
    const missing = listFields.filter((field) => field.required && !(draft[field.key] as string[]).some((item) => item.trim()));
    return (
      <AppScreen contentStyle={styles.interviewInputScreen} fixedHeader={(
        <View style={styles.interviewHeroHeader}>
          <Image source={interviewAssets.hero} style={styles.interviewHero} contentFit="contain" />
          <MainModuleHeader englishTitle="MATERIAL REVIEW" light title="确认面试材料" style={styles.interviewHeader} action={<HeaderIconButton accessibilityLabel="退出英文面试" color="#F4F8FF" icon="close" onPress={onExit} />} />
        </View>
      )}>
        <Text style={styles.interviewReviewLead}>AI 已从 JD 与简历中整理出结构化材料。请核对并修改后，再确认生成面试。</Text>
        <View style={styles.interviewPanel}>
          <Text style={styles.interviewPanelTitle}>岗位信息</Text>
          <TextInput accessibilityLabel="岗位名称" onChangeText={(value) => setDraft({ ...draft, jobTitle: value })} placeholder="岗位名称" placeholderTextColor={interviewPalette.subtle} style={styles.interviewReviewInput} value={draft.jobTitle ?? ''} />
          <TextInput accessibilityLabel="其他岗位信息" multiline onChangeText={(value) => setDraft({ ...draft, otherJobInformation: value })} placeholder="其他岗位信息（可选）" placeholderTextColor={interviewPalette.subtle} style={styles.interviewReviewInput} textAlignVertical="top" value={draft.otherJobInformation ?? ''} />
        </View>
        {listFields.map((field) => (
          <View key={field.key} style={styles.interviewPanel}>
            <Text style={styles.interviewPanelTitle}>{field.label}{field.required ? ' *' : ''}</Text>
            <TextInput accessibilityLabel={field.label} multiline onChangeText={(value) => setDraft({ ...draft, [field.key]: value.split('\n').map((item) => item.trim()).filter(Boolean) })} placeholder={`每行填写一项${field.required ? '（必填）' : ''}`} placeholderTextColor={interviewPalette.subtle} style={styles.interviewReviewTextarea} textAlignVertical="top" value={(draft[field.key] as string[]).join('\n')} />
          </View>
        ))}
        {error ? <Text accessibilityRole="alert" style={styles.interviewError}>{error}</Text> : null}
        {missing.length ? <Text accessibilityRole="alert" style={styles.interviewError}>请补充：{missing.map((item) => item.label).join('、')}</Text> : null}
        <View style={styles.interviewReviewActions}>
          <AppButton title="返回修改输入" variant="secondary" onPress={() => setRoute('input')} style={styles.interviewReviewSecondaryButton} />
          <AppButton title={isPreparing ? '正在生成…' : '确认并生成面试'} disabled={Boolean(missing.length) || isPreparing} onPress={() => void confirm({ material: draft, difficulty }).then((prepared) => {
            if (!prepared?.scene) return;
            setPreparation({ scene: prepared.scene, material: prepared.material, jobTitle: prepared.jobTitle });
            setRoute('live');
          })} style={styles.interviewReviewPrimaryButton} />
        </View>
      </AppScreen>
    );
  }

  if (route === 'finalizing') {
    return (
      <AppScreen contentStyle={[styles.analysis, styles.interviewAnalysis]} stickyHeader={false}>
        <AppIcon name="document" size={34} color={interviewPalette.accentBright} />
        <Text style={styles.interviewAnalysisTitle}>正在生成面试复盘</Text>
        <Text style={styles.interviewAnalysisCopy}>整理回答亮点、风险点和更好的表达方式。</Text>
        {completedSession ? <InterviewReportView api={completedSession.api} sessionId={completedSession.sessionId} /> : null}
        <AppButton title="返回面试首页" variant="secondary" onPress={closeReport} />
      </AppScreen>
    );
  }

  if (route === 'live') {
    if (!preparation?.scene) return null;
    const livePreparation = preparation as InterviewPreparationResult & { scene: NonNullable<InterviewPreparationResult['scene']> };
    return (
      <InterviewSession
        analytics={analytics}
        preparation={livePreparation}
        onFinished={(sessionId, api) => {
          setCompletedSession({ sessionId, api });
          setRoute('finalizing');
        }}
      />
    );
  }
  return null;
}

const styles = StyleSheet.create({
  intakeScreen: { flexGrow: 1, position: 'relative', justifyContent: 'center', paddingTop: 60, paddingBottom: 90, backgroundColor: ieltsPalette.canvas },
  intakeBody: { gap: 18 },
  homeScreen: { paddingBottom: 48, gap: 18, backgroundColor: ieltsPalette.canvas },
  setupScreen: { backgroundColor: ieltsPalette.canvas },
  setupBackRow: { height: 56, marginHorizontal: -22, marginTop: -18, paddingHorizontal: 22, justifyContent: 'center', backgroundColor: ieltsPalette.canvas },
  fixedSetupBackRow: { marginHorizontal: 0, marginTop: 0 },
  ieltsSetupStartButton: { borderColor: ieltsPalette.purple, backgroundColor: ieltsPalette.purple, shadowColor: ieltsPalette.purple, shadowOffset: { width: 0, height: 6 }, shadowOpacity: 0.2, shadowRadius: 14, elevation: 4 },
  ieltsStageScreen: { backgroundColor: ieltsPalette.canvas },
  ieltsCallScreen: { flex: 1, paddingHorizontal: 22, paddingTop: 24, paddingBottom: 22, backgroundColor: ieltsPalette.canvas },
  ieltsSessionHeader: { height: 38, alignItems: 'flex-end', justifyContent: 'center' },
  part2Screen: { flex: 1, backgroundColor: ieltsPalette.canvas },
  part2KeyboardView: { flex: 1 },
  part2Content: { flexGrow: 1, paddingHorizontal: 14, paddingTop: 12, paddingBottom: 120, gap: 12 },
  part2Presence: { minHeight: 82, paddingHorizontal: 10, flexDirection: 'row', alignItems: 'center', gap: 11 },
  part2PresenceCopy: { minWidth: 0, flex: 1 },
  part2ExaminerImage: { width: 54, height: 64 },
  part2Timer: { marginTop: 2, color: ieltsPalette.text, fontSize: 18, lineHeight: 24, fontWeight: '600', fontVariant: ['tabular-nums'] },
  part2ExaminerName: { color: ieltsPalette.muted, fontSize: 10, lineHeight: 14, fontWeight: '600' },
  part2Instruction: { marginTop: 3, color: ieltsPalette.muted, fontSize: 11, lineHeight: 16, fontWeight: '400' },
  part2Workspace: { width: '100%', gap: 10 },
  part2CueCard: { width: '100%', padding: 14, gap: 8, borderWidth: 1, borderColor: ieltsPalette.border, borderRadius: 8, backgroundColor: ieltsPalette.paper, shadowColor: ieltsPalette.purple, shadowOffset: { width: 0, height: 5 }, shadowOpacity: 0.08, shadowRadius: 14, elevation: 2, boxShadow: '0px 5px 16px rgba(128, 96, 232, 0.08)' },
  part2Eyebrow: { color: ieltsPalette.muted, fontSize: 10, lineHeight: 14, fontWeight: '600', letterSpacing: 1.5 },
  part2CueTitle: { color: ieltsPalette.text, fontSize: 16, lineHeight: 22, fontWeight: '600' },
  part2ShouldSay: { marginTop: 2, color: ieltsPalette.text, fontSize: 13, lineHeight: 18, fontWeight: '600' },
  part2CuePoints: { gap: 7 },
  part2CuePointRow: { flexDirection: 'row', alignItems: 'flex-start', gap: 9 },
  part2Bullet: { width: 5, height: 5, marginTop: 7, borderRadius: 3, backgroundColor: ieltsPalette.purple },
  part2CuePoint: { flex: 1, color: ieltsPalette.muted, fontSize: 11, lineHeight: 16, fontWeight: '400' },
  part2NoteCard: { width: '100%', minHeight: 220, padding: 12, gap: 9, borderWidth: 1, borderColor: ieltsPalette.border, borderRadius: 8, backgroundColor: ieltsPalette.paper, shadowColor: ieltsPalette.purple, shadowOffset: { width: 0, height: 5 }, shadowOpacity: 0.06, shadowRadius: 14, elevation: 2, boxShadow: '0px 5px 16px rgba(128, 96,232,0.06)' },
  part2NoteHeader: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 10 },
  part2NoteTitleRow: { flexDirection: 'row', alignItems: 'center', gap: 7 },
  part2NoteTitle: { color: ieltsPalette.text, fontSize: 14, lineHeight: 20, fontWeight: '600' },
  part2NoteHint: { color: ieltsPalette.muted, fontSize: 10, lineHeight: 15, fontWeight: '400' },
  part2NoteInput: { minHeight: 166, paddingHorizontal: 11, paddingVertical: 10, color: ieltsPalette.text, fontSize: 14, lineHeight: 21, fontWeight: '400', borderRadius: 6, backgroundColor: ieltsPalette.purpleSoft, outlineWidth: 0 },
  part2LockedNote: { minHeight: 166, paddingHorizontal: 11, paddingVertical: 10, color: ieltsPalette.text, fontSize: 14, lineHeight: 21, borderRadius: 6, backgroundColor: ieltsPalette.purpleSoft },
  part2Footer: { paddingHorizontal: 22, paddingTop: 10, paddingBottom: 6, alignItems: 'center', backgroundColor: ieltsPalette.canvas },
  part2EndButton: { width: 58, height: 58, alignItems: 'center', justifyContent: 'center', borderRadius: 29, backgroundColor: colors.ink },
  ieltsHeader: { backgroundColor: ieltsPalette.canvas, borderBottomColor: ieltsPalette.border },
  goalRow: {
    minHeight: 92,
    paddingVertical: 10,
    flexDirection: 'row',
    borderWidth: 1,
    borderColor: ieltsPalette.border,
    borderRadius: 17,
    backgroundColor: ieltsPalette.paper,
    shadowColor: ieltsPalette.purple,
    shadowOffset: { width: 0, height: 5 },
    shadowOpacity: 0.08,
    shadowRadius: 14,
    elevation: 2,
    boxShadow: '0px 5px 16px rgba(128, 96, 232, 0.08)',
  },
  goalMetric: { minHeight: 66, flex: 1, alignItems: 'center', justifyContent: 'center', gap: 3, borderRightWidth: StyleSheet.hairlineWidth, borderRightColor: ieltsPalette.border },
  goalMetricLast: { borderRightWidth: 0 },
  goalLabel: { color: ieltsPalette.text, fontSize: 12, fontWeight: '500' },
  goalValueRow: { flexDirection: 'row', alignItems: 'center', gap: 2 },
  goalValue: { color: ieltsPalette.purpleDark, fontSize: 29, lineHeight: 35, fontWeight: '600', fontVariant: ['tabular-nums'] },
  goalSuffix: { color: ieltsPalette.muted, fontSize: 12, fontWeight: '500' },
  goalIcon: { width: 66, height: 66, marginHorizontal: -15, opacity: 0.68 },
  goalIconTarget: { transform: [{ translateY: 2 }, { scale: 1.9 }] },
  goalIconCalendar: { transform: [{ scale: 2 }] },
  goalIconFlame: { transform: [{ translateY: 6 }, { scale: 3.1 }] },
  intakeProgressRow: { position: 'absolute', top: 18, right: 22, left: 22, flexDirection: 'row', alignItems: 'center', gap: 12 },
  intakeProgressLabel: { width: 30, color: ieltsPalette.purpleDark, fontSize: 12, fontWeight: '600', fontVariant: ['tabular-nums'] },
  intakeProgressTrack: { height: 6, flex: 1, overflow: 'hidden', borderRadius: 3, backgroundColor: ieltsPalette.border },
  intakeProgressFill: { height: '100%', borderRadius: 3, backgroundColor: ieltsPalette.purple },
  intakeOptions: { gap: 10 },
  intakeOption: { minHeight: 88, paddingHorizontal: 16, paddingVertical: 14, flexDirection: 'row', alignItems: 'center', gap: 13, borderWidth: 1, borderColor: ieltsPalette.border, borderRadius: 15, backgroundColor: ieltsPalette.paper, shadowColor: ieltsPalette.purple, shadowOffset: { width: 0, height: 4 }, shadowOpacity: 0.05, shadowRadius: 12, elevation: 1 },
  intakeOptionSelected: { borderColor: ieltsPalette.borderStrong, backgroundColor: ieltsPalette.purpleSoft, borderWidth: 1.5 },
  intakeOptionNumber: { width: 48, color: '#B39AF5', fontSize: 27, lineHeight: 34, fontWeight: '500', fontVariant: ['tabular-nums'] },
  intakeOptionTitle: { color: ieltsPalette.text, fontSize: 18, lineHeight: 24, fontWeight: '600' },
  intakeOptionNote: { marginTop: 6, color: ieltsPalette.muted, fontSize: 13, lineHeight: 18, fontWeight: '400' },
  intakeCheckSpace: { width: 18 },
  intakeCheck: { width: 34, height: 34, alignItems: 'center', justifyContent: 'center', borderRadius: 17, backgroundColor: ieltsPalette.purple },
  intakeActions: { flexDirection: 'row', gap: 10, marginTop: 4 },
  intakeBackButton: { minWidth: 106, borderColor: ieltsPalette.borderStrong },
  intakeNextButton: { flex: 1, borderColor: ieltsPalette.purple, backgroundColor: ieltsPalette.purple },
  intakeError: { marginTop: 10, color: '#B42318', fontSize: 13, lineHeight: 18, textAlign: 'center' },
  pressed: { opacity: 0.78, transform: [{ scale: 0.985 }] },
  bandOptions: { flexDirection: 'row', gap: 8 },
  bandOption: { minHeight: 48, flex: 1, alignItems: 'center', justifyContent: 'center', borderWidth: 1, borderColor: colors.line, borderRadius: 12, backgroundColor: colors.white },
  selected: { borderColor: colors.ink, borderWidth: 2 },
  bandOptionText: { color: colors.muted, fontSize: 13, fontWeight: '500' },
  selectedText: { color: colors.ink },
  mockCard: { padding: 20, gap: 8, borderColor: ieltsPalette.borderStrong, backgroundColor: ieltsPalette.purpleSoft, shadowColor: ieltsPalette.purple, shadowOpacity: 0.14, boxShadow: '0px 6px 18px rgba(128, 96, 232, 0.14)' },
  mockTopRow: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  mockBadge: { width: 220, height: 220, marginRight: -66, marginVertical: -34, transform: [{ translateX: -30 }, { scale: 1.8 }] },
  ieltsMockPill: { alignSelf: 'flex-start', paddingVertical: 5, paddingHorizontal: 10, overflow: 'hidden', color: ieltsPalette.purpleDark, fontSize: 12, fontWeight: '600', borderRadius: 999, backgroundColor: '#EDE5FF' },
  mockTitle: { marginTop: 11, color: ieltsPalette.text, fontSize: 22, lineHeight: 29, fontWeight: '700' },
  mockCopy: { marginTop: 6, color: ieltsPalette.muted, fontSize: 13, lineHeight: 20, fontWeight: '400' },
  mockFooter: { marginTop: 8, paddingTop: 15, flexDirection: 'row', alignItems: 'center', gap: 12, borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: ieltsPalette.border },
  mockMetaLabel: { color: ieltsPalette.muted, fontSize: 10, fontWeight: '400' },
  mockMetaValue: { marginTop: 4, color: ieltsPalette.purpleDark, fontSize: 17, fontWeight: '700' },
  mockMetaNote: { marginTop: 3, color: ieltsPalette.muted, fontSize: 10, fontWeight: '400' },
  ieltsMockButton: { minWidth: 126, borderColor: ieltsPalette.purple, backgroundColor: ieltsPalette.purple },
  ieltsSectionTitle: { flexDirection: 'row', alignItems: 'center', gap: 10, marginTop: 2 },
  ieltsSectionMarker: { width: 7, height: 28, borderRadius: 4, backgroundColor: ieltsPalette.purple },
  ieltsSectionHeading: { color: ieltsPalette.text, fontSize: 21, lineHeight: 28, fontWeight: '700' },
  partCard: { minHeight: 128, flexDirection: 'row', alignItems: 'center', gap: 13 },
  ieltsPartCard: { minHeight: 132, borderColor: ieltsPalette.border, backgroundColor: ieltsPalette.paper, shadowColor: ieltsPalette.purple, shadowOpacity: 0.08, boxShadow: '0px 5px 16px rgba(128, 96, 232, 0.08)' },
  ieltsPartNumber: { width: 62, color: '#A98DF3', fontSize: 44, lineHeight: 51, fontWeight: '500', fontVariant: ['tabular-nums'] },
  ieltsPartPill: { alignSelf: 'flex-start', paddingVertical: 6, paddingHorizontal: 12, color: ieltsPalette.purpleDark, fontSize: 13, lineHeight: 18, fontWeight: '600', borderRadius: 999, backgroundColor: '#F0E9FF' },
  ieltsPartTitle: { marginTop: 8, color: ieltsPalette.text, fontSize: 20, lineHeight: 26, fontWeight: '700' },
  ieltsPartNote: { marginTop: 4, color: ieltsPalette.muted, fontSize: 14, lineHeight: 20, fontWeight: '400' },
  ieltsPartArrow: { width: 42, height: 42, alignItems: 'center', justifyContent: 'center', borderRadius: 21, backgroundColor: '#F0E9FF' },
  topicsScreen: { gap: 18, paddingBottom: 140, backgroundColor: ieltsPalette.canvas },
  topicHero: { paddingTop: 2, paddingBottom: 20, gap: 10, borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: colors.line },
  topicHeroTitle: { color: ieltsPalette.text, fontSize: 30, lineHeight: 38, fontWeight: '700' },
  topicHeroCopy: { maxWidth: 320, color: colors.muted, fontSize: 13, lineHeight: 20, fontWeight: '400' },
  randomTopicButton: { minHeight: 44, alignSelf: 'flex-start', paddingHorizontal: 15, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8, borderWidth: 1, borderColor: colors.line, borderRadius: 22, backgroundColor: colors.white },
  randomTopicButtonText: { color: ieltsPalette.text, fontSize: 14, fontWeight: '600' },
  topicSearch: { minHeight: 52, paddingHorizontal: 15, flexDirection: 'row', alignItems: 'center', gap: 9, borderWidth: 1, borderColor: '#D7C7FF', borderRadius: 26, backgroundColor: colors.white, shadowColor: ieltsPalette.purple, shadowOffset: { width: 0, height: 4 }, shadowOpacity: 0.06, shadowRadius: 12, elevation: 1 },
  topicSearchInput: { minWidth: 0, flex: 1, paddingVertical: 10, color: ieltsPalette.text, fontSize: 14, fontWeight: '400' },
  topicSearchReset: { width: 32, height: 32, alignItems: 'center', justifyContent: 'center' },
  topicFiltersScroll: { height: 38, flexGrow: 0 },
  topicFilters: { alignItems: 'center', paddingRight: 16, gap: 6 },
  topicFilter: { minHeight: 38, paddingHorizontal: 14, alignItems: 'center', justifyContent: 'center', borderWidth: 1, borderColor: 'transparent', borderRadius: 19 },
  topicFilterActive: { borderColor: ieltsPalette.purple, backgroundColor: ieltsPalette.purple },
  topicFilterText: { color: colors.muted, fontSize: 12, fontWeight: '500' },
  topicFilterTextActive: { color: colors.white, fontWeight: '600' },
  topicTable: { gap: 7, backgroundColor: 'transparent' },
  topicTableHeader: { minHeight: 36, paddingHorizontal: 14, flexDirection: 'row', alignItems: 'center', gap: 8 },
  topicTableHeaderText: { color: ieltsPalette.purpleDark, fontSize: 10, lineHeight: 13, fontWeight: '600' },
  topicTableRow: { minHeight: 112, paddingHorizontal: 14, paddingVertical: 14, flexDirection: 'row', alignItems: 'center', gap: 8, borderWidth: 1, borderColor: '#E8DEFF', borderRadius: 17, backgroundColor: 'rgba(255,255,255,0.92)', shadowColor: ieltsPalette.purple, shadowOffset: { width: 0, height: 5 }, shadowOpacity: 0.07, shadowRadius: 14, elevation: 1 },
  topicTableRowPressed: { backgroundColor: '#F8F4FF', borderColor: '#CDBAFF' },
  topicColumnMain: { minWidth: 0, flex: 1.22 },
  topicColumnPractice: { minWidth: 0, flex: 0.82 },
  topicColumnResult: { minWidth: 0, flex: 0.72 },
  topicColumnArrow: { width: 18, alignItems: 'flex-end' },
  topicCategory: { alignSelf: 'flex-start', marginBottom: 7, paddingVertical: 3, paddingHorizontal: 7, overflow: 'hidden', color: ieltsPalette.purpleDark, fontSize: 10, lineHeight: 14, fontWeight: '600', borderRadius: 10, backgroundColor: '#F0E9FF' },
  topicTitle: { color: ieltsPalette.text, fontSize: 15, lineHeight: 19, fontWeight: '700' },
  topicQuestionCount: { marginTop: 4, color: colors.muted, fontSize: 10, lineHeight: 13, fontWeight: '400' },
  topicPracticeTitle: { color: ieltsPalette.text, fontSize: 12, lineHeight: 16, fontWeight: '600' },
  topicPracticeNote: { marginTop: 6, color: colors.subtle, fontSize: 9, lineHeight: 13, fontWeight: '400' },
  topicResult: { color: ieltsPalette.purpleDark, fontSize: 16, lineHeight: 20, fontWeight: '700' },
  topicResultNote: { marginTop: 6, color: colors.subtle, fontSize: 9, lineHeight: 13, fontWeight: '400' },
  topicEmpty: { minHeight: 130, padding: 20, alignItems: 'center', justifyContent: 'center', borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: colors.line },
  topicEmptyTitle: { color: ieltsPalette.text, fontSize: 15, fontWeight: '600' },
  topicEmptyCopy: { marginTop: 6, color: colors.muted, fontSize: 12, lineHeight: 18, fontWeight: '400', textAlign: 'center' },
  topicPagination: { minHeight: 48, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8 },
  topicPaginationArrow: { width: 38, height: 38, alignItems: 'center', justifyContent: 'center', borderWidth: 1, borderColor: '#E3D8FF', borderRadius: 19, backgroundColor: colors.white },
  topicPaginationDisabled: { opacity: 0.45 },
  topicPaginationPages: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  topicPaginationPage: { width: 38, height: 38, alignItems: 'center', justifyContent: 'center', borderRadius: 19 },
  topicPaginationPageActive: { backgroundColor: ieltsPalette.purple, shadowColor: ieltsPalette.purple, shadowOffset: { width: 0, height: 4 }, shadowOpacity: 0.2, shadowRadius: 8, elevation: 2 },
  topicPaginationPageText: { color: ieltsPalette.muted, fontSize: 14, fontWeight: '500' },
  topicPaginationPageTextActive: { color: colors.white, fontWeight: '700' },
  topicPageCount: { minWidth: 38, color: ieltsPalette.muted, fontSize: 12, textAlign: 'center' },
  topicCount: { color: ieltsPalette.muted, fontSize: 11, fontWeight: '400' },
  examinerScreen: { gap: 18, paddingBottom: 130, backgroundColor: ieltsPalette.canvas },
  examinerIntro: { gap: 6 },
  examinerPart: { color: ieltsPalette.text, fontSize: 20, lineHeight: 27, fontWeight: '700' },
  examinerIntroCopy: { color: ieltsPalette.muted, fontSize: 13, lineHeight: 20, fontWeight: '400' },
  examinerGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: 9 },
  examinerCard: { position: 'relative', width: '48%', minHeight: 158, padding: 12, alignItems: 'center', gap: 5, borderWidth: 1, borderColor: ieltsPalette.border, borderRadius: 14, backgroundColor: colors.white },
  examinerCardSelected: { borderWidth: 2, borderColor: ieltsPalette.purple, backgroundColor: ieltsPalette.purpleSoft },
  examinerImage: { width: 72, height: 82 },
  examinerName: { color: colors.ink, fontSize: 14, fontWeight: '500' },
  examinerAccent: { color: ieltsPalette.muted, fontSize: 11, fontWeight: '400' },
  examinerSelected: { position: 'absolute', top: 9, right: 9, width: 24, height: 24, alignItems: 'center', justifyContent: 'center', borderRadius: 12, backgroundColor: ieltsPalette.purple },
  examinerDetail: { gap: 6, borderColor: ieltsPalette.border, backgroundColor: colors.white },
  examinerDetailTitle: { color: ieltsPalette.text, fontSize: 17, lineHeight: 23, fontWeight: '600' },
  examinerDetailCopy: { color: ieltsPalette.muted, fontSize: 13, lineHeight: 20, fontWeight: '400' },
  deviceCheck: { flexDirection: 'row', alignItems: 'center', backgroundColor: colors.greenSoft },
  sessionExaminer: { alignItems: 'center', gap: 6 },
  examinerLarge: { width: 112, height: 132 },
  examinerTitle: { color: colors.ink, fontSize: 17, fontWeight: '600' },
  questionCard: { minHeight: 190, justifyContent: 'center' },
  questionNumber: { color: colors.subtle, fontSize: 10, fontWeight: '500', letterSpacing: 1.5 },
  question: { color: colors.ink, fontSize: 21, lineHeight: 30, fontWeight: '500' },
  callControls: { flexDirection: 'row', justifyContent: 'center', gap: 14 },
  roundControl: { width: 60, height: 60, alignItems: 'center', justifyContent: 'center', borderWidth: 1, borderColor: colors.line, borderRadius: 30 },
  roundControlOn: { backgroundColor: '#E9E9E5' },
  analysis: { alignItems: 'center', justifyContent: 'center', paddingBottom: 80 },
  ieltsEvaluationRoot: { flex: 1, position: 'relative', backgroundColor: colors.white },
  ieltsEvaluationError: { flex: 1, paddingHorizontal: 28, alignItems: 'center', justifyContent: 'center', gap: 14, backgroundColor: colors.white },
  analysisTitle: { color: colors.ink, fontSize: 25, lineHeight: 34, fontWeight: '600', textAlign: 'center' },
  progressText: { color: colors.subtle, fontSize: 12, fontWeight: '300', fontVariant: ['tabular-nums'] },
  reportScreen: { paddingTop: 32, paddingBottom: 44, justifyContent: 'center', gap: 14 },
  practiceScoreScreen: { paddingHorizontal: 0, paddingTop: 0, paddingBottom: 0, backgroundColor: ieltsPalette.canvas },
  bandHero: { minHeight: 204, paddingVertical: 25, alignItems: 'center', justifyContent: 'center', gap: 4, borderWidth: 1, borderColor: ieltsPalette.borderStrong, borderRadius: 24, backgroundColor: ieltsPalette.purple, shadowColor: ieltsPalette.purple, shadowOffset: { width: 0, height: 10 }, shadowOpacity: 0.2, shadowRadius: 22, elevation: 5, boxShadow: '0px 10px 24px rgba(128, 96, 232, 0.20)' },
  bandEyebrow: { color: '#E8DEFF', fontSize: 12, fontWeight: '600', letterSpacing: 1.1 },
  bandScore: { color: colors.white, fontSize: 82, lineHeight: 88, fontWeight: '600', letterSpacing: -5 },
  bandLabel: { color: '#E8DEFF', fontSize: 10, fontWeight: '500', letterSpacing: 1.5 },
  metrics: { flexDirection: 'row', gap: 10 },
  reportMetric: { minHeight: 102, flex: 1, padding: 16, justifyContent: 'space-between', borderWidth: 1, borderColor: ieltsPalette.border, borderRadius: 17, backgroundColor: ieltsPalette.paper, shadowColor: ieltsPalette.purple, shadowOffset: { width: 0, height: 5 }, shadowOpacity: 0.08, shadowRadius: 14, elevation: 2, boxShadow: '0px 5px 16px rgba(128, 96, 232, 0.08)' },
  reportMetricLabel: { color: ieltsPalette.muted, fontSize: 12, lineHeight: 17, fontWeight: '500' },
  reportMetricValue: { color: ieltsPalette.purpleDark, fontSize: 31, lineHeight: 37, fontWeight: '600', fontVariant: ['tabular-nums'] },
  reportActions: { marginTop: 6, flexDirection: 'row', gap: 10 },
  reportSecondaryButton: { flex: 1, borderColor: ieltsPalette.borderStrong, backgroundColor: ieltsPalette.paper },
  reportPrimaryButton: { flex: 1, borderColor: ieltsPalette.purple, backgroundColor: ieltsPalette.purple },
  interviewInputScreen: {
    flexGrow: 1,
    gap: 16,
    paddingTop: 18,
    paddingBottom: 118,
    backgroundColor: interviewPalette.canvas,
  },
  interviewHeroHeader: {
    height: 104,
    position: 'relative',
    overflow: 'hidden',
    backgroundColor: '#164C87',
  },
  interviewHero: {
    position: 'absolute',
    top: -34,
    right: -4,
    left: -4,
    height: 184,
  },
  interviewHeader: {
    height: 92,
    backgroundColor: 'transparent',
    borderBottomWidth: 0,
  },
  interviewPanel: {
    padding: 16,
    gap: 14,
    borderWidth: 1,
    borderColor: interviewPalette.border,
    borderRadius: 20,
    backgroundColor: interviewPalette.paper,
  },
  interviewUploadPanel: {
    minHeight: 112,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 14,
  },
  interviewPanelSelected: {
    borderColor: interviewPalette.borderStrong,
    backgroundColor: interviewPalette.paperStrong,
  },
  interviewResumeAsset: { width: 64, height: 72 },
  interviewPanelCopy: { minWidth: 0, flex: 1, gap: 5 },
  interviewTitleRow: { flexDirection: 'row', alignItems: 'center', gap: 7 },
  interviewPanelTitle: { color: interviewPalette.text, fontSize: 17, lineHeight: 23, fontWeight: '600' },
  interviewPanelNote: { color: interviewPalette.muted, fontSize: 12, lineHeight: 18, fontWeight: '400' },
  interviewReviewLead: { color: interviewPalette.muted, fontSize: 14, lineHeight: 21, fontWeight: '400' },
  interviewResumeInput: { minHeight: 132, paddingHorizontal: 14, paddingVertical: 12, color: interviewPalette.text, fontSize: 14, lineHeight: 21, borderWidth: 1, borderColor: interviewPalette.border, borderRadius: 14, backgroundColor: '#FFFFFF', outlineWidth: 0 },
  interviewResumeModeTabs: { flexDirection: 'row', padding: 3, gap: 4, borderRadius: 12, backgroundColor: interviewPalette.paperStrong },
  interviewResumeModeTab: { flex: 1, minHeight: 38, alignItems: 'center', justifyContent: 'center', borderRadius: 9 },
  interviewResumeModeTabSelected: { backgroundColor: interviewPalette.accent },
  interviewResumeModeText: { color: interviewPalette.muted, fontSize: 13, fontWeight: '500' },
  interviewResumeModeTextSelected: { color: colors.white },
  interviewResumeFileButton: { minHeight: 48, paddingHorizontal: 13, flexDirection: 'row', alignItems: 'center', gap: 9, borderWidth: 1, borderColor: interviewPalette.border, borderRadius: 13, backgroundColor: interviewPalette.paperStrong },
  interviewResumeFileText: { flex: 1, color: interviewPalette.accent, fontSize: 13, lineHeight: 18, fontWeight: '500' },
  interviewPanelHeader: { minHeight: 74, flexDirection: 'row', alignItems: 'center', gap: 12 },
  interviewCompanyAsset: { width: 72, height: 84, marginRight: -4 },
  interviewJdInput: {
    minHeight: 126,
    paddingHorizontal: 14,
    paddingVertical: 12,
    color: interviewPalette.text,
    fontSize: 14,
    lineHeight: 21,
    fontWeight: '400',
    borderWidth: 1,
    borderColor: interviewPalette.border,
    borderRadius: 14,
    backgroundColor: '#FFFFFF',
    outlineWidth: 0,
  },
  interviewReviewInput: { minHeight: 50, paddingHorizontal: 13, paddingVertical: 11, color: interviewPalette.text, fontSize: 14, lineHeight: 20, borderWidth: 1, borderColor: interviewPalette.border, borderRadius: 13, backgroundColor: '#FFFFFF', outlineWidth: 0 },
  interviewReviewTextarea: { minHeight: 100, paddingHorizontal: 13, paddingVertical: 11, color: interviewPalette.text, fontSize: 14, lineHeight: 21, borderWidth: 1, borderColor: interviewPalette.border, borderRadius: 13, backgroundColor: '#FFFFFF', outlineWidth: 0 },
  interviewReviewActions: { flexDirection: 'row', gap: 10, paddingBottom: 20 },
  interviewReviewSecondaryButton: { flex: 1, borderColor: interviewPalette.borderStrong, backgroundColor: interviewPalette.paper },
  interviewReviewPrimaryButton: { flex: 1, borderColor: interviewPalette.accent, backgroundColor: interviewPalette.accent },
  interviewError: { color: '#B42318', fontSize: 13, lineHeight: 19, fontWeight: '500' },
  interviewBriefcaseAsset: { width: 72, height: 84, marginRight: -4 },
  interviewDifficultyOptions: { flexDirection: 'row', gap: 8 },
  interviewDifficulty: {
    minHeight: 78,
    flex: 1,
    paddingHorizontal: 9,
    paddingVertical: 11,
    justifyContent: 'center',
    borderWidth: 1,
    borderColor: interviewPalette.border,
    borderRadius: 14,
    backgroundColor: '#F1F7FE',
  },
  interviewDifficultySelected: {
    borderColor: interviewPalette.accentBright,
    backgroundColor: '#D8EBFF',
  },
  interviewDifficultyTitleRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 4 },
  interviewDifficultyTitle: { color: interviewPalette.muted, fontSize: 15, lineHeight: 20, fontWeight: '600', textAlign: 'center' },
  interviewDifficultyTitleSelected: { color: interviewPalette.text },
  interviewRecommended: { color: '#FFD166', fontSize: 13, lineHeight: 16 },
  interviewDifficultyNote: { marginTop: 5, color: interviewPalette.subtle, fontSize: 10, lineHeight: 14, textAlign: 'center' },
  interviewDifficultyNoteSelected: { color: interviewPalette.muted },
  interviewStartButton: {
    marginTop: 2,
    borderColor: interviewPalette.accent,
    backgroundColor: interviewPalette.accent,
    shadowColor: interviewPalette.accent,
    shadowOffset: { width: 0, height: 7 },
    shadowOpacity: 0.25,
    shadowRadius: 15,
    elevation: 5,
  },
  interviewCallScreen: { flex: 1, paddingHorizontal: 22, paddingTop: 24, paddingBottom: 22, backgroundColor: interviewPalette.session },
  interviewAnalysis: { flexGrow: 1, gap: 12, backgroundColor: interviewPalette.canvas },
  interviewAnalysisTitle: { color: interviewPalette.text, fontSize: 25, lineHeight: 34, fontWeight: '600', textAlign: 'center' },
  interviewAnalysisCopy: { maxWidth: 300, color: interviewPalette.muted, fontSize: 14, lineHeight: 21, textAlign: 'center' },
  interviewProgressText: { color: interviewPalette.subtle, fontSize: 12, fontWeight: '400', fontVariant: ['tabular-nums'] },
  interviewReportScreen: { flexGrow: 1, gap: 16, paddingTop: 34, paddingBottom: 48, backgroundColor: interviewPalette.canvas },
  interviewScore: { color: interviewPalette.accentBright, fontSize: 72, lineHeight: 80, fontWeight: '600', fontVariant: ['tabular-nums'] },
  interviewReportMuted: { color: interviewPalette.muted, fontSize: 13, lineHeight: 19 },
  interviewMetrics: { flexDirection: 'row', gap: 8 },
  interviewMetric: { minHeight: 88, flex: 1, padding: 12, justifyContent: 'space-between', borderWidth: 1, borderColor: interviewPalette.border, borderRadius: 15, backgroundColor: interviewPalette.paper },
  interviewMetricLabel: { color: interviewPalette.muted, fontSize: 11, lineHeight: 16 },
  interviewMetricValue: { color: interviewPalette.accentBright, fontSize: 28, lineHeight: 34, fontWeight: '600', fontVariant: ['tabular-nums'] },
  interviewReportCard: { padding: 16, gap: 8, borderWidth: 1, borderColor: interviewPalette.border, borderRadius: 16, backgroundColor: interviewPalette.paper },
  interviewReportTitle: { color: interviewPalette.text, fontSize: 15, lineHeight: 21, fontWeight: '600' },
  interviewReportBody: { color: interviewPalette.muted, fontSize: 13, lineHeight: 20 },
  interviewReportActions: { marginTop: 4, flexDirection: 'row', gap: 10 },
  interviewReportSecondaryButton: { flex: 1, borderColor: interviewPalette.borderStrong, backgroundColor: interviewPalette.paper },
  interviewReportPrimaryButton: { flex: 1, borderColor: interviewPalette.accent, backgroundColor: interviewPalette.accent },
  interviewSteps: { flexDirection: 'row', gap: 7 },
  interviewStep: { flex: 1, padding: 11, gap: 6, borderRadius: 13, backgroundColor: colors.soft },
  stepNumber: { color: colors.subtle, fontSize: 10, fontWeight: '500' },
  stepText: { color: colors.ink, fontSize: 11, lineHeight: 16, fontWeight: '500' },
  formGroup: { gap: 8 },
  fieldLabel: { color: colors.ink, fontSize: 13, fontWeight: '500' },
  input: { minHeight: 52, paddingHorizontal: 14, color: colors.ink, fontSize: 15, fontWeight: '300', borderWidth: 1, borderColor: colors.line, borderRadius: 13, backgroundColor: colors.white },
  uploadCard: { minHeight: 78, padding: 13, flexDirection: 'row', alignItems: 'center', gap: 12, borderWidth: 1, borderColor: colors.line, borderRadius: 14 },
  scoreHero: { minHeight: 138, alignItems: 'center', justifyContent: 'center', paddingVertical: 10 },
  score: { color: colors.ink, fontSize: 72, fontWeight: '600', letterSpacing: -4 },
});
