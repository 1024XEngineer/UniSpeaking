import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

import { AuthenticatedMediaClient } from '@/features/ielts/AuthenticatedMediaClient';
import { SecureTokenStore } from '@/infrastructure/auth/SecureTokenStore';
import { getRuntimeConfig } from '@/infrastructure/config/runtimeConfig';

type NativeAudioPlayer = {
  play(): void;
  pause(): void;
  remove(): void;
};

function createNativePlayer(uri: string): NativeAudioPlayer {
  // eslint-disable-next-line @typescript-eslint/no-require-imports
  const { createAudioPlayer } = require('expo-audio') as typeof import('expo-audio');
  return createAudioPlayer(uri);
}

export function useRecordingPlayback(urls: readonly string[]) {
  const mediaClient = useMemo(() => {
    const { backendUrl } = getRuntimeConfig();
    return new AuthenticatedMediaClient(backendUrl, new SecureTokenStore());
  }, []);
  const [playing, setPlaying] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const cancelledRef = useRef(false);
  const playerRef = useRef<NativeAudioPlayer | null>(null);
  const cacheRef = useRef<{ remove(): void }[]>([]);

  const cleanup = useCallback(() => {
    const player = playerRef.current;
    playerRef.current = null;
    try {
      player?.pause();
    } finally {
      player?.remove();
    }
    for (const file of cacheRef.current) file.remove();
    cacheRef.current = [];
  }, []);

  const stop = useCallback(() => {
    cancelledRef.current = true;
    setPlaying(false);
    cleanup();
  }, [cleanup]);

  const playAll = useCallback(async () => {
    if (!urls.length || playing) return;
    cancelledRef.current = false;
    setError(null);
    setPlaying(true);
    try {
      for (const url of urls) {
        if (cancelledRef.current) break;
        const asset = await mediaClient.download(url);
        cacheRef.current.push(asset);
        if (cancelledRef.current) break;
        const player = createNativePlayer(asset.uri);
        playerRef.current = player;
        player.play();
        await new Promise<void>((resolve) => {
          setTimeout(resolve, 4_000);
        });
      }
    } catch (playbackError) {
      setError(
        playbackError instanceof Error ? playbackError.message : '录音播放失败',
      );
    } finally {
      stop();
    }
  }, [mediaClient, playing, stop, urls]);

  const toggle = useCallback(() => {
    if (playing) {
      stop();
      return;
    }
    void playAll();
  }, [playAll, playing, stop]);

  useEffect(() => () => stop(), [stop]);

  return {
    playing,
    error,
    canPlay: urls.length > 0,
    toggle,
    stop,
  };
}
