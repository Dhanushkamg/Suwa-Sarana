import Link from 'next/link';
import { Heart, Zap, Shield, Users, ArrowRight, MapPin, Clock, Star } from 'lucide-react';

export default function Home() {
  return (
    <div className="min-h-screen bg-[#0d0d14] text-white overflow-x-hidden">
      {/* Navigation */}
      <nav className="fixed top-0 inset-x-0 z-50 border-b border-white/5 bg-[#0d0d14]/80 backdrop-blur-xl">
        <div className="max-w-7xl mx-auto px-6 h-16 flex items-center justify-between">
          <Link href="/" className="flex items-center gap-2.5 group">
            <div className="w-9 h-9 rounded-xl bg-gradient-to-br from-red-500 to-rose-600 flex items-center justify-center shadow-lg shadow-red-500/30 group-hover:shadow-red-500/50 transition-all duration-300">
              <Heart className="w-4.5 h-4.5 text-white fill-white" />
            </div>
            <span className="text-lg font-bold text-white">Suwa Sarana</span>
          </Link>

          <div className="hidden md:flex items-center gap-8 text-sm text-gray-400">
            <Link href="#how-it-works" className="hover:text-white transition-colors">How it works</Link>
            <Link href="#features" className="hover:text-white transition-colors">Features</Link>
            <Link href="#stats" className="hover:text-white transition-colors">Impact</Link>
          </div>

          <div className="flex items-center gap-3">
            <Link
              href="/login"
              className="text-sm text-gray-400 hover:text-white transition-colors px-4 py-2 rounded-xl hover:bg-white/5"
            >
              Sign in
            </Link>
            <Link
              href="/register"
              className="text-sm font-semibold bg-gradient-to-r from-red-500 to-rose-600 hover:from-red-600 hover:to-rose-700 text-white px-5 py-2 rounded-xl shadow-lg shadow-red-500/25 hover:shadow-red-500/40 transition-all duration-200"
            >
              Donate blood
            </Link>
          </div>
        </div>
      </nav>

      {/* Hero */}
      <section className="relative pt-32 pb-24 px-6">
        {/* Background elements */}
        <div className="absolute inset-0 overflow-hidden pointer-events-none">
          <div className="absolute top-20 left-1/4 w-96 h-96 rounded-full bg-red-600/10 blur-3xl animate-pulse" />
          <div className="absolute bottom-0 right-1/4 w-80 h-80 rounded-full bg-rose-500/8 blur-3xl animate-pulse" style={{ animationDelay: '1s' }} />
        </div>

        <div className="relative max-w-5xl mx-auto text-center">
          {/* Badge */}
          <div className="inline-flex items-center gap-2 px-4 py-2 rounded-full bg-red-500/10 border border-red-500/20 text-red-400 text-xs font-medium mb-8">
            <span className="w-1.5 h-1.5 rounded-full bg-red-400 animate-ping" />
            Sri Lanka&apos;s first real-time donor matching platform
          </div>

          <h1 className="text-6xl md:text-7xl font-bold tracking-tight mb-6 leading-tight">
            Every{' '}
            <span className="gradient-text">drop</span>
            {' '}counts.
            <br />
            Every{' '}
            <span className="gradient-text">second</span>
            {' '}matters.
          </h1>

          <p className="text-xl text-gray-400 max-w-2xl mx-auto leading-relaxed mb-10">
            Suwa Sarana connects voluntary blood donors with patients across Sri Lanka — instantly, intelligently, and in your language.
          </p>

          <div className="flex flex-col sm:flex-row items-center justify-center gap-4">
            <Link
              href="/register"
              className="group inline-flex items-center gap-2 bg-gradient-to-r from-red-500 to-rose-600 hover:from-red-600 hover:to-rose-700 text-white font-semibold text-base px-8 py-4 rounded-2xl shadow-xl shadow-red-500/30 hover:shadow-red-500/50 transition-all duration-300 hover:-translate-y-0.5"
            >
              Register as donor
              <ArrowRight className="w-5 h-5 group-hover:translate-x-0.5 transition-transform" />
            </Link>
            <Link
              href="/register?role=REQUESTER"
              className="inline-flex items-center gap-2 bg-white/8 hover:bg-white/12 text-white font-semibold text-base px-8 py-4 rounded-2xl border border-white/12 hover:border-white/24 backdrop-blur-sm transition-all duration-300 hover:-translate-y-0.5"
            >
              Request blood
            </Link>
          </div>
        </div>
      </section>

      {/* Stats */}
      <section id="stats" className="py-16 px-6">
        <div className="max-w-5xl mx-auto grid grid-cols-2 md:grid-cols-4 gap-4">
          {[
            { value: '466K+', label: 'Units collected yearly', icon: '🩸' },
            { value: '108', label: 'Blood banks covered', icon: '🏥' },
            { value: '24', label: 'Districts connected', icon: '🗺️' },
            { value: '8', label: 'Blood types matched', icon: '🔬' },
          ].map((stat) => (
            <div
              key={stat.label}
              className="glass-card rounded-2xl p-6 text-center transition-all duration-300 hover:scale-105 hover:border-red-500/20"
            >
              <div className="text-3xl mb-2">{stat.icon}</div>
              <div className="text-3xl font-bold text-white mb-1">{stat.value}</div>
              <div className="text-xs text-gray-500 leading-tight">{stat.label}</div>
            </div>
          ))}
        </div>
      </section>

      {/* How it works */}
      <section id="how-it-works" className="py-20 px-6">
        <div className="max-w-5xl mx-auto">
          <div className="text-center mb-14">
            <h2 className="text-4xl font-bold text-white mb-4">How it works</h2>
            <p className="text-gray-500 max-w-xl mx-auto">Three steps to save a life</p>
          </div>

          <div className="grid md:grid-cols-3 gap-6">
            {[
              {
                step: '01',
                icon: Users,
                title: 'Register & Set Up',
                desc: 'Create your account, provide your blood type, and share your location. Donors set availability; requesters create patient profiles.',
                color: 'from-red-500 to-rose-600',
              },
              {
                step: '02',
                icon: Zap,
                title: 'Smart Matching',
                desc: 'Our engine instantly matches compatible, eligible donors near the hospital — using geospatial scoring and reliability ratings.',
                color: 'from-orange-500 to-red-500',
              },
              {
                step: '03',
                icon: Shield,
                title: 'Respond & Save',
                desc: 'Matched donors receive real-time alerts, confirm availability, and donate. The request auto-escalates if no response in time.',
                color: 'from-rose-500 to-pink-600',
              },
            ].map((item) => (
              <div
                key={item.step}
                className="glass-card rounded-2xl p-8 transition-all duration-300 hover:-translate-y-1 hover:border-red-500/20 group"
              >
                <div className="flex items-start gap-4 mb-5">
                  <div className={`w-12 h-12 rounded-xl bg-gradient-to-br ${item.color} flex items-center justify-center shadow-lg flex-shrink-0 group-hover:scale-110 transition-transform duration-300`}>
                    <item.icon className="w-5 h-5 text-white" />
                  </div>
                  <span className="text-5xl font-black text-white/5 leading-none mt-1">{item.step}</span>
                </div>
                <h3 className="text-lg font-semibold text-white mb-3">{item.title}</h3>
                <p className="text-gray-500 text-sm leading-relaxed">{item.desc}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* Features */}
      <section id="features" className="py-20 px-6">
        <div className="max-w-5xl mx-auto">
          <div className="text-center mb-14">
            <h2 className="text-4xl font-bold text-white mb-4">Built for real emergencies</h2>
            <p className="text-gray-500 max-w-xl mx-auto">Every feature designed to save time when every minute counts</p>
          </div>

          <div className="grid md:grid-cols-2 gap-4">
            {[
              {
                icon: MapPin,
                title: 'Geospatial donor matching',
                desc: 'PostGIS-powered radius search finds the closest compatible donors first, then auto-expands if no match is found.',
              },
              {
                icon: Zap,
                title: 'Real-time SSE notifications',
                desc: 'Donors receive instant Server-Sent Event alerts the moment a matching request is raised in their area.',
              },
              {
                icon: Clock,
                title: 'Escalation engine',
                desc: 'Automated escalation every 15 minutes widens the search radius until a match is confirmed or the request expires.',
              },
              {
                icon: Star,
                title: 'Reliability scoring',
                desc: 'Donors earn score for confirmed donations; no-shows are penalized — prioritising the most reliable donors first.',
              },
            ].map((feat) => (
              <div
                key={feat.title}
                className="glass-card rounded-2xl p-7 flex gap-5 transition-all duration-300 hover:-translate-y-0.5 hover:border-red-500/20 group"
              >
                <div className="w-11 h-11 rounded-xl bg-red-500/10 flex items-center justify-center flex-shrink-0 group-hover:bg-red-500/20 transition-colors duration-300">
                  <feat.icon className="w-5 h-5 text-red-400" />
                </div>
                <div>
                  <h3 className="font-semibold text-white mb-1.5">{feat.title}</h3>
                  <p className="text-sm text-gray-500 leading-relaxed">{feat.desc}</p>
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
              <h2 className="text-4xl font-bold text-white mb-4">Be someone&apos;s hero today</h2>
              <p className="text-gray-400 mb-8 max-w-xl mx-auto">
                One unit of blood can save up to three lives. Register as a donor and be ready when your community needs you most.
              </p>
              <Link
                href="/register"
                className="inline-flex items-center gap-2 bg-gradient-to-r from-red-500 to-rose-600 hover:from-red-600 hover:to-rose-700 text-white font-semibold text-lg px-10 py-4 rounded-2xl shadow-xl shadow-red-500/30 hover:shadow-red-500/50 transition-all duration-300 hover:-translate-y-0.5"
              >
                Register now — it&apos;s free
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
            <span>Suwa Sarana © 2026. All rights reserved.</span>
          </div>
          <div className="flex gap-6">
            <Link href="/privacy" className="hover:text-gray-400 transition-colors">Privacy</Link>
            <Link href="/terms" className="hover:text-gray-400 transition-colors">Terms</Link>
            <Link href="/contact" className="hover:text-gray-400 transition-colors">Contact</Link>
          </div>
        </div>
      </footer>
    </div>
  );
}
