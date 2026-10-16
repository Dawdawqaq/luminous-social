import React from "react";
import { ArrowLeft } from "lucide-react";
import { useAppNavigation } from "../../context/NavigationContext";

interface Props { fallback?: string; fallbackLabel?: string; }

export const PageBackButton: React.FC<Props> = ({ fallback = "/", fallbackLabel = "返回首页" }) => {
  const { canGoBack, goBack } = useAppNavigation();
  return (
    <button type="button" onClick={() => goBack(fallback)}
      className="inline-flex min-h-10 items-center gap-2 rounded-md text-sm font-semibold text-ink-muted hover:text-terracotta dark:text-night-muted dark:hover:text-terracotta-dark"
      aria-label={canGoBack ? "返回上一页" : fallbackLabel}>
      <ArrowLeft size={18} aria-hidden="true" />
      {canGoBack ? "返回上一页" : fallbackLabel}
    </button>
  );
};
