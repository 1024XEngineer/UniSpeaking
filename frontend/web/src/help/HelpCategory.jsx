import { ArrowLeft, CaretRight, Lifebuoy } from "@phosphor-icons/react";
import { paths } from "../router.js";
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
          <div><strong>已经完成基础排查？</strong><p>使用反馈页整理复现步骤和环境信息，当前阶段不会自动上传内容。</p></div>
          <a
            href={paths.help.feedback}
            onClick={(event) => handleHelpLinkClick(event, paths.help.feedback, onNavigate)}
          >
            前往问题反馈
          </a>
        </aside>
      )}
    </main>
  );
}
