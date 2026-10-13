'use client';

import { useEffect } from 'react';
import { useRouter, usePathname } from 'next/navigation';
import { Heart, Home, Droplets, Bell, ShieldCheck, Plus, Building2, Map, MapPin, Calendar } from 'lucide-react';
import { useAuthStore } from '@/store/authStore';
import { useI18n } from '@/lib/i18n';
import { DashShell, type DashNavItem } from '@/components/dash/DashShell';
import { homeForRole } from '@/lib/auth';
import type { User } from '@/types';

/** Which roles may open each dashboard section; anything else is sent to the user's own home. */
const SECTION_ROLES: { prefix: string; roles: User['role'][] }[] = [
  { prefix: '/dashboard/donor/heatmap', roles: ['DONOR', 'ADMIN'] },
  { prefix: '/dashboard/donor', roles: ['DONOR'] },
  { prefix: '/dashboard/admin', roles: ['ADMIN'] },
  { prefix: '/dashboard/hospital', roles: ['HOSPITAL_REQUESTER'] },
  { prefix: '/dashboard/blood-bank', roles: ['BLOOD_BANK_REQUESTER'] },
  { prefix: '/dashboard/requests/new', roles: ['REQUESTER', 'HOSPITAL_REQUESTER'] },
];

function isAllowed(pathname: string, role: User['role']) {
  const section = SECTION_ROLES.find((s) => pathname === s.prefix || pathname.startsWith(s.prefix + '/'));
  return !section || section.roles.includes(role);
}

export default function DashboardLayout({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const { t } = useI18n();
  const { isAuthenticated, isInitialized, initialize, user, logout } = useAuthStore();

  // After a full page load the in-memory token is gone: try to restore the session before deciding to redirect.
  useEffect(() => {
    initialize();
  }, [initialize]);

  useEffect(() => {
    if (!isInitialized) return;
    if (!isAuthenticated || !user) {
      router.replace('/login');
    } else if (!isAllowed(pathname, user.role)) {
      router.replace(homeForRole(user.role));
    }
  }, [isInitialized, isAuthenticated, user, pathname, router]);

  if (!isInitialized) {
    return (
      <div className="min-h-screen bg-background flex items-center justify-center" role="status" aria-label={t('common.loading')}>
        <div className="h-8 w-8 animate-spin rounded-full border-2 border-foreground/[0.16] border-t-ember" />
      </div>
    );
  }
  if (!isAuthenticated || !user || !isAllowed(pathname, user.role)) return null;

  const handleLogout = async () => {
    await logout();
    router.push('/');
  };

  const getNavItems = (): DashNavItem[] => {
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
    <DashShell navItems={navItems} user={user} onLogout={handleLogout}>
      {children}
    </DashShell>
  );
}
