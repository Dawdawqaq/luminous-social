import React, { useState } from "react";
import { Plus, Search } from "lucide-react";
import { SalonCard } from "../components/community/SalonCard";
import { ContentState } from "../components/common/ContentState";
import { PageMascot } from "../components/common/PageMascot";
import { useCommunityBrowse } from "../hooks/useCommunityBrowse";
import { useAuth } from "../context/AuthContext";
import { communityCategories } from "../community/categories";
import { Button } from "../components/ui/Button";
export const SalonsPage: React.FC<{ onOpenCreateSalon: () => void }> = ({
  onOpenCreateSalon,
}) => {
  const { user } = useAuth();
  const [search, setSearch] = useState("");
  const [scope, setScope] = useState("all");
  const [category, setCategory] = useState("");
  const browse = useCommunityBrowse(search, scope, category, true, user?.id);
  const { items: matches, loading: isLoading, error, refresh } = browse;
  return (
    <div className="space-y-6">
      <header className="panel page-heading page-heading-with-mascot">
        <div className="page-heading-copy">
          <h1>发现社区</h1>
          <p>兴趣各不相同，总有一处适合你。</p>
        </div>
        <div className="page-heading-actions">
          <PageMascot pose="explore" className="heading-mascot" />
          <button
            className="ink-button primary small shrink-0"
            onClick={onOpenCreateSalon}
          >
            <Plus size={18} />
            <span className="hidden sm:inline">创建社区</span>
            <span className="sm:hidden">创建</span>
          </button>
        </div>
      </header>
      <div className="flex flex-col sm:flex-row gap-4 items-stretch sm:items-center">
        <div className="search-field flex-1">
          <Search size={20} />
          <input
            className="search-input"
            aria-label="筛选社区"
            placeholder="搜一个感兴趣的主题"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>
        <div className="flex gap-2">
          <button
            className={"ink-button small " + (scope === "all" ? "primary" : "")}
            aria-pressed={scope === "all"}
            onClick={() => setScope("all")}
          >
            全部社区
          </button>
          <button
            className={
              "ink-button small " + (scope === "joined" ? "primary" : "")
            }
            aria-pressed={scope === "joined"}
            onClick={() => setScope("joined")}
          >
            我加入的
          </button>
        </div>
      </div>
      <div className="flex items-center gap-3 text-sm"><label htmlFor="browse-category" className="shrink-0 font-semibold">兴趣分类</label><select id="browse-category" className="min-w-0 rounded-md border border-ink-primary dark:border-night-border bg-white dark:bg-night-surface px-3 py-2" value={category} onChange={e => setCategory(e.target.value)}><option value="">全部分类</option>{Object.entries(communityCategories).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></div>
      {isLoading ? (
        <div className="panel">
          <ContentState title="正在加载社区" loading />
        </div>
      ) : error ? (
        <div className="panel">
          <ContentState
            title="社区暂时加载失败"
            description={error}
            onRetry={refresh}
          />
        </div>
      ) : matches.length ? (
        <>
          <p className="text-sm text-ink-muted dark:text-night-muted">
            已展示 {matches.length} 个社区{browse.hasNext ? "，继续加载发现更多" : ""}
          </p>
          <div className="community-grid">
            {matches.map((c) => (
              <SalonCard key={c.id || c.name} community={c} />
            ))}
          </div>
          {browse.moreError && <p role="alert">{browse.moreError}</p>}
          {browse.hasNext && <div className="text-center"><Button variant="outline" size="sm" onClick={browse.loadMore} isLoading={browse.loadingMore}>{browse.moreError ? "重试加载" : "加载更多社区"}</Button></div>}
        </>
      ) : (
        <div className="panel">
          <ContentState
            title={
              search || category
                ? "没找到符合筛选的社区"
                : scope === "joined"
                  ? "还没有加入符合筛选的社区"
                  : "社区故事，从这里开始"
            }
            description={
              search || category
                ? "换一个关键词，或创建你想要的社区。"
                : "看看其他社区，或者创建一个属于你们的空间。"
            }
          >
            <button
              className="ink-button primary small"
              onClick={
                search || category
                  ? () => { setSearch(""); setCategory(""); }
                  : scope === "joined"
                    ? () => setScope("all")
                    : onOpenCreateSalon
              }
            >
              {search || category
                ? "清空筛选"
                : scope === "joined"
                  ? "浏览全部社区"
                  : "创建社区"}
            </button>
          </ContentState>
        </div>
      )}
    </div>
  );
};
