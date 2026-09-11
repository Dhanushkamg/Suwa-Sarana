CREATE TABLE blood_bank_inventory (
    id             BIGSERIAL PRIMARY KEY,
    hospital_name  VARCHAR(150) NOT NULL,
    district       VARCHAR(50) NOT NULL,
    blood_type     VARCHAR(3) NOT NULL CHECK (blood_type IN ('A+','A-','B+','B-','AB+','AB-','O+','O-')),
    status         VARCHAR(20) NOT NULL CHECK (status IN ('ADEQUATE', 'LOW', 'CRITICAL')),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(hospital_name, blood_type)
);

-- Seed some initial realistic data for the ticker
INSERT INTO blood_bank_inventory (hospital_name, district, blood_type, status) VALUES
('National Hospital Colombo', 'Colombo', 'O-', 'CRITICAL'),
('Teaching Hospital Kandy', 'Kandy', 'A-', 'LOW'),
('Karapitiya Teaching Hospital', 'Galle', 'B+', 'ADEQUATE'),
('Teaching Hospital Jaffna', 'Jaffna', 'AB-', 'CRITICAL');
