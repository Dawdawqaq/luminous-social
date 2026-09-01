import React from 'react';

/**
 * 借鉴 Anthropic Claude 视觉语言的艺术背景层
 * 包含：
 * 1. 晨光与天球同心弧线 (Celestial Sunburst & Concentric Arcs - 右上)
 * 2. 大地拓扑与手作等高波纹 (Topographic Contour Waves - 左下)
 * 3. 柔和赭石/暖琥珀色温渐变光晕 (Warm Ambient Meshes)
 * 4. 纸质微噪点触感 (Tactile Paper Texture)
 */
export const ArtisticBackground: React.FC = () => {
  return (
    <div className="fixed inset-0 pointer-events-none overflow-hidden z-0 select-none" aria-hidden="true">
      
      {/* 1. 柔和环境光晕 (Warm Ambient Glows) */}
      <div className="absolute -top-32 -right-32 w-[600px] h-[600px] rounded-full bg-gradient-to-br from-terracotta/6 via-[#D97706]/4 to-transparent blur-[140px] dark:from-terracotta-dark/8 dark:via-[#B45309]/5 dark:to-transparent" />
      <div className="absolute top-1/2 -left-48 w-[500px] h-[500px] rounded-full bg-gradient-to-tr from-[#C26B38]/4 via-[#E5A93C]/3 to-transparent blur-[160px] dark:from-[#9E4427]/6 dark:via-[#78350F]/4 dark:to-transparent" />
      <div className="absolute -bottom-40 right-1/4 w-[550px] h-[550px] rounded-full bg-gradient-to-t from-terracotta/4 via-transparent to-transparent blur-[150px] dark:from-terracotta-dark/6" />

      {/* 2. 右上方：Claude 标志性天球同心弧与发散光芒 (Celestial Arcs & Sunburst) */}
      <svg
        className="absolute -top-24 -right-24 w-[750px] h-[750px] text-terracotta/15 dark:text-terracotta-dark/20 transition-opacity duration-700"
        viewBox="0 0 800 800"
        fill="none"
        xmlns="http://www.w3.org/2000/svg"
      >
        {/* 同心圆弧 (Concentric Orbital Rings) */}
        <circle cx="700" cy="100" r="120" stroke="currentColor" strokeWidth="0.75" strokeDasharray="4 6" />
        <circle cx="700" cy="100" r="200" stroke="currentColor" strokeWidth="0.75" />
        <circle cx="700" cy="100" r="280" stroke="currentColor" strokeWidth="0.75" strokeDasharray="2 4" />
        <circle cx="700" cy="100" r="360" stroke="currentColor" strokeWidth="0.75" />
        <circle cx="700" cy="100" r="450" stroke="currentColor" strokeWidth="0.75" strokeDasharray="8 8" />
        <circle cx="700" cy="100" r="540" stroke="currentColor" strokeWidth="0.75" />
        <circle cx="700" cy="100" r="640" stroke="currentColor" strokeWidth="0.75" strokeDasharray="3 5" />
        <circle cx="700" cy="100" r="740" stroke="currentColor" strokeWidth="0.75" />

        {/* 发散光束 (Radiant Rays) */}
        <line x1="700" y1="100" x2="100" y2="100" stroke="currentColor" strokeWidth="0.75" strokeDasharray="3 6" />
        <line x1="700" y1="100" x2="150" y2="280" stroke="currentColor" strokeWidth="0.75" />
        <line x1="700" y1="100" x2="240" y2="450" stroke="currentColor" strokeWidth="0.75" strokeDasharray="4 8" />
        <line x1="700" y1="100" x2="360" y2="600" stroke="currentColor" strokeWidth="0.75" />
        <line x1="700" y1="100" x2="500" y2="700" stroke="currentColor" strokeWidth="0.75" strokeDasharray="2 5" />
        <line x1="700" y1="100" x2="650" y2="760" stroke="currentColor" strokeWidth="0.75" />

        {/* 焦点星芒小徽记 */}
        <circle cx="700" cy="100" r="12" fill="currentColor" fillOpacity="0.2" />
        <circle cx="700" cy="100" r="4" fill="currentColor" fillOpacity="0.6" />
      </svg>

      {/* 3. 左下方：有机拓扑等高线 (Organic Topographic Contours) */}
      <svg
        className="absolute -bottom-36 -left-36 w-[800px] h-[800px] text-ink-primary/[0.04] dark:text-night-ink/[0.06] transition-opacity duration-700"
        viewBox="0 0 800 800"
        fill="none"
        xmlns="http://www.w3.org/2000/svg"
      >
        <path
          d="M -50 400 C 150 350, 250 550, 450 480 C 650 410, 700 650, 850 600"
          stroke="currentColor"
          strokeWidth="0.75"
        />
        <path
          d="M -50 460 C 170 410, 270 610, 470 540 C 670 470, 720 710, 850 660"
          stroke="currentColor"
          strokeWidth="0.75"
          strokeDasharray="4 6"
        />
        <path
          d="M -50 520 C 190 470, 290 670, 490 600 C 690 530, 740 770, 850 720"
          stroke="currentColor"
          strokeWidth="0.75"
        />
        <path
          d="M -50 580 C 210 530, 310 730, 510 660 C 710 590, 760 830, 850 780"
          stroke="currentColor"
          strokeWidth="0.75"
          strokeDasharray="2 4"
        />
        <path
          d="M -50 640 C 230 590, 330 790, 530 720 C 730 650, 780 890, 850 840"
          stroke="currentColor"
          strokeWidth="0.75"
        />
        <path
          d="M -50 700 C 250 650, 350 850, 550 780 C 750 710, 800 950, 850 900"
          stroke="currentColor"
          strokeWidth="0.75"
        />
      </svg>

      {/* 4. 纵深辅助线与版面标尺细节 (Editorial Grid & Compass Accent) */}
      <div className="absolute top-24 left-8 hidden 2xl:flex flex-col items-center gap-6 text-[9px] font-mono text-ink-subtle/30 dark:text-night-subtle/30 tracking-widest uppercase">
        <span className="[writing-mode:vertical-lr]">LUMINOUS · SALON CANVAS</span>
        <div className="w-[1px] h-32 bg-ink-primary/10 dark:bg-night-ink/10" />
        <span className="text-[8px]">31°14′N 121°28′E</span>
      </div>

      <div className="absolute bottom-24 right-8 hidden 2xl:flex flex-col items-center gap-6 text-[9px] font-mono text-ink-subtle/30 dark:text-night-subtle/30 tracking-widest uppercase">
        <div className="w-[1px] h-32 bg-ink-primary/10 dark:bg-night-ink/10" />
        <span className="[writing-mode:vertical-lr]">PRINT EDITION · ARCHIVAL PAPER</span>
      </div>

    </div>
  );
};
