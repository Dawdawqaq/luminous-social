import { Comment, Community, PageResponse, Post, User } from '../types';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api';

interface ApiEnvelope<T> {
  success: boolean;
  data: T;
  error?: {
    code: string;
    message: string;
  };
}

export interface AuthResponse {
  token: string;
  user: User;
}

export interface UploadResponse {
  id: string;
  url: string;
  originalName: string;
  sizeBytes: number;
}

let authToken = localStorage.getItem('luminous_token');

export function setAuthToken(token: string | null) {
  authToken = token;
  if (token) {
    localStorage.setItem('luminous_token', token);
  } else {
    localStorage.removeItem('luminous_token');
  }
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers);
  if (!(options.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json');
  }
  if (authToken) {
    headers.set('Authorization', `Bearer ${authToken}`);
  }

  const response = await fetch(`${API_BASE_URL}${path}`, { ...options, headers });
  const body = (await response.json().catch(() => null)) as ApiEnvelope<T> | null;
  if (!response.ok || !body?.success) {
    if (response.status === 401 || response.status === 403 || body?.error?.code === 'UNAUTHORIZED' || body?.error?.message?.includes('未登录')) {
      if (path !== '/auth/me') {
        window.dispatchEvent(new CustomEvent('auth:required'));
      }
      throw new Error('请先登录以继续操作');
    }
    throw new Error(body?.error?.message ?? '请求失败');
  }
  return body.data;
}

export const api = {
  register: (payload: { username: string; displayName: string; password: string }) =>
    request<AuthResponse>('/auth/register', { method: 'POST', body: JSON.stringify(payload) }),
  login: (payload: { username: string; password: string }) =>
    request<AuthResponse>('/auth/login', { method: 'POST', body: JSON.stringify(payload) }),
  me: () => request<User>('/auth/me'),
  updateProfile: (payload: { displayName?: string; bio?: string; avatar?: string; banner?: string }) => request<User>('/auth/me', { method: 'PUT', body: JSON.stringify(payload) }),
  userProfile: (username: string) => request<User>(`/users/${username}`),
  posts: (params: { filter?: string; community?: string; search?: string; page?: number; size?: number } = {}) => {
    const query = new URLSearchParams();
    Object.entries(params).forEach(([key, value]) => {
      if (value !== undefined && value !== '') query.set(key, String(value));
    });
    return request<PageResponse<Post>>(`/posts?${query.toString()}`);
  },
  post: (id: string) => request<Post>(`/posts/${id}`),
  createPost: (payload: { community: string; title: string; content: string; image?: string | null; isPublic: boolean }) =>
    request<Post>('/posts', { method: 'POST', body: JSON.stringify(payload) }),
  votePost: (id: string, value: -1 | 0 | 1) =>
    request<Post>(`/posts/${id}/vote`, { method: 'POST', body: JSON.stringify({ value }) }),
  bookmarkPost: (id: string) => request<Post>(`/posts/${id}/bookmark`, { method: 'POST' }),
  removeBookmark: (id: string) => request<Post>(`/posts/${id}/bookmark`, { method: 'DELETE' }),
  bookmarks: (page = 1, size = 10) => request<PageResponse<Post>>(`/bookmarks?page=${page}&size=${size}`),
  comments: (postId: string) => request<Comment[]>(`/posts/${postId}/comments`),
  addComment: (postId: string, content: string) =>
    request<Comment>(`/posts/${postId}/comments`, { method: 'POST', body: JSON.stringify({ content }) }),
  voteComment: (id: string, value: -1 | 0 | 1) =>
    request<Comment>(`/comments/${id}/vote`, { method: 'POST', body: JSON.stringify({ value }) }),
  communities: (search?: string) => request<Community[]>(search ? `/communities?search=${encodeURIComponent(search)}` : '/communities'),
  community: (slug: string) => request<Community>(`/communities/${slug}`),
  recommendedCommunities: () => request<Array<{ id: string; name: string; members: string; category: string; icon: string; isJoined: boolean }>>('/communities/recommended'),
  joinCommunity: (slug: string) => request<Community>(`/communities/${slug}/join`, { method: 'POST' }),
  leaveCommunity: (slug: string) => request<Community>(`/communities/${slug}/join`, { method: 'DELETE' }),
  createCommunity: (payload: { slug: string; name: string; category: string; description: string }) => 
    request<Community>('/communities', { method: 'POST', body: JSON.stringify(payload) }),
  updateCommunity: (slug: string, payload: { name: string; iconUrl?: string; bannerUrl?: string; description: string; rules: string[] }) =>
    request<Community>(`/communities/${slug}`, { method: 'PUT', body: JSON.stringify(payload) }),
  uploadImage: (file: File) => {
    const form = new FormData();
    form.set('file', file);
    return request<UploadResponse>('/uploads/images', { method: 'POST', body: form });
  },
  followUser: (username: string) => request<User>(`/users/${username}/follow`, { method: 'POST' }),
  unfollowUser: (username: string) => request<User>(`/users/${username}/follow`, { method: 'DELETE' }),
  searchUsers: (search?: string) => request<User[]>(search ? `/users?search=${encodeURIComponent(search)}` : '/users')
};
