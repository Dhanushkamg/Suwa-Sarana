import Link from 'next/link';
import { Heart, ArrowLeft, Shield } from 'lucide-react';

export const metadata = {
  title: 'Privacy Policy — Suwa Sarana',
  description: 'Learn how Suwa Sarana collects, uses, and protects your personal data.',
};

export default function PrivacyPage() {
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
        <div className="max-w-3xl mx-auto">
          {/* Header */}
          <div className="flex items-center gap-4 mb-10">
            <div className="w-14 h-14 rounded-2xl bg-gradient-to-br from-red-500 to-rose-600 flex items-center justify-center shadow-lg shadow-red-500/30 flex-shrink-0">
              <Shield className="w-7 h-7 text-white" />
            </div>
            <div>
              <h1 className="text-4xl font-bold text-white">Privacy Policy</h1>
              <p className="text-gray-500 mt-1 text-sm">Last updated: September 2026</p>
            </div>
          </div>

          <div className="prose prose-invert max-w-none space-y-8">
            <section className="bg-white/4 border border-white/8 rounded-2xl p-7">
              <h2 className="text-xl font-semibold text-white mb-3">1. Information We Collect</h2>
              <p className="text-gray-400 leading-relaxed">
                Suwa Sarana collects information you provide directly, such as your name, email address, phone number,
                blood type, and district of residence. We also collect location data when you consent to share it for
                donor matching purposes.
              </p>
            </section>

            <section className="bg-white/4 border border-white/8 rounded-2xl p-7">
              <h2 className="text-xl font-semibold text-white mb-3">2. How We Use Your Information</h2>
              <p className="text-gray-400 leading-relaxed">
                Your data is used exclusively to connect blood donors with patients in need across Sri Lanka. We use
                your blood type and location to find the most compatible, nearest donor. Your contact information is
                used only to send verified blood request notifications.
              </p>
            </section>

            <section className="bg-white/4 border border-white/8 rounded-2xl p-7">
              <h2 className="text-xl font-semibold text-white mb-3">3. Data Sharing</h2>
              <p className="text-gray-400 leading-relaxed">
                We do not sell, trade, or rent your personal information to third parties. Your details may be shared
                with hospitals or healthcare providers only in the context of a confirmed blood request, and only with
                your consent.
              </p>
            </section>

            <section className="bg-white/4 border border-white/8 rounded-2xl p-7">
              <h2 className="text-xl font-semibold text-white mb-3">4. Data Security</h2>
              <p className="text-gray-400 leading-relaxed">
                We implement industry-standard security measures including JWT authentication, encrypted tokens, and
                HTTPS-only communication to protect your personal information from unauthorized access.
              </p>
            </section>

            <section className="bg-white/4 border border-white/8 rounded-2xl p-7">
              <h2 className="text-xl font-semibold text-white mb-3">5. Your Rights</h2>
              <p className="text-gray-400 leading-relaxed">
                You have the right to access, correct, or delete your personal data at any time. You may also withdraw
                consent for location sharing or notifications from your donor profile settings. To request data
                deletion, contact us at{' '}
                <a href="mailto:privacy@suwasarana.lk" className="text-red-400 hover:text-red-300 transition-colors">
                  privacy@suwasarana.lk
                </a>
                .
              </p>
            </section>

            <section className="bg-white/4 border border-white/8 rounded-2xl p-7">
              <h2 className="text-xl font-semibold text-white mb-3">6. Contact Us</h2>
              <p className="text-gray-400 leading-relaxed">
                If you have any questions about this Privacy Policy, please contact us at{' '}
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
