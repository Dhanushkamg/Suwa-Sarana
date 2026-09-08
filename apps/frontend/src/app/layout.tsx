import type { Metadata } from 'next';
import { Inter } from 'next/font/google';
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
  keywords: ['blood donation', 'Sri Lanka', 'donor matching', 'Suwa Sarana', 'ශ්‍රී ලංකා'],
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
    <html lang="en" className={`${inter.variable} h-full`}>
      <body className="min-h-full flex flex-col antialiased">{children}</body>
    </html>
  );
}
