import { Image } from 'expo-image';
import { GearSixIcon } from 'phosphor-react-native/src/icons/GearSix';
import { MicrophoneIcon } from 'phosphor-react-native/src/icons/Microphone';
import { MicrophoneSlashIcon } from 'phosphor-react-native/src/icons/MicrophoneSlash';
import { PhoneDisconnectIcon } from 'phosphor-react-native/src/icons/PhoneDisconnect';
import { SubtitlesIcon } from 'phosphor-react-native/src/icons/Subtitles';
import { type ComponentProps, useCallback, useEffect, useRef, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
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
import { speedCodeForLabel } from '@/features/auth/preferenceMappings';
import { useFreeChatSession } from '@/features/conversation/useFreeChatSession';
import { createTranscriptTranslationApi } from '@/features/conversation/TranscriptTranslationApi';
import type { RealtimeTranscriptEntry } from '@/features/realtime/RealtimeSessionController';
import type { RealtimeState } from '@/features/realtime/types';
import { useAppModel } from '@/model/AppModel';
import { colors } from '@/theme/tokens';

function formatDuration(total: number) {
  return `${String(Math.floor(total / 60)).padStart(2, '0')}:${String(total % 60).padStart(2, '0')}`;
}

const voiceWaveRestingLevels = [0.28, 0.52, 0.78, 1, 0.72, 0.48, 0.3];

export function selectCallCaption(
  session: {
    state: RealtimeState;
    error: string | { message: string } | null;
    userTranscript: string;
    assistantTranscript: string;
  },
  teacherName: string,
  statusLabel: string,
) {
  if (session.error) {
    return {
      speaker: '系统',
      text: typeof session.error === 'string' ? session.error : session.error.message,
    };
  }
  if (session.state === 'user_speaking') {
    return { speaker: '你', text: session.userTranscript || statusLabel };
  }
  if (session.state === 'assistant_speaking') {
    return {
      speaker: teacherName,
      text: session.assistantTranscript || statusLabel,
    };
  }
  if (session.assistantTranscript) {
    return { speaker: teacherName, text: session.assistantTranscript };
  }
  if (session.userTranscript) {
    return { speaker: '你', text: session.userTranscript };
  }
  return { speaker: teacherName, text: statusLabel };
}

function VoiceWaveBar({ active, compact, index, level, tone }: { active: boolean; compact: boolean; index: number; level: number; tone: 'light' | 'navy' }) {
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
        tone === 'navy' && styles.voiceWaveBarNavy,
        compact && styles.voiceWaveBarCompact,
        !active && styles.voiceWaveBarInactive,
        animatedStyle,
      ]}
    />
  );
}

function VoiceWaveform({ active, compact, tone }: { active: boolean; compact: boolean; tone: 'light' | 'navy' }) {
  return (
    <View accessibilityElementsHidden importantForAccessibility="no-hide-descendants" style={[styles.voiceWave, compact && styles.voiceWaveCompact]}>
      {voiceWaveRestingLevels.map((level, index) => (
        <VoiceWaveBar key={index} active={active} compact={compact} index={index} level={level} tone={tone} />
      ))}
    </View>
  );
}

function TranscriptBubble({
  content,
  owner,
  speaker,
  tone,
  showTranslation,
  fallbackTranslation,
  onTranslate,
}: {
  content: string;
  owner: 0 | 1;
  speaker: string;
  tone: 'light' | 'navy';
  showTranslation: boolean;
  fallbackTranslation?: string;
  onTranslate?: (text: string) => Promise<string>;
}) {
  const [expanded, setExpanded] = useState(false);
  const [translation, setTranslation] = useState(fallbackTranslation ?? '');
  const [translationError, setTranslationError] = useState<string | null>(null);
  const [translating, setTranslating] = useState(false);

  const toggleTranslation = async () => {
    if (expanded) {
      setExpanded(false);
      return;
    }
    if (translation) {
      setExpanded(true);
      return;
    }
    if (!onTranslate || translating) return;
    setTranslating(true);
    setTranslationError(null);
    try {
      const translatedText = await onTranslate(content);
      setTranslation(translatedText);
      setExpanded(true);
    } catch (error) {
      setTranslationError(error instanceof Error ? error.message : '翻译失败，请重试');
    } finally {
      setTranslating(false);
    }
  };

  const isUser = owner === 1;
  return (
    <View style={[styles.messageRow, isUser ? styles.messageRowUser : styles.messageRowAssistant]}>
      <View style={[styles.messageColumn, isUser && styles.messageColumnUser]}>
        <Text style={[styles.speaker, tone === 'navy' && styles.speakerNavy, isUser && styles.speakerUser]}>{speaker}</Text>
        <View style={[
          styles.messageBubble,
          isUser ? styles.userMessageBubble : styles.assistantMessageBubble,
          tone === 'navy' && (isUser ? styles.userMessageBubbleNavy : styles.assistantMessageBubbleNavy),
        ]}>
          <Text style={[styles.messageText, tone === 'navy' && styles.transcriptEnglishNavy]}>{content}</Text>
          {!isUser && showTranslation && (onTranslate || fallbackTranslation) ? (
            <>
              <Pressable
                accessibilityRole="button"
                accessibilityLabel={expanded ? '收起翻译' : '翻译'}
                disabled={translating}
                onPress={() => void toggleTranslation()}
                style={styles.translate}
              >
                <AppIcon name="translate" size={14} color={tone === 'navy' ? '#5D7896' : colors.subtle} />
                <Text style={[styles.translateText, tone === 'navy' && styles.translateTextNavy]}>
                  {translating ? '翻译中…' : expanded ? '收起翻译' : '翻译'}
                </Text>
              </Pressable>
              {expanded && translation ? <Text style={[styles.translation, tone === 'navy' && styles.translationNavy]}>{translation}</Text> : null}
              {translationError ? <Text style={styles.translationError}>{translationError}</Text> : null}
            </>
          ) : null}
        </View>
      </View>
    </View>
  );
}

export function CallExperience({
  onEnd,
  allowSubtitleToggle = true,
  compactTranscriptLayout = false,
  endAccessibilityLabel = '结束当前会话',
  endControlIcon = 'phone',
  initialSubtitles = true,
  participant,
  progressCollapsed = false,
  transcriptSpeaker,
  showMuteControl = true,
  showEndControl = true,
  showTranslationControl = true,
  showUserTranscript = true,
  statusText = '可以开始说了',
  timerRunning = true,
  tone = 'light',
  transcriptEnglish = 'Hi there! How are you feeling today?',
  transcriptChinese = '',
  userTranscript = '',
  transcriptHistory = [],
  elapsed: controlledElapsed,
  muted: controlledMuted,
  statusLabel,
  onMutedChange,
  onTranslate,
}: {
  onEnd: () => void;
  allowSubtitleToggle?: boolean;
  compactTranscriptLayout?: boolean;
  endAccessibilityLabel?: string;
  endControlIcon?: 'phone' | 'arrow';
  initialSubtitles?: boolean;
  participant?: {
    image: ComponentProps<typeof Image>['source'];
    name: string;
  };
  progressCollapsed?: boolean;
  transcriptSpeaker?: string;
  showMuteControl?: boolean;
  showEndControl?: boolean;
  showTranslationControl?: boolean;
  showUserTranscript?: boolean;
  statusText?: string;
  timerRunning?: boolean;
  tone?: 'light' | 'navy';
  transcriptEnglish?: string;
  transcriptChinese?: string;
  userTranscript?: string;
  transcriptHistory?: readonly RealtimeTranscriptEntry[];
  elapsed?: number;
  muted?: boolean;
  statusLabel?: string;
  onMutedChange?: (muted: boolean) => void;
  onTranslate?: (text: string) => Promise<string>;
}) {
  const { teacher } = useAppModel();
  const activeParticipant = participant ?? teacher;
  const [internalElapsed, setInternalElapsed] = useState(0);
  const [internalMuted, setInternalMuted] = useState(false);
  const [subtitles, setSubtitles] = useState(initialSubtitles);
  const transcriptScrollRef = useRef<ScrollView>(null);
  const subtitlesProgress = useSharedValue(initialSubtitles ? 1 : 0);
  const transcriptVisibility = useSharedValue(initialSubtitles ? 1 : 0);
  const compactLayoutProgress = useSharedValue(progressCollapsed ? 1 : 0);
  const elapsed = controlledElapsed ?? internalElapsed;
  const muted = controlledMuted ?? internalMuted;
  const primaryDuplicatesUser =
    transcriptSpeaker === '你' && transcriptEnglish === userTranscript;

  useEffect(() => {
    if (controlledElapsed !== undefined || muted || !timerRunning) return;
    const timer = setInterval(
      () => setInternalElapsed((current) => current + 1),
      1000,
    );
    return () => clearInterval(timer);
  }, [controlledElapsed, muted, timerRunning]);

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
    <View style={[styles.callExperience, tone === 'navy' && styles.callExperienceNavy]}>
      <View style={styles.callStage}>
        <Animated.View style={[styles.callPresence, presenceTransitionStyle]}>
          <Animated.View style={[styles.callPortrait, tone === 'navy' && styles.callPortraitNavy, portraitTransitionStyle]}>
            <Image
              source={activeParticipant.image}
              style={styles.callTeacherImage}
              contentFit="contain"
            />
          </Animated.View>
          <Animated.View style={[styles.listeningState, listeningTransitionStyle]}>
            <VoiceWaveform active={!muted} compact={subtitles} tone={tone} />
            <Text style={[styles.timer, tone === 'navy' && styles.timerNavy]}>{muted ? `已暂停 · ${formatDuration(elapsed)}` : formatDuration(elapsed)}</Text>
            {!subtitles ? <Text style={[styles.callStatus, tone === 'navy' && styles.callStatusNavy]}>{muted ? '会话已暂停' : statusLabel ?? statusText}</Text> : null}
          </Animated.View>
        </Animated.View>
        <Animated.View
          accessibilityElementsHidden={!subtitles}
          importantForAccessibility={subtitles ? 'auto' : 'no-hide-descendants'}
          pointerEvents={subtitles ? 'auto' : 'none'}
          style={[styles.transcript, tone === 'navy' && styles.transcriptNavy, compactTranscriptLayout && styles.transcriptCompact, transcriptTransitionStyle]}
        >
          <ScrollView
            ref={transcriptScrollRef}
            contentContainerStyle={styles.transcriptContent}
            onContentSizeChange={() => transcriptScrollRef.current?.scrollToEnd({ animated: true })}
            showsVerticalScrollIndicator={false}
          >
            {transcriptHistory.filter((entry) => showUserTranscript || entry.owner === 0).map((entry) => (
              <TranscriptBubble
                content={entry.content}
                key={entry.id}
                onTranslate={onTranslate}
                owner={entry.owner}
                showTranslation={showTranslationControl}
                speaker={entry.owner === 1 ? '你' : activeParticipant.name}
                tone={tone}
              />
            ))}
            {showUserTranscript && userTranscript && !transcriptHistory.some((entry) => entry.owner === 1 && entry.content === userTranscript.trim()) ? (
              <TranscriptBubble content={userTranscript} owner={1} showTranslation={false} speaker="你" tone={tone} />
            ) : null}
            {(showUserTranscript || transcriptSpeaker !== '你') &&
            !primaryDuplicatesUser &&
            !transcriptHistory.some((entry) => entry.owner === 0 && entry.content === transcriptEnglish.trim()) ? (
              <TranscriptBubble
                content={transcriptEnglish}
                fallbackTranslation={transcriptChinese}
                onTranslate={onTranslate}
                owner={transcriptSpeaker === '你' ? 1 : 0}
                showTranslation={showTranslationControl}
                speaker={transcriptSpeaker ?? activeParticipant.name}
                tone={tone}
              />
            ) : null}
          </ScrollView>
        </Animated.View>
      </View>
      <View style={[styles.callControls, compactTranscriptLayout && styles.callControlsCompact]}>
        {showMuteControl ? (
          <Pressable accessibilityRole="button" accessibilityLabel={muted ? '恢复会话' : '暂停会话'} onPress={() => {
            const nextMuted = !muted;
            if (onMutedChange) onMutedChange(nextMuted);
            else setInternalMuted(nextMuted);
          }} style={[styles.callControl, tone === 'navy' && styles.callControlNavy, muted && (tone === 'navy' ? styles.callControlActiveNavy : styles.callControlActive)]}>
            {muted ? <MicrophoneSlashIcon size={24} color={tone === 'navy' ? '#123255' : colors.ink} /> : <MicrophoneIcon size={24} color={tone === 'navy' ? '#123255' : colors.ink} />}
          </Pressable>
        ) : null}
        {allowSubtitleToggle ? (
          <Pressable accessibilityRole="button" accessibilityLabel={subtitles ? '关闭字幕' : '打开字幕'} onPress={() => setSubtitles((current) => !current)} style={[styles.callControl, tone === 'navy' && styles.callControlNavy, subtitles && (tone === 'navy' ? styles.callControlActiveNavy : styles.callControlActive)]}>
            <SubtitlesIcon size={24} color={tone === 'navy' ? '#123255' : colors.ink} />
          </Pressable>
        ) : null}
        {showEndControl ? (
          <Pressable accessibilityRole="button" accessibilityLabel={endAccessibilityLabel} onPress={onEnd} style={[styles.callControl, tone === 'navy' && styles.callControlNavy, styles.endControl, tone === 'navy' && styles.endControlNavy]}>
            {endControlIcon === 'arrow' ? <AppIcon name="arrow-right" size={25} color={colors.white} /> : <PhoneDisconnectIcon size={24} color={colors.white} weight="fill" />}
          </Pressable>
        ) : null}
      </View>
    </View>
  );
}

export function CallScreen({ onEnd }: { onEnd: () => void }) {
  const { teacher, speed } = useAppModel();
  const session = useFreeChatSession({
    voice: teacher.voiceId,
    model: 'qwen3.5-omni-flash-realtime',
    speechSpeed: speedCodeForLabel(speed),
  });
  const caption = selectCallCaption(session, teacher.name, session.statusLabel);
  const [translationApi] = useState(createTranscriptTranslationApi);
  const translate = useCallback((text: string) => {
    if (!session.sessionId) return Promise.reject(new Error('会话尚未连接，暂时无法翻译'));
    return translationApi.translateFreeChat(session.sessionId, text);
  }, [session.sessionId, translationApi]);

  return (
    <SafeAreaView edges={['top', 'bottom']} style={styles.callScreen}>
      <CallExperience
        onEnd={() => {
          void session.end().finally(onEnd);
        }}
        elapsed={session.elapsed}
        muted={session.muted}
        statusLabel={session.statusLabel}
        transcriptSpeaker={caption.speaker}
        transcriptEnglish={caption.text}
        userTranscript={session.userTranscript}
        transcriptHistory={session.transcriptHistory}
        onTranslate={translate}
        onMutedChange={() => session.toggleMuted()}
      />
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
    // Reanimated shared values are mutable handles by design.
    // eslint-disable-next-line react-hooks/immutability
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
          // eslint-disable-next-line react-hooks/immutability
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
  content: { paddingHorizontal: 18, paddingTop: 56, paddingBottom: 84, gap: 24 },
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
  callExperienceNavy: { backgroundColor: '#DCEBFA' },
  timer: { marginTop: 7, color: colors.subtle, fontSize: 12, fontWeight: '300', fontVariant: ['tabular-nums'] },
  timerNavy: { color: '#5D7896' },
  callStage: { flex: 1, position: 'relative', alignItems: 'center' },
  callPresence: { position: 'absolute', top: 0, left: 0, right: 0, alignItems: 'center' },
  callPortrait: { overflow: 'hidden', alignItems: 'center', justifyContent: 'flex-end', borderWidth: 1, borderColor: '#EDEDE9', backgroundColor: colors.soft },
  callPortraitNavy: { borderColor: '#83B4DF', backgroundColor: '#C7E0F6' },
  callTeacherImage: { position: 'absolute', bottom: '-14%', width: '100%', height: '118%' },
  listeningState: { alignItems: 'center' },
  voiceWave: { width: 60, height: 34, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 4 },
  voiceWaveCompact: { width: 27, height: 14, gap: 1.5 },
  voiceWaveBar: { width: 4, height: 30, borderRadius: 999, backgroundColor: '#969692' },
  voiceWaveBarNavy: { backgroundColor: '#2875C8' },
  voiceWaveBarCompact: { width: 1.5, height: 12 },
  voiceWaveBarInactive: { opacity: 0.48 },
  callStatus: { marginTop: 13, color: colors.muted, fontSize: 14, fontWeight: '300' },
  callStatusNavy: { color: '#5D7896' },
  transcript: { position: 'absolute', top: 220, left: 0, right: 0, bottom: 0, paddingHorizontal: 8, paddingTop: 12 },
  transcriptNavy: { backgroundColor: '#DCEBFA' },
  transcriptCompact: { top: 126, paddingHorizontal: 2, paddingTop: 18 },
  transcriptContent: { paddingBottom: 16, gap: 12 },
  messageRow: { width: '100%', flexDirection: 'row' },
  messageRowAssistant: { justifyContent: 'flex-start' },
  messageRowUser: { justifyContent: 'flex-end' },
  messageColumn: { maxWidth: '84%', alignItems: 'flex-start' },
  messageColumnUser: { alignItems: 'flex-end' },
  messageBubble: { marginTop: 4, paddingHorizontal: 14, paddingVertical: 10, borderRadius: 8 },
  assistantMessageBubble: { backgroundColor: '#F1F1ED' },
  userMessageBubble: { backgroundColor: '#DCEBFA' },
  assistantMessageBubbleNavy: { backgroundColor: '#F7FBFF' },
  userMessageBubbleNavy: { backgroundColor: '#BEDAF3' },
  messageText: { color: colors.ink, fontSize: 17, lineHeight: 25, fontWeight: '300' },
  speaker: { color: colors.subtle, fontSize: 13, fontWeight: '300' },
  speakerUser: { textAlign: 'right' },
  userTranscriptBlock: { paddingBottom: 14, borderBottomWidth: 1, borderBottomColor: colors.line },
  userTranscriptText: { marginTop: 6, color: colors.muted, fontSize: 18, lineHeight: 26, fontWeight: '300' },
  userTranscriptTextCompact: { fontSize: 19, lineHeight: 27 },
  assistantTranscriptBlock: { paddingTop: 14 },
  speakerNavy: { color: '#5D7896' },
  transcriptEnglish: { marginTop: 10, maxWidth: 350, color: colors.ink, fontSize: 24, lineHeight: 34, fontWeight: '300', letterSpacing: -0.6 },
  transcriptEnglishNavy: { color: '#123255' },
  transcriptEnglishCompact: { maxWidth: 380, fontSize: 27, lineHeight: 38, letterSpacing: -0.8 },
  translate: { marginTop: 8, flexDirection: 'row', alignItems: 'center', gap: 5 },
  translateText: { color: colors.subtle, fontSize: 12, fontWeight: '300' },
  translateTextNavy: { color: '#5D7896' },
  translation: { marginTop: 8, color: colors.muted, fontSize: 13, lineHeight: 20, fontWeight: '300' },
  translationNavy: { color: '#5D7896' },
  translationError: { marginTop: 6, color: '#B94D44', fontSize: 12, lineHeight: 18 },
  callControls: { paddingTop: 12, flexDirection: 'row', justifyContent: 'center', gap: 14 },
  callControlsCompact: { paddingTop: 8 },
  callControl: { width: 64, height: 64, alignItems: 'center', justifyContent: 'center', borderWidth: 1, borderColor: colors.line, borderRadius: 32, backgroundColor: colors.white },
  callControlNavy: { borderColor: '#83B4DF', backgroundColor: '#F7FBFF' },
  callControlActive: { borderColor: '#D2D2CD', backgroundColor: '#E9E9E5' },
  callControlActiveNavy: { borderColor: '#2875C8', backgroundColor: '#C7E0F6' },
  endControl: { borderColor: '#171716', backgroundColor: '#171716' },
  endControlNavy: { borderColor: '#2875C8', backgroundColor: '#2875C8' },
});
