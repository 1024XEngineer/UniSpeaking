import { createContext, type PropsWithChildren, useCallback, useContext, useEffect, useMemo, useState } from 'react';

import { AuthSessionController, type AuthSessionState } from '@/features/auth/AuthSessionController';
import { AuthService, type EmailChallenge, type UserPreference } from '@/features/auth/AuthService';
import {
  cefrLevelForLevel,
  levelForCefrLevel,
  speedCodeForLabel,
  speedLabelForCode,
  teacherForVoice,
  voiceForTeacher,
} from '@/features/auth/preferenceMappings';
import {
  initialIeltsLearningRecords,
  initialSceneLearningRecords,
  type IeltsLearningRecord,
  type InterviewLearningRecord,
  type SceneLearningRecord,
} from '@/data/learningAssets';
import { SecureTokenStore } from '@/infrastructure/auth/SecureTokenStore';
import { getRuntimeConfig } from '@/infrastructure/config/runtimeConfig';
import { ApiClient } from '@/infrastructure/http/ApiClient';
import { levels, teachers, type Teacher } from '@/theme/tokens';

export type AppModelAuthController = {
  getSnapshot(): AuthSessionState;
  subscribe(listener: (state: AuthSessionState) => void): () => void;
  bootstrap(): Promise<void>;
  login(input: { username: string; password: string }): Promise<void>;
  issueEmailChallenge(input: { email: string }): Promise<EmailChallenge>;
  register(input: {
    username: string;
    password: string;
    nickname: string | null;
    challengeId: string;
    code: string;
  }): Promise<void>;
  updatePreference(patch: Partial<UserPreference>): Promise<UserPreference>;
  logout(): Promise<void>;
  unauthorized(): Promise<void>;
};

type AppModelValue = {
  isModelReady: boolean;
  isAuthenticated: boolean;
  hasCompletedOnboarding: boolean;
  authStatus: AuthSessionState['status'];
  authError: string | null;
  signIn: (input: { username: string; password: string }) => Promise<void>;
  issueEmailChallenge: (input: { email: string }) => Promise<EmailChallenge>;
  signUp: (input: {
    username: string;
    password: string;
    nickname: string | null;
    challengeId: string;
    code: string;
  }) => Promise<void>;
  completeOnboarding: () => Promise<void>;
  signOut: () => Promise<void>;
  nickname: string;
  setNickname: (value: string) => void;
  email: string;
  speed: string;
  setSpeed: (value: string) => void;
  saveSpeed: (value: string) => Promise<void>;
  level: string;
  setLevel: (value: string) => void;
  saveLevel: (value: string) => Promise<void>;
  teacher: Teacher;
  setTeacher: (value: Teacher) => void;
  saveTeacher: (value: Teacher) => Promise<void>;
  sceneRecords: SceneLearningRecord[];
  ieltsRecords: IeltsLearningRecord[];
  interviewRecords: InterviewLearningRecord[];
  addSceneRecord: (record: SceneLearningRecord) => void;
  addIeltsRecord: (record: IeltsLearningRecord) => void;
  addInterviewRecord: (record: InterviewLearningRecord) => void;
  removeSceneRecord: (id: string) => void;
  membership: string;
  setMembership: (value: string) => void;
};

const AppModelContext = createContext<AppModelValue | null>(null);

function createDefaultAuthController(): AppModelAuthController {
  const tokenStore = new SecureTokenStore();
  let controller: AuthSessionController;
  const apiClient = new ApiClient({
    baseUrl: getRuntimeConfig().backendUrl,
    tokenStore,
    onUnauthorized: () => controller.unauthorized(),
  });
  controller = new AuthSessionController({
    tokenStore,
    authService: new AuthService(apiClient),
  });
  return controller;
}

export function AppModelProvider({
  children,
  authController: injectedAuthController,
}: PropsWithChildren<{ authController?: AppModelAuthController }>) {
  const [authController] = useState<AppModelAuthController>(
    () => injectedAuthController ?? createDefaultAuthController(),
  );
  const [authState, setAuthState] = useState<AuthSessionState>(() => authController.getSnapshot());
  const [nickname, setNickname] = useState('');
  const [speed, setSpeed] = useState('自然');
  const [level, setLevel] = useState('starter');
  const [teacher, setTeacher] = useState(teachers[0]);
  const [sceneRecords, setSceneRecords] = useState(initialSceneLearningRecords);
  const [ieltsRecords, setIeltsRecords] = useState(initialIeltsLearningRecords);
  const [interviewRecords, setInterviewRecords] = useState<InterviewLearningRecord[]>([]);
  const [membership, setMembership] = useState('免费版');

  useEffect(() => {
    const unsubscribe = authController.subscribe(setAuthState);
    // AuthSessionController is an external store; synchronize its current snapshot on mount.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setAuthState(authController.getSnapshot());
    void authController.bootstrap();
    return unsubscribe;
  }, [authController]);

  useEffect(() => {
    // Preferences arrive from the external auth session and hydrate the local controls.
    /* eslint-disable react-hooks/set-state-in-effect */
    if (authState.user?.nickname) setNickname(authState.user.nickname);
    if (!authState.preference) return;
    setLevel(levelForCefrLevel(authState.preference.cefrLevel, levels).id);
    setTeacher(teacherForVoice(authState.preference.preferredVoice, teachers));
    setSpeed(speedLabelForCode(authState.preference.preferredAiSpeechSpeed));
    /* eslint-enable react-hooks/set-state-in-effect */
  }, [authState.preference, authState.user]);

  const addSceneRecord = useCallback((record: SceneLearningRecord) => {
    setSceneRecords((current) => [record, ...current.filter((item) => item.id !== record.id)]);
  }, []);

  const addIeltsRecord = useCallback((record: IeltsLearningRecord) => {
    setIeltsRecords((current) => [record, ...current.filter((item) => item.id !== record.id)]);
  }, []);

  const addInterviewRecord = useCallback((record: InterviewLearningRecord) => {
    setInterviewRecords((current) => [record, ...current.filter((item) => item.id !== record.id)]);
  }, []);

  const removeSceneRecord = useCallback((id: string) => {
    setSceneRecords((current) => current.filter((item) => item.id !== id));
  }, []);

  const signIn = useCallback(
    (input: { username: string; password: string }) => authController.login(input),
    [authController],
  );

  const issueEmailChallenge = useCallback(
    (input: { email: string }) => authController.issueEmailChallenge(input),
    [authController],
  );

  const signUp = useCallback(
    (input: { username: string; password: string; nickname: string | null; challengeId: string; code: string }) =>
      authController.register(input),
    [authController],
  );

  const completeOnboarding = useCallback(async () => {
    const selectedLevel = levels.find((option) => option.id === level) ?? levels[0];
    await authController.updatePreference({
      cefrLevel: cefrLevelForLevel(selectedLevel),
      preferredVoice: voiceForTeacher(teacher),
    });
  }, [authController, level, teacher]);

  const saveLevel = useCallback(
    async (value: string) => {
      const selectedLevel = levels.find((option) => option.id === value) ?? levels[0];
      const preference = await authController.updatePreference({
        cefrLevel: cefrLevelForLevel(selectedLevel),
      });
      setLevel(levelForCefrLevel(preference.cefrLevel, levels).id);
    },
    [authController],
  );

  const saveSpeed = useCallback(
    async (value: string) => {
      const preference = await authController.updatePreference({
        preferredAiSpeechSpeed: speedCodeForLabel(value),
      });
      setSpeed(speedLabelForCode(preference.preferredAiSpeechSpeed));
    },
    [authController],
  );

  const saveTeacher = useCallback(
    async (value: Teacher) => {
      const preference = await authController.updatePreference({
        preferredVoice: voiceForTeacher(value),
      });
      setTeacher(teacherForVoice(preference.preferredVoice, teachers));
    },
    [authController],
  );

  const signOut = useCallback(() => authController.logout(), [authController]);

  const isModelReady = authState.status !== 'booting';
  const isAuthenticated = authState.status === 'authenticated';
  const hasCompletedOnboarding = Boolean(authState.preference?.cefrLevel && authState.preference?.preferredVoice);

  const value = useMemo(
    () => ({
      isModelReady,
      isAuthenticated,
      hasCompletedOnboarding,
      authStatus: authState.status,
      authError: authState.error,
      signIn,
      issueEmailChallenge,
      signUp,
      completeOnboarding,
      signOut,
      nickname,
      setNickname,
      email: authState.user?.username ?? '',
      speed,
      setSpeed,
      saveSpeed,
      level,
      setLevel,
      saveLevel,
      teacher,
      setTeacher,
      saveTeacher,
      sceneRecords,
      ieltsRecords,
      interviewRecords,
      addSceneRecord,
      addIeltsRecord,
      addInterviewRecord,
      removeSceneRecord,
      membership,
      setMembership,
    }),
    [
      addIeltsRecord,
      addInterviewRecord,
      addSceneRecord,
      completeOnboarding,
      hasCompletedOnboarding,
      isModelReady,
      isAuthenticated,
      issueEmailChallenge,
      authState.error,
      authState.status,
      authState.user,
      level,
      membership,
      nickname,
      ieltsRecords,
      interviewRecords,
      removeSceneRecord,
      saveSpeed,
      saveTeacher,
      saveLevel,
      sceneRecords,
      signIn,
      signOut,
      signUp,
      speed,
      teacher,
    ],
  );

  return <AppModelContext.Provider value={value}>{children}</AppModelContext.Provider>;
}

export function useAppModel() {
  const context = useContext(AppModelContext);
  if (!context) throw new Error('useAppModel must be used inside AppModelProvider');
  return context;
}
