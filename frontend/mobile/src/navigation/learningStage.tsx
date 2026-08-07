import { createContext, type PropsWithChildren, useContext } from 'react';

type LearningStageContextValue = {
  immersiveLearning: boolean;
  setImmersiveLearning: (immersive: boolean) => void;
};

const LearningStageContext = createContext<LearningStageContextValue>({
  immersiveLearning: false,
  setImmersiveLearning: () => undefined,
});

export function LearningStageProvider({
  children,
  immersiveLearning,
  setImmersiveLearning,
}: PropsWithChildren<LearningStageContextValue>) {
  return (
    <LearningStageContext.Provider value={{ immersiveLearning, setImmersiveLearning }}>
      {children}
    </LearningStageContext.Provider>
  );
}

export function useLearningStage() {
  return useContext(LearningStageContext);
}
