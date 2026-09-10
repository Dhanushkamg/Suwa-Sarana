'use client';

import { useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { Mail, Lock, Eye, EyeOff } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { useAuthStore } from '@/store/authStore';
import { useI18n } from '@/lib/i18n';
import apiClient from '@/lib/apiClient';
import { User } from '@/types';

export default function LoginPage() {
  const router = useRouter();
  const { t } = useI18n();
  const login = useAuthStore((s) => s.login);

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      const res = await apiClient.post<{ accessToken: string; userId: number; email: string; role: string; phoneNumber: string }>('/auth/login', {
        username: email,
        email,
        password,
      });

      const user: User = {
        id: res.data.userId,
        email: res.data.email,
        phoneNumber: res.data.phoneNumber,
        role: res.data.role as User['role'],
      };

      login(res.data.accessToken, user);

      // Role-based redirection
      if (user.role === 'DONOR') {
        router.push('/dashboard/donor');
      } else if (user.role === 'ADMIN') {
        router.push('/dashboard/admin');
      } else if (user.role === 'HOSPITAL_REQUESTER') {
        router.push('/dashboard/hospital');
      } else {
        router.push('/dashboard');
      }
    } catch (err: unknown) {
      const message =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ||
        t('common.error');
      setError(message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div>
      <div className="mb-8">
        <h2 className="text-3xl font-bold text-white">{t('auth.loginTitle')}</h2>
        <p className="text-gray-400 mt-2">{t('auth.loginSubtitle')}</p>
      </div>

      {error && (
        <div className="mb-5 rounded-xl border border-red-500/30 bg-red-500/10 px-4 py-3 text-sm text-red-400">
          {error}
        </div>
      )}

      <form onSubmit={handleSubmit} className="space-y-4" noValidate>
        <Input
          id="email"
          type="email"
          label={t('auth.email')}
          placeholder="you@example.com"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          icon={<Mail className="w-4 h-4" />}
          autoComplete="email"
          required
        />

        <div className="flex flex-col gap-1.5">
          <div className="flex items-center justify-between">
            <label htmlFor="password" className="text-sm font-medium text-gray-300">
              {t('auth.password')}
            </label>
            <Link href="/forgot-password" className="text-xs text-red-400 hover:text-red-300 transition-colors">
              {t('auth.forgotPassword')}
            </Link>
          </div>
          <div className="relative">
            <div className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3.5 text-gray-500">
              <Lock className="w-4 h-4" />
            </div>
            <input
              id="password"
              type={showPassword ? 'text' : 'password'}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="••••••••"
              autoComplete="current-password"
              required
              className="w-full rounded-xl border border-white/10 bg-white/5 pl-10 pr-11 py-3 text-sm text-white placeholder-gray-500 backdrop-blur-sm transition-all duration-200 focus:border-red-500/60 focus:outline-none focus:ring-2 focus:ring-red-500/20"
            />
            <button
              type="button"
              onClick={() => setShowPassword(!showPassword)}
              className="absolute inset-y-0 right-0 flex items-center pr-3.5 text-gray-500 hover:text-gray-300 transition-colors"
              aria-label={showPassword ? 'Hide password' : 'Show password'}
            >
              {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
            </button>
          </div>
        </div>

        <div className="pt-2">
          <Button type="submit" size="lg" className="w-full" loading={loading}>
            {t('auth.signIn')}
          </Button>
        </div>
      </form>

      <div className="mt-8 text-center">
        <p className="text-sm text-gray-500">
          {t('auth.noAccount')}{' '}
          <Link href="/register" className="font-medium text-red-400 hover:text-red-300 transition-colors">
            {t('auth.registerNow')}
          </Link>
        </p>
      </div>
    </div>
  );
}
