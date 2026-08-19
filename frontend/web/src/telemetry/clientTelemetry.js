const API_BASE = (import.meta.env?.VITE_BACKEND_URL || "").replace(/\/$/, "");
const ENDPOINT = `${API_BASE}/api/telemetry/events`;
const ACCESS_TOKEN_KEY = "unispeaking.accessToken";
const ANONYMOUS_ID_KEY = "unispeaking.telemetry.anonymousId";
const SESSION_ID_KEY = "unispeaking.telemetry.sessionId";
const RELEASE = import.meta.env?.VITE_APP_RELEASE || "web@development";
const MAX_BATCH_SIZE = 20;
const FLUSH_INTERVAL_MS = 5_000;
const MAX_QUEUE_SIZE = 100;

let queue = [];
let flushTimer = null;
let initialized = false;
let sentryEnabled = false;
let sentry = null;
let telemetryUserId = null;

function randomId(prefix) {
  const value = globalThis.crypto?.randomUUID?.()
    || `${Date.now()}-${Math.random().toString(36).slice(2, 12)}`;
  return `${prefix}-${value}`;
}

function storedId(storage, key, prefix) {
  try {
    const existing = storage.getItem(key);
    if (existing) return existing;
    const created = randomId(prefix);
    storage.setItem(key, created);
    return created;
  } catch {
    return randomId(prefix);
  }
}

function anonymousId() {
  return storedId(window.localStorage, ANONYMOUS_ID_KEY, "web");
}

function browserSessionId() {
  return storedId(window.sessionStorage, SESSION_ID_KEY, "page");
}

function cleanRoute(value) {
  const location = globalThis.window?.location;
  const routeValue = value ?? location?.href ?? "/";
  try {
    const parsed = new URL(routeValue, location?.origin || "http://localhost");
    return parsed.pathname;
  } catch {
    return String(routeValue).split(/[?#]/, 1)[0].slice(0, 300);
  }
}

function cleanStack(stack) {
  return String(stack || "")
    .replace(/([?&](?:token|access_token|authorization)=)[^&\s)]+/gi, "$1[redacted]")
    .slice(0, 8_000) || null;
}

function cleanAttributes(attributes = {}) {
  return Object.fromEntries(Object.entries(attributes)
    .filter(([key, value]) => /^[a-z][a-z0-9_]{0,63}$/.test(key)
      && ["string", "number", "boolean"].includes(typeof value)
      && (typeof value !== "number" || Number.isFinite(value)))
    .slice(0, 32)
    .map(([key, value]) => [key, typeof value === "string" ? value.slice(0, 500) : value]));
}

function eventPayload(eventType, options = {}) {
  return {
    eventType,
    platform: "WEB",
    severity: options.severity || "INFO",
    occurredAt: new Date().toISOString(),
    anonymousId: anonymousId(),
    sessionId: options.sessionId || browserSessionId(),
    route: cleanRoute(options.route),
    release: RELEASE,
    message: String(options.message || "").slice(0, 500) || null,
    stack: cleanStack(options.stack),
    attributes: cleanAttributes(options.attributes),
  };
}

function scheduleFlush() {
  if (flushTimer || !queue.length) return;
  flushTimer = globalThis.setTimeout(() => {
    flushTimer = null;
    void flushTelemetry();
  }, FLUSH_INTERVAL_MS);
  flushTimer?.unref?.();
}

export function recordTelemetry(eventType, options = {}) {
  if (!/^[a-z][a-z0-9_.-]{1,63}$/.test(eventType)) return;
  queue.push(eventPayload(eventType, options));
  if (queue.length > MAX_QUEUE_SIZE) queue = queue.slice(-MAX_QUEUE_SIZE);
  if (queue.length >= MAX_BATCH_SIZE) void flushTelemetry();
  else scheduleFlush();
}

export async function flushTelemetry({ keepalive = false } = {}) {
  if (!queue.length) return;
  const events = queue.splice(0, MAX_BATCH_SIZE);
  const token = window.sessionStorage.getItem(ACCESS_TOKEN_KEY);
  try {
    const response = await fetch(ENDPOINT, {
      method: "POST",
      credentials: "include",
      keepalive,
      headers: {
        "Content-Type": "application/json",
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: JSON.stringify({ events }),
    });
    if (!response.ok && response.status !== 429) throw new Error(`telemetry ${response.status}`);
  } catch {
    if (!keepalive) {
      queue = [...events, ...queue].slice(0, MAX_QUEUE_SIZE);
      scheduleFlush();
    }
  }
  if (queue.length && !keepalive) scheduleFlush();
}

export function setTelemetryUser(userId) {
  telemetryUserId = userId ? String(userId) : null;
  if (sentryEnabled) sentry?.setUser(telemetryUserId ? { id: telemetryUserId } : null);
}

export function captureException(error, context = {}) {
  const normalized = error instanceof Error ? error : new Error(String(error || "Unknown error"));
  recordTelemetry(context.eventType || "js.exception", {
    severity: context.severity || "ERROR",
    message: normalized.message,
    stack: normalized.stack,
    attributes: {
      error_name: normalized.name,
      ...context.attributes,
    },
  });
  if (sentryEnabled) {
    sentry.withScope((scope) => {
      Object.entries(cleanAttributes(context.attributes)).forEach(([key, value]) => scope.setExtra(key, value));
      sentry.captureException(normalized);
    });
  }
}

function observePerformance() {
  if (!("PerformanceObserver" in window)) return;
  const reportMetric = (metricName, value, unit = "ms") => recordTelemetry("web.performance", {
    attributes: { metric_name: metricName, metric_value: Math.round(value * 100) / 100, unit },
  });
  try {
    const paintObserver = new PerformanceObserver((list) => {
      list.getEntries().forEach((entry) => {
        if (entry.name === "first-contentful-paint") reportMetric("fcp", entry.startTime);
      });
    });
    paintObserver.observe({ type: "paint", buffered: true });
  } catch { /* Unsupported performance entry type. */ }

  try {
    let lcp = 0;
    const lcpObserver = new PerformanceObserver((list) => {
      const latest = list.getEntries().at(-1);
      if (latest) lcp = latest.startTime;
    });
    lcpObserver.observe({ type: "largest-contentful-paint", buffered: true });
    document.addEventListener("visibilitychange", () => {
      if (document.visibilityState === "hidden" && lcp > 0) reportMetric("lcp", lcp);
    }, { once: true });
  } catch { /* Unsupported performance entry type. */ }

  try {
    let cls = 0;
    const clsObserver = new PerformanceObserver((list) => {
      list.getEntries().forEach((entry) => {
        if (!entry.hadRecentInput) cls += entry.value;
      });
    });
    clsObserver.observe({ type: "layout-shift", buffered: true });
    document.addEventListener("visibilitychange", () => {
      if (document.visibilityState === "hidden") reportMetric("cls", cls, "score");
    }, { once: true });
  } catch { /* Unsupported performance entry type. */ }

  try {
    let inp = 0;
    const eventObserver = new PerformanceObserver((list) => {
      list.getEntries().forEach((entry) => { inp = Math.max(inp, entry.duration); });
    });
    eventObserver.observe({ type: "event", buffered: true, durationThreshold: 40 });
    document.addEventListener("visibilitychange", () => {
      if (document.visibilityState === "hidden" && inp > 0) reportMetric("inp", inp);
    }, { once: true });
  } catch { /* Unsupported performance entry type. */ }

  window.addEventListener("load", () => {
    window.setTimeout(() => {
      const navigation = performance.getEntriesByType("navigation")[0];
      if (navigation) {
        reportMetric("ttfb", navigation.responseStart);
        reportMetric("dom_content_loaded", navigation.domContentLoadedEventEnd);
        reportMetric("page_load", navigation.loadEventEnd || performance.now());
      }
      const root = document.getElementById("root");
      const visibleText = root?.innerText?.trim() || "";
      const visibleElements = root?.querySelectorAll?.("img,canvas,video,svg,input,button")?.length || 0;
      if (!root || (!visibleText && visibleElements === 0)) {
        recordTelemetry("web.white_screen", {
          severity: "ERROR",
          message: "Root view is empty after page load",
          attributes: { detection_delay_ms: 5_000 },
        });
      }
    }, 5_000);
  }, { once: true });
}

export function initializeBrowserTelemetry() {
  if (initialized) return;
  initialized = true;
  const dsn = import.meta.env?.VITE_SENTRY_DSN?.trim();
  if (dsn) {
    void import("@sentry/react").then((sdk) => {
      sdk.init({
        dsn,
        environment: import.meta.env?.MODE || "production",
        release: RELEASE,
        sendDefaultPii: false,
        integrations: [sdk.browserTracingIntegration()],
        tracesSampleRate: 0.1,
      });
      sentry = sdk;
      sentryEnabled = true;
      sentry.setUser(telemetryUserId ? { id: telemetryUserId } : null);
    });
  }

  window.addEventListener("error", (event) => {
    const resource = event.target;
    if (resource && resource !== window && !(event instanceof ErrorEvent)) {
      const resourceUrl = resource.currentSrc || resource.src || resource.href || "";
      recordTelemetry("web.resource_error", {
        severity: "ERROR",
        message: `Failed to load ${resource.tagName || "resource"}`,
        attributes: {
          resource_tag: String(resource.tagName || "unknown").toLowerCase(),
          resource_path: cleanRoute(resourceUrl),
        },
      });
      return;
    }
    const error = event.error instanceof Error ? event.error : new Error(event.message || "JavaScript error");
    recordTelemetry("js.exception", {
      severity: "ERROR",
      message: error.message,
      stack: error.stack,
      attributes: { error_name: error.name },
    });
  }, true);

  window.addEventListener("unhandledrejection", (event) => {
    const error = event.reason instanceof Error ? event.reason : new Error(String(event.reason || "Unhandled rejection"));
    recordTelemetry("js.unhandled_rejection", {
      severity: "ERROR",
      message: error.message,
      stack: error.stack,
      attributes: { error_name: error.name },
    });
  });

  document.addEventListener("visibilitychange", () => {
    if (document.visibilityState === "hidden") void flushTelemetry({ keepalive: true });
  });
  window.addEventListener("pagehide", () => { void flushTelemetry({ keepalive: true }); });
  observePerformance();
  recordTelemetry("web.app_started", { attributes: { user_agent: navigator.userAgent.slice(0, 300) } });
}
