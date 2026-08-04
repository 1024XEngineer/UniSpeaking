import { ArrowRight } from "@phosphor-icons/react";
import { paths } from "../router.js";
import { handleHelpLinkClick } from "./helpUtils.js";

export function HelpLayout({ children, onNavigate }) {
  return (
    <div className="public-help-shell">
      <header className="public-help-header">
        <a
          className="public-help-brand"
          href={paths.help.root}
          aria-label="UniSpeaking 帮助中心首页"
          onClick={(event) => handleHelpLinkClick(event, paths.help.root, onNavigate)}
        >
          <img src="/brand/unispeaking-mark-user.jpg" alt="" />
          <img src="/brand/unispeaking-wordmark.png" alt="UniSpeaking" />
          <span>帮助中心</span>
        </a>
        <nav aria-label="帮助中心访客导航">
          <a
            href={paths.auth.login}
            onClick={(event) => handleHelpLinkClick(event, paths.auth.login, onNavigate)}
          >
            登录
          </a>
          <a
            className="public-help-header__primary"
            href={paths.auth.signup}
            onClick={(event) => handleHelpLinkClick(event, paths.auth.signup, onNavigate)}
          >
            开始使用<ArrowRight weight="bold" />
          </a>
        </nav>
      </header>
      <div className="public-help-content">{children}</div>
    </div>
  );
}
