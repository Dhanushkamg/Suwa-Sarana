'use client';

import { useAuthStore } from '@/store/authStore';
import { Heart, Droplets, Bell, ArrowRight, LucideIcon } from 'lucide-react';
import Link from 'next/link';

interface QuickAction {
  icon: LucideIcon;
  title: string;
  desc: string;
  href: string;
  color: string;
}

const DONOR_ACTIONS: QuickAction[] = [
  {
    icon: Droplets,
    title: 'Update availability',
    desc: 'Toggle your donor status on or off',
    href: '/dashboard/donor',
    color: 'from-red-500 to-rose-600',
  },
  {
    icon: Bell,
    title: 'Notifications',
    desc: 'Check for nearby blood requests',
    href: '/dashboard/notifications',
    color: 'from-orange-500 to-red-500',
  },
];

const REQUESTER_ACTIONS: QuickAction[] = [
  {
    icon: Heart,
    title: 'New blood request',
    desc: 'Create an urgent request for a patient',
    href: '/dashboard/requests/new',
    color: 'from-red-500 to-rose-600',
  },
  {
    icon: Droplets,
    title: 'Active requests',
    desc: 'Track the status of your requests',
    href: '/dashboard/requests',
    color: 'from-orange-500 to-red-500',
  },
];

export default function DashboardPage() {
  const { user } = useAuthStore();
  const isDonor = user?.role === 'DONOR';
  const actions: QuickAction[] = isDonor ? DONOR_ACTIONS : REQUESTER_ACTIONS;

  return (
    <div>
      {/* Header */}
      <div className="mb-8">
        <h1 className="text-3xl font-bold text-white">
          Welcome back
          <span className="gradient-text ml-2">👋</span>
        </h1>
        <p className="text-gray-500 mt-2">
          {isDonor
            ? "Thank you for being a part of Sri Lanka's donor community."
            : 'Manage your blood requests and find compatible donors.'}
        </p>
      </div>

      {/* Quick actions */}
      <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-4 mb-8">
        {actions.map((action) => (
          <Link
            key={action.href}
            href={action.href}
            className="glass-card rounded-2xl p-6 flex flex-col gap-4 transition-all duration-300 hover:-translate-y-1 hover:border-red-500/20 group"
          >
            <div
              className={`w-11 h-11 rounded-xl bg-gradient-to-br ${action.color} flex items-center justify-center shadow-lg flex-shrink-0 group-hover:scale-110 transition-transform duration-300`}
            >
              <action.icon className="w-5 h-5 text-white" />
            </div>
            <div>
              <div className="font-semibold text-white mb-1">{action.title}</div>
              <div className="text-sm text-gray-500">{action.desc}</div>
            </div>
          </Link>
        ))}

        {/* Coming soon placeholder */}
        <div className="glass-card rounded-2xl p-5 border-dashed border-white/8 flex flex-col items-center justify-center text-center gap-2 cursor-pointer hover:border-red-500/20 transition-all duration-300 group">
          <div className="w-10 h-10 rounded-xl bg-white/4 group-hover:bg-red-500/10 flex items-center justify-center transition-colors">
            <Bell className="w-4 h-4 text-gray-600 group-hover:text-red-400 transition-colors" />
          </div>
          <span className="text-sm text-gray-600 group-hover:text-gray-400 transition-colors">
            More features coming soon
          </span>
        </div>
      </div>

      {/* Info card */}
      <div className="glass-card rounded-2xl p-6 border-red-500/10 relative overflow-hidden">
        <div className="absolute top-0 right-0 w-48 h-48 bg-red-500/5 rounded-full blur-2xl pointer-events-none" />
        <div className="relative flex items-start gap-4">
          <div className="w-12 h-12 rounded-xl bg-gradient-to-br from-red-500 to-rose-600 flex items-center justify-center shadow-lg shadow-red-500/25 flex-shrink-0">
            <Heart className="w-6 h-6 text-white fill-white" />
          </div>
          <div className="flex-1">
            <h3 className="font-semibold text-white mb-1">
              {isDonor ? 'Your profile is active' : 'Platform status'}
            </h3>
            <p className="text-sm text-gray-500 leading-relaxed">
              {isDonor
                ? 'You are registered as a blood donor. Keep your availability and location updated to receive match alerts in real time.'
                : 'You can post blood requests for patients in need. The matching engine will find compatible donors near the hospital.'}
            </p>
          </div>
          <Link
            href={isDonor ? '/dashboard/donor' : '/dashboard/requests/new'}
            className="flex-shrink-0 flex items-center gap-1.5 text-sm font-medium text-red-400 hover:text-red-300 transition-colors"
          >
            {isDonor ? 'Edit profile' : 'New request'}
            <ArrowRight className="w-4 h-4" />
          </Link>
        </div>
      </div>
    </div>
  );
}
