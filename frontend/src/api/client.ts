import { notificationsChanged } from "../notification/notificationEvents";
import type { ProfilePresetCatalog } from "../profile/profilePresets";
import {
  OperationCoordinator,
  operationFingerprint,
  operationGroup,
} from "./operationCoordinator";
import {
  ApiResponse,
  PageResponse,
  Post,
  Comment,
  CommentFloorSummary,
  CommentSlice,
  Community,
  RecommendedCommunity,
  User,
  FollowUser,
  Notification,
  AdminUser,
  ScrollResult,
  RankedPostSlice,
  SearchKind,
  SearchSuggestions,
  SearchSlice,
  CheckInResponse,
  CheckInStats,
  GrowthOverview,
  GrowthLedgerEntry,
} from "../types";

const API_BASE = "/api";

export interface LumiChatTurn { id: string; question: string; answer: string | null; status: string; message: string | null; retryable: boolean; createdAt: string; revision: string; sessionId: string; }
export interface LumiChatPage { sessionId: string; enabled: boolean; maxLength: number; cooldownRemaining: number; turns: LumiChatTurn[]; hasMore: boolean; nextCursor: string | null; title: string; busy: boolean; activeSessionId: string | null; activeTurnId: string | null; }
export interface LumiConversation { id: string; title: string; activityAt: string; active: boolean; }
export interface LumiConversations { items: LumiConversation[]; hasMore: boolean; nextCursor: string | null; maxConversations: number; busy: boolean; activeSessionId: string | null; }
export const lumiChatApi = {
  read: (before?: string, signal?: AbortSignal, sessionId?: string) => request<LumiChatPage>(`/lumi/chat?${new URLSearchParams({ ...(before ? { before } : {}), ...(sessionId ? { sessionId } : {}) })}`, { signal }),
  conversations: (cursor?: string, signal?: AbortSignal) => request<LumiConversations>(`/lumi/chat/conversations${cursor ? `?cursor=${encodeURIComponent(cursor)}` : ""}`, { signal }),
  create: (requestKey: string) => request<LumiChatPage>("/lumi/chat/conversations", { method: "POST", body: JSON.stringify({ requestKey }) }),
  rename: (id: string, title: string) => request<void>(`/lumi/chat/conversations/${id}`, { method: "PUT", body: JSON.stringify({ title }) }),
  delete: (id: string) => request<void>(`/lumi/chat/conversations/${id}`, { method: "DELETE" }),
  send: (sessionId: string, requestKey: string, content: string) => request<LumiChatTurn>("/lumi/chat/messages", { method: "POST", body: JSON.stringify({ sessionId, requestKey, content }) }),
  retry: (id: string) => request<LumiChatTurn>(`/lumi/chat/messages/${id}/retry`, { method: "POST", body: "{}" }),
  stop: (id: string) => request<LumiChatTurn>(`/lumi/chat/messages/${id}/stop`, { method: "POST", body: "{}" }),
  watch: async (id: string, receive: (turn: LumiChatTurn) => void, signal: AbortSignal) => {
    const token = localStorage.getItem("luminous_token");
    const response = await fetch(`/api/lumi/chat/messages/${id}/stream`, { signal, headers: { Accept: "text/event-stream, application/json", ...(token ? { Authorization: `Bearer ${token}` } : {}) } });
    if (!response.ok || !response.body) throw new Error("实时连接中断，正在重新连接");
    const reader = response.body.getReader(); const decoder = new TextDecoder(); let buffer = "";
    try {
      while (true) {
        const { value, done } = await reader.read(); if (done) break;
        buffer += decoder.decode(value, { stream: true }).replace(/\r/g, "");
        if (buffer.length > 65536) throw new Error("实时响应格式异常");
        let end: number;
        while ((end = buffer.indexOf("\n\n")) >= 0) {
          const event = buffer.slice(0, end); buffer = buffer.slice(end + 2);
          if (!event.split("\n").some(line => line === "event:turn" || line === "event: turn")) continue;
          const data = event.split("\n").filter(line => line.startsWith("data:")).map(line => line.slice(5).trimStart()).join("\n");
          const turn: LumiChatTurn = JSON.parse(data);
          if (turn.id !== id || !/^\d+$/.test(turn.revision)) throw new Error("实时响应格式异常");
          if (!signal.aborted) receive(turn);
        }
      }
    } finally { await reader.cancel().catch(() => {}); reader.releaseLock(); }
  },
  reset: (sessionId: string) => request<LumiChatPage>("/lumi/chat/reset", { method: "POST", body: JSON.stringify({ sessionId }) }),
};

export class ApiError extends Error {
  code?: string;
  status?: number;
  retryAfterSeconds?: number;

  constructor(message: string, code?: string, status?: number, retryAfterSeconds?: number) {
    super(message);
    this.name = "ApiError";
    this.code = code;
    this.status = status;
    this.retryAfterSeconds = retryAfterSeconds;
  }
}

function operationStorage(): Storage | undefined {
  try {
    return globalThis.sessionStorage;
  } catch {
    return undefined;
  }
}
const writeOperations = new OperationCoordinator(operationStorage());

async function request<T>(
  endpoint: string,
  options: RequestInit = {},
): Promise<T> {
  const token = localStorage.getItem("luminous_token");
  const headers: Record<string, string> = {
    ...(options.headers as Record<string, string>),
  };

  if (!(options.body instanceof FormData)) {
    headers["Content-Type"] = "application/json";
  }

  if (token) {
    headers["Authorization"] = `Bearer ${token}`;
  }

  const method = (options.method || "GET").toUpperCase();
  if (
    ["POST", "PUT", "DELETE", "PATCH"].includes(method) &&
    !endpoint.startsWith("/auth/captcha") &&
    !headers["X-Idempotency-Key"]
  ) {
    const fingerprint = await operationFingerprint(
      token || "anonymous",
      endpoint,
      method,
      options.body,
      headers["X-Captcha-Proof"],
    );
    const group = await operationGroup(token || "anonymous", endpoint);
    return writeOperations.run(
      fingerprint,
      (key) =>
        sendRequest<T>(endpoint, options, {
          ...headers,
          "X-Idempotency-Key": key,
        }),
      group,
      // 评论成功后结束本次操作，允许用户下一次发布相同文字；不确定失败仍复用原标识。
      endpoint !== "/notifications/read-all" && !(method === "POST" && /^\/posts\/[^/]+\/comments$/.test(endpoint)),
      /^\/users\/[^/]+\/follow$/.test(endpoint),
    );
  }
  return sendRequest<T>(endpoint, options, headers);
}

async function sendRequest<T>(
  endpoint: string,
  options: RequestInit,
  headers: Record<string, string>,
): Promise<T> {
  let response: Response;
  try {
    response = await fetch(`${API_BASE}${endpoint}`, { ...options, headers });
  } catch (error) {
    if (error instanceof DOMException && error.name === "AbortError") throw error;
    throw new ApiError("网络连接中断，请重试。", "NETWORK_ERROR");
  }

  if (response.status === 204) {
    return {} as T;
  }

  const json: ApiResponse<T> = await response.json().catch(() => ({
    success: false,
    data: null as any,
    error: { code: "NETWORK_ERROR", message: "网络响应解析异常" },
    timestamp: new Date().toISOString(),
  }));

  if (!response.ok || !json.success) {
    throw new ApiError(
      json.error?.message || `请求失败 (${response.status})`,
      json.error?.code,
      response.status,
      Number(response.headers.get("Retry-After")) || undefined,
    );
  }

  return json.data;
}

export const searchApi = {
  suggest: (q: string, signal?: AbortSignal) => request<SearchSuggestions>(`/search/suggest?q=${encodeURIComponent(q)}`, { signal }),
  list: (q: string, type: SearchKind, cursor?: string, signal?: AbortSignal) => request<SearchSlice>(`/search?q=${encodeURIComponent(q)}&type=${type}&size=20${cursor ? `&cursor=${encodeURIComponent(cursor)}` : ""}`, { signal }),
};

export const lumiApi = {
  status: (postId: string | number, signal?: AbortSignal) => request<import("../types").LumiStatus>(`/lumi/posts/${postId}/tasks`, { signal }),
  retry: (id: string) => request<import("../types").LumiTask>(`/lumi/tasks/${id}/retry`, { method: "POST", body: JSON.stringify({}) }),
};

export const authApi = {
  login: (data: { username: string; password: string }, captchaProof: string) =>
    request<{ token: string; user: User }>("/auth/login", {
      method: "POST",
      headers: { "X-Captcha-Proof": captchaProof },
      body: JSON.stringify(data),
    }),

  register: (
    data: { username: string; displayName: string; password: string; avatarPresetId?: string },
    captchaProof: string,
  ) =>
    request<{ token: string; user: User }>("/auth/register", {
      method: "POST",
      headers: { "X-Captcha-Proof": captchaProof },
      body: JSON.stringify(data),
    }),

  me: () => request<User>("/auth/me"),

  updateProfile: (data: {
    displayName?: string;
    bio?: string;
    avatar?: string;
    banner?: string;
  }) =>
    request<User>("/auth/me", {
      method: "PUT",
      body: JSON.stringify(data),
    }),
};

export const userApi = {
  profilePresets: () => request<ProfilePresetCatalog>("/users/profile-presets"),
  listFollowers: (username: string, page = 1, size = 20) =>
    request<PageResponse<FollowUser>>(
      `/users/${encodeURIComponent(username)}/followers?page=${page}&size=${size}&identifierType=username`,
    ),

  listFollowing: (username: string, page = 1, size = 20) =>
    request<PageResponse<FollowUser>>(
      `/users/${encodeURIComponent(username)}/following?page=${page}&size=${size}&identifierType=username`,
    ),

  getProfile: (username: string) =>
    request<User>(`/users/${encodeURIComponent(username)}?identifierType=username`),

  search: (query?: string) =>
    request<User[]>(
      `/users${query ? `?search=${encodeURIComponent(query)}` : ""}`,
    ),

  follow: (username: string) =>
    request<User>(`/users/${encodeURIComponent(username)}/follow?identifierType=username`, {
      method: "POST",
    }),

  unfollow: (username: string) =>
    request<User>(`/users/${encodeURIComponent(username)}/follow?identifierType=username`, {
      method: "DELETE",
    }),
};

export const postApi = {
  ranking: (params: { type: "recommend" | "hot"; period?: "day" | "week"; community?: string; cursor?: string; size?: number }) => {
    const query = new URLSearchParams({ type: params.type });
    if (params.period) query.set("period", params.period);
    if (params.community) query.set("community", params.community);
    if (params.cursor) query.set("cursor", params.cursor);
    if (params.size) query.set("size", String(params.size));
    return request<RankedPostSlice>(`/posts/ranking?${query}`);
  },
  getCommentContext: (postId: string | number, commentId: string) => request<{floor: CommentFloorSummary; target: Comment}>(`/posts/${postId}/comments/${encodeURIComponent(commentId)}/context`),
  list: (
    params: {
      filter?: string;
      community?: string;
      search?: string;
      author?: string;
      page?: number;
      size?: number;
    } = {},
  ) => {
    const query = new URLSearchParams();
    if (params.filter) query.set("filter", params.filter);
    if (params.community) query.set("community", params.community);
    if (params.search) query.set("search", params.search);
    if (params.author) query.set("author", params.author);
    if (params.page) query.set("page", params.page.toString());
    if (params.size) query.set("size", params.size.toString());
    return request<PageResponse<Post>>(`/posts?${query.toString()}`);
  },

  getById: (id: string | number) => request<Post>(`/posts/${id}`),

  create: (data: {
    community: string;
    title: string;
    content: string;
    image?: string;
    type?: string;
    isPublic?: boolean;
  }) =>
    request<Post>("/posts", {
      method: "POST",
      body: JSON.stringify({ ...data, isPublic: data.isPublic ?? true }),
    }),

  vote: (id: string | number, value: number) =>
    request<Post>(`/posts/${id}/vote`, {
      method: "POST",
      body: JSON.stringify({ value }),
    }),

  bookmark: (id: string | number) =>
    request<Post>(`/posts/${id}/bookmark`, {
      method: "POST",
    }),

  removeBookmark: (id: string | number) =>
    request<Post>(`/posts/${id}/bookmark`, {
      method: "DELETE",
    }),

  getBookmarks: (params: { page?: number; size?: number } = {}) => {
    const query = new URLSearchParams();
    if (params.page) query.set("page", params.page.toString());
    if (params.size) query.set("size", params.size.toString());
    return request<PageResponse<Post>>(`/bookmarks?${query.toString()}`);
  },

  delete: (id: string | number) =>
    request<void>(`/posts/${id}`, {
      method: "DELETE",
    }),

  getComments: (postId: string | number) =>
    request<Comment[]>(`/posts/${postId}/comments`),

  getCommentFloors: (postId: string | number, cursor?: string | null) =>
    request<CommentSlice<CommentFloorSummary>>(`/posts/${postId}/comment-floors?size=10${cursor ? "&cursor=" + encodeURIComponent(cursor) : ""}`),

  getCommentReplies: (postId: string | number, rootId: string, cursor?: string | null) =>
    request<CommentSlice<Comment>>(`/posts/${postId}/comments/${rootId}/replies?size=5${cursor ? "&cursor=" + encodeURIComponent(cursor) : ""}`),

  createComment: (postId: string | number, content: string, image?: string, replyToCommentId?: string) =>
    request<Comment>(`/posts/${postId}/comments`, {
      method: "POST",
      body: JSON.stringify({ content, image, replyToCommentId }),
    }),

  voteComment: (id: string | number, value: number) =>
    request<Comment>(`/comments/${id}/vote`, {
      method: "POST",
      body: JSON.stringify({ value }),
    }),

  deleteComment: (id: string | number) =>
    request<void>(`/comments/${id}`, {
      method: "DELETE",
    }),
};

export const growthApi = {
  getOverview: (username: string, community?: string) =>
    request<GrowthOverview>(
      `/users/${encodeURIComponent(username)}/growth${community ? `?community=${encodeURIComponent(community)}` : ""}`,
    ),
  getLedger: (page = 1, size = 20) =>
    request<PageResponse<GrowthLedgerEntry>>(`/growth/ledger?page=${page}&size=${size}`),
};

export const communityApi = {
  posts: (slug: string, cursor?: string) => request<{ items: Post[]; nextCursor: string | null; hasMore: boolean }>(`/communities/${encodeURIComponent(slug)}/posts?size=10${cursor ? `&cursor=${encodeURIComponent(cursor)}` : ""}`),
  browse: (params: { search?: string; scope?: string; category?: string; cursor?: string; size?: number } = {}) => {
    const query = new URLSearchParams();
    Object.entries(params).forEach(([key, value]) => { if (value !== undefined && value !== "") query.set(key, String(value)); });
    return request<{ items: Community[]; nextCursor: string | null; hasNext: boolean }>(`/communities/browse?${query}`);
  },
  list: (search?: string) =>
    request<Community[]>(
      `/communities${search ? `?search=${encodeURIComponent(search)}` : ""}`,
    ),

  getRecommended: () =>
    request<RecommendedCommunity[]>("/communities/recommended"),

  getBySlug: (slug: string) =>
    request<Community>(`/communities/${encodeURIComponent(slug)}`),

  join: (slug: string) =>
    request<Community>(`/communities/${encodeURIComponent(slug)}/join`, {
      method: "POST",
    }),

  leave: (slug: string) =>
    request<Community>(`/communities/${encodeURIComponent(slug)}/join`, {
      method: "DELETE",
    }),

  create: (data: {
    slug: string;
    name: string;
    category: string;
    description: string;
  }) =>
    request<Community>("/communities", {
      method: "POST",
      body: JSON.stringify(data),
    }),

  update: (
    slug: string,
    data: {
      name?: string;
      iconUrl?: string;
      bannerUrl?: string;
      description?: string;
      rules?: string[];
    },
  ) =>
    request<Community>(`/communities/${encodeURIComponent(slug)}`, {
      method: "PUT",
      body: JSON.stringify(data),
    }),
};

export const notificationApi = {
  inbox: (type: string, cursor?: string | null) => {
    const query = new URLSearchParams({type, size: "20"});
    if(cursor) query.set("cursor",cursor);
    return request<import("../types").NotificationSlice>(`/notifications/inbox?${query}`);
  },
  summary: () => request<import("../types").NotificationSummary>("/notifications/summary"),
  getUnreadCount: (signal?: AbortSignal) =>
    request<{ unreadCount: number }>("/notifications/unread-count", { signal }),

  list: (params: { page?: number; size?: number; type?: string } = {}) => {
    const query = new URLSearchParams();
    if (params.page) query.set("page", params.page.toString());
    if (params.size) query.set("size", params.size.toString());
    if (params.type) query.set("type", params.type);
    return request<PageResponse<Notification>>(
      `/notifications?${query.toString()}`,
    );
  },

  markAsRead: (id: string | number, version?: number) =>
    request<void>(`/notifications/${id}/read${version ? `?version=${version}` : ""}`, {
      method: "PUT",
    }).then((result) => {
      notificationsChanged();
      return result;
    }),

  markAllAsRead: () =>
    request<{ markedCount: number }>("/notifications/read-all", {
      method: "PUT",
    }).then((result) => {
      notificationsChanged();
      return result;
    }),

  delete: (id: string | number) =>
    request<void>(`/notifications/${id}`, {
      method: "DELETE",
    }).then((result) => {
      notificationsChanged();
      return result;
    }),
};

export const uploadApi = {
  uploadImage: async (file: File) => {
    const formData = new FormData();
    formData.append("file", file);
    return request<{ url: string; filename?: string }>("/uploads/images", {
      method: "POST",
      body: formData,
    });
  },
};

export const adminApi = {
  listUsers: () => request<AdminUser[]>("/admin/users"),
  banUser: (username: string) =>
    request<User>(`/admin/users/${encodeURIComponent(username)}/ban`, {
      method: "POST",
    }),
  unbanUser: (username: string) =>
    request<User>(`/admin/users/${encodeURIComponent(username)}/ban`, {
      method: "DELETE",
    }),
};

export const feedApi = {
  getFeed: (
    params: { lastScore?: number; offset?: number; size?: number } = {},
  ) => {
    const query = new URLSearchParams();
    if (params.lastScore !== undefined)
      query.set("lastScore", params.lastScore.toString());
    if (params.offset !== undefined)
      query.set("offset", params.offset.toString());
    if (params.size !== undefined) query.set("size", params.size.toString());
    return request<ScrollResult<Post>>(`/feed?${query.toString()}`);
  },
};

export const checkInApi = {
  checkIn: (communitySlug: string) =>
    request<CheckInResponse>(`/checkin/${encodeURIComponent(communitySlug)}`, {
      method: "POST",
    }),

  getStats: (communitySlug: string, month?: string) =>
    request<CheckInStats>(
      `/checkin/${encodeURIComponent(communitySlug)}/stats${month ? `?month=${encodeURIComponent(month)}` : ""}`,
    ),
};

export interface CaptchaChallenge {
  challengeId: string;
  image: string;
  piece: string;
  width: number;
  height: number;
  pieceSize: number;
  pieceY: number;
  expiresIn: number;
}
// 顺序获取挑战，避免首次打开时并发响应设置不同浏览器会话 Cookie。
let challengeQueue: Promise<unknown> = Promise.resolve();
export const captchaApi = {
  challenge: (purpose: "login" | "register") => {
    const next = challengeQueue
      .catch(() => undefined)
      .then(() =>
        request<CaptchaChallenge>(`/auth/captcha?purpose=${purpose}`),
      );
    challengeQueue = next;
    return next;
  },
  verify: (data: {
    purpose: "login" | "register";
    challengeId: string;
    offset: number;
    durationMillis: number;
  }) =>
    request<{ proof: string; expiresIn: number }>("/auth/captcha/verify", {
      method: "POST",
      body: JSON.stringify(data),
    }),
};
