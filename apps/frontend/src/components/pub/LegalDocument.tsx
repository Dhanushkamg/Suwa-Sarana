'use client';

import { useEffect, useState, type ReactNode } from 'react';
import { PubHeader } from './PubHeader';
import { PubFooter } from './PubFooter';
import { PageHeading } from './PageHeading';
import { Reveal } from '@/components/motion/Reveal';
import { useI18n } from '@/lib/i18n';
import { cn } from '@/lib/utils';

export interface LegalSection {
  id: string;
  /** Title as stored in the messages, e.g. "1. Information We Collect" (the numeric prefix is lifted into the margin). */
  title: string;
  body: ReactNode;
}

/** "3. Data Sharing" -> ["03", "Data Sharing"]; titles without a numeric prefix fall back to the section index. */
function splitTitle(title: string, index: number): [string, string] {
  const match = title.match(/^\s*(\d+)\.\s*(.*)$/);
  return match ? [match[1].padStart(2, '0'), match[2]] : [String(index + 1).padStart(2, '0'), title];
}

/**
 * Long-form reading layout shared by Privacy and Terms: editorial heading, sticky table of contents
 * on desktop (a collapsible list on mobile), numbered sections separated by hairlines.
 */
export function LegalDocument({
  page,
  eyebrow,
  title,
  updated,
  sections,
}: {
  page: 'privacy' | 'terms';
  eyebrow: string;
  title: string;
  updated: string;
  sections: LegalSection[];
}) {
  const { t } = useI18n();
  const [active, setActive] = useState(sections[0]?.id ?? '');

  // Highlight the section currently crossing the upper third of the viewport
  useEffect(() => {
    const els = sections.map((s) => document.getElementById(s.id)).filter((el): el is HTMLElement => el !== null);
    if (els.length === 0 || typeof IntersectionObserver === 'undefined') return;
    const observer = new IntersectionObserver(
      (entries) => {
        const visible = entries.filter((e) => e.isIntersecting).sort((a, b) => a.boundingClientRect.top - b.boundingClientRect.top);
        if (visible[0]) setActive(visible[0].target.id);
      },
      { rootMargin: '-15% 0px -65% 0px' }
    );
    els.forEach((el) => observer.observe(el));
    return () => observer.disconnect();
  }, [sections]);

  const toc = sections.map((s, i) => {
    const [num, label] = splitTitle(s.title, i);
    return { id: s.id, num, label };
  });

  return (
    <div className="min-h-screen bg-background text-foreground">
      <PubHeader />

      <main className="mx-auto max-w-6xl px-5 pb-24 pt-12 sm:px-8 sm:pt-20">
        <PageHeading eyebrow={eyebrow} title={title} aside={updated} />

        <div className="mt-10 grid gap-10 lg:mt-14 lg:grid-cols-[220px_minmax(0,1fr)] lg:gap-20">
          {/* Mobile: collapsible contents */}
          <details className="group rounded-xl border border-line bg-surface lg:hidden">
            <summary className="flex cursor-pointer list-none items-center justify-between gap-3 px-4 py-3 [&::-webkit-details-marker]:hidden">
              <span className="eyebrow">{t('pub.onThisPage')}</span>
              <span aria-hidden="true" className="font-mono text-gray-500 transition-transform duration-300 group-open:rotate-45">
                +
              </span>
            </summary>
            <ol className="space-y-1 border-t border-line px-4 py-3">
              {toc.map((item) => (
                <li key={item.id}>
                  <a href={`#${item.id}`} className="flex gap-3 py-1.5 text-sm text-gray-400 transition-colors hover:text-foreground">
                    <span className="font-mono text-xs text-gray-600">{item.num}</span>
                    <span className="min-w-0 break-words">{item.label}</span>
                  </a>
                </li>
              ))}
            </ol>
          </details>

          {/* Desktop: sticky contents */}
          <nav aria-label={t('pub.onThisPage')} className="hidden lg:block">
            <div className="sticky top-28">
              <p className="eyebrow mb-5">{t('pub.onThisPage')}</p>
              <ol className="space-y-1 border-l border-line">
                {toc.map((item) => (
                  <li key={item.id}>
                    <a
                      href={`#${item.id}`}
                      aria-current={active === item.id ? 'location' : undefined}
                      className={cn(
                        '-ml-px flex gap-3 border-l py-1.5 pl-4 text-sm transition-colors',
                        active === item.id
                          ? 'border-ember text-foreground'
                          : 'border-transparent text-gray-500 hover:text-gray-300'
                      )}
                    >
                      <span className={cn('font-mono text-xs leading-5', active === item.id ? 'text-ember' : 'text-gray-600')}>
                        {item.num}
                      </span>
                      <span className="min-w-0 break-words">{item.label}</span>
                    </a>
                  </li>
                ))}
              </ol>
            </div>
          </nav>

          <div className="min-w-0">
            {sections.map((section, i) => {
              const [num, label] = splitTitle(section.title, i);
              return (
                <Reveal key={section.id} y={18}>
                  <section
                    id={section.id}
                    aria-labelledby={`${section.id}-title`}
                    className={cn(
                      'scroll-mt-28 border-b border-line py-10',
                      i === 0 && 'pt-0',
                      i === sections.length - 1 && 'border-b-0'
                    )}
                  >
                    <div className="flex gap-5 sm:gap-8">
                      <span className="w-7 shrink-0 pt-2 font-mono text-xs text-ember sm:w-9">{num}</span>
                      <div className="min-w-0">
                        <h2 id={`${section.id}-title`} className="font-display break-words text-3xl text-foreground sm:text-4xl">
                          {label}
                        </h2>
                        <div className="mt-5 max-w-prose text-base leading-8 text-gray-300">{section.body}</div>
                      </div>
                    </div>
                  </section>
                </Reveal>
              );
            })}
          </div>
        </div>
      </main>

      <PubFooter current={page} />
    </div>
  );
}
