import type { Metadata } from 'next';
import { Inter } from 'next/font/google';
import { I18nProvider } from '@/lib/i18n';
import './globals.css';

const inter = Inter({
  subsets: ['latin'],
  variable: '--font-inter',
  display: 'swap',
});

export const metadata: Metadata = {
  title: 'Suwa Sarana — Community Blood Donation Platform',
  description:
    'Suwa Sarana connects voluntary blood donors with patients in need across Sri Lanka. Register as a donor, find compatible matches, and save lives — in Sinhala, Tamil, or English.',
  keywords: ['blood donation', 'Sri Lanka', 'donor matching', 'Suwa Sarana', 'ශ්‍රී ලංකා', 'இலங்கை'],
  openGraph: {
    title: 'Suwa Sarana — Community Blood Donation Platform',
    description: 'Connecting voluntary blood donors with patients across Sri Lanka.',
    type: 'website',
  },
};

async function loadMessages(locale: string) {
  try {
    return (await import(`../../messages/${locale}.json`)).default;
  } catch {
    return (await import('../../messages/en.json')).default;
  }
}

export default async function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  // Default locale — middleware will handle locale-prefixed routes
  const locale = 'en';
  const messages = await loadMessages(locale);

  return (
    <html lang={locale} className={`${inter.variable} h-full`}>
      <body className="min-h-full flex flex-col antialiased">
        <I18nProvider locale={locale} messages={messages}>
          {children}
        </I18nProvider>
      </body>
    </html>
  );
}
