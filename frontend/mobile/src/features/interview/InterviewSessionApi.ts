import { File } from 'expo-file-system';

import type { ApiRequestOptions } from '@/infrastructure/http/ApiClient';

export type InterviewProvider =
  | 'QWEN'
  | 'OPENAI'
  | 'DEEPSEEK'
  | 'IFLYTEK'
  | 'ALIYUN'
  | 'MINIMAX'
  | 'DOUBAO';

export type InterviewSessionStatus =
  | 'CREATED'
  | 'CONNECTING'
  | 'WAITING_CLIENT'
  | 'ACTIVE'
  | 'PAUSED'
  | 'INTERRUPTED'
  | 'COMPLETED'
  | 'FAILED';

export type InterviewSceneType =
  | 'FREE_CHAT'
  | 'CUSTOM_SCENE'
  | 'IELTS_SCENE'
  | 'INTERVIEW_SCENE';

export type InterviewSceneFlowStage =
  | 'WORD_LEARNING'
  | 'PHRASE_LEARNING'
  | 'SENTENCE_LEARNING'
  | 'DIALOGUE'
  | 'IELTS_PART_1'
  | 'IELTS_PART_2'
  | 'IELTS_PART_3'
  | 'COMPLETED';

export type InterviewLearningContentItem = {
  contentId: string;
  englishText: string;
  chineseText: string;
  phonetic: string | null;
};

export type StartInterviewSessionRequest = {
  sceneId: string;
  offerSdp: string;
  provider: InterviewProvider;
  model: string;
  voice: string;
  translationEnabled: boolean;
};

export type StartInterviewSessionResponse = {
  sceneId: string;
  sceneName: string;
  sceneType: InterviewSceneType;
  wordList: InterviewLearningContentItem[];
  phraseList: InterviewLearningContentItem[];
  sentenceList: InterviewLearningContentItem[];
  currentStage: InterviewSceneFlowStage;
  scoringEnabled: boolean;
  sessionId: string;
  providerSessionId: string;
  answerSdp: string;
  credentialExpiresAt: string;
  voiceId: string;
  status: InterviewSessionStatus;
  startTime: string;
  systemPrompt: string;
};

export type InterviewTurnState = {
  shouldEnd: boolean;
  completedTopicCount: number;
  coveredTopicCount: number;
  currentTopic: string;
  controlInstruction: string;
};

export type InterviewReportStatus = 'PROCESSING' | 'COMPLETED' | 'FAILED';

export type InterviewTurnResponse = {
  state: InterviewTurnState;
  reportStatus: 'PROCESSING' | null;
};

export type InterviewEndResponse = {
  sessionId: string;
  reportStatus: InterviewReportStatus;
};

export type InterviewDimension =
  | 'FLUENCY'
  | 'PRONUNCIATION_INTELLIGIBILITY'
  | 'LOGIC_COHERENCE'
  | 'GRAMMAR_CONTROL'
  | 'VOCABULARY_EXPRESSION';

export type InterviewDimensionScore = {
  dimension: InterviewDimension;
  score: number | null;
  evaluation: string;
  advice: string;
};

export type InterviewReport = {
  sessionId: string;
  sceneId: string;
  overallScore: number;
  summary: string;
  dimensions: InterviewDimensionScore[];
  completedAt: string;
};

export type ProcessingInterviewReport = {
  sessionId: string;
  sceneId: string;
  status: 'PROCESSING';
  report: null;
  failureReason: null;
};

export type CompletedInterviewReport = {
  sessionId: string;
  sceneId: string;
  status: 'COMPLETED';
  report: InterviewReport;
  failureReason: null;
};

export type FailedInterviewReport = {
  sessionId: string;
  sceneId: string;
  status: 'FAILED';
  report: null;
  failureReason: string;
};

export type InterviewReportResponse =
  | ProcessingInterviewReport
  | CompletedInterviewReport
  | FailedInterviewReport;

export type InterviewAssetItem = {
  sceneId: string;
  jobTitle: string;
  difficulty: string;
  latestSessionId: string | null;
  latestReportStatus: InterviewReportStatus | null;
  latestOverallScore: number | null;
  latestPracticedAt: string | null;
  practiceCount: number;
  createdAt: string;
};

type ApiRequester = {
  request(path: string, options?: ApiRequestOptions): Promise<unknown>;
};

const START_TIMEOUT_MS = 20_000;
const TURN_TIMEOUT_MS = 30_000;
const END_TIMEOUT_MS = 25_000;
const REPORT_TIMEOUT_MS = 15_000;
const RETRY_REPORT_TIMEOUT_MS = 30_000;

export function createInterviewWavFile(wavUri: string) {
  return new File(wavUri);
}

export class InterviewSessionApi {
  constructor(
    private readonly client: ApiRequester,
    private readonly sceneId: string,
  ) {}

  startSession(request: Omit<StartInterviewSessionRequest, 'sceneId'>) {
    return this.client.request(this.scenePath('sessions'), {
      method: 'POST',
      body: JSON.stringify(request),
      timeoutMs: START_TIMEOUT_MS,
    }) as Promise<StartInterviewSessionResponse>;
  }

  submitTurn(
    sessionId: string,
    turnNo: number,
    transcript: string,
    wavUri?: string,
  ) {
    const body = new FormData();
    body.append('transcript', transcript);
    if (wavUri) body.append('audio', createInterviewWavFile(wavUri));

    return this.client.request(
      `${this.sessionPath(sessionId)}/turns/${turnNo}`,
      { method: 'POST', body, timeoutMs: TURN_TIMEOUT_MS },
    ) as Promise<InterviewTurnResponse>;
  }

  end(sessionId: string) {
    return this.client.request(`${this.sessionPath(sessionId)}/end`, {
      method: 'POST',
      timeoutMs: END_TIMEOUT_MS,
    }) as Promise<InterviewEndResponse>;
  }

  getReport(sessionId: string) {
    return this.client.request(`${this.sessionPath(sessionId)}/report`, {
      timeoutMs: REPORT_TIMEOUT_MS,
    }) as Promise<InterviewReportResponse>;
  }

  retryReport(sessionId: string) {
    return this.client.request(`${this.sessionPath(sessionId)}/report/retry`, {
      method: 'POST',
      timeoutMs: RETRY_REPORT_TIMEOUT_MS,
    }) as Promise<InterviewReportResponse>;
  }

  listAssets() {
    return this.client.request('/api/interview-scenes/assets', {
      timeoutMs: REPORT_TIMEOUT_MS,
    }) as Promise<InterviewAssetItem[]>;
  }

  private scenePath(suffix: string) {
    return `/api/interview-scenes/${encodeURIComponent(this.sceneId)}/${suffix}`;
  }

  private sessionPath(sessionId: string) {
    return `${this.scenePath(`sessions/${encodeURIComponent(sessionId)}`)}`;
  }
}
