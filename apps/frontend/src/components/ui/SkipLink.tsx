'use client';

import { useI18n } from '@/lib/i18n';

/** First focusable element on every page: lets keyboard users jump past the navigation. Pages put id="main-content" on their <main>. */
export function SkipLink() {
  const { t } = useI18n();
  return (
    <a href="#main-content" className="skip-link">
      {t('a11y.skipToContent')}
    </a>
  );
}
