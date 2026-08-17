import { useCallback, useEffect, useRef } from 'react';

import type { Teacher } from '@/theme/tokens';

import { TeacherPreviewPlayer } from './TeacherPreviewPlayer';

export function useTeacherPreview() {
  const previewPlayerRef = useRef<TeacherPreviewPlayer | null>(null);

  const getPreviewPlayer = useCallback(() => {
    if (previewPlayerRef.current) return previewPlayerRef.current;

    // Keep expo-audio out of component import/render paths. Jest and web
    // callers can render selectors without a native AudioModule; the module
    // is loaded only when the user actually asks to hear a preview.
    // eslint-disable-next-line @typescript-eslint/no-require-imports
    const { createAudioPlayer, setAudioModeAsync } = require('expo-audio') as typeof import('expo-audio');
    void setAudioModeAsync({
      allowsRecording: false,
      interruptionMode: 'doNotMix',
      playsInSilentMode: true,
      shouldRouteThroughEarpiece: false,
    });
    previewPlayerRef.current = new TeacherPreviewPlayer(createAudioPlayer(null));
    return previewPlayerRef.current;
  }, []);

  useEffect(() => () => {
    previewPlayerRef.current?.dispose();
  }, []);

  const playTeacher = useCallback((teacher: Teacher) => {
    void getPreviewPlayer().play(teacher);
  }, [getPreviewPlayer]);

  const stop = useCallback(() => {
    previewPlayerRef.current?.stop();
  }, []);

  return { playTeacher, stop };
}
