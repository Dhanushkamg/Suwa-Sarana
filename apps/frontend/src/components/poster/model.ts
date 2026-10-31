import { formatterIn, translateIn } from '@/lib/i18n';
import { TIME_FORMAT, toDate, toTimeOfDay } from '@/components/pub/dates';
import type { PosterFacts, PosterLanguage } from './types';

/** Facts turned into the strings a poster shows, formatted for the poster's language (not the app's). */
export interface PosterModel {
  language: PosterLanguage;
  bloodType?: string;
  bloodTypes: string[];
  units?: number;
  unitsText?: string;
  hospital?: string;
  district?: string;
  /** "Hospital, District" */
  place?: string;
  campName?: string;
  organiser?: string;
  venue?: string;
  /** "Venue, District" */
  venueText?: string;
  contact?: string;
  day?: string;
  monthShort?: string;
  weekday?: string;
  dateLong?: string;
  timeText?: string;
  /** "14 November 2026, 9:00 AM – 1:00 PM" */
  when?: string;
}

const clean = (v: string | undefined): string | undefined => {
  const s = v?.replace(/\s+/g, ' ').trim();
  return s ? s : undefined;
};

export function buildPosterModel(facts: PosterFacts, language: PosterLanguage): PosterModel {
  const format = formatterIn(language);
  const hospital = clean(facts.hospital);
  const district = clean(facts.district);
  const venue = clean(facts.venue);
  // An e-mail address is never printed on a poster (older camp records stored the organiser's e-mail as the name)
  const organiserRaw = clean(facts.organiser);
  const organiser = organiserRaw && !organiserRaw.includes('@') ? organiserRaw : undefined;

  const date = toDate(facts.date);
  const start = toTimeOfDay(facts.startTime);
  const end = toTimeOfDay(facts.endTime);
  const startText = start ? format.dateTime(start, TIME_FORMAT) : undefined;
  const endText = end ? format.dateTime(end, TIME_FORMAT) : undefined;
  const timeText = startText && endText ? `${startText} – ${endText}` : startText;
  const dateLong = date ? format.date(date, { dateStyle: 'long' }) : undefined;

  const units = typeof facts.units === 'number' && facts.units > 0 ? facts.units : undefined;
  const bloodType = clean(facts.bloodType);
  const bloodTypes = (facts.bloodTypes ?? (bloodType ? [bloodType] : [])).map((b) => b.trim()).filter(Boolean);

  return {
    language,
    bloodType,
    bloodTypes,
    units,
    unitsText: units !== undefined ? translateIn(language, 'poster.unitsHero', { count: units }) : undefined,
    hospital,
    district,
    place: [hospital, district].filter(Boolean).join(', ') || undefined,
    campName: clean(facts.campName),
    organiser,
    venue,
    venueText: [venue, district].filter(Boolean).join(', ') || undefined,
    contact: clean(facts.contactPhone),
    day: date ? format.date(date, { day: 'numeric' }) : undefined,
    monthShort: date ? format.date(date, { month: 'short' }) : undefined,
    weekday: date ? format.date(date, { weekday: 'long' }) : undefined,
    dateLong,
    timeText,
    when: [dateLong, timeText].filter(Boolean).join(', ') || undefined,
  };
}
