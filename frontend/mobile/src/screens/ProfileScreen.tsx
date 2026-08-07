import { Image } from 'expo-image';
import { useState, type ReactNode } from 'react';
import { Modal, Pressable, StyleSheet, Switch, Text, TextInput, View } from 'react-native';
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
import { useAppModel } from '@/model/AppModel';
import { brandAssets, colors, teachers, type Teacher } from '@/theme/tokens';

export type ProfileRoute = 'home' | 'overview' | 'insights' | 'membership' | 'assistant' | 'account' | 'help' | 'about';

const email = '123@123.com';

function StatCard({ icon, label, value, suffix, onPress }: { icon: ReactNode; label: string; value: string | number; suffix: string; onPress?: () => void }) {
  const content = <><View style={styles.statIcon}>{icon}</View><View style={styles.flex}><Text style={styles.statLabel}>{label}</Text><Text style={styles.statValue}>{value}<Text style={styles.statSuffix}> {suffix}</Text></Text></View></>;
  return onPress ? <Pressable accessibilityRole="button" onPress={onPress} style={({ pressed }) => [styles.statCard, pressed && styles.pressed]}>{content}</Pressable> : <View style={styles.statCard}>{content}</View>;
}

function ProfileMenuItem({ icon, title, active = false, onPress }: { icon: ReactNode; title: string; active?: boolean; onPress: () => void }) {
  return <Pressable accessibilityRole="button" onPress={onPress} style={({ pressed }) => [styles.profileMenuItem, active && styles.profileMenuItemActive, pressed && styles.pressed]}><View style={styles.profileMenuIcon}>{icon}</View><Text style={styles.profileMenuTitle}>{title}</Text></Pressable>;
}

function ProfileEditModal({ teacher, nickname, onClose, onSave, onTeacherChange }: { teacher: Teacher; nickname: string; onClose: () => void; onSave: (nickname: string) => void; onTeacherChange: (teacher: Teacher) => void }) {
  const [draft, setDraft] = useState(nickname);
  const [avatarChoicesOpen, setAvatarChoicesOpen] = useState(false);
  return (
    <Modal transparent visible animationType="fade" onRequestClose={onClose}>
      <View style={styles.modalRoot}>
        <Pressable style={styles.modalBackdrop} onPress={onClose} />
        <View style={styles.editModal}>
          <View style={styles.editModalTop}><View style={styles.flex}><Text style={styles.modalEyebrow}>EDIT PROFILE</Text><Text style={styles.modalTitle}>编辑个人资料</Text><Text style={styles.modalLead}>修改你的展示用户名或个人头像。</Text></View><Pressable accessibilityRole="button" accessibilityLabel="关闭编辑个人资料" onPress={onClose} style={styles.closeButton}><XIcon color={colors.ink} size={20} /></Pressable></View>
          <View style={styles.editAvatarCard}>
            <Image source={teacher.image} style={styles.editAvatarImage} contentFit="contain" />
            <View style={styles.flex}><Text style={styles.editAvatarTitle}>个人头像</Text><Text style={styles.editAvatarNote}>支持 JPEG、PNG，文件不超过 2 MiB</Text><Pressable accessibilityRole="button" onPress={() => setAvatarChoicesOpen((value) => !value)} style={styles.avatarPicker}><Text style={styles.avatarPickerText}>选择新头像</Text></Pressable></View>
          </View>
          {avatarChoicesOpen ? <View style={styles.avatarChoices}>{teachers.map((item) => <Pressable key={item.id} onPress={() => { onTeacherChange(item); setAvatarChoicesOpen(false); }} style={styles.avatarChoice}><Image source={item.image} style={styles.avatarChoiceImage} /><Text style={styles.avatarChoiceLabel}>{item.name}</Text></Pressable>)}</View> : null}
          <View style={styles.formGroup}><Text style={styles.fieldLabel}>用户名</Text><TextInput maxLength={80} value={draft} onChangeText={setDraft} style={styles.input} /></View>
          <View style={styles.modalActions}><AppButton title="取消" variant="secondary" onPress={onClose} style={styles.modalAction} /><AppButton title="保存修改" onPress={() => { onSave(draft.trim()); onClose(); }} style={styles.modalAction} /></View>
        </View>
      </View>
    </Modal>
  );
}

function CalendarCard() {
  const [selectedDay, setSelectedDay] = useState(7);
  const leadingDays = 5;
  const days = Array.from({ length: 31 }, (_, index) => index + 1);
  return (
    <Card style={styles.calendarCard}>
      <View style={styles.calendarHeader}><View><Text style={styles.eyebrow}>LEARNING CALENDAR</Text><Text style={styles.sectionHeadingLarge}>学习日历</Text></View><View style={styles.monthSwitcher}><Pressable accessibilityLabel="上个月" style={styles.monthArrow}><AppIcon name="arrow-left" size={16} color={colors.muted} /></Pressable><Text style={styles.monthLabel}>2026 年 8 月</Text><Pressable accessibilityLabel="下个月" style={styles.monthArrow}><AppIcon name="arrow-right" size={16} color={colors.line} /></Pressable></View></View>
      <View style={styles.calendarWeekdays}>{['一', '二', '三', '四', '五', '六', '日'].map((day) => <Text key={day} style={styles.calendarWeekday}>周{day}</Text>)}</View>
      <View style={styles.calendarGrid}>{Array.from({ length: leadingDays }, (_, index) => <View key={`blank-${index}`} style={styles.calendarCell} />)}{days.map((day) => <Pressable key={day} accessibilityRole="button" onPress={() => setSelectedDay(day)} style={[styles.calendarCell, selectedDay === day && styles.calendarCellSelected]}><Text style={[styles.calendarDay, selectedDay === day && styles.calendarDaySelected]}>{day}</Text>{day === 7 ? <Text style={styles.calendarToday}>今天</Text> : null}</Pressable>)}</View>
      <View style={[styles.calendarSummary, selectedDay === 7 && styles.calendarSummaryActive]}><View style={styles.calendarStatus}><CalendarCheckIcon color={selectedDay === 7 ? colors.muted : colors.subtle} size={17} /><Text style={styles.calendarStatusText}>{selectedDay === 7 ? '未打卡' : '未打卡'}</Text></View><View style={styles.flex}><Text style={styles.calendarSummaryDate}>8 月 {selectedDay} 日</Text><Text style={styles.calendarSummaryNote}>这一天还没有五维评分报告</Text></View></View>
    </Card>
  );
}

function AchievementSummary() {
  const items = [
    { title: '对话历程', category: '开口', icon: <AppIcon name="chat" size={24} color={colors.muted} />, next: '初次开口', unit: '次' },
    { title: '连续学习', category: '连续', icon: <AppIcon name="trophy" size={24} color={colors.muted} />, next: '三日启程', unit: '天' },
    { title: '场景探索', category: '场景', icon: <AppIcon name="grid" size={24} color={colors.muted} />, next: '场景初探', unit: '个' },
    { title: '表达质量', category: '成长', icon: <ChartLineUpIcon size={24} color={colors.muted} />, next: '表达进阶', unit: '分' },
  ];
  return <View style={styles.achievementSection}><View style={styles.achievementHeader}><View style={styles.flex}><Text style={styles.eyebrow}>ACHIEVEMENTS</Text><Text style={styles.sectionHeadingLarge}>成就图鉴</Text><Text style={styles.sectionSubcopy}>每一级进步，都由你真实的练习记录点亮。</Text></View><Text style={styles.achievementCount}>0<Text style={styles.achievementTotal}> / 48 已获得</Text></Text></View><View style={styles.achievementFilters}>{['全部 10', '开口 2', '连续 3', '场景 2', '成长 3'].map((item, index) => <Pressable key={item} style={[styles.filterPill, index === 0 && styles.filterPillActive]}><Text style={[styles.filterPillText, index === 0 && styles.filterPillTextActive]}>{item.split(' ')[0]}</Text><Text style={[styles.filterPillCount, index === 0 && styles.filterPillCountActive]}>{item.split(' ')[1]}</Text></Pressable>)}</View><View style={styles.achievementGrid}>{items.map((item) => <Card key={item.title} style={styles.achievementCard}><View style={styles.achievementCardHeader}><View style={styles.achievementIcon}>{item.icon}</View><View style={styles.flex}><Text style={styles.achievementCategory}>{item.category}</Text><Text style={styles.achievementTitle}>{item.title}</Text></View><Pill>Lv.0</Pill></View><View style={styles.achievementBody}><Text style={styles.achievementLabel}>当前等级</Text><Text style={styles.achievementLevel}>尚未解锁</Text><Text style={styles.achievementNote}>完成“{item.next}”后即可点亮该系列</Text></View><View style={styles.achievementProgress}><View style={styles.rowBetween}><Text style={styles.achievementLabel}>当前进度</Text><Text style={styles.achievementLabel}>下一阶段</Text></View><ProgressBar value={0} max={1} /><View style={styles.rowBetween}><Text style={styles.achievementValue}>0 {item.unit}</Text><Text style={styles.achievementNext}>{item.next}</Text></View></View><View style={styles.achievementFooter}><Text style={styles.achievementFooterText}>查看全部 5 个等级</Text><AppIcon name="chevron-right" size={17} color={colors.subtle} /></View></Card>)}</View></View>;
}

export function Overview({ onBack }: { onBack: () => void }) {
  const { sceneRecords, ieltsRecords, interviewRecords } = useAppModel();
  const trainingRecordCount = sceneRecords.length + ieltsRecords.length + interviewRecords.length;
  const weeklyMinutes = 74;
  const consecutiveLearningDays = 0;
  return (
    <AppScreen contentStyle={styles.pageContent} fixedHeader={<PageHeader fixed onBack={onBack} title="你的学习空间" />}>
      <Text style={styles.pageEyebrow}>PERSONAL OVERVIEW</Text>
      <Text style={styles.pageTitle}>你的学习空间</Text>
      <Text style={styles.pageSubtitle}>把每一次开口变成看得见、可继续的成长记录。</Text>
      <View style={styles.statGrid}><StatCard icon={<ClockIcon color={colors.ink} size={24} />} label="本周学习时长" value={weeklyMinutes} suffix="分钟" /><StatCard icon={<BookOpenTextIcon color={colors.ink} size={24} />} label="已保存学习资产" value={trainingRecordCount} suffix="项" /><StatCard icon={<FireIcon color={colors.ink} size={24} />} label="连续学习天数" value={consecutiveLearningDays} suffix="天" /></View>
      <View style={styles.overviewGrid}><CalendarCard /><Card style={styles.rhythmCard}><Text style={styles.eyebrow}>LAST SEVEN DAYS</Text><Text style={styles.sectionHeadingLarge}>练习节奏</Text><View style={styles.rhythmBars}>{[0, 0, 0, 0, 0, 0, 0].map((value, index) => <View key={index} style={styles.rhythmBarColumn}><View style={[styles.rhythmBar, value > 0 && styles.rhythmBarActive]} /><Text style={styles.rhythmValue}>{value}m</Text><Text style={styles.rhythmDay}>{['周六', '周日', '周一', '周二', '周三', '周四', '周五'][index]}</Text></View>)}</View></Card></View>
      <AchievementSummary />
    </AppScreen>
  );
}

export function Insights({ onBack }: { onBack: () => void }) {
  const [goalsOpen, setGoalsOpen] = useState(false);
  return <AppScreen contentStyle={styles.pageContent} fixedHeader={<PageHeader fixed onBack={onBack} title="学习目标与洞察" action={<Pressable accessibilityRole="button" onPress={() => setGoalsOpen(true)} style={styles.headerAction}><PencilSimpleIcon color={colors.ink} size={18} /><Text style={styles.headerActionText}>调整目标</Text></Pressable>} />}><Text style={styles.pageEyebrow}>LEARNING INSIGHTS</Text><Text style={styles.pageTitle}>学习目标与洞察</Text><Text style={styles.pageSubtitle}>8 月 3 日 至 8 月 9 日</Text><View style={styles.goalGrid}><Card style={styles.goalCard}><View style={styles.goalHeader}><View style={[styles.goalIcon, styles.goalIconGreen]}><ClockIcon color={colors.green} size={22} /></View><View style={styles.flex}><Text style={styles.goalLabel}>口语时长</Text><Text style={styles.goalState}>进行中</Text></View><Text style={styles.goalPercent}>0%</Text></View><Text style={styles.goalValue}>0<Text style={styles.goalSuffix}> / 120 分钟</Text></Text><ProgressBar value={0} max={120} /><Text style={styles.goalRemaining}>还差 120 分钟</Text></Card><Card style={styles.goalCard}><View style={styles.goalHeader}><View style={[styles.goalIcon, styles.goalIconBlue]}><ChartLineUpIcon color="#35659B" size={22} /></View><View style={styles.flex}><Text style={styles.goalLabel}>训练次数</Text><Text style={styles.goalState}>进行中</Text></View><Text style={styles.goalPercent}>0%</Text></View><Text style={styles.goalValue}>0<Text style={styles.goalSuffix}> / 5 次</Text></Text><ProgressBar value={0} max={5} /><Text style={styles.goalRemaining}>还差 5 次</Text></Card></View><View style={styles.divider} /><View style={styles.sectionTitleRow}><View><Text style={styles.eyebrow}>TRAINING MIX</Text><Text style={styles.sectionHeadingLarge}>本周训练类型占比</Text></View><Text style={styles.sectionSubcopy}>按有效训练时长统计</Text></View><View style={styles.emptyInsight}><ClockIcon color={colors.subtle} size={32} /><Text style={styles.emptyInsightText}>本周暂无有效训练记录</Text></View>{goalsOpen ? <WeeklyGoalsModal onClose={() => setGoalsOpen(false)} /> : null}</AppScreen>;
}

function WeeklyGoalsModal({ onClose }: { onClose: () => void }) {
  const [minutes, setMinutes] = useState('120');
  const [sessions, setSessions] = useState('5');
  return <Modal transparent visible animationType="fade" onRequestClose={onClose}><View style={styles.modalRoot}><Pressable style={styles.modalBackdrop} onPress={onClose} /><View style={styles.goalsModal}><View style={styles.editModalTop}><View style={styles.flex}><Text style={styles.modalEyebrow}>WEEKLY GOALS</Text><Text style={styles.modalTitle}>调整每周目标</Text></View><Pressable accessibilityRole="button" accessibilityLabel="关闭每周目标" onPress={onClose} style={styles.closeButton}><XIcon color={colors.ink} size={20} /></Pressable></View><View style={styles.goalFormGroup}><Text style={styles.fieldLabel}>口语时长</Text><View style={styles.goalInputRow}><TextInput keyboardType="number-pad" value={minutes} onChangeText={setMinutes} style={styles.goalInput} /><Text style={styles.goalInputSuffix}>分钟 / 周</Text></View></View><View style={styles.goalFormGroup}><Text style={styles.fieldLabel}>训练次数</Text><View style={styles.goalInputRow}><TextInput keyboardType="number-pad" value={sessions} onChangeText={setSessions} style={styles.goalInput} /><Text style={styles.goalInputSuffix}>次 / 周</Text></View></View><View style={styles.modalActions}><AppButton title="取消" variant="secondary" onPress={onClose} style={styles.modalAction} /><AppButton title="保存目标" onPress={onClose} style={styles.modalAction} /></View></View></View></Modal>;
}

export function Membership({ onBack }: { onBack: () => void }) {
  const { membership, setMembership } = useAppModel();
  const plans = [
    { name: '免费版', price: '0', note: '适合轻量体验与每日开口', features: ['每天 5 分钟自由对话', '每天 1 次普通场景', '全部六位 AI 老师'] },
    { name: '专业版', price: '48', note: '适合稳定提升日常与职场口语', features: ['每月 600 分钟自由对话', '每月 50 次普通场景', '全部六位 AI 老师'] },
    { name: '特训版', price: '198', note: '适合雅思备考与英文面试', features: ['包含专业版全部权益', 'IELTS Part 1 / 2 / 3 模拟', '英文面试与材料分析', '每天 5 次特训，共用 150 次/月'] },
  ];
  return <AppScreen contentStyle={styles.pageContent} fixedHeader={<PageHeader fixed onBack={onBack} title="会员与订阅中心" />}><Text style={styles.pageEyebrow}>MEMBERSHIP & PRICING</Text><Text style={styles.pageTitle}>会员与订阅中心</Text><Text style={styles.pageSubtitle}>练习额度平时不会打扰你，只会在不足 20% 或无法开始时提醒。</Text><View style={styles.planGrid}>{plans.map((plan) => { const selected = membership === plan.name; return <Card key={plan.name} style={[styles.planCard, selected && styles.planCardSelected]}><View>{selected ? <Pill dark>当前方案</Pill> : plan.name === '专业版' ? <Pill>推荐</Pill> : null}<Text style={styles.planName}>{plan.name}</Text><Text style={styles.planNote}>{plan.note}</Text></View><Text style={styles.planPrice}><Text style={styles.planCurrency}>¥</Text>{plan.price}<Text style={styles.planCycle}>/月</Text></Text><View style={styles.planFeatures}>{plan.features.map((feature) => <View key={feature} style={styles.planFeature}><Text style={styles.checkMark}>✓</Text><Text style={styles.planFeatureText}>{feature}</Text></View>)}</View><AppButton title={selected ? '当前方案' : `升级${plan.name}`} variant={selected ? 'soft' : plan.name === '专业版' ? 'primary' : 'secondary'} disabled={selected} onPress={() => setMembership(plan.name)} /></Card>; })}</View></AppScreen>;
}

export function AssistantSettings({ onBack }: { onBack: () => void }) {
  const { speed, setSpeed, level, setLevel, teacher, setTeacher } = useAppModel();
  const [translation, setTranslation] = useState(true);
  const [sound, setSound] = useState(true);
  return <AppScreen contentStyle={styles.pageContent} fixedHeader={<PageHeader fixed onBack={onBack} title="AI 助手设置" action={<View style={styles.syncState}><Text style={styles.syncDot}>✓</Text><Text style={styles.syncText}>设置已同步</Text></View>} />}><Text style={styles.pageEyebrow}>ASSISTANT SETTINGS</Text><Text style={styles.pageTitle}>AI 助手设置</Text><Text style={styles.pageSubtitle}>只调整真正影响对话体验的选项。</Text><View style={styles.settingsList}><Card style={styles.settingCard}><View style={styles.settingIntro}><Text style={styles.settingTitle}>对话语速</Text><Text style={styles.settingNote}>选择更舒适的回应节奏。</Text></View><SpeedSelector value={speed} onChange={setSpeed} /></Card><Card style={styles.settingCard}><View style={styles.settingIntro}><Text style={styles.settingTitle}>英语水平</Text><Text style={styles.settingNote}>新对话会按照该难度调整表达。</Text></View><LevelSelector value={level} onChange={setLevel} /></Card><Card style={styles.settingCard}><View style={styles.settingIntro}><Text style={styles.settingTitle}>AI 老师</Text><Text style={styles.settingNote}>每位老师有固定口音和陪练方式。</Text></View><TeacherSelector selectedId={teacher.id} onSelect={setTeacher} /></Card><Card style={styles.settingCard}><View style={styles.settingRow}><View style={styles.flex}><Text style={styles.settingTitle}>自动显示翻译</Text><Text style={styles.settingNote}>新字幕出现时同时显示中文参考。</Text></View><Switch value={translation} onValueChange={setTranslation} trackColor={{ true: colors.ink }} /></View><View style={styles.settingRow}><View style={styles.flex}><Text style={styles.settingTitle}>自动播放示范音频</Text><Text style={styles.settingNote}>训练步骤切换后自动播放 AI 示范。</Text></View><Switch value={sound} onValueChange={setSound} trackColor={{ true: colors.ink }} /></View></Card></View></AppScreen>;
}

export function AccountSettings({ onBack, onLogout }: { onBack: () => void; onLogout?: () => void }) {
  const { nickname, setNickname } = useAppModel();
  const [draft, setDraft] = useState(nickname);
  return <AppScreen contentStyle={styles.pageContent} fixedHeader={<PageHeader fixed onBack={onBack} title="账号与安全" />}><View style={styles.accountHero}><View style={styles.accountShield}><ShieldCheckIcon color={colors.green} size={27} /></View><Text style={styles.pageEyebrow}>ACCOUNT & SECURITY</Text><Text style={styles.pageTitle}>账号与安全</Text><Text style={styles.pageSubtitle}>管理登录凭据与当前登录状态</Text></View><SectionTitle eyebrow="LOGIN DETAILS" title="登录信息" /><Card style={styles.accountCard}><ListRow title="登录邮箱" subtitle={email} icon="chat" meta="当前账号" /><ListRow title="登录密码" subtitle="密码已设置" icon="lock" meta="修改密码" onPress={() => undefined} /></Card><SectionTitle eyebrow="CURRENT SESSION" title="当前登录" /><Card style={styles.accountCard}><ListRow title="UniSpeaking Mobile" subtitle="iOS · 此设备" icon="user" meta="已登录" /><ListRow title="退出当前账号" subtitle="清除当前设备中的登录信息" icon="logout" danger meta="退出登录" onPress={onLogout} /></Card><View style={styles.formGroup}><Text style={styles.fieldLabel}>展示用户名</Text><TextInput value={draft} onChangeText={setDraft} style={styles.input} /><AppButton title="保存用户名" onPress={() => setNickname(draft.trim() || nickname)} /></View></AppScreen>;
}

export function HelpCenter({ onBack }: { onBack: () => void }) {
  const helpItems = [['常见问题', '了解训练、报告和学习资产的使用方式'], ['训练与报告', '查看训练记录、评分和复练入口'], ['账户与安全', '管理登录信息、设置和跨端同步']];
  return <AppScreen contentStyle={styles.pageContent} fixedHeader={<PageHeader fixed onBack={onBack} title="帮助中心" />}><Text style={styles.pageEyebrow}>HELP CENTER</Text><Text style={styles.pageTitle}>帮助中心</Text><Text style={styles.pageSubtitle}>遇到问题时，从这里找到清晰的解决路径。</Text><View style={styles.helpList}>{helpItems.map(([title, note]) => <Card key={title} style={styles.helpCard}><View style={styles.flex}><Text style={styles.helpTitle}>{title}</Text><Text style={styles.helpNote}>{note}</Text></View><AppIcon name="chevron-right" size={18} color={colors.subtle} /></Card>)}</View><Card style={styles.helpContact}><LifebuoyIcon color={colors.ink} size={25} /><View style={styles.flex}><Text style={styles.helpTitle}>仍然需要帮助？</Text><Text style={styles.helpNote}>联系 UniSpeaking 支持团队，我们会继续协助你。</Text></View></Card></AppScreen>;
}

export function AboutProduct({ onBack }: { onBack: () => void }) {
  return <AppScreen contentStyle={styles.pageContent} fixedHeader={<PageHeader fixed onBack={onBack} title="关于 UniSpeaking" />}><View style={styles.aboutBrand}><Image source={brandAssets.mark} style={styles.aboutMark} /><Image source={brandAssets.wordmark} style={styles.aboutWordmark} contentFit="contain" /></View><Text style={styles.pageEyebrow}>ABOUT UNISPEAKING</Text><Text style={styles.pageTitle}>关于 UniSpeaking</Text><Text style={styles.pageSubtitle}>专注真实表达的 AI 英语口语训练工具</Text><View style={styles.divider} /><SectionTitle eyebrow="PRODUCT INFORMATION" title="产品信息" /><View style={styles.productInfo}><View style={styles.productRow}><Text style={styles.productLabel}>当前版本</Text><Text style={styles.productValue}>v1.0</Text></View><View style={styles.productRow}><Text style={styles.productLabel}>产品形态</Text><Text style={styles.productValue}>Mobile App</Text></View><View style={styles.productRow}><Text style={styles.productLabel}>客服邮箱</Text><Text style={styles.productValue}>support@unispeaking.example</Text></View><View style={styles.productRow}><Text style={styles.productLabel}>更新方式</Text><Text style={styles.productValue}>自动更新</Text></View></View></AppScreen>;
}

export function ProfileHome({ onOpen, onLogout }: { onOpen: (route: ProfileRoute) => void; onLogout?: () => void }) {
  const { nickname, setNickname, teacher, setTeacher } = useAppModel();
  const [editOpen, setEditOpen] = useState(false);
  return <AppScreen contentStyle={styles.profileContent}><View style={styles.profileUser}><View style={styles.profileAvatarWrap}><View style={styles.profileAvatar}><Image source={teacher.image} style={styles.profileAvatarImage} contentFit="contain" /></View><Pressable accessibilityRole="button" accessibilityLabel="编辑用户名和头像" onPress={() => setEditOpen(true)} style={styles.profileEdit}><PencilSimpleIcon color={colors.muted} size={16} /></Pressable></View><View style={styles.flex}><Text style={styles.profileName}>{nickname}</Text><Text style={styles.profileEmail}>{email}</Text></View></View><View style={styles.profileMenu}><ProfileMenuItem icon={<UserCircleIcon color={colors.ink} size={25} />} title="个人概览" active onPress={() => onOpen('overview')} /><ProfileMenuItem icon={<ChartLineUpIcon color={colors.ink} size={25} />} title="学习目标与洞察" onPress={() => onOpen('insights')} /><ProfileMenuItem icon={<CrownIcon color={colors.ink} size={25} />} title="会员权益" onPress={() => onOpen('membership')} /><ProfileMenuItem icon={<SlidersHorizontalIcon color={colors.ink} size={25} />} title="助手设置" onPress={() => onOpen('assistant')} /><ProfileMenuItem icon={<ShieldCheckIcon color={colors.ink} size={25} />} title="账号与安全" onPress={() => onOpen('account')} /><ProfileMenuItem icon={<LifebuoyIcon color={colors.ink} size={25} />} title="帮助中心" onPress={() => onOpen('help')} /><ProfileMenuItem icon={<InfoIcon color={colors.ink} size={25} />} title="关于产品" onPress={() => onOpen('about')} /></View><Pressable accessibilityRole="button" onPress={onLogout} style={styles.logout}><AppIcon name="logout" size={19} color={colors.red} /><Text style={styles.logoutText}>退出登录</Text></Pressable>{editOpen ? <ProfileEditModal teacher={teacher} nickname={nickname} onClose={() => setEditOpen(false)} onSave={(value) => setNickname(value || nickname)} onTeacherChange={setTeacher} /> : null}</AppScreen>;
}

export function ProfileScreen() {
  const [route, setRoute] = useState<ProfileRoute>('home');
  if (route === 'overview') return <Overview onBack={() => setRoute('home')} />;
  if (route === 'insights') return <Insights onBack={() => setRoute('home')} />;
  if (route === 'membership') return <Membership onBack={() => setRoute('home')} />;
  if (route === 'assistant') return <AssistantSettings onBack={() => setRoute('home')} />;
  if (route === 'account') return <AccountSettings onBack={() => setRoute('home')} />;
  if (route === 'help') return <HelpCenter onBack={() => setRoute('home')} />;
  if (route === 'about') return <AboutProduct onBack={() => setRoute('home')} />;
  return <ProfileHome onOpen={setRoute} />;
}

const styles = StyleSheet.create({
  pageContent: { paddingBottom: 110 },
  profileContent: { minHeight: '100%', paddingHorizontal: 28, paddingTop: 32, paddingBottom: 110 },
  flex: { flex: 1 },
  rowBetween: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  pressed: { opacity: 0.7, transform: [{ scale: 0.985 }] },
  profileUser: { minHeight: 116, paddingHorizontal: 2, flexDirection: 'row', alignItems: 'center', gap: 18 },
  profileAvatarWrap: { position: 'relative', width: 92, height: 92 },
  profileAvatar: { width: 92, height: 92, overflow: 'hidden', alignItems: 'center', justifyContent: 'flex-end', borderRadius: 46, backgroundColor: colors.soft },
  profileAvatarImage: { width: 92, height: 112, marginBottom: -12 },
  profileEdit: { position: 'absolute', top: -4, right: -4, width: 36, height: 36, alignItems: 'center', justifyContent: 'center', borderWidth: 1, borderColor: colors.white, borderRadius: 18, backgroundColor: colors.white, shadowColor: colors.ink, shadowOpacity: 0.12, shadowRadius: 8, elevation: 3 },
  profileName: { color: colors.ink, fontSize: 28, lineHeight: 34, fontWeight: '600' },
  profileEmail: { marginTop: 7, color: colors.muted, fontSize: 16, lineHeight: 22, fontWeight: '300' },
  profileMenu: { marginTop: 26, gap: 7 },
  profileMenuItem: { minHeight: 58, paddingHorizontal: 16, flexDirection: 'row', alignItems: 'center', gap: 14, borderRadius: 14 },
  profileMenuItemActive: { backgroundColor: colors.soft },
  profileMenuIcon: { width: 28, alignItems: 'center' },
  profileMenuTitle: { color: colors.ink, fontSize: 20, lineHeight: 28, fontWeight: '400' },
  logout: { minHeight: 52, marginTop: 26, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 9, borderRadius: 14, backgroundColor: colors.redSoft },
  logoutText: { color: colors.red, fontSize: 13, fontWeight: '500' },
  pageEyebrow: { marginTop: 9, color: colors.subtle, fontSize: 11, fontWeight: '600', letterSpacing: 1.7 },
  eyebrow: { color: colors.subtle, fontSize: 10, fontWeight: '600', letterSpacing: 1.7 },
  pageTitle: { marginTop: 8, color: colors.ink, fontSize: 35, lineHeight: 43, fontWeight: '600', letterSpacing: -1.2 },
  pageSubtitle: { marginTop: 7, color: colors.muted, fontSize: 15, lineHeight: 22, fontWeight: '300' },
  statGrid: { marginTop: 24, gap: 10 },
  statCard: { minHeight: 92, padding: 16, flexDirection: 'row', alignItems: 'center', gap: 13, borderWidth: 1, borderColor: colors.line, borderRadius: 17, backgroundColor: colors.white },
  statIcon: { width: 38, height: 38, alignItems: 'center', justifyContent: 'center' },
  statLabel: { color: colors.muted, fontSize: 13, fontWeight: '300' },
  statValue: { marginTop: 3, color: colors.ink, fontSize: 26, lineHeight: 31, fontWeight: '600' },
  statSuffix: { fontSize: 12, fontWeight: '500' },
  overviewGrid: { marginTop: 16, gap: 16 },
  calendarCard: { gap: 16 },
  calendarHeader: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 8 },
  sectionHeadingLarge: { marginTop: 5, color: colors.ink, fontSize: 25, lineHeight: 31, fontWeight: '600' },
  monthSwitcher: { minHeight: 40, paddingHorizontal: 7, flexDirection: 'row', alignItems: 'center', gap: 4, borderWidth: 1, borderColor: colors.line, borderRadius: 22 },
  monthArrow: { width: 26, height: 28, alignItems: 'center', justifyContent: 'center' },
  monthLabel: { color: colors.ink, fontSize: 13, fontWeight: '600' },
  calendarWeekdays: { flexDirection: 'row', justifyContent: 'space-between' },
  calendarWeekday: { width: 30, color: colors.subtle, fontSize: 10, textAlign: 'center', fontWeight: '300' },
  calendarGrid: { flexDirection: 'row', flexWrap: 'wrap', rowGap: 8 },
  calendarCell: { width: '14.285%', minHeight: 34, alignItems: 'center', justifyContent: 'center', borderRadius: 9 },
  calendarCellSelected: { backgroundColor: colors.ink },
  calendarDay: { color: colors.muted, fontSize: 12, fontWeight: '300' },
  calendarDaySelected: { color: colors.white, fontWeight: '600' },
  calendarToday: { marginTop: 1, color: colors.white, fontSize: 7 },
  calendarSummary: { minHeight: 60, paddingHorizontal: 12, flexDirection: 'row', alignItems: 'center', gap: 10, borderRadius: 15, backgroundColor: colors.soft },
  calendarSummaryActive: { backgroundColor: colors.soft },
  calendarStatus: { minHeight: 34, paddingHorizontal: 10, flexDirection: 'row', alignItems: 'center', gap: 5, borderRadius: 17, backgroundColor: '#EDEDE9' },
  calendarStatusText: { color: colors.muted, fontSize: 11, fontWeight: '500' },
  calendarSummaryDate: { color: colors.ink, fontSize: 15, fontWeight: '600' },
  calendarSummaryNote: { marginTop: 2, color: colors.muted, fontSize: 11, fontWeight: '300' },
  rhythmCard: { minHeight: 220, gap: 6 },
  rhythmBars: { minHeight: 140, paddingTop: 16, flexDirection: 'row', alignItems: 'flex-end', justifyContent: 'space-between', gap: 5 },
  rhythmBarColumn: { flex: 1, alignItems: 'center', justifyContent: 'flex-end', gap: 4 },
  rhythmBar: { width: '70%', height: 10, borderRadius: 6, backgroundColor: '#E4E4DF' },
  rhythmBarActive: { backgroundColor: colors.ink },
  rhythmValue: { color: colors.muted, fontSize: 9 },
  rhythmDay: { color: colors.subtle, fontSize: 9 },
  achievementSection: { marginTop: 26 },
  achievementHeader: { flexDirection: 'row', alignItems: 'flex-end', gap: 12 },
  sectionSubcopy: { marginTop: 5, color: colors.muted, fontSize: 12, lineHeight: 18, fontWeight: '300' },
  achievementCount: { color: colors.ink, fontSize: 25, fontWeight: '600' },
  achievementTotal: { color: colors.muted, fontSize: 11, fontWeight: '300' },
  achievementFilters: { marginTop: 16, flexDirection: 'row', gap: 7 },
  filterPill: { minHeight: 34, paddingHorizontal: 11, flexDirection: 'row', alignItems: 'center', gap: 5, borderWidth: 1, borderColor: colors.line, borderRadius: 17, backgroundColor: colors.white },
  filterPillActive: { borderColor: colors.ink, backgroundColor: colors.ink },
  filterPillText: { color: colors.muted, fontSize: 11 },
  filterPillTextActive: { color: colors.white },
  filterPillCount: { minWidth: 16, color: colors.subtle, fontSize: 10, textAlign: 'center' },
  filterPillCountActive: { color: colors.white },
  achievementGrid: { marginTop: 12, gap: 12 },
  achievementCard: { padding: 0, overflow: 'hidden' },
  achievementCardHeader: { minHeight: 70, padding: 14, flexDirection: 'row', alignItems: 'center', gap: 10, borderBottomWidth: 1, borderBottomColor: colors.line },
  achievementIcon: { width: 42, height: 42, alignItems: 'center', justifyContent: 'center', borderWidth: 1, borderColor: colors.line, borderRadius: 13, backgroundColor: colors.white },
  achievementCategory: { color: colors.subtle, fontSize: 10, fontWeight: '300' },
  achievementTitle: { marginTop: 3, color: colors.ink, fontSize: 18, fontWeight: '600' },
  achievementBody: { minHeight: 92, padding: 16, gap: 4 },
  achievementLabel: { color: colors.subtle, fontSize: 10, fontWeight: '300' },
  achievementLevel: { color: colors.muted, fontSize: 21, fontWeight: '600' },
  achievementNote: { color: colors.muted, fontSize: 11, fontWeight: '300' },
  achievementProgress: { padding: 16, gap: 8, borderTopWidth: 1, borderTopColor: colors.line },
  achievementValue: { color: colors.ink, fontSize: 12, fontWeight: '500' },
  achievementNext: { color: colors.muted, fontSize: 12, fontWeight: '500' },
  achievementFooter: { minHeight: 46, paddingHorizontal: 16, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', backgroundColor: colors.soft },
  achievementFooterText: { color: colors.muted, fontSize: 11, fontWeight: '500' },
  headerAction: { minHeight: 36, paddingHorizontal: 11, flexDirection: 'row', alignItems: 'center', gap: 6, borderWidth: 1, borderColor: colors.line, borderRadius: 18 },
  headerActionText: { color: colors.ink, fontSize: 11, fontWeight: '500' },
  goalGrid: { marginTop: 24, gap: 12 },
  goalCard: { minHeight: 190, gap: 15 },
  goalHeader: { flexDirection: 'row', alignItems: 'center', gap: 10 },
  goalIcon: { width: 42, height: 42, alignItems: 'center', justifyContent: 'center', borderRadius: 12 },
  goalIconGreen: { backgroundColor: colors.greenSoft },
  goalIconBlue: { backgroundColor: '#EEF4FC' },
  goalLabel: { color: colors.muted, fontSize: 13, fontWeight: '300' },
  goalState: { marginTop: 3, color: colors.ink, fontSize: 17, fontWeight: '600' },
  goalPercent: { color: colors.muted, fontSize: 12, fontWeight: '500' },
  goalValue: { color: colors.ink, fontSize: 39, lineHeight: 45, fontWeight: '600' },
  goalSuffix: { color: colors.muted, fontSize: 15, fontWeight: '300' },
  goalRemaining: { color: colors.muted, fontSize: 12, fontWeight: '300' },
  goalFormGroup: { gap: 8 },
  goalInputRow: { minHeight: 54, paddingHorizontal: 14, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', borderWidth: 1, borderColor: colors.line, borderRadius: 13, backgroundColor: colors.white },
  goalInput: { flex: 1, color: colors.ink, fontSize: 18, fontWeight: '600' },
  goalInputSuffix: { color: colors.muted, fontSize: 14, fontWeight: '500' },
  divider: { height: StyleSheet.hairlineWidth, marginVertical: 24, backgroundColor: colors.line },
  sectionTitleRow: { flexDirection: 'row', alignItems: 'flex-end', justifyContent: 'space-between', gap: 10 },
  emptyInsight: { minHeight: 200, alignItems: 'center', justifyContent: 'center', gap: 10 },
  emptyInsightText: { color: colors.muted, fontSize: 14, fontWeight: '300' },
  settingsList: { marginTop: 24, gap: 12 },
  settingCard: { gap: 16 },
  settingIntro: { gap: 4 },
  settingTitle: { color: colors.ink, fontSize: 18, lineHeight: 24, fontWeight: '600' },
  settingNote: { color: colors.muted, fontSize: 12, lineHeight: 18, fontWeight: '300' },
  settingRow: { paddingVertical: 7, flexDirection: 'row', alignItems: 'center', gap: 12 },
  syncState: { flexDirection: 'row', alignItems: 'center', gap: 5 },
  syncDot: { color: colors.green, fontSize: 16, fontWeight: '600' },
  syncText: { color: colors.muted, fontSize: 11 },
  planGrid: { marginTop: 24, gap: 14 },
  planCard: { minHeight: 340, gap: 18 },
  planCardSelected: { borderWidth: 1.5, borderColor: colors.ink },
  planName: { marginTop: 10, color: colors.ink, fontSize: 28, lineHeight: 33, fontWeight: '600' },
  planNote: { marginTop: 7, color: colors.muted, fontSize: 13, lineHeight: 20, fontWeight: '300' },
  planPrice: { color: colors.ink, fontSize: 50, lineHeight: 56, fontWeight: '600' },
  planCurrency: { fontSize: 16, fontWeight: '500' },
  planCycle: { color: colors.muted, fontSize: 14, fontWeight: '300' },
  planFeatures: { gap: 11 },
  planFeature: { flexDirection: 'row', alignItems: 'center', gap: 9 },
  checkMark: { color: colors.muted, fontSize: 17 },
  planFeatureText: { color: colors.muted, fontSize: 13, fontWeight: '300' },
  accountHero: { marginBottom: 22 },
  accountShield: { width: 52, height: 52, marginBottom: 18, alignItems: 'center', justifyContent: 'center', borderRadius: 14, backgroundColor: colors.greenSoft },
  accountCard: { paddingHorizontal: 16, paddingVertical: 2, marginBottom: 20 },
  helpList: { marginTop: 24, gap: 10 },
  helpCard: { minHeight: 76, flexDirection: 'row', alignItems: 'center', gap: 12 },
  helpTitle: { color: colors.ink, fontSize: 17, fontWeight: '600' },
  helpNote: { marginTop: 4, color: colors.muted, fontSize: 12, lineHeight: 18, fontWeight: '300' },
  helpContact: { marginTop: 18, flexDirection: 'row', alignItems: 'center', gap: 12, backgroundColor: colors.soft },
  aboutBrand: { marginTop: 20, flexDirection: 'row', alignItems: 'center', gap: 10 },
  aboutMark: { width: 34, height: 34, borderRadius: 8 },
  aboutWordmark: { width: 150, height: 32 },
  productInfo: { marginTop: 14, borderTopWidth: 1, borderTopColor: colors.line },
  productRow: { minHeight: 54, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', borderBottomWidth: 1, borderBottomColor: colors.line },
  productLabel: { color: colors.muted, fontSize: 12, fontWeight: '300' },
  productValue: { color: colors.ink, fontSize: 13, fontWeight: '600' },
  modalRoot: { flex: 1, justifyContent: 'center', padding: 18 },
  modalBackdrop: { position: 'absolute', top: 0, right: 0, bottom: 0, left: 0, backgroundColor: 'rgba(20,20,19,0.38)' },
  editModal: { padding: 22, gap: 18, borderRadius: 22, backgroundColor: colors.white },
  goalsModal: { padding: 22, gap: 20, borderRadius: 22, backgroundColor: colors.white },
  editModalTop: { flexDirection: 'row', alignItems: 'flex-start', gap: 12 },
  modalEyebrow: { color: colors.subtle, fontSize: 11, fontWeight: '600', letterSpacing: 1.7 },
  modalTitle: { marginTop: 9, color: colors.ink, fontSize: 27, lineHeight: 34, fontWeight: '600' },
  modalLead: { marginTop: 7, color: colors.muted, fontSize: 14, lineHeight: 21, fontWeight: '300' },
  closeButton: { width: 38, height: 38, alignItems: 'center', justifyContent: 'center', borderRadius: 19, backgroundColor: colors.soft },
  editAvatarCard: { padding: 14, flexDirection: 'row', alignItems: 'center', gap: 14, borderWidth: 1, borderColor: colors.line, borderRadius: 15, backgroundColor: colors.paper },
  editAvatarImage: { width: 76, height: 76, borderRadius: 15, backgroundColor: colors.soft },
  editAvatarTitle: { color: colors.ink, fontSize: 16, fontWeight: '600' },
  editAvatarNote: { marginTop: 5, color: colors.muted, fontSize: 11, lineHeight: 16, fontWeight: '300' },
  avatarPicker: { alignSelf: 'flex-start', minHeight: 34, marginTop: 11, paddingHorizontal: 13, alignItems: 'center', justifyContent: 'center', borderWidth: 1, borderColor: colors.line, borderRadius: 18 },
  avatarPickerText: { color: colors.ink, fontSize: 11, fontWeight: '600' },
  avatarChoices: { flexDirection: 'row', gap: 8 },
  avatarChoice: { alignItems: 'center', gap: 4 },
  avatarChoiceImage: { width: 40, height: 40, borderRadius: 20, backgroundColor: colors.soft },
  avatarChoiceLabel: { color: colors.muted, fontSize: 9 },
  formGroup: { gap: 8 },
  fieldLabel: { color: colors.ink, fontSize: 13, fontWeight: '600' },
  input: { minHeight: 52, paddingHorizontal: 14, color: colors.ink, fontSize: 15, fontWeight: '300', borderWidth: 1, borderColor: colors.line, borderRadius: 13, backgroundColor: colors.white },
  modalActions: { marginTop: 8, flexDirection: 'row', justifyContent: 'flex-end', gap: 10 },
  modalAction: { flex: 1 },
});
