import { SHAPE_PATH, TONE, glyphPath, type MapGlyph, type MapShape, type MapTone } from './mapStyles';

export interface LegendItem {
  shape: MapShape;
  glyph: MapGlyph;
  tone: MapTone;
  /** Short name, identical to the label printed under the marker. */
  label: string;
  /** What the status means, in plain words. */
  description: string;
}

/** The marker symbol (shape + glyph + colour) as a React element, identical to the one drawn on the map. */
export function MapShapeIcon({ shape, glyph, tone, size = 28 }: Pick<LegendItem, 'shape' | 'glyph' | 'tone'> & { size?: number }) {
  const { color, text } = TONE[tone];
  return (
    <svg width={size} height={size} viewBox="0 0 32 32" aria-hidden="true" focusable="false" className="shrink-0">
      <path d={SHAPE_PATH[shape]} fill="var(--surface)" stroke={color} strokeWidth="2.6" strokeLinejoin="round" />
      <path d={glyphPath(glyph, shape)} fill="none" stroke={text} strokeWidth="2.6" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

/**
 * Legend shown under a map (not over it, so it never hides data and is read in order by screen readers).
 * Each entry repeats the marker symbol, its text label and what it means.
 */
export function MapLegend({ title, items, className }: { title: string; items: LegendItem[]; className?: string }) {
  return (
    <section aria-label={title} className={className}>
      <h3 className="eyebrow mb-3">{title}</h3>
      <ul className="grid gap-x-6 gap-y-3 sm:grid-cols-2">
        {items.map((item) => (
          <li key={item.label} className="flex items-start gap-3">
            <MapShapeIcon shape={item.shape} glyph={item.glyph} tone={item.tone} />
            <div className="min-w-0 text-sm">
              <p className="font-semibold text-foreground">{item.label}</p>
              <p className="text-gray-400">{item.description}</p>
            </div>
          </li>
        ))}
      </ul>
    </section>
  );
}
