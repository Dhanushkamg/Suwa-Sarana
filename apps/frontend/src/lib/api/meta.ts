'use client';

import { useEffect, useState } from 'react';
import apiClient from '@/lib/apiClient';
import { DISTRICT_COORDS } from '@/lib/districts';
import { num, unwrap } from './common';

/** GET /api/meta/districts (public): the 25 official districts with a centre point, and Sri Lanka's bounding box. */
export const META_PATHS = { districts: '/meta/districts' } as const;

export interface DistrictInfo {
  name: string;
  latitude: number;
  longitude: number;
}

export interface Bounds {
  minLat: number;
  maxLat: number;
  minLon: number;
  maxLon: number;
}

export interface DistrictMeta {
  districts: DistrictInfo[];
  bounds: Bounds;
  /** True while the list is the built-in fallback (the server could not be reached). */
  fallback: boolean;
}

const FALLBACK_BOUNDS: Bounds = { minLat: 5.85, maxLat: 9.9, minLon: 79.5, maxLon: 81.95 };

/** The built-in list: only used when the server does not answer, so the forms still work offline. */
export const FALLBACK_META: DistrictMeta = {
  districts: Object.entries(DISTRICT_COORDS).map(([name, [latitude, longitude]]) => ({ name, latitude, longitude })),
  bounds: FALLBACK_BOUNDS,
  fallback: true,
};

export function toDistrictMeta(body: unknown): DistrictMeta {
  const data = (unwrap<Record<string, unknown>>(body) ?? {}) as Record<string, unknown>;
  const list = Array.isArray(data.districts) ? (data.districts as Record<string, unknown>[]) : [];
  const districts = list
    .map((d) => ({ name: String(d.name ?? ''), latitude: num(d.latitude), longitude: num(d.longitude) }))
    .filter((d) => d.name);
  if (districts.length === 0) return FALLBACK_META;
  const b = (data.bounds ?? {}) as Record<string, unknown>;
  return {
    districts,
    bounds: {
      minLat: num(b.minLat, FALLBACK_BOUNDS.minLat),
      maxLat: num(b.maxLat, FALLBACK_BOUNDS.maxLat),
      minLon: num(b.minLon, FALLBACK_BOUNDS.minLon),
      maxLon: num(b.maxLon, FALLBACK_BOUNDS.maxLon),
    },
    fallback: false,
  };
}

let cached: Promise<DistrictMeta> | null = null;

/** One request per page load; a failed request is retried on the next call and answers with the built-in list meanwhile. */
export function fetchDistrictMeta(): Promise<DistrictMeta> {
  if (!cached) {
    cached = apiClient
      .get(META_PATHS.districts)
      .then((res) => toDistrictMeta(res.data))
      .catch(() => {
        cached = null;
        return FALLBACK_META;
      });
  }
  return cached;
}

/** For tests. */
export function resetDistrictMetaCache() {
  cached = null;
}

/** District names and centres for forms. Starts with the built-in list so the first render is never empty. */
export function useDistricts(): DistrictMeta & { centre: (name: string) => [number, number] } {
  const [meta, setMeta] = useState<DistrictMeta>(FALLBACK_META);
  useEffect(() => {
    let alive = true;
    fetchDistrictMeta().then((m) => {
      if (alive) setMeta(m);
    });
    return () => {
      alive = false;
    };
  }, []);
  const centre = (name: string): [number, number] => {
    const d = meta.districts.find((x) => x.name === name) ?? meta.districts[0];
    return [d.latitude, d.longitude];
  };
  return { ...meta, centre };
}
