import type { Href } from 'expo-router';

const href = (pathname: string) => pathname as Href;

export const routes = {
  public: {
    welcome: href('/welcome'),
    login: href('/login'),
    signup: href('/signup'),
  },
  onboarding: {
    level: href('/onboarding/level'),
    teacher: href('/onboarding/teacher'),
  },
  tabs: {
    conversation: href('/conversation'),
    scenes: href('/scenes'),
    learning: href('/learning'),
    profile: href('/profile'),
  },
  conversation: {
    call: href('/conversation/call'),
  },
  scenes: {
    intro: (id: string) => href(`/scenes/${id}/intro`),
    training: (id: string, stage?: 'learn' | 'read' | 'speak') => href(`/scenes/${id}/training${stage ? `?stage=${stage}` : ''}`),
  },
  specialty: {
    ielts: href('/specialty/ielts'),
    interview: href('/specialty/interview'),
  },
  learning: {
    sceneDetail: (id: string) => href(`/learning/scenes/${id}`),
    ielts: {
      overview: href('/learning/ielts'),
      history: href('/learning/ielts/history'),
      trends: href('/learning/ielts/trends'),
    },
    interview: {
      overview: href('/learning/interview'),
      history: href('/learning/interview/history'),
      trends: href('/learning/interview/trends'),
      record: (id: string) => href(`/learning/interview/${id}`),
    },
  },
  profile: {
    overview: href('/profile/overview'),
    membership: href('/profile/membership'),
    assistant: href('/profile/assistant'),
    account: href('/profile/account'),
  },
} as const;
