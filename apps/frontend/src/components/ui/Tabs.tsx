'use client';

import { useRef } from 'react';
import { cn } from '@/lib/utils';

export interface TabItem {
  id: string;
  label: string;
  count?: number;
}

/** Accessible tab list: arrow keys / Home / End move between tabs. Render the matching panel yourself with role="tabpanel" aria-labelledby={`tab-${id}`}. */
export function Tabs({ tabs, value, onChange, label }: { tabs: TabItem[]; value: string; onChange: (id: string) => void; label: string }) {
  const refs = useRef<Record<string, HTMLButtonElement | null>>({});

  const onKeyDown = (e: React.KeyboardEvent, index: number) => {
    const last = tabs.length - 1;
    const next = e.key === 'ArrowRight' ? (index === last ? 0 : index + 1) : e.key === 'ArrowLeft' ? (index === 0 ? last : index - 1) : e.key === 'Home' ? 0 : e.key === 'End' ? last : -1;
    if (next < 0) return;
    e.preventDefault();
    onChange(tabs[next].id);
    refs.current[tabs[next].id]?.focus();
  };

  return (
    <div role="tablist" aria-label={label} className="flex gap-1 overflow-x-auto border-b border-line">
      {tabs.map((tab, i) => {
        const active = tab.id === value;
        return (
          <button
            key={tab.id}
            ref={(el) => {
              refs.current[tab.id] = el;
            }}
            id={`tab-${tab.id}`}
            role="tab"
            type="button"
            aria-selected={active}
            tabIndex={active ? 0 : -1}
            onClick={() => onChange(tab.id)}
            onKeyDown={(e) => onKeyDown(e, i)}
            className={cn(
              'relative min-h-11 whitespace-nowrap px-4 text-sm font-medium transition-colors',
              active ? 'text-primary-text after:absolute after:inset-x-3 after:bottom-0 after:h-0.5 after:rounded-full after:bg-primary' : 'text-muted hover:text-foreground'
            )}
          >
            {tab.label}
            {tab.count !== undefined && <span className="ms-2 rounded-full bg-foreground/[0.08] px-2 py-0.5 text-xs">{tab.count}</span>}
          </button>
        );
      })}
    </div>
  );
}
