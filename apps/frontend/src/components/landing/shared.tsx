import Link from 'next/link';
import type { ReactNode } from 'react';
import { cn } from '@/lib/utils';
import styles from './landing.module.css';

/** Tiny blood-drop glyph used as a separator (marquee) and bullet. Inherits colour via currentColor. */
export function DropGlyph({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 32 32" aria-hidden="true" className={cn('h-4 w-4 shrink-0', className)}>
      <path d="M16 2.500c-3.300 4.600-9 10.200-9 16.100a9 9 0 0 0 18 0C25 12.700 19.300 7.100 16 2.500Z" fill="currentColor" />
    </svg>
  );
}

/**
 * Italic ember accent for a single display word. Noto Serif Sinhala/Tamil have no italic cut,
 * so the slant is dropped for those scripts and only the colour carries the emphasis.
 */
export const ACCENT_CLASS = 'italic text-ember [:lang(si)_&]:not-italic [:lang(ta)_&]:not-italic';

/** Splits "Be someone's hero today" into ["Be someone's hero", "today"] so the last word can be accented. */
export function splitLastWord(text: string): [string, string] {
  const trimmed = text.trim();
  const i = trimmed.lastIndexOf(' ');
  if (i === -1) return ['', trimmed];
  return [trimmed.slice(0, i), trimmed.slice(i + 1)];
}

/**
 * Display sizes. Sinhala/Tamil words are far wider than Latin ones, so each breakpoint has a smaller size for them.
 * Each breakpoint needs its own lang variant (`:lang(si) .x` out-ranks a plain `sm:` utility), and the class names
 * are written out in full so Tailwind can see them.
 */
export const H2_CLASS =
  'font-display break-words text-5xl text-foreground sm:text-6xl lg:text-7xl [:lang(si)_&]:text-4xl [:lang(ta)_&]:text-4xl sm:[:lang(si)_&]:text-5xl sm:[:lang(ta)_&]:text-5xl lg:[:lang(si)_&]:text-6xl lg:[:lang(ta)_&]:text-6xl';
export const H1_CLASS =
  'font-display break-words text-[2.9rem] text-foreground sm:text-6xl lg:text-7xl xl:text-8xl [:lang(si)_&]:text-4xl [:lang(ta)_&]:text-4xl sm:[:lang(si)_&]:text-5xl sm:[:lang(ta)_&]:text-5xl lg:[:lang(si)_&]:text-6xl lg:[:lang(ta)_&]:text-6xl xl:[:lang(si)_&]:text-7xl xl:[:lang(ta)_&]:text-7xl';
export const CTA_CLASS =
  'font-display break-words text-[2.9rem] text-foreground sm:text-7xl lg:text-8xl [:lang(si)_&]:text-4xl [:lang(ta)_&]:text-4xl sm:[:lang(si)_&]:text-6xl sm:[:lang(ta)_&]:text-6xl lg:[:lang(si)_&]:text-7xl lg:[:lang(ta)_&]:text-7xl';

/** Utility-class equivalent of styles.scriptFix for mono captions that are not built on .eyebrow */
export const SCRIPT_RESET =
  '[:lang(si)_&]:tracking-normal [:lang(si)_&]:normal-case [:lang(ta)_&]:tracking-normal [:lang(ta)_&]:normal-case';

const BASE =
  'group/btn relative inline-flex items-center justify-center gap-2 rounded-full text-center font-medium tracking-[-0.005em] transition-[background-color,border-color,color,transform,box-shadow] duration-300 ease-[cubic-bezier(0.22,1,0.36,1)] active:scale-[0.97] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ember';

const VARIANTS = {
  primary:
    'bg-ember text-white shadow-[inset_0_1px_0_rgba(255,255,255,0.28),0_6px_20px_-8px_color-mix(in_oklch,var(--ember)_70%,transparent)] hover:bg-[color-mix(in_oklch,var(--ember)_88%,white)]',
  secondary: 'border border-line-strong bg-foreground/[0.04] text-foreground hover:border-foreground/30 hover:bg-foreground/[0.08]',
  ghost: 'text-gray-400 hover:bg-foreground/[0.06] hover:text-foreground',
};

const SIZES = {
  sm: 'px-4 py-1.5 text-[13px]',
  md: 'px-6 py-2.5 text-sm',
  lg: 'px-7 py-3.5 text-[15px] sm:px-8',
};

interface PillLinkProps {
  href: string;
  variant?: keyof typeof VARIANTS;
  size?: keyof typeof SIZES;
  className?: string;
  onClick?: () => void;
  children: ReactNode;
}

/** The pill button from components/ui/Button, rendered as a link so navigation stays semantic. */
export function PillLink({ href, variant = 'primary', size = 'md', className, onClick, children }: PillLinkProps) {
  return (
    <Link href={href} onClick={onClick} className={cn(BASE, VARIANTS[variant], SIZES[size], className)}>
      {children}
    </Link>
  );
}

/** Mono eyebrow with a small ember tick, shared by every section header. */
export function SectionEyebrow({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <p className={cn('eyebrow', styles.scriptFix, 'inline-flex items-center gap-2.5', className)}>
      <span aria-hidden="true" className="h-px w-6 bg-ember" />
      {children}
    </p>
  );
}
