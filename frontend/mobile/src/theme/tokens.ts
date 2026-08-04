import type { ImageSourcePropType } from 'react-native';

export const colors = {
  ink: '#151514',
  muted: '#777773',
  subtle: '#92928E',
  line: '#DEDED8',
  canvas: '#FFFFFF',
  soft: '#F4F4F1',
  paper: '#FBFBF9',
  white: '#FFFFFF',
  green: '#4A765E',
  greenSoft: '#EDF5EF',
  red: '#A8463C',
  redSoft: '#FAF2F0',
} as const;

export const spacing = {
  xs: 6,
  sm: 10,
  md: 16,
  lg: 22,
  xl: 28,
} as const;

export type Teacher = {
  id: string;
  name: string;
  accent: string;
  personality: string;
  image: ImageSourcePropType;
};

export const teachers: Teacher[] = [
  {
    id: 'clara',
    name: 'Clara',
    accent: '美式口音',
    personality: '温柔耐心',
    image: require('../../assets/images/unispeaking/teachers/clara.png'),
  },
  {
    id: 'james',
    name: 'James',
    accent: '英式口音',
    personality: '清晰理性',
    image: require('../../assets/images/unispeaking/teachers/james.png'),
  },
  {
    id: 'leo',
    name: 'Leo',
    accent: '美式口音',
    personality: '开朗活力',
    image: require('../../assets/images/unispeaking/teachers/leo.png'),
  },
  {
    id: 'david',
    name: 'David',
    accent: '美式口音',
    personality: '沉稳直接',
    image: require('../../assets/images/unispeaking/teachers/david.png'),
  },
  {
    id: 'emily',
    name: 'Emily',
    accent: '英式口音',
    personality: '自然亲切',
    image: require('../../assets/images/unispeaking/teachers/emily.png'),
  },
  {
    id: 'arthur',
    name: 'Arthur',
    accent: '英式口音',
    personality: '睿智从容',
    image: require('../../assets/images/unispeaking/teachers/arthur.png'),
  },
];

export const levels = [
  { id: 'starter', title: '刚开始学', note: '能听懂或说出少量单词' },
  { id: 'basic', title: '可以简单交流', note: '能用简单句表达基本需求' },
  { id: 'independent', title: '可以连续表达', note: '能围绕熟悉话题说一段话' },
  { id: 'fluent', title: '表达比较流利', note: '能自然参与大多数日常交流' },
] as const;

export const speedOptions = ['慢一些', '适中', '自然', '快一些'] as const;

export const brandAssets = {
  mark: require('../../assets/images/unispeaking/brand-mark.jpg'),
  wordmark: require('../../assets/images/unispeaking/wordmark.png'),
} as const;

export const examinerAssets = {
  daniel: require('../../assets/images/unispeaking/examiners/daniel.png'),
  marcus: require('../../assets/images/unispeaking/examiners/marcus.png'),
  margaret: require('../../assets/images/unispeaking/examiners/margaret.png'),
  sophia: require('../../assets/images/unispeaking/examiners/sophia.png'),
} as const;
