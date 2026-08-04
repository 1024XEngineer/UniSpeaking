import { Image } from 'expo-image';
import { useEffect, useState } from 'react';
import {
  KeyboardAvoidingView,
  Modal,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { AppButton, AppIcon } from '@/components/ui';
import { colors, levels, speedOptions, teachers, type Teacher } from '@/theme/tokens';

export function SpeedSelector({
  value,
  onChange,
}: {
  value: string;
  onChange: (value: string) => void;
}) {
  return (
    <View style={styles.segment}>
      {speedOptions.map((item) => {
        const selected = item === value;
        return (
          <Pressable
            key={item}
            accessibilityRole="radio"
            accessibilityState={{ checked: selected }}
            onPress={() => onChange(item)}
            style={[styles.segmentItem, selected && styles.segmentItemSelected]}
          >
            <Text style={[styles.segmentText, selected && styles.segmentTextSelected]}>{item}</Text>
          </Pressable>
        );
      })}
    </View>
  );
}

export function LevelSelector({
  value,
  onChange,
}: {
  value: string;
  onChange: (value: string) => void;
}) {
  return (
    <View style={styles.options}>
      {levels.map((item) => {
        const selected = item.id === value;
        return (
          <Pressable
            key={item.id}
            onPress={() => onChange(item.id)}
            style={[styles.level, selected && styles.selectedBorder]}
          >
            <View style={styles.flex}>
              <Text style={styles.optionTitle}>{item.title}</Text>
              <Text style={styles.optionNote}>{item.note}</Text>
            </View>
            {selected ? <AppIcon name="check" size={18} /> : null}
          </Pressable>
        );
      })}
    </View>
  );
}

export function TeacherSelector({
  selectedId,
  onSelect,
}: {
  selectedId: string;
  onSelect: (teacher: Teacher) => void;
}) {
  return (
    <View style={styles.teacherGrid}>
      {teachers.map((teacher) => {
        const selected = teacher.id === selectedId;
        return (
          <Pressable
            key={teacher.id}
            accessibilityRole="radio"
            accessibilityState={{ checked: selected }}
            onPress={() => onSelect(teacher)}
            style={[styles.teacher, selected && styles.selectedBorder]}
          >
            <Image source={teacher.image} style={styles.teacherImage} contentFit="contain" />
            <View style={styles.flex}>
              <Text style={styles.teacherName}>{teacher.name}</Text>
              <Text numberOfLines={1} style={styles.teacherMeta}>
                {teacher.accent} · {teacher.personality}
              </Text>
            </View>
            {selected ? <AppIcon name="check" size={16} /> : null}
          </Pressable>
        );
      })}
    </View>
  );
}

export function ConversationSettings({
  open,
  speed,
  level,
  teacher,
  onSave,
  onClose,
}: {
  open: boolean;
  speed: string;
  level: string;
  teacher: Teacher;
  onSave: (settings: { speed: string; level: string; teacher: Teacher }) => void;
  onClose: () => void;
}) {
  const [draftSpeed, setDraftSpeed] = useState(speed);
  const [draftLevel, setDraftLevel] = useState(level);
  const [draftTeacher, setDraftTeacher] = useState(teacher);

  useEffect(() => {
    if (!open) return;
    setDraftSpeed(speed);
    setDraftLevel(level);
    setDraftTeacher(teacher);
  }, [level, open, speed, teacher]);

  return (
    <Modal animationType="slide" transparent visible={open} onRequestClose={onClose}>
      <KeyboardAvoidingView style={styles.modal} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        <Pressable accessibilityLabel="关闭设置" onPress={onClose} style={styles.backdrop} />
        <SafeAreaView edges={['bottom']} style={styles.sheet}>
          <View style={styles.handle} />
          <View style={styles.sheetHeader}>
            <Text style={styles.sheetTitle}>对话设置</Text>
            <Text style={styles.sheetDescription}>调整后会从下一次对话开始生效。</Text>
          </View>
          <ScrollView keyboardShouldPersistTaps="handled" contentContainerStyle={styles.sheetContent}>
            <View style={styles.group}>
              <Text style={styles.groupTitle}>对话语速</Text>
              <SpeedSelector value={draftSpeed} onChange={setDraftSpeed} />
            </View>
            <View style={styles.group}>
              <Text style={styles.groupTitle}>英语水平</Text>
              <LevelSelector value={draftLevel} onChange={setDraftLevel} />
            </View>
            <View style={styles.group}>
              <Text style={styles.groupTitle}>AI 老师</Text>
              <TeacherSelector selectedId={draftTeacher.id} onSelect={setDraftTeacher} />
            </View>
            <View style={styles.actions}>
              <AppButton title="取消" variant="secondary" onPress={onClose} style={styles.flex} />
              <AppButton
                title="保存设置"
                onPress={() => onSave({ speed: draftSpeed, level: draftLevel, teacher: draftTeacher })}
                style={styles.flex}
              />
            </View>
          </ScrollView>
        </SafeAreaView>
      </KeyboardAvoidingView>
    </Modal>
  );
}

const styles = StyleSheet.create({
  modal: { flex: 1, justifyContent: 'flex-end' },
  backdrop: { position: 'absolute', top: 0, right: 0, bottom: 0, left: 0, backgroundColor: 'rgba(15,15,14,0.36)' },
  sheet: {
    width: '100%',
    maxWidth: Platform.OS === 'web' ? 430 : undefined,
    maxHeight: '88%',
    alignSelf: 'center',
    overflow: 'hidden',
    borderTopLeftRadius: 24,
    borderTopRightRadius: 24,
    backgroundColor: colors.white,
  },
  handle: { width: 42, height: 5, marginTop: 9, alignSelf: 'center', borderRadius: 3, backgroundColor: '#D2D2CD' },
  sheetHeader: { paddingHorizontal: 22, paddingTop: 17, paddingBottom: 16, borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: colors.line },
  sheetTitle: { color: colors.ink, fontSize: 23, fontWeight: '600' },
  sheetDescription: { marginTop: 5, color: colors.muted, fontSize: 13, fontWeight: '300' },
  sheetContent: { padding: 22, paddingBottom: 12, gap: 26 },
  group: { gap: 12 },
  groupTitle: { color: colors.ink, fontSize: 15, fontWeight: '500' },
  segment: { padding: 4, flexDirection: 'row', borderRadius: 11, backgroundColor: colors.soft },
  segmentItem: { height: 42, flex: 1, alignItems: 'center', justifyContent: 'center', borderRadius: 8 },
  segmentItemSelected: { backgroundColor: colors.white },
  segmentText: { color: colors.muted, fontSize: 12, fontWeight: '300' },
  segmentTextSelected: { color: colors.ink, fontWeight: '500' },
  options: { gap: 8 },
  level: { minHeight: 62, padding: 13, flexDirection: 'row', alignItems: 'center', gap: 12, borderWidth: 1, borderColor: colors.line, borderRadius: 13 },
  selectedBorder: { borderColor: colors.ink, borderWidth: 2 },
  flex: { flex: 1 },
  optionTitle: { color: colors.ink, fontSize: 14, fontWeight: '500' },
  optionNote: { marginTop: 4, color: colors.muted, fontSize: 11, fontWeight: '300' },
  teacherGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: 9 },
  teacher: { width: '48%', minHeight: 72, padding: 8, flexDirection: 'row', alignItems: 'center', gap: 7, borderWidth: 1, borderColor: colors.line, borderRadius: 13 },
  teacherImage: { width: 38, height: 50, borderRadius: 9, backgroundColor: colors.soft },
  teacherName: { color: colors.ink, fontSize: 13, fontWeight: '500' },
  teacherMeta: { marginTop: 3, color: colors.muted, fontSize: 9, fontWeight: '300' },
  actions: { paddingTop: 18, flexDirection: 'row', gap: 10, borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: colors.line },
});
