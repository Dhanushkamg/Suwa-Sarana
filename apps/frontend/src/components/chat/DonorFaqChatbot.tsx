'use client';

import { useState, useRef, useEffect } from 'react';
import {
  MessageSquare, X, Send, Bot, User, Sparkles, ShieldCheck,
  ChevronDown, HelpCircle, Loader2, RefreshCw
} from 'lucide-react';
import apiClient from '@/lib/apiClient';
import { useI18n } from '@/lib/i18n';

interface Message {
  id: string;
  sender: 'user' | 'bot';
  text: string;
  timestamp: Date;
  disclaimer?: string;
}

export default function DonorFaqChatbot() {
  const { locale } = useI18n();
  const [isOpen, setIsOpen] = useState(false);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);
  const [messages, setMessages] = useState<Message[]>([
    {
      id: 'welcome',
      sender: 'bot',
      text: 'Ayubowan & Vanakkam! 🙏 I am your Suwa Sarana Blood Donation Eligibility Assistant. Ask me anything about deferral periods, age/weight criteria, or donation safety in English, සිංහල, or தமிழ்.',
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
    { label: '💉 Tattoo & Piercing', query: 'Can I donate blood if I got a tattoo or piercing recently?' },
    { label: '⚖️ Age & Weight', query: 'What is the minimum age and weight to donate blood?' },
    { label: '⏳ Donation Interval', query: 'How often can I donate blood in Sri Lanka?' },
    { label: '🇱🇰 සිංහල: පච්ච (Tattoo)', query: 'පච්චයක් (Tattoo) ගැහුවට පස්සේ ලේ දෙන්න පුලුවන්ද?' },
    { label: '🇱🇰 தமிழ்: பச்சை (Tattoo)', query: 'பச்சை குத்திய பிறகு நான் இரத்த தானம் செய்யலாமா?' },
  ];

  const handleSend = async (questionText?: string) => {
    const q = (questionText || input).trim();
    if (!q || loading) return;

    const userMsg: Message = {
      id: Date.now().toString(),
      sender: 'user',
      text: q,
      timestamp: new Date(),
    };

    setMessages((prev) => [...prev, userMsg]);
    setInput('');
    setLoading(true);

    try {
      const res = await apiClient.post<any>('/faq/ask', {
        question: q,
        locale: locale || 'en',
      });
      const data = res.data?.data ?? res.data;

      const botMsg: Message = {
        id: (Date.now() + 1).toString(),
        sender: 'bot',
        text: data?.answer || 'I could not process your query. Please consult clinical staff at the blood bank.',
        timestamp: new Date(),
        disclaimer: data?.disclaimer,
      };

      setMessages((prev) => [...prev, botMsg]);
    } catch {
      const errorMsg: Message = {
        id: (Date.now() + 1).toString(),
        sender: 'bot',
        text: 'Sorry, I am currently unable to reach the eligibility engine. Please check with clinical staff at your nearest blood bank.',
        timestamp: new Date(),
      };
      setMessages((prev) => [...prev, errorMsg]);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="fixed bottom-6 right-6 z-50">
      {/* Floating Toggle Button */}
      {!isOpen && (
        <button
          onClick={() => setIsOpen(true)}
          className="group relative flex items-center gap-2.5 px-4 py-3.5 rounded-full bg-gradient-to-r from-red-500 via-rose-600 to-red-600 hover:from-red-600 hover:to-rose-700 text-white font-bold text-xs shadow-2xl shadow-red-500/40 border border-white/20 transition-all transform hover:scale-105 active:scale-95"
        >
          <div className="relative">
            <Bot className="w-5 h-5 text-white" />
            <span className="absolute -top-1 -right-1 w-2.5 h-2.5 bg-emerald-400 rounded-full border-2 border-[#0d0d14] animate-pulse" />
          </div>
          <span className="hidden sm:inline">Eligibility AI Assistant</span>
        </button>
      )}

      {/* Expanded Chatbot Modal */}
      {isOpen && (
        <div className="w-[92vw] sm:w-[380px] h-[540px] glass-card rounded-3xl border border-white/15 shadow-2xl bg-[#0d0d14]/95 backdrop-blur-2xl flex flex-col overflow-hidden animate-in fade-in slide-in-from-bottom-5 duration-200">
          {/* Header */}
          <div className="p-4 border-b border-white/10 bg-white/5 flex items-center justify-between">
            <div className="flex items-center gap-3">
              <div className="w-9 h-9 rounded-xl bg-gradient-to-br from-red-500 to-rose-600 flex items-center justify-center text-white shadow-lg shadow-red-500/30">
                <Bot className="w-5 h-5" />
              </div>
              <div>
                <h3 className="text-sm font-bold text-white flex items-center gap-1.5">
                  Suwa Sarana AI
                  <span className="text-[9px] px-1.5 py-0.5 rounded bg-emerald-500/20 text-emerald-300 font-semibold uppercase">
                    NBTS Grounded
                  </span>
                </h3>
                <p className="text-[11px] text-gray-400">Eligibility & Deferral FAQ</p>
              </div>
            </div>

            <button
              onClick={() => setIsOpen(false)}
              className="w-8 h-8 rounded-xl bg-white/5 hover:bg-white/10 text-gray-400 hover:text-white flex items-center justify-center transition-colors"
            >
              <X className="w-4 h-4" />
            </button>
          </div>

          {/* Messages Area */}
          <div className="flex-1 p-4 overflow-y-auto space-y-3 text-xs">
            {messages.map((m) => (
              <div
                key={m.id}
                className={`flex gap-2.5 ${m.sender === 'user' ? 'justify-end' : 'justify-start'}`}
              >
                {m.sender === 'bot' && (
                  <div className="w-6 h-6 rounded-full bg-red-500/20 border border-red-500/30 flex items-center justify-center text-red-400 flex-shrink-0 mt-0.5">
                    <Bot className="w-3.5 h-3.5" />
                  </div>
                )}
                <div
                  className={`max-w-[82%] p-3 rounded-2xl ${
                    m.sender === 'user'
                      ? 'bg-gradient-to-r from-red-500 to-rose-600 text-white rounded-tr-none'
                      : 'bg-white/10 border border-white/10 text-gray-200 rounded-tl-none'
                  }`}
                >
                  <p className="leading-relaxed whitespace-pre-wrap">{m.text}</p>
                  {m.disclaimer && (
                    <div className="mt-2 pt-2 border-t border-white/10 text-[10px] text-gray-400 flex items-start gap-1">
                      <ShieldCheck className="w-3 h-3 text-emerald-400 flex-shrink-0 mt-0.5" />
                      <span>{m.disclaimer}</span>
                    </div>
                  )}
                </div>
              </div>
            ))}

            {loading && (
              <div className="flex gap-2.5 items-center text-gray-400">
                <div className="w-6 h-6 rounded-full bg-red-500/20 border border-red-500/30 flex items-center justify-center text-red-400 flex-shrink-0">
                  <Bot className="w-3.5 h-3.5" />
                </div>
                <div className="p-3 rounded-2xl bg-white/5 border border-white/5 flex items-center gap-2">
                  <Loader2 className="w-3.5 h-3.5 animate-spin text-red-400" />
                  <span className="text-[11px]">Consulting NBTS eligibility rules...</span>
                </div>
              </div>
            )}
            <div ref={messagesEndRef} />
          </div>

          {/* Quick Question Chips */}
          <div className="px-4 py-2 border-t border-white/5 bg-black/20 flex gap-1.5 overflow-x-auto no-scrollbar">
            {quickPrompts.map((p, idx) => (
              <button
                key={idx}
                onClick={() => handleSend(p.query)}
                className="whitespace-nowrap px-2.5 py-1 rounded-lg bg-white/5 hover:bg-white/10 border border-white/10 text-[10px] font-medium text-gray-300 transition-colors"
              >
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
            className="p-3 border-t border-white/10 bg-white/5 flex items-center gap-2"
          >
            <input
              type="text"
              placeholder="Ask in English, සිංහල, or தமிழ்..."
              value={input}
              onChange={(e) => setInput(e.target.value)}
              className="flex-1 bg-white/5 border border-white/10 rounded-xl px-3 py-2.5 text-xs text-white placeholder-gray-500 focus:outline-none focus:border-red-500/50 transition-colors"
            />
            <button
              type="submit"
              disabled={!input.trim() || loading}
              className="w-9 h-9 rounded-xl bg-gradient-to-r from-red-500 to-rose-600 hover:from-red-600 hover:to-rose-700 text-white flex items-center justify-center shadow-md disabled:opacity-40 transition-all flex-shrink-0"
            >
              <Send className="w-4 h-4" />
            </button>
          </form>
        </div>
      )}
    </div>
  );
}
