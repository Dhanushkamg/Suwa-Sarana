'use client';

import { useI18n } from '@/lib/i18n';
import Link from 'next/link';
import { Heart, Zap, Shield, Users, ArrowRight, MapPin, Clock, Star, Calendar } from 'lucide-react';
import { LanguageSwitcher } from '@/components/ui/LanguageSwitcher';
import { StockTicker } from '@/components/ui/StockTicker';

export default function Home() {
  const { t } = useI18n();

  return (
    <div className="min-h-screen bg-[#0d0d14] text-white overflow-x-hidden">
      {/* Navigation */}
      <nav className="fixed top-0 inset-x-0 z-50 border-b border-white/5 bg-[#0d0d14]/80 backdrop-blur-xl">
        <div className="max-w-7xl mx-auto px-6 h-16 flex items-center justify-between">
          <Link href="/" className="flex items-center gap-2.5 group">
            <div className="w-9 h-9 rounded-xl bg-gradient-to-br from-red-500 to-rose-600 flex items-center justify-center shadow-lg shadow-red-500/30 group-hover:shadow-red-500/50 transition-all duration-300">
              <Heart className="w-5 h-5 text-white fill-white" />
            </div>
            <span className="text-lg font-bold text-white">Suwa Sarana</span>
          </Link>

          <div className="hidden md:flex items-center gap-8 text-sm text-gray-400">
            <Link href="#how-it-works" className="hover:text-white transition-colors">{t('nav.howItWorks')}</Link>
            <Link href="#features" className="hover:text-white transition-colors">{t('nav.features')}</Link>
            <Link href="/camps" className="hover:text-white transition-colors flex items-center gap-1.5"><Calendar className="w-3.5 h-3.5 text-red-400" /> Donation Camps</Link>
            <Link href="#stats" className="hover:text-white transition-colors">{t('nav.impact')}</Link>
          </div>

          <div className="flex items-center gap-2">
            <LanguageSwitcher />
            <Link
              href="/login"
              className="text-sm text-gray-400 hover:text-white transition-colors px-4 py-2 rounded-xl hover:bg-white/5"
            >
              {t('nav.signIn')}
            </Link>
            <Link
              href="/register"
              className="text-sm font-semibold bg-gradient-to-r from-red-500 to-rose-600 hover:from-red-600 hover:to-rose-700 text-white px-5 py-2 rounded-xl shadow-lg shadow-red-500/25 hover:shadow-red-500/40 transition-all duration-200"
            >
              {t('nav.donateBlood')}
            </Link>
          </div>
        </div>
      </nav>

      {/* Stock Ticker Banner */}
      <div className="pt-16">
        <StockTicker />
      </div>

      {/* Hero */}
      <section className="relative pt-16 pb-24 px-6">
        <div className="absolute inset-0 overflow-hidden pointer-events-none">
          <div className="absolute top-20 left-1/4 w-96 h-96 rounded-full bg-red-600/10 blur-3xl animate-pulse" />
          <div className="absolute bottom-0 right-1/4 w-80 h-80 rounded-full bg-rose-500/8 blur-3xl animate-pulse" style={{ animationDelay: '1s' }} />
        </div>

        <div className="relative max-w-5xl mx-auto text-center">
          <div className="inline-flex items-center gap-2 px-4 py-2 rounded-full bg-red-500/10 border border-red-500/20 text-red-400 text-xs font-medium mb-8">
            <span className="w-1.5 h-1.5 rounded-full bg-red-400 animate-ping" />
            {t('hero.badge')}
          </div>

          <h1 className="text-6xl md:text-7xl font-bold tracking-tight mb-6 leading-tight">
            {t('hero.headline1')}{' '}
            <span className="gradient-text">{t('hero.headline2')}</span>{' '}
            {t('hero.headline3')}
            <br />
            {t('hero.headline4')}{' '}
            <span className="gradient-text">{t('hero.headline5')}</span>{' '}
            {t('hero.headline6')}
          </h1>

          <p className="text-xl text-gray-400 max-w-2xl mx-auto leading-relaxed mb-10">
            {t('hero.subtitle')}
          </p>

          <div className="flex flex-col sm:flex-row items-center justify-center gap-4">
            <Link
              href="/register"
              className="group inline-flex items-center gap-2 bg-gradient-to-r from-red-500 to-rose-600 hover:from-red-600 hover:to-rose-700 text-white font-semibold text-base px-8 py-4 rounded-2xl shadow-xl shadow-red-500/30 hover:shadow-red-500/50 transition-all duration-300 hover:-translate-y-0.5"
            >
              {t('hero.registerDonor')}
              <ArrowRight className="w-5 h-5 group-hover:translate-x-0.5 transition-transform" />
            </Link>
            <Link
              href="/register?role=REQUESTER"
              className="inline-flex items-center gap-2 bg-white/8 hover:bg-white/12 text-white font-semibold text-base px-8 py-4 rounded-2xl border border-white/12 hover:border-white/24 backdrop-blur-sm transition-all duration-300 hover:-translate-y-0.5"
            >
              {t('hero.requestBlood')}
            </Link>
          </div>
        </div>
      </section>

      {/* Stats */}
      <section id="stats" className="py-16 px-6">
        <div className="max-w-5xl mx-auto grid grid-cols-2 md:grid-cols-4 gap-4">
          {[
            { value: '466K+', labelKey: 'stats.units', icon: '🩸' },
            { value: '108', labelKey: 'stats.bloodBanks', icon: '🏥' },
            { value: '24', labelKey: 'stats.districts', icon: '🗺️' },
            { value: '8', labelKey: 'stats.bloodTypes', icon: '🔬' },
          ].map((stat) => (
            <div
              key={stat.labelKey}
              className="glass-card rounded-2xl p-6 text-center transition-all duration-300 hover:scale-105 hover:border-red-500/20"
            >
              <div className="text-3xl mb-2">{stat.icon}</div>
              <div className="text-3xl font-bold text-white mb-1">{stat.value}</div>
              <div className="text-xs text-gray-500 leading-tight">{t(stat.labelKey )}</div>
            </div>
          ))}
        </div>
      </section>

      {/* How it works */}
      <section id="how-it-works" className="py-20 px-6">
        <div className="max-w-5xl mx-auto">
          <div className="text-center mb-14">
            <h2 className="text-4xl font-bold text-white mb-4">{t('howItWorks.title')}</h2>
            <p className="text-gray-500 max-w-xl mx-auto">{t('howItWorks.subtitle')}</p>
          </div>

          <div className="grid md:grid-cols-3 gap-6">
            {[
              { step: '01', icon: Users, titleKey: 'howItWorks.step1Title', descKey: 'howItWorks.step1Desc', color: 'from-red-500 to-rose-600' },
              { step: '02', icon: Zap, titleKey: 'howItWorks.step2Title', descKey: 'howItWorks.step2Desc', color: 'from-orange-500 to-red-500' },
              { step: '03', icon: Shield, titleKey: 'howItWorks.step3Title', descKey: 'howItWorks.step3Desc', color: 'from-rose-500 to-pink-600' },
            ].map((item) => (
              <div key={item.step} className="glass-card rounded-2xl p-8 transition-all duration-300 hover:-translate-y-1 hover:border-red-500/20 group">
                <div className="flex items-start gap-4 mb-5">
                  <div className={`w-12 h-12 rounded-xl bg-gradient-to-br ${item.color} flex items-center justify-center shadow-lg flex-shrink-0 group-hover:scale-110 transition-transform duration-300`}>
                    <item.icon className="w-5 h-5 text-white" />
                  </div>
                  <span className="text-5xl font-black text-white/5 leading-none mt-1">{item.step}</span>
                </div>
                <h3 className="text-lg font-semibold text-white mb-3">{t(item.titleKey )}</h3>
                <p className="text-gray-500 text-sm leading-relaxed">{t(item.descKey )}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* Features */}
      <section id="features" className="py-20 px-6">
        <div className="max-w-5xl mx-auto">
          <div className="text-center mb-14">
            <h2 className="text-4xl font-bold text-white mb-4">{t('features.title')}</h2>
            <p className="text-gray-500 max-w-xl mx-auto">{t('features.subtitle')}</p>
          </div>

          <div className="grid md:grid-cols-2 gap-4">
            {[
              { icon: MapPin, titleKey: 'features.geo', descKey: 'features.geoDesc' },
              { icon: Zap, titleKey: 'features.sse', descKey: 'features.sseDesc' },
              { icon: Clock, titleKey: 'features.escalation', descKey: 'features.escalationDesc' },
              { icon: Star, titleKey: 'features.reliability', descKey: 'features.reliabilityDesc' },
            ].map((feat) => (
              <div key={feat.titleKey} className="glass-card rounded-2xl p-7 flex gap-5 transition-all duration-300 hover:-translate-y-0.5 hover:border-red-500/20 group">
                <div className="w-11 h-11 rounded-xl bg-red-500/10 flex items-center justify-center flex-shrink-0 group-hover:bg-red-500/20 transition-colors duration-300">
                  <feat.icon className="w-5 h-5 text-red-400" />
                </div>
                <div>
                  <h3 className="font-semibold text-white mb-1.5">{t(feat.titleKey )}</h3>
                  <p className="text-sm text-gray-500 leading-relaxed">{t(feat.descKey )}</p>
                </div>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* CTA */}
      <section className="py-24 px-6">
        <div className="max-w-3xl mx-auto text-center">
          <div className="glass-card rounded-3xl p-12 border-red-500/10 relative overflow-hidden">
            <div className="absolute inset-0 bg-gradient-to-br from-red-500/5 to-transparent pointer-events-none" />
            <div className="relative">
              <div className="inline-flex items-center justify-center w-16 h-16 rounded-2xl bg-gradient-to-br from-red-500 to-rose-600 shadow-xl shadow-red-500/30 mb-6 animate-pulse-glow">
                <Heart className="w-8 h-8 text-white fill-white" />
              </div>
              <h2 className="text-4xl font-bold text-white mb-4">{t('cta.title')}</h2>
              <p className="text-gray-400 mb-8 max-w-xl mx-auto">{t('cta.subtitle')}</p>
              <Link
                href="/register"
                className="inline-flex items-center gap-2 bg-gradient-to-r from-red-500 to-rose-600 hover:from-red-600 hover:to-rose-700 text-white font-semibold text-lg px-10 py-4 rounded-2xl shadow-xl shadow-red-500/30 hover:shadow-red-500/50 transition-all duration-300 hover:-translate-y-0.5"
              >
                {t('cta.button')}
                <ArrowRight className="w-5 h-5" />
              </Link>
            </div>
          </div>
        </div>
      </section>

      {/* Footer */}
      <footer className="border-t border-white/5 py-8 px-6">
        <div className="max-w-7xl mx-auto flex flex-col md:flex-row items-center justify-between gap-4 text-sm text-gray-600">
          <div className="flex items-center gap-2">
            <Heart className="w-4 h-4 text-red-500 fill-red-500" />
            <span>{t('footer.rights')}</span>
          </div>
          <div className="flex gap-6">
            <Link href="/privacy" className="hover:text-gray-400 transition-colors">{t('footer.privacy')}</Link>
            <Link href="/terms" className="hover:text-gray-400 transition-colors">{t('footer.terms')}</Link>
            <Link href="/contact" className="hover:text-gray-400 transition-colors">{t('footer.contact')}</Link>
          </div>
        </div>
      </footer>
    </div>
  );
}

