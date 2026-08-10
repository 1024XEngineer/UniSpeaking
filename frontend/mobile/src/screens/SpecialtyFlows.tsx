import { Image } from 'expo-image';
import { useEffect, useRef, useState } from 'react';
import { KeyboardAvoidingView, Platform, Pressable, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import {
  AppButton,
  AppIcon,
  AppScreen,
  Card,
  HeaderIconButton,
  MainModuleHeader,
  ProgressBar,
  uiStyles,
} from '@/components/ui';
import { ieltsParts, interviewQuestions } from '@/data/content';
import { selectCallCaption } from '@/screens/ConversationScreen';
import { useIeltsFlowController } from '@/features/ielts/useIeltsFlowController';
import { useIeltsSession } from '@/features/ielts/useIeltsSession';
import { ieltsExaminers, toApiPart, type MobileIeltsPartId } from '@/features/ielts/ieltsMappings';
import type { IeltsTopicSummary } from '@/features/ielts/types';
import { useAppModel } from '@/model/AppModel';
import { useLearningStage } from '@/navigation/learningStage';
import { colors, examinerAssets, ieltsAssets, interviewAssets, levels } from '@/theme/tokens';

import { CallExperience } from './ConversationScreen';

type IeltsRoute =
  | 'intake'
  | 'home'
  | 'topics'
  | 'session'
  | 'analysis'
  | 'report';

type IeltsPartId = MobileIeltsPartId;

const examiners = ieltsExaminers.map((item) => ({
  ...item,
  image: examinerAssets[item.id],
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
  onFinish,
}: {
  examiner: (typeof examiners)[number];
  part: 'p1' | 'p3';
  ieltsId: string;
  voiceId: string;
  onFinish: (sessionId: string | null) => void;
}) {
  const session = useIeltsSession({ ieltsId, voiceId, part: toApiPart(part) });
  const partThreeTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const lastInputReadyTick = useRef(0);
  const caption = selectCallCaption(
    session.snapshot,
    examiner.name,
    session.statusLabel,
  );
  const dialogueState = session.snapshot.ieltsDialogueState;
  const progressLabel = dialogueState
    ? `${dialogueState.answeredQuestions} / ${dialogueState.totalQuestions} 题`
    : session.statusLabel;

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
    if (!session.snapshot.ieltsDialogueCompleted) return;
    if (partThreeTimerRef.current) {
      clearInterval(partThreeTimerRef.current);
      partThreeTimerRef.current = null;
    }
  }, [session.snapshot.ieltsDialogueCompleted]);

  return (
    <SafeAreaView edges={['top', 'bottom']} style={styles.ieltsCallScreen}>
      <CallExperience
        endAccessibilityLabel="结束本题并进入下一题"
        endControlIcon="arrow"
        initialSubtitles={false}
        onEnd={() => {
          void session.end().finally(() => onFinish(session.sessionId));
        }}
        participant={examiner}
        showMuteControl={false}
        showTranslationControl={false}
        statusText={`${part === 'p1' ? 'Part 1' : 'Part 3'} · ${progressLabel}`}
        transcriptEnglish={caption.text}
        transcriptSpeaker={caption.speaker}
        userTranscript={session.snapshot.userTranscript}
      />
    </SafeAreaView>
  );
}


function formatSessionDuration(seconds: number) {
  const minutes = Math.floor(seconds / 60).toString().padStart(2, '0');
  const remainingSeconds = (seconds % 60).toString().padStart(2, '0');
  return `${minutes}:${remainingSeconds}`;
}

type Part2Phase = 'INTRODUCTION' | 'PREPARATION' | 'STARTING' | 'LONG_TURN' | 'FINISHING';

function IeltsPart2Session({
  examiner,
  cueCard,
  ieltsId,
  voiceId,
  onFinish,
}: {
  examiner: (typeof examiners)[number];
  cueCard: { title: string; points: string[] };
  ieltsId: string;
  voiceId: string;
  onFinish: (sessionId: string | null) => void;
}) {
  const session = useIeltsSession({ ieltsId, voiceId, part: 'PART_2' });
  const [phase, setPhase] = useState<Part2Phase>('INTRODUCTION');
  const [prepRemaining, setPrepRemaining] = useState(60);
  const [longTurnRemaining, setLongTurnRemaining] = useState(120);
  const [notesLocked, setNotesLocked] = useState(false);
  const [note, setNote] = useState('');
  const [sessionError, setSessionError] = useState<string | null>(null);
  const phaseRef = useRef<Part2Phase>('INTRODUCTION');
  const prevStateRef = useRef(session.snapshot.state);
  const prepTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const longTurnTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const silenceTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const finishTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const lastInputReadyTick = useRef(0);

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
      void session.end().finally(() => onFinish(session.sessionId));
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

  const caption = selectCallCaption(
    session.snapshot,
    examiner.name,
    session.statusLabel,
  );
  const showLongTurn = phase === 'STARTING' || phase === 'LONG_TURN' || phase === 'FINISHING';
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

  if (showLongTurn) {
    return (
      <SafeAreaView edges={['top', 'bottom']} style={styles.ieltsCallScreen}>
        <CallExperience
          endAccessibilityLabel="结束 Part 2"
          endControlIcon="arrow"
          initialSubtitles={false}
          onEnd={() => {
            if (phaseRef.current === 'LONG_TURN') {
              finishPartTwoAfterSilence();
              return;
            }
            void session.end().finally(() => onFinish(session.sessionId));
          }}
          participant={examiner}
          showMuteControl={false}
          showTranslationControl={false}
          statusText={`Part 2 · ${statusText}`}
          transcriptEnglish={caption.text}
          transcriptSpeaker={caption.speaker}
          userTranscript={session.snapshot.userTranscript}
        />
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView edges={['top', 'bottom']} style={styles.part2Screen}>
      <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : undefined} style={styles.part2KeyboardView}>
        <ScrollView
          contentContainerStyle={styles.part2Content}
          keyboardShouldPersistTaps="handled"
          showsVerticalScrollIndicator={false}
        >
          <View style={styles.part2Presence}>
            <Image source={examiner.image} style={styles.part2ExaminerImage} contentFit="contain" />
            <Text style={styles.part2Timer}>{statusText}</Text>
            <Text style={styles.part2ExaminerName}>{examiner.name}</Text>
            <Text style={styles.part2Instruction}>
              {phase === 'PREPARATION'
                ? '你有 1 分钟准备时间，可以根据题卡记录关键词。'
                : '请等待考官说明 Part 2 规则。'}
            </Text>
            {sessionError ? <Text style={styles.intakeError}>{sessionError}</Text> : null}
            {session.startupError ? <Text style={styles.intakeError}>{session.startupError}</Text> : null}
          </View>

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
                <AppIcon name="edit" size={17} color={ieltsPalette.purpleDark} />
                <Text style={styles.part2NoteTitle}>答题笔记</Text>
              </View>
              <Text style={styles.part2NoteHint}>
                {notesLocked ? '准备已结束' : '准备结束后自动锁定'}
              </Text>
            </View>
            <TextInput
              editable={!notesLocked}
              multiline
              onChangeText={setNote}
              placeholder="记录关键词、人物、地点、原因或例子……"
              placeholderTextColor={ieltsPalette.muted}
              style={styles.part2NoteInput}
              textAlignVertical="top"
              value={note}
            />
          </View>
        </ScrollView>

        {phase === 'PREPARATION' ? (
          <View style={styles.part2Footer}>
            <Pressable
              accessibilityRole="button"
              accessibilityLabel="提前开始作答"
              onPress={beginPartTwoAnswer}
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

export function IeltsFlow({ onExit, onViewDetails }: { onExit: () => void; onViewDetails?: () => void }) {
  const { addIeltsRecord } = useAppModel();
  const { setImmersiveLearning } = useLearningStage();
  const ielts = useIeltsFlowController();
  const [route, setRoute] = useState<IeltsRoute>('intake');
  const [target, setTarget] = useState('7.0');
  const [startingLevel, setStartingLevel] = useState<string>(levels[2].id);
  const [intakeStep, setIntakeStep] = useState(0);
  const [intakeSaving, setIntakeSaving] = useState(false);
  const [intakeError, setIntakeError] = useState<string | null>(null);
  const [part, setPart] = useState<IeltsPartId>('p2');
  const [topic, setTopic] = useState('');
  const [selectedTopicId, setSelectedTopicId] = useState<string | null>(null);
  const [fullMock, setFullMock] = useState(false);
  const [topicCategory, setTopicCategory] = useState('ALL');
  const [topicQuery, setTopicQuery] = useState('');
  const [topicPage, setTopicPage] = useState(1);
  const [examiner, setExaminer] = useState<(typeof examiners)[number]>(() => randomExaminer());
  const [progress, setProgress] = useState(0);
  const [activeSessionId, setActiveSessionId] = useState<string | null>(null);
  const [evaluationError, setEvaluationError] = useState<string | null>(null);

  const beginSession = async (input: {
    nextPart: IeltsPartId | 'mock';
    topicItem: IeltsTopicSummary | null;
    random: boolean;
  }) => {
    const nextExaminer = randomExaminer();
    setExaminer(nextExaminer);
    const scene = await ielts.prepareSession({
      part: input.nextPart,
      topicId: input.topicItem?.id ?? null,
      random: input.random,
      examiner: nextExaminer,
    });
    setTopic(input.topicItem?.title ?? scene.title);
    setSelectedTopicId(input.topicItem?.id ?? scene.selectedTopicId ?? null);
    setActiveSessionId(null);
    setRoute('session');
  };

  const startSinglePart = async (topicItem: IeltsTopicSummary | null, random = false) => {
    setFullMock(false);
    setPart(part);
    try {
      await beginSession({ nextPart: part, topicItem, random });
    } catch {
      // prepareSession 已写入 sessionError
    }
  };

  const startFullMock = async () => {
    setFullMock(true);
    setPart('p1');
    setProgress(0);
    try {
      await beginSession({ nextPart: 'mock', topicItem: null, random: true });
    } catch {
      // prepareSession 已写入 sessionError
    }
  };

  useEffect(() => {
    setImmersiveLearning(route === 'session' || route === 'analysis' || route === 'report');
  }, [route, setImmersiveLearning]);

  useEffect(() => () => setImmersiveLearning(false), [setImmersiveLearning]);

  useEffect(() => {
    if (route !== 'topics') return;
    const timer = setTimeout(() => {
      void ielts.loadTopics(part, topicCategory, topicQuery, topicPage);
    }, 250);
    return () => clearTimeout(timer);
  }, [route, part, topicCategory, topicQuery, topicPage, ielts]);

  useEffect(() => {
    if (route !== 'home') return;
    void ielts.refreshSettings();
  }, [route, ielts]);

  useEffect(() => {
    if (ielts.settings?.targetScore != null) {
      setTarget(String(ielts.settings.targetScore));
    }
    if (ielts.settings?.examinerId) {
      const saved = examiners.find((item) => item.id === ielts.settings?.examinerId);
      if (saved) setExaminer(saved);
    }
  }, [ielts.settings]);

  useEffect(() => {
    if (route !== 'analysis') return;
    const ieltsId = ielts.generated?.ieltsId;
    if (!ieltsId || !activeSessionId) {
      const timer = setInterval(() => setProgress((current) => Math.min(100, current + 14)), 220);
      return () => clearInterval(timer);
    }
    let cancelled = false;
    setProgress(12);
    void ielts.finalizeEvaluation(ieltsId, activeSessionId)
      .then(() => {
        if (!cancelled) setProgress(100);
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          setEvaluationError(error instanceof Error ? error.message : '评估生成失败');
          setProgress(100);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [route, ielts.finalizeEvaluation, ielts.generated?.ieltsId, activeSessionId]);

  useEffect(() => {
    if (route === 'analysis' && progress >= 100) {
      const timer = setTimeout(() => setRoute('report'), 300);
      return () => clearTimeout(timer);
    }
  }, [progress, route]);

  if (route === 'intake') {
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
                void ielts.saveTargetScore(target)
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
                  <Text style={styles.topicResultNote}>{item.latestPerformanceSummary ?? (practiced ? '已练习' : '未练习')}</Text>
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
            {Array.from({ length: totalTopicPages }, (_, index) => index + 1).map((page) => (
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

  if (route === 'session') {
    const ieltsId = ielts.generated?.ieltsId;
    const voiceId = examiner.voiceId;
    const finishSession = (sessionId: string | null) => {
      if (sessionId) setActiveSessionId(sessionId);
      const currentPartIndex = ieltsPartOrder.indexOf(part);
      const nextPart = ieltsPartOrder[currentPartIndex + 1];
      if (fullMock && nextPart && ielts.generated) {
        setPart(nextPart);
        void beginSession({ nextPart, topicItem: null, random: true });
        return;
      }
      setProgress(0);
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
      const question = ielts.training?.questions[0];
      const cueCard = {
        title: question?.questionText ?? ielts.generated.title,
        points: question?.cuePoints?.length
          ? question.cuePoints
          : ['What it is', 'When or where you experienced it', 'Who was involved', 'And explain why it is important to you'],
      };
      return (
        <IeltsPart2Session
          cueCard={cueCard}
          examiner={examiner}
          ieltsId={ieltsId}
          onFinish={finishSession}
          voiceId={voiceId}
        />
      );
    }
    return (
      <IeltsSession
        examiner={examiner}
        part={part}
        ieltsId={ieltsId}
        voiceId={voiceId}
        onFinish={finishSession}
      />
    );
  }

  if (route === 'analysis') {
    return (
      <AppScreen contentStyle={[styles.analysis, styles.ieltsStageScreen]} stickyHeader={false}>
        <AppIcon name="sliders" size={32} />
        <Text style={styles.analysisTitle}>正在分析你的口语表现</Text>
        <Text style={uiStyles.muted}>{evaluationError ?? '评估流利度、词汇、语法和发音，并生成可复练的表达。'}</Text>
        <ProgressBar value={progress} />
        <Text style={styles.progressText}>{progress}%</Text>
      </AppScreen>
    );
  }

  const evaluation = ielts.latestEvaluation;
  const bandScore = evaluation ? ielts.formatBand(evaluation.overallBandScore) : '—';

  const saveReport = () => {
    if (!evaluation) return;
    addIeltsRecord({
      id: activeSessionId ?? `ielts-${Date.now()}`,
      type: fullMock ? '完整模考' : part === 'p1' ? 'Part 1' : part === 'p3' ? 'Part 3' : 'Part 2',
      title: fullMock ? '完整口语模拟' : topic || ielts.generated?.title || 'IELTS 专项练习',
      date: '刚刚',
      duration: fullMock ? '14 分钟' : '4 分钟',
      result: `预估 ${bandScore}`,
      estimatedBand: Number(evaluation.overallBandScore),
      scores: [
        Math.round((evaluation.fluencyCoherenceScore / 9) * 100),
        Math.round((evaluation.lexicalResourceScore / 9) * 100),
        Math.round((evaluation.grammaticalRangeAccuracyScore / 9) * 100),
        Math.round((evaluation.pronunciationScore / 9) * 100),
      ],
    });
  };

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
          onPress={() => {
            saveReport();
            setRoute('home');
          }}
          style={styles.reportSecondaryButton}
        />
        <AppButton
          title="查看详情"
          onPress={() => {
            saveReport();
            if (onViewDetails) {
              setImmersiveLearning(false);
              onViewDetails();
            } else {
              onExit();
            }
          }}
          style={styles.reportPrimaryButton}
        />
      </View>
    </AppScreen>
  );
}

type InterviewRoute = 'input' | 'live' | 'finalizing' | 'report';
type InterviewDifficulty = 'easy' | 'standard' | 'hard';

const interviewDifficulties: readonly { id: InterviewDifficulty; title: string; note: string; recommended?: boolean }[] = [
  { id: 'easy', title: '简单', note: '基础问答' },
  { id: 'standard', title: '标准', note: '核心能力', recommended: true },
  { id: 'hard', title: '困难', note: '深入追问' },
];

function InterviewSession({ question, questionIndex, onNext }: { question: string; questionIndex: number; onNext: () => void }) {
  return (
    <SafeAreaView edges={['top', 'bottom']} style={styles.interviewCallScreen}>
      <CallExperience
        endAccessibilityLabel={questionIndex === interviewQuestions.length - 1 ? '结束面试' : '进入下一题'}
        endControlIcon="arrow"
        initialSubtitles={false}
        onEnd={onNext}
        participant={{ image: examinerAssets.sophia, name: 'AI 面试官' }}
        showMuteControl={false}
        showTranslationControl={false}
        statusText="正在聆听你的回答"
        tone="navy"
        transcriptEnglish={question}
      />
    </SafeAreaView>
  );
}

export function InterviewFlow({ onExit, onViewDetails }: { onExit: () => void; onViewDetails?: () => void }) {
  const { addInterviewRecord } = useAppModel();
  const { setImmersiveLearning } = useLearningStage();
  const [route, setRoute] = useState<InterviewRoute>('input');
  const [resume, setResume] = useState(false);
  const [jobDescription, setJobDescription] = useState('');
  const [difficulty, setDifficulty] = useState<InterviewDifficulty | null>(null);
  const [question, setQuestion] = useState(0);
  const [progress, setProgress] = useState(0);
  const difficultyLabel = interviewDifficulties.find((item) => item.id === difficulty)?.title ?? '标准';
  const canStart = Boolean(jobDescription.trim() && difficulty);

  useEffect(() => {
    setImmersiveLearning(route !== 'input');
  }, [route, setImmersiveLearning]);

  useEffect(() => () => setImmersiveLearning(false), [setImmersiveLearning]);

  useEffect(() => {
    if (route !== 'finalizing') return;
    const timer = setInterval(() => setProgress((current) => Math.min(100, current + 16)), 230);
    return () => clearInterval(timer);
  }, [route]);

  useEffect(() => {
    if (route !== 'finalizing' || progress < 100) return;
    const timer = setTimeout(() => setRoute('report'), 300);
    return () => clearTimeout(timer);
  }, [progress, route]);

  const saveReport = () => {
    addInterviewRecord({
      id: `interview-${Date.now()}`,
      role: '英文面试',
      company: `${difficultyLabel}难度`,
      date: '刚刚',
      duration: '15 分钟',
      score: 82,
      summary: '回答结构清楚，下一步要让结果和影响更具体。',
      scores: [84, 86, 76, 81],
    });
  };

  const closeReport = () => {
    setQuestion(0);
    setProgress(0);
    setRoute('input');
  };

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
        <Pressable
          accessibilityRole="button"
            accessibilityLabel={resume ? '移除已添加的简历' : '添加简历'}
          accessibilityState={{ selected: resume }}
          onPress={() => setResume((current) => !current)}
          style={({ pressed }) => [styles.interviewPanel, styles.interviewUploadPanel, resume && styles.interviewPanelSelected, pressed && styles.pressed]}
        >
          <Image source={interviewAssets.resume} style={styles.interviewResumeAsset} contentFit="contain" />
          <View style={styles.interviewPanelCopy}>
            <View style={styles.interviewTitleRow}>
              <Text style={styles.interviewPanelTitle}>{resume ? '简历已添加' : '添加简历（可选）'}</Text>
              {resume ? <AppIcon name="check-circle" size={19} color={interviewPalette.accentBright} /> : null}
            </View>
            <Text style={styles.interviewPanelNote}>{resume ? 'resume-yufan.pdf · 已用于本次问题生成' : '支持 PDF / DOCX，用于生成更贴合的面试问题'}</Text>
          </View>
          <AppIcon name="chevron-right" size={20} color={interviewPalette.muted} />
        </Pressable>

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

        <AppButton
          title="开始模拟面试"
          icon="arrow-right"
          disabled={!canStart}
          onPress={() => {
            setQuestion(0);
            setRoute('live');
          }}
          style={styles.interviewStartButton}
        />
      </AppScreen>
    );
  }

  if (route === 'finalizing') {
    return (
      <AppScreen contentStyle={[styles.analysis, styles.interviewAnalysis]} stickyHeader={false}>
        <AppIcon name="document" size={34} color={interviewPalette.accentBright} />
        <Text style={styles.interviewAnalysisTitle}>正在生成面试复盘</Text>
        <Text style={styles.interviewAnalysisCopy}>整理回答亮点、风险点和更好的表达方式。</Text>
        <ProgressBar value={progress} />
        <Text style={styles.interviewProgressText}>{progress}%</Text>
      </AppScreen>
    );
  }

  if (route === 'live') {
    const next = () => {
      if (question >= interviewQuestions.length - 1) {
        setProgress(0);
        setRoute('finalizing');
      } else {
        setQuestion((current) => current + 1);
      }
    };
    return <InterviewSession question={interviewQuestions[question]} questionIndex={question} onNext={next} />;
  }

  return (
    <AppScreen contentStyle={styles.interviewReportScreen} stickyHeader={false}>
      <View style={styles.scoreHero}>
        <Text style={styles.interviewScore}>82</Text>
        <Text style={styles.interviewReportMuted}>综合表现 · {difficultyLabel}难度</Text>
      </View>
      <View style={styles.interviewMetrics}>
        {[['岗位匹配', '84'], ['表达清晰', '86'], ['回答深度', '76']].map(([label, value]) => (
          <View key={label} style={styles.interviewMetric}>
            <Text style={styles.interviewMetricLabel}>{label}</Text>
            <Text style={styles.interviewMetricValue}>{value}</Text>
          </View>
        ))}
      </View>
      <View style={styles.interviewReportCard}><Text style={styles.interviewReportTitle}>更好的表达方式</Text><Text style={styles.interviewReportBody}>I validated the riskiest assumption first, aligned the team on a reversible test, and used the result to decide whether to scale.</Text></View>
      <View style={styles.interviewReportCard}><Text style={styles.interviewReportTitle}>下一次重点</Text><Text style={styles.interviewReportBody}>用数字说明决策带来的业务影响，并在回答结尾明确总结你的个人贡献。</Text></View>
      <View style={styles.interviewReportActions}>
        <AppButton
          title="返回主页"
          variant="secondary"
          onPress={() => {
            saveReport();
            closeReport();
          }}
          style={styles.interviewReportSecondaryButton}
        />
        <AppButton
          title="查看详情"
          onPress={() => {
            saveReport();
            closeReport();
            if (onViewDetails) {
              setImmersiveLearning(false);
              onViewDetails();
            } else {
              onExit();
            }
          }}
          style={styles.interviewReportPrimaryButton}
        />
      </View>
    </AppScreen>
  );
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
  part2Screen: { flex: 1, backgroundColor: ieltsPalette.canvas },
  part2KeyboardView: { flex: 1 },
  part2Content: { paddingHorizontal: 22, paddingTop: 18, paddingBottom: 16, gap: 14 },
  part2Presence: { alignItems: 'center', gap: 3 },
  part2ExaminerImage: { width: 72, height: 82 },
  part2Timer: { color: ieltsPalette.text, fontSize: 25, lineHeight: 31, fontWeight: '600', fontVariant: ['tabular-nums'] },
  part2ExaminerName: { color: ieltsPalette.muted, fontSize: 12, lineHeight: 17, fontWeight: '500' },
  part2Instruction: { maxWidth: 330, marginTop: 4, color: ieltsPalette.text, fontSize: 13, lineHeight: 19, fontWeight: '400', textAlign: 'center' },
  part2CueCard: { padding: 18, gap: 10, borderWidth: 1, borderColor: ieltsPalette.border, borderRadius: 18, backgroundColor: ieltsPalette.paper, shadowColor: ieltsPalette.purple, shadowOffset: { width: 0, height: 5 }, shadowOpacity: 0.08, shadowRadius: 14, elevation: 2, boxShadow: '0px 5px 16px rgba(128, 96, 232, 0.08)' },
  part2Eyebrow: { color: ieltsPalette.muted, fontSize: 10, lineHeight: 14, fontWeight: '600', letterSpacing: 1.5 },
  part2CueTitle: { color: ieltsPalette.text, fontSize: 21, lineHeight: 28, fontWeight: '600', letterSpacing: -0.4 },
  part2ShouldSay: { marginTop: 2, color: ieltsPalette.text, fontSize: 13, lineHeight: 18, fontWeight: '600' },
  part2CuePoints: { gap: 7 },
  part2CuePointRow: { flexDirection: 'row', alignItems: 'flex-start', gap: 9 },
  part2Bullet: { width: 5, height: 5, marginTop: 7, borderRadius: 3, backgroundColor: ieltsPalette.purple },
  part2CuePoint: { flex: 1, color: ieltsPalette.muted, fontSize: 13, lineHeight: 19, fontWeight: '400' },
  part2NoteCard: { minHeight: 180, padding: 16, gap: 10, borderWidth: 1, borderColor: ieltsPalette.border, borderRadius: 18, backgroundColor: ieltsPalette.paper, shadowColor: ieltsPalette.purple, shadowOffset: { width: 0, height: 5 }, shadowOpacity: 0.06, shadowRadius: 14, elevation: 2, boxShadow: '0px 5px 16px rgba(128, 96, 232, 0.06)' },
  part2NoteHeader: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 10 },
  part2NoteTitleRow: { flexDirection: 'row', alignItems: 'center', gap: 7 },
  part2NoteTitle: { color: ieltsPalette.text, fontSize: 14, lineHeight: 20, fontWeight: '600' },
  part2NoteHint: { color: ieltsPalette.muted, fontSize: 10, lineHeight: 15, fontWeight: '400' },
  part2NoteInput: { minHeight: 116, paddingHorizontal: 13, paddingVertical: 11, color: ieltsPalette.text, fontSize: 14, lineHeight: 21, fontWeight: '400', borderRadius: 13, backgroundColor: ieltsPalette.purpleSoft, outlineWidth: 0 },
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
  examinerGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: 9 },
  examinerCard: { width: '48%', minHeight: 142, padding: 12, alignItems: 'center', gap: 5, borderWidth: 1, borderColor: colors.line, borderRadius: 14 },
  examinerImage: { width: 72, height: 82 },
  examinerName: { color: colors.ink, fontSize: 14, fontWeight: '500' },
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
  analysisTitle: { color: colors.ink, fontSize: 25, lineHeight: 34, fontWeight: '600', textAlign: 'center' },
  progressText: { color: colors.subtle, fontSize: 12, fontWeight: '300', fontVariant: ['tabular-nums'] },
  reportScreen: { paddingTop: 32, paddingBottom: 44, justifyContent: 'center', gap: 14 },
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
