/**
 * Map configuration shared by every map.
 *
 * Tiles: OpenStreetMap's standard layer by default. Set NEXT_PUBLIC_MAP_TILE_URL to a keyed/other provider
 * (and NEXT_PUBLIC_MAP_ATTRIBUTION to that provider's required credit), and add its origin to the CSP
 * `img-src` in next.config.ts. Attribution is always shown (ODbL requirement).
 */
export const TILE_URL = process.env.NEXT_PUBLIC_MAP_TILE_URL || 'https://tile.openstreetmap.org/{z}/{x}/{y}.png';

export const TILE_ATTRIBUTION =
  process.env.NEXT_PUBLIC_MAP_ATTRIBUTION || '&copy; <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener noreferrer">OpenStreetMap</a> contributors';

/** South-west and north-east corners of Sri Lanka (with a little sea around it). Southern India is outside. */
export const SRI_LANKA_BOUNDS: [[number, number], [number, number]] = [
  [5.85, 79.5],
  [9.9, 81.95],
];

/** How shortage levels are decided (mirrors the backend rule in AdminService); the legend text is generated from these. */
export const SHORTAGE_RULES = {
  /** fewer than this many available donors per active request = CRITICAL */
  critical: 2,
  /** fewer than this many = WARNING */
  warning: 5,
  /** at least this many donors and no active request = SURPLUS */
  surplusDonors: 10,
};
