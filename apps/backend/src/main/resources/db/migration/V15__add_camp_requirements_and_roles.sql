-- Add required_blood_groups to donation_camps
ALTER TABLE donation_camps ADD COLUMN required_blood_groups VARCHAR(100);
