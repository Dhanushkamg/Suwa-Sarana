'use client';

import Link from 'next/link';
import { Heart, ArrowLeft, Shield } from 'lucide-react';
import { useI18n } from '@/lib/i18n';
import { LanguageSwitcher } from '@/components/ui/LanguageSwitcher';

export default function PrivacyClient() {
  const { t } = useI18n();
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
        <div className="max-w-3xl mx-auto">
          {/* Header */}
          <div className="flex items-center gap-4 mb-10">
            <div className="w-14 h-14 rounded-2xl bg-gradient-to-br from-red-500 to-rose-600 flex items-center justify-center shadow-lg shadow-red-500/30 flex-shrink-0">
              <Shield className="w-7 h-7 text-white" />
            </div>
            <div>
              <h1 className="text-4xl font-bold text-white">{t('privacy.title')}</h1>
              <p className="text-gray-500 mt-1 text-sm">{t('privacy.lastUpdated')}</p>
            </div>
          </div>

          <div className="prose prose-invert max-w-none space-y-8">
            <section className="bg-white/4 border border-white/8 rounded-2xl p-7">
              <h2 className="text-xl font-semibold text-white mb-3">{t('privacy.s1Title')}</h2>
              <p className="text-gray-400 leading-relaxed">
                {t('privacy.s1Desc')}
              </p>
            </section>

            <section className="bg-white/4 border border-white/8 rounded-2xl p-7">
              <h2 className="text-xl font-semibold text-white mb-3">{t('privacy.s2Title')}</h2>
              <p className="text-gray-400 leading-relaxed">
                {t('privacy.s2Desc')}
              </p>
            </section>

            <section className="bg-white/4 border border-white/8 rounded-2xl p-7">
              <h2 className="text-xl font-semibold text-white mb-3">{t('privacy.s3Title')}</h2>
              <p className="text-gray-400 leading-relaxed">
                {t('privacy.s3Desc')}
              </p>
            </section>

            <section className="bg-white/4 border border-white/8 rounded-2xl p-7">
              <h2 className="text-xl font-semibold text-white mb-3">{t('privacy.s4Title')}</h2>
              <p className="text-gray-400 leading-relaxed">
                {t('privacy.s4Desc')}
              </p>
            </section>

            <section className="bg-white/4 border border-white/8 rounded-2xl p-7">
              <h2 className="text-xl font-semibold text-white mb-3">{t('privacy.s5Title')}</h2>
              <p className="text-gray-400 leading-relaxed">
                {t('privacy.s5Desc')} {' '}
                <a href="mailto:privacy@suwasarana.lk" className="text-red-400 hover:text-red-300 transition-colors">
                  privacy@suwasarana.lk
                </a>
                .
              </p>
            </section>

            <section className="bg-white/4 border border-white/8 rounded-2xl p-7">
              <h2 className="text-xl font-semibold text-white mb-3">{t('privacy.s6Title')}</h2>
              <p className="text-gray-400 leading-relaxed">
                {t('privacy.s6Desc')} {' '}
                <a href="mailto:privacy@suwasarana.lk" className="text-red-400 hover:text-red-300 transition-colors">
                  privacy@suwasarana.lk
                </a>{' '}
                or visit our{' '}
                <Link href="/contact" className="text-red-400 hover:text-red-300 transition-colors">
                  Contact page
                </Link>
                .
              </p>
            </section>
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
            <Link href="/privacy" className="text-red-400">Privacy</Link>
            <Link href="/terms" className="hover:text-gray-400 transition-colors">Terms</Link>
            <Link href="/contact" className="hover:text-gray-400 transition-colors">Contact</Link>
          </div>
        </div>
      </footer>
    </div>
  );
}
