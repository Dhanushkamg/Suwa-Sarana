-- Track failed guesses so a 6-digit code cannot be brute-forced
ALTER TABLE otp_verifications ADD COLUMN attempts INTEGER NOT NULL DEFAULT 0;
