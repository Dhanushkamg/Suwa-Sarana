'use client';

import { useEffect, useMemo, useRef, useState } from 'react';
import { fallbackCopy } from './copy';
import { detectOverflow, ensureFonts } from './exportPoster';
import { fixtureFacts, FIXTURE_SHARE_URL } from './fixtures';
import { buildPosterModel } from './model';
import { PosterCanvas } from './PosterCanvas';
import { posterSize } from './sizes';
import type { PosterLanguage, PosterSizeId, PosterTemplate } from './types';

/**
 * Renders one poster at 1:1 from fixture data, with nothing else on the page, for the dev-only route
 * /dev/poster-preview. Screenshot `#poster-root` once it has `data-ready="true"`:
 *   data-ready    "true" once the web fonts for the text on the poster are loaded
 *   data-fonts    "ok" | "missing" (a face for the text could not be confirmed: do not accept the screenshot)
 *   data-overflow "true" when text is clipped (should never be the case for the fixtures)
 */
export default function PosterPreviewClient({
  template,
  sizeId,
  language,
  unverified,
  long,
}: {
  template: PosterTemplate;
  sizeId: PosterSizeId;
  language: PosterLanguage;
  unverified: boolean;
  long: boolean;
}) {
  const ref = useRef<HTMLDivElement>(null);
  const [status, setStatus] = useState<{ ready: boolean; fonts: boolean; overflow: boolean }>({ ready: false, fonts: false, overflow: false });

  const size = posterSize(sizeId);
  const model = useMemo(() => buildPosterModel(fixtureFacts(template, language, long), language), [template, language, long]);
  const copy = useMemo(() => fallbackCopy(template, model), [template, model]);

  // Keeps the floating chat assistant out of the screenshot (it steps aside while a full-screen menu is open)
  useEffect(() => {
    document.body.setAttribute('data-nav-open', 'true');
    return () => document.body.removeAttribute('data-nav-open');
  }, []);

  useEffect(() => {
    const node = ref.current;
    if (!node) return;
    let cancelled = false;
    ensureFonts(node).then((fonts) => {
      requestAnimationFrame(() => {
        if (!cancelled) setStatus({ ready: true, fonts, overflow: detectOverflow(node) });
      });
    });
    return () => {
      cancelled = true;
    };
  }, [template, sizeId, language, unverified, long]);

  return (
    <>
      {/* No film grain over the artwork, no smooth scrolling: the screenshot must be deterministic */}
      <style>{'body::before{display:none!important}html{scroll-behavior:auto!important}body{margin:0;background:#fff}'}</style>
      <div
        id="poster-root"
        ref={ref}
        data-ready={status.ready ? 'true' : 'false'}
        data-fonts={status.fonts ? 'ok' : 'missing'}
        data-overflow={status.overflow ? 'true' : 'false'}
        style={{ width: size.width, height: size.height }}
      >
        <PosterCanvas
          template={template}
          size={size}
          language={language}
          model={model}
          headline={copy.headline}
          body={copy.body}
          qrUrl={FIXTURE_SHARE_URL}
          unverified={unverified}
        />
      </div>
    </>
  );
}
