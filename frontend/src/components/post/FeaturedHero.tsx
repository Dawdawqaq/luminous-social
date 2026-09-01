import React from 'react';
import { Link } from 'react-router-dom';
import { Post } from '../../types';
import { Avatar } from '../ui/Avatar';
import { formatEchoes, estimateReadingTime, formatTimeAgo } from '../../utils';
import { Sparkles, ArrowRight } from 'lucide-react';

interface FeaturedHeroProps {
  post: Post;
}

export const FeaturedHero: React.FC<FeaturedHeroProps> = ({ post }) => {
  const readingTime = estimateReadingTime(post.content);
  const commSlug = (post.community?.name || 'Craft').replace(/^c\//, '');
  const cleanExcerpt = post.content ? post.content.replace(/[#*`>]/g, '').slice(0, 240) : '';

  return (
    <section className="relative overflow-hidden bg-paper-50 dark:bg-night-surface border border-paper-300 dark:border-night-border rounded-[2px] shadow-tactile my-4 md:my-6 p-6 sm:p-8 md:p-9 space-y-4">
      {/* Top Header Tagline */}
      <div className="flex items-center justify-between gap-4 pb-3.5 hairline-b text-xs font-sans text-ink-muted dark:text-night-muted">
        <div className="flex items-center gap-2">
          <span className="flex items-center gap-1.5 text-[11px] font-semibold tracking-wider uppercase text-terracotta dark:text-terracotta-dark">
            <Sparkles className="w-3.5 h-3.5" /> 今日沙龙特选 · Featured Spotlight
          </span>
        </div>
        <span className="text-[11px] font-mono text-ink-subtle dark:text-night-subtle">
          ISSUE NO. {new Date().getDate()} · {new Date().toLocaleString('zh-CN', { month: 'short', year: 'numeric' })}
        </span>
      </div>

      {/* Main Full-Width Content */}
      <div className="space-y-3.5">
        <div className="flex items-center gap-2 text-xs font-sans text-ink-muted dark:text-night-muted">
          <Link
            to={`/s/${commSlug}`}
            className="font-medium text-terracotta dark:text-terracotta-dark hover:underline"
          >
            c/{commSlug}
          </Link>
          <span className="text-ink-subtle">·</span>
          <span>{post.type === 'ESSAY' ? '深度长文' : '主题探讨'}</span>
          <span className="text-ink-subtle">·</span>
          <span>{readingTime} 分钟深度阅读</span>
        </div>

        <Link to={`/post/${post.id}`} className="group block">
          <h1 className="font-serif text-2xl sm:text-3xl md:text-4xl font-medium text-ink-primary dark:text-night-ink leading-[1.28] tracking-tight group-hover:text-terracotta-hover transition-colors">
            {post.title}
          </h1>
        </Link>

        {cleanExcerpt && (
          <blockquote className="font-serif italic text-sm sm:text-base md:text-[17px] text-ink-muted dark:text-night-muted leading-relaxed border-l-2 border-terracotta/50 pl-4 py-1 my-2">
            “{cleanExcerpt}...”
          </blockquote>
        )}
      </div>

      {/* Footer Meta & Direct Action */}
      <div className="pt-4 hairline-t flex flex-wrap items-center justify-between gap-4 text-xs font-sans text-ink-muted dark:text-night-muted">
        <Link
          to={`/u/${post.author.username}`}
          className="flex items-center gap-2.5 hover:text-ink-primary dark:hover:text-night-ink transition-colors"
        >
          <Avatar
            src={post.author.avatar}
            name={post.author.displayName || post.author.username}
            size="sm"
          />
          <div>
            <p className="font-medium text-ink-primary dark:text-night-ink">
              {post.author.displayName || post.author.username}
            </p>
            <p className="text-[10px] text-ink-subtle">
              发布于 {formatTimeAgo(post.timestamp)}
            </p>
          </div>
        </Link>

        <div className="flex items-center gap-4 sm:gap-6">
          <span>{formatEchoes(post.votes)} 赞同</span>
          <span>{post.comments} 条讨论</span>
          <Link
            to={`/post/${post.id}`}
            className="inline-flex items-center gap-1.5 py-1.5 px-3.5 rounded-[2px] bg-ink-primary text-paper-50 dark:bg-night-ink dark:text-night-canvas hover:opacity-90 font-medium transition-opacity"
          >
            <span>阅读全文</span>
            <ArrowRight className="w-3.5 h-3.5" />
          </Link>
        </div>
      </div>
    </section>
  );
};
