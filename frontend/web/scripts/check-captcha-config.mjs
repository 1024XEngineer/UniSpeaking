import test from "node:test";
import assert from "node:assert/strict";
import {
  getAliyunCaptchaCdnServers,
  getAliyunCaptchaMode,
  getAliyunCaptchaScriptUrl,
  normalizeAliyunCaptchaRegion,
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
