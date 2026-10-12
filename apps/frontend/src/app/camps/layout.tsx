import type { Metadata } from 'next';

export const metadata: Metadata = { title: 'Donation camps' };

export default function Layout({ children }: { children: React.ReactNode }) {
  return children;
}
