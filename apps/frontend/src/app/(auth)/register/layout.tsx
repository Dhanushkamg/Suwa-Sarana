import { localizedMeta } from '@/components/pub/pageMeta';

export const generateMetadata = () => localizedMeta('pub.meta.register');

export default function Layout({ children }: { children: React.ReactNode }) {
  return children;
}
