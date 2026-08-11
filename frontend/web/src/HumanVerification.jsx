import { useEffect, useRef } from "react";
import {
  getAliyunCaptchaCdnServers,
  getAliyunCaptchaMode,
  getAliyunCaptchaScriptUrl,
  normalizeAliyunCaptchaRegion,
} from "./captchaConfig.js";

const ALIYUN_CAPTCHA_SCRIPT = getAliyunCaptchaScriptUrl(import.meta.env.VITE_ALIYUN_CAPTCHA_SCRIPT_URL);

export function HumanVerification({ buttonId, onVerify }) {
  const instanceRef = useRef(null);
  const onVerifyRef = useRef(onVerify);
  const sceneId = import.meta.env.VITE_ALIYUN_CAPTCHA_SCENE_ID || "i12nr63f";
  const prefix = import.meta.env.VITE_ALIYUN_CAPTCHA_PREFIX || "1nsp37";
  const region = normalizeAliyunCaptchaRegion(import.meta.env.VITE_ALIYUN_CAPTCHA_REGION || "cn");
  const cdnServers = getAliyunCaptchaCdnServers(import.meta.env.VITE_ALIYUN_CAPTCHA_CDN_BASE);
  const mode = getAliyunCaptchaMode();

  useEffect(() => { onVerifyRef.current = onVerify; }, [onVerify]);

  useEffect(() => {
    let cancelled = false;
    const initialize = () => {
      if (cancelled || !window.initAliyunCaptcha) return;
      window.AliyunCaptchaConfig = { region, prefix };
      window.initAliyunCaptcha({
        SceneId: sceneId,
        prefix,
        mode,
        button: `#${buttonId}`,
        region,
        ...(cdnServers ? { cdnServers } : {}),
        language: "cn",
        captchaVerifyCallback: async (captchaVerifyParam) => {
          if (!captchaVerifyParam) return { captchaResult: false, bizResult: false };
          return onVerifyRef.current(captchaVerifyParam);
        },
        onBizResultCallback: () => undefined,
        getInstance: (instance) => { instanceRef.current = instance; },
      });
    };
    if (window.initAliyunCaptcha) initialize();
    else {
      const script = document.querySelector(`script[src="${ALIYUN_CAPTCHA_SCRIPT}"]`) || document.createElement("script");
      script.src = ALIYUN_CAPTCHA_SCRIPT;
      script.async = true;
      script.defer = true;
      script.addEventListener("load", initialize, { once: true });
      if (!script.isConnected) document.head.appendChild(script);
    }
    return () => {
      cancelled = true;
      instanceRef.current?.destroy?.();
      instanceRef.current = null;
    };
  }, [buttonId, mode, prefix, region, sceneId]);

  return null;
}
