import { describe, expect, it } from 'vitest';
import { toBackendFacts } from '@/lib/api/poster';
import { captionForShare, fallbackCopy, parseHashtags, validateCopy } from '../copy';
import { fixtureFacts } from '../fixtures';
import { buildPosterModel } from '../model';
import { POSTER_LANGUAGES, POSTER_TEMPLATES, COPY_LIMITS } from '../types';

describe('fallbackCopy', () => {
  for (const language of POSTER_LANGUAGES) {
    for (const template of POSTER_TEMPLATES) {
      it(`${template} / ${language}: complete, within the limits, no unresolved keys or placeholders`, () => {
        const copy = fallbackCopy(template, buildPosterModel(fixtureFacts(template, language), language));
        for (const text of [copy.headline, copy.body, copy.caption]) {
          expect(text.trim()).not.toBe('');
          expect(text).not.toMatch(/poster\.|\{|\}/);
        }
        expect(copy.headline.length).toBeLessThanOrEqual(COPY_LIMITS.headline);
        expect(copy.body.length).toBeLessThanOrEqual(COPY_LIMITS.body);
        expect(copy.caption.length).toBeLessThanOrEqual(COPY_LIMITS.caption);
        expect(copy.hashtags.length).toBeGreaterThanOrEqual(3);
      });
    }
  }

  it('fills the urgent headline from the request, never from free text', () => {
    const copy = fallbackCopy('URGENT_REQUEST', buildPosterModel(fixtureFacts('URGENT_REQUEST', 'en'), 'en'));
    expect(copy.headline).toBe('3 units of O- blood needed');
    expect(copy.body).toContain('National Hospital of Sri Lanka, Colombo, Colombo');
  });

  it('handles an unknown unit count and singular units', () => {
    const noUnits = fallbackCopy('URGENT_REQUEST', buildPosterModel({ bloodType: 'A+', hospital: 'H', district: 'D' }, 'en'));
    expect(noUnits.headline).toBe('A+ blood needed');
    const one = fallbackCopy('URGENT_REQUEST', buildPosterModel({ bloodType: 'A+', units: 1, hospital: 'H', district: 'D' }, 'en'));
    expect(one.headline).toBe('1 unit of A+ blood needed');
  });

  it('uses no scare language in any standard wording', () => {
    const scary = /\b(dying|die|death|deadly|life or death|critical condition|desperate|tragedy|hurry)\b/i;
    for (const template of POSTER_TEMPLATES) {
      const copy = fallbackCopy(template, buildPosterModel(fixtureFacts(template, 'en'), 'en'));
      expect(`${copy.headline} ${copy.body} ${copy.caption}`).not.toMatch(scary);
    }
  });

  it('never prints an e-mail address as the organiser', () => {
    const model = buildPosterModel({ organiser: 'someone@example.org', campName: 'Camp' }, 'en');
    expect(model.organiser).toBeUndefined();
  });
});

describe('validateCopy', () => {
  const model = buildPosterModel(fixtureFacts('URGENT_REQUEST', 'en'), 'en'); // O-, 3 units, contact +94 77 123 4567
  const good = { headline: 'O- blood needed', body: 'Please help at the hospital.', caption: 'Please share this post.', hashtags: ['#SuwaSarana', 'BloodDonation', 'not a tag'] };

  it('accepts clean copy and normalises hashtags', () => {
    const copy = validateCopy(good, model);
    expect(copy).not.toBeNull();
    expect(copy?.hashtags).toEqual(['#SuwaSarana', '#BloodDonation']); // 'not a tag' is not one tag, so it is dropped
  });

  it.each([
    ['missing field', { ...good, body: '' }],
    ['too long', { ...good, headline: 'x'.repeat(COPY_LIMITS.headline + 1) }],
    ['a link', { ...good, caption: 'See https://evil.example now' }],
    ['an invented phone number', { ...good, body: 'Call 077 999 8888 now' }],
    ['a different blood type', { ...good, headline: 'A+ blood needed' }],
    ['a different unit count', { ...good, headline: '10 units needed' }],
    ['not an object', 'text'],
  ])('rejects copy with %s', (_name, value) => {
    expect(validateCopy(value, model)).toBeNull();
  });

  it('allows the supplied contact number and the real unit count', () => {
    expect(validateCopy({ ...good, body: 'Call +94 77 123 4567. 3 units of O- needed.' }, model)).not.toBeNull();
  });
});

describe('hashtags and caption', () => {
  it('keeps Sinhala and Tamil tags, drops junk, caps the count', () => {
    expect(parseHashtags('#රුධිරදානය #இரத்ததானம் #a!b')).toEqual(['#රුධිරදානය', '#இரத்ததானம்']);
    expect(parseHashtags(Array.from({ length: 20 }, (_, i) => `t${i}`))).toHaveLength(COPY_LIMITS.hashtags);
  });

  it('puts the real link into the shared caption', () => {
    expect(captionForShare({ caption: 'Hello', hashtags: ['#a'] }, 'https://x.lk/share/t')).toBe('Hello\n\nhttps://x.lk/share/t\n\n#a');
  });
});

describe('toBackendFacts', () => {
  it('sends only supplied system facts in the contract shape', () => {
    const facts = toBackendFacts({ ...fixtureFacts('CAMP', 'en'), organiser: 'a@b.lk' });
    expect(facts).toMatchObject({ campName: 'Community Blood Drive', date: '2026-12-12', time: '09:00' });
    // several blood types are not one valid bloodType for the server: omitted (the artwork still lists them)
    expect(facts).not.toHaveProperty('bloodType');
    expect(facts).not.toHaveProperty('organiser'); // e-mail addresses are never sent
    expect(facts).not.toHaveProperty('units');
  });
});
