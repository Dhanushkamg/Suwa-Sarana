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
