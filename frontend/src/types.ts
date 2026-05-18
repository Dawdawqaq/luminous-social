export interface User {
  id: string;
  username: string;
  displayName: string;
  avatar: string;
  banner?: string;
  bio?: string;
  stats: {
    posts: number;
    followers: number;
    following: number;
  };
  joinDate: string;
  isFollowing?: boolean;
}

export interface Community {
  id: string;
  name: string;
  displayName: string;
  icon: string;
  banner: string;
  description: string;
  membersCount: number;
  membersUnit: string;
  rules: string[];
  moderators: {
    id: string;
    username: string;
    initials: string;
  }[];
  joined?: boolean;
  ownerId?: string;
}

export interface Post {
  id: string;
  author: {
    username: string;
    displayName: string;
    avatar: string;
  };
  community: {
    name: string;
    icon?: string;
  };
  title: string;
  content: string;
  image?: string;
  timestamp: string;
  votes: number;
  comments: number;
  type: 'standard' | 'bento' | 'link';
  bookmarked?: boolean;
  userVote?: number;
}

export interface Comment {
  id: string;
  author: {
    username: string;
    displayName: string;
    avatar: string;
  };
  content: string;
  timestamp: string;
  votes: number;
  userVote?: number;
}

export interface TrendingTopic {
  id: string;
  category: string;
  hashtag: string;
  postsCount: string;
}

export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  total: number;
  hasNext: boolean;
}
