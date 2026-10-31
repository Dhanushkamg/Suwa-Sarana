'use client';

import { useMemo } from 'react';
import { SITE_URL } from '@/lib/constants';
import { useAuthStore } from '@/store/authStore';
import { PosterDialog } from './PosterDialog';
import type { PosterFacts } from './types';

export interface RequestPosterDialogProps {
  bloodType: string;
  units?: number;
  hospital: string;
  district: string;
  /** Token of the request's public share link (/share/{token}); the QR code points there */
  token: string;
  onClose: () => void;
}

/** Poster for a blood request: facts come from the request, the QR code from its share link. Used by the request page and the share page. */
export default function RequestPosterDialog({ bloodType, units, hospital, district, token, onClose }: RequestPosterDialogProps) {
  const phone = useAuthStore((s) => s.user?.phoneNumber);
  const facts = useMemo<PosterFacts>(() => ({ bloodType, units, hospital, district }), [bloodType, units, hospital, district]);

  return (
    <PosterDialog
      onClose={onClose}
      facts={facts}
      shareUrl={`${SITE_URL}/share/${encodeURIComponent(token)}`}
      initialTemplate="URGENT_REQUEST"
      templates={['URGENT_REQUEST', 'AWARENESS', 'THANK_YOU']}
      contactPhone={phone || undefined}
    />
  );
}
