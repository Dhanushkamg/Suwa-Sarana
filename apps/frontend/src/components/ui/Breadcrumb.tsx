import Link from 'next/link';
import { ChevronRight } from 'lucide-react';

export interface Crumb {
  label: string;
  href?: string;
}

/** Last item is the current page (no link). */
export function Breadcrumb({ items, label }: { items: Crumb[]; label: string }) {
  return (
    <nav aria-label={label}>
      <ol className="flex flex-wrap items-center gap-1.5 text-sm text-muted">
        {items.map((item, i) => (
          <li key={`${item.label}-${i}`} className="flex items-center gap-1.5">
            {item.href && i < items.length - 1 ? (
              <Link href={item.href} className="rounded underline-offset-4 hover:text-foreground hover:underline">
                {item.label}
              </Link>
            ) : (
              <span aria-current="page" className="text-foreground">
                {item.label}
              </span>
            )}
            {i < items.length - 1 && <ChevronRight className="h-3.5 w-3.5 rtl:rotate-180" aria-hidden="true" />}
          </li>
        ))}
      </ol>
    </nav>
  );
}
