import { Pressable, StyleSheet, Text, View } from 'react-native';

import { formatBand } from './ieltsMappings';
import type { IeltsEvaluationResult } from './types';

const palette = {
  canvas: '#FCFAFF',
  paper: '#FFFFFF',
  border: '#E6DBFF',
  purple: '#8060E8',
  purpleDark: '#5A3DBB',
  text: '#171323',
  muted: '#847D92',
} as const;

export function IeltsPracticeScoreDialog({
  evaluation,
  onHome,
  onDetails,
}: {
  evaluation: IeltsEvaluationResult | null;
  onHome: () => void;
  onDetails: () => void;
}) {
  const scores = evaluation ? [
    ['流利度与连贯性', evaluation.fluencyCoherenceScore],
    ['词汇资源', evaluation.lexicalResourceScore],
    ['语法多样性与准确性', evaluation.grammaticalRangeAccuracyScore],
    ['发音', evaluation.pronunciationScore],
  ] as const : [];

  return (
    <View style={styles.root}>
      <View style={styles.background}>
        <Text style={styles.backgroundEyebrow}>IELTS SPEAKING</Text>
        <Text style={styles.backgroundTitle}>本次专项练习已结束</Text>
        <Text style={styles.backgroundCopy}>评分结果已自动保存到学习资产</Text>
      </View>

      <View style={styles.dialog} accessibilityLabel="雅思专项训练评分">
        <Text style={styles.dialogEyebrow}>{evaluation ? '评分完成' : '有效回答不足'}</Text>
        <Text style={styles.dialogTitle}>
          {evaluation ? '本次专项表现' : '本次暂时无法评分'}
        </Text>
        <Text style={styles.dialogCopy}>
          {evaluation
            ? '本页只反映当前 Part 的四项能力表现，不作为完整雅思口语预估分。'
            : '至少完成一轮有效英文回答后，才能生成四项能力评分。'}
        </Text>

        {evaluation ? (
          <View style={styles.dimensionGrid}>
            {scores.map(([label, score]) => (
              <View key={label} style={styles.dimensionItem}>
                <Text style={styles.dimensionLabel}>{label}</Text>
                <Text style={styles.dimensionValue}>
                  {formatBand(score)}<Text style={styles.dimensionSuffix}> / 9</Text>
                </Text>
              </View>
            ))}
          </View>
        ) : null}

        <View style={styles.actions}>
          <Pressable
            accessibilityRole="button"
            onPress={onHome}
            style={({ pressed }) => [styles.button, styles.secondaryButton, pressed && styles.pressed]}
          >
            <Text style={styles.secondaryButtonText}>返回训练中心</Text>
          </Pressable>
          {evaluation ? (
            <Pressable
              accessibilityRole="button"
              onPress={onDetails}
              style={({ pressed }) => [styles.button, styles.primaryButton, pressed && styles.pressed]}
            >
              <Text style={styles.primaryButtonText}>查看详细报告</Text>
            </Pressable>
          ) : null}
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: palette.canvas },
  background: {
    minHeight: 190,
    paddingHorizontal: 24,
    paddingTop: 34,
    paddingBottom: 58,
    backgroundColor: palette.purple,
  },
  backgroundEyebrow: { color: '#EDE7FF', fontSize: 11, lineHeight: 16, fontWeight: '600' },
  backgroundTitle: { marginTop: 10, color: '#FFFFFF', fontSize: 28, lineHeight: 36, fontWeight: '700' },
  backgroundCopy: { marginTop: 8, color: '#EDE7FF', fontSize: 13, lineHeight: 20 },
  dialog: {
    marginTop: -38,
    marginHorizontal: 18,
    marginBottom: 28,
    padding: 22,
    borderWidth: 1,
    borderColor: palette.border,
    borderRadius: 18,
    backgroundColor: palette.paper,
    shadowColor: palette.purpleDark,
    shadowOffset: { width: 0, height: 10 },
    shadowOpacity: 0.14,
    shadowRadius: 24,
    elevation: 5,
    boxShadow: '0px 10px 24px rgba(90, 61, 187, 0.14)',
  },
  dialogEyebrow: { color: palette.purpleDark, fontSize: 12, lineHeight: 17, fontWeight: '700' },
  dialogTitle: { marginTop: 7, color: palette.text, fontSize: 25, lineHeight: 33, fontWeight: '700' },
  dialogCopy: { marginTop: 8, color: palette.muted, fontSize: 13, lineHeight: 20 },
  dimensionGrid: { marginTop: 20, flexDirection: 'row', flexWrap: 'wrap', gap: 10 },
  dimensionItem: {
    width: '48%',
    minHeight: 102,
    flexGrow: 1,
    padding: 15,
    justifyContent: 'space-between',
    borderWidth: 1,
    borderColor: palette.border,
    borderRadius: 12,
    backgroundColor: '#FDFBFF',
  },
  dimensionLabel: { color: palette.muted, fontSize: 12, lineHeight: 17, fontWeight: '500' },
  dimensionValue: { marginTop: 12, color: palette.purpleDark, fontSize: 30, lineHeight: 36, fontWeight: '700' },
  dimensionSuffix: { color: palette.muted, fontSize: 12, fontWeight: '600' },
  actions: { marginTop: 22, flexDirection: 'row', gap: 10 },
  button: { minHeight: 48, flex: 1, alignItems: 'center', justifyContent: 'center', paddingHorizontal: 12, borderWidth: 1, borderRadius: 24 },
  secondaryButton: { borderColor: palette.border, backgroundColor: palette.paper },
  primaryButton: { borderColor: palette.purple, backgroundColor: palette.purple },
  secondaryButtonText: { color: palette.text, fontSize: 13, fontWeight: '600', textAlign: 'center' },
  primaryButtonText: { color: '#FFFFFF', fontSize: 13, fontWeight: '600', textAlign: 'center' },
  pressed: { opacity: 0.78 },
});
