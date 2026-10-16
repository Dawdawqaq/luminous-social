import React from "react";
import { GrowthProgress } from "../../types";
import { Modal } from "./Modal";
import { LevelBadge } from "./LevelBadge";
import { getLevelStarStage, LEVEL_STAR_STAGES, MAX_USER_LEVEL } from "./levelStars";
import { cn } from "../../utils";

interface Props {
  isOpen: boolean;
  onClose: () => void;
  progress: GrowthProgress;
  displayName: string;
  own: boolean;
}

/** 按既有标识阶段展示全部等级，当前进度取个人主页的真实成长数据。 */
export const LevelGuideModal: React.FC<Props> = ({ isOpen, onClose, progress, displayName, own }) => {
  const currentStage = getLevelStarStage(progress.level);
  if (!currentStage) return null;
  const nextStage = LEVEL_STAR_STAGES.find(stage => stage.min > progress.level);
  return <Modal isOpen={isOpen} onClose={onClose} title="等级图鉴"
    subtitle="从 LV1 到 LV18，看看星星一路成长的样子。" maxWidth="lg">
    <div className="flex flex-wrap items-center justify-between gap-4 pb-5 hairline-b">
      <div className="min-w-0">
        <p className="text-sm text-ink-muted dark:text-night-muted break-words">{own ? "你的个人等级" : `${displayName} 的个人等级`}</p>
        <div className="flex items-center gap-3 mt-1">
          <LevelBadge level={progress.level} size="md" />
          <span className="text-sm text-ink-muted dark:text-night-muted">{progress.experience.toLocaleString()} 经验</span>
        </div>
        <p className="mt-1 text-sm">
          {progress.level === MAX_USER_LEVEL ? "已到达最高等级，双星为你闪耀。" : `距 LV${progress.level + 1} 还需 ${progress.experienceToNextLevel.toLocaleString()} 经验`}
        </p>
      </div>
      {nextStage && <div className="text-sm">
        <span className="text-ink-muted dark:text-night-muted">下个样式</span>
        <p className="mt-1 font-bold">LV{nextStage.min} 解锁</p>
      </div>}
    </div>
    <ol className="mt-2" aria-label="全部等级标识">
      {LEVEL_STAR_STAGES.map(stage => {
        const current = stage === currentStage;
        const next = stage === nextStage;
        const attained = stage.max < progress.level;
        const levels = Array.from({ length: stage.max - stage.min + 1 }, (_, index) => stage.min + index);
        return <li key={stage.min} className={cn("flex items-center gap-4 py-4 px-2 rounded-md", current && "bg-[#EFF6FF] dark:bg-[#172B46]")}
          aria-current={current ? "step" : undefined}>
          <img src={stage.image} alt={`LV${stage.min}${stage.max !== stage.min ? `–${stage.max}` : ""} 星星标识`}
            width={60} height={60} className="w-14 h-14 sm:w-16 sm:h-16 object-contain shrink-0" />
          <div className="min-w-0 flex-1">
            <div className="flex flex-wrap items-center justify-between gap-x-3 gap-y-1">
              <p className="font-bold">LV{stage.min}{stage.max !== stage.min ? `–${stage.max}` : ""}</p>
              <span className={cn("text-xs", current ? "font-semibold text-terracotta dark:text-terracotta-dark" : "text-ink-muted dark:text-night-muted")}>
                {current ? "当前阶段" : next ? "下个样式" : attained ? "已达到" : "待解锁"}
              </span>
            </div>
            <div className="mt-2 flex flex-wrap gap-2">
              {levels.map(level => <span key={level} aria-current={level === progress.level ? "true" : undefined}
                className={cn("text-xs py-1 px-1.5 rounded tabular-nums", level === progress.level ? "bg-terracotta text-white font-bold" : "text-ink-muted dark:text-night-muted")}>
                LV{level}
              </span>)}
            </div>
          </div>
        </li>;
      })}
    </ol>
    <p className="pt-4 hairline-t text-xs leading-relaxed text-ink-muted dark:text-night-muted">同一阶段使用相同的星星样式。个人等级与社区等级共用标识，经验分别累计。</p>
  </Modal>;
};
