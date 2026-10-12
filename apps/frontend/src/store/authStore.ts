import { create } from 'zustand';
import { AuthState, User } from '@/types';
import axios from 'axios';
import { API_BASE_URL } from '@/lib/constants';
import { AuthData, hasSessionHint, setSessionHint, toUser, unwrap } from '@/lib/auth';

/**
 * Authentication store — the access token lives in memory ONLY.
 *
 * - localStorage is readable by any script on the page (XSS), so the short-lived (15 min)
 *   access token is never stored there.
 * - The long-lived refresh token is an HttpOnly cookie set by the backend.
 * - On a full page load the store starts empty; `initialize()` silently trades the refresh
 *   cookie for a new access token so a reload does not sign the user out.
 */

let initPromise: Promise<void> | null = null;

export const useAuthStore = create<AuthState>()((set, get) => ({
  accessToken: null,
  user: null,
  isAuthenticated: false,
  isInitialized: false,

  login: (token: string, user: User) => {
    setSessionHint(true);
    set({ accessToken: token, user, isAuthenticated: true, isInitialized: true });
  },

  setAccessToken: (token: string) => {
    set({ accessToken: token });
  },

  // Safe to call from many components / twice in React StrictMode: only one refresh request is made
  // (refresh tokens rotate, so a second parallel call would fail and sign the user out).
  initialize: () => {
    if (get().isInitialized) return Promise.resolve();
    if (!initPromise) {
      initPromise = (async () => {
        try {
          if (hasSessionHint()) {
            const res = await axios.post(`${API_BASE_URL}/auth/refresh`, {}, { withCredentials: true });
            const auth = unwrap<AuthData>(res.data);
            set({ accessToken: auth.accessToken, user: toUser(auth), isAuthenticated: true });
          }
        } catch {
          setSessionHint(false); // cookie missing or expired — treat as signed out
        } finally {
          set({ isInitialized: true });
          initPromise = null;
        }
      })();
    }
    return initPromise;
  },

  logout: async () => {
    try {
      // The refresh-token cookie is sent automatically via withCredentials.
      await axios.post(
        `${API_BASE_URL}/auth/logout`,
        {},
        {
          withCredentials: true,
          headers: get().accessToken ? { Authorization: `Bearer ${get().accessToken}` } : {},
        }
      );
    } catch {
      // Even if the server call fails we clear local state; the refresh token expires on its own.
    } finally {
      setSessionHint(false);
      set({ accessToken: null, user: null, isAuthenticated: false });
    }
  },
}));
