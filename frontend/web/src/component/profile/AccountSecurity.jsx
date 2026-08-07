import {
  Desktop,
  EnvelopeSimple,
  Key,
  ShieldCheck,
  SignOut,
} from "@phosphor-icons/react";

function currentBrowserName() {
  if (typeof navigator === "undefined") return "当前浏览器";
  const userAgent = navigator.userAgent || "";
  if (/Edg\//.test(userAgent)) return "Microsoft Edge";
  if (/Firefox\//.test(userAgent)) return "Mozilla Firefox";
  if (/Chrome\//.test(userAgent) || /CriOS\//.test(userAgent)) return "Google Chrome";
  if (/Safari\//.test(userAgent)) return "Safari";
  return "当前浏览器";
}

function currentPlatformName() {
  if (typeof navigator === "undefined") return "当前设备";
  const platform = navigator.userAgentData?.platform || navigator.platform || "";
  const userAgent = navigator.userAgent || "";
  if (/Android/i.test(userAgent)) return "Android";
  if (/iPhone|iPad|iPod/i.test(userAgent)) return "iOS";
  if (/Mac/i.test(platform)) return "macOS";
  if (/Win/i.test(platform)) return "Windows";
  if (/Linux/i.test(platform)) return "Linux";
  return "当前设备";
}

export function AccountSecurity({ email, onOpenPassword, onLogout }) {
  const browser = currentBrowserName();
  const platform = currentPlatformName();

  return (
    <main className="account-security-page">
      <header className="account-security-header">
        <div className="account-security-header__icon"><ShieldCheck weight="duotone" aria-hidden="true" /></div>
        <p>ACCOUNT &amp; SECURITY</p>
        <h1>账号与安全</h1>
        <span>管理登录凭据与当前登录状态</span>
      </header>

      <section className="account-security-section" aria-labelledby="account-login-title">
        <header><p>LOGIN DETAILS</p><h2 id="account-login-title">登录信息</h2></header>
        <div className="account-security-list">
          <article>
            <span className="account-security-row__icon"><EnvelopeSimple weight="duotone" aria-hidden="true" /></span>
            <div><strong>登录邮箱</strong><small>{email || "未获取到邮箱"}</small></div>
            <span className="account-security-state">当前账号</span>
          </article>
          <article>
            <span className="account-security-row__icon"><Key weight="duotone" aria-hidden="true" /></span>
            <div><strong>登录密码</strong><small>密码已设置</small></div>
            <button type="button" onClick={onOpenPassword}><Key weight="bold" aria-hidden="true" />修改密码</button>
          </article>
        </div>
      </section>

      <section className="account-security-section" aria-labelledby="current-session-title">
        <header><p>CURRENT SESSION</p><h2 id="current-session-title">当前登录</h2></header>
        <div className="account-security-list">
          <article>
            <span className="account-security-row__icon"><Desktop weight="duotone" aria-hidden="true" /></span>
            <div><strong>{browser}</strong><small>{platform} · 此浏览器</small></div>
            <span className="account-security-state is-active">已登录</span>
          </article>
          <article>
            <span className="account-security-row__icon"><SignOut weight="duotone" aria-hidden="true" /></span>
            <div><strong>退出当前账号</strong><small>清除当前浏览器中的登录信息</small></div>
            <button type="button" className="is-danger" onClick={onLogout}><SignOut weight="bold" aria-hidden="true" />退出登录</button>
          </article>
        </div>
      </section>
    </main>
  );
}
