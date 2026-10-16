import React from "react";
import { cn } from "../../utils";

export interface BadgeProps {
  children: React.ReactNode;
  variant?: "default" | "terracotta" | "subtle" | "outline";
  className?: string;
}

export const Badge: React.FC<BadgeProps> = ({
  children,
  variant = "default",
  className,
}) => {
  const variants = {
    default:
      "text-[10px] uppercase tracking-wider font-sans font-medium text-ink-muted dark:text-night-muted bg-paper-200 dark:bg-night-surface border border-paper-300 dark:border-night-border px-2 py-0.5 rounded-md",
    terracotta:
      "text-[10px] uppercase tracking-wider font-sans font-medium text-terracotta dark:text-terracotta-dark bg-terracotta-light/60 dark:bg-terracotta-dark/10 border border-terracotta/20 dark:border-terracotta-dark/20 px-2 py-0.5 rounded-md",
    subtle:
      "text-[10px] uppercase tracking-wider font-sans font-medium text-ink-subtle dark:text-night-subtle px-1.5 py-0.5",
    outline:
      "text-[10px] uppercase tracking-wider font-sans font-medium text-ink-muted dark:text-night-muted border border-paper-300 dark:border-night-border px-2 py-0.5 rounded-md",
  };

  return <span className={cn(variants[variant], className)}>{children}</span>;
};
