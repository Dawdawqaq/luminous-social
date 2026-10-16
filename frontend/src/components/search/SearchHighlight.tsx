import React from "react";

/** 用文本节点高亮，搜索词和返回内容都不作为 HTML 执行。 */
export function SearchHighlight({ text, query }: { text: string; query: string }) {
  const terms = [...new Set((query.normalize("NFKC").replace(/^(@|c\/|id:)/, "").match(/[\p{L}\p{N}]+/gu) || []).filter(value => value.length > 0))].sort((a, b) => b.length - a.length);
  if (!terms.length) return <>{text}</>;
  const pattern = new RegExp(`(${terms.map(value => value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")).join("|")})`, "giu");
  return <>{text.split(pattern).map((part, index) => index % 2 ? <mark key={index} className="bg-yellow-200 text-ink-primary rounded-sm px-0.5">{part}</mark> : part)}</>;
}
