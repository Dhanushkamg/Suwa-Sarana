'use client';

// Last-resort boundary for errors in the root layout itself. It replaces the whole document, so it
// re-imports the global stylesheet (semantic tokens, light/dark) and sets the theme before paint.
// Fonts are not loaded here: the system font stack is used on purpose.
import './globals.css';
import { I18nProvider, useI18n } from '@/lib/i18n';
import { THEME_INIT_SCRIPT } from '@/components/theme/ThemeProvider';
import { buttonClasses } from '@/components/ui/Button';

function Message({ retry }: { retry: () => void }) {
  const { t } = useI18n();
  return (
    <main id="main-content" className="flex min-h-screen flex-col items-center justify-center gap-4 bg-background p-6 text-center text-foreground">
      <h1 className="text-2xl font-semibold">{t('errors.title')}</h1>
      <p role="alert" className="max-w-md text-gray-400">
        {t('errors.description')}
      </p>
      <button type="button" onClick={() => retry()} className={buttonClasses('primary', 'lg')}>
        {t('errors.retry')}
      </button>
    </main>
  );
}

export default function GlobalError({ retry }: { error: Error & { digest?: string }; retry: () => void }) {
  return (
    <html lang="en" suppressHydrationWarning>
      <head>
        <script dangerouslySetInnerHTML={{ __html: THEME_INIT_SCRIPT }} />
      </head>
      <body className="min-h-full">
        <I18nProvider>
          <Message retry={retry} />
        </I18nProvider>
      </body>
    </html>
  );
}
