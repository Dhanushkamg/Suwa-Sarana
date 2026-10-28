import PrivacyClient from './PrivacyClient';
import { localizedMeta } from '@/components/pub/pageMeta';

export const generateMetadata = () => localizedMeta('pub.meta.privacy', 'pub.meta.privacyDesc');

export default function PrivacyPage() {
  return <PrivacyClient />;
}
