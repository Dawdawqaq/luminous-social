export const MAX_USER_LEVEL = 18;

export const LEVEL_STAR_STAGES = [
  { min: 1, max: 4, name: "原型", image: "/brand/level-stars/star-stage-01.png" },
  { min: 5, max: 9, name: "柔亮", image: "/brand/level-stars/star-stage-02.png" },
  { min: 10, max: 12, name: "内框", image: "/brand/level-stars/star-stage-03.png" },
  { min: 13, max: 15, name: "切面", image: "/brand/level-stars/star-stage-04.png" },
  { min: 16, max: 17, name: "一颗闪星", image: "/brand/level-stars/star-stage-05.png" },
  { min: 18, max: 18, name: "两颗闪星", image: "/brand/level-stars/star-stage-06.png" },
] as const;

// 缺少等级或数据不合法时不显示，不能用默认等级冒充真实成长数据。
export function getLevelStarStage(level?: number | null) {
  if (
    typeof level !== "number" ||
    !Number.isInteger(level) ||
    level < 1 ||
    level > MAX_USER_LEVEL
  ) {
    return undefined;
  }
  return LEVEL_STAR_STAGES.find((stage) => level >= stage.min && level <= stage.max);
}
