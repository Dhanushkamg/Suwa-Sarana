'use client';

import { useEffect, useState } from 'react';
import { useRouter, usePathname } from 'next/navigation';
import Link from 'next/link';
import { Heart, LogOut, Home, Droplets, Bell, ShieldCheck, Plus, Building2, Map, MapPin, Calendar, Menu, X } from 'lucide-react';
import { useAuthStore } from '@/store/authStore';
import { useI18n } from '@/lib/i18n';
import { LanguageSwitcher } from '@/components/ui/LanguageSwitcher';

export default function DashboardLayout({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const { t } = useI18n();
  const { isAuthenticated, user, logout } = useAuthStore();
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);

  useEffect(() => {
    if (!isAuthenticated) {
      router.replace('/login');
    }
  }, [isAuthenticated, router]);

  if (!isAuthenticated) return null;

  const handleLogout = async () => {
    await logout();
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
          { href: '/dashboard/donor/matches', label: t('layout.myMatches'), icon: Heart },
          { href: '/dashboard/donor/heatmap', label: t('layout.supplyHeatmap'), icon: Map },
          { href: '/camps', label: t('adminDashboard.camps'), icon: Calendar },
          { href: '/dashboard/notifications', label: t('common.notifications'), icon: Bell },
        ];
      case 'HOSPITAL_REQUESTER':
        return [
          { href: '/dashboard/hospital', label: t('layout.hospitalPortal'), icon: Building2 },
          { href: '/dashboard/requests/new', label: t('layout.broadcastRequest'), icon: Plus },
          { href: '/dashboard/requests', label: t('layout.activeRequisitions'), icon: Droplets },
          { href: '/camps', label: t('layout.allDonationCamps'), icon: Calendar },
          { href: '/dashboard/notifications', label: t('common.notifications'), icon: Bell },
        ];
      case 'BLOOD_BANK_REQUESTER':
        return [
          { href: '/dashboard/blood-bank', label: t('layout.bloodBankPortal'), icon: Building2 },
          { href: '/camps', label: t('layout.allDonationCamps'), icon: Calendar },
          { href: '/dashboard/notifications', label: t('common.notifications'), icon: Bell },
        ];
      case 'ADMIN':
        return [
          { href: '/dashboard/admin', label: t('layout.adminConsole'), icon: ShieldCheck },
          { href: '/dashboard/requests', label: t('layout.allBloodRequests'), icon: Droplets },
          { href: '/dashboard/donor/heatmap', label: t('layout.supplyHeatmap'), icon: Map },
          { href: '/dashboard/admin/camps', label: t('layout.campsMapView'), icon: MapPin },
          { href: '/dashboard/notifications', label: t('common.notifications'), icon: Bell },
        ];
      case 'REQUESTER':
      default:
        return [
          { href: '/dashboard', label: t('common.overview'), icon: Home },
          { href: '/dashboard/requests/new', label: t('bloodRequests.newRequest'), icon: Plus },
          { href: '/dashboard/requests', label: t('common.bloodRequests'), icon: Droplets },
          { href: '/dashboard/notifications', label: t('common.notifications'), icon: Bell },
        ];
    }
  };

  const navItems = getNavItems();

  return (
    <div className="min-h-screen bg-[#0d0d14] flex flex-col md:flex-row">
      {/* Mobile Header */}
      <header className="md:hidden sticky top-0 z-40 h-16 flex items-center justify-between px-4 border-b border-white/5 bg-[#0d0d14]/90 backdrop-blur-xl">
        <Link href="/" className="flex items-center gap-2">
          <div className="w-8 h-8 rounded-lg bg-gradient-to-br from-red-500 to-rose-600 flex items-center justify-center shadow-lg shadow-red-500/30">
            <Heart className="w-4 h-4 text-white fill-white" />
          </div>
          <span className="font-bold text-white">Suwa Sarana</span>
        </Link>
        <button
          onClick={() => setIsMobileMenuOpen(!isMobileMenuOpen)}
          className="p-2 -mr-2 text-gray-400 hover:text-white transition-colors"
          aria-label="Toggle menu"
        >
          {isMobileMenuOpen ? <X className="w-6 h-6" /> : <Menu className="w-6 h-6" />}
        </button>
      </header>

      {/* Backdrop overlay for mobile */}
      {isMobileMenuOpen && (
        <div 
          className="fixed inset-0 z-40 bg-black/50 backdrop-blur-sm md:hidden" 
          onClick={() => setIsMobileMenuOpen(false)}
        />
      )}

      {/* Sidebar */}
      <aside 
        className={`fixed left-0 top-0 h-full w-64 flex flex-col border-r border-white/5 bg-[#0d0d14]/95 md:bg-[#0d0d14]/90 backdrop-blur-xl z-50 transition-transform duration-300 ease-in-out md:translate-x-0 ${isMobileMenuOpen ? 'translate-x-0' : '-translate-x-full'}`}
      >
        {/* Logo (hidden on mobile, visible on desktop) */}
        <div className="hidden md:flex h-16 items-center justify-between px-6 border-b border-white/5">
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
                onClick={() => setIsMobileMenuOpen(false)}
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
      <main className="flex-1 md:ml-64 min-h-screen w-full">
        <div className="p-4 md:p-8">{children}</div>
      </main>
    </div>
  );
}
