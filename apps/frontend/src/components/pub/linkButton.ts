import { buttonClasses } from '@/components/ui/Button';

/** Class names that make an <a>/<Link> look like ui/Button without nesting a <button> in a link. Thin wrapper over buttonClasses. */
export function linkButtonClass(variant: 'primary' | 'secondary' | 'brand' = 'primary', size: 'md' | 'lg' = 'md', extra?: string) {
  return buttonClasses(variant, size, extra);
}
