'use client';

import { useMemo, useRef } from 'react';
import { DISTRICT_COORDS } from '@/lib/districts';
import { cn } from '@/lib/utils';

/**
 * Sri Lanka drawn as a field of dots, with the island's main cities pulsing like a heartbeat.
 * Purely decorative (aria-hidden): a simplified coastline, not a survey-grade map.
 * Drag the cursor across it and the dots under it light up in ember.
 */

// Coarse coastline, [longitude, latitude], clockwise from the northern tip.
const COAST: [number, number][] = [
  [80.23, 9.83], [80.5, 9.6], [80.78, 9.3], [81.0, 8.95], [81.23, 8.57], [81.45, 8.1], [81.7, 7.72], [81.83, 7.35],
  [81.86, 6.88], [81.7, 6.55], [81.4, 6.25], [81.12, 6.1], [80.8, 5.99], [80.58, 5.92], [80.22, 6.01], [80.08, 6.2],
  [79.98, 6.5], [79.85, 6.93], [79.82, 7.3], [79.78, 7.6], [79.72, 8.05], [79.76, 8.4], [79.84, 8.7], [79.72, 9.1],
  [79.95, 9.15], [80.1, 9.5], [80.0, 9.78],
];

const VIEW_W = 250;
const VIEW_H = 410;
const project = ([lon, lat]: [number, number]): [number, number] => [(lon - 79.5) * 100, (9.95 - lat) * 100];

function inside(x: number, y: number, poly: [number, number][]) {
  let c = false;
  for (let i = 0, j = poly.length - 1; i < poly.length; j = i++) {
    const [xi, yi] = poly[i];
    const [xj, yj] = poly[j];
    if (yi > y !== yj > y && x < ((xj - xi) * (y - yi)) / (yj - yi) + xi) c = !c;
  }
  return c;
}

// Cities that pulse, with the label side so text never collides with the coast
const PULSES: { name: string; side: 'l' | 'r' }[] = [
  { name: 'Jaffna', side: 'r' },
  { name: 'Anuradhapura', side: 'l' },
  { name: 'Trincomalee', side: 'r' },
  { name: 'Kurunegala', side: 'l' },
  { name: 'Kandy', side: 'r' },
  { name: 'Colombo', side: 'l' },
  { name: 'Ratnapura', side: 'l' },
  { name: 'Batticaloa', side: 'r' },
  { name: 'Galle', side: 'l' },
];

function Dots({ dots, fill }: { dots: [number, number][]; fill: string }) {
  return (
    <svg viewBox={`0 0 ${VIEW_W} ${VIEW_H}`} className="absolute inset-0 h-full w-full" fill={fill}>
      {dots.map(([x, y]) => (
        <circle key={`${x}-${y}`} cx={x} cy={y} r={1.5} />
      ))}
    </svg>
  );
}

export function SriLankaMap({ className, showLabels = true }: { className?: string; showLabels?: boolean }) {
  const ref = useRef<HTMLDivElement>(null);

  const dots = useMemo(() => {
    const poly = COAST.map(project);
    const out: [number, number][] = [];
    const step = 6.5;
    for (let row = 0, y = 4; y < VIEW_H; y += step, row++) {
      for (let x = (row % 2) * (step / 2) + 2; x < VIEW_W; x += step) {
        if (inside(x, y, poly)) out.push([x, y]);
      }
    }
    return out;
  }, []);

  const cities = useMemo(
    () =>
      PULSES.map((p) => {
        const [lat, lon] = DISTRICT_COORDS[p.name];
        const [x, y] = project([lon, lat]);
        return { ...p, x, y };
      }),
    []
  );

  const onMove = (e: React.PointerEvent<HTMLDivElement>) => {
    const r = e.currentTarget.getBoundingClientRect();
    e.currentTarget.style.setProperty('--mx', `${e.clientX - r.left}px`);
    e.currentTarget.style.setProperty('--my', `${e.clientY - r.top}px`);
  };

  return (
    <div
      ref={ref}
      onPointerMove={onMove}
      aria-hidden="true"
      className={cn('relative aspect-[250/410] select-none', className)}
      style={{ ['--mx' as string]: '-200px', ['--my' as string]: '-200px' }}
    >
      {/* base layer: quiet bone dots */}
      <div className="absolute inset-0 opacity-30">
        <Dots dots={dots} fill="var(--foreground)" />
      </div>
      {/* light layer: ember dots revealed only under the cursor */}
      <div
        className="absolute inset-0"
        style={{ maskImage: 'radial-gradient(circle 70px at var(--mx) var(--my), #000 20%, transparent 100%)', WebkitMaskImage: 'radial-gradient(circle 70px at var(--mx) var(--my), #000 20%, transparent 100%)' }}
      >
        <Dots dots={dots} fill="var(--ember)" />
      </div>

      <svg viewBox={`0 0 ${VIEW_W} ${VIEW_H}`} className="absolute inset-0 h-full w-full overflow-visible">
        {/* dispatch arcs fanning out from Colombo */}
        {cities
          .filter((c) => c.name !== 'Colombo')
          .map((c, i) => {
            const origin = cities.find((o) => o.name === 'Colombo')!;
            const mx = (origin.x + c.x) / 2 + (c.x < origin.x ? -18 : 18);
            const my = (origin.y + c.y) / 2;
            return (
              <path
                key={c.name}
                d={`M${origin.x} ${origin.y} Q${mx} ${my} ${c.x} ${c.y}`}
                fill="none"
                stroke="var(--ember)"
                strokeOpacity={0.35}
                strokeWidth={0.6}
                strokeDasharray="2 3"
                style={{ animationDelay: `${i * 0.2}s` }}
              />
            );
          })}

        {cities.map((c, i) => (
          <g key={c.name}>
            <circle cx={c.x} cy={c.y} r={3} fill="var(--ember)" className="dot-ping" style={{ animationDelay: `${i * 0.35}s` }} />
            <circle cx={c.x} cy={c.y} r={2.4} fill="var(--ember)" />
            <circle cx={c.x} cy={c.y} r={0.9} fill="var(--background)" />
            {showLabels && (
            <text
              x={c.x + (c.side === 'r' ? 7 : -7)}
              y={c.y + 2.4}
              textAnchor={c.side === 'r' ? 'start' : 'end'}
              fill="var(--foreground)"
              fillOpacity={0.62}
              fontSize={6.5}
              stroke="var(--background)"
              strokeWidth={2.6}
              strokeLinejoin="round"
              paintOrder="stroke"
              style={{ fontFamily: 'var(--font-mono)', letterSpacing: '0.08em', textTransform: 'uppercase' }}
            >
              {c.name}
            </text>
            )}
          </g>
        ))}
      </svg>
    </div>
  );
}
