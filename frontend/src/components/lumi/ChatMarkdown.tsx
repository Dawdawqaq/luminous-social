import React, { useEffect, useRef, useState } from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { Check, Copy } from "lucide-react";

export function ChatCopyButton({ text, label = "复制回答" }: { text: string; label?: string }) {
  const [result, setResult] = useState(""); const timer = useRef<number>();
  useEffect(() => () => clearTimeout(timer.current), []);
  const copy = async () => {
    try {
      if (navigator.clipboard?.writeText) await navigator.clipboard.writeText(text);
      else {
        const focused = document.activeElement as HTMLElement | null;
        const field = document.createElement("textarea"); field.value = text; field.style.cssText = "position:fixed;left:-9999px;top:0"; document.body.append(field);
        try { field.select(); if (!document.execCommand("copy")) throw new Error(); } finally { field.remove(); focused?.focus(); }
      }
      setResult("已复制");
    } catch { setResult("复制失败，请手动选择"); }
    clearTimeout(timer.current); timer.current = window.setTimeout(() => setResult(""), 2500);
  };
  return <button type="button" className="post-action lumi-chat-copy" onClick={() => void copy()} aria-label={label}>{result === "已复制" ? <Check size={15} aria-hidden="true" /> : <Copy size={15} aria-hidden="true" />}<span aria-live="polite">{result || label}</span></button>;
}

function CodeBlock({ children }: { children?: React.ReactNode }) {
  const code = React.isValidElement<{ children?: React.ReactNode; className?: string }>(children) ? children : undefined;
  const text = String(code?.props.children ?? "").replace(/\n$/, "");
  const language = code?.props.className?.replace(/^language-/, "") || "代码";
  return <div className="lumi-chat-code"><div className="lumi-chat-code-header"><span>{language}</span><ChatCopyButton text={text} label="复制代码" /></div><pre>{children}</pre></div>;
}

/** 模型内容只经 Markdown 转为 React 节点，禁用原始 HTML 和远程图片，链接沿用安全协议过滤。 */
export function ChatMarkdown({ content }: { content: string }) {
  return <div className="lumi-chat-markdown" data-chat-answer><ReactMarkdown skipHtml remarkPlugins={[remarkGfm]} components={{
    pre: CodeBlock,
    img: () => null,
    a: ({ children, href }) => <a href={href} target="_blank" rel="noopener noreferrer nofollow">{children}</a>,
    table: ({ children }) => <div className="lumi-chat-table" tabIndex={0} role="region" aria-label="回答中的表格"><table>{children}</table></div>,
  }}>{content}</ReactMarkdown></div>;
}
