'use client';

import { useState, useEffect, useCallback } from 'react';
import {
  ArrowLeft, MapPin, Users, Droplets, Activity, AlertTriangle,
  RefreshCw, CheckCircle2, TrendingUp, Filter, Search
} from 'lucide-react';
import Link from 'next/link';
import apiClient from '@/lib/apiClient';
import SriLankaHeatmap, { DistrictSummary } from '@/components/maps/SriLankaHeatmap';
import { useI18n } from '@/lib/i18n';

export default function AdminAnalyticsHeatmapPage() {
  const { t } = useI18n();
  const [districts, setDistricts] = useState<DistrictSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [filter, setFilter] = useState<'ALL' | 'CRITICAL' | 'WARNING' | 'BALANCED' | 'SURPLUS'>('ALL');
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedDistrict, setSelectedDistrict] = useState<DistrictSummary | null>(null);

  const loadAnalytics = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await apiClient.get<any>('/admin/analytics/district-summary');
      const data = res.data?.data ?? res.data;
      if (Array.isArray(data)) {
        setDistricts(data);
      }
    } catch (err: unknown) {
      const msg =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ||
        'Failed to load district shortage telemetry data.';
      setError(msg);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadAnalytics();
  }, [loadAnalytics]);

  // Aggregate Metrics
  const totalDonors = districts.reduce((acc, d) => acc + d.donorCount, 0);
  const totalActiveRequests = districts.reduce((acc, d) => acc + d.activeRequests, 0);
  const criticalDistricts = districts.filter((d) => d.shortageLevel === 'CRITICAL').length;
  const avgFulfillment = districts.length > 0
    ? Math.round((districts.reduce((acc, d) => acc + d.fulfillmentRate, 0) / districts.length) * 10) / 10
    : 100;

  const filteredDistricts = districts.filter((d) => {
    const matchesFilter = filter === 'ALL' || d.shortageLevel === filter;
    const matchesSearch = d.district.toLowerCase().includes(searchQuery.toLowerCase());
    return matchesFilter && matchesSearch;
  });

  return (
    <div className="max-w-7xl mx-auto space-y-8 pb-12">
      {/* Top Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <Link
            href="/dashboard/admin"
            className="inline-flex items-center gap-2 text-sm text-gray-400 hover:text-white transition-colors mb-4"
          >
            <ArrowLeft className="w-4 h-4" />
            {t('common.back')}
          </Link>
          <h1 className="text-3xl font-extrabold text-white flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-red-500/30 to-amber-500/20 border border-red-500/30 flex items-center justify-center text-red-400">
              <MapPin className="w-6 h-6" />
            </div>
            {t('adminDashboard.heatmapTitle')}
          </h1>
          <p className="text-gray-400 mt-1">
            {t('adminDashboard.heatmapDesc')}
          </p>
        </div>

        <div className="flex items-center gap-3">
          <button
            onClick={loadAnalytics}
            disabled={loading}
            className="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl bg-white/5 border border-white/10 text-gray-300 hover:text-white hover:bg-white/10 text-xs font-semibold transition-colors"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin' : ''}`} />
            {t('adminDashboard.refreshTelemetry')}
          </button>
        </div>
      </div>

      {error && (
        <div className="rounded-2xl border border-red-500/30 bg-red-500/10 p-4 flex items-center gap-3 text-red-400">
          <AlertTriangle className="w-5 h-5 flex-shrink-0" />
          <span className="text-sm font-medium">{error}</span>
        </div>
      )}

      {/* KPI Summary Cards */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <div className="glass-card p-5 rounded-2xl border border-white/10 relative overflow-hidden">
          <div className="flex items-center justify-between text-gray-400 mb-2">
            <span className="text-xs font-semibold uppercase tracking-wider">{t('adminDashboard.registeredDonors')}</span>
            <Users className="w-4 h-4 text-emerald-400" />
          </div>
          <p className="text-2xl font-black text-white">{totalDonors}</p>
          <p className="text-xs text-emerald-400/80 mt-1 flex items-center gap-1">
            <CheckCircle2 className="w-3.5 h-3.5" /> {t('adminDashboard.districtsActive')}
          </p>
        </div>

        <div className="glass-card p-5 rounded-2xl border border-white/10 relative overflow-hidden">
          <div className="flex items-center justify-between text-gray-400 mb-2">
            <span className="text-xs font-semibold uppercase tracking-wider">{t('adminDashboard.activeRequests')}</span>
            <Droplets className="w-4 h-4 text-red-400" />
          </div>
          <p className="text-2xl font-black text-white">{totalActiveRequests}</p>
          <p className="text-xs text-gray-400 mt-1">{t('adminDashboard.openEmergencies')}</p>
        </div>

        <div className="glass-card p-5 rounded-2xl border border-red-500/20 bg-gradient-to-br from-red-950/20 to-neutral-900/60 relative overflow-hidden">
          <div className="flex items-center justify-between text-gray-400 mb-2">
            <span className="text-xs font-semibold uppercase tracking-wider text-red-300">{t('adminDashboard.criticalShortages')}</span>
            <AlertTriangle className="w-4 h-4 text-red-400 animate-pulse" />
          </div>
          <p className="text-2xl font-black text-red-400">{criticalDistricts}</p>
          <p className="text-xs text-red-300/80 mt-1">{t('adminDashboard.urgentMobilization')}</p>
        </div>

        <div className="glass-card p-5 rounded-2xl border border-white/10 relative overflow-hidden">
          <div className="flex items-center justify-between text-gray-400 mb-2">
            <span className="text-xs font-semibold uppercase tracking-wider">{t('adminDashboard.nationalFulfillment')}</span>
            <TrendingUp className="w-4 h-4 text-blue-400" />
          </div>
          <p className="text-2xl font-black text-white">{avgFulfillment}%</p>
          <p className="text-xs text-gray-400 mt-1">{t('adminDashboard.avgResolution')}</p>
        </div>
      </div>

      {/* Interactive Map & District Focus Container */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Leaflet Map Column */}
        <div className="lg:col-span-2 space-y-4">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 bg-white/5 p-3.5 rounded-2xl border border-white/10">
            <div className="flex items-center gap-2 text-xs font-bold uppercase tracking-wider text-gray-300">
              <Filter className="w-4 h-4 text-red-400" />
              {t('adminDashboard.filterMap')}
            </div>
            <div className="flex flex-wrap gap-1.5">
              {(['ALL', 'CRITICAL', 'WARNING', 'BALANCED', 'SURPLUS'] as const).map((lvl) => (
                <button
                  key={lvl}
                  onClick={() => setFilter(lvl)}
                  className={`px-3 py-1.5 rounded-xl text-xs font-semibold transition-all ${
                    filter === lvl
                      ? 'bg-red-500 text-white shadow-lg shadow-red-500/30'
                      : 'bg-white/5 text-gray-400 hover:text-white hover:bg-white/10'
                  }`}
                >
                  {lvl === 'ALL' ? t('adminDashboard.allDistricts') : lvl}
                </button>
              ))}
            </div>
          </div>

          <SriLankaHeatmap
            districts={districts}
            filter={filter}
            onSelectDistrict={(d) => setSelectedDistrict(d)}
          />
        </div>

        {/* District Detail Sidebar */}
        <div className="glass-card rounded-2xl p-6 border border-white/10 flex flex-col justify-between">
          <div>
            <h3 className="text-lg font-bold text-white mb-1 flex items-center gap-2">
              <Activity className="w-5 h-5 text-red-400" />
              {t('adminDashboard.districtFocus')}
            </h3>
            <p className="text-xs text-gray-400 mb-6">
              {t('adminDashboard.inspectMetrics')}
            </p>

            {selectedDistrict ? (
              <div className="space-y-4 animate-in fade-in duration-200">
                <div className="p-4 rounded-xl bg-white/5 border border-white/10 flex items-center justify-between">
                  <h4 className="text-xl font-black text-white">{selectedDistrict.district}</h4>
                  <span
                    className={`px-2.5 py-1 rounded-full text-xs font-bold uppercase ${
                      selectedDistrict.shortageLevel === 'CRITICAL'
                        ? 'bg-red-500/20 text-red-400 border border-red-500/30'
                        : selectedDistrict.shortageLevel === 'WARNING'
                        ? 'bg-amber-500/20 text-amber-400 border border-amber-500/30'
                        : selectedDistrict.shortageLevel === 'SURPLUS'
                        ? 'bg-cyan-500/20 text-cyan-400 border border-cyan-500/30'
                        : 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/30'
                    }`}
                  >
                    {selectedDistrict.shortageLevel}
                  </span>
                </div>

                <div className="grid grid-cols-2 gap-3">
                  <div className="p-3.5 rounded-xl bg-white/5 border border-white/5">
                    <p className="text-[11px] text-gray-400">{t('adminDashboard.availableDonors')}</p>
                    <p className="text-lg font-bold text-emerald-400">{selectedDistrict.donorCount}</p>
                  </div>
                  <div className="p-3.5 rounded-xl bg-white/5 border border-white/5">
                    <p className="text-[11px] text-gray-400">{t('adminDashboard.activeRequests')}</p>
                    <p className="text-lg font-bold text-red-400">{selectedDistrict.activeRequests}</p>
                  </div>
                  <div className="p-3.5 rounded-xl bg-white/5 border border-white/5">
                    <p className="text-[11px] text-gray-400">{t('adminDashboard.fulfilledRequests')}</p>
                    <p className="text-lg font-bold text-blue-400">{selectedDistrict.fulfilledRequests}</p>
                  </div>
                  <div className="p-3.5 rounded-xl bg-white/5 border border-white/5">
                    <p className="text-[11px] text-gray-400">{t('adminDashboard.totalRequests')}</p>
                    <p className="text-lg font-bold text-white">{selectedDistrict.totalRequests}</p>
                  </div>
                </div>

                <div className="p-4 rounded-xl bg-white/5 border border-white/5 space-y-2">
                  <div className="flex justify-between text-xs text-gray-300">
                    <span>{t('adminDashboard.fulfillmentRate')}</span>
                    <span className="font-bold text-emerald-400">{selectedDistrict.fulfillmentRate}%</span>
                  </div>
                  <div className="w-full h-2 bg-white/10 rounded-full overflow-hidden">
                    <div
                      className="h-full bg-gradient-to-r from-blue-500 to-emerald-400 transition-all duration-500"
                      style={{ width: `${selectedDistrict.fulfillmentRate}%` }}
                    />
                  </div>
                </div>
              </div>
            ) : (
              <div className="text-center py-12 rounded-xl bg-white/2 border border-dashed border-white/10 text-gray-500 text-xs">
                {t('adminDashboard.noDistrictSelected')}
              </div>
            )}
          </div>

          <div className="pt-6 border-t border-white/10">
            <p className="text-[11px] text-gray-500">
              {t('adminDashboard.telemetryRefreshed')}
            </p>
          </div>
        </div>
      </div>

      {/* Searchable 25-District Breakdown Table */}
      <section className="glass-card rounded-2xl p-6 border border-white/10 space-y-4">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div>
            <h2 className="text-xl font-bold text-white flex items-center gap-2">
              <TrendingUp className="w-5 h-5 text-blue-400" />
              {t('adminDashboard.telemetryTable')}
            </h2>
            <p className="text-xs text-gray-400 mt-0.5">
              {t('adminDashboard.telemetryTableDesc')}
            </p>
          </div>

          <div className="relative w-full sm:w-64">
            <Search className="w-4 h-4 text-gray-500 absolute left-3.5 top-1/2 -translate-y-1/2" />
            <input
              type="text"
              placeholder={t('common.search')}
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-full pl-9 pr-4 py-2 bg-white/5 border border-white/10 rounded-xl text-xs text-white placeholder-gray-500 focus:outline-none focus:border-red-500/50 transition-colors"
            />
          </div>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs text-gray-300">
            <thead className="bg-white/5 uppercase font-bold text-gray-400 border-b border-white/10">
              <tr>
                <th className="py-3 px-4">{t('publicPages.district')}</th>
                <th className="py-3 px-4">{t('bloodRequests.status')}</th>
                <th className="py-3 px-4 text-right">{t('adminDashboard.availableDonors')}</th>
                <th className="py-3 px-4 text-right">{t('adminDashboard.activeRequests')}</th>
                <th className="py-3 px-4 text-right">{t('adminDashboard.fulfilled')}</th>
                <th className="py-3 px-4">{t('adminDashboard.fulfillmentRate').replace(':', '')}</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-white/5">
              {filteredDistricts.length === 0 ? (
                <tr>
                  <td colSpan={6} className="text-center py-8 text-gray-500">
                    {t('adminDashboard.noDistrictsFilter')}
                  </td>
                </tr>
              ) : (
                filteredDistricts.map((d) => (
                  <tr
                    key={d.district}
                    onClick={() => setSelectedDistrict(d)}
                    className="hover:bg-white/5 transition-colors cursor-pointer"
                  >
                    <td className="py-3 px-4 font-semibold text-white flex items-center gap-2">
                      <MapPin className="w-3.5 h-3.5 text-gray-500" />
                      {d.district}
                    </td>
                    <td className="py-3 px-4">
                      <span
                        className={`inline-block px-2.5 py-0.5 rounded-full text-[10px] font-bold uppercase ${
                          d.shortageLevel === 'CRITICAL'
                            ? 'bg-red-500/20 text-red-400 border border-red-500/30'
                            : d.shortageLevel === 'WARNING'
                            ? 'bg-amber-500/20 text-amber-400 border border-amber-500/30'
                            : d.shortageLevel === 'SURPLUS'
                            ? 'bg-cyan-500/20 text-cyan-400 border border-cyan-500/30'
                            : 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/30'
                        }`}
                      >
                        {d.shortageLevel}
                      </span>
                    </td>
                    <td className="py-3 px-4 text-right font-bold text-emerald-400">{d.donorCount}</td>
                    <td className="py-3 px-4 text-right font-bold text-red-400">{d.activeRequests}</td>
                    <td className="py-3 px-4 text-right text-gray-300">{d.fulfilledRequests}</td>
                    <td className="py-3 px-4">
                      <div className="flex items-center gap-2">
                        <div className="flex-1 h-1.5 bg-white/10 rounded-full overflow-hidden">
                          <div
                            className="h-full bg-blue-400"
                            style={{ width: `${d.fulfillmentRate}%` }}
                          />
                        </div>
                        <span className="font-mono text-[11px] text-gray-400 w-10 text-right">
                          {d.fulfillmentRate}%
                        </span>
                      </div>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
}
