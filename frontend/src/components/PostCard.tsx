import { ThumbsUp, ThumbsDown, MessageCircle, Share2, MoreVertical, Smartphone, Brain, User as UserIcon, Bookmark } from 'lucide-react';
import { Post } from '../types';
import { cn } from '../lib/utils';
import { motion } from 'motion/react';
import { Link } from 'react-router-dom';
import { useEffect, useState } from 'react';
import { api } from '../lib/api';
import { useAuth } from '../auth/AuthContext';

interface PostCardProps {
  post: Post;
  key?: string | number;
}

export function PostCard({ post }: PostCardProps) {
  const { user } = useAuth();
  const [currentPost, setCurrentPost] = useState(post);
  const [isBookmarked, setIsBookmarked] = useState(Boolean(post.bookmarked));
  const isAltIcon = currentPost.community.name.includes('Futurism') || currentPost.community.name.includes('pixel8');

  // Map community name to path
  const communitySlug = currentPost.community.name.replace(/^c\//, '');
  const communityPath = `/community/${communitySlug}`;
  useEffect(() => {
    setCurrentPost(post);
    setIsBookmarked(Boolean(post.bookmarked));
  }, [post]);
  const vote = async (value: -1 | 1) => {
    if (!user) {
      window.dispatchEvent(new CustomEvent('auth:required', {
        detail: { message: value === 1 ? '您需要先登录，才能给帖子点赞哦' : '您需要先登录，才能给帖子点踩哦' }
      }));
      return;
    }
    const actualValue = currentPost.userVote === value ? 0 : value;
    const updated = await api.votePost(currentPost.id, actualValue);
    setCurrentPost(updated);
  };

  const toggleBookmark = async () => {
    if (!user) {
      window.dispatchEvent(new CustomEvent('auth:required', {
        detail: { message: '您需要先登录，才能将帖子加入收藏' }
      }));
      return;
    }
    const updated = isBookmarked ? await api.removeBookmark(currentPost.id) : await api.bookmarkPost(currentPost.id);
    setCurrentPost(updated);
    setIsBookmarked(Boolean(updated.bookmarked));
  };

  return (
    <motion.article 
      initial={{ opacity: 0, y: 20 }}
      whileInView={{ opacity: 1, y: 0 }}
      viewport={{ once: true }}
      className="card-luminous"
    >
      <header className="flex items-center justify-between mb-4">
        <div className="flex items-center gap-3">
          <Link to={user && currentPost.author.username === user.username ? "/settings" : `/user/${currentPost.author.username}`}>
            {currentPost.author.avatar ? (
              <img src={currentPost.author.avatar} alt={currentPost.author.username} className="w-10 h-10 rounded-full object-cover hover:opacity-80 transition-opacity" />
            ) : (
              <div className={cn(
                "w-10 h-10 rounded-full flex items-center justify-center transition-opacity hover:opacity-80",
                currentPost.community.name.includes('Futurism') ? "bg-tertiary-fixed text-on-tertiary-fixed" : "bg-primary-container text-on-primary-container"
              )}>
                {currentPost.community.name.includes('Futurism') ? <Brain size={20} /> : <UserIcon size={20} />}
              </div>
            )}
          </Link>
          <div>
            <h3 className="text-sm font-bold text-on-surface">
              <Link to={user && currentPost.author.username === user.username ? "/settings" : `/user/${currentPost.author.username}`} className="hover:underline">{currentPost.author.displayName}</Link>
              <span className="text-on-surface-variant font-normal"> in </span>
              <Link to={communityPath} className="text-primary font-bold hover:underline">{currentPost.community.name}</Link>
            </h3>
            <p className="text-xs text-on-surface-variant font-medium">{currentPost.timestamp}</p>
          </div>
        </div>
        <button className="text-on-surface-variant hover:text-on-surface">
          <MoreVertical size={20} />
        </button>
      </header>
      
      <Link to={`/post/${currentPost.id}`} className="block group/content">
        <div className="mb-4">
          <h2 className={cn(
            "font-display leading-tight text-on-surface mb-2 group-hover/content:text-primary transition-colors",
            currentPost.image ? "text-xl font-semibold" : "text-2xl font-bold"
          )}>
            {currentPost.title}
          </h2>
          {currentPost.content && (
            <p className="text-on-surface-variant text-sm mb-4 leading-relaxed font-normal line-clamp-3">
              {currentPost.content}
            </p>
          )}
          {currentPost.image && (
            <img 
              src={currentPost.image} 
              alt={currentPost.title} 
              className="w-full h-auto max-h-[500px] object-cover rounded-2xl mb-4 group-hover/content:opacity-95 transition-opacity"
            />
          )}
        </div>
      </Link>

      <footer className="flex items-center gap-6 border-t border-outline-variant pt-4">
        <div className="flex items-center bg-surface-container-high rounded-full overflow-hidden">
          <button onClick={(e) => { e.preventDefault(); e.stopPropagation(); vote(1); }} className="px-4 py-2 hover:bg-surface-variant transition-colors flex items-center gap-2 group">
            <ThumbsUp size={18} className={cn("group-hover:scale-110 transition-transform", currentPost.userVote === 1 ? "text-green-600 fill-green-600" : "text-on-surface-variant")} />
            <span className={cn("text-xs font-bold", currentPost.userVote !== 0 && currentPost.userVote !== undefined ? (currentPost.userVote === 1 ? "text-green-600" : "text-red-600") : "")}>{currentPost.votes}</span>
          </button>
          <div className="w-px h-4 bg-outline-variant" />
          <button onClick={(e) => { e.preventDefault(); e.stopPropagation(); vote(-1); }} className="px-4 py-2 hover:bg-surface-variant transition-colors">
            <ThumbsDown size={18} className={cn("group-hover:scale-110 transition-transform", currentPost.userVote === -1 ? "text-red-600 fill-red-600" : "text-on-surface-variant")} />
          </button>
        </div>
        
        <button className="flex items-center gap-1.5 text-on-surface-variant hover:text-on-surface transition-colors group">
          <MessageCircle size={18} className="group-hover:scale-110 transition-transform" />
          <span className="text-xs font-semibold">{currentPost.comments} 条评论</span>
        </button>

        <button className="flex items-center gap-1.5 text-on-surface-variant hover:text-on-surface transition-colors group">
          <Share2 size={18} className="group-hover:scale-110 transition-transform" />
          <span className="text-xs font-semibold">分享</span>
        </button>

        <button 
          onClick={toggleBookmark}
          className={cn(
            "ml-auto flex items-center gap-1.5 transition-all p-2 rounded-xl group",
            isBookmarked 
              ? "bg-primary/10 text-primary scale-110" 
              : "text-on-surface-variant hover:text-on-surface hover:bg-surface-variant flex items-center"
          )}
        >
          <Bookmark 
            size={18} 
            className={cn(
              "transition-transform",
              isBookmarked ? "fill-current scale-110" : "group-hover:scale-110"
            )} 
          />
          {isBookmarked && <span className="text-xs font-bold">已收藏</span>}
        </button>
      </footer>
    </motion.article>
  );
}
