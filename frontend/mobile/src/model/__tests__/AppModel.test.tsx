import { fireEvent, render, waitFor } from '@testing-library/react-native';
import { Pressable, Text, View } from 'react-native';

import type { AuthSessionState } from '@/features/auth/AuthSessionController';
import type { UserPreference } from '@/features/auth/AuthService';
import { teachers } from '@/theme/tokens';

import {
  AppModelProvider,
  type AppModelAuthController,
  useAppModel,
} from '../AppModel';

function createController(state: AuthSessionState): AppModelAuthController & {
  emit(nextState: AuthSessionState): void;
  login: jest.Mock;
  updatePreference: jest.Mock;
} {
  let listener: ((nextState: AuthSessionState) => void) | null = null;
  return {
    getSnapshot: () => state,
    subscribe: jest.fn((nextListener: (nextState: AuthSessionState) => void) => {
      listener = nextListener;
      return () => {
        listener = null;
      };
    }),
    bootstrap: jest.fn(async () => {
      listener?.(state);
    }),
    login: jest.fn(async () => undefined),
    issueEmailChallenge: jest.fn(async () => ({
      challengeId: 'challenge-1',
      expiresInSeconds: 600,
      resendAfterSeconds: 60,
    })),
    register: jest.fn(async () => undefined),
    updatePreference: jest.fn(async (patch: Partial<UserPreference>) => ({
      userId: 'user-1',
      preferredVoice: null,
      preferredAiSpeechSpeed: null,
      cefrLevel: null,
      memoryText: null,
      ...patch,
    })),
    logout: jest.fn(async () => undefined),
    unauthorized: jest.fn(async () => undefined),
    emit(nextState) {
      state = nextState;
      listener?.(nextState);
    },
  };
}

const authenticatedState: AuthSessionState = {
  status: 'authenticated',
  user: {
    id: 'user-1',
    username: 'learner@example.com',
    nickname: 'Yufan',
    role: 'USER',
    status: 'ACTIVE',
    lastLoginAt: null,
    createdAt: '2026-08-05T00:00:00Z',
  },
  preference: {
    userId: 'user-1',
    preferredVoice: 'Harvey',
    preferredAiSpeechSpeed: 'NATURAL',
    cefrLevel: 'B',
    memoryText: null,
  },
  error: null,
};

function SessionProbe() {
  const model = useAppModel();
  return (
    <View>
      <Text testID="session-state">
        {JSON.stringify({
          ready: model.isModelReady,
          authenticated: model.isAuthenticated,
          onboarded: model.hasCompletedOnboarding,
          nickname: model.nickname,
          level: model.level,
          teacher: model.teacher.name,
          speed: model.speed,
        })}
      </Text>
      <Pressable
        accessibilityLabel="test-login"
        onPress={() =>
          void model.signIn({
            username: 'learner@example.com',
            password: 'password123456',
          })
        }
      />
    </View>
  );
}

function OnboardingProbe() {
  const model = useAppModel();
  return (
    <View>
      <Pressable accessibilityLabel="choose-level" onPress={() => model.setLevel('basic')} />
      <Pressable accessibilityLabel="save-ielts-level" onPress={() => void model.saveLevel('independent')} />
      <Pressable accessibilityLabel="choose-teacher" onPress={() => model.setTeacher(teachers[1])} />
      <Pressable
        accessibilityLabel="complete-onboarding"
        onPress={() => void model.completeOnboarding()}
      />
    </View>
  );
}

describe('AppModelProvider authentication binding', () => {
  it('hydrates the finalized UI choices from the authenticated backend preference', async () => {
    const controller = createController(authenticatedState);
    const screen = await render(
      <AppModelProvider authController={controller}>
        <SessionProbe />
      </AppModelProvider>,
    );

    await waitFor(() =>
      expect(screen.getByTestId('session-state').props.children).toBe(
        JSON.stringify({
          ready: true,
          authenticated: true,
          onboarded: true,
          nickname: 'Yufan',
          level: 'basic',
          teacher: 'James',
          speed: '自然',
        }),
      ),
    );
  });

  it('passes credentials to the real authentication controller', async () => {
    const controller = createController({
      status: 'anonymous',
      user: null,
      preference: null,
      error: null,
    });
    const screen = await render(
      <AppModelProvider authController={controller}>
        <SessionProbe />
      </AppModelProvider>,
    );

    await waitFor(() => expect(controller.subscribe).toHaveBeenCalledTimes(1));
    await fireEvent.press(screen.getByLabelText('test-login'));

    await waitFor(() =>
      expect(controller.login).toHaveBeenCalledWith({
        username: 'learner@example.com',
        password: 'password123456',
      }),
    );
  });

  it('persists the selected level and teacher when onboarding completes', async () => {
    const controller = createController({
      ...authenticatedState,
      preference: {
        ...authenticatedState.preference!,
        preferredVoice: null,
        cefrLevel: null,
      },
    });
    const screen = await render(
      <AppModelProvider authController={controller}>
        <OnboardingProbe />
      </AppModelProvider>,
    );

    await waitFor(() => expect(controller.subscribe).toHaveBeenCalledTimes(1));
    await fireEvent.press(screen.getByLabelText('choose-level'));
    await fireEvent.press(screen.getByLabelText('choose-teacher'));
    await fireEvent.press(screen.getByLabelText('complete-onboarding'));

    await waitFor(() =>
      expect(controller.updatePreference).toHaveBeenCalledWith({
        cefrLevel: 'B',
        preferredVoice: 'Harvey',
      }),
    );
  });

  it('persists a changed IELTS intake level in the user preference', async () => {
    const controller = createController(authenticatedState);
    const screen = await render(
      <AppModelProvider authController={controller}>
        <OnboardingProbe />
      </AppModelProvider>,
    );

    await fireEvent.press(screen.getByLabelText('save-ielts-level'));

    await waitFor(() =>
      expect(controller.updatePreference).toHaveBeenCalledWith({
        cefrLevel: 'C',
      }),
    );
  });
});
