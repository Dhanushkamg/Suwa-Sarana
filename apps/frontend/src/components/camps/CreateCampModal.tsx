'use client';

import { useState, useEffect } from 'react';
import { X, Calendar } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { Alert } from '@/components/pub/Alert';
import apiClient from '@/lib/apiClient';
import { useI18n } from '@/lib/i18n';

interface CreateCampModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
}

const INITIAL_FORM = {
  name: '',
  district: 'Colombo',
  location: '',
  latitude: '',
  longitude: '',
  scheduledDate: '',
  startTime: '09:00',
  endTime: '15:00',
  requiredBloodGroups: ''
};

const DISTRICTS = [
  'Ampara', 'Anuradhapura', 'Badulla', 'Batticaloa', 'Colombo', 'Galle', 'Gampaha',
  'Hambantota', 'Jaffna', 'Kalutara', 'Kandy', 'Kegalle', 'Kilinochchi', 'Kurunegala',
  'Mannar', 'Matale', 'Matara', 'Monaragala', 'Mullaitivu', 'Nuwara Eliya',
  'Polonnaruwa', 'Puttalam', 'Ratnapura', 'Trincomalee', 'Vavuniya'
];

export default function CreateCampModal({ isOpen, onClose, onSuccess }: CreateCampModalProps) {
  const { t } = useI18n();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  
  const [form, setForm] = useState(INITIAL_FORM);

  // Close on Escape
  useEffect(() => {
    if (!isOpen) return;
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', onKeyDown);
    return () => document.removeEventListener('keydown', onKeyDown);
  }, [isOpen, onClose]);

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
      setForm(INITIAL_FORM);
      onSuccess();
      onClose();
    } catch (err: unknown) {
      const message = (err as { response?: { data?: { message?: unknown } } })?.response?.data?.message;
      setError(typeof message === 'string' && message ? message : t('modals.createCampFailed'));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div role="dialog" aria-modal="true" aria-labelledby="create-camp-title" className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4">
      <div className="max-h-[90vh] w-full max-w-2xl overflow-y-auto rounded-3xl border border-line-strong bg-surface">
        <div className="sticky top-0 z-10 flex items-center justify-between gap-4 border-b border-line bg-surface p-6">
          <h2 id="create-camp-title" className="flex items-center gap-3 font-display text-2xl text-foreground sm:text-3xl">
            <Calendar className="h-5 w-5 shrink-0 text-ember" aria-hidden="true" />
            {t('modals.createCampTitle')}
          </h2>
          <button type="button" onClick={onClose} aria-label={t('common.close')} className="shrink-0 rounded-full p-2 text-gray-400 transition-colors hover:bg-foreground/[0.06] hover:text-foreground">
            <X className="w-5 h-5" />
          </button>
        </div>

        <div className="p-4 sm:p-6">
          {error && (
            <Alert className="mb-6">{error}</Alert>
          )}

          <form onSubmit={handleSubmit} className="space-y-6">
            <div className="space-y-4">
              <Input
                id="name"
                name="name"
                label={t('modals.campaignName') || 'Campaign Name'}
                placeholder={t('modals.campaignNamePlaceholder') || 'e.g. Annual Blood Drive'}
                value={form.name}
                onChange={handleChange}
                required
              />

              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <Select
                  id="district"
                  name="district"
                  label={t('modals.district') || 'District'}
                  value={form.district}
                  onChange={handleChange}
                  options={DISTRICTS.map(d => ({ value: d, label: d }))}
                  required
                />
                <Input
                  id="location"
                  name="location"
                  label={t('modals.location') || 'Venue / Location Details'}
                  placeholder={t('modals.locationPlaceholder') || 'e.g. Town Hall'}
                  value={form.location}
                  onChange={handleChange}
                  required
                />
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <Input
                  id="latitude"
                  name="latitude"
                  label={t('modals.latitude') || 'Latitude'}
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
                  label={t('modals.longitude') || 'Longitude'}
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
                  label={t('modals.date') || 'Date'}
                  type="date"
                  value={form.scheduledDate}
                  onChange={handleChange}
                  required
                />
                <Input
                  id="startTime"
                  name="startTime"
                  label={t('modals.startTime') || 'Start Time'}
                  type="time"
                  value={form.startTime}
                  onChange={handleChange}
                  required
                />
                <Input
                  id="endTime"
                  name="endTime"
                  label={t('modals.endTime') || 'End Time'}
                  type="time"
                  value={form.endTime}
                  onChange={handleChange}
                  required
                />
              </div>

              <Input
                id="requiredBloodGroups"
                name="requiredBloodGroups"
                label={t('modals.requiredBloodGroups') || 'Required Blood Groups (Optional)'}
                placeholder={t('modals.requiredBloodGroupsPlaceholder') || 'e.g. O+, A- (Leave blank if open to all)'}
                value={form.requiredBloodGroups}
                onChange={handleChange}
              />
            </div>

            <div className="flex justify-end gap-3 border-t border-line pt-5">
              <Button type="button" variant="ghost" onClick={onClose}>
                {t('common.cancel') || 'Cancel'}
              </Button>
              <Button type="submit" loading={loading}>
                {t('campManager.createCamp') || 'Create Camp'}
              </Button>
            </div>
          </form>
        </div>
      </div>
    </div>
  );
}
