'use client';

import { useEffect } from 'react';
import { useRouter, usePathname } from 'next/navigation';
import Link from 'next/link';
import { Heart, LogOut, Home, Droplets, Bell, ShieldCheck, Plus, Building2 } from 'lucide-react';
import { useAuthStore } from '@/store/authStore';
import { useI18n } from '@/lib/i18n';
import { LanguageSwitcher } from '@/components/ui/LanguageSwitcher';

export default function DashboardLayout({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const { t } = useI18n();
  const { isAuthenticated, user, logout } = useAuthStore();

  useEffect(() => {
    if (!isAuthenticated) {
      router.replace('/login');
    }
  }, [isAuthenticated, router]);

  if (!isAuthenticated) return null;

  const handleLogout = () => {
    logout();
    router.push('/');
  };

  const formatRole = (role?: string) => {
    if (!role) return '';
    if (role === 'HOSPITAL_REQUESTER') return 'hospital requester';
    return role.toLowerCase().replace('_', ' ');
  };

  const getNavItems = () => {
    switch (user?.role) {
      case 'DONOR':
        return [
          { href: '/dashboard/donor', label: t('common.myProfile'), icon: Droplets },
          { href: '/dashboard/donor/matches', label: 'My Matches', icon: Heart },
          { href: '/dashboard/notifications', label: t('common.notifications'), icon: Bell },
        ];
      case 'HOSPITAL_REQUESTER':
        return [
          { href: '/dashboard/hospital', label: 'Hospital Portal', icon: Building2 },
          { href: '/dashboard/requests/new', label: 'Broadcast Request', icon: Plus },
          { href: '/dashboard/requests', label: 'Active Requisitions', icon: Droplets },
          { href: '/dashboard/notifications', label: t('common.notifications'), icon: Bell },
        ];
      case 'ADMIN':
        return [
          { href: '/dashboard/admin', label: 'Admin Console', icon: ShieldCheck },
          { href: '/dashboard/requests', label: 'All Blood Requests', icon: Droplets },
          { href: '/dashboard/notifications', label: t('common.notifications'), icon: Bell },
        ];
      case 'REQUESTER':
      default:
        return [
          { href: '/dashboard', label: t('common.overview'), icon: Home },
          { href: '/dashboard/requests/new', label: 'New Blood Request', icon: Plus },
          { href: '/dashboard/requests', label: t('common.bloodRequests'), icon: Droplets },
          { href: '/dashboard/notifications', label: t('common.notifications'), icon: Bell },
        ];
    }
  };

  const navItems = getNavItems();

  return (
    <div className="min-h-screen bg-[#0d0d14] flex">
      {/* Sidebar */}
      <aside className="fixed left-0 top-0 h-full w-64 flex flex-col border-r border-white/5 bg-[#0d0d14]/90 backdrop-blur-xl z-40">
        {/* Logo */}
        <div className="h-16 flex items-center justify-between px-6 border-b border-white/5">
          <Link href="/" className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-lg bg-gradient-to-br from-red-500 to-rose-600 flex items-center justify-center shadow-lg shadow-red-500/30">
              <Heart className="w-4 h-4 text-white fill-white" />
            </div>
            <span className="font-bold text-white">Suwa Sarana</span>
          </Link>
        </div>

        {/* User badge */}
        <div className="px-4 py-4 border-b border-white/5">
          <div className="flex items-center gap-3 p-3 rounded-xl bg-white/4">
            <div className="w-9 h-9 rounded-full bg-gradient-to-br from-red-500/30 to-rose-600/30 flex items-center justify-center text-sm font-bold text-red-300">
              {user?.email?.charAt(0).toUpperCase()}
            </div>
            <div className="min-w-0">
              <div className="text-xs font-medium text-white truncate">{user?.email}</div>
              <div className="text-xs text-gray-400 capitalize font-medium">{formatRole(user?.role)}</div>
            </div>
          </div>
        </div>

        {/* Nav */}
        <nav className="flex-1 px-3 py-4 space-y-1">
          {navItems.map((item) => {
            const isActive = pathname === item.href;
            return (
              <Link
                key={item.href}
                href={item.href}
                className={`flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm transition-all duration-200 group ${
                  isActive
                    ? 'bg-red-500/10 text-red-400 font-semibold border border-red-500/20'
                    : 'text-gray-400 hover:text-white hover:bg-white/6'
                }`}
              >
                <item.icon className={`w-4 h-4 transition-colors ${isActive ? 'text-red-400' : 'group-hover:text-red-400'}`} />
                {item.label}
              </Link>
            );
          })}
        </nav>

        {/* Language switch & Logout */}
        <div className="p-4 border-t border-white/5 space-y-2">
          <div className="flex justify-center pb-2">
            <LanguageSwitcher className="w-full" />
          </div>
          <button
            onClick={handleLogout}
            className="w-full flex items-center gap-3 px-3 py-2.5 rounded-xl text-gray-500 hover:text-red-400 hover:bg-red-500/8 transition-all duration-200 text-sm"
          >
            <LogOut className="w-4 h-4" />
            {t('common.signOut')}
          </button>
        </div>
      </aside>

      {/* Main content */}
      <main className="flex-1 ml-64 min-h-screen">
        <div className="p-8">{children}</div>
      </main>
    </div>
  );
}
