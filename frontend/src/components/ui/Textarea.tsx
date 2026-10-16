import React, { useId } from "react";
import { cn } from "../../utils";
export interface TextareaProps extends React.TextareaHTMLAttributes<HTMLTextAreaElement> {
  label?: string;
  error?: string;
  hint?: string;
}
export const Textarea = React.forwardRef<HTMLTextAreaElement, TextareaProps>(
  ({ className, label, error, hint, rows = 4, id, ...props }, ref) => {
    const generatedId = useId();
    const inputId = id || generatedId;
    return (
      <div className="w-full space-y-2 text-left">
        {label && (
          <label htmlFor={inputId} className="block text-sm font-semibold">
            {label}
          </label>
        )}
        <textarea
          {...props}
          id={inputId}
          rows={rows}
          ref={ref}
          aria-invalid={!!error}
          aria-describedby={error || hint ? inputId + "-note" : undefined}
          className={cn(
            "w-full rounded-md border-[1.5px] border-ink-primary bg-paper-50 px-3.5 py-3 text-sm leading-relaxed text-ink-primary resize-y placeholder:text-ink-subtle dark:bg-night-surface dark:text-night-ink dark:border-night-border",
            error && "border-red-600",
            className,
          )}
        />
        {(error || hint) && (
          <p
            id={inputId + "-note"}
            className={cn(
              "text-xs",
              error
                ? "text-red-600 dark:text-red-400"
                : "text-ink-muted dark:text-night-muted",
            )}
          >
            {error || hint}
          </p>
        )}
      </div>
    );
  },
);
Textarea.displayName = "Textarea";
