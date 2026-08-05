const baseRoute = {
  authMode: "signup",
  training: null,
  result: null,
  sceneId: null,
  sessionId: null,
  conversationSessionId: null,
  assetSceneId: null,
  helpRoute: null,
  aboutRoute: null,
  publicAccess: false,
};

const appRoute = (page, extra = {}) => ({
  ...baseRoute,
  flow: "app",
  page,
  ...extra,
});

export const APP_PAGES = [
  "conversation",
  "scenes",
  "assets",
  "ielts",
  "ielts-assets",
  "interview",
  "interview-assets",
  "profile",
  "insights",
  "membership",
  "settings",
  "security",
  "help",
  "about",
];

export const PAGE_PATHS = {
  conversation: "/conversation",
  scenes: "/scenes",
  assets: "/assets",
  profile: "/profile",
  insights: "/profile/insights",
  membership: "/membership",
  settings: "/settings",
  security: "/profile/security",
  help: "/help",
  about: "/about",
  ielts: "/ielts",
  "ielts-assets": "/ielts/assets",
  interview: "/interview",
  "interview-assets": "/interview/assets",
};

export const paths = {
  root: "/",
  auth: {
    login: "/login",
    signup: "/signup",
    level: "/level",
    teacher: "/teacher",
  },
  app: PAGE_PATHS,
  conversation: {
    root: "/conversation",
    session: (sessionId) => `/conversation/${encodeURIComponent(sessionId)}`,
  },
  scenes: {
    training: "/scenes/training",
    result: "/scenes/training/result",
    word: (sceneId) => `/scenes/${encodeURIComponent(sceneId)}/word`,
    phrase: (sceneId) => `/scenes/${encodeURIComponent(sceneId)}/phrase`,
    sentence: (sceneId) => `/scenes/${encodeURIComponent(sceneId)}/sentence`,
    session: (sceneId, sessionId) => `/scenes/${encodeURIComponent(sceneId)}/session/${encodeURIComponent(sessionId)}`,
    sessionResult: (sceneId, sessionId) => `/scenes/${encodeURIComponent(sceneId)}/session/${encodeURIComponent(sessionId)}/result`,
    assets: (sceneId) => `/scenes/${encodeURIComponent(sceneId)}/assets`,
  },
  assets: {
    root: "/assets",
    latest: "/assets/records/latest",
  },
  ielts: {
    root: "/ielts",
    part: (part) => `/ielts/${part}`,
    topic: (part, selection) => `/ielts/${part}/${encodeURIComponent(selection)}`,
    step: (part, selection, screen) => part === "mock"
      ? `/ielts/mock/${screen}`
      : `/ielts/${part}/${encodeURIComponent(selection)}/${screen}`,
    assets: {
      root: "/ielts/assets",
      history: "/ielts/assets/history",
      trends: "/ielts/assets/trends",
    },
  },
  interview: {
    root: "/interview",
    preparing: "/interview/preparing",
    live: "/interview/live",
    finalizing: "/interview/finalizing",
    report: "/interview/report",
    reportPartial: "/interview/report/partial",
    reportFailed: "/interview/report/failed",
    error: "/interview/error",
    transcript: "/interview/report/transcript",
    assets: {
      root: "/interview/assets",
      history: "/interview/assets/history",
      trends: "/interview/assets/trends",
      record: (recordId) => `/interview/assets/${encodeURIComponent(recordId)}`,
    },
  },
  help: {
    root: "/help",
    category: (categoryId) => `/help/category/${encodeURIComponent(categoryId)}`,
    article: (articleId) => `/help/article/${encodeURIComponent(articleId)}`,
  },
  about: {
    root: "/about",
    userAgreement: "/about/user-agreement",
    privacyPolicy: "/about/privacy-policy",
    aiService: "/about/ai-service",
  },
};

const ieltsPartBySegment = {
  part1: "p1",
  part2: "p2",
  part3: "p3",
  mock: "mock",
};

const ieltsScreens = ["setup", "session", "analysis", "report"];
const interviewScreens = ["preparing", "live", "finalizing", "report", "error", "report-failed"];
const assetTabs = ["history", "trends"];
const safeDecode = (value) => {
  try {
    return decodeURIComponent(value);
  } catch {
    return value;
  }
};

export function normalizePath(pathname = "/") {
  const withLeadingSlash = pathname.startsWith("/") ? pathname : `/${pathname}`;
  return withLeadingSlash.replace(/\/{2,}/g, "/").replace(/\/+$/, "") || "/";
}

export function parseIeltsRoute(pathname) {
  const path = normalizePath(pathname);
  const segments = path.split("/").filter(Boolean);
  if (segments[0] !== "ielts") return null;
  if (segments.length === 1) {
    return appRoute("ielts", { ieltsRoute: { area: "training", screen: "home" } });
  }
  if (segments[1] === "assets") {
    const requestedTab = segments[2] === "review" ? "history" : segments[2];
    const tab = assetTabs.includes(requestedTab) ? requestedTab : "overview";
    return appRoute("ielts-assets", {
      ieltsRoute: { area: "assets", tab },
      canonicalPath: tab === "overview" ? paths.ielts.assets.root : paths.ielts.assets[tab],
    });
  }

  const part = ieltsPartBySegment[segments[1]];
  if (!part) {
    return appRoute("ielts", {
      ieltsRoute: { area: "training", screen: "home" },
      canonicalPath: paths.ielts.root,
    });
  }
  if (part === "mock") {
    const screen = ieltsScreens.includes(segments[2]) ? segments[2] : "setup";
    return appRoute("ielts", {
      ieltsRoute: { area: "training", part, screen, selection: "random" },
      canonicalPath: paths.ielts.step("mock", "random", screen),
    });
  }
  if (segments.length === 2) {
    return appRoute("ielts", { ieltsRoute: { area: "training", part, screen: "topics" } });
  }

  const selection = safeDecode(segments[2]);
  const screen = ieltsScreens.includes(segments[3]) ? segments[3] : "setup";
  return appRoute("ielts", {
    ieltsRoute: { area: "training", part, screen, selection },
    canonicalPath: paths.ielts.step(segments[1], selection, screen),
  });
}

export function parseInterviewRoute(pathname) {
  const path = normalizePath(pathname);
  const segments = path.split("/").filter(Boolean);
  if (segments[0] !== "interview") return null;
  if (segments[1] === "assets") {
    if (segments[2] && !assetTabs.includes(segments[2])) {
      return appRoute("interview-assets", {
        interviewRoute: { area: "assets", record: safeDecode(segments[2]) },
      });
    }
    const tab = assetTabs.includes(segments[2]) ? segments[2] : "overview";
    return appRoute("interview-assets", {
      interviewRoute: { area: "assets", tab },
      canonicalPath: tab === "overview" ? paths.interview.assets.root : paths.interview.assets[tab],
    });
  }
  if (segments[1] === "report" && segments[2] === "transcript") {
    return appRoute("interview", { interviewRoute: { area: "training", screen: "transcript" } });
  }
  if (segments[1] === "report" && segments[2] === "partial") {
    return appRoute("interview", { interviewRoute: { area: "training", screen: "report", result: "partial" } });
  }
  if (segments[1] === "report" && segments[2] === "failed") {
    return appRoute("interview", { interviewRoute: { area: "training", screen: "report-failed" } });
  }

  const screen = interviewScreens.includes(segments[1]) ? segments[1] : "input";
  return appRoute("interview", {
    interviewRoute: { area: "training", screen },
    ...(segments[1] && screen === "input" ? { canonicalPath: paths.interview.root } : {}),
  });
}

export function parseSceneRoute(pathname) {
  const path = normalizePath(pathname);
  const segments = path.split("/").filter(Boolean);
  if (segments[0] !== "scenes" || segments.length < 2) return null;
  if (segments[1] === "training") return null;

  const sceneId = safeDecode(segments[1]);
  const stage = segments[2];
  if (["word", "phrase", "sentence"].includes(stage)) {
    return appRoute("scenes", {
      sceneId,
      training: {
        sceneId,
        stage,
        initialStep: stage === "sentence" ? "read" : "learn",
        returnPage: "scenes",
        standaloneSpeak: false,
      },
    });
  }
  if (stage === "session" && segments[3]) {
    const sessionId = safeDecode(segments[3]);
    return appRoute("scenes", {
      sceneId,
      sessionId,
      training: {
        sceneId,
        sessionId,
        stage: "session",
        initialStep: "speak",
        returnPage: "scenes",
        standaloneSpeak: false,
      },
      ...(segments[4] === "result" ? { result: { completed: true } } : {}),
    });
  }
  if (stage === "assets") {
    return appRoute("assets", {
      sceneId,
      assetSceneId: sceneId,
      assetView: "detail",
    });
  }
  return appRoute("scenes", { canonicalPath: paths.app.scenes });
}

export function parseHelpRoute(pathname) {
  const path = normalizePath(pathname);
  const segments = path.split("/").filter(Boolean);
  if (segments[0] !== "help") return null;
  if (segments.length === 1) {
    return appRoute("help", { helpRoute: { screen: "home" }, publicAccess: true });
  }
  if (segments.length === 3 && segments[1] === "category") {
    return appRoute("help", {
      helpRoute: { screen: "category", categoryId: safeDecode(segments[2]) },
      publicAccess: true,
    });
  }
  if (segments.length === 3 && segments[1] === "article") {
    return appRoute("help", {
      helpRoute: { screen: "article", articleId: safeDecode(segments[2]) },
      publicAccess: true,
    });
  }
  return appRoute("help", {
    helpRoute: { screen: "home" },
    publicAccess: true,
    canonicalPath: paths.help.root,
  });
}

export function parseAboutRoute(pathname) {
  const path = normalizePath(pathname);
  const segments = path.split("/").filter(Boolean);
  if (segments[0] !== "about") return null;
  if (segments.length === 1) {
    return appRoute("about", { aboutRoute: { screen: "home" } });
  }
  const documentIds = ["user-agreement", "privacy-policy", "ai-service"];
  if (segments.length === 2 && documentIds.includes(segments[1])) {
    return appRoute("about", {
      aboutRoute: { screen: "document", documentId: segments[1] },
    });
  }
  return appRoute("about", {
    aboutRoute: { screen: "home" },
    canonicalPath: paths.about.root,
  });
}

function previewRoute(preview) {
  if (!preview) return null;
  if (preview === "teacher") return { ...baseRoute, flow: "teacher", page: "conversation" };
  if (preview === "training") {
    return appRoute("scenes", { training: { initialStep: "learn", returnPage: "scenes", standaloneSpeak: false } });
  }
  if (preview === "result") {
    return appRoute("scenes", {
      training: { initialStep: "speak", returnPage: "scenes", standaloneSpeak: false },
      result: { completed: true },
    });
  }
  if (APP_PAGES.includes(preview)) return appRoute(preview);
  return null;
}

export function resolveRoute(locationLike = window.location) {
  const pathname = normalizePath(locationLike.pathname);
  const search = locationLike.search || "";
  const preview = previewRoute(new URLSearchParams(search).get("preview"));
  if (preview) return preview;

  if (pathname === paths.root) return { ...baseRoute, flow: "splash", page: "conversation" };
  if (pathname === paths.auth.login) return { ...baseRoute, flow: "auth", page: "conversation", authMode: "login" };
  if (pathname === paths.auth.signup) return { ...baseRoute, flow: "auth", page: "conversation", authMode: "signup" };
  if (pathname === paths.auth.level) return { ...baseRoute, flow: "level", page: "conversation" };
  if (pathname === paths.auth.teacher) return { ...baseRoute, flow: "teacher", page: "conversation" };

  if (pathname.startsWith(`${paths.conversation.root}/`)) {
    const segments = pathname.split("/").filter(Boolean);
    if (segments.length === 2) {
      return appRoute("conversation", {
        conversationSessionId: safeDecode(segments[1]),
      });
    }
  }

  if (pathname === paths.scenes.training || pathname === "/training") {
    return appRoute("scenes", {
      training: { initialStep: "learn", returnPage: "scenes", standaloneSpeak: false },
      ...(pathname === "/training" ? { canonicalPath: paths.scenes.training } : {}),
    });
  }
  if (pathname === paths.scenes.result || pathname === "/result") {
    return appRoute("scenes", {
      training: { initialStep: "speak", returnPage: "scenes", standaloneSpeak: false },
      result: { completed: true },
      ...(pathname === "/result" ? { canonicalPath: paths.scenes.result } : {}),
    });
  }
  if (pathname === paths.assets.latest) return appRoute("assets", { assetView: "detail" });
  const sceneRoute = parseSceneRoute(pathname);
  if (sceneRoute) return sceneRoute;

  if (pathname === "/ielts-assets") {
    return appRoute("ielts-assets", {
      ieltsRoute: { area: "assets", tab: "overview" },
      canonicalPath: paths.ielts.assets.root,
    });
  }
  const ieltsRoute = parseIeltsRoute(pathname);
  if (ieltsRoute) return ieltsRoute;
  const interviewRoute = parseInterviewRoute(pathname);
  if (interviewRoute) return interviewRoute;
  const helpRoute = parseHelpRoute(pathname);
  if (helpRoute) return helpRoute;
  const aboutRoute = parseAboutRoute(pathname);
  if (aboutRoute) return aboutRoute;

  const page = Object.entries(PAGE_PATHS).find(([, routePath]) => routePath === pathname)?.[0];
  if (page) return appRoute(page, {
    assetView: page === "assets" && new URLSearchParams(search).get("view") === "detail" ? "detail" : undefined,
  });

  return {
    ...baseRoute,
    flow: "splash",
    page: "conversation",
    canonicalPath: paths.root,
  };
}

export function hrefForPage(page) {
  return PAGE_PATHS[page] || paths.app.conversation;
}
