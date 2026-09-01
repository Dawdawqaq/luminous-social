import React from 'react';
import { cn } from '../../utils';

export interface TextareaProps extends React.TextareaHTMLAttributes<HTMLTextAreaElement> {
  label?: string;
  error?: string;
  hint?: string;
}

export const Textarea = React.forwardRef<HTMLTextAreaElement, TextareaProps>(
  ({ className, label, error, hint, rows = 4, ...props }, ref) => {
    return (
      <div className="w-full space-y-1.5 text-left">
        {label && (
          <label className="block text-xs font-sans font-medium text-ink-muted dark:text-night-muted tracking-wide uppercase">
            {label}
          </label>
        )}
        <textarea
          ref={ref}
          rows={rows}
          className={cn(
            'w-full px-3.5 py-2.5 text-sm font-sans bg-paper-50 dark:bg-night-surface border border-paper-300 dark:border-night-border rounded-[2px] text-ink-primary dark:text-night-ink placeholder:text-ink-subtle dark:placeholder:text-night-subtle focus:outline-none focus:border-ink-primary dark:focus:border-night-ink focus:ring-1 focus:ring-ink-primary dark:focus:ring-night-ink transition-colors resize-y leading-relaxed',
            error && 'border-terracotta focus:border-terracotta focus:ring-terracotta',
            className
          )}
          {...props}
        />
        {hint && !error && (
          <p className="text-[11px] text-ink-subtle dark:text-night-subtle font-sans">{hint}</p>
        )}
        {error && (
          <p className="text-[11px] text-terracotta font-sans">{error}</p>
        )}
      </div>
    );
  }
);
Textarea.displayName = 'Textarea';
