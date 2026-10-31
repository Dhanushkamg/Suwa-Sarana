'use client';

import { useCallback, useEffect, useId, useMemo, useRef, useState } from 'react';
import { Copy, Download, MessageCircle, Send, Share2, Smartphone, TriangleAlert, ShieldAlert } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Checkbox } from '@/components/ui/Checkbox';
import { Dialog } from '@/components/ui/Dialog';
import { FormField } from '@/components/ui/FormField';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { Tabs } from '@/components/ui/Tabs';
import { useToast } from '@/components/ui/Toast';
import { Alert } from '@/components/pub/Alert';
import { Textarea } from '@/components/pub/Textarea';
import { requestPosterCopy, PosterCopyError } from '@/lib/api/poster';
import { useI18n } from '@/lib/i18n';
import { useAuthStore } from '@/store/authStore';
import { captionForShare, fallbackCopy, parseHashtags, validateCopy } from './copy';
import { detectOverflow, downloadBlob, ensureFonts, exportPosterPng, PosterExportError } from './exportPoster';
import { buildPosterModel } from './model';
import { PosterCanvas } from './PosterCanvas';
import { POSTER_SIZES, posterSize } from './sizes';
import { shareChannels } from './shareLinks';
import { COPY_LIMITS, POSTER_LANGUAGES, POSTER_TEMPLATES, type PosterCopy, type PosterFacts, type PosterLanguage, type PosterSizeId, type PosterTemplate } from './types';

export interface PosterDialogProps {
  open?: boolean;
  onClose: () => void;
  /** Facts from the system (request, camp). There is intentionally no way to type patient details. */
  facts: PosterFacts;
  /** What the QR code and the share links point to: the public request share page, or the camp page */
  shareUrl: string;
  initialTemplate: PosterTemplate;
  /** Templates offered for this source. Default: all four. */
  templates?: PosterTemplate[];
  /** The requester's own number; only printed when they tick the box */
  contactPhone?: string;
  /** Overrides the account's verification (tests). Default: the signed-in user's `verificationStatus === 'VERIFIED'`. */
  verified?: boolean;
}

interface CopyEdits {
  headline?: string;
  body?: string;
  caption?: string;
  hashtags?: string;
}

type AiState = { status: 'loading' } | { status: 'done'; copy: PosterCopy } | { status: 'none' };

const MAX_PREVIEW_HEIGHT_RATIO = 0.6;

export function PosterDialog({ open = true, onClose, facts, shareUrl, initialTemplate, templates = POSTER_TEMPLATES, contactPhone, verified }: PosterDialogProps) {
  const { t, locale } = useI18n();
  const toast = useToast();
  const sizeSelectId = useId();
  const accountStatus = useAuthStore((s) => s.user?.verificationStatus);
  const isVerified = verified ?? accountStatus === 'VERIFIED';

  const [template, setTemplate] = useState<PosterTemplate>(templates.includes(initialTemplate) ? initialTemplate : templates[0]);
  const [sizeId, setSizeId] = useState<PosterSizeId>('1080x1080');
  const [language, setLanguage] = useState<PosterLanguage>(locale);
  const [showContact, setShowContact] = useState(false);
  const [edits, setEdits] = useState<Record<string, CopyEdits>>({});
  const [ai, setAi] = useState<Record<string, AiState>>({});
  const [backendBlocked, setBackendBlocked] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [exportError, setExportError] = useState<string | null>(null);
  const [overflowing, setOverflowing] = useState(false);

  const size = posterSize(sizeId);
  const key = `${template}:${language}`;
  const posterRef = useRef<HTMLDivElement>(null);
  const requested = useRef(new Set<string>());

  const printedFacts = useMemo<PosterFacts>(() => ({ ...facts, contactPhone: showContact ? contactPhone : undefined }), [facts, showContact, contactPhone]);
  const model = useMemo(() => buildPosterModel(printedFacts, language), [printedFacts, language]);
  const standard = useMemo(() => fallbackCopy(template, model), [template, model]);

  // --- Copy: AI suggestion when the backend has one, standard wording otherwise; the organiser can edit everything ---
  const aiState = ai[key];
  const aiCopy = aiState?.status === 'done' ? aiState.copy : null;
  const base = aiCopy ?? standard;
  const edit = edits[key] ?? {};
  const edited = Object.keys(edit).length > 0;
  const copy: PosterCopy = {
    headline: edit.headline ?? base.headline,
    body: edit.body ?? base.body,
    caption: edit.caption ?? base.caption,
    hashtags: edit.hashtags !== undefined ? parseHashtags(edit.hashtags) : base.hashtags,
  };
  const hashtagsText = edit.hashtags ?? base.hashtags.join(' ');

  useEffect(() => {
    if (!open || requested.current.has(key)) return;
    requested.current.add(key);
    setAi((cur) => ({ ...cur, [key]: { status: 'loading' } }));
    requestPosterCopy({ template, language, facts: printedFacts })
      .then((res) => {
        const valid = validateCopy(res, model);
        setAi((cur) => ({ ...cur, [key]: valid ? { status: 'done', copy: valid } : { status: 'none' } }));
      })
      .catch((err: unknown) => {
        if (err instanceof PosterCopyError && err.kind === 'unverified') setBackendBlocked(true);
        setAi((cur) => ({ ...cur, [key]: { status: 'none' } }));
      });
    // The request is made once per template+language; later edits of the contact checkbox do not trigger another one
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, key]);

  const setField = (field: keyof CopyEdits, value: string) => setEdits((cur) => ({ ...cur, [key]: { ...cur[key], [field]: value } }));
  const resetCopy = () =>
    setEdits((cur) => ({ ...cur, [key]: { headline: standard.headline, body: standard.body, caption: standard.caption, hashtags: standard.hashtags.join(' ') } }));

  // --- Guardrails ---
  const blocked = template === 'URGENT_REQUEST' && (!isVerified || backendBlocked);
  const unverified = !isVerified;
  const canExport = !blocked && copy.headline.trim().length > 0 && !exporting;

  // --- Text that does not fit (checked once fonts are in, since Sinhala/Tamil change the line breaks) ---
  const overflowKey = `${template}|${sizeId}|${language}|${copy.headline}|${copy.body}|${showContact}`;
  useEffect(() => {
    if (!open) return;
    let cancelled = false;
    const node = posterRef.current;
    if (!node) return;
    ensureFonts(node).then(() => {
      if (!cancelled) setOverflowing(detectOverflow(node));
    });
    return () => {
      cancelled = true;
    };
  }, [open, overflowKey]);

  // --- Preview scaled to fit its column (never clipped) ---
  const frameRef = useRef<HTMLDivElement>(null);
  const [scale, setScale] = useState(0.3);
  useEffect(() => {
    const el = frameRef.current;
    if (!el || !open) return;
    const update = () => {
      const maxHeight = Math.max(260, window.innerHeight * MAX_PREVIEW_HEIGHT_RATIO);
      setScale(Math.max(0.05, Math.min(el.clientWidth / size.width, maxHeight / size.height)));
    };
    update();
    if (typeof ResizeObserver === 'undefined') return;
    const observer = new ResizeObserver(update);
    observer.observe(el);
    return () => observer.disconnect();
  }, [open, size.width, size.height]);

  // --- Actions ---
  const filename = `suwa-sarana-${template.toLowerCase().replace(/_/g, '-')}-${size.id}-${language}.png`;

  const exportImage = useCallback(async () => {
    const node = posterRef.current;
    if (!node) throw new PosterExportError('failed');
    return exportPosterPng(node, size.width, size.height);
  }, [size.width, size.height]);

  const failureMessage = (err: unknown) => (err instanceof PosterExportError && err.kind === 'blank' ? t('poster.exportBlank') : t('poster.exportFailed'));

  const download = async () => {
    if (!canExport) return;
    setExporting(true);
    setExportError(null);
    try {
      const { blob, fontsReady } = await exportImage();
      downloadBlob(blob, filename);
      toast.show(t('poster.downloaded'), 'success');
      if (!fontsReady && language !== 'en') toast.show(t('poster.fontWarning'), 'info');
    } catch (err) {
      const message = failureMessage(err);
      setExportError(message);
      toast.show(message, 'error');
    } finally {
      setExporting(false);
    }
  };

  const captionText = captionForShare(copy, shareUrl);
  const copyCaption = async () => {
    try {
      await navigator.clipboard.writeText(captionText);
      toast.show(t('poster.captionCopied'), 'success');
    } catch {
      toast.show(t('poster.copyFailed'), 'error');
    }
  };

  const canNativeShare = typeof navigator !== 'undefined' && typeof navigator.share === 'function';
  const nativeShare = async () => {
    if (!canExport) return;
    setExporting(true);
    try {
      const { blob } = await exportImage();
      const file = new File([blob], filename, { type: 'image/png' });
      const data: ShareData = { title: copy.headline, text: copy.caption, url: shareUrl };
      if (navigator.canShare?.({ files: [file] })) data.files = [file];
      await navigator.share(data);
    } catch (err) {
      // The person closing the share sheet is not an error
      if ((err as { name?: string })?.name !== 'AbortError') toast.show(failureMessage(err), 'error');
    } finally {
      setExporting(false);
    }
  };

  const channels = shareChannels(copy.caption, shareUrl);
  const channelIcons = { whatsapp: MessageCircle, facebook: Share2, telegram: Send, viber: Smartphone } as const;

  const tabs = templates.map((id) => ({ id, label: t(`poster.template.${id}`) }));

  return (
    <Dialog open={open} onClose={onClose} title={t('poster.title')} description={t('poster.description')} className="w-[min(96vw,76rem)]">
      <div className="grid gap-8 lg:grid-cols-[minmax(0,1fr)_23rem]">
        {/* Preview + actions */}
        <div className="min-w-0 space-y-4">
          <div
            ref={frameRef}
            role="img"
            aria-label={t('poster.previewLabel', { headline: copy.headline })}
            className="mx-auto w-full overflow-hidden rounded-xl border border-line bg-surface-raised"
            style={{ height: size.height * scale }}
          >
            <div style={{ width: size.width * scale, height: size.height * scale, margin: '0 auto', position: 'relative' }}>
              <div style={{ position: 'absolute', top: 0, left: 0, width: size.width, height: size.height, transform: `scale(${scale})`, transformOrigin: 'top left' }}>
                <div ref={posterRef} style={{ width: size.width, height: size.height }}>
                  <PosterCanvas
                    template={template}
                    size={size}
                    language={language}
                    model={model}
                    headline={copy.headline}
                    body={copy.body}
                    qrUrl={shareUrl}
                    unverified={unverified}
                  />
                </div>
              </div>
            </div>
          </div>

          <div className="flex flex-wrap gap-3">
            <Button size="lg" onClick={download} loading={exporting} disabled={!canExport} className="min-w-0 flex-1 basis-48">
              {!exporting && <Download className="h-5 w-5" aria-hidden="true" />}
              {exporting ? t('poster.downloading') : t('poster.download')}
            </Button>
            <Button size="lg" variant="secondary" onClick={copyCaption} className="min-w-0 flex-1 basis-48">
              <Copy className="h-5 w-5" aria-hidden="true" />
              {t('poster.copyCaption')}
            </Button>
          </div>
          {exportError && <Alert>{exportError}</Alert>}

          {!blocked && (
            <section aria-labelledby={`${sizeSelectId}-share`} className="space-y-3 rounded-2xl border border-line bg-surface-raised p-4">
              <h3 id={`${sizeSelectId}-share`} className="text-sm font-semibold text-foreground">
                {t('poster.share.heading')}
              </h3>
              <p className="text-xs leading-relaxed text-muted">{t('poster.share.help')}</p>
              <div className="flex flex-wrap gap-2">
                {channels.map((c) => {
                  const Icon = channelIcons[c.id];
                  return (
                    <a
                      key={c.id}
                      href={c.href}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="inline-flex min-h-11 items-center gap-2 rounded-full border border-line-strong bg-surface px-4 text-sm font-medium text-foreground transition-colors hover:border-primary hover:bg-primary-soft"
                    >
                      <Icon className="h-4 w-4 text-primary-text" aria-hidden="true" />
                      {c.name}
                      <span className="sr-only">{t('pub.opensNewTab')}</span>
                    </a>
                  );
                })}
                {canNativeShare && (
                  <button
                    type="button"
                    onClick={nativeShare}
                    disabled={!canExport}
                    className="inline-flex min-h-11 items-center gap-2 rounded-full border border-line-strong bg-surface px-4 text-sm font-medium text-foreground transition-colors hover:border-primary hover:bg-primary-soft disabled:opacity-50"
                  >
                    <Share2 className="h-4 w-4 text-primary-text" aria-hidden="true" />
                    {t('poster.share.native')}
                  </button>
                )}
              </div>
            </section>
          )}
        </div>

        {/* Settings and wording */}
        <div className="min-w-0 space-y-5">
          <Tabs tabs={tabs} value={template} onChange={(id) => setTemplate(id as PosterTemplate)} label={t('poster.template.label')} />

          <div role="tabpanel" aria-labelledby={`tab-${template}`} className="space-y-5">
            {blocked && (
              <div role="alert" className="flex items-start gap-3 rounded-xl border border-danger/30 bg-danger-soft px-4 py-3 text-sm leading-relaxed text-foreground">
                <ShieldAlert className="mt-0.5 h-5 w-5 shrink-0 text-danger-text" aria-hidden="true" />
                <div>
                  <p className="font-semibold">{t('poster.blocked.title')}</p>
                  <p className="mt-1">{t('poster.blocked.desc')}</p>
                </div>
              </div>
            )}
            {!blocked && unverified && (
              <div role="status" className="flex items-start gap-3 rounded-xl border border-warning/30 bg-warning-soft px-4 py-3 text-sm leading-relaxed text-foreground">
                <TriangleAlert className="mt-0.5 h-5 w-5 shrink-0 text-warning-text" aria-hidden="true" />
                <p>{t('poster.unverifiedNote')}</p>
              </div>
            )}
            {overflowing && (
              <div role="status" className="flex items-start gap-3 rounded-xl border border-warning/30 bg-warning-soft px-4 py-3 text-sm leading-relaxed text-foreground">
                <TriangleAlert className="mt-0.5 h-5 w-5 shrink-0 text-warning-text" aria-hidden="true" />
                <p>{t('poster.overflow')}</p>
              </div>
            )}

            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-1 xl:grid-cols-2">
              <Select
                label={t('poster.size.label')}
                value={sizeId}
                onChange={(e) => setSizeId(e.target.value as PosterSizeId)}
                options={POSTER_SIZES.map((s) => ({ value: s.id, label: t(`poster.size.${s.id}`) }))}
              />
              <Select
                label={t('poster.language.label')}
                value={language}
                onChange={(e) => setLanguage(e.target.value as PosterLanguage)}
                options={POSTER_LANGUAGES.map((l) => ({ value: l, label: t(`poster.language.${l}`) }))}
              />
            </div>

            <div className="space-y-4">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <h3 className="text-sm font-semibold text-foreground">{t('poster.wording')}</h3>
                <button
                  type="button"
                  onClick={resetCopy}
                  className="inline-flex min-h-11 items-center px-2 text-xs font-medium text-primary-text underline-offset-2 hover:underline"
                >
                  {t('poster.resetCopy')}
                </button>
              </div>
              <p role="status" className="text-xs leading-relaxed text-muted">
                {aiState?.status === 'loading' ? t('poster.copySource.loading') : aiCopy && !edited ? t('poster.copySource.ai') : t('poster.copySource.standard')}
              </p>

              <FormField label={t('poster.field.headline')} help={t('poster.field.count', { count: copy.headline.length, max: COPY_LIMITS.headline })}>
                {(p) => <Input {...p} value={copy.headline} maxLength={COPY_LIMITS.headline} onChange={(e) => setField('headline', e.target.value)} />}
              </FormField>
              <FormField label={t('poster.field.body')} help={t('poster.field.count', { count: copy.body.length, max: COPY_LIMITS.body })}>
                {(p) => <Textarea {...p} rows={3} value={copy.body} maxLength={COPY_LIMITS.body} onChange={(e) => setField('body', e.target.value)} />}
              </FormField>
              <FormField label={t('poster.field.caption')} help={t('poster.field.count', { count: copy.caption.length, max: COPY_LIMITS.caption })}>
                {(p) => <Textarea {...p} rows={5} value={copy.caption} maxLength={COPY_LIMITS.caption} onChange={(e) => setField('caption', e.target.value)} />}
              </FormField>
              <FormField label={t('poster.field.hashtags')} help={t('poster.field.hashtagsHelp', { max: COPY_LIMITS.hashtags })}>
                {(p) => <Input {...p} value={hashtagsText} onChange={(e) => setField('hashtags', e.target.value)} dir="auto" />}
              </FormField>
            </div>

            {contactPhone && (template === 'URGENT_REQUEST' || template === 'CAMP') && (
              <div className="rounded-xl border border-line p-3">
                <Checkbox checked={showContact} onChange={(e) => setShowContact(e.target.checked)} label={t('poster.contact.label')} />
                <p className="ps-8 text-xs leading-relaxed text-muted">{t('poster.contact.help', { phone: contactPhone })}</p>
              </div>
            )}
          </div>
        </div>
      </div>
    </Dialog>
  );
}
