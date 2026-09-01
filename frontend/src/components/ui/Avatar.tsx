import React from 'react';
import { cn, getInitials } from '../../utils';

export interface AvatarProps {
  src?: string;
  name?: string;
  size?: 'xs' | 'sm' | 'md' | 'lg' | 'xl';
  className?: string;
}

export const Avatar: React.FC<AvatarProps> = ({ src, name = 'L', size = 'md', className }) => {
  const [imgError, setImgError] = React.useState(false);

  const sizes = {
    xs: 'w-5 h-5 text-[10px]',
    sm: 'w-7 h-7 text-xs',
    md: 'w-9 h-9 text-sm',
    lg: 'w-12 h-12 text-base',
    xl: 'w-16 h-16 text-lg',
  };

  const getFullAvatarUrl = (url?: string) => {
    if (!url) return undefined;
    if (url.startsWith('http://') || url.startsWith('https://')) return url;
    return url.startsWith('/') ? url : `/${url}`;
  };

  const avatarUrl = getFullAvatarUrl(src);

  return (
    <div
      className={cn(
        'relative inline-flex items-center justify-center shrink-0 rounded-full overflow-hidden bg-paper-200 dark:bg-night-border border border-paper-300/80 dark:border-night-border font-serif font-medium text-ink-primary dark:text-night-ink select-none',
        sizes[size],
        className
      )}
    >
      {avatarUrl && !imgError ? (
        <img
          src={avatarUrl}
          alt={name}
          onError={() => setImgError(true)}
          className="w-full h-full object-cover"
        />
      ) : (
        <span>{getInitials(name)}</span>
      )}
    </div>
  );
};
