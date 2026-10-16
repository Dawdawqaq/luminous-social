import React from "react";
import { Loader2 } from "lucide-react";
import { cn } from "../../utils";
export interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?:
    "primary" | "secondary" | "outline" | "ghost" | "terracotta" | "subtle";
  size?: "sm" | "md" | "lg";
  isLoading?: boolean;
}
export const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(
  (
    {
      className,
      variant = "primary",
      size = "md",
      isLoading,
      disabled,
      children,
      type = "button",
      ...props
    },
    ref,
  ) => (
    <button
      ref={ref}
      type={type}
      disabled={disabled || isLoading}
      aria-busy={isLoading || undefined}
      className={cn(
        variant === "ghost" || variant === "subtle"
          ? "inline-flex items-center justify-center gap-2 rounded-md px-3 py-2 font-semibold hover:bg-paper-100 dark:hover:bg-night-surfaceHover disabled:opacity-50"
          : "ink-button",
        (variant === "primary" || variant === "terracotta") && "primary",
        variant === "secondary" && "yellow",
        size === "sm" && "small",
        size === "lg" && "px-6 py-3 text-base",
        className,
      )}
      {...props}
    >
      {isLoading && (
        <Loader2 size={16} className="animate-spin" aria-hidden="true" />
      )}
      {children}
    </button>
  ),
);
Button.displayName = "Button";
