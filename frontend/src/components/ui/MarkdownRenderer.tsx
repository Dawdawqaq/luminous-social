import React from "react";
import { cn } from "../../utils";
import { CatEmoteText } from "../emotes/CatEmoteText";

interface MarkdownRendererProps {
  content: string;
  className?: string;
}

export const MarkdownRenderer: React.FC<MarkdownRendererProps> = ({
  content,
  className,
}) => {
  const parseMarkdown = (text: string) => {
    if (!text) return null;
    const lines = text.split("\n");
    const elements: React.ReactNode[] = [];

    lines.forEach((line, idx) => {
      const trimmed = line.trim();

      if (trimmed.startsWith("# ")) {
        elements.push(
          <h1
            key={idx}
            className="font-sans text-2xl md:text-3xl font-extrabold text-ink-primary dark:text-night-ink mt-6 mb-3 tracking-tight"
          >
            <CatEmoteText text={trimmed.slice(2)} />
          </h1>,
        );
        return;
      }

      if (trimmed.startsWith("## ")) {
        elements.push(
          <h2
            key={idx}
            className="font-sans text-xl md:text-2xl font-extrabold text-ink-primary dark:text-night-ink mt-5 mb-2.5 tracking-tight"
          >
            <CatEmoteText text={trimmed.slice(3)} />
          </h2>,
        );
        return;
      }

      if (trimmed.startsWith("### ")) {
        elements.push(
          <h3
            key={idx}
            className="font-sans text-lg md:text-xl font-extrabold text-ink-primary dark:text-night-ink mt-4 mb-2 tracking-tight"
          >
            <CatEmoteText text={trimmed.slice(4)} />
          </h3>,
        );
        return;
      }

      if (trimmed.startsWith("> ")) {
        elements.push(
          <blockquote
            key={idx}
            className="border-l-2 border-terracotta dark:border-terracotta-dark pl-4 my-4 font-sans text-base md:text-lg text-ink-muted dark:text-night-muted leading-relaxed"
          >
            <CatEmoteText text={trimmed.slice(2)} />
          </blockquote>,
        );
        return;
      }

      if (trimmed === "---" || trimmed === "***") {
        elements.push(<hr key={idx} className="my-6 hairline-t border-0" />);
        return;
      }

      if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
        elements.push(
          <li
            key={idx}
            className="ml-5 list-disc font-sans text-sm md:text-base text-ink-primary dark:text-night-ink leading-relaxed my-1"
          >
            <CatEmoteText text={trimmed.slice(2)} />
          </li>,
        );
        return;
      }

      if (trimmed.startsWith("```")) {
        return;
      }

      if (!trimmed) {
        elements.push(<div key={idx} className="h-3" />);
        return;
      }

      elements.push(
        <p
          key={idx}
          className="font-sans text-base md:text-lg text-ink-primary dark:text-night-ink leading-[1.8] my-2 text-justify"
        >
          <CatEmoteText text={trimmed} />
        </p>,
      );
    });

    return elements;
  };

  return <div className={cn("prose", className)}>{parseMarkdown(content)}</div>;
};
