'use client';

import { useI18n } from '@/lib/i18n';
import { LegalDocument, type LegalSection } from '@/components/pub/LegalDocument';

export default function TermsClient() {
  const { t } = useI18n();

  const sections: LegalSection[] = [
    { id: 'acceptance', title: t('terms.s1Title'), body: <p>{t('terms.s1Desc')}</p> },
    { id: 'eligibility', title: t('terms.s2Title'), body: <p>{t('terms.s2Desc')}</p> },
    { id: 'donors', title: t('terms.s3Title'), body: <p>{t('terms.s3Desc')}</p> },
    { id: 'requesters', title: t('terms.s4Title'), body: <p>{t('terms.s4Desc')}</p> },
    { id: 'liability', title: t('terms.s5Title'), body: <p>{t('terms.s5Desc')}</p> },
    { id: 'changes', title: t('terms.s6Title'), body: <p>{t('terms.s6Desc')}</p> },
    {
      id: 'contact',
      title: t('terms.s7Title'),
      body: (
        <p>
          {t('terms.s7Desc')}{' '}
          <a
            href="mailto:legal@suwasarana.lk"
            className="text-ember underline underline-offset-4 transition-colors hover:text-foreground"
          >
            legal@suwasarana.lk
          </a>
          .
        </p>
      ),
    },
  ];

  return (
    <LegalDocument
      page="terms"
      eyebrow={t('pub.legalEyebrow')}
      title={t('terms.title')}
      updated={t('terms.lastUpdated')}
      sections={sections}
    />
  );
}
