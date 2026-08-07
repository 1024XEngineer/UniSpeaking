import { helpArticles, helpCategories, popularHelpArticleIds } from "./helpData.js";

export function findHelpCategory(categoryId) {
  return helpCategories.find((category) => category.id === categoryId) || null;
}

export function findHelpArticle(articleId) {
  return helpArticles.find((article) => article.id === articleId) || null;
}

export function getCategoryArticles(categoryId) {
  return helpArticles.filter((article) => article.categoryId === categoryId);
}

export function getPopularHelpArticles() {
  const popularity = new Map(popularHelpArticleIds.map((articleId, index) => [articleId, index]));
  return helpArticles
    .filter((article) => popularity.has(article.id))
    .sort((left, right) => popularity.get(left.id) - popularity.get(right.id));
}

export function getRelatedHelpArticles(article, limit = 3) {
  if (!article) return [];
  return helpArticles
    .filter((candidate) => candidate.categoryId === article.categoryId && candidate.id !== article.id)
    .slice(0, limit);
}

function normalizeSearchText(value) {
  return String(value || "")
    .normalize("NFKC")
    .toLocaleLowerCase("zh-CN")
    .replace(/[，。！？、；：,.!?;:()[\]{}'"“”‘’]/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

export function searchHelpArticles(query) {
  const normalizedQuery = normalizeSearchText(query);
  if (!normalizedQuery) return [];
  const terms = normalizedQuery.split(" ").filter(Boolean);

  return helpArticles.filter((article) => {
    const category = findHelpCategory(article.categoryId);
    const searchableText = normalizeSearchText([
      article.title,
      article.summary,
      category?.title,
      ...(article.keywords || []),
      ...article.steps.flatMap((step) => [step.title, step.body]),
    ].join(" "));
    return terms.every((term) => searchableText.includes(term));
  });
}

export function formatHelpDate(value) {
  if (!value) return "";
  const date = new Date(`${value}T00:00:00+08:00`);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat("zh-CN", {
    year: "numeric",
    month: "long",
    day: "numeric",
    timeZone: "Asia/Shanghai",
  }).format(date);
}

export function handleHelpLinkClick(event, href, onNavigate) {
  if (
    event.defaultPrevented
    || event.button !== 0
    || event.metaKey
    || event.ctrlKey
    || event.shiftKey
    || event.altKey
  ) return;
  event.preventDefault();
  onNavigate(href);
}
