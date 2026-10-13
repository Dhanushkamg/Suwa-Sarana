import { LogoMark } from '@/components/brand/Logo';

export default function Loading() {
  return (
    <div className="flex min-h-[60vh] items-center justify-center" role="status" aria-label="Loading">
      <div className="relative flex h-14 w-14 items-center justify-center">
        <span aria-hidden="true" className="dot-ping absolute inset-1 rounded-full bg-ember/40" />
        <LogoMark className="relative h-8 w-8 animate-float" />
      </div>
    </div>
  );
}
