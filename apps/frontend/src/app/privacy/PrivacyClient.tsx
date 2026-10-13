'use client';

import Link from 'next/link';
import { useI18n } from '@/lib/i18n';
import { LegalDocument, type LegalSection } from '@/components/pub/LegalDocument';

const MAIL_LINK = 'text-ember underline underline-offset-4 transition-colors hover:text-foreground';

export default function PrivacyClient() {
  const { t } = useI18n();

  const sections: LegalSection[] = [
    { id: 'information', title: t('privacy.s1Title'), body: <p>{t('privacy.s1Desc')}</p> },
    { id: 'usage', title: t('privacy.s2Title'), body: <p>{t('privacy.s2Desc')}</p> },
    { id: 'sharing', title: t('privacy.s3Title'), body: <p>{t('privacy.s3Desc')}</p> },
    { id: 'security', title: t('privacy.s4Title'), body: <p>{t('privacy.s4Desc')}</p> },
    {
      id: 'rights',
      title: t('privacy.s5Title'),
      body: (
        <p>
          {t('privacy.s5Desc')}{' '}
          <a href="mailto:privacy@suwasarana.lk" className={MAIL_LINK}>
            privacy@suwasarana.lk
          </a>
          .
        </p>
      ),
    },
    {
      id: 'contact',
      title: t('privacy.s6Title'),
      body: (
        <p>
          {t('privacy.s6Desc')}{' '}
          <a href="mailto:privacy@suwasarana.lk" className={MAIL_LINK}>
            privacy@suwasarana.lk
          </a>{' '}
          {t('pub.orVisit')}{' '}
          <Link href="/contact" className={MAIL_LINK}>
            {t('pub.contactPage')}
          </Link>
          .
        </p>
      ),
    },
  ];

  return (
    <LegalDocument
      page="privacy"
      eyebrow={t('pub.legalEyebrow')}
      title={t('privacy.title')}
      updated={t('privacy.lastUpdated')}
      sections={sections}
    />
  );
}
