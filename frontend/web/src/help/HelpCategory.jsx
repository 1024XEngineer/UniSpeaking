import { ArrowLeft, CaretRight, Lifebuoy } from "@phosphor-icons/react";
import { paths } from "../router.js";
import { ExternalFeedbackLink } from "./ExternalFeedbackLink.jsx";
import { handleHelpLinkClick } from "./helpUtils.js";

export function HelpCategory({ category, articles, onNavigate }) {
  return (
    <main className="help-page help-category-page">
      <a
        className="help-back-link"
        href={paths.help.root}
        onClick={(event) => handleHelpLinkClick(event, paths.help.root, onNavigate)}
      >
        <ArrowLeft weight="bold" />返回帮助中心
      </a>

      <header className="help-section-header">
        <p className="eyebrow">HELP CATEGORY</p>
        <h1>{category.title}</h1>
        <p>{category.description}</p>
      </header>

      <section className="help-question-list" aria-labelledby="category-question-title">
        <div className="help-list-heading">
          <h2 id="category-question-title">常见问题</h2>
          <span>{articles.length} 篇</span>
        </div>
        <ul>
          {articles.map((article) => {
            const href = paths.help.article(article.id);
            return (
              <li key={article.id}>
                <a href={href} onClick={(event) => handleHelpLinkClick(event, href, onNavigate)}>
                  <span><strong>{article.title}</strong><small>{article.summary}</small></span>
                  <CaretRight weight="bold" aria-hidden="true" />
                </a>
              </li>
            );
          })}
        </ul>
      </section>

      {category.id === "feedback" && (
        <aside className="help-category-feedback">
          <Lifebuoy weight="duotone" aria-hidden="true" />
          <div><strong>已经完成基础排查？</strong><p>通过独立问卷提交复现步骤和设备环境，反馈数据由问卷平台收集。</p></div>
          <ExternalFeedbackLink>
            {(configured) => configured ? "前往问题反馈" : "反馈入口待配置"}
          </ExternalFeedbackLink>
        </aside>
      )}
    </main>
  );
}
