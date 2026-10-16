export interface GrowthProgress {
  experience: number;
  level: number;
  levelStartExperience: number;
  nextLevelExperience: number | null;
  experienceInLevel: number;
  experienceToNextLevel: number;
  progressPercent: number;
  maxLevel: boolean;
  badgeStage: number;
  ruleVersion: string;
  provisional: boolean;
}

export type SearchKind = "posts" | "communities" | "users";
export interface LumiTask { id: string; commentId: string; status: string; message: string; replyId: string | null; retryable: boolean }
export interface LumiStatus { enabled: boolean; name: string; webSearchAvailable: boolean; tasks: LumiTask[] }
export interface SearchHit { id: string; kind: SearchKind; title: string; subtitle: string; snippet?: string; image?: string; url: string }
export interface SearchGroup { kind: SearchKind; items: SearchHit[]; hasMore: boolean; unavailable: boolean }
export interface SearchSuggestions { groups: SearchGroup[]; hint: string }
export interface SearchSlice { kind: SearchKind; items: SearchHit[]; nextCursor: string | null; hasMore: boolean; windowLimited: boolean; hint: string }

export interface GrowthOverview {
  personal: GrowthProgress | null;
  community: GrowthProgress | null;
  communitySlug: string | null;
  rewardsEnabled: boolean;
  ruleVersion: string;
  provisional: boolean;
}

export interface GrowthLedgerEntry {
  id: string;
  rewardId: string;
  sourceKey: string;
  communityId: string;
  action: string;
  contentType: string;
  contentId: string;
  rewardDate: string;
  delta: number;
  operation: string;
  reason: string;
  ruleVersion: string;
  createdAt: string;
}

export interface User {
  accountType?: "HUMAN" | "BOT";
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
  growth?: GrowthProgress | null;
}

export interface FollowUser {
  id: string;
  username: string;
  displayName: string;
  avatar?: string;
  bio?: string;
  isFollowing?: boolean;
}

export interface Author {
  accountType?: "HUMAN" | "BOT";
  username: string;
  displayName: string;
  avatar?: string;
  personalGrowth?: GrowthProgress | null;
  communityGrowth?: GrowthProgress | null;
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
  image?: string | null;
  parentId?: string | null;
  replyToCommentId?: string | null;
  replyTo?: Author | null;
  timestamp: string;
  votes: number;
  userVote?: number;
}

export interface CommentFloorSummary { id: string; root: Comment | null; replyCount: number }
export interface CommentSlice<T> { items: T[]; nextCursor: string | null; hasNext: boolean; total: number }

export interface Moderator {
  id: string;
  username: string;
  displayName: string;
  initials?: string;
}

export interface Community {
  category?: string;
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
  type:
    | "LIKE_POST"
    | "COMMENT_POST"
    | "REPLY_COMMENT"
    | "FOLLOW_USER"
    | "SYSTEM"
    | string;
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
  interactionCount?: number;
  updatedAt?: string;
}

export interface NotificationSlice { items: Notification[]; nextCursor: string | null; hasNext: boolean }
export interface NotificationSummary { unread: Record<string, number>; unreadCount: number; revision: string }

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

export interface RankedPostSlice {
  items: Post[];
  nextCursor: string | null;
  hasMore: boolean;
  generatedAt: number;
  expiresAt: number;
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
