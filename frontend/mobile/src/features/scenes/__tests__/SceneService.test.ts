import type { ApiRequestOptions } from '@/infrastructure/http/ApiClient';

import { createWavUploadFile, SceneService } from '../SceneService';

function createClient(responses: unknown[]) {
  return {
    request: jest.fn(
      async (_path: string, _options?: ApiRequestOptions) => responses.shift(),
    ),
  };
}

const generatedScene = {
  sceneId: 'scene/1',
  title: 'Coffee order',
  background: 'A busy coffee shop.',
  aiRole: 'Barista',
  userRole: 'Customer',
  learningGoal: 'Order and customize a drink.',
  estimatedMinutes: 8,
  wordList: [
    {
      contentId: 'word-1',
      englishText: 'decaf',
      chineseText: '无咖啡因的',
      phonetic: '/ˈdiːkæf/',
    },
  ],
  phraseList: [
    {
      contentId: 'phrase-1',
      englishText: 'Could I get',
      chineseText: '我可以要……吗',
      phonetic: null,
    },
  ],
  sentenceList: [
    {
      contentId: 'sentence-1',
      englishText: 'Could I get a decaf latte?',
      chineseText: '我可以要一杯无咖啡因拿铁吗？',
      phonetic: null,
    },
  ],
  scenePrompt: 'Provider prompt',
};

describe('SceneService', () => {
  it('generates and validates all three learning content groups', async () => {
    const client = createClient([generatedScene]);
    const service = new SceneService(client);

    await expect(
      service.generate('  I want to order coffee.  ', 'CEFR B'),
    ).resolves.toEqual(generatedScene);
    expect(client.request).toHaveBeenCalledWith('/api/custom-scenes/generate', {
      method: 'POST',
      body: JSON.stringify({
        sceneInput: 'I want to order coffee.',
        userPreference: 'CEFR B',
      }),
      timeoutMs: 60_000,
    });
  });

  it('rejects an incomplete generated scene before the UI starts training', async () => {
    const client = createClient([{ ...generatedScene, sentenceList: [] }]);
    const service = new SceneService(client);

    await expect(service.generate('Coffee')).rejects.toThrow(
      '场景生成内容不完整，请重新生成',
    );
  });

  it('creates a flow, loads an explicit stage and advances it', async () => {
    const client = createClient([
      { sceneId: 'scene/1', stage: 'WORD_LEARNING', completed: false },
      generatedScene.wordList,
      { sceneId: 'scene/1', stage: 'PHRASE_LEARNING', completed: false },
    ]);
    const service = new SceneService(client);

    await service.createFlow('scene/1');
    await service.getContent('scene/1', 'WORD_LEARNING');
    await service.advanceStage('scene/1', 'PHRASE_LEARNING');

    expect(client.request.mock.calls).toEqual([
      [
        '/api/custom-scenes/flows',
        { method: 'POST', body: JSON.stringify({ sceneId: 'scene/1' }) },
      ],
      [
        '/api/custom-scenes/flows/scene%2F1/content?stage=WORD_LEARNING',
        undefined,
      ],
      [
        '/api/custom-scenes/flows/advance',
        {
          method: 'POST',
          body: JSON.stringify({
            sceneId: 'scene/1',
            stage: 'PHRASE_LEARNING',
          }),
        },
      ],
    ]);
  });

  it('uploads a React Native WAV file descriptor for sentence scoring', async () => {
    const client = createClient([
      { overallScore: 86, passed: true, words: [] },
    ]);
    const service = new SceneService(client);

    await service.evaluateSentence('scene/1', 'sentence 1', 'file:///take.wav');

    const [path, options] = client.request.mock.calls[0];
    expect(path).toBe(
      '/api/custom-scenes/scene%2F1/sentences/sentence%201/evaluation',
    );
    expect(options).toEqual(
      expect.objectContaining({ method: 'POST', body: expect.any(FormData) }),
    );
    const uploadFile = createWavUploadFile('file:///take.wav');
    expect(uploadFile.uri).toBe('file:///take.wav');
    expect(uploadFile.bytes).toEqual(expect.any(Function));
    expect((options as ApiRequestOptions).headers).toBeUndefined();
  });
});
