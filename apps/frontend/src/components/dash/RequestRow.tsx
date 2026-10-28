'use client';

import type { ReactNode } from 'react';
import Link from 'next/link';
import { ArrowUpRight, Clock, Droplets, MapPin } from 'lucide-react';
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
  /** Extra buttons (e.g. "View matches"). Rendered outside the row link so the markup stays valid. */
  actions?: ReactNode;
  className?: string;
}

/** One blood request as a list row: blood group badge, hospital, status chips, meta facts, trailing hint. */
export function RequestRow({ req, href, cta, actions, className }: RequestRowProps) {
  const { t, format } = useI18n();
  const expires = req.expiresAt ? new Date(req.expiresAt) : null;
  return (
    <div className={cn('flex flex-col sm:flex-row sm:items-stretch', ROW_HOVER, className)}>
      <Link
        href={href ?? `/dashboard/requests/${req.id}`}
        className="group flex min-w-0 flex-1 flex-col gap-4 px-5 py-4 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary sm:flex-row sm:items-center sm:justify-between sm:gap-5"
      >
        <div className="flex min-w-0 items-start gap-4">
          <BloodTypeTile type={req.patientBloodType} size="md" />
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-x-3 gap-y-1.5">
              <span className="break-words text-base font-medium text-foreground">{req.hospitalName}</span>
              <UrgencyBadge urgency={req.urgency} />
              <RequestStatusBadge status={req.status} />
            </div>
            <div className="mt-2 flex flex-wrap items-center gap-x-4 gap-y-1">
              <Meta icon={<MapPin />}>
                {req.district} <span className="text-gray-500">({t('dash.radiusKm', { km: format.number(req.currentRadiusKm) })})</span>
              </Meta>
              {req.unitsNeeded !== undefined && <Meta icon={<Droplets />}>{t('dashUi.units', { count: req.unitsNeeded })}</Meta>}
              {expires && (
                <Meta icon={<Clock />}>
                  {t('bloodRequests.expires')} {format.dateTime(expires, { dateStyle: 'short', timeStyle: 'short' })}
                </Meta>
              )}
            </div>
          </div>
        </div>
        <div className="flex shrink-0 items-center gap-3 self-end sm:self-auto">
          <span className="text-sm tabular-nums text-gray-500">#{req.id}</span>
          {cta && <span className="text-sm font-medium text-primary-text">{cta}</span>}
          <span
            aria-hidden="true"
            className="flex h-8 w-8 items-center justify-center rounded-full border border-line text-gray-500 transition-colors group-hover:border-primary group-hover:text-primary-text"
          >
            <ArrowUpRight className="h-4 w-4 rtl:-scale-x-100" />
          </span>
        </div>
      </Link>
      {actions && <div className="flex shrink-0 items-center gap-2 px-5 pb-4 sm:pb-0 sm:pe-5 sm:ps-0">{actions}</div>}
    </div>
  );
}
