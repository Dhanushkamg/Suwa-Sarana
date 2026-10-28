import TermsClient from './TermsClient';
import { localizedMeta } from '@/components/pub/pageMeta';

export const generateMetadata = () => localizedMeta('pub.meta.terms', 'pub.meta.termsDesc');

export default function TermsPage() {
  return <TermsClient />;
}
