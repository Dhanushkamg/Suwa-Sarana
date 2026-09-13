import { create } from 'zustand';
import { AuthState, User } from '@/types';
import axios from 'axios';
import { API_BASE_URL } from '@/lib/constants';

/**
 * Authentication store — access token is held in memory ONLY.
 *
 * Security rationale:
 * - localStorage is readable by any JavaScript on the page (XSS risk).
 * - The short-lived access token (15 min) is stored only in React memory.
 * - The long-lived refresh token is stored in an HttpOnly cookie (set by the backend),
 *   making it invisible to JavaScript entirely.
 * - On page reload, the apiClient's 401 interceptor will transparently refresh
 *   the access token using the refresh token cookie.
 */
export const useAuthStore = create<AuthState>()((set, get) => ({
  accessToken: null,
  user: null,
  isAuthenticated: false,

  login: (token: string, user: User) => {
    set({ accessToken: token, user, isAuthenticated: true });
  },

  setAccessToken: (token: string) => {
    set({ accessToken: token });
  },

  logout: async () => {
    try {
      // Invalidate the server-side refresh token.
      // The refresh token HttpOnly cookie is sent automatically via withCredentials.
      await axios.post(
        `${API_BASE_URL}/auth/logout`,
        {},
        {
          withCredentials: true,
          headers: get().accessToken
            ? { Authorization: `Bearer ${get().accessToken}` }
            : {},
        }
      );
    } catch {
      // Even if the server call fails (e.g. network error, already expired),
      // we clear local state. The refresh token will expire naturally on the server.
    } finally {
      set({ accessToken: null, user: null, isAuthenticated: false });
    }
  },
}));

