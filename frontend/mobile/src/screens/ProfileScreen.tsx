import { Image } from 'expo-image';
import { useState } from 'react';
import { Pressable, StyleSheet, Switch, Text, TextInput, View } from 'react-native';

import {
  AppButton,
  AppIcon,
  AppScreen,
  Card,
  ListRow,
  Metric,
  PageHeader,
  Pill,
  ProgressBar,
  SectionTitle,
  uiStyles,
} from '@/components/ui';
import {
  LevelSelector,
  SpeedSelector,
  TeacherSelector,
} from '@/components/ConversationSettings';
import { useAppModel } from '@/model/AppModel';
import { colors } from '@/theme/tokens';

export type ProfileRoute = 'home' | 'overview' | 'membership' | 'assistant' | 'account';

export function Overview({ onBack }: { onBack: () => void }) {
  const { sceneRecords, ieltsRecords, interviewRecords } = useAppModel();
  const practiceCount = sceneRecords.length + ieltsRecords.length + interviewRecords.length;
  return (
    <AppScreen>
      <PageHeader onBack={onBack} eyebrow="LEARNING OVERVIEW" title="你的口语进步" subtitle="持续开口比一次完美更重要。" />
      <View style={styles.metrics}>
        <Metric label="累计练习" value={practiceCount + 8} suffix="次" />
        <Metric label="连续学习" value="6" suffix="天" />
        <Metric label="本周时长" value="74" suffix="分钟" />
      </View>
      <Card>
        <View style={styles.rowBetween}>
          <Text style={uiStyles.title}>本周目标</Text>
          <Text style={styles.progressLabel}>4 / 5 次</Text>
        </View>
        <ProgressBar value={4} max={5} />
        <Text style={uiStyles.muted}>再完成一次练习，就能达成本周目标。</Text>
      </Card>
      <SectionTitle eyebrow="LAST 7 DAYS" title="学习节奏" />
      <View style={styles.calendar}>
        {['一', '二', '三', '四', '五', '六', '日'].map((day, index) => (
          <View key={day} style={styles.day}>
            <View style={[styles.dayDot, index < 5 && styles.dayDotActive]} />
            <Text style={styles.dayLabel}>周{day}</Text>
          </View>
        ))}
      </View>
      <SectionTitle eyebrow="ACHIEVEMENTS" title="最近成就" />
      <Card>
        <ListRow title="连续开口 5 天" subtitle="保持稳定练习节奏" icon="trophy" />
        <ListRow title="完成首次 IELTS 模拟" subtitle="已生成完整评分报告" icon="medal" />
        <ListRow title="积累 20 个表达" subtitle="正在形成自己的表达库" icon="book" />
      </Card>
    </AppScreen>
  );
}

export function Membership({ onBack }: { onBack: () => void }) {
  const { membership, setMembership } = useAppModel();
  const plans = [
    { name: '免费版', price: '¥0', note: '基础自由对话与每周练习报告' },
    { name: '进阶版', price: '¥39', note: '不限场景训练、IELTS 与面试专项' },
    { name: '专业版', price: '¥79', note: '更长会话、深度报告和完整学习资产' },
  ];
  return (
    <AppScreen>
      <PageHeader onBack={onBack} eyebrow="MEMBERSHIP" title="选择适合你的练习方式" subtitle="当前为前端交互演示，不会产生真实扣款。" />
      {plans.map((plan) => {
        const selected = membership === plan.name;
        return (
          <Card key={plan.name} style={selected && styles.selectedPlan}>
            <View style={styles.rowBetween}>
              <View>
                <Pill dark={selected}>{selected ? '当前方案' : '可选方案'}</Pill>
                <Text style={styles.planName}>{plan.name}</Text>
              </View>
              <Text style={styles.planPrice}>{plan.price}<Text style={styles.planCycle}> / 月</Text></Text>
            </View>
            <Text style={uiStyles.muted}>{plan.note}</Text>
            <View style={uiStyles.stack}>
              {['AI 对话老师', '训练报告同步', '学习资产管理'].map((feature) => (
                <View key={feature} style={styles.feature}>
                  <AppIcon name="check-circle" size={17} color={colors.green} />
                  <Text style={uiStyles.body}>{feature}</Text>
                </View>
              ))}
            </View>
            <AppButton
              title={selected ? '当前方案' : `选择${plan.name}`}
              variant={selected ? 'soft' : 'primary'}
              disabled={selected}
              onPress={() => setMembership(plan.name)}
            />
          </Card>
        );
      })}
    </AppScreen>
  );
}

export function AssistantSettings({ onBack }: { onBack: () => void }) {
  const {
    speed,
    setSpeed,
    level,
    setLevel,
    teacher,
    setTeacher,
  } = useAppModel();
  const [translation, setTranslation] = useState(true);
  const [sound, setSound] = useState(true);
  return (
    <AppScreen>
      <PageHeader onBack={onBack} eyebrow="AI ASSISTANT" title="AI 对话助手设置" subtitle="这些设置会应用到自由对话和部分训练场景。" />
      <Card>
        <Text style={uiStyles.title}>默认老师</Text>
        <TeacherSelector selectedId={teacher.id} onSelect={setTeacher} />
      </Card>
      <Card>
        <Text style={uiStyles.title}>默认语速</Text>
        <SpeedSelector value={speed} onChange={setSpeed} />
      </Card>
      <Card>
        <Text style={uiStyles.title}>英语水平</Text>
        <LevelSelector value={level} onChange={setLevel} />
      </Card>
      <Card>
        <View style={styles.settingRow}>
          <View style={styles.flex}>
            <Text style={uiStyles.title}>自动显示翻译</Text>
            <Text style={uiStyles.muted}>新字幕出现时同时显示中文参考。</Text>
          </View>
          <Switch value={translation} onValueChange={setTranslation} trackColor={{ true: colors.ink }} />
        </View>
        <View style={styles.settingRow}>
          <View style={styles.flex}>
            <Text style={uiStyles.title}>自动播放示范音频</Text>
            <Text style={uiStyles.muted}>训练步骤切换后自动播放 AI 示范。</Text>
          </View>
          <Switch value={sound} onValueChange={setSound} trackColor={{ true: colors.ink }} />
        </View>
      </Card>
    </AppScreen>
  );
}

export function AccountSettings({ onBack }: { onBack: () => void }) {
  const { nickname, setNickname } = useAppModel();
  const [draft, setDraft] = useState(nickname);
  return (
    <AppScreen>
      <PageHeader onBack={onBack} eyebrow="ACCOUNT" title="账号与同步" subtitle="编辑移动端显示信息并查看同步状态。" />
      <View style={styles.formGroup}>
        <Text style={styles.fieldLabel}>昵称</Text>
        <TextInput value={draft} onChangeText={setDraft} style={styles.input} />
      </View>
      <Card style={styles.syncCard}>
        <AppIcon name="check-circle" size={22} color={colors.green} />
        <View style={styles.flex}>
          <Text style={uiStyles.title}>数据已同步</Text>
          <Text style={uiStyles.muted}>学习记录和设置已与 Web 端账号保持一致。</Text>
        </View>
      </Card>
      <AppButton title="保存账号信息" onPress={() => setNickname(draft.trim() || nickname)} />
    </AppScreen>
  );
}

export function ProfileHome({
  onOpen,
  onLogout,
}: {
  onOpen: (route: ProfileRoute) => void;
  onLogout?: () => void;
}) {
  const { nickname, teacher, membership, sceneRecords, ieltsRecords, interviewRecords } = useAppModel();
  const practiceCount = sceneRecords.length + ieltsRecords.length + interviewRecords.length;
  const expressionCount = sceneRecords.reduce((sum, item) => sum + item.expressions.length, 0);
  return (
    <AppScreen>
      <PageHeader eyebrow="PROFILE" title="我的" subtitle="管理学习节奏、对话助手和账号设置。" />
      <Card style={styles.profileHero}>
        <View style={styles.avatar}>
          <Image source={teacher.image} style={styles.avatarImage} contentFit="contain" />
        </View>
        <View style={styles.flex}>
          <View style={styles.nameRow}>
            <Text style={styles.profileName}>{nickname}</Text>
            <Pill>{membership}</Pill>
          </View>
          <Text style={uiStyles.muted}>当前老师：{teacher.name} · {teacher.accent}</Text>
        </View>
      </Card>
      <View style={styles.metrics}>
        <Metric label="练习记录" value={practiceCount} />
        <Metric label="连续学习" value="6 天" />
        <Metric label="累计表达" value={expressionCount} />
      </View>
      <Card style={styles.menuCard}>
        <ListRow title="学习总览" subtitle="查看本周节奏与成就" icon="grid" onPress={() => onOpen('overview')} />
        <ListRow title="会员方案" subtitle={`当前：${membership}`} icon="crown" onPress={() => onOpen('membership')} />
        <ListRow title="AI 对话助手" subtitle="老师、语速、水平与字幕" icon="sliders" onPress={() => onOpen('assistant')} />
        <ListRow title="账号与同步" subtitle="昵称与跨端同步状态" icon="user" onPress={() => onOpen('account')} />
      </Card>
      <Pressable accessibilityRole="button" onPress={onLogout} style={styles.logout}>
        <AppIcon name="logout" size={19} color={colors.red} />
        <Text style={styles.logoutText}>退出登录</Text>
      </Pressable>
    </AppScreen>
  );
}

export function ProfileScreen() {
  const [route, setRoute] = useState<ProfileRoute>('home');
  if (route === 'overview') return <Overview onBack={() => setRoute('home')} />;
  if (route === 'membership') return <Membership onBack={() => setRoute('home')} />;
  if (route === 'assistant') return <AssistantSettings onBack={() => setRoute('home')} />;
  if (route === 'account') return <AccountSettings onBack={() => setRoute('home')} />;
  return <ProfileHome onOpen={setRoute} />;
}

const styles = StyleSheet.create({
  metrics: { flexDirection: 'row', gap: 8 },
  rowBetween: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  progressLabel: { color: colors.muted, fontSize: 12, fontWeight: '500' },
  calendar: {
    padding: 16,
    flexDirection: 'row',
    justifyContent: 'space-between',
    borderWidth: 1,
    borderColor: colors.line,
    borderRadius: 18,
    backgroundColor: colors.white,
    shadowColor: '#1A1A18',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.04,
    shadowRadius: 12,
    elevation: 2,
    boxShadow: '0px 4px 14px rgba(21, 21, 20, 0.04)',
  },
  day: { alignItems: 'center', gap: 8 },
  dayDot: { width: 24, height: 24, borderRadius: 12, backgroundColor: colors.soft },
  dayDotActive: { backgroundColor: colors.ink },
  dayLabel: { color: colors.muted, fontSize: 10, fontWeight: '300' },
  selectedPlan: { borderColor: colors.ink, borderWidth: 2 },
  planName: { marginTop: 9, color: colors.ink, fontSize: 23, fontWeight: '600' },
  planPrice: { color: colors.ink, fontSize: 29, fontWeight: '600' },
  planCycle: { color: colors.muted, fontSize: 10, fontWeight: '300' },
  feature: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  settingRow: { paddingVertical: 6, flexDirection: 'row', alignItems: 'center', gap: 12 },
  flex: { flex: 1 },
  formGroup: { gap: 8 },
  fieldLabel: { color: colors.ink, fontSize: 13, fontWeight: '500' },
  input: { minHeight: 52, paddingHorizontal: 14, color: colors.ink, fontSize: 15, fontWeight: '300', borderWidth: 1, borderColor: colors.line, borderRadius: 13, backgroundColor: colors.white },
  syncCard: { flexDirection: 'row', alignItems: 'center', backgroundColor: colors.white },
  profileHero: { flexDirection: 'row', alignItems: 'center', gap: 15 },
  avatar: { width: 72, height: 72, overflow: 'hidden', alignItems: 'center', justifyContent: 'flex-end', borderRadius: 36, backgroundColor: colors.soft },
  avatarImage: { width: 72, height: 88, marginBottom: -10 },
  nameRow: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  profileName: { color: colors.ink, fontSize: 24, fontWeight: '600' },
  menuCard: { paddingVertical: 2, backgroundColor: colors.white },
  logout: { minHeight: 54, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 9, borderRadius: 14, backgroundColor: colors.redSoft },
  logoutText: { color: colors.red, fontSize: 13, fontWeight: '500' },
});
