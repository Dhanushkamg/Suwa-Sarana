import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const http = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }));
vi.mock('@/lib/apiClient', () => ({ default: http }));
const push = vi.hoisted(() => vi.fn());
vi.mock('next/navigation', () => ({ useRouter: () => ({ push, replace: push }) }));

import { I18nProvider } from '@/lib/i18n';
import { ToastProvider } from '@/components/ui/Toast';
import { useAuthStore } from '@/store/authStore';
import { resetDistrictMetaCache } from '@/lib/api/meta';
import NewBloodRequestPage from '../page';

const envelope = (data: unknown) => ({ data: { success: true, message: 'Success', data, timestamp: '2026-10-31T08:47:50.712Z' } });

/** Real shape of POST /api/requests/ai-draft when the text did not say which district / how many units. */
const partialDraft = {
  approximateCoordinates: false, coordinateSource: null, district: null, extractedSuccessfully: true, hospitalName: 'Karapitiya Teaching Hospital',
  latitude: null, longitude: null, message: 'Draft ready.', missingFields: ['unitsNeeded', 'district'],
  needsConfirmation: { patientBloodType: true, unitsNeeded: true, urgency: false, hospitalName: true, district: true, coordinates: false },
  patientBloodType: 'O+', source: 'llm', unitsNeeded: null, urgency: 'URGENT', warnings: [],
};

function renderPage() {
  return render(
    <I18nProvider defaultLocale="en">
      <ToastProvider>
        <NewBloodRequestPage />
      </ToastProvider>
    </I18nProvider>
  );
}

async function draftFrom(user: ReturnType<typeof userEvent.setup>, draft: unknown) {
  http.post.mockResolvedValueOnce(envelope(draft));
  await user.type(screen.getByRole('textbox'), 'need blood at Karapitiya');
  await user.click(screen.getByRole('button', { name: /Extract/i }));
  await screen.findByText('Requisition Details Review');
}

beforeEach(() => {
  vi.clearAllMocks();
  resetDistrictMetaCache();
  http.get.mockRejectedValue(new Error('offline')); // /meta/districts: the built-in list is used
  useAuthStore.setState({ user: { id: 1, email: 'r@x.lk', phoneNumber: '', role: 'REQUESTER', verificationStatus: 'VERIFIED' }, isAuthenticated: true, isInitialized: true, accessToken: 't' });
});

describe('AI request intake', () => {
  it('fills only what the draft found; unknown district and units stay empty (no silent defaults)', async () => {
    const user = userEvent.setup();
    renderPage();
    await draftFrom(user, partialDraft);

    expect(screen.getByLabelText(/Hospital Name/i)).toHaveValue('Karapitiya Teaching Hospital');
    expect(screen.getByLabelText(/Units Needed/i)).toHaveValue(null);
    expect(screen.getByLabelText(/^District/i)).toHaveValue('');
    expect(screen.getByLabelText(/Patient Blood Type/i)).toHaveValue('O+');
    // the server's flags show up as "please check" notes
    expect(screen.getAllByText(/Please check this/i).length).toBeGreaterThanOrEqual(2);
    // and the fields it could not find are listed
    expect(screen.getByText('We could not find these in your text:')).toBeInTheDocument();
  });

  it('refuses to submit until missing fields are filled and flagged ones are confirmed', async () => {
    const user = userEvent.setup();
    renderPage();
    await draftFrom(user, partialDraft);

    await user.click(screen.getByRole('button', { name: /Broadcast Blood Request/i }));
    expect(http.post).toHaveBeenCalledTimes(1); // only the draft call
    expect(screen.getByText(/Enter the number of units/i)).toBeInTheDocument();
    expect(screen.getByText('Choose the hospital\'s district.')).toBeInTheDocument();
    expect(screen.getAllByText(/Check this value and press/i).length).toBeGreaterThan(0); // blood type and hospital still unchecked
    expect(screen.getByLabelText(/Units Needed/i)).toHaveAttribute('aria-invalid', 'true');
  });

  it('submits once every field is filled and every flagged value was confirmed or edited', async () => {
    const user = userEvent.setup();
    renderPage();
    await draftFrom(user, partialDraft);

    await user.type(screen.getByLabelText(/Units Needed/i), '2'); // typing counts as checking it
    await user.selectOptions(screen.getByLabelText(/^District/i), 'Galle');
    for (const button of screen.getAllByRole('button', { name: 'This is correct' })) await user.click(button);

    http.post.mockResolvedValueOnce(envelope({ id: 99 }));
    await user.click(screen.getByRole('button', { name: /Broadcast Blood Request/i }));

    await waitFor(() => expect(http.post).toHaveBeenCalledTimes(2));
    expect(http.post).toHaveBeenLastCalledWith('/requests', {
      patientBloodType: 'O+', unitsNeeded: 2, urgency: 'URGENT', hospitalName: 'Karapitiya Teaching Hospital', district: 'Galle', latitude: 6.0535, longitude: 80.221,
    });
    await waitFor(() => expect(push).toHaveBeenCalledWith('/dashboard/requests'));
  });

  it('never selects CRITICAL for an unverified requester, even when the draft guessed it', async () => {
    useAuthStore.setState({ user: { id: 1, email: 'r@x.lk', phoneNumber: '', role: 'REQUESTER', verificationStatus: 'PENDING' } });
    const user = userEvent.setup();
    renderPage();
    await draftFrom(user, { ...partialDraft, urgency: 'CRITICAL', needsConfirmation: { ...partialDraft.needsConfirmation, urgency: true } });
    expect(screen.getByLabelText(/Urgency Level/i)).toHaveValue('');
  });
});
