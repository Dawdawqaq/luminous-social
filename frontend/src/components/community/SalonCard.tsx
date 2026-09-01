import React from 'react';
import { Link } from 'react-router-dom';
import { Community } from '../../types';
import { Avatar } from '../ui/Avatar';
import { Users, Check } from 'lucide-react';

interface SalonCardProps {
  community: Community;
}

export const SalonCard: React.FC<SalonCardProps> = ({ community }) => {
  const slug = (community.name || '').replace(/^c\//, '');
  return (
    <Link
      to={`/s/${slug}`}
      className="group block p-5 bg-paper-50 dark:bg-night-surface border border-paper-300 dark:border-night-border rounded-[2px] shadow-tactile hover:border-ink-primary/40 dark:hover:border-night-ink/40 transition-all flex flex-col justify-between"
    >
      <div className="space-y-3">
        <div className="flex items-start justify-between gap-3">
          <div className="flex items-center gap-3 min-w-0">
            <Avatar src={community.icon} name={community.displayName || slug} size="md" />
            <div className="min-w-0">
              <div className="flex items-center gap-2">
                <h3 className="font-serif text-lg font-medium text-ink-primary dark:text-night-ink group-hover:text-terracotta transition-colors truncate">
                  c/{slug}
                </h3>
                {community.joined && (
                  <span className="inline-flex items-center gap-0.5 text-[10px] font-sans px-1.5 py-0.5 rounded-[2px] bg-terracotta/10 text-terracotta dark:text-terracotta-dark font-medium shrink-0">
                    <Check className="w-2.5 h-2.5" /> 已加入
                  </span>
                )}
              </div>
              {community.displayName && community.displayName !== slug && (
                <p className="text-xs font-sans text-ink-subtle dark:text-night-subtle truncate">
                  {community.displayName}
                </p>
              )}
            </div>
          </div>
        </div>

        <p className="font-serif text-xs md:text-sm text-ink-muted dark:text-night-muted line-clamp-2 leading-relaxed">
          {community.description || '探索思想与作品的专属社区空间。'}
        </p>
      </div>

      <div className="flex items-center justify-between pt-3 mt-3 hairline-t text-xs font-sans text-ink-subtle dark:text-night-subtle">
        <span className="flex items-center gap-1.5">
          <Users className="w-3.5 h-3.5" />
          <span>{community.membersCount} 位成员</span>
        </span>
        <span className="font-medium text-ink-primary dark:text-night-ink group-hover:text-terracotta transition-colors">
          进入社区 ↗
        </span>
      </div>
    </Link>
  );
};
