import { Pressable, StyleSheet, Text, View } from 'react-native';

import type { InterviewSessionApi } from './InterviewSessionApi';
import { useInterviewReport } from './useInterviewReport';

const dimensionLabels: Record<string, string> = {
  FLUENCY: '流利度',
  PRONUNCIATION_INTELLIGIBILITY: '发音清晰度',
  LOGIC_COHERENCE: '逻辑连贯',
  GRAMMAR_CONTROL: '语法控制',
  VOCABULARY_EXPRESSION: '词汇表达',
};

export function InterviewReportView({
  sessionId,
  api,
}: {
  sessionId: string;
  api: Pick<InterviewSessionApi, 'getReport' | 'retryReport'>;
}) {
  const reportState = useInterviewReport(sessionId, api);

  if (reportState.error) {
    return (
      <View style={styles.stack}>
        <Text style={styles.title}>网络错误</Text>
        <Text style={styles.muted}>报告加载暂时失败，正在自动重试。</Text>
        <Pressable accessibilityRole="button" onPress={() => void reportState.refresh()} style={styles.button}>
          <Text style={styles.buttonText}>立即刷新</Text>
        </Pressable>
      </View>
    );
  }

  if (reportState.status === 'PROCESSING' || reportState.status === 'IDLE') {
    return <Text style={styles.muted}>报告生成中，请稍候…</Text>;
  }
  if (reportState.status === 'FAILED') {
    return (
      <View style={styles.stack}>
        <Text style={styles.title}>报告生成失败</Text>
        <Text style={styles.muted}>{reportState.failureReason ?? '暂时无法生成报告'}</Text>
        <Pressable accessibilityRole="button" disabled={reportState.isRetrying} onPress={() => void reportState.retry()} style={styles.button}>
          <Text style={styles.buttonText}>{reportState.isRetrying ? '正在重试…' : '重试生成报告'}</Text>
        </Pressable>
      </View>
    );
  }

  const report = reportState.report;
  if (!report) return null;
  return (
    <View style={styles.stack}>
      <Text style={styles.eyebrow}>综合表现</Text>
      <Text style={styles.score}>{report.overallScore}<Text style={styles.max}>/100</Text></Text>
      <Text style={styles.summary}>{report.summary}</Text>
      {report.dimensions.map((dimension) => (
        <View key={dimension.dimension} style={styles.dimension}>
          <View style={styles.row}>
            <Text style={styles.dimensionTitle}>{dimensionLabels[dimension.dimension] ?? dimension.dimension}</Text>
            <Text style={styles.dimensionScore}>{dimension.score === null ? '未评分' : `${dimension.score}/100`}</Text>
          </View>
          <Text style={styles.copy}>{dimension.evaluation}</Text>
          <Text style={styles.advice}>建议：{dimension.advice}</Text>
        </View>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  stack: { gap: 12 },
  eyebrow: { color: '#5D7896', fontSize: 12, letterSpacing: 1 },
  title: { color: '#123255', fontSize: 20, fontWeight: '600' },
  score: { color: '#2875C8', fontSize: 48, fontWeight: '600' },
  max: { fontSize: 16, fontWeight: '400' },
  summary: { color: '#35516E', fontSize: 14, lineHeight: 21 },
  muted: { color: '#5D7896', fontSize: 14, lineHeight: 21 },
  dimension: { gap: 5, paddingVertical: 10, borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: '#B9D3EC' },
  row: { flexDirection: 'row', justifyContent: 'space-between', gap: 10 },
  dimensionTitle: { color: '#123255', fontSize: 14, fontWeight: '600' },
  dimensionScore: { color: '#2875C8', fontSize: 14, fontWeight: '600' },
  copy: { color: '#5D7896', fontSize: 13, lineHeight: 19 },
  advice: { color: '#35516E', fontSize: 13, lineHeight: 19 },
  button: { alignSelf: 'flex-start', paddingHorizontal: 16, paddingVertical: 10, borderRadius: 8, backgroundColor: '#2875C8' },
  buttonText: { color: '#FFF', fontSize: 14, fontWeight: '600' },
});
