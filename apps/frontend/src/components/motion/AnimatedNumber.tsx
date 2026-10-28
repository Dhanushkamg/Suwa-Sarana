'use client';

import { useEffect, useRef } from 'react';
import { animate, useInView, useReducedMotion } from 'motion/react';
import { useI18n } from '@/lib/i18n';

/** Counts up to `value` once when scrolled into view. Renders the final value immediately for reduced-motion users. */
export function AnimatedNumber({ value, className, suffix = '' }: { value: number; className?: string; suffix?: string }) {
  const ref = useRef<HTMLSpanElement>(null);
  const inView = useInView(ref, { once: true, margin: '-60px' });
  const reduce = useReducedMotion();
  const { format } = useI18n();

  useEffect(() => {
    const el = ref.current;
    if (!el || !inView || reduce) return;
    const controls = animate(0, value, {
      duration: 1.6,
      ease: [0.22, 1, 0.36, 1],
      onUpdate: (v) => {
        el.textContent = format.number(Math.round(v)) + suffix;
      },
    });
    return () => controls.stop();
  }, [inView, reduce, value, suffix, format]);

  return (
    <span ref={ref} className={className}>
      {format.number(reduce ? value : 0)}
      {suffix}
    </span>
  );
}
