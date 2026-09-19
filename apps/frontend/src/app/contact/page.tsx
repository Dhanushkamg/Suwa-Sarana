'use client';

import { useState } from 'react';
import Link from 'next/link';
import { Heart, ArrowLeft, Mail, Phone, MapPin } from 'lucide-react';
import apiClient from '@/lib/apiClient';
import { useI18n } from '@/lib/i18n';
import { LanguageSwitcher } from '@/components/ui/LanguageSwitcher';

export default function ContactPage() {
  const { t } = useI18n();
  const [formData, setFormData] = useState({ name: '', email: '', subject: '', message: '' });
  const [loading, setLoading] = useState(false);
  const [submitStatus, setSubmitStatus] = useState<'idle' | 'success' | 'error'>('idle');
  const [submitMessage, setSubmitMessage] = useState('');

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setSubmitStatus('idle');
    try {
      const res = await apiClient.post('/contact', formData);
      setSubmitStatus('success');
      setSubmitMessage(res.data.message || 'Message sent successfully.');
      setFormData({ name: '', email: '', subject: '', message: '' });
    } catch (err: any) {
      setSubmitStatus('error');
      setSubmitMessage(err.response?.data?.message || 'Failed to send message.');
    } finally {
      setLoading(false);
    }
  };
  return (
    <div className="min-h-screen bg-[#0d0d14] text-white">
      {/* Nav */}
      <nav className="fixed top-0 inset-x-0 z-50 border-b border-white/5 bg-[#0d0d14]/80 backdrop-blur-xl">
        <div className="max-w-7xl mx-auto px-6 h-16 flex items-center justify-between">
          <Link href="/" className="flex items-center gap-2.5 group">
            <div className="w-9 h-9 rounded-xl bg-gradient-to-br from-red-500 to-rose-600 flex items-center justify-center shadow-lg shadow-red-500/30 group-hover:shadow-red-500/50 transition-all duration-300">
              <Heart className="w-5 h-5 text-white fill-white" />
            </div>
            <span className="text-lg font-bold text-white">Suwa Sarana</span>
          </Link>
          <div className="flex items-center gap-4">
            <LanguageSwitcher />
            <Link
              href="/"
              className="inline-flex items-center gap-1.5 text-sm text-gray-400 hover:text-white transition-colors group"
            >
              <ArrowLeft className="w-4 h-4 group-hover:-translate-x-0.5 transition-transform" />
              {t('common.backToHome')}
            </Link>
          </div>
        </div>
      </nav>

      {/* Content */}
      <main className="pt-28 pb-20 px-6">
        <div className="max-w-4xl mx-auto">
          {/* Header */}
          <div className="text-center mb-14">
            <div className="inline-flex items-center justify-center w-16 h-16 rounded-2xl bg-gradient-to-br from-red-500 to-rose-600 shadow-xl shadow-red-500/30 mb-6">
              <Mail className="w-8 h-8 text-white" />
            </div>
            <h1 className="text-4xl font-bold text-white mb-4">{t('contact.title')}</h1>
            <p className="text-gray-400 max-w-xl mx-auto leading-relaxed">
              {t('contact.subtitle')}
            </p>
          </div>

          <div className="grid md:grid-cols-3 gap-6 mb-12">
            {/* Email */}
            <div className="bg-white/4 border border-white/8 rounded-2xl p-7 flex flex-col items-center text-center gap-4 hover:border-red-500/20 transition-all duration-300">
              <div className="w-12 h-12 rounded-xl bg-red-500/10 flex items-center justify-center">
                <Mail className="w-6 h-6 text-red-400" />
              </div>
              <div>
                <div className="font-semibold text-white mb-1">{t('contact.email')}</div>
                <a
                  href="mailto:support@suwasarana.lk"
                  className="text-sm text-gray-400 hover:text-red-400 transition-colors"
                >
                  support@suwasarana.lk
                </a>
              </div>
            </div>

            {/* Phone */}
            <div className="bg-white/4 border border-white/8 rounded-2xl p-7 flex flex-col items-center text-center gap-4 hover:border-red-500/20 transition-all duration-300">
              <div className="w-12 h-12 rounded-xl bg-orange-500/10 flex items-center justify-center">
                <Phone className="w-6 h-6 text-orange-400" />
              </div>
              <div>
                <div className="font-semibold text-white mb-1">{t('contact.hotline')}</div>
                <a
                  href="tel:+94112345678"
                  className="text-sm text-gray-400 hover:text-orange-400 transition-colors"
                >
                  +94 11 234 5678
                </a>
                <p className="text-xs text-gray-600 mt-1">{t('contact.hours')}</p>
              </div>
            </div>

            {/* Location */}
            <div className="bg-white/4 border border-white/8 rounded-2xl p-7 flex flex-col items-center text-center gap-4 hover:border-red-500/20 transition-all duration-300">
              <div className="w-12 h-12 rounded-xl bg-rose-500/10 flex items-center justify-center">
                <MapPin className="w-6 h-6 text-rose-400" />
              </div>
              <div>
                <div className="font-semibold text-white mb-1">{t('contact.locationTitle')}</div>
                <p className="text-sm text-gray-400">
                  {t('contact.locationDesc')}
                </p>
              </div>
            </div>
          </div>

          {/* Contact Form */}
          <div className="bg-white/4 border border-white/8 rounded-2xl p-8">
            <h2 className="text-xl font-semibold text-white mb-6">{t('contact.formTitle')}</h2>
            <form onSubmit={handleSubmit} className="space-y-5">
              {submitStatus === 'success' && (
                <div className="p-4 rounded-xl bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 text-sm">
                  {submitMessage}
                </div>
              )}
              {submitStatus === 'error' && (
                <div className="p-4 rounded-xl bg-red-500/10 border border-red-500/20 text-red-400 text-sm">
                  {submitMessage}
                </div>
              )}
              
              <div className="grid sm:grid-cols-2 gap-5">
                <div className="flex flex-col gap-1.5">
                  <label className="text-sm font-medium text-gray-300">{t('contact.nameLabel')}</label>
                  <input
                    type="text"
                    required
                    value={formData.name}
                    onChange={(e) => setFormData({...formData, name: e.target.value})}
                    placeholder={t('contact.namePlaceholder')}
                    className="w-full rounded-xl border border-white/10 bg-white/5 px-4 py-3 text-sm text-white placeholder-gray-500 focus:outline-none focus:border-red-500/60 focus:ring-2 focus:ring-red-500/20 transition-all"
                  />
                </div>
                <div className="flex flex-col gap-1.5">
                  <label className="text-sm font-medium text-gray-300">{t('contact.emailLabel')}</label>
                  <input
                    type="email"
                    required
                    value={formData.email}
                    onChange={(e) => setFormData({...formData, email: e.target.value})}
                    placeholder={t('contact.emailPlaceholder')}
                    className="w-full rounded-xl border border-white/10 bg-white/5 px-4 py-3 text-sm text-white placeholder-gray-500 focus:outline-none focus:border-red-500/60 focus:ring-2 focus:ring-red-500/20 transition-all"
                  />
                </div>
              </div>
              <div className="flex flex-col gap-1.5">
                <label className="text-sm font-medium text-gray-300">{t('contact.subjectLabel')}</label>
                <input
                  type="text"
                  required
                  value={formData.subject}
                  onChange={(e) => setFormData({...formData, subject: e.target.value})}
                  placeholder={t('contact.subjectPlaceholder')}
                  className="w-full rounded-xl border border-white/10 bg-white/5 px-4 py-3 text-sm text-white placeholder-gray-500 focus:outline-none focus:border-red-500/60 focus:ring-2 focus:ring-red-500/20 transition-all"
                />
              </div>
              <div className="flex flex-col gap-1.5">
                <label className="text-sm font-medium text-gray-300">{t('contact.messageLabel')}</label>
                <textarea
                  rows={5}
                  required
                  value={formData.message}
                  onChange={(e) => setFormData({...formData, message: e.target.value})}
                  placeholder={t('contact.messagePlaceholder')}
                  className="w-full rounded-xl border border-white/10 bg-white/5 px-4 py-3 text-sm text-white placeholder-gray-500 focus:outline-none focus:border-red-500/60 focus:ring-2 focus:ring-red-500/20 transition-all resize-none"
                />
              </div>
              <button
                type="submit"
                disabled={loading}
                className="w-full bg-gradient-to-r from-red-500 to-rose-600 hover:from-red-600 hover:to-rose-700 text-white font-semibold py-3 rounded-xl shadow-lg shadow-red-500/25 hover:shadow-red-500/40 transition-all duration-200 disabled:opacity-70 disabled:cursor-not-allowed flex justify-center items-center"
              >
                {loading ? t('contact.sending') : t('contact.sendBtn')}
              </button>
            </form>
          </div>
        </div>
      </main>

      {/* Footer */}
      <footer className="border-t border-white/5 py-8 px-6">
        <div className="max-w-7xl mx-auto flex flex-col md:flex-row items-center justify-between gap-4 text-sm text-gray-600">
          <div className="flex items-center gap-2">
            <Heart className="w-4 h-4 text-red-500 fill-red-500" />
            <span>Suwa Sarana © 2026. All rights reserved.</span>
          </div>
          <div className="flex gap-6">
            <Link href="/privacy" className="hover:text-gray-400 transition-colors">Privacy</Link>
            <Link href="/terms" className="hover:text-gray-400 transition-colors">Terms</Link>
            <Link href="/contact" className="text-red-400">Contact</Link>
          </div>
        </div>
      </footer>
    </div>
  );
}
