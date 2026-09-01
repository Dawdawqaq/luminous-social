import React, { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { Search, FileText, Users, Compass, ArrowRight, Loader2, X } from 'lucide-react';
import { postApi, communityApi, userApi } from '../../api/client';
import { Post, Community, User } from '../../types';
import { Avatar } from '../ui/Avatar';

export const HeaderSearch: React.FC = () => {
  const navigate = useNavigate();
  const containerRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const [query, setQuery] = useState('');
  const [isOpen, setIsOpen] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [posts, setPosts] = useState<Post[]>([]);
  const [salons, setSalons] = useState<Community[]>([]);
  const [users, setUsers] = useState<User[]>([]);

  // 快捷键支持 (Cmd/Ctrl + K)
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
        e.preventDefault();
        inputRef.current?.focus();
        setIsOpen(true);
      }
      if (e.key === 'Escape') {
        setIsOpen(false);
        inputRef.current?.blur();
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, []);

  // 点击外部自动关闭下拉浮层
  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setIsOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  // 实时防抖检索
  useEffect(() => {
    if (!query.trim()) {
      setPosts([]);
      setSalons([]);
      setUsers([]);
      setIsLoading(false);
      return;
    }

    const timer = setTimeout(async () => {
      setIsLoading(true);
      try {
        const [postsRes, salonsRes, usersRes] = await Promise.allSettled([
          postApi.list({ search: query.trim(), size: 4 }),
          communityApi.list(query.trim()),
          userApi.search(query.trim()),
        ]);

        if (postsRes.status === 'fulfilled') setPosts(postsRes.value.items || []);
        if (salonsRes.status === 'fulfilled') setSalons((salonsRes.value || []).slice(0, 4));
        if (usersRes.status === 'fulfilled') setUsers((usersRes.value || []).slice(0, 4));
      } catch {
      } finally {
        setIsLoading(false);
      }
    }, 220);

    return () => clearTimeout(timer);
  }, [query]);

  const handleSelect = (url: string) => {
    setIsOpen(false);
    setQuery('');
    navigate(url);
  };

  const hasResults = posts.length > 0 || salons.length > 0 || users.length > 0;

  return (
    <div ref={containerRef} className="relative">
      {/* 顶部导航栏原位搜索输入框 */}
      <div className="relative flex items-center">
        <Search className="w-3.5 h-3.5 absolute left-2.5 text-ink-subtle dark:text-night-subtle pointer-events-none" />
        <input
          ref={inputRef}
          type="text"
          value={query}
          onFocus={() => setIsOpen(true)}
          onChange={(e) => {
            setQuery(e.target.value);
            setIsOpen(true);
          }}
          placeholder="搜索帖子、社区、用户..."
          className="w-36 sm:w-48 md:w-56 focus:w-60 md:focus:w-72 pl-8 pr-11 py-1.5 text-xs font-sans bg-paper-50 dark:bg-night-surface border border-paper-300 dark:border-night-border rounded-[2px] text-ink-primary dark:text-night-ink placeholder:text-ink-subtle dark:placeholder:text-night-subtle focus:outline-none focus:border-terracotta dark:focus:border-terracotta-dark focus:ring-1 focus:ring-terracotta/20 transition-all shadow-xs"
        />
        {query ? (
          <button
            onClick={() => {
              setQuery('');
              setIsOpen(false);
            }}
            className="absolute right-2 p-0.5 text-ink-subtle hover:text-ink-primary dark:text-night-subtle cursor-pointer"
          >
            <X className="w-3.5 h-3.5" />
          </button>
        ) : (
          <kbd className="hidden md:inline absolute right-2 text-[10px] bg-paper-200/80 dark:bg-night-border px-1 py-0.2 rounded text-ink-subtle dark:text-night-subtle font-mono pointer-events-none">
            ⌘K
          </kbd>
        )}
      </div>

      {/* 原位向下展开的实时检索建议卡 */}
      {isOpen && query.trim() && (
        <div className="absolute right-0 top-full mt-2 w-[340px] sm:w-[420px] bg-paper-50 dark:bg-night-surface border border-paper-300 dark:border-night-border rounded-[2px] shadow-lifted z-50 overflow-hidden animate-in fade-in zoom-in-95 duration-100">
          
          <div className="max-h-[420px] overflow-y-auto p-3 space-y-4">
            {isLoading ? (
              <div className="py-6 text-center text-xs font-sans text-ink-subtle flex items-center justify-center gap-2">
                <Loader2 className="w-4 h-4 animate-spin text-terracotta" />
                <span>正在检索中...</span>
              </div>
            ) : !hasResults ? (
              <div className="py-6 text-center text-xs font-sans text-ink-subtle dark:text-night-subtle">
                未检索到与 “{query}” 相关的内容
              </div>
            ) : (
              <>
                {/* 帖子篇章匹配 */}
                {posts.length > 0 && (
                  <div className="space-y-1.5">
                    <div className="flex items-center gap-1 text-[11px] font-sans font-semibold uppercase tracking-wider text-ink-muted dark:text-night-muted px-1.5">
                      <FileText className="w-3 h-3 text-terracotta" />
                      <span>帖子篇章 ({posts.length})</span>
                    </div>
                    <div className="space-y-0.5">
                      {posts.map((p) => {
                        const commSlug = (p.community?.name || '').replace(/^c\//, '');
                        return (
                          <div
                            key={p.id}
                            onClick={() => handleSelect(`/post/${p.id}`)}
                            className="p-2 rounded-[2px] hover:bg-paper-200/60 dark:hover:bg-night-surfaceHover cursor-pointer transition-colors flex items-center justify-between"
                          >
                            <div className="min-w-0 pr-2">
                              <p className="font-serif text-xs font-medium text-ink-primary dark:text-night-ink truncate">
                                {p.title}
                              </p>
                              <p className="text-[10px] text-ink-subtle dark:text-night-subtle truncate">
                                by {p.author.displayName || p.author.username} · in c/{commSlug}
                              </p>
                            </div>
                            <ArrowRight className="w-3 h-3 text-ink-subtle shrink-0" />
                          </div>
                        );
                      })}
                    </div>
                  </div>
                )}

                {/* 社区空间匹配 */}
                {salons.length > 0 && (
                  <div className="space-y-1.5 hairline-t pt-2.5">
                    <div className="flex items-center gap-1 text-[11px] font-sans font-semibold uppercase tracking-wider text-ink-muted dark:text-night-muted px-1.5">
                      <Compass className="w-3 h-3 text-terracotta" />
                      <span>社区空间 ({salons.length})</span>
                    </div>
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-1.5">
                      {salons.map((s) => {
                        const slug = (s.name || '').replace(/^c\//, '');
                        return (
                          <div
                            key={s.id || s.name}
                            onClick={() => handleSelect(`/s/${slug}`)}
                            className="p-2 rounded-[2px] border border-paper-300/70 dark:border-night-border hover:bg-paper-200/60 dark:hover:bg-night-surfaceHover cursor-pointer transition-colors flex items-center gap-2 min-w-0"
                          >
                            <Avatar src={s.icon} name={s.displayName || slug} size="sm" />
                            <div className="min-w-0 truncate">
                              <p className="font-serif text-xs font-medium text-ink-primary dark:text-night-ink truncate">
                                c/{slug}
                              </p>
                              <p className="text-[10px] text-ink-subtle">{s.membersCount} 位成员</p>
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  </div>
                )}

                {/* 创作者匹配 */}
                {users.length > 0 && (
                  <div className="space-y-1.5 hairline-t pt-2.5">
                    <div className="flex items-center gap-1 text-[11px] font-sans font-semibold uppercase tracking-wider text-ink-muted dark:text-night-muted px-1.5">
                      <Users className="w-3 h-3 text-terracotta" />
                      <span>创作者 ({users.length})</span>
                    </div>
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-1.5">
                      {users.map((u) => (
                        <div
                          key={u.id || u.username}
                          onClick={() => handleSelect(`/u/${u.username}`)}
                          className="p-2 rounded-[2px] border border-paper-300/70 dark:border-night-border hover:bg-paper-200/60 dark:hover:bg-night-surfaceHover cursor-pointer transition-colors flex items-center gap-2 min-w-0"
                        >
                          <Avatar src={u.avatar} name={u.displayName || u.username} size="sm" />
                          <div className="min-w-0 truncate">
                            <p className="font-medium text-xs text-ink-primary dark:text-night-ink truncate">
                              {u.displayName || u.username}
                            </p>
                            <p className="text-[10px] text-ink-subtle truncate">@{u.username}</p>
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </>
            )}
          </div>

          <div className="px-3 py-1.5 bg-paper-100 dark:bg-night-canvas hairline-t flex items-center justify-between text-[10px] font-sans text-ink-subtle">
            <span>按 ESC 关闭</span>
            <span>按 ↑ ↓ 快捷浏览</span>
          </div>
        </div>
      )}
    </div>
  );
};
