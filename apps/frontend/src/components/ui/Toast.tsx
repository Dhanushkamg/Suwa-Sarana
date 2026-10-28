'use client';

import { createContext, useCallback, useContext, useMemo, useRef, useState } from 'react';
import { AlertCircle, CheckCircle2, Info, X } from 'lucide-react';
import { useI18n } from '@/lib/i18n';
import { cn } from '@/lib/utils';

type ToastTone = 'success' | 'error' | 'info';
interface ToastItem {
  id: number;
  tone: ToastTone;
  message: string;
}

const ToastContext = createContext<{ show: (message: string, tone?: ToastTone) => void }>({ show: () => {} });
export const useToast = () => useContext(ToastContext);

const STYLE: Record<ToastTone, { cls: string; Icon: typeof Info; icon: string }> = {
  success: { cls: 'border-success/40', Icon: CheckCircle2, icon: 'text-success-text' },
  error: { cls: 'border-danger/40', Icon: AlertCircle, icon: 'text-danger-text' },
  info: { cls: 'border-info/40', Icon: Info, icon: 'text-info-text' },
};

/** Wrap the app once. `useToast().show('Saved', 'success')` announces politely (errors assertively) and disappears after 6 s. */
export function ToastProvider({ children }: { children: React.ReactNode }) {
  const [items, setItems] = useState<ToastItem[]>([]);
  const nextId = useRef(1);
  const { t } = useI18n();

  const dismiss = useCallback((id: number) => setItems((cur) => cur.filter((i) => i.id !== id)), []);
  const show = useCallback(
    (message: string, tone: ToastTone = 'info') => {
      const id = nextId.current++;
      setItems((cur) => [...cur.slice(-3), { id, tone, message }]);
      window.setTimeout(() => dismiss(id), 6000);
    },
    [dismiss]
  );
  const value = useMemo(() => ({ show }), [show]);

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div className="pointer-events-none fixed inset-x-0 bottom-4 z-[150] flex flex-col items-center gap-2 px-4 sm:items-end sm:pe-6">
        {items.map(({ id, tone, message }) => {
          const { cls, Icon, icon } = STYLE[tone];
          return (
            <div
              key={id}
              role={tone === 'error' ? 'alert' : 'status'}
              className={cn('pointer-events-auto flex w-full max-w-sm items-start gap-3 rounded-xl border bg-surface p-4 text-sm text-foreground shadow-lg', cls)}
            >
              <Icon className={cn('mt-0.5 h-5 w-5 shrink-0', icon)} aria-hidden="true" />
              <p className="flex-1">{message}</p>
              <button
                type="button"
                onClick={() => dismiss(id)}
                aria-label={t('common.close')}
                className="-m-1.5 inline-flex h-9 w-9 items-center justify-center rounded-full text-muted hover:text-foreground"
              >
                <X className="h-4 w-4" aria-hidden="true" />
              </button>
            </div>
          );
        })}
      </div>
    </ToastContext.Provider>
  );
}
