import React from 'react';
import { Link } from 'react-router-dom';

export const Footer: React.FC = () => {
  return (
    <footer className="w-full hairline-t mt-20 py-12 bg-paper-50/50 dark:bg-night-surface/30 transition-colors">
      <div className="max-w-6xl mx-auto px-4 sm:px-6">
        <div className="grid grid-cols-1 md:grid-cols-4 gap-8 mb-8">
          
          <div className="md:col-span-2 space-y-3">
            <div className="flex items-center gap-2">
              <span className="font-serif text-xl font-bold tracking-tight text-ink-primary dark:text-night-ink">
                LUMINOUS
              </span>
              <span className="w-1.5 h-1.5 rounded-full bg-terracotta -mt-1" />
            </div>
            <p className="font-serif italic text-sm text-ink-muted dark:text-night-muted max-w-sm leading-relaxed">
              “思想、作品与人的微光。在嘈杂的信息洪流中，为真正被认真对待的对话保留一席宁静。”
            </p>
            <p className="text-[11px] font-sans text-ink-subtle dark:text-night-subtle">
              A Quiet Sanctuary for Contemporary Humanism & Digital Discourse.
            </p>
          </div>

          <div className="space-y-2.5">
            <h4 className="text-xs font-sans font-semibold tracking-wider uppercase text-ink-primary dark:text-night-ink">
              沙龙索引
            </h4>
            <ul className="space-y-2 text-xs text-ink-muted dark:text-night-muted font-sans">
              <li><Link to="/salons" className="hover:text-ink-primary dark:hover:text-night-ink">探索所有沙龙房间</Link></li>
              <li><Link to="/?filter=hot" className="hover:text-ink-primary dark:hover:text-night-ink">今日焦点议题</Link></li>
              <li><Link to="/archive" className="hover:text-ink-primary dark:hover:text-night-ink">私人思想书架</Link></li>
            </ul>
          </div>

          <div className="space-y-2.5">
            <h4 className="text-xs font-sans font-semibold tracking-wider uppercase text-ink-primary dark:text-night-ink">
              沙龙守则
            </h4>
            <p className="text-xs text-ink-subtle dark:text-night-subtle leading-relaxed font-sans">
              保持宁静与真诚，珍视推敲与论辩。拒绝算法投喂与无意义的喧嚣。
            </p>
          </div>

        </div>

        <div className="hairline-t pt-6 flex flex-col sm:flex-row justify-between items-center gap-3 text-[11px] font-sans text-ink-subtle dark:text-night-subtle">
          <p>© {new Date().getFullYear()} Luminous. Crafted for Intellectual Quietude.</p>
          <div className="flex items-center gap-4">
            <span>按 <kbd className="font-mono bg-paper-200 dark:bg-night-border px-1 rounded text-[10px]">⌘K</kbd> 唤醒全局索引</span>
          </div>
        </div>
      </div>
    </footer>
  );
};
