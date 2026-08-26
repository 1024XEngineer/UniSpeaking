import { cleanup, render } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { HumanVerification } from "../HumanVerification.jsx";

afterEach(() => {
  cleanup();
  delete window.initAliyunCaptcha;
  delete window.AliyunCaptchaConfig;
  document.head.querySelectorAll("script").forEach((script) => script.remove());
  vi.unstubAllEnvs();
});

describe("HumanVerification", () => {
  it("does nothing in development mode", () => {
    render(<HumanVerification buttonId="signup" onVerify={vi.fn()} />);
    expect(window.initAliyunCaptcha).toBeUndefined();
    expect(document.head.querySelector("script")).toBeNull();
  });

  it("initializes an existing captcha SDK and forwards callback results", async () => {
    vi.stubEnv("DEV", false);
    vi.stubEnv("VITE_AUTH_CAPTCHA_PROVIDER", "aliyun");
    vi.stubEnv("VITE_ALIYUN_CAPTCHA_SCENE_ID", "scene-test");
    const onVerify = vi.fn(async (params) => ({ captchaResult: params.token === "ok", bizResult: true }));
    const instance = { destroy: vi.fn() };
    let config;
    window.initAliyunCaptcha = vi.fn((next) => {
      config = next;
      next.getInstance(instance);
    });
    const { unmount } = render(<HumanVerification buttonId="signup" onVerify={onVerify} />);
    expect(window.AliyunCaptchaConfig).toMatchObject({ region: "cn" });
    expect(window.initAliyunCaptcha).toHaveBeenCalledWith(expect.objectContaining({ SceneId: "scene-test", button: "#signup" }));
    await expect(config.captchaVerifyCallback({ token: "ok" })).resolves.toEqual({ captchaResult: true, bizResult: true });
    await expect(config.captchaVerifyCallback(null)).resolves.toEqual({ captchaResult: false, bizResult: false });
    unmount();
    expect(instance.destroy).toHaveBeenCalled();
  });

  it("loads the captcha script once and initializes on load", () => {
    vi.stubEnv("DEV", false);
    vi.stubEnv("VITE_AUTH_CAPTCHA_PROVIDER", "aliyun");
    const init = vi.fn();
    window.initAliyunCaptcha = undefined;
    const { unmount } = render(<HumanVerification buttonId="login" onVerify={vi.fn()} />);
    const script = document.head.querySelector("script");
    expect(script.async).toBe(true);
    expect(script.defer).toBe(true);
    Object.defineProperty(window, "initAliyunCaptcha", { configurable: true, value: init });
    script.dispatchEvent(new Event("load"));
    expect(init).toHaveBeenCalled();
    unmount();
  });
});
