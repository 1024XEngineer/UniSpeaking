const DEFAULT_ALIYUN_CAPTCHA_SCRIPT = "https://o.alicdn.com/captcha-frontend/aliyunCaptcha/AliyunCaptcha.js";
const ALIYUN_CAPTCHA_MODE = "popup";

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
