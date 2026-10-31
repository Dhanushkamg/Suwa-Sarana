import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { I18nProvider } from '@/lib/i18n';
import apiClient from '@/lib/apiClient';
import DonorFaqChatbot from '../DonorFaqChatbot';
import { buildHistory, CHAT_STORAGE_KEY, type ChatMessage } from '../chatState';

vi.mock('@/lib/apiClient', () => ({ default: { post: vi.fn() } }));
const post = vi.mocked(apiClient.post);

const reply = (data: Record<string, unknown>) => ({ data: { success: true, data: { answer: 'An answer', ...data } } });

async function openChat() {
  const user = userEvent.setup();
  render(
    <I18nProvider defaultLocale="en">
      <DonorFaqChatbot />
    </I18nProvider>
  );
  await user.click(screen.getByRole('button', { name: 'Eligibility AI Assistant' }));
  return user;
}

const box = () => screen.getByRole('textbox');

async function ask(user: ReturnType<typeof userEvent.setup>, text: string) {
  await user.type(box(), text);
  await user.keyboard('{Enter}');
}

beforeEach(() => {
  post.mockReset();
  sessionStorage.clear();
});

describe('DonorFaqChatbot', () => {
  it('shows the Verified source badge only when the answer is grounded', async () => {
    post.mockResolvedValueOnce(reply({ answer: 'Ungrounded reply', grounded: false })).mockResolvedValueOnce(reply({ answer: 'Grounded reply', grounded: true }));
    const user = await openChat();

    await ask(user, 'first');
    expect(await screen.findByText('Ungrounded reply')).toBeInTheDocument();
    expect(screen.queryByText('Verified source')).not.toBeInTheDocument();

    await ask(user, 'second');
    expect(await screen.findByText('Grounded reply')).toBeInTheDocument();
    expect(screen.getAllByText('Verified source')).toHaveLength(1);
  });

  it('never claims NBTS grounding statically', async () => {
    await openChat();
    expect(screen.queryByText(/NBTS Grounded/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/based solely/i)).not.toBeInTheDocument();
    expect(screen.queryByText('Verified source')).not.toBeInTheDocument();
  });

  it('renders cited sources in a collapsible disclosure with link, review date, status and notices', async () => {
    post.mockResolvedValueOnce(
      reply({
        answer: 'Weight and age answer',
        grounded: true,
        language: 'en',
        needsConfirmation: true,
        disclaimer: 'The Medical Officer decides.',
        followUps: ['How often can I donate?'],
        sources: [
          { id: 's1', title: 'NBTS eligibility guide', url: 'https://example.org/guide', lastReviewed: '2026-03-01', status: 'VERIFIED' },
          { id: 's2', title: 'Tattoo rule', url: 'javascript:alert(1)', lastReviewed: '2026-02-01', status: 'CONFLICT' },
        ],
      })
    );
    const user = await openChat();
    await ask(user, 'age and weight?');

    await screen.findByText('Weight and age answer');
    const details = screen.getByText('Show 2 sources').closest('details')!;
    expect(details).not.toHaveAttribute('open');

    const link = within(details).getByRole('link', { name: /NBTS eligibility guide/ });
    expect(link).toHaveAttribute('href', 'https://example.org/guide');
    expect(link).toHaveAttribute('rel', expect.stringContaining('noopener'));
    // an unsafe URL is shown as plain text, not a link
    expect(within(details).queryByRole('link', { name: /Tattoo rule/ })).not.toBeInTheDocument();
    expect(within(details).getByText('Tattoo rule')).toBeInTheDocument();

    expect(within(details).getAllByText(/Last reviewed/)).toHaveLength(2);
    expect(within(details).getByText('Verified')).toBeInTheDocument();
    expect(within(details).getByText('Sources disagree')).toBeInTheDocument();

    expect(screen.getByText(/confirm this with the staff at your blood bank/i)).toBeInTheDocument();
    expect(screen.getByText('The Medical Officer decides.')).toBeInTheDocument();
    expect(screen.getByText('Reply language: English')).toBeInTheDocument();
    // the follow-up chip replaces the starter questions
    expect(screen.getByRole('button', { name: 'How often can I donate?' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Tattoo & Piercing' })).not.toBeInTheDocument();
  });

  it('handles a 429 with Retry-After: explains the wait and disables retry until it is over', async () => {
    post.mockRejectedValueOnce({ response: { status: 429, headers: { 'retry-after': '7' } } });
    const user = await openChat();
    await ask(user, 'tattoo?');

    expect(await screen.findByText(/wait 7 seconds/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Try again in \d+s/ })).toBeDisabled();
    // typing stays possible
    expect(box()).toBeEnabled();
  });

  it('offers a working retry after a server error', async () => {
    post.mockRejectedValueOnce({ response: { status: 503, headers: {} } }).mockResolvedValueOnce(reply({ answer: 'Recovered' }));
    const user = await openChat();
    await ask(user, 'hello');

    await user.click(await screen.findByRole('button', { name: 'Try again' }));
    expect(await screen.findByText('Recovered')).toBeInTheDocument();
    expect(post).toHaveBeenCalledTimes(2);
    // the retried question is not sent as its own history, and appears once
    expect(post.mock.calls[1][1]).toMatchObject({ question: 'hello', history: [] });
    expect(screen.getAllByText('hello')).toHaveLength(1);
  });

  it('says so when offline', async () => {
    post.mockRejectedValueOnce(new Error('Network Error'));
    const user = await openChat();
    await ask(user, 'hello');
    expect(await screen.findByText(/seem to be offline/i)).toBeInTheDocument();
  });

  it('sends at most 6 earlier messages as history, plus the fields of the contract', async () => {
    post.mockResolvedValue(reply({ answer: 'ok' }));
    const user = await openChat();
    for (let i = 1; i <= 5; i++) await ask(user, `question ${i}`);
    await screen.findAllByText('ok');

    expect(post).toHaveBeenCalledTimes(5);
    const [path, body] = post.mock.calls[4] as [string, { question: string; history: { role: string; content: string }[]; language: string; replyStyle: string }];
    expect(path).toBe('/faq/ask');
    expect(body.question).toBe('question 5');
    expect(body.history).toHaveLength(6);
    expect(body.history[0]).toEqual({ role: 'user', content: 'question 2' });
    expect(body.history[5]).toEqual({ role: 'assistant', content: 'ok' });
    expect(body.language).toBe('auto');
    expect(body.replyStyle).toBe('auto');
  });

  it('maps the reply style toggle to replyStyle', async () => {
    post.mockResolvedValue(reply({ answer: 'ok' }));
    const user = await openChat();
    await user.click(screen.getByText(/Sinhala reply style/));
    await user.click(screen.getByLabelText('Singlish (English letters)'));
    await ask(user, 'mama blood denna puluwanda?');
    await screen.findByText('ok');
    expect(post.mock.calls[0][1]).toMatchObject({ replyStyle: 'romanised' });
  });

  it('Enter sends, Shift+Enter adds a line, and the input stops at 500 characters with a counter', async () => {
    post.mockResolvedValue(reply({ answer: 'ok' }));
    const user = await openChat();
    await user.type(box(), 'line one{Shift>}{Enter}{/Shift}line two');
    expect(post).not.toHaveBeenCalled();
    expect(box()).toHaveValue('line one\nline two');
    expect(screen.getByTestId('char-counter')).toHaveTextContent('17/500');
    expect(box()).toHaveAttribute('maxlength', '500');
    await user.keyboard('{Enter}');
    expect(post).toHaveBeenCalledTimes(1);
    expect(post.mock.calls[0][1]).toMatchObject({ question: 'line one\nline two' });
  });

  it('keeps the conversation in sessionStorage and clears it on request', async () => {
    post.mockResolvedValue(reply({ answer: 'stored reply' }));
    const user = await openChat();
    await ask(user, 'remember me');
    await screen.findByText('stored reply');
    expect(sessionStorage.getItem(CHAT_STORAGE_KEY)).toContain('remember me');

    await user.click(screen.getByRole('button', { name: 'Clear conversation' }));
    expect(screen.queryByText('stored reply')).not.toBeInTheDocument();
    expect(sessionStorage.getItem(CHAT_STORAGE_KEY)).toBeNull();
  });
});

describe('buildHistory', () => {
  const msg = (i: number, sender: 'user' | 'bot', extra: Partial<ChatMessage> = {}): ChatMessage => ({ id: String(i), sender, text: `m${i}`, ...extra });

  it('keeps only the last 6 real turns and skips errors', () => {
    const all = Array.from({ length: 10 }, (_, i) => msg(i, i % 2 === 0 ? 'user' : 'bot'));
    all.splice(5, 0, msg(99, 'bot', { text: '', error: { kind: 'server', question: 'q', at: 0 } }));
    const history = buildHistory(all);
    expect(history).toHaveLength(6);
    expect(history.map((h) => h.content)).toEqual(['m4', 'm5', 'm6', 'm7', 'm8', 'm9']);
    expect(history[0].role).toBe('user');
    expect(history[1].role).toBe('assistant');
  });

  it('cuts very long answers to the question limit', () => {
    const [item] = buildHistory([msg(1, 'bot', { text: 'x'.repeat(900) })]);
    expect(item.content).toHaveLength(500);
  });
});
