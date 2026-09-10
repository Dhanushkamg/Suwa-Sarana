'use client';

import { useState, useEffect, Suspense } from 'react';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { Mail, Lock, Phone, Eye, EyeOff } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { useAuthStore } from '@/store/authStore';
import { useI18n } from '@/lib/i18n';
import apiClient from '@/lib/apiClient';
import { User } from '@/types';

function RegisterForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { t } = useI18n();
  const login = useAuthStore((s) => s.login);

  const roleOptions = [
    { value: 'DONOR', label: t('auth.roleDonor') },
    { value: 'REQUESTER', label: t('auth.roleRequester') },
  ];

  // Read ?role= from URL and default to DONOR if not provided
  const initialRole = searchParams.get('role') === 'REQUESTER' ? 'REQUESTER' : 'DONOR';

  const [form, setForm] = useState({
    email: '',
    password: '',
    confirmPassword: '',
    phoneNumber: '',
    role: initialRole,
  });
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [serverError, setServerError] = useState('');

  // Sync role if the URL param changes after mount (e.g. back/forward navigation)
  useEffect(() => {
    const roleParam = searchParams.get('role');
    if (roleParam === 'REQUESTER' || roleParam === 'DONOR') {
      setForm((prev) => ({ ...prev, role: roleParam }));
    }
  }, [searchParams]);

  const validate = () => {
    const newErrors: Record<string, string> = {};
    if (!form.email) newErrors.email = 'Email is required';
    else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) newErrors.email = 'Enter a valid email';
    if (!form.phoneNumber) newErrors.phoneNumber = 'Phone number is required';
    // Accept local format (07XXXXXXXX) or international Sri Lanka format (+94XXXXXXXXX)
    else if (!/^(\+94|0)\d{9}$/.test(form.phoneNumber)) newErrors.phoneNumber = 'Enter a valid number: 07XXXXXXXX or +94XXXXXXXXX';
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
      const res = await apiClient.post<any>(
        '/auth/register',
        {
          email: form.email,
          password: form.password,
          phoneNumber: form.phoneNumber,
          role: form.role,
        }
      );

      const authData = res.data?.data ?? res.data;

      const user: User = {
        id: authData.userId,
        email: authData.email,
        phoneNumber: authData.phoneNumber,
        role: authData.role as User['role'],
      };

      login(authData.accessToken, user);

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
          placeholder="07XXXXXXXX or +94XXXXXXXXX"
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

export default function RegisterPage() {
  return (
    <Suspense fallback={<div className="text-gray-400 text-sm">Loading...</div>}>
      <RegisterForm />
    </Suspense>
  );
}
