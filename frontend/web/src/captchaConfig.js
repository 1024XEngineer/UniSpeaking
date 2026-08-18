const DEFAULT_ALIYUN_CAPTCHA_SCRIPT = "https://o.alicdn.com/captcha-frontend/aliyunCaptcha/AliyunCaptcha.js";
const ALIYUN_CAPTCHA_MODE = "popup";

function firstConfigured(...values) {
  return values.find((value) => String(value || "").trim()) || "";
}

export function resolvePublicCaptchaEnv(frontendEnv = {}, backendEnv = {}) {
  return {
    VITE_AUTH_CAPTCHA_PROVIDER: firstConfigured(
      frontendEnv.VITE_AUTH_CAPTCHA_PROVIDER,
      backendEnv.VITE_AUTH_CAPTCHA_PROVIDER,
      backendEnv.AUTH_CAPTCHA_PROVIDER,
      "development",
    ),
    VITE_ALIYUN_CAPTCHA_SCENE_ID: firstConfigured(
      frontendEnv.VITE_ALIYUN_CAPTCHA_SCENE_ID,
      backendEnv.VITE_ALIYUN_CAPTCHA_SCENE_ID,
      backendEnv.ALIYUN_CAPTCHA_SCENE_ID,
    ),
    VITE_ALIYUN_CAPTCHA_PREFIX: firstConfigured(
      frontendEnv.VITE_ALIYUN_CAPTCHA_PREFIX,
      backendEnv.VITE_ALIYUN_CAPTCHA_PREFIX,
      backendEnv.ALIYUN_CAPTCHA_PREFIX,
    ),
    VITE_ALIYUN_CAPTCHA_REGION: firstConfigured(
      frontendEnv.VITE_ALIYUN_CAPTCHA_REGION,
      backendEnv.VITE_ALIYUN_CAPTCHA_REGION,
      backendEnv.ALIYUN_CAPTCHA_REGION,
    ),
    VITE_ALIYUN_CAPTCHA_SCRIPT_URL: firstConfigured(
      frontendEnv.VITE_ALIYUN_CAPTCHA_SCRIPT_URL,
      backendEnv.VITE_ALIYUN_CAPTCHA_SCRIPT_URL,
      backendEnv.ALIYUN_CAPTCHA_SCRIPT_URL,
    ),
    VITE_ALIYUN_CAPTCHA_CDN_BASE: firstConfigured(
      frontendEnv.VITE_ALIYUN_CAPTCHA_CDN_BASE,
      backendEnv.VITE_ALIYUN_CAPTCHA_CDN_BASE,
      backendEnv.ALIYUN_CAPTCHA_CDN_BASE,
    ),
  };
}

export function normalizeAliyunCaptchaRegion(region) {
  return region === "cn" || region === "cn-shanghai" ? "cn" : "cn";
}

export function getAliyunCaptchaCdnServers(cdnBase) {
  const value = String(cdnBase || "").trim();
  return value ? [value.replace(/\/$/, "")] : undefined;
}

export function getAliyunCaptchaScriptUrl(scriptUrl) {
  return String(scriptUrl || "").trim() || DEFAULT_ALIYUN_CAPTCHA_SCRIPT;
}

export function getAliyunCaptchaMode() {
  return ALIYUN_CAPTCHA_MODE;
}
