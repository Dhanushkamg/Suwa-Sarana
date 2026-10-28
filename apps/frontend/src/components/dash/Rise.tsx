'use client';

import type { ReactNode } from 'react';
import { motion } from 'motion/react';
import { EASE } from './styles';
import { useCalm } from './useCalm';

interface RiseProps {
  children: ReactNode;
  delay?: number;
  y?: number;
  className?: string;
  /** Wait until scrolled into view (for blocks below the fold) instead of animating on mount. */
  inView?: boolean;
}

/**
 * Quick mount animation: short, small travel, transform and opacity only.
 * Renders a plain <div> (no motion at all) for reduced-motion users and in Low-Data Mode.
 */
export function Rise({ children, delay = 0, y = 10, className, inView = false }: RiseProps) {
  const calm = useCalm();
  if (calm) return <div className={className}>{children}</div>;
  const transition = { duration: 0.45, ease: EASE, delay };
  return inView ? (
    <motion.div
      className={className}
      initial={{ opacity: 0, y }}
      whileInView={{ opacity: 1, y: 0 }}
      viewport={{ once: true, margin: '-60px' }}
      transition={transition}
    >
      {children}
    </motion.div>
  ) : (
    <motion.div className={className} initial={{ opacity: 0, y }} animate={{ opacity: 1, y: 0 }} transition={transition}>
      {children}
    </motion.div>
  );
}
