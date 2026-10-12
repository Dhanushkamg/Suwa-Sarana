-- Add first_name and last_name to users
ALTER TABLE users 
ADD COLUMN first_name VARCHAR(100),
ADD COLUMN last_name VARCHAR(100);

-- Since they are ideally required for new users, but we have existing users,
-- we'll update existing seeded test accounts with dummy data.
UPDATE users SET first_name = 'Test', last_name = 'User' WHERE first_name IS NULL;

-- Make them not null moving forward
ALTER TABLE users 
ALTER COLUMN first_name SET NOT NULL,
ALTER COLUMN last_name SET NOT NULL;
