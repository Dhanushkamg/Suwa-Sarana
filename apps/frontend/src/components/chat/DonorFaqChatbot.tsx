'use client';

import { useState, useRef, useEffect, useId, type KeyboardEvent } from 'react';
import { motion, useReducedMotion } from 'motion/react';
import { X, Send, Bot, ShieldCheck, Info, Loader2, Syringe, Scale, Hourglass, Trash2, Languages, TriangleAlert, WifiOff, RotateCw, ChevronDown, Check } from 'lucide-react';
import { askFaq, FAQ_MAX_QUESTION_LENGTH, FaqError, type FaqReplyStyle } from '@/lib/api/faq';
import { useI18n } from '@/lib/i18n';
import { buildHistory, errorMessage, loadChat, makeId, saveChat, type ChatMessage } from './chatState';
import { SourcesDisclosure } from './SourcesDisclosure';

/** Small "this answer cites a stored source" marker. Render only when the response said `grounded === true`. */
function GroundedBadge({ label }: { label: string }) {
  return (
    <span className="inline-flex items-center gap-1 rounded-full border border-success/30 bg-success-soft px-2 py-0.5 text-[11px] font-medium text-success-text">
      <ShieldCheck className="h-3 w-3" aria-hidden="true" />
      {label}
    </span>
  );
}

/** True while a full-screen menu or drawer has locked the page (landing menu, dashboard drawer): the assistant steps aside. */
function usePageObscured() {
  const [obscured, setObscured] = useState(false);
  useEffect(() => {
    const body = document.body;
    const read = () => setObscured(body.hasAttribute('data-nav-open') || body.style.overflow === 'hidden');
    read();
    const observer = new MutationObserver(read);
    observer.observe(body, { attributes: true, attributeFilter: ['data-nav-open', 'style'] });
    return () => observer.disconnect();
  }, []);
  return obscured;
}

/** Seconds left until `at + seconds`, ticking once a second; 0 when there is nothing to wait for. */
function useCountdown(at: number, seconds: number | undefined) {
  const total = seconds ?? 0;
  const [remaining, setRemaining] = useState(() => Math.max(0, Math.ceil((at + total * 1000 - Date.now()) / 1000)));
  useEffect(() => {
    if (total <= 0) return;
    const tick = () => setRemaining(Math.max(0, Math.ceil((at + total * 1000 - Date.now()) / 1000)));
    const timer = window.setInterval(tick, 1000);
    return () => window.clearInterval(timer);
  }, [at, total]);
  return total <= 0 ? 0 : remaining;
}

function ErrorBubble({ error, onRetry, disabled }: { error: NonNullable<ChatMessage['error']>; onRetry: () => void; disabled: boolean }) {
  const { t } = useI18n();
  const remaining = useCountdown(error.at, error.kind === 'rate_limit' ? error.retryAfterSeconds : undefined);
  const Icon = error.kind === 'offline' ? WifiOff : TriangleAlert;
  const message =
    error.kind === 'offline'
      ? t('chatUi.err.offline')
      : error.kind === 'rate_limit'
        ? t('chatUi.err.rateLimit', { seconds: error.retryAfterSeconds ?? 30 })
        : error.kind === 'invalid'
          ? t('chatUi.err.invalid')
          : t('chat.error');
  return (
    <div className="flex items-start gap-2">
      <Icon className="mt-0.5 h-4 w-4 shrink-0 text-danger-text" aria-hidden="true" />
      <div className="min-w-0">
        <p className="break-words leading-relaxed">{message}</p>
        {error.kind !== 'invalid' && (
          <button
            type="button"
            onClick={onRetry}
            disabled={disabled || remaining > 0}
            className="mt-2 inline-flex min-h-11 items-center gap-1.5 rounded-full border border-line-strong px-4 text-xs font-medium text-foreground transition-colors hover:border-primary hover:bg-primary-soft disabled:cursor-not-allowed disabled:opacity-60"
          >
            <RotateCw className="h-3.5 w-3.5" aria-hidden="true" />
            {remaining > 0 ? t('chatUi.retryIn', { seconds: remaining }) : t('chatUi.retry')}
          </button>
        )}
      </div>
    </div>
  );
}

const REPLY_STYLES: FaqReplyStyle[] = ['auto', 'script', 'romanised'];

export default function DonorFaqChatbot() {
  const { t } = useI18n();
  const reduce = useReducedMotion();
  const titleId = useId();
  const counterId = useId();
  const hintId = useId();
  const styleName = useId();
  const [isOpen, setIsOpen] = useState(false);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);
  // The conversation lives in this tab's sessionStorage. Nothing about it is rendered while the panel is closed,
  // so reading it during the first client render cannot cause a hydration mismatch.
  const [initial] = useState(() => loadChat());
  const [messages, setMessages] = useState<ChatMessage[]>(initial.messages);
  const [replyStyle, setReplyStyle] = useState<FaqReplyStyle>(initial.replyStyle);

  const messagesEndRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);
  const openerRef = useRef<HTMLButtonElement>(null);
  const hasOpened = useRef(false);
  const [focusTick, setFocusTick] = useState(0);
  const obscured = usePageObscured();

  useEffect(() => {
    saveChat({ messages, replyStyle });
  }, [messages, replyStyle]);

  useEffect(() => {
    if (isOpen) {
      messagesEndRef.current?.scrollIntoView?.({ behavior: reduce ? 'auto' : 'smooth', block: 'end' });
    }
  }, [messages, loading, isOpen, reduce]);

  // Focus: into the input when the panel opens, back to the floating button when it closes
  useEffect(() => {
    if (isOpen) {
      hasOpened.current = true;
      inputRef.current?.focus();
    } else if (hasOpened.current) {
      openerRef.current?.focus();
    }
  }, [isOpen]);

  // Buttons ask for the input to be focused again (suggestion chips, retry and clear remove or replace what had focus)
  const requestFocus = () => setFocusTick((n) => n + 1);
  useEffect(() => {
    if (focusTick > 0) inputRef.current?.focus();
  }, [focusTick]);

  // Grow the input with its content (up to about four lines), shrink it again after sending
  useEffect(() => {
    const el = inputRef.current;
    if (!el) return;
    el.style.height = 'auto';
    el.style.height = `${Math.min(el.scrollHeight, 112)}px`;
  }, [input, isOpen]);

  const quickPrompts = [
    { icon: Syringe, label: t('chat.prompt1Label'), query: t('chat.prompt1Query') },
    { icon: Scale, label: t('chat.prompt2Label'), query: t('chat.prompt2Query') },
    { icon: Hourglass, label: t('chat.prompt3Label'), query: t('chat.prompt3Query') },
  ];

  const last = messages[messages.length - 1];
  const followUps = last?.sender === 'bot' && last.answer?.followUps.length ? last.answer.followUps : [];

  /**
   * Ask a question. On a retry the question is already in the conversation (its error bubble is replaced), and it must
   * not be sent as its own history.
   */
  const submit = async (question: string, retry = false) => {
    const q = question.trim().slice(0, FAQ_MAX_QUESTION_LENGTH);
    if (!q || loading) return;

    const settled = messages.filter((m) => !m.error);
    const lastSettled = settled[settled.length - 1];
    const pendingIsSame = retry && lastSettled?.sender === 'user' && lastSettled.text === q;
    const history = buildHistory(pendingIsSame ? settled.slice(0, -1) : settled);
    const withQuestion: ChatMessage[] = pendingIsSame ? settled : [...settled, { id: makeId(), sender: 'user', text: q }];

    setMessages(withQuestion);
    setInput('');
    setLoading(true);

    try {
      const answer = await askFaq({ question: q, history, language: 'auto', replyStyle });
      const reply: ChatMessage = {
        id: makeId(),
        sender: 'bot',
        text: answer.answer || t('chat.noAnswer'),
        answer: {
          grounded: answer.grounded,
          sources: answer.sources,
          needsConfirmation: answer.needsConfirmation,
          disclaimer: answer.disclaimer,
          followUps: answer.followUps,
          language: answer.language,
          translationDraft: answer.translationDraft,
          translationNote: answer.translationNote,
        },
      };
      setMessages([...withQuestion, reply]);
    } catch (err) {
      const failure = err instanceof FaqError ? err : new FaqError('server');
      setMessages([...withQuestion, errorMessage(failure.kind, q, failure.retryAfterSeconds)]);
    } finally {
      setLoading(false);
    }
  };

  const clearConversation = () => {
    setMessages([]);
    setInput('');
    requestFocus();
  };

  const onInputKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    // Enter sends, Shift+Enter adds a line. Enter that confirms an IME composition (Sinhala/Tamil input methods) must not send.
    if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
      e.preventDefault();
      void submit(input);
    }
  };

  const onPanelKeyDown = (e: KeyboardEvent<HTMLDivElement>) => {
    if (e.key === 'Escape') {
      e.stopPropagation();
      setIsOpen(false);
    }
  };

  const askSuggested = (query: string) => {
    void submit(query);
    requestFocus();
  };

  // A full-screen menu or drawer is open: stay out of its way (state is kept, so the chat is still there afterwards)
  if (obscured) return null;

  const remainingChars = FAQ_MAX_QUESTION_LENGTH - input.length;

  return (
    // Mobile: lifted above the sticky bottom bars (e.g. the donor Save bar) and the iOS home indicator
    <div className="fixed bottom-[calc(env(safe-area-inset-bottom,0px)+5rem)] right-4 z-50 sm:bottom-6 sm:right-6">
      {/* Floating toggle: a quiet pill with a live dot, no glow. Icon only on small screens. */}
      {!isOpen && (
        <button
          ref={openerRef}
          type="button"
          onClick={() => setIsOpen(true)}
          aria-label={t('chat.assistantName')}
          aria-haspopup="dialog"
          aria-expanded={false}
          className="group flex min-h-12 min-w-12 items-center justify-center gap-2.5 rounded-full border border-line-strong bg-surface-raised px-3.5 py-3 text-sm font-medium text-foreground shadow-lg transition-[border-color,background-color,transform] duration-300 hover:border-primary hover:bg-surface active:scale-[0.97] sm:px-4"
        >
          <span className="relative flex h-5 w-5 items-center justify-center text-primary-text">
            <Bot className="h-5 w-5" aria-hidden="true" />
            <span className="absolute -right-0.5 -top-0.5 h-2 w-2 rounded-full border-2 border-surface-raised bg-success" aria-hidden="true" />
          </span>
          <span className="hidden sm:inline">{t('chat.assistantName')}</span>
        </button>
      )}

      {/* Expanded chat panel: a non-modal dialog (the page stays usable), Esc closes it */}
      {isOpen && (
        <motion.div
          role="dialog"
          aria-labelledby={titleId}
          onKeyDown={onPanelKeyDown}
          initial={reduce ? false : { opacity: 0, y: 16 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.35, ease: [0.22, 1, 0.36, 1] }}
          className="glass-card flex h-[min(36rem,calc(100dvh-8rem))] w-[calc(100vw-2rem)] flex-col overflow-hidden rounded-3xl border-line-strong bg-surface shadow-lg sm:w-[380px]"
        >
          {/* Header */}
          <div className="flex items-center justify-between gap-2 border-b border-line p-4">
            <div className="flex min-w-0 items-center gap-3">
              <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-primary-soft text-primary-text">
                <Bot className="h-5 w-5" aria-hidden="true" />
              </div>
              <div className="min-w-0">
                <h2 id={titleId} className="font-display text-lg text-foreground">
                  {t('chatUi.title')}
                </h2>
                <p className="text-xs leading-relaxed text-muted">{t('chat.subtitle')}</p>
              </div>
            </div>

            <div className="flex shrink-0 items-center gap-1.5">
              <button
                type="button"
                onClick={clearConversation}
                disabled={messages.length === 0 && !input}
                aria-label={t('chatUi.clear')}
                title={t('chatUi.clear')}
                className="flex h-11 w-11 items-center justify-center rounded-full border border-line-strong text-muted transition-colors hover:bg-foreground/[0.08] hover:text-foreground disabled:cursor-not-allowed disabled:opacity-40"
              >
                <Trash2 className="h-4 w-4" aria-hidden="true" />
              </button>
              <button
                type="button"
                onClick={() => setIsOpen(false)}
                aria-label={t('common.close')}
                className="flex h-11 w-11 items-center justify-center rounded-full border border-line-strong text-muted transition-colors hover:bg-foreground/[0.08] hover:text-foreground"
              >
                <X className="h-4 w-4" aria-hidden="true" />
              </button>
            </div>
          </div>

          {/* Persistent notice: honest about what the assistant is. Not a claim of official endorsement. */}
          <div className="flex items-start gap-2 border-b border-line bg-info-soft px-4 py-2.5">
            <Info className="mt-0.5 h-4 w-4 shrink-0 text-info-text" aria-hidden="true" />
            <p className="text-xs leading-relaxed text-foreground">{t('chatUi.notice')}</p>
          </div>

          {/* Reply style: only matters when the person writes in Sinhala (script, or romanised "Singlish") */}
          <details className="group border-b border-line">
            <summary className="flex min-h-11 cursor-pointer list-none items-center justify-between gap-2 px-4 text-xs text-muted marker:hidden [&::-webkit-details-marker]:hidden">
              <span className="flex min-w-0 items-center gap-2">
                <Languages className="h-4 w-4 shrink-0" aria-hidden="true" />
                <span className="min-w-0 break-words">
                  {t('chatUi.replyStyle.summary', { style: t(`chatUi.replyStyle.${replyStyle}`) })}
                </span>
              </span>
              <ChevronDown className="h-4 w-4 shrink-0 transition-transform group-open:rotate-180 motion-reduce:transition-none" aria-hidden="true" />
            </summary>
            <fieldset className="px-4 pb-3">
              <legend className="mb-2 text-xs leading-relaxed text-foreground">{t('chatUi.replyStyle.legend')}</legend>
              <div className="flex flex-wrap gap-2">
                {REPLY_STYLES.map((style) => (
                  <label key={style} className="relative">
                    <input
                      type="radio"
                      name={styleName}
                      value={style}
                      checked={replyStyle === style}
                      onChange={() => setReplyStyle(style)}
                      className="peer sr-only"
                    />
                    <span className="flex min-h-11 cursor-pointer items-center gap-1.5 rounded-full border border-line-strong px-3.5 text-xs font-medium text-foreground transition-colors hover:border-primary peer-checked:border-primary peer-checked:bg-primary-soft peer-focus-visible:outline-2 peer-focus-visible:outline-offset-2 peer-focus-visible:outline-primary">
                      {replyStyle === style && <Check className="h-3.5 w-3.5 text-primary-text" aria-hidden="true" />}
                      {t(`chatUi.replyStyle.${style}`)}
                    </span>
                  </label>
                ))}
              </div>
            </fieldset>
          </details>

          {/* Messages. The log announces each new reply once (not the whole history); the status line below announces "looking for an answer". */}
          <div className="min-h-0 flex-1 overflow-y-auto p-4 text-sm">
            <div role="log" aria-live="polite" aria-relevant="additions" aria-label={t('chatUi.conversation')} className="space-y-3">
              <Bubble sender="bot">
                <p className="whitespace-pre-wrap break-words leading-relaxed">{t('chat.welcome')}</p>
              </Bubble>

              {messages.map((m) =>
                m.sender === 'user' ? (
                  <Bubble key={m.id} sender="user">
                    <p className="whitespace-pre-wrap break-words leading-relaxed">{m.text}</p>
                  </Bubble>
                ) : m.error ? (
                  <Bubble key={m.id} sender="bot">
                    <span className="sr-only">{t('chatUi.assistantSays')} </span>
                    <ErrorBubble error={m.error} disabled={loading} onRetry={() => { void submit(m.error!.question, true); requestFocus(); }} />
                  </Bubble>
                ) : (
                  <Bubble key={m.id} sender="bot">
                    <span className="sr-only">{t('chatUi.assistantSays')} </span>
                    <p className="whitespace-pre-wrap break-words leading-relaxed">{m.text}</p>
                    {m.answer && (
                      <>
                        {(m.answer.grounded || m.answer.language) && (
                          <div className="mt-2 flex flex-wrap items-center gap-x-3 gap-y-1.5">
                            {/* Green only for a fully sourced answer; anything that needs confirming gets the amber note below instead */}
                            {m.answer.grounded && !m.answer.needsConfirmation && <GroundedBadge label={t('chatUi.grounded')} />}
                            {m.answer.language && (
                              <span className="inline-flex items-center gap-1 text-[11px] text-muted">
                                <Languages className="h-3 w-3" aria-hidden="true" />
                                {t('chatUi.replyLanguage', { language: t(`chatUi.lang.${m.answer.language}`) })}
                              </span>
                            )}
                          </div>
                        )}
                        {m.answer.needsConfirmation && (
                          <div className="mt-2 flex items-start gap-1.5 rounded-lg border border-warning/30 bg-warning-soft px-2.5 py-2 text-xs leading-relaxed text-foreground">
                            <TriangleAlert className="mt-0.5 h-3.5 w-3.5 shrink-0 text-warning-text" aria-hidden="true" />
                            <span>{t('chatUi.needsConfirmation')}</span>
                          </div>
                        )}
                        {m.answer.translationDraft && (
                          <div className="mt-2 flex items-start gap-1.5 text-xs leading-relaxed text-muted">
                            <Languages className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" />
                            <span className="min-w-0 break-words">{m.answer.translationNote || t('chatUi.translationDraft')}</span>
                          </div>
                        )}
                        <SourcesDisclosure sources={m.answer.sources} />
                        {m.answer.disclaimer && (
                          <div className="mt-2 flex items-start gap-1.5 border-t border-line pt-2 text-xs leading-relaxed text-muted">
                            <Info className="mt-0.5 h-3.5 w-3.5 shrink-0 text-info-text" aria-hidden="true" />
                            <span className="min-w-0 break-words">{m.answer.disclaimer}</span>
                          </div>
                        )}
                      </>
                    )}
                  </Bubble>
                )
              )}
            </div>

            <div role="status" aria-live="polite" className="mt-3">
              {loading && (
                <div className="flex items-center gap-2.5 text-muted">
                  <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-primary-soft text-primary-text">
                    <Bot className="h-4 w-4" aria-hidden="true" />
                  </div>
                  <div className="flex items-center gap-2 rounded-2xl border border-line bg-surface-raised p-3">
                    <Loader2 className="h-4 w-4 animate-spin text-primary-text motion-reduce:animate-none" aria-hidden="true" />
                    <span className="text-xs">{t('chatUi.thinking')}</span>
                  </div>
                </div>
              )}
            </div>
            <div ref={messagesEndRef} />
          </div>

          {/* Suggested questions: the answer's follow-ups when there are any, otherwise the starter questions. They wrap instead of clipping. */}
          <div role="group" aria-label={t('chatUi.suggested')} className="flex max-h-32 flex-wrap gap-2 overflow-y-auto border-t border-line px-4 py-2.5">
            {followUps.length > 0
              ? followUps.map((q) => (
                  <button
                    key={q}
                    type="button"
                    onClick={() => askSuggested(q)}
                    disabled={loading}
                    className="min-h-11 max-w-full rounded-2xl border border-line-strong px-3.5 py-2 text-left text-xs font-medium leading-snug text-foreground transition-colors hover:border-primary hover:bg-primary-soft disabled:opacity-50"
                  >
                    {q}
                  </button>
                ))
              : quickPrompts.map((p, idx) => (
                  <button
                    key={idx}
                    type="button"
                    onClick={() => askSuggested(p.query)}
                    disabled={loading}
                    className="flex min-h-11 items-center gap-1.5 rounded-full border border-line-strong px-3.5 py-2 text-left text-xs font-medium leading-snug text-foreground transition-colors hover:border-primary hover:bg-primary-soft disabled:opacity-50"
                  >
                    <p.icon className="h-3.5 w-3.5 shrink-0 text-primary-text" aria-hidden="true" />
                    {p.label}
                  </button>
                ))}
          </div>

          {/* Input area: Enter sends, Shift+Enter starts a new line */}
          <form
            onSubmit={(e) => {
              e.preventDefault();
              void submit(input);
            }}
            className="border-t border-line p-3"
          >
            <div className="flex items-end gap-2">
              <textarea
                ref={inputRef}
                rows={1}
                placeholder={t('chat.placeholder')}
                aria-label={t('chat.placeholder')}
                aria-describedby={`${hintId} ${counterId}`}
                value={input}
                maxLength={FAQ_MAX_QUESTION_LENGTH}
                onChange={(e) => setInput(e.target.value)}
                onKeyDown={onInputKeyDown}
                className="max-h-28 min-h-11 min-w-0 flex-1 resize-none rounded-2xl border border-control bg-surface px-4 py-2.5 text-sm leading-relaxed text-foreground placeholder:text-muted transition-[border-color,box-shadow] hover:border-foreground/60 focus:border-primary focus:outline-none focus:ring-4 focus:ring-primary/20"
              />
              <button
                type="submit"
                disabled={!input.trim() || loading}
                aria-label={t('chat.send')}
                className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-primary text-primary-fg transition-[background-color,transform,opacity] hover:bg-primary-hover active:scale-95 disabled:cursor-not-allowed disabled:opacity-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
              >
                <Send className="h-4 w-4" aria-hidden="true" />
              </button>
            </div>
            <div className="mt-1.5 flex flex-wrap items-center justify-between gap-x-3 gap-y-0.5 px-1 text-[11px] leading-relaxed text-muted">
              <span id={hintId}>{t('chatUi.inputHint')}</span>
              <span id={counterId} className="tabular-nums" data-testid="char-counter">
                {input.length}/{FAQ_MAX_QUESTION_LENGTH}
              </span>
            </div>
            <span role="status" className="sr-only">
              {remainingChars <= 50 ? t('chatUi.charsLeft', { count: remainingChars }) : ''}
            </span>
          </form>
        </motion.div>
      )}
    </div>
  );
}

/** One chat bubble. Bot bubbles carry the avatar; user bubbles are filled with the primary colour. */
function Bubble({ sender, children }: { sender: 'user' | 'bot'; children: React.ReactNode }) {
  return (
    <div className={`flex gap-2.5 ${sender === 'user' ? 'justify-end' : 'justify-start'}`}>
      {sender === 'bot' && (
        <div className="mt-0.5 flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-primary-soft text-primary-text">
          <Bot className="h-4 w-4" aria-hidden="true" />
        </div>
      )}
      <div
        className={`min-w-0 max-w-[85%] rounded-2xl p-3 ${
          sender === 'user' ? 'rounded-tr-md bg-primary text-primary-fg' : 'rounded-tl-md border border-line bg-surface-raised text-foreground'
        }`}
      >
        {children}
      </div>
    </div>
  );
}
