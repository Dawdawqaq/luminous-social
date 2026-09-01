import React from 'react';
import { Comment } from '../../types';
import { Avatar } from '../ui/Avatar';
import { formatTimeAgo } from '../../utils';
import { Pin } from 'lucide-react';

interface MarginaliaSidebarProps {
  comments: Comment[];
  onScrollToDiscussion?: () => void;
}

export const MarginaliaSidebar: React.FC<MarginaliaSidebarProps> = ({ comments, onScrollToDiscussion }) => {
  const curatedComments = [...comments]
    .sort((a, b) => b.votes - a.votes)
    .slice(0, 3);

  if (curatedComments.length === 0) {
    return (
      <div className="p-4 bg-paper-50/50 dark:bg-night-surface/30 border border-paper-300/60 dark:border-night-border rounded-[2px] text-xs font-sans text-ink-subtle dark:text-night-subtle">
        <p className="font-serif italic text-sm text-ink-muted dark:text-night-muted mb-1">
          暂无边注批注
        </p>
        <p>在此处留下有见地的回响，即可作为精选边注被永久锚定在书页边缘。</p>
      </div>
    );
  }

  return (
    <aside className="space-y-4">
      <div className="flex items-center justify-between text-xs font-sans text-ink-muted dark:text-night-muted pb-2 hairline-b">
        <span className="flex items-center gap-1 font-semibold uppercase tracking-wider text-[11px] text-terracotta dark:text-terracotta-dark">
          <Pin className="w-3 h-3" /> 精选边注 (Marginalia)
        </span>
        <button
          onClick={onScrollToDiscussion}
          className="text-[11px] text-ink-subtle hover:text-ink-primary dark:hover:text-night-ink cursor-pointer"
        >
          查看全部 {comments.length} 条 ↳
        </button>
      </div>

      <div className="space-y-3">
        {curatedComments.map((comment) => (
          <div
            key={comment.id}
            className="p-3.5 bg-paper-50 dark:bg-night-surface border border-paper-300 dark:border-night-border rounded-[2px] shadow-tactile space-y-2 text-xs"
          >
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-1.5">
                <Avatar
                  src={comment.author.avatar}
                  name={comment.author.displayName || comment.author.username}
                  size="xs"
                />
                <span className="font-medium text-ink-primary dark:text-night-ink">
                  {comment.author.displayName || comment.author.username}
                </span>
              </div>
              <span className="text-[10px] text-ink-subtle dark:text-night-subtle">
                {formatTimeAgo(comment.timestamp)}
              </span>
            </div>

            <p className="font-serif italic text-ink-muted dark:text-night-muted leading-relaxed">
              “{comment.content}”
            </p>

            <div className="text-[10px] font-sans text-terracotta dark:text-terracotta-dark">
              · {comment.votes} echoes
            </div>
          </div>
        ))}
      </div>
    </aside>
  );
};
