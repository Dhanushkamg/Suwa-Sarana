/** Sri Lanka's 25 districts with an approximate centre point, used when the exact location is unknown. */
export const DISTRICT_COORDS: Record<string, [number, number]> = {
  Colombo: [6.9271, 79.8612],
  Gampaha: [7.084, 80.0098],
  Kalutara: [6.5854, 79.9607],
  Kandy: [7.2906, 80.6337],
  Matale: [7.4675, 80.6234],
  'Nuwara Eliya': [6.9497, 80.7891],
  Galle: [6.0535, 80.221],
  Matara: [5.9549, 80.555],
  Hambantota: [6.1429, 81.1212],
  Jaffna: [9.6615, 80.0255],
  Kilinochchi: [9.3803, 80.377],
  Mannar: [8.981, 79.9044],
  Vavuniya: [8.7542, 80.4982],
  Mullaitivu: [9.2671, 80.8143],
  Batticaloa: [7.731, 81.6747],
  Ampara: [7.2912, 81.6724],
  Trincomalee: [8.5874, 81.2152],
  Kurunegala: [7.4863, 80.3623],
  Puttalam: [8.0408, 79.8394],
  Anuradhapura: [8.3114, 80.4037],
  Polonnaruwa: [7.9403, 81.0188],
  Badulla: [6.9934, 81.055],
  Monaragala: [6.8728, 81.3507],
  Ratnapura: [6.6828, 80.3992],
  Kegalle: [7.2513, 80.3464],
};

export const DISTRICTS = Object.keys(DISTRICT_COORDS);

/** Centre of a district, falling back to Colombo for unknown names. */
export function districtCenter(district: string): [number, number] {
  return DISTRICT_COORDS[district] ?? DISTRICT_COORDS.Colombo;
}
