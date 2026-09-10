-- Seed default hospital requester and admin accounts if they do not exist
INSERT INTO users (email, phone_number, password_hash, role, is_verified)
VALUES 
  ('hospital@test.com', '0771234567', '$2a$12$3BvaI6MkH8E3t3jPGyPlFOoCmkGoxsq6h2uag/KhZpbVxxBe8DmfS', 'HOSPITAL_REQUESTER', TRUE),
  ('admin@test.com', '0777654321', '$2a$12$3BvaI6MkH8E3t3jPGyPlFOoCmkGoxsq6h2uag/KhZpbVxxBe8DmfS', 'ADMIN', TRUE)
ON CONFLICT (email) DO UPDATE 
SET role = EXCLUDED.role, is_verified = EXCLUDED.is_verified;
