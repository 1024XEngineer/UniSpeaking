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
    ielts: href('/ielts'),
    interview: href('/interview'),
  },
  learning: {
    sceneDetail: (id: string) => href(`/learning/scenes/${id}`),
    ielts: {
      overview: href('/ielts/assets'),
      history: href('/ielts/assets/history'),
      trends: href('/ielts/assets/trends'),
      record: (id: string) => href(`/ielts/assets/${id}`),
    },
    interview: {
      overview: href('/interview/assets'),
      history: href('/interview/assets/history'),
      trends: href('/interview/assets/trends'),
      record: (id: string) => href(`/interview/assets/${id}`),
    },
  },
  profile: {
    overview: href('/profile/overview'),
    insights: href('/profile/insights'),
    membership: href('/profile/membership'),
    assistant: href('/profile/assistant'),
    account: href('/profile/account'),
    help: href('/profile/help'),
    about: href('/profile/about'),
  },
} as const;
