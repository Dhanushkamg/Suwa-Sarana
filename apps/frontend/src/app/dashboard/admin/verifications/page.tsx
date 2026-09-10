'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { ArrowLeft, ShieldCheck, ShieldX, UserCheck, AlertCircle, CheckCircle2, Hospital, User, Phone, Mail } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { useAuthStore } from '@/store/authStore';
import apiClient from '@/lib/apiClient';

interface PendingUser {
  id: number;
  email: string;
  phoneNumber: string;
  role: string;
  verificationStatus: string;
}

export default function AdminVerificationsPage() {
  const { user } = useAuthStore();
  const [pendingUsers, setPendingUsers] = useState<PendingUser[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [actionSuccess, setActionSuccess] = useState<string | null>(null);
  const [processingId, setProcessingId] = useState<number | null>(null);

  useEffect(() => {
    fetchPendingUsers();
  }, []);

  const fetchPendingUsers = async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await apiClient.get<PendingUser[]>('/admin/verifications/pending');
      setPendingUsers(response.data);
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message
        || 'Failed to load pending verifications.';
      setError(msg);
    } finally {
      setLoading(false);
    }
  };

  const handleApprove = async (userId: number, email: string) => {
    setProcessingId(userId);
    setError(null);
    try {
      await apiClient.put(`/admin/verifications/${userId}/approve`);
      setPendingUsers((prev) => prev.filter((u) => u.id !== userId));
      setActionSuccess(`Successfully approved verification for ${email}.`);
      setTimeout(() => setActionSuccess(null), 4000);
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message
        || 'Failed to approve user.';
      setError(msg);
    } finally {
      setProcessingId(null);
    }
  };

  const handleReject = async (userId: number, email: string) => {
    setProcessingId(userId);
    setError(null);
    try {
      await apiClient.put(`/admin/verifications/${userId}/reject`);
      setPendingUsers((prev) => prev.filter((u) => u.id !== userId));
      setActionSuccess(`Verification rejected for ${email}.`);
      setTimeout(() => setActionSuccess(null), 4000);
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message
        || 'Failed to reject user.';
      setError(msg);
    } finally {
      setProcessingId(null);
    }
  };

  if (!user || user.role !== 'ADMIN') {
    return (
      <div className="max-w-2xl mx-auto py-16 text-center">
        <div className="rounded-2xl border border-red-500/30 bg-red-500/10 p-8 text-red-400">
          <ShieldX className="w-12 h-12 mx-auto mb-4 text-red-400" />
          <h2 className="text-xl font-bold text-white mb-2">Access Denied</h2>
          <p className="text-sm text-gray-300">
            This management interface is restricted strictly to platform administrators.
          </p>
          <Link href="/dashboard" className="mt-6 inline-block">
            <Button variant="secondary" size="sm">
              Return to Dashboard
            </Button>
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className="max-w-6xl mx-auto">
      <div className="mb-6">
        <Link
          href="/dashboard"
          className="inline-flex items-center gap-2 text-sm text-gray-400 hover:text-white transition-colors mb-4"
        >
          <ArrowLeft className="w-4 h-4" />
          Back to Dashboard
        </Link>
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
          <div>
            <h1 className="text-3xl font-bold text-white flex items-center gap-3">
              <UserCheck className="w-8 h-8 text-red-500" />
              Requester Verifications
            </h1>
            <p className="text-gray-400 mt-1">
              Review and approve hospital and requester accounts to grant emergency blood request authorization.
            </p>
          </div>
          <Button variant="secondary" size="sm" onClick={fetchPendingUsers} loading={loading}>
            Refresh Queue
          </Button>
        </div>
      </div>

      {actionSuccess && (
        <div className="mb-6 rounded-2xl border border-emerald-500/30 bg-emerald-500/10 p-4 flex items-center gap-3 text-emerald-400 animate-in fade-in duration-200">
          <CheckCircle2 className="w-5 h-5 flex-shrink-0" />
          <p className="text-sm font-medium">{actionSuccess}</p>
        </div>
      )}

      {error && (
        <div className="mb-6 rounded-2xl border border-red-500/30 bg-red-500/10 p-4 flex items-center gap-3 text-red-400 animate-in fade-in duration-200">
          <AlertCircle className="w-5 h-5 flex-shrink-0" />
          <p className="text-sm font-medium">{error}</p>
        </div>
      )}

      {loading ? (
        <div className="glass-card rounded-2xl p-12 text-center">
          <div className="inline-block w-8 h-8 border-2 border-red-500 border-t-transparent rounded-full animate-spin mb-4" />
          <p className="text-gray-400">Loading pending verification requests...</p>
        </div>
      ) : pendingUsers.length === 0 ? (
        <div className="glass-card rounded-2xl p-12 text-center">
          <div className="w-16 h-16 rounded-full bg-emerald-500/10 border border-emerald-500/20 mx-auto flex items-center justify-center mb-4">
            <ShieldCheck className="w-8 h-8 text-emerald-400" />
          </div>
          <h3 className="text-lg font-semibold text-white mb-1">Queue is clear!</h3>
          <p className="text-sm text-gray-400 max-w-md mx-auto">
            There are no pending accounts awaiting verification at this time. All requester accounts are up to date.
          </p>
        </div>
      ) : (
        <div className="glass-card rounded-2xl overflow-hidden border border-white/10">
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm text-gray-300">
              <thead className="bg-white/5 border-b border-white/10 text-xs uppercase tracking-wider text-gray-400">
                <tr>
                  <th className="px-6 py-4">Account Details</th>
                  <th className="px-6 py-4">Contact</th>
                  <th className="px-6 py-4">Role / Scope</th>
                  <th className="px-6 py-4">Status</th>
                  <th className="px-6 py-4 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-white/5">
                {pendingUsers.map((u) => {
                  const isHospital = u.role === 'HOSPITAL_REQUESTER';
                  const isProcessing = processingId === u.id;

                  return (
                    <tr key={u.id} className="hover:bg-white/2 transition-colors">
                      <td className="px-6 py-4 whitespace-nowrap">
                        <div className="flex items-center gap-3">
                          <div className={`w-10 h-10 rounded-xl flex items-center justify-center ${
                            isHospital ? 'bg-blue-500/10 text-blue-400 border border-blue-500/20' : 'bg-purple-500/10 text-purple-400 border border-purple-500/20'
                          }`}>
                            {isHospital ? <Hospital className="w-5 h-5" /> : <User className="w-5 h-5" />}
                          </div>
                          <div>
                            <div className="font-medium text-white flex items-center gap-2">
                              {u.email}
                            </div>
                            <div className="text-xs text-gray-400">ID: #{u.id}</div>
                          </div>
                        </div>
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap">
                        <div className="flex items-center gap-1.5 text-xs text-gray-300">
                          <Phone className="w-3.5 h-3.5 text-gray-400" />
                          {u.phoneNumber || 'N/A'}
                        </div>
                        <div className="flex items-center gap-1.5 text-xs text-gray-400 mt-1">
                          <Mail className="w-3.5 h-3.5 text-gray-400" />
                          {u.email}
                        </div>
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap">
                        <span className={`inline-flex items-center px-2.5 py-1 rounded-full text-xs font-semibold ${
                          isHospital
                            ? 'bg-blue-500/15 text-blue-300 border border-blue-500/30'
                            : 'bg-purple-500/15 text-purple-300 border border-purple-500/30'
                        }`}>
                          {u.role.replace('_', ' ')}
                        </span>
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap">
                        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium bg-amber-500/15 text-amber-300 border border-amber-500/30">
                          <span className="w-1.5 h-1.5 rounded-full bg-amber-400 animate-pulse" />
                          {u.verificationStatus}
                        </span>
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-right space-x-2">
                        <Button
                          size="sm"
                          variant="secondary"
                          className="bg-emerald-500/20 hover:bg-emerald-500/30 text-emerald-300 border-emerald-500/30"
                          disabled={isProcessing}
                          onClick={() => handleApprove(u.id, u.email)}
                        >
                          <ShieldCheck className="w-4 h-4 mr-1.5" />
                          Approve
                        </Button>
                        <Button
                          size="sm"
                          variant="secondary"
                          className="bg-red-500/20 hover:bg-red-500/30 text-red-300 border-red-500/30"
                          disabled={isProcessing}
                          onClick={() => handleReject(u.id, u.email)}
                        >
                          <ShieldX className="w-4 h-4 mr-1.5" />
                          Reject
                        </Button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
}
