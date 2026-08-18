import { act, fireEvent, render, waitFor } from '@testing-library/react-native';

import type { GeneratedScene, SceneFlowStage } from '@/features/scenes/SceneService';
import { SceneTrainingController } from '@/features/scenes/SceneTrainingController';
import type {
  RealtimeSessionSnapshot,
  RealtimeSessionOptions,
} from '@/features/realtime/RealtimeSessionController';
import type { FreeChatControllerPort } from '@/features/conversation/useFreeChatSession';

const mockTranslateScene = jest.fn();

jest.mock('@/features/conversation/TranscriptTranslationApi', () => ({
  createTranscriptTranslationApi: () => ({ translateScene: mockTranslateScene }),
}));

jest.mock('react-native-reanimated', () => {
  const { View } = require('react-native');
  return {
    __esModule: true,
    default: { View },
    cancelAnimation: jest.fn(),
    Easing: {
      cubic: jest.fn(),
      ease: jest.fn(),
      linear: jest.fn(),
      inOut: (value: unknown) => value,
      out: (value: unknown) => value,
    },
    interpolate: (_value: number, _input: number[], output: number[]) => output[0],
    runOnJS: (fn: (...args: unknown[]) => unknown) => fn,
    useAnimatedStyle: (factory: () => unknown) => factory(),
    useSharedValue: (value: unknown) => ({ value }),
    withDelay: (_delay: number, value: unknown) => value,
    withRepeat: (value: unknown) => value,
    withTiming: (value: unknown) => value,
  };
});

jest.mock('@/model/AppModel', () => ({
  useAppModel: () => ({
    addSceneRecord: jest.fn(),
    teacher: {
      id: 'james',
      name: 'James',
      accent: 'American English',
      voiceId: 'Harvey',
      image: 1,
    },
  }),
}));

const scene: GeneratedScene = {
  sceneId: 'generated-scene-1',
  title: '机场行李托运',
  label: '出行',
  background: '在机场柜台办理行李托运。',
  aiRole: '航空公司工作人员',
  userRole: '乘客',
  learningGoal: '确认行李重量和登机信息。',
  estimatedMinutes: 8,
  wordList: [
    {
      contentId: 'word-1',
      englishText: 'baggage',
      chineseText: '行李',
      phonetic: '/ˈbæɡɪdʒ/',
    },
  ],
  phraseList: [
    {
      contentId: 'phrase-1',
      englishText: 'check in',
      chineseText: '办理托运',
      phonetic: null,
    },
  ],
  sentenceList: [
    {
      contentId: 'sentence-1',
      englishText: 'I would like to check in this bag.',
      chineseText: '我想托运这个行李。',
      phonetic: null,
    },
    {
      contentId: 'sentence-2',
      englishText: 'Is this bag within the weight limit?',
      chineseText: '这个行李在限重内吗？',
      phonetic: null,
    },
  ],
  scenePrompt: 'Prompt',
};

import {
  sceneMetricsForReport,
  SceneCallStage,
  ScenesHome,
  ScenesScreen,
  Training,
} from '../ScenesScreen';

describe('scene completion report mapping', () => {
  it('uses the five dimensions returned by the Java backend', () => {
    expect(
      sceneMetricsForReport({
        accuracyScore: 91,
        fluencyScore: 82,
        grammarScore: 73,
        vocabularyScore: 64,
        naturalnessScore: 55,
        finalScore: 77,
        summary: 'Done',
        strengths: [],
        improvements: [],
      }),
    ).toEqual([
      { label: '准确', value: 91 },
      { label: '流利', value: 82 },
      { label: '语法', value: 73 },
      { label: '词汇', value: 64 },
      { label: '自然', value: 55 },
    ]);
  });
});

describe('ScenesHome backend generation binding', () => {
  it('opens IELTS and interview through the parent tab routes', async () => {
    const onOpenIelts = jest.fn();
    const onOpenInterview = jest.fn();
    const screen = await render(
      <ScenesScreen
        onOpenIelts={onOpenIelts}
        onOpenInterview={onOpenInterview}
      />,
    );

    expect(screen.getByText('IELTS SPEAKING')).toBeTruthy();
    expect(screen.getByText('ENGLISH INTERVIEW')).toBeTruthy();
    expect(screen.getByText('雅思口语').props.numberOfLines).toBe(1);
    expect(screen.getByText('雅思口语').props.adjustsFontSizeToFit).toBe(true);
    expect(screen.getByText('英文面试').props.numberOfLines).toBe(1);
    expect(screen.getByText('英文面试').props.adjustsFontSizeToFit).toBe(true);
    expect(screen.queryByText('模考与评分')).toBeNull();
    expect(screen.queryByText('岗位模拟追问')).toBeNull();

    await fireEvent.press(screen.getByLabelText('进入雅思口语'));
    expect(onOpenIelts).toHaveBeenCalledTimes(1);
    await fireEvent.press(screen.getByLabelText('进入英文面试'));
    expect(onOpenInterview).toHaveBeenCalledTimes(1);
  });

  it('shows the generated backend preview and opens that exact scene', async () => {
    const onOpen = jest.fn();
    const onStartScene = jest.fn();
    const sceneService = {
      generate: jest.fn(async () => scene),
    };
    const screen = await render(
      <ScenesHome
        onOpen={onOpen}
        onStartScene={onStartScene}
        promptExample={{ id: 'airport', prompt: '机场托运行李' }}
        sceneService={sceneService}
      />,
    );

    await fireEvent.changeText(
      screen.getByLabelText('描述想练习的场景'),
      '  我想练习机场托运行李  ',
    );
    await fireEvent.press(screen.getByLabelText('生成练习场景'));

    await waitFor(() => expect(screen.getByText('机场行李托运')).toBeTruthy());
    expect(sceneService.generate).toHaveBeenCalledWith('我想练习机场托运行李');
    expect(screen.getByText('AI 扮演')).toBeTruthy();
    expect(screen.getByText('航空公司工作人员')).toBeTruthy();
    expect(screen.getByText('你将扮演')).toBeTruthy();
    expect(screen.getByText('乘客')).toBeTruthy();
    await fireEvent.press(screen.getByText('开始练习'));
    expect(onStartScene).toHaveBeenCalledTimes(1);
    expect(onOpen).toHaveBeenCalledWith({ name: 'training', scene });
  });

  it('does not show untranslated English in the preview', async () => {
    const resolveTranslations: Array<(value: string) => void> = [];
    mockTranslateScene.mockImplementation(
      () => new Promise<string>((resolve) => resolveTranslations.push(resolve)),
    );
    const englishScene = {
      ...scene,
      title: 'Coffee Shop Order',
      background: 'Order a coffee and customize the drink.',
      aiRole: 'Barista',
      userRole: 'Customer',
      learningGoal: 'Practice ordering and confirming details.',
    };
    const screen = await render(
      <ScenesHome
        onOpen={jest.fn()}
        sceneService={{ generate: jest.fn(async () => englishScene) }}
      />,
    );

    await fireEvent.changeText(
      screen.getByLabelText('描述想练习的场景'),
      '咖啡店点单',
    );
    await fireEvent.press(screen.getByLabelText('生成练习场景'));

    await waitFor(() => expect(screen.getByText('正在整理场景…')).toBeTruthy());
    expect(screen.queryByText('Coffee Shop Order')).toBeNull();
    expect(screen.queryByText('Barista')).toBeNull();
    expect(screen.getAllByText('正在整理中文摘要…')).toHaveLength(2);
    expect(screen.getAllByText('正在整理…')).toHaveLength(2);

    await act(async () => {
      resolveTranslations.forEach((resolve) => resolve('咖啡店点单'));
    });
    await waitFor(() => expect(screen.getAllByText('咖啡店点单').length).toBeGreaterThan(0));
    expect(mockTranslateScene).toHaveBeenCalled();
  });

  it('shows a generation error without opening a fake preview', async () => {
    const sceneService = {
      generate: jest.fn(async () => {
        throw new Error('模型生成超时');
      }),
    };
    const screen = await render(
      <ScenesHome
        onOpen={jest.fn()}
        promptExample={{ id: 'airport', prompt: '机场托运行李' }}
        sceneService={sceneService}
      />,
    );
    await fireEvent.changeText(
      screen.getByLabelText('描述想练习的场景'),
      '机场托运行李',
    );
    await fireEvent.press(screen.getByLabelText('生成练习场景'));

    await waitFor(() => expect(screen.getByText('模型生成超时')).toBeTruthy());
    expect(screen.queryByText('确认进入')).toBeNull();
  });

  it('shows recommendation generation feedback on the selected lower arrow', async () => {
    let resolveScene: (value: GeneratedScene) => void = () => undefined;
    const sceneService = {
      generate: jest.fn(() => new Promise<GeneratedScene>((resolve) => {
        resolveScene = resolve;
      })),
    };
    const screen = await render(
      <ScenesHome onOpen={jest.fn()} sceneService={sceneService} />,
    );

    await fireEvent.press(screen.getByLabelText('生成每日推荐：咖啡店点单'));
    expect(screen.getByLabelText('正在生成推荐场景')).toBeTruthy();
    expect(screen.getByText('生成练习场景')).toBeTruthy();

    await act(async () => resolveScene(scene));
    await waitFor(() => expect(screen.getByText('机场行李托运')).toBeTruthy());
  });
});

describe('Training backend content binding', () => {
  it('renders and advances through generated words, phrases and sentences', async () => {
    const service = {
      createFlow: jest.fn(async () => ({
        sceneId: scene.sceneId,
        stage: 'WORD_LEARNING' as const,
        completed: false,
      })),
      getContent: jest.fn(async (_sceneId: string, stage?: string) => {
        if (stage === 'WORD_LEARNING') return scene.wordList;
        if (stage === 'PHRASE_LEARNING') return scene.phraseList;
        return scene.sentenceList;
      }),
      advanceStage: jest.fn(async (_sceneId: string, stage: SceneFlowStage) => ({
        sceneId: scene.sceneId,
        stage: (
          stage === 'WORD_LEARNING'
            ? 'PHRASE_LEARNING'
            : stage === 'PHRASE_LEARNING'
              ? 'SENTENCE_LEARNING'
              : 'DIALOGUE') as SceneFlowStage,
        completed: false,
      })),
      evaluateSentence: jest.fn(async () => ({
        overallScore: 86,
        passed: true,
        words: [],
      })),
    };
    const controller = new SceneTrainingController(service);
    const wavRecorder = {
      start: jest.fn(async () => undefined),
      stop: jest.fn(async () => 'file:///sentence.wav'),
      cancel: jest.fn(async () => undefined),
    };
    const ttsPlayer = {
      play: jest.fn(async () => undefined),
      stop: jest.fn(),
    };
    const screen = await render(
      <Training
        scene={scene}
        trainingController={controller}
        wavRecorder={wavRecorder}
        ttsPlayer={ttsPlayer}
        onBack={jest.fn()}
        onFinish={jest.fn()}
      />,
    );

    await waitFor(() => expect(screen.getByText('baggage')).toBeTruthy());
    await fireEvent.press(screen.getByLabelText('播放发音'));
    await waitFor(() =>
      expect(ttsPlayer.play).toHaveBeenCalledWith(scene.sceneId, 'baggage'),
    );
    await fireEvent.press(screen.getByText('进入词组'));
    await waitFor(() => expect(screen.getByText('check in')).toBeTruthy());
    await fireEvent.press(screen.getByText('进入朗读'));
    await waitFor(() =>
      expect(screen.getByText('I would like to check in this bag.')).toBeTruthy(),
    );
    await fireEvent.press(screen.getByLabelText('开始朗读'));
    await waitFor(() => expect(wavRecorder.start).toHaveBeenCalledTimes(1));
    await fireEvent.press(screen.getByLabelText('结束朗读'));
    await waitFor(() => expect(screen.getByText('朗读通过')).toBeTruthy());
    expect(service.evaluateSentence).toHaveBeenCalledWith(
      scene.sceneId,
      'sentence-1',
      'file:///sentence.wav',
    );
    await fireEvent.press(screen.getByText('知道了'));
    expect(screen.getByText('下一句')).toBeTruthy();
    await fireEvent.press(screen.getByText('下一句'));
    await waitFor(() =>
      expect(screen.getByText('Is this bag within the weight limit?')).toBeTruthy(),
    );
    expect(screen.getByText('进入模拟')).toBeTruthy();
    expect(screen.getByText('点击麦克风开始朗读')).toBeTruthy();

    jest.useFakeTimers();
    await fireEvent.press(screen.getByLabelText('开始朗读'));
    await act(async () => {
      jest.advanceTimersByTime(30_000);
      await Promise.resolve();
    });
    expect(wavRecorder.stop).toHaveBeenCalledTimes(2);
    jest.useRealTimers();
  });
});

describe('SceneCallStage realtime binding', () => {
  it('starts scene WebRTC, renders live transcript and returns backend completion', async () => {
    let snapshot: RealtimeSessionSnapshot = {
      state: 'idle',
      muted: false,
      sessionId: null,
      userTranscript: '',
      assistantTranscript: '',
      transcriptHistory: [],
      error: null,
    };
    let listener: ((value: RealtimeSessionSnapshot) => void) | null = null;
    const completion = {
      sceneId: scene.sceneId,
      sessionId: 'session-1',
      stopTime: '2026-08-05T10:00:00Z',
      evaluation: {
        accuracyScore: 88,
        fluencyScore: 87,
        grammarScore: 86,
        vocabularyScore: 85,
        naturalnessScore: 89,
        finalScore: 87,
        summary: 'Good work.',
        strengths: ['Clear intent'],
        improvements: ['Use more detail'],
      },
    };
    const controller: FreeChatControllerPort = {
      getSnapshot: () => snapshot,
      subscribe: jest.fn((nextListener) => {
        listener = nextListener;
        listener(snapshot);
        return () => {
          listener = null;
        };
      }),
      start: jest.fn(async () => {
        snapshot = {
          ...snapshot,
          state: 'ready',
          sessionId: 'session-1',
          assistantTranscript: 'May I see your passport?',
        };
        listener?.(snapshot);
      }),
      setMuted: jest.fn(),
      interrupt: jest.fn(),
      end: jest.fn(async () => {
        snapshot = { ...snapshot, state: 'ended', completion };
        listener?.(snapshot);
        return completion;
      }),
    };
    const createController = jest.fn(
      (_sceneId: string, _config: Pick<RealtimeSessionOptions, 'voice' | 'model' | 'speechSpeed'>) => controller,
    );
    const onComplete = jest.fn();
    const screen = await render(
      <SceneCallStage
        scene={scene}
        progressCollapsed={false}
        createController={createController}
        onComplete={onComplete}
      />,
    );

    await waitFor(() =>
      expect(screen.getByText('May I see your passport?')).toBeTruthy(),
    );
    expect(createController).toHaveBeenCalledWith(
      scene.sceneId,
      expect.objectContaining({ voice: 'Harvey' }),
    );
    await fireEvent.press(screen.getByLabelText('结束当前会话'));
    await waitFor(() => expect(onComplete).toHaveBeenCalledWith(completion));
  });
});
