import apiClient from '@/lib/apiClient';
import { asDate, asList, num } from './common';

/**
 * Blood-bank / hospital inventory.
 * TODO: adjust these paths and field names once the backend report for /api/inventory/me/** arrives.
 * Assumed contract:
 *   GET  /api/inventory/me
 *        -> [{ bloodType: 'O+', component: 'RED_CELLS', units: 12, threshold: 10, nearestExpiry?: 'YYYY-MM-DD' }]
 *   POST /api/inventory/me/collections   {bloodType, component, units, collectedOn: 'YYYY-MM-DD', expiresOn?: 'YYYY-MM-DD'}
 *   PUT  /api/inventory/me/thresholds    {bloodType, component, threshold}
 */
export const INVENTORY_PATHS = {
  stock: '/inventory/me',
  collections: '/inventory/me/collections',
  thresholds: '/inventory/me/thresholds',
} as const;

export const COMPONENTS = ['WHOLE_BLOOD', 'RED_CELLS', 'PLATELETS', 'PLASMA'] as const;
export type BloodComponent = (typeof COMPONENTS)[number];

export interface StockRow {
  bloodType: string;
  component: string;
  units: number;
  /** Minimum units wanted on the shelf; null when none has been set. */
  threshold: number | null;
  nearestExpiry: Date | null;
}

export type StockLevel = 'out' | 'low' | 'ok' | 'unknown';

export function stockLevel(row: Pick<StockRow, 'units' | 'threshold'> | undefined): StockLevel {
  if (!row) return 'unknown';
  if (row.units <= 0) return 'out';
  if (row.threshold != null && row.units < row.threshold) return 'low';
  return 'ok';
}

export async function fetchStock(): Promise<StockRow[]> {
  const res = await apiClient.get(INVENTORY_PATHS.stock);
  return asList<Record<string, unknown>>(res.data).map((r) => ({
    bloodType: String(r.bloodType ?? ''),
    component: String(r.component ?? 'WHOLE_BLOOD'),
    units: num(r.units ?? r.quantity),
    threshold: r.threshold == null && r.lowThreshold == null ? null : num(r.threshold ?? r.lowThreshold),
    nearestExpiry: asDate(r.nearestExpiry ?? r.expiresOn),
  }));
}

export interface CollectionInput {
  bloodType: string;
  component: string;
  units: number;
  collectedOn: string;
  expiresOn?: string;
}

export async function addCollection(input: CollectionInput): Promise<void> {
  await apiClient.post(INVENTORY_PATHS.collections, input);
}

export async function setThreshold(input: { bloodType: string; component: string; threshold: number }): Promise<void> {
  await apiClient.put(INVENTORY_PATHS.thresholds, input);
}

