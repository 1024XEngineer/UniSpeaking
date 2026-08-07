import { StyleSheet, Text, View } from 'react-native';

import { sceneCategories, type SceneCategory } from '@/data/sceneCategories';

const subtleCategoryColors: Record<SceneCategory, { backgroundColor: string; textColor: string }> = {
  food: { backgroundColor: '#FFF8E8', textColor: '#9A6A1A' },
  shopping: { backgroundColor: '#FFF2F7', textColor: '#B55A82' },
  transit: { backgroundColor: '#F0F6FF', textColor: '#4776AD' },
  accommodation: { backgroundColor: '#F6F1FC', textColor: '#7658A1' },
  health: { backgroundColor: '#ECF8F0', textColor: '#3D8558' },
  workplace: { backgroundColor: '#F1F3F8', textColor: '#596B8F' },
  social: { backgroundColor: '#FFF1EE', textColor: '#AB5E58' },
  education: { backgroundColor: '#ECF9F7', textColor: '#3A8179' },
  services: { backgroundColor: '#F3F4F2', textColor: '#666A64' },
  other: { backgroundColor: '#F0F0EF', textColor: '#4B4B48' },
};

export function SceneCategoryTag({ category, subtle = false }: { category?: SceneCategory; subtle?: boolean }) {
  const key = category ?? 'other';
  const palette = subtle ? subtleCategoryColors[key] : sceneCategories[key];

  return (
    <View style={[styles.tag, { backgroundColor: palette.backgroundColor }]}>
      <Text style={[styles.label, { color: palette.textColor }]}>{sceneCategories[key].label}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  tag: {
    minHeight: 20,
    paddingHorizontal: 8,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 4,
  },
  label: { fontSize: 9, lineHeight: 14, fontWeight: '500' },
});
