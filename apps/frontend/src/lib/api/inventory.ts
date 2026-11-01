import apiClient from '@/lib/apiClient';
import { asDate, asList, num, str, unwrap } from './common';

/**
 * Blood-bank / hospital stock (docs/API-CONTRACT.md section 8). Only HOSPITAL_REQUESTER and BLOOD_BANK_REQUESTER may use these
 * endpoints; writes also need a VERIFIED institution with a confirmed e-mail (403 verification.required / email.verificationRequired).
 */
export const INVENTORY_PATHS = {
  stock: '/blood-bank/stock',
  batch: (id: number | string) => `/blood-bank/stock/${id}`,
  issue: '/blood-bank/stock/issue',
  confirm: '/blood-bank/stock/confirm',
  movements: '/blood-bank/stock/movements',
  thresholds: '/blood-bank/thresholds',
  site: '/blood-bank/site',
} as const;

/** The Java enum InventoryModel.Component. Note RBC, not RED_CELLS. */
export const COMPONENTS = ['WHOLE_BLOOD', 'RBC', 'PLATELETS', 'PLASMA'] as const;
export type BloodComponent = (typeof COMPONENTS)[number];

/** InventoryModel.StockStatus: what a stock line looks like against its thresholds and expiry dates. */
export type StockStatus = 'OK' | 'LOW' | 'CRITICAL' | 'EXPIRING_SOON';
const STATUSES: StockStatus[] = ['OK', 'LOW', 'CRITICAL', 'EXPIRING_SOON'];

export type BatchStatus = 'ACTIVE' | 'DEPLETED' | 'EXPIRED';
export type MovementReason = 'COLLECTION' | 'ISSUE' | 'EXPIRY' | 'ADJUSTMENT' | 'DISCARD';

export const MAX_UNITS = 100000;

export interface StockLine {
  bloodType: string;
  component: string;
  /** Usable units (not expired). */
  units: number;
  expiringSoonUnits: number;
  /** YYYY-MM-DD of the earliest-expiring usable batch. */
  nextExpiry: string | null;
  status: StockStatus;
  /** Below this the line is LOW; null = no threshold set. */
  lowThreshold: number | null;
  /** Below this the line is CRITICAL; null = no threshold set. */
  criticalThreshold: number | null;
}

export interface StockBatch {
  id: number;
  bloodType: string;
  component: string;
  units: number;
  collectedOn: string | null;
  expiresOn: string;
  status: BatchStatus;
  daysToExpiry: number;
}

export interface StockSite {
  siteName: string | null;
  district: string | null;
  latitude: number | null;
  longitude: number | null;
  /** True = the low/critical signals of this site appear in the public ticker (only when the stock was confirmed in the last 48 h). */
  publicSignals: boolean;
  confirmedAt: Date | null;
}

export interface StockOverview {
  site: StockSite;
  asOf: Date | null;
  expiringSoonDays: number;
  lines: StockLine[];
  batches: StockBatch[];
}

const optionalNum = (v: unknown): number | null => (v == null ? null : num(v));
const dateOnly = (v: unknown): string | null => (typeof v === 'string' && /^\d{4}-\d{2}-\d{2}/.test(v) ? v.slice(0, 10) : null);

export function toStockLine(r: Record<string, unknown>): StockLine {
  const status = String(r.status ?? '') as StockStatus;
  return {
    bloodType: String(r.bloodType ?? ''),
    component: String(r.component ?? 'WHOLE_BLOOD'),
    units: num(r.units),
    expiringSoonUnits: num(r.expiringSoonUnits),
    nextExpiry: dateOnly(r.nextExpiry),
    status: STATUSES.includes(status) ? status : 'OK',
    lowThreshold: optionalNum(r.lowThreshold),
    criticalThreshold: optionalNum(r.criticalThreshold),
  };
}

export function toBatch(r: Record<string, unknown>): StockBatch {
  const status = String(r.status ?? 'ACTIVE') as BatchStatus;
  return {
    id: num(r.id),
    bloodType: String(r.bloodType ?? ''),
    component: String(r.component ?? 'WHOLE_BLOOD'),
    units: num(r.units),
    collectedOn: dateOnly(r.collectedOn),
    expiresOn: dateOnly(r.expiresOn) ?? '',
    status: status === 'DEPLETED' || status === 'EXPIRED' ? status : 'ACTIVE',
    daysToExpiry: num(r.daysToExpiry),
  };
}

export function toSite(r: Record<string, unknown> | null | undefined): StockSite {
  const o = r ?? {};
  return {
    siteName: str(o.siteName) ?? null,
    district: str(o.district) ?? null,
    latitude: optionalNum(o.latitude),
    longitude: optionalNum(o.longitude),
    publicSignals: o.publicSignals === true,
    confirmedAt: asDate(o.confirmedAt),
  };
}

/** GET /api/blood-bank/stock -> { site, asOf, expiringSoonDays, lines, batches } */
export async function fetchStock(): Promise<StockOverview> {
  const res = await apiClient.get(INVENTORY_PATHS.stock);
  const body = (unwrap<Record<string, unknown>>(res.data) ?? {}) as Record<string, unknown>;
  return {
    site: toSite(body.site as Record<string, unknown> | undefined),
    asOf: asDate(body.asOf),
    expiringSoonDays: num(body.expiringSoonDays, 7),
    lines: asList<Record<string, unknown>>(body.lines).map(toStockLine),
    batches: asList<Record<string, unknown>>(body.batches).map(toBatch),
  };
}

/** Lines that need attention, worst first. */
export function attentionLines(lines: StockLine[]): StockLine[] {
  const rank: Record<StockStatus, number> = { CRITICAL: 0, LOW: 1, EXPIRING_SOON: 2, OK: 3 };
  return lines.filter((l) => l.status !== 'OK').sort((a, b) => rank[a.status] - rank[b.status] || a.units - b.units);
}

export interface BatchInput {
  bloodType: string;
  component: string;
  units: number;
  /** YYYY-MM-DD, not in the future; the server defaults it to today. */
  collectedOn?: string;
  /** YYYY-MM-DD, required, after the collection date. */
  expiresOn: string;
}

/** POST /api/blood-bank/stock: record a collected batch. */
export async function recordBatch(input: BatchInput): Promise<StockBatch> {
  const res = await apiClient.post(INVENTORY_PATHS.stock, {
    bloodType: input.bloodType,
    component: input.component,
    units: input.units,
    expiresOn: input.expiresOn,
    ...(input.collectedOn ? { collectedOn: input.collectedOn } : {}),
  });
  return toBatch((unwrap<Record<string, unknown>>(res.data) ?? {}) as Record<string, unknown>);
}

/** PUT /api/blood-bank/stock/{id}: correct the unit count (0 to 100000) and/or the expiry date. */
export async function updateBatch(id: number, input: { units: number; expiresOn?: string; note?: string }): Promise<StockBatch> {
  const res = await apiClient.put(INVENTORY_PATHS.batch(id), {
    units: input.units,
    ...(input.expiresOn ? { expiresOn: input.expiresOn } : {}),
    ...(input.note?.trim() ? { note: input.note.trim() } : {}),
  });
  return toBatch((unwrap<Record<string, unknown>>(res.data) ?? {}) as Record<string, unknown>);
}

/** DELETE /api/blood-bank/stock/{id}: discard a batch (it is written to the movement log). */
export async function discardBatch(id: number): Promise<void> {
  await apiClient.delete(INVENTORY_PATHS.batch(id));
}

export interface Movement {
  id: number;
  unitId: number | null;
  bloodType: string;
  component: string;
  /** Units added (positive) or removed (negative). */
  delta: number;
  reason: MovementReason | string;
  requestId: number | null;
  note: string | null;
  createdAt: Date | null;
}

export function toMovement(r: Record<string, unknown>): Movement {
  return {
    id: num(r.id),
    unitId: optionalNum(r.unitId),
    bloodType: String(r.bloodType ?? ''),
    component: String(r.component ?? 'WHOLE_BLOOD'),
    delta: num(r.delta),
    reason: String(r.reason ?? 'ADJUSTMENT'),
    requestId: optionalNum(r.requestId),
    note: str(r.note) ?? null,
    createdAt: asDate(r.createdAt),
  };
}

/** POST /api/blood-bank/stock/issue: hand out units, first-expiry-first-out. 409 inventory.insufficient_stock when there are not enough. */
export async function issueStock(input: { bloodType: string; component: string; units: number; requestId?: number; note?: string }): Promise<Movement[]> {
  const res = await apiClient.post(INVENTORY_PATHS.issue, {
    bloodType: input.bloodType,
    component: input.component,
    units: input.units,
    ...(input.requestId ? { requestId: input.requestId } : {}),
    ...(input.note?.trim() ? { note: input.note.trim() } : {}),
  });
  return asList<Record<string, unknown>>(res.data).map(toMovement);
}

/** POST /api/blood-bank/stock/confirm: "these figures are current" (refreshes confirmedAt, which the public ticker's 48-hour rule uses). */
export async function confirmStock(): Promise<StockSite> {
  const res = await apiClient.post(INVENTORY_PATHS.confirm);
  return toSite(unwrap<Record<string, unknown>>(res.data));
}

/** GET /api/blood-bank/stock/movements?limit=100 */
export async function fetchMovements(limit = 50): Promise<Movement[]> {
  const res = await apiClient.get(INVENTORY_PATHS.movements, { params: { limit } });
  return asList<Record<string, unknown>>(res.data).map(toMovement);
}

export interface Threshold {
  id: number;
  bloodType: string;
  component: string;
  low: number;
  critical: number;
}

export function toThreshold(r: Record<string, unknown>): Threshold {
  return { id: num(r.id), bloodType: String(r.bloodType ?? ''), component: String(r.component ?? 'WHOLE_BLOOD'), low: num(r.low), critical: num(r.critical) };
}

/** GET /api/blood-bank/thresholds -> [{ id, bloodType, component, low, critical }] */
export async function fetchThresholds(): Promise<Threshold[]> {
  const res = await apiClient.get(INVENTORY_PATHS.thresholds);
  return asList<Record<string, unknown>>(res.data).map(toThreshold);
}

/** PUT /api/blood-bank/thresholds {bloodType, component, low, critical}: critical must not be higher than low (400 inventory.threshold_invalid). */
export async function saveThreshold(input: { bloodType: string; component: string; low: number; critical: number }): Promise<Threshold> {
  const res = await apiClient.put(INVENTORY_PATHS.thresholds, input);
  return toThreshold((unwrap<Record<string, unknown>>(res.data) ?? {}) as Record<string, unknown>);
}

/** GET /api/blood-bank/site */
export async function fetchSite(): Promise<StockSite> {
  const res = await apiClient.get(INVENTORY_PATHS.site);
  return toSite(unwrap<Record<string, unknown>>(res.data));
}

/** PUT /api/blood-bank/site: a district is required before the site can appear in the public ticker. */
export async function saveSite(input: { siteName?: string; district?: string; latitude?: number; longitude?: number; publicSignals?: boolean }): Promise<StockSite> {
  const res = await apiClient.put(INVENTORY_PATHS.site, input);
  return toSite(unwrap<Record<string, unknown>>(res.data));
}
