import React from 'react';
import { cn } from '../../utils';

export interface InputProps extends React.InputHTMLAttributes<HTMLInputElement> {
  label?: string;
  error?: string;
  hint?: string;
}

export const Input = React.forwardRef<HTMLInputElement, InputProps>(
  ({ className, type = 'text', label, error, hint, ...props }, ref) => {
    return (
      <div className="w-full space-y-1.5 text-left">
        {label && (
          <label className="block text-xs font-sans font-medium text-ink-muted dark:text-night-muted tracking-wide uppercase">
            {label}
          </label>
        )}
        <input
          type={type}
          ref={ref}
          className={cn(
            'w-full px-3.5 py-2 text-sm font-sans bg-paper-50 dark:bg-night-surface border border-paper-300 dark:border-night-border rounded-[2px] text-ink-primary dark:text-night-ink placeholder:text-ink-subtle/50 dark:placeholder:text-night-subtle/50 focus:outline-none focus:border-terracotta dark:focus:border-terracotta-dark focus:ring-1 focus:ring-terracotta/20 transition-all',
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
Input.displayName = 'Input';
