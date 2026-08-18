import { Image } from 'expo-image';
import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import {
  ActivityIndicator,
  Alert,
  Modal,
  Platform,
  Pressable,
  StyleSheet,
  Text,
  TextInput,
  View,
  type ImageSourcePropType,
} from 'react-native';
import { BookOpenTextIcon } from 'phosphor-react-native/src/icons/BookOpenText';
import { CalendarCheckIcon } from 'phosphor-react-native/src/icons/CalendarCheck';
import { ChartLineUpIcon } from 'phosphor-react-native/src/icons/ChartLineUp';
import { ClockIcon } from 'phosphor-react-native/src/icons/Clock';
import { CrownIcon } from 'phosphor-react-native/src/icons/Crown';
import { FireIcon } from 'phosphor-react-native/src/icons/Fire';
import { InfoIcon } from 'phosphor-react-native/src/icons/Info';
import { LifebuoyIcon } from 'phosphor-react-native/src/icons/Lifebuoy';
import { PencilSimpleIcon } from 'phosphor-react-native/src/icons/PencilSimple';
import { ShieldCheckIcon } from 'phosphor-react-native/src/icons/ShieldCheck';
import { SlidersHorizontalIcon } from 'phosphor-react-native/src/icons/SlidersHorizontal';
import { UserCircleIcon } from 'phosphor-react-native/src/icons/UserCircle';
import { XIcon } from 'phosphor-react-native/src/icons/X';

import {
  AppButton,
  AppIcon,
  AppScreen,
  Card,
  ListRow,
  Pill,
  PageHeader,
  ProgressBar,
  SectionTitle,
} from '@/components/ui';
import { LevelSelector, SpeedSelector, TeacherSelector } from '@/components/ConversationSettings';
import { useTeacherPreview } from '@/features/audio/useTeacherPreview';
import {
  ProfileApi,
  type AchievementOverview,
  type HelpArticle as HelpArticleData,
  type HelpCategoryDetail,
  type HelpCenterContent,
  type ProfileAvatar,
  type ProfileInsights,
  type ProfileOverview,
  type WeeklyGoals,
} from '@/features/profile/ProfileApi';
import { SecureTokenStore } from '@/infrastructure/auth/SecureTokenStore';
import { getRuntimeConfig } from '@/infrastructure/config/runtimeConfig';
import { ApiClient } from '@/infrastructure/http/ApiClient';
import { useAppModel } from '@/model/AppModel';
import { brandAssets, colors } from '@/theme/tokens';

export type ProfileRoute = 'home' | 'overview' | 'insights' | 'membership' | 'assistant' | 'account' | 'help' | 'about';

const trainingTypeLabels: Record<string, string> = {
  FREE_CHAT: '自由对话',
  CUSTOM_SCENE: '情景口语',
  IELTS_SCENE: '雅思口语',
};

const dimensionLabels: Record<string, string> = {
  accuracy: '准确度',
  fluency: '流利度',
  grammar: '语法',
  vocabulary: '词汇',
  naturalness: '自然度',
};

function errorMessage(error: unknown, fallback: string) {
  return error instanceof Error ? error.message : fallback;
}

function useProfileApi() {
  const { signOut } = useAppModel();
  return useMemo(() => {
    const tokenStore = new SecureTokenStore();
    return new ProfileApi(
      new ApiClient({
        baseUrl: getRuntimeConfig().backendUrl,
        tokenStore,
        onUnauthorized: signOut,
      }),
    );
  }, [signOut]);
}

function StatCard({
  icon,
  label,
  value,
  suffix,
  onPress,
}: {
  icon: ReactNode;
  label: string;
  value: string | number;
  suffix: string;
  onPress?: () => void;
}) {
  const content = (
    <>
      <View style={styles.statIcon}>{icon}</View>
      <View style={styles.flex}>
        <Text style={styles.statLabel}>{label}</Text>
        <Text style={styles.statValue}>
          {value}
          <Text style={styles.statSuffix}> {suffix}</Text>
        </Text>
      </View>
    </>
  );
  return onPress ? (
    <Pressable
      accessibilityRole="button"
      onPress={onPress}
      style={({ pressed }) => [styles.statCard, pressed && styles.pressed]}
    >
      {content}
    </Pressable>
  ) : (
    <View style={styles.statCard}>{content}</View>
  );
}

function ProfileMenuItem({
  icon,
  title,
  active = false,
  onPress,
}: {
  icon: ReactNode;
  title: string;
  active?: boolean;
  onPress: () => void;
}) {
  return (
    <Pressable
      accessibilityRole="button"
      onPress={onPress}
      style={({ pressed }) => [
        styles.profileMenuItem,
        active && styles.profileMenuItemActive,
        pressed && styles.pressed,
      ]}
    >
      <View style={styles.profileMenuIcon}>{icon}</View>
      <Text style={styles.profileMenuTitle}>{title}</Text>
    </Pressable>
  );
}

function ProfileEditModal({
  avatarUrl,
  fallbackAvatar,
  nickname,
  onClose,
  onSave,
}: {
  avatarUrl: string | null;
  fallbackAvatar: ImageSourcePropType;
  nickname: string;
  onClose: () => void;
  onSave: (nickname: string, avatar: ProfileAvatar | null) => Promise<void>;
}) {
  const [draft, setDraft] = useState(nickname);
  const [avatar, setAvatar] = useState<ProfileAvatar | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  const selectAvatar = async () => {
    let imagePicker: typeof import('expo-image-picker');
    try {
      imagePicker = await import('expo-image-picker');
    } catch {
      setError('当前客户端不支持选择照片，请更新 Expo Go 或使用最新开发构建');
      return;
    }
    const permission = await imagePicker.requestMediaLibraryPermissionsAsync();
    if (!permission.granted) {
      setError('需要允许访问照片后才能选择头像');
      return;
    }
    const result = await imagePicker.launchImageLibraryAsync({
      mediaTypes: ['images'],
      allowsEditing: true,
      aspect: [1, 1],
      quality: 0.8,
    });
    if (result.canceled) return;
    const asset = result.assets[0];
    const mimeType = asset.mimeType ?? '';
    if (!['image/jpeg', 'image/png'].includes(mimeType)) {
      setError('请选择 JPEG 或 PNG 图片');
      return;
    }
    if ((asset.fileSize ?? 0) > 2 * 1024 * 1024) {
      setError('图片不能超过 2 MiB');
      return;
    }
    setError('');
    setAvatar({
      uri: asset.uri,
      mimeType,
      fileName: asset.fileName ?? `avatar.${mimeType === 'image/png' ? 'png' : 'jpg'}`,
      fileSize: asset.fileSize,
    });
  };

  const submit = async () => {
    const normalized = draft.trim();
    if (!normalized || normalized.length > 32) {
      setError('用户名需为 1 到 32 个字符');
      return;
    }
    setSubmitting(true);
    setError('');
    try {
      await onSave(normalized, avatar);
      onClose();
    } catch (requestError) {
      setError(errorMessage(requestError, '个人资料保存失败'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal transparent visible animationType="fade" onRequestClose={submitting ? undefined : onClose}>
      <View style={styles.modalRoot}>
        <Pressable style={styles.modalBackdrop} disabled={submitting} onPress={onClose} />
        <View style={styles.editModal}>
          <View style={styles.editModalTop}>
            <View style={styles.flex}>
              <Text style={styles.modalEyebrow}>EDIT PROFILE</Text>
              <Text style={styles.modalTitle}>编辑个人资料</Text>
              <Text style={styles.modalLead}>修改你的展示用户名或个人头像。</Text>
            </View>
            <Pressable
              accessibilityRole="button"
              accessibilityLabel="关闭编辑个人资料"
              disabled={submitting}
              onPress={onClose}
              style={styles.closeButton}
            >
              <XIcon color={colors.ink} size={20} />
            </Pressable>
          </View>
          <View style={styles.editAvatarCard}>
            <Image
              source={avatar ? { uri: avatar.uri } : avatarUrl ? { uri: avatarUrl } : fallbackAvatar}
              style={styles.editAvatarImage}
              contentFit="cover"
            />
            <View style={styles.flex}>
              <Text style={styles.editAvatarTitle}>个人头像</Text>
              <Text style={styles.editAvatarNote}>支持 JPEG、PNG，文件不超过 2 MiB</Text>
              <Pressable
                accessibilityRole="button"
                disabled={submitting}
                onPress={selectAvatar}
                style={styles.avatarPicker}
              >
                <Text style={styles.avatarPickerText}>选择新头像</Text>
              </Pressable>
            </View>
          </View>
          <View style={styles.formGroup}>
            <Text style={styles.fieldLabel}>用户名</Text>
            <TextInput
              editable={!submitting}
              maxLength={32}
              value={draft}
              onChangeText={setDraft}
              style={styles.input}
            />
          </View>
          {error ? (
            <Text accessibilityRole="alert" style={styles.formError}>
              {error}
            </Text>
          ) : null}
          <View style={styles.modalActions}>
            <AppButton
              title="取消"
              variant="secondary"
              disabled={submitting}
              onPress={onClose}
              style={styles.modalAction}
            />
            <AppButton
              title={submitting ? '正在保存' : '保存修改'}
              disabled={submitting}
              onPress={submit}
              style={styles.modalAction}
            />
          </View>
        </View>
      </View>
    </Modal>
  );
}

export function getShanghaiToday() {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(new Date());
  const values = parts.reduce<Record<string, string>>((result, { type, value }) => {
    result[type] = value;
    return result;
  }, {});
  return `${values.year}-${values.month}-${values.day}`;
}

export function CalendarCard({
  calendar,
  onMonthChange,
}: {
  calendar: ProfileOverview['calendar'];
  onMonthChange: (month: string) => void;
}) {
  const [year, monthNumber] = calendar.month.split('-').map(Number);
  const today = getShanghaiToday();
  const todayDay = today.startsWith(calendar.month) ? Number(today.slice(-2)) : null;
  const [selectedDay, setSelectedDay] = useState(todayDay ?? 1);
  const leadingDays = (new Date(year, monthNumber - 1, 1).getDay() + 6) % 7;
  const daysInMonth = new Date(year, monthNumber, 0).getDate();
  const days = Array.from({ length: daysInMonth }, (_, index) => index + 1);
  const checkedDates = new Set(calendar.checkedDates);
  const selectedDate = `${calendar.month}-${String(selectedDay).padStart(2, '0')}`;
  const selectedChecked = checkedDates.has(selectedDate);
  const currentMonth = today.slice(0, 7);
  const shiftMonth = (offset: number) => {
    const shifted = new Date(year, monthNumber - 1 + offset, 1);
    onMonthChange(`${shifted.getFullYear()}-${String(shifted.getMonth() + 1).padStart(2, '0')}`);
  };
  return (
    <Card style={styles.calendarCard}>
      <View style={styles.calendarHeader}>
        <View>
          <Text style={styles.eyebrow}>LEARNING CALENDAR</Text>
          <Text style={styles.sectionHeadingLarge}>学习日历</Text>
        </View>
        <View style={styles.monthSwitcher}>
          <Pressable accessibilityLabel="上个月" onPress={() => shiftMonth(-1)} style={styles.monthArrow}>
            <AppIcon name="arrow-left" size={16} color={colors.muted} />
          </Pressable>
          <Text style={styles.monthLabel}>
            {year} 年 {monthNumber} 月
          </Text>
          <Pressable
            accessibilityLabel="下个月"
            disabled={calendar.month >= currentMonth}
            onPress={() => shiftMonth(1)}
            style={styles.monthArrow}
          >
            <AppIcon name="arrow-right" size={16} color={calendar.month >= currentMonth ? colors.line : colors.muted} />
          </Pressable>
        </View>
      </View>
      <View style={styles.calendarWeekdays}>
        {['一', '二', '三', '四', '五', '六', '日'].map((day) => (
          <Text key={day} style={styles.calendarWeekday}>
            周{day}
          </Text>
        ))}
      </View>
      <View style={styles.calendarGrid}>
        {Array.from({ length: leadingDays }, (_, index) => (
          <View key={`blank-${index}`} style={styles.calendarCell} />
        ))}
        {days.map((day) => {
          const date = `${calendar.month}-${String(day).padStart(2, '0')}`;
          const checked = checkedDates.has(date);
          const isFuture = date > today;
          return (
            <Pressable
              key={day}
              accessibilityRole="button"
              accessibilityState={{ disabled: isFuture }}
              accessibilityLabel={`${monthNumber}月${day}日${checked ? '，已打卡' : '，未打卡'}`}
              disabled={isFuture}
              onPress={() => {
                if (!isFuture) setSelectedDay(day);
              }}
              style={[
                styles.calendarCell,
                selectedDay === day && styles.calendarCellSelected,
                checked && styles.calendarCellChecked,
                isFuture && styles.calendarCellDisabled,
              ]}
            >
              <Text
                style={[
                  styles.calendarDay,
                  selectedDay === day && styles.calendarDaySelected,
                  isFuture && styles.calendarDayDisabled,
                ]}
              >
                {day}
              </Text>
              {day === todayDay ? (
                <Text style={styles.calendarToday}>今天</Text>
              ) : checked ? (
                <View style={styles.calendarCheckDot} />
              ) : null}
            </Pressable>
          );
        })}
      </View>
      <View style={[styles.calendarSummary, selectedChecked && styles.calendarSummaryActive]}>
        <View style={styles.calendarStatus}>
          <CalendarCheckIcon color={selectedChecked ? colors.green : colors.subtle} size={17} />
          <Text style={styles.calendarStatusText}>{selectedChecked ? '已打卡' : '未打卡'}</Text>
        </View>
        <View style={styles.flex}>
          <Text style={styles.calendarSummaryDate}>
            {monthNumber} 月 {selectedDay} 日
          </Text>
          <Text style={styles.calendarSummaryNote}>
            {selectedChecked ? '已生成五维评分报告，自动打卡完成' : '这一天还没有五维评分报告'}
          </Text>
        </View>
      </View>
    </Card>
  );
}

function AchievementSummary({
  overview,
  loading,
  error,
  onRetry,
}: {
  overview: AchievementOverview | null;
  loading: boolean;
  error: string;
  onRetry: () => void;
}) {
  const series = overview?.series ?? [];
  const milestones = series.flatMap((item) => item.milestones);
  const unlockedCount = milestones.filter((item) => item.unlocked).length;
  return (
    <View style={styles.achievementSection}>
      <View style={styles.achievementHeader}>
        <View style={styles.flex}>
          <Text style={styles.eyebrow}>ACHIEVEMENTS</Text>
          <Text style={styles.sectionHeadingLarge}>成就图鉴</Text>
          <Text style={styles.sectionSubcopy}>每一级进步，都由你真实的练习记录点亮。</Text>
        </View>
        {!loading && !error ? (
          <Text style={styles.achievementCount}>
            {unlockedCount}
            <Text style={styles.achievementTotal}> / {milestones.length} 已获得</Text>
          </Text>
        ) : null}
      </View>
      {loading ? (
        <View style={styles.loadingState}>
          <ActivityIndicator color={colors.ink} />
          <Text style={styles.emptyInsightText}>正在计算成就进度</Text>
        </View>
      ) : error ? (
        <View style={styles.errorState}>
          <Text style={styles.formError}>{error}</Text>
          <AppButton title="重新加载" variant="secondary" onPress={onRetry} />
        </View>
      ) : series.length === 0 ? (
        <View style={styles.emptyInsight}>
          <Text style={styles.emptyInsightText}>成就目录暂时为空</Text>
        </View>
      ) : (
        <View style={styles.achievementGrid}>
          {series.map((item) => {
            const maximum = Number(item.nextThreshold ?? item.currentValue ?? 1);
            return (
              <Card key={item.seriesId} style={styles.achievementCard}>
                <View style={styles.achievementCardHeader}>
                  <View style={styles.achievementIcon}>
                    <AppIcon
                      name={
                        item.seriesId.includes('scene') ? 'grid' : item.seriesId.includes('streak') ? 'trophy' : 'chat'
                      }
                      size={24}
                      color={colors.muted}
                    />
                  </View>
                  <View style={styles.flex}>
                    <Text style={styles.achievementCategory}>{item.category}</Text>
                    <Text style={styles.achievementTitle}>{item.title}</Text>
                  </View>
                  <Pill>{item.completed ? '全部达成' : `Lv.${item.currentLevel}`}</Pill>
                </View>
                <View style={styles.achievementBody}>
                  <Text style={styles.achievementLabel}>当前等级</Text>
                  <Text style={styles.achievementLevel}>{item.currentTitle ?? '尚未解锁'}</Text>
                  <Text style={styles.achievementNote}>
                    {item.completed ? '该系列所有成就已解锁' : `下一阶段：${item.nextTitle ?? '待解锁'}`}
                  </Text>
                </View>
                <View style={styles.achievementProgress}>
                  <View style={styles.rowBetween}>
                    <Text style={styles.achievementLabel}>当前进度</Text>
                    <Text style={styles.achievementLabel}>{item.completed ? '完成状态' : '下一阶段'}</Text>
                  </View>
                  <ProgressBar value={Math.min(Number(item.currentValue), maximum)} max={Math.max(1, maximum)} />
                  <View style={styles.rowBetween}>
                    <Text style={styles.achievementValue}>
                      {item.currentValue} {item.unit}
                    </Text>
                    <Text style={styles.achievementNext}>
                      {item.completed ? '已完成' : (item.nextTitle ?? '待解锁')}
                    </Text>
                  </View>
                </View>
                <View style={styles.achievementFooter}>
                  <Text style={styles.achievementFooterText}>共 {item.milestones.length} 个等级</Text>
                </View>
              </Card>
            );
          })}
        </View>
      )}
    </View>
  );
}

export function Overview({ onBack }: { onBack: () => void }) {
  const api = useProfileApi();
  const [month, setMonth] = useState(() => getShanghaiToday().slice(0, 7));
  const [overview, setOverview] = useState<ProfileOverview | null>(null);
  const [achievements, setAchievements] = useState<AchievementOverview | null>(null);
  const [loading, setLoading] = useState(true);
  const [overviewError, setOverviewError] = useState('');
  const [overviewRetry, setOverviewRetry] = useState(0);
  const [achievementError, setAchievementError] = useState('');
  const [achievementRetry, setAchievementRetry] = useState(0);

  useEffect(() => {
    let cancelled = false;
    api
      .getOverview(month)
      .then((value) => {
        if (!cancelled) setOverview(value);
      })
      .catch((error) => {
        if (!cancelled) setOverviewError(errorMessage(error, '个人概览加载失败'));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [api, month, overviewRetry]);

  useEffect(() => {
    let cancelled = false;
    api
      .getAchievements()
      .then((value) => {
        if (!cancelled) setAchievements(value);
      })
      .catch((error) => {
        if (!cancelled) setAchievementError(errorMessage(error, '成就数据加载失败'));
      });
    return () => {
      cancelled = true;
    };
  }, [api, achievementRetry]);

  const statistics = overview?.statistics;
  const days = statistics?.lastSevenDays ?? [];
  const maximumSeconds = Math.max(1, ...days.map((day) => day.practiceSeconds));
  const changeMonth = (nextMonth: string) => {
    setOverview(null);
    setOverviewError('');
    setLoading(true);
    setMonth(nextMonth);
  };
  const retryOverview = () => {
    setOverview(null);
    setOverviewError('');
    setLoading(true);
    setOverviewRetry((value) => value + 1);
  };
  const retryAchievements = () => {
    setAchievements(null);
    setAchievementError('');
    setAchievementRetry((value) => value + 1);
  };
  return (
    <AppScreen
      contentStyle={styles.pageContent}
      fixedHeader={<PageHeader fixed onBack={onBack} title="你的学习空间" />}
    >
      <Text style={styles.pageEyebrow}>PERSONAL OVERVIEW</Text>
      <Text style={styles.pageTitle}>你的学习空间</Text>
      <Text style={styles.pageSubtitle}>把每一次开口变成看得见、可继续的成长记录。</Text>
      {loading && !overview ? (
        <View style={styles.loadingState}>
          <ActivityIndicator color={colors.ink} />
          <Text style={styles.emptyInsightText}>正在加载真实学习数据</Text>
        </View>
      ) : overviewError ? (
        <View style={styles.errorState}>
          <Text style={styles.formError}>{overviewError}</Text>
          <AppButton title="重新加载" variant="secondary" onPress={retryOverview} />
        </View>
      ) : overview ? (
        <>
          <View style={styles.statGrid}>
            <StatCard
              icon={<ClockIcon color={colors.ink} size={24} />}
              label="本周学习时长"
              value={Math.ceil(statistics!.weeklyPracticeSeconds / 60)}
              suffix="分钟"
            />
            <StatCard
              icon={<BookOpenTextIcon color={colors.ink} size={24} />}
              label="已保存学习资产"
              value={statistics!.trainingRecordCount}
              suffix="项"
            />
            <StatCard
              icon={<FireIcon color={colors.ink} size={24} />}
              label="连续学习天数"
              value={statistics!.consecutiveLearningDays}
              suffix="天"
            />
          </View>
          <View style={styles.overviewGrid}>
            <CalendarCard key={overview.calendar.month} calendar={overview.calendar} onMonthChange={changeMonth} />
            <Card style={styles.rhythmCard}>
              <Text style={styles.eyebrow}>LAST SEVEN DAYS</Text>
              <Text style={styles.sectionHeadingLarge}>练习节奏</Text>
              <View style={styles.rhythmBars}>
                {days.map((day) => {
                  const minutes = day.practiceSeconds > 0 ? Math.ceil(day.practiceSeconds / 60) : 0;
                  const weekday = new Intl.DateTimeFormat('zh-CN', {
                    weekday: 'short',
                  }).format(new Date(`${day.date}T12:00:00`));
                  return (
                    <View key={day.date} style={styles.rhythmBarColumn}>
                      <View
                        style={[
                          styles.rhythmBar,
                          {
                            height: Math.max(10, (day.practiceSeconds / maximumSeconds) * 90),
                          },
                          day.practiceSeconds > 0 && styles.rhythmBarActive,
                        ]}
                      />
                      <Text style={styles.rhythmValue}>{minutes}m</Text>
                      <Text style={styles.rhythmDay}>{weekday}</Text>
                    </View>
                  );
                })}
              </View>
            </Card>
          </View>
        </>
      ) : null}
      <AchievementSummary
        overview={achievements}
        loading={!achievements && !achievementError}
        error={achievementError}
        onRetry={retryAchievements}
      />
    </AppScreen>
  );
}

export function Insights({ onBack }: { onBack: () => void }) {
  const api = useProfileApi();
  const [goalsOpen, setGoalsOpen] = useState(false);
  const [insights, setInsights] = useState<ProfileInsights | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    let cancelled = false;
    api
      .getInsights()
      .then((value) => {
        if (!cancelled) setInsights(value);
      })
      .catch((requestError) => {
        if (!cancelled) setError(errorMessage(requestError, '学习目标加载失败'));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [api, retry]);

  const goals = insights?.weeklyGoals;
  const completedMinutes = Math.ceil((goals?.completedDurationSeconds ?? 0) / 60);
  const formatDate = (value: string) =>
    new Intl.DateTimeFormat('zh-CN', { month: 'long', day: 'numeric' }).format(new Date(value));
  const weekRange = goals
    ? `${formatDate(goals.weekStartsAt)} 至 ${formatDate(new Date(new Date(goals.weekEndsAt).getTime() - 1).toISOString())}`
    : '本周';
  const latestTrend = insights?.abilityTrends.at(-1);
  const scoreEntries = latestTrend
    ? Object.entries(latestTrend.scores).filter((entry): entry is [string, number] => typeof entry[1] === 'number')
    : [];
  const saveGoals = async (value: { durationTargetMinutes: number; trainingCountTarget: number }) => {
    setInsights(await api.updateWeeklyGoals(value));
  };
  const retryInsights = () => {
    setError('');
    setLoading(true);
    setRetry((value) => value + 1);
  };

  return (
    <AppScreen
      contentStyle={styles.pageContent}
      fixedHeader={
        <PageHeader
          fixed
          onBack={onBack}
          title="学习目标与洞察"
          action={
            goals ? (
              <Pressable accessibilityRole="button" onPress={() => setGoalsOpen(true)} style={styles.headerAction}>
                <PencilSimpleIcon color={colors.ink} size={18} />
                <Text style={styles.headerActionText}>调整目标</Text>
              </Pressable>
            ) : null
          }
        />
      }
    >
      <Text style={styles.pageEyebrow}>LEARNING INSIGHTS</Text>
      <Text style={styles.pageTitle}>学习目标与洞察</Text>
      <Text style={styles.pageSubtitle}>{weekRange}</Text>
      {loading ? (
        <View style={styles.loadingState}>
          <ActivityIndicator color={colors.ink} />
          <Text style={styles.emptyInsightText}>正在加载学习洞察</Text>
        </View>
      ) : error ? (
        <View style={styles.errorState}>
          <Text style={styles.formError}>{error}</Text>
          <AppButton title="重新加载" variant="secondary" onPress={retryInsights} />
        </View>
      ) : goals && insights ? (
        <>
          <View style={styles.goalGrid}>
            <Card style={styles.goalCard}>
              <View style={styles.goalHeader}>
                <View style={[styles.goalIcon, styles.goalIconGreen]}>
                  <ClockIcon color={colors.green} size={22} />
                </View>
                <View style={styles.flex}>
                  <Text style={styles.goalLabel}>口语时长</Text>
                  <Text style={styles.goalState}>{goals.durationAchieved ? '已达标' : '进行中'}</Text>
                </View>
                <Text style={styles.goalPercent}>{Math.round(goals.durationProgress * 10) / 10}%</Text>
              </View>
              <Text style={styles.goalValue}>
                {completedMinutes}
                <Text style={styles.goalSuffix}> / {goals.durationTargetMinutes} 分钟</Text>
              </Text>
              <ProgressBar value={goals.durationProgress} max={100} />
              <Text style={styles.goalRemaining}>
                {goals.durationAchieved
                  ? '本周目标已完成'
                  : `还差 ${Math.ceil(goals.remainingDurationSeconds / 60)} 分钟`}
              </Text>
            </Card>
            <Card style={styles.goalCard}>
              <View style={styles.goalHeader}>
                <View style={[styles.goalIcon, styles.goalIconBlue]}>
                  <ChartLineUpIcon color="#35659B" size={22} />
                </View>
                <View style={styles.flex}>
                  <Text style={styles.goalLabel}>训练次数</Text>
                  <Text style={styles.goalState}>{goals.countAchieved ? '已达标' : '进行中'}</Text>
                </View>
                <Text style={styles.goalPercent}>{Math.round(goals.countProgress * 10) / 10}%</Text>
              </View>
              <Text style={styles.goalValue}>
                {goals.completedTrainingCount}
                <Text style={styles.goalSuffix}> / {goals.trainingCountTarget} 次</Text>
              </Text>
              <ProgressBar value={goals.countProgress} max={100} />
              <Text style={styles.goalRemaining}>
                {goals.countAchieved ? '本周目标已完成' : `还差 ${goals.remainingTrainingCount} 次`}
              </Text>
            </Card>
          </View>
          <View style={styles.divider} />
          <View style={styles.sectionTitleRow}>
            <View>
              <Text style={styles.eyebrow}>TRAINING MIX</Text>
              <Text style={styles.sectionHeadingLarge}>本周训练类型占比</Text>
            </View>
            <Text style={styles.sectionSubcopy}>按有效训练时长统计</Text>
          </View>
          {insights.trainingTypeDistribution.length ? (
            <Card style={styles.insightList}>
              {insights.trainingTypeDistribution
                .filter((item) => item.durationSeconds > 0)
                .map((item) => (
                  <View key={item.type} style={styles.insightRow}>
                    <View style={styles.flex}>
                      <Text style={styles.insightTitle}>{trainingTypeLabels[item.type] ?? '其他训练'}</Text>
                      <Text style={styles.insightNote}>{Math.ceil(item.durationSeconds / 60)} 分钟</Text>
                    </View>
                    <Text style={styles.insightMetric}>{Math.round(item.percentage * 10) / 10}%</Text>
                  </View>
                ))}
            </Card>
          ) : (
            <View style={styles.emptyInsight}>
              <ClockIcon color={colors.subtle} size={32} />
              <Text style={styles.emptyInsightText}>本周暂无有效训练记录</Text>
            </View>
          )}
          <View style={styles.divider} />
          <SectionTitle eyebrow="ABILITY TREND" title="最近能力表现" />
          {scoreEntries.length ? (
            <Card style={styles.insightList}>
              {scoreEntries.map(([key, value]) => (
                <View key={key} style={styles.insightRow}>
                  <Text style={styles.insightTitle}>{dimensionLabels[key] ?? key}</Text>
                  <Text style={styles.insightMetric}>{Number(value).toFixed(1)}</Text>
                </View>
              ))}
            </Card>
          ) : (
            <View style={styles.emptyInsightCompact}>
              <Text style={styles.emptyInsightText}>完成训练后将显示能力趋势</Text>
            </View>
          )}
          <View style={styles.divider} />
          <SectionTitle eyebrow="RECOMMENDATIONS" title="薄弱项与训练建议" />
          {insights.weaknessAnalysis.reliable ? (
            <View style={styles.insightStack}>
              {insights.weaknesses.map((item) => (
                <Card key={item.dimension} style={styles.recommendationCard}>
                  <Text style={styles.insightTitle}>
                    {dimensionLabels[item.dimension.toLowerCase()] ?? item.dimension} ·{' '}
                    {Number(item.averageScore).toFixed(1)}
                  </Text>
                  <Text style={styles.insightNote}>{item.basis}</Text>
                </Card>
              ))}
              {insights.recommendations.map((item, index) => (
                <Card key={`${item.dimension}-${index}`} style={styles.recommendationCard}>
                  <Text style={styles.insightTitle}>{trainingTypeLabels[item.trainingType] ?? '推荐训练'}</Text>
                  <Text style={styles.insightNote}>{item.reason}</Text>
                </Card>
              ))}
            </View>
          ) : (
            <View style={styles.emptyInsightCompact}>
              <Text style={styles.emptyInsightText}>
                已有 {insights.weaknessAnalysis.sampleCount} 份样本，至少需要{' '}
                {insights.weaknessAnalysis.minimumSampleCount} 份才能生成可靠建议
              </Text>
            </View>
          )}
        </>
      ) : null}
      {goalsOpen && goals ? (
        <WeeklyGoalsModal goals={goals} onSave={saveGoals} onClose={() => setGoalsOpen(false)} />
      ) : null}
    </AppScreen>
  );
}

function WeeklyGoalsModal({
  goals,
  onClose,
  onSave,
}: {
  goals: WeeklyGoals;
  onClose: () => void;
  onSave: (value: { durationTargetMinutes: number; trainingCountTarget: number }) => Promise<void>;
}) {
  const [minutes, setMinutes] = useState(String(goals.durationTargetMinutes));
  const [sessions, setSessions] = useState(String(goals.trainingCountTarget));
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const submit = async () => {
    const durationTargetMinutes = Number(minutes);
    const trainingCountTarget = Number(sessions);
    if (!Number.isInteger(durationTargetMinutes) || durationTargetMinutes < 1 || durationTargetMinutes > 1260) {
      setError('时长目标需在 1 到 1260 分钟之间');
      return;
    }
    if (!Number.isInteger(trainingCountTarget) || trainingCountTarget < 1 || trainingCountTarget > 70) {
      setError('训练次数需在 1 到 70 次之间');
      return;
    }
    setSaving(true);
    setError('');
    try {
      await onSave({ durationTargetMinutes, trainingCountTarget });
      onClose();
    } catch (requestError) {
      setError(errorMessage(requestError, '目标保存失败'));
    } finally {
      setSaving(false);
    }
  };
  return (
    <Modal transparent visible animationType="fade" onRequestClose={saving ? undefined : onClose}>
      <View style={styles.modalRoot}>
        <Pressable style={styles.modalBackdrop} disabled={saving} onPress={onClose} />
        <View style={styles.goalsModal}>
          <View style={styles.editModalTop}>
            <View style={styles.flex}>
              <Text style={styles.modalEyebrow}>WEEKLY GOALS</Text>
              <Text style={styles.modalTitle}>调整每周目标</Text>
            </View>
            <Pressable
              accessibilityRole="button"
              accessibilityLabel="关闭每周目标"
              disabled={saving}
              onPress={onClose}
              style={styles.closeButton}
            >
              <XIcon color={colors.ink} size={20} />
            </Pressable>
          </View>
          <View style={styles.goalFormGroup}>
            <Text style={styles.fieldLabel}>口语时长</Text>
            <View style={styles.goalInputRow}>
              <TextInput
                editable={!saving}
                keyboardType="number-pad"
                value={minutes}
                onChangeText={setMinutes}
                style={styles.goalInput}
              />
              <Text style={styles.goalInputSuffix}>分钟 / 周</Text>
            </View>
          </View>
          <View style={styles.goalFormGroup}>
            <Text style={styles.fieldLabel}>训练次数</Text>
            <View style={styles.goalInputRow}>
              <TextInput
                editable={!saving}
                keyboardType="number-pad"
                value={sessions}
                onChangeText={setSessions}
                style={styles.goalInput}
              />
              <Text style={styles.goalInputSuffix}>次 / 周</Text>
            </View>
          </View>
          {error ? (
            <Text accessibilityRole="alert" style={styles.formError}>
              {error}
            </Text>
          ) : null}
          <View style={styles.modalActions}>
            <AppButton
              title="取消"
              variant="secondary"
              disabled={saving}
              onPress={onClose}
              style={styles.modalAction}
            />
            <AppButton
              title={saving ? '正在保存' : '保存目标'}
              disabled={saving}
              onPress={submit}
              style={styles.modalAction}
            />
          </View>
        </View>
      </View>
    </Modal>
  );
}

export function Membership({ onBack }: { onBack: () => void }) {
  const plans = [
    {
      name: '免费版',
      price: '0',
      note: '适合轻量体验与每日开口',
      features: ['每天 5 分钟自由对话', '每天 1 次普通场景', '全部六位 AI 老师'],
    },
    {
      name: '专业版',
      price: '48',
      note: '适合稳定提升日常与职场口语',
      features: ['每月 600 分钟自由对话', '每月 50 次普通场景', '全部六位 AI 老师'],
    },
    {
      name: '特训版',
      price: '198',
      note: '适合雅思备考与英文面试',
      features: [
        '包含专业版全部权益',
        'IELTS Part 1 / 2 / 3 模拟',
        '英文面试与材料分析',
        '每天 5 次特训，共用 150 次/月',
      ],
    },
  ];
  return (
    <AppScreen
      contentStyle={styles.pageContent}
      fixedHeader={<PageHeader fixed onBack={onBack} title="会员与订阅中心" />}
    >
      <Text style={styles.pageEyebrow}>MEMBERSHIP & PRICING</Text>
      <Text style={styles.pageTitle}>会员与订阅中心</Text>
      <Text style={styles.pageSubtitle}>会员支付接口尚未开放，当前仅展示方案，不会伪造升级结果。</Text>
      <View style={styles.planGrid}>
        {plans.map((plan) => {
          const selected = plan.name === '免费版';
          return (
            <Card key={plan.name} style={[styles.planCard, selected && styles.planCardSelected]}>
              <View>
                {selected ? <Pill dark>当前方案</Pill> : plan.name === '专业版' ? <Pill>推荐</Pill> : null}
                <Text style={styles.planName}>{plan.name}</Text>
                <Text style={styles.planNote}>{plan.note}</Text>
              </View>
              <Text style={styles.planPrice}>
                <Text style={styles.planCurrency}>¥</Text>
                {plan.price}
                <Text style={styles.planCycle}>/月</Text>
              </Text>
              <View style={styles.planFeatures}>
                {plan.features.map((feature) => (
                  <View key={feature} style={styles.planFeature}>
                    <Text style={styles.checkMark}>✓</Text>
                    <Text style={styles.planFeatureText}>{feature}</Text>
                  </View>
                ))}
              </View>
              <AppButton
                title={selected ? '当前方案' : '暂未开放'}
                variant={selected ? 'soft' : 'secondary'}
                disabled
              />
            </Card>
          );
        })}
      </View>
    </AppScreen>
  );
}

export function AssistantSettings({ onBack }: { onBack: () => void }) {
  const { speed, saveSpeed, level, saveLevel, teacher, saveTeacher } = useAppModel();
  const { playTeacher } = useTeacherPreview();
  const [syncState, setSyncState] = useState<'idle' | 'saving' | 'saved' | 'error'>('idle');
  const save = async (operation: () => Promise<void>) => {
    setSyncState('saving');
    try {
      await operation();
      setSyncState('saved');
    } catch (error) {
      setSyncState('error');
      Alert.alert('设置保存失败', errorMessage(error, '请稍后重试'));
    }
  };
  const statusText = syncState === 'saving' ? '正在同步' : syncState === 'error' ? '同步失败' : '设置已同步';
  return (
    <AppScreen
      contentStyle={styles.pageContent}
      fixedHeader={
        <PageHeader
          fixed
          onBack={onBack}
          title="AI 助手设置"
          action={
            <View style={styles.syncState}>
              <Text style={styles.syncDot}>{syncState === 'saving' ? '…' : syncState === 'error' ? '!' : '✓'}</Text>
              <Text style={styles.syncText}>{statusText}</Text>
            </View>
          }
        />
      }
    >
      <Text style={styles.pageEyebrow}>ASSISTANT SETTINGS</Text>
      <Text style={styles.pageTitle}>AI 助手设置</Text>
      <Text style={styles.pageSubtitle}>设置会同步到后端，并在 Web 端和移动端保持一致。</Text>
      <View style={styles.settingsList}>
        <Card style={styles.settingCard}>
          <View style={styles.settingIntro}>
            <Text style={styles.settingTitle}>对话语速</Text>
            <Text style={styles.settingNote}>选择更舒适的回应节奏。</Text>
          </View>
          <SpeedSelector
            value={speed}
            onChange={(value) => {
              void save(() => saveSpeed(value));
            }}
          />
        </Card>
        <Card style={styles.settingCard}>
          <View style={styles.settingIntro}>
            <Text style={styles.settingTitle}>英语水平</Text>
            <Text style={styles.settingNote}>新对话会按照该难度调整表达。</Text>
          </View>
          <LevelSelector
            value={level}
            onChange={(value) => {
              void save(() => saveLevel(value));
            }}
          />
        </Card>
        <Card style={styles.settingCard}>
          <View style={styles.settingIntro}>
            <Text style={styles.settingTitle}>AI 老师</Text>
            <Text style={styles.settingNote}>每位老师有固定口音和陪练方式。</Text>
          </View>
          <TeacherSelector
            selectedId={teacher.id}
            onSelect={(value) => {
              void save(() => saveTeacher(value));
            }}
            onPreview={playTeacher}
          />
        </Card>
      </View>
    </AppScreen>
  );
}

export function AccountSettings({ onBack, onLogout }: { onBack: () => void; onLogout?: () => void }) {
  const api = useProfileApi();
  const { nickname, setNickname, email } = useAppModel();
  const [draft, setDraft] = useState(nickname);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [nicknameOpen, setNicknameOpen] = useState(false);
  const [passwordOpen, setPasswordOpen] = useState(false);
  const saveNickname = async () => {
    const normalized = draft.trim();
    if (!normalized || normalized.length > 32) {
      setError('用户名需为 1 到 32 个字符');
      return;
    }
    setSaving(true);
    setError('');
    try {
      const updated = await api.updateNickname(normalized);
      setNickname(updated.nickname);
      setDraft(updated.nickname);
      setNicknameOpen(false);
    } catch (requestError) {
      setError(errorMessage(requestError, '用户名保存失败'));
    } finally {
      setSaving(false);
    }
  };
  const changePassword = async (input: { currentPassword: string; newPassword: string }) => {
    await api.changePassword(input);
    setPasswordOpen(false);
    Alert.alert('密码已修改', '请使用新密码重新登录');
    await onLogout?.();
  };
  return (
    <AppScreen contentStyle={styles.pageContent} fixedHeader={<PageHeader fixed onBack={onBack} title="账号与安全" />}>
      <View style={styles.accountHero}>
        <View style={styles.accountShield}>
          <ShieldCheckIcon color={colors.green} size={27} />
        </View>
        <Text style={styles.pageEyebrow}>ACCOUNT & SECURITY</Text>
        <Text style={styles.pageTitle}>账号与安全</Text>
        <Text style={styles.pageSubtitle}>管理登录凭据与当前登录状态</Text>
      </View>
      <SectionTitle eyebrow="LOGIN DETAILS" title="登录信息" />
      <Card style={styles.accountCard}>
        <ListRow title="登录邮箱" subtitle={email} icon="chat" meta="当前账号" />
        <ListRow
          title="展示用户名"
          subtitle={nickname || '尚未设置'}
          icon="user"
          meta="修改用户名"
          onPress={() => {
            setDraft(nickname);
            setError('');
            setNicknameOpen(true);
          }}
        />
        <ListRow
          title="登录密码"
          subtitle="密码已设置"
          icon="lock"
          meta="修改密码"
          onPress={() => setPasswordOpen(true)}
        />
      </Card>
      <SectionTitle eyebrow="CURRENT SESSION" title="当前登录" />
      <Card style={styles.accountCard}>
        <ListRow
          title="UniSpeaking Mobile"
          subtitle={`${Platform.OS === 'ios' ? 'iOS' : Platform.OS === 'android' ? 'Android' : 'Web'} · 此设备`}
          icon="user"
          meta="已登录"
        />
        <ListRow
          title="退出当前账号"
          subtitle="清除当前设备中的登录信息"
          icon="logout"
          danger
          meta="退出登录"
          onPress={onLogout}
        />
      </Card>
      {nicknameOpen ? (
        <NicknameChangeModal
          draft={draft}
          error={error}
          saving={saving}
          onChange={setDraft}
          onClose={() => setNicknameOpen(false)}
          onSubmit={saveNickname}
        />
      ) : null}
      {passwordOpen ? <PasswordChangeModal onClose={() => setPasswordOpen(false)} onSubmit={changePassword} /> : null}
    </AppScreen>
  );
}

function NicknameChangeModal({
  draft,
  error,
  saving,
  onChange,
  onClose,
  onSubmit,
}: {
  draft: string;
  error: string;
  saving: boolean;
  onChange: (value: string) => void;
  onClose: () => void;
  onSubmit: () => void;
}) {
  return (
    <Modal transparent visible animationType="fade" onRequestClose={saving ? undefined : onClose}>
      <View style={styles.modalRoot}>
        <Pressable style={styles.modalBackdrop} disabled={saving} onPress={onClose} />
        <View style={styles.goalsModal}>
          <View style={styles.editModalTop}>
            <View style={styles.flex}>
              <Text style={styles.modalTitle}>修改用户名</Text>
              <Text style={styles.modalLead}>用户名将同步显示在个人概览中。</Text>
            </View>
            <Pressable
              accessibilityRole="button"
              accessibilityLabel="关闭修改用户名"
              disabled={saving}
              onPress={onClose}
              style={styles.closeButton}
            >
              <XIcon color={colors.ink} size={20} />
            </Pressable>
          </View>
          <View style={styles.formGroup}>
            <Text style={styles.fieldLabel}>展示用户名</Text>
            <TextInput
              autoFocus
              editable={!saving}
              maxLength={32}
              value={draft}
              onChangeText={onChange}
              style={styles.input}
            />
          </View>
          {error ? <Text accessibilityRole="alert" style={styles.formError}>{error}</Text> : null}
          <View style={styles.modalActions}>
            <AppButton title="取消" variant="secondary" disabled={saving} onPress={onClose} style={styles.modalAction} />
            <AppButton title={saving ? '正在保存' : '保存用户名'} disabled={saving} onPress={onSubmit} style={styles.modalAction} />
          </View>
        </View>
      </View>
    </Modal>
  );
}

function PasswordChangeModal({
  onClose,
  onSubmit,
}: {
  onClose: () => void;
  onSubmit: (input: { currentPassword: string; newPassword: string }) => Promise<void>;
}) {
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const submit = async () => {
    if (newPassword !== confirmPassword) {
      setError('两次输入的新密码不一致');
      return;
    }
    if (currentPassword.length < 6 || newPassword.length < 6 || newPassword.length > 72) {
      setError('密码长度需为 6 到 72 位');
      return;
    }
    setSaving(true);
    setError('');
    try {
      await onSubmit({ currentPassword, newPassword });
    } catch (requestError) {
      setError(errorMessage(requestError, '密码修改失败'));
      setSaving(false);
    }
  };
  return (
    <Modal transparent visible animationType="fade" onRequestClose={saving ? undefined : onClose}>
      <View style={styles.modalRoot}>
        <Pressable style={styles.modalBackdrop} disabled={saving} onPress={onClose} />
        <View style={styles.goalsModal}>
          <View style={styles.editModalTop}>
            <View style={styles.flex}>
              <Text style={styles.modalEyebrow}>ACCOUNT SECURITY</Text>
              <Text style={styles.modalTitle}>修改密码</Text>
              <Text style={styles.modalLead}>修改成功后，所有设备都需要重新登录。</Text>
            </View>
            <Pressable
              accessibilityRole="button"
              accessibilityLabel="关闭修改密码"
              disabled={saving}
              onPress={onClose}
              style={styles.closeButton}
            >
              <XIcon color={colors.ink} size={20} />
            </Pressable>
          </View>
          <View style={styles.formGroup}>
            <Text style={styles.fieldLabel}>当前密码</Text>
            <TextInput
              secureTextEntry
              editable={!saving}
              value={currentPassword}
              onChangeText={setCurrentPassword}
              style={styles.input}
            />
          </View>
          <View style={styles.formGroup}>
            <Text style={styles.fieldLabel}>新密码</Text>
            <TextInput
              secureTextEntry
              editable={!saving}
              value={newPassword}
              onChangeText={setNewPassword}
              style={styles.input}
            />
          </View>
          <View style={styles.formGroup}>
            <Text style={styles.fieldLabel}>确认新密码</Text>
            <TextInput
              secureTextEntry
              editable={!saving}
              value={confirmPassword}
              onChangeText={setConfirmPassword}
              style={styles.input}
            />
          </View>
          {error ? (
            <Text accessibilityRole="alert" style={styles.formError}>
              {error}
            </Text>
          ) : null}
          <View style={styles.modalActions}>
            <AppButton
              title="取消"
              variant="secondary"
              disabled={saving}
              onPress={onClose}
              style={styles.modalAction}
            />
            <AppButton
              title={saving ? '正在修改' : '确认修改'}
              disabled={saving}
              onPress={submit}
              style={styles.modalAction}
            />
          </View>
        </View>
      </View>
    </Modal>
  );
}

export function HelpCenter({
  onBack,
  onOpenCategory,
}: {
  onBack: () => void;
  onOpenCategory: (categoryId: string) => void;
}) {
  const api = useProfileApi();
  const [content, setContent] = useState<HelpCenterContent | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const load = useCallback(async () => {
    setLoading(true);
    try {
      const value = await api.getHelpCenter();
      setContent(value);
      setError('');
    } catch (requestError) {
      setError(errorMessage(requestError, '帮助内容加载失败'));
    } finally {
      setLoading(false);
    }
  }, [api]);
  useEffect(() => {
    let cancelled = false;
    api
      .getHelpCenter()
      .then((value) => {
        if (cancelled) return;
        setContent(value);
        setError('');
      })
      .catch((requestError) => {
        if (!cancelled) setError(errorMessage(requestError, '帮助内容加载失败'));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [api]);

  return (
    <AppScreen contentStyle={styles.pageContent} fixedHeader={<PageHeader fixed onBack={onBack} title="帮助中心" />}>
      <Text style={styles.pageEyebrow}>HELP CENTER</Text>
      <Text style={styles.pageTitle}>帮助中心</Text>
      <Text style={styles.pageSubtitle}>遇到问题时，从这里找到清晰的解决路径。</Text>
      {loading ? (
        <View style={styles.loadingState}>
          <ActivityIndicator color={colors.ink} />
          <Text style={styles.helpNote}>正在加载帮助内容</Text>
        </View>
      ) : error ? (
        <View style={styles.errorState}>
          <Text accessibilityRole="alert" style={styles.formError}>{error}</Text>
          <AppButton title="重新加载" variant="secondary" onPress={() => void load()} />
        </View>
      ) : (
        <View style={styles.helpList}>
          {(content?.categories ?? []).map((category) => (
            <Pressable
              key={category.id}
              accessibilityRole="button"
              accessibilityLabel={`打开${category.title}`}
              onPress={() => onOpenCategory(category.id)}
              style={({ pressed }) => pressed && styles.pressed}
            >
              <Card style={styles.helpCard}>
                <View style={styles.flex}>
                  <Text style={styles.helpTitle}>{category.title}</Text>
                  <Text style={styles.helpNote}>{category.description}</Text>
                  <Text style={styles.helpCount}>共 {category.articleCount} 篇说明</Text>
                </View>
                <AppIcon name="chevron-right" size={18} color={colors.subtle} />
              </Card>
            </Pressable>
          ))}
        </View>
      )}
      <Card style={styles.helpContact}>
        <LifebuoyIcon color={colors.ink} size={25} />
        <View style={styles.flex}>
          <Text style={styles.helpTitle}>仍然需要帮助？</Text>
          <Text style={styles.helpNote}>联系 UniSpeaking 支持团队，我们会继续协助你。</Text>
        </View>
      </Card>
    </AppScreen>
  );
}

export function HelpCategory({
  categoryId,
  onBack,
  onOpenArticle,
}: {
  categoryId: string;
  onBack: () => void;
  onOpenArticle: (articleId: string) => void;
}) {
  const api = useProfileApi();
  const [category, setCategory] = useState<HelpCategoryDetail | null>(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const load = useCallback(async () => {
    setLoading(true);
    try {
      setCategory(await api.getHelpCategory(categoryId));
      setError('');
    } catch (requestError) {
      setError(errorMessage(requestError, '帮助分类加载失败'));
    } finally {
      setLoading(false);
    }
  }, [api, categoryId]);
  useEffect(() => {
    let cancelled = false;
    api.getHelpCategory(categoryId)
      .then((value) => {
        if (!cancelled) setCategory(value);
      })
      .catch((requestError) => {
        if (!cancelled) setError(errorMessage(requestError, '帮助分类加载失败'));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => { cancelled = true; };
  }, [api, categoryId]);

  return (
    <AppScreen
      contentStyle={styles.pageContent}
      fixedHeader={<PageHeader fixed onBack={onBack} title={category?.title ?? '帮助分类'} />}
    >
      {loading ? (
        <View style={styles.loadingState}><ActivityIndicator color={colors.ink} /></View>
      ) : error ? (
        <View style={styles.errorState}>
          <Text accessibilityRole="alert" style={styles.formError}>{error}</Text>
          <AppButton title="重新加载" variant="secondary" onPress={() => void load()} />
        </View>
      ) : category ? (
        <>
          <Text style={styles.pageTitle}>{category.title}</Text>
          <Text style={styles.pageSubtitle}>{category.description}</Text>
          <View style={styles.helpList}>
            {category.articles.map((article) => (
              <Pressable
                key={article.id}
                accessibilityRole="button"
                accessibilityLabel={`打开${article.title}`}
                onPress={() => onOpenArticle(article.id)}
                style={({ pressed }) => pressed && styles.pressed}
              >
                <Card style={styles.helpCard}>
                  <View style={styles.flex}>
                    <Text style={styles.helpTitle}>{article.title}</Text>
                    <Text style={styles.helpNote}>{article.summary}</Text>
                  </View>
                  <AppIcon name="chevron-right" size={18} color={colors.subtle} />
                </Card>
              </Pressable>
            ))}
          </View>
        </>
      ) : null}
    </AppScreen>
  );
}

export function HelpArticle({
  articleId,
  onBack,
}: {
  articleId: string;
  onBack: () => void;
}) {
  const api = useProfileApi();
  const [article, setArticle] = useState<HelpArticleData | null>(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const load = useCallback(async () => {
    setLoading(true);
    try {
      setArticle(await api.getHelpArticle(articleId));
      setError('');
    } catch (requestError) {
      setError(errorMessage(requestError, '帮助文章加载失败'));
    } finally {
      setLoading(false);
    }
  }, [api, articleId]);
  useEffect(() => {
    let cancelled = false;
    api.getHelpArticle(articleId)
      .then((value) => {
        if (!cancelled) setArticle(value);
      })
      .catch((requestError) => {
        if (!cancelled) setError(errorMessage(requestError, '帮助文章加载失败'));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => { cancelled = true; };
  }, [api, articleId]);

  return (
    <AppScreen
      contentStyle={styles.pageContent}
      fixedHeader={<PageHeader fixed onBack={onBack} title="帮助详情" />}
    >
      {loading ? (
        <View style={styles.loadingState}><ActivityIndicator color={colors.ink} /></View>
      ) : error ? (
        <View style={styles.errorState}>
          <Text accessibilityRole="alert" style={styles.formError}>{error}</Text>
          <AppButton title="重新加载" variant="secondary" onPress={() => void load()} />
        </View>
      ) : article ? (
        <>
          <Text style={styles.pageTitle}>{article.title}</Text>
          <Text style={styles.helpArticleDate}>更新时间：{article.updatedAt}</Text>
          <Card style={styles.helpArticleBody}>
            <Text style={styles.helpArticleHeading}>说明</Text>
            <Text style={styles.helpArticleText}>{article.summary}</Text>
          </Card>
        </>
      ) : null}
    </AppScreen>
  );
}

export function AboutProduct({ onBack }: { onBack: () => void }) {
  return (
    <AppScreen
      contentStyle={styles.pageContent}
      fixedHeader={<PageHeader fixed onBack={onBack} title="关于 UniSpeaking" />}
    >
      <View style={styles.aboutBrand}>
        <Image source={brandAssets.mark} style={styles.aboutMark} />
        <Image source={brandAssets.wordmark} style={styles.aboutWordmark} contentFit="contain" />
      </View>
      <Text style={styles.pageEyebrow}>ABOUT UNISPEAKING</Text>
      <Text style={styles.pageTitle}>关于 UniSpeaking</Text>
      <Text style={styles.pageSubtitle}>专注真实表达的 AI 英语口语训练工具</Text>
      <View style={styles.divider} />
      <SectionTitle eyebrow="PRODUCT INFORMATION" title="产品信息" />
      <View style={styles.productInfo}>
        <View style={styles.productRow}>
          <Text style={styles.productLabel}>当前版本</Text>
          <Text style={styles.productValue}>v1.0</Text>
        </View>
        <View style={styles.productRow}>
          <Text style={styles.productLabel}>产品形态</Text>
          <Text style={styles.productValue}>Mobile App</Text>
        </View>
        <View style={styles.productRow}>
          <Text style={styles.productLabel}>客服邮箱</Text>
          <Text style={styles.productValue}>support@unispeaking.example</Text>
        </View>
        <View style={styles.productRow}>
          <Text style={styles.productLabel}>更新方式</Text>
          <Text style={styles.productValue}>自动更新</Text>
        </View>
      </View>
    </AppScreen>
  );
}

export function ProfileHome({
  activeRoute = 'overview',
  onOpen,
  onLogout,
}: {
  activeRoute?: ProfileRoute;
  onOpen: (route: ProfileRoute) => void;
  onLogout?: () => void;
}) {
  const api = useProfileApi();
  const { nickname, setNickname, email, teacher } = useAppModel();
  const [editOpen, setEditOpen] = useState(false);
  const [overview, setOverview] = useState<ProfileOverview | null>(null);
  const [error, setError] = useState('');
  const loadOverview = useCallback(async () => {
    try {
      const value = await api.getOverview();
      setError('');
      setOverview(value);
      setNickname(value.account.nickname ?? value.account.displayName);
    } catch (requestError) {
      setError(errorMessage(requestError, '个人资料加载失败'));
    }
  }, [api, setNickname]);
  useEffect(() => {
    let cancelled = false;
    api
      .getOverview()
      .then((value) => {
        if (cancelled) return;
        setError('');
        setOverview(value);
        setNickname(value.account.nickname ?? value.account.displayName);
      })
      .catch((requestError) => {
        if (!cancelled) setError(errorMessage(requestError, '个人资料加载失败'));
      });
    return () => {
      cancelled = true;
    };
  }, [api, setNickname]);
  const saveProfile = async (nextNickname: string, avatar: ProfileAvatar | null) => {
    if (nextNickname !== (overview?.account.nickname ?? nickname)) {
      const updated = await api.updateNickname(nextNickname);
      setNickname(updated.nickname);
    }
    if (avatar) await api.uploadAvatar(avatar);
    await loadOverview();
  };
  const account = overview?.account;
  const displayName = account?.displayName || nickname || email.split('@')[0] || 'UniSpeaking User';
  const accountEmail = account?.email || email;
  const avatarSource = account?.avatarUrl ? { uri: account.avatarUrl } : teacher.image;
  return (
    <AppScreen contentStyle={styles.profileContent}>
      <View style={styles.profileUser}>
        <View style={styles.profileAvatarWrap}>
          <View style={styles.profileAvatar}>
            <Image
              source={avatarSource}
              style={styles.profileAvatarImage}
              contentFit={account?.avatarUrl ? 'cover' : 'contain'}
            />
          </View>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="编辑用户名和头像"
            onPress={() => setEditOpen(true)}
            style={styles.profileEdit}
          >
            <PencilSimpleIcon color={colors.muted} size={16} />
          </Pressable>
        </View>
        <View style={styles.flex}>
          <Text style={styles.profileName}>{displayName}</Text>
          <Text style={styles.profileEmail}>{accountEmail}</Text>
          {error ? (
            <Text accessibilityRole="alert" style={styles.profileError}>
              {error}
            </Text>
          ) : null}
        </View>
      </View>
      <View style={styles.profileMenu}>
        <ProfileMenuItem
          icon={<UserCircleIcon color={colors.ink} size={25} />}
          title="个人概览"
          active={activeRoute === 'overview'}
          onPress={() => onOpen('overview')}
        />
        <ProfileMenuItem
          icon={<ChartLineUpIcon color={colors.ink} size={25} />}
          title="学习目标与洞察"
          active={activeRoute === 'insights'}
          onPress={() => onOpen('insights')}
        />
        <ProfileMenuItem
          icon={<CrownIcon color={colors.ink} size={25} />}
          title="会员权益"
          active={activeRoute === 'membership'}
          onPress={() => onOpen('membership')}
        />
        <ProfileMenuItem
          icon={<SlidersHorizontalIcon color={colors.ink} size={25} />}
          title="助手设置"
          active={activeRoute === 'assistant'}
          onPress={() => onOpen('assistant')}
        />
        <ProfileMenuItem
          icon={<ShieldCheckIcon color={colors.ink} size={25} />}
          title="账号与安全"
          active={activeRoute === 'account'}
          onPress={() => onOpen('account')}
        />
        <ProfileMenuItem
          icon={<LifebuoyIcon color={colors.ink} size={25} />}
          title="帮助中心"
          active={activeRoute === 'help'}
          onPress={() => onOpen('help')}
        />
        <ProfileMenuItem
          icon={<InfoIcon color={colors.ink} size={25} />}
          title="关于产品"
          active={activeRoute === 'about'}
          onPress={() => onOpen('about')}
        />
      </View>
      <Pressable accessibilityRole="button" onPress={onLogout} style={styles.logout}>
        <AppIcon name="logout" size={19} color={colors.red} />
        <Text style={styles.logoutText}>退出登录</Text>
      </Pressable>
      {editOpen ? (
        <ProfileEditModal
          avatarUrl={account?.avatarUrl ?? null}
          fallbackAvatar={teacher.image}
          nickname={account?.nickname ?? nickname}
          onClose={() => setEditOpen(false)}
          onSave={saveProfile}
        />
      ) : null}
    </AppScreen>
  );
}

export function ProfileScreen() {
  const [route, setRoute] = useState<ProfileRoute>('home');
  if (route === 'overview') return <Overview onBack={() => setRoute('home')} />;
  if (route === 'insights') return <Insights onBack={() => setRoute('home')} />;
  if (route === 'membership') return <Membership onBack={() => setRoute('home')} />;
  if (route === 'assistant') return <AssistantSettings onBack={() => setRoute('home')} />;
  if (route === 'account') return <AccountSettings onBack={() => setRoute('home')} />;
  if (route === 'help') return <HelpCenter onBack={() => setRoute('home')} onOpenCategory={() => undefined} />;
  if (route === 'about') return <AboutProduct onBack={() => setRoute('home')} />;
  return <ProfileHome onOpen={setRoute} />;
}

const styles = StyleSheet.create({
  pageContent: { paddingBottom: 110 },
  profileContent: {
    minHeight: '100%',
    paddingHorizontal: 28,
    paddingTop: 32,
    paddingBottom: 110,
  },
  flex: { flex: 1 },
  rowBetween: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  pressed: { opacity: 0.7, transform: [{ scale: 0.985 }] },
  profileUser: {
    minHeight: 116,
    paddingHorizontal: 2,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 18,
  },
  profileAvatarWrap: { position: 'relative', width: 92, height: 92 },
  profileAvatar: {
    width: 92,
    height: 92,
    overflow: 'hidden',
    alignItems: 'center',
    justifyContent: 'flex-end',
    borderRadius: 46,
    backgroundColor: colors.soft,
  },
  profileAvatarImage: { width: 92, height: 112, marginBottom: -12 },
  profileEdit: {
    position: 'absolute',
    top: -4,
    right: -4,
    width: 36,
    height: 36,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1,
    borderColor: colors.white,
    borderRadius: 18,
    backgroundColor: colors.white,
    shadowColor: colors.ink,
    shadowOpacity: 0.12,
    shadowRadius: 8,
    elevation: 3,
  },
  profileName: {
    color: colors.ink,
    fontSize: 28,
    lineHeight: 34,
    fontWeight: '600',
  },
  profileEmail: {
    marginTop: 7,
    color: colors.muted,
    fontSize: 16,
    lineHeight: 22,
    fontWeight: '300',
  },
  profileError: {
    marginTop: 4,
    color: colors.red,
    fontSize: 11,
    lineHeight: 16,
  },
  profileMenu: { marginTop: 26, gap: 7 },
  profileMenuItem: {
    minHeight: 58,
    paddingHorizontal: 16,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 14,
    borderRadius: 14,
  },
  profileMenuItemActive: { backgroundColor: colors.soft },
  profileMenuIcon: { width: 28, alignItems: 'center' },
  profileMenuTitle: {
    color: colors.ink,
    fontSize: 20,
    lineHeight: 28,
    fontWeight: '400',
  },
  logout: {
    minHeight: 52,
    marginTop: 26,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 9,
    borderRadius: 14,
    backgroundColor: colors.redSoft,
  },
  logoutText: { color: colors.red, fontSize: 13, fontWeight: '500' },
  pageEyebrow: {
    marginTop: 9,
    color: colors.subtle,
    fontSize: 11,
    fontWeight: '600',
    letterSpacing: 1.7,
  },
  eyebrow: {
    color: colors.subtle,
    fontSize: 10,
    fontWeight: '600',
    letterSpacing: 1.7,
  },
  pageTitle: {
    marginTop: 8,
    color: colors.ink,
    fontSize: 35,
    lineHeight: 43,
    fontWeight: '600',
    letterSpacing: 0,
  },
  pageSubtitle: {
    marginTop: 7,
    color: colors.muted,
    fontSize: 15,
    lineHeight: 22,
    fontWeight: '300',
  },
  statGrid: { marginTop: 24, gap: 10 },
  statCard: {
    minHeight: 92,
    padding: 16,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 13,
    borderWidth: 1,
    borderColor: colors.line,
    borderRadius: 17,
    backgroundColor: colors.white,
  },
  statIcon: {
    width: 38,
    height: 38,
    alignItems: 'center',
    justifyContent: 'center',
  },
  statLabel: { color: colors.muted, fontSize: 13, fontWeight: '300' },
  statValue: {
    marginTop: 3,
    color: colors.ink,
    fontSize: 26,
    lineHeight: 31,
    fontWeight: '600',
  },
  statSuffix: { fontSize: 12, fontWeight: '500' },
  overviewGrid: { marginTop: 16, gap: 16 },
  calendarCard: { gap: 16 },
  calendarHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 8,
  },
  sectionHeadingLarge: {
    marginTop: 5,
    color: colors.ink,
    fontSize: 25,
    lineHeight: 31,
    fontWeight: '600',
  },
  monthSwitcher: {
    minHeight: 40,
    paddingHorizontal: 7,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    borderWidth: 1,
    borderColor: colors.line,
    borderRadius: 22,
  },
  monthArrow: {
    width: 26,
    height: 28,
    alignItems: 'center',
    justifyContent: 'center',
  },
  monthLabel: { color: colors.ink, fontSize: 13, fontWeight: '600' },
  calendarWeekdays: { flexDirection: 'row', justifyContent: 'space-between' },
  calendarWeekday: {
    width: 30,
    color: colors.subtle,
    fontSize: 10,
    textAlign: 'center',
    fontWeight: '300',
  },
  calendarGrid: { flexDirection: 'row', flexWrap: 'wrap', rowGap: 8 },
  calendarCell: {
    width: '14.285%',
    minHeight: 34,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 9,
  },
  calendarCellChecked: { borderWidth: 1, borderColor: colors.green },
  calendarCellSelected: { backgroundColor: colors.ink },
  calendarCellDisabled: { opacity: 0.4 },
  calendarDay: { color: colors.muted, fontSize: 12, fontWeight: '300' },
  calendarDaySelected: { color: colors.white, fontWeight: '600' },
  calendarDayDisabled: { color: colors.subtle },
  calendarToday: { marginTop: 1, color: colors.white, fontSize: 7 },
  calendarCheckDot: {
    width: 4,
    height: 4,
    marginTop: 2,
    borderRadius: 2,
    backgroundColor: colors.green,
  },
  calendarSummary: {
    minHeight: 60,
    paddingHorizontal: 12,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    borderRadius: 15,
    backgroundColor: colors.soft,
  },
  calendarSummaryActive: { backgroundColor: colors.soft },
  calendarStatus: {
    minHeight: 34,
    paddingHorizontal: 10,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 5,
    borderRadius: 17,
    backgroundColor: '#EDEDE9',
  },
  calendarStatusText: { color: colors.muted, fontSize: 11, fontWeight: '500' },
  calendarSummaryDate: { color: colors.ink, fontSize: 15, fontWeight: '600' },
  calendarSummaryNote: {
    marginTop: 2,
    color: colors.muted,
    fontSize: 11,
    fontWeight: '300',
  },
  rhythmCard: { minHeight: 220, gap: 6 },
  rhythmBars: {
    minHeight: 140,
    paddingTop: 16,
    flexDirection: 'row',
    alignItems: 'flex-end',
    justifyContent: 'space-between',
    gap: 5,
  },
  rhythmBarColumn: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'flex-end',
    gap: 4,
  },
  rhythmBar: {
    width: '70%',
    height: 10,
    borderRadius: 6,
    backgroundColor: '#E4E4DF',
  },
  rhythmBarActive: { backgroundColor: colors.ink },
  rhythmValue: { color: colors.muted, fontSize: 9 },
  rhythmDay: { color: colors.subtle, fontSize: 9 },
  achievementSection: { marginTop: 26 },
  achievementHeader: { flexDirection: 'row', alignItems: 'flex-end', gap: 12 },
  sectionSubcopy: {
    marginTop: 5,
    color: colors.muted,
    fontSize: 12,
    lineHeight: 18,
    fontWeight: '300',
  },
  achievementCount: { color: colors.ink, fontSize: 25, fontWeight: '600' },
  achievementTotal: { color: colors.muted, fontSize: 11, fontWeight: '300' },
  achievementFilters: { marginTop: 16, flexDirection: 'row', gap: 7 },
  filterPill: {
    minHeight: 34,
    paddingHorizontal: 11,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 5,
    borderWidth: 1,
    borderColor: colors.line,
    borderRadius: 17,
    backgroundColor: colors.white,
  },
  filterPillActive: { borderColor: colors.ink, backgroundColor: colors.ink },
  filterPillText: { color: colors.muted, fontSize: 11 },
  filterPillTextActive: { color: colors.white },
  filterPillCount: {
    minWidth: 16,
    color: colors.subtle,
    fontSize: 10,
    textAlign: 'center',
  },
  filterPillCountActive: { color: colors.white },
  achievementGrid: { marginTop: 12, gap: 12 },
  achievementCard: { padding: 0, overflow: 'hidden' },
  achievementCardHeader: {
    minHeight: 70,
    padding: 14,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    borderBottomWidth: 1,
    borderBottomColor: colors.line,
  },
  achievementIcon: {
    width: 42,
    height: 42,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1,
    borderColor: colors.line,
    borderRadius: 13,
    backgroundColor: colors.white,
  },
  achievementCategory: {
    color: colors.subtle,
    fontSize: 10,
    fontWeight: '300',
  },
  achievementTitle: {
    marginTop: 3,
    color: colors.ink,
    fontSize: 18,
    fontWeight: '600',
  },
  achievementBody: { minHeight: 92, padding: 16, gap: 4 },
  achievementLabel: { color: colors.subtle, fontSize: 10, fontWeight: '300' },
  achievementLevel: { color: colors.muted, fontSize: 21, fontWeight: '600' },
  achievementNote: { color: colors.muted, fontSize: 11, fontWeight: '300' },
  achievementProgress: {
    padding: 16,
    gap: 8,
    borderTopWidth: 1,
    borderTopColor: colors.line,
  },
  achievementValue: { color: colors.ink, fontSize: 12, fontWeight: '500' },
  achievementNext: { color: colors.muted, fontSize: 12, fontWeight: '500' },
  achievementFooter: {
    minHeight: 46,
    paddingHorizontal: 16,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    backgroundColor: colors.soft,
  },
  achievementFooterText: {
    color: colors.muted,
    fontSize: 11,
    fontWeight: '500',
  },
  headerAction: {
    minHeight: 36,
    paddingHorizontal: 11,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    borderWidth: 1,
    borderColor: colors.line,
    borderRadius: 18,
  },
  headerActionText: { color: colors.ink, fontSize: 11, fontWeight: '500' },
  goalGrid: { marginTop: 24, gap: 12 },
  goalCard: { minHeight: 190, gap: 15 },
  goalHeader: { flexDirection: 'row', alignItems: 'center', gap: 10 },
  goalIcon: {
    width: 42,
    height: 42,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 12,
  },
  goalIconGreen: { backgroundColor: colors.greenSoft },
  goalIconBlue: { backgroundColor: '#EEF4FC' },
  goalLabel: { color: colors.muted, fontSize: 13, fontWeight: '300' },
  goalState: {
    marginTop: 3,
    color: colors.ink,
    fontSize: 17,
    fontWeight: '600',
  },
  goalPercent: { color: colors.muted, fontSize: 12, fontWeight: '500' },
  goalValue: {
    color: colors.ink,
    fontSize: 39,
    lineHeight: 45,
    fontWeight: '600',
  },
  goalSuffix: { color: colors.muted, fontSize: 15, fontWeight: '300' },
  goalRemaining: { color: colors.muted, fontSize: 12, fontWeight: '300' },
  goalFormGroup: { gap: 8 },
  goalInputRow: {
    minHeight: 54,
    paddingHorizontal: 14,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    borderWidth: 1,
    borderColor: colors.line,
    borderRadius: 13,
    backgroundColor: colors.white,
  },
  goalInput: { flex: 1, color: colors.ink, fontSize: 18, fontWeight: '600' },
  goalInputSuffix: { color: colors.muted, fontSize: 14, fontWeight: '500' },
  divider: {
    height: StyleSheet.hairlineWidth,
    marginVertical: 24,
    backgroundColor: colors.line,
  },
  sectionTitleRow: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    justifyContent: 'space-between',
    gap: 10,
  },
  emptyInsight: {
    minHeight: 200,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 10,
  },
  emptyInsightCompact: {
    minHeight: 92,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 20,
  },
  emptyInsightText: { color: colors.muted, fontSize: 14, fontWeight: '300' },
  loadingState: {
    minHeight: 180,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 12,
  },
  errorState: {
    minHeight: 150,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 14,
    paddingHorizontal: 20,
  },
  insightList: { marginTop: 16, paddingVertical: 4 },
  insightRow: {
    minHeight: 62,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: colors.line,
  },
  insightTitle: {
    color: colors.ink,
    fontSize: 15,
    lineHeight: 21,
    fontWeight: '600',
  },
  insightNote: {
    marginTop: 3,
    color: colors.muted,
    fontSize: 12,
    lineHeight: 18,
    fontWeight: '300',
  },
  insightMetric: { color: colors.ink, fontSize: 17, fontWeight: '600' },
  insightStack: { marginTop: 14, gap: 10 },
  recommendationCard: { gap: 5 },
  settingsList: { marginTop: 24, gap: 12 },
  settingCard: { gap: 16 },
  settingIntro: { gap: 4 },
  settingTitle: {
    color: colors.ink,
    fontSize: 18,
    lineHeight: 24,
    fontWeight: '600',
  },
  settingNote: {
    color: colors.muted,
    fontSize: 12,
    lineHeight: 18,
    fontWeight: '300',
  },
  settingRow: {
    paddingVertical: 7,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
  },
  syncState: { flexDirection: 'row', alignItems: 'center', gap: 5 },
  syncDot: { color: colors.green, fontSize: 16, fontWeight: '600' },
  syncText: { color: colors.muted, fontSize: 11 },
  planGrid: { marginTop: 24, gap: 14 },
  planCard: { minHeight: 340, gap: 18 },
  planCardSelected: { borderWidth: 1.5, borderColor: colors.ink },
  planName: {
    marginTop: 10,
    color: colors.ink,
    fontSize: 28,
    lineHeight: 33,
    fontWeight: '600',
  },
  planNote: {
    marginTop: 7,
    color: colors.muted,
    fontSize: 13,
    lineHeight: 20,
    fontWeight: '300',
  },
  planPrice: {
    color: colors.ink,
    fontSize: 50,
    lineHeight: 56,
    fontWeight: '600',
  },
  planCurrency: { fontSize: 16, fontWeight: '500' },
  planCycle: { color: colors.muted, fontSize: 14, fontWeight: '300' },
  planFeatures: { gap: 11 },
  planFeature: { flexDirection: 'row', alignItems: 'center', gap: 9 },
  checkMark: { color: colors.muted, fontSize: 17 },
  planFeatureText: { color: colors.muted, fontSize: 13, fontWeight: '300' },
  accountHero: { marginBottom: 22 },
  accountShield: {
    width: 52,
    height: 52,
    marginBottom: 18,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 14,
    backgroundColor: colors.greenSoft,
  },
  accountCard: { paddingHorizontal: 16, paddingVertical: 2, marginBottom: 20 },
  helpList: { marginTop: 24, gap: 10 },
  helpCard: {
    minHeight: 76,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
  },
  helpTitle: { color: colors.ink, fontSize: 17, fontWeight: '600' },
  helpNote: {
    marginTop: 4,
    color: colors.muted,
    fontSize: 12,
    lineHeight: 18,
    fontWeight: '300',
  },
  helpCount: {
    marginTop: 5,
    color: colors.subtle,
    fontSize: 10,
    fontWeight: '300',
  },
  helpArticleDate: {
    marginTop: 12,
    color: colors.subtle,
    fontSize: 11,
    fontWeight: '300',
  },
  helpArticleBody: { marginTop: 24, gap: 10 },
  helpArticleHeading: { color: colors.ink, fontSize: 18, fontWeight: '600' },
  helpArticleText: { color: colors.muted, fontSize: 15, lineHeight: 25, fontWeight: '300' },
  helpContact: {
    marginTop: 18,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    backgroundColor: colors.soft,
  },
  aboutBrand: {
    marginTop: 20,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
  },
  aboutMark: { width: 34, height: 34, borderRadius: 8 },
  aboutWordmark: { width: 150, height: 32 },
  productInfo: {
    marginTop: 14,
    borderTopWidth: 1,
    borderTopColor: colors.line,
  },
  productRow: {
    minHeight: 54,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    borderBottomWidth: 1,
    borderBottomColor: colors.line,
  },
  productLabel: { color: colors.muted, fontSize: 12, fontWeight: '300' },
  productValue: { color: colors.ink, fontSize: 13, fontWeight: '600' },
  modalRoot: { flex: 1, justifyContent: 'center', padding: 18 },
  modalBackdrop: {
    position: 'absolute',
    top: 0,
    right: 0,
    bottom: 0,
    left: 0,
    backgroundColor: 'rgba(20,20,19,0.38)',
  },
  editModal: {
    padding: 22,
    gap: 18,
    borderRadius: 22,
    backgroundColor: colors.white,
  },
  goalsModal: {
    padding: 22,
    gap: 20,
    borderRadius: 22,
    backgroundColor: colors.white,
  },
  editModalTop: { flexDirection: 'row', alignItems: 'flex-start', gap: 12 },
  modalEyebrow: {
    color: colors.subtle,
    fontSize: 11,
    fontWeight: '600',
    letterSpacing: 1.7,
  },
  modalTitle: {
    marginTop: 9,
    color: colors.ink,
    fontSize: 27,
    lineHeight: 34,
    fontWeight: '600',
  },
  modalLead: {
    marginTop: 7,
    color: colors.muted,
    fontSize: 14,
    lineHeight: 21,
    fontWeight: '300',
  },
  closeButton: {
    width: 38,
    height: 38,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 19,
    backgroundColor: colors.soft,
  },
  editAvatarCard: {
    padding: 14,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 14,
    borderWidth: 1,
    borderColor: colors.line,
    borderRadius: 15,
    backgroundColor: colors.paper,
  },
  editAvatarImage: {
    width: 76,
    height: 76,
    borderRadius: 15,
    backgroundColor: colors.soft,
  },
  editAvatarTitle: { color: colors.ink, fontSize: 16, fontWeight: '600' },
  editAvatarNote: {
    marginTop: 5,
    color: colors.muted,
    fontSize: 11,
    lineHeight: 16,
    fontWeight: '300',
  },
  avatarPicker: {
    alignSelf: 'flex-start',
    minHeight: 34,
    marginTop: 11,
    paddingHorizontal: 13,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1,
    borderColor: colors.line,
    borderRadius: 18,
  },
  avatarPickerText: { color: colors.ink, fontSize: 11, fontWeight: '600' },
  avatarChoices: { flexDirection: 'row', gap: 8 },
  avatarChoice: { alignItems: 'center', gap: 4 },
  avatarChoiceImage: {
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: colors.soft,
  },
  avatarChoiceLabel: { color: colors.muted, fontSize: 9 },
  formGroup: { gap: 8 },
  fieldLabel: { color: colors.ink, fontSize: 13, fontWeight: '600' },
  input: {
    minHeight: 52,
    paddingHorizontal: 14,
    color: colors.ink,
    fontSize: 15,
    fontWeight: '300',
    borderWidth: 1,
    borderColor: colors.line,
    borderRadius: 13,
    backgroundColor: colors.white,
  },
  formError: {
    color: colors.red,
    fontSize: 12,
    lineHeight: 18,
    textAlign: 'center',
  },
  modalActions: {
    marginTop: 8,
    flexDirection: 'row',
    justifyContent: 'flex-end',
    gap: 10,
  },
  modalAction: { flex: 1 },
});
