/**
 * Public contact details. Configure per deployment (NEXT_PUBLIC_*); the defaults are placeholders
 * until the owner confirms real addresses (audit F38).
 */
export const CONTACT = {
  email: process.env.NEXT_PUBLIC_CONTACT_EMAIL || 'support@suwasarana.lk',
  phone: process.env.NEXT_PUBLIC_CONTACT_PHONE || '+94 11 234 5678',
  privacyEmail: process.env.NEXT_PUBLIC_PRIVACY_EMAIL || 'privacy@suwasarana.lk',
  legalEmail: process.env.NEXT_PUBLIC_LEGAL_EMAIL || 'legal@suwasarana.lk',
};

/** "+94 11 234 5678" -> "+94112345678" for tel: links */
export const telHref = (phone: string) => `tel:${phone.replace(/[^+\d]/g, '')}`;
