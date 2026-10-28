import Link from 'next/link';
import type { ComponentProps } from 'react';
import { buttonClasses, type ButtonSize, type ButtonVariant } from '@/components/ui/Button';

type Props = ComponentProps<typeof Link> & { variant?: ButtonVariant; size?: ButtonSize };

/** A `<Link>` styled exactly like `components/ui/Button` (same classes, so the two cannot drift apart). One `primary` per view. */
export function ButtonLink({ variant = 'secondary', size = 'md', className, ...props }: Props) {
  return <Link className={buttonClasses(variant, size, className)} {...props} />;
}
