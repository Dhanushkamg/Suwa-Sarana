import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios';
import { API_BASE_URL } from './constants';
import { useAuthStore } from '@/store/authStore';
import { AuthData, setSessionHint, toUser, unwrap } from './auth';

const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
  withCredentials: true, // lets the HttpOnly refresh-token cookie travel with auth requests
});

// Attach the in-memory access token to every request.
apiClient.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken;
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

type RetriableRequest = InternalAxiosRequestConfig & { _retry?: boolean };

// One refresh at a time: every request that hits a 401 meanwhile waits for the same new token.
let refreshPromise: Promise<string> | null = null;

function refreshAccessToken(): Promise<string> {
  if (!refreshPromise) {
    refreshPromise = axios
      .post(`${API_BASE_URL}/auth/refresh`, {}, { withCredentials: true })
      .then((res) => {
        const auth = unwrap<AuthData>(res.data);
        const { login } = useAuthStore.getState();
        login(auth.accessToken, toUser(auth));
        return auth.accessToken;
      })
      .finally(() => {
        refreshPromise = null;
      });
  }
  return refreshPromise;
}

function signOutLocally() {
  setSessionHint(false);
  useAuthStore.setState({ accessToken: null, user: null, isAuthenticated: false });
  // Public pages (landing, camps...) work signed out; only the dashboard needs a login.
  if (window.location.pathname.startsWith('/dashboard')) {
    window.location.href = '/login';
  }
}

apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const original = error.config as RetriableRequest | undefined;
    const isAuthCall = original?.url?.includes('/auth/') ?? false;

    // A 401 on /auth/* means "wrong credentials" etc., not "token expired" — let the form show the message.
    if (error.response?.status !== 401 || !original || original._retry || isAuthCall) {
      return Promise.reject(error);
    }
    original._retry = true;

    try {
      const token = await refreshAccessToken();
      original.headers.Authorization = `Bearer ${token}`;
      return apiClient(original);
    } catch {
      signOutLocally();
      return Promise.reject(error);
    }
  }
);

export default apiClient;
