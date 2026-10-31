import type { CSSProperties, ReactNode } from 'react';
import { QRCodeSVG } from 'qrcode.react';
import { Calendar, Clock, Droplets, Heart, Hospital, MapPin, Phone, TriangleAlert, Users } from 'lucide-react';
import { translateIn } from '@/lib/i18n';
import { POSTER } from '@/components/camps/posterPalette';
import type { PosterModel } from './model';
import type { PosterLanguage, PosterSize, PosterTemplate } from './types';

/**
 * The poster artwork: plain DOM with inline styles at the exact export size (1080x1080 ... ), so html-to-image captures
 * it 1:1. Colours are the literal POSTER palette on purpose (the PNG must look the same in light and dark mode).
 * Every word on it is either system data or text from `poster.*` messages in the poster's own language.
 */
export interface PosterCanvasProps {
  template: PosterTemplate;
  size: PosterSize;
  language: PosterLanguage;
  model: PosterModel;
  headline: string;
  body: string;
  /** Link encoded in the QR code */
  qrUrl: string;
  /** Adds the "unverified" mark (organisers who are not VERIFIED yet) */
  unverified?: boolean;
}

// One stack for every language: Latin from Geist, Sinhala and Tamil from Noto (next/font variables set on <html> in layout.tsx)
export const POSTER_FONT_STACK = 'var(--font-geist), var(--font-sinhala), var(--font-tamil), ui-sans-serif, system-ui, sans-serif';

interface Metrics {
  wide: boolean;
  /** tile beside the headline (square, portrait) instead of above it (story) */
  heroSide: boolean;
  pad: number;
  gap: number;
  tile: number;
  head: number;
  body: number;
  fact: number;
  qr: number;
  brand: number;
  chip: number;
  headLines: number;
  bodyLines: number;
}

function metricsFor(size: PosterSize): Metrics {
  switch (size.kind) {
    case 'landscape':
      return { wide: true, heroSide: false, pad: 40, gap: 14, tile: 0, head: 50, body: 24, fact: 25, qr: 150, brand: 28, chip: 17, headLines: 3, bodyLines: 4 };
    case 'portrait':
      return { wide: false, heroSide: true, pad: 60, gap: 28, tile: 260, head: 72, body: 35, fact: 35, qr: 230, brand: 38, chip: 23, headLines: 3, bodyLines: 6 };
    case 'story':
      return { wide: false, heroSide: false, pad: 72, gap: 36, tile: 380, head: 92, body: 40, fact: 38, qr: 260, brand: 46, chip: 30, headLines: 4, bodyLines: 6 };
    default:
      return { wide: false, heroSide: true, pad: 56, gap: 22, tile: 224, head: 68, body: 31, fact: 31, qr: 200, brand: 36, chip: 22, headLines: 3, bodyLines: 5 };
  }
}

/** Sinhala and Tamil words are longer and the glyphs taller: smaller type, more line height. */
const SCRIPT = {
  en: { scale: 1, headLeading: 1.15, bodyLeading: 1.5 },
  si: { scale: 0.88, headLeading: 1.4, bodyLeading: 1.7 },
  ta: { scale: 0.84, headLeading: 1.4, bodyLeading: 1.7 },
} as const;

const fitFactor = (text: string) => (text.length <= 36 ? 1 : text.length <= 52 ? 0.9 : text.length <= 68 ? 0.8 : 0.72);

const clamp = (lines: number): CSSProperties => ({
  display: '-webkit-box',
  WebkitLineClamp: lines,
  WebkitBoxOrient: 'vertical',
  overflow: 'hidden',
});

/** The Suwa Sarana mark with literal colours (the app's LogoMark follows the theme, a poster must not) */
function Drop({ size, fill, pulse }: { size: number; fill: string; pulse: string }) {
  return (
    <svg viewBox="0 0 32 32" width={size} height={size} aria-hidden="true">
      <path d="M16 2.5c-3.3 4.6-9 10.2-9 16.1a9 9 0 0 0 18 0C25 12.700 19.300 7.100 16 2.500Z" fill={fill} />
      <path d="M8.500 19.500h4.200l1.800-4.200 2.800 8 2-4h4.200" fill="none" stroke={pulse} strokeWidth="1.800" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

function Tile({ template, model, language, w, h, flex, size }: { template: PosterTemplate; model: PosterModel; language: PosterLanguage; w: number | string; h: number | string; flex?: boolean; size?: number }) {
  const dark = template === 'CAMP' || template === 'THANK_YOU';
  const base = size ?? (typeof w === 'number' ? w : typeof h === 'number' ? h : 200);
  const common: CSSProperties = {
    width: w,
    height: h,
    flex: flex ? 1 : '0 0 auto',
    minHeight: 0,
    borderRadius: base * 0.1,
    background: dark ? POSTER.ink : POSTER.brand,
    color: dark ? POSTER.onInk : POSTER.onBrand,
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center',
    textAlign: 'center',
    gap: base * 0.02,
    overflow: 'hidden',
  };
  const small = Math.max(14, base * 0.1);

  if (template === 'URGENT_REQUEST' && model.bloodType) {
    return (
      <div style={common}>
        <div style={{ fontSize: base * (model.bloodType.length > 2 ? 0.4 : 0.48), fontWeight: 800, lineHeight: 1 }}>{model.bloodType}</div>
        {model.unitsText && <div style={{ fontSize: small * 1.15, fontWeight: 700, lineHeight: 1.3 }}>{model.unitsText}</div>}
      </div>
    );
  }
  if (template === 'CAMP' && model.day) {
    return (
      <div style={common}>
        <div style={{ fontSize: small * 1.2, fontWeight: 700, lineHeight: 1.3, color: POSTER.onInkMuted }}>{model.monthShort}</div>
        <div style={{ fontSize: base * 0.46, fontWeight: 800, lineHeight: 1 }}>{model.day}</div>
        <div style={{ fontSize: small, fontWeight: 600, lineHeight: 1.3, color: POSTER.onInkMuted }}>{model.weekday}</div>
      </div>
    );
  }
  if (template === 'THANK_YOU') {
    return (
      <div style={common}>
        <Heart size={base * 0.42} color={POSTER.brandSoft} fill={POSTER.brandSoft} aria-hidden="true" />
        <div style={{ fontSize: small, fontWeight: 700, color: POSTER.onInkMuted }}>{translateIn(language, 'poster.eyebrow.THANK_YOU')}</div>
      </div>
    );
  }
  // AWARENESS, and any template whose key datum is missing
  return (
    <div style={common}>
      <Drop size={base * 0.5} fill={dark ? POSTER.brand : POSTER.onBrand} pulse={dark ? POSTER.onBrand : POSTER.brand} />
    </div>
  );
}

interface Row {
  icon: ReactNode;
  text?: string;
  pills?: string[];
}

function rowsFor(template: PosterTemplate, model: PosterModel, language: PosterLanguage, iconSize: number): Row[] {
  const icon = (Icon: typeof MapPin) => <Icon size={iconSize} color={POSTER.primary} aria-hidden="true" />;
  const rows: Row[] = [];
  const push = (Icon: typeof MapPin, text?: string) => text && rows.push({ icon: icon(Icon), text });

  switch (template) {
    case 'URGENT_REQUEST':
      push(Hospital, model.hospital);
      push(MapPin, model.district);
      push(Phone, model.contact);
      break;
    case 'CAMP':
      push(Calendar, model.dateLong);
      push(Clock, model.timeText);
      push(MapPin, model.venueText);
      if (model.bloodTypes.length > 0) rows.push({ icon: icon(Droplets), pills: model.bloodTypes });
      if (model.organiser) push(Users, translateIn(language, 'poster.organisedBy', { name: model.organiser }));
      break;
    case 'THANK_YOU':
      push(Heart, model.campName);
      push(Calendar, model.dateLong);
      push(MapPin, model.venueText ?? model.district);
      break;
    default:
      break;
  }
  return rows;
}

function Rows({ rows, fact }: { rows: Row[]; fact: number }) {
  if (rows.length === 0) return null;
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: fact * 0.4 }}>
      {rows.map((row, i) => (
        <div key={i} style={{ display: 'flex', alignItems: 'flex-start', gap: fact * 0.5 }}>
          <span style={{ display: 'flex', flex: '0 0 auto', paddingTop: fact * 0.12 }}>{row.icon}</span>
          {row.pills ? (
            <span style={{ display: 'flex', flexWrap: 'wrap', gap: fact * 0.25 }}>
              {row.pills.map((p) => (
                <span key={p} style={{ background: POSTER.brandSoft, color: POSTER.brand, borderRadius: 999, padding: `${fact * 0.04}px ${fact * 0.45}px`, fontSize: fact * 0.92, fontWeight: 800, lineHeight: 1.35 }}>
                  {p}
                </span>
              ))}
            </span>
          ) : (
            <span style={{ minWidth: 0, fontSize: fact, fontWeight: 700, lineHeight: 1.3, color: POSTER.text, overflowWrap: 'anywhere' }}>{row.text}</span>
          )}
        </div>
      ))}
    </div>
  );
}

function QrTile({ url, size }: { url: string; size: number }) {
  return (
    <div style={{ background: '#FFFFFF', border: `2px solid ${POSTER.line}`, borderRadius: size * 0.1, padding: size * 0.07, display: 'flex' }}>
      <QRCodeSVG value={url} size={size} level="M" marginSize={0} fgColor={POSTER.ink} bgColor="#FFFFFF" />
    </div>
  );
}

const hostOf = (url: string) => url.replace(/^https?:\/\//, '').replace(/\/.*$/, '');

export function PosterCanvas({ template, size, language, model, headline, body, qrUrl, unverified }: PosterCanvasProps) {
  const m = metricsFor(size);
  const script = SCRIPT[language];
  const tr = (key: string, values?: Record<string, string | number>) => translateIn(language, key, values);

  const headSize = m.head * script.scale * fitFactor(headline);
  const bodySize = m.body * script.scale * (body.length <= 140 ? 1 : body.length <= 200 ? 0.93 : 0.86);
  const rows = rowsFor(template, model, language, m.fact * 0.95);
  const cta = tr(`poster.cta.${template}`);

  const frame: CSSProperties = {
    position: 'relative',
    width: size.width,
    height: size.height,
    overflow: 'hidden',
    boxSizing: 'border-box',
    padding: m.pad,
    background: POSTER.background,
    color: POSTER.text,
    fontFamily: POSTER_FONT_STACK,
    display: 'flex',
    flexDirection: 'column',
    gap: m.gap,
  };

  const chip = (
    <span style={{ background: POSTER.brandSoft, color: POSTER.brand, borderRadius: 999, padding: `${m.chip * 0.3}px ${m.chip * 0.8}px`, fontSize: m.chip, fontWeight: 800, lineHeight: 1.3, whiteSpace: 'nowrap' }}>
      {tr(`poster.eyebrow.${template}`)}
    </span>
  );
  const unverifiedBadge = unverified && (
    <span style={{ display: 'inline-flex', alignItems: 'center', gap: m.chip * 0.4, background: POSTER.warningSoft, color: POSTER.warningText, borderRadius: 999, padding: `${m.chip * 0.3}px ${m.chip * 0.8}px`, fontSize: m.chip, fontWeight: 800, lineHeight: 1.3, whiteSpace: 'nowrap' }}>
      <TriangleAlert size={m.chip * 1.1} aria-hidden="true" />
      {tr('poster.unverifiedBadge')}
    </span>
  );

  const header = (
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: m.gap }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: m.brand * 0.35, minWidth: 0 }}>
        <Drop size={m.brand * 1.6} fill={POSTER.brand} pulse={POSTER.onBrand} />
        <div style={{ minWidth: 0 }}>
          <div style={{ fontSize: m.brand, fontWeight: 800, lineHeight: 1.15 }}>Suwa Sarana</div>
          <div style={{ fontSize: m.brand * 0.55, fontWeight: 500, lineHeight: 1.3, color: POSTER.muted }}>{tr('poster.tagline')}</div>
        </div>
      </div>
      <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: m.chip * 0.4 }}>
        {chip}
        {unverifiedBadge}
      </div>
    </div>
  );

  const headlineEl = (
    <h1
      data-fit
      style={{ margin: 0, minWidth: 0, flex: 1, fontSize: headSize, fontWeight: 800, lineHeight: script.headLeading, letterSpacing: language === 'en' ? '-0.01em' : 0, color: POSTER.text, overflowWrap: 'anywhere', ...clamp(m.headLines) }}
    >
      {headline}
    </h1>
  );
  const bodyEl = (
    <p data-fit style={{ margin: 0, fontSize: bodySize, fontWeight: 500, lineHeight: script.bodyLeading, color: POSTER.muted, overflowWrap: 'anywhere', ...clamp(m.bodyLines) }}>
      {body}
    </p>
  );
  const ctaEl = (
    <div style={{ fontSize: m.fact * 0.92 * script.scale, fontWeight: 800, lineHeight: language === 'en' ? 1.3 : 1.5, color: POSTER.text }}>{cta}</div>
  );
  const qrBlock = (
    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: m.gap * 0.4, flex: '0 0 auto' }}>
      <QrTile url={qrUrl} size={m.qr} />
      <div style={{ fontSize: Math.max(16, m.fact * 0.62), fontWeight: 600, color: POSTER.muted, lineHeight: 1.3 }}>{hostOf(qrUrl)}</div>
    </div>
  );

  const watermark = unverified && (
    <div
      aria-hidden="true"
      data-decor
      style={{ position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', pointerEvents: 'none', transform: 'rotate(-24deg)', fontSize: Math.min(size.width, size.height) * 0.19, fontWeight: 900, letterSpacing: '0.06em', color: POSTER.ink, opacity: 0.07, whiteSpace: 'nowrap' }}
    >
      {tr('poster.unverifiedStamp')}
    </div>
  );

  const decoration = (
    <div aria-hidden="true" data-decor style={{ position: 'absolute', top: -size.height * 0.16, right: -size.height * 0.16, width: size.height * 0.5, height: size.height * 0.5, borderRadius: '50%', background: POSTER.brandSoft, opacity: 0.55 }} />
  );

  if (m.wide) {
    return (
      <div data-poster={template} lang={language} dir="ltr" style={{ ...frame, flexDirection: 'row', gap: 36 }}>
        {decoration}
        <div style={{ position: 'relative', flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column', gap: m.gap }}>
          {header}
          {headlineEl}
          {bodyEl}
          <div style={{ marginTop: 'auto' }}>
            <Rows rows={rows} fact={m.fact} />
          </div>
        </div>
        <div style={{ position: 'relative', width: 300, flex: '0 0 auto', display: 'flex', flexDirection: 'column', gap: m.gap }}>
          <Tile template={template} model={model} language={language} w="100%" h="auto" flex size={290} />
          <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
            {qrBlock}
            <div style={{ minWidth: 0, fontSize: m.fact * 0.86 * script.scale, fontWeight: 800, lineHeight: language === 'en' ? 1.3 : 1.5 }}>{cta}</div>
          </div>
        </div>
        {watermark}
      </div>
    );
  }

  return (
    <div data-poster={template} lang={language} dir="ltr" style={frame}>
      {decoration}
      <div style={{ position: 'relative' }}>{header}</div>

      {m.heroSide ? (
        <div style={{ position: 'relative', display: 'flex', alignItems: 'center', gap: m.gap * 1.4 }}>
          <Tile template={template} model={model} language={language} w={m.tile} h={m.tile} />
          {headlineEl}
        </div>
      ) : (
        <div style={{ position: 'relative', display: 'flex', flexDirection: 'column', gap: m.gap }}>
          <Tile template={template} model={model} language={language} w="100%" h={m.tile} size={m.tile} />
          {headlineEl}
        </div>
      )}

      <div style={{ position: 'relative' }}>{bodyEl}</div>

      <div style={{ position: 'relative', marginTop: 'auto', display: 'flex', alignItems: 'flex-end', justifyContent: 'space-between', gap: m.gap * 1.2 }}>
        <div style={{ minWidth: 0, flex: 1, display: 'flex', flexDirection: 'column', gap: m.gap * 0.8 }}>
          <Rows rows={rows} fact={m.fact} />
          {ctaEl}
        </div>
        {qrBlock}
      </div>
      {watermark}
    </div>
  );
}
