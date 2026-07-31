-- ============================================================
-- V6__seed_admin_user.sql
-- Seed admin account for BanHangTrucTuyen
-- ============================================================

-- Admin account credentials:
-- Email: admin@example.com
-- Password: admin123
-- Role: ADMIN
-- 
-- Note: Password is hashed with BCrypt-12
-- Hash of 'admin123': $2a$12$1z0X4j9kZwKXO5rVJzzVAOm8.xz5kZFGRuPfZ0QVBw5O5D7R5F2lC
-- (Generated with BCrypt strength 12)

INSERT INTO CUSTOMERS (
    EMAIL,
    FULL_NAME,
    PHONE,
    PASSWORD_HASH,
    STATUS,
    ROLE,
    CREATED_BY,
    UPDATED_BY
) VALUES (
    'admin@example.com',
    'Admin',
    '+84987654321',
    '$2a$12$1z0X4j9kZwKXO5rVJzzVAOm8.xz5kZFGRuPfZ0QVBw5O5D7R5F2lC',
    'ACTIVE',
    'ADMIN',
    'SYSTEM',
    'SYSTEM'
);

COMMIT;
