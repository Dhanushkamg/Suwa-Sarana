-- V24__align_schema_with_entities.sql                                                          (audit F-33)
--
-- Found by PostgresSmokeTest, which boots the application on a real PostgreSQL with Hibernate `ddl-auto=validate`:
-- places where the Flyway-created schema and the JPA entities disagree. Each statement is a safe, in-place widening
-- or correction (no data loss).

-- contact_messages.id was created as SERIAL (integer); ContactMessage maps it as Long (bigint).
ALTER SEQUENCE contact_messages_id_seq AS bigint;
ALTER TABLE contact_messages ALTER COLUMN id TYPE BIGINT;
