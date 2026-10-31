import * as htmlToImage from 'html-to-image';
import { POSTER } from '@/components/camps/posterPalette';

export type PosterExportErrorKind = 'blank' | 'failed';

export class PosterExportError extends Error {
  readonly kind: PosterExportErrorKind;
  constructor(kind: PosterExportErrorKind, cause?: unknown) {
    super(`Poster export failed: ${kind}`);
    this.name = 'PosterExportError';
    this.kind = kind;
    if (cause !== undefined) this.cause = cause;
  }
}

/**
 * True when something on the poster does not fit: an element sticks out of the content box (inside the padding), or
 * the headline / short text (data-fit) is clipped by its line clamp. Decorations (data-decor) are meant to bleed off the edge and are ignored.
 * Works on the scaled preview too (positions are compared in screen pixels).
 */
export function detectOverflow(root: HTMLElement): boolean {
  const rect = root.getBoundingClientRect();
  if (rect.width === 0 || rect.height === 0) return false;
  const scale = root.offsetHeight ? rect.height / root.offsetHeight : 1;
  const style = getComputedStyle(root);
  const inner = {
    top: rect.top + (parseFloat(style.paddingTop) || 0) * scale,
    left: rect.left + (parseFloat(style.paddingLeft) || 0) * scale,
    bottom: rect.bottom - (parseFloat(style.paddingBottom) || 0) * scale,
    right: rect.right - (parseFloat(style.paddingRight) || 0) * scale,
  };
  const tolerance = Math.max(2, 2 * scale);
  for (const el of Array.from(root.querySelectorAll<HTMLElement>('*'))) {
    if (el.closest('[data-decor]')) continue;
    const r = el.getBoundingClientRect();
    if (r.width === 0 && r.height === 0) continue;
    if (r.bottom > inner.bottom + tolerance || r.right > inner.right + tolerance || r.top < inner.top - tolerance || r.left < inner.left - tolerance) return true;
  }
  // A clamped element hides whole lines, so compare against half a line (a few pixels of font-metric slack are normal)
  return Array.from(root.querySelectorAll<HTMLElement>('[data-fit]')).some((el) => {
    const line = parseFloat(getComputedStyle(el).lineHeight) || parseFloat(getComputedStyle(el).fontSize) * 1.3 || 20;
    return el.scrollHeight - el.clientHeight > line * 0.5;
  });
}

/**
 * A rendered poster has a background, text, a QR code and colour tiles: dozens of distinct colours. An image with
 * (almost) a single colour means the capture failed (a known html-to-image failure when fonts or images were not ready).
 * Samples a grid of pixels so it stays cheap on a 1080x1920 canvas.
 */
export function isBlankImage(data: Uint8ClampedArray, width: number, height: number): boolean {
  const seen = new Set<number>();
  const stepX = Math.max(1, Math.floor(width / 64));
  const stepY = Math.max(1, Math.floor(height / 64));
  for (let y = 0; y < height; y += stepY) {
    for (let x = 0; x < width; x += stepX) {
      const i = (y * width + x) * 4;
      // quantise to 4 bits per channel (+ alpha) so anti-aliasing noise does not count as "different"
      seen.add(((data[i] >> 4) << 12) | ((data[i + 1] >> 4) << 8) | ((data[i + 2] >> 4) << 4) | (data[i + 3] >> 4));
      if (seen.size > 3) return false;
    }
  }
  return true;
}

/**
 * Makes sure the web fonts the poster uses (Geist, Noto Sans Sinhala / Tamil: loaded lazily per script by next/font) are
 * really loaded for the characters that are on it. Returns false when a face for that text could not be confirmed.
 */
export async function ensureFonts(node: HTMLElement): Promise<boolean> {
  const fonts = typeof document !== 'undefined' ? document.fonts : undefined;
  if (!fonts) return true;
  try {
    await fonts.ready;
    const family = getComputedStyle(node).fontFamily;
    const text = node.textContent ?? '';
    await Promise.all(['500', '700', '800'].map((weight) => fonts.load(`${weight} 40px ${family}`, text)));
    await fonts.ready;
    return fonts.check(`800 40px ${family}`, text) && fonts.check(`500 40px ${family}`, text);
  } catch {
    return false;
  }
}

const isSafari = () => typeof navigator !== 'undefined' && /^((?!chrome|android).)*safari/i.test(navigator.userAgent);

export interface PosterExport {
  blob: Blob;
  /** False when the Sinhala/Tamil (or Latin) font could not be confirmed, so the image should be checked by eye */
  fontsReady: boolean;
}

/**
 * Renders the poster node to a PNG of exactly width x height (pixelRatio 1, no matter the screen).
 * Fonts are awaited and embedded explicitly (fontEmbedCSS), and the result is rejected when it is blank.
 */
export async function exportPosterPng(node: HTMLElement, width: number, height: number): Promise<PosterExport> {
  const fontsReady = await ensureFonts(node);
  try {
    const fontEmbedCSS = await htmlToImage.getFontEmbedCSS(node);
    const options = {
      width,
      height,
      pixelRatio: 1,
      backgroundColor: POSTER.background,
      fontEmbedCSS,
      // the preview is scaled with CSS on a parent; the capture itself is always 1:1
      style: { transform: 'none', margin: '0' } as Partial<CSSStyleDeclaration>,
    };
    // Safari draws the SVG snapshot before inline resources are decoded on the first pass
    if (isSafari()) await htmlToImage.toCanvas(node, options);
    const canvas = await htmlToImage.toCanvas(node, options);

    const context = canvas.getContext('2d');
    if (!context) throw new PosterExportError('failed');
    const pixels = context.getImageData(0, 0, canvas.width, canvas.height);
    if (isBlankImage(pixels.data, canvas.width, canvas.height)) throw new PosterExportError('blank');

    const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/png'));
    if (!blob) throw new PosterExportError('failed');
    return { blob, fontsReady };
  } catch (err) {
    if (err instanceof PosterExportError) throw err;
    throw new PosterExportError('failed', err);
  }
}

/** Saves a blob through a temporary link. */
export function downloadBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 10_000);
}
