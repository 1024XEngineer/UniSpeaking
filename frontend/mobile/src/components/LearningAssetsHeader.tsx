import { useState, type ReactNode } from 'react';
import { Modal, Platform, Pressable, StyleSheet, Text, View } from 'react-native';
import { ArrowRightIcon } from 'phosphor-react-native/src/icons/ArrowRight';
import { BookOpenTextIcon } from 'phosphor-react-native/src/icons/BookOpenText';
import { BriefcaseIcon } from 'phosphor-react-native/src/icons/Briefcase';

import { HeaderIconButton, MainModuleHeader } from '@/components/ui';
import { colors } from '@/theme/tokens';

export type LearningAssetModule = 'scenes' | 'ielts' | 'interview';

const assetHeaderThemes = {
  scenes: {
    englishTitle: 'LEARNING ASSETS',
    title: '学习资产',
    background: colors.paper,
    border: colors.line,
    icon: colors.ink,
    text: colors.ink,
  },
  ielts: {
    englishTitle: 'IELTS ASSETS',
    title: '雅思学习资产',
    background: '#FCFAFF',
    border: '#E6DBFF',
    icon: '#5A3DBB',
    text: '#171323',
  },
  interview: {
    englishTitle: 'INTERVIEW ASSETS',
    title: '英文面试资产',
    background: '#DCEBFA',
    border: '#B9D3EC',
    icon: '#123255',
    text: '#123255',
  },
} as const;

type ModuleOption = {
  id: LearningAssetModule;
  title: string;
  note: string;
  icon: ReactNode;
  onPress?: () => void;
};

export function LearningAssetsHeader({
  current,
  onScenes,
  onIelts,
  onInterview,
}: {
  current: LearningAssetModule;
  onScenes?: () => void;
  onIelts?: () => void;
  onInterview?: () => void;
}) {
  const [open, setOpen] = useState(false);
  const theme = assetHeaderThemes[current];
  const allOptions: ModuleOption[] = [
    {
      id: 'scenes',
      title: '场景训练学习资产',
      note: '对话记录、纠错与场景复练',
      icon: <BookOpenTextIcon color={colors.ink} size={20} weight="fill" />,
      onPress: onScenes,
    },
    {
      id: 'ielts',
      title: '雅思学习资产',
      note: '专项训练、模考与能力趋势',
      icon: <Text style={styles.ieltsMark}>IELTS</Text>,
      onPress: onIelts,
    },
    {
      id: 'interview',
      title: '英文面试资产',
      note: '历史报告与口语复盘',
      icon: <BriefcaseIcon color={colors.ink} size={20} weight="fill" />,
      onPress: onInterview,
    },
  ];
  const options = allOptions.filter((option) => option.id !== current);

  const selectModule = (option: ModuleOption) => {
    setOpen(false);
    option.onPress?.();
  };

  const menu = (
    <View style={[styles.menuRoot, { paddingTop: 74 }]}>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel="关闭学习资产菜单"
        onPress={() => setOpen(false)}
        style={styles.menuBackdrop}
      />
      <View style={[styles.menuCard, { borderColor: theme.border, backgroundColor: theme.background }]}>
        {options.map((option) => (
          <Pressable
            key={option.id}
            accessibilityRole="button"
            accessibilityLabel={`进入${option.title}`}
            onPress={() => selectModule(option)}
            style={({ pressed }) => [styles.menuRow, pressed && styles.pressed]}
          >
            <View style={[styles.menuIcon, { backgroundColor: current === 'ielts' ? '#F3EEFF' : current === 'interview' ? '#EAF4FF' : colors.soft }]}>{option.icon}</View>
            <View style={styles.flex}>
              <Text style={[styles.menuTitle, { color: theme.text }]}>{option.title}</Text>
              <Text style={styles.menuNote}>{option.note}</Text>
            </View>
            <ArrowRightIcon color={colors.subtle} size={17} weight="bold" />
          </Pressable>
        ))}
      </View>
    </View>
  );

  return (
    <>
      <MainModuleHeader
        englishTitle={theme.englishTitle}
        title={theme.title}
        style={{ backgroundColor: theme.background, borderBottomColor: theme.border }}
        action={(
          <HeaderIconButton
            color={theme.icon}
            icon="menu"
            accessibilityLabel="切换学习资产模块"
            onPress={() => setOpen(true)}
          />
        )}
      />
      {Platform.OS === 'web' ? (
        open ? <View style={styles.webMenuLayer}>{menu}</View> : null
      ) : (
        <Modal transparent visible={open} animationType="fade" onRequestClose={() => setOpen(false)}>
          {menu}
        </Modal>
      )}
    </>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  pressed: { opacity: 0.72, transform: [{ scale: 0.985 }] },
  webMenuLayer: { position: 'absolute', top: 0, right: 0, bottom: 0, left: 0, zIndex: 20 },
  menuRoot: { flex: 1, paddingRight: 22, alignItems: 'flex-end' },
  menuBackdrop: {
    position: 'absolute',
    top: 0,
    right: 0,
    bottom: 0,
    left: 0,
    backgroundColor: 'rgba(21,21,20,0.08)',
  },
  menuCard: {
    width: 302,
    padding: 10,
    borderWidth: 1,
    borderColor: colors.line,
    borderRadius: 18,
    backgroundColor: colors.white,
    shadowColor: colors.ink,
    shadowOffset: { width: 0, height: 12 },
    shadowOpacity: 0.16,
    shadowRadius: 28,
    elevation: 12,
    boxShadow: '0px 12px 30px rgba(21,21,20,0.16)',
  },
  menuRow: { minHeight: 70, padding: 10, flexDirection: 'row', alignItems: 'center', gap: 11, borderRadius: 13 },
  menuIcon: { width: 42, height: 42, alignItems: 'center', justifyContent: 'center', borderRadius: 12, backgroundColor: colors.soft },
  ieltsMark: { color: colors.ink, fontSize: 9, fontWeight: '600' },
  menuTitle: { color: colors.ink, fontSize: 14, fontWeight: '500' },
  menuNote: { marginTop: 4, color: colors.muted, fontSize: 11, fontWeight: '300' },
});
