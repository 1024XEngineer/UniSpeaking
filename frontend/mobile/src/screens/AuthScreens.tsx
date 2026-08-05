import { ArrowRightIcon } from 'phosphor-react-native/src/icons/ArrowRight';
import { type ReactNode, useEffect, useRef, useState } from 'react';
import {
  AccessibilityInfo,
  Animated,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  type StyleProp,
  type ViewStyle,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { AppButton, AppIcon, Brand } from '@/components/ui';
import { TeacherSwipeStack } from '@/components/TeacherSwipeStack';
import { useAppModel } from '@/model/AppModel';
import { colors, levels } from '@/theme/tokens';

function AnimatedSloganLine({ text, delay }: { text: string; delay: number }) {
  const characters = Array.from(text);
  const progress = useRef(characters.map(() => new Animated.Value(0))).current;

  useEffect(() => {
    let mounted = true;
    void AccessibilityInfo.isReduceMotionEnabled().then((reduceMotion) => {
      if (!mounted) return;
      if (reduceMotion) {
        progress.forEach((value) => value.setValue(1));
        return;
      }
      Animated.sequence([
        Animated.delay(delay),
        Animated.stagger(
          34,
          progress.map((value) =>
            Animated.spring(value, {
              toValue: 1,
              damping: 15,
              stiffness: 145,
              mass: 0.72,
              useNativeDriver: true,
            }),
          ),
        ),
      ]).start();
    });
    return () => {
      mounted = false;
      progress.forEach((value) => value.stopAnimation());
    };
  }, [delay, progress]);

  return (
    <View accessibilityLabel={text} style={styles.sloganLine}>
      {characters.map((character, index) => (
        <Animated.Text
          accessibilityElementsHidden
          key={`${character}-${index}`}
          style={[
            styles.sloganCharacter,
            {
              opacity: progress[index],
              transform: [
                { translateY: progress[index].interpolate({ inputRange: [0, 1], outputRange: [28, 0] }) },
              ],
            },
          ]}
        >
          {character === ' ' ? '\u00A0' : character}
        </Animated.Text>
      ))}
    </View>
  );
}

function OnboardingCta({
  title,
  onPress,
  style,
}: {
  title: string;
  onPress: () => void;
  style?: StyleProp<ViewStyle>;
}) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={title}
      onPress={onPress}
      style={({ pressed }) => [styles.onboardingCta, pressed && styles.onboardingCtaPressed, style]}
    >
      <View pointerEvents="none" style={styles.onboardingCtaIcon}>
        <ArrowRightIcon color={colors.ink} size={21} weight="bold" />
      </View>
    </Pressable>
  );
}

function OnboardingFooter({
  title,
  onPress,
}: {
  title: string;
  onPress: () => void;
}) {
  return (
    <View style={styles.onboardingFooter}>
      <OnboardingCta title={title} onPress={onPress} />
    </View>
  );
}

function AuthHeader({ title, onBack }: { title: string; onBack: () => void }) {
  return (
    <View style={styles.authHeader}>
      <Pressable accessibilityRole="button" accessibilityLabel="返回" onPress={onBack} style={styles.backButton}>
        <AppIcon name="arrow-left" size={20} />
      </Pressable>
      <Text style={styles.authHeaderTitle}>{title}</Text>
      <View style={styles.headerBalance} />
    </View>
  );
}

function AuthPage({
  children,
  header,
}: {
  children: ReactNode;
  header?: ReactNode;
}) {
  return (
    <SafeAreaView edges={['top']} style={styles.safeArea}>
      {header}
      <KeyboardAvoidingView style={styles.flex} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        <ScrollView
          keyboardShouldPersistTaps="handled"
          contentContainerStyle={styles.authContent}
        >
          {children}
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

export function WelcomeScreen({
  onLogin,
  onSignup,
}: {
  onLogin: () => void;
  onSignup: () => void;
}) {
  return (
    <SafeAreaView edges={['top', 'bottom']} style={styles.welcome}>
      <Brand />
      <View style={styles.welcomeMain}>
        <Text style={styles.eyebrow}>AI ENGLISH SPEAKING PARTNER</Text>
        <View style={styles.welcomeTitle}>
          <AnimatedSloganLine text="Speak More," delay={120} />
          <AnimatedSloganLine text="Speak Better." delay={410} />
        </View>
        <Text style={styles.welcomeChinese}>越说，越会说。</Text>
        <View style={styles.welcomeActions}>
          <AppButton title="登录" onPress={onLogin} />
          <AppButton title="注册" variant="secondary" onPress={onSignup} />
        </View>
      </View>
    </SafeAreaView>
  );
}

export function AuthFormScreen({
  mode,
  onBack,
  onSwitch,
}: {
  mode: 'login' | 'signup';
  onBack: () => void;
  onSwitch: () => void;
}) {
  const { nickname, setNickname, signIn, signUp } = useAppModel();
  const [draftNickname, setDraftNickname] = useState(nickname);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [submitted, setSubmitted] = useState(false);
  const emailValid = /^\S+@\S+\.\S+$/.test(email.trim());
  const passwordValid = password.length >= 8;
  const nicknameValid = mode === 'login' || draftNickname.trim().length >= 2;
  const valid = emailValid && passwordValid && nicknameValid;

  const submit = () => {
    setSubmitted(true);
    if (!valid) return;
    if (mode === 'signup') {
      setNickname(draftNickname.trim());
      signUp();
      return;
    }
    signIn();
  };

  return (
    <AuthPage header={<AuthHeader title={mode === 'login' ? '登录' : '创建账号'} onBack={onBack} />}>
      <View style={styles.heading}>
        <Text style={styles.authTitle}>{mode === 'login' ? '欢迎回来' : '创建账号'}</Text>
        <Text style={styles.authSubtitle}>
          {mode === 'login' ? '继续上一次的学习进度。' : '用邮箱注册，开始你的口语练习。'}
        </Text>
      </View>

      <View style={styles.form}>
        {mode === 'signup' ? (
          <View style={styles.formGroup}>
            <Text style={styles.fieldLabel}>昵称</Text>
            <TextInput
              value={draftNickname}
              onChangeText={setDraftNickname}
              placeholder="怎么称呼你"
              placeholderTextColor={colors.subtle}
              autoComplete="name"
              style={[styles.input, submitted && !nicknameValid && styles.inputError]}
            />
            {submitted && !nicknameValid ? <Text style={styles.errorText}>昵称至少需要 2 个字符</Text> : null}
          </View>
        ) : null}

        <View style={styles.formGroup}>
          <Text style={styles.fieldLabel}>邮箱</Text>
          <TextInput
            value={email}
            onChangeText={setEmail}
            placeholder="name@example.com"
            placeholderTextColor={colors.subtle}
            keyboardType="email-address"
            autoCapitalize="none"
            autoCorrect={false}
            autoComplete="email"
            style={[styles.input, submitted && !emailValid && styles.inputError]}
          />
          {submitted && !emailValid ? <Text style={styles.errorText}>请输入有效的邮箱地址</Text> : null}
        </View>

        <View style={styles.formGroup}>
          <View style={styles.labelRow}>
            <Text style={styles.fieldLabel}>密码</Text>
            {mode === 'login' ? <Text style={styles.forgotText}>忘记密码？</Text> : null}
          </View>
          <TextInput
            value={password}
            onChangeText={setPassword}
            placeholder="至少 8 位字符"
            placeholderTextColor={colors.subtle}
            secureTextEntry
            autoCapitalize="none"
            autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
            style={[styles.input, submitted && !passwordValid && styles.inputError]}
            onSubmitEditing={submit}
          />
          {submitted && !passwordValid ? <Text style={styles.errorText}>密码至少需要 8 位字符</Text> : null}
        </View>
      </View>

      <AppButton
        title={mode === 'login' ? '登录' : '注册并继续'}
        onPress={submit}
        style={styles.fullWidth}
      />

      <Pressable accessibilityRole="button" onPress={onSwitch} style={styles.switchButton}>
        <Text style={styles.switchText}>
          {mode === 'login' ? '还没有账号？' : '已经有账号？'}
          <Text style={styles.switchStrong}>{mode === 'login' ? ' 创建账号' : ' 直接登录'}</Text>
        </Text>
      </Pressable>
    </AuthPage>
  );
}

export function LevelOnboardingScreen({ onNext }: { onNext: () => void }) {
  const { level, setLevel } = useAppModel();
  return (
    <SafeAreaView edges={['top', 'bottom']} style={styles.onboardingPage}>
      <View style={styles.onboardingHeader}>
        <Brand />
        <Text style={styles.stepText}>1 / 2</Text>
      </View>
      <ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={styles.levelContent}>
        <View style={styles.levelIntro}>
          <Text style={styles.eyebrow}>A SIMPLE START</Text>
          <Text style={styles.onboardingTitle}>你现在说英语时，{`\n`}更接近哪种状态？</Text>
          <Text style={styles.onboardingSubtitle}>没有测试，也没有标准答案。这个选择只用于匹配对话难度。</Text>
        </View>
        <View style={styles.levelOptions} accessibilityRole="radiogroup">
          {levels.map((item, index) => {
            const selected = item.id === level;
            return (
              <Pressable
                key={item.id}
                accessibilityRole="radio"
                accessibilityState={{ checked: selected }}
                onPress={() => setLevel(item.id)}
                style={[styles.levelCard, selected && styles.levelCardSelected]}
              >
                <Text style={styles.levelNumber}>0{index + 1}</Text>
                <View style={styles.flex}>
                  <Text style={styles.levelTitle}>{item.title}</Text>
                  <Text style={styles.levelNote}>{item.note}</Text>
                </View>
                {selected ? <AppIcon name="check" size={17} color={colors.ink} /> : <View style={styles.checkSpace} />}
              </Pressable>
            );
          })}
        </View>
      </ScrollView>
      <OnboardingFooter title="下一步" onPress={onNext} />
    </SafeAreaView>
  );
}

export function TeacherOnboardingScreen({ onComplete }: { onComplete: () => void }) {
  const { teacher, setTeacher, completeOnboarding } = useAppModel();
  return (
    <SafeAreaView edges={['top', 'bottom']} style={styles.onboardingPage}>
      <View style={styles.onboardingHeader}>
        <Brand />
        <Text style={styles.stepText}>2 / 2</Text>
      </View>
      <View style={styles.teacherContent}>
        <View style={styles.teacherHeading}>
          <Text style={styles.eyebrow}>CHOOSE YOUR PARTNER</Text>
          <Text style={styles.teacherTitle}>选择一位 AI 老师</Text>
          <Text style={styles.teacherSubtitle}>每位老师都有固定口音和陪练方式，之后可在设置中更换。</Text>
        </View>
        <TeacherSwipeStack selected={teacher} onSelect={setTeacher} />
      </View>
      <OnboardingFooter
        title="选择这位老师"
        onPress={() => {
          completeOnboarding();
          onComplete();
        }}
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  safeArea: { flex: 1, backgroundColor: colors.white },
  authHeader: {
    height: 58,
    paddingHorizontal: 18,
    flexDirection: 'row',
    alignItems: 'center',
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: colors.line,
  },
  backButton: {
    width: 40,
    height: 40,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1,
    borderColor: colors.line,
    borderRadius: 20,
  },
  authHeaderTitle: { flex: 1, color: colors.ink, fontSize: 14, fontWeight: '500', textAlign: 'center' },
  headerBalance: { width: 40 },
  authContent: { flexGrow: 1, paddingHorizontal: 22, paddingTop: 28, paddingBottom: 48, gap: 24 },
  heading: { gap: 8 },
  authTitle: { color: colors.ink, fontSize: 31, lineHeight: 39, fontWeight: '600', letterSpacing: -1.2 },
  authSubtitle: { color: colors.muted, fontSize: 15, lineHeight: 23, fontWeight: '300' },
  form: { gap: 18 },
  formGroup: { gap: 8 },
  labelRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  fieldLabel: { color: colors.ink, fontSize: 13, fontWeight: '500' },
  forgotText: { color: colors.muted, fontSize: 12, fontWeight: '300' },
  input: {
    minHeight: 54,
    paddingHorizontal: 15,
    color: colors.ink,
    fontSize: 15,
    borderWidth: 1,
    borderColor: colors.line,
    borderRadius: 12,
    backgroundColor: colors.white,
  },
  inputError: { borderColor: colors.red },
  errorText: { color: colors.red, fontSize: 11, fontWeight: '300' },
  fullWidth: { width: '100%' },
  switchButton: { minHeight: 44, alignItems: 'center', justifyContent: 'center' },
  switchText: { color: colors.muted, fontSize: 13, fontWeight: '300' },
  switchStrong: { color: colors.ink, fontWeight: '500' },
  welcome: { flex: 1, paddingHorizontal: 24, paddingVertical: 24, backgroundColor: colors.white },
  welcomeMain: { flex: 1, justifyContent: 'center' },
  eyebrow: { color: colors.subtle, fontSize: 10, fontWeight: '500', letterSpacing: 1.7 },
  welcomeTitle: { marginTop: 18 },
  sloganLine: { minHeight: 50, flexDirection: 'row', alignItems: 'baseline', overflow: 'hidden' },
  sloganCharacter: { color: colors.ink, fontSize: 45, lineHeight: 50, fontWeight: '600', letterSpacing: -2.4 },
  welcomeChinese: { marginTop: 16, color: colors.ink, fontSize: 21, fontWeight: '600' },
  welcomeActions: { marginTop: 34, gap: 10 },
  onboardingPage: { flex: 1, paddingHorizontal: 22, backgroundColor: colors.white },
  onboardingHeader: { height: 74, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  levelContent: { flexGrow: 1, paddingTop: 24, paddingBottom: 4 },
  levelIntro: { paddingRight: 5 },
  onboardingTitle: { marginTop: 15, color: colors.ink, fontSize: 35, lineHeight: 43, fontWeight: '600', letterSpacing: -1.6 },
  onboardingSubtitle: { marginTop: 14, maxWidth: 350, color: colors.muted, fontSize: 14, lineHeight: 22, fontWeight: '300' },
  levelOptions: { height: 408, marginTop: 30, justifyContent: 'space-between', gap: 12 },
  levelCard: { minHeight: 90, flex: 1, paddingHorizontal: 18, flexDirection: 'row', alignItems: 'center', gap: 14, borderWidth: 1, borderColor: colors.line, borderRadius: 16, backgroundColor: colors.white },
  levelCardSelected: { borderColor: colors.ink, backgroundColor: colors.paper },
  levelNumber: { width: 30, color: '#A0A09B', fontSize: 12, fontWeight: '300', fontVariant: ['tabular-nums'] },
  levelTitle: { color: colors.ink, fontSize: 17, fontWeight: '500', letterSpacing: -0.2 },
  levelNote: { marginTop: 7, color: colors.muted, fontSize: 12, lineHeight: 17, fontWeight: '300' },
  checkSpace: { width: 17 },
  onboardingFooter: {
    height: 88,
    alignItems: 'center',
    justifyContent: 'flex-start',
    transform: [{ translateY: -32 }],
  },
  teacherContent: {
    flex: 1,
    paddingTop: 24,
    alignItems: 'center',
  },
  teacherHeading: { alignItems: 'center', paddingTop: 2, paddingHorizontal: 4 },
  teacherTitle: { marginTop: 9, color: colors.ink, fontSize: 28, lineHeight: 35, fontWeight: '600', letterSpacing: -1.1 },
  teacherSubtitle: { marginTop: 8, maxWidth: 290, color: colors.muted, fontSize: 12, lineHeight: 18, fontWeight: '300', textAlign: 'center' },

  onboardingCta: {
    position: 'relative',
    width: 62,
    height: 62,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1,
    borderColor: colors.line,
    borderRadius: 31,
    backgroundColor: colors.paper,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.07,
    shadowRadius: 8,
    elevation: 2,
  },
  onboardingCtaPressed: { opacity: 0.72, transform: [{ scale: 0.96 }] },
  onboardingCtaIcon: { position: 'relative', zIndex: 1 },
  stepText: { alignSelf: 'flex-end', color: colors.subtle, fontSize: 11, fontWeight: '300', fontVariant: ['tabular-nums'] },
});
