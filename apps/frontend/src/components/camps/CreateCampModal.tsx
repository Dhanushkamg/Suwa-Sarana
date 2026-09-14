'use client';

import { useState } from 'react';
import { X, Calendar, MapPin, Clock, Droplets } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import apiClient from '@/lib/apiClient';

interface CreateCampModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
}

export default function CreateCampModal({ isOpen, onClose, onSuccess }: CreateCampModalProps) {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  
  const [form, setForm] = useState({
    name: '',
    district: 'Colombo',
    location: '',
    latitude: '',
    longitude: '',
    scheduledDate: '',
    startTime: '09:00',
    endTime: '15:00',
    requiredBloodGroups: ''
  });

  if (!isOpen) return null;

  const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
    setForm(prev => ({ ...prev, [e.target.name]: e.target.value }));
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError('');

    try {
      await apiClient.post('/camps', {
        ...form,
        latitude: form.latitude ? parseFloat(form.latitude) : 0,
        longitude: form.longitude ? parseFloat(form.longitude) : 0
      });
      onSuccess();
      onClose();
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to create camp');
    } finally {
      setLoading(false);
    }
  };

  const districts = [
    'Ampara', 'Anuradhapura', 'Badulla', 'Batticaloa', 'Colombo', 'Galle', 'Gampaha',
    'Hambantota', 'Jaffna', 'Kalutara', 'Kandy', 'Kegalle', 'Kilinochchi', 'Kurunegala',
    'Mannar', 'Matale', 'Matara', 'Monaragala', 'Mullaitivu', 'Nuwara Eliya',
    'Polonnaruwa', 'Puttalam', 'Ratnapura', 'Trincomalee', 'Vavuniya'
  ];

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm">
      <div className="bg-[#0d0d14] border border-white/10 rounded-3xl w-full max-w-2xl max-h-[90vh] overflow-y-auto">
        <div className="sticky top-0 bg-[#0d0d14]/90 backdrop-blur-xl border-b border-white/10 p-6 flex items-center justify-between z-10">
          <h2 className="text-xl font-bold text-white flex items-center gap-2">
            <Calendar className="w-5 h-5 text-rose-500" />
            Create Donation Camp
          </h2>
          <button onClick={onClose} className="p-2 hover:bg-white/5 rounded-full transition-colors text-gray-400">
            <X className="w-5 h-5" />
          </button>
        </div>

        <div className="p-6">
          {error && (
            <div className="mb-6 p-4 rounded-xl bg-red-500/10 border border-red-500/20 text-red-400 text-sm font-medium">
              {error}
            </div>
          )}

          <form onSubmit={handleSubmit} className="space-y-6">
            <div className="space-y-4">
              <Input
                id="name"
                name="name"
                label="Campaign Name"
                placeholder="e.g. Annual Blood Drive 2026"
                value={form.name}
                onChange={handleChange}
                required
              />

              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div className="space-y-1.5">
                  <label className="text-sm font-medium text-gray-300">District</label>
                  <select
                    name="district"
                    value={form.district}
                    onChange={handleChange}
                    className="w-full bg-white/5 border border-white/10 rounded-xl px-4 py-2.5 text-white focus:outline-none focus:border-rose-500"
                    required
                  >
                    {districts.map(d => (
                      <option key={d} value={d} className="bg-neutral-900">{d}</option>
                    ))}
                  </select>
                </div>
                <Input
                  id="location"
                  name="location"
                  label="Venue / Location Details"
                  placeholder="e.g. Town Hall, Colombo 07"
                  value={form.location}
                  onChange={handleChange}
                  required
                />
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <Input
                  id="latitude"
                  name="latitude"
                  label="Latitude"
                  type="number"
                  step="any"
                  placeholder="6.9271"
                  value={form.latitude}
                  onChange={handleChange}
                  required
                />
                <Input
                  id="longitude"
                  name="longitude"
                  label="Longitude"
                  type="number"
                  step="any"
                  placeholder="79.8612"
                  value={form.longitude}
                  onChange={handleChange}
                  required
                />
              </div>

              <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                <Input
                  id="scheduledDate"
                  name="scheduledDate"
                  label="Date"
                  type="date"
                  value={form.scheduledDate}
                  onChange={handleChange}
                  required
                />
                <Input
                  id="startTime"
                  name="startTime"
                  label="Start Time"
                  type="time"
                  value={form.startTime}
                  onChange={handleChange}
                  required
                />
                <Input
                  id="endTime"
                  name="endTime"
                  label="End Time"
                  type="time"
                  value={form.endTime}
                  onChange={handleChange}
                  required
                />
              </div>

              <Input
                id="requiredBloodGroups"
                name="requiredBloodGroups"
                label="Required Blood Groups (Optional)"
                placeholder="e.g. O+, A- (Leave blank if open to all)"
                value={form.requiredBloodGroups}
                onChange={handleChange}
              />
            </div>

            <div className="flex justify-end gap-3 pt-4 border-t border-white/10">
              <Button type="button" variant="ghost" onClick={onClose}>
                Cancel
              </Button>
              <Button type="submit" loading={loading} className="bg-rose-600 hover:bg-rose-500 text-white">
                Create Camp
              </Button>
            </div>
          </form>
        </div>
      </div>
    </div>
  );
}
