'use client';

import { useRef, useState } from 'react';
import { Button } from '@/components/ui/Button';
import { Dialog } from '@/components/ui/Dialog';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { Alert } from '@/components/pub/Alert';
import { focusFirstInvalid, parseApiError } from '@/components/pub/apiErrors';
import { DISTRICTS, DISTRICT_COORDS } from '@/lib/districts';
import apiClient from '@/lib/apiClient';
import { useI18n } from '@/lib/i18n';

interface CreateCampModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
}

const centre = (district: string) => DISTRICT_COORDS[district] ?? DISTRICT_COORDS.Colombo;

const INITIAL_FORM = {
  name: '',
  district: 'Colombo',
  location: '',
  latitude: String(centre('Colombo')[0]),
  longitude: String(centre('Colombo')[1]),
  scheduledDate: '',
  startTime: '09:00',
  endTime: '15:00',
  requiredBloodGroups: '',
};

type FormState = typeof INITIAL_FORM;

// Sri Lanka's bounding box: catches swapped or mistyped coordinates before they reach the API
const LAT_RANGE: [number, number] = [5.8, 10];
const LNG_RANGE: [number, number] = [79.4, 82];

export default function CreateCampModal({ isOpen, onClose, onSuccess }: CreateCampModalProps) {
  const { t } = useI18n();
  const formRef = useRef<HTMLFormElement>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [form, setForm] = useState<FormState>(INITIAL_FORM);

  const failWith = (errors: Record<string, string>) => {
    setFieldErrors(errors);
    // Focus after the errors have rendered
    window.setTimeout(() => formRef.current && focusFirstInvalid(formRef.current), 0);
  };

  const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
    const { name, value } = e.target;
    setForm((prev) => {
      const next = { ...prev, [name]: value };
      // Moving the district also moves the map point, unless the organiser already typed their own coordinates
      if (name === 'district') {
        const [prevLat, prevLng] = centre(prev.district);
        if (prev.latitude === String(prevLat) && prev.longitude === String(prevLng)) {
          const [lat, lng] = centre(value);
          next.latitude = String(lat);
          next.longitude = String(lng);
        }
      }
      return next;
    });
    if (fieldErrors[name]) setFieldErrors((prev) => ({ ...prev, [name]: '' }));
  };

  const validate = () => {
    const errors: Record<string, string> = {};
    if (!form.name.trim()) errors.name = t('pub.campNameRequired');
    if (!form.location.trim()) errors.location = t('pub.campLocationRequired');
    const lat = Number(form.latitude);
    const lng = Number(form.longitude);
    if (form.latitude === '' || !Number.isFinite(lat) || lat < LAT_RANGE[0] || lat > LAT_RANGE[1]) errors.latitude = t('pub.coordsOutOfRange');
    if (form.longitude === '' || !Number.isFinite(lng) || lng < LNG_RANGE[0] || lng > LNG_RANGE[1]) errors.longitude = t('pub.coordsOutOfRange');
    if (!form.scheduledDate) errors.scheduledDate = t('pub.campDateRequired');
    if (form.startTime && form.endTime && form.endTime <= form.startTime) errors.endTime = t('pub.campEndAfterStart');
    return errors;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    const errors = validate();
    if (Object.keys(errors).length > 0) {
      failWith(errors);
      return;
    }
    setFieldErrors({});
    setLoading(true);

    try {
      await apiClient.post('/camps', {
        ...form,
        latitude: parseFloat(form.latitude),
        longitude: parseFloat(form.longitude),
      });
      setForm(INITIAL_FORM);
      onSuccess();
      onClose();
    } catch (err: unknown) {
      const parsed = parseApiError(err, t, { fallback: t('modals.createCampFailed') });
      setError(parsed.message);
      if (Object.keys(parsed.fields).length > 0) failWith(parsed.fields);
    } finally {
      setLoading(false);
    }
  };

  return (
    <Dialog open={isOpen} onClose={onClose} title={t('modals.createCampTitle')} className="w-[min(94vw,44rem)]">
      {error && <Alert className="mb-6">{error}</Alert>}

      <form ref={formRef} onSubmit={handleSubmit} noValidate className="space-y-6">
        <div className="space-y-4">
          <Input
            id="name"
            name="name"
            label={t('modals.campaignName')}
            placeholder={t('modals.campaignNamePlaceholder')}
            value={form.name}
            onChange={handleChange}
            error={fieldErrors.name}
            aria-required="true"
          />

          <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
            <Select
              id="district"
              name="district"
              label={t('modals.district')}
              value={form.district}
              onChange={handleChange}
              options={DISTRICTS.map((d) => ({ value: d, label: d }))}
              error={fieldErrors.district}
            />
            <Input
              id="location"
              name="location"
              label={t('modals.location')}
              placeholder={t('modals.locationPlaceholder')}
              value={form.location}
              onChange={handleChange}
              error={fieldErrors.location}
              aria-required="true"
            />
          </div>

          <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
            <Input
              id="latitude"
              name="latitude"
              label={t('modals.latitude')}
              type="number"
              inputMode="decimal"
              step="any"
              placeholder="6.9271"
              value={form.latitude}
              onChange={handleChange}
              error={fieldErrors.latitude}
              aria-required="true"
            />
            <Input
              id="longitude"
              name="longitude"
              label={t('modals.longitude')}
              type="number"
              inputMode="decimal"
              step="any"
              placeholder="79.8612"
              value={form.longitude}
              onChange={handleChange}
              error={fieldErrors.longitude}
              aria-required="true"
            />
          </div>

          <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
            <Input
              id="scheduledDate"
              name="scheduledDate"
              label={t('modals.date')}
              type="date"
              value={form.scheduledDate}
              onChange={handleChange}
              error={fieldErrors.scheduledDate}
              aria-required="true"
            />
            <Input
              id="startTime"
              name="startTime"
              label={t('modals.startTime')}
              type="time"
              value={form.startTime}
              onChange={handleChange}
              error={fieldErrors.startTime}
              aria-required="true"
            />
            <Input
              id="endTime"
              name="endTime"
              label={t('modals.endTime')}
              type="time"
              value={form.endTime}
              onChange={handleChange}
              error={fieldErrors.endTime}
              aria-required="true"
            />
          </div>

          <Input
            id="requiredBloodGroups"
            name="requiredBloodGroups"
            label={t('modals.requiredBloodGroups')}
            placeholder={t('modals.requiredBloodGroupsPlaceholder')}
            value={form.requiredBloodGroups}
            onChange={handleChange}
            error={fieldErrors.requiredBloodGroups}
          />
        </div>

        <div className="flex justify-end gap-3 border-t border-line pt-5">
          <Button type="button" variant="ghost" onClick={onClose}>
            {t('common.cancel')}
          </Button>
          <Button type="submit" loading={loading}>
            {t('campManager.createCamp')}
          </Button>
        </div>
      </form>
    </Dialog>
  );
}
