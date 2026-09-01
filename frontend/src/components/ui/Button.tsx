import React from 'react';
import { cn } from '../../utils';

export interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary' | 'outline' | 'ghost' | 'terracotta' | 'subtle';
  size?: 'sm' | 'md' | 'lg';
  isLoading?: boolean;
}

export const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className, variant = 'primary', size = 'md', isLoading, disabled, children, ...props }, ref) => {
    const baseStyles =
      'inline-flex items-center justify-center font-sans font-medium transition-all duration-150 rounded-[2px] disabled:opacity-50 disabled:cursor-not-allowed cursor-pointer focus:outline-none focus-visible:ring-1 focus-visible:ring-ink-primary dark:focus-visible:ring-night-ink';

    const variants = {
      primary:
        'bg-ink-primary hover:bg-ink-dark text-paper-50 dark:bg-night-ink dark:hover:bg-white dark:text-night-canvas shadow-tactile active:scale-[0.99]',
      secondary:
        'bg-paper-200 hover:bg-paper-300 text-ink-primary dark:bg-night-surface hover:dark:bg-night-surfaceHover dark:text-night-ink border border-paper-300 dark:border-night-border',
      outline:
        'bg-transparent hover:bg-paper-200/60 dark:hover:bg-night-surface text-ink-primary dark:text-night-ink border border-paper-300 dark:border-night-border',
      ghost:
        'bg-transparent hover:bg-paper-200/50 dark:hover:bg-night-surface/60 text-ink-muted hover:text-ink-primary dark:text-night-muted dark:hover:text-night-ink',
      terracotta:
        'bg-terracotta hover:bg-terracotta-hover text-white dark:bg-terracotta-dark dark:hover:bg-terracotta shadow-tactile active:scale-[0.99]',
      subtle:
        'bg-paper-100 hover:bg-paper-200 text-ink-muted hover:text-ink-primary dark:bg-night-canvas dark:hover:bg-night-surface dark:text-night-muted dark:hover:text-night-ink',
    };

    const sizes = {
      sm: 'text-xs px-2.5 py-1.5 gap-1.5',
      md: 'text-xs md:text-sm px-4 py-2 gap-2',
      lg: 'text-sm md:text-base px-5 py-2.5 gap-2.5',
    };

    return (
      <button
        ref={ref}
        disabled={disabled || isLoading}
        className={cn(baseStyles, variants[variant], sizes[size], className)}
        {...props}
      >
        {isLoading ? (
          <span className="inline-block w-4 h-4 border-2 border-current border-t-transparent rounded-full animate-spin mr-1" />
        ) : null}
        {children}
      </button>
    );
  }
);
Button.displayName = 'Button';
