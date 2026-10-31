'use client';

import { useMemo } from 'react';
import { PosterDialog } from '@/components/poster/PosterDialog';
import type { PosterFacts } from '@/components/poster/types';
import { SITE_URL } from '@/lib/constants';
import { useAuthStore } from '@/store/authStore';

interface CampPostProps {
  camp: {
    id?: number;
    name: string;
    district: string;
    location: string;
    scheduledDate: string;
    startTime: string;
    endTime: string;
    organizerName: string;
    requiredBloodGroups?: string;
  };
  onClose: () => void;
}

/**
 * Camp organiser entry point (opened from the camps dashboard). The poster itself lives in components/poster: this only
 * maps the camp record onto poster facts. The QR code points at the public camps page.
 */
export default function CampPostGenerator({ camp, onClose }: CampPostProps) {
  const phone = useAuthStore((s) => s.user?.phoneNumber);

  const facts = useMemo<PosterFacts>(
    () => ({
      campName: camp.name,
      venue: camp.location,
      district: camp.district,
      date: String(camp.scheduledDate),
      startTime: camp.startTime,
      endTime: camp.endTime,
      // Older camp records stored the organiser's e-mail address as the name: the poster never prints an e-mail
      organiser: camp.organizerName,
      bloodTypes: camp.requiredBloodGroups ? camp.requiredBloodGroups.split(',').map((s) => s.trim()).filter(Boolean) : undefined,
    }),
    [camp]
  );

  return (
    <PosterDialog
      onClose={onClose}
      facts={facts}
      shareUrl={`${SITE_URL}/camps`}
      initialTemplate="CAMP"
      templates={['CAMP', 'AWARENESS', 'THANK_YOU']}
      contactPhone={phone || undefined}
    />
  );
}
