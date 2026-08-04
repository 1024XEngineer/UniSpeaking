import AsyncStorage from '@react-native-async-storage/async-storage';
import {
  createContext,
  type PropsWithChildren,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from 'react';

import {
  initialIeltsLearningRecords,
  initialInterviewLearningRecords,
  initialSceneLearningRecords,
  type IeltsLearningRecord,
  type InterviewLearningRecord,
  type SceneLearningRecord,
} from '@/data/learningAssets';
import { teachers, type Teacher } from '@/theme/tokens';

type AppModelValue = {
  isModelReady: boolean;
  isAuthenticated: boolean;
  hasCompletedOnboarding: boolean;
  signIn: () => void;
  signUp: () => void;
  completeOnboarding: () => void;
  signOut: () => void;
  nickname: string;
  setNickname: (value: string) => void;
  speed: string;
  setSpeed: (value: string) => void;
  level: string;
  setLevel: (value: string) => void;
  teacher: Teacher;
  setTeacher: (value: Teacher) => void;
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
const onboardingStorageKey = 'unispeaking.onboarding.v1';

export function AppModelProvider({ children }: PropsWithChildren) {
  const [isModelReady, setIsModelReady] = useState(false);
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [hasCompletedOnboarding, setHasCompletedOnboarding] = useState(false);
  const [nickname, setNickname] = useState('Yufan');
  const [speed, setSpeed] = useState('自然');
  const [level, setLevel] = useState('starter');
  const [teacher, setTeacher] = useState(teachers[0]);
  const [sceneRecords, setSceneRecords] = useState(initialSceneLearningRecords);
  const [ieltsRecords, setIeltsRecords] = useState(initialIeltsLearningRecords);
  const [interviewRecords, setInterviewRecords] = useState(initialInterviewLearningRecords);
  const [membership, setMembership] = useState('免费版');

  useEffect(() => {
    let active = true;
    const hydrateOnboarding = async () => {
      try {
        const saved = await AsyncStorage.getItem(onboardingStorageKey);
        if (!saved || !active) return;
        const preference = JSON.parse(saved) as { completed?: boolean; level?: string; teacherId?: string };
        if (preference.completed) setHasCompletedOnboarding(true);
        if (preference.level) setLevel(preference.level);
        const savedTeacher = teachers.find((item) => item.id === preference.teacherId);
        if (savedTeacher) setTeacher(savedTeacher);
      } catch {
        // A damaged local preference should never block sign-in.
      } finally {
        if (active) setIsModelReady(true);
      }
    };
    void hydrateOnboarding();
    return () => {
      active = false;
    };
  }, []);

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

  const signIn = useCallback(() => {
    setIsAuthenticated(true);
  }, []);

  const signUp = useCallback(() => {
    setIsAuthenticated(true);
    setHasCompletedOnboarding(false);
  }, []);

  const completeOnboarding = useCallback(() => {
    setHasCompletedOnboarding(true);
    void AsyncStorage.setItem(
      onboardingStorageKey,
      JSON.stringify({ completed: true, level, teacherId: teacher.id }),
    );
  }, [level, teacher.id]);

  const signOut = useCallback(() => {
    setIsAuthenticated(false);
  }, []);

  const value = useMemo(
    () => ({
      isModelReady,
      isAuthenticated,
      hasCompletedOnboarding,
      signIn,
      signUp,
      completeOnboarding,
      signOut,
      nickname,
      setNickname,
      speed,
      setSpeed,
      level,
      setLevel,
      teacher,
      setTeacher,
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
      level,
      membership,
      nickname,
      ieltsRecords,
      interviewRecords,
      removeSceneRecord,
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
