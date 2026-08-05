import { Image } from 'expo-image';
import { GearSixIcon } from 'phosphor-react-native/src/icons/GearSix';
import { MicrophoneIcon } from 'phosphor-react-native/src/icons/Microphone';
import { MicrophoneSlashIcon } from 'phosphor-react-native/src/icons/MicrophoneSlash';
import { PhoneDisconnectIcon } from 'phosphor-react-native/src/icons/PhoneDisconnect';
import { SubtitlesIcon } from 'phosphor-react-native/src/icons/Subtitles';
import { useEffect, useRef, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import Animated, {
  cancelAnimation,
  Easing,
  interpolate,
  runOnJS,
  useAnimatedStyle,
  useSharedValue,
  withDelay,
  withRepeat,
  withTiming,
} from 'react-native-reanimated';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ConversationSettings } from '@/components/ConversationSettings';
import { AppButton, AppIcon, AppScreen, Brand } from '@/components/ui';
import { useAppModel } from '@/model/AppModel';
import { colors } from '@/theme/tokens';

function formatDuration(total: number) {
  return `${String(Math.floor(total / 60)).padStart(2, '0')}:${String(total % 60).padStart(2, '0')}`;
}

const voiceWaveRestingLevels = [0.28, 0.52, 0.78, 1, 0.72, 0.48, 0.3];

function VoiceWaveBar({ active, compact, index, level }: { active: boolean; compact: boolean; index: number; level: number }) {
  const scale = useSharedValue(level);

  useEffect(() => {
    cancelAnimation(scale);
    if (!active) {
      scale.value = level;
      return;
    }
    scale.value = withDelay(
      index * 45,
      withRepeat(
        withTiming(0.2 + ((index * 17) % 5) * 0.11, {
          duration: 360 + ((index * 73) % 190),
          easing: Easing.inOut(Easing.ease),
        }),
        -1,
        true,
      ),
    );
  }, [active, index, level, scale]);

  const animatedStyle = useAnimatedStyle(() => ({
    transform: [{ scaleY: scale.value }],
  }));

  return (
    <Animated.View
      style={[
        styles.voiceWaveBar,
        compact && styles.voiceWaveBarCompact,
        !active && styles.voiceWaveBarInactive,
        animatedStyle,
      ]}
    />
  );
}

function VoiceWaveform({ active, compact }: { active: boolean; compact: boolean }) {
  return (
    <View accessibilityElementsHidden importantForAccessibility="no-hide-descendants" style={[styles.voiceWave, compact && styles.voiceWaveCompact]}>
      {voiceWaveRestingLevels.map((level, index) => (
        <VoiceWaveBar key={index} active={active} compact={compact} index={index} level={level} />
      ))}
    </View>
  );
}

export function CallExperience({
  onEnd,
  allowSubtitleToggle = true,
  compactTranscriptLayout = false,
  progressCollapsed = false,
  transcriptEnglish = 'Hi there! How are you feeling today?',
  transcriptChinese = '嗨！你今天感觉怎么样？',
}: {
  onEnd: () => void;
  allowSubtitleToggle?: boolean;
  compactTranscriptLayout?: boolean;
  progressCollapsed?: boolean;
  transcriptEnglish?: string;
  transcriptChinese?: string;
}) {
  const { teacher } = useAppModel();
  const [elapsed, setElapsed] = useState(0);
  const [muted, setMuted] = useState(false);
  const [subtitles, setSubtitles] = useState(true);
  const [translated, setTranslated] = useState(false);
  const subtitlesProgress = useSharedValue(1);
  const transcriptVisibility = useSharedValue(1);
  const compactLayoutProgress = useSharedValue(progressCollapsed ? 1 : 0);

  useEffect(() => {
    if (muted) return;
    const timer = setInterval(() => setElapsed((current) => current + 1), 1000);
    return () => clearInterval(timer);
  }, [muted]);

  useEffect(() => {
    subtitlesProgress.value = withTiming(subtitles ? 1 : 0, {
      duration: 480,
      easing: Easing.inOut(Easing.cubic),
    });
    transcriptVisibility.value = subtitles
      ? withDelay(250, withTiming(1, { duration: 190, easing: Easing.out(Easing.ease) }))
      : withTiming(0, { duration: 120, easing: Easing.out(Easing.ease) });
  }, [subtitles, subtitlesProgress, transcriptVisibility]);

  useEffect(() => {
    compactLayoutProgress.value = withTiming(progressCollapsed ? 1 : 0, {
      duration: 420,
      easing: Easing.inOut(Easing.cubic),
    });
  }, [compactLayoutProgress, progressCollapsed]);

  const presenceTransitionStyle = useAnimatedStyle(() => ({
    transform: [{
      translateY:
        interpolate(subtitlesProgress.value, [0, 1], [205, compactTranscriptLayout ? -10 : 18])
        + interpolate(compactLayoutProgress.value, [0, 1], [0, compactTranscriptLayout ? -78 : 0]),
    }],
  }));

  const portraitTransitionStyle = useAnimatedStyle(() => {
    const size = interpolate(subtitlesProgress.value, [0, 1], [250, compactTranscriptLayout ? 78 : 112]);
    return {
      width: size,
      height: size,
      borderRadius: size / 2,
    };
  });

  const listeningTransitionStyle = useAnimatedStyle(() => ({
    marginTop: interpolate(subtitlesProgress.value, [0, 1], [40, compactTranscriptLayout ? 2 : 8]),
  }));

  const transcriptTransitionStyle = useAnimatedStyle(() => ({
    opacity: transcriptVisibility.value,
    transform: [{
      translateY:
        interpolate(transcriptVisibility.value, [0, 1], [24, 0])
        + interpolate(compactLayoutProgress.value, [0, 1], [0, compactTranscriptLayout ? -78 : 0]),
    }],
  }));

  return (
    <View style={styles.callExperience}>
      <View style={styles.callStage}>
        <Animated.View style={[styles.callPresence, presenceTransitionStyle]}>
          <Animated.View style={[styles.callPortrait, portraitTransitionStyle]}>
            <Image
              source={teacher.image}
              style={styles.callTeacherImage}
              contentFit="contain"
            />
          </Animated.View>
          <Animated.View style={[styles.listeningState, listeningTransitionStyle]}>
            <VoiceWaveform active={!muted} compact={subtitles} />
            <Text style={styles.timer}>{muted ? `已暂停 · ${formatDuration(elapsed)}` : formatDuration(elapsed)}</Text>
            {!subtitles ? <Text style={styles.callStatus}>{muted ? '会话已暂停' : '可以开始说了'}</Text> : null}
          </Animated.View>
        </Animated.View>
        <Animated.View
          accessibilityElementsHidden={!subtitles}
          importantForAccessibility={subtitles ? 'auto' : 'no-hide-descendants'}
          pointerEvents={subtitles ? 'auto' : 'none'}
          style={[styles.transcript, compactTranscriptLayout && styles.transcriptCompact, transcriptTransitionStyle]}
        >
            <Text style={styles.speaker}>{teacher.name}</Text>
            <Text style={[styles.transcriptEnglish, compactTranscriptLayout && styles.transcriptEnglishCompact]}>{transcriptEnglish}</Text>
            <Pressable accessibilityRole="button" accessibilityLabel={translated ? '收起翻译' : '翻译'} onPress={() => setTranslated((current) => !current)} style={styles.translate}>
              <AppIcon name="translate" size={14} color={colors.subtle} />
              <Text style={styles.translateText}>{translated ? '收起翻译' : '翻译'}</Text>
            </Pressable>
            {translated ? <Text style={styles.translation}>{transcriptChinese}</Text> : null}
        </Animated.View>
      </View>
      <View style={[styles.callControls, compactTranscriptLayout && styles.callControlsCompact]}>
        <Pressable accessibilityRole="button" accessibilityLabel={muted ? '恢复会话' : '暂停会话'} onPress={() => setMuted((current) => !current)} style={[styles.callControl, muted && styles.callControlActive]}>
          {muted ? <MicrophoneSlashIcon size={24} color={colors.ink} /> : <MicrophoneIcon size={24} color={colors.ink} />}
        </Pressable>
        {allowSubtitleToggle ? (
          <Pressable accessibilityRole="button" accessibilityLabel={subtitles ? '关闭字幕' : '打开字幕'} onPress={() => setSubtitles((current) => !current)} style={[styles.callControl, subtitles && styles.callControlActive]}>
            <SubtitlesIcon size={24} color={colors.ink} />
          </Pressable>
        ) : null}
        <Pressable accessibilityRole="button" accessibilityLabel="结束当前会话" onPress={onEnd} style={[styles.callControl, styles.endControl]}>
          <PhoneDisconnectIcon size={24} color={colors.white} weight="fill" />
        </Pressable>
      </View>
    </View>
  );
}

export function CallScreen({ onEnd }: { onEnd: () => void }) {
  return (
    <SafeAreaView edges={['top', 'bottom']} style={styles.callScreen}>
      <CallExperience onEnd={onEnd} />
    </SafeAreaView>
  );
}

export function ConversationScreen({
  onImmersiveChange,
  onStartCall,
}: {
  onImmersiveChange?: (immersive: boolean) => void;
  onStartCall?: () => void;
}) {
  const {
    nickname,
    teacher,
    speed,
    level,
    setSpeed,
    setLevel,
    setTeacher,
  } = useAppModel();
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [inCall, setInCall] = useState(false);
  const [callTransitioning, setCallTransitioning] = useState(false);
  const settingsInteractionCount = useRef(0);
  const settingsRotation = useSharedValue(0);
  const callTransitionProgress = useSharedValue(0);

  const settingsIconStyle = useAnimatedStyle(() => ({
    transform: [{ rotate: `${settingsRotation.value}deg` }],
  }));

  const startSettingsRotation = () => {
    settingsInteractionCount.current += 1;
    if (settingsInteractionCount.current !== 1) return;
    settingsRotation.value = 0;
    settingsRotation.value = withRepeat(
      withTiming(360, { duration: 2000, easing: Easing.linear }),
      -1,
      false,
    );
  };

  const stopSettingsRotation = () => {
    settingsInteractionCount.current = Math.max(0, settingsInteractionCount.current - 1);
    if (settingsInteractionCount.current !== 0) return;
    cancelAnimation(settingsRotation);
    settingsRotation.value = 0;
  };

  useEffect(() => () => cancelAnimation(settingsRotation), [settingsRotation]);

  useEffect(() => () => cancelAnimation(callTransitionProgress), [callTransitionProgress]);

  useEffect(() => {
    onImmersiveChange?.(inCall);
    return () => onImmersiveChange?.(false);
  }, [inCall, onImmersiveChange]);

  const homeTransitionStyle = useAnimatedStyle(() => ({
    opacity: interpolate(callTransitionProgress.value, [0, 0.58, 1], [1, 1, 0]),
  }));

  const portraitTransitionStyle = useAnimatedStyle(() => {
    const size = interpolate(callTransitionProgress.value, [0, 1], [212, 112]);
    return {
      width: size,
      height: size,
      borderRadius: size / 2,
      transform: [{ translateY: interpolate(callTransitionProgress.value, [0, 1], [0, -300]) }],
    };
  });

  const startCall = () => {
    if (onStartCall) {
      onStartCall();
      return;
    }
    setCallTransitioning(true);
    callTransitionProgress.value = withTiming(1, {
      duration: 620,
      easing: Easing.inOut(Easing.cubic),
    }, (finished) => {
      if (finished) runOnJS(setInCall)(true);
    });
  };

  if (inCall) {
    return (
      <CallScreen
        onEnd={() => {
          setInCall(false);
          setCallTransitioning(false);
          callTransitionProgress.value = 0;
        }}
      />
    );
  }

  return (
    <>
      <Animated.View pointerEvents={callTransitioning ? 'none' : 'auto'} style={[styles.homeContainer, homeTransitionStyle]}>
        <AppScreen scrollEnabled={false} contentStyle={styles.content}>
          <View style={styles.brandHeader}>
          <Brand />
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="对话设置"
            onHoverIn={startSettingsRotation}
            onHoverOut={stopSettingsRotation}
            onPressIn={startSettingsRotation}
            onPressOut={stopSettingsRotation}
            onPress={() => setSettingsOpen(true)}
            style={styles.settingsButton}
          >
            <Animated.View testID="settings-gear" style={settingsIconStyle}>
              <GearSixIcon color="#666662" size={17} weight="bold" />
            </Animated.View>
            <Text style={styles.settingsLabel}>对话设置</Text>
          </Pressable>
          </View>
          <View>
            <Text style={styles.greeting}>晚上好，{nickname}</Text>
            <Text style={styles.greetingCopy}>今天也来开口说英语吧，{'\n'}每一次练习，都是进步。</Text>
          </View>
          <Animated.View style={styles.conversationModule}>
            <Animated.View style={[styles.portrait, portraitTransitionStyle]}>
              <Image source={teacher.image} style={styles.teacherImage} contentFit="contain" />
            </Animated.View>
            <Text style={styles.eyebrow}>{teacher.name.toUpperCase()} · {teacher.accent}</Text>
            <Text style={styles.moduleTitle}>想聊什么都可以</Text>
            <Text style={styles.moduleSubtitle}>像打电话一样自然开口</Text>
            <AppButton
              title="开始对话"
              variant="primary"
              icon="arrow-right"
              onPress={startCall}
              style={styles.startButton}
            />
            <View style={styles.privacy}>
              <AppIcon name="lock" size={14} color={colors.subtle} />
              <Text style={styles.privacyText}>自由对话内容不会保存</Text>
            </View>
          </Animated.View>
        </AppScreen>
      </Animated.View>
      <ConversationSettings
        open={settingsOpen}
        speed={speed}
        level={level}
        teacher={teacher}
        onClose={() => setSettingsOpen(false)}
        onSave={(settings) => {
          setSpeed(settings.speed);
          setLevel(settings.level);
          setTeacher(settings.teacher);
          setSettingsOpen(false);
        }}
      />
    </>
  );
}

const styles = StyleSheet.create({
  content: { paddingHorizontal: 18, paddingTop: 22, paddingBottom: 84, gap: 24 },
  brandHeader: { minHeight: 44, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  settingsButton: { height: 36, paddingHorizontal: 12, flexDirection: 'row', alignItems: 'center', gap: 7, borderRadius: 18, backgroundColor: '#F0F0ED' },
  settingsLabel: { color: '#666662', fontSize: 12, fontWeight: '300' },
  greeting: { color: colors.ink, fontSize: 31, lineHeight: 38, fontWeight: '600', letterSpacing: -1.2 },
  greetingCopy: { marginTop: 8, color: colors.muted, fontSize: 16, lineHeight: 23, fontWeight: '300' },
  conversationModule: {
    minHeight: 536,
    paddingHorizontal: 22,
    paddingVertical: 26,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#E1E0DA',
    borderRadius: 24,
    backgroundColor: colors.white,
    shadowColor: '#1A1A18',
    shadowOffset: { width: 0, height: 5 },
    shadowOpacity: 0.045,
    shadowRadius: 15,
    elevation: 2,
    boxShadow: '0px 5px 18px rgba(21, 21, 20, 0.045)',
  },
  portrait: { width: 212, height: 212, overflow: 'hidden', alignItems: 'center', justifyContent: 'flex-end', borderWidth: 1, borderColor: '#EDEDE9', borderRadius: 106, backgroundColor: colors.soft },
  teacherImage: { width: 212, height: 250, marginBottom: -29 },
  eyebrow: { marginTop: 20, color: colors.subtle, fontSize: 11, fontWeight: '500', letterSpacing: 2.1 },
  moduleTitle: { marginTop: 13, color: colors.ink, fontSize: 29, lineHeight: 37, fontWeight: '600', letterSpacing: -1.3 },
  moduleSubtitle: { marginTop: 4, color: colors.muted, fontSize: 16, fontWeight: '300' },
  startButton: {
    marginTop: 20,
    paddingHorizontal: 24,
    borderColor: colors.ink,
    borderRadius: 24,
    backgroundColor: colors.ink,
    shadowColor: colors.ink,
    shadowOffset: { width: 0, height: 7 },
    shadowOpacity: 0.18,
    shadowRadius: 15,
    elevation: 5,
    boxShadow: '0px 7px 18px rgba(21, 21, 20, 0.18)',
  },
  privacy: { marginTop: 20, flexDirection: 'row', alignItems: 'center', gap: 7 },
  privacyText: { color: colors.subtle, fontSize: 11, fontWeight: '300' },
  homeContainer: { flex: 1 },
  callScreen: { flex: 1, paddingHorizontal: 22, paddingTop: 24, paddingBottom: 22, backgroundColor: colors.white },
  callExperience: { flex: 1 },
  timer: { marginTop: 7, color: colors.subtle, fontSize: 12, fontWeight: '300', fontVariant: ['tabular-nums'] },
  callStage: { flex: 1, position: 'relative', alignItems: 'center' },
  callPresence: { position: 'absolute', top: 0, left: 0, right: 0, alignItems: 'center' },
  callPortrait: { overflow: 'hidden', alignItems: 'center', justifyContent: 'flex-end', borderWidth: 1, borderColor: '#EDEDE9', backgroundColor: colors.soft },
  callTeacherImage: { position: 'absolute', bottom: '-14%', width: '100%', height: '118%' },
  listeningState: { alignItems: 'center' },
  voiceWave: { width: 60, height: 34, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 4 },
  voiceWaveCompact: { width: 27, height: 14, gap: 1.5 },
  voiceWaveBar: { width: 4, height: 30, borderRadius: 999, backgroundColor: '#969692' },
  voiceWaveBarCompact: { width: 1.5, height: 12 },
  voiceWaveBarInactive: { opacity: 0.48 },
  callStatus: { marginTop: 13, color: colors.muted, fontSize: 14, fontWeight: '300' },
  transcript: { position: 'absolute', top: 220, left: 0, right: 0, bottom: 0, paddingHorizontal: 8, paddingTop: 12 },
  transcriptCompact: { top: 126, paddingHorizontal: 2, paddingTop: 18 },
  speaker: { color: colors.subtle, fontSize: 13, fontWeight: '300' },
  transcriptEnglish: { marginTop: 10, maxWidth: 350, color: colors.ink, fontSize: 24, lineHeight: 34, fontWeight: '300', letterSpacing: -0.6 },
  transcriptEnglishCompact: { maxWidth: 380, fontSize: 27, lineHeight: 38, letterSpacing: -0.8 },
  translate: { marginTop: 8, flexDirection: 'row', alignItems: 'center', gap: 5 },
  translateText: { color: colors.subtle, fontSize: 12, fontWeight: '300' },
  translation: { marginTop: 8, color: colors.muted, fontSize: 13, lineHeight: 20, fontWeight: '300' },
  callControls: { paddingTop: 12, flexDirection: 'row', justifyContent: 'center', gap: 14 },
  callControlsCompact: { paddingTop: 8 },
  callControl: { width: 64, height: 64, alignItems: 'center', justifyContent: 'center', borderWidth: 1, borderColor: colors.line, borderRadius: 32, backgroundColor: colors.white },
  callControlActive: { borderColor: '#D2D2CD', backgroundColor: '#E9E9E5' },
  endControl: { borderColor: '#171716', backgroundColor: '#171716' },
});
