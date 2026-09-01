import { clsx, type ClassValue } from 'clsx';
import { twMerge } from 'tailwind-merge';

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

export function formatTimeAgo(timeStr?: string): string {
  if (!timeStr) return '';
  try {
    const date = new Date(timeStr);
    if (isNaN(date.getTime())) return timeStr;
    const now = new Date();
    const diffInSeconds = Math.floor((now.getTime() - date.getTime()) / 1000);

    if (diffInSeconds < 60) return '刚刚';
    if (diffInSeconds < 3600) return `${Math.floor(diffInSeconds / 60)} 分钟前`;
    if (diffInSeconds < 86400) return `${Math.floor(diffInSeconds / 3600)} 小时前`;
    if (diffInSeconds < 604800) return `${Math.floor(diffInSeconds / 86400)} 天前`;
    
    return date.toLocaleDateString('zh-CN', {
      month: 'short',
      day: 'numeric',
    });
  } catch {
    return timeStr;
  }
}

export function estimateReadingTime(content?: string): number {
  if (!content) return 1;
  const wordsPerMinute = 250;
  const wordCount = content.trim().length;
  return Math.max(1, Math.ceil(wordCount / wordsPerMinute));
}

export function formatEchoes(count: number): string {
  if (count <= 0) return '0';
  if (count >= 1000) return `${(count / 1000).toFixed(1)}k`;
  return count.toString();
}

export function getInitials(name?: string): string {
  if (!name) return 'L';
  const parts = name.trim().split(/\s+/);
  if (parts.length >= 2) {
    return (parts[0][0] + parts[1][0]).toUpperCase();
  }
  return name.slice(0, 2).toUpperCase();
}
