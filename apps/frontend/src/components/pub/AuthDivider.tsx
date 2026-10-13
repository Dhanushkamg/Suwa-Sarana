/** Hairline rule with a small mono label in the middle ("or"). */
export function AuthDivider({ label }: { label: string }) {
  return (
    <div className="my-8 flex items-center gap-4" role="separator" aria-label={label}>
      <span aria-hidden="true" className="h-px flex-1 bg-line-strong" />
      <span aria-hidden="true" className="eyebrow">
        {label}
      </span>
      <span aria-hidden="true" className="h-px flex-1 bg-line-strong" />
    </div>
  );
}
