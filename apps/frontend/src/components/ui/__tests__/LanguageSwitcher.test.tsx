import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import '@testing-library/jest-dom/vitest';
import { LanguageSwitcher } from '../LanguageSwitcher';
import { useI18n } from '@/lib/i18n';

// Real English strings for the keys this component uses; everything else echoes the key
const MESSAGES: Record<string, string> = {
  'landing.switchLanguage': 'Switch language',
  'landing.languageMenu': 'Language',
};
const t = (key: string) => MESSAGES[key] ?? key;

// Mock the i18n hook
vi.mock('@/lib/i18n', () => ({
  useI18n: vi.fn(),
}));

describe('LanguageSwitcher Component', () => {
  it('renders correctly with default language', () => {
    vi.mocked(useI18n).mockReturnValue({
      locale: 'en',
      setLocale: vi.fn(),
      t: vi.fn(t),
      format: { number: String, date: String, dateTime: String },
    });

    render(<LanguageSwitcher />);
    
    // Switch language button should be present
    expect(screen.getByRole('button', { name: /switch language/i })).toBeInTheDocument();
    
    // Current language label should be English (in both button and dropdown)
    expect(screen.getAllByText(/English/i).length).toBeGreaterThan(0);
  });

  it('calls setLocale when a different language is clicked', () => {
    const setLocaleMock = vi.fn();
    vi.mocked(useI18n).mockReturnValue({
      locale: 'en',
      setLocale: setLocaleMock,
      t: vi.fn(t),
      format: { number: String, date: String, dateTime: String },
    });

    render(<LanguageSwitcher />);
    
    // Click on Sinhala
    const sinhalaButton = screen.getByText('සිංහල');
    fireEvent.click(sinhalaButton);
    
    // Verify setLocale was called with 'si'
    expect(setLocaleMock).toHaveBeenCalledWith('si');
  });

  it('highlights the currently selected language', () => {
    vi.mocked(useI18n).mockReturnValue({
      locale: 'ta',
      setLocale: vi.fn(),
      t: vi.fn(t),
      format: { number: String, date: String, dateTime: String },
    });

    render(<LanguageSwitcher />);
    
    // The active language 'தமிழ்' should be in the document
    // Shown on the toggle button and as a menu option
    expect(screen.getAllByText('தமிழ்').length).toBeGreaterThan(0);
    expect(screen.getByRole('menuitemradio', { name: 'தமிழ்' })).toHaveAttribute('aria-checked', 'true');
  });
});
