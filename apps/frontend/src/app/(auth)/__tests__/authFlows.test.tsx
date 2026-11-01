import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const http = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), patch: vi.fn() }));
vi.mock('@/lib/apiClient', () => ({ default: http }));
const search = vi.hoisted(() => ({ params: new URLSearchParams() }));
const nav = vi.hoisted(() => ({ push: vi.fn(), replace: vi.fn() }));
vi.mock('next/navigation', () => ({ useRouter: () => nav, useSearchParams: () => search.params }));
vi.mock('@react-oauth/google', () => ({ GoogleLogin: () => null }));

import { I18nProvider } from '@/lib/i18n';
import { useAuthStore } from '@/store/authStore';
import LoginPage from '../login/page';
import ResetPasswordPage from '../reset-password/page';
import VerifyEmailPage from '../verify-email/page';

const envelope = (data: unknown, message = 'Success') => ({ data: { success: true, message, data, timestamp: '2026-10-31T08:47:50.712Z' } });
const failure = (status: number, code: string, message: string, data: unknown = null, headers: Record<string, string> = {}) => ({
  response: { status, headers, data: { success: false, message, code, data, timestamp: '2026-10-31T08:47:50.835Z', requestId: 'a86e0a75-9072-4890-849b-4e7104ab72d5' } },
});

const wrap = (ui: React.ReactElement) => render(<I18nProvider defaultLocale="en">{ui}</I18nProvider>);
const signedOut = () => useAuthStore.setState({ user: null, isAuthenticated: false, isInitialized: true, accessToken: null });
const signedIn = (emailVerified = false) =>
  useAuthStore.setState({ user: { id: 1, email: 'inst@demo.suwasarana.test', phoneNumber: '', role: 'HOSPITAL_REQUESTER', emailVerified }, isAuthenticated: true, isInitialized: true, accessToken: 't' });

beforeEach(() => {
  vi.clearAllMocks();
  search.params = new URLSearchParams();
  signedOut();
});

describe('verify-email page', () => {
  it('verifies a link with POST /auth/verify-email {token}', async () => {
    search.params = new URLSearchParams('token=abc123');
    http.post.mockResolvedValueOnce(envelope(null, 'auth.emailVerification.verified'));
    wrap(<VerifyEmailPage />);
    expect(await screen.findByText('Email verified')).toBeInTheDocument();
    expect(http.post).toHaveBeenCalledWith('/auth/verify-email', { token: 'abc123' });
  });

  it('a failed link in the real error shape shows the translated message', async () => {
    search.params = new URLSearchParams('token=old');
    http.post.mockRejectedValueOnce(failure(400, 'auth.emailVerification.invalid', 'This verification link is invalid or has expired'));
    wrap(<VerifyEmailPage />);
    expect(await screen.findByText(/verification link is not valid or has expired/i)).toBeInTheDocument();
  });

  it('signed out: no resend button, only "sign in to get a new link"', async () => {
    wrap(<VerifyEmailPage />);
    expect(await screen.findByRole('link', { name: 'Sign in to get a new verification link.' })).toHaveAttribute('href', '/login');
    expect(screen.queryByRole('button', { name: /new link/i })).not.toBeInTheDocument();
  });

  it('signed in: resend with POST /auth/resend-verification and no body', async () => {
    signedIn(false);
    http.post.mockResolvedValueOnce(envelope(null, 'auth.emailVerification.sent'));
    const user = userEvent.setup();
    wrap(<VerifyEmailPage />);
    await user.click(await screen.findByRole('button', { name: 'Send a new link' }));
    expect(http.post).toHaveBeenCalledWith('/auth/resend-verification');
    expect(await screen.findByText(/new link is on its way/i)).toBeInTheDocument();
  });

  it('shows the cool-down text from the server code', async () => {
    signedIn(false);
    http.post.mockRejectedValueOnce(failure(429, 'auth.emailVerification.cooldown', 'Please wait before requesting another email', { retryAfterSeconds: 60 }, { 'retry-after': '60' }));
    const user = userEvent.setup();
    wrap(<VerifyEmailPage />);
    await user.click(await screen.findByRole('button', { name: 'Send a new link' }));
    expect(await screen.findByText(/wait a little before asking again.*Try again in 60 seconds/i)).toBeInTheDocument();
  });
});

describe('reset-password page', () => {
  it('posts the link token to /auth/reset-password/token', async () => {
    search.params = new URLSearchParams('token=reset-token');
    http.post.mockResolvedValueOnce(envelope(null, 'auth.password.updated'));
    const user = userEvent.setup();
    wrap(<ResetPasswordPage />);
    await user.type(await screen.findByLabelText('New password'), 'Brand-New-Pass-1');
    await user.type(screen.getByLabelText('Confirm password'), 'Brand-New-Pass-1');
    await user.click(screen.getByRole('button', { name: 'Update password' }));
    await waitFor(() => expect(http.post).toHaveBeenCalledWith('/auth/reset-password/token', { token: 'reset-token', newPassword: 'Brand-New-Pass-1' }));
  });

  it('a field code from the server is mapped onto the password field', async () => {
    search.params = new URLSearchParams('token=bad');
    http.post.mockRejectedValueOnce(failure(400, 'validation.failed', 'Validation failed', { newPassword: 'auth.password.common' }));
    const user = userEvent.setup();
    wrap(<ResetPasswordPage />);
    await user.type(await screen.findByLabelText('New password'), 'password1234');
    await user.type(screen.getByLabelText('Confirm password'), 'password1234');
    await user.click(screen.getByRole('button', { name: 'Update password' }));
    expect(await screen.findByText('This password is too common. Choose a different one.')).toBeInTheDocument();
  });
});

describe('login page', () => {
  it('sends {username, password} and stores the new profile fields from the real AuthResponse', async () => {
    http.post.mockResolvedValueOnce(
      envelope({
        accessToken: 'jwt', userId: 31, role: 'DONOR', email: 'donor1@demo.suwasarana.test', phoneNumber: '+94771234567', verificationStatus: 'VERIFIED',
        firstName: 'Dinesh', lastName: 'W', emailVerified: true, preferredLanguage: 'si',
      })
    );
    const user = userEvent.setup();
    wrap(<LoginPage />);
    await user.type(await screen.findByLabelText('Email address'), 'donor1@demo.suwasarana.test');
    await user.type(screen.getByLabelText('Password'), 'Demo-Pass-12345');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));
    await waitFor(() => expect(http.post).toHaveBeenCalledWith('/auth/login', { username: 'donor1@demo.suwasarana.test', password: 'Demo-Pass-12345' }));
    await waitFor(() => expect(useAuthStore.getState().user).toMatchObject({ id: 31, firstName: 'Dinesh', emailVerified: true, preferredLanguage: 'si' }));
  });

  it('wrong password: the dotted server code gives the translated message', async () => {
    http.post.mockRejectedValueOnce(failure(401, 'auth.credentials.invalid', 'Invalid credentials'));
    const user = userEvent.setup();
    wrap(<LoginPage />);
    await user.type(await screen.findByLabelText('Email address'), 'a@b.lk');
    await user.type(screen.getByLabelText('Password'), 'wrong-password');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(await screen.findByText('The email or password is incorrect. Please check and try again.')).toBeInTheDocument();
  });

  it('locked and suspended accounts show their own messages, the lock-out with the wait time', async () => {
    http.post.mockRejectedValueOnce(failure(429, 'auth.account.locked', 'Account temporarily locked after too many failed attempts', { retryAfterSeconds: 900 }, { 'retry-after': '900' }));
    const user = userEvent.setup();
    wrap(<LoginPage />);
    await user.type(await screen.findByLabelText('Email address'), 'a@b.lk');
    await user.type(screen.getByLabelText('Password'), 'x');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(await screen.findByText(/temporarily locked.*Try again in 15 minutes/i)).toBeInTheDocument();

    http.post.mockRejectedValueOnce(failure(403, 'auth.account.suspended', 'This account is suspended'));
    await user.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(await screen.findByText(/suspended by an administrator/i)).toBeInTheDocument();
  });
});
