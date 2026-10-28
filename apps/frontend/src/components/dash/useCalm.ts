'use client';

import { useReducedMotion } from 'motion/react';
import { useLowData } from '@/lib/lowData';

/** True when animation should be off: the visitor asked for reduced motion, or Low-Data Mode is on. */
export function useCalm(): boolean {
  const reduce = useReducedMotion();
  const { lowData } = useLowData();
  return !!reduce || lowData;
}
