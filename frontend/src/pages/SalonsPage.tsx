import React, { useState, useEffect } from 'react';
import { Community } from '../types';
import { communityApi } from '../api/client';
import { SalonCard } from '../components/community/SalonCard';
import { Button } from '../components/ui/Button';
import { Search, Plus, Compass, Loader2, X } from 'lucide-react';

interface SalonsPageProps {
  onOpenCreateSalon: () => void;
}

export const SalonsPage: React.FC<SalonsPageProps> = ({ onOpenCreateSalon }) => {
  const [salons, setSalons] = useState<Community[]>([]);
  const [search, setSearch] = useState('');
  const [scope, setScope] = useState<'all' | 'joined'>('all');
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    setIsLoading(true);
    communityApi.list()
      .then((res) => {
        setSalons(res || []);
      })
      .catch(() => {
        setSalons([]);
      })
      .finally(() => {
        setIsLoading(false);
      });
  }, []);

  const filteredSalons = salons.filter((s) => {
    if (scope === 'joined' && !s.joined) return false;
    if (!search.trim()) return true;
    const q = search.toLowerCase().trim();
    const slug = (s.name || '').toLowerCase();
    const title = (s.displayName || '').toLowerCase();
    const desc = (s.description || '').toLowerCase();
    return slug.includes(q) || title.includes(q) || desc.includes(q);
  });

  return (
    <div className="space-y-6 md:space-y-8 animate-in fade-in duration-150">
      
      {/* Salons Header */}
      <div className="flex flex-col sm:flex-row sm:items-end justify-between gap-4 pb-6 hairline-b">
        <div className="space-y-1.5 max-w-xl">
          <div className="flex items-center gap-2">
            <Compass className="w-5 h-5 text-terracotta" />
            <h1 className="font-serif text-3xl font-medium text-ink-primary dark:text-night-ink tracking-tight">
              社区列表 · Communities Directory
            </h1>
          </div>
          <p className="font-serif italic text-sm text-ink-muted dark:text-night-muted leading-relaxed">
            浏览不同主题领域的专属社区，找到与您志趣相投的创作者与研讨伙伴。
          </p>
        </div>

        <Button size="sm" onClick={onOpenCreateSalon} className="shrink-0">
          <Plus className="w-3.5 h-3.5" />
          <span>创建新社区</span>
        </Button>
      </div>

      {/* 联结搜索栏 (Unified Search & Discovery Bar) */}
      <div className="space-y-3">
        <div className="bg-paper-50 dark:bg-night-surface border border-paper-300 dark:border-night-border rounded-[2px] shadow-tactile p-2 sm:p-2.5 flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-3">
          
          {/* Search Input */}
          <div className="relative flex-1 flex items-center">
            <Search className="w-4 h-4 absolute left-3 text-ink-subtle dark:text-night-subtle pointer-events-none" />
            <input
              type="text"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="输入关键词实时筛选社区名称、主题或简介..."
              className="w-full pl-9 pr-8 py-1.5 text-xs font-sans bg-transparent text-ink-primary dark:text-night-ink placeholder:text-ink-subtle dark:placeholder:text-night-subtle focus:outline-none"
            />
            {search && (
              <button
                onClick={() => setSearch('')}
                className="absolute right-2.5 p-1 text-ink-subtle hover:text-ink-primary dark:text-night-subtle cursor-pointer"
                title="清空搜索"
              >
                <X className="w-3.5 h-3.5" />
              </button>
            )}
          </div>

          {/* Scope Controls */}
          <div className="flex items-center justify-end text-xs font-sans shrink-0">
            <div className="inline-flex p-0.5 rounded-[2px] bg-paper-200/70 dark:bg-night-canvas border border-paper-300/60 dark:border-night-border/60">
              <button
                onClick={() => setScope('all')}
                className={`px-3 py-1 rounded-[2px] transition-colors cursor-pointer ${
                  scope === 'all'
                    ? 'bg-paper-50 dark:bg-night-surface text-ink-primary dark:text-night-ink font-semibold shadow-xs'
                    : 'text-ink-muted hover:text-ink-primary dark:text-night-muted'
                }`}
              >
                全部社区
              </button>
              <button
                onClick={() => setScope('joined')}
                className={`px-3 py-1 rounded-[2px] transition-colors cursor-pointer ${
                  scope === 'joined'
                    ? 'bg-paper-50 dark:bg-night-surface text-ink-primary dark:text-night-ink font-semibold shadow-xs'
                    : 'text-ink-muted hover:text-ink-primary dark:text-night-muted'
                }`}
              >
                我加入的
              </button>
            </div>
          </div>
        </div>

        {/* Live Filter Counter */}
        <div className="flex items-center justify-between text-xs font-sans text-ink-subtle dark:text-night-subtle px-1">
          <span>
            {search.trim() ? (
              <>
                筛选 “<strong className="text-ink-primary dark:text-night-ink">{search}</strong>” 匹配到{' '}
                <strong>{filteredSalons.length}</strong> 个社区
              </>
            ) : (
              <>
                共 <strong>{filteredSalons.length}</strong> 个活跃社区
              </>
            )}
          </span>
          {search && (
            <button
              onClick={() => setSearch('')}
              className="text-terracotta dark:text-terracotta-dark hover:underline cursor-pointer"
            >
              重置筛选
            </button>
          )}
        </div>
      </div>

      {/* Salons Grid */}
      {isLoading ? (
        <div className="py-24 text-center space-y-3">
          <Loader2 className="w-6 h-6 animate-spin mx-auto text-ink-subtle" />
          <p className="font-serif italic text-sm text-ink-muted dark:text-night-muted">
            正在载入社区列表...
          </p>
        </div>
      ) : filteredSalons.length === 0 ? (
        <div className="py-20 text-center space-y-3 bg-paper-50 dark:bg-night-surface border border-paper-300 dark:border-night-border rounded-[2px]">
          <p className="font-serif italic text-sm text-ink-muted dark:text-night-muted">
            {search
              ? `未发现与 “${search}” 相关的社区。`
              : scope === 'joined'
              ? '您尚未加入任何社区。'
              : '当前暂无社区。'}
          </p>
          <div className="flex items-center justify-center gap-3 pt-2">
            {search && (
              <Button size="sm" variant="outline" onClick={() => setSearch('')}>
                清空搜索词
              </Button>
            )}
            <Button size="sm" onClick={onOpenCreateSalon}>
              创建新社区
            </Button>
          </div>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
          {filteredSalons.map((salon) => (
            <SalonCard key={salon.id || salon.name} community={salon} />
          ))}
        </div>
      )}

    </div>
  );
};
