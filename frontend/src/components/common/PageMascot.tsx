import React from "react";

const mascotImages = {
  explore: "luminous-cat-explore-v2.png",
  community: "luminous-cat-community-v2.png",
  reading: "luminous-cat-reading-v2.png",
  collection: "luminous-cat-collection-v2.png",
  profile: "luminous-cat-profile-v2.png",
  admin: "luminous-cat-admin-v2.png",
  lost: "luminous-cat-lost-v2.png",
} as const;

interface PageMascotProps {
  pose: keyof typeof mascotImages;
  className?: string;
}

// 猫猫只作页面装饰，预留图片比例，避免遮挡操作或重复播报。
export const PageMascot: React.FC<PageMascotProps> = ({ pose, className = "" }) => (
  <span className={`page-mascot ${className}`} aria-hidden="true">
    <img
      src={`/illustrations/cats/${mascotImages[pose]}`}
      alt=""
      width="1157"
      height="1359"
      loading="lazy"
      decoding="async"
      draggable={false}
    />
  </span>
);
