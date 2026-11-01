import { beforeEach, describe, expect, it, vi } from 'vitest';

const http = vi.hoisted(() => ({ get: vi.fn(), put: vi.fn(), post: vi.fn(), delete: vi.fn() }));
vi.mock('@/lib/apiClient', () => ({ default: http }));

import {
  COMPONENTS,
  attentionLines,
  confirmStock,
  discardBatch,
  fetchMovements,
  fetchStock,
  fetchThresholds,
  issueStock,
  recordBatch,
  saveSite,
  saveThreshold,
  updateBatch,
} from '../inventory';
import { fetchDistrictMeta, resetDistrictMetaCache, toDistrictMeta } from '../meta';

const envelope = (data: unknown) => ({ data: { success: true, message: 'Success', data, timestamp: '2026-10-31T08:47:50.712Z' } });

/** Shape of GET /api/blood-bank/stock (InventoryDtos.OverviewDto). */
const overview = {
  site: { siteName: 'Teaching Hospital Kandy (Demo)', district: 'Kandy', latitude: 7.2906, longitude: 80.6337, publicSignals: true, confirmedAt: '2026-10-31T03:15:00Z' },
  asOf: '2026-10-31T08:47:50.628Z',
  expiringSoonDays: 3,
  lines: [
    { bloodType: 'A+', component: 'WHOLE_BLOOD', units: 40, expiringSoonUnits: 0, nextExpiry: '2026-11-20', status: 'OK', lowThreshold: 10, criticalThreshold: 5 },
    { bloodType: 'O-', component: 'RBC', units: 4, expiringSoonUnits: 0, nextExpiry: '2026-11-12', status: 'CRITICAL', lowThreshold: 10, criticalThreshold: 5 },
    { bloodType: 'B+', component: 'PLATELETS', units: 12, expiringSoonUnits: 12, nextExpiry: '2026-11-02', status: 'EXPIRING_SOON', lowThreshold: null, criticalThreshold: null },
    { bloodType: 'AB-', component: 'PLASMA', units: 8, expiringSoonUnits: 0, nextExpiry: '2026-12-01', status: 'LOW', lowThreshold: 10, criticalThreshold: 3 },
  ],
  batches: [
    { id: 301, bloodType: 'O-', component: 'RBC', units: 4, collectedOn: '2026-10-20', expiresOn: '2026-11-12', status: 'ACTIVE', daysToExpiry: 12 },
  ],
};

beforeEach(() => vi.clearAllMocks());

describe('blood-bank stock', () => {
  it('uses the Java component enum (RBC, not RED_CELLS)', () => {
    expect([...COMPONENTS]).toEqual(['WHOLE_BLOOD', 'RBC', 'PLATELETS', 'PLASMA']);
  });

  it('reads lines, batches, site and status chips from /blood-bank/stock', async () => {
    http.get.mockResolvedValueOnce(envelope(overview));
    const stock = await fetchStock();
    expect(http.get).toHaveBeenCalledWith('/blood-bank/stock');
    expect(stock.expiringSoonDays).toBe(3);
    expect(stock.lines.map((l) => [l.bloodType, l.component, l.status])).toEqual([
      ['A+', 'WHOLE_BLOOD', 'OK'],
      ['O-', 'RBC', 'CRITICAL'],
      ['B+', 'PLATELETS', 'EXPIRING_SOON'],
      ['AB-', 'PLASMA', 'LOW'],
    ]);
    expect(stock.lines[2]).toMatchObject({ lowThreshold: null, criticalThreshold: null, expiringSoonUnits: 12, nextExpiry: '2026-11-02' });
    expect(stock.batches[0]).toMatchObject({ id: 301, expiresOn: '2026-11-12', status: 'ACTIVE', daysToExpiry: 12 });
    expect(stock.site).toMatchObject({ district: 'Kandy', publicSignals: true });
    expect(stock.site.confirmedAt?.toISOString()).toBe('2026-10-31T03:15:00.000Z');
  });

  it('copes with an empty institution (no site name, no lines)', async () => {
    http.get.mockResolvedValueOnce(envelope({ site: { siteName: null, district: null, latitude: null, longitude: null, publicSignals: false, confirmedAt: null }, asOf: '2026-10-31T08:47:50.628Z', expiringSoonDays: 3, lines: [], batches: [] }));
    const stock = await fetchStock();
    expect(stock.lines).toEqual([]);
    expect(stock.site).toMatchObject({ siteName: null, district: null, publicSignals: false, confirmedAt: null });
  });

  it('lists what needs attention, worst first', async () => {
    http.get.mockResolvedValueOnce(envelope(overview));
    const { lines } = await fetchStock();
    expect(attentionLines(lines).map((l) => l.status)).toEqual(['CRITICAL', 'LOW', 'EXPIRING_SOON']);
  });

  it('records a batch with the required expiry date and without an empty collection date', async () => {
    http.post.mockResolvedValueOnce(envelope({ id: 5, bloodType: 'O+', component: 'RBC', units: 6, collectedOn: '2026-10-31', expiresOn: '2026-12-05', status: 'ACTIVE', daysToExpiry: 35 }));
    const batch = await recordBatch({ bloodType: 'O+', component: 'RBC', units: 6, expiresOn: '2026-12-05' });
    expect(http.post).toHaveBeenCalledWith('/blood-bank/stock', { bloodType: 'O+', component: 'RBC', units: 6, expiresOn: '2026-12-05' });
    expect(batch.id).toBe(5);
  });

  it('updates, discards and issues through the stock endpoints', async () => {
    http.put.mockResolvedValue(envelope({ id: 5, bloodType: 'O+', component: 'RBC', units: 0, collectedOn: null, expiresOn: '2026-12-05', status: 'DEPLETED', daysToExpiry: 35 }));
    const updated = await updateBatch(5, { units: 0, note: ' counted again ' });
    expect(http.put).toHaveBeenCalledWith('/blood-bank/stock/5', { units: 0, note: 'counted again' });
    expect(updated.status).toBe('DEPLETED');

    http.delete.mockResolvedValue(envelope(null));
    await discardBatch(5);
    expect(http.delete).toHaveBeenCalledWith('/blood-bank/stock/5');

    http.post.mockResolvedValueOnce(envelope([{ id: 9, unitId: 301, bloodType: 'O-', component: 'RBC', delta: -2, reason: 'ISSUE', requestId: 77, note: null, createdAt: '2026-10-31T09:00:00Z' }]));
    const moves = await issueStock({ bloodType: 'O-', component: 'RBC', units: 2, requestId: 77 });
    expect(http.post).toHaveBeenLastCalledWith('/blood-bank/stock/issue', { bloodType: 'O-', component: 'RBC', units: 2, requestId: 77 });
    expect(moves[0]).toMatchObject({ delta: -2, reason: 'ISSUE', requestId: 77 });
  });

  it('confirms the stock and reads the movement log', async () => {
    http.post.mockResolvedValueOnce(envelope({ siteName: 'X', district: 'Kandy', latitude: 7.29, longitude: 80.63, publicSignals: true, confirmedAt: '2026-10-31T09:10:00Z' }));
    const site = await confirmStock();
    expect(http.post).toHaveBeenCalledWith('/blood-bank/stock/confirm');
    expect(site.confirmedAt?.toISOString()).toBe('2026-10-31T09:10:00.000Z');

    http.get.mockResolvedValueOnce(envelope([{ id: 1, unitId: null, bloodType: 'A+', component: 'WHOLE_BLOOD', delta: 20, reason: 'COLLECTION', requestId: null, note: null, createdAt: '2026-10-30T05:00:00Z' }]));
    expect(await fetchMovements(30)).toHaveLength(1);
    expect(http.get).toHaveBeenLastCalledWith('/blood-bank/stock/movements', { params: { limit: 30 } });
  });

  it('thresholds are {low, critical}', async () => {
    http.get.mockResolvedValueOnce(envelope([{ id: 1, bloodType: 'O-', component: 'RBC', low: 10, critical: 5 }]));
    expect(await fetchThresholds()).toEqual([{ id: 1, bloodType: 'O-', component: 'RBC', low: 10, critical: 5 }]);
    expect(http.get).toHaveBeenLastCalledWith('/blood-bank/thresholds');
    http.put.mockResolvedValueOnce(envelope({ id: 2, bloodType: 'A+', component: 'WHOLE_BLOOD', low: 20, critical: 8 }));
    await saveThreshold({ bloodType: 'A+', component: 'WHOLE_BLOOD', low: 20, critical: 8 });
    expect(http.put).toHaveBeenLastCalledWith('/blood-bank/thresholds', { bloodType: 'A+', component: 'WHOLE_BLOOD', low: 20, critical: 8 });
  });

  it('saves the site profile', async () => {
    http.put.mockResolvedValueOnce(envelope({ siteName: 'Kandy', district: 'Kandy', latitude: 7.29, longitude: 80.63, publicSignals: false, confirmedAt: null }));
    const site = await saveSite({ district: 'Kandy', publicSignals: false });
    expect(http.put).toHaveBeenCalledWith('/blood-bank/site', { district: 'Kandy', publicSignals: false });
    expect(site.publicSignals).toBe(false);
  });
});

describe('GET /api/meta/districts', () => {
  const body = envelope({
    districts: [
      { name: 'Colombo', latitude: 6.9271, longitude: 79.8612 },
      { name: 'Galle', latitude: 6.0535, longitude: 80.221 },
    ],
    bounds: { minLat: 5.8, maxLat: 9.9, minLon: 79.4, maxLon: 82.0 },
  });

  beforeEach(() => resetDistrictMetaCache());

  it('maps districts and bounds', () => {
    const meta = toDistrictMeta(body.data);
    expect(meta.districts.map((d) => d.name)).toEqual(['Colombo', 'Galle']);
    expect(meta.bounds).toEqual({ minLat: 5.8, maxLat: 9.9, minLon: 79.4, maxLon: 82.0 });
    expect(meta.fallback).toBe(false);
  });

  it('is requested once and cached', async () => {
    http.get.mockResolvedValue(body);
    await fetchDistrictMeta();
    await fetchDistrictMeta();
    expect(http.get).toHaveBeenCalledTimes(1);
    expect(http.get).toHaveBeenCalledWith('/meta/districts');
  });

  it('falls back to the built-in 25 districts when the server does not answer, and retries next time', async () => {
    http.get.mockRejectedValueOnce(new Error('Network Error'));
    const meta = await fetchDistrictMeta();
    expect(meta.fallback).toBe(true);
    expect(meta.districts).toHaveLength(25);
    http.get.mockResolvedValueOnce(body);
    expect((await fetchDistrictMeta()).fallback).toBe(false);
  });
});
