import type { ApiRequestOptions } from '@/infrastructure/http/ApiClient';
import { File as ExpoFile } from 'expo-file-system';

export type InterviewDifficulty = 'EASY' | 'STANDARD' | 'HARD';

export type InterviewMaterial = {
  jobTitle: string | null;
  responsibilities: string[];
  qualificationRequirements: string[];
  requiredSkills: string[];
  otherJobInformation: string | null;
  education: string[];
  workExperiences: string[];
  projectExperiences: string[];
  skillsAndAbilities: string[];
  interviewableExperienceClues: string[];
  finalText: string;
};

export type InterviewMaterialDraft = {
  material: InterviewMaterial;
};

export type InterviewScene = {
  sceneId: string;
  scenePrompt: string;
};

export type PrepareMaterialsInput = {
  jobDescriptionText: string;
  resumeText?: string;
  resumeFile?: Blob | ExpoFile;
};

type ApiRequester = {
  request(path: string, options?: ApiRequestOptions): Promise<unknown>;
};

const DIFFICULTIES: readonly InterviewDifficulty[] = ['EASY', 'STANDARD', 'HARD'];

function nonEmptyString(value: unknown): value is string {
  return typeof value === 'string' && value.trim().length > 0;
}

function isStringOrNull(value: unknown): value is string | null {
  return value === null || typeof value === 'string';
}

function isStringArray(value: unknown): value is string[] {
  return Array.isArray(value) && value.every((item) => typeof item === 'string');
}

function stringList(value: unknown) {
  if (Array.isArray(value)) return value.filter((item): item is string => typeof item === 'string').map((item) => item.trim()).filter(Boolean);
  if (typeof value === 'string' && value.trim()) return value.split(/\r?\n|[；;]/).map((item) => item.trim()).filter(Boolean);
  return [];
}

function renderFinalText(material: {
  jobTitle: string | null;
  responsibilities: string[];
  qualificationRequirements: string[];
}) {
  return [
    material.jobTitle,
    material.responsibilities.slice(0, 3).join('、'),
    material.qualificationRequirements.slice(0, 3).join('、'),
  ].filter(Boolean).join(' · ');
}

function normalizeMaterialDraft(value: unknown): InterviewMaterialDraft | null {
  if (!value || typeof value !== 'object' || !(value as { material?: unknown }).material || typeof (value as { material: unknown }).material !== 'object') return null;
  const raw = (value as { material: Record<string, unknown> }).material;
  const responsibilities = stringList(raw.responsibilities);
  const qualificationRequirements = stringList(raw.qualificationRequirements);
  if (!responsibilities.length || !qualificationRequirements.length) return null;
  const nextMaterial = {
    jobTitle: typeof raw.jobTitle === 'string' ? raw.jobTitle.trim() || null : null,
    responsibilities,
    qualificationRequirements,
    requiredSkills: stringList(raw.requiredSkills),
    otherJobInformation: typeof raw.otherJobInformation === 'string' ? raw.otherJobInformation.trim() || null : null,
    education: stringList(raw.education),
    workExperiences: stringList(raw.workExperiences),
    projectExperiences: stringList(raw.projectExperiences),
    skillsAndAbilities: stringList(raw.skillsAndAbilities),
    interviewableExperienceClues: stringList(raw.interviewableExperienceClues),
    finalText: typeof raw.finalText === 'string' ? raw.finalText.trim() : '',
  };
  return { material: {
    ...nextMaterial,
    finalText: nextMaterial.finalText || renderFinalText(nextMaterial),
  } };
}

function isInterviewMaterial(value: unknown): value is InterviewMaterial {
  if (!value || typeof value !== 'object') return false;
  const material = value as Partial<InterviewMaterial>;
  return (
    isStringOrNull(material.jobTitle) &&
    isStringArray(material.responsibilities) &&
    material.responsibilities.some(nonEmptyString) &&
    isStringArray(material.qualificationRequirements) &&
    material.qualificationRequirements.some(nonEmptyString) &&
    isStringArray(material.requiredSkills) &&
    isStringOrNull(material.otherJobInformation) &&
    isStringArray(material.education) &&
    isStringArray(material.workExperiences) &&
    isStringArray(material.projectExperiences) &&
    isStringArray(material.skillsAndAbilities) &&
    isStringArray(material.interviewableExperienceClues) &&
    nonEmptyString(material.finalText)
  );
}

function isMaterialDraft(value: unknown): value is InterviewMaterialDraft {
  return Boolean(value && typeof value === 'object' && isInterviewMaterial((value as { material?: unknown }).material));
}

function isInterviewScene(value: unknown): value is InterviewScene {
  if (!value || typeof value !== 'object') return false;
  const scene = value as Partial<InterviewScene>;
  return nonEmptyString(scene.sceneId) && nonEmptyString(scene.scenePrompt);
}

function isDifficulty(value: string): value is InterviewDifficulty {
  return DIFFICULTIES.includes(value as InterviewDifficulty);
}

export function createResumeUploadFile(uri: string) {
  return new ExpoFile(uri);
}

export class InterviewService {
  constructor(private readonly client: ApiRequester) {}

  async prepareMaterials(input: PrepareMaterialsInput): Promise<InterviewMaterialDraft> {
    if (!nonEmptyString(input.jobDescriptionText)) {
      throw new Error('职位描述不能为空');
    }

    const body = new FormData();
    body.append('jobDescriptionText', input.jobDescriptionText.trim());
    if (input.resumeText !== undefined && input.resumeText.trim()) {
      body.append('resumeText', input.resumeText.trim());
    }
    if (input.resumeFile !== undefined) {
      body.append('resumeFile', input.resumeFile);
    }

    const draft = await this.client.request('/api/interview-scenes/prepare-materials', {
      method: 'POST',
      body,
      timeoutMs: 60_000,
    });
    if (isMaterialDraft(draft)) return draft;
    const normalized = normalizeMaterialDraft(draft);
    if (!normalized) {
      throw new Error('面试材料响应不完整，请重新准备');
    }
    return normalized;
  }

  async generateScene(material: InterviewMaterial, difficulty: InterviewDifficulty): Promise<InterviewScene> {
    if (!isInterviewMaterial(material)) {
      throw new Error('面试材料中的职责和任职要求不能为空');
    }
    if (!isDifficulty(difficulty)) {
      throw new Error('面试难度无效');
    }

    const scene = await this.client.request('/api/interview-scenes', {
      method: 'POST',
      body: JSON.stringify({ material, difficulty }),
      timeoutMs: 60_000,
    });
    if (!isInterviewScene(scene)) {
      throw new Error('面试场景响应不完整，请重新生成');
    }
    return scene;
  }
}
