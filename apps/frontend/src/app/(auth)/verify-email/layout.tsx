import { localizedMeta } from '@/components/pub/pageMeta';

export const generateMetadata = () => localizedMeta('pub.meta.verifyEmail');

export default function Layout({ children }: { children: React.ReactNode }) {
  return children;
}
