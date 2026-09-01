export interface User {
  id: string;
  username: string;
  displayName: string;
  avatar?: string;
  banner?: string;
  bio?: string;
  stats?: {
    posts: number;
    followers: number;
    following: number;
  };
  joinDate?: string;
  isFollowing?: boolean;
  role?: string;
  isBanned?: boolean;
}

export interface Author {
  username: string;
  displayName: string;
  avatar?: string;
}

export interface CommunityRef {
  name: string;
  icon?: string;
}

export interface Post {
  id: string;
  author: Author;
  community: CommunityRef;
  title: string;
  content: string;
  image?: string;
  timestamp: string;
  votes: number;
  comments: number;
  type?: string; // ESSAY | DISCUSSION | FIELD_NOTE | IDEA
  bookmarked?: boolean;
  userVote?: number; // 1 | 0 | -1
  viewCount?: number;
}

export interface Comment {
  id: string;
  author: Author;
  content: string;
  timestamp: string;
  votes: number;
  userVote?: number;
}

export interface Moderator {
  id: string;
  username: string;
  displayName: string;
  initials?: string;
}

export interface Community {
  id: string;
  name: string;
  displayName?: string;
  icon?: string;
  banner?: string;
  description: string;
  membersCount: number;
  membersUnit?: string;
  rules?: string[];
  moderators?: Moderator[];
  joined?: boolean;
  ownerId?: string;
}

export interface RecommendedCommunity {
  id: string;
  name: string;
  members: string;
  category: string;
  icon?: string;
  isJoined?: boolean;
}

export interface Notification {
  id: string;
  type: 'VOTE' | 'COMMENT' | 'FOLLOW' | 'SYSTEM' | string;
  actor: {
    id: string;
    username: string;
    displayName: string;
    avatar?: string;
  };
  target?: {
    type: string;
    id: string;
    title: string;
    targetUrl?: string;
  };
  contentPreview?: string;
  isRead: boolean;
  createdAt: string;
}

export interface AdminUser {
  id: string;
  username: string;
  displayName: string;
  role: string;
  isBanned: boolean;
  createdAt: string;
  postCount?: number;
}

export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  total: number;
  hasNext: boolean;
}

export interface ApiResponse<T> {
  success: boolean;
  data: T;
  error?: {
    code: string;
    message: string;
  };
  timestamp: string;
}

export interface ScrollResult<T> {
  items: T[];
  minScore?: number;
  offset: number;
  hasMore: boolean;
}

export interface CheckInResponse {
  success: boolean;
  totalDays: number;
  consecutiveDays: number;
  message: string;
}

export interface CheckInStats {
  totalDays: number;
  consecutiveDays: number;
  checkedToday: boolean;
  checkedDays: number[];
}
