import React from "react";
import { Link } from "react-router-dom";
import { Post } from "../types";
import { postApi } from "../api/client";
import { useAuth } from "../context/AuthContext";
import { useRemoteData } from "../hooks/useRemoteData";
import { ContentState } from "../components/common/ContentState";
import { PageMascot } from "../components/common/PageMascot";
import { PostCard } from "../components/post/PostCard";
export const BookmarksPage: React.FC<{ onRequireAuth: () => void }> = ({
  onRequireAuth,
}) => {
  const { user, isAuthenticated, isLoading: authLoading } = useAuth();
  const bookmarks = useRemoteData<Post[]>(
    async () => (await postApi.getBookmarks({ size: 30 })).items,
    [user?.id],
    [],
    isAuthenticated,
  );
  return (
    <div className="max-w-5xl mx-auto space-y-6">
      <header className="panel page-heading page-heading-with-mascot">
        <div className="page-heading-copy">
          <h1>我的收藏</h1>
          <p>喜欢的讨论和发现，留在这里慢慢看。</p>
        </div>
        <PageMascot pose="collection" className="heading-mascot" />
      </header>
      <section className="panel">
        <div className="px-5">
          {authLoading || bookmarks.loading ? (
            <ContentState title="正在加载收藏" loading />
          ) : !isAuthenticated ? (
            <ContentState
              title="登录后查看收藏"
              description="收藏喜欢的帖子，下次回来接着看。"
            >
              <button
                className="ink-button primary small"
                onClick={onRequireAuth}
              >
                登录
              </button>
            </ContentState>
          ) : bookmarks.error ? (
            <ContentState
              title="收藏加载失败"
              description={bookmarks.error}
              onRetry={bookmarks.reload}
            />
          ) : bookmarks.data.length ? (
            bookmarks.data.map((post) => (
              <PostCard
                key={post.id}
                post={post}
                onRequireAuth={onRequireAuth}
                onVoteChange={(updated) =>
                  bookmarks.setData((previous) =>
                    updated.bookmarked === false
                      ? previous.filter((p) => p.id !== updated.id)
                      : previous.map((p) =>
                          p.id === updated.id ? updated : p,
                        ),
                  )
                }
              />
            ))
          ) : (
            <ContentState
              title="还没有收藏"
              description="浏览帖子时点一下收藏，就能在这里找到它。"
            >
              <Link className="ink-button primary small" to="/">
                看看帖子
              </Link>
            </ContentState>
          )}
        </div>
      </section>
    </div>
  );
};
