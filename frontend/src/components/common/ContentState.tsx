import React from "react";
import { MessageCircle, RefreshCw, Loader2 } from "lucide-react";
interface Props {
  title: string;
  description?: string;
  onRetry?: () => void;
  children?: React.ReactNode;
  loading?: boolean;
  showIcon?: boolean;
}
export const ContentState: React.FC<Props> = ({
  title,
  description,
  onRetry,
  children,
  loading,
  showIcon = true,
}) => (
  <div
    className={showIcon ? "state-card" : "state-card state-card-text"}
    role={loading ? "status" : onRetry ? "alert" : undefined}
    aria-busy={loading || undefined}
  >
    {showIcon && (loading ? (
      <Loader2 className="animate-spin" size={29} />
    ) : onRetry ? (
      <RefreshCw size={29} />
    ) : (
      <MessageCircle size={29} />
    ))}
    <h2>{title}</h2>
    {description && <p>{description}</p>}
    <div className="state-actions">
      {onRetry && (
        <button className="ink-button small" onClick={onRetry}>
          重新加载
        </button>
      )}
      {children}
    </div>
  </div>
);
