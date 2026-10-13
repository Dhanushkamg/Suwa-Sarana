import Link from 'next/link';
import { cn } from '@/lib/utils';

/** The Suwa Sarana mark: a drop with a heartbeat notch cut through it. Pure SVG, inherits colour via `currentColor`. */
export function LogoMark({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 32 32" className={cn('h-8 w-8', className)} aria-hidden="true">
      <path
        d="M16 2.5c-3.3 4.6-9 10.2-9 16.1a9 9 0 0 0 18 0C25 12.700 19.300 7.100 16 2.500Z"
        fill="var(--ember)"
      />
      <path
        d="M8.500 19.500h4.200l1.800-4.200 2.800 8 2-4h4.200"
        fill="none"
        stroke="var(--background)"
        strokeWidth="1.800"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

/** Mark + wordmark. The wordmark mixes the display serif with an italic second word. */
export function Logo({ href = '/', className, compact = false }: { href?: string; className?: string; compact?: boolean }) {
  return (
    <Link href={href} className={cn('group inline-flex items-center gap-2.5', className)} aria-label="Suwa Sarana">
      <LogoMark className="transition-transform duration-500 group-hover:-rotate-6 group-hover:scale-110" />
      {!compact && (
        <span className="font-display text-[1.65rem] leading-none text-foreground">
          Suwa <span className="italic text-foreground/70">Sarana</span>
        </span>
      )}
    </Link>
  );
}
