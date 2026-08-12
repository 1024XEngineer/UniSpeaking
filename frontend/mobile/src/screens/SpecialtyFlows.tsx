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
import { ieltsParts, ieltsTopics } from '@/data/content';
import { InterviewReportView } from '@/features/interview/InterviewReportView';
import { type InterviewSessionApi } from '@/features/interview/InterviewSessionApi';
import { createInterviewApi, useInterviewSession } from '@/features/interview/useInterviewSession';
import { useInterviewPreparation, type InterviewDifficultyOption, type InterviewPreparationResult } from '@/features/interview/useInterviewPreparation';
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

type IeltsPartId = keyof typeof ieltsTopics;

const examiners = [
  { id: 'daniel', name: 'Daniel', accent: '英式', image: examinerAssets.daniel },
  { id: 'sophia', name: 'Sophia', accent: '英式', image: examinerAssets.sophia },
  { id: 'marcus', name: 'Marcus', accent: '美式', image: examinerAssets.marcus },
  { id: 'margaret', name: 'Margaret', accent: '澳式', image: examinerAssets.margaret },
] as const;

const ieltsPartOrder: readonly IeltsPartId[] = ['p1', 'p2', 'p3'];

function pickRandom<T>(items: readonly T[]) {
  return items[Math.floor(Math.random() * items.length)];
}

function randomExaminer() {
  return pickRandom(examiners);
}

function randomIeltsTopic(part: IeltsPartId) {
  const topics: readonly { title: string }[] = ieltsTopics[part];
  return pickRandom(topics).title;
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
  topic,
  onFinish,
}: {
  examiner: (typeof examiners)[number];
  part: 'p1' | 'p3';
  topic: string;
  onFinish: () => void;
}) {
  const currentQuestion = part === 'p1'
    ? `Let's talk about ${topic}. What comes to mind first?`
    : `Let's discuss ${topic} in more depth. Why do you think this topic matters to society?`;

  return (
    <SafeAreaView edges={['top', 'bottom']} style={styles.ieltsCallScreen}>
      <CallExperience
        endAccessibilityLabel="结束本题并进入下一题"
        endControlIcon="arrow"
        initialSubtitles={false}
        onEnd={onFinish}
        participant={examiner}
        showMuteControl={false}
        showTranslationControl={false}
        statusText={`${part === 'p1' ? 'Part 1' : 'Part 3'} · 正在聆听你的回答`}
        transcriptEnglish={currentQuestion}
      />
    </SafeAreaView>
  );
}

const part2CueCards: Record<string, { title: string; points: string[] }> = {
  想见的名人: {
    title: 'Describe a famous person you would like to meet',
    points: [
      'Who this person is',
      'How you know about this person',
      'Where you would like to meet them',
      'And explain why you would like to meet them',
    ],
  },
  一次难忘的旅行: {
    title: 'Describe a memorable trip you have taken',
    points: [
      'Where you went',
      'Who you went with',
      'What you did during the trip',
      'And explain why it was memorable',
    ],
  },
  一个安静的地方: {
    title: 'Describe a quiet place you enjoy visiting',
    points: [
      'Where this place is',
      'When you usually go there',
      'What you do there',
      'And explain why you enjoy this quiet place',
    ],
  },
};

function formatSessionDuration(seconds: number) {
  const minutes = Math.floor(seconds / 60).toString().padStart(2, '0');
  const remainingSeconds = (seconds % 60).toString().padStart(2, '0');
  return `${minutes}:${remainingSeconds}`;
}

function IeltsPart2Session({
  examiner,
  topic,
  onFinish,
}: {
  examiner: (typeof examiners)[number];
  topic: string;
  onFinish: () => void;
}) {
  const [elapsed, setElapsed] = useState(0);
  const [note, setNote] = useState('');
  const cueCard = part2CueCards[topic] ?? {
    title: `Describe ${topic}`,
    points: ['What it is', 'When or where you experienced it', 'Who was involved', 'And explain why it is important to you'],
  };

  useEffect(() => {
    const timer = setInterval(() => setElapsed((current) => current + 1), 1000);
    return () => clearInterval(timer);
  }, []);

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
            <Text style={styles.part2Timer}>{formatSessionDuration(elapsed)}</Text>
            <Text style={styles.part2ExaminerName}>{examiner.name}</Text>
            <Text style={styles.part2Instruction}>你有 1 分钟准备时间，可以根据题卡记录关键词。</Text>
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
              <Text style={styles.part2NoteHint}>准备结束后自动锁定</Text>
            </View>
            <TextInput
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

        <View style={styles.part2Footer}>
          <Pressable accessibilityRole="button" accessibilityLabel="进入下一步" onPress={onFinish} style={styles.part2EndButton}>
            <AppIcon name="arrow-right" size={25} color={colors.white} />
          </Pressable>
        </View>
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
  const [route, setRoute] = useState<IeltsRoute>('intake');
  const [target, setTarget] = useState('7.0');
  const [startingLevel, setStartingLevel] = useState<string>(levels[2].id);
  const [intakeStep, setIntakeStep] = useState(0);
  const [part, setPart] = useState<IeltsPartId>('p2');
  const [topic, setTopic] = useState('一次难忘的旅行');
  const [fullMock, setFullMock] = useState(false);
  const [topicCategory, setTopicCategory] = useState('全部');
  const [topicQuery, setTopicQuery] = useState('');
  const [topicPage, setTopicPage] = useState(1);
  const [examiner, setExaminer] = useState<(typeof examiners)[number]>(() => randomExaminer());
  const [progress, setProgress] = useState(0);

  const startSinglePart = (selectedTopic: string) => {
    setFullMock(false);
    setTopic(selectedTopic);
    setExaminer(randomExaminer());
    setRoute('session');
  };

  const startFullMock = () => {
    const firstPart: IeltsPartId = 'p1';
    setFullMock(true);
    setPart(firstPart);
    setTopic(randomIeltsTopic(firstPart));
    setExaminer(randomExaminer());
    setProgress(0);
    setRoute('session');
  };

  useEffect(() => {
    setImmersiveLearning(route === 'session' || route === 'analysis' || route === 'report');
  }, [route, setImmersiveLearning]);

  useEffect(() => () => setImmersiveLearning(false), [setImmersiveLearning]);

  useEffect(() => {
    if (route !== 'analysis') return;
    const timer = setInterval(() => setProgress((current) => Math.min(100, current + 14)), 220);
    return () => clearInterval(timer);
  }, [route]);

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
              title={isTargetStep ? '下一步' : '进入 IELTS 专项'}
              icon="arrow-right"
              disabled={!selected}
              onPress={() => {
                if (isTargetStep) setIntakeStep(1);
                else setRoute('home');
              }}
              style={styles.intakeNextButton}
            />
          </View>
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
              <Text style={styles.goalValue}>{target}</Text>
              <Image source={ieltsAssets.target} style={[styles.goalIcon, styles.goalIconTarget]} contentFit="contain" />
            </View>
          </View>
          <View style={styles.goalMetric}>
            <Text style={styles.goalLabel}>连续打卡</Text>
            <View style={styles.goalValueRow}>
              <Text style={styles.goalValue}>12<Text style={styles.goalSuffix}> 天</Text></Text>
              <Image source={ieltsAssets.calendar} style={[styles.goalIcon, styles.goalIconCalendar]} contentFit="contain" />
            </View>
          </View>
          <View style={[styles.goalMetric, styles.goalMetricLast]}>
            <Text style={styles.goalLabel}>今日特训</Text>
            <View style={styles.goalValueRow}>
              <Text style={styles.goalValue}>3<Text style={styles.goalSuffix}> / 5</Text></Text>
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
              title="开始模考"
              variant="primary"
              icon="arrow-right"
              style={styles.ieltsMockButton}
              onPress={startFullMock}
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
              setTopicCategory('全部');
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
    const topics = ieltsTopics[part];
    const partMeta = ieltsParts.find((item) => item.id === part) ?? ieltsParts[0];
    const defaultFilters = ['全部', '事件', '事物', '人物', '地点', '必考题'];
    const filters = Array.from(new Set([...defaultFilters, ...topics.map((item) => item.category)]));
    const normalizedQuery = topicQuery.trim().toLocaleLowerCase();
    const filteredTopics = topics.filter((item) => {
      const categoryMatches = topicCategory === '全部' || item.category === topicCategory;
      const queryMatches = !normalizedQuery || item.title.toLocaleLowerCase().includes(normalizedQuery);
      return categoryMatches && queryMatches;
    });
    const topicPageSize = 5;
    const totalTopicPages = Math.max(1, Math.ceil(filteredTopics.length / topicPageSize));
    const visibleTopics = filteredTopics.slice((topicPage - 1) * topicPageSize, topicPage * topicPageSize);
    const startRandomTopic = () => {
      const candidates = filteredTopics.length > 0 ? filteredTopics : topics;
      const selectedTopic = pickRandom(candidates);
      startSinglePart(selectedTopic.title);
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
              key={filter}
              onPress={() => { setTopicCategory(filter); setTopicPage(1); }}
              style={[styles.topicFilter, topicCategory === filter && styles.topicFilterActive]}
            >
              <Text style={[styles.topicFilterText, topicCategory === filter && styles.topicFilterTextActive]}>{filter}</Text>
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
          {visibleTopics.map((item) => {
            const practiced = item.state !== '未练习';
            const recentScore = item.state === '建议复练' ? '7.0 分' : item.state === '已练习' ? '6.5 分' : '未练习';
            return (
              <Pressable
                accessibilityRole="button"
                accessibilityLabel={`${item.title}，${item.state}`}
                key={item.title}
                onPress={() => startSinglePart(item.title)}
                style={({ pressed }) => [styles.topicTableRow, pressed && styles.topicTableRowPressed]}
              >
                <View style={styles.topicColumnMain}>
                  <Text style={styles.topicCategory}>{item.category}</Text>
                  <Text numberOfLines={2} style={styles.topicTitle}>{item.title}</Text>
                  <Text style={styles.topicQuestionCount}>{part === 'p2' ? '1 道题目' : '4 道问题'}</Text>
                </View>
                <View style={styles.topicColumnPractice}>
                  <Text style={styles.topicPracticeTitle}>{practiced ? '指定专项练习' : '未练习'}</Text>
                  <Text style={styles.topicPracticeNote}>{practiced ? '共 1 次' : '暂无记录'}</Text>
                </View>
                <View style={styles.topicColumnResult}>
                  <Text style={styles.topicResult}>{recentScore}</Text>
                  <Text style={styles.topicResultNote}>{item.state}</Text>
                </View>
                <View style={styles.topicColumnArrow}>
                  <AppIcon name="chevron-right" size={18} color={ieltsPalette.text} />
                </View>
              </Pressable>
            );
          })}
          {filteredTopics.length === 0 ? (
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
        <Text style={styles.topicCount}>共 {filteredTopics.length} 个话题</Text>
      </AppScreen>
    );
  }

  if (route === 'session') {
    const finishSession = () => {
      const currentPartIndex = ieltsPartOrder.indexOf(part);
      const nextPart = ieltsPartOrder[currentPartIndex + 1];
      if (fullMock && nextPart) {
        setPart(nextPart);
        setTopic(randomIeltsTopic(nextPart));
        return;
      }
      setProgress(0);
      setRoute('analysis');
    };
    return part === 'p2'
      ? <IeltsPart2Session examiner={examiner} topic={topic} onFinish={finishSession} />
      : <IeltsSession examiner={examiner} part={part} topic={topic} onFinish={finishSession} />;
  }

  if (route === 'analysis') {
    return (
      <AppScreen contentStyle={[styles.analysis, styles.ieltsStageScreen]} stickyHeader={false}>
        <AppIcon name="sliders" size={32} />
        <Text style={styles.analysisTitle}>正在分析你的口语表现</Text>
        <Text style={uiStyles.muted}>评估流利度、词汇、语法和发音，并生成可复练的表达。</Text>
        <ProgressBar value={progress} />
        <Text style={styles.progressText}>{progress}%</Text>
      </AppScreen>
    );
  }

  const saveReport = () => {
    addIeltsRecord({
      id: `ielts-${Date.now()}`,
      type: fullMock ? '完整模考' : part === 'p1' ? 'Part 1' : part === 'p3' ? 'Part 3' : 'Part 2',
      title: fullMock ? '完整口语模拟' : topic,
      date: '刚刚',
      duration: fullMock ? '14 分钟' : '4 分钟',
      result: '预估 6.5',
      estimatedBand: 6.5,
      scores: [68, 72, 64, 70],
    });
  };

  return (
    <AppScreen contentStyle={[styles.ieltsStageScreen, styles.reportScreen]} stickyHeader={false}>
      <View style={styles.bandHero}>
        <Text style={styles.bandEyebrow}>本次模拟评分</Text>
        <Text style={styles.bandScore}>6.5</Text>
        <Text style={styles.bandLabel}>ESTIMATED BAND</Text>
      </View>
      <View style={styles.metrics}>
        <ReportMetric label="流利与连贯" value="6.5" />
        <ReportMetric label="词汇资源" value="7.0" />
      </View>
      <View style={styles.metrics}>
        <ReportMetric label="语法范围" value="6.0" />
        <ReportMetric label="发音" value="6.5" />
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

type InterviewRoute = 'input' | 'live' | 'finalizing';
type InterviewDifficulty = InterviewDifficultyOption;

const interviewDifficulties: readonly { id: InterviewDifficulty; title: string; note: string; recommended?: boolean }[] = [
  { id: 'easy', title: '简单', note: '基础问答' },
  { id: 'standard', title: '标准', note: '核心能力', recommended: true },
  { id: 'hard', title: '困难', note: '深入追问' },
];

function InterviewSession({ preparation, onFinished }: { preparation: InterviewPreparationResult; onFinished: (sessionId: string, api: InterviewSessionApi) => void }) {
  const { teacher } = useAppModel();
  const session = useInterviewSession({ sceneId: preparation.scene.sceneId, voice: teacher.voiceId });
  const deliveredSession = useRef<string | null>(null);
  const latestAssistant = [...session.transcripts].reverse().find((item) => item.owner === 0)?.text ?? '';

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
        muted={session.muted}
        onEnd={() => void session.end().catch(() => undefined)}
        onMutedChange={session.setMuted}
        participant={{ image: examinerAssets.sophia, name: 'AI 面试官' }}
        showTranslationControl={false}
        statusLabel={statusText}
        statusText={`${preparation.jobTitle ?? '英文面试'} · ${statusText}`}
        tone="navy"
        transcriptEnglish={latestAssistant || statusText}
        transcriptSpeaker="AI 面试官"
        userTranscript={session.transcripts.filter((item) => item.owner === 1).at(-1)?.text ?? ''}
      />
    </SafeAreaView>
  );
}

export function InterviewFlow({ onExit }: { onExit: () => void; onViewDetails?: () => void }) {
  const { setImmersiveLearning } = useLearningStage();
  const [route, setRoute] = useState<InterviewRoute>('input');
  const [jobDescription, setJobDescription] = useState('');
  const [difficulty, setDifficulty] = useState<InterviewDifficulty | null>(null);
  const [preparation, setPreparation] = useState<InterviewPreparationResult | null>(null);
  const [completedSession, setCompletedSession] = useState<{ sessionId: string; api: InterviewSessionApi } | null>(null);
  const { resumeFileName, isPreparing, error, pickResume, start } = useInterviewPreparation();
  const canStart = Boolean(jobDescription.trim() && difficulty && !isPreparing);

  useEffect(() => {
    setImmersiveLearning(route !== 'input');
  }, [route, setImmersiveLearning]);

  useEffect(() => () => setImmersiveLearning(false), [setImmersiveLearning]);

  const closeReport = () => {
    setPreparation(null);
    setCompletedSession(null);
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
          accessibilityLabel={resumeFileName ? '重新选择简历' : '添加简历'}
          onPress={() => void pickResume()}
          style={({ pressed }) => [styles.interviewPanel, styles.interviewUploadPanel, resumeFileName && styles.interviewPanelSelected, pressed && styles.pressed]}
        >
          <Image source={interviewAssets.resume} style={styles.interviewResumeAsset} contentFit="contain" />
          <View style={styles.interviewPanelCopy}>
            <View style={styles.interviewTitleRow}>
              <Text style={styles.interviewPanelTitle}>{resumeFileName ? '简历已添加' : '添加简历（可选）'}</Text>
              {resumeFileName ? <AppIcon name="check-circle" size={19} color={interviewPalette.accentBright} /> : null}
            </View>
            <Text style={styles.interviewPanelNote}>{resumeFileName ?? '支持 PDF / DOCX，用于生成更贴合的面试问题'}</Text>
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

        {error ? <Text accessibilityRole="alert" style={styles.interviewError}>{error}</Text> : null}
        <AppButton
          title={isPreparing ? '正在准备面试…' : '开始模拟面试'}
          icon="arrow-right"
          disabled={!canStart}
          onPress={() => {
            void start({ jobDescription, difficulty }).then((prepared) => {
              if (!prepared) return;
              // Keep the real scene/material/job title attached to live state until coordinator integration lands.
              setPreparation(prepared);
              setRoute('live');
            });
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
        {completedSession ? <InterviewReportView api={completedSession.api} sessionId={completedSession.sessionId} /> : null}
        <AppButton title="返回面试首页" variant="secondary" onPress={closeReport} />
      </AppScreen>
    );
  }

  if (route === 'live') {
    if (!preparation) return null;
    return (
      <InterviewSession
        preparation={preparation}
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
