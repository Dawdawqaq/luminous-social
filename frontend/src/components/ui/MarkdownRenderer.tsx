import React from 'react';
import { cn } from '../../utils';

interface MarkdownRendererProps {
  content: string;
  className?: string;
}

export const MarkdownRenderer: React.FC<MarkdownRendererProps> = ({ content, className }) => {
  // Simple, elegant, safe editorial parser for Luminous text
  const parseMarkdown = (text: string) => {
    if (!text) return null;
    const lines = text.split('\n');
    const elements: React.ReactNode[] = [];

    lines.forEach((line, idx) => {
      const trimmed = line.trim();

      // Heading 1 (# Headline)
      if (trimmed.startsWith('# ')) {
        elements.push(
          <h1 key={idx} className="font-serif text-2xl md:text-3xl font-medium text-ink-primary dark:text-night-ink mt-6 mb-3 tracking-tight">
            {trimmed.slice(2)}
          </h1>
        );
        return;
      }

      // Heading 2 (## Subheading)
      if (trimmed.startsWith('## ')) {
        elements.push(
          <h2 key={idx} className="font-serif text-xl md:text-2xl font-medium text-ink-primary dark:text-night-ink mt-5 mb-2.5 tracking-tight">
            {trimmed.slice(3)}
          </h2>
        );
        return;
      }

      // Heading 3 (### Section)
      if (trimmed.startsWith('### ')) {
        elements.push(
          <h3 key={idx} className="font-serif text-lg md:text-xl font-medium text-ink-primary dark:text-night-ink mt-4 mb-2 tracking-tight">
            {trimmed.slice(4)}
          </h3>
        );
        return;
      }

      // Blockquote (> Quote)
      if (trimmed.startsWith('> ')) {
        elements.push(
          <blockquote
            key={idx}
            className="border-l-2 border-terracotta dark:border-terracotta-dark pl-4 my-4 font-serif italic text-base md:text-lg text-ink-muted dark:text-night-muted leading-relaxed"
          >
            {trimmed.slice(2)}
          </blockquote>
        );
        return;
      }

      // Divider (--- or ***)
      if (trimmed === '---' || trimmed === '***') {
        elements.push(<hr key={idx} className="my-6 hairline-t border-0" />);
        return;
      }

      // Unordered list (- or *)
      if (trimmed.startsWith('- ') || trimmed.startsWith('* ')) {
        elements.push(
          <li key={idx} className="ml-5 list-disc font-sans text-sm md:text-base text-ink-primary dark:text-night-ink leading-relaxed my-1">
            {trimmed.slice(2)}
          </li>
        );
        return;
      }

      // Code snippet (`...`) or block
      if (trimmed.startsWith('```')) {
        return;
      }

      // Empty line
      if (!trimmed) {
        elements.push(<div key={idx} className="h-3" />);
        return;
      }

      // Regular Paragraph with inline formatting
      elements.push(
        <p key={idx} className="font-serif text-base md:text-lg text-ink-primary dark:text-night-ink leading-[1.8] my-2 text-justify">
          {trimmed}
        </p>
      );
    });

    return elements;
  };

  return (
    <div className={cn('prose-editorial', className)}>
      {parseMarkdown(content)}
    </div>
  );
};
