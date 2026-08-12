import { File as ExpoFile } from 'expo-file-system';
import { useCallback, useRef, useState } from 'react';

import { InterviewService, type InterviewDifficulty, type InterviewMaterial, type InterviewScene } from './InterviewService';
import { SecureTokenStore } from '@/infrastructure/auth/SecureTokenStore';
import { getRuntimeConfig } from '@/infrastructure/config/runtimeConfig';
import { ApiClient } from '@/infrastructure/http/ApiClient';

export type InterviewDifficultyOption = 'easy' | 'standard' | 'hard';

export type InterviewPreparationResult = {
  scene: InterviewScene;
  material: InterviewMaterial;
  jobTitle: string | null;
};

export type InterviewPreparationService = Pick<InterviewService, 'prepareMaterials' | 'generateScene'>;

export type InterviewPreparationState = {
  resumeFile: ExpoFile | null;
  resumeFileName: string | null;
  isPreparing: boolean;
  error: string | null;
  result: InterviewPreparationResult | null;
  pickResume: () => Promise<void>;
  start: (input: {
    jobDescription: string;
    difficulty: InterviewDifficultyOption | null;
  }) => Promise<InterviewPreparationResult | null>;
  clearError: () => void;
};

const difficultyMap: Record<InterviewDifficultyOption, InterviewDifficulty> = {
  easy: 'EASY',
  standard: 'STANDARD',
  hard: 'HARD',
};

export function mapInterviewDifficulty(value: InterviewDifficultyOption): InterviewDifficulty {
  return difficultyMap[value];
}

function errorMessage(cause: unknown) {
  return cause instanceof Error ? cause.message : '面试准备失败，请重试';
}

function createInterviewService(): InterviewService {
  return new InterviewService(new ApiClient({
    baseUrl: getRuntimeConfig().backendUrl,
    tokenStore: new SecureTokenStore(),
  }));
}

export function useInterviewPreparation(
  injectedService?: InterviewPreparationService,
): InterviewPreparationState {
  const [resumeFile, setResumeFile] = useState<ExpoFile | null>(null);
  const [isPreparing, setIsPreparing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<InterviewPreparationResult | null>(null);
  const submitting = useRef(false);
  const [service] = useState<InterviewPreparationService>(
    () => injectedService ?? createInterviewService(),
  );

  const pickResume = useCallback(async () => {
    try {
      const picked = await ExpoFile.pickFileAsync({
        mimeTypes: ['application/pdf', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'],
      });
      if (picked.canceled || !picked.result) return;
      setResumeFile(picked.result);
      setError(null);
    } catch (cause) {
      setError(errorMessage(cause));
    }
  }, []);

  const start = useCallback(async ({ jobDescription, difficulty }: {
    jobDescription: string;
    difficulty: InterviewDifficultyOption | null;
  }) => {
    if (submitting.current) return null;
    if (!jobDescription.trim()) {
      setError('职位描述不能为空');
      return null;
    }
    if (!difficulty) {
      setError('请选择面试难度');
      return null;
    }

    submitting.current = true;
    setIsPreparing(true);
    setError(null);
    try {
      const draft = await service.prepareMaterials({
        jobDescriptionText: jobDescription,
        resumeFile: resumeFile ?? undefined,
      });
      const scene = await service.generateScene(draft.material, mapInterviewDifficulty(difficulty));
      const nextResult = { scene, material: draft.material, jobTitle: draft.material.jobTitle };
      setResult(nextResult);
      return nextResult;
    } catch (cause) {
      setError(errorMessage(cause));
      return null;
    } finally {
      submitting.current = false;
      setIsPreparing(false);
    }
  }, [resumeFile, service]);

  return {
    resumeFile,
    resumeFileName: resumeFile?.name ?? null,
    isPreparing,
    error,
    result,
    pickResume,
    start,
    clearError: () => setError(null),
  };
}
