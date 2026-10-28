'use client';

import { useState, useRef, useEffect, useId, type KeyboardEvent } from 'react';
import { motion, useReducedMotion } from 'motion/react';
import { X, Send, Bot, ShieldCheck, Info, Loader2, Syringe, Scale, Hourglass } from 'lucide-react';
import apiClient from '@/lib/apiClient';
import { useI18n } from '@/lib/i18n';

interface Message {
  id: string;
  sender: 'user' | 'bot';
  text: string;
  timestamp: Date;
  disclaimer?: string;
  /**
   * True only when the reply was built from a stored, citable source. Drives the "grounded" badge: never show it otherwise.
   * Filled from the `grounded` field of the /faq/ask response (the AI work will supply it).
   */
  grounded?: boolean;
}

// The API rejects questions longer than this (400), so stop the input here instead
const MAX_QUESTION_LENGTH = 500;

// crypto.randomUUID is unavailable on non-HTTPS origins, so fall back to a simple id
function makeId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    try {
      return crypto.randomUUID();
    } catch {
      // fall through
    }
  }
  return `${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

/** Small "this answer cites a stored source" marker. Render only when the response said so. */
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

export default function DonorFaqChatbot() {
  const { locale, t } = useI18n();
  const reduce = useReducedMotion();
  const titleId = useId();
  const [isOpen, setIsOpen] = useState(false);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);
  const [messages, setMessages] = useState<Message[]>([
    {
      id: 'welcome',
      sender: 'bot',
      text: '', // rendered from i18n (chat.welcome) so it follows the active language
      timestamp: new Date(),
    },
  ]);

  const messagesEndRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const openerRef = useRef<HTMLButtonElement>(null);
  const hasOpened = useRef(false);
  const obscured = usePageObscured();

  useEffect(() => {
    if (isOpen) {
      messagesEndRef.current?.scrollIntoView({ behavior: reduce ? 'auto' : 'smooth', block: 'end' });
    }
  }, [messages, isOpen, reduce]);

  // Focus: into the input when the panel opens, back to the floating button when it closes
  useEffect(() => {
    if (isOpen) {
      hasOpened.current = true;
      inputRef.current?.focus();
    } else if (hasOpened.current) {
      openerRef.current?.focus();
    }
  }, [isOpen]);

  const quickPrompts = [
    { icon: Syringe, label: t('chat.prompt1Label'), query: t('chat.prompt1Query') },
    { icon: Scale, label: t('chat.prompt2Label'), query: t('chat.prompt2Query') },
    { icon: Hourglass, label: t('chat.prompt3Label'), query: t('chat.prompt3Query') },
  ];

  const handleSend = async (questionText?: string) => {
    const q = (questionText || input).trim();
    if (!q || loading) return;

    const userMsg: Message = {
      id: makeId(),
      sender: 'user',
      text: q,
      timestamp: new Date(),
    };

    setMessages((prev) => [...prev, userMsg]);
    setInput('');
    setLoading(true);

    try {
      const res = await apiClient.post<unknown>('/faq/ask', {
        question: q,
        locale: locale || 'en',
      });
      type FaqPayload = { answer?: string; disclaimer?: string; grounded?: boolean };
      const data = (res.data as { data?: FaqPayload })?.data ?? (res.data as FaqPayload);

      const botMsg: Message = {
        id: makeId(),
        sender: 'bot',
        text: data?.answer || t('chat.noAnswer'),
        timestamp: new Date(),
        disclaimer: data?.disclaimer,
        grounded: data?.grounded === true,
      };

      setMessages((prev) => [...prev, botMsg]);
    } catch {
      const errorMsg: Message = {
        id: makeId(),
        sender: 'bot',
        text: t('chat.error'),
        timestamp: new Date(),
      };
      setMessages((prev) => [...prev, errorMsg]);
    } finally {
      setLoading(false);
    }
  };

  const onPanelKeyDown = (e: KeyboardEvent<HTMLDivElement>) => {
    if (e.key === 'Escape') {
      e.stopPropagation();
      setIsOpen(false);
    }
  };

  // A full-screen menu or drawer is open: stay out of its way (state is kept, so the chat is still there afterwards)
  if (obscured) return null;

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
          className="glass-card flex h-[min(34rem,calc(100dvh-8rem))] w-[calc(100vw-2rem)] flex-col overflow-hidden rounded-3xl border-line-strong bg-surface shadow-lg sm:w-[380px]"
        >
          {/* Header */}
          <div className="flex items-center justify-between gap-3 border-b border-line p-4">
            <div className="flex min-w-0 items-center gap-3">
              <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-primary-soft text-primary-text">
                <Bot className="h-5 w-5" aria-hidden="true" />
              </div>
              <div className="min-w-0">
                <h2 id={titleId} className="font-display text-lg text-foreground">
                  {t('chatUi.title')}
                </h2>
                <p className="text-xs leading-relaxed text-gray-500">{t('chat.subtitle')}</p>
              </div>
            </div>

            <button
              type="button"
              onClick={() => setIsOpen(false)}
              aria-label={t('common.close')}
              className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full border border-line-strong text-gray-400 transition-colors hover:bg-foreground/[0.08] hover:text-foreground"
            >
              <X className="h-4 w-4" aria-hidden="true" />
            </button>
          </div>

          {/* Persistent notice: honest about what the assistant is. Not a claim of official endorsement. */}
          <div className="flex items-start gap-2 border-b border-line bg-info-soft px-4 py-2.5">
            <Info className="mt-0.5 h-4 w-4 shrink-0 text-info-text" aria-hidden="true" />
            <p className="text-xs leading-relaxed text-foreground">{t('chatUi.notice')}</p>
          </div>

          {/* Messages: a log, so each new reply is announced once (not the whole history) */}
          <div
            role="log"
            aria-live="polite"
            aria-relevant="additions"
            aria-label={t('chatUi.conversation')}
            className="min-h-0 flex-1 space-y-3 overflow-y-auto p-4 text-sm"
          >
            {messages.map((m) => (
              <div key={m.id} className={`flex gap-2.5 ${m.sender === 'user' ? 'justify-end' : 'justify-start'}`}>
                {m.sender === 'bot' && (
                  <div className="mt-0.5 flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-primary-soft text-primary-text">
                    <Bot className="h-4 w-4" aria-hidden="true" />
                  </div>
                )}
                <div
                  className={`max-w-[82%] rounded-2xl p-3 ${
                    m.sender === 'user'
                      ? 'rounded-tr-md bg-primary text-primary-fg'
                      : 'rounded-tl-md border border-line bg-surface-raised text-foreground'
                  }`}
                >
                  {m.sender === 'bot' && <span className="sr-only">{t('chatUi.assistantSays')} </span>}
                  <p className="whitespace-pre-wrap break-words leading-relaxed">{m.id === 'welcome' ? t('chat.welcome') : m.text}</p>
                  {m.grounded && (
                    <div className="mt-2">
                      <GroundedBadge label={t('chatUi.grounded')} />
                    </div>
                  )}
                  {m.disclaimer && (
                    <div className="mt-2 flex items-start gap-1.5 border-t border-line pt-2 text-xs leading-relaxed text-gray-400">
                      <Info className="mt-0.5 h-3.5 w-3.5 shrink-0 text-info-text" aria-hidden="true" />
                      <span>{m.disclaimer}</span>
                    </div>
                  )}
                </div>
              </div>
            ))}

            {loading && (
              <div role="status" className="flex items-center gap-2.5 text-gray-400">
                <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-primary-soft text-primary-text">
                  <Bot className="h-4 w-4" aria-hidden="true" />
                </div>
                <div className="flex items-center gap-2 rounded-2xl border border-line bg-surface-raised p-3">
                  <Loader2 className="h-4 w-4 animate-spin text-primary-text motion-reduce:animate-none" aria-hidden="true" />
                  <span className="text-xs">{t('chatUi.thinking')}</span>
                </div>
              </div>
            )}
            <div ref={messagesEndRef} />
          </div>

          {/* Quick questions: wrap onto extra lines instead of clipping */}
          <div role="group" aria-label={t('chatUi.suggested')} className="flex flex-wrap gap-2 border-t border-line px-4 py-2.5">
            {quickPrompts.map((p, idx) => (
              <button
                key={idx}
                type="button"
                onClick={() => handleSend(p.query)}
                disabled={loading}
                className="flex min-h-11 items-center gap-1.5 rounded-full border border-line-strong px-3.5 py-2 text-left text-xs font-medium leading-snug text-foreground transition-colors hover:border-primary hover:bg-primary-soft disabled:opacity-50"
              >
                <p.icon className="h-3.5 w-3.5 shrink-0 text-primary-text" aria-hidden="true" />
                {p.label}
              </button>
            ))}
          </div>

          {/* Input Area */}
          <form
            onSubmit={(e) => {
              e.preventDefault();
              handleSend();
            }}
            className="flex items-center gap-2 border-t border-line p-3"
          >
            <input
              ref={inputRef}
              type="text"
              placeholder={t('chat.placeholder')}
              aria-label={t('chat.placeholder')}
              value={input}
              maxLength={MAX_QUESTION_LENGTH}
              onChange={(e) => setInput(e.target.value)}
              className="min-h-11 min-w-0 flex-1 rounded-full border border-control bg-surface px-4 py-2.5 text-sm text-foreground placeholder:text-muted transition-[border-color,box-shadow] hover:border-foreground/60 focus:border-primary focus:outline-none focus:ring-4 focus:ring-primary/20"
            />
            <button
              type="submit"
              disabled={!input.trim() || loading}
              aria-label={t('chat.send')}
              className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-primary text-primary-fg transition-[background-color,transform,opacity] hover:bg-primary-hover active:scale-95 disabled:cursor-not-allowed disabled:opacity-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
            >
              <Send className="h-4 w-4" aria-hidden="true" />
            </button>
          </form>
        </motion.div>
      )}
    </div>
  );
}
