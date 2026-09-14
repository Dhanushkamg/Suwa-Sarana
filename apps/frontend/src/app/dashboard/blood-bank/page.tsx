'use client';

import { Building2 } from 'lucide-react';
import { useAuthStore } from '@/store/authStore';
import CampManagerDashboard from '@/components/camps/CampManagerDashboard';

export default function BloodBankDashboardPage() {
  const { user } = useAuthStore();

  if (!user) return null;

  return (
    <div className="max-w-6xl mx-auto space-y-8">
      {/* Blood Bank Banner */}
      <div className="relative glass-card rounded-3xl p-8 border border-red-500/20 overflow-hidden bg-gradient-to-r from-red-950/40 via-rose-950/20 to-neutral-900/50">
        <div className="absolute top-0 right-0 w-96 h-96 bg-red-500/10 rounded-full blur-3xl pointer-events-none" />
        
        <div className="relative flex flex-col md:flex-row md:items-center justify-between gap-6">
          <div className="flex items-start gap-4">
            <div className="w-16 h-16 rounded-2xl bg-gradient-to-br from-red-500 to-rose-600 flex items-center justify-center shadow-xl shadow-red-500/30 flex-shrink-0">
              <Building2 className="w-8 h-8 text-white" />
            </div>
            <div>
              <div className="flex items-center gap-2 mb-1">
                <span className="px-2.5 py-0.5 rounded-full text-xs font-semibold bg-red-500/20 text-red-300 border border-red-500/30 uppercase tracking-wider">
                  Blood Bank Portal
                </span>
                <span className="flex items-center gap-1 text-xs text-emerald-400 font-medium">
                  <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse" /> Verified Organization
                </span>
              </div>
              <h1 className="text-3xl font-bold text-white">Blood Bank Command Center</h1>
              <p className="text-gray-400 text-sm mt-1 max-w-xl">
                Manage your national and regional blood donation camps, appointments, and inventory operations.
              </p>
            </div>
          </div>
        </div>
      </div>

      {/* Donation Camp Management */}
      <CampManagerDashboard />
    </div>
  );
}
