export const recommendations = [
  { id: 'coffee', title: '咖啡店点单', tag: '日常', duration: '8–10 分钟', level: '初级', goal: '流利点单，清晰表达需求' },
  { id: 'hotel', title: '酒店入住', tag: '旅行', duration: '10–12 分钟', level: '中级', goal: '礼貌沟通，确认入住细节' },
  { id: 'pharmacy', title: '药店咨询', tag: '生活', duration: '8–10 分钟', level: '中级', goal: '描述症状，确认用法与注意事项' },
] as const;

export type ScenePromptExample = {
  id: string;
  prompt: string;
};

// Local fallback for the future daily-prompt API. ScenesHome accepts an injected
// ScenePromptExample, so a backend response can replace this without changing UI code.
export const scenePromptExamples: readonly ScenePromptExample[] = [
  { id: 'fitness-first-visit', prompt: '第一次去健身房，咨询设施和会员体验' },
  { id: 'restaurant-allergy', prompt: '在餐厅点餐，并向服务员说明食物过敏' },
  { id: 'lost-luggage', prompt: '在机场行李服务台说明行李丢失情况' },
  { id: 'doctor-appointment', prompt: '预约医生，并清楚说明希望就诊的时间和原因' },
  { id: 'return-product', prompt: '到商店退换一件有质量问题的商品' },
  { id: 'meet-new-colleague', prompt: '第一次见新同事，进行自然的工作寒暄' },
  { id: 'haircut-request', prompt: '在理发店描述想要的发型和长度' },
];

export function getDailyScenePromptExample(date = new Date()): ScenePromptExample {
  const dayKey = Math.floor(Date.UTC(date.getFullYear(), date.getMonth(), date.getDate()) / 86_400_000);
  return scenePromptExamples[dayKey % scenePromptExamples.length];
}

export const learningItems = [
  { type: '单词', en: 'recommend', phonetic: '/ˌrek.əˈmend/', zh: '推荐；建议' },
  { type: '短语', en: 'feel like trying something different', phonetic: '/fiːl laɪk ˈtraɪ.ɪŋ ˈsʌm.θɪŋ ˈdɪf.ər.ənt/', zh: '想尝试一些不一样的选择' },
  { type: '短语', en: 'with oat milk', phonetic: '/wɪð oʊt mɪlk/', zh: '换成燕麦奶' },
  { type: '句子', en: 'Could you recommend something less sweet?', phonetic: '/kʊd juː ˌrek.əˈmend ˈsʌm.θɪŋ les swiːt/', zh: '你能推荐一些不太甜的吗？' },
] as const;

export const ieltsParts = [
  { id: 'p1', number: '01', label: 'Part 1', title: '日常问答', duration: '2–4 分钟', note: '单话题 · 4–5 道问题' },
  { id: 'p2', number: '02', label: 'Part 2', title: '长陈述', duration: '3–4 分钟', note: '1 分钟准备 · 2 分钟陈述' },
  { id: 'p3', number: '03', label: 'Part 3', title: '深入讨论', duration: '4–5 分钟', note: '关联话题与独立分类' },
] as const;

export const ieltsTopics = {
  p1: [
    { title: 'Home & Accommodation', category: '必考题', state: '建议复练' },
    { title: 'Work or Studies', category: '必考题', state: '已练习' },
    { title: 'Food', category: '事物', state: '未练习' },
  ],
  p2: [
    { title: '想见的名人', category: '人物', state: '建议复练' },
    { title: '一次难忘的旅行', category: '事件', state: '已练习' },
    { title: '一个安静的地方', category: '地点', state: '已练习' },
  ],
  p3: [
    { title: '名人与社会影响', category: '关联话题', state: '建议复练' },
    { title: '计划与未来选择', category: '关联话题', state: '已练习' },
    { title: '科技', category: '独立分类', state: '已练习' },
  ],
} as const;

export const interviewQuestions = [
  'Could you walk me through a product decision you made with incomplete information?',
  'What trade-off did you make, and how did you communicate it to the team?',
  'What did you learn from the result, and what would you do differently next time?',
] as const;
