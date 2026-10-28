'use client';

import { useEffect, useState } from 'react';
import { Button } from '@/components/ui/Button';
import { Dialog } from '@/components/ui/Dialog';
import { FormField } from '@/components/ui/FormField';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { BLOOD_TYPES } from '@/lib/constants';
import { useI18n } from '@/lib/i18n';
import { apiMessage } from '@/lib/api/common';
import { addCollection, COMPONENTS, setThreshold } from '@/lib/api/inventory';
import { Notice } from './Notice';

interface BaseProps {
  open: boolean;
  onClose: () => void;
  /** Called after a successful save so the page can reload the stock. */
  onSaved: () => void;
}

const today = () => new Date().toISOString().slice(0, 10);

function useOptions() {
  const { t } = useI18n();
  return {
    bloodTypes: BLOOD_TYPES.map((b) => ({ value: b, label: b })),
    components: COMPONENTS.map((c) => ({ value: c, label: t(`dashUi.inv.component.${c}`) })),
  };
}

/** Record a new collection (units added to stock). */
export function CollectionDialog({ open, onClose, onSaved }: BaseProps) {
  const { t } = useI18n();
  const { bloodTypes, components } = useOptions();
  const [bloodType, setBloodType] = useState<string>('O+');
  const [component, setComponent] = useState<string>('WHOLE_BLOOD');
  const [units, setUnits] = useState('1');
  const [collectedOn, setCollectedOn] = useState(today());
  const [expiresOn, setExpiresOn] = useState('');
  const [errors, setErrors] = useState<{ units?: string; collectedOn?: string; expiresOn?: string }>({});
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (open) {
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setUnits('1');
      setCollectedOn(today());
      setExpiresOn('');
      setErrors({});
      setError(null);
      setBusy(false);
    }
  }, [open]);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (busy) return;
    const next: typeof errors = {};
    const n = Number(units);
    if (!Number.isInteger(n) || n < 1 || n > 500) next.units = t('dashUi.inv.unitsInvalid');
    if (!collectedOn || collectedOn > today()) next.collectedOn = t('dashUi.inv.dateInvalid');
    if (expiresOn && expiresOn < collectedOn) next.expiresOn = t('dashUi.inv.expiryInvalid');
    setErrors(next);
    if (Object.keys(next).length) return;
    setBusy(true);
    setError(null);
    try {
      await addCollection({ bloodType, component, units: n, collectedOn, expiresOn: expiresOn || undefined });
      onSaved();
      onClose();
    } catch (err) {
      setError(apiMessage(err, t('common.error')));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Dialog open={open} onClose={onClose} title={t('dashUi.inv.addCollection')} description={t('dashUi.inv.addCollectionDesc')}>
      <form onSubmit={submit} noValidate className="space-y-4">
        <div className="grid gap-4 sm:grid-cols-2">
          <Select label={t('dashUi.donor.bloodType')} options={bloodTypes} value={bloodType} onChange={(e) => setBloodType(e.target.value)} />
          <Select label={t('dashUi.inv.componentLabel')} options={components} value={component} onChange={(e) => setComponent(e.target.value)} />
        </div>
        <FormField label={t('dashUi.inv.units')} error={errors.units} required>
          {(p) => <Input {...p} type="number" min="1" max="500" inputMode="numeric" value={units} onChange={(e) => setUnits(e.target.value)} />}
        </FormField>
        <div className="grid gap-4 sm:grid-cols-2">
          <FormField label={t('dashUi.inv.collectedOn')} error={errors.collectedOn} required>
            {(p) => <Input {...p} type="date" max={today()} value={collectedOn} onChange={(e) => setCollectedOn(e.target.value)} />}
          </FormField>
          <FormField label={t('dashUi.inv.expiresOn')} help={t('dashUi.inv.expiresHelp')} error={errors.expiresOn}>
            {(p) => <Input {...p} type="date" min={collectedOn} value={expiresOn} onChange={(e) => setExpiresOn(e.target.value)} />}
          </FormField>
        </div>
        {error && <Notice tone="error">{error}</Notice>}
        <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
          <Button type="button" variant="secondary" onClick={onClose} disabled={busy}>
            {t('common.cancel')}
          </Button>
          <Button type="submit" loading={busy}>
            {t('dashUi.inv.save')}
          </Button>
        </div>
      </form>
    </Dialog>
  );
}

/** Set the minimum units wanted on the shelf for one blood group and component. */
export function ThresholdDialog({ open, onClose, onSaved }: BaseProps) {
  const { t } = useI18n();
  const { bloodTypes, components } = useOptions();
  const [bloodType, setBloodType] = useState<string>('O+');
  const [component, setComponent] = useState<string>('WHOLE_BLOOD');
  const [threshold, setThresholdValue] = useState('10');
  const [fieldError, setFieldError] = useState<string | undefined>();
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (open) {
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setFieldError(undefined);
      setError(null);
      setBusy(false);
    }
  }, [open]);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (busy) return;
    const n = Number(threshold);
    if (!Number.isInteger(n) || n < 0 || n > 10000) {
      setFieldError(t('dashUi.inv.thresholdInvalid'));
      return;
    }
    setFieldError(undefined);
    setBusy(true);
    setError(null);
    try {
      await setThreshold({ bloodType, component, threshold: n });
      onSaved();
      onClose();
    } catch (err) {
      setError(apiMessage(err, t('common.error')));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Dialog open={open} onClose={onClose} title={t('dashUi.inv.setThreshold')} description={t('dashUi.inv.setThresholdDesc')}>
      <form onSubmit={submit} noValidate className="space-y-4">
        <div className="grid gap-4 sm:grid-cols-2">
          <Select label={t('dashUi.donor.bloodType')} options={bloodTypes} value={bloodType} onChange={(e) => setBloodType(e.target.value)} />
          <Select label={t('dashUi.inv.componentLabel')} options={components} value={component} onChange={(e) => setComponent(e.target.value)} />
        </div>
        <FormField label={t('dashUi.inv.minUnits')} help={t('dashUi.inv.minUnitsHelp')} error={fieldError} required>
          {(p) => <Input {...p} type="number" min="0" inputMode="numeric" value={threshold} onChange={(e) => setThresholdValue(e.target.value)} />}
        </FormField>
        {error && <Notice tone="error">{error}</Notice>}
        <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
          <Button type="button" variant="secondary" onClick={onClose} disabled={busy}>
            {t('common.cancel')}
          </Button>
          <Button type="submit" loading={busy}>
            {t('dashUi.inv.save')}
          </Button>
        </div>
      </form>
    </Dialog>
  );
}
