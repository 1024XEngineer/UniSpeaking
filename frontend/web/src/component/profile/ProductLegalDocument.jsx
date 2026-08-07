import { ArrowLeft, WarningCircle } from "@phosphor-icons/react";
import { paths } from "../../controller/router.js";
import { PRODUCT_INFORMATION, productDocuments } from "./productDocuments.js";

function followInternalLink(event, path, onNavigate) {
  if (!onNavigate || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
  event.preventDefault();
  onNavigate(path);
}

export function ProductLegalDocument({ documentId, onNavigate }) {
  const document = productDocuments[documentId] || productDocuments["user-agreement"];
  return (
    <main className="product-legal-page">
      <a
        className="product-legal-page__back"
        href={paths.about.root}
        onClick={(event) => followInternalLink(event, paths.about.root, onNavigate)}
      >
        <ArrowLeft weight="bold" />返回关于产品
      </a>

      <div className="product-placeholder-notice" role="note">
        <WarningCircle weight="fill" aria-hidden="true" />
        <p><strong>开发草案</strong>主体、备案号、邮箱和条款尚未完成法律审核，不能用于正式运营。</p>
      </div>

      <header>
        <p>{document.eyebrow}</p>
        <h1>{document.title}</h1>
        <span>版本 {PRODUCT_INFORMATION.version} · 最后更新 {PRODUCT_INFORMATION.lastUpdated}</span>
        <div>{document.summary}</div>
      </header>

      <article className="product-legal-content">
        {document.sections.map((section) => (
          <section key={section.title}>
            <h2>{section.title}</h2>
            {section.paragraphs?.map((paragraph) => <p key={paragraph}>{paragraph}</p>)}
            {section.items && (
              <ul>
                {section.items.map((item) => <li key={item}>{item}</li>)}
              </ul>
            )}
          </section>
        ))}
      </article>
    </main>
  );
}
