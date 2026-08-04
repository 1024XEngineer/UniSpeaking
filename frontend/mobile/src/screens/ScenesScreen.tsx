import { useEffect, useMemo, useRef, useState } from 'react';
import { Animated, PanResponder, Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import { ArrowRightIcon } from 'phosphor-react-native/src/icons/ArrowRight';
import { BookOpenTextIcon } from 'phosphor-react-native/src/icons/BookOpenText';
import { BriefcaseIcon } from 'phosphor-react-native/src/icons/Briefcase';
import { CaretDownIcon } from 'phosphor-react-native/src/icons/CaretDown';
import Svg, { Circle, Line, Polygon, Text as SvgText } from 'react-native-svg';

import {
  AppIcon,
  AppScreen,
  PageHeader,
  uiStyles,
} from '@/components/ui';
import {
  getDailyScenePromptExample,
  learningItems,
  recommendations,
  type ScenePromptExample,
} from '@/data/content';
import { useAppModel } from '@/model/AppModel';
import { colors } from '@/theme/tokens';
import { CallExperience } from './ConversationScreen';
import { IeltsFlow, InterviewFlow } from './SpecialtyFlows';

export type SceneRoute =
  | { name: 'home' }
  | { name: 'training'; id: string }
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
  const collapseProgress = useRef(new Animated.Value(0)).current;
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

export function Training({ id, initialStage = 'learn', onBack, onFinish }: { id: string; initialStage?: TrainingStage; onBack: () => void; onFinish: () => void }) {
  const { addSceneRecord } = useAppModel();
  const scenario = recommendations.find((item) => item.id === id) ?? recommendations[0];
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
  const [recording, setRecording] = useState(false);
  const [completionOpen, setCompletionOpen] = useState(false);
  const [progressCollapsed, setProgressCollapsed] = useState(false);
  const learnItems = learningGroup === 'words' ? wordItems : phraseItems;
  const learnItem = learnItems[learnIndex] ?? learnItems[0];
  const readItem = readItems[readIndex] ?? readItems[0];

  const nextLearn = () => {
    setDemoPlaying(false);
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
    setDemoPlaying(false);
    if (learnIndex > 0) setLearnIndex((current) => current - 1);
    else if (learningGroup === 'phrases' && wordItems.length > 0) {
      setLearningGroup('words');
      setLearnIndex(wordItems.length - 1);
    }
  };
  const toggleReadRecording = () => {
    if (recording) {
      setRecording(false);
      setReadPassed(true);
      setReadFeedbackOpen(true);
    } else {
      setReadPassed(false);
      setRecording(true);
    }
  };
  const finishTraining = () => {
    addSceneRecord({
      id,
      title: scenario.title,
      date: '刚刚',
      status: '已完成',
      score: 86,
      practiceCount: 1,
      expressions: learningItems.map((item, index) => ({
        id: `${id}-${index}`,
        type: item.type === '短语' ? '词组' : item.type,
        englishText: item.en,
        chineseText: item.zh,
        phonetic: item.phonetic,
      })),
      conversation: [
        { id: `${id}-assistant`, role: 'assistant', speaker: 'James', text: 'Good morning! What can I get started for you today?' },
        {
          id: `${id}-user`,
          role: 'user',
          speaker: '你',
          text: 'Could you recommend something less sweet?',
          feedback: {
            suggestedExpression: 'Could you recommend something that is not too sweet?',
            feedbackSummary: '表达清楚自然；补充 “not too” 可以让甜度需求更具体。',
          },
        },
      ],
    });
    setCompletionOpen(false);
    onFinish();
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
        stage={stage}
        unlockedStage={unlockedStage}
        onCollapsedChange={setProgressCollapsed}
        onSelect={(nextStage) => {
          setRecording(false);
          setDemoPlaying(false);
          setStage(nextStage);
        }}
      />

      {stage === 'learn' ? (
        <View style={styles.stageShell}>
          <View style={styles.stageMetaRow}>
            <Text style={styles.stageHeading}>{learningGroup === 'words' ? '场景单词' : '场景短语'}</Text>
          </View>
          <View style={styles.languageCard}>
            <Text style={styles.languageCount}>{learnIndex + 1} / {learnItems.length}</Text>
            <Text style={styles.languageType}>{learnItem.type}</Text>
            <Text style={styles.languageEnglish}>{learnItem.en}</Text>
            <View style={styles.pronunciationRow}>
              <Text style={styles.phoneticText}>{learnItem.phonetic}</Text>
              <Pressable
                accessibilityRole="button"
                accessibilityLabel="播放发音"
                onPress={() => setDemoPlaying((current) => !current)}
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
              disabled={learnIndex === 0 && learningGroup === 'words'}
              onPress={previousLearn}
              style={[styles.roundNavButton, learnIndex === 0 && learningGroup === 'words' && styles.roundNavDisabled]}
            >
              <AppIcon name="arrow-left" size={20} />
            </Pressable>
            <Pressable accessibilityRole="button" onPress={nextLearn} style={styles.primaryPillButton}>
              <Text style={styles.primaryPillText}>
                {learnIndex < learnItems.length - 1 ? '下一个' : learningGroup === 'words' && phraseItems.length > 0 ? '进入词组' : '进入朗读'}
              </Text>
              <AppIcon name="arrow-right" size={18} color={colors.white} />
            </Pressable>
          </View>
        </View>
      ) : null}

      {stage === 'read' ? (
        <View style={styles.stageShell}>
          <View style={styles.stageMetaRow}>
            <Text style={styles.stageHeading}>场景句子</Text>
            <Text style={styles.stageCount}>{readIndex + 1} / {readItems.length}</Text>
          </View>
          <View style={styles.readCard}>
            {readPassed ? <View style={styles.scoreBadge}><Text style={styles.scoreBadgeValue}>86</Text><Text style={styles.scoreBadgeMax}>/100</Text></View> : null}
            <Text style={styles.readSentence}>
              {readPassed ? <><Text style={styles.scoreCorrect}>Could you </Text><Text style={styles.scoreIncorrect}>recommend</Text><Text style={styles.scoreCorrect}> something less sweet?</Text></> : readItem.en}
            </Text>
            <Text style={styles.readTranslation}>{readItem.zh}</Text>
            <Pressable accessibilityRole="button" accessibilityLabel={recording ? '结束朗读' : '开始朗读'} onPress={toggleReadRecording} style={[styles.readRecordButton, recording && styles.readRecordButtonActive]}>
              <AppIcon name={recording ? 'microphone' : 'microphone-off'} size={28} color={recording ? '#B94D44' : colors.subtle} />
            </Pressable>
            <Text style={styles.readStatus}>{recording ? '正在听你朗读' : readPassed ? '朗读通过' : '点击麦克风开始朗读'}</Text>
            <Text style={styles.readInstruction}>{recording ? '再次点击麦克风结束录音并提交评分。' : readPassed ? '本句已达到 80 分，可以进入下一步。' : '再次点击麦克风结束录音并提交评分。'}</Text>
            <Pressable accessibilityRole="button" accessibilityLabel="听标准示范" onPress={() => setDemoPlaying((current) => !current)} style={styles.readDemoButton}>
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
              disabled={!readPassed}
              onPress={() => { setUnlockedStage(2); setStage('speak'); setRecording(false); setDemoPlaying(false); }}
              style={[styles.primaryPillButton, !readPassed && styles.primaryPillDisabled]}
            >
              <Text style={styles.primaryPillText}>进入模拟</Text>
              <AppIcon name="arrow-right" size={18} color={colors.white} />
            </Pressable>
          </View>
        </View>
      ) : null}

      {stage === 'speak' ? (
        <View style={styles.sceneCallStage}>
          <CallExperience
            allowSubtitleToggle={false}
            compactTranscriptLayout
            onEnd={() => setCompletionOpen(true)}
            progressCollapsed={progressCollapsed}
            transcriptEnglish="Good morning! What can I get started for you today?"
            transcriptChinese="早上好！今天想点些什么？"
          />
        </View>
      ) : null}
    </AppScreen>
    {readFeedbackOpen ? (
      <View style={styles.scoreModalBackdrop}>
        <View style={styles.scoreModal}>
          <View style={styles.scoreModalValueRow}><Text style={styles.scoreModalValue}>86</Text><Text style={styles.scoreModalMax}>/100</Text></View>
          <Text style={styles.scoreModalTitle}>本句发音评估</Text>
          <Text style={styles.scoreModalLead}>本句已达到 80 分，可以进入下一句；红色部分仍可继续练习。</Text>
          <View style={styles.scoreFocusCard}>
            <Text style={styles.scoreFocusLabel}>逐词结果</Text>
            <Text style={styles.scoreFocusSentence}><Text style={styles.scoreCorrect}>Could you </Text><Text style={styles.scoreIncorrect}>recommend</Text><Text style={styles.scoreCorrect}> something less sweet?</Text></Text>
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
              <Text style={styles.completionScore}>86</Text>
              <Text style={styles.completionScoreMax}>/100</Text>
            </View>
          </View>
          <View style={styles.completionOverview}>
            <SceneRadar metrics={defaultSceneMetrics} />
            <View style={styles.completionMetrics}>
              {defaultSceneMetrics.map((metric) => (
                <View key={metric.label} style={styles.completionMetricRow}>
                  <Text style={styles.completionMetricLabel}>{metric.label}</Text>
                  <Text style={styles.completionMetricValue}>{metric.value}</Text>
                </View>
              ))}
            </View>
          </View>
          <Pressable accessibilityRole="button" onPress={finishTraining} style={styles.completionDoneButton}>
            <Text style={styles.completionDoneText}>返回场景广场</Text>
            <AppIcon name="arrow-right" size={18} color={colors.white} />
          </Pressable>
        </View>
      </View>
    ) : null}
    </View>
  );
}

type ScenePreview = {
  id: string;
  title: string;
  background: string;
  aiRole: string;
  userRole: string;
  learningGoal: string;
  duration: string;
};

const scenePreviewDetails: Record<string, Omit<ScenePreview, 'id'>> = {
  coffee: {
    title: '咖啡店点单',
    background: '工作日早晨，一家繁忙的本地咖啡店。',
    aiRole: '咖啡师',
    userRole: '客户',
    learningGoal: '成功下单一款带定制选项的咖啡饮品及一份食品，并完成支付流程。',
    duration: '6 分钟',
  },
  hotel: {
    title: '酒店入住',
    background: '抵达酒店前台，确认预订信息并办理入住。',
    aiRole: '酒店前台接待',
    userRole: '住客',
    learningGoal: '礼貌确认预订、房型、早餐和退房时间等入住细节。',
    duration: '8 分钟',
  },
  pharmacy: {
    title: '药店咨询',
    background: '在当地药店向药剂师描述身体不适并寻求建议。',
    aiRole: '药剂师',
    userRole: '顾客',
    learningGoal: '清晰描述症状，并确认药品用法、用量和注意事项。',
    duration: '8 分钟',
  },
};

function getScenePreview(id: string): ScenePreview {
  return { id, ...(scenePreviewDetails[id] ?? scenePreviewDetails.coffee) };
}

export function ScenesHome({
  onOpen,
  promptExample = getDailyScenePromptExample(),
}: {
  onOpen: (route: SceneRoute) => void;
  promptExample?: ScenePromptExample;
}) {
  const [prompt, setPrompt] = useState('');
  const [specialtyOpen, setSpecialtyOpen] = useState(false);
  const [preview, setPreview] = useState<ScenePreview | null>(null);
  const startCustomScene = () => {
    if (!prompt.trim()) return;
    setPreview({
      id: 'coffee',
      title: '自定义练习场景',
      background: prompt.trim(),
      aiRole: '英语对话伙伴',
      userRole: '学习者',
      learningGoal: '围绕设定情境完成自然、清晰的英语交流。',
      duration: '8 分钟',
    });
  };

  return (
    <View style={[styles.sceneHomeRoot, preview && styles.sceneHomeRootModal]}>
      <AppScreen scrollEnabled={false} contentStyle={styles.sceneHomeContent}>
        <View style={styles.sceneHeading}>
          <PageHeader
            eyebrow="SCENARIO MARKETPLACE"
            title="场景广场"
            subtitle="把真实生活中的需求，变成高质量的口语练习。"
          />
        </View>

        <View style={styles.customBuilder}>
          <View style={styles.builderTopRow}>
            <View style={styles.builderHeadingCopy}>
              <Text style={styles.builderEyebrow}>CREATE YOUR OWN</Text>
              <Text style={styles.builderTitle}>创建专属场景</Text>
            </View>
            <Pressable
              accessibilityRole="button"
              accessibilityLabel="打开专项训练"
              onPress={() => setSpecialtyOpen((current) => !current)}
              style={({ pressed }) => [styles.specialtyTrigger, pressed && styles.compactPressed]}
            >
              <Text style={styles.specialtyTriggerText}>专项训练</Text>
              <CaretDownIcon
                color={colors.muted}
                size={13}
                style={specialtyOpen ? styles.specialtyTriggerIconOpen : undefined}
                weight="bold"
              />
            </Pressable>
          </View>
          <Text numberOfLines={1} style={styles.builderDescription}>说一句你想练习的真实情境，AI 会整理角色、目标和表达任务。</Text>

          {specialtyOpen ? (
            <View style={styles.specialtyMenu}>
              <Pressable
                accessibilityRole="button"
                onPress={() => onOpen({ name: 'ielts' })}
                style={({ pressed }) => [styles.specialtyMenuRow, pressed && styles.compactPressed]}
              >
                <View style={styles.specialtyMenuIcon}><BookOpenTextIcon color={colors.ink} size={18} /></View>
                <View style={uiStyles.flex}>
                  <Text style={styles.specialtyMenuTitle}>IELTS 口语</Text>
                  <Text style={styles.specialtyMenuNote}>全流程模拟与评分</Text>
                </View>
                <ArrowRightIcon color={colors.subtle} size={14} />
              </Pressable>
              <Pressable
                accessibilityRole="button"
                onPress={() => onOpen({ name: 'interview' })}
                style={({ pressed }) => [styles.specialtyMenuRow, pressed && styles.compactPressed]}
              >
                <View style={styles.specialtyMenuIcon}><BriefcaseIcon color={colors.ink} size={18} /></View>
                <View style={uiStyles.flex}>
                  <Text style={styles.specialtyMenuTitle}>英文面试</Text>
                  <Text style={styles.specialtyMenuNote}>职位与简历模拟追问</Text>
                </View>
                <ArrowRightIcon color={colors.subtle} size={14} />
              </Pressable>
            </View>
          ) : null}

          <TextInput
            accessibilityLabel="描述想练习的场景"
            multiline
            maxLength={200}
            onChangeText={setPrompt}
            placeholder={`你今天想练习什么？例如：${promptExample.prompt}`}
            placeholderTextColor="#8D8D88"
            style={styles.builderInput}
            textAlignVertical="top"
            value={prompt}
          />
          <View style={styles.builderFooter}>
            <Text style={styles.characterCount}>{prompt.length}/200</Text>
            <Pressable
              accessibilityRole="button"
              accessibilityLabel="生成练习场景"
              disabled={!prompt.trim()}
              onPress={startCustomScene}
              style={({ pressed }) => [
                styles.generateButton,
                !prompt.trim() && styles.generateButtonDisabled,
                pressed && prompt.trim() && styles.compactPressed,
              ]}
            >
              <Text style={[styles.generateButtonText, !prompt.trim() && styles.generateButtonTextDisabled]}>生成练习场景</Text>
              <AppIcon name="arrow-right" size={18} color={colors.white} />
            </Pressable>
          </View>
        </View>

        <View style={styles.recommendationHeader}>
          <View>
            <Text style={styles.builderEyebrow}>DAILY PICKS</Text>
            <Text style={styles.recommendationHeading}>每日推荐</Text>
          </View>
        </View>
        <View style={styles.recommendationList}>
          {recommendations.map((item, index) => (
            <Pressable
              accessibilityRole="button"
              key={item.id}
              onPress={() => setPreview(getScenePreview(item.id))}
              style={({ pressed }) => [styles.recommendation, pressed && styles.compactPressed]}
            >
              <Text style={styles.number}>0{index + 1}</Text>
              <View style={styles.recommendationCopy}>
                <View style={styles.recommendationTitleRow}>
                  <Text style={styles.recommendationTitle}>{item.title}</Text>
                  <Text style={styles.recommendationTag}>{item.tag}</Text>
                </View>
                <Text numberOfLines={1} style={styles.recommendationMeta}>{item.goal} · {item.duration}</Text>
              </View>
              <View style={styles.recommendationArrow}>
                <AppIcon name="arrow-right" size={17} color={colors.ink} />
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
            <Text style={styles.previewTitle}>{preview.title}</Text>
            <Text style={styles.previewLead}>确认场景信息，然后开始学习。</Text>
            <View style={styles.previewSummary}>
              {[
                ['场景简介', preview.background],
                ['AI 扮演', preview.aiRole],
                ['你将扮演', preview.userRole],
                ['练习重点', preview.learningGoal],
                ['预计用时', preview.duration],
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
                onPress={() => onOpen({ name: 'training', id: preview.id })}
                style={[styles.previewButton, styles.previewButtonPrimary]}
              >
                <Text style={styles.previewButtonPrimaryText}>确认进入</Text>
                <AppIcon name="arrow-right" size={18} color={colors.white} />
              </Pressable>
            </View>
          </View>
        </View>
      ) : null}
    </View>
  );
}

export function ScenesScreen() {
  const [route, setRoute] = useState<SceneRoute>({ name: 'home' });
  if (route.name === 'training') {
    return <Training id={route.id} onBack={() => setRoute({ name: 'home' })} onFinish={() => setRoute({ name: 'home' })} />;
  }
  if (route.name === 'ielts') return <IeltsFlow onExit={() => setRoute({ name: 'home' })} />;
  if (route.name === 'interview') return <InterviewFlow onExit={() => setRoute({ name: 'home' })} />;
  return <ScenesHome onOpen={setRoute} />;
}

const styles = StyleSheet.create({
  sceneHomeRoot: { flex: 1, backgroundColor: colors.canvas },
  sceneHomeRootModal: { position: 'relative', zIndex: 100, elevation: 100 },
  sceneHomeContent: { paddingHorizontal: 18, paddingTop: 18, paddingBottom: 82, gap: 0 },
  sceneHeading: { marginBottom: 14 },
  customBuilder: {
    zIndex: 2,
    height: 286,
    padding: 15,
    borderWidth: 1,
    borderColor: '#E1E1DC',
    borderRadius: 22,
    backgroundColor: colors.white,
    shadowColor: '#1A1A18',
    shadowOffset: { width: 0, height: 5 },
    shadowOpacity: 0.045,
    shadowRadius: 15,
    elevation: 2,
    boxShadow: '0px 5px 18px rgba(21, 21, 20, 0.045)',
  },
  builderTopRow: { flexDirection: 'row', alignItems: 'flex-start', justifyContent: 'space-between', gap: 12 },
  builderHeadingCopy: { flex: 1, gap: 2 },
  builderEyebrow: { color: colors.subtle, fontSize: 10, fontWeight: '600', letterSpacing: 1.6 },
  builderTitle: { color: colors.ink, fontSize: 23, lineHeight: 29, fontWeight: '600', letterSpacing: -0.7 },
  builderDescription: {
    marginTop: 6,
    flexShrink: 1,
    color: colors.muted,
    fontSize: 9,
    lineHeight: 14,
    fontWeight: '300',
    letterSpacing: -0.2,
  },
  specialtyTrigger: {
    height: 32,
    paddingHorizontal: 11,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 2,
    borderWidth: 1,
    borderColor: colors.line,
    borderRadius: 17,
    backgroundColor: colors.white,
  },
  specialtyTriggerText: { color: colors.ink, fontSize: 11, fontWeight: '500' },
  specialtyTriggerIconOpen: { transform: [{ rotate: '180deg' }] },
  specialtyMenu: {
    position: 'absolute',
    zIndex: 20,
    top: 53,
    right: 14,
    width: 202,
    padding: 7,
    borderWidth: 1,
    borderColor: colors.line,
    borderRadius: 15,
    backgroundColor: colors.white,
    shadowColor: '#000000',
    shadowOffset: { width: 0, height: 8 },
    shadowOpacity: 0.12,
    shadowRadius: 18,
    elevation: 8,
  },
  specialtyMenuRow: { minHeight: 54, padding: 7, flexDirection: 'row', alignItems: 'center', gap: 9, borderRadius: 10 },
  specialtyMenuIcon: { width: 32, height: 32, alignItems: 'center', justifyContent: 'center', borderRadius: 10, backgroundColor: colors.soft },
  specialtyMenuTitle: { color: colors.ink, fontSize: 12, fontWeight: '600' },
  specialtyMenuNote: { marginTop: 2, color: colors.subtle, fontSize: 9, fontWeight: '300' },
  builderInput: {
    height: 120,
    marginTop: 11,
    paddingHorizontal: 13,
    paddingVertical: 11,
    color: colors.ink,
    fontSize: 15,
    lineHeight: 22,
    fontWeight: '300',
    borderWidth: 1,
    borderColor: colors.line,
    borderRadius: 14,
    backgroundColor: colors.white,
  },
  builderFooter: { marginTop: 14, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  characterCount: { color: colors.subtle, fontSize: 12, fontWeight: '300' },
  generateButton: {
    height: 44,
    paddingHorizontal: 17,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 7,
    borderRadius: 20,
    backgroundColor: colors.ink,
  },
  generateButtonDisabled: { backgroundColor: '#A7A7A2' },
  generateButtonText: { color: colors.white, fontSize: 14, fontWeight: '600' },
  generateButtonTextDisabled: { color: colors.white },
  compactPressed: { opacity: 0.72, transform: [{ scale: 0.98 }] },
  recommendationHeader: { marginTop: 28, marginBottom: 9, flexDirection: 'row', alignItems: 'flex-end', justifyContent: 'space-between' },
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
  number: { width: 30, color: '#B2B2AD', fontSize: 20, fontWeight: '300' },
  recommendationCopy: { flex: 1, gap: 4 },
  recommendationTitleRow: { flexDirection: 'row', alignItems: 'center', gap: 7 },
  recommendationTitle: { color: colors.ink, fontSize: 17, fontWeight: '600' },
  recommendationTag: { paddingHorizontal: 8, paddingVertical: 3, overflow: 'hidden', color: colors.muted, fontSize: 9, fontWeight: '300', borderRadius: 10, backgroundColor: colors.soft },
  recommendationMeta: { color: colors.muted, fontSize: 12, fontWeight: '300' },
  recommendationArrow: { width: 35, height: 35, alignItems: 'center', justifyContent: 'center', borderRadius: 18, backgroundColor: colors.soft },
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
  previewTitle: { marginTop: 12, paddingRight: 44, color: colors.ink, fontSize: 28, lineHeight: 35, fontWeight: '600', letterSpacing: -0.9 },
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
  trainingScreen: { paddingHorizontal: 18, paddingTop: 28, paddingBottom: 22, gap: 15 },
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
  completionDoneButton: { height: 48, paddingHorizontal: 20, alignSelf: 'flex-end', flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8, borderRadius: 24, backgroundColor: colors.ink },
  completionDoneText: { color: colors.white, fontSize: 14, fontWeight: '600' },
});
