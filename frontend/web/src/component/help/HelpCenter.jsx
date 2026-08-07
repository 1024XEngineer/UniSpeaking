import { useMemo, useRef, useState } from "react";
import {
  ArrowRight,
  BookOpenText,
  CaretRight,
  ChatCircleDots,
  Crown,
  Lifebuoy,
  MagnifyingGlass,
  Microphone,
  Question,
  RocketLaunch,
  ShieldCheck,
  UserCircle,
  X,
} from "@phosphor-icons/react";
import { paths } from "../../controller/router.js";
import { ExternalFeedbackLink } from "./ExternalFeedbackLink.jsx";
import { HelpArticle } from "./HelpArticle.jsx";
import { HelpCategory } from "./HelpCategory.jsx";
import { helpCategories } from "./helpData.js";
import {
  findHelpArticle,
  findHelpCategory,
  getCategoryArticles,
  getPopularHelpArticles,
  getRelatedHelpArticles,
  handleHelpLinkClick,
  searchHelpArticles,
} from "./helpUtils.js";

const categoryIcons = {
  rocket: RocketLaunch,
  account: UserCircle,
  conversation: ChatCircleDots,
  microphone: Microphone,
  records: BookOpenText,
  membership: Crown,
  security: ShieldCheck,
  feedback: Lifebuoy,
};

function HelpNotFound({ onNavigate }) {
  return (
    <main className="help-page help-not-found">
      <Question weight="duotone" />
      <p className="eyebrow">HELP PAGE NOT FOUND</p>
      <h1>没有找到这项帮助内容</h1>
      <p>链接可能已经更新，返回帮助中心可以继续搜索或浏览分类。</p>
      <a href={paths.help.root} onClick={(event) => handleHelpLinkClick(event, paths.help.root, onNavigate)}>
        返回帮助中心<ArrowRight weight="bold" />
      </a>
    </main>
  );
}

function HelpHome({ onNavigate }) {
  const [query, setQuery] = useState("");
  const results = useMemo(() => searchHelpArticles(query), [query]);
  const popularArticles = useMemo(() => getPopularHelpArticles(), []);
  const resultsRef = useRef(null);
  const normalizedQuery = query.trim();

  const submitSearch = (event) => {
    event.preventDefault();
    if (normalizedQuery) resultsRef.current?.focus();
  };

  return (
    <main className="help-page help-home">
      <header className="help-hero">
        <p className="eyebrow">UNISPEAKING HELP CENTER</p>
        <h1>你好，需要什么帮助？</h1>
        <p>搜索常见问题，或按主题找到与当前功能对应的操作说明。</p>
        <form className="help-search" role="search" onSubmit={submitSearch}>
          <MagnifyingGlass aria-hidden="true" />
          <label className="sr-only" htmlFor="help-search-input">搜索帮助文章</label>
          <input
            id="help-search-input"
            type="search"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === "Escape" && query) {
                event.preventDefault();
                setQuery("");
              }
            }}
            placeholder="搜索问题，例如：麦克风、修改密码、学习资产"
            autoComplete="off"
            aria-controls="help-search-results"
          />
          {query && (
            <button type="button" onClick={() => setQuery("")} aria-label="清空帮助搜索">
              <X weight="bold" />
            </button>
          )}
        </form>
      </header>

      {normalizedQuery ? (
        <section
          id="help-search-results"
          className="help-search-results"
          aria-labelledby="help-search-results-title"
          ref={resultsRef}
          tabIndex={-1}
        >
          <div className="help-list-heading">
            <div><p className="eyebrow">SEARCH RESULTS</p><h2 id="help-search-results-title">搜索结果</h2></div>
            <span aria-live="polite">找到 {results.length} 篇</span>
          </div>
          {results.length > 0 ? (
            <ul>
              {results.map((article) => {
                const category = findHelpCategory(article.categoryId);
                const href = paths.help.article(article.id);
                return (
                  <li key={article.id}>
                    <a href={href} onClick={(event) => handleHelpLinkClick(event, href, onNavigate)}>
                      <span><small>{category?.title}</small><strong>{article.title}</strong><p>{article.summary}</p></span>
                      <CaretRight weight="bold" aria-hidden="true" />
                    </a>
                  </li>
                );
              })}
            </ul>
          ) : (
            <div className="help-empty-state" role="status">
              <Question weight="duotone" />
              <h3>暂时没有找到相关内容</h3>
              <p>试试更短的关键词，例如“麦克风”“密码”或“评分”；也可以浏览下方分类。</p>
              <button type="button" onClick={() => setQuery("")}>浏览全部分类</button>
            </div>
          )}
        </section>
      ) : (
        <>
          <section className="help-popular" aria-labelledby="popular-help-title">
            <div className="help-list-heading"><div><p className="eyebrow">POPULAR QUESTIONS</p><h2 id="popular-help-title">热门问题</h2></div></div>
            <ul>
              {popularArticles.map((article) => {
                const href = paths.help.article(article.id);
                return (
                  <li key={article.id}>
                    <a href={href} onClick={(event) => handleHelpLinkClick(event, href, onNavigate)}>
                      <span>{article.title}</span><CaretRight weight="bold" />
                    </a>
                  </li>
                );
              })}
            </ul>
          </section>

          <section className="help-categories" aria-labelledby="help-category-title">
            <div className="help-list-heading"><div><p className="eyebrow">BROWSE BY TOPIC</p><h2 id="help-category-title">按主题查找</h2></div></div>
            <div className="help-category-grid">
              {helpCategories.map((category) => {
                const Icon = categoryIcons[category.icon] || Question;
                const href = paths.help.category(category.id);
                return (
                  <a key={category.id} href={href} onClick={(event) => handleHelpLinkClick(event, href, onNavigate)}>
                    <span className="help-category-icon"><Icon weight="duotone" /></span>
                    <span><strong>{category.title}</strong><small>{category.description}</small><em>{getCategoryArticles(category.id).length} 个问题</em></span>
                    <CaretRight weight="bold" aria-hidden="true" />
                  </a>
                );
              })}
            </div>
          </section>
        </>
      )}

      <aside className="help-support-cta">
        <span><Lifebuoy weight="duotone" /></span>
        <div><p className="eyebrow">STILL NEED HELP?</p><h2>仍然需要帮助？</h2><p>先整理问题现象、复现步骤和设备环境，避免提交账号密码等敏感信息。</p></div>
        <ExternalFeedbackLink>
          {(configured) => <>{configured ? "问题反馈" : "反馈入口待配置"}<ArrowRight weight="bold" /></>}
        </ExternalFeedbackLink>
      </aside>
    </main>
  );
}

export function HelpCenter({ route, onNavigate }) {
  const screen = route?.screen || "home";
  if (screen === "category") {
    const category = findHelpCategory(route.categoryId);
    if (!category) return <HelpNotFound onNavigate={onNavigate} />;
    return <HelpCategory category={category} articles={getCategoryArticles(category.id)} onNavigate={onNavigate} />;
  }
  if (screen === "article") {
    const article = findHelpArticle(route.articleId);
    const category = findHelpCategory(article?.categoryId);
    if (!article || !category) return <HelpNotFound onNavigate={onNavigate} />;
    return (
      <HelpArticle
        article={article}
        category={category}
        relatedArticles={getRelatedHelpArticles(article)}
        onNavigate={onNavigate}
      />
    );
  }
  return <HelpHome onNavigate={onNavigate} />;
}
