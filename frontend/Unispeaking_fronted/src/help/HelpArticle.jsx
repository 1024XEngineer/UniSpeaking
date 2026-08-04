import { useEffect, useState } from "react";
import { ArrowLeft, CaretRight, CheckCircle, ChatCircleDots, XCircle } from "@phosphor-icons/react";
import { paths } from "../router.js";
import { formatHelpDate, handleHelpLinkClick } from "./helpUtils.js";

export function HelpArticle({ article, category, relatedArticles, onNavigate }) {
  const [helpful, setHelpful] = useState(null);

  useEffect(() => setHelpful(null), [article.id]);

  const categoryHref = paths.help.category(category.id);
  return (
    <main className="help-page help-article-page">
      <a
        className="help-back-link"
        href={paths.help.root}
        onClick={(event) => handleHelpLinkClick(event, paths.help.root, onNavigate)}
      >
        <ArrowLeft weight="bold" />返回帮助中心
      </a>

      <article className="help-article">
        <header>
          <a href={categoryHref} onClick={(event) => handleHelpLinkClick(event, categoryHref, onNavigate)}>
            {category.title}
          </a>
          <h1>{article.title}</h1>
          <p>{article.summary}</p>
          <time dateTime={article.updatedAt}>更新时间：{formatHelpDate(article.updatedAt)}</time>
        </header>

        <ol className="help-article-steps">
          {article.steps.map((step, index) => (
            <li key={`${article.id}-${step.title}`}>
              <span aria-hidden="true">{String(index + 1).padStart(2, "0")}</span>
              <div><h2>{step.title}</h2><p>{step.body}</p></div>
            </li>
          ))}
        </ol>

        <section className="help-article-rating" aria-labelledby="helpful-title">
          <h2 id="helpful-title">这篇内容是否解决了你的问题？</h2>
          {helpful === null ? (
            <div>
              <button type="button" onClick={() => setHelpful(true)}><CheckCircle />解决了</button>
              <button type="button" onClick={() => setHelpful(false)}><XCircle />仍有问题</button>
            </div>
          ) : helpful ? (
            <p role="status"><CheckCircle weight="fill" />谢谢你的反馈，很高兴这篇内容对你有帮助。</p>
          ) : (
            <div className="help-article-unresolved" role="status">
              <ChatCircleDots weight="duotone" />
              <span><strong>我们继续帮你排查</strong><small>整理问题现象和复现步骤，前往静态反馈页生成反馈摘要。</small></span>
              <a
                href={paths.help.feedback}
                onClick={(event) => handleHelpLinkClick(event, paths.help.feedback, onNavigate)}
              >
                仍未解决，提交反馈
              </a>
            </div>
          )}
        </section>
      </article>

      {relatedArticles.length > 0 && (
        <section className="help-related" aria-labelledby="related-help-title">
          <h2 id="related-help-title">相关文章</h2>
          <ul>
            {relatedArticles.map((related) => {
              const href = paths.help.article(related.id);
              return (
                <li key={related.id}>
                  <a href={href} onClick={(event) => handleHelpLinkClick(event, href, onNavigate)}>
                    <span>{related.title}</span><CaretRight weight="bold" />
                  </a>
                </li>
              );
            })}
          </ul>
        </section>
      )}
    </main>
  );
}
