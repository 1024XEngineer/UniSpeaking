import { createActivityTimer, type Clock } from './ActivityTimer';
import { normalizeTrackedPath, type TrainingMode } from './pageCatalog';

type FetchLike = (input: RequestInfo | URL, init?: RequestInit) => Promise<Response>;

type UmamiConfig = Readonly<{
  enabled: boolean;
  endpoint: string;
  websiteId: string;
  hostname: string;
}>;

export type AnalyticsContext = Readonly<{
  mode?: TrainingMode;
  pageCode?: string;
}>;

export type TrainingTracker = ReturnType<typeof createActivityTimer> & {
  attempt(): void;
  started(): void;
  fail(reason?: string): void;
  complete(): void;
  abandon(reason?: string): void;
};

const trainingModes = new Set<TrainingMode>(['SCENE', 'FREE_CHAT', 'INTERVIEW', 'IELTS']);
const safeValues = new Set(['mode', 'page_code', 'source', 'reason', 'asset_type', 'effective_duration_seconds']);

function safeData(input: Record<string, unknown> = {}) {
  return Object.fromEntries(Object.entries(input).filter(([key, value]) => {
    if (!safeValues.has(key)) return false;
    if (typeof value === 'string') return value.length > 0 && value.length <= 80;
    return (typeof value === 'number' && Number.isFinite(value)) || typeof value === 'boolean';
  }));
}

function contextData(context: AnalyticsContext = {}) {
  return safeData({ mode: context.mode, page_code: context.pageCode });
}

function validTrainingContext(context: AnalyticsContext = {}) {
  return typeof context.mode === 'string' && trainingModes.has(context.mode);
}

export class AnalyticsClient {
  private distinctId: string | null = null;
  private currentUrl = '/';
  private readonly activeTrackers = new Set<TrainingTracker>();
  private appVisible = true;

  constructor(
    private readonly config: UmamiConfig,
    private readonly dependencies: Readonly<{
      fetch: FetchLike;
      now?: Clock;
      language?: () => string;
      screen?: () => string;
      userAgent?: () => string;
    }>,
  ) {}

  setDistinctId(value: string | null | undefined) {
    this.distinctId = typeof value === 'string' && value.length > 0 && value.length <= 50 ? value : null;
  }

  setAppVisible(visible: boolean) {
    this.appVisible = visible;
    this.activeTrackers.forEach((tracker) => tracker.setVisible(visible));
  }

  trackPageView(pathname = '/') {
    this.currentUrl = normalizeTrackedPath(pathname);
    this.send();
  }

  trackModeSelection(context: AnalyticsContext = {}, source = 'navigation') {
    if (validTrainingContext(context)) {
      this.send('mode_selected', { ...contextData(context), source });
    }
  }

  trackLearningAsset(context: AnalyticsContext = {}, assetType = 'REPORT') {
    this.send('learning_asset_view', { ...contextData(context), asset_type: assetType });
  }

  training(context: AnalyticsContext = {}) {
    const timer = createActivityTimer(this.dependencies.now);
    const base = contextData(context);
    let attempted = false;
    let ended = false;
    const tracker: TrainingTracker = {
      ...timer,
      attempt: () => {
        if (ended || attempted || !validTrainingContext(context)) return;
        attempted = true;
        this.send('training_start_attempt', base);
      },
      started: () => {
        if (ended || timer.isStarted() || !validTrainingContext(context)) return;
        timer.start();
        timer.setVisible(this.appVisible);
        this.activeTrackers.add(tracker);
        this.send('training_started', base);
      },
      fail: (reason = 'REALTIME_ERROR') => {
        if (ended || timer.isStarted() || !validTrainingContext(context)) return;
        this.send('training_start_failed', { ...base, reason });
        ended = true;
      },
      complete: () => {
        if (ended || !timer.isStarted()) return;
        this.send('training_completed', { ...base, effective_duration_seconds: timer.stop() });
        this.activeTrackers.delete(tracker);
        ended = true;
      },
      abandon: (reason = 'USER_EXIT') => {
        if (ended || !timer.isStarted()) return;
        this.send('training_abandoned', { ...base, reason, effective_duration_seconds: timer.stop() });
        this.activeTrackers.delete(tracker);
        ended = true;
      },
    };
    return tracker;
  }

  private send(name?: string, data: Record<string, unknown> = {}) {
    if (!this.config.enabled) return;

    const payload = {
      website: this.config.websiteId,
      hostname: this.config.hostname,
      language: this.dependencies.language?.() ?? 'zh-CN',
      screen: this.dependencies.screen?.() ?? 'mobile',
      title: 'UniSpeaking',
      url: this.currentUrl,
      id: this.distinctId || undefined,
      name,
      data: name ? safeData(data) : undefined,
    };

    void this.dependencies.fetch(this.config.endpoint, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'User-Agent': this.dependencies.userAgent?.() ?? 'UniSpeaking-Mobile/1.0',
      },
      body: JSON.stringify({ type: 'event', payload }),
    }).catch(() => undefined);
  }
}

export type AnalyticsTrackerFactory = Pick<AnalyticsClient, 'training'>;
