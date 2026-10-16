import React, { useEffect, useId, useRef } from "react";
import { createPortal } from "react-dom";
import { X } from "lucide-react";
import { cn } from "../../utils";
export interface ModalProps {
  isOpen: boolean;
  onClose: () => void;
  title?: string;
  subtitle?: string;
  children: React.ReactNode;
  maxWidth?: "sm" | "md" | "lg" | "xl" | "2xl";
  dismissible?: boolean;
  initialFocusRef?: React.RefObject<HTMLElement>;
  role?: "dialog" | "alertdialog";
  describedBy?: string;
  placement?: "center" | "drawer";
}
export const Modal: React.FC<ModalProps> = ({
  isOpen,
  onClose,
  title,
  subtitle,
  children,
  maxWidth = "md",
  dismissible = true,
  initialFocusRef,
  role = "dialog",
  describedBy,
  placement = "center",
}) => {
  const titleId = useId();
  const modalRef = useRef<HTMLDivElement>(null);
  const closeRef = useRef(onClose);
  closeRef.current = dismissible ? onClose : () => {};
  useEffect(() => {
    if (!isOpen) return;
    const previous = document.activeElement as HTMLElement | null;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    const focusable = () =>
      Array.from(
        modalRef.current?.querySelectorAll<HTMLElement>(
          'button:not([disabled]),a[href],input:not([disabled]),textarea:not([disabled]),select:not([disabled]),[tabindex="0"]',
        ) || [],
      ).filter((el) => el.getClientRects().length > 0);
    const frame = requestAnimationFrame(() => {
      (initialFocusRef?.current || focusable()[0] || modalRef.current)?.focus();
    });
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        event.stopPropagation();
        closeRef.current();
      }
      if (event.key === "Tab") {
        const items = focusable();
        const first = items[0];
        const last = items[items.length - 1];
        if (!first) {
          event.preventDefault();
          modalRef.current?.focus();
        } else if (
          event.shiftKey &&
          (document.activeElement === first ||
            document.activeElement === modalRef.current)
        ) {
          event.preventDefault();
          last.focus();
        } else if (!event.shiftKey && document.activeElement === last) {
          event.preventDefault();
          first.focus();
        }
      }
    };
    document.addEventListener("keydown", onKey, true);
    return () => {
      cancelAnimationFrame(frame);
      document.body.style.overflow = previousOverflow;
      document.removeEventListener("keydown", onKey, true);
      previous?.focus();
    };
  }, [isOpen]);
  if (!isOpen) return null;
  const widths = {
    sm: "max-w-sm",
    md: "max-w-md",
    lg: "max-w-lg",
    xl: "max-w-2xl",
    "2xl": "max-w-4xl",
  };
  return createPortal(
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div
        className="absolute inset-0 bg-black/50"
        onClick={() => closeRef.current()}
        aria-hidden="true"
      />
      <div
        ref={modalRef}
        role={role}
        aria-modal="true"
        aria-labelledby={title ? titleId : undefined}
        aria-label={title ? undefined : "操作窗口"}
        aria-describedby={describedBy}
        tabIndex={-1}
        className={cn(
          "panel relative z-10 w-full max-h-[calc(100dvh-32px)] overflow-y-auto",
          widths[maxWidth],
          placement === "drawer" && "lumi-conversations-drawer",
        )}
      >
        <div className="flex items-start justify-between gap-3 p-5 hairline-b">
          <div>
            {title && (
              <h2 id={titleId} className="text-xl font-extrabold">
                {title}
              </h2>
            )}
            {subtitle && (
              <p className="mt-1 text-sm text-ink-muted dark:text-night-muted">
                {subtitle}
              </p>
            )}
          </div>
          <button
            type="button"
            aria-label="关闭窗口"
            onClick={() => closeRef.current()}
            disabled={!dismissible}
            className="icon-button -mr-2 -mt-2"
          >
            <X size={22} />
          </button>
        </div>
        <div className="p-5 sm:p-6">{children}</div>
      </div>
    </div>,
    document.body,
  );
};
