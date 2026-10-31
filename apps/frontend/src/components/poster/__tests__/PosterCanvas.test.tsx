import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';
import { fallbackCopy } from '../copy';
import { isBlankImage } from '../exportPoster';
import { fixtureFacts, FIXTURE_SHARE_URL } from '../fixtures';
import { buildPosterModel } from '../model';
import { PosterCanvas } from '../PosterCanvas';
import { POSTER_SIZES } from '../sizes';
import { POSTER_LANGUAGES, POSTER_TEMPLATES, type PosterLanguage, type PosterTemplate } from '../types';
import type { PosterSize } from '../types';

function render(template: PosterTemplate, size: PosterSize, language: PosterLanguage, opts: { unverified?: boolean; long?: boolean } = {}) {
  const model = buildPosterModel(fixtureFacts(template, language, opts.long), language);
  const copy = fallbackCopy(template, model);
  const html = renderToStaticMarkup(
    <PosterCanvas template={template} size={size} language={language} model={model} headline={copy.headline} body={copy.body} qrUrl={FIXTURE_SHARE_URL} unverified={opts.unverified} />
  );
  return { html, model, copy };
}

describe('PosterCanvas', () => {
  for (const template of POSTER_TEMPLATES) {
    for (const size of POSTER_SIZES) {
      it(`${template} renders at ${size.id} in en, si and ta with the headline, a QR code and the exact size`, () => {
        for (const language of POSTER_LANGUAGES) {
          const { html, copy } = render(template, size, language);
          expect(html).toContain(`width:${size.width}px`);
          expect(html).toContain(`height:${size.height}px`);
          expect(html).toContain(`lang="${language}"`);
          expect(html).toContain(copy.headline.replace(/&/g, '&amp;'));
          // QR code is an inline SVG (path data), not an external image
          expect(html).toMatch(/<svg[^>]*viewBox="0 0 \d+ \d+"/);
          expect(html).not.toMatch(/<img/);
          expect(html).not.toContain('poster.'); // no unresolved message keys
        }
      });
    }
  }

  it('prints the system facts of an urgent request', () => {
    const { html } = render('URGENT_REQUEST', POSTER_SIZES[0], 'en');
    expect(html).toContain('O-');
    expect(html).toContain('3 units');
    expect(html).toContain('National Hospital of Sri Lanka, Colombo');
    expect(html).toContain('Scan to volunteer or share');
    // contact numbers are only printed when the organiser opted in (the fixture has none unless supplied via facts)
  });

  it('prints date, time, venue, blood types and organiser of a camp, formatted in the poster language', () => {
    const { html } = render('CAMP', POSTER_SIZES[1], 'en');
    expect(html).toContain('December 12, 2026');
    expect(html).toContain('09:00 AM – 03:00 PM'); // wall-clock time, not shifted by Sri Lanka's historical UTC+6
    expect(html).toContain('Town Hall Auditorium');
    expect(html).toContain('Lions Club of Colombo');
    expect(html).toContain('A+');
    const si = render('CAMP', POSTER_SIZES[1], 'si').html;
    expect(si).toContain('ප්‍රජා රුධිර දන් දීමේ කඳවුර');
  });

  it('marks unverified posters and not verified ones', () => {
    expect(render('CAMP', POSTER_SIZES[0], 'en', { unverified: true }).html).toContain('UNVERIFIED');
    expect(render('CAMP', POSTER_SIZES[0], 'en').html).not.toContain('UNVERIFIED');
  });

  it('uses only the literal poster palette, never theme variables, for colours', () => {
    const { html } = render('URGENT_REQUEST', POSTER_SIZES[0], 'en');
    expect(html).not.toMatch(/var\(--(background|foreground|surface|brand|primary|muted)/);
  });

  it('has no input for patient details: the canvas contains no form controls and no patient fields', () => {
    const { html } = render('URGENT_REQUEST', POSTER_SIZES[0], 'en');
    expect(html).not.toMatch(/<(input|textarea|select)/i);
    expect(html.toLowerCase()).not.toContain('patient name');
  });

  it('survives very long names (clamped, still rendered)', () => {
    const { html } = render('URGENT_REQUEST', POSTER_SIZES[2], 'ta', { long: true });
    expect(html).toContain('line-clamp');
  });
});

describe('isBlankImage', () => {
  const solid = (w: number, h: number, rgba: number[]) => {
    const data = new Uint8ClampedArray(w * h * 4);
    for (let i = 0; i < data.length; i += 4) data.set(rgba, i);
    return data;
  };

  it('flags a single-colour or transparent capture', () => {
    expect(isBlankImage(solid(200, 200, [248, 250, 252, 255]), 200, 200)).toBe(true);
    expect(isBlankImage(solid(200, 200, [0, 0, 0, 0]), 200, 200)).toBe(true);
  });

  it('accepts an image with real content', () => {
    const data = solid(200, 200, [248, 250, 252, 255]);
    for (const [x, y, rgba] of [[10, 10, [185, 28, 28, 255]], [90, 90, [15, 23, 42, 255]], [150, 150, [37, 99, 235, 255]], [20, 160, [254, 226, 226, 255]]] as const) {
      for (let dy = 0; dy < 8; dy++) for (let dx = 0; dx < 8; dx++) data.set(rgba, ((y + dy) * 200 + x + dx) * 4);
    }
    expect(isBlankImage(data, 200, 200)).toBe(false);
  });
});
