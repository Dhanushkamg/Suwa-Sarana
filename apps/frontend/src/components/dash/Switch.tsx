'use client';

import type { ReactNode } from 'react';
import { Switch } from '@/components/ui/Switch';
import { IconTile } from './IconTile';

/** A setting line: flat icon tile, then the shared accessible switch (title, description, toggle). */
export function SettingRow({
  id,
  icon,
  title,
  description,
  checked,
  onChange,
  disabled,
}: {
  id: string;
  icon: ReactNode;
  title: string;
  description?: string;
  checked: boolean;
  onChange: (checked: boolean) => void;
  disabled?: boolean;
}) {
  return (
    <div className="flex items-start gap-3 py-4">
      <IconTile size="sm" tone={checked ? 'primary' : 'muted'}>
        {icon}
      </IconTile>
      <div className="min-w-0 flex-1">
        <Switch id={id} label={title} description={description} checked={checked} onChange={onChange} disabled={disabled} />
      </div>
    </div>
  );
}
