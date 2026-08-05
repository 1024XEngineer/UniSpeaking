import { useState } from 'react';
import { Platform, Pressable, StyleSheet, Text, useWindowDimensions, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { AppIcon } from '@/components/ui';
import { AssetsScreen } from '@/screens/AssetsScreen';
import { ConversationScreen } from '@/screens/ConversationScreen';
import { ProfileScreen } from '@/screens/ProfileScreen';
import { ScenesScreen } from '@/screens/ScenesScreen';
import { colors } from '@/theme/tokens';

type TabId = 'conversation' | 'scenes' | 'assets' | 'profile';

const tabs = [
  { id: 'conversation', label: '对话', icon: 'chat' },
  { id: 'scenes', label: '场景', icon: 'grid' },
  { id: 'assets', label: '资产', icon: 'document' },
  { id: 'profile', label: '我的', icon: 'user' },
] as const;

export function MainApp() {
  const [activeTab, setActiveTab] = useState<TabId>('conversation');
  const [immersive, setImmersive] = useState(false);
  const insets = useSafeAreaInsets();
  const window = useWindowDimensions();
  const previewScale =
    Platform.OS === 'web'
      ? Math.min(1, (window.width - 48) / 450, (window.height - 48) / 900)
      : 1;

  return (
    <View style={styles.stage}>
      <View
        testID="phone-preview-frame"
        style={[
          styles.deviceFrame,
          Platform.OS === 'web' && {
            width: 450,
            height: 900,
            flexGrow: 0,
            flexShrink: 0,
            transform: [{ scale: previewScale }],
          },
        ]}
      >
        {Platform.OS === 'web' ? <View style={styles.deviceSpeaker} /> : null}
        <View testID="mobile-preview-frame" style={styles.root}>
          <View style={styles.content}>
            {activeTab === 'conversation' ? <ConversationScreen onImmersiveChange={setImmersive} /> : null}
            {activeTab === 'scenes' ? <ScenesScreen /> : null}
            {activeTab === 'assets' ? <AssetsScreen onOpenRecord={() => undefined} onOpenIelts={() => undefined} onOpenInterview={() => undefined} /> : null}
            {activeTab === 'profile' ? <ProfileScreen /> : null}
          </View>
          {!immersive ? (
            <View style={[styles.tabBar, { height: 66 + insets.bottom, paddingBottom: insets.bottom }]}>
              {tabs.map((tab) => {
                const selected = tab.id === activeTab;
                return (
                  <Pressable
                    key={tab.id}
                    accessibilityRole="tab"
                    accessibilityState={{ selected }}
                    accessibilityLabel={tab.label}
                    onPress={() => setActiveTab(tab.id)}
                    style={styles.tab}
                  >
                    <View style={[styles.indicator, selected && styles.indicatorSelected]} />
                    <AppIcon name={tab.icon} size={23} color={selected ? colors.ink : '#888884'} />
                    <Text style={[styles.tabLabel, selected && styles.tabLabelSelected]}>{tab.label}</Text>
                  </Pressable>
                );
              })}
            </View>
          ) : null}
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  stage: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: Platform.OS === 'web' ? 24 : 0,
    backgroundColor: Platform.OS === 'web' ? '#E8E8E4' : colors.white,
  },
  deviceFrame: {
    flex: Platform.OS === 'web' ? undefined : 1,
    width: '100%',
    ...(Platform.OS === 'web'
      ? {
          maxWidth: 450,
          padding: 10,
          paddingTop: 18,
          borderRadius: 52,
          backgroundColor: '#171716',
          shadowColor: '#151514',
          shadowOffset: { width: 0, height: 18 },
          shadowOpacity: 0.24,
          shadowRadius: 34,
        }
      : {}),
  },
  deviceSpeaker: {
    position: 'absolute',
    zIndex: 2,
    top: 7,
    left: '50%',
    width: 72,
    height: 5,
    marginLeft: -36,
    borderRadius: 3,
    backgroundColor: '#3C3C3A',
  },
  root: {
    flex: 1,
    width: '100%',
    overflow: 'hidden',
    backgroundColor: colors.canvas,
    ...(Platform.OS === 'web'
      ? {
          borderRadius: 40,
        }
      : {}),
  },
  content: { flex: 1, backgroundColor: colors.canvas },
  tabBar: {
    position: 'absolute',
    right: 0,
    bottom: 0,
    left: 0,
    flexDirection: 'row',
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: '#E8E7E2',
    backgroundColor: 'rgba(255,255,255,0.98)',
  },
  tab: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  indicator: { position: 'absolute', top: 0, width: 44, height: 3, backgroundColor: 'transparent' },
  indicatorSelected: { backgroundColor: colors.ink },
  tabLabel: { marginTop: 7, color: '#888884', fontSize: 11, fontWeight: '300' },
  tabLabelSelected: { color: colors.ink, fontWeight: '500' },
});
