import { Image } from 'expo-image';
import { useEffect, useState } from 'react';
import { Pressable, StyleSheet, Text, TextInput, View } from 'react-native';

import {
  AppButton,
  AppIcon,
  AppScreen,
  Card,
  Metric,
  PageHeader,
  Pill,
  ProgressBar,
  SectionTitle,
  uiStyles,
} from '@/components/ui';
import { ieltsParts, ieltsTopics, interviewQuestions } from '@/data/content';
import { useAppModel } from '@/model/AppModel';
import { colors, examinerAssets } from '@/theme/tokens';

type IeltsRoute =
  | 'intake'
  | 'home'
  | 'topics'
  | 'setup'
  | 'session'
  | 'analysis'
  | 'report';

const examiners = [
  { id: 'daniel', name: 'Daniel', accent: '英式', image: examinerAssets.daniel },
  { id: 'sophia', name: 'Sophia', accent: '英式', image: examinerAssets.sophia },
  { id: 'marcus', name: 'Marcus', accent: '美式', image: examinerAssets.marcus },
  { id: 'margaret', name: 'Margaret', accent: '澳式', image: examinerAssets.margaret },
] as const;

function IeltsSession({
  part,
  topic,
  onBack,
  onFinish,
}: {
  part: string;
  topic: string;
  onBack: () => void;
  onFinish: () => void;
}) {
  const [question, setQuestion] = useState(0);
  const [paused, setPaused] = useState(false);
  const questions = [
    `Let's talk about ${topic}. What comes to mind first?`,
    'Why is this important to you?',
    'How might this change in the future?',
  ];
  const next = () => {
    if (question >= questions.length - 1) onFinish();
    else setQuestion((current) => current + 1);
  };
  return (
    <AppScreen>
      <PageHeader onBack={onBack} eyebrow={`IELTS ${part.toUpperCase()} · ${question + 1}/${questions.length}`} title="口语模拟进行中" />
      <ProgressBar value={question + 1} max={questions.length} />
      <View style={styles.sessionExaminer}>
        <Image source={examinerAssets.daniel} style={styles.examinerLarge} contentFit="contain" />
        <Text style={styles.examinerTitle}>Daniel · IELTS Examiner</Text>
        <Text style={uiStyles.muted}>{paused ? '已暂停' : '正在聆听你的回答'}</Text>
      </View>
      <Card style={styles.questionCard}>
        <Text style={styles.questionNumber}>QUESTION {question + 1}</Text>
        <Text style={styles.question}>{questions[question]}</Text>
      </Card>
      <View style={styles.callControls}>
        <Pressable onPress={() => setPaused((current) => !current)} style={[styles.roundControl, !paused && styles.roundControlOn]}>
          <AppIcon name={paused ? 'microphone-off' : 'microphone'} size={25} />
        </Pressable>
        <Pressable style={styles.roundControl}>
          <AppIcon name="subtitles" size={23} />
        </Pressable>
      </View>
      <AppButton title={question === questions.length - 1 ? '结束模拟' : '下一题'} onPress={next} />
    </AppScreen>
  );
}

export function IeltsFlow({ onExit }: { onExit: () => void }) {
  const { addIeltsRecord } = useAppModel();
  const [route, setRoute] = useState<IeltsRoute>('intake');
  const [target, setTarget] = useState('7.0');
  const [part, setPart] = useState('p2');
  const [topic, setTopic] = useState('一次难忘的旅行');
  const [examiner, setExaminer] = useState<(typeof examiners)[number]>(examiners[0]);
  const [progress, setProgress] = useState(0);

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
    return (
      <AppScreen>
        <PageHeader onBack={onExit} eyebrow="IELTS SPEAKING" title="先设定你的目标" subtitle="我们会据此调整反馈重点和练习节奏。" />
        <Card>
          <Text style={uiStyles.title}>目标分数</Text>
          <View style={styles.bandOptions}>
            {['6.0', '6.5', '7.0', '7.5+'].map((band) => (
              <Pressable key={band} onPress={() => setTarget(band)} style={[styles.bandOption, target === band && styles.selected]}>
                <Text style={[styles.bandOptionText, target === band && styles.selectedText]}>{band}</Text>
              </Pressable>
            ))}
          </View>
        </Card>
        <Card>
          <Text style={uiStyles.title}>当前状态</Text>
          <Text style={uiStyles.body}>已有少量备考经验，希望提升内容展开与表达自然度。</Text>
        </Card>
        <AppButton title="进入 IELTS 专项" icon="arrow-right" onPress={() => setRoute('home')} />
      </AppScreen>
    );
  }

  if (route === 'home') {
    return (
      <AppScreen>
        <PageHeader onBack={onExit} eyebrow={`目标分数 ${target}`} title="IELTS 口语训练" subtitle="分项训练薄弱环节，或直接完成一套全真模拟。" />
        <Card style={styles.mockCard}>
          <Pill dark>FULL MOCK</Pill>
          <Text style={styles.mockTitle}>一次完整口语模拟</Text>
          <Text style={styles.mockCopy}>Part 1–3 · 约 14 分钟 · 完整评分报告</Text>
          <AppButton
            title="开始全真模拟"
            variant="secondary"
            onPress={() => {
              setPart('mock');
              setTopic('Full IELTS Mock');
              setRoute('setup');
            }}
          />
        </Card>
        <SectionTitle eyebrow="分项练习" title="选择一个 Part" />
        {ieltsParts.map((item) => (
          <Card
            key={item.id}
            onPress={() => {
              setPart(item.id);
              setRoute('topics');
            }}
            style={styles.partCard}
          >
            <Text style={styles.partNumber}>{item.number}</Text>
            <View style={uiStyles.flex}>
              <Pill>{item.label}</Pill>
              <Text style={styles.partTitle}>{item.title}</Text>
              <Text style={uiStyles.muted}>{item.note} · {item.duration}</Text>
            </View>
            <AppIcon name="chevron-right" size={19} color={colors.subtle} />
          </Card>
        ))}
      </AppScreen>
    );
  }

  if (route === 'topics') {
    const topics = ieltsTopics[part as keyof typeof ieltsTopics] ?? ieltsTopics.p2;
    return (
      <AppScreen>
        <PageHeader onBack={() => setRoute('home')} eyebrow={`IELTS ${part.toUpperCase()}`} title="选择练习话题" subtitle="优先练习标记为“建议复练”的话题。" />
        {topics.map((item) => (
          <Card
            key={item.title}
            onPress={() => {
              setTopic(item.title);
              setRoute('setup');
            }}
          >
            <View style={styles.rowBetween}>
              <Pill>{item.category}</Pill>
              <Text style={styles.topicState}>{item.state}</Text>
            </View>
            <Text style={styles.topicTitle}>{item.title}</Text>
          </Card>
        ))}
      </AppScreen>
    );
  }

  if (route === 'setup') {
    return (
      <AppScreen>
        <PageHeader onBack={() => setRoute(part === 'mock' ? 'home' : 'topics')} eyebrow="模拟设置" title="准备开始" subtitle={`${part.toUpperCase()} · ${topic}`} />
        <SectionTitle title="选择考官" />
        <View style={styles.examinerGrid}>
          {examiners.map((item) => (
            <Pressable key={item.id} onPress={() => setExaminer(item)} style={[styles.examinerCard, examiner.id === item.id && styles.selected]}>
              <Image source={item.image} style={styles.examinerImage} contentFit="contain" />
              <Text style={styles.examinerName}>{item.name}</Text>
              <Text style={uiStyles.muted}>{item.accent}口音</Text>
            </Pressable>
          ))}
        </View>
        <Card style={styles.deviceCheck}>
          <AppIcon name="check-circle" size={24} color={colors.green} />
          <View style={uiStyles.flex}>
            <Text style={uiStyles.title}>设备检查通过</Text>
            <Text style={uiStyles.muted}>麦克风可用，环境音量适合开始练习。</Text>
          </View>
        </Card>
        <AppButton title={`与 ${examiner.name} 开始模拟`} onPress={() => setRoute('session')} />
      </AppScreen>
    );
  }

  if (route === 'session') {
    return <IeltsSession part={part} topic={topic} onBack={() => setRoute('setup')} onFinish={() => { setProgress(0); setRoute('analysis'); }} />;
  }

  if (route === 'analysis') {
    return (
      <AppScreen contentStyle={styles.analysis}>
        <AppIcon name="sliders" size={32} />
        <Text style={styles.analysisTitle}>正在分析你的口语表现</Text>
        <Text style={uiStyles.muted}>评估流利度、词汇、语法和发音，并生成可复练的表达。</Text>
        <ProgressBar value={progress} />
        <Text style={styles.progressText}>{progress}%</Text>
      </AppScreen>
    );
  }

  return (
    <AppScreen>
      <PageHeader onBack={() => setRoute('home')} eyebrow={`${part.toUpperCase()} · ${topic}`} title="IELTS 练习报告" subtitle="本次回答结构完整，下一步重点提升观点展开。" />
      <View style={styles.bandHero}>
        <Text style={styles.bandScore}>6.5</Text>
        <Text style={styles.bandLabel}>Estimated Band</Text>
      </View>
      <View style={styles.metrics}>
        <Metric label="流利与连贯" value="6.5" />
        <Metric label="词汇资源" value="7.0" />
      </View>
      <View style={styles.metrics}>
        <Metric label="语法范围" value="6.0" />
        <Metric label="发音" value="6.5" />
      </View>
      <Card>
        <Text style={uiStyles.title}>最值得改进的一点</Text>
        <Text style={uiStyles.body}>给出观点后再补充一个具体例子，避免答案停留在抽象判断。</Text>
      </Card>
      <AppButton
        title="保存报告并返回"
        onPress={() => {
          addIeltsRecord({
            id: `ielts-${Date.now()}`,
            type: part === 'p1' ? 'Part 1' : part === 'p3' ? 'Part 3' : 'Part 2',
            title: topic,
            date: '刚刚',
            duration: '4 分钟',
            result: '预估 6.5',
            estimatedBand: 6.5,
            scores: [68, 72, 64, 70],
          });
          setRoute('home');
        }}
      />
    </AppScreen>
  );
}

type InterviewRoute = 'input' | 'preparing' | 'live' | 'finalizing' | 'report';

export function InterviewFlow({ onExit }: { onExit: () => void }) {
  const { addInterviewRecord } = useAppModel();
  const [route, setRoute] = useState<InterviewRoute>('input');
  const [role, setRole] = useState('产品经理');
  const [company, setCompany] = useState('');
  const [duration, setDuration] = useState('15');
  const [resume, setResume] = useState(false);
  const [question, setQuestion] = useState(0);
  const [progress, setProgress] = useState(0);

  useEffect(() => {
    if (route !== 'preparing' && route !== 'finalizing') return;
    const timer = setInterval(() => setProgress((current) => Math.min(100, current + 16)), 230);
    return () => clearInterval(timer);
  }, [route]);

  useEffect(() => {
    if ((route !== 'preparing' && route !== 'finalizing') || progress < 100) return;
    const timer = setTimeout(() => setRoute(route === 'preparing' ? 'live' : 'report'), 300);
    return () => clearTimeout(timer);
  }, [progress, route]);

  if (route === 'input') {
    return (
      <AppScreen>
        <PageHeader onBack={onExit} eyebrow="AI INTERVIEW" title="创建一次英文模拟面试" subtitle="输入职位背景，AI 会生成更贴近真实招聘的追问。" />
        <View style={styles.interviewSteps}>
          {['职位信息', 'AI 准备', '模拟面试'].map((item, index) => (
            <View key={item} style={styles.interviewStep}>
              <Text style={styles.stepNumber}>0{index + 1}</Text>
              <Text style={styles.stepText}>{item}</Text>
            </View>
          ))}
        </View>
        <View style={styles.formGroup}>
          <Text style={styles.fieldLabel}>目标职位</Text>
          <TextInput value={role} onChangeText={setRole} placeholder="例如：Product Manager" placeholderTextColor={colors.subtle} style={styles.input} />
        </View>
        <View style={styles.formGroup}>
          <Text style={styles.fieldLabel}>公司或行业（可选）</Text>
          <TextInput value={company} onChangeText={setCompany} placeholder="例如：消费互联网" placeholderTextColor={colors.subtle} style={styles.input} />
        </View>
        <Pressable onPress={() => setResume((current) => !current)} style={[styles.uploadCard, resume && styles.selected]}>
          <AppIcon name={resume ? 'check-circle' : 'upload'} size={24} />
          <View style={uiStyles.flex}>
            <Text style={uiStyles.title}>{resume ? 'resume-yufan.pdf' : '添加简历（可选）'}</Text>
            <Text style={uiStyles.muted}>{resume ? '简历已加入本次面试上下文' : '支持 PDF / DOCX，当前为前端选择态'}</Text>
          </View>
        </Pressable>
        <View style={styles.bandOptions}>
          {['10', '15', '20'].map((item) => (
            <Pressable key={item} onPress={() => setDuration(item)} style={[styles.bandOption, duration === item && styles.selected]}>
              <Text style={[styles.bandOptionText, duration === item && styles.selectedText]}>{item} 分钟</Text>
            </Pressable>
          ))}
        </View>
        <AppButton
          title="生成面试"
          icon="arrow-right"
          onPress={() => {
            setProgress(0);
            setRoute('preparing');
          }}
        />
      </AppScreen>
    );
  }

  if (route === 'preparing' || route === 'finalizing') {
    const finalizing = route === 'finalizing';
    return (
      <AppScreen contentStyle={styles.analysis}>
        <AppIcon name={finalizing ? 'document' : 'briefcase'} size={34} />
        <Text style={styles.analysisTitle}>{finalizing ? '正在生成面试复盘' : 'AI 正在准备你的面试'}</Text>
        <Text style={uiStyles.muted}>{finalizing ? '整理回答亮点、风险点和更好的表达方式。' : `基于“${role}”岗位生成问题与追问路径。`}</Text>
        <ProgressBar value={progress} />
        <Text style={styles.progressText}>{progress}%</Text>
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
    return (
      <AppScreen>
        <PageHeader onBack={() => setRoute('input')} eyebrow={`${role} · ${duration} 分钟`} title="模拟面试进行中" />
        <ProgressBar value={question + 1} max={interviewQuestions.length} />
        <View style={styles.sessionExaminer}>
          <Image source={examinerAssets.sophia} style={styles.examinerLarge} contentFit="contain" />
          <Text style={styles.examinerTitle}>AI 面试官</Text>
          <Text style={uiStyles.muted}>正在等待你的回答</Text>
        </View>
        <Card style={styles.questionCard}>
          <Text style={styles.questionNumber}>QUESTION {question + 1}</Text>
          <Text style={styles.question}>{interviewQuestions[question]}</Text>
        </Card>
        <View style={styles.callControls}>
          <Pressable style={[styles.roundControl, styles.roundControlOn]}>
            <AppIcon name="microphone" size={25} />
          </Pressable>
        </View>
        <AppButton title={question === interviewQuestions.length - 1 ? '结束面试' : '完成回答'} onPress={next} />
      </AppScreen>
    );
  }

  return (
    <AppScreen>
      <PageHeader onBack={onExit} eyebrow={`${role}${company ? ` · ${company}` : ''}`} title="面试复盘" subtitle="你的回答有清晰的决策过程，下一步要让结果和影响更具体。" />
      <View style={styles.scoreHero}>
        <Text style={styles.score}>82</Text>
        <Text style={uiStyles.muted}>综合表现</Text>
      </View>
      <View style={styles.metrics}>
        <Metric label="岗位匹配" value="84" />
        <Metric label="表达清晰" value="86" />
        <Metric label="回答深度" value="76" />
      </View>
      <Card>
        <Text style={uiStyles.title}>更好的表达方式</Text>
        <Text style={uiStyles.body}>I validated the riskiest assumption first, aligned the team on a reversible test, and used the result to decide whether to scale.</Text>
      </Card>
      <Card>
        <Text style={uiStyles.title}>下一次重点</Text>
        <Text style={uiStyles.body}>用数字说明决策带来的业务影响，并在回答结尾明确总结你的个人贡献。</Text>
      </Card>
      <AppButton
        title="保存复盘并返回"
        onPress={() => {
          addInterviewRecord({
            id: `interview-${Date.now()}`,
            role: `${role}英文面试`,
            company: company || '未填写公司',
            date: '刚刚',
            duration: `${duration} 分钟`,
            score: 82,
            summary: '回答结构清楚，下一步要让结果和影响更具体。',
            scores: [84, 86, 76, 81],
          });
          onExit();
        }}
      />
    </AppScreen>
  );
}

const styles = StyleSheet.create({
  bandOptions: { flexDirection: 'row', gap: 8 },
  bandOption: { minHeight: 48, flex: 1, alignItems: 'center', justifyContent: 'center', borderWidth: 1, borderColor: colors.line, borderRadius: 12, backgroundColor: colors.white },
  selected: { borderColor: colors.ink, borderWidth: 2 },
  bandOptionText: { color: colors.muted, fontSize: 13, fontWeight: '500' },
  selectedText: { color: colors.ink },
  mockCard: { padding: 20, backgroundColor: colors.ink },
  mockTitle: { color: colors.white, fontSize: 23, lineHeight: 31, fontWeight: '600' },
  mockCopy: { color: '#C7C7C1', fontSize: 13, lineHeight: 20, fontWeight: '300' },
  partCard: { minHeight: 128, flexDirection: 'row', alignItems: 'center', gap: 13 },
  partNumber: { color: '#B2B2AD', fontSize: 27, fontWeight: '500' },
  partTitle: { marginTop: 8, color: colors.ink, fontSize: 18, fontWeight: '600' },
  rowBetween: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  topicState: { color: colors.muted, fontSize: 11, fontWeight: '500' },
  topicTitle: { color: colors.ink, fontSize: 18, fontWeight: '600' },
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
  bandHero: { paddingVertical: 28, alignItems: 'center', borderRadius: 22, backgroundColor: colors.ink },
  bandScore: { color: colors.white, fontSize: 82, fontWeight: '600', letterSpacing: -5 },
  bandLabel: { color: '#C7C7C1', fontSize: 11, fontWeight: '300', letterSpacing: 1.2 },
  metrics: { flexDirection: 'row', gap: 8 },
  interviewSteps: { flexDirection: 'row', gap: 7 },
  interviewStep: { flex: 1, padding: 11, gap: 6, borderRadius: 13, backgroundColor: colors.soft },
  stepNumber: { color: colors.subtle, fontSize: 10, fontWeight: '500' },
  stepText: { color: colors.ink, fontSize: 11, lineHeight: 16, fontWeight: '500' },
  formGroup: { gap: 8 },
  fieldLabel: { color: colors.ink, fontSize: 13, fontWeight: '500' },
  input: { minHeight: 52, paddingHorizontal: 14, color: colors.ink, fontSize: 15, fontWeight: '300', borderWidth: 1, borderColor: colors.line, borderRadius: 13, backgroundColor: colors.white },
  uploadCard: { minHeight: 78, padding: 13, flexDirection: 'row', alignItems: 'center', gap: 12, borderWidth: 1, borderColor: colors.line, borderRadius: 14 },
  scoreHero: { alignItems: 'center', paddingVertical: 10 },
  score: { color: colors.ink, fontSize: 72, fontWeight: '600', letterSpacing: -4 },
});
