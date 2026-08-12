export const teachers = [
  {
    id: "clara",
    name: "Clara",
    accent: "美式口音",
    personality: "温柔耐心",
    method: "会放慢节奏，用轻松追问帮助你继续说下去。",
    intro: "Hi, I’m Clara. Take your time — we’ll make speaking English feel natural and easy.",
    image: "/teachers/clara.png",
    previewAudio: "/teachers/audio/clara.wav",
    voiceId: "Katerina",
  },
  {
    id: "james",
    name: "James",
    accent: "英式口音",
    personality: "清晰理性",
    method: "擅长梳理表达逻辑，适合希望说得更有条理的学习者。",
    intro: "Hello, I’m James. Let’s turn your ideas into clear, confident English.",
    image: "/teachers/james.png",
    previewAudio: "/teachers/audio/james.wav",
    voiceId: "Harvey",
  },
  {
    id: "leo",
    name: "Leo",
    accent: "美式口音",
    personality: "开朗活力",
    method: "像朋友一样自然聊天，快速降低开口压力。",
    intro: "Hey, I’m Leo! Don’t overthink it — just speak, and we’ll have a great chat.",
    image: "/teachers/leo.png",
    previewAudio: "/teachers/audio/leo.wav",
    voiceId: "Raymond",
  },
  {
    id: "david",
    name: "David",
    accent: "美式口音",
    personality: "沉稳直接",
    method: "关注表达效率，适合职场沟通与面试准备。",
    intro: "Hi, I’m David. We’ll make your English concise, natural, and ready for work.",
    image: "/teachers/david.png",
    previewAudio: "/teachers/audio/david.wav",
    voiceId: "Aiden",
  },
  {
    id: "emily",
    name: "Emily",
    accent: "英式口音",
    personality: "自然亲切",
    method: "善于从日常话题展开，让对话持续而不尴尬。",
    intro: "Hi, I’m Emily. Let’s talk about everyday life and enjoy the conversation.",
    image: "/teachers/emily.png",
    previewAudio: "/teachers/audio/emily.wav",
    voiceId: "Tina",
  },
  {
    id: "arthur",
    name: "Arthur",
    accent: "英式口音",
    personality: "睿智从容",
    method: "提供更成熟的表达方式，适合进阶交流。",
    intro: "Good to meet you. I’m Arthur. Let’s give your English more depth and confidence.",
    image: "/teachers/arthur.png",
    previewAudio: "/teachers/audio/arthur.wav",
    voiceId: "Dolce",
  },
];

export const levels = [
  { id: "starter", cefrLevel: "A", title: "刚开始学", note: "能听懂或说出少量单词" },
  { id: "basic", cefrLevel: "B", title: "可以简单交流", note: "能用简单句表达基本需求" },
  { id: "independent", cefrLevel: "C", title: "可以连续表达", note: "能围绕熟悉话题说一段话" },
  { id: "fluent", cefrLevel: "D", title: "表达比较流利", note: "能自然参与大多数日常交流" },
];

export const sceneCategories = {
  food: { label: "餐饮", backgroundColor: "#FFF0C7", subtleBackgroundColor: "#FFF8E8", textColor: "#8A5400" },
  shopping: { label: "购物", backgroundColor: "#FFE4ED", subtleBackgroundColor: "#FFF2F7", textColor: "#A32658" },
  transit: { label: "出行", backgroundColor: "#E3EFFF", subtleBackgroundColor: "#F0F6FF", textColor: "#245FA8" },
  accommodation: { label: "住宿", backgroundColor: "#F0E6FA", subtleBackgroundColor: "#F6F1FC", textColor: "#69419A" },
  health: { label: "健康", backgroundColor: "#DFF4E6", subtleBackgroundColor: "#ECF8F0", textColor: "#247344" },
  workplace: { label: "职场", backgroundColor: "#E6EAF3", subtleBackgroundColor: "#F1F3F8", textColor: "#3E527C" },
  social: { label: "社交", backgroundColor: "#FFE5E1", subtleBackgroundColor: "#FFF1EE", textColor: "#A33E35" },
  education: { label: "学习", backgroundColor: "#DDF3F0", subtleBackgroundColor: "#ECF9F7", textColor: "#176B63" },
  services: { label: "服务", backgroundColor: "#EBECEA", subtleBackgroundColor: "#F3F4F2", textColor: "#555954" },
  other: { label: "其他", backgroundColor: "#1B1B1A", subtleBackgroundColor: "#F0F0EF", textColor: "#FFFFFF", subtleTextColor: "#4B4B48" },
};

export const recommendations = [
  { id: "coffee", number: "01", title: "咖啡店点单", category: "food", duration: "8–10 分钟", level: "初级", goal: "流利点单，清晰表达需求" },
  { id: "hotel", number: "02", title: "酒店入住", category: "accommodation", duration: "10–12 分钟", level: "中级", goal: "礼貌沟通，确认入住细节" },
  { id: "pharmacy", number: "03", title: "药店咨询", category: "health", duration: "8–10 分钟", level: "中级", goal: "描述症状，确认用法与注意事项" },
];

export const learningItems = [
  { type: "单词", en: "recommend", zh: "推荐；建议" },
  { type: "短语", en: "feel like trying something different", zh: "想尝试一些不一样的选择" },
  { type: "短语", en: "with oat milk", zh: "换成燕麦奶" },
  { type: "句子", en: "Could you recommend something less sweet?", zh: "你能推荐一些不太甜的吗？" },
];

export const assetRecords = [
  { id: "coffee", title: "咖啡店点单", category: "普通场景", date: "今天", status: "已完成", score: 84, items: 4 },
  { id: "hotel", title: "酒店入住办理", category: "普通场景", date: "2 天前", status: "已完成", score: 78, items: 5 },
  { id: "ielts", title: "IELTS Part 2 · 一次旅行", category: "IELTS", date: "1 周前", status: "已完成", score: 6.5, items: 5 },
];

export const plans = [
  {
    id: "free",
    name: "免费版",
    price: "0",
    suffix: "",
    desc: "适合轻量体验与每日开口",
    features: ["每天 5 分钟自由对话", "每天 1 次普通场景", "全部六位 AI 老师"],
  },
  {
    id: "pro",
    name: "专业版",
    price: "48",
    suffix: "/ 月",
    desc: "适合稳定提升日常与职场口语",
    features: ["每月 600 分钟自由对话", "每月 50 次普通场景", "全部六位 AI 老师"],
  },
  {
    id: "intensive",
    name: "特训版",
    price: "198",
    suffix: "/ 月",
    desc: "适合雅思备考与英文面试",
    features: ["包含专业版全部权益", "IELTS Part 1 / 2 / 3 模拟", "英文面试与材料分析", "每天 5 次特训，共用 150 次/月"],
  },
];
