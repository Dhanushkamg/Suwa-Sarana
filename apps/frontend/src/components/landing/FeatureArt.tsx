import type { ReactNode } from 'react';
import styles from './landing.module.css';

/**
 * Tiny inline illustrations for the feature bento. All decorative (aria-hidden), drawn with the
 * design tokens only: hairlines (--line-strong), bone (--foreground) and ember / saffron accents.
 */

const LINE = 'var(--line-strong)';
const EMBER = 'var(--ember)';
const BONE = 'var(--foreground)';
const SAFFRON = 'var(--saffron)';

function Frame({ viewBox, children }: { viewBox: string; children: ReactNode }) {
  return (
    <svg viewBox={viewBox} aria-hidden="true" focusable="false" preserveAspectRatio="xMinYMid meet" className="h-full w-full overflow-visible">
      {children}
    </svg>
  );
}

/** Proximity matching: a request pin, distance rings, a radar sweep and the nearest donors lit in ember. */
export function GeoArt() {
  const cx = 90;
  const cy = 75;
  const donors = [
    { x: 118, y: 52, near: true },
    { x: 68, y: 104, near: true },
    { x: 142, y: 96, near: false },
    { x: 26, y: 50, near: false },
    { x: 186, y: 100, near: false },
    { x: 215, y: 72, near: false },
    { x: 200, y: 38, near: false },
    { x: 262, y: 108, near: false },
    { x: 268, y: 30, near: false },
    { x: 300, y: 70, near: false },
    { x: 338, y: 112, near: false },
    { x: 340, y: 40, near: false },
  ];
  return (
    <Frame viewBox="0 0 360 150">
      {[34, 64, 98].map((r, i) => (
        <circle key={r} cx={cx} cy={cy} r={r} fill="none" stroke={i === 0 ? EMBER : LINE} strokeOpacity={i === 0 ? 0.55 : 1} strokeDasharray={i === 0 ? undefined : '2 4'} />
      ))}
      <g className={styles.sweep} style={{ transformOrigin: `${cx}px ${cy}px` }}>
        <path d={`M${cx} ${cy} L${cx} ${cy - 98} A98 98 0 0 1 ${cx + 52} ${cy - 83} Z`} fill={EMBER} fillOpacity={0.1} />
        <line x1={cx} y1={cy} x2={cx} y2={cy - 98} stroke={EMBER} strokeOpacity={0.6} />
      </g>
      {donors
        .filter((d) => d.near)
        .map((d) => (
          <line key={`l${d.x}`} x1={cx} y1={cy} x2={d.x} y2={d.y} stroke={EMBER} strokeOpacity={0.5} />
        ))}
      {donors.map((d) => (
        <circle key={`${d.x}-${d.y}`} cx={d.x} cy={d.y} r={d.near ? 4 : 2.6} fill={d.near ? EMBER : BONE} fillOpacity={d.near ? 1 : 0.35} />
      ))}
      <circle cx={cx} cy={cy} r={6} fill={EMBER} className="dot-ping" />
      <circle cx={cx} cy={cy} r={5} fill={EMBER} />
      <circle cx={cx} cy={cy} r={1.8} fill="var(--background)" />
      <text x={cx + 38} y={cy + 12} fill={BONE} fillOpacity={0.5} fontSize={8} style={{ fontFamily: 'var(--font-mono)' }}>
        5 km
      </text>
    </Frame>
  );
}

/** Real-time alerts: a broadcasting source on the left, notification cards arriving on the right. */
export function LiveArt() {
  return (
    <Frame viewBox="0 0 280 150">
      <circle cx={34} cy={75} r={5} fill={EMBER} className="dot-ping" />
      <circle cx={34} cy={75} r={5} fill={EMBER} />
      {[22, 42, 62].map((r, i) => (
        <path
          key={r}
          d={`M${34 + r * Math.cos(-0.75)} ${75 + r * Math.sin(-0.75)} A${r} ${r} 0 0 1 ${34 + r * Math.cos(0.75)} ${75 + r * Math.sin(0.75)}`}
          fill="none"
          stroke={EMBER}
          strokeOpacity={0.85 - i * 0.25}
          strokeWidth={1.6}
          strokeLinecap="round"
        />
      ))}
      {[0, 1, 2].map((i) => (
        <g key={i} opacity={1 - i * 0.28}>
          <rect x={132} y={14 + i * 42} width={138} height={34} rx={10} fill={BONE} fillOpacity={i === 0 ? 0.09 : 0.05} stroke={i === 0 ? EMBER : LINE} strokeOpacity={i === 0 ? 0.7 : 1} />
          <circle cx={150} cy={31 + i * 42} r={4.5} fill={i === 0 ? EMBER : BONE} fillOpacity={i === 0 ? 1 : 0.35} className={i === 0 ? styles.blink : undefined} />
          <rect x={164} y={25 + i * 42} width={70 - i * 10} height={4} rx={2} fill={BONE} fillOpacity={0.4} />
          <rect x={164} y={34 + i * 42} width={44} height={3.5} rx={1.75} fill={BONE} fillOpacity={0.2} />
        </g>
      ))}
    </Frame>
  );
}

/** Escalation: the search widens in steps, 5 -> 15 -> 25 km. */
export function EscalationArt() {
  const steps = [
    { x: 10, h: 36, label: '5', o: 0.28 },
    { x: 78, h: 72, label: '15', o: 0.55 },
    { x: 146, h: 108, label: '25', o: 1 },
  ];
  return (
    <Frame viewBox="0 0 220 150">
      {steps.map((s) => (
        <g key={s.label}>
          <rect x={s.x} y={126 - s.h} width={56} height={s.h} rx={8} fill={EMBER} fillOpacity={s.o * 0.9} />
          <text x={s.x + 28} y={142} textAnchor="middle" fill={BONE} fillOpacity={0.55} fontSize={9} style={{ fontFamily: 'var(--font-mono)' }}>
            {s.label} km
          </text>
        </g>
      ))}
    </Frame>
  );
}

/** Reliability score: a gauge arc with a knob. No number is shown on purpose. */
export function GaugeArt() {
  const ticks = Array.from({ length: 11 }, (_, i) => {
    const a = Math.PI - (i / 10) * Math.PI;
    return { x1: 110 + 92 * Math.cos(a), y1: 120 - 92 * Math.sin(a), x2: 110 + 98 * Math.cos(a), y2: 120 - 98 * Math.sin(a), k: i };
  });
  return (
    <Frame viewBox="0 0 220 150">
      {ticks.map((t) => (
        <line key={t.k} x1={t.x1} y1={t.y1} x2={t.x2} y2={t.y2} stroke={LINE} strokeWidth={1.5} />
      ))}
      <path d="M30 120 A80 80 0 0 1 190 120" fill="none" stroke={LINE} strokeWidth={8} strokeLinecap="round" />
      <path d="M30 120 A80 80 0 0 1 166.6 63.4" fill="none" stroke={SAFFRON} strokeWidth={8} strokeLinecap="round" />
      <circle cx={166.6} cy={63.4} r={9} fill="var(--background)" stroke={SAFFRON} strokeWidth={2.5} />
      <circle cx={166.6} cy={63.4} r={3} fill={SAFFRON} />
    </Frame>
  );
}

/** AI intake: a plain-language message on the left becomes structured fields on the right. */
export function ChatArt() {
  return (
    <Frame viewBox="0 0 300 150">
      <path d="M12 12 h156 a12 12 0 0 1 12 12 v34 a12 12 0 0 1 -12 12 h-120 l-18 14 v-14 h-6 a12 12 0 0 1 -12 -12 v-34 a12 12 0 0 1 12 -12 Z" fill={BONE} fillOpacity={0.07} stroke={LINE} />
      <rect x={26} y={26} width={120} height={4} rx={2} fill={BONE} fillOpacity={0.45} />
      <rect x={26} y={37} width={92} height={4} rx={2} fill={BONE} fillOpacity={0.3} />
      <rect x={26} y={48} width={104} height={4} rx={2} fill={BONE} fillOpacity={0.2} />
      <path d="M196 64 C 214 80, 214 92, 232 100" fill="none" stroke={EMBER} strokeOpacity={0.7} strokeDasharray="2 3" />
      <path d="M228 94 l6 6 l-8 3" fill="none" stroke={EMBER} strokeOpacity={0.9} strokeLinecap="round" strokeLinejoin="round" />
      <g>
        <rect x={20} y={104} width={36} height={22} rx={11} fill={EMBER} />
        <text x={38} y={119} textAnchor="middle" fill="var(--background)" fontSize={11} fontWeight={600} style={{ fontFamily: 'var(--font-mono)' }}>
          O+
        </text>
        <rect x={64} y={104} width={64} height={22} rx={11} fill="none" stroke={LINE} />
        <rect x={76} y={113} width={40} height={4} rx={2} fill={BONE} fillOpacity={0.35} />
        <rect x={136} y={104} width={48} height={22} rx={11} fill="none" stroke={SAFFRON} strokeOpacity={0.7} />
        <rect x={148} y={113} width={24} height={4} rx={2} fill={SAFFRON} fillOpacity={0.7} />
        <rect x={192} y={104} width={58} height={22} rx={11} fill="none" stroke={LINE} />
        <rect x={204} y={113} width={34} height={4} rx={2} fill={BONE} fillOpacity={0.35} />
      </g>
    </Frame>
  );
}

/** Verified requests: a shield with a check, ringed by hairline orbits. */
export function ShieldArt() {
  return (
    <Frame viewBox="0 0 220 150">
      <circle cx={110} cy={75} r={70} fill="none" stroke={LINE} strokeDasharray="2 5" />
      <circle cx={110} cy={75} r={52} fill="none" stroke={LINE} />
      <path d="M110 22 L152 38 V76 C152 102 134 120 110 130 C86 120 68 102 68 76 V38 Z" fill={EMBER} fillOpacity={0.12} stroke={EMBER} strokeWidth={1.6} strokeLinejoin="round" />
      <path d="M91 76 L106 91 L131 62" fill="none" stroke={EMBER} strokeWidth={3.2} strokeLinecap="round" strokeLinejoin="round" />
    </Frame>
  );
}

/** Private circles: a lock at the centre of a ring of people-dots, a few of them lit. */
export function CircleArt() {
  const ring = (r: number, n: number, offset: number) =>
    Array.from({ length: n }, (_, i) => {
      const a = offset + (i / n) * Math.PI * 2;
      return { x: 150 + r * Math.cos(a), y: 75 + r * Math.sin(a), k: `${r}-${i}` };
    });
  const inner = ring(46, 8, -Math.PI / 2);
  const outer = ring(68, 12, -Math.PI / 2 + 0.26);
  const lit = new Set([0, 3, 5]);
  return (
    <Frame viewBox="0 0 300 150">
      <circle cx={150} cy={75} r={46} fill="none" stroke={LINE} strokeDasharray="2 4" />
      <circle cx={150} cy={75} r={68} fill="none" stroke={LINE} />
      {outer.map((d, i) => (
        <circle key={d.k} cx={d.x} cy={d.y} r={2.4} fill={BONE} fillOpacity={i % 4 === 1 ? 0.6 : 0.25} />
      ))}
      {inner.map((d, i) => (
        <circle key={d.k} cx={d.x} cy={d.y} r={lit.has(i) ? 4.6 : 3.4} fill={lit.has(i) ? EMBER : BONE} fillOpacity={lit.has(i) ? 1 : 0.4} />
      ))}
      <rect x={138} y={72} width={24} height={19} rx={4} fill={EMBER} />
      <path d="M143 72 v-6 a7 7 0 0 1 14 0 v6" fill="none" stroke={EMBER} strokeWidth={3} strokeLinecap="round" />
      <circle cx={150} cy={80} r={2.2} fill="var(--background)" />
    </Frame>
  );
}

/** Three scripts, stacked like avatars: Sinhala, Tamil, Latin. */
export function LanguageStack({ className }: { className?: string }) {
  return (
    <div className={className} aria-hidden="true">
      <div className="flex items-center">
        {[
          { g: 'සි', cls: 'text-ember' },
          { g: 'த', cls: 'text-saffron' },
          { g: 'E', cls: 'text-foreground' },
        ].map((l, i) => (
          <span
            key={l.g}
            className={`font-display flex h-11 w-11 items-center justify-center rounded-full border border-line-strong bg-surface-raised text-xl sm:h-12 sm:w-12 sm:text-2xl ${l.cls} ${i > 0 ? '-ml-3' : ''}`}
          >
            {l.g}
          </span>
        ))}
      </div>
    </div>
  );
}
