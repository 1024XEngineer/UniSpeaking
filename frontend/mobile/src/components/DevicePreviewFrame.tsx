import type { PropsWithChildren } from 'react';
import { Platform, StyleSheet, useWindowDimensions, View } from 'react-native';

import { colors } from '@/theme/tokens';

export function DevicePreviewFrame({ children }: PropsWithChildren) {
  const window = useWindowDimensions();
  const previewScale =
    Platform.OS === 'web'
      ? Math.min(1, (window.width - 48) / 450, (window.height - 48) / 900)
      : 1;

  const device = (
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
      <View testID="mobile-preview-frame" style={styles.screen}>
        {children}
      </View>
    </View>
  );

  return (
    <View testID="device-preview-stage" style={styles.stage}>
      {Platform.OS === 'web' ? (
        <View
          style={[
            styles.previewSlot,
            { width: 450 * previewScale, height: 900 * previewScale },
          ]}
        >
          {device}
        </View>
      ) : (
        device
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  stage: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: Platform.OS === 'web' ? 24 : 0,
    overflow: 'hidden',
    backgroundColor: Platform.OS === 'web' ? '#E8E8E4' : colors.white,
  },
  previewSlot: { alignItems: 'center', justifyContent: 'center' },
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
  screen: {
    flex: 1,
    width: '100%',
    overflow: 'hidden',
    backgroundColor: colors.white,
    ...(Platform.OS === 'web' ? { borderRadius: 40 } : {}),
  },
});
