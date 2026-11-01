import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const http = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), patch: vi.fn(), delete: vi.fn(), put: vi.fn() }));
vi.mock('@/lib/apiClient', () => ({ default: http }));
const replace = vi.hoisted(() => vi.fn());
vi.mock('next/navigation', () => ({ useRouter: () => ({ push: replace, replace }) }));

import { I18nProvider } from '@/lib/i18n';
import { ToastProvider } from '@/components/ui/Toast';
import { useAuthStore } from '@/store/authStore';
import AccountPage from '../page';

const envelope = (data: unknown) => ({ data: { success: true, message: 'Success', data, timestamp: '2026-10-31T08:47:50.712Z' } });
const me = (over: Record<string, unknown> = {}) => ({
  id: 31, email: 'donor1@demo.suwasarana.test', emailVerified: true, phoneNumber: '+94771234567', firstName: 'Dinesh', lastName: 'Wickramasinghe',
  role: 'DONOR', verificationStatus: 'VERIFIED', verificationReason: null, status: 'ACTIVE', preferredLanguage: 'en', authProvider: 'LOCAL',
  hasPassword: true, googleLinked: false, consentVersion: '2026-10', consentedAt: '2026-10-01T05:00:00Z', consentRequired: false, createdAt: '2026-09-01T05:00:00Z', ...over,
});

function renderPage() {
  return render(
    <I18nProvider defaultLocale="en">
      <ToastProvider>
        <AccountPage />
      </ToastProvider>
    </I18nProvider>
  );
}

// jsdom has no <dialog>.showModal(): open/close by attribute so the dialog content is reachable
HTMLDialogElement.prototype.showModal = function showModal(this: HTMLDialogElement) {
  this.setAttribute('open', '');
};
HTMLDialogElement.prototype.close = function close(this: HTMLDialogElement) {
  this.removeAttribute('open');
};

beforeEach(() => {
  vi.clearAllMocks();
  useAuthStore.setState({ user: { id: 31, email: 'donor1@demo.suwasarana.test', phoneNumber: '+94771234567', role: 'DONOR' }, isAuthenticated: true, isInitialized: true, accessToken: 't' });
});

describe('account page', () => {
  it('loads /users/me and saves only the changed fields', async () => {
    http.get.mockResolvedValueOnce(envelope(me()));
    http.patch.mockResolvedValueOnce(envelope(me({ firstName: 'Dinuka' })));
    const user = userEvent.setup();
    renderPage();

    const first = await screen.findByLabelText(/First name/i);
    expect(first).toHaveValue('Dinesh');
    await user.clear(first);
    await user.type(first, 'Dinuka');
    await user.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(http.patch).toHaveBeenCalledWith('/users/me', { firstName: 'Dinuka' }));
    expect(useAuthStore.getState().user?.firstName).toBe('Dinuka');
  });

  it('shows server field errors in the form language', async () => {
    http.get.mockResolvedValueOnce(envelope(me()));
    http.patch.mockRejectedValueOnce({ response: { status: 400, data: { success: false, message: 'Validation failed', code: 'validation.failed', data: { firstName: 'auth.name.tooLong' } } } });
    const user = userEvent.setup();
    renderPage();
    const first = await screen.findByLabelText(/First name/i);
    await user.type(first, 'x');
    await user.click(screen.getByRole('button', { name: 'Save' }));
    expect(await screen.findByText('The name is too long (at most 100 characters).')).toBeInTheDocument();
  });

  it('an unconfirmed e-mail offers a new link, a changed terms version asks for agreement', async () => {
    http.get.mockResolvedValueOnce(envelope(me({ emailVerified: false, consentRequired: true })));
    http.post.mockResolvedValue(envelope(null));
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('Not confirmed yet');
    await user.click(screen.getByRole('button', { name: /Send a new link/i }));
    expect(http.post).toHaveBeenCalledWith('/auth/resend-verification');
    expect(screen.getByRole('button', { name: 'I agree' })).toBeInTheDocument();
  });

  it('a Google-only account has no password section and an administrator cannot delete the account', async () => {
    http.get.mockResolvedValueOnce(envelope(me({ role: 'ADMIN', hasPassword: false, authProvider: 'GOOGLE', googleLinked: true })));
    renderPage();
    await screen.findByText(/there is no password on this account/i);
    expect(screen.queryByRole('button', { name: 'Change password' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Delete account' })).toBeDisabled();
  });

  it('deleting asks for the password, then signs out locally', async () => {
    http.get.mockResolvedValueOnce(envelope(me()));
    http.delete.mockResolvedValueOnce(envelope(null));
    const user = userEvent.setup();
    renderPage();
    await user.click(await screen.findByRole('button', { name: 'Delete account' }));
    await user.type(await screen.findByLabelText('Password'), 'Demo-Pass-12345');
    await user.click(screen.getByRole('button', { name: 'Delete my account' }));
    await waitFor(() => expect(http.delete).toHaveBeenCalledWith('/users/me', { data: { password: 'Demo-Pass-12345' } }));
    await waitFor(() => expect(replace).toHaveBeenCalledWith('/'));
    expect(useAuthStore.getState().isAuthenticated).toBe(false);
  });
});
