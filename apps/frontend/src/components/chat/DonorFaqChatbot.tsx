'use client';

import { useState, useRef, useEffect } from 'react';
import { motion, useReducedMotion } from 'motion/react';
import { X, Send, Bot, ShieldCheck, Loader2, Syringe, Scale, Hourglass } from 'lucide-react';
import apiClient from '@/lib/apiClient';
import { useI18n } from '@/lib/i18n';

interface Message {
  id: string;
  sender: 'user' | 'bot';
  text: string;
  timestamp: Date;
  disclaimer?: string;
}

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

export default function DonorFaqChatbot() {
  const { locale, t } = useI18n();
  const reduce = useReducedMotion();
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

  useEffect(() => {
    if (isOpen) {
      messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }
  }, [messages, isOpen]);

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
      const data = (res.data as { data?: { answer?: string; disclaimer?: string } })?.data ?? (res.data as { answer?: string; disclaimer?: string });

      const botMsg: Message = {
        id: makeId(),
        sender: 'bot',
        text: data?.answer || t('chat.noAnswer'),
        timestamp: new Date(),
        disclaimer: data?.disclaimer,
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

  return (
    <div className="fixed bottom-4 right-4 z-50 sm:bottom-6 sm:right-6">
      {/* Floating toggle: a quiet pill with a live dot, no glow */}
      {!isOpen && (
        <button
          type="button"
          onClick={() => setIsOpen(true)}
          aria-label={t('chat.assistantName')}
          className="group flex items-center gap-2.5 rounded-full border border-line-strong bg-surface-raised px-4 py-3 text-xs font-medium text-foreground shadow-[0_10px_30px_-12px_rgba(0,0,0,0.8)] transition-[border-color,background-color,transform] duration-300 hover:border-ember/60 hover:bg-surface active:scale-[0.97] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ember"
        >
          <span className="relative flex h-5 w-5 items-center justify-center text-ember">
            <Bot className="h-5 w-5" aria-hidden="true" />
            <span className="absolute -right-0.5 -top-0.5 h-2 w-2 rounded-full border-2 border-surface-raised bg-emerald-400" aria-hidden="true" />
          </span>
          <span className="hidden sm:inline">{t('chat.assistantName')}</span>
        </button>
      )}

      {/* Expanded chat panel */}
      {isOpen && (
        <motion.div
          role="dialog"
          aria-label={t('chat.assistantName')}
          initial={reduce ? false : { opacity: 0, y: 16 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.35, ease: [0.22, 1, 0.36, 1] }}
          className="glass-card flex h-[min(540px,80vh)] w-[calc(100vw-2rem)] flex-col overflow-hidden rounded-3xl border-line-strong bg-surface shadow-[0_30px_60px_-20px_rgba(0,0,0,0.9)] sm:w-[380px]"
        >
          {/* Header */}
          <div className="flex items-center justify-between gap-3 border-b border-line p-4">
            <div className="flex min-w-0 items-center gap-3">
              <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-ember/15 text-ember">
                <Bot className="h-5 w-5" aria-hidden="true" />
              </div>
              <div className="min-w-0">
                <h3 className="font-display flex flex-wrap items-center gap-x-2 gap-y-1 text-lg text-foreground">
                  Suwa Sarana AI
                  <span className="rounded-full border border-emerald-500/30 px-2 py-0.5 font-mono text-[9px] uppercase tracking-[0.1em] text-emerald-300">
                    {t('chat.grounded')}
                  </span>
                </h3>
                <p className="text-[11px] text-gray-500">{t('chat.subtitle')}</p>
              </div>
            </div>

            <button
              type="button"
              onClick={() => setIsOpen(false)}
              aria-label={t('common.close')}
              className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full border border-line text-gray-400 transition-colors hover:bg-foreground/[0.08] hover:text-foreground"
            >
              <X className="h-4 w-4" aria-hidden="true" />
            </button>
          </div>

          {/* Persistent Banner */}
          <div className="flex items-start gap-2 border-b border-line bg-emerald-500/[0.07] px-4 py-2.5">
            <ShieldCheck className="mt-0.5 h-4 w-4 shrink-0 text-emerald-400" aria-hidden="true" />
            <p className="text-[11px] leading-snug text-emerald-200/90">{t('chat.banner')}</p>
          </div>

          {/* Messages Area */}
          <div className="flex-1 space-y-3 overflow-y-auto p-4 text-xs" aria-live="polite">
            {messages.map((m) => (
              <div key={m.id} className={`flex gap-2.5 ${m.sender === 'user' ? 'justify-end' : 'justify-start'}`}>
                {m.sender === 'bot' && (
                  <div className="mt-0.5 flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-ember/15 text-ember">
                    <Bot className="h-3.5 w-3.5" aria-hidden="true" />
                  </div>
                )}
                <div
                  className={`max-w-[82%] rounded-2xl p-3 ${
                    m.sender === 'user'
                      ? 'rounded-tr-md bg-ember text-white'
                      : 'rounded-tl-md border border-line bg-foreground/[0.05] text-gray-200'
                  }`}
                >
                  <p className="whitespace-pre-wrap break-words leading-relaxed">{m.id === 'welcome' ? t('chat.welcome') : m.text}</p>
                  {m.disclaimer && (
                    <div className="mt-2 flex items-start gap-1 border-t border-line pt-2 text-[10px] text-gray-400">
                      <ShieldCheck className="mt-0.5 h-3 w-3 shrink-0 text-emerald-400" aria-hidden="true" />
                      <span>{m.disclaimer}</span>
                    </div>
                  )}
                </div>
              </div>
            ))}

            {loading && (
              <div className="flex items-center gap-2.5 text-gray-400">
                <div className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-ember/15 text-ember">
                  <Bot className="h-3.5 w-3.5" aria-hidden="true" />
                </div>
                <div className="flex items-center gap-2 rounded-2xl border border-line bg-foreground/[0.03] p-3">
                  <Loader2 className="h-3.5 w-3.5 animate-spin text-ember" aria-hidden="true" />
                  <span className="text-[11px]">{t('chat.thinking')}</span>
                </div>
              </div>
            )}
            <div ref={messagesEndRef} />
          </div>

          {/* Quick Question Chips */}
          <div className="flex gap-1.5 overflow-x-auto border-t border-line px-4 py-2">
            {quickPrompts.map((p, idx) => (
              <button
                key={idx}
                type="button"
                onClick={() => handleSend(p.query)}
                className="flex shrink-0 items-center gap-1.5 whitespace-nowrap rounded-full border border-line px-3 py-1.5 text-[10px] font-medium text-gray-300 transition-colors hover:border-ember/50 hover:text-foreground"
              >
                <p.icon className="h-3 w-3 text-ember" aria-hidden="true" />
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
              type="text"
              placeholder={t('chat.placeholder')}
              aria-label={t('chat.placeholder')}
              value={input}
              onChange={(e) => setInput(e.target.value)}
              className="min-w-0 flex-1 rounded-full border border-line bg-foreground/[0.04] px-4 py-2.5 text-xs text-foreground placeholder-gray-500 transition-colors focus:border-ember/60 focus:outline-none"
            />
            <button
              type="submit"
              disabled={!input.trim() || loading}
              aria-label={t('chat.send')}
              className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-ember text-white transition-[background-color,transform,opacity] hover:bg-[color-mix(in_oklch,var(--ember)_88%,white)] active:scale-95 disabled:opacity-40 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ember"
            >
              <Send className="h-4 w-4" aria-hidden="true" />
            </button>
          </form>
        </motion.div>
      )}
    </div>
  );
}
