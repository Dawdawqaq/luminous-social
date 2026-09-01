import React, { useState } from 'react';
import { Comment } from '../../types';
import { Avatar } from '../ui/Avatar';
import { Button } from '../ui/Button';
import { Textarea } from '../ui/Textarea';
import { formatTimeAgo, formatEchoes } from '../../utils';
import { postApi } from '../../api/client';
import { useAuth } from '../../context/AuthContext';
import { useToast } from '../../context/ToastContext';
import { CornerDownRight, Trash2, ArrowBigUp } from 'lucide-react';

interface CommentThreadProps {
  postId: string | number;
  comments: Comment[];
  onCommentAdded: (newComment: Comment) => void;
  onCommentDeleted: (commentId: string) => void;
  onRequireAuth: () => void;
}

export const CommentThread: React.FC<CommentThreadProps> = ({
  postId,
  comments,
  onCommentAdded,
  onCommentDeleted,
  onRequireAuth,
}) => {
  const { user, isAuthenticated } = useAuth();
  const { showToast } = useToast();
  const [content, setContent] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!isAuthenticated) {
      onRequireAuth();
      return;
    }
    if (!content.trim()) return;

    try {
      setIsSubmitting(true);
      const newComment = await postApi.createComment(postId, content.trim());
      onCommentAdded(newComment);
      setContent('');
      showToast('评论已发布', 'success');
    } catch (err: any) {
      showToast(err.message || '发表评论失败', 'error');
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleVote = async (comment: Comment) => {
    if (!isAuthenticated) {
      onRequireAuth();
      return;
    }
    try {
      const newVote = comment.userVote === 1 ? 0 : 1;
      await postApi.voteComment(comment.id, newVote);
      comment.userVote = newVote;
      comment.votes += newVote === 1 ? 1 : -1;
      showToast(newVote === 1 ? '已赞同评论' : '已取消赞同', 'info');
    } catch (err: any) {
      showToast(err.message || '操作失败', 'error');
    }
  };

  const handleDelete = async (commentId: string) => {
    if (!confirm('确认删除此条评论吗？')) return;
    try {
      await postApi.deleteComment(commentId);
      onCommentDeleted(commentId);
      showToast('评论已删除', 'info');
    } catch (err: any) {
      showToast(err.message || '删除失败', 'error');
    }
  };

  return (
    <div id="responses" className="space-y-6 pt-8 hairline-t">
      <div className="flex items-center justify-between">
        <h3 className="font-serif text-xl font-medium text-ink-primary dark:text-night-ink tracking-tight">
          评论与讨论 ({comments.length})
        </h3>
        <span className="text-xs font-sans text-ink-subtle dark:text-night-subtle">
          遵循社区公约，友善交流
        </span>
      </div>

      {/* Comment Composer */}
      <form onSubmit={handleSubmit} className="space-y-3 bg-paper-50 dark:bg-night-surface p-4 sm:p-5 border border-paper-300 dark:border-night-border rounded-[2px]">
        <Textarea
          value={content}
          onChange={(e) => setContent(e.target.value)}
          placeholder={
            isAuthenticated
              ? '写下您的见解、思考或回复...'
              : '登录后即可在此参与讨论...'
          }
          rows={3}
          disabled={!isAuthenticated}
        />
        <div className="flex justify-between items-center pt-1">
          <span className="text-[11px] text-ink-subtle dark:text-night-subtle">
            支持 Markdown 格式 · 鼓励成体系的探讨
          </span>
          <Button
            type="submit"
            size="sm"
            isLoading={isSubmitting}
            disabled={!isAuthenticated || !content.trim()}
          >
            发表评论
          </Button>
        </div>
      </form>

      {/* Comments List */}
      <div className="space-y-5">
        {comments.length === 0 ? (
          <div className="py-10 text-center text-ink-subtle dark:text-night-subtle font-serif italic text-sm">
            暂无评论。成为第一个发表看法的人吧。
          </div>
        ) : (
          comments.map((c) => {
            const isAuthor = user?.username === c.author.username;
            return (
              <div key={c.id} className="p-4 bg-paper-50 dark:bg-night-surface border border-paper-300/80 dark:border-night-border rounded-[2px] space-y-2 group">
                <div className="flex items-start justify-between">
                  <div className="flex items-center gap-2">
                    <Avatar
                      src={c.author.avatar}
                      name={c.author.displayName || c.author.username}
                      size="sm"
                    />
                    <div>
                      <span className="font-medium text-xs text-ink-primary dark:text-night-ink">
                        {c.author.displayName || c.author.username}
                      </span>
                      <span className="text-[11px] text-ink-subtle dark:text-night-subtle ml-2">
                        {formatTimeAgo(c.timestamp)}
                      </span>
                    </div>
                  </div>

                  {isAuthor && (
                    <button
                      onClick={() => handleDelete(c.id)}
                      className="opacity-0 group-hover:opacity-100 text-ink-subtle hover:text-terracotta transition-opacity p-1 cursor-pointer"
                      title="删除评论"
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                    </button>
                  )}
                </div>

                <div className="font-serif text-sm md:text-base text-ink-primary dark:text-night-ink leading-relaxed pl-1">
                  {c.content}
                </div>

                <div className="flex items-center gap-4 text-xs font-sans text-ink-muted dark:text-night-muted pt-1 pl-1">
                  <button
                    onClick={() => handleVote(c)}
                    className={`flex items-center gap-1 hover:text-ink-primary dark:hover:text-night-ink cursor-pointer ${
                      c.userVote === 1 ? 'text-terracotta dark:text-terracotta-dark font-medium' : ''
                    }`}
                  >
                    <ArrowBigUp className={`w-3.5 h-3.5 ${c.userVote === 1 ? 'fill-current' : ''}`} />
                    <span>{formatEchoes(c.votes)} 赞同</span>
                  </button>

                  <button
                    onClick={() => {
                      if (!isAuthenticated) onRequireAuth();
                      else setContent(`@${c.author.username} `);
                    }}
                    className="flex items-center gap-1 hover:text-ink-primary dark:hover:text-night-ink cursor-pointer"
                  >
                    <CornerDownRight className="w-3 h-3" />
                    <span>回复</span>
                  </button>
                </div>
              </div>
            );
          })
        )}
      </div>
    </div>
  );
};
