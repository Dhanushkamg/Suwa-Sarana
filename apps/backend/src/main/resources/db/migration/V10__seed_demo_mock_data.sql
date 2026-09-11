-- V10__seed_demo_mock_data.sql
-- Comprehensive mock data seed for manual testing across all user roles and workflows

-- 1. Seed Demo User Accounts (Password for all: 'password123')
INSERT INTO users (email, phone_number, password_hash, role, verification_status) VALUES
  ('admin@test.com', '0777654321', '$2a$12$3BvaI6MkH8E3t3jPGyPlFOoCmkGoxsq6h2uag/KhZpbVxxBe8DmfS', 'ADMIN', 'VERIFIED'),
  ('hospital@test.com', '0771234567', '$2a$12$3BvaI6MkH8E3t3jPGyPlFOoCmkGoxsq6h2uag/KhZpbVxxBe8DmfS', 'HOSPITAL_REQUESTER', 'VERIFIED'),
  ('requester@test.com', '0772223344', '$2a$12$3BvaI6MkH8E3t3jPGyPlFOoCmkGoxsq6h2uag/KhZpbVxxBe8DmfS', 'REQUESTER', 'VERIFIED'),
  ('donor@test.com', '0773334455', '$2a$12$3BvaI6MkH8E3t3jPGyPlFOoCmkGoxsq6h2uag/KhZpbVxxBe8DmfS', 'DONOR', 'VERIFIED'),
  ('donor2@test.com', '0774445566', '$2a$12$3BvaI6MkH8E3t3jPGyPlFOoCmkGoxsq6h2uag/KhZpbVxxBe8DmfS', 'DONOR', 'VERIFIED'),
  ('donor3@test.com', '0775556677', '$2a$12$3BvaI6MkH8E3t3jPGyPlFOoCmkGoxsq6h2uag/KhZpbVxxBe8DmfS', 'DONOR', 'VERIFIED'),
  ('pending_hospital@test.com', '0779998877', '$2a$12$3BvaI6MkH8E3t3jPGyPlFOoCmkGoxsq6h2uag/KhZpbVxxBe8DmfS', 'HOSPITAL_REQUESTER', 'PENDING')
ON CONFLICT (email) DO UPDATE 
SET role = EXCLUDED.role, verification_status = EXCLUDED.verification_status;

-- 2. Seed Donor Profiles
INSERT INTO donor_profiles (user_id, blood_type, date_of_birth, weight_kg, district, latitude, longitude, is_available, reliability_score, total_donations, lives_helped_estimate)
SELECT u.id, 'O+', '1995-04-12', 68.50, 'Colombo', 6.9271, 79.8612, TRUE, 85.00, 4, 12
FROM users u WHERE u.email = 'donor@test.com'
ON CONFLICT DO NOTHING;

INSERT INTO donor_profiles (user_id, blood_type, date_of_birth, weight_kg, district, latitude, longitude, is_available, reliability_score, total_donations, lives_helped_estimate)
SELECT u.id, 'A-', '1998-08-20', 74.00, 'Kandy', 7.2906, 80.6337, TRUE, 92.00, 6, 18
FROM users u WHERE u.email = 'donor2@test.com'
ON CONFLICT DO NOTHING;

INSERT INTO donor_profiles (user_id, blood_type, date_of_birth, weight_kg, district, latitude, longitude, is_available, reliability_score, total_donations, lives_helped_estimate)
SELECT u.id, 'B+', '2000-01-15', 62.00, 'Galle', 6.0535, 80.2210, TRUE, 75.00, 2, 6
FROM users u WHERE u.email = 'donor3@test.com'
ON CONFLICT DO NOTHING;

-- 3. Seed Blood Requests
INSERT INTO blood_requests (requester_id, patient_blood_type, units_needed, urgency, hospital_name, district, latitude, longitude, status, current_radius_km, created_at, expires_at, fraud_risk_score, ai_flag_reason)
SELECT u.id, 'O+', 2, 'CRITICAL', 'National Hospital of Sri Lanka', 'Colombo', 6.9271, 79.8612, 'OPEN', 10, now(), now() + INTERVAL '3 days', 5, 'Urgent postpartum hemorrhage indicated'
FROM users u WHERE u.email = 'hospital@test.com'
LIMIT 1;

INSERT INTO blood_requests (requester_id, patient_blood_type, units_needed, urgency, hospital_name, district, latitude, longitude, status, current_radius_km, created_at, expires_at, fraud_risk_score, ai_flag_reason)
SELECT u.id, 'A-', 3, 'URGENT', 'Teaching Hospital Kandy', 'Kandy', 7.2906, 80.6337, 'OPEN', 5, now(), now() + INTERVAL '2 days', 10, 'Cardiovascular bypass surgery scheduled'
FROM users u WHERE u.email = 'requester@test.com'
LIMIT 1;

INSERT INTO blood_requests (requester_id, patient_blood_type, units_needed, urgency, hospital_name, district, latitude, longitude, status, current_radius_km, created_at, expires_at, fraud_risk_score, ai_flag_reason)
SELECT u.id, 'B+', 1, 'ROUTINE', 'Karapitiya Teaching Hospital', 'Galle', 6.0535, 80.2210, 'OPEN', 5, now(), now() + INTERVAL '5 days', 0, 'Routine orthopedic stabilization'
FROM users u WHERE u.email = 'hospital@test.com'
LIMIT 1;

INSERT INTO blood_requests (requester_id, patient_blood_type, units_needed, urgency, hospital_name, district, latitude, longitude, status, current_radius_km, created_at, expires_at, fraud_risk_score, ai_flag_reason)
SELECT u.id, 'AB-', 8, 'CRITICAL', 'Teaching Hospital Jaffna', 'Jaffna', 9.6615, 80.0255, 'OPEN', 25, now(), now() + INTERVAL '1 day', 85, 'Unusually high unit count requested by unverified user'
FROM users u WHERE u.email = 'requester@test.com'
LIMIT 1;

-- 4. Seed Request Matches (For donor@test.com and donor2@test.com to test Accept/Decline)
INSERT INTO request_matches (request_id, donor_id, distance_km, notified_at, response)
SELECT r.id, d.id, 2.40, now() - INTERVAL '15 minutes', 'PENDING'
FROM blood_requests r
JOIN donor_profiles d ON d.blood_type = r.patient_blood_type
WHERE r.patient_blood_type = 'O+' AND r.urgency = 'CRITICAL'
LIMIT 1
ON CONFLICT DO NOTHING;

INSERT INTO request_matches (request_id, donor_id, distance_km, notified_at, response, responded_at, donated)
SELECT r.id, d.id, 1.80, now() - INTERVAL '1 hour', 'ACCEPTED', now() - INTERVAL '45 minutes', FALSE
FROM blood_requests r
JOIN donor_profiles d ON d.blood_type = r.patient_blood_type
WHERE r.patient_blood_type = 'A-' AND r.urgency = 'URGENT'
LIMIT 1
ON CONFLICT DO NOTHING;

-- 5. Seed Notification Logs
INSERT INTO notification_logs (donor_id, request_id, channel, status, recipient, message_body, sent_at)
SELECT d.id, r.id, 'SSE', 'DELIVERED', 'donor@test.com', 'Emergency Alert: CRITICAL O+ blood needed at National Hospital of Sri Lanka', now() - INTERVAL '15 minutes'
FROM donor_profiles d, blood_requests r
WHERE d.blood_type = 'O+' AND r.patient_blood_type = 'O+'
LIMIT 1;

-- 6. Seed Donation Camps
INSERT INTO donation_camps (name, district, location, latitude, longitude, scheduled_date, start_time, end_time, organizer_id, status)
SELECT 'Colombo Red Cross Blood Drive', 'Colombo', 'Town Hall Grounds, Colombo 07', 6.9147, 79.8640, CURRENT_DATE + INTERVAL '2 days', '09:00:00', '15:00:00', u.id, 'SCHEDULED'
FROM users u WHERE u.email = 'hospital@test.com'
LIMIT 1;

INSERT INTO donation_camps (name, district, location, latitude, longitude, scheduled_date, start_time, end_time, organizer_id, status)
SELECT 'Kandy Central Community Blood Camp', 'Kandy', 'Kandy City Centre Auditorium', 7.2936, 80.6366, CURRENT_DATE + INTERVAL '5 days', '08:30:00', '14:30:00', u.id, 'SCHEDULED'
FROM users u WHERE u.email = 'hospital@test.com'
LIMIT 1;

INSERT INTO donation_camps (name, district, location, latitude, longitude, scheduled_date, start_time, end_time, organizer_id, status)
SELECT 'Southern Life Blood Donation Drive', 'Galle', 'Galle Fort Community Center', 6.0329, 80.2168, CURRENT_DATE + INTERVAL '12 days', '09:00:00', '16:00:00', u.id, 'SCHEDULED'
FROM users u WHERE u.email = 'hospital@test.com'
LIMIT 1;

-- 7. Seed Camp Pre-Registration
INSERT INTO camp_registrations (camp_id, donor_id, attended)
SELECT c.id, d.id, FALSE
FROM donation_camps c, donor_profiles d
WHERE c.name = 'Kandy Central Community Blood Camp' AND d.blood_type = 'A-'
LIMIT 1
ON CONFLICT DO NOTHING;

-- 8. Seed Live Blood Bank Inventory
INSERT INTO blood_bank_inventory (hospital_name, district, blood_type, status) VALUES
  ('Batticaloa General Hospital', 'Batticaloa', 'O+', 'LOW'),
  ('Anuradhapura Teaching Hospital', 'Anuradhapura', 'A+', 'LOW'),
  ('Kurunegala Teaching Hospital', 'Kurunegala', 'O-', 'CRITICAL'),
  ('Badulla Provincial General Hospital', 'Badulla', 'B-', 'LOW')
ON CONFLICT (hospital_name, blood_type) DO UPDATE 
SET status = EXCLUDED.status;

-- 9. Seed Replacement-Donor Circles
INSERT INTO request_circles (request_id, invite_token, created_at, expires_at)
SELECT r.id, 'circle-demo-colombo-2026', now(), now() + INTERVAL '3 days'
FROM blood_requests r
WHERE r.patient_blood_type = 'O+'
LIMIT 1
ON CONFLICT DO NOTHING;

INSERT INTO circle_responses (circle_id, responder_name, responder_phone, blood_type, notes)
SELECT c.id, 'Kasun Perera', '0711122334', 'O+', 'Available to donate immediately at NHSL'
FROM request_circles c
WHERE c.invite_token = 'circle-demo-colombo-2026'
LIMIT 1;

INSERT INTO circle_responses (circle_id, responder_name, responder_phone, blood_type, notes)
SELECT c.id, 'Nimal Fernando', '0722233445', 'O+', 'Can arrive after 2 PM'
FROM request_circles c
WHERE c.invite_token = 'circle-demo-colombo-2026'
LIMIT 1;

-- 10. Seed Request Report (For Admin Queue Testing)
INSERT INTO request_reports (request_id, reported_by, reason)
SELECT r.id, u.id, 'Suspected commercial blood sale solicitation or unverified high unit count'
FROM blood_requests r, users u
WHERE r.patient_blood_type = 'AB-' AND u.email = 'donor@test.com'
LIMIT 1;
