import { useCallback, useEffect, useMemo, useState } from 'react';

import type { IeltsLearningRecord } from '@/data/learningAssets';

import { createIeltsService } from './createIeltsService';
import {
  examinerById,
  formatBand,
  parseTargetScore,
  practiceTypeLabel,
  toApiPart,
  type IeltsExaminer,
  type MobileIeltsPartId,
} from './ieltsMappings';
import { mapEvaluationToRecord } from './ieltsRecordMapper';
import type {
  IeltsCategory,
  IeltsEvaluationResult,
  IeltsGeneration,
  IeltsSettings,
  IeltsTopicSummary,
  IeltsTraining,
} from './types';

export function useIeltsFlowController() {
  const service = useMemo(() => createIeltsService(), []);
  const [settings, setSettings] = useState<IeltsSettings | null>(null);
  const [settingsLoading, setSettingsLoading] = useState(true);
  const [settingsError, setSettingsError] = useState<string | null>(null);
  const [categories, setCategories] = useState<IeltsCategory[]>([]);
  const [topics, setTopics] = useState<IeltsTopicSummary[]>([]);
  const [topicsLoading, setTopicsLoading] = useState(false);
  const [topicsError, setTopicsError] = useState<string | null>(null);
  const [topicTotal, setTopicTotal] = useState(0);
  const [topicTotalPages, setTopicTotalPages] = useState(0);
  const [generated, setGenerated] = useState<IeltsGeneration | null>(null);
  const [training, setTraining] = useState<IeltsTraining | null>(null);
  const [latestEvaluation, setLatestEvaluation] = useState<IeltsEvaluationResult | null>(null);
  const [historyRecords, setHistoryRecords] = useState<IeltsLearningRecord[]>([]);
  const [sessionBusy, setSessionBusy] = useState(false);
  const [sessionError, setSessionError] = useState<string | null>(null);

  const refreshSettings = useCallback(async () => {
    setSettingsLoading(true);
    setSettingsError(null);
    try {
      const next = await service.getSettings();
      setSettings(next);
      return next;
    } catch (error) {
      setSettingsError(error instanceof Error ? error.message : 'IELTS 设置加载失败');
      return null;
    } finally {
      setSettingsLoading(false);
    }
  }, [service]);

  const saveTargetScore = useCallback(async (targetId: string) => {
    const updated = await service.updateSettings({ targetScore: parseTargetScore(targetId) });
    setSettings(updated);
    return updated;
  }, [service]);

  const loadTopics = useCallback(async (
    part: MobileIeltsPartId,
    categoryCode: string,
    keyword: string,
    page: number,
  ) => {
    setTopicsLoading(true);
    setTopicsError(null);
    try {
      const result = await service.searchTopics({
        part: toApiPart(part),
        category: !categoryCode || categoryCode === 'ALL' ? null : categoryCode,
        keyword,
        page,
        pageSize: 5,
      });
      setCategories(result.categories ?? []);
      setTopics(result.topics ?? []);
      setTopicTotal(result.total ?? 0);
      setTopicTotalPages(result.totalPages ?? 0);
    } catch (error) {
      setTopics([]);
      setTopicTotal(0);
      setTopicTotalPages(0);
      setTopicsError(error instanceof Error ? error.message : '雅思题库加载失败');
    } finally {
      setTopicsLoading(false);
    }
  }, [service]);

  const prepareSession = useCallback(async (input: {
    part: MobileIeltsPartId | 'mock';
    topicId?: string | null;
    random: boolean;
    examiner: IeltsExaminer;
  }) => {
    setSessionBusy(true);
    setSessionError(null);
    setLatestEvaluation(null);
    try {
      await service.updateSettings({ examinerId: input.examiner.id });
      const scene = await service.generateScene({
        mode: input.part === 'mock' ? 'MOCK_TEST' : 'PART_PRACTICE',
        part: input.part === 'mock' ? null : toApiPart(input.part),
        topicId: input.random ? null : input.topicId ?? null,
      });
      await service.createFlow(scene.ieltsId);
      setGenerated(scene);
      if (input.part === 'p2' && (input.topicId || scene.selectedTopicId)) {
        const nextTraining = await service.getTraining(
          'PART_2',
          input.topicId ?? scene.selectedTopicId,
        );
        setTraining(nextTraining);
      } else {
        setTraining(null);
      }
      return scene;
    } catch (error) {
      const message = error instanceof Error ? error.message : 'IELTS 场景准备失败';
      setSessionError(message);
      throw error;
    } finally {
      setSessionBusy(false);
    }
  }, [service]);

  const finalizeEvaluation = useCallback(async (ieltsId: string, sessionId: string) => {
    const result = await service.generateEvaluation(ieltsId, sessionId);
    setLatestEvaluation(result);
    return result;
  }, [service]);

  const refreshHistory = useCallback(async () => {
    try {
      const items = await service.getEvaluationHistory();
      setHistoryRecords(items.map((item) => mapEvaluationToRecord(item)));
    } catch {
      setHistoryRecords([]);
    }
  }, [service]);

  useEffect(() => {
    const timer = setTimeout(() => void refreshSettings(), 0);
    return () => clearTimeout(timer);
  }, [refreshSettings]);

  return useMemo(
    () => ({
      settings,
      settingsLoading,
      settingsError,
      categories,
      topics,
      topicsLoading,
      topicsError,
      topicTotal,
      topicTotalPages,
      generated,
      training,
      latestEvaluation,
      historyRecords,
      sessionBusy,
      sessionError,
      refreshSettings,
      saveTargetScore,
      loadTopics,
      prepareSession,
      finalizeEvaluation,
      refreshHistory,
      formatBand,
      practiceTypeLabel,
      examinerById,
    }),
    [
      settings,
      settingsLoading,
      settingsError,
      categories,
      topics,
      topicsLoading,
      topicsError,
      topicTotal,
      topicTotalPages,
      generated,
      training,
      latestEvaluation,
      historyRecords,
      sessionBusy,
      sessionError,
      refreshSettings,
      saveTargetScore,
      loadTopics,
      prepareSession,
      finalizeEvaluation,
      refreshHistory,
    ],
  );
}
