import AsyncStorage from '@react-native-async-storage/async-storage';
import { forgetSpecialty, readRememberedSpecialty, rememberSpecialty } from '../specialtyMemory';
import { routes } from '../routes';

describe('navigation helpers', () => {
  it('builds stable route hrefs', () => {
    expect(routes.public.login).toBe('/login');
    expect(routes.scenes.training('scene-1', 'speak')).toBe('/scenes/scene-1/training?stage=speak');
    expect(routes.scenes.training('scene-1')).toBe('/scenes/scene-1/training');
    expect(routes.specialty.interviewPractice('scene-1', 'PM')).toEqual({ pathname: '/interview', params: { sceneId: 'scene-1', jobTitle: 'PM', practice: '1' } });
    expect(routes.scenes.intro('scene-1')).toBe('/scenes/scene-1/intro');
    expect(routes.specialty.interviewPractice('scene-1')).toEqual({ pathname: '/interview', params: { sceneId: 'scene-1', jobTitle: '', practice: '1' } });
    expect(routes.learning.sceneDetail('scene-1')).toBe('/learning/scenes/scene-1');
    expect(routes.learning.ielts.record('record-1')).toBe('/ielts/assets/record-1');
    expect(routes.learning.interview.record('record-1')).toBe('/interview/assets/record-1');
    expect(routes.profile.helpCategory('a b')).toBe('/profile/help/a%20b');
    expect(routes.profile.helpArticle('a/b')).toBe('/profile/help/article/a%2Fb');
  });

  it('persists only supported specialty values and tolerates storage failures', async () => {
    await rememberSpecialty('ielts');
    expect(AsyncStorage.setItem).toHaveBeenCalledWith('unispeaking.navigation.last-specialty.v1', 'ielts');
    (AsyncStorage.getItem as jest.Mock).mockResolvedValueOnce('interview');
    await expect(readRememberedSpecialty()).resolves.toBe('interview');
    (AsyncStorage.getItem as jest.Mock).mockResolvedValueOnce('other');
    await expect(readRememberedSpecialty()).resolves.toBeNull();
    await forgetSpecialty();
    expect(AsyncStorage.removeItem).toHaveBeenCalled();
    (AsyncStorage.setItem as jest.Mock).mockRejectedValueOnce(new Error('offline'));
    await expect(rememberSpecialty('interview')).resolves.toBeUndefined();
  });
});
