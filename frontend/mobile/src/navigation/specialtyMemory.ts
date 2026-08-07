import AsyncStorage from '@react-native-async-storage/async-storage';

export type SpecialtyKind = 'ielts' | 'interview';

const storageKey = 'unispeaking.navigation.last-specialty.v1';

export async function rememberSpecialty(kind: SpecialtyKind) {
  try {
    await AsyncStorage.setItem(storageKey, kind);
  } catch {
    // Navigation must remain usable when local storage is unavailable.
  }
}

export async function readRememberedSpecialty(): Promise<SpecialtyKind | null> {
  try {
    const saved = await AsyncStorage.getItem(storageKey);
    return saved === 'ielts' || saved === 'interview' ? saved : null;
  } catch {
    return null;
  }
}

export async function forgetSpecialty() {
  try {
    await AsyncStorage.removeItem(storageKey);
  } catch {
    // A stale preference is less important than completing navigation.
  }
}
