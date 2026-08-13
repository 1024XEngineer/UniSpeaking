export const sceneCategories = {
  food: { label: '餐饮', backgroundColor: '#FFF0C7', textColor: '#8A5400' },
  shopping: { label: '购物', backgroundColor: '#FFE4ED', textColor: '#A32658' },
  transit: { label: '出行', backgroundColor: '#E3EFFF', textColor: '#245FA8' },
  accommodation: { label: '住宿', backgroundColor: '#F0E6FA', textColor: '#69419A' },
  health: { label: '健康', backgroundColor: '#DFF4E6', textColor: '#247344' },
  workplace: { label: '职场', backgroundColor: '#E6EAF3', textColor: '#3E527C' },
  social: { label: '社交', backgroundColor: '#FFE5E1', textColor: '#A33E35' },
  education: { label: '学习', backgroundColor: '#DDF3F0', textColor: '#176B63' },
  services: { label: '服务', backgroundColor: '#EBECEA', textColor: '#555954' },
  other: { label: '其他', backgroundColor: '#1B1B1A', textColor: '#FFFFFF' },
} as const;

export type SceneCategory = keyof typeof sceneCategories;

export type SceneLabel = (typeof sceneCategories)[SceneCategory]['label'];

const categoryByLabel = Object.fromEntries(
  Object.entries(sceneCategories).map(([category, value]) => [value.label, category]),
) as Record<SceneLabel, SceneCategory>;

export function sceneCategoryForLabel(label: string | null | undefined): SceneCategory {
  return categoryByLabel[label as SceneLabel] ?? 'other';
}
