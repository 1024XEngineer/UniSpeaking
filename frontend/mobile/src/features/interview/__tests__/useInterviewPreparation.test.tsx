import { act, renderHook } from '@testing-library/react-native';

import { useInterviewPreparation } from '../useInterviewPreparation';

function createService(): {
  prepareMaterials: jest.Mock;
  generateScene: jest.Mock;
} {
  return {
    prepareMaterials: jest.fn(async () => ({
      material: {
        jobTitle: 'Product Manager',
        responsibilities: ['Own product strategy'],
        qualificationRequirements: ['Five years of experience'],
        requiredSkills: ['Communication'],
        otherJobInformation: null,
        education: [],
        workExperiences: [],
        projectExperiences: [],
        skillsAndAbilities: [],
        interviewableExperienceClues: [],
        finalText: 'Product Manager',
      },
    })),
    generateScene: jest.fn(async (_material?: unknown, _difficulty?: unknown) => ({ sceneId: 'scene-123', scenePrompt: 'Tell me about strategy.' })),
  };
}

jest.mock('expo-file-system', () => ({
  File: { pickFileAsync: jest.fn() },
}));

describe('useInterviewPreparation', () => {
  it('keeps canceled file selection empty', async () => {
    const { File } = jest.requireMock('expo-file-system') as { File: { pickFileAsync: jest.Mock } };
    File.pickFileAsync.mockResolvedValue({ canceled: true, result: null });
    const service = createService();
    const { result } = await renderHook(() => useInterviewPreparation(service));

    await act(async () => result.current.pickResume());

    expect(result.current.resumeFile).toBeNull();
    expect(result.current.resumeFileName).toBeNull();
  });

  it('validates JD before calling the service', async () => {
    const service = createService();
    const { result } = await renderHook(() => useInterviewPreparation(service));

    await act(async () => result.current.start({ jobDescription: '  ', difficulty: 'standard' }));

    expect(result.current.error).toBe('职位描述不能为空');
    expect(service.prepareMaterials).not.toHaveBeenCalled();
  });

  it('prepares materials before generating the scene and maps difficulty', async () => {
    const service = createService();
    const { result } = await renderHook(() => useInterviewPreparation(service));
    const order: string[] = [];
    service.prepareMaterials.mockImplementation(async () => {
      order.push('prepare');
      return (await createService().prepareMaterials());
    });
    service.generateScene.mockImplementation(async (_material: unknown, difficulty: unknown) => {
      order.push(`generate:${difficulty}`);
      return { sceneId: 'scene-123', scenePrompt: 'Tell me about strategy.' };
    });

    await act(async () => result.current.start({ jobDescription: 'JD', difficulty: 'hard' }));

    expect(order).toEqual(['prepare', 'generate:HARD']);
    expect(result.current.result).toEqual(expect.objectContaining({
      jobTitle: 'Product Manager',
      scene: expect.objectContaining({ sceneId: 'scene-123' }),
    }));
  });

  it('ignores a second start while the first request is pending', async () => {
    let resolvePrepare!: (value: Awaited<ReturnType<ReturnType<typeof createService>['prepareMaterials']>>) => void;
    const service = createService();
    service.prepareMaterials.mockReturnValue(new Promise((resolve) => { resolvePrepare = resolve; }));
    const { result } = await renderHook(() => useInterviewPreparation(service));

    let first: Promise<unknown>;
    await act(async () => {
      first = result.current.start({ jobDescription: 'JD', difficulty: 'easy' });
    });
    await act(async () => {
      await result.current.start({ jobDescription: 'JD', difficulty: 'easy' });
    });
    expect(service.prepareMaterials).toHaveBeenCalledTimes(1);
    resolvePrepare(await createService().prepareMaterials());
    await act(async () => { await first; });
  });

  it('retains input state when preparation fails', async () => {
    const service = createService();
    service.prepareMaterials.mockRejectedValue(new Error('服务器暂时不可用'));
    const { result } = await renderHook(() => useInterviewPreparation(service));

    await act(async () => result.current.start({ jobDescription: 'Keep this JD', difficulty: 'standard' }));

    expect(result.current.error).toBe('服务器暂时不可用');
    expect(result.current.result).toBeNull();
  });
});
