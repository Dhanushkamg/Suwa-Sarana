export interface User {
  id: number;
  email: string;
  phoneNumber: string;
  role: 'DONOR' | 'REQUESTER' | 'HOSPITAL_REQUESTER' | 'BLOOD_BANK_REQUESTER' | 'ADMIN';
  verificationStatus?: 'PENDING' | 'VERIFIED' | 'REJECTED';
  firstName?: string;
  lastName?: string;
  /** False until the e-mail link is opened; institutions cannot publish before that (403 email.verificationRequired). */
  emailVerified?: boolean;
  /** 'en' | 'si' | 'ta': the language the server uses for SMS and e-mail. */
  preferredLanguage?: string;
}

export interface DonorProfile {
  id: number;
  bloodType: string;
  available: boolean;
  district: string;
  reliabilityScore: number;
}

export interface BloodRequest {
  id: number;
  patientBloodType: string;
  unitsNeeded?: number;
  urgency: 'ROUTINE' | 'URGENT' | 'CRITICAL';
  status: 'OPEN' | 'ESCALATING' | 'MATCHED' | 'FULFILLED' | 'CANCELLED' | 'EXPIRED';
  hospitalName: string;
  district: string;
  latitude?: number;
  longitude?: number;
  currentRadiusKm: number;
  /** ISO instant with Z. */
  createdAt?: string;
  expiresAt: string;
  /** When the request was fulfilled, cancelled or expired (ISO instant); null while it is live. */
  closedAt?: string | null;
  /** CloseReason set by the owner: NO_LONGER_NEEDED | FOUND_DONOR_ELSEWHERE | OTHER. */
  closeReason?: string | null;
  /** Donors who said yes. null for everybody except the owner and admins. */
  acceptedCount?: number | null;
  /** Donations the owner confirmed. null for everybody except the owner and admins. */
  confirmedDonations?: number | null;
  /** True when latitude/longitude were rounded (about 1 km) because the viewer does not own the request. */
  coarseLocation?: boolean;
}

export interface AuthState {
  accessToken: string | null;
  user: User | null;
  isAuthenticated: boolean;
  /** True once the first attempt to restore a session (after a page load) has finished. */
  isInitialized: boolean;
  initialize: () => Promise<void>;
  login: (token: string, user: User) => void;
  logout: () => Promise<void>;
  setAccessToken: (token: string) => void;
  /** Merge changed profile fields (name, language, e-mail verified ...) into the signed-in user. */
  updateUser: (patch: Partial<User>) => void;
}

export interface DonationSlot {
  id: number;
  hostName: string;
  startTime: string;   // ISO datetime string from backend LocalDateTime
  endTime: string;
  capacity: number;
  bookedCount: number;
  spotsLeft: number;
  status: 'OPEN' | 'FULL' | 'CANCELLED' | 'COMPLETED';
}

export interface SlotBooking {
  id: number;
  slotId: number;
  donorId: number;
  status: 'BOOKED' | 'CHECKED_IN' | 'NO_SHOW' | 'CANCELLED' | 'COMPLETED';
  bookedAt: string;    // ISO instant string
  checkedInAt?: string;
  slot: DonationSlot;  // embedded slot summary from SlotBookingDto
}
