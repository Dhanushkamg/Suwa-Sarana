'use client';

import { useEffect, useState } from 'react';
import { Button } from '@/components/ui/Button';
import { Dialog } from '@/components/ui/Dialog';
import { FormField } from '@/components/ui/FormField';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { Switch } from '@/components/ui/Switch';
import { parseApiError } from '@/lib/apiError';
import { addDaysToDay, todayInColombo } from '@/lib/dates';
import { BLOOD_TYPES } from '@/lib/constants';
import { useI18n } from '@/lib/i18n';
import { useDistricts } from '@/lib/api/meta';
import {
  COMPONENTS,
  MAX_UNITS,
  issueStock,
  recordBatch,
  saveSite,
  saveThreshold,
  updateBatch,
  type StockBatch,
  type StockSite,
  type Threshold,
} from '@/lib/api/inventory';
import { Notice } from './Notice';

interface BaseProps {
  open: boolean;
  onClose: () => void;
  /** Called after a successful save so the page can reload the stock. */
  onSaved: () => void;
}

type Errors = Record<string, string | undefined>;

function useOptions() {
  const { t } = useI18n();
  return {
    bloodTypes: BLOOD_TYPES.map((b) => ({ value: b, label: b })),
    components: COMPONENTS.map((c) => ({ value: c, label: t(`dashUi.inv.component.${c}`) })),
  };
}

/** Whole number inside [min, max], or undefined. */
function wholeNumber(text: string, min: number, max: number): number | undefined {
  const trimmed = text.trim();
  if (!/^\d+$/.test(trimmed)) return undefined;
  const n = Number(trimmed);
  return n >= min && n <= max ? n : undefined;
}

/** Shared bits of every dialog: busy flag, a page-level error and server field errors. */
function useSubmit(onDone: () => void) {
  const { t } = useI18n();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [serverFields, setServerFields] = useState<Errors>({});

  const reset = () => {
    setBusy(false);
    setError(null);
    setServerFields({});
  };

  const run = async (action: () => Promise<unknown>) => {
    setBusy(true);
    setError(null);
    setServerFields({});
    try {
      await action();
      onDone();
    } catch (err) {
      const parsed = parseApiError(err, t, { fallback: t('common.error') });
      setError(parsed.message);
      setServerFields(parsed.fields);
    } finally {
      setBusy(false);
    }
  };

  return { busy, error, serverFields, reset, run };
}

function Actions({ busy, onClose, label }: { busy: boolean; onClose: () => void; label: string }) {
  const { t } = useI18n();
  return (
    <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
      <Button type="button" variant="secondary" onClick={onClose} disabled={busy}>
        {t('common.cancel')}
      </Button>
      <Button type="submit" loading={busy}>
        {label}
      </Button>
    </div>
  );
}

/** Record a collected batch: blood group, component, units, optional collection date and the required expiry date. */
export function CollectionDialog({ open, onClose, onSaved }: BaseProps) {
  const { t } = useI18n();
  const { bloodTypes, components } = useOptions();
  const [bloodType, setBloodType] = useState<string>('O+');
  const [component, setComponent] = useState<string>('WHOLE_BLOOD');
  const [units, setUnits] = useState('1');
  const [collectedOn, setCollectedOn] = useState('');
  const [expiresOn, setExpiresOn] = useState('');
  const [errors, setErrors] = useState<Errors>({});
  const submitState = useSubmit(() => {
    onSaved();
    onClose();
  });
  const today = todayInColombo();

  useEffect(() => {
    if (open) {
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setUnits('1');
      setCollectedOn(todayInColombo());
      setExpiresOn('');
      setErrors({});
      submitState.reset();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (submitState.busy) return;
    const next: Errors = {};
    const n = wholeNumber(units, 1, MAX_UNITS);
    if (n === undefined) next.units = t('dashUi.inv.unitsInvalid', { max: MAX_UNITS });
    if (collectedOn && collectedOn > today) next.collectedOn = t('dashUi.inv.dateInvalid');
    if (!expiresOn) next.expiresOn = t('dashUi.inv.expiryRequired');
    else if (expiresOn <= (collectedOn || today)) next.expiresOn = t('dashUi.inv.expiryInvalid');
    setErrors(next);
    if (Object.keys(next).length || n === undefined) return;
    await submitState.run(() => recordBatch({ bloodType, component, units: n, collectedOn: collectedOn || undefined, expiresOn }));
  };

  const fieldError = (name: string) => errors[name] ?? submitState.serverFields[name];

  return (
    <Dialog open={open} onClose={onClose} title={t('dashUi.inv.addCollection')} description={t('dashUi.inv.addCollectionDesc')}>
      <form onSubmit={submit} noValidate className="space-y-4">
        <div className="grid gap-4 sm:grid-cols-2">
          <Select label={t('dashUi.donor.bloodType')} options={bloodTypes} value={bloodType} onChange={(e) => setBloodType(e.target.value)} />
          <Select label={t('dashUi.inv.componentLabel')} options={components} value={component} onChange={(e) => setComponent(e.target.value)} />
        </div>
        <FormField label={t('dashUi.inv.units')} error={fieldError('units')} required>
          {(p) => <Input {...p} type="number" min="1" max={MAX_UNITS} inputMode="numeric" value={units} onChange={(e) => setUnits(e.target.value)} />}
        </FormField>
        <div className="grid gap-4 sm:grid-cols-2">
          <FormField label={t('dashUi.inv.collectedOn')} help={t('dashUi.inv.collectedHelp')} error={fieldError('collectedOn')}>
            {(p) => <Input {...p} type="date" max={today} value={collectedOn} onChange={(e) => setCollectedOn(e.target.value)} />}
          </FormField>
          <FormField label={t('dashUi.inv.expiresOn')} help={t('dashUi.inv.expiresHelp')} error={fieldError('expiresOn')} required>
            {(p) => <Input {...p} type="date" min={addDaysToDay(collectedOn || today, 1)} value={expiresOn} onChange={(e) => setExpiresOn(e.target.value)} />}
          </FormField>
        </div>
        {submitState.error && <Notice tone="error">{submitState.error}</Notice>}
        <Actions busy={submitState.busy} onClose={onClose} label={t('dashUi.inv.save')} />
      </form>
    </Dialog>
  );
}

/** Correct a batch: the unit count (0 means used up) and the expiry date, with an optional note for the movement log. */
export function EditBatchDialog({ batch, onClose, onSaved }: { batch: StockBatch | null; onClose: () => void; onSaved: () => void }) {
  const { t, format } = useI18n();
  const [units, setUnits] = useState('');
  const [expiresOn, setExpiresOn] = useState('');
  const [note, setNote] = useState('');
  const [errors, setErrors] = useState<Errors>({});
  const submitState = useSubmit(() => {
    onSaved();
    onClose();
  });

  useEffect(() => {
    if (batch) {
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setUnits(String(batch.units));
      setExpiresOn(batch.expiresOn);
      setNote('');
      setErrors({});
      submitState.reset();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [batch]);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!batch || submitState.busy) return;
    const next: Errors = {};
    const n = wholeNumber(units, 0, MAX_UNITS);
    if (n === undefined) next.units = t('dashUi.inv.unitsInvalidZero', { max: MAX_UNITS });
    if (!expiresOn) next.expiresOn = t('dashUi.inv.expiryRequired');
    setErrors(next);
    if (Object.keys(next).length || n === undefined) return;
    await submitState.run(() => updateBatch(batch.id, { units: n, expiresOn: expiresOn !== batch.expiresOn ? expiresOn : undefined, note }));
  };

  const fieldError = (name: string) => errors[name] ?? submitState.serverFields[name];
  const title = batch ? t('dashUi.inv.editBatchTitle', { type: batch.bloodType, component: t(`dashUi.inv.component.${batch.component}`) }) : '';

  return (
    <Dialog open={batch !== null} onClose={onClose} title={title} description={batch ? t('dashUi.inv.editBatchDesc', { date: format.date(batch.expiresOn, { dateStyle: 'medium' }) }) : undefined}>
      <form onSubmit={submit} noValidate className="space-y-4">
        <div className="grid gap-4 sm:grid-cols-2">
          <FormField label={t('dashUi.inv.units')} help={t('dashUi.inv.unitsZeroHelp')} error={fieldError('units')} required>
            {(p) => <Input {...p} type="number" min="0" max={MAX_UNITS} inputMode="numeric" value={units} onChange={(e) => setUnits(e.target.value)} />}
          </FormField>
          <FormField label={t('dashUi.inv.expiresOn')} error={fieldError('expiresOn')} required>
            {(p) => <Input {...p} type="date" value={expiresOn} onChange={(e) => setExpiresOn(e.target.value)} />}
          </FormField>
        </div>
        <FormField label={t('dashUi.inv.note')} help={t('dashUi.inv.noteHelp')} error={fieldError('note')}>
          {(p) => <Input {...p} maxLength={255} value={note} onChange={(e) => setNote(e.target.value)} />}
        </FormField>
        {submitState.error && <Notice tone="error">{submitState.error}</Notice>}
        <Actions busy={submitState.busy} onClose={onClose} label={t('dashUi.inv.save')} />
      </form>
    </Dialog>
  );
}

/** Low and critical levels for one blood group and component (critical is at or below low). Starts from the saved levels. */
export function ThresholdDialog({ open, onClose, onSaved, thresholds }: BaseProps & { thresholds: Threshold[] }) {
  const { t } = useI18n();
  const { bloodTypes, components } = useOptions();
  const [bloodType, setBloodType] = useState<string>('O+');
  const [component, setComponent] = useState<string>('WHOLE_BLOOD');
  const [low, setLow] = useState('10');
  const [critical, setCritical] = useState('5');
  const [errors, setErrors] = useState<Errors>({});
  const submitState = useSubmit(() => {
    onSaved();
    onClose();
  });

  const load = (type: string, comp: string) => {
    const saved = thresholds.find((x) => x.bloodType === type && x.component === comp);
    setLow(saved ? String(saved.low) : '10');
    setCritical(saved ? String(saved.critical) : '5');
    setErrors({});
  };

  useEffect(() => {
    if (open) {
      // eslint-disable-next-line react-hooks/set-state-in-effect
      load(bloodType, component);
      submitState.reset();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (submitState.busy) return;
    const next: Errors = {};
    const l = wholeNumber(low, 0, MAX_UNITS);
    const c = wholeNumber(critical, 0, MAX_UNITS);
    if (l === undefined) next.low = t('dashUi.inv.thresholdInvalid');
    if (c === undefined) next.critical = t('dashUi.inv.thresholdInvalid');
    else if (l !== undefined && c > l) next.critical = t('dashUi.inv.thresholdOrder');
    setErrors(next);
    if (Object.keys(next).length || l === undefined || c === undefined) return;
    await submitState.run(() => saveThreshold({ bloodType, component, low: l, critical: c }));
  };

  const fieldError = (name: string) => errors[name] ?? submitState.serverFields[name];

  return (
    <Dialog open={open} onClose={onClose} title={t('dashUi.inv.setThreshold')} description={t('dashUi.inv.setThresholdDesc')}>
      <form onSubmit={submit} noValidate className="space-y-4">
        <div className="grid gap-4 sm:grid-cols-2">
          <Select
            label={t('dashUi.donor.bloodType')}
            options={bloodTypes}
            value={bloodType}
            onChange={(e) => {
              setBloodType(e.target.value);
              load(e.target.value, component);
            }}
          />
          <Select
            label={t('dashUi.inv.componentLabel')}
            options={components}
            value={component}
            onChange={(e) => {
              setComponent(e.target.value);
              load(bloodType, e.target.value);
            }}
          />
        </div>
        <div className="grid gap-4 sm:grid-cols-2">
          <FormField label={t('dashUi.inv.lowLevel')} help={t('dashUi.inv.lowLevelHelp')} error={fieldError('low')} required>
            {(p) => <Input {...p} type="number" min="0" max={MAX_UNITS} inputMode="numeric" value={low} onChange={(e) => setLow(e.target.value)} />}
          </FormField>
          <FormField label={t('dashUi.inv.criticalLevel')} help={t('dashUi.inv.criticalLevelHelp')} error={fieldError('critical')} required>
            {(p) => <Input {...p} type="number" min="0" max={MAX_UNITS} inputMode="numeric" value={critical} onChange={(e) => setCritical(e.target.value)} />}
          </FormField>
        </div>
        {submitState.error && <Notice tone="error">{submitState.error}</Notice>}
        <Actions busy={submitState.busy} onClose={onClose} label={t('dashUi.inv.save')} />
      </form>
    </Dialog>
  );
}

/** Hand out units (first-expiry-first-out). `defaults` pre-selects the line the person clicked on. */
export function IssueDialog({ open, onClose, onSaved, defaults }: BaseProps & { defaults?: { bloodType: string; component: string } }) {
  const { t } = useI18n();
  const { bloodTypes, components } = useOptions();
  const [bloodType, setBloodType] = useState<string>('O+');
  const [component, setComponent] = useState<string>('WHOLE_BLOOD');
  const [units, setUnits] = useState('1');
  const [requestId, setRequestId] = useState('');
  const [note, setNote] = useState('');
  const [errors, setErrors] = useState<Errors>({});
  const submitState = useSubmit(() => {
    onSaved();
    onClose();
  });

  useEffect(() => {
    if (open) {
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setBloodType(defaults?.bloodType ?? 'O+');
      setComponent(defaults?.component ?? 'WHOLE_BLOOD');
      setUnits('1');
      setRequestId('');
      setNote('');
      setErrors({});
      submitState.reset();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (submitState.busy) return;
    const next: Errors = {};
    const n = wholeNumber(units, 1, MAX_UNITS);
    if (n === undefined) next.units = t('dashUi.inv.unitsInvalid', { max: MAX_UNITS });
    const rid = requestId.trim() ? wholeNumber(requestId, 1, Number.MAX_SAFE_INTEGER) : undefined;
    if (requestId.trim() && rid === undefined) next.requestId = t('dashUi.inv.requestIdInvalid');
    setErrors(next);
    if (Object.keys(next).length || n === undefined) return;
    await submitState.run(() => issueStock({ bloodType, component, units: n, requestId: rid, note }));
  };

  const fieldError = (name: string) => errors[name] ?? submitState.serverFields[name];

  return (
    <Dialog open={open} onClose={onClose} title={t('dashUi.inv.issueTitle')} description={t('dashUi.inv.issueDesc')}>
      <form onSubmit={submit} noValidate className="space-y-4">
        <div className="grid gap-4 sm:grid-cols-2">
          <Select label={t('dashUi.donor.bloodType')} options={bloodTypes} value={bloodType} onChange={(e) => setBloodType(e.target.value)} />
          <Select label={t('dashUi.inv.componentLabel')} options={components} value={component} onChange={(e) => setComponent(e.target.value)} />
        </div>
        <FormField label={t('dashUi.inv.units')} error={fieldError('units')} required>
          {(p) => <Input {...p} type="number" min="1" max={MAX_UNITS} inputMode="numeric" value={units} onChange={(e) => setUnits(e.target.value)} />}
        </FormField>
        <FormField label={t('dashUi.inv.requestId')} help={t('dashUi.inv.requestIdHelp')} error={fieldError('requestId')}>
          {(p) => <Input {...p} inputMode="numeric" value={requestId} onChange={(e) => setRequestId(e.target.value)} />}
        </FormField>
        <FormField label={t('dashUi.inv.note')} error={fieldError('note')}>
          {(p) => <Input {...p} maxLength={255} value={note} onChange={(e) => setNote(e.target.value)} />}
        </FormField>
        {submitState.error && <Notice tone="error">{submitState.error}</Notice>}
        <Actions busy={submitState.busy} onClose={onClose} label={t('dashUi.inv.issueConfirm')} />
      </form>
    </Dialog>
  );
}

/** Name, district and whether this site's low/critical signals appear in the public ticker. */
export function SiteDialog({ open, onClose, onSaved, site }: BaseProps & { site: StockSite | null }) {
  const { t } = useI18n();
  const { districts, centre } = useDistricts();
  const [siteName, setSiteName] = useState('');
  const [district, setDistrict] = useState('');
  const [publicSignals, setPublicSignals] = useState(false);
  const [errors, setErrors] = useState<Errors>({});
  const submitState = useSubmit(() => {
    onSaved();
    onClose();
  });

  useEffect(() => {
    if (open) {
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setSiteName(site?.siteName ?? '');
      setDistrict(site?.district ?? '');
      setPublicSignals(site?.publicSignals ?? false);
      setErrors({});
      submitState.reset();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (submitState.busy) return;
    const next: Errors = {};
    if (publicSignals && !district) next.district = t('dashUi.inv.districtNeeded');
    if (siteName.length > 150) next.siteName = t('dashUi.inv.siteNameLong');
    setErrors(next);
    if (Object.keys(next).length) return;
    const [latitude, longitude] = district ? centre(district) : [undefined, undefined];
    await submitState.run(() =>
      saveSite({ siteName: siteName.trim() || undefined, district: district || undefined, publicSignals, ...(district && site?.latitude == null ? { latitude, longitude } : {}) })
    );
  };

  const fieldError = (name: string) => errors[name] ?? submitState.serverFields[name];

  return (
    <Dialog open={open} onClose={onClose} title={t('dashUi.inv.siteTitle')} description={t('dashUi.inv.siteDesc')}>
      <form onSubmit={submit} noValidate className="space-y-4">
        <FormField label={t('dashUi.inv.siteName')} error={fieldError('siteName')}>
          {(p) => <Input {...p} maxLength={150} value={siteName} onChange={(e) => setSiteName(e.target.value)} />}
        </FormField>
        <Select
          label={t('dash.district')}
          error={fieldError('district')}
          options={[{ value: '', label: t('dashUi.inv.districtNone') }, ...districts.map((d) => ({ value: d.name, label: d.name }))]}
          value={district}
          onChange={(e) => setDistrict(e.target.value)}
        />
        <Switch id="inv-public-signals" checked={publicSignals} onChange={setPublicSignals} label={t('dashUi.inv.publicSignals')} description={t('dashUi.inv.publicSignalsDesc')} />
        {submitState.error && <Notice tone="error">{submitState.error}</Notice>}
        <Actions busy={submitState.busy} onClose={onClose} label={t('dashUi.inv.save')} />
      </form>
    </Dialog>
  );
}
