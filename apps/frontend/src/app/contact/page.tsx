import Link from 'next/link';
import { Heart, ArrowLeft, Mail, Phone, MapPin } from 'lucide-react';

export const metadata = {
  title: 'Contact Us — Suwa Sarana',
  description: 'Get in touch with the Suwa Sarana team for support, partnerships, or general enquiries.',
};

export default function ContactPage() {
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
          <Link
            href="/"
            className="inline-flex items-center gap-1.5 text-sm text-gray-400 hover:text-white transition-colors group"
          >
            <ArrowLeft className="w-4 h-4 group-hover:-translate-x-0.5 transition-transform" />
            Back to Home
          </Link>
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
            <h1 className="text-4xl font-bold text-white mb-4">Contact Us</h1>
            <p className="text-gray-400 max-w-xl mx-auto leading-relaxed">
              Have a question, found a bug, or want to partner with us? We&apos;re here to help.
              Reach out and we&apos;ll respond within 24 hours.
            </p>
          </div>

          <div className="grid md:grid-cols-3 gap-6 mb-12">
            {/* Email */}
            <div className="bg-white/4 border border-white/8 rounded-2xl p-7 flex flex-col items-center text-center gap-4 hover:border-red-500/20 transition-all duration-300">
              <div className="w-12 h-12 rounded-xl bg-red-500/10 flex items-center justify-center">
                <Mail className="w-6 h-6 text-red-400" />
              </div>
              <div>
                <div className="font-semibold text-white mb-1">Email</div>
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
                <div className="font-semibold text-white mb-1">Hotline</div>
                <a
                  href="tel:+94112345678"
                  className="text-sm text-gray-400 hover:text-orange-400 transition-colors"
                >
                  +94 11 234 5678
                </a>
                <p className="text-xs text-gray-600 mt-1">Mon–Fri, 8am–6pm</p>
              </div>
            </div>

            {/* Location */}
            <div className="bg-white/4 border border-white/8 rounded-2xl p-7 flex flex-col items-center text-center gap-4 hover:border-red-500/20 transition-all duration-300">
              <div className="w-12 h-12 rounded-xl bg-rose-500/10 flex items-center justify-center">
                <MapPin className="w-6 h-6 text-rose-400" />
              </div>
              <div>
                <div className="font-semibold text-white mb-1">Location</div>
                <p className="text-sm text-gray-400">
                  Colombo 07,<br />Western Province,<br />Sri Lanka
                </p>
              </div>
            </div>
          </div>

          {/* Contact Form */}
          <div className="bg-white/4 border border-white/8 rounded-2xl p-8">
            <h2 className="text-xl font-semibold text-white mb-6">Send us a message</h2>
            <form className="space-y-5">
              <div className="grid sm:grid-cols-2 gap-5">
                <div className="flex flex-col gap-1.5">
                  <label className="text-sm font-medium text-gray-300">Full Name</label>
                  <input
                    type="text"
                    placeholder="Your name"
                    className="w-full rounded-xl border border-white/10 bg-white/5 px-4 py-3 text-sm text-white placeholder-gray-500 focus:outline-none focus:border-red-500/60 focus:ring-2 focus:ring-red-500/20 transition-all"
                  />
                </div>
                <div className="flex flex-col gap-1.5">
                  <label className="text-sm font-medium text-gray-300">Email Address</label>
                  <input
                    type="email"
                    placeholder="you@example.com"
                    className="w-full rounded-xl border border-white/10 bg-white/5 px-4 py-3 text-sm text-white placeholder-gray-500 focus:outline-none focus:border-red-500/60 focus:ring-2 focus:ring-red-500/20 transition-all"
                  />
                </div>
              </div>
              <div className="flex flex-col gap-1.5">
                <label className="text-sm font-medium text-gray-300">Subject</label>
                <input
                  type="text"
                  placeholder="How can we help?"
                  className="w-full rounded-xl border border-white/10 bg-white/5 px-4 py-3 text-sm text-white placeholder-gray-500 focus:outline-none focus:border-red-500/60 focus:ring-2 focus:ring-red-500/20 transition-all"
                />
              </div>
              <div className="flex flex-col gap-1.5">
                <label className="text-sm font-medium text-gray-300">Message</label>
                <textarea
                  rows={5}
                  placeholder="Describe your question or issue in detail..."
                  className="w-full rounded-xl border border-white/10 bg-white/5 px-4 py-3 text-sm text-white placeholder-gray-500 focus:outline-none focus:border-red-500/60 focus:ring-2 focus:ring-red-500/20 transition-all resize-none"
                />
              </div>
              <button
                type="submit"
                className="w-full bg-gradient-to-r from-red-500 to-rose-600 hover:from-red-600 hover:to-rose-700 text-white font-semibold py-3 rounded-xl shadow-lg shadow-red-500/25 hover:shadow-red-500/40 transition-all duration-200"
              >
                Send Message
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
