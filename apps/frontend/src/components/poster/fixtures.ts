import type { PosterFacts, PosterLanguage, PosterTemplate } from './types';

/**
 * Fixed sample data for the dev-only poster preview route and the tests: the same input always renders the same poster,
 * so screenshots can be compared. Names are fixture data in the poster's language (hospital and camp names are not translated by the app).
 */
export const FIXTURE_SHARE_URL = 'https://suwasarana.lk/share/fixture-token';

const NAMES = {
  en: {
    hospital: 'National Hospital of Sri Lanka, Colombo',
    hospitalLong: 'Teaching Hospital Anuradhapura, Department of Transfusion Medicine, Sixth Floor Blood Bank',
    district: 'Colombo',
    camp: 'Community Blood Drive',
    campLong: 'Annual Community Blood Donation Drive of the Colombo Youth Service Club in Memory of Our Founders',
    venue: 'Town Hall Auditorium, Viharamahadevi Park',
    organiser: 'Lions Club of Colombo',
  },
  si: {
    hospital: 'ශ්‍රී ලංකා ජාතික රෝහල, කොළඹ',
    hospitalLong: 'අනුරාධපුර ශික්ෂණ රෝහල, රුධිර පාරවිලයන වෛද්‍ය අංශය, හයවන මහල රුධිර බැංකුව',
    district: 'කොළඹ',
    camp: 'ප්‍රජා රුධිර දන් දීමේ කඳවුර',
    campLong: 'කොළඹ තරුණ සේවා සමාජයේ අපගේ ආරම්භකයන්ගේ ස්මරණය පිණිස සංවිධානය කරන වාර්ෂික රුධිර දන් දීමේ වැඩසටහන',
    venue: 'නගර ශාලා ශ්‍රවණාගාරය, විහාරමහාදේවී උද්‍යානය',
    organiser: 'කොළඹ ලයන්ස් සමාජය',
  },
  ta: {
    hospital: 'இலங்கை தேசிய மருத்துவமனை, கொழும்பு',
    hospitalLong: 'அனுராதபுரம் போதனா மருத்துவமனை, இரத்தமாற்று மருத்துவப் பிரிவு, ஆறாவது மாடி இரத்த வங்கி',
    district: 'கொழும்பு',
    camp: 'சமூக இரத்த தான முகாம்',
    campLong: 'எங்கள் நிறுவனர்களின் நினைவாக கொழும்பு இளைஞர் சேவை மன்றம் நடத்தும் ஆண்டுதோறும் இரத்த தான முகாம்',
    venue: 'நகர மண்டப அரங்கம், விகாரமகாதேவி பூங்கா',
    organiser: 'கொழும்பு லயன்ஸ் கிளப்',
  },
} as const;

export function fixtureFacts(template: PosterTemplate, language: PosterLanguage, long = false): PosterFacts {
  const n = NAMES[language];
  switch (template) {
    case 'URGENT_REQUEST':
      return {
        bloodType: 'O-',
        units: 3,
        hospital: long ? n.hospitalLong : n.hospital,
        district: n.district,
        contactPhone: '+94 77 123 4567',
      };
    case 'CAMP':
      return {
        campName: long ? n.campLong : n.camp,
        venue: n.venue,
        district: n.district,
        date: '2026-12-12',
        startTime: '09:00',
        endTime: '15:00',
        bloodTypes: long ? ['O+', 'O-', 'A+', 'A-', 'B+', 'B-', 'AB+', 'AB-'] : ['O+', 'O-', 'A+', 'B+'],
        organiser: n.organiser,
      };
    case 'THANK_YOU':
      return { campName: long ? n.campLong : n.camp, district: n.district, date: '2026-12-12', venue: n.venue };
    default:
      return {};
  }
}
