export type LearningExpression = {
  id: string;
  type: '单词' | '词组' | '句子';
  englishText: string;
  chineseText: string;
  phonetic?: string;
};

export type DialogueTurnFeedback = {
  suggestedExpression: string;
  feedbackSummary: string;
};

export type AssetDialogueMessage = {
  id: string;
  role: 'assistant' | 'user';
  speaker: string;
  text: string;
  feedback?: DialogueTurnFeedback;
};

export type SceneLearningRecord = {
  id: string;
  title: string;
  date: string;
  status: '已完成' | '待练习';
  score: number | null;
  practiceCount: number;
  expressions: LearningExpression[];
  conversation: AssetDialogueMessage[];
};

export type IeltsLearningRecord = {
  id: string;
  type: 'Part 1' | 'Part 2' | 'Part 3' | '完整模考';
  title: string;
  date: string;
  duration: string;
  result: string;
  estimatedBand: number;
  scores: readonly [number, number, number, number];
};

export type InterviewLearningRecord = {
  id: string;
  role: string;
  company: string;
  date: string;
  duration: string;
  score: number | null;
  summary: string;
  scores: readonly [number, number, number, number];
};

const coffeeExpressions: LearningExpression[] = [
  { id: 'recommend', type: '单词', englishText: 'recommend', chineseText: '推荐；建议', phonetic: '/ˌrek.əˈmend/' },
  { id: 'feel-like', type: '词组', englishText: 'feel like trying something different', chineseText: '想尝试一些不一样的选择', phonetic: '/fiːl laɪk ˈtraɪ.ɪŋ ˈsʌm.θɪŋ ˈdɪf.ər.ənt/' },
  { id: 'oat-milk', type: '词组', englishText: 'with oat milk', chineseText: '换成燕麦奶', phonetic: '/wɪð oʊt mɪlk/' },
  { id: 'less-sweet', type: '句子', englishText: 'Could you recommend something less sweet?', chineseText: '你能推荐一些不太甜的吗？' },
];

export const initialSceneLearningRecords: SceneLearningRecord[] = [
  {
    id: 'coffee',
    title: '咖啡店点单',
    date: '刚刚',
    status: '已完成',
    score: 86,
    practiceCount: 2,
    expressions: coffeeExpressions,
    conversation: [
      { id: 'coffee-ai-1', role: 'assistant', speaker: 'James', text: 'Good morning! What can I get started for you today?' },
      {
        id: 'coffee-user-1',
        role: 'user',
        speaker: '你',
        text: 'I want a latte, but not too sweet.',
        feedback: {
          suggestedExpression: 'I’d like a latte that isn’t too sweet, please.',
          feedbackSummary: '需求表达清楚；使用 “I’d like” 和 “please” 会让点单语气更自然、礼貌。',
        },
      },
      { id: 'coffee-ai-2', role: 'assistant', speaker: 'James', text: 'Of course. Would you like regular milk or oat milk?' },
      {
        id: 'coffee-user-2',
        role: 'user',
        speaker: '你',
        text: 'With oat milk, and can I take it away?',
        feedback: {
          suggestedExpression: 'With oat milk, please. Could I get that to go?',
          feedbackSummary: '信息完整；“get that to go” 更符合美式咖啡店的常用表达。',
        },
      },
    ],
  },
  {
    id: 'hotel',
    title: '酒店入住办理',
    date: '2 天前',
    status: '已完成',
    score: 78,
    practiceCount: 1,
    expressions: [
      { id: 'reservation', type: '单词', englishText: 'reservation', chineseText: '预订', phonetic: '/ˌrez.ɚˈveɪ.ʃən/' },
      { id: 'under-name', type: '词组', englishText: 'under the name of', chineseText: '以……的名字预订' },
      { id: 'check-in', type: '句子', englishText: 'I have a reservation under the name of Yufan.', chineseText: '我用 Yufan 的名字预订了房间。' },
    ],
    conversation: [
      { id: 'hotel-ai-1', role: 'assistant', speaker: 'David', text: 'Welcome. Do you have a reservation with us?' },
      {
        id: 'hotel-user-1',
        role: 'user',
        speaker: '你',
        text: 'Yes, I booked a room with my name Yufan.',
        feedback: {
          suggestedExpression: 'Yes, I have a reservation under the name of Yufan.',
          feedbackSummary: '意思准确；酒店入住时使用 “reservation under the name of” 更地道。',
        },
      },
    ],
  },
];

export const initialIeltsLearningRecords: IeltsLearningRecord[] = [
  { id: 'ielts-mock-01', type: '完整模考', title: '完整口语模拟', date: '今天', duration: '14 分钟', result: '预估 6.5', estimatedBand: 6.5, scores: [68, 72, 64, 70] },
  { id: 'ielts-p2-travel', type: 'Part 2', title: '一次难忘的旅行', date: '2 天前', duration: '4 分钟', result: '结构完整', estimatedBand: 6.5, scores: [70, 67, 63, 71] },
  { id: 'ielts-p1-home', type: 'Part 1', title: 'Home & Accommodation', date: '5 天前', duration: '3 分钟', result: '建议复练', estimatedBand: 6, scores: [65, 61, 62, 68] },
];

export const initialInterviewLearningRecords: InterviewLearningRecord[] = [
  { id: 'interview-pm', role: '产品经理英文面试', company: '消费互联网', date: '今天', duration: '15 分钟', score: 82, summary: '回答结构清楚，下一步需要用数据强化业务影响。', scores: [84, 86, 76, 81] },
  { id: 'interview-operations', role: '海外运营英文面试', company: 'SaaS', date: '6 天前', duration: '12 分钟', score: 78, summary: '沟通自然，案例结尾需要更明确地总结个人贡献。', scores: [80, 82, 72, 78] },
];
