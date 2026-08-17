import { normalizeTrackedPath, pageForPath } from '../pageCatalog';

describe('analytics page catalog', () => {
  it('normalizes mobile routes without dynamic identifiers', () => {
    expect(normalizeTrackedPath('/conversation/call')).toBe('/conversation/session');
    expect(normalizeTrackedPath('/scenes/scene-1/training?stage=speak')).toBe('/scenes/session');
    expect(normalizeTrackedPath('/learning/scenes/private-scene')).toBe('/learning/scenes/detail');
    expect(normalizeTrackedPath('/profile/help/article/article-1')).toBe('/profile/help/article');
  });

  it('maps tracked routes to the same stable mode codes as Web analytics', () => {
    expect(pageForPath('/conversation')).toEqual({ pageCode: 'conversation', mode: 'FREE_CHAT' });
    expect(pageForPath('/scenes/scene-1/intro')).toEqual({ pageCode: 'scene-training', mode: 'SCENE' });
    expect(pageForPath('/ielts/assets/record-1')).toEqual({ pageCode: 'ielts-assets', mode: 'IELTS', assetType: 'REPORT' });
    expect(pageForPath('/interview')).toEqual({ pageCode: 'interview-training', mode: 'INTERVIEW' });
  });
});
