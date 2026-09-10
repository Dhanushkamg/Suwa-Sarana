import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { LanguageSwitcher } from '../LanguageSwitcher';
import { useI18n } from '@/lib/i18n';

// Mock the i18n hook
vi.mock('@/lib/i18n', () => ({
  useI18n: vi.fn(),
}));

describe('LanguageSwitcher Component', () => {
  it('renders correctly with default language', () => {
    (useI18n as any).mockReturnValue({
      locale: 'en',
      setLocale: vi.fn(),
    });

    render(<LanguageSwitcher />);
    
    // Switch language button should be present
    expect(screen.getByRole('button', { name: /switch language/i })).toBeInTheDocument();
    
    // Current language label should be English (in both button and dropdown)
    expect(screen.getAllByText(/English/i).length).toBeGreaterThan(0);
  });

  it('calls setLocale when a different language is clicked', () => {
    const setLocaleMock = vi.fn();
    (useI18n as any).mockReturnValue({
      locale: 'en',
      setLocale: setLocaleMock,
    });

    render(<LanguageSwitcher />);
    
    // Click on Sinhala
    const sinhalaButton = screen.getByText('සිංහල');
    fireEvent.click(sinhalaButton);
    
    // Verify setLocale was called with 'si'
    expect(setLocaleMock).toHaveBeenCalledWith('si');
  });

  it('highlights the currently selected language', () => {
    (useI18n as any).mockReturnValue({
      locale: 'ta',
      setLocale: vi.fn(),
    });

    render(<LanguageSwitcher />);
    
    // The active language 'தமிழ்' should be in the document
    expect(screen.getByText('தமிழ்')).toBeInTheDocument();
  });
});
