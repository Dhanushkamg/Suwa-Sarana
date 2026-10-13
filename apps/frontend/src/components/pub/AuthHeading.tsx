'use client';

import type { ReactNode } from 'react';
import { motion, useReducedMotion } from 'motion/react';

/** Eyebrow + serif title + supporting line shared by every auth page. Rises in on mount (above the fold, so no scroll trigger). */
export function AuthHeading({ eyebrow, title, subtitle }: { eyebrow: string; title: string; subtitle?: ReactNode }) {
  const reduce = useReducedMotion();
  return (
    <motion.div
      className="mb-9"
      initial={reduce ? false : { opacity: 0, y: 14 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.6, ease: [0.22, 1, 0.36, 1] }}
    >
      <p className="eyebrow mb-4 flex items-center gap-2.5">
        <span aria-hidden="true" className="h-px w-6 bg-ember" />
        {eyebrow}
      </p>
      <h1 className="font-display break-words text-4xl text-foreground sm:text-5xl">{title}</h1>
      {subtitle && <p className="mt-4 text-[15px] leading-relaxed text-gray-400">{subtitle}</p>}
    </motion.div>
  );
}
