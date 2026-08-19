import AsyncStorage from '@react-native-async-storage/async-storage';
import Constants from 'expo-constants';
import * as Device from 'expo-device';
import * as Network from 'expo-network';
import type { ComponentType } from 'react';
import { AppState, Platform } from 'react-native';
import type * as SentryTypes from '@sentry/react-native';
import type { Crashlytics } from '@react-native-firebase/crashlytics';

import { SecureTokenStore } from '@/infrastructure/auth/SecureTokenStore';
import { getRuntimeConfig } from '@/infrastructure/config/runtimeConfig';

export type TelemetrySeverity = 'INFO' | 'WARN' | 'ERROR' | 'FATAL';
export type TelemetryAttributes = Record<string, string | number | boolean | null | undefined>;

export type MobileTelemetryEvent = Readonly<{
  eventType: string;
  severity?: TelemetrySeverity;
  sessionId?: string | null;
  route?: string | null;
  message?: string | null;
  stack?: string | null;
  attributes?: TelemetryAttributes;
}>;

type ErrorUtilsLike = {
  getGlobalHandler(): (error: Error, isFatal?: boolean) => void;
  setGlobalHandler(handler: (error: Error, isFatal?: boolean) => void): void;
};

type RejectionEventLike = { reason?: unknown };
type GlobalWithErrorHandlers = typeof globalThis & {
  ErrorUtils?: ErrorUtilsLike;
  onunhandledrejection?: ((event: RejectionEventLike) => void) | null;
};

const ANONYMOUS_ID_KEY = 'unispeaking.telemetry.anonymousId';
const MAX_BATCH_SIZE = 20;
const MAX_QUEUE_SIZE = 100;
const FLUSH_INTERVAL_MS = 5_000;
const applicationVersion = Constants.expoConfig?.version || Constants.nativeAppVersion || 'unknown';
const release = `mobile@${applicationVersion}`;
const sessionId = randomId('app');
const tokenStore = new SecureTokenStore();

let anonymousId = randomId('mobile');
let queue: Record<string, unknown>[] = [];
let flushTimer: ReturnType<typeof setTimeout> | null = null;
let initialized = false;
let sentryEnabled = false;
let sentry: typeof SentryTypes | null = null;
let crashlytics: Crashlytics | null = null;
let crashlyticsSdk: typeof import('@react-native-firebase/crashlytics') | null = null;
let telemetryUserId: string | null = null;

function randomId(prefix: string) {
  const random = globalThis.crypto?.randomUUID?.()
    || `${Date.now()}-${Math.random().toString(36).slice(2, 12)}`;
  return `${prefix}-${random}`;
}

function cleanRoute(value?: string | null) {
  if (!value) return null;
  try {
    return new URL(value).pathname.slice(0, 300);
  } catch {
    return value.split(/[?#]/, 1)[0].slice(0, 300);
  }
}

function cleanStack(value?: string | null) {
  return String(value || '')
    .replace(/([?&](?:token|access_token|authorization)=)[^&\s)]+/gi, '$1[redacted]')
    .slice(0, 8_000) || null;
}

function cleanAttributes(attributes: TelemetryAttributes = {}) {
  return Object.fromEntries(Object.entries(attributes)
    .filter(([key, value]) => /^[a-z][a-z0-9_]{0,63}$/.test(key)
      && value !== null && value !== undefined
      && ['string', 'number', 'boolean'].includes(typeof value)
      && (typeof value !== 'number' || Number.isFinite(value)))
    .slice(0, 32)
    .map(([key, value]) => [key, typeof value === 'string' ? value.slice(0, 500) : value]));
}

function deviceAttributes(): TelemetryAttributes {
  return {
    app_version: applicationVersion,
    platform: Platform.OS,
    platform_version: String(Platform.Version),
    device_brand: Device.brand || 'unknown',
    device_model: Device.modelName || 'unknown',
    os_name: Device.osName || Platform.OS,
    os_version: Device.osVersion || String(Platform.Version),
    is_device: Device.isDevice,
  };
}

function scheduleFlush() {
  if (process.env.NODE_ENV === 'test' || flushTimer || !queue.length) return;
  flushTimer = setTimeout(() => {
    flushTimer = null;
    void mobileTelemetry.flush();
  }, FLUSH_INTERVAL_MS);
}

async function loadAnonymousId() {
  try {
    const existing = await AsyncStorage.getItem(ANONYMOUS_ID_KEY);
    if (existing) anonymousId = existing;
    else await AsyncStorage.setItem(ANONYMOUS_ID_KEY, anonymousId);
  } catch {
    // A process-local ID still provides deduplication when storage is unavailable.
  }
}

const scrubSentryEvent: NonNullable<Parameters<typeof SentryTypes.init>[0]['beforeSend']> = (event) => {
  if (event.request?.url) event.request.url = cleanRoute(event.request.url) || undefined;
  if (event.request) {
    delete event.request.data;
    delete event.request.cookies;
    delete event.request.headers;
  }
  return event;
};

async function initializeCrashlytics() {
  const extra = Constants.expoConfig?.extra;
  const configured = Platform.OS === 'android'
    ? extra?.firebaseCrashlyticsAndroidConfigured
    : Platform.OS === 'ios' && extra?.firebaseCrashlyticsIosConfigured;
  if (!configured) return;
  try {
	// eslint-disable-next-line @typescript-eslint/no-require-imports
	crashlyticsSdk = require('@react-native-firebase/crashlytics') as typeof import('@react-native-firebase/crashlytics');
    crashlytics = crashlyticsSdk.getCrashlytics();
    await crashlyticsSdk.setCrashlyticsCollectionEnabled(crashlytics, true);
    await crashlyticsSdk.setUserId(crashlytics, telemetryUserId || 'anonymous');
    mobileTelemetry.record({
      eventType: 'mobile.crashlytics_ready',
      attributes: { crashlytics_enabled: true },
    });
  } catch (error) {
    crashlytics = null;
    mobileTelemetry.record({
      eventType: 'mobile.crashlytics_unavailable',
      severity: 'WARN',
      message: error instanceof Error ? error.message : 'Crashlytics initialization failed',
    });
  }
}

function installGlobalErrorHandlers() {
  const globals = globalThis as GlobalWithErrorHandlers;
  const errorUtils = globals.ErrorUtils;
  if (errorUtils) {
    const previousHandler = errorUtils.getGlobalHandler();
    errorUtils.setGlobalHandler((error, isFatal) => {
      mobileTelemetry.record({
        eventType: isFatal ? 'mobile.js_crash' : 'mobile.js_exception',
        severity: isFatal ? 'FATAL' : 'ERROR',
        message: error.message,
        stack: error.stack,
        attributes: { error_name: error.name, fatal: Boolean(isFatal) },
      });
      if (crashlytics && crashlyticsSdk) crashlyticsSdk.recordError(crashlytics, error, error.name);
      previousHandler(error, isFatal);
    });
  }

  const previousRejectionHandler = globals.onunhandledrejection;
  globals.onunhandledrejection = (event) => {
    const error = event.reason instanceof Error
      ? event.reason
      : new Error(String(event.reason || 'Unhandled promise rejection'));
    mobileTelemetry.record({
      eventType: 'mobile.unhandled_rejection',
      severity: 'ERROR',
      message: error.message,
      stack: error.stack,
      attributes: { error_name: error.name },
    });
    if (crashlytics && crashlyticsSdk) crashlyticsSdk.recordError(crashlytics, error, error.name);
    previousRejectionHandler?.(event);
  };
}

export const mobileTelemetry = {
  initialize() {
    if (initialized || process.env.NODE_ENV === 'test') return;
    initialized = true;
    const dsn = process.env.EXPO_PUBLIC_SENTRY_DSN?.trim();
	// eslint-disable-next-line @typescript-eslint/no-require-imports
	sentry = require('@sentry/react-native') as typeof SentryTypes;
    sentry.init({
      dsn,
      enabled: Boolean(dsn),
      environment: __DEV__ ? 'development' : 'production',
      release,
      sendDefaultPii: false,
      tracesSampleRate: 0.1,
      beforeSend: scrubSentryEvent,
    });
    sentryEnabled = Boolean(dsn);
    void loadAnonymousId();
    void initializeCrashlytics();
    installGlobalErrorHandlers();
    AppState.addEventListener('change', (state) => {
      this.record({ eventType: 'mobile.app_state', attributes: { app_state: state } });
      if (state !== 'active') void this.flush();
    });
    this.record({ eventType: 'mobile.app_started', attributes: deviceAttributes() });
  },

  record(event: MobileTelemetryEvent) {
    if (process.env.NODE_ENV === 'test' || !/^[a-z][a-z0-9_.-]{1,63}$/.test(event.eventType)) return;
    queue.push({
      eventType: event.eventType,
      platform: 'MOBILE',
      severity: event.severity || 'INFO',
      occurredAt: new Date().toISOString(),
      anonymousId,
      sessionId: event.sessionId || sessionId,
      route: cleanRoute(event.route),
      release,
      message: String(event.message || '').slice(0, 500) || null,
      stack: cleanStack(event.stack),
      attributes: cleanAttributes({ ...deviceAttributes(), ...event.attributes }),
    });
    if (queue.length > MAX_QUEUE_SIZE) queue = queue.slice(-MAX_QUEUE_SIZE);
    if (queue.length >= MAX_BATCH_SIZE) void this.flush();
    else scheduleFlush();
  },

  async flush() {
    if (!queue.length || process.env.NODE_ENV === 'test') return;
    const events = queue.splice(0, MAX_BATCH_SIZE);
    try {
      const token = await tokenStore.get();
      const response = await fetch(`${getRuntimeConfig().backendUrl}/api/telemetry/events`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
        body: JSON.stringify({ events }),
      });
      if (!response.ok && response.status !== 429) throw new Error(`telemetry ${response.status}`);
    } catch {
      queue = [...events, ...queue].slice(0, MAX_QUEUE_SIZE);
      scheduleFlush();
    }
    if (queue.length) scheduleFlush();
  },

  setUser(userId: string | null) {
    telemetryUserId = userId;
    if (sentryEnabled) sentry?.setUser(telemetryUserId ? { id: telemetryUserId } : null);
    if (crashlytics && crashlyticsSdk) {
	  void crashlyticsSdk.setUserId(crashlytics, telemetryUserId || 'anonymous');
	}
  },

  captureException(error: unknown, context: Omit<MobileTelemetryEvent, 'message' | 'stack'> = { eventType: 'mobile.js_exception' }) {
    const normalized = error instanceof Error ? error : new Error(String(error || 'Unknown error'));
    this.record({ ...context, message: normalized.message, stack: normalized.stack });
    if (sentryEnabled) sentry?.captureException(normalized);
    if (crashlytics && crashlyticsSdk) {
	  crashlyticsSdk.recordError(crashlytics, normalized, normalized.name);
	}
  },

  async recordApiRequest(input: {
    path: string;
    method: string;
    durationMs: number;
    outcome: 'success' | 'error' | 'network_error' | 'timeout';
    status?: number;
    message?: string;
  }) {
	if (process.env.NODE_ENV === 'test') return;
    let networkType = 'unknown';
    let isConnected = false;
    try {
      const network = await Network.getNetworkStateAsync();
      networkType = String(network.type || 'unknown');
      isConnected = Boolean(network.isConnected && network.isInternetReachable !== false);
    } catch {
      // Network details are supplemental and must never block the API call.
    }
    this.record({
      eventType: 'api.request',
      severity: input.outcome === 'success' ? 'INFO' : 'ERROR',
      message: input.message,
      route: input.path,
      attributes: {
        api_path: cleanRoute(input.path) || input.path,
        api_method: input.method,
        duration_ms: input.durationMs,
        outcome: input.outcome,
        http_status: input.status,
        network_type: networkType,
        network_connected: isConnected,
      },
    });
  },
};

export function initializeMobileTelemetry() {
  mobileTelemetry.initialize();
}

export function wrapTelemetryRoot<T extends ComponentType<unknown>>(component: T): T {
  if (process.env.NODE_ENV === 'test') return component;
	// eslint-disable-next-line @typescript-eslint/no-require-imports
	const sdk = require('@sentry/react-native') as typeof SentryTypes;
  return sdk.wrap(component) as T;
}
