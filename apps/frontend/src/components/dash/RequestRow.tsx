'use client';

import type { ReactNode } from 'react';
import Link from 'next/link';
import { ArrowUpRight, Clock, MapPin } from 'lucide-react';
import { useI18n } from '@/lib/i18n';
import type { BloodRequest } from '@/types';
import { BloodTypeTile } from './IconTile';
import { Meta } from './Meta';
import { RequestStatusBadge, UrgencyBadge } from './UrgencyBadge';
import { ROW_HOVER } from './styles';
import { cn } from '@/lib/utils';

interface RequestRowProps {
  req: BloodRequest;
  /** Where the row leads. Defaults to the request detail page. */
  href?: string;
  /** Text of the trailing hint (e.g. "Manage circle"); also gets an arrow. */
  cta?: ReactNode;
  className?: string;
}

/** One blood request as a hairline-separated list row: blood group tile, hospital, badges, meta, trailing hint. */
export function RequestRow({ req, href, cta, className }: RequestRowProps) {
  const { t } = useI18n();
  return (
    <Link
      href={href ?? `/dashboard/requests/${req.id}`}
      className={cn('group flex flex-col gap-4 px-5 py-4 sm:flex-row sm:items-center sm:justify-between sm:gap-5', ROW_HOVER, className)}
    >
      <div className="flex min-w-0 items-start gap-4">
        <BloodTypeTile type={req.patientBloodType} size="md" />
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-x-3 gap-y-1.5">
            <span className="break-words text-[15px] font-medium text-foreground">{req.hospitalName}</span>
            <UrgencyBadge urgency={req.urgency} />
            <RequestStatusBadge status={req.status} />
          </div>
          <div className="mt-2 flex flex-wrap items-center gap-x-4 gap-y-1">
            <Meta icon={<MapPin />}>
              {req.district} <span className="text-gray-500">({t('dash.radiusKm', { km: req.currentRadiusKm })})</span>
            </Meta>
            <Meta icon={<Clock />}>
              {t('bloodRequests.expires')} {new Date(req.expiresAt).toLocaleString([], { dateStyle: 'short', timeStyle: 'short' })}
            </Meta>
          </div>
        </div>
      </div>
      <div className="flex shrink-0 items-center gap-3 self-end sm:self-auto">
        <span className="font-mono text-xs tabular-nums text-gray-500">#{req.id}</span>
        {cta && <span className="text-xs font-medium text-ember">{cta}</span>}
        <span
          aria-hidden="true"
          className="flex h-8 w-8 items-center justify-center rounded-full border border-line text-gray-500 transition-[transform,color,border-color] duration-300 group-hover:-translate-y-0.5 group-hover:translate-x-0.5 group-hover:border-ember/50 group-hover:text-ember"
        >
          <ArrowUpRight className="h-4 w-4" />
        </span>
      </div>
    </Link>
  );
}
