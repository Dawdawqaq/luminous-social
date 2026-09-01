import { ApiResponse, PageResponse, Post, Comment, Community, RecommendedCommunity, User, Notification, AdminUser, ScrollResult, CheckInResponse, CheckInStats } from '../types';

const API_BASE = '/api';

export class ApiError extends Error {
  code?: string;
  status?: number;

  constructor(message: string, code?: string, status?: number) {
    super(message);
    this.name = 'ApiError';
    this.code = code;
    this.status = status;
  }
}

function generateIdempotencyKey(): string {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) {
    return crypto.randomUUID();
  }
  return Math.random().toString(36).substring(2) + Date.now().toString(36);
}

async function request<T>(endpoint: string, options: RequestInit = {}): Promise<T> {
  const token = localStorage.getItem('luminous_token');
  const headers: Record<string, string> = {
    ...(options.headers as Record<string, string>),
  };

  if (!(options.body instanceof FormData)) {
    headers['Content-Type'] = 'application/json';
  }

  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  // 为写操作自动附加幂等 Token (如果调用方未显式设置)
  const method = (options.method || 'GET').toUpperCase();
  if (['POST', 'PUT', 'DELETE'].includes(method) && !headers['X-Idempotency-Key']) {
    headers['X-Idempotency-Key'] = generateIdempotencyKey();
  }

  const response = await fetch(`${API_BASE}${endpoint}`, {
    ...options,
    headers,
  });

  if (response.status === 204) {
    return {} as T;
  }

  const json: ApiResponse<T> = await response.json().catch(() => ({
    success: false,
    data: null as any,
    error: { code: 'NETWORK_ERROR', message: '网络响应解析异常' },
    timestamp: new Date().toISOString(),
  }));

  if (!response.ok || !json.success) {
    throw new ApiError(
      json.error?.message || `请求失败 (${response.status})`,
      json.error?.code,
      response.status
    );
  }

  return json.data;
}

export const authApi = {
  login: (data: { username: string; password: string }) =>
    request<{ token: string; user: User }>('/auth/login', {
      method: 'POST',
      body: JSON.stringify(data),
    }),

  register: (data: { username: string; displayName: string; password: string }) =>
    request<{ token: string; user: User }>('/auth/register', {
      method: 'POST',
      body: JSON.stringify(data),
    }),

  me: () => request<User>('/auth/me'),

  updateProfile: (data: { displayName?: string; bio?: string; avatar?: string; banner?: string }) =>
    request<User>('/auth/me', {
      method: 'PUT',
      body: JSON.stringify(data),
    }),
};

export const userApi = {
  getProfile: (username: string) => request<User>(`/users/${encodeURIComponent(username)}`),

  search: (query?: string) =>
    request<User[]>(`/users${query ? `?search=${encodeURIComponent(query)}` : ''}`),

  follow: (username: string) =>
    request<User>(`/users/${encodeURIComponent(username)}/follow`, {
      method: 'POST',
    }),

  unfollow: (username: string) =>
    request<User>(`/users/${encodeURIComponent(username)}/follow`, {
      method: 'DELETE',
    }),
};

export const postApi = {
  list: (params: { filter?: string; community?: string; search?: string; author?: string; page?: number; size?: number } = {}) => {
    const query = new URLSearchParams();
    if (params.filter) query.set('filter', params.filter);
    if (params.community) query.set('community', params.community);
    if (params.search) query.set('search', params.search);
    if (params.author) query.set('author', params.author);
    if (params.page) query.set('page', params.page.toString());
    if (params.size) query.set('size', params.size.toString());
    return request<PageResponse<Post>>(`/posts?${query.toString()}`);
  },

  getById: (id: string | number) => request<Post>(`/posts/${id}`),

  create: (data: { community: string; title: string; content: string; image?: string; type?: string; isPublic?: boolean }) =>
    request<Post>('/posts', {
      method: 'POST',
      body: JSON.stringify({ ...data, isPublic: data.isPublic ?? true }),
    }),

  vote: (id: string | number, value: number) =>
    request<Post>(`/posts/${id}/vote`, {
      method: 'POST',
      body: JSON.stringify({ value }),
    }),

  bookmark: (id: string | number) =>
    request<Post>(`/posts/${id}/bookmark`, {
      method: 'POST',
    }),

  removeBookmark: (id: string | number) =>
    request<Post>(`/posts/${id}/bookmark`, {
      method: 'DELETE',
    }),

  getBookmarks: (params: { page?: number; size?: number } = {}) => {
    const query = new URLSearchParams();
    if (params.page) query.set('page', params.page.toString());
    if (params.size) query.set('size', params.size.toString());
    return request<PageResponse<Post>>(`/bookmarks?${query.toString()}`);
  },

  delete: (id: string | number) =>
    request<void>(`/posts/${id}`, {
      method: 'DELETE',
    }),

  getComments: (postId: string | number) =>
    request<Comment[]>(`/posts/${postId}/comments`),

  createComment: (postId: string | number, content: string) =>
    request<Comment>(`/posts/${postId}/comments`, {
      method: 'POST',
      body: JSON.stringify({ content }),
    }),

  voteComment: (id: string | number, value: number) =>
    request<Comment>(`/comments/${id}/vote`, {
      method: 'POST',
      body: JSON.stringify({ value }),
    }),

  deleteComment: (id: string | number) =>
    request<void>(`/comments/${id}`, {
      method: 'DELETE',
    }),
};

export const communityApi = {
  list: (search?: string) =>
    request<Community[]>(`/communities${search ? `?search=${encodeURIComponent(search)}` : ''}`),

  getRecommended: () =>
    request<RecommendedCommunity[]>('/communities/recommended'),

  getBySlug: (slug: string) =>
    request<Community>(`/communities/${encodeURIComponent(slug)}`),

  join: (slug: string) =>
    request<Community>(`/communities/${encodeURIComponent(slug)}/join`, {
      method: 'POST',
    }),

  leave: (slug: string) =>
    request<Community>(`/communities/${encodeURIComponent(slug)}/join`, {
      method: 'DELETE',
    }),

  create: (data: { slug: string; name: string; category: string; description: string }) =>
    request<Community>('/communities', {
      method: 'POST',
      body: JSON.stringify(data),
    }),

  update: (slug: string, data: { name?: string; iconUrl?: string; bannerUrl?: string; description?: string; rules?: string[] }) =>
    request<Community>(`/communities/${encodeURIComponent(slug)}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    }),
};

export const notificationApi = {
  getUnreadCount: () =>
    request<{ unreadCount: number }>('/notifications/unread-count'),

  list: (params: { page?: number; size?: number; type?: string } = {}) => {
    const query = new URLSearchParams();
    if (params.page) query.set('page', params.page.toString());
    if (params.size) query.set('size', params.size.toString());
    if (params.type) query.set('type', params.type);
    return request<PageResponse<Notification>>(`/notifications?${query.toString()}`);
  },

  markAsRead: (id: string | number) =>
    request<void>(`/notifications/${id}/read`, {
      method: 'PUT',
    }),

  markAllAsRead: () =>
    request<{ markedCount: number }>('/notifications/read-all', {
      method: 'PUT',
    }),

  delete: (id: string | number) =>
    request<void>(`/notifications/${id}`, {
      method: 'DELETE',
    }),
};

export const uploadApi = {
  uploadImage: async (file: File) => {
    const formData = new FormData();
    formData.append('file', file);
    return request<{ url: string; filename?: string }>('/uploads/images', {
      method: 'POST',
      body: formData,
    });
  },
};

export const adminApi = {
  listUsers: () => request<AdminUser[]>('/admin/users'),
  banUser: (username: string) =>
    request<User>(`/admin/users/${encodeURIComponent(username)}/ban`, {
      method: 'POST',
    }),
  unbanUser: (username: string) =>
    request<User>(`/admin/users/${encodeURIComponent(username)}/ban`, {
      method: 'DELETE',
    }),
};

export const feedApi = {
  getFeed: (params: { lastScore?: number; offset?: number; size?: number } = {}) => {
    const query = new URLSearchParams();
    if (params.lastScore !== undefined) query.set('lastScore', params.lastScore.toString());
    if (params.offset !== undefined) query.set('offset', params.offset.toString());
    if (params.size !== undefined) query.set('size', params.size.toString());
    return request<ScrollResult<Post>>(`/feed?${query.toString()}`);
  },
};

export const checkInApi = {
  checkIn: (communitySlug: string) =>
    request<CheckInResponse>(`/checkin/${encodeURIComponent(communitySlug)}`, {
      method: 'POST',
    }),

  getStats: (communitySlug: string) =>
    request<CheckInStats>(`/checkin/${encodeURIComponent(communitySlug)}/stats`),
};
