export type TrainingMode = 'SCENE' | 'FREE_CHAT' | 'INTERVIEW' | 'IELTS';

export type AnalyticsPage = Readonly<{
  pageCode: string;
  mode?: TrainingMode;
  assetType?: 'REPORT';
}>;

const routes: readonly { prefix: string; value: AnalyticsPage }[] = [
  { prefix: '/conversation', value: { pageCode: 'conversation', mode: 'FREE_CHAT' } },
  { prefix: '/scenes', value: { pageCode: 'scene-training', mode: 'SCENE' } },
  { prefix: '/learning/scenes', value: { pageCode: 'scene-assets', mode: 'SCENE', assetType: 'REPORT' } },
  { prefix: '/learning', value: { pageCode: 'scene-assets', mode: 'SCENE', assetType: 'REPORT' } },
  { prefix: '/ielts/assets', value: { pageCode: 'ielts-assets', mode: 'IELTS', assetType: 'REPORT' } },
  { prefix: '/ielts', value: { pageCode: 'ielts-training', mode: 'IELTS' } },
  { prefix: '/interview/assets', value: { pageCode: 'interview-assets', mode: 'INTERVIEW', assetType: 'REPORT' } },
  { prefix: '/interview', value: { pageCode: 'interview-training', mode: 'INTERVIEW' } },
];

export function pageForPath(pathname = '/'): AnalyticsPage {
  const path = normalizeTrackedPath(pathname);
  return routes.find(({ prefix }) => path === prefix || path.startsWith(`${prefix}/`))?.value
    ?? { pageCode: 'other' };
}

export function normalizeTrackedPath(pathname = '/') {
  const path = `/${String(pathname).split(/[?#]/, 1)[0].split('/').filter(Boolean).join('/')}`;

  if (/^\/conversation\/call$/.test(path)) return '/conversation/session';
  if (/^\/scenes\/[^/]+\/intro$/.test(path)) return '/scenes/intro';
  if (/^\/scenes\/[^/]+\/training$/.test(path)) return '/scenes/session';
  if (/^\/learning\/scenes\/[^/]+$/.test(path)) return '/learning/scenes/detail';
  if (/^\/ielts\/assets\/[^/]+$/.test(path)) return '/ielts/assets/detail';
  if (/^\/interview\/assets\/[^/]+$/.test(path)) return '/interview/assets/detail';
  if (/^\/profile\/help\/article\/[^/]+$/.test(path)) return '/profile/help/article';
  if (/^\/profile\/help\/[^/]+$/.test(path)) return '/profile/help/category';

  return path || '/';
}
