import { FAQ_MAX_HISTORY, FAQ_MAX_QUESTION_LENGTH, type FaqAnswer, type FaqErrorKind, type FaqHistoryItem, type FaqReplyStyle } from '@/lib/api/faq';

/** Answer details kept with a bot message. Only what the UI needs to render it. */
export type StoredAnswer = Pick<FaqAnswer, 'grounded' | 'sources' | 'needsConfirmation' | 'disclaimer' | 'followUps' | 'language'>;

export interface ChatMessage {
  id: string;
  sender: 'user' | 'bot';
  text: string;
  /** Present on real answers (not on the welcome message or errors) */
  answer?: StoredAnswer;
  /** Present on failed requests: what went wrong and which question to retry */
  error?: { kind: FaqErrorKind; retryAfterSeconds?: number; question: string; at: number };
}

export const WELCOME_ID = 'welcome';
export const CHAT_STORAGE_KEY = 'suwa_faq_chat_v1';
/** Messages kept in the tab's storage (older ones are dropped) */
const MAX_STORED = 40;

// crypto.randomUUID is unavailable on non-HTTPS origins, so fall back to a simple id
export function makeId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    try {
      return crypto.randomUUID();
    } catch {
      // fall through
    }
  }
  return `${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

/**
 * The context sent with the next question: the last FAQ_MAX_HISTORY real turns (user questions and assistant answers),
 * never the welcome text or error bubbles. Each item is cut to the question limit so the request stays valid.
 */
export function buildHistory(messages: ChatMessage[]): FaqHistoryItem[] {
  return messages
    .filter((m) => m.id !== WELCOME_ID && !m.error && m.text.trim())
    .map<FaqHistoryItem>((m) => ({ role: m.sender === 'user' ? 'user' : 'assistant', content: m.text.slice(0, FAQ_MAX_QUESTION_LENGTH) }))
    .slice(-FAQ_MAX_HISTORY);
}

export interface StoredChat {
  messages: ChatMessage[];
  replyStyle: FaqReplyStyle;
}

const REPLY_STYLES: FaqReplyStyle[] = ['auto', 'script', 'romanised'];

/** Conversation of this browser tab (sessionStorage: gone when the tab closes). Never throws. */
export function loadChat(): StoredChat {
  const empty: StoredChat = { messages: [], replyStyle: 'auto' };
  try {
    const raw = sessionStorage.getItem(CHAT_STORAGE_KEY);
    if (!raw) return empty;
    const parsed = JSON.parse(raw) as Partial<StoredChat>;
    const messages = Array.isArray(parsed.messages)
      ? parsed.messages.filter((m): m is ChatMessage => !!m && typeof m.id === 'string' && typeof m.text === 'string' && (m.sender === 'user' || m.sender === 'bot') && !m.error)
      : [];
    return { messages, replyStyle: REPLY_STYLES.includes(parsed.replyStyle as FaqReplyStyle) ? (parsed.replyStyle as FaqReplyStyle) : 'auto' };
  } catch {
    return empty;
  }
}

export function saveChat(chat: StoredChat): void {
  try {
    const messages = chat.messages.filter((m) => !m.error && m.id !== WELCOME_ID).slice(-MAX_STORED);
    if (messages.length === 0 && chat.replyStyle === 'auto') sessionStorage.removeItem(CHAT_STORAGE_KEY);
    else sessionStorage.setItem(CHAT_STORAGE_KEY, JSON.stringify({ messages, replyStyle: chat.replyStyle }));
  } catch {
    // storage unavailable or full: the chat simply won't survive a reload
  }
}

/** The bubble shown when a request failed. Built here (not inline) because it stamps the time of the failure. */
export function errorMessage(kind: FaqErrorKind, question: string, retryAfterSeconds?: number): ChatMessage {
  return { id: makeId(), sender: 'bot', text: '', error: { kind, retryAfterSeconds, question, at: Date.now() } };
}
