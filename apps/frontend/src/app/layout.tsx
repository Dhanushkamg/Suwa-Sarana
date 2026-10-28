import type { Metadata, Viewport } from 'next';
import {
  Geist,
  Geist_Mono,
  Instrument_Serif,
  Noto_Sans_Sinhala,
  Noto_Serif_Sinhala,
  Noto_Sans_Tamil,
  Noto_Serif_Tamil,
} from 'next/font/google';
import { I18nProvider } from '@/lib/i18n';
import DonorFaqChatbot from '@/components/chat/DonorFaqChatbot';
import { GoogleAuthProvider } from '@/providers/GoogleAuthProvider';
import { SITE_URL } from '@/lib/constants';
import { ThemeProvider, THEME_INIT_SCRIPT } from '@/components/theme/ThemeProvider';
import { SkipLink } from '@/components/ui/SkipLink';
import { ToastProvider } from '@/components/ui/Toast';
import './globals.css';

// Latin: Geist for UI, Instrument Serif for editorial headings, Geist Mono for small captions.
// Sinhala/Tamil: Noto families are listed after them in the font stacks (see globals.css) and only download when those scripts are shown.
const geist = Geist({ subsets: ['latin'], variable: '--font-geist', display: 'swap' });
const geistMono = Geist_Mono({ subsets: ['latin'], variable: '--font-geist-mono', display: 'swap' });
const instrument = Instrument_Serif({ subsets: ['latin'], weight: '400', style: ['normal', 'italic'], variable: '--font-instrument', display: 'swap' });
const sinhala = Noto_Sans_Sinhala({ subsets: ['sinhala'], variable: '--font-sinhala', display: 'swap' });
const sinhalaSerif = Noto_Serif_Sinhala({ subsets: ['sinhala'], variable: '--font-sinhala-serif', display: 'swap' });
const tamil = Noto_Sans_Tamil({ subsets: ['tamil'], variable: '--font-tamil', display: 'swap' });
const tamilSerif = Noto_Serif_Tamil({ subsets: ['tamil'], variable: '--font-tamil-serif', display: 'swap' });
const fontVars = [geist, geistMono, instrument, sinhala, sinhalaSerif, tamil, tamilSerif].map((f) => f.variable).join(' ');

export const viewport: Viewport = {
  themeColor: [
    { media: '(prefers-color-scheme: light)', color: '#f8fafc' },
    { media: '(prefers-color-scheme: dark)', color: '#0e1116' },
  ],
  colorScheme: 'light dark',
};

export const metadata: Metadata = {
  metadataBase: new URL(SITE_URL),
  title: {
    default: 'Suwa Sarana — Community Blood Donation Platform',
    template: '%s | Suwa Sarana',
  },
  description:
    'Suwa Sarana connects voluntary blood donors with patients in need across Sri Lanka. Register as a donor, find compatible matches, and save lives — in Sinhala, Tamil, or English.',
  keywords: ['blood donation', 'Sri Lanka', 'donor matching', 'Suwa Sarana', 'ශ්‍රී ලංකා', 'இலங்கை'],
  openGraph: {
    title: 'Suwa Sarana — Community Blood Donation Platform',
    description: 'Connecting voluntary blood donors with patients across Sri Lanka.',
    type: 'website',
  },
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    // suppressHydrationWarning: the inline script below sets data-theme before React hydrates
    <html lang="en" className={`${fontVars} h-full`} suppressHydrationWarning>
      <head>
        <script dangerouslySetInnerHTML={{ __html: THEME_INIT_SCRIPT }} />
      </head>
      <body className="min-h-full flex flex-col antialiased">
        <ThemeProvider>
          <GoogleAuthProvider>
            <I18nProvider defaultLocale="en">
              <ToastProvider>
                <SkipLink />
                {children}
                <DonorFaqChatbot />
              </ToastProvider>
            </I18nProvider>
          </GoogleAuthProvider>
        </ThemeProvider>
      </body>
    </html>
  );
}
