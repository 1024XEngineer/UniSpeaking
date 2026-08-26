import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { captureException, flushTelemetry, initializeBrowserTelemetry, recordTelemetry, setTelemetryUser } from "../clientTelemetry.js";

beforeEach(() => {
  vi.useFakeTimers();
  localStorage.clear();
  sessionStorage.clear();
  vi.stubGlobal("fetch", vi.fn(async () => new Response(null, { status: 204 })));
  document.body.innerHTML = '<div id="root">Visible app</div>';
});
afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

describe("browser telemetry queue", () => {
  it("filters invalid events, sanitizes payloads, batches, and sends auth", async () => {
    localStorage.setItem("unispeaking.accessToken", "secret");
    recordTelemetry("BAD", { attributes: { Good_key: "ok", bad: "x", count: 2, nan: Number.NaN, tooLong: "x".repeat(600) } });
    recordTelemetry("web.test", { route: "/safe?token=secret", message: "hello", stack: "at x?token=secret", sessionId: "session" });
    vi.advanceTimersByTime(5_000);
    await vi.runOnlyPendingTimersAsync();
    await flushTelemetry();
    expect(fetch).toHaveBeenCalledWith(expect.stringContaining("/api/telemetry/events"), expect.objectContaining({ method: "POST", headers: expect.objectContaining({ Authorization: "Bearer secret" }) }));
    const request = fetch.mock.calls[0][1];
    const body = JSON.parse(request.body);
    expect(body.events).toHaveLength(1);
    expect(body.events[0]).toMatchObject({ eventType: "web.test", route: "/safe", sessionId: "session", message: "hello" });
    expect(body.events[0].stack).toContain("[redacted]");
  });

  it("requeues failed non-keepalive events and accepts 429/keepalive semantics", async () => {
    fetch.mockRejectedValueOnce(new Error("offline"));
    recordTelemetry("web.retry");
    await flushTelemetry();
    fetch.mockResolvedValueOnce(new Response(null, { status: 429 }));
    await flushTelemetry({ keepalive: true });
    expect(fetch).toHaveBeenCalledTimes(2);
    recordTelemetry("web.retry-again");
    await flushTelemetry({ keepalive: true });
    expect(fetch).toHaveBeenCalledTimes(3);
  });

  it("captures exceptions and installs browser error/rejection/page lifecycle listeners", async () => {
    captureException("plain failure", { eventType: "custom.error", attributes: { good: true, BAD: "drop" } });
    initializeBrowserTelemetry();
    initializeBrowserTelemetry();
    const resource = document.createElement("img");
    resource.src = "https://example.com/image.png?token=secret";
    window.dispatchEvent(new Event("error"));
    resource.dispatchEvent(new Event("error"));
    window.dispatchEvent(new ErrorEvent("error", { error: new Error("boom"), message: "boom" }));
    const rejection = new Event("unhandledrejection");
    Object.defineProperty(rejection, "reason", { value: "rejected" });
    window.dispatchEvent(rejection);
    document.dispatchEvent(new Event("visibilitychange"));
    window.dispatchEvent(new Event("pagehide"));
    setTelemetryUser("user-1");
    setTelemetryUser(null);
    expect(fetch).toHaveBeenCalled();
    await flushTelemetry({ keepalive: true });
  });

  it("reports supported performance entries and white-screen checks", async () => {
    vi.resetModules();
    const telemetryModule = await import("../clientTelemetry.js");
    const observers = [];
    class TestPerformanceObserver {
      constructor(callback) {
        this.callback = callback;
        observers.push(this);
      }

      observe(options) {
        this.type = options.type;
      }
    }
    vi.stubGlobal("PerformanceObserver", TestPerformanceObserver);
    window.PerformanceObserver = TestPerformanceObserver;
    vi.spyOn(performance, "getEntriesByType").mockReturnValue([{
      responseStart: 12.345,
      domContentLoadedEventEnd: 34.567,
      loadEventEnd: 56.789,
    }]);
    telemetryModule.initializeBrowserTelemetry();

    const observerByType = Object.fromEntries(observers.map((observer) => [observer.type, observer]));
    observerByType.paint.callback({ getEntries: () => [{ name: "first-contentful-paint", startTime: 10.126 }] });
    observerByType["largest-contentful-paint"].callback({ getEntries: () => [{ startTime: 22.229 }] });
    observerByType["layout-shift"].callback({ getEntries: () => [
      { hadRecentInput: false, value: 0.1 },
      { hadRecentInput: true, value: 0.9 },
    ] });
    observerByType.event.callback({ getEntries: () => [{ duration: 41 }, { duration: 30 }] });

    document.body.innerHTML = '<div id="root"></div>';
    window.dispatchEvent(new Event("load"));
    vi.advanceTimersByTime(5_000);
    Object.defineProperty(document, "visibilityState", { configurable: true, value: "hidden" });
    document.dispatchEvent(new Event("visibilitychange"));
    await telemetryModule.flushTelemetry();
    await telemetryModule.flushTelemetry();
    await telemetryModule.flushTelemetry();

    const sentEvents = fetch.mock.calls.flatMap(([, options]) => {
      if (!options?.body) return [];
      return JSON.parse(options.body).events || [];
    });
    expect(sentEvents.map((event) => event.eventType)).toEqual(expect.arrayContaining([
      "web.performance",
      "web.white_screen",
    ]));
    expect(sentEvents.some((event) => event.attributes.metric_name === "cls" && event.attributes.unit === "score")).toBe(true);
    expect(sentEvents.some((event) => event.attributes.metric_name === "inp")).toBe(true);
  });

  it("handles storage failures and trims the queue to its maximum size", async () => {
    const originalLocalStorage = Object.getOwnPropertyDescriptor(window, "localStorage");
    const originalSessionStorage = Object.getOwnPropertyDescriptor(window, "sessionStorage");
    const brokenStorage = {
      getItem: () => { throw new Error("storage blocked"); },
      setItem: () => { throw new Error("storage blocked"); },
      removeItem: () => { throw new Error("storage blocked"); },
    };
    Object.defineProperty(window, "localStorage", { configurable: true, value: brokenStorage });
    Object.defineProperty(window, "sessionStorage", { configurable: true, value: brokenStorage });
    try {
      recordTelemetry("web.storage-fallback", { route: "not a valid URL" });
    } finally {
      Object.defineProperty(window, "localStorage", originalLocalStorage);
      Object.defineProperty(window, "sessionStorage", originalSessionStorage);
    }

    await flushTelemetry();
    expect(fetch).toHaveBeenCalled();

    fetch.mockClear();
    for (let index = 0; index < 101; index += 1) recordTelemetry(`web.queue-${index}`);
    await flushTelemetry();
    expect(fetch.mock.calls[0]).toBeDefined();
    expect(JSON.parse(fetch.mock.calls[0][1].body).events).toHaveLength(20);
  });

  it("covers telemetry defaults, empty branches, and Sentry integration", async () => {
    vi.resetModules();
    const telemetryModule = await import("../clientTelemetry.js");
    await telemetryModule.flushTelemetry();
    telemetryModule.recordTelemetry("web.defaults", {
      severity: "",
      message: "",
      stack: "",
      attributes: undefined,
    });
    await telemetryModule.flushTelemetry();
    const defaultBody = JSON.parse(fetch.mock.calls.at(-1)[1].body);
    expect(defaultBody.events[0]).toMatchObject({ severity: "INFO", message: null, stack: null, attributes: {} });

    vi.stubGlobal("crypto", { randomUUID: undefined });
    telemetryModule.recordTelemetry("web.fallback-id", {
      route: "https://example.test/path?token=secret",
      sessionId: "",
      attributes: { enabled: false, count: Infinity, "bad-key": true },
    });
    await telemetryModule.flushTelemetry();
    const fallbackBody = JSON.parse(fetch.mock.calls.at(-1)[1].body);
    expect(fallbackBody.events[0].route).toBe("/path");
    expect(fallbackBody.events[0].sessionId).toMatch(/^page-/);
    expect(fallbackBody.events[0].attributes).toEqual({ enabled: false });

    vi.resetModules();
    vi.stubEnv("VITE_SENTRY_DSN", " https://sentry.example/123 ");
    const sentry = {
      init: vi.fn(),
      browserTracingIntegration: vi.fn(() => ({ name: "tracing" })),
      setUser: vi.fn(),
      withScope: vi.fn((callback) => callback({ setExtra: vi.fn() })),
      captureException: vi.fn(),
    };
    vi.doMock("@sentry/react", () => sentry);
    const sentryTelemetry = await import("../clientTelemetry.js");
    sentryTelemetry.setTelemetryUser(42);
    sentryTelemetry.initializeBrowserTelemetry();
    await vi.waitFor(() => expect(sentry.init).toHaveBeenCalled());
    expect(sentry.init).toHaveBeenCalledWith(expect.objectContaining({
      dsn: "https://sentry.example/123",
      sendDefaultPii: false,
      tracesSampleRate: 0.1,
    }));
    expect(sentry.setUser).toHaveBeenCalledWith({ id: "42" });
    sentryTelemetry.captureException(new Error("sentry failure"), { attributes: { feature: "test" } });
    expect(sentry.withScope).toHaveBeenCalled();
    expect(sentry.captureException).toHaveBeenCalledWith(expect.any(Error));
  });

  it("handles unsupported performance observers and all listener fallbacks", async () => {
    vi.resetModules();
    vi.stubGlobal("PerformanceObserver", class UnsupportedObserver {
      constructor() { throw new Error("unsupported"); }
    });
    window.PerformanceObserver = globalThis.PerformanceObserver;
    const telemetryModule = await import("../clientTelemetry.js");
    telemetryModule.initializeBrowserTelemetry();

    const resource = document.createElement("script");
    resource.src = "not a valid url?access_token=secret";
    const resourceError = new Event("error");
    Object.defineProperty(resourceError, "target", { value: resource });
    window.dispatchEvent(resourceError);
    const jsError = new Event("error");
    Object.defineProperties(jsError, {
      target: { value: window },
      error: { value: null },
      message: { value: "window boom" },
    });
    window.dispatchEvent(jsError);
    const rejection = new Event("unhandledrejection");
    Object.defineProperty(rejection, "reason", { value: new Error("promise boom") });
    window.dispatchEvent(rejection);
    document.body.innerHTML = '<div id="root"><button>click</button></div>';
    window.dispatchEvent(new Event("load"));
    vi.advanceTimersByTime(5_000);
    await telemetryModule.flushTelemetry({ keepalive: true });
    const events = fetch.mock.calls.flatMap(([, options]) => options?.body ? JSON.parse(options.body).events : []);
    expect(events.map((event) => event.eventType)).toEqual(expect.arrayContaining([
      "web.app_started",
      "web.resource_error",
      "js.exception",
      "js.unhandled_rejection",
    ]));
  });
});
