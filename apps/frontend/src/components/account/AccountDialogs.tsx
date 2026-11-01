'use client';

import { useEffect, useRef, useState } from 'react';
import { Button } from '@/components/ui/Button';
import { Dialog } from '@/components/ui/Dialog';
import { FormField } from '@/components/ui/FormField';
import { Input } from '@/components/ui/Input';
import { PasswordField } from '@/components/pub/PasswordField';
import { OTP_INPUT_CLASS } from '@/components/pub/otp';
import { Notice } from '@/components/dash/Notice';
import { FilterChips } from '@/components/dash/FilterChips';
import { useI18n } from '@/lib/i18n';
import { parseApiError } from '@/lib/apiError';
import { isValidPhone, normalizePhone } from '@/lib/auth';
import { changePassword, deleteAccount, sendDeletionCode, sendPhoneCode, updateMe, verifyPhoneCode, type Me } from '@/lib/api/account';
import type { User } from '@/types';

const MIN_PASSWORD = 10;
const RESEND_SECONDS = 60;

type Errors = Record<string, string | undefined>;

/** busy flag + page-level message + server field messages for one dialog. */
function useDialogRequest() {
  const { t } = useI18n();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [fields, setFields] = useState<Errors>({});
  const reset = () => {
    setBusy(false);
    setError('');
    setFields({});
  };
  /** Runs `action`; returns true on success. On failure the message and the field messages are kept for display. */
  const run = async (action: () => Promise<unknown>, fallback?: string): Promise<boolean> => {
    setBusy(true);
    setError('');
    setFields({});
    try {
      await action();
      return true;
    } catch (err) {
      const parsed = parseApiError(err, t, { fallback: fallback ?? t('common.error') });
      setError(parsed.message);
      setFields(parsed.fields);
      return false;
    } finally {
      setBusy(false);
    }
  };
  return { busy, error, fields, reset, run };
}

function useCountdown(seconds: number) {
  const [left, setLeft] = useState(0);
  useEffect(() => {
    if (left <= 0) return;
    const id = setTimeout(() => setLeft((s) => s - 1), 1000);
    return () => clearTimeout(id);
  }, [left]);
  return { left, start: () => setLeft(seconds) };
}

function FooterButtons({ busy, onClose, label, danger = false, disabled = false }: { busy: boolean; onClose: () => void; label: string; danger?: boolean; disabled?: boolean }) {
  const { t } = useI18n();
  return (
    <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
      <Button type="button" variant="secondary" onClick={onClose} disabled={busy}>
        {t('common.cancel')}
      </Button>
      <Button type="submit" variant={danger ? 'danger' : 'primary'} loading={busy} disabled={disabled}>
        {label}
      </Button>
    </div>
  );
}

interface DialogProps {
  open: boolean;
  onClose: () => void;
}

/** New phone number: a code is texted to the NEW number, then the account is changed (the number must be verified first). */
export function ChangePhoneDialog({ open, onClose, currentPhone, onChanged }: DialogProps & { currentPhone: string; onChanged: (me: Me) => void }) {
  const { t } = useI18n();
  const request = useDialogRequest();
  const countdown = useCountdown(RESEND_SECONDS);
  const [step, setStep] = useState<'number' | 'code'>('number');
  const [phone, setPhone] = useState('');
  const [code, setCode] = useState('');
  const [errors, setErrors] = useState<Errors>({});
  const codeRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (open) {
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setStep('number');
      setPhone('');
      setCode('');
      setErrors({});
      request.reset();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  useEffect(() => {
    if (step === 'code') codeRef.current?.querySelector('input')?.focus();
  }, [step]);

  const sendCode = async () => {
    const next: Errors = {};
    if (!phone.trim()) next.phoneNumber = t('authFlow.phoneRequired');
    else if (!isValidPhone(phone)) next.phoneNumber = t('authFlow.phoneInvalid');
    else if (normalizePhone(phone) === currentPhone) next.phoneNumber = t('authFlow.errors.user.phone.unchanged');
    setErrors(next);
    if (Object.keys(next).length) return;
    if (await request.run(() => sendPhoneCode(normalizePhone(phone)), t('authFlow.otpSendFailed'))) {
      setStep('code');
      countdown.start();
    }
  };

  const confirm = async () => {
    if (!code.trim()) {
      setErrors({ otpCode: t('authFlow.otpRequired') });
      return;
    }
    setErrors({});
    const phoneNumber = normalizePhone(phone);
    let me: Me | null = null;
    const ok = await request.run(async () => {
      await verifyPhoneCode(phoneNumber, code.trim());
      me = await updateMe({ phoneNumber });
    });
    if (ok && me) {
      onChanged(me);
      onClose();
    }
  };

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    if (request.busy) return;
    if (step === 'number') void sendCode();
    else void confirm();
  };

  return (
    <Dialog open={open} onClose={onClose} title={t('account.phoneDialogTitle')} description={step === 'number' ? t('account.phoneDialogDesc') : t('authFlow.otpSent', { phone: normalizePhone(phone) })}>
      <form onSubmit={submit} noValidate className="space-y-4">
        {step === 'number' ? (
          <FormField label={t('account.newPhone')} error={errors.phoneNumber ?? request.fields.phoneNumber} required>
            {(p) => <Input {...p} type="tel" inputMode="tel" placeholder="07XXXXXXXX" autoComplete="tel" value={phone} onChange={(e) => setPhone(e.target.value)} />}
          </FormField>
        ) : (
          <>
            <div ref={codeRef}>
              <FormField label={t('authFlow.otpLabel')} error={errors.otpCode ?? request.fields.otpCode} required>
                {(p) => (
                  <Input
                    {...p}
                    inputMode="numeric"
                    maxLength={6}
                    autoComplete="one-time-code"
                    placeholder="······"
                    className={OTP_INPUT_CLASS}
                    value={code}
                    onChange={(e) => setCode(e.target.value)}
                  />
                )}
              </FormField>
            </div>
            <div className="flex flex-wrap items-center justify-between gap-3 text-sm">
              <button type="button" onClick={() => setStep('number')} className="inline-flex min-h-11 items-center text-gray-400 hover:text-foreground">
                {t('authFlow.backToInfo')}
              </button>
              <button
                type="button"
                onClick={() => void sendCode()}
                disabled={countdown.left > 0 || request.busy}
                className="inline-flex min-h-11 items-center text-primary-text underline-offset-4 hover:underline disabled:cursor-not-allowed disabled:text-gray-500 disabled:no-underline"
              >
                {countdown.left > 0 ? t('authFlow.resendIn', { seconds: countdown.left }) : t('authFlow.resend')}
              </button>
            </div>
          </>
        )}
        {request.error && <Notice tone="error">{request.error}</Notice>}
        <FooterButtons busy={request.busy} onClose={onClose} label={step === 'number' ? t('authFlow.sendOtp') : t('account.confirmPhone')} />
      </form>
    </Dialog>
  );
}

/** Current password + new password. Success returns a NEW session (the other sessions stop working). */
export function ChangePasswordDialog({ open, onClose, onChanged }: DialogProps & { onChanged: (accessToken: string, user: User) => void }) {
  const { t } = useI18n();
  const request = useDialogRequest();
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [confirm, setConfirm] = useState('');
  const [show, setShow] = useState(false);
  const [errors, setErrors] = useState<Errors>({});

  useEffect(() => {
    if (open) {
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setCurrent('');
      setNext('');
      setConfirm('');
      setShow(false);
      setErrors({});
      request.reset();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (request.busy) return;
    const found: Errors = {};
    if (!current) found.currentPassword = t('authFlow.passwordRequired');
    if (!next) found.newPassword = t('authFlow.passwordRequired');
    else if (next.length < MIN_PASSWORD) found.newPassword = t('authFlow.passwordShort');
    if (next !== confirm) found.confirm = t('authFlow.passwordMismatch');
    setErrors(found);
    if (Object.keys(found).length) return;
    let session: { accessToken: string; user: User } | null = null;
    const ok = await request.run(async () => {
      session = await changePassword(current, next);
    });
    if (ok && session) {
      const done = session as { accessToken: string; user: User };
      onChanged(done.accessToken, done.user);
      onClose();
    }
  };

  const fieldError = (name: string) => errors[name] ?? request.fields[name];

  return (
    <Dialog open={open} onClose={onClose} title={t('account.passwordDialogTitle')} description={t('account.passwordDialogDesc')}>
      <form onSubmit={submit} noValidate className="space-y-4">
        <PasswordField id="acc-current-password" label={t('account.currentPassword')} autoComplete="current-password" value={current} onChange={(e) => setCurrent(e.target.value)} error={fieldError('currentPassword')} visible={show} onVisibleChange={setShow} />
        <PasswordField
          id="acc-new-password"
          label={t('authFlow.newPassword')}
          hint={t('authFlow.passwordHint', { min: MIN_PASSWORD })}
          autoComplete="new-password"
          value={next}
          onChange={(e) => setNext(e.target.value)}
          error={fieldError('newPassword')}
          visible={show}
          onVisibleChange={setShow}
        />
        <PasswordField id="acc-confirm-password" label={t('auth.confirmPassword')} autoComplete="new-password" value={confirm} onChange={(e) => setConfirm(e.target.value)} error={fieldError('confirm')} visible={show} onVisibleChange={setShow} />
        {request.error && <Notice tone="error">{request.error}</Notice>}
        <FooterButtons busy={request.busy} onClose={onClose} label={t('account.changePassword')} />
      </form>
    </Dialog>
  );
}

/** Erases the account after the person confirms with their password, or with a code texted to their phone. */
export function DeleteAccountDialog({ open, onClose, hasPassword, onDeleted }: DialogProps & { hasPassword: boolean; onDeleted: () => void }) {
  const { t } = useI18n();
  const request = useDialogRequest();
  const sending = useDialogRequest();
  const countdown = useCountdown(RESEND_SECONDS);
  const [method, setMethod] = useState<'password' | 'sms'>(hasPassword ? 'password' : 'sms');
  const [password, setPassword] = useState('');
  const [show, setShow] = useState(false);
  const [code, setCode] = useState('');
  const [codeSent, setCodeSent] = useState(false);
  const [errors, setErrors] = useState<Errors>({});

  useEffect(() => {
    if (open) {
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setMethod(hasPassword ? 'password' : 'sms');
      setPassword('');
      setShow(false);
      setCode('');
      setCodeSent(false);
      setErrors({});
      request.reset();
      sending.reset();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  const sendCode = async () => {
    if (await sending.run(() => sendDeletionCode(), t('authFlow.otpSendFailed'))) {
      setCodeSent(true);
      countdown.start();
    }
  };

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (request.busy) return;
    if (method === 'password' && !password) {
      setErrors({ password: t('authFlow.passwordRequired') });
      return;
    }
    if (method === 'sms' && !code.trim()) {
      setErrors({ otpCode: t('authFlow.otpRequired') });
      return;
    }
    setErrors({});
    const ok = await request.run(() => deleteAccount(method === 'password' ? { password } : { otpCode: code.trim() }));
    if (ok) {
      onDeleted();
      onClose();
    }
  };

  return (
    <Dialog open={open} onClose={onClose} title={t('account.deleteDialogTitle')} description={t('account.deleteDialogDesc')}>
      <form onSubmit={submit} noValidate className="space-y-4">
        <Notice tone="warning">{t('account.deleteWarning')}</Notice>
        {hasPassword && (
          <FilterChips
            label={t('account.confirmWith')}
            value={method}
            onChange={(m) => {
              setMethod(m);
              setErrors({});
            }}
            options={[
              { value: 'password', label: t('account.confirmPasswordOption') },
              { value: 'sms', label: t('account.confirmSmsOption') },
            ]}
          />
        )}
        {method === 'password' ? (
          <PasswordField id="acc-delete-password" label={t('auth.password')} autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} error={errors.password ?? request.fields.password} visible={show} onVisibleChange={setShow} />
        ) : (
          <div className="space-y-3">
            <p className="text-sm text-gray-400">{t('account.smsConfirmDesc')}</p>
            <Button type="button" variant="secondary" onClick={() => void sendCode()} loading={sending.busy} disabled={countdown.left > 0}>
              {countdown.left > 0 ? t('authFlow.resendIn', { seconds: countdown.left }) : codeSent ? t('authFlow.resend') : t('account.sendCode')}
            </Button>
            {codeSent && <p role="status" className="text-sm text-success-text">{t('account.codeSent')}</p>}
            {sending.error && <Notice tone="error">{sending.error}</Notice>}
            <FormField label={t('authFlow.otpLabel')} error={errors.otpCode ?? request.fields.otpCode}>
              {(p) => <Input {...p} inputMode="numeric" maxLength={6} autoComplete="one-time-code" placeholder="······" className={OTP_INPUT_CLASS} value={code} onChange={(e) => setCode(e.target.value)} />}
            </FormField>
          </div>
        )}
        {request.error && <Notice tone="error">{request.error}</Notice>}
        <FooterButtons busy={request.busy} onClose={onClose} label={t('account.deleteConfirm')} danger />
      </form>
    </Dialog>
  );
}
