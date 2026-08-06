import {
  ArrowRight,
  ArrowSquareOut,
  ChatCircleDots,
  FileText,
  Lifebuoy,
  Robot,
  ShieldCheck,
  WarningCircle,
} from "@phosphor-icons/react";
import { paths } from "../router.js";
import { ExternalFeedbackLink } from "../help/ExternalFeedbackLink.jsx";
import { PRODUCT_INFORMATION } from "./productDocuments.js";

const legalLinks = [
  {
    title: "用户协议",
    description: "账户、服务规则、AI 内容与用户责任",
    href: paths.about.userAgreement,
    icon: FileText,
  },
  {
    title: "隐私政策",
    description: "信息处理、权限、第三方服务与用户权利",
    href: paths.about.privacyPolicy,
    icon: ShieldCheck,
  },
  {
    title: "AI 服务说明",
    description: "生成内容、语言评分与训练推荐的适用边界",
    href: paths.about.aiService,
    icon: Robot,
  },
];

function followInternalLink(event, path, onNavigate) {
  if (!onNavigate || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
  event.preventDefault();
  onNavigate(path);
}

export function AboutProduct({ onNavigate, onHelpNavigate }) {
  return (
    <main className="about-product-page">
      <header className="about-product-hero">
        <div className="about-product-brand" aria-label="UniSpeaking">
          <img src="/brand/unispeaking-mark-user.jpg" alt="" />
          <img src="/brand/unispeaking-wordmark.png" alt="UniSpeaking" />
        </div>
        <p>ABOUT UNISPEAKING</p>
        <h1>关于 UniSpeaking</h1>
        <span>专注真实表达的 AI 英语口语训练工具</span>
      </header>

      <div className="product-placeholder-notice" role="note">
        <WarningCircle weight="fill" aria-hidden="true" />
        <p><strong>开发占位信息</strong>以下主体、备案、邮箱和协议草案必须在正式发布前替换并完成法律审核。</p>
      </div>

      <section className="about-product-section" aria-labelledby="product-information-title">
        <header><p>PRODUCT INFORMATION</p><h2 id="product-information-title">产品信息</h2></header>
        <dl className="about-product-facts">
          <div><dt>当前版本</dt><dd>{PRODUCT_INFORMATION.version}</dd></div>
          <div><dt>产品形态</dt><dd>Web 应用</dd></div>
          <div><dt>运营主体</dt><dd>{PRODUCT_INFORMATION.operator}</dd></div>
          <div><dt>网站备案</dt><dd>{PRODUCT_INFORMATION.filingNumber}</dd></div>
          <div><dt>客服邮箱</dt><dd>{PRODUCT_INFORMATION.supportEmail}</dd></div>
          <div><dt>更新方式</dt><dd>Web 自动更新</dd></div>
        </dl>
      </section>

      <section className="about-product-section about-product-ai" aria-labelledby="product-ai-title">
        <header><p>RESPONSIBLE AI</p><h2 id="product-ai-title">AI 使用说明</h2></header>
        <div>
          <Robot weight="duotone" aria-hidden="true" />
          <p>对话回复、评分、纠错和训练推荐可能由人工智能生成，存在不准确或不完整的可能。相关结果仅用于语言学习参考，不应作为医疗、法律、就业或其他高风险决策依据。</p>
        </div>
      </section>

      <section className="about-product-section" aria-labelledby="product-legal-title">
        <header><p>TERMS & POLICIES</p><h2 id="product-legal-title">协议与说明</h2></header>
        <nav className="about-product-links" aria-label="产品协议与说明">
          {legalLinks.map(({ title, description, href, icon: Icon }) => (
            <a key={href} href={href} onClick={(event) => followInternalLink(event, href, onNavigate)}>
              <Icon weight="duotone" aria-hidden="true" />
              <span><strong>{title}</strong><small>{description}</small></span>
              <ArrowRight weight="bold" aria-hidden="true" />
            </a>
          ))}
          <a
            href="https://github.com/1024XEngineer/UniSpeaking/blob/main/frontend/LICENSE"
            target="_blank"
            rel="noreferrer"
          >
            <FileText weight="duotone" aria-hidden="true" />
            <span><strong>开源许可</strong><small>Web 客户端采用 Apache License 2.0</small></span>
            <ArrowSquareOut weight="bold" aria-hidden="true" />
          </a>
        </nav>
      </section>

      <section className="about-product-section about-product-support" aria-labelledby="product-support-title">
        <header><p>SUPPORT</p><h2 id="product-support-title">支持与反馈</h2></header>
        <div>
          <a href={paths.help.root} onClick={(event) => followInternalLink(event, paths.help.root, onHelpNavigate)}>
            <Lifebuoy weight="duotone" aria-hidden="true" /><span><strong>帮助中心</strong><small>查找常见问题与使用说明</small></span><ArrowRight weight="bold" />
          </a>
          <ExternalFeedbackLink>
            {(configured) => <><ChatCircleDots weight="duotone" aria-hidden="true" /><span><strong>{configured ? "问题反馈" : "反馈入口待配置"}</strong><small>通过独立问卷提交问题、建议或内容反馈</small></span><ArrowRight weight="bold" /></>}
          </ExternalFeedbackLink>
        </div>
      </section>
    </main>
  );
}
