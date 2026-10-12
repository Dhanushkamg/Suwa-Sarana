-- Java caps reliability at 100.00, which does not fit NUMERIC(4,2) (max 99.99)
ALTER TABLE donor_profiles ALTER COLUMN reliability_score TYPE NUMERIC(5,2);
