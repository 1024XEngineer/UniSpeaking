import { act, fireEvent, render, waitFor } from '@testing-library/react-native';
import { Alert, BackHandler, Platform } from 'react-native';

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

jest.mock('@siteed/audio-studio', () => ({
  AudioStudioModule: {
    requestPermissionsAsync: jest.fn(async () => ({ granted: true })),
    startRecording: jest.fn(async () => undefined),
    stopRecording: jest.fn(async () => null),
  },
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
  StageProgressRail,
  ScenesHome,
  ScenesScreen,
  Training,
} from '../ScenesScreen';

describe('scene completion report mapping', () => {
  it('lays out, collapses, expands, and selects the available training rail stages', async () => {
    const onSelect = jest.fn();
    const onCollapsedChange = jest.fn();
    const screen = await render(
      <StageProgressRail stage="read" unlockedStage={2} initialCollapsed onSelect={onSelect} onCollapsedChange={onCollapsedChange} />,
    );
    const rail = screen.getAllByLabelText(/训练进度/).find((node) => typeof node.props.onResponderRelease === 'function')!;
    fireEvent(rail, 'layout', { nativeEvent: { layout: { width: 320 } } });
    await waitFor(() => expect(screen.getByLabelText('展开训练进度')).toBeTruthy());
    await fireEvent.press(screen.getByLabelText('展开训练进度'));
    const tabs = screen.getAllByRole('tab');
    await fireEvent.press(tabs[0]);
    await fireEvent.press(tabs[2]);
    expect(onSelect).toHaveBeenCalledWith('learn');
    expect(onSelect).toHaveBeenCalledWith('speak');
    expect(onCollapsedChange).toHaveBeenCalledWith(true);
  });

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

  it('does not present an all-zero unavailable report as a scored result', () => {
    expect(
      sceneMetricsForReport({
        accuracyScore: 0,
        fluencyScore: 0,
        grammarScore: 0,
        vocabularyScore: 0,
        naturalnessScore: 0,
        finalScore: 0,
        summary: '本次对话已保存，但有效英文语音不足，暂时无法生成完整五维评分。',
        strengths: [],
        improvements: ['请使用完整英文句子完成至少一轮回答后再试。'],
      }),
    ).toEqual([]);
  });
});

describe('ScenesHome backend generation binding', () => {
  beforeEach(() => {
    mockTranslateScene.mockReset();
  });

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

  it('uses internal specialty routes and Android back navigation when parent routes are absent', async () => {
    const originalOs = Platform.OS;
    Object.defineProperty(Platform, 'OS', { configurable: true, value: 'android' });
    const handlers: Array<(event: any) => boolean | null | undefined> = [];
    const backSpy = jest.spyOn(BackHandler, 'addEventListener').mockImplementation((_, handler) => {
      handlers.push(handler);
      return { remove: jest.fn() } as any;
    });
    const screen = await render(<ScenesScreen />);
    await fireEvent.press(screen.getByLabelText('进入雅思口语'));
    await waitFor(() => expect(screen.getByText('下一步')).toBeTruthy());
    expect(handlers.at(-1)?.({})).toBe(true);
    await waitFor(() => expect(screen.getByText('IELTS SPEAKING')).toBeTruthy());
    await fireEvent.press(screen.getByLabelText('进入英文面试'));
    await waitFor(() => expect(screen.getByText('填写岗位 JD')).toBeTruthy());
    expect(handlers.at(-1)?.({})).toBe(true);
    await waitFor(() => expect(screen.getByText('IELTS SPEAKING')).toBeTruthy());
    screen.unmount();
    backSpy.mockRestore();
    Object.defineProperty(Platform, 'OS', { configurable: true, value: originalOs });
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
    expect(screen.getByLabelText('描述想练习的场景').props.editable).toBe(false);
    expect(screen.getByText('AI 扮演')).toBeTruthy();
    expect(screen.getByText('航空公司工作人员')).toBeTruthy();
    expect(screen.getByText('你将扮演')).toBeTruthy();
    expect(screen.getByText('乘客')).toBeTruthy();
    await fireEvent.press(screen.getByText('开始练习'));
    expect(onStartScene).toHaveBeenCalledTimes(1);
    expect(onOpen).toHaveBeenCalledWith({ name: 'training', scene });
  });

  it('closes a generated preview through the Android hardware back button', async () => {
    const originalOs = Platform.OS;
    Object.defineProperty(Platform, 'OS', { configurable: true, value: 'android' });
    let handler: ((event: any) => boolean | null | undefined) | undefined;
    const remove = jest.fn();
    const backSpy = jest.spyOn(BackHandler, 'addEventListener').mockImplementation((_event, callback) => {
      handler = callback;
      return { remove } as any;
    });
    const screen = await render(<ScenesHome onOpen={jest.fn()} sceneService={{ generate: jest.fn(async () => scene) }} />);
    await fireEvent.changeText(screen.getByLabelText('描述想练习的场景'), '机场托运');
    await fireEvent.press(screen.getByLabelText('生成练习场景'));
    await waitFor(() => expect(screen.getByText('机场行李托运')).toBeTruthy());
    await act(async () => { expect(handler?.({})).toBe(true); });
    await waitFor(() => expect(screen.getByLabelText('生成练习场景')).toBeTruthy());
    expect(remove).toHaveBeenCalled();
    screen.unmount();
    backSpy.mockRestore();
    Object.defineProperty(Platform, 'OS', { configurable: true, value: originalOs });
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
    expect(screen.getByLabelText('描述想练习的场景').props.editable).toBe(true);
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

  it('loads backend daily picks and tolerates an invalid refresh date', async () => {
    const dailyPicksApi = {
      getDailyPicks: jest.fn(async () => ({
        date: '2026-08-24',
        timezone: 'Asia/Shanghai',
        picks: [{
          id: 'doctor',
          title: '预约医生',
          category: 'health' as const,
          goal: '描述症状',
          duration: '6 分钟',
          level: 'B1',
          position: 1,
          sceneInput: '预约医生并描述症状',
        }],
        nextRefreshAt: 'not-a-date',
      })),
    };
    const screen = await render(
      <ScenesHome
        onOpen={jest.fn()}
        dailyPicksApi={dailyPicksApi}
        sceneService={{ generate: jest.fn(async () => scene) }}
      />,
    );

    await waitFor(() => expect(screen.getByText('预约医生')).toBeTruthy());
    expect(screen.getByLabelText('描述想练习的场景')).toBeTruthy();
  });

  it('refreshes daily picks at a valid backend refresh time and clears the timer', async () => {
    jest.useFakeTimers();
    const dailyPicksApi = { getDailyPicks: jest.fn(async () => ({
      date: '2026-08-24', timezone: 'Asia/Shanghai', picks: [],
      nextRefreshAt: new Date(Date.now() + 2_000).toISOString(),
    })) };
    const screen = await render(<ScenesHome onOpen={jest.fn()} dailyPicksApi={dailyPicksApi} sceneService={{ generate: jest.fn() }} />);
    await waitFor(() => expect(dailyPicksApi.getDailyPicks).toHaveBeenCalledTimes(1));
    await act(async () => { await jest.advanceTimersByTimeAsync(3_100); });
    await waitFor(() => expect(dailyPicksApi.getDailyPicks).toHaveBeenCalledTimes(2));
    screen.unmount();
    jest.useRealTimers();
  });

  it('falls back to bounded source text when preview translation fails and supports both close actions', async () => {
    mockTranslateScene.mockRejectedValue(new Error('translate failed'));
    const englishScene = {
      ...scene,
      title: 'A very long coffee shop ordering scenario title',
      background: 'Order a coffee and customize every detail of the drink with the barista.',
      aiRole: 'Professional coffee shop barista',
      userRole: 'Customer ordering a customized drink',
      learningGoal: 'Practice ordering, clarifying sizes, and confirming every detail.',
    };
    const sceneService = { generate: jest.fn(async () => englishScene) };
    const screen = await render(<ScenesHome onOpen={jest.fn()} sceneService={sceneService} />);
    const generate = async () => {
      await fireEvent.changeText(screen.getByLabelText('描述想练习的场景'), '咖啡店点单');
      await fireEvent.press(screen.getByLabelText('生成练习场景'));
      await waitFor(() => expect(screen.getByText(/^A very long coffee/)).toBeTruthy());
    };

    await generate();
    await fireEvent.press(screen.getByText('返回修改'));
    await waitFor(() => expect(screen.queryByText(/^A very long coffee/)).toBeNull());
    await generate();
    await fireEvent.press(screen.getByLabelText('关闭场景确认'));
    await waitFor(() => expect(screen.queryByText(/^A very long coffee/)).toBeNull());
  });

  it('shows the generic generation message for non-Error failures', async () => {
    const screen = await render(
      <ScenesHome
        onOpen={jest.fn()}
        sceneService={{ generate: jest.fn(async () => { throw 'failed'; }) }}
      />,
    );
    await fireEvent.changeText(screen.getByLabelText('描述想练习的场景'), '机场');
    await fireEvent.press(screen.getByLabelText('生成练习场景'));
    await waitFor(() => expect(screen.getByText('场景生成失败，请重试')).toBeTruthy());
  });
});

describe('Training backend content binding', () => {
  it('constructs and releases the default scene audio adapters', async () => {
    const service = {
      createFlow: jest.fn(async () => ({ sceneId: scene.sceneId, stage: 'WORD_LEARNING' as const, completed: false })),
      getContent: jest.fn(async () => scene.wordList),
      advanceStage: jest.fn(), evaluateSentence: jest.fn(),
    };
    const screen = await render(
      <Training scene={scene} trainingController={new SceneTrainingController(service)} onBack={jest.fn()} onFinish={jest.fn()} />,
    );
    await waitFor(() => expect(screen.getByText('baggage')).toBeTruthy());
    screen.unmount();
  });

  it('fully releases reading audio before entering the realtime speak stage', async () => {
    const lifecycle: string[] = [];
    const service = {
      createFlow: jest.fn(async () => ({
        sceneId: scene.sceneId,
        stage: 'SENTENCE_LEARNING' as const,
        completed: false,
      })),
      getContent: jest.fn(async () => scene.sentenceList.slice(0, 1)),
      advanceStage: jest.fn(async (_sceneId: string, stage: SceneFlowStage) => {
        if (stage === 'SENTENCE_LEARNING') {
          lifecycle.push('advance-stage');
          return new Promise<never>(() => undefined);
        }
        return {
          sceneId: scene.sceneId,
          stage: (
            stage === 'WORD_LEARNING'
              ? 'PHRASE_LEARNING'
              : stage === 'PHRASE_LEARNING'
                ? 'SENTENCE_LEARNING'
                : 'DIALOGUE'
          ) as SceneFlowStage,
          completed: false,
        };
      }),
      evaluateSentence: jest.fn(async () => ({
        overallScore: 88,
        passed: true,
        words: [],
      })),
    };
    const controller = new SceneTrainingController(service);
    const wavRecorder = {
      start: jest.fn(async () => undefined),
      stop: jest.fn(async () => 'file:///sentence.wav'),
      cancel: jest.fn(async () => {
        lifecycle.push('release-reading-audio');
      }),
    };
    const screen = await render(
      <Training
        initialStage="read"
        scene={scene}
        trainingController={controller}
        wavRecorder={wavRecorder}
        ttsPlayer={{ play: jest.fn(async () => undefined), stop: jest.fn() }}
        onBack={jest.fn()}
        onFinish={jest.fn()}
      />,
    );

    await waitFor(() => expect(screen.getByLabelText('开始朗读')).toBeTruthy());
    await fireEvent.press(screen.getByLabelText('开始朗读'));
    await fireEvent.press(screen.getByLabelText('结束朗读'));
    await waitFor(() => expect(screen.getByText('朗读通过')).toBeTruthy());
    await fireEvent.press(screen.getByText('知道了'));
    await fireEvent.press(screen.getByText('进入模拟'));

    await waitFor(() => expect(service.advanceStage).toHaveBeenCalledTimes(3));
    expect(lifecycle).toEqual(['release-reading-audio', 'advance-stage']);
  });

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
    await fireEvent.press(screen.getByLabelText('上一个表达'));
    await waitFor(() => expect(screen.getByText('baggage')).toBeTruthy());
    await fireEvent.press(screen.getByText('进入词组'));
    await fireEvent.press(screen.getByText('进入朗读'));
    await waitFor(() =>
      expect(screen.getByText('I would like to check in this bag.')).toBeTruthy(),
    );
    const readContent = screen.getByLabelText('朗读内容');
    const readInner = readContent.props.children;
    await act(async () => {
      fireEvent(readContent.parent!, 'layout', { nativeEvent: { layout: { height: 260 } } });
      fireEvent(readInner, 'layout', { nativeEvent: { layout: { height: 200 } } });
      fireEvent(readInner, 'layout', { nativeEvent: { layout: { height: 400 } } });
    });
    await fireEvent.press(screen.getByLabelText('开始朗读'));
    await waitFor(() => expect(wavRecorder.start).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(screen.getByLabelText('结束朗读')).toBeTruthy());
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
    await fireEvent.press(screen.getByLabelText('上一句'));
    await waitFor(() => expect(screen.getByText('I would like to check in this bag.')).toBeTruthy());

    jest.useFakeTimers();
    await fireEvent.press(screen.getByLabelText('重新朗读'));
    await act(async () => {
      jest.advanceTimersByTime(30_000);
      await Promise.resolve();
    });
    await waitFor(() => expect(wavRecorder.stop).toHaveBeenCalledTimes(2));
    await waitFor(() => expect(screen.getByText('本句发音评估')).toBeTruthy());
    jest.useRealTimers();
    screen.unmount();
  });

  it('runs the local read/speak fallback, renders the radar, and exposes completion actions', async () => {
    const onBack = jest.fn();
    const onFinish = jest.fn();
    const onViewDetails = jest.fn();
    const alert = jest.spyOn(Alert, 'alert').mockImplementation(() => undefined);
    const screen = await render(
      <Training
        initialStage="read"
        onBack={onBack}
        onFinish={onFinish}
        onViewDetails={onViewDetails}
      />,
    );

    await fireEvent.press(screen.getByLabelText('退出训练'));
    expect(alert).toHaveBeenCalled();
    const buttons = alert.mock.calls[0][2] ?? [];
    buttons[1]?.onPress?.();
    expect(onBack).toHaveBeenCalled();

    await fireEvent.press(screen.getByLabelText('开始朗读'));
    await waitFor(() => expect(screen.getByText('正在听你朗读')).toBeTruthy());
    await fireEvent.press(screen.getByLabelText('结束朗读'));
    await waitFor(() => expect(screen.getByText('本句发音评估')).toBeTruthy());
    await fireEvent.press(screen.getByText('知道了'));
    await fireEvent.press(screen.getByText('进入模拟'));
    await waitFor(() => expect(screen.getByLabelText('结束当前会话')).toBeTruthy());
    await fireEvent.press(screen.getByLabelText('结束当前会话'));

    await waitFor(() => expect(screen.getByText('模拟完成')).toBeTruthy());
    expect(screen.getByLabelText(/五维评分/)).toBeTruthy();
    await fireEvent.press(screen.getByText('查看详情'));
    await waitFor(() => expect(onViewDetails).toHaveBeenCalled());
    await fireEvent.press(screen.getByLabelText('结束当前会话'));
    await waitFor(() => expect(screen.getByText('模拟完成')).toBeTruthy());
    await fireEvent.press(screen.getByText('返回场景广场'));
    expect(onFinish).toHaveBeenCalledTimes(1);
    alert.mockRestore();
  });

  it('navigates the local word/phrase fallback in both directions and toggles demo state', async () => {
    const screen = await render(<Training onBack={jest.fn()} onFinish={jest.fn()} />);
    expect(screen.getByText('recommend')).toBeTruthy();
    await fireEvent.press(screen.getByLabelText('播放发音'));
    expect(screen.getByText('recommend')).toBeTruthy();
    await fireEvent.press(screen.getByLabelText('播放发音'));
    await fireEvent.press(screen.getByText('进入词组'));
    expect(screen.getByText('feel like trying something different')).toBeTruthy();
    await fireEvent.press(screen.getByLabelText('上一个表达'));
    expect(screen.getByText('recommend')).toBeTruthy();
    await fireEvent.press(screen.getByText('进入词组'));
    await fireEvent.press(screen.getByText('下一个'));
    expect(screen.getByText('with oat milk')).toBeTruthy();
    await fireEvent.press(screen.getByText('进入朗读'));
    expect(screen.getByText('Could you recommend something less sweet?')).toBeTruthy();
    await fireEvent.press(screen.getByLabelText('返回学习阶段'));
    expect(screen.getByText('with oat milk')).toBeTruthy();
    screen.unmount();
  });

  it('uses onFinish for both local completion actions when no detail route exists', async () => {
    const onFinish = jest.fn();
    const first = await render(<Training initialStage="speak" onBack={jest.fn()} onFinish={onFinish} />);
    await fireEvent.press(first.getByLabelText('结束当前会话'));
    await waitFor(() => expect(first.getByText('模拟完成')).toBeTruthy());
    await fireEvent.press(first.getByText('查看详情'));
    expect(onFinish).toHaveBeenCalledTimes(1);
    first.unmount();
  });

  it('surfaces demo, recorder, and scoring failures without leaving reading', async () => {
    const service = {
      createFlow: jest.fn(async () => ({ sceneId: scene.sceneId, stage: 'WORD_LEARNING' as const, completed: false })),
      getContent: jest.fn(async () => scene.sentenceList.slice(0, 1)),
      advanceStage: jest.fn(async (_sceneId: string, stage: SceneFlowStage) => ({
        sceneId: scene.sceneId,
        stage: stage === 'WORD_LEARNING' ? 'PHRASE_LEARNING' as const : 'SENTENCE_LEARNING' as const,
        completed: false,
      })),
      evaluateSentence: jest.fn(async () => { throw new Error('评分失败'); }),
    };
    const controller = new SceneTrainingController(service);
    const wavRecorder = {
      start: jest.fn().mockRejectedValueOnce(new Error('麦克风忙碌')).mockResolvedValue(undefined),
      stop: jest.fn(async () => 'file:///sentence.wav'),
      cancel: jest.fn(async () => undefined),
    };
    const ttsPlayer = { play: jest.fn(async () => { throw '播放失败'; }), stop: jest.fn() };
    const screen = await render(
      <Training scene={scene} initialStage="read" trainingController={controller} wavRecorder={wavRecorder} ttsPlayer={ttsPlayer} onBack={jest.fn()} onFinish={jest.fn()} />,
    );
    await waitFor(() => expect(screen.getByLabelText('开始朗读')).toBeTruthy());
    await fireEvent.press(screen.getByLabelText('听标准示范'));
    await waitFor(() => expect(screen.getByText('标准发音播放失败')).toBeTruthy());
    await fireEvent.press(screen.getByLabelText('开始朗读'));
    await waitFor(() => expect(screen.getByText('麦克风忙碌')).toBeTruthy());
    await fireEvent.press(screen.getByLabelText('开始朗读'));
    await waitFor(() => expect(screen.getByLabelText('结束朗读')).toBeTruthy());
    await fireEvent.press(screen.getByLabelText('结束朗读'));
    await waitFor(() => expect(screen.getAllByText('评分失败').length).toBeGreaterThan(0));
    screen.unmount();
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

  it('toggles mute and clears the evaluation overlay when ending fails', async () => {
    let listener: ((value: RealtimeSessionSnapshot) => void) | null = null;
    let rejectEnd!: (error: Error) => void;
    const snapshot: RealtimeSessionSnapshot = {
      state: 'ready',
      muted: false,
      sessionId: 'session-1',
      userTranscript: 'I need to check this bag.',
      assistantTranscript: 'May I see your passport?',
      transcriptHistory: [],
      error: null,
    };
    const controller: FreeChatControllerPort = {
      getSnapshot: () => snapshot,
      subscribe: jest.fn((next) => {
        listener = next;
        next(snapshot);
        return () => { listener = null; };
      }),
      start: jest.fn(async () => undefined),
      setMuted: jest.fn(),
      interrupt: jest.fn(),
      end: jest.fn(() => new Promise((_, reject) => { rejectEnd = reject; })),
    };
    const screen = await render(
      <SceneCallStage
        scene={scene}
        progressCollapsed
        createController={() => controller}
        onComplete={jest.fn()}
      />,
    );

    await fireEvent.press(screen.getByLabelText('关闭麦克风'));
    expect(controller.setMuted).toHaveBeenCalledWith(true);
    await fireEvent.press(screen.getByLabelText('结束当前会话'));
    expect(screen.getByText('正在整理本次对话与五项能力表现…')).toBeTruthy();
    await act(async () => rejectEnd(new Error('end failed')));
    await waitFor(() => expect(screen.queryByText('正在整理本次对话与五项能力表现…')).toBeNull());
    expect(listener).not.toBeNull();
  });

  it('returns an unavailable completion when a silent scene session cannot be scored', async () => {
    const snapshot: RealtimeSessionSnapshot = {
      state: 'ready',
      muted: false,
      sessionId: 'session-silent',
      userTranscript: '',
      assistantTranscript: 'Hello, how can I help?',
      transcriptHistory: [],
      error: null,
    };
    const controller: FreeChatControllerPort = {
      getSnapshot: () => snapshot,
      subscribe: jest.fn((next) => {
        next(snapshot);
        return () => undefined;
      }),
      start: jest.fn(async () => undefined),
      setMuted: jest.fn(),
      interrupt: jest.fn(),
      end: jest.fn(async () => { throw new Error('有效用户轮次不足'); }),
    };
    const onComplete = jest.fn();
    const screen = await render(
      <SceneCallStage
        scene={scene}
        progressCollapsed={false}
        createController={() => controller}
        onComplete={onComplete}
      />,
    );

    await fireEvent.press(screen.getByLabelText('结束当前会话'));
    await waitFor(() => expect(onComplete).toHaveBeenCalledWith(
      expect.objectContaining({
        sceneId: scene.sceneId,
        sessionId: 'session-silent',
      }),
    ));
  });
});
