import MaterialIcons from '@expo/vector-icons/MaterialIcons';
import { Image } from 'expo-image';
import { SymbolView, type AndroidSymbol, type SFSymbol } from 'expo-symbols';
import type { ComponentProps, PropsWithChildren, ReactNode } from 'react';
import {
  Pressable,
  Platform,
  ScrollView,
  StyleSheet,
  Text,
  View,
  type ColorValue,
  type StyleProp,
  type ViewStyle,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { brandAssets, colors } from '@/theme/tokens';

type IconName =
  | 'arrow-left'
  | 'arrow-right'
  | 'book'
  | 'briefcase'
  | 'check'
  | 'check-circle'
  | 'chevron-right'
  | 'close'
  | 'crown'
  | 'delete'
  | 'document'
  | 'edit'
  | 'grid'
  | 'lock'
  | 'logout'
  | 'medal'
  | 'microphone'
  | 'microphone-off'
  | 'pause'
  | 'phone-end'
  | 'play'
  | 'settings'
  | 'sliders'
  | 'subtitles'
  | 'translate'
  | 'trophy'
  | 'upload'
  | 'user'
  | 'volume'
  | 'volume-off'
  | 'chat';

const symbolNames: Record<IconName, { ios: SFSymbol; android: AndroidSymbol }> = {
  'arrow-left': { ios: 'arrow.left', android: 'arrow_back' },
  'arrow-right': { ios: 'arrow.right', android: 'arrow_forward' },
  book: { ios: 'book.closed', android: 'book_2' },
  briefcase: { ios: 'briefcase', android: 'work' },
  check: { ios: 'checkmark', android: 'check' },
  'check-circle': { ios: 'checkmark.circle.fill', android: 'check_circle' },
  'chevron-right': { ios: 'chevron.right', android: 'chevron_right' },
  close: { ios: 'xmark', android: 'close' },
  crown: { ios: 'crown', android: 'crown' },
  delete: { ios: 'trash', android: 'delete' },
  document: { ios: 'doc.text', android: 'description' },
  edit: { ios: 'pencil', android: 'edit' },
  grid: { ios: 'square.grid.2x2', android: 'grid_view' },
  lock: { ios: 'lock', android: 'lock' },
  logout: { ios: 'rectangle.portrait.and.arrow.right', android: 'logout' },
  medal: { ios: 'medal', android: 'military_tech' },
  microphone: { ios: 'mic.fill', android: 'mic' },
  'microphone-off': { ios: 'mic.slash.fill', android: 'mic_off' },
  pause: { ios: 'pause.fill', android: 'pause' },
  'phone-end': { ios: 'phone.down.fill', android: 'call_end' },
  play: { ios: 'play.fill', android: 'play_arrow' },
  settings: { ios: 'gearshape', android: 'settings' },
  sliders: { ios: 'slider.horizontal.3', android: 'tune' },
  subtitles: { ios: 'captions.bubble', android: 'subtitles' },
  translate: { ios: 'character.book.closed', android: 'translate' },
  trophy: { ios: 'trophy', android: 'emoji_events' },
  upload: { ios: 'arrow.up.doc', android: 'upload' },
  user: { ios: 'person', android: 'person' },
  volume: { ios: 'speaker.wave.2.fill', android: 'volume_up' },
  'volume-off': { ios: 'speaker.slash.fill', android: 'volume_off' },
  chat: { ios: 'bubble.left.and.bubble.right', android: 'chat' },
};

const materialNames: Record<IconName, ComponentProps<typeof MaterialIcons>['name']> = {
  'arrow-left': 'arrow-back',
  'arrow-right': 'arrow-forward',
  book: 'book',
  briefcase: 'work',
  check: 'check',
  'check-circle': 'check-circle',
  'chevron-right': 'chevron-right',
  close: 'close',
  crown: 'workspace-premium',
  delete: 'delete',
  document: 'description',
  edit: 'edit',
  grid: 'grid-view',
  lock: 'lock',
  logout: 'logout',
  medal: 'military-tech',
  microphone: 'mic',
  'microphone-off': 'mic-off',
  pause: 'pause',
  'phone-end': 'call-end',
  play: 'play-arrow',
  settings: 'settings',
  sliders: 'tune',
  subtitles: 'subtitles',
  translate: 'translate',
  trophy: 'emoji-events',
  upload: 'upload',
  user: 'person',
  volume: 'volume-up',
  'volume-off': 'volume-off',
  chat: 'chat',
};

export function AppIcon({
  name,
  size = 22,
  color = colors.ink,
}: {
  name: IconName;
  size?: number;
  color?: ColorValue;
}) {
  if (Platform.OS !== 'ios') {
    return (
      <MaterialIcons
        name={materialNames[name]}
        size={size}
        color={color}
      />
    );
  }

  return (
    <SymbolView
      name={symbolNames[name]}
      size={size}
      tintColor={color}
      style={{ width: size, height: size }}
    />
  );
}

export function Brand({ compact = false }: { compact?: boolean }) {
  return (
    <View style={styles.brand}>
      <Image source={brandAssets.mark} style={compact ? styles.brandMarkCompact : styles.brandMark} />
      {!compact ? <Image source={brandAssets.wordmark} style={styles.wordmark} contentFit="contain" /> : null}
    </View>
  );
}

export function AppScreen({
  children,
  contentStyle,
  scrollEnabled = true,
}: PropsWithChildren<{ contentStyle?: StyleProp<ViewStyle>; scrollEnabled?: boolean }>) {
  return (
    <SafeAreaView edges={['top']} style={styles.screen}>
      <ScrollView
        bounces={scrollEnabled}
        keyboardShouldPersistTaps="handled"
        contentInsetAdjustmentBehavior="automatic"
        contentContainerStyle={[styles.screenContent, contentStyle]}
        overScrollMode={scrollEnabled ? 'auto' : 'never'}
        scrollEnabled={scrollEnabled}
        showsVerticalScrollIndicator={scrollEnabled}
      >
        {children}
      </ScrollView>
    </SafeAreaView>
  );
}

export function PageHeader({
  title,
  subtitle,
  eyebrow,
  onBack,
  action,
}: {
  title: string;
  subtitle?: string;
  eyebrow?: string;
  onBack?: () => void;
  action?: ReactNode;
}) {
  return (
    <View style={styles.header}>
      <View style={styles.headerTop}>
        {onBack ? (
          <Pressable accessibilityRole="button" accessibilityLabel="返回" onPress={onBack} style={styles.iconButton}>
            <AppIcon name="arrow-left" size={20} />
          </Pressable>
        ) : null}
        <View style={styles.headerSpacer} />
        {action}
      </View>
      {eyebrow ? <Text style={styles.eyebrow}>{eyebrow}</Text> : null}
      <Text style={styles.pageTitle}>{title}</Text>
      {subtitle ? <Text style={styles.pageSubtitle}>{subtitle}</Text> : null}
    </View>
  );
}

export function SectionTitle({
  title,
  eyebrow,
  action,
}: {
  title: string;
  eyebrow?: string;
  action?: ReactNode;
}) {
  return (
    <View style={styles.sectionTitle}>
      <View style={styles.flex}>
        {eyebrow ? <Text style={styles.eyebrow}>{eyebrow}</Text> : null}
        <Text style={styles.sectionHeading}>{title}</Text>
      </View>
      {action}
    </View>
  );
}

export function AppButton({
  title,
  variant = 'primary',
  icon,
  onPress,
  disabled,
  style,
}: {
  title: string;
  variant?: 'primary' | 'secondary' | 'soft' | 'danger';
  icon?: IconName;
  onPress?: () => void;
  disabled?: boolean;
  style?: StyleProp<ViewStyle>;
}) {
  const primary = variant === 'primary';
  return (
    <Pressable
      accessibilityRole="button"
      disabled={disabled}
      onPress={onPress}
      style={({ pressed }) => [
        styles.button,
        styles[`button_${variant}`],
        pressed && styles.pressed,
        disabled && styles.disabled,
        style,
      ]}
    >
      <Text style={[styles.buttonLabel, primary && styles.buttonLabelPrimary, variant === 'danger' && styles.buttonLabelDanger]}>
        {title}
      </Text>
      {icon ? <AppIcon name={icon} size={18} color={primary ? colors.white : variant === 'danger' ? colors.red : colors.ink} /> : null}
    </Pressable>
  );
}

export function Card({
  children,
  onPress,
  style,
}: PropsWithChildren<{ onPress?: () => void; style?: StyleProp<ViewStyle> }>) {
  if (!onPress) return <View style={[styles.card, style]}>{children}</View>;
  return (
    <Pressable onPress={onPress} style={({ pressed }) => [styles.card, style, pressed && styles.pressed]}>
      {children}
    </Pressable>
  );
}

export function Pill({ children, dark = false }: PropsWithChildren<{ dark?: boolean }>) {
  return <Text style={[styles.pill, dark && styles.pillDark]}>{children}</Text>;
}

export function ListRow({
  title,
  subtitle,
  meta,
  icon,
  onPress,
  danger = false,
}: {
  title: string;
  subtitle?: string;
  meta?: string;
  icon?: IconName;
  onPress?: () => void;
  danger?: boolean;
}) {
  return (
    <Pressable onPress={onPress} style={({ pressed }) => [styles.listRow, pressed && styles.pressed]}>
      {icon ? (
        <View style={[styles.listIcon, danger && styles.listIconDanger]}>
          <AppIcon name={icon} size={20} color={danger ? colors.red : colors.ink} />
        </View>
      ) : null}
      <View style={styles.flex}>
        <Text style={[styles.listTitle, danger && styles.dangerText]}>{title}</Text>
        {subtitle ? <Text style={styles.listSubtitle}>{subtitle}</Text> : null}
      </View>
      {meta ? <Text style={styles.listMeta}>{meta}</Text> : null}
      {onPress ? <AppIcon name="chevron-right" size={18} color={colors.subtle} /> : null}
    </Pressable>
  );
}

export function ProgressBar({ value, max = 100 }: { value: number; max?: number }) {
  return (
    <View style={styles.progressTrack}>
      <View style={[styles.progressFill, { width: `${Math.min(100, (value / max) * 100)}%` }]} />
    </View>
  );
}

export function Metric({ label, value, suffix }: { label: string; value: string | number; suffix?: string }) {
  return (
    <View style={styles.metric}>
      <Text style={styles.metricLabel}>{label}</Text>
      <Text style={styles.metricValue}>
        {value}
        {suffix ? <Text style={styles.metricSuffix}> {suffix}</Text> : null}
      </Text>
    </View>
  );
}

export const uiStyles = StyleSheet.create({
  body: { color: colors.ink, fontSize: 15, lineHeight: 23, fontWeight: '300' },
  muted: { color: colors.muted, fontSize: 13, lineHeight: 20, fontWeight: '300' },
  title: { color: colors.ink, fontSize: 20, fontWeight: '500' },
  row: { flexDirection: 'row', alignItems: 'center' },
  stack: { gap: 10 },
  flex: { flex: 1 },
});

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.canvas },
  screenContent: { flexGrow: 1, paddingHorizontal: 22, paddingTop: 18, paddingBottom: 132, gap: 22 },
  brand: { flexDirection: 'row', alignItems: 'center', gap: 11 },
  brandMark: { width: 42, height: 42, borderRadius: 12 },
  brandMarkCompact: { width: 38, height: 38, borderRadius: 11 },
  wordmark: { width: 142, height: 24 },
  header: { gap: 7 },
  headerTop: { minHeight: 44, flexDirection: 'row', alignItems: 'center' },
  headerSpacer: { flex: 1 },
  iconButton: {
    width: 44,
    height: 44,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1,
    borderColor: colors.line,
    borderRadius: 22,
    backgroundColor: colors.white,
  },
  eyebrow: { color: colors.subtle, fontSize: 11, fontWeight: '500', letterSpacing: 1.7 },
  pageTitle: { color: colors.ink, fontSize: 31, lineHeight: 38, fontWeight: '600', letterSpacing: -1.2 },
  pageSubtitle: { color: colors.muted, fontSize: 15, lineHeight: 23, fontWeight: '300' },
  sectionTitle: { flexDirection: 'row', alignItems: 'flex-end', gap: 12 },
  sectionHeading: { marginTop: 5, color: colors.ink, fontSize: 21, fontWeight: '600', letterSpacing: -0.6 },
  flex: { flex: 1 },
  button: {
    minHeight: 48,
    paddingHorizontal: 20,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 9,
    borderWidth: 1,
    borderRadius: 12,
  },
  button_primary: { borderColor: colors.ink, backgroundColor: colors.ink },
  button_secondary: { borderColor: colors.line, backgroundColor: colors.white },
  button_soft: { borderColor: colors.soft, backgroundColor: colors.soft },
  button_danger: { borderColor: colors.redSoft, backgroundColor: colors.redSoft },
  buttonLabel: { color: colors.ink, fontSize: 15, fontWeight: '500' },
  buttonLabelPrimary: { color: colors.white },
  buttonLabelDanger: { color: colors.red },
  pressed: { opacity: 0.78, transform: [{ scale: 0.98 }] },
  disabled: { opacity: 0.42 },
  card: {
    padding: 17,
    gap: 10,
    borderWidth: 1,
    borderColor: colors.line,
    borderRadius: 18,
    backgroundColor: colors.white,
    shadowColor: '#1A1A18',
    shadowOffset: { width: 0, height: 5 },
    shadowOpacity: 0.045,
    shadowRadius: 14,
    elevation: 2,
    boxShadow: '0px 5px 16px rgba(21, 21, 20, 0.045)',
  },
  pill: {
    alignSelf: 'flex-start',
    paddingVertical: 6,
    paddingHorizontal: 10,
    overflow: 'hidden',
    color: colors.muted,
    fontSize: 10,
    fontWeight: '500',
    borderRadius: 999,
    backgroundColor: colors.soft,
  },
  pillDark: { color: colors.white, backgroundColor: colors.ink },
  listRow: {
    minHeight: 72,
    paddingVertical: 12,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: colors.line,
  },
  listIcon: {
    width: 42,
    height: 42,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 12,
    backgroundColor: colors.soft,
  },
  listIconDanger: { backgroundColor: colors.redSoft },
  listTitle: { color: colors.ink, fontSize: 15, fontWeight: '500' },
  listSubtitle: { marginTop: 4, color: colors.muted, fontSize: 12, lineHeight: 18, fontWeight: '300' },
  listMeta: { color: colors.subtle, fontSize: 11, fontWeight: '300' },
  dangerText: { color: colors.red },
  progressTrack: { height: 6, overflow: 'hidden', borderRadius: 3, backgroundColor: '#E9E9E5' },
  progressFill: { height: '100%', borderRadius: 3, backgroundColor: colors.ink },
  metric: {
    flex: 1,
    padding: 14,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: '#E7E7E2',
    borderRadius: 15,
    backgroundColor: colors.white,
    shadowColor: '#1A1A18',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.04,
    shadowRadius: 11,
    elevation: 2,
    boxShadow: '0px 4px 13px rgba(21, 21, 20, 0.04)',
  },
  metricLabel: { color: colors.muted, fontSize: 10, fontWeight: '300' },
  metricValue: { marginTop: 7, color: colors.ink, fontSize: 21, fontWeight: '600' },
  metricSuffix: { color: colors.muted, fontSize: 10, fontWeight: '300' },
});

export type AppButtonProps = ComponentProps<typeof AppButton>;
