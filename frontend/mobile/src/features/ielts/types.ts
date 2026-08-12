export type IeltsPart = 'PART_1' | 'PART_2' | 'PART_3';
export type IeltsMode = 'PART_PRACTICE' | 'MOCK_TEST';
export type IeltsPart2Event =
  | 'PREPARATION_COMPLETE'
  | 'ANSWER_COMPLETE'
  | 'LONG_TURN_TIME_LIMIT';

export type IeltsSettings = {
  targetScore: number | null;
  todayCompletedCount: number;
  examinerId: string | null;
  preferredVoice: string | null;
  latestEstimatedScore: number | null;
  currentStreakDays: number;
  totalCheckInDays: number;
  lastCheckInDate: string | null;
};

export type IeltsCategory = {
  code: string;
  label: string;
};

export type IeltsTopicSummary = {
  id: string;
  title: string;
  topicType: string;
  category: string;
  categoryLabel: string;
  source: string;
  questionCount: number;
  practiceCount: number;
  mockTestCount: number;
  randomPartPracticeCount: number;
  selectedPartPracticeCount: number;
  latestPracticeType: string | null;
  latestPerformanceSummary: string | null;
  latestPerformanceScore: number | null;
  lastPracticedAt: string | null;
};

export type IeltsTopicSearchResult = {
  categories: IeltsCategory[];
  topics: IeltsTopicSummary[];
  page: number;
  pageSize: number;
  total: number;
  totalPages: number;
};

export type IeltsTrainingQuestion = {
  id: string;
  part: IeltsPart;
  sortNo: number;
  questionText: string;
  cuePoints: string[];
  recommendedExpressions: unknown[];
};

export type IeltsTraining = {
  topicId: string;
  title: string;
  part: IeltsPart;
  questions: IeltsTrainingQuestion[];
};

export type IeltsContentQuestion = {
  question: string;
  cue_points: string[];
  recommended_expressions: unknown[];
};

export type IeltsContent = {
  part1: IeltsContentQuestion[];
  part2: IeltsContentQuestion[];
  part3: IeltsContentQuestion[];
};

export type IeltsGeneration = {
  ieltsId: string;
  mode: IeltsMode;
  selectedPart: IeltsPart | null;
  selectedTopicId: string | null;
  title: string;
  content: IeltsContent;
  voiceId: string;
  scenePrompt: string;
};

export type IeltsSceneFlow = {
  sceneId: string;
  stage: string;
  completed: boolean;
};

export type IeltsEvaluationResult = {
  part: IeltsPart | null;
  assessmentType: string;
  overallBandScore: number | null;
  fluencyCoherenceScore: number | null;
  lexicalResourceScore: number | null;
  grammaticalRangeAccuracyScore: number | null;
  pronunciationScore: number | null;
  summary: string;
  strengths: string[];
  improvements: string[];
  recommendedExpressions: string[];
  fluencyCoherenceReason?: string | null;
  lexicalResourceReason?: string | null;
  grammaticalRangeAccuracyReason?: string | null;
  pronunciationReason?: string | null;
  partEvaluations?: IeltsPartEvaluation[];
};

export type IeltsPartEvaluation = {
  part: IeltsPart;
  fluencyCoherenceScore: number | null;
  lexicalResourceScore: number | null;
  grammaticalRangeAccuracyScore: number | null;
  pronunciationScore: number | null;
  summary: string;
  strengths: string[];
  improvements: string[];
  recommendedExpressions: string[];
  fluencyCoherenceReason?: string | null;
  lexicalResourceReason?: string | null;
  grammaticalRangeAccuracyReason?: string | null;
  pronunciationReason?: string | null;
};

export type IeltsEvaluationHistoryItem = IeltsEvaluationResult & {
  sessionId: string;
  ieltsId: string;
  mode: IeltsMode;
  topicSelectionMethod: string | null;
  topicTitles: Partial<Record<IeltsPart, string>>;
  recordingUrls: string[];
  startedAt: string;
  endedAt: string;
};

export type IeltsDialogueState = {
  sceneId: string;
  sessionId: string;
  part: IeltsPart;
  openingCompleted: boolean;
  answeredQuestions: number;
  totalQuestions: number;
  completed: boolean;
  controlInstruction: string;
};

export type IeltsPart2State = {
  sceneId: string;
  sessionId: string;
  phase: string;
  completed: boolean;
  controlInstruction: string;
};
