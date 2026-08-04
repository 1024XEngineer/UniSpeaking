import { Image } from 'expo-image';
import { SpeakerHighIcon } from 'phosphor-react-native/src/icons/SpeakerHigh';
import { useCallback, useEffect, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  FadeIn,
  FadeOut,
  interpolate,
  runOnJS,
  type SharedValue,
  useAnimatedStyle,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';

import { colors, teachers, type Teacher } from '@/theme/tokens';

const DIAL_RADIUS = 164;
const DIAL_STEP = 0.34;
const DIAL_BUTTON_SIZE = 52;
const DRAG_DISTANCE_PER_STEP = DIAL_RADIUS * DIAL_STEP;

const teacherIntros: Record<string, string> = {
  clara: "Hi, I'm Clara. Take your time — we'll make speaking feel natural.",
  james: "Hi, I'm James. We'll make every sentence clear and confident.",
  leo: "Hi, I'm Leo. Let's keep it relaxed, lively, and easy to start.",
  david: "Hi, I'm David. We'll make your English concise, natural, and ready for work.",
  emily: "Hi, I'm Emily. We'll build fluent English through warm, everyday conversation.",
  arthur: "Hi, I'm Arthur. We'll slow down, think clearly, and express richer ideas.",
};

function modIndex(index: number, count: number) {
  'worklet';
  return ((index % count) + count) % count;
}

function wrapSlot(index: number, position: number, count: number) {
  'worklet';
  let slot = index - position;
  slot %= count;
  if (slot > count / 2) slot -= count;
  if (slot < -count / 2) slot += count;
  return slot;
}

function TeacherDialButton({
  teacher,
  index,
  activeIndex,
  position,
  mirrorAcrossDial = false,
  decorative = false,
  onPress,
}: {
  teacher: Teacher;
  index: number;
  activeIndex: number;
  position: SharedValue<number>;
  mirrorAcrossDial?: boolean;
  decorative?: boolean;
  onPress: () => void;
}) {
  const selected = index === activeIndex;

  const positionStyle = useAnimatedStyle(() => {
    const wrappedSlot = wrapSlot(index, position.value, teachers.length);
    const slot = mirrorAcrossDial
      ? wrappedSlot < 0
        ? wrappedSlot + teachers.length
        : wrappedSlot - teachers.length
      : wrappedSlot;
    const angle = slot * DIAL_STEP;
    const distance = Math.abs(slot);
    return {
      opacity: interpolate(distance, [0, 2, 3], [1, 0.72, 0.28], 'clamp'),
      zIndex: Math.round(20 - distance * 3),
      transform: [
        { translateX: DIAL_RADIUS * Math.sin(angle) },
        { translateY: DIAL_RADIUS * (1 - Math.cos(angle)) },
        { scale: interpolate(distance, [0, 1, 3], [1.08, 0.88, 0.66], 'clamp') },
      ],
    };
  });

  return (
    <Animated.View pointerEvents={decorative ? 'none' : 'auto'} style={[styles.dialButtonSlot, positionStyle]}>
      {decorative ? (
        <View style={styles.dialButton}>
          <Image source={teacher.image} style={styles.dialImage} contentFit="cover" />
        </View>
      ) : (
        <Pressable
          accessibilityRole="button"
          accessibilityLabel={`选择 ${teacher.name}`}
          accessibilityState={{ selected }}
          onPress={onPress}
          style={({ pressed }) => [
            styles.dialButton,
            selected && styles.dialButtonSelected,
            pressed && styles.dialButtonPressed,
          ]}
        >
          <Image source={teacher.image} style={styles.dialImage} contentFit="cover" />
        </Pressable>
      )}
    </Animated.View>
  );
}

export function TeacherSwipeStack({
  selected,
  onSelect,
}: {
  selected: Teacher;
  onSelect: (teacher: Teacher) => void;
}) {
  const selectedIndex = Math.max(0, teachers.findIndex((teacher) => teacher.id === selected.id));
  const [position, setPosition] = useState(selectedIndex);
  const dialPosition = useSharedValue(selectedIndex);
  const dragStartPosition = useSharedValue(selectedIndex);
  const reportedIndex = useSharedValue(selectedIndex);
  const activeIndex = modIndex(Math.round(position), teachers.length);

  const active = teachers[activeIndex];
  const oppositeIndex = modIndex(activeIndex + 3, teachers.length);

  const commitPosition = useCallback((nextPosition: number) => {
    setPosition(nextPosition);
    onSelect(teachers[modIndex(nextPosition, teachers.length)]);
  }, [onSelect]);

  useEffect(() => {
    const current = Math.round(dialPosition.value);
    const currentIndex = modIndex(current, teachers.length);
    if (currentIndex === selectedIndex) return;

    let delta = modIndex(selectedIndex - currentIndex, teachers.length);
    if (delta > teachers.length / 2) delta -= teachers.length;
    const target = current + delta;
    setPosition(target);
    reportedIndex.value = selectedIndex;
    dialPosition.value = withTiming(target, {
      duration: 340,
      easing: Easing.inOut(Easing.cubic),
    });
  }, [dialPosition, reportedIndex, selectedIndex]);

  const dragGesture = Gesture.Pan()
    .activeOffsetX([-6, 6])
    .onBegin(() => {
      dragStartPosition.value = dialPosition.value;
    })
    .onUpdate((event) => {
      const nextPosition = dragStartPosition.value - event.translationX / DRAG_DISTANCE_PER_STEP;
      dialPosition.value = nextPosition;

      const roundedPosition = Math.round(nextPosition);
      const nextIndex = modIndex(roundedPosition, teachers.length);
      if (nextIndex !== reportedIndex.value) {
        reportedIndex.value = nextIndex;
        runOnJS(commitPosition)(roundedPosition);
      }
    })
    .onEnd((event) => {
      const momentum = Math.max(-1.25, Math.min(1.25, event.velocityX / 800));
      const target = Math.round(dialPosition.value - momentum);
      dialPosition.value = withTiming(target, {
        duration: 280,
        easing: Easing.out(Easing.cubic),
      });
      reportedIndex.value = modIndex(target, teachers.length);
      runOnJS(commitPosition)(target);
    });

  const selectTeacher = (index: number) => {
    const current = Math.round(dialPosition.value);
    const currentIndex = modIndex(current, teachers.length);
    let delta = modIndex(index - currentIndex, teachers.length);
    if (delta > teachers.length / 2) delta -= teachers.length;
    const target = current + delta;
    reportedIndex.value = index;
    dialPosition.value = withTiming(target, {
      duration: 340,
      easing: Easing.inOut(Easing.cubic),
    });
    commitPosition(target);
  };

  return (
    <View style={styles.container}>
      <View style={styles.hero} accessibilityLiveRegion="polite">
        <Animated.View
          key={active.id}
          entering={FadeIn.duration(420).easing(Easing.out(Easing.cubic))}
          exiting={FadeOut.duration(180)}
          style={styles.heroImageFrame}
        >
          <Image source={active.image} style={styles.heroImage} contentFit="contain" />
        </Animated.View>

        <Animated.View key={`${active.id}-meta`} entering={FadeIn.duration(300)} style={styles.meta}>
          <Text style={styles.name}>{active.name}</Text>
          <Text style={styles.detail}>{active.accent} · {active.personality}</Text>
        </Animated.View>
      </View>

      <View style={styles.intro}>
        <SpeakerHighIcon color={colors.subtle} size={18} weight="fill" />
        <Text numberOfLines={2} style={styles.introText}>“{teacherIntros[active.id]}”</Text>
      </View>

      <GestureDetector gesture={dragGesture}>
        <Animated.View style={styles.dial} accessibilityLabel="拖动圆盘选择 AI 老师">
          {teachers.map((teacher, index) => (
            <TeacherDialButton
              key={teacher.id}
              teacher={teacher}
              index={index}
              activeIndex={activeIndex}
              position={dialPosition}
              onPress={() => selectTeacher(index)}
            />
          ))}
          <TeacherDialButton
            key={`mirror-${oppositeIndex}`}
            teacher={teachers[oppositeIndex]}
            index={oppositeIndex}
            activeIndex={activeIndex}
            position={dialPosition}
            mirrorAcrossDial
            decorative
            onPress={() => undefined}
          />
        </Animated.View>
      </GestureDetector>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, width: '100%', alignItems: 'center', paddingTop: 18 },
  hero: { alignItems: 'center' },
  heroImageFrame: {
    width: 230,
    height: 230,
    overflow: 'hidden',
    borderRadius: 18,
    backgroundColor: '#F7F7F4',
  },
  heroImage: { width: '100%', height: '100%' },
  meta: { minHeight: 48, marginTop: 10, alignItems: 'center' },
  name: { color: colors.ink, fontSize: 21, fontWeight: '600', letterSpacing: -0.45 },
  detail: { marginTop: 4, color: colors.muted, fontSize: 12, fontWeight: '300' },
  intro: {
    minHeight: 42,
    marginTop: 9,
    paddingHorizontal: 8,
    flexDirection: 'row',
    alignItems: 'flex-start',
    justifyContent: 'center',
    gap: 8,
  },
  introText: { maxWidth: 304, color: colors.muted, fontSize: 14, lineHeight: 21, fontWeight: '300', textAlign: 'center' },
  dial: {
    position: 'relative',
    width: '100%',
    height: 170,
    marginTop: 'auto',
    marginBottom: 0,
    overflow: 'hidden',
  },
  dialButtonSlot: {
    position: 'absolute',
    top: 12,
    left: '50%',
    width: DIAL_BUTTON_SIZE,
    height: DIAL_BUTTON_SIZE,
    marginLeft: -DIAL_BUTTON_SIZE / 2,
  },
  dialButton: {
    width: '100%',
    height: '100%',
    overflow: 'hidden',
    borderWidth: 1,
    borderColor: 'rgba(21,21,20,0.12)',
    borderRadius: DIAL_BUTTON_SIZE / 2,
    backgroundColor: 'rgba(255,255,255,0.72)',
  },
  dialButtonSelected: {
    borderWidth: 2,
    borderColor: colors.ink,
    backgroundColor: colors.white,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 7 },
    shadowOpacity: 0.16,
    shadowRadius: 12,
    elevation: 6,
  },
  dialButtonPressed: { opacity: 0.72 },
  dialImage: { width: '100%', height: '100%', backgroundColor: '#F7F7F4' },
});
