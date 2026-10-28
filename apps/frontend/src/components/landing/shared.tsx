import Link from 'next/link';
import type { ReactNode } from 'react';
import { cn } from '@/lib/utils';
import { buttonClasses, type ButtonSize, type ButtonVariant } from '@/components/ui/Button';
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
 * Italic primary-colour accent for a single display word. Noto Serif Sinhala/Tamil have no italic cut,
 * so the slant is dropped for those scripts and only the colour carries the emphasis.
 */
export const ACCENT_CLASS = 'italic text-primary-text [:lang(si)_&]:not-italic [:lang(ta)_&]:not-italic';

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

interface PillLinkProps {
  href: string;
  /** `primary` = the one main action; `brand` = the single urgent "Request blood" call to action; the rest are quiet. */
  variant?: ButtonVariant;
  size?: ButtonSize;
  className?: string;
  onClick?: () => void;
  children: ReactNode;
}

/** The pill button from components/ui/Button, rendered as a link so navigation stays semantic. Same classes, so it cannot drift. */
export function PillLink({ href, variant = 'primary', size = 'md', className, onClick, children }: PillLinkProps) {
  return (
    <Link href={href} onClick={onClick} className={buttonClasses(variant, size, cn('group/btn text-center', className))}>
      {children}
    </Link>
  );
}

/** Mono eyebrow with a small primary tick, shared by every section header. */
export function SectionEyebrow({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <p className={cn('eyebrow', styles.scriptFix, 'inline-flex items-center gap-2.5', className)}>
      <span aria-hidden="true" className="h-px w-6 bg-primary" />
      {children}
    </p>
  );
}
