import Link from 'next/link';
import { Heart, ArrowLeft, FileText } from 'lucide-react';

export const metadata = {
  title: 'Terms of Service — Suwa Sarana',
  description: 'Read the Terms of Service for Suwa Sarana, Sri Lanka\'s blood donor matching platform.',
};

export default function TermsPage() {
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
            <div className="w-14 h-14 rounded-2xl bg-gradient-to-br from-orange-500 to-red-500 flex items-center justify-center shadow-lg shadow-orange-500/30 flex-shrink-0">
              <FileText className="w-7 h-7 text-white" />
            </div>
            <div>
              <h1 className="text-4xl font-bold text-white">Terms of Service</h1>
              <p className="text-gray-500 mt-1 text-sm">Last updated: September 2026</p>
            </div>
          </div>

          <div className="space-y-6">
            <section className="bg-white/4 border border-white/8 rounded-2xl p-7">
              <h2 className="text-xl font-semibold text-white mb-3">1. Acceptance of Terms</h2>
              <p className="text-gray-400 leading-relaxed">
                By creating an account and using Suwa Sarana, you agree to these Terms of Service. If you do not agree,
                please do not use this platform. We reserve the right to update these terms with notice.
              </p>
            </section>

            <section className="bg-white/4 border border-white/8 rounded-2xl p-7">
              <h2 className="text-xl font-semibold text-white mb-3">2. Eligibility</h2>
              <p className="text-gray-400 leading-relaxed">
                You must be at least 18 years old to register as a blood donor. Requesters must be healthcare professionals,
                patients, or authorized family members acting on behalf of patients in Sri Lanka.
              </p>
            </section>

            <section className="bg-white/4 border border-white/8 rounded-2xl p-7">
              <h2 className="text-xl font-semibold text-white mb-3">3. Donor Responsibilities</h2>
              <p className="text-gray-400 leading-relaxed">
                Donors must ensure their health eligibility before confirming a donation. Providing false blood type or
                availability information is strictly prohibited and may result in account suspension. Confirmed donations
                that are abandoned without notification will negatively affect your reliability score.
              </p>
            </section>

            <section className="bg-white/4 border border-white/8 rounded-2xl p-7">
              <h2 className="text-xl font-semibold text-white mb-3">4. Requester Responsibilities</h2>
              <p className="text-gray-400 leading-relaxed">
                Blood requests must be genuine medical emergencies or scheduled needs. Submitting false requests is a
                serious misuse of the platform and will result in immediate account termination and potential legal action.
              </p>
            </section>

            <section className="bg-white/4 border border-white/8 rounded-2xl p-7">
              <h2 className="text-xl font-semibold text-white mb-3">5. Limitation of Liability</h2>
              <p className="text-gray-400 leading-relaxed">
                Suwa Sarana is a matching platform and is not responsible for the outcome of any blood donation or
                medical procedure. We do not provide medical advice. Always consult qualified healthcare professionals
                for medical decisions.
              </p>
            </section>

            <section className="bg-white/4 border border-white/8 rounded-2xl p-7">
              <h2 className="text-xl font-semibold text-white mb-3">6. Account Termination</h2>
              <p className="text-gray-400 leading-relaxed">
                We reserve the right to suspend or terminate accounts that violate these terms, provide false information,
                abuse the platform, or engage in any activity harmful to donors, requesters, or patients.
              </p>
            </section>

            <section className="bg-white/4 border border-white/8 rounded-2xl p-7">
              <h2 className="text-xl font-semibold text-white mb-3">7. Contact</h2>
              <p className="text-gray-400 leading-relaxed">
                For questions about these Terms, contact us at{' '}
                <a href="mailto:legal@suwasarana.lk" className="text-red-400 hover:text-red-300 transition-colors">
                  legal@suwasarana.lk
                </a>
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
            <Link href="/privacy" className="hover:text-gray-400 transition-colors">Privacy</Link>
            <Link href="/terms" className="text-red-400">Terms</Link>
            <Link href="/contact" className="hover:text-gray-400 transition-colors">Contact</Link>
          </div>
        </div>
      </footer>
    </div>
  );
}
