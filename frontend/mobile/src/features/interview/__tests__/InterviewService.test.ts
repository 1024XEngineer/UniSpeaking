import type { ApiRequestOptions } from '@/infrastructure/http/ApiClient';

import { InterviewService, type InterviewMaterial } from '../InterviewService';

function createClient(responses: unknown[]) {
  return {
    request: jest.fn(async (_path: string, _options?: ApiRequestOptions) => responses.shift()),
  };
}

const material: InterviewMaterial = {
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
  finalText: 'Product Manager\nOwn product strategy',
};

describe('InterviewService', () => {
  it('posts materials as multipart form data and trims text fields', async () => {
    const client = createClient([{ material }]);
    const service = new InterviewService(client);

    await service.prepareMaterials({
      jobDescriptionText: '  Build products.  ',
      resumeText: '  Led a team.  ',
      resumeFile: new Blob(['resume'], { type: 'application/pdf' }),
    });

    const [path, options] = client.request.mock.calls[0];
    expect(path).toBe('/api/interview-scenes/prepare-materials');
    expect(options).toEqual(expect.objectContaining({ method: 'POST', body: expect.any(FormData) }));
    const form = (options as ApiRequestOptions).body as FormData;
    expect(form.get('jobDescriptionText')).toBe('Build products.');
    expect(form.get('resumeText')).toBe('Led a team.');
    expect(form.get('resumeFile')).toEqual(expect.any(Blob));
  });

  it.each(['EASY', 'STANDARD', 'HARD'] as const)('sends %s difficulty to the interview endpoint', async (difficulty) => {
    const client = createClient([{ sceneId: 'scene-1', scenePrompt: 'Ask about strategy.' }]);
    await new InterviewService(client).generateScene(material, difficulty);

    expect(client.request).toHaveBeenCalledWith('/api/interview-scenes', {
      method: 'POST',
      body: JSON.stringify({ material, difficulty }),
      timeoutMs: 60_000,
    });
  });

  it('rejects blank job descriptions before uploading', async () => {
    const client = createClient([]);
    await expect(new InterviewService(client).prepareMaterials({ jobDescriptionText: '  ' })).rejects.toThrow('职位描述不能为空');
    expect(client.request).not.toHaveBeenCalled();
  });

  it('rejects empty responsibilities or qualification requirements', async () => {
    const client = createClient([]);
    const service = new InterviewService(client);
    await expect(service.generateScene({ ...material, responsibilities: [] }, 'STANDARD')).rejects.toThrow();
    await expect(service.generateScene({ ...material, qualificationRequirements: [' '] }, 'STANDARD')).rejects.toThrow();
    expect(client.request).not.toHaveBeenCalled();
  });

  it('derives final text when the server omits it', async () => {
    const result = await new InterviewService(createClient([{ material: { ...material, finalText: '' } }])).prepareMaterials({ jobDescriptionText: 'JD' });
    expect(result.material.finalText).toContain('Own product strategy');
  });

  it('rejects invalid scene responses', async () => {
    await expect(new InterviewService(createClient([{ sceneId: 'scene-1' }])).generateScene(material, 'STANDARD')).rejects.toThrow('面试场景响应不完整');
  });

	it('normalizes harmless model shape drift instead of making the user retry', async () => {
		const response = { material: { ...material, responsibilities: 'Build APIs\nReview code', requiredSkills: null, finalText: '' } };
		const result = await new InterviewService(createClient([response])).prepareMaterials({ jobDescriptionText: 'JD' });
		expect(result.material.responsibilities).toEqual(['Build APIs', 'Review code']);
		expect(result.material.requiredSkills).toEqual([]);
		expect(result.material.finalText).toContain('Build APIs');
	});
});
