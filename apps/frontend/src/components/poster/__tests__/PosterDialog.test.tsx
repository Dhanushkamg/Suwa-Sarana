import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { I18nProvider } from '@/lib/i18n';
import { ToastProvider } from '@/components/ui/Toast';
import { PosterCopyError, requestPosterCopy } from '@/lib/api/poster';
import { PosterDialog, type PosterDialogProps } from '../PosterDialog';
import { fixtureFacts } from '../fixtures';

vi.mock('@/lib/api/poster', async (importOriginal) => {
  const original = await importOriginal<typeof import('@/lib/api/poster')>();
  return { ...original, requestPosterCopy: vi.fn() };
});
const copyRequest = vi.mocked(requestPosterCopy);

beforeAll(() => {
  // jsdom has no modal <dialog> support
  HTMLDialogElement.prototype.showModal ??= function showModal(this: HTMLDialogElement) {
    this.setAttribute('open', '');
  };
  HTMLDialogElement.prototype.close ??= function close(this: HTMLDialogElement) {
    this.removeAttribute('open');
  };
});

beforeEach(() => {
  copyRequest.mockReset();
  copyRequest.mockRejectedValue(new PosterCopyError('unavailable'));
});

function open(props: Partial<PosterDialogProps> = {}) {
  return render(
    <I18nProvider defaultLocale="en">
      <ToastProvider>
        <PosterDialog
          onClose={() => {}}
          facts={fixtureFacts('URGENT_REQUEST', 'en')}
          shareUrl="https://suwasarana.lk/share/abc"
          initialTemplate="URGENT_REQUEST"
          verified
          {...props}
        />
      </ToastProvider>
    </I18nProvider>
  );
}

const downloadButton = () => screen.getByRole('button', { name: 'Download PNG' });

describe('PosterDialog guardrails', () => {
  it('blocks urgent-request export for an unverified requester', () => {
    open({ verified: false });
    expect(screen.getByText('Verification needed for this poster')).toBeInTheDocument();
    expect(downloadButton()).toBeDisabled();
    // sharing links are hidden too
    expect(screen.queryByText('WhatsApp')).not.toBeInTheDocument();
  });

  it('allows urgent-request export for a verified requester', () => {
    open({ verified: true });
    expect(screen.queryByText('Verification needed for this poster')).not.toBeInTheDocument();
    expect(downloadButton()).toBeEnabled();
    expect(screen.getByText('WhatsApp')).toBeInTheDocument();
  });

  it('blocks export when the backend answers 422 poster.unverified_requester', async () => {
    copyRequest.mockRejectedValue(new PosterCopyError('unverified'));
    open({ verified: true });
    expect(await screen.findByText('Verification needed for this poster')).toBeInTheDocument();
    expect(downloadButton()).toBeDisabled();
  });

  it('lets an unverified organiser export a camp poster, with a notice about the mark', async () => {
    const user = userEvent.setup();
    open({ verified: false, facts: fixtureFacts('CAMP', 'en'), initialTemplate: 'CAMP', templates: ['CAMP', 'AWARENESS'] });
    expect(screen.getByText(/carry an Unverified mark/)).toBeInTheDocument();
    expect(downloadButton()).toBeEnabled();
    // switching to the urgent template is not offered for camps
    expect(screen.queryByRole('tab', { name: 'Urgent request' })).not.toBeInTheDocument();
    await user.click(screen.getByRole('tab', { name: 'Awareness' }));
    expect(downloadButton()).toBeEnabled();
  });

  it('has no field for patient names or details', () => {
    open();
    expect(screen.queryByLabelText(/patient/i)).not.toBeInTheDocument();
    const labels = screen.getAllByRole('textbox').map((el) => el.getAttribute('id'));
    expect(labels.length).toBe(4); // headline, short text, caption, hashtags only
  });
});

describe('PosterDialog wording', () => {
  it('starts with standard wording filled from the request data', () => {
    open();
    expect(screen.getByLabelText('Headline')).toHaveValue('3 units of O- blood needed');
  });

  it('uses the AI copy when it passes validation', async () => {
    copyRequest.mockResolvedValue({ headline: 'Please help: O- blood needed', body: 'A patient needs O- blood.', caption: 'Please share.', hashtags: ['#SuwaSarana'], usedLlm: true });
    open();
    await waitFor(() => expect(screen.getByLabelText('Headline')).toHaveValue('Please help: O- blood needed'));
    expect(screen.getByText(/Wording suggested by AI/)).toBeInTheDocument();
  });

  it('falls back to the standard wording when the AI invents a blood type', async () => {
    copyRequest.mockResolvedValue({ headline: 'A+ blood needed', body: 'Help.', caption: 'Share.', hashtags: [], usedLlm: true });
    open();
    await waitFor(() => expect(copyRequest).toHaveBeenCalled());
    await waitFor(() => expect(screen.getByText(/Standard wording/)).toBeInTheDocument());
    expect(screen.getByLabelText('Headline')).toHaveValue('3 units of O- blood needed');
  });

  it('keeps the organiser edits and counts characters', async () => {
    const user = userEvent.setup();
    open();
    const headline = screen.getByLabelText('Headline');
    await user.clear(headline);
    await user.type(headline, 'Help needed');
    expect(headline).toHaveValue('Help needed');
    expect(screen.getByText('11 / 80')).toBeInTheDocument();
  });

  it('prints the contact number only after the organiser ticks the box', async () => {
    const user = userEvent.setup();
    open({ contactPhone: '+94 71 000 0000' });
    expect(copyRequest).toHaveBeenCalledWith(expect.objectContaining({ facts: expect.not.objectContaining({ contactPhone: expect.anything() }) }));
    const box = screen.getByRole('checkbox', { name: 'Show my phone number on the poster' });
    expect(box).not.toBeChecked();
    await user.click(box);
    expect(box).toBeChecked();
  });
});
