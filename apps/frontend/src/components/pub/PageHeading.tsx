import type { ReactNode } from 'react';
import { Reveal } from '@/components/motion/Reveal';
import { cn } from '@/lib/utils';

/** Editorial page opener: mono eyebrow with a short ember rule, big serif title, optional lede. */
export function PageHeading({
  eyebrow,
  title,
  description,
  aside,
  className,
}: {
  eyebrow: string;
  title: string;
  description?: ReactNode;
  /** Small mono caption aligned to the bottom-right on wide screens (e.g. "Last updated"). */
  aside?: ReactNode;
  className?: string;
}) {
  return (
    <Reveal y={20} className={cn('border-b border-line pb-10 sm:pb-14', className)}>
      <p className="eyebrow mb-5 flex items-center gap-2.5">
        <span aria-hidden="true" className="h-px w-8 bg-ember" />
        {eyebrow}
      </p>
      <div className="flex flex-col gap-6 md:flex-row md:items-end md:justify-between">
        <div className="min-w-0 max-w-3xl">
          <h1 className="font-display break-words text-5xl text-foreground sm:text-6xl lg:text-7xl">{title}</h1>
          {description && <p className="mt-6 max-w-2xl text-base leading-relaxed text-gray-400 sm:text-lg">{description}</p>}
        </div>
        {aside && <div className="eyebrow shrink-0 md:text-right">{aside}</div>}
      </div>
    </Reveal>
  );
}
