'use client';

import { useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { Mail, Lock, Phone, Eye, EyeOff } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { useAuthStore } from '@/store/authStore';
import { useI18n } from '@/lib/i18n';
import apiClient from '@/lib/apiClient';
import { User } from '@/types';

export default function RegisterPage() {
  const router = useRouter();
  const { t } = useI18n();
  const login = useAuthStore((s) => s.login);

  const roleOptions = [
    { value: 'DONOR', label: t('auth.roleDonor') },
    { value: 'REQUESTER', label: t('auth.roleRequester') },
  ];

  const [form, setForm] = useState({
    email: '',
    password: '',
    confirmPassword: '',
    phoneNumber: '',
    role: 'DONOR',
  });
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [serverError, setServerError] = useState('');

  const validate = () => {
    const newErrors: Record<string, string> = {};
    if (!form.email) newErrors.email = 'Email is required';
    else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) newErrors.email = 'Enter a valid email';
    if (!form.phoneNumber) newErrors.phoneNumber = 'Phone number is required';
    else if (!/^0\d{9}$/.test(form.phoneNumber)) newErrors.phoneNumber = 'Enter a valid Sri Lankan number (07XXXXXXXX)';
    if (!form.password) newErrors.password = 'Password is required';
    else if (form.password.length < 8) newErrors.password = 'Password must be at least 8 characters';
    if (form.password !== form.confirmPassword) newErrors.confirmPassword = 'Passwords do not match';
    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleChange = (field: string) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
    setForm((prev) => ({ ...prev, [field]: e.target.value }));
    if (errors[field]) setErrors((prev) => ({ ...prev, [field]: '' }));
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!validate()) return;

    setServerError('');
    setLoading(true);

    try {
      const res = await apiClient.post<{ accessToken: string; userId: number; email: string; role: string; phoneNumber: string }>(
        '/auth/register',
        {
          email: form.email,
          password: form.password,
          phoneNumber: form.phoneNumber,
          role: form.role,
        }
      );

      const user: User = {
        id: res.data.userId,
        email: res.data.email,
        phoneNumber: res.data.phoneNumber,
        role: res.data.role as User['role'],
      };

      login(res.data.accessToken, user);

      // Redirect to dashboard
      router.push('/dashboard');
    } catch (err: unknown) {
      const message =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ||
        t('common.error');
      setServerError(message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div>
      <div className="mb-8">
        <h2 className="text-3xl font-bold text-white">{t('auth.registerTitle')}</h2>
        <p className="text-gray-400 mt-2">{t('auth.registerSubtitle')}</p>
      </div>

      {serverError && (
        <div className="mb-5 rounded-xl border border-red-500/30 bg-red-500/10 px-4 py-3 text-sm text-red-400">
          {serverError}
        </div>
      )}

      <form onSubmit={handleSubmit} className="space-y-4" noValidate>
        <Select
          id="role"
          label={t('auth.roleLabel')}
          options={roleOptions}
          value={form.role}
          onChange={handleChange('role')}
        />

        <Input
          id="reg-email"
          type="email"
          label={t('auth.email')}
          placeholder="you@example.com"
          value={form.email}
          onChange={handleChange('email')}
          error={errors.email}
          icon={<Mail className="w-4 h-4" />}
          autoComplete="email"
        />

        <Input
          id="reg-phone"
          type="tel"
          label={t('auth.phone')}
          placeholder="0712345678"
          value={form.phoneNumber}
          onChange={handleChange('phoneNumber')}
          error={errors.phoneNumber}
          icon={<Phone className="w-4 h-4" />}
          autoComplete="tel"
        />

        {/* Password */}
        <div className="flex flex-col gap-1.5">
          <label htmlFor="reg-password" className="text-sm font-medium text-gray-300">
            {t('auth.password')}
          </label>
          <div className="relative">
            <div className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3.5 text-gray-500">
              <Lock className="w-4 h-4" />
            </div>
            <input
              id="reg-password"
              type={showPassword ? 'text' : 'password'}
              value={form.password}
              onChange={handleChange('password')}
              placeholder="••••••••"
              autoComplete="new-password"
              className={`w-full rounded-xl border bg-white/5 pl-10 pr-11 py-3 text-sm text-white placeholder-gray-500 backdrop-blur-sm transition-all duration-200 focus:outline-none focus:ring-2 ${errors.password ? 'border-red-500/50 focus:border-red-500 focus:ring-red-500/30' : 'border-white/10 focus:border-red-500/60 focus:ring-red-500/20'}`}
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
          {errors.password && <p className="text-xs text-red-400">{errors.password}</p>}
        </div>

        <Input
          id="reg-confirm-password"
          type="password"
          label={t('auth.confirmPassword')}
          placeholder="••••••••"
          value={form.confirmPassword}
          onChange={handleChange('confirmPassword')}
          error={errors.confirmPassword}
          icon={<Lock className="w-4 h-4" />}
          autoComplete="new-password"
        />

        <div className="pt-2">
          <Button type="submit" size="lg" className="w-full" loading={loading}>
            {t('auth.createAccount')}
          </Button>
        </div>

        <p className="text-center text-xs text-gray-600 leading-relaxed">
          {t('auth.termsNote')}{' '}
          <Link href="/terms" className="text-gray-400 hover:text-white transition-colors underline underline-offset-2">
            {t('auth.termsLink')}
          </Link>{' '}
          {t('auth.andText')}{' '}
          <Link href="/privacy" className="text-gray-400 hover:text-white transition-colors underline underline-offset-2">
            {t('auth.privacyLink')}
          </Link>
          .
        </p>
      </form>

      <div className="mt-6 text-center">
        <p className="text-sm text-gray-500">
          {t('auth.haveAccount')}{' '}
          <Link href="/login" className="font-medium text-red-400 hover:text-red-300 transition-colors">
            {t('auth.signInLink')}
          </Link>
        </p>
      </div>
    </div>
  );
}
