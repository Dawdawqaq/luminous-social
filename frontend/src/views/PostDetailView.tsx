import { useParams, Link, useNavigate } from 'react-router-dom';
import { 
  ThumbsUp, 
  ThumbsDown, 
  MessageCircle, 
  Share2, 
  MoreVertical, 
  ArrowLeft,
  User as UserIcon,
  Brain,
  Send,
  Bookmark,
  Sparkles,
  Trash2
} from 'lucide-react';
import { motion } from 'motion/react';
import { cn } from '../lib/utils';
import { useEffect, useState } from 'react';
import { api } from '../lib/api';
import { Comment, Post } from '../types';
import { useAuth } from '../auth/AuthContext';

export function PostDetailView() {
  const { id } = useParams();
  const { user } = useAuth();
  const navigate = useNavigate();
  const [post, setPost] = useState<Post | null>(null);
  const [comments, setComments] = useState<Comment[]>([]);
  const [commentText, setCommentText] = useState('');
  const [isBookmarked, setIsBookmarked] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [deletingPost, setDeletingPost] = useState(false);

  useEffect(() => {
    if (!id) return;
    let alive = true;
    setLoading(true);
    setError('');
    Promise.all([api.post(id), api.comments(id)])
      .then(([postData, commentData]) => {
        if (!alive) return;
        setPost(postData);
        setComments(commentData);
        setIsBookmarked(Boolean(postData.bookmarked));
      })
      .catch((ex) => alive && setError(ex instanceof Error ? ex.message : '加载失败'))
      .finally(() => alive && setLoading(false));
    return () => {
      alive = false;
    };
  }, [id]);

  if (loading) {
    return <div className="max-w-[800px] mx-auto card-luminous text-center text-on-surface-variant">正在加载帖子详情...</div>;
  }

  if (error || !post) {
    return <div className="max-w-[800px] mx-auto card-luminous text-center text-red-600 font-bold">{error || '帖子不存在'}</div>;
  }

  const isAltIcon = post.community.name.includes('Futurism') || post.community.name.includes('pixel8');
  const communitySlug = post.community.name.replace(/^c\//, '');
  const communityPath = `/community/${communitySlug}`;
  const submitComment = async () => {
    if (!user) {
      window.dispatchEvent(new CustomEvent('auth:required', {
        detail: { message: '您需要先登录，才能发表评论哦' }
      }));
      return;
    }
    if (!id || !commentText.trim()) {
      return;
    }
    const comment = await api.addComment(id, commentText.trim());
    setComments([...comments, comment]);
    setCommentText('');
    setPost({ ...post, comments: post.comments + 1 });
  };
  const toggleBookmark = async () => {
    if (!user) {
      window.dispatchEvent(new CustomEvent('auth:required', {
        detail: { message: '您需要先登录，才能将帖子加入收藏' }
      }));
      return;
    }
    const updated = isBookmarked ? await api.removeBookmark(post.id) : await api.bookmarkPost(post.id);
    setPost(updated);
    setIsBookmarked(Boolean(updated.bookmarked));
  };
  const vote = async (value: -1 | 1) => {
    if (!user) {
      window.dispatchEvent(new CustomEvent('auth:required', {
        detail: { message: value === 1 ? '您需要先登录，才能给帖子点赞哦' : '您需要先登录，才能给帖子点踩哦' }
      }));
      return;
    }
    if (!post) return;
    const actualValue = post.userVote === value ? 0 : value;
    setPost(await api.votePost(post.id, actualValue));
  };
  const voteComment = async (commentId: string, value: -1 | 1, currentVote?: number) => {
    if (!user) {
      window.dispatchEvent(new CustomEvent('auth:required', {
        detail: { message: value === 1 ? '您需要先登录，才能给该评论点赞哦' : '您需要先登录，才能给该评论点踩哦' }
      }));
      return;
    }
    const actualValue = currentVote === value ? 0 : value;
    const updated = await api.voteComment(commentId, actualValue);
    setComments(comments.map(c => c.id === commentId ? updated : c));
  };

  const handleDeletePost = async () => {
    if (!post || !window.confirm('确定要删除这篇帖子吗？此操作不可撤销。')) return;
    setDeletingPost(true);
    try {
      await api.deletePost(post.id);
      navigate(-1);
    } catch (e) {
      setDeletingPost(false);
      alert(e instanceof Error ? e.message : '删除失败，请重试');
    }
  };

  const handleDeleteComment = async (commentId: string) => {
    if (!window.confirm('确定要删除这条评论吗？')) return;
    try {
      await api.deleteComment(commentId);
      setComments(prev => prev.filter(c => c.id !== commentId));
      if (post) setPost({ ...post, comments: Math.max(0, post.comments - 1) });
    } catch (e) {
      alert(e instanceof Error ? e.message : '删除失败，请重试');
    }
  };

  return (
    <motion.div 
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      className="max-w-[800px] mx-auto pb-20"
    >
      <button 
        onClick={() => window.history.back()}
        className="flex items-center gap-2 text-on-surface-variant hover:text-primary transition-colors mb-6 group"
      >
        <ArrowLeft size={20} className="group-hover:-translate-x-1 transition-transform" />
        <span className="text-sm font-bold">返回</span>
      </button>

      <article className="card-luminous p-8">
        <header className="flex items-center justify-between mb-6">
          <div className="flex items-center gap-4">
            <Link to={user && post.author.username === user.username ? "/settings" : `/user/${post.author.username}`} className="relative">
              {post.author.avatar ? (
                <img src={post.author.avatar} alt={post.author.username} className="w-12 h-12 rounded-2xl object-cover border border-outline-variant shadow-sm hover:opacity-80 transition-opacity" />
              ) : (
                <div className={cn(
                  "w-12 h-12 rounded-2xl flex items-center justify-center border border-outline-variant shadow-sm text-xl font-bold hover:opacity-80 transition-opacity",
                  isAltIcon ? "bg-tertiary-container text-on-tertiary-container" : "bg-primary-container text-on-primary-container"
                )}>
                  {post.author.username.slice(0, 2).toUpperCase()}
                </div>
              )}
            </Link>
            <div>
              <div className="flex items-center gap-2">
                <Link to={communityPath} className="font-display font-bold text-lg text-primary hover:underline">
                  {post.community.name}
                </Link>
                <span className="text-on-surface-variant text-xs">•</span>
                <span className="text-on-surface-variant text-xs font-medium">{post.timestamp}</span>
              </div>
              <p className="text-sm text-on-surface-variant">
                由 <Link to={user && post.author.username === user.username ? "/settings" : `/user/${post.author.username}`} className="font-bold hover:underline">{post.author.displayName}</Link> 发布
              </p>
            </div>
          </div>
          <div className="flex items-center gap-3">
            {/* 作者专属删除按钮 */}
            {user && post.author.username === user.username && (
              <button
                id="delete-post-btn"
                onClick={handleDeletePost}
                disabled={deletingPost}
                className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-xs font-bold text-red-500 hover:bg-red-50 dark:hover:bg-red-950/30 transition-colors disabled:opacity-50"
                title="删除帖子"
              >
                <Trash2 size={15} />
                <span>{deletingPost ? '删除中...' : '删除'}</span>
              </button>
            )}
            <button className="p-2 hover:bg-surface-variant rounded-full transition-colors text-on-surface-variant">
              <MoreVertical size={24} />
            </button>
          </div>
        </header>

        <div className="mb-8">
          <h1 className="text-3xl font-display font-bold text-on-surface mb-4 leading-tight">
            {post.title}
          </h1>
          {post.content && (
            <p className="text-on-surface-variant text-lg mb-6 leading-relaxed">
              {post.content}
            </p>
          )}
          {post.image && (
            <div className="rounded-[32px] overflow-hidden border border-outline-variant shadow-xl">
              <img 
                src={post.image} 
                alt={post.title} 
                className="w-full h-auto max-h-[600px] object-cover"
              />
            </div>
          )}
        </div>

        <footer className="flex items-center gap-8 border-t border-outline-variant pt-6">
          <div className="flex items-center bg-surface-container-high rounded-full overflow-hidden">
            <button onClick={(e) => { e.preventDefault(); e.stopPropagation(); vote(1); }} className="px-6 py-3 hover:bg-surface-variant transition-colors flex items-center gap-2 group">
              <ThumbsUp size={20} className={cn("group-hover:scale-110 transition-transform", post.userVote === 1 ? "text-green-600 fill-green-600" : "text-on-surface-variant")} />
              <span className={cn("text-sm font-bold", post.userVote !== 0 && post.userVote !== undefined ? (post.userVote === 1 ? "text-green-600" : "text-red-600") : "")}>{post.votes}</span>
            </button>
            <div className="w-px h-6 bg-outline-variant" />
            <button onClick={(e) => { e.preventDefault(); e.stopPropagation(); vote(-1); }} className="px-6 py-3 hover:bg-surface-variant transition-colors">
              <ThumbsDown size={20} className={cn("group-hover:scale-110 transition-transform", post.userVote === -1 ? "text-red-600 fill-red-600" : "text-on-surface-variant")} />
            </button>
          </div>
          
          <div className="flex items-center gap-2 text-on-surface-variant font-bold text-sm">
            <MessageCircle size={20} />
            <span>{post.comments} 条评论</span>
          </div>

          <button className="flex items-center gap-2 text-on-surface-variant hover:text-on-surface transition-colors font-bold text-sm group">
            <Share2 size={20} className="group-hover:scale-110 transition-transform" />
            <span>分享</span>
          </button>

          <button 
            onClick={toggleBookmark}
            className={cn(
              "ml-auto flex items-center gap-2 px-6 py-3 rounded-2xl transition-all group",
              isBookmarked 
                ? "bg-primary text-on-primary shadow-lg shadow-primary/20 scale-105" 
                : "bg-surface-container-high text-on-surface-variant hover:text-on-surface hover:bg-surface-variant"
            )}
          >
            <Bookmark 
              size={20} 
              className={cn(
                "transition-transform",
                isBookmarked ? "fill-current" : "group-hover:scale-110"
              )} 
            />
            <span className="font-bold text-sm">{isBookmarked ? '已收藏' : '收藏'}</span>
          </button>
        </footer>
      </article>

      {/* Comment Section */}
      <section className="mt-8" id="comment-section">
        <div className="card-luminous p-6 mb-8">
          <div className="flex items-center gap-3 mb-4">
            <span className="text-sm font-bold text-on-surface">添加评论</span>
          </div>
          <div className="relative group/comment-box">
            <textarea 
              id="comment-input"
              value={commentText}
              onChange={(e) => setCommentText(e.target.value)}
              placeholder="分享你的想法..."
              className="w-full min-h-[120px] bg-surface-container-lowest border border-outline-variant rounded-2xl p-4 text-on-surface focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all resize-none"
              disabled={!user}
            />
            {!user && (
              <div 
                onClick={() => window.dispatchEvent(new CustomEvent('auth:required', {
                  detail: { message: '您需要先登录，才能在这个帖子下发表您的看法哦' }
                }))}
                className="absolute inset-0 bg-surface-container-lowest/60 backdrop-blur-[4px] rounded-2xl flex flex-col items-center justify-center cursor-pointer transition-all hover:bg-surface-container-lowest/40 group-hover/comment-box:backdrop-blur-[2px]"
              >
                <div className="bg-primary/10 text-primary w-10 h-10 rounded-full flex items-center justify-center mb-2 shadow-inner">
                  <Sparkles size={18} className="animate-pulse" />
                </div>
                <span className="text-sm font-bold text-on-surface">登录以发表你的看法</span>
                <span className="text-xs text-on-surface-variant font-medium mt-1">分享你的独特高见...</span>
              </div>
            )}
            {user && (
              <button 
                className={cn(
                  "absolute bottom-4 right-4 p-2 rounded-xl transition-all flex items-center gap-2",
                  commentText.trim() ? "bg-primary text-on-primary shadow-lg scale-100" : "bg-surface-variant text-on-surface-variant scale-90 opacity-50"
                )}
                disabled={!commentText.trim()}
                onClick={submitComment}
              >
                <span className="text-xs font-bold px-2">发布</span>
                <div className="w-8 h-8 rounded-lg bg-white/20 flex items-center justify-center">
                  <Send size={16} />
                </div>
              </button>
            )}
          </div>
        </div>

        <div className="flex flex-col gap-4">
          <h3 className="font-bold text-on-surface px-2 mb-2 flex items-center gap-2">
            评论区
            <span className="text-xs font-medium text-on-surface-variant bg-surface-container-high px-2 py-0.5 rounded-full">
              {comments.length}
            </span>
          </h3>
          {comments.map((comment, index) => (
            <motion.div 
              key={comment.id}
              initial={{ opacity: 0, x: -10 }}
              animate={{ opacity: 1, x: 0 }}
              transition={{ delay: index * 0.1 }}
              className="card-luminous !p-6"
            >
              <div className="flex gap-4">
                <Link to={user && comment.author.username === user.username ? "/settings" : `/user/${comment.author.username}`} className="shrink-0">
                  {comment.author.avatar ? (
                    <img src={comment.author.avatar} alt={comment.author.username} className="w-10 h-10 rounded-full object-cover hover:opacity-80 transition-opacity" />
                  ) : (
                    <div className="w-10 h-10 rounded-full bg-secondary-container flex items-center justify-center text-on-secondary-container font-bold text-sm hover:opacity-80 transition-opacity">
                      {comment.author.username.slice(0, 2).toUpperCase()}
                    </div>
                  )}
                </Link>
                <div className="flex-1">
                  <div className="flex items-center justify-between mb-1">
                    <Link to={user && comment.author.username === user.username ? "/settings" : `/user/${comment.author.username}`} className="text-sm font-bold text-primary hover:underline">
                      {comment.author.displayName}
                    </Link>
                    <span className="text-xs text-on-surface-variant">{comment.timestamp}</span>
                  </div>
                  <p className="text-sm text-on-surface leading-relaxed mb-4">
                    {comment.content}
                  </p>
                  <div className="flex items-center gap-4">
                    <button onClick={() => voteComment(comment.id, 1, comment.userVote)} className="flex items-center gap-1.5 text-on-surface-variant hover:text-green-600 transition-colors">
                      <ThumbsUp size={16} className={cn(comment.userVote === 1 && "text-green-600 fill-green-600")} />
                      <span className={cn("text-xs font-bold", comment.userVote !== 0 && comment.userVote !== undefined ? (comment.userVote === 1 ? "text-green-600" : "text-red-600") : "")}>{comment.votes}</span>
                    </button>
                    <button onClick={() => voteComment(comment.id, -1, comment.userVote)} className="text-on-surface-variant hover:text-red-600 transition-colors">
                      <ThumbsDown size={16} className={cn(comment.userVote === -1 && "text-red-600 fill-red-600")} />
                    </button>
                    <button 
                      onClick={() => {
                        setCommentText(`回复 @${comment.author.displayName} : `);
                        document.getElementById('comment-section')?.scrollIntoView({ behavior: 'smooth' });
                        setTimeout(() => document.getElementById('comment-input')?.focus(), 300);
                      }} 
                      className="text-on-surface-variant hover:text-on-surface text-xs font-bold"
                    >
                      回复
                    </button>
                    {/* 评论作者专属删除按钮 */}
                    {user && comment.author.username === user.username && (
                      <button
                        id={`delete-comment-btn-${comment.id}`}
                        onClick={() => handleDeleteComment(comment.id)}
                        className="ml-auto flex items-center gap-1 text-xs font-bold text-red-400 hover:text-red-600 transition-colors"
                        title="删除评论"
                      >
                        <Trash2 size={13} />
                        <span>删除</span>
                      </button>
                    )}
                  </div>
                </div>
              </div>
            </motion.div>
          ))}
        </div>
      </section>
    </motion.div>
  );
}
