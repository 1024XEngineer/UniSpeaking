import { GlassView, isGlassEffectAPIAvailable } from 'expo-glass-effect';
import type { BottomTabBarProps } from 'expo-router/build/react-navigation/bottom-tabs';
import { Platform, Pressable, StyleSheet, View, type ViewStyle } from 'react-native';

import { colors } from '@/theme/tokens';

const dockHeight = 60;

export function LiquidGlassTabBar({ state, descriptors, navigation, insets }: BottomTabBarProps) {
  const nativeGlassAvailable = Platform.OS === 'ios' && isGlassEffectAPIAvailable();
  const webGlassStyle = Platform.OS === 'web'
    ? ({ backdropFilter: 'blur(20px) saturate(150%)', WebkitBackdropFilter: 'blur(20px) saturate(150%)' } as ViewStyle)
    : undefined;

  return (
    <View pointerEvents="box-none" style={[styles.bar, { height: dockHeight + insets.bottom + 20, paddingBottom: insets.bottom + 10 }]}>
      <View style={styles.dock}>
        <GlassView
          pointerEvents="none"
          style={styles.glassLayer}
          glassEffectStyle={nativeGlassAvailable ? 'clear' : 'none'}
          tintColor="#FFFFFF"
          isInteractive
        />
        <View pointerEvents="none" style={[styles.glassTint, webGlassStyle]} />
        <View pointerEvents="none" style={styles.glassRim} />
        <View style={styles.tabRow} accessibilityRole="tablist">
          {state.routes.map((route, index) => {
            const descriptor = descriptors[route.key];
            const { options } = descriptor;
            const focused = state.index === index;
            const color = focused
              ? options.tabBarActiveTintColor ?? colors.ink
              : options.tabBarInactiveTintColor ?? colors.subtle;
            const label = options.tabBarAccessibilityLabel ?? options.title ?? route.name;
            const icon = options.tabBarIcon?.({ focused, color, size: 25 });

            const onPress = () => {
              const event = navigation.emit({ type: 'tabPress', target: route.key, canPreventDefault: true });
              if (!focused && !event.defaultPrevented) navigation.navigate(route.name, route.params);
            };
            const onLongPress = () => navigation.emit({ type: 'tabLongPress', target: route.key });

            return (
              <Pressable
                key={route.key}
                accessibilityRole="tab"
                accessibilityLabel={label}
                accessibilityState={{ selected: focused }}
                testID={options.tabBarButtonTestID}
                onPress={onPress}
                onLongPress={onLongPress}
                android_ripple={{ color: 'transparent', borderless: true }}
                style={({ pressed }) => [styles.tab, pressed && styles.pressed]}
              >
                {icon}
              </Pressable>
            );
          })}
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  bar: {
    position: 'absolute',
    right: 0,
    bottom: 0,
    left: 0,
    paddingHorizontal: 18,
    alignItems: 'center',
    justifyContent: 'flex-end',
  },
  dock: {
    width: '100%',
    maxWidth: 364,
    height: dockHeight,
    position: 'relative',
    overflow: 'hidden',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.78)',
    borderRadius: 31,
    backgroundColor: 'rgba(255,255,255,0.28)',
    shadowColor: '#151514',
    shadowOffset: { width: 0, height: 8 },
    shadowOpacity: 0.14,
    shadowRadius: 20,
    elevation: 10,
    boxShadow: '0px 8px 20px rgba(21,21,20,0.14)',
  },
  glassLayer: { position: 'absolute', top: 0, right: 0, bottom: 0, left: 0, borderRadius: 31 },
  glassTint: { position: 'absolute', top: 0, right: 0, bottom: 0, left: 0, borderRadius: 31, backgroundColor: 'rgba(255,255,255,0.34)' },
  glassRim: {
    position: 'absolute',
    top: 0,
    right: 0,
    bottom: 0,
    left: 0,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.56)',
    borderRadius: 31,
  },
  tabRow: { flex: 1, flexDirection: 'row', alignItems: 'center', paddingHorizontal: 6 },
  tab: { flex: 1, height: 56, alignItems: 'center', justifyContent: 'center' },
  pressed: { opacity: 0.68, transform: [{ scale: 0.94 }] },
});
