'use client';

import { useEffect, useRef, useState } from 'react';
import * as htmlToImage from 'html-to-image';
import { QRCodeSVG } from 'qrcode.react';
import { Download, Droplets, Calendar, MapPin, Clock } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Dialog } from '@/components/ui/Dialog';
import { Alert } from '@/components/pub/Alert';
import { TIME_FORMAT, toDate, toTimeOfDay } from '@/components/pub/dates';
import { LogoMark } from '@/components/brand/Logo';
import { useI18n } from '@/lib/i18n';
import { POSTER } from './posterPalette';

interface CampPostProps {
  camp: {
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

const POSTER_SIZE = 1080; // Instagram square

const ALL_GROUPS = ['O+', 'O-', 'A+', 'A-', 'B+', 'B-', 'AB+', 'AB-'];

export default function CampPostGenerator({ camp, onClose }: CampPostProps) {
  const { t, format } = useI18n();
  const postRef = useRef<HTMLDivElement>(null);
  const previewRef = useRef<HTMLDivElement>(null);
  const [scale, setScale] = useState(0.4);
  const [downloading, setDownloading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [origin, setOrigin] = useState('');

  // Fit the 1080px poster into whatever width the preview column has
  useEffect(() => {
    const el = previewRef.current;
    if (!el) return;
    const update = () => setScale(el.clientWidth / POSTER_SIZE);
    update();
    const observer = new ResizeObserver(update);
    observer.observe(el);
    return () => observer.disconnect();
  }, []);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setOrigin(window.location.origin);
  }, []);

  const date = toDate(camp.scheduledDate);
  const start = toTimeOfDay(camp.startTime);
  const end = toTimeOfDay(camp.endTime);
  const dateText = date ? format.date(date, { dateStyle: 'long' }) : String(camp.scheduledDate);
  const timeText = start && end ? `${format.dateTime(start, TIME_FORMAT)} – ${format.dateTime(end, TIME_FORMAT)}` : `${camp.startTime} – ${camp.endTime}`;
  const organizer = camp.organizerName.includes('@') ? camp.organizerName.split('@')[0] : camp.organizerName;
  const bloodGroups = camp.requiredBloodGroups ? camp.requiredBloodGroups.split(',').map((s) => s.trim()).filter(Boolean) : ALL_GROUPS;

  const handleDownload = async () => {
    if (!postRef.current) return;
    setDownloading(true);
    setError(null);
    try {
      const dataUrl = await htmlToImage.toJpeg(postRef.current, {
        quality: 0.95,
        backgroundColor: POSTER.background,
        width: POSTER_SIZE,
        height: POSTER_SIZE,
        style: { transform: 'none', margin: '0' },
      });
      const link = document.createElement('a');
      link.download = `blood_drive_${String(camp.scheduledDate).slice(0, 10)}.jpg`;
      link.href = dataUrl;
      link.click();
    } catch (err) {
      console.error('Failed to generate image', err);
      setError(t('pub.posterFailed'));
    } finally {
      setDownloading(false);
    }
  };

  const card = { background: POSTER.surface, border: `2px solid ${POSTER.line}`, borderRadius: 24, padding: 28 } as const;
  const cardLabel = { display: 'flex', alignItems: 'center', gap: 12, color: POSTER.muted, fontSize: 24, fontWeight: 700 } as const;
  const cardValue = { marginTop: 10, color: POSTER.text, fontSize: 30, fontWeight: 800, lineHeight: 1.3, overflowWrap: 'anywhere' } as const;

  return (
    <Dialog open onClose={onClose} title={t('modals.posterTitle')} description={t('modals.posterDesc')} className="w-[min(96vw,72rem)]">
      <div className="flex flex-col items-start gap-8 md:flex-row">
        {/* Preview: the real 1080px node, scaled down for display. It is captured un-scaled. */}
        <div
          ref={previewRef}
          role="img"
          aria-label={t('pub.posterPreviewLabel', { name: camp.name })}
          className="relative aspect-square w-full flex-1 overflow-hidden rounded-xl border border-line"
        >
          <div style={{ width: POSTER_SIZE, height: POSTER_SIZE, transform: `scale(${scale})`, transformOrigin: 'top left', position: 'absolute', top: 0, left: 0 }}>
            <div
              ref={postRef}
              style={{ position: 'relative', width: POSTER_SIZE, height: POSTER_SIZE, overflow: 'hidden', background: POSTER.background, color: POSTER.text, display: 'flex', flexDirection: 'column' }}
            >
              <div style={{ position: 'absolute', top: -220, right: -220, width: 640, height: 640, borderRadius: '50%', background: POSTER.brandSoft }} />

              {/* Header */}
              <div style={{ position: 'relative', display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 24, padding: '56px 64px 0' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 20 }}>
                  <LogoMark className="h-20 w-20" />
                  <div>
                    <div style={{ fontSize: 40, fontWeight: 800 }}>Suwa Sarana</div>
                    <div style={{ fontSize: 24, color: POSTER.muted, fontWeight: 600 }}>{t('pub.posterTagline')}</div>
                  </div>
                </div>
                <div style={{ textAlign: 'right', maxWidth: 380 }}>
                  <div style={{ fontSize: 22, color: POSTER.muted, fontWeight: 600 }}>{t('pub.posterPresentedBy')}</div>
                  <div style={{ fontSize: 28, color: POSTER.brand, fontWeight: 800, overflowWrap: 'anywhere' }}>{organizer}</div>
                </div>
              </div>

              {/* Headline */}
              <div style={{ position: 'relative', padding: '56px 64px 0', maxWidth: 820 }}>
                <div style={{ fontSize: 40, fontWeight: 700, color: POSTER.primary }}>{camp.name}</div>
                <h1 style={{ margin: '12px 0 0', fontSize: 92, fontWeight: 900, lineHeight: 1.12, color: POSTER.text }}>
                  {t('pub.posterHeadline')}
                  <br />
                  <span style={{ color: POSTER.brand }}>{t('pub.posterHeadlineAccent')}</span>
                </h1>
                <p style={{ margin: '28px 0 0', maxWidth: 700, fontSize: 28, lineHeight: 1.5, color: POSTER.muted }}>{t('pub.posterBody')}</p>
              </div>

              {/* Facts */}
              <div style={{ position: 'relative', marginTop: 'auto', padding: '0 64px 36px', display: 'grid', gridTemplateColumns: '1fr 1fr 1.2fr', gap: 20 }}>
                <div style={{ ...card, borderLeft: `10px solid ${POSTER.primary}` }}>
                  <div style={cardLabel}>
                    <Calendar size={30} color={POSTER.primary} aria-hidden="true" />
                    {t('publicPages.date')}
                  </div>
                  <div style={cardValue}>{dateText}</div>
                </div>
                <div style={{ ...card, borderLeft: `10px solid ${POSTER.primary}` }}>
                  <div style={cardLabel}>
                    <Clock size={30} color={POSTER.primary} aria-hidden="true" />
                    {t('publicPages.time')}
                  </div>
                  <div style={cardValue}>{timeText}</div>
                </div>
                <div style={{ ...card, borderLeft: `10px solid ${POSTER.primary}` }}>
                  <div style={cardLabel}>
                    <MapPin size={30} color={POSTER.primary} aria-hidden="true" />
                    {t('pub.venue')}
                  </div>
                  <div style={cardValue}>
                    {camp.location}, {camp.district}
                  </div>
                </div>
              </div>

              {/* Footer bar */}
              <div style={{ position: 'relative', display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 24, padding: '28px 64px', background: POSTER.brand, color: POSTER.onBrand }}>
                <div>
                  <div style={{ fontSize: 26, fontWeight: 800 }}>{t('pub.posterRequiredTypes')}</div>
                  <div style={{ marginTop: 12, display: 'flex', flexWrap: 'wrap', gap: 10, maxWidth: 560 }}>
                    {bloodGroups.map((bg) => (
                      <span key={bg} style={{ background: POSTER.surface, color: POSTER.brand, borderRadius: 999, padding: '6px 18px', fontSize: 26, fontWeight: 900 }}>
                        {bg}
                      </span>
                    ))}
                  </div>
                </div>
                <div style={{ display: 'flex', alignItems: 'center', gap: 20 }}>
                  <div style={{ textAlign: 'right' }}>
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'flex-end', gap: 10, fontSize: 34, fontWeight: 900 }}>
                      <Droplets size={34} aria-hidden="true" />
                      {t('pub.posterCtaTitle')}
                    </div>
                    <div style={{ marginTop: 6, fontSize: 24, fontWeight: 700 }}>{t('pub.posterCtaSub')}</div>
                  </div>
                  {origin && (
                    <div style={{ background: POSTER.surface, padding: 10, borderRadius: 16, display: 'flex' }}>
                      <QRCodeSVG value={`${origin}/camps`} size={112} level="M" fgColor={POSTER.text} bgColor={POSTER.surface} />
                    </div>
                  )}
                </div>
              </div>
            </div>
          </div>
        </div>

        {/* Controls */}
        <div className="flex w-full shrink-0 flex-col gap-6 md:w-80">
          <div className="space-y-4 rounded-2xl border border-line bg-surface-raised p-5">
            <h3 className="eyebrow">{t('modals.campaignDetails')}</h3>

            <div className="flex items-start gap-3">
              <MapPin className="h-5 w-5 shrink-0 text-gray-500" aria-hidden="true" />
              <div>
                <p className="break-words text-sm font-medium text-foreground">{camp.name}</p>
                <p className="break-words text-xs text-gray-400">{camp.location}</p>
              </div>
            </div>

            <div className="flex items-start gap-3">
              <Calendar className="h-5 w-5 shrink-0 text-gray-500" aria-hidden="true" />
              <div>
                <p className="text-sm text-foreground">{dateText}</p>
                <p className="text-xs text-gray-400">{timeText}</p>
              </div>
            </div>

            <div className="flex items-start gap-3">
              <Droplets className="h-5 w-5 shrink-0 text-gray-500" aria-hidden="true" />
              <div className="mt-0.5 flex flex-wrap gap-1">
                {bloodGroups.map((bg) => (
                  <span key={bg} className="rounded-full border border-brand/30 bg-brand-soft px-2.5 py-0.5 font-mono text-xs font-semibold text-brand">
                    {bg}
                  </span>
                ))}
              </div>
            </div>
          </div>

          {error && <Alert>{error}</Alert>}

          <div className="space-y-3">
            <Button size="lg" className="w-full" onClick={handleDownload} loading={downloading}>
              {downloading ? (
                t('modals.generatingJpg')
              ) : (
                <>
                  <Download className="h-5 w-5" aria-hidden="true" /> {t('modals.downloadPoster')}
                </>
              )}
            </Button>

            <Button variant="secondary" size="lg" className="w-full" onClick={onClose} disabled={downloading}>
              {t('common.cancel')}
            </Button>
          </div>
        </div>
      </div>
    </Dialog>
  );
}
