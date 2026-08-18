import test from "node:test";
import assert from "node:assert/strict";
import {
  getAliyunCaptchaCdnServers,
  getAliyunCaptchaMode,
  getAliyunCaptchaScriptUrl,
  normalizeAliyunCaptchaRegion,
  resolvePublicCaptchaEnv,
} from "../src/captchaConfig.js";

test("maps the backend region to the Alibaba frontend region", () => {
  assert.equal(normalizeAliyunCaptchaRegion("cn-shanghai"), "cn");
  assert.equal(normalizeAliyunCaptchaRegion(""), "cn");
});

test("production defaults to the official Alibaba SDK", () => {
  assert.equal(getAliyunCaptchaCdnServers(""), undefined);
  assert.match(getAliyunCaptchaScriptUrl(""), /^https:\/\/o\.alicdn\.com\//);
});

test("registration uses popup verification after the send-code action", () => {
  assert.equal(getAliyunCaptchaMode(), "popup");
});

test("local web builds inherit only public captcha settings from the backend environment", () => {
  const config = resolvePublicCaptchaEnv(
    { VITE_ALIYUN_CAPTCHA_REGION: "cn" },
    {
      AUTH_CAPTCHA_PROVIDER: "aliyun",
      ALIYUN_CAPTCHA_SCENE_ID: "scene-from-backend",
      ALIYUN_CAPTCHA_REGION: "cn-shanghai",
      ALIYUN_CAPTCHA_ACCESS_KEY_ID: "must-not-be-public",
      ALIYUN_CAPTCHA_ACCESS_KEY_SECRET: "must-not-be-public",
    },
  );

  assert.equal(config.VITE_AUTH_CAPTCHA_PROVIDER, "aliyun");
  assert.equal(config.VITE_ALIYUN_CAPTCHA_SCENE_ID, "scene-from-backend");
  assert.equal(config.VITE_ALIYUN_CAPTCHA_REGION, "cn");
  assert.deepEqual(
    Object.keys(config).filter((key) => key.includes("ACCESS_KEY")),
    [],
  );
});
