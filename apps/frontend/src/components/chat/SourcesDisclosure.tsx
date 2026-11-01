'use client';

import { ChevronDown, ExternalLink } from 'lucide-react';
import { StatusBadge } from '@/components/ui/StatusBadge';
import { useI18n } from '@/lib/i18n';
import { safeSourceUrl, type FaqSource } from '@/lib/api/faq';

const STATUS_TONE = { VERIFIED: 'success', CONFLICT: 'warning', GAP: 'neutral', SECONDARY: 'neutral' } as const;

/**
 * "Show source": the stored sources an answer cites, collapsed by default. A native <details>, so it is keyboard and
 * screen-reader friendly without extra code. Each source says whether it is verified, in conflict with another source,
 * or a known gap (icon + text, never colour alone), and when it was last reviewed.
 */
export function SourcesDisclosure({ sources }: { sources: FaqSource[] }) {
  const { t, format } = useI18n();
  if (sources.length === 0) return null;

  return (
    <details className="group mt-2 rounded-xl border border-line bg-surface">
      <summary className="flex min-h-11 cursor-pointer list-none items-center justify-between gap-2 rounded-xl px-3 text-xs font-medium text-primary-text marker:hidden [&::-webkit-details-marker]:hidden">
        <span>{t('chatUi.showSources', { count: sources.length })}</span>
        <ChevronDown className="h-4 w-4 shrink-0 transition-transform group-open:rotate-180 motion-reduce:transition-none" aria-hidden="true" />
      </summary>
      <ul className="space-y-3 border-t border-line px-3 py-3">
        {sources.map((source) => {
          const href = safeSourceUrl(source.url);
          return (
            <li key={source.id} className="min-w-0 text-xs leading-relaxed">
              {href ? (
                <a
                  href={href}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="inline-flex min-h-6 items-start gap-1.5 break-words font-medium text-primary-text underline-offset-2 hover:underline"
                >
                  <span className="min-w-0 break-words">{source.title}</span>
                  <ExternalLink className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" />
                  <span className="sr-only">{t('pub.opensNewTab')}</span>
                </a>
              ) : (
                <span className="break-words font-medium text-foreground">{source.title}</span>
              )}
              <div className="mt-1.5 flex flex-wrap items-center gap-x-3 gap-y-1.5">
                <StatusBadge value={source.status} tone={STATUS_TONE[source.status]} label={t(`chatUi.sourceStatus.${source.status}`)} />
                {source.lastReviewed && (
                  <span className="text-muted">{t('chatUi.lastReviewed', { date: format.date(source.lastReviewed) })}</span>
                )}
              </div>
            </li>
          );
        })}
      </ul>
    </details>
  );
}
