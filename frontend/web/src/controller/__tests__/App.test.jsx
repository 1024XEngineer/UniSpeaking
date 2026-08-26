import { act, cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const mockApi = vi.hoisted(() => ({
  changePassword: vi.fn(),
  clearAuthSession: vi.fn(),
  advanceCustomSceneFlow: vi.fn(),
  createCustomSceneFlow: vi.fn(),
  deleteLearningAsset: vi.fn(),
  evaluateSentenceReading: vi.fn(),
  generateCustomScene: vi.fn(),
  getDailyPicks: vi.fn(),
  getAchievementOverview: vi.fn(),
  getAccessToken: vi.fn(),
  getCurrentUser: vi.fn(),
  getLearningAsset: vi.fn(),
  getLearningAssets: vi.fn(),
  getProfileOverview: vi.fn(),
  getUserPreference: vi.fn(),
  synthesizeSpeech: vi.fn(),
  translateSceneText: vi.fn(),
  translateSessionText: vi.fn(),
  updateProfile: vi.fn(),
  updateUserPreference: vi.fn(),
  uploadProfileAvatar: vi.fn(),
  revokeWebSession: vi.fn(),
}));

const mockAuthApi = vi.hoisted(() => ({
  issueEmailChallenge: vi.fn(),
  issuePasswordResetChallenge: vi.fn(),
  loginWithPassword: vi.fn(),
  logoutUser: vi.fn(),
  registerWithEmail: vi.fn(),
  resetPasswordWithEmail: vi.fn(),
  validateRegistrationCredentials: vi.fn(),
}));

const mockAnalytics = vi.hoisted(() => ({
  setDistinctId: vi.fn(),
  trackPageView: vi.fn(),
  trackModeSelection: vi.fn(),
  trackLearningAsset: vi.fn(),
  training: vi.fn(() => ({
    attempt: vi.fn(),
    started: vi.fn(),
    fail: vi.fn(),
    complete: vi.fn(),
    abandon: vi.fn(),
    pause: vi.fn(),
    resume: vi.fn(),
    setVisible: vi.fn(),
  })),
}));

const mockRealtime = vi.hoisted(() => ({
  clients: [],
  createRealtimeClient: vi.fn((options = {}) => {
    const client = {
      options,
      start: vi.fn(),
      pause: vi.fn(),
      resume: vi.fn(),
      stop: vi.fn(),
    };
    mockRealtime.clients.push(client);
    return client;
  }),
  realtimeFailureMessage: vi.fn((error) => error?.message || "实时连接失败"),
}));

const mockRecorder = vi.hoisted(() => ({
  create: vi.fn(),
}));

const mockAchievements = vi.hoisted(() => ({
  synchronizeAchievements: vi.fn().mockResolvedValue(undefined),
  clearAchievementNotifications: vi.fn(),
}));

vi.mock("@phosphor-icons/react", () => {
  const Icon = () => null;
  return {
    ArrowLeft: Icon,
    ArrowClockwise: Icon,
    ArrowRight: Icon,
    ArrowsClockwise: Icon,
    BookOpenText: Icon,
    Briefcase: Icon,
    CalendarBlank: Icon,
    CaretDown: Icon,
    CaretRight: Icon,
    Subtitles: Icon,
    Check: Icon,
    CheckCircle: Icon,
    Clock: Icon,
    Crown: Icon,
    EnvelopeSimple: Icon,
    Eye: Icon,
    EyeSlash: Icon,
    Fire: Icon,
    GearSix: Icon,
    Headphones: Icon,
    Lifebuoy: Icon,
    LockKey: Icon,
    Medal: Icon,
    Microphone: Icon,
    MicrophoneSlash: Icon,
    PaperPlaneTilt: Icon,
    Pause: Icon,
    PencilSimple: Icon,
    PhoneDisconnect: Icon,
    Play: Icon,
    Plus: Icon,
    ShieldCheck: Icon,
    SignOut: Icon,
    SlidersHorizontal: Icon,
    SpeakerHigh: Icon,
    SpeakerSlash: Icon,
    SquaresFour: Icon,
    Trash: Icon,
    Translate: Icon,
    UploadSimple: Icon,
    User: Icon,
    Waveform: Icon,
    X: Icon,
  };
});

vi.mock("lucide-react", () => {
  const Icon = () => null;
  return {
    AudioLines: Icon,
    CalendarCheck2: Icon,
    ChevronLeft: Icon,
    ChevronRight: Icon,
    Compass: Icon,
    Footprints: Icon,
    Info: Icon,
    MessagesSquare: Icon,
    PackageCheck: Icon,
    Sparkles: Icon,
    Target: Icon,
    ChartLine: Icon,
  };
});

vi.mock("../../infrastructure/http/apiClient.js", () => ({
  AUTH_SESSION_EXPIRED_EVENT: "unispeaking:auth-session-expired",
  ...mockApi,
}));

vi.mock("../../userAuthApi.js", () => ({
  ...mockAuthApi,
  UserAuthApiError: class UserAuthApiError extends Error {
    constructor(code, message) {
      super(message);
      this.name = "UserAuthApiError";
      this.code = code;
    }
  },
}));

vi.mock("../../analytics/analyticsClient.js", () => ({ analytics: mockAnalytics }));
vi.mock("../../websocket/realtimeClient.js", () => mockRealtime);
vi.mock("../../infrastructure/audio/audioRecorder.js", () => ({
  createPcmWavRecorder: mockRecorder.create,
}));
vi.mock("../../component/achievement/AchievementNotifications.jsx", () => ({
  useAchievementNotifications: () => mockAchievements,
}));

function Stub({ name, children, ...props }) {
  return <section data-testid={`stub-${name}`} {...props}>{children || name}</section>;
}

vi.mock("../../component/ielts/IeltsModule.jsx", () => ({
  IeltsAssets: (props) => <Stub name="ielts-assets">
    <button onClick={props.onBack}>返回场景广场</button>
    <button onClick={() => props.onNavigate("/ielts/assets/history")}>打开雅思历史</button>
    <button onClick={props.onBackToAssets}>返回普通资产</button>
    <button onClick={props.onBackToInterview}>打开面试资产</button>
    <button onClick={props.onTraining}>开始雅思训练</button>
  </Stub>,
  IeltsTrainingCenter: (props) => <Stub name="ielts-training">
    <button onClick={() => props.onNavigate("/ielts/assets")}>打开雅思资产</button>
    <button onClick={props.onExit}>返回场景广场</button>
    <button onClick={props.onAssets}>打开雅思资产入口</button>
  </Stub>,
}));
vi.mock("../../component/interview/InterviewModule.jsx", () => ({
  InterviewAssets: (props) => <Stub name="interview-assets">
    <button onClick={() => props.onNavigate("/interview/assets/history")}>打开面试历史</button>
    <button onClick={props.onBack}>返回场景广场</button>
    <button onClick={props.onBackToAssets}>返回普通资产</button>
    <button onClick={props.onBackToIelts}>打开雅思资产</button>
    <button onClick={props.onTraining}>开始面试训练</button>
    <button onClick={() => props.onPractice("practice-scene")}>复练面试场景</button>
  </Stub>,
  InterviewModule: (props) => <Stub name="interview-training">
    <button onClick={() => props.onNavigate("/interview/assets")}>打开面试资产</button>
    <button onClick={props.onBack}>返回场景广场</button>
  </Stub>,
}));
vi.mock("../../component/help/HelpCenter.jsx", () => ({
  HelpCenter: ({ route, onNavigate }) => (
    <Stub name="help-center">
      <output data-testid="help-route">{JSON.stringify(route || null)}</output>
      <button onClick={() => onNavigate?.("/help")}>帮助首页</button>
      <button onClick={() => onNavigate?.("/help/category/account")}>账户分类</button>
      <button onClick={() => onNavigate?.("/help/article/session-expired")}>会话文章</button>
    </Stub>
  ),
}));
vi.mock("../../component/help/HelpLayout.jsx", () => ({ HelpLayout: ({ children }) => <Stub name="help-layout">{children}</Stub> }));
vi.mock("../../component/help/ExternalFeedbackLink.jsx", () => ({ feedbackUrl: "https://feedback.example.com" }));
vi.mock("../../component/landing/LandingPage.jsx", () => ({
  LandingPage: ({ onLogin, onStart, onWeb, onSpecialty }) => (
    <main data-testid="landing-page">
      <button onClick={() => onStart("Tina")}>开始体验</button>
      <button onClick={onLogin}>登录</button>
      <button onClick={onWeb}>打开应用</button>
      <button onClick={() => onSpecialty("ielts")}>雅思专项</button>
      <button onClick={() => onSpecialty("interview")}>英文面试</button>
      <button onClick={() => onSpecialty("unknown")}>未知专项</button>
    </main>
  ),
}));
vi.mock("../../component/common/NewtonsCradle.jsx", () => ({ NewtonsCradle: () => null }));
vi.mock("../../component/common/Modal.jsx", () => ({ Modal: ({ children }) => <div role="dialog">{children}</div> }));
vi.mock("../../component/profile/LearningInsights.jsx", () => ({
  LearningInsights: ({ onStartTraining }) => (
    <Stub name="learning-insights">
      <button onClick={() => onStartTraining("FREE_CHAT")}>开始自由对话</button>
      <button onClick={() => onStartTraining("CUSTOM_SCENE")}>开始场景训练</button>
    </Stub>
  ),
}));
vi.mock("../../component/profile/AccountSecurity.jsx", () => ({
  AccountSecurity: ({ onOpenPassword, onLogout }) => (
    <Stub name="account-security">
      <button onClick={onOpenPassword}>修改密码</button>
      <button onClick={onLogout}>安全页退出登录</button>
    </Stub>
  ),
}));
vi.mock("../../component/profile/AboutProduct.jsx", () => ({
  AboutProduct: ({ onNavigate, onHelpNavigate }) => (
    <Stub name="about-product">
      <button onClick={() => onNavigate("/about/user-agreement")}>用户协议</button>
      <button onClick={() => onHelpNavigate("/help")}>帮助中心</button>
    </Stub>
  ),
}));
vi.mock("../../component/profile/ProductLegalDocument.jsx", () => ({
  ProductLegalDocument: ({ onNavigate }) => (
    <Stub name="legal-document">
      <button onClick={() => onNavigate("/about")}>返回关于产品</button>
    </Stub>
  ),
}));
vi.mock("../../HumanVerification.jsx", () => ({
  HumanVerification: ({ onVerify }) => <button type="button" onClick={() => onVerify("human-token")}>人机验证</button>,
}));

const { App } = await import("../App.jsx");

function resetLocation(path = "/") {
  window.history.replaceState({}, "", path);
}

function configureAuthenticatedDefaults() {
  window.sessionStorage.setItem("unispeaking.accessToken", "session-token");
  mockApi.getAccessToken.mockImplementation(() => window.sessionStorage.getItem("unispeaking.accessToken"));
  mockApi.getCurrentUser.mockResolvedValue({ id: "user-1", nickname: "测试用户" });
  mockApi.getUserPreference.mockResolvedValue({ cefrLevel: "B1", preferredVoice: "Tina", preferredAiSpeechSpeed: "NATURAL" });
  mockApi.getProfileOverview.mockResolvedValue({
    account: {
      userId: "user-1",
      nickname: "测试用户",
      displayName: "测试用户",
      email: "user@example.com",
    },
    calendar: {
      month: "2026-08",
      checkedDates: ["2026-08-01"],
      checkedInToday: false,
    },
    statistics: {
      weeklyPracticeSeconds: 125,
      trainingRecordCount: 1,
      consecutiveLearningDays: 2,
      lastSevenDays: [],
    },
  });
  mockApi.getDailyPicks.mockResolvedValue({ picks: [] });
}

describe("App", () => {
  afterEach(() => {
    cleanup();
  });

  beforeEach(() => {
    resetLocation("/");
    window.localStorage.clear();
    window.sessionStorage.clear();
    vi.clearAllMocks();
    mockRealtime.clients.length = 0;
    mockApi.getAccessToken.mockReturnValue(null);
    mockApi.getDailyPicks.mockResolvedValue({ picks: [] });
    mockApi.getAchievementOverview.mockResolvedValue({ achievements: [] });
    mockApi.getLearningAssets.mockResolvedValue([]);
    mockApi.getLearningAsset.mockResolvedValue(null);
    mockApi.getProfileOverview.mockResolvedValue({ account: { userId: "user-1" } });
    mockApi.getUserPreference.mockResolvedValue({ cefrLevel: "B1", preferredVoice: "Tina" });
    mockApi.updateUserPreference.mockResolvedValue({ cefrLevel: "B1", preferredVoice: "Tina" });
    mockApi.translateSessionText.mockResolvedValue({ translatedText: "你好" });
    mockApi.translateSceneText.mockResolvedValue({ translatedText: "中文摘要" });
    mockApi.synthesizeSpeech.mockResolvedValue(new Blob(["audio"], { type: "audio/wav" }));
    mockApi.generateCustomScene.mockResolvedValue(null);
    mockApi.createCustomSceneFlow.mockResolvedValue(undefined);
    mockApi.advanceCustomSceneFlow.mockResolvedValue(undefined);
    mockApi.evaluateSentenceReading.mockResolvedValue({ overallScore: 88, passed: true, words: [] });
    mockRecorder.create.mockResolvedValue({
      stop: vi.fn().mockResolvedValue(new Blob(["wav"], { type: "audio/wav" })),
      cancel: vi.fn(),
    });
    mockRealtime.createRealtimeClient.mockImplementation((options = {}) => {
      const client = {
        options,
        start: vi.fn().mockResolvedValue({ sessionId: "default-session" }),
        pause: vi.fn().mockResolvedValue(undefined),
        resume: vi.fn().mockResolvedValue(undefined),
        stop: vi.fn().mockResolvedValue(undefined),
      };
      mockRealtime.clients.push(client);
      return client;
    });
    HTMLCanvasElement.prototype.getContext = vi.fn(() => ({
      scale: vi.fn(),
      clearRect: vi.fn(),
      beginPath: vi.fn(),
      moveTo: vi.fn(),
      lineTo: vi.fn(),
      closePath: vi.fn(),
      stroke: vi.fn(),
      fill: vi.fn(),
      arc: vi.fn(),
      fillText: vi.fn(),
    }));
    mockAuthApi.validateRegistrationCredentials.mockReturnValue(null);
    if (!URL.createObjectURL) URL.createObjectURL = vi.fn();
    if (!URL.revokeObjectURL) URL.revokeObjectURL = vi.fn();
    URL.createObjectURL = vi.fn(() => "blob:avatar-preview");
    URL.revokeObjectURL = vi.fn();
    window.alert = vi.fn();
  });

  it("renders the splash route, removes a legacy local token, and enters auth", async () => {
    window.localStorage.setItem("unispeaking.accessToken", "legacy-token");
    render(<App />);

    await waitFor(() => expect(screen.getByTestId("landing-page")).toBeInTheDocument());
    expect(window.localStorage.getItem("unispeaking.accessToken")).toBeNull();
    expect(mockApi.getAccessToken).toHaveBeenCalled();

    await userEvent.setup().click(screen.getByRole("button", { name: "登录" }));
    expect(await screen.findByRole("heading", { name: "欢迎回来" })).toBeInTheDocument();
    expect(window.location.pathname).toBe("/login");
  });

  it("logs in through the auth screen and navigates the authenticated shell", async () => {
    resetLocation("/login");
    mockApi.getAccessToken.mockReturnValue(null);
    mockAuthApi.loginWithPassword.mockResolvedValue({ accessToken: "session-token", user: { id: "user-1" } });
    const user = userEvent.setup();
    render(<App />);

    await screen.findByRole("heading", { name: "欢迎回来" });
    await user.type(screen.getByLabelText("邮箱"), "user@example.com");
    await user.type(screen.getByLabelText("密码"), "a-valid-password");
    await user.click(screen.getByRole("button", { name: "人机验证" }));

    await waitFor(() => expect(mockAuthApi.loginWithPassword).toHaveBeenCalledWith(
      "user@example.com",
      "a-valid-password",
      "human-token",
    ));
    await waitFor(() => expect(screen.getByRole("heading", { name: "想聊什么都可以" })).toBeInTheDocument());
    expect(mockApi.getUserPreference).toHaveBeenCalled();
    expect(mockAnalytics.setDistinctId).toHaveBeenCalledWith("user-1");
  });

  it("navigates from conversation to scenes and the mocked IELTS page", async () => {
    configureAuthenticatedDefaults();
    resetLocation("/conversation");
    const user = userEvent.setup();
    render(<App />);

    await screen.findByRole("heading", { name: "想聊什么都可以" });
    await user.click(screen.getByRole("button", { name: "场景广场" }));
    expect(await screen.findByRole("heading", { name: "场景广场" })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /雅思口语/ }));
    expect(await screen.findByTestId("stub-ielts-training")).toBeInTheDocument();
    expect(window.location.pathname).toBe("/ielts");
    expect(mockAnalytics.trackModeSelection).toHaveBeenCalledWith(
      { mode: "IELTS", pageCode: "ielts-training" },
      "scene-plaza",
    );
  });

  it("records the return path and shows login after session expiration", async () => {
    configureAuthenticatedDefaults();
    resetLocation("/conversation");
    render(<App />);
    await screen.findByRole("heading", { name: "想聊什么都可以" });

    fireEvent(window, new Event("unispeaking:auth-session-expired"));
    expect(await screen.findByRole("heading", { name: "欢迎回来" })).toBeInTheDocument();
    expect(window.location.pathname).toBe("/login");
    expect(window.sessionStorage.getItem("unispeaking.authReturnPath")).toBe("/conversation");
    expect(mockAchievements.clearAchievementNotifications).toHaveBeenCalled();
  });

  it("keeps public help accessible without an authentication token", async () => {
    resetLocation("/help");
    render(<App />);
    expect(await screen.findByTestId("stub-help-center")).toBeInTheDocument();
    expect(screen.getByTestId("stub-help-layout")).toBeInTheDocument();
    expect(window.location.pathname).toBe("/help");
  });

  it("renders profile navigation and stores a selected page", async () => {
    configureAuthenticatedDefaults();
    resetLocation("/conversation");
    const user = userEvent.setup();
    render(<App />);
    await screen.findByRole("heading", { name: "想聊什么都可以" });

    const sidebar = screen.getByRole("complementary");
    await user.click(within(sidebar).getByRole("button", { name: "个人中心" }));
    expect(await screen.findByRole("heading", { name: "你的学习空间" })).toBeInTheDocument();
    expect(window.location.pathname).toBe("/profile");
  });

  it("registers an account through email verification and enters level setup", async () => {
    resetLocation("/signup");
    mockAuthApi.issueEmailChallenge.mockResolvedValue({ challengeId: "signup-challenge" });
    mockAuthApi.registerWithEmail.mockResolvedValue({
      accessToken: "signup-token",
      user: { id: "user-2", nickname: "新用户" },
    });
    const user = userEvent.setup();
    render(<App />);

    expect(await screen.findByRole("heading", { name: "创建账号" })).toBeInTheDocument();
    await user.type(screen.getByLabelText("邮箱"), "new@example.com");
    await user.type(screen.getByLabelText("昵称"), "新用户");
    await user.type(screen.getByLabelText("密码"), "a-valid-password");
    await user.type(screen.getByLabelText("确认密码"), "a-valid-password");
    await user.click(screen.getByRole("button", { name: "人机验证" }));

    expect(await screen.findByRole("heading", { name: "验证你的邮箱" })).toBeInTheDocument();
    expect(mockAuthApi.issueEmailChallenge).toHaveBeenCalledWith("new@example.com", "human-token");
    await user.type(screen.getByLabelText("6 位验证码"), "12a3456");
    expect(screen.getByLabelText("6 位验证码")).toHaveValue("123456");
    await user.click(screen.getByRole("button", { name: "完成注册" }));

    await waitFor(() => expect(mockAuthApi.registerWithEmail).toHaveBeenCalledWith({
      email: "new@example.com",
      password: "a-valid-password",
      nickname: "新用户",
      challengeId: "signup-challenge",
      code: "123456",
    }));
    expect(await screen.findByRole("heading", { name: /你现在说英语时/ })).toBeInTheDocument();
  });

  it("resets a password after switching from login to reset verification", async () => {
    resetLocation("/login");
    mockAuthApi.issuePasswordResetChallenge.mockResolvedValue({ challengeId: "reset-challenge" });
    mockAuthApi.resetPasswordWithEmail.mockResolvedValue({ success: true });
    const user = userEvent.setup();
    render(<App />);

    await screen.findByRole("heading", { name: "欢迎回来" });
    await user.click(screen.getByRole("button", { name: "忘记密码？" }));
    expect(screen.getByRole("heading", { name: "重置密码" })).toBeInTheDocument();
    await user.type(screen.getByLabelText("邮箱"), "reset@example.com");
    await user.click(screen.getByRole("button", { name: "人机验证" }));

    expect(await screen.findByLabelText("6 位验证码")).toBeInTheDocument();
    expect(mockAuthApi.issuePasswordResetChallenge).toHaveBeenCalledWith("reset@example.com", "human-token");
    await user.type(screen.getByLabelText("6 位验证码"), "654321");
    await user.type(screen.getByPlaceholderText("至少 12 位字符"), "new-valid-password");
    await user.type(screen.getByPlaceholderText("再次输入新密码"), "new-valid-password");
    await user.click(screen.getByRole("button", { name: "重置密码" }));

    await waitFor(() => expect(mockAuthApi.resetPasswordWithEmail).toHaveBeenCalledWith({
      email: "reset@example.com",
      password: "new-valid-password",
      challengeId: "reset-challenge",
      code: "654321",
    }));
    expect(await screen.findByRole("heading", { name: "欢迎回来" })).toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent("密码已重置");
  });

  it("covers profile help, about, legal document, and recommended training navigation", async () => {
    configureAuthenticatedDefaults();
    resetLocation("/profile");
    const user = userEvent.setup();
    render(<App />);
    await screen.findByRole("heading", { name: "你的学习空间" });

    const profileNav = screen.getByRole("navigation", { name: "个人中心导航" });
    await user.click(within(profileNav).getByRole("button", { name: "学习目标与洞察" }));
    expect(await screen.findByTestId("stub-learning-insights")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "开始自由对话" }));
    expect(await screen.findByRole("heading", { name: "想聊什么都可以" })).toBeInTheDocument();

    await user.click(within(screen.getByRole("complementary")).getByRole("button", { name: "个人中心" }));
    await user.click(within(screen.getByRole("navigation", { name: "个人中心导航" })).getByRole("button", { name: "帮助中心" }));
    expect(await screen.findByTestId("stub-help-center")).toBeInTheDocument();

    await user.click(within(screen.getByRole("navigation", { name: "个人中心导航" })).getByRole("button", { name: "关于产品" }));
    expect(await screen.findByTestId("stub-about-product")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "用户协议" }));
    expect(await screen.findByTestId("stub-legal-document")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "返回关于产品" }));
    expect(await screen.findByTestId("stub-about-product")).toBeInTheDocument();
    await user.click(within(screen.getByTestId("stub-about-product")).getByRole("button", { name: "帮助中心" }));
    expect(await screen.findByTestId("stub-help-center")).toBeInTheDocument();
  });

  it("updates assistant settings and shows the synchronized state", async () => {
    configureAuthenticatedDefaults();
    resetLocation("/settings");
    mockApi.updateUserPreference.mockResolvedValue({
      cefrLevel: "A",
      preferredVoice: "Katerina",
      preferredAiSpeechSpeed: "SLOWER",
    });
    const user = userEvent.setup();
    render(<App />);
    await screen.findByRole("heading", { name: "AI 助手设置" });

    await user.click(screen.getByRole("button", { name: "慢一些" }));
    await waitFor(() => expect(mockApi.updateUserPreference).toHaveBeenCalledWith({ preferredAiSpeechSpeed: "SLOWER" }));
    expect(screen.getByText("设置已同步")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /刚开始学/ }));
    await user.click(screen.getByRole("option", { name: /可以简单交流/ }));
    await waitFor(() => expect(mockApi.updateUserPreference).toHaveBeenCalledWith({ cefrLevel: "B" }));

    await user.click(screen.getByRole("button", { name: /Clara/ }));
    await waitFor(() => expect(mockApi.updateUserPreference).toHaveBeenCalledWith({ preferredVoice: "Katerina" }));
  });

  it("confirms logout and returns to the splash page", async () => {
    configureAuthenticatedDefaults();
    resetLocation("/profile");
    mockAuthApi.logoutUser.mockResolvedValue(undefined);
    const user = userEvent.setup();
    render(<App />);
    await screen.findByRole("heading", { name: "你的学习空间" });

    await user.click(screen.getByRole("button", { name: "退出登录" }));
    expect(screen.getByRole("heading", { name: "确定要退出登录吗？" })).toBeInTheDocument();
    await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: "退出登录" }));
    await waitFor(() => expect(mockAuthApi.logoutUser).toHaveBeenCalledTimes(1));
    expect(await screen.findByTestId("landing-page")).toBeInTheDocument();
    expect(window.location.pathname).toBe("/");
    expect(mockAchievements.clearAchievementNotifications).toHaveBeenCalled();
  });

  it("loads, filters, opens, and deletes a scene learning asset", async () => {
    configureAuthenticatedDefaults();
    resetLocation("/assets");
    const record = {
      sceneId: "scene-1",
      title: "咖啡店点单",
      label: "餐饮",
      latestPracticedAt: "2026-08-01T10:00:00Z",
      latestSessionId: "session-1",
      latestScore: 84,
    };
    mockApi.getLearningAssets.mockResolvedValue([record]);
    mockApi.getLearningAsset.mockResolvedValue({
      ...record,
      aiRole: "咖啡师",
      dialogueEvaluation: { dialogue: [], turnEvaluation: [] },
      wordList: [{ contentId: "word-1", englishText: "latte", chineseText: "拿铁" }],
      phraseList: [],
      sentenceList: [],
    });
    mockApi.deleteLearningAsset.mockResolvedValue(undefined);
    const user = userEvent.setup();
    render(<App />);
    await screen.findByRole("heading", { name: "学习资产" });
    expect(await screen.findByRole("button", { name: /咖啡店点单/ })).toBeInTheDocument();
    expect(await screen.findByText("latte")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "餐饮" }));
    expect(screen.getByRole("button", { name: /咖啡店点单/ })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "删除当前学习资产" }));
    expect(screen.getByRole("heading", { name: "删除当前学习资产？" })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "确认删除" }));
    await waitFor(() => expect(mockApi.deleteLearningAsset).toHaveBeenCalledWith("scene-1"));
  });

  it("covers authentication validation, mode switching, and the onboarding steps", async () => {
    resetLocation("/signup");
    const user = userEvent.setup();
    render(<App />);

    await screen.findByRole("heading", { name: "创建账号" });
    await user.click(screen.getByRole("button", { name: "人机验证" }));
    expect(screen.getByRole("alert")).toHaveTextContent("请输入有效邮箱地址");

    await user.type(screen.getByLabelText("邮箱"), "onboard@example.com");
    await user.type(screen.getByLabelText("昵称"), "测试昵称");
    await user.type(screen.getByLabelText("密码"), "short");
    await user.type(screen.getByLabelText("确认密码"), "different-password");
    mockAuthApi.validateRegistrationCredentials.mockReturnValue("WEAK_PASSWORD");
    await user.click(screen.getByRole("button", { name: "人机验证" }));
    expect(screen.getByRole("alert")).toHaveTextContent("密码至少需要 12 位字符");

    mockAuthApi.validateRegistrationCredentials.mockReturnValue(null);
    await user.clear(screen.getByLabelText("密码"));
    await user.type(screen.getByLabelText("密码"), "a-valid-password");
    await user.clear(screen.getByLabelText("确认密码"));
    await user.type(screen.getByLabelText("确认密码"), "a-valid-password");
    mockAuthApi.issueEmailChallenge.mockResolvedValue({ challengeId: "onboard-challenge" });
    await user.click(screen.getByRole("button", { name: "人机验证" }));
    expect(await screen.findByRole("heading", { name: "验证你的邮箱" })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "返回修改邮箱" }));
    expect(screen.getByRole("heading", { name: "创建账号" })).toBeInTheDocument();
    expect(screen.getByLabelText("密码")).toHaveValue("a-valid-password");

    await user.click(screen.getByRole("button", { name: "直接登录" }));
    expect(screen.getByRole("heading", { name: "欢迎回来" })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "创建账号" }));
    expect(screen.getByRole("heading", { name: "创建账号" })).toBeInTheDocument();

    resetLocation("/login");
    cleanup();
    render(<App />);
    await screen.findByRole("heading", { name: "欢迎回来" });
    await user.click(screen.getByRole("button", { name: "忘记密码？" }));
    await user.click(screen.getByRole("button", { name: "返回登录" }));
    expect(screen.getByRole("heading", { name: "欢迎回来" })).toBeInTheDocument();

    resetLocation("/login");
    cleanup();
    mockAuthApi.loginWithPassword.mockResolvedValue({ accessToken: "onboard-token", user: { id: "user-3" } });
    mockApi.getUserPreference.mockResolvedValue({ cefrLevel: null, preferredVoice: null });
    render(<App />);
    await screen.findByRole("heading", { name: "欢迎回来" });
    await user.type(screen.getByLabelText("邮箱"), "onboard@example.com");
    await user.type(screen.getByLabelText("密码"), "a-valid-password");
    await user.click(screen.getByRole("button", { name: "人机验证" }));
    expect(await screen.findByRole("heading", { name: /你现在说英语时/ })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /可以简单交流/ }));
    mockApi.updateUserPreference.mockResolvedValue({ cefrLevel: "B", preferredVoice: null });
    await user.click(screen.getByRole("button", { name: "下一步" }));
    expect(await screen.findByRole("heading", { name: "选择一位 AI 老师" })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "选择 James" }));
    mockApi.updateUserPreference.mockResolvedValue({ cefrLevel: "B", preferredVoice: "Harvey" });
    await user.click(screen.getByRole("button", { name: "选择这位老师" }));
    expect(await screen.findByRole("heading", { name: "想聊什么都可以" })).toBeInTheDocument();
  });

  it("covers the free conversation lifecycle, realtime events, translation, and cleanup", async () => {
    configureAuthenticatedDefaults();
    resetLocation("/conversation");
    const user = userEvent.setup();
    render(<App />);
    await screen.findByRole("heading", { name: "想聊什么都可以" });

    await user.click(screen.getByRole("button", { name: "对话设置" }));
    const settings = screen.getByRole("dialog", { name: "对话设置" });
    expect(settings).toBeInTheDocument();
    await user.click(within(settings).getByRole("button", { name: "自然" }));
    await user.click(within(settings).getByRole("button", { name: "英语水平" }));
    await user.click(within(settings).getByRole("option", { name: /可以简单交流/ }));
    await user.click(within(settings).getByRole("button", { name: /James 英式口音/ }));
    mockApi.updateUserPreference.mockResolvedValue({ cefrLevel: "B", preferredVoice: "Harvey", preferredAiSpeechSpeed: "NATURAL" });
    await user.click(within(settings).getByRole("button", { name: "保存设置" }));
    await waitFor(() => expect(mockApi.updateUserPreference).toHaveBeenCalled());

    const startPromise = Promise.resolve({ sessionId: "free-session-1" });
    mockRealtime.createRealtimeClient.mockImplementationOnce((options = {}) => {
      const client = {
        options,
        start: vi.fn().mockReturnValue(startPromise),
        pause: vi.fn().mockResolvedValue(undefined),
        resume: vi.fn().mockResolvedValue(undefined),
        stop: vi.fn().mockResolvedValue(undefined),
      };
      mockRealtime.clients.push(client);
      return client;
    });
    await user.click(screen.getByRole("button", { name: "开始对话" }));
    await waitFor(() => expect(mockRealtime.clients.length).toBeGreaterThan(0));
    const client = mockRealtime.clients.at(-1);
    await waitFor(() => expect(client.start).toHaveBeenCalledWith({ voice: "Harvey", speechSpeed: "NATURAL" }));
    await screen.findByRole("button", { name: "结束当前会话" });

    await act(async () => {
      client.options.onEvent({ type: "local.connecting" });
      client.options.onEvent({ type: "local.connected" });
      client.options.onEvent({ type: "session.created" });
      client.options.onEvent({ type: "session.updated" });
      client.options.onEvent({ type: "local.transcript.final", itemId: "assistant-1", owner: 2, text: "Hey there" });
      client.options.onEvent({ type: "conversation.item.input_audio_transcription.delta", item_id: "user-1", delta: "Hello" });
      client.options.onEvent({ type: "response.text.delta", response_id: "assistant-live", delta: " How are you?" });
      client.options.onEvent({ type: "input_audio_buffer.speech_started" });
      client.options.onEvent({ type: "response.audio.delta" });
      client.options.onEvent({ type: "local.interrupted" });
    });
    expect(screen.getByText("Hey there")).toBeInTheDocument();
    expect(screen.getByText("Hello")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "查看James这句字幕的翻译" }));
    await waitFor(() => expect(mockApi.translateSessionText).toHaveBeenCalledWith("free-session-1", "Hey there"));
    await user.click(screen.getByRole("button", { name: "收起James这句字幕的翻译" }));

    await user.click(screen.getByRole("button", { name: "暂停会话" }));
    await waitFor(() => expect(client.pause).toHaveBeenCalled());
    await user.click(screen.getByRole("button", { name: "恢复会话" }));
    await waitFor(() => expect(client.resume).toHaveBeenCalled());
    await user.click(screen.getByRole("button", { name: "关闭字幕" }));
    await user.click(screen.getByRole("button", { name: "打开字幕" }));

    await user.click(screen.getByRole("button", { name: "结束当前会话" }));
    await waitFor(() => expect(client.stop).toHaveBeenCalledWith({ reason: "user_stop" }));
    await waitFor(() => expect(screen.getByRole("heading", { name: "想聊什么都可以" })).toBeInTheDocument());
  });

  it("covers generated scene creation, staged learning, pronunciation scoring, result, and cleanup", async () => {
    configureAuthenticatedDefaults();
    resetLocation("/scenes");
    const scene = {
      sceneId: "generated-scene-1",
      title: "在咖啡店点单",
      label: "餐饮",
      background: "Practice ordering a drink at a cafe.",
      aiRole: "a barista",
      userRole: "a customer",
      learningGoal: "Order clearly and politely.",
      estimatedMinutes: 8,
      wordList: [{ contentId: "word-1", englishText: "latte", chineseText: "拿铁", phonetic: "/ˈlɑːteɪ/" }],
      phraseList: [{ contentId: "phrase-1", englishText: "less sweet", chineseText: "不太甜", phonetic: "/les swiːt/" }],
      sentenceList: [
        { contentId: "sentence-1", englishText: "Could I have a latte?", chineseText: "我可以要一杯拿铁吗？" },
        { contentId: "sentence-2", englishText: "Could you make it less sweet?", chineseText: "可以少放一点糖吗？" },
      ],
    };
    mockApi.generateCustomScene.mockResolvedValue(scene);
    mockApi.translateSceneText.mockResolvedValue({ translatedText: "咖啡店练习摘要" });
    mockApi.evaluateSentenceReading
      .mockRejectedValueOnce(new Error("朗读评分服务暂时不可用"))
      .mockResolvedValue({
        overallScore: 91,
        passed: true,
        words: [{ word: "Could", wordScore: 95 }, { word: "I", wordScore: 80 }],
      });
    const user = userEvent.setup();
    render(<App />);

    await screen.findByRole("heading", { name: "场景广场" });
    await user.type(screen.getByPlaceholderText(/你今天想练习什么/), "在咖啡店练习点单");
    await user.click(screen.getByRole("button", { name: /生成练习场景/ }));
    expect(await screen.findByText("场景已准备好")).toBeInTheDocument();
    await waitFor(() => expect(mockApi.translateSceneText).toHaveBeenCalled());
    expect(await screen.findAllByText("咖啡店练习摘要")).not.toHaveLength(0);

    await user.click(screen.getByRole("button", { name: "确认进入" }));
    await waitFor(() => expect(mockApi.createCustomSceneFlow).toHaveBeenCalledWith("generated-scene-1"));
    expect(await screen.findByRole("heading", { name: "latte" })).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "进入词组" }));
    await waitFor(() => expect(mockApi.advanceCustomSceneFlow).toHaveBeenCalledWith("generated-scene-1", "WORD_LEARNING"));
    expect(await screen.findByRole("heading", { name: "less sweet" })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "进入朗读" }));
    await waitFor(() => expect(mockApi.advanceCustomSceneFlow).toHaveBeenCalledWith("generated-scene-1", "PHRASE_LEARNING"));
    expect(await screen.findByRole("heading", { name: "Could I have a latte?" })).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "开始朗读录音" }));
    await user.click(screen.getByRole("button", { name: "结束朗读并评分" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("朗读评分服务暂时不可用");
    mockApi.evaluateSentenceReading.mockResolvedValueOnce({
      overallScore: 91,
      passed: true,
      words: [{ word: "Could", wordScore: 95 }, { word: "I", wordScore: 80 }],
    });
    await user.click(screen.getByRole("button", { name: "开始朗读录音" }));
    await user.click(screen.getByRole("button", { name: "结束朗读并评分" }));
    expect(await screen.findAllByText("91")).not.toHaveLength(0);
    await user.click(screen.getByRole("button", { name: "知道了" }));
    await user.click(screen.getByRole("button", { name: "下一句" }));

    await user.click(screen.getByRole("button", { name: "开始朗读录音" }));
    await user.click(screen.getByRole("button", { name: "结束朗读并评分" }));
    await waitFor(() => expect(mockApi.evaluateSentenceReading).toHaveBeenCalledWith(
      "generated-scene-1",
      "sentence-2",
      expect.any(Blob),
    ));
    await user.click(screen.getByRole("button", { name: "进入模拟" }));
    await waitFor(() => expect(mockApi.advanceCustomSceneFlow).toHaveBeenCalledWith("generated-scene-1", "SENTENCE_LEARNING"));
    expect(await screen.findByRole("button", { name: "结束当前会话" })).toBeInTheDocument();

    const client = mockRealtime.clients.at(-1);
    await act(async () => {
      client.options.onEvent({ type: "local.connecting" });
      client.options.onEvent({ type: "local.connected", sessionId: "scene-session-1" });
      client.options.onEvent({ type: "session.updated" });
      client.options.onEvent({ type: "input_audio_buffer.speech_started" });
      client.options.onEvent({ type: "response.audio.delta" });
      client.options.onEvent({ type: "local.transcript.final", itemId: "scene-ai-1", owner: 2, text: "Welcome to the cafe." });
      client.options.onEvent({ type: "conversation.item.input_audio_transcription.delta", item_id: "scene-user-1", delta: "I would like" });
      client.options.onEvent({ type: "response.text.delta", response_id: "scene-ai-live", delta: " a latte." });
      client.options.onEvent({
        type: "local.turn_evaluation",
        evaluation: { turnNo: 1, overallScore: 84 },
        scenarioState: { outcomes: [{ satisfied: true }], effectiveUserTurns: 1, maximumUserTurns: 10 },
      });
      client.options.onEvent({ type: "local.scenario_state", state: { stage: "CONFIRMATION", completed: false } });
      client.options.onEvent({ type: "local.scenario_completed" });
      client.options.onEvent({
        type: "local.ended",
        reason: "state_machine",
        completion: {
          evaluation: {
            finalScore: 88,
            accuracyScore: 90,
            fluencyScore: 86,
            grammarScore: 87,
            vocabularyScore: 89,
            naturalnessScore: 88,
            summary: "表达清晰，继续保持。",
          },
        },
      });
    });
    expect(await screen.findByRole("heading", { name: "模拟完成" })).toBeInTheDocument();
    expect(screen.getByText("表达清晰，继续保持。")).toBeInTheDocument();
    expect(screen.getAllByText("88").length).toBeGreaterThan(0);
    await user.click(screen.getByRole("button", { name: "返回场景广场" }));
    expect(await screen.findByRole("heading", { name: "场景广场" })).toBeInTheDocument();
  });

  it("covers scene generation, flow advancement, translation, reconnect, and stop errors", async () => {
    configureAuthenticatedDefaults();
    resetLocation("/scenes");
    const user = userEvent.setup();
    mockApi.getDailyPicks.mockRejectedValueOnce(new Error("推荐暂时不可用"));
    mockApi.generateCustomScene.mockRejectedValueOnce(new Error("场景生成失败"));
    render(<App />);

    await screen.findByRole("heading", { name: "场景广场" });
    await user.type(screen.getByPlaceholderText(/你今天想练习什么/), "练习问路");
    await user.click(screen.getByRole("button", { name: /生成练习场景/ }));
    expect(await screen.findByRole("alert")).toHaveTextContent("场景生成失败");

    const scene = {
      sceneId: "error-scene",
      title: "问路",
      label: "出行",
      background: "Ask for directions.",
      aiRole: "a local",
      userRole: "a traveler",
      learningGoal: "Confirm the route.",
      estimatedMinutes: 5,
      wordList: [{ contentId: "word", englishText: "station", chineseText: "车站" }],
      phraseList: [],
      sentenceList: [],
    };
    mockApi.generateCustomScene.mockResolvedValueOnce(scene);
    mockApi.createCustomSceneFlow.mockRejectedValueOnce(new Error("流程创建失败"));
    await user.click(screen.getByRole("button", { name: /生成练习场景/ }));
    await user.click(await screen.findByRole("button", { name: "确认进入" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("流程创建失败");
    expect(screen.queryByRole("heading", { name: "station" })).not.toBeInTheDocument();

    mockApi.createCustomSceneFlow.mockResolvedValueOnce(undefined);
    await user.clear(screen.getByPlaceholderText(/你今天想练习什么/));
    await user.type(screen.getByPlaceholderText(/你今天想练习什么/), "再次生成问路场景");
    mockApi.generateCustomScene.mockResolvedValueOnce({ ...scene, sceneId: "error-scene-2", wordList: [] });
    await user.click(screen.getByRole("button", { name: /生成练习场景/ }));
    await user.click(await screen.findByRole("button", { name: "确认进入" }));
    expect(await screen.findByText("场景内容加载失败")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "关闭训练" }));
    await user.click(screen.getByRole("button", { name: "继续训练" }));
    await user.click(screen.getByRole("button", { name: "关闭训练" }));
    await user.click(screen.getByRole("button", { name: "确认退出" }));
    expect(await screen.findByRole("heading", { name: "场景广场" })).toBeInTheDocument();
  });

  it("covers free-call microphone, provider, quota, start, and unmount errors", async () => {
    configureAuthenticatedDefaults();
    resetLocation("/conversation");
    const user = userEvent.setup();
    let client;
    mockRealtime.createRealtimeClient.mockImplementationOnce((options = {}) => {
      client = {
        options,
        start: vi.fn().mockRejectedValue(new Error("启动失败")),
        pause: vi.fn().mockResolvedValue(undefined),
        resume: vi.fn().mockResolvedValue(undefined),
        stop: vi.fn().mockResolvedValue(undefined),
      };
      mockRealtime.clients.push(client);
      return client;
    });
    render(<App />);
    await screen.findByRole("heading", { name: "想聊什么都可以" });
    await user.click(screen.getByRole("button", { name: "开始对话" }));
    expect(await screen.findByText("启动失败")).toBeInTheDocument();

    await act(async () => {
      client.options.onEvent({ type: "local.mic_error", message: "麦克风被拒绝" });
      client.options.onEvent({ type: "error", error: { message: "提供方异常" } });
      client.options.onEvent({ type: "local.provider_warning" });
      client.options.onEvent({ type: "local.greeting_timeout" });
    });
    expect(screen.getByText("提供方异常")).toBeInTheDocument();
    cleanup();
    expect(client.stop).toHaveBeenCalledWith({ notifyBackend: false, reason: "component_unmount" });
  });

  it("covers scene translation failures, completion errors, and user stop errors", async () => {
    configureAuthenticatedDefaults();
    const user = userEvent.setup();
    const scene = {
      sceneId: "scene-error-events",
      title: "问路",
      label: "出行",
      background: "问路练习",
      aiRole: "路人",
      userRole: "旅行者",
      learningGoal: "确认路线",
      estimatedMinutes: 5,
      wordList: [],
      phraseList: [],
      sentenceList: [],
    };
    window.sessionStorage.setItem("unispeaking.scene.scene-error-events", JSON.stringify(scene));
    resetLocation("/scenes/scene-error-events/session/session-error");
    mockRealtime.createRealtimeClient.mockImplementation((options = {}) => {
      const client = {
        options,
        start: vi.fn().mockResolvedValue(undefined),
        pause: vi.fn().mockResolvedValue(undefined),
        resume: vi.fn().mockResolvedValue(undefined),
        stop: vi.fn().mockRejectedValue(new Error("结束失败")),
      };
      mockRealtime.clients.push(client);
      return client;
    });
    mockApi.translateSceneText.mockRejectedValue(new Error("翻译失败"));
    render(<App />);
    expect(await screen.findByRole("button", { name: "结束当前会话" })).toBeInTheDocument();
    await waitFor(() => expect(mockRealtime.clients.length).toBeGreaterThan(0));
    const client = mockRealtime.clients.at(-1);
    await act(async () => {
      client.options.onEvent({ type: "local.connected", sessionId: "session-error" });
      client.options.onEvent({ type: "local.transcript.final", itemId: "line-1", owner: 2, text: "Where is the station?" });
      client.options.onEvent({ type: "local.scenario_state_error" });
      client.options.onEvent({ type: "local.turn_evaluation_error", message: "评分失败" });
      client.options.onEvent({ type: "local.mic_error", message: "无麦克风" });
    });
    await user.click(screen.getByRole("button", { name: /查看Emily这句字幕的翻译/ }));
    expect(await screen.findByText("翻译失败")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "结束当前会话" }));
    expect(await screen.findByRole("heading", { name: "本次模拟已结束" })).toBeInTheDocument();

    resetLocation("/scenes/scene-error-events/session/session-error");
    cleanup();
    mockRealtime.createRealtimeClient.mockImplementationOnce((options = {}) => {
      const nextClient = {
        options,
        start: vi.fn().mockResolvedValue(undefined),
        pause: vi.fn().mockResolvedValue(undefined),
        resume: vi.fn().mockResolvedValue(undefined),
        stop: vi.fn().mockResolvedValue(undefined),
      };
      mockRealtime.clients.push(nextClient);
      return nextClient;
    });
    render(<App />);
    await screen.findByRole("button", { name: "结束当前会话" });
    const completionClient = mockRealtime.clients.at(-1);
    await act(async () => {
      completionClient.options.onEvent({ type: "local.scenario_completion_error", message: "自动结束失败" });
    });
    expect(await screen.findByRole("heading", { name: "模拟完成" })).toBeInTheDocument();
  });

  it("covers public landing entry points and authentication return paths", async () => {
    const user = userEvent.setup();
    render(<App />);
    await screen.findByTestId("landing-page");

    await user.click(screen.getByRole("button", { name: "打开应用" }));
    expect(await screen.findByRole("heading", { name: "欢迎回来" })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "返回" }));
    await screen.findByTestId("landing-page");

    await user.click(screen.getByRole("button", { name: "英文面试" }));
    expect(await screen.findByRole("heading", { name: "创建账号" })).toBeInTheDocument();
    expect(window.sessionStorage.getItem("unispeaking.authReturnPath")).toBe("/interview");
    await user.click(screen.getByRole("button", { name: "返回" }));
    await screen.findByTestId("landing-page");

    await user.click(screen.getByRole("button", { name: "未知专项" }));
    expect(screen.getByTestId("landing-page")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "开始体验" }));
    expect(await screen.findByRole("heading", { name: "创建账号" })).toBeInTheDocument();
    expect(window.location.search).toBe("?voice=Tina");
  });

  it("applies a requested landing teacher and routes login to the stored destination", async () => {
    configureAuthenticatedDefaults();
    resetLocation("/login?voice=Harvey");
    mockApi.getUserPreference.mockResolvedValue({ cefrLevel: "B1", preferredVoice: "Tina" });
    mockApi.updateUserPreference.mockResolvedValue({ cefrLevel: "B1", preferredVoice: "Harvey" });
    mockAuthApi.loginWithPassword.mockResolvedValue({ accessToken: "session-token", user: { id: "user-landing" } });
    const user = userEvent.setup();
    render(<App />);

    await screen.findByRole("heading", { name: "欢迎回来" });
    await user.type(screen.getByLabelText("邮箱"), "landing@example.com");
    await user.type(screen.getByLabelText("密码"), "a-valid-password");
    await user.click(screen.getByRole("button", { name: "人机验证" }));

    await waitFor(() => expect(mockApi.updateUserPreference).toHaveBeenCalledWith({ preferredVoice: "Harvey" }));
    expect(await screen.findByRole("heading", { name: "想聊什么都可以" })).toBeInTheDocument();
    expect(window.location.pathname).toBe("/conversation");
    expect(window.sessionStorage.getItem("unispeaking.authReturnPath")).toBeNull();
  });

  it("covers assets module routing, detail replay, and delete failure recovery", async () => {
    configureAuthenticatedDefaults();
    resetLocation("/assets");
    mockApi.getLearningAssets.mockResolvedValue([
      { sceneId: "asset-a", title: "咖啡店点单", label: "餐饮", latestPracticedAt: "not-a-date", latestSessionId: null },
      { sceneId: "asset-b", title: "问路", label: "出行", createdAt: "2026-08-02T00:00:00Z", latestSessionId: "session-b", latestScore: 72 },
    ]);
    mockApi.getLearningAsset.mockResolvedValue({
      sceneId: "asset-a",
      title: "咖啡店点单",
      aiRole: "咖啡师",
      wordList: [{ contentId: "word-a", englishText: "latte", chineseText: "拿铁" }],
      phraseList: [],
      sentenceList: [],
      dialogueEvaluation: {
        dialogue: [{ owner: 1, content: "I want a latte." }, { owner: 2, content: "Sure." }],
        turnEvaluation: [{ turnNo: 1, feedbackSummary: "表达清楚", suggestedExpression: "Could I have a latte?" }],
      },
      latestSessionId: "asset-session",
    });
    mockApi.deleteLearningAsset.mockRejectedValueOnce(new Error("删除失败"));
    const user = userEvent.setup();
    render(<App />);

    await screen.findByRole("heading", { name: "学习资产" });
    await screen.findByText("latte");
    await user.click(screen.getByRole("button", { name: "出行" }));
    expect(screen.getAllByText("问路").length).toBeGreaterThan(0);
    await user.click(screen.getByRole("button", { name: "全部" }));
    await user.click(screen.getByRole("button", { name: "删除当前学习资产" }));
    await user.click(screen.getByRole("button", { name: "确认删除" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("删除失败");
    await user.click(screen.getByRole("button", { name: "取消" }));

    await user.click(screen.getByRole("button", { name: "打开当前学习资产" }));
    expect(await screen.findByRole("heading", { name: "咖啡店点单 · 语境复现" })).toBeInTheDocument();
    expect(screen.getByText("I want a latte.")).toBeInTheDocument();
    expect(screen.getByText("Could I have a latte?")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "复练场景" }));
    expect(await screen.findByRole("button", { name: "结束当前会话" })).toBeInTheDocument();
    cleanup();
    resetLocation("/assets");
    render(<App />);
    await screen.findByRole("heading", { name: "学习资产" });

    await user.click(screen.getByRole("button", { name: "切换学习资产模块" }));
    await user.click(screen.getByRole("menuitem", { name: /IELTS 学习资产/ }));
    expect(await screen.findByTestId("stub-ielts-assets")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "返回普通资产" }));
    expect(await screen.findByRole("heading", { name: "学习资产" })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "切换学习资产模块" }));
    await user.click(screen.getByRole("menuitem", { name: /面试学习资产/ }));
    expect(await screen.findByTestId("stub-interview-assets")).toBeInTheDocument();
  });

  it("covers profile editing, calendar, membership, and security error paths", async () => {
    configureAuthenticatedDefaults();
    resetLocation("/profile");
    mockApi.getProfileOverview.mockResolvedValue({
      account: { userId: "user-1", nickname: "旧昵称", displayName: "旧昵称", email: "old@example.com", avatarUrl: "avatar.png" },
      calendar: { month: "2026-07", checkedDates: ["2026-07-02"], checkedInToday: true },
      statistics: { weeklyPracticeSeconds: 0, trainingRecordCount: 0, consecutiveLearningDays: 0, lastSevenDays: [] },
    });
    mockApi.updateProfile.mockResolvedValue({ nickname: "新昵称", displayName: "新昵称" });
    mockApi.uploadProfileAvatar.mockResolvedValue({ avatarUrl: "new-avatar.png", avatarUrlExpiresAt: "2026-09-01" });
    mockApi.changePassword.mockRejectedValueOnce(new Error("当前密码错误"));
    const user = userEvent.setup();
    render(<App />);

    await screen.findByRole("heading", { name: "你的学习空间" });
    await user.click(screen.getByRole("button", { name: "查看上一个月" }));
    await waitFor(() => expect(mockApi.getProfileOverview).toHaveBeenCalledWith("2026-06"));
    await user.click(screen.getByRole("gridcell", { name: /2026 年 7 月2日/ }));
    expect(screen.getByText("已打卡")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "编辑用户名和头像" }));
    const avatarInput = screen.getByLabelText("选择新头像");
    fireEvent.change(avatarInput, { target: { files: [new File(["bad"], "avatar.gif", { type: "image/gif" })] } });
    expect(screen.getByRole("alert")).toHaveTextContent("JPEG 或 PNG");
    fireEvent.change(avatarInput, { target: { files: [new File(["image"], "avatar.png", { type: "image/png" })] } });
    await user.clear(screen.getByLabelText("用户名"));
    await user.type(screen.getByLabelText("用户名"), "新昵称");
    await user.click(screen.getByRole("button", { name: "保存修改" }));
    await waitFor(() => expect(mockApi.updateProfile).toHaveBeenCalledWith({ nickname: "新昵称" }));
    expect(mockApi.uploadProfileAvatar).toHaveBeenCalled();

    await user.click(screen.getByRole("button", { name: "会员权益" }));
    expect(await screen.findByRole("heading", { name: "会员与订阅中心" })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "升级专业版" }));
    await user.click(screen.getByRole("button", { name: "模拟支付并完成" }));
    await user.click(screen.getByRole("button", { name: "账号与安全" }));
    await user.click(screen.getByRole("button", { name: "修改密码" }));
    await user.type(screen.getByLabelText("当前密码"), "current");
    await user.type(screen.getByLabelText("新密码"), "new-password");
    await user.type(screen.getByLabelText("确认新密码"), "different");
    await user.click(screen.getByRole("button", { name: "确认修改" }));
    expect(screen.getByRole("alert")).toHaveTextContent("两次输入的新密码不一致");
    await user.clear(screen.getByLabelText("确认新密码"));
    await user.type(screen.getByLabelText("确认新密码"), "new-password");
    await user.click(screen.getByRole("button", { name: "确认修改" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("当前密码错误");
  });

  it("covers specialty assets navigation and guarded conversation navigation", async () => {
    configureAuthenticatedDefaults();
    resetLocation("/ielts");
    const user = userEvent.setup();
    render(<App />);

    expect(await screen.findByTestId("stub-ielts-training")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "打开雅思资产入口" }));
    expect(await screen.findByTestId("stub-ielts-assets")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "打开面试资产" }));
    expect(await screen.findByTestId("stub-interview-assets")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "打开雅思资产" }));
    expect(await screen.findByTestId("stub-ielts-assets")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "返回场景广场" }));
    expect(await screen.findByRole("heading", { name: "场景广场" })).toBeInTheDocument();

    resetLocation("/conversation");
    cleanup();
    configureAuthenticatedDefaults();
    render(<App />);
    await screen.findByRole("heading", { name: "想聊什么都可以" });
    await user.click(screen.getByRole("button", { name: "开始对话" }));
    const client = mockRealtime.clients.at(-1);
    await waitFor(() => expect(client.start).toHaveBeenCalled());
    await user.click(within(screen.getByRole("complementary")).getByRole("button", { name: "场景广场" }));
    expect(screen.getByRole("heading", { name: "确定要退出当前训练吗？" })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "继续训练" }));
    expect(screen.queryByRole("heading", { name: "确定要退出当前训练吗？" })).not.toBeInTheDocument();
    await user.click(within(screen.getByRole("complementary")).getByRole("button", { name: "场景广场" }));
    await user.click(screen.getByRole("button", { name: "确认退出" }));
    expect(await screen.findByRole("heading", { name: "场景广场" })).toBeInTheDocument();
  });

  it("covers daily recommendation refresh and recommendation-generated preview", async () => {
    configureAuthenticatedDefaults();
    resetLocation("/scenes");
    mockApi.getDailyPicks.mockResolvedValueOnce({ picks: [] }).mockResolvedValueOnce({
      picks: [
        { id: "pick-a", position: 4, title: "预约理发", category: "services", duration: "6 分钟", goal: "说明需求", sceneInput: "预约理发并说明需求" },
        { id: "pick-b", position: 5, title: "医院挂号", category: "health", duration: "8 分钟", goal: "描述症状", sceneInput: "去医院挂号" },
        { id: "pick-c", position: 6, title: "课堂讨论", category: "education", duration: "8 分钟", goal: "表达观点", sceneInput: "参加英语课堂讨论" },
      ],
    });
    mockApi.generateCustomScene.mockResolvedValue({
      sceneId: "recommendation-scene",
      title: "预约理发",
      label: "服务",
      background: "Book a haircut.",
      aiRole: "a stylist",
      userRole: "a customer",
      learningGoal: "Explain your needs.",
      estimatedMinutes: 6,
      wordList: [],
      phraseList: [],
      sentenceList: [],
    });
    const user = userEvent.setup();
    render(<App />);
    await screen.findByRole("heading", { name: "场景广场" });
    await user.click(screen.getByRole("button", { name: "换一批" }));
    expect(await screen.findByText("预约理发")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "开始练习 预约理发 场景" }));
    await waitFor(() => expect(mockApi.generateCustomScene).toHaveBeenCalledWith("预约理发并说明需求"));
    expect(await screen.findByText("场景已准备好")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "返回修改" }));
    expect(screen.queryByRole("heading", { name: "场景已准备好" })).not.toBeInTheDocument();
  });

  it("covers achievement rendering, filtering, expansion, and empty/error recovery", async () => {
    configureAuthenticatedDefaults();
    resetLocation("/profile");
    mockApi.getAchievementOverview
      .mockResolvedValueOnce({
        series: [
          {
            seriesId: "conversation",
            category: "练习",
            title: "开口练习",
            currentLevel: 1,
            currentValue: 3,
            nextLevel: 2,
            nextThreshold: 5,
            nextTitle: "持续表达",
            unit: "次",
            completed: false,
            milestones: [
              { achievementId: "m1", level: 1, title: "第一次开口", description: "完成第一次练习", threshold: 1, unlocked: true },
              { achievementId: "m2", level: 2, title: "持续表达", description: "完成五次练习", threshold: 5, unlocked: false },
              { achievementId: "m3", level: 3, title: "表达习惯", description: "完成十次练习", threshold: 10, unlocked: false },
            ],
          },
          {
            seriesId: "unknown-series",
            category: "其他",
            title: "隐藏成就",
            currentLevel: 0,
            currentValue: "bad-number",
            unit: "分",
            completed: true,
            milestones: [],
          },
        ],
      })
      .mockRejectedValueOnce(new Error("成就服务不可用"))
      .mockResolvedValueOnce({ series: [] });
    const user = userEvent.setup();
    render(<App />);
    await screen.findByRole("heading", { name: "你的学习空间" });
    expect(await screen.findByText("开口练习")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "查看开口练习全部等级" }));
    expect(screen.getByRole("region", { name: "开口练习全部等级" })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "收起开口练习等级" }));
    await user.click(screen.getByRole("button", { name: /其他/ }));
    expect(screen.getByText("隐藏成就")).toBeInTheDocument();

    await user.click(screen.getAllByRole("button", { name: "个人中心" })[0]);
    await user.click(within(screen.getByRole("navigation", { name: "个人中心导航" })).getByRole("button", { name: "会员权益" }));
    await user.click(within(screen.getByRole("navigation", { name: "个人中心导航" })).getByRole("button", { name: "个人概览" }));
    await waitFor(() => expect(mockApi.getAchievementOverview).toHaveBeenCalledTimes(2));
    expect(await screen.findByRole("alert")).toHaveTextContent("成就服务不可用");
    await user.click(screen.getByRole("button", { name: "重新加载" }));
    expect(await screen.findByText("成就目录暂时为空")).toBeInTheDocument();
  });

  it("covers bootstrap failure, preference failures, and safe public about routes", async () => {
    resetLocation("/about/privacy-policy");
    render(<App />);
    expect(await screen.findByTestId("stub-legal-document")).toBeInTheDocument();
    cleanup();

    configureAuthenticatedDefaults();
    resetLocation("/conversation");
    mockApi.getCurrentUser.mockRejectedValueOnce(new Error("session invalid"));
    mockApi.clearAuthSession.mockImplementationOnce(() => undefined);
    render(<App />);
    expect(await screen.findByRole("heading", { name: "欢迎回来" })).toBeInTheDocument();
    expect(mockApi.clearAuthSession).toHaveBeenCalledWith("session-token");

    cleanup();
    configureAuthenticatedDefaults();
    resetLocation("/level");
    mockApi.updateUserPreference.mockRejectedValueOnce(new Error("水平保存失败"));
    const user = userEvent.setup();
    render(<App />);
    await screen.findByRole("heading", { name: /你现在说英语时/ });
    await user.click(screen.getByRole("button", { name: /可以简单交流/ }));
    await user.click(screen.getByRole("button", { name: "下一步" }));
    expect(window.alert).toHaveBeenCalledWith("水平保存失败");
  });

  it("renders the public about home and keeps its home link available", async () => {
    resetLocation("/about");
    render(<App />);

    expect(await screen.findByTestId("stub-about-product")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /返回首页/ })).toHaveAttribute("href", "/");
    expect(window.location.pathname).toBe("/about");
  });

  it("falls back to an inline error when learning assets cannot be listed or opened", async () => {
    configureAuthenticatedDefaults();
    resetLocation("/assets");
    mockApi.getLearningAssets.mockRejectedValueOnce(new Error("资产列表暂时不可用"));
    render(<App />);

    expect(await screen.findByRole("heading", { name: "学习资产" })).toBeInTheDocument();
    expect(await screen.findByRole("alert")).toHaveTextContent("资产列表暂时不可用");
    expect(screen.getByText("暂无场景学习资产")).toBeInTheDocument();

    cleanup();
    resetLocation("/scenes/asset-fallback/assets");
    mockApi.getLearningAssets.mockResolvedValueOnce([
      { sceneId: "asset-fallback", title: "资产详情回退", label: "其他", latestSessionId: "session-1" },
    ]);
    mockApi.getLearningAsset.mockRejectedValue(new Error("资产详情暂时不可用"));
    render(<App />);

    expect(await screen.findByRole("heading", { name: "学习资产" })).toBeInTheDocument();
    expect(await screen.findByRole("alert")).toHaveTextContent("资产详情暂时不可用");
    expect(screen.getByText("正在读取最近一次对话与评分。")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "打开当前学习资产" })).not.toBeInTheDocument();
  });

  it("redirects an unauthenticated protected route and preserves its return path", async () => {
    resetLocation("/profile/security");
    render(<App />);

    expect(await screen.findByRole("heading", { name: "欢迎回来" })).toBeInTheDocument();
    expect(window.location.pathname).toBe("/login");
    expect(window.sessionStorage.getItem("unispeaking.authReturnPath")).toBe("/profile/security");
    expect(mockApi.getCurrentUser).not.toHaveBeenCalled();
  });

  it("cleans the auth session when logout fails and still returns to the splash page", async () => {
    configureAuthenticatedDefaults();
    resetLocation("/profile/security");
    mockAuthApi.logoutUser.mockRejectedValueOnce(new Error("logout endpoint unavailable"));
    const user = userEvent.setup();
    render(<App />);

    await screen.findByTestId("stub-account-security");
    await user.click(screen.getByRole("button", { name: "安全页退出登录" }));
    const logoutDialog = screen.getByRole("dialog");
    await user.click(within(logoutDialog).getByRole("button", { name: "退出登录" }));

    await waitFor(() => expect(mockApi.clearAuthSession).toHaveBeenCalledWith());
    expect(await screen.findByTestId("landing-page")).toBeInTheDocument();
    expect(window.location.pathname).toBe("/");
  });

  it("completes the membership checkout and clears the session after a successful password change", async () => {
    configureAuthenticatedDefaults();
    resetLocation("/membership");
    mockApi.changePassword.mockResolvedValueOnce(undefined);
    const user = userEvent.setup();
    render(<App />);

    expect(await screen.findByRole("heading", { name: "会员与订阅中心" })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "升级专业版" }));
    expect(screen.getByRole("heading", { name: "确认升级至专业版" })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "模拟支付并完成" }));
    expect(screen.queryByRole("heading", { name: "确认升级至专业版" })).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "账号与安全" }));
    await user.click(screen.getByRole("button", { name: "修改密码" }));
    await user.type(screen.getByLabelText("当前密码"), "current-password");
    await user.type(screen.getByLabelText("新密码"), "new-password");
    await user.type(screen.getByLabelText("确认新密码"), "new-password");
    await user.click(screen.getByRole("button", { name: "确认修改" }));

    await waitFor(() => expect(mockApi.changePassword).toHaveBeenCalledWith({
      currentPassword: "current-password",
      newPassword: "new-password",
    }));
    expect(mockApi.clearAuthSession).toHaveBeenCalledWith();
    expect(await screen.findByRole("heading", { name: "欢迎回来" })).toBeInTheDocument();
    expect(window.location.pathname).toBe("/login");
  });

  it("guards navigation away from IELTS session and releases the guard after confirmation", async () => {
    configureAuthenticatedDefaults();
    resetLocation("/ielts/part1/travel/session");
    const user = userEvent.setup();
    render(<App />);

    expect(await screen.findByTestId("stub-ielts-training")).toBeInTheDocument();
    await user.click(within(screen.getByRole("complementary")).getByRole("button", { name: "学习资产" }));
    expect(screen.getByRole("heading", { name: "确定要退出当前训练吗？" })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "确认退出" }));

    expect(await screen.findByTestId("stub-ielts-assets")).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "确定要退出当前训练吗？" })).not.toBeInTheDocument();
  });

  it("covers landing entry choices and preserves a specialty return path", async () => {
    const user = userEvent.setup();
    render(<App />);
    await screen.findByTestId("landing-page");

    await user.click(screen.getByRole("button", { name: "未知专项" }));
    expect(window.location.pathname).toBe("/");
    await user.click(screen.getByRole("button", { name: "打开应用" }));
    expect(await screen.findByRole("heading", { name: "欢迎回来" })).toBeInTheDocument();
    expect(window.location.pathname).toBe("/login");

    cleanup();
    resetLocation("/");
    render(<App />);
    await screen.findByTestId("landing-page");
    await user.click(screen.getByRole("button", { name: "雅思专项" }));
    expect(await screen.findByRole("heading", { name: "创建账号" })).toBeInTheDocument();
    expect(window.sessionStorage.getItem("unispeaking.authReturnPath")).toBe("/ielts");
  });

  it("handles profile editing validation, avatar upload, and calendar navigation", async () => {
    configureAuthenticatedDefaults();
    resetLocation("/profile");
    mockApi.getProfileOverview.mockResolvedValue({
      account: { userId: "user-1", nickname: "测试用户", displayName: "测试用户", email: "user@example.com" },
      calendar: { month: "2026-08", checkedDates: ["2026-08-01"], checkedInToday: false },
      statistics: {
        weeklyPracticeSeconds: 125,
        trainingRecordCount: 2,
        consecutiveLearningDays: 2,
        lastSevenDays: [{ date: "2026-08-01", practiceSeconds: 0 }, { date: "2026-08-02", practiceSeconds: 30 }],
      },
    });
    mockApi.updateProfile.mockResolvedValue({ nickname: "新昵称", displayName: "新昵称" });
    mockApi.uploadProfileAvatar.mockResolvedValue({ avatarUrl: "https://cdn.example/avatar.png", avatarUrlExpiresAt: "2026-09-01" });
    const user = userEvent.setup();
    render(<App />);
    await screen.findByRole("heading", { name: "你的学习空间" });
    await user.click(screen.getByRole("button", { name: "编辑用户名和头像" }));
    const dialog = screen.getByRole("dialog");
    const nickname = within(dialog).getByRole("textbox");
    await user.clear(nickname);
    fireEvent.submit(dialog.querySelector("form"));
    expect(within(dialog).getByRole("alert")).toHaveTextContent("用户名不能为空");

    await user.type(nickname, "新昵称");
    const invalidFile = new File(["not-image"], "avatar.gif", { type: "image/gif" });
    fireEvent.change(within(dialog).getByLabelText("选择新头像"), { target: { files: [invalidFile] } });
    expect(within(dialog).getByRole("alert")).toHaveTextContent("JPEG 或 PNG");
    const validFile = new File(["image"], "avatar.png", { type: "image/png" });
    fireEvent.change(within(dialog).getByLabelText("选择新头像"), { target: { files: [validFile] } });
    await user.click(within(dialog).getByRole("button", { name: "保存修改" }));
    await waitFor(() => expect(mockApi.updateProfile).toHaveBeenCalledWith({ nickname: "新昵称" }));
    await waitFor(() => expect(mockApi.uploadProfileAvatar).toHaveBeenCalledWith(validFile));
    expect(await screen.findByText("新昵称")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "查看上一个月" }));
    await waitFor(() => expect(mockApi.getProfileOverview).toHaveBeenCalledWith("2026-07"));
  });

  it("routes specialty training from the scene plaza", async () => {
    configureAuthenticatedDefaults();
    resetLocation("/scenes");
    const user = userEvent.setup();
    render(<App />);
    await screen.findByRole("heading", { name: "场景广场" });
    await user.click(screen.getByRole("button", { name: "场景广场" }));
    await screen.findByRole("heading", { name: "场景广场" });
    await user.click(screen.getByRole("button", { name: /雅思口语/ }));
    expect(await screen.findByTestId("stub-ielts-training")).toBeInTheDocument();
  });
});
