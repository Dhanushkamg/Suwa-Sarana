'use client';

import { useState, useEffect, useCallback } from 'react';
import { Plus, Users, Clock, CheckCircle2, AlertCircle, Loader2, RefreshCw, QrCode } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import apiClient from '@/lib/apiClient';
import { DonationSlot, SlotBooking } from '@/types';

interface SlotManagerProps {
  hostType: 'camp' | 'hospital';
  hostId: number;
}

function formatDateTime(iso: string) {
  return new Date(iso).toLocaleString([], {
    month: 'short', day: 'numeric',
    hour: '2-digit', minute: '2-digit',
  });
}

export default function SlotManager({ hostType, hostId }: SlotManagerProps) {
  const [slots, setSlots] = useState<DonationSlot[]>([]);
  const [rosterSlotId, setRosterSlotId] = useState<number | null>(null);
  const [roster, setRoster] = useState<SlotBooking[]>([]);
  const [loading, setLoading] = useState(true);
  const [rosterLoading, setRosterLoading] = useState(false);
  const [checkingIn, setCheckingIn] = useState<number | null>(null);
  const [feedback, setFeedback] = useState<string | null>(null);
  
  // Scanner state
  const [showScanner, setShowScanner] = useState(false);

  // Bulk create form state
  const [showBulkForm, setShowBulkForm] = useState(false);
  const [bulkForm, setBulkForm] = useState({
    overallStartTime: '',
    overallEndTime: '',
    intervalMinutes: 30,
    capacityPerSlot: 4,
  });
  const [creating, setCreating] = useState(false);

  const slotsEndpoint = hostType === 'camp'
    ? `/camps/${hostId}/slots`
    : `/hospitals/${hostId}/slots`;

  const fetchSlots = useCallback(async () => {
    try {
      const res = await apiClient.get(slotsEndpoint, { params: { openOnly: false } });
      setSlots(res.data.data ?? res.data);
    } catch {
      setFeedback('Failed to load slots.');
    } finally {
      setLoading(false);
    }
  }, [slotsEndpoint]);

  useEffect(() => { fetchSlots(); }, [fetchSlots]);

  useEffect(() => {
    if (!showScanner) return;
    
    let scannerInstance: any = null;
    
    import('html5-qrcode').then(({ Html5QrcodeScanner }) => {
      scannerInstance = new Html5QrcodeScanner(
        "qr-reader",
        { fps: 10, qrbox: { width: 250, height: 250 } },
        /* verbose= */ false
      );
      
      scannerInstance.render(
        (decodedText: string) => {
          try {
            const data = JSON.parse(decodedText);
            if (data.s && data.b) {
              scannerInstance.clear();
              setShowScanner(false);
              handleCheckIn(data.s, data.b);
            } else {
              setFeedback('Invalid Suwa Sarana QR format.');
            }
          } catch (e) {
            setFeedback('Unrecognized QR code.');
          }
        },
        (error: any) => {
          // Ignore frequent frame-level scan errors
        }
      );
    });

    return () => {
      if (scannerInstance) {
        scannerInstance.clear().catch(console.error);
      }
    };
  }, [showScanner]);

  const fetchRoster = async (slotId: number) => {
    setRosterSlotId(slotId);
    setRosterLoading(true);
    try {
      // Roster endpoint: GET /api/slots/{slotId}/check-in returns bookings for that slot
      // We reuse the bookings listed from the slot — no dedicated roster endpoint needed
      // since SlotBookingController's checkIn endpoint is per-booking.
      // For the roster, we call a hypothetical /api/slots/{id}/bookings or fall back to
      // the check-in flow after staff manually looks up the booking ID from the QR scan.
      // For now, show a placeholder message directing staff to scan QR codes.
      setRoster([]);
    } catch {
      setFeedback('Failed to load roster.');
    } finally {
      setRosterLoading(false);
    }
  };

  const handleCheckIn = async (slotId: number, bookingId: number) => {
    setCheckingIn(bookingId);
    setFeedback(null);
    try {
      await apiClient.post(`/slots/${slotId}/check-in/${bookingId}`);
      setFeedback(`Booking #${bookingId} checked in successfully.`);
      await fetchRoster(slotId);
    } catch (err: any) {
      setFeedback(err.response?.data?.message || 'Check-in failed.');
    } finally {
      setCheckingIn(null);
    }
  };

  const handleBulkCreate = async () => {
    setCreating(true);
    setFeedback(null);
    try {
      const endpoint = `${slotsEndpoint}/bulk`;
      const res = await apiClient.post(endpoint, bulkForm);
      const created: DonationSlot[] = res.data.data ?? res.data;
      setFeedback(`Created ${created.length} slot(s) successfully.`);
      setShowBulkForm(false);
      await fetchSlots();
    } catch (err: any) {
      setFeedback(err.response?.data?.message || 'Failed to create slots.');
    } finally {
      setCreating(false);
    }
  };

  if (loading) {
    return (
      <div className="flex items-center gap-2 py-8 text-gray-400 text-sm">
        <Loader2 className="w-4 h-4 animate-spin" />
        Loading slots...
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <h2 className="text-lg font-bold text-white">Slot Manager</h2>
        <div className="flex items-center gap-2">
          <Button variant="ghost" size="sm" onClick={fetchSlots}>
            <RefreshCw className="w-3.5 h-3.5" />
          </Button>
          <Button variant="secondary" size="sm" onClick={() => setShowScanner(true)}>
            <QrCode className="w-3.5 h-3.5 mr-1" />
            Scan QR
          </Button>
          <Button variant="secondary" size="sm" onClick={() => setShowBulkForm(v => !v)}>
            <Plus className="w-3.5 h-3.5 mr-1" />
            Add Slots
          </Button>
        </div>
      </div>

      {/* Global feedback */}
      {feedback && (
        <div className="flex items-center gap-2 text-sm p-3 rounded-xl bg-white/5 border border-white/10 text-gray-300">
          <AlertCircle className="w-4 h-4 flex-shrink-0 text-amber-400" />
          {feedback}
        </div>
      )}

      {/* Scanner Modal */}
      {showScanner && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-sm p-4">
          <div className="bg-[#12121a] border border-white/10 rounded-2xl p-6 w-full max-w-md">
            <div className="flex items-center justify-between mb-4">
              <h3 className="text-lg font-bold text-white">Scan Donor QR</h3>
              <Button variant="ghost" size="sm" onClick={() => setShowScanner(false)}>Close</Button>
            </div>
            
            {/* The ID matches what html5-qrcode targets */}
            <div id="qr-reader" className="w-full rounded-lg overflow-hidden border border-white/10 bg-black/50"></div>
            
            <p className="text-xs text-gray-400 mt-4 text-center">
              Position the donor's QR code within the camera frame.
            </p>
          </div>
        </div>
      )}

      {/* Bulk create form */}
      {showBulkForm && (
        <div className="rounded-2xl border border-white/10 bg-white/[0.03] p-5 space-y-4">
          <h3 className="text-sm font-semibold text-white">Bulk Generate Slots</h3>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs text-gray-400 mb-1">Overall Start</label>
              <input
                type="datetime-local"
                value={bulkForm.overallStartTime}
                onChange={e => setBulkForm(f => ({ ...f, overallStartTime: e.target.value }))}
                className="w-full bg-white/5 border border-white/10 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:border-red-500/50"
              />
            </div>
            <div>
              <label className="block text-xs text-gray-400 mb-1">Overall End</label>
              <input
                type="datetime-local"
                value={bulkForm.overallEndTime}
                onChange={e => setBulkForm(f => ({ ...f, overallEndTime: e.target.value }))}
                className="w-full bg-white/5 border border-white/10 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:border-red-500/50"
              />
            </div>
            <div>
              <label className="block text-xs text-gray-400 mb-1">Interval (minutes)</label>
              <input
                type="number"
                min={5}
                value={bulkForm.intervalMinutes}
                onChange={e => setBulkForm(f => ({ ...f, intervalMinutes: Number(e.target.value) }))}
                className="w-full bg-white/5 border border-white/10 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:border-red-500/50"
              />
            </div>
            <div>
              <label className="block text-xs text-gray-400 mb-1">Donors per slot</label>
              <input
                type="number"
                min={1}
                value={bulkForm.capacityPerSlot}
                onChange={e => setBulkForm(f => ({ ...f, capacityPerSlot: Number(e.target.value) }))}
                className="w-full bg-white/5 border border-white/10 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:border-red-500/50"
              />
            </div>
          </div>
          <div className="flex gap-2 justify-end">
            <Button variant="ghost" size="sm" onClick={() => setShowBulkForm(false)}>Cancel</Button>
            <Button variant="primary" size="sm" loading={creating} onClick={handleBulkCreate}>
              Generate Slots
            </Button>
          </div>
        </div>
      )}

      {/* Slot list */}
      {slots.length === 0 ? (
        <div className="text-center py-10 border border-white/5 rounded-2xl bg-white/[0.02] text-gray-400 text-sm">
          No slots created yet. Use "Add Slots" to generate time windows.
        </div>
      ) : (
        <div className="space-y-3">
          {slots.map((slot) => {
            const fillPct = Math.min(100, (slot.bookedCount / slot.capacity) * 100);
            const isExpanded = rosterSlotId === slot.id;

            return (
              <div
                key={slot.id}
                className="rounded-2xl border border-white/10 bg-white/[0.03] overflow-hidden"
              >
                {/* Slot header row */}
                <div className="flex items-center justify-between gap-4 p-4 flex-wrap">
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2">
                      <Clock className="w-3.5 h-3.5 text-amber-400" />
                      <span className="text-sm font-semibold text-white">
                        {formatDateTime(slot.startTime)} – {new Date(slot.endTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                      </span>
                      <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${
                        slot.status === 'OPEN'      ? 'bg-emerald-500/15 text-emerald-400' :
                        slot.status === 'FULL'      ? 'bg-red-500/15 text-red-400' :
                        slot.status === 'COMPLETED' ? 'bg-purple-500/15 text-purple-400' :
                                                      'bg-gray-500/15 text-gray-400'
                      }`}>
                        {slot.status}
                      </span>
                    </div>
                    {/* Capacity bar */}
                    <div className="flex items-center gap-2 mt-2">
                      <div className="flex-1 h-1.5 rounded-full bg-white/10 overflow-hidden">
                        <div
                          className={`h-full rounded-full transition-all ${slot.status === 'FULL' ? 'bg-red-500' : 'bg-emerald-500'}`}
                          style={{ width: `${fillPct}%` }}
                        />
                      </div>
                      <span className="text-xs text-gray-400 flex-shrink-0">
                        <Users className="w-3 h-3 inline mr-1" />{slot.bookedCount}/{slot.capacity}
                      </span>
                    </div>
                  </div>

                  <Button
                    variant="secondary"
                    size="sm"
                    onClick={() => isExpanded ? setRosterSlotId(null) : fetchRoster(slot.id)}
                  >
                    {isExpanded ? 'Hide Roster' : 'View Roster'}
                  </Button>
                </div>

                {/* Expanded roster */}
                {isExpanded && (
                  <div className="border-t border-white/5 px-4 py-3">
                    {rosterLoading ? (
                      <div className="flex items-center gap-2 text-gray-400 text-sm py-2">
                        <Loader2 className="w-3.5 h-3.5 animate-spin" /> Loading roster...
                      </div>
                    ) : roster.length === 0 ? (
                      <div className="text-sm text-gray-500 py-2">
                        <p>No bookings yet for this slot.</p>
                      </div>
                    ) : (
                      <div className="space-y-2">
                        {roster.map(b => (
                          <div key={b.id} className="flex items-center justify-between text-sm">
                            <span className="text-gray-300">Booking #{b.id}</span>
                            <div className="flex items-center gap-2">
                              <span className={`text-xs ${b.status === 'CHECKED_IN' ? 'text-blue-400' : 'text-gray-400'}`}>
                                {b.status}
                              </span>
                              {b.status === 'BOOKED' && (
                                <Button
                                  variant="secondary"
                                  size="sm"
                                  loading={checkingIn === b.id}
                                  onClick={() => handleCheckIn(slot.id, b.id)}
                                >
                                  <CheckCircle2 className="w-3 h-3 mr-1" /> Check In
                                </Button>
                              )}
                            </div>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
