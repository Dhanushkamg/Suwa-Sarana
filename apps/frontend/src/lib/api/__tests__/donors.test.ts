import { beforeEach, describe, expect, it, vi } from 'vitest';

const http = vi.hoisted(() => ({ get: vi.fn(), put: vi.fn(), post: vi.fn(), patch: vi.fn(), delete: vi.fn() }));
vi.mock('@/lib/apiClient', () => ({ default: http }));

import { reasonText } from '@/components/dash/DonorPanels';
import { translateIn } from '@/lib/i18n';
import { ELIGIBILITY_REASONS, fetchDonations, fetchEligibility } from '../donors';

const envelope = (data: unknown) => ({ data: { success: true, message: 'Success', data, timestamp: '2026-10-31T08:47:50.712Z' } });

beforeEach(() => vi.clearAllMocks());

describe('donations', () => {
  it('reads donatedOn and hostName (real DonationHistoryDto), newest first', async () => {
    http.get.mockResolvedValueOnce(
      envelope([
        { id: 3, donatedOn: '2026-08-02', source: 'SLOT_CHECKIN', hostName: 'National Blood Centre', district: 'Colombo', bloodType: 'O+', requestId: null },
        { id: 9, donatedOn: '2026-10-11', source: 'MATCH', hostName: 'Karapitiya Teaching Hospital', district: 'Galle', bloodType: 'O+', requestId: 41 },
      ])
    );
    const rows = await fetchDonations();
    expect(http.get).toHaveBeenCalledWith('/donors/me/donations', { params: { limit: 100 } });
    expect(rows.map((r) => r.id)).toEqual([9, 3]);
    expect(rows[0]).toMatchObject({ hostName: 'Karapitiya Teaching Hospital', district: 'Galle', bloodType: 'O+', source: 'MATCH', requestId: 41 });
    expect(rows[0].donatedOn?.toISOString()).toBe('2026-10-11T06:30:00.000Z'); // noon in Colombo: the same calendar day everywhere
    expect(rows[1].requestId).toBeNull();
  });
});

describe('eligibility', () => {
  const body = {
    eligible: false,
    nextEligibleDate: '2027-01-11',
    reasons: ['RECENT_DONATION'],
    evaluatedOn: '2026-10-31',
    lastDonationDate: '2026-09-11',
    totalDonations: 4,
    rules: { minAge: 18, maxAge: 60, maxAgeFirstTimeDonor: 55, minWeightKgExclusive: 50, donationIntervalMonths: 4, minHaemoglobinGdLExclusive: 12.5, source: 'https://nbts.health.gov.lk/donate-blood/', ownerConfirmed: false },
    rulesConfirmedByOwner: false,
  };

  it('reads reason codes, dates and rules', async () => {
    http.get.mockResolvedValueOnce(envelope(body));
    const e = await fetchEligibility();
    expect(e).toMatchObject({ eligible: false, reasons: ['RECENT_DONATION'], totalDonations: 4, rulesConfirmedByOwner: false });
    expect(e.nextEligibleDate?.toISOString()).toBe('2027-01-11T06:30:00.000Z');
    expect(e.rules).toMatchObject({ minAge: 18, maxAgeFirstTimeDonor: 55, minWeightKgExclusive: 50, donationIntervalMonths: 4, ownerConfirmed: false });
  });

  it('an eligible donor has no next date and no reasons', async () => {
    http.get.mockResolvedValueOnce(envelope({ ...body, eligible: true, nextEligibleDate: null, reasons: [], lastDonationDate: null }));
    const e = await fetchEligibility();
    expect(e).toMatchObject({ eligible: true, nextEligibleDate: null, reasons: [], lastDonationDate: null });
  });

  it('every reason code the server can send has a translation in en, si and ta', () => {
    for (const locale of ['en', 'si', 'ta'] as const) {
      const t = (key: string) => translateIn(locale, key);
      for (const code of ELIGIBILITY_REASONS) {
        expect(t(`dashUi.donor.reason.${code}`), `${locale} ${code}`).not.toBe(`dashUi.donor.reason.${code}`);
      }
    }
  });

  it('shows the translated sentence, and a generic one (never the raw code) for an unknown code', () => {
    const t = (key: string) => translateIn('en', key);
    expect(reasonText(t, 'RECENT_DONATION')).toContain('donated recently');
    expect(reasonText(t, 'SOMETHING_NEW')).toContain('Medical Officer');
    expect(reasonText(t, 'SOMETHING_NEW')).not.toContain('SOMETHING_NEW');
  });
});
