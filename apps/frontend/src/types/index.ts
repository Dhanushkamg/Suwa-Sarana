export interface User {
  id: number;
  email: string;
  phoneNumber: string;
  role: 'DONOR' | 'REQUESTER' | 'HOSPITAL_REQUESTER' | 'ADMIN';
  verificationStatus?: 'PENDING' | 'VERIFIED' | 'REJECTED';
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
  expiresAt: string;
}

export interface AuthState {
  accessToken: string | null;
  user: User | null;
  isAuthenticated: boolean;
  login: (token: string, user: User) => void;
  logout: () => Promise<void>;
  setAccessToken: (token: string) => void;
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
