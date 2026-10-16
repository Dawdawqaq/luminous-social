import React from "react";
import { ChevronRight } from "lucide-react";
import { cn } from "../../utils";
import { getLevelStarStage, MAX_USER_LEVEL } from "./levelStars";
import "./LevelBadge.css";

export interface LevelBadgeProps {
  level?: number | null;
  scope?: "personal" | "community";
  size?: "sm" | "md";
  className?: string;
  onClick?: () => void;
}

export const LevelBadge: React.FC<LevelBadgeProps> = ({
  level,
  scope = "personal",
  size = "sm",
  className,
  onClick,
}) => {
  const stage = getLevelStarStage(level);
  if (!stage) return null;

  const label = `${scope === "community" ? "社区内等级" : "个人等级"} ${level} 级${level === MAX_USER_LEVEL ? "，已满级" : ""}`;
  const content = <>
      <img src={stage.image} alt="" width={24} height={24} decoding="async" />
      <span className="level-badge__label" aria-hidden="true">
        <span className="level-badge__prefix">LV</span>
        <span className="level-badge__number">{level}</span>
      </span>
      {onClick && <ChevronRight size={12} aria-hidden="true" />}
    </>;
  const classes = cn("level-badge", `level-badge--${size}`, onClick && "level-badge--interactive", className);
  return onClick ? (
    <button type="button" className={classes} onClick={onClick} aria-label={`查看等级图鉴，${label}`} aria-haspopup="dialog" title="查看等级图鉴">
      {content}
    </button>
  ) : (
    <span className={classes} aria-label={label} title={label}>{content}</span>
  );
};
