'use client';

import { useState } from 'react';
import Link from 'next/link';
import { Mail, ArrowLeft, CheckCircle2, AlertCircle } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { useI18n } from '@/lib/i18n';

export default function ForgotPasswordPage() {
  const { t } = useI18n();
  const [email, setEmail] = useState('');
  const [loading, setLoading] = useState(false);
  const [submitted, setSubmitted] = useState(false);
  const [error, setError] = useState('');

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    if (!email.trim() || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      setError('Please enter a valid email address');
      return;
    }

    setLoading(true);
    // Simulate password reset request
    setTimeout(() => {
      setLoading(false);
      setSubmitted(true);
    }, 800);
  };

  return (
    <div>
      <div className="mb-8">
        <h2 className="text-3xl font-bold text-white">Reset Password</h2>
        <p className="text-gray-400 mt-2">
          Enter your registered email address and we&apos;ll send you instructions to reset your password.
        </p>
      </div>

      {submitted ? (
        <div className="glass-card rounded-2xl p-6 border-emerald-500/20 bg-emerald-500/5 space-y-4">
          <div className="flex items-start gap-3 text-emerald-400">
            <CheckCircle2 className="w-6 h-6 flex-shrink-0 mt-0.5" />
            <div>
              <h3 className="font-semibold text-white">Reset instructions sent!</h3>
              <p className="text-sm text-gray-300 mt-1 leading-relaxed">
                If an account exists for <span className="text-white font-medium">{email}</span>, you will receive password reset instructions shortly.
              </p>
            </div>
          </div>
          <div className="pt-2">
            <Link href="/login">
              <Button size="lg" className="w-full">
                Back to Sign In
              </Button>
            </Link>
          </div>
        </div>
      ) : (
        <form onSubmit={handleSubmit} className="space-y-4" noValidate>
          {error && (
            <div className="mb-4 rounded-xl border border-red-500/30 bg-red-500/10 px-4 py-3 text-sm text-red-400 flex items-center gap-2">
              <AlertCircle className="w-4 h-4 flex-shrink-0" />
              <span>{error}</span>
            </div>
          )}

          <Input
            id="forgot-email"
            type="email"
            label={t('auth.email')}
            placeholder="you@example.com"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            icon={<Mail className="w-4 h-4" />}
            autoComplete="email"
            required
          />

          <div className="pt-2">
            <Button type="submit" size="lg" className="w-full" loading={loading}>
              Send Reset Link
            </Button>
          </div>

          <div className="mt-6 text-center">
            <Link
              href="/login"
              className="inline-flex items-center gap-1.5 text-sm text-gray-400 hover:text-white transition-colors"
            >
              <ArrowLeft className="w-4 h-4" />
              {t('auth.signInLink')}
            </Link>
          </div>
        </form>
      )}
    </div>
  );
}
