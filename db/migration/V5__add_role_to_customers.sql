-- ============================================================
-- V5__add_role_to_customers.sql
-- Description: Add ROLE column to CUSTOMERS table
-- ============================================================

ALTER TABLE CUSTOMERS ADD (ROLE VARCHAR2(20 CHAR) DEFAULT 'USER' NOT NULL);
ALTER TABLE CUSTOMERS ADD CONSTRAINT CHK_CUSTOMERS_ROLE CHECK (ROLE IN ('USER', 'ADMIN'));

-- Update the admin account created in V4
UPDATE CUSTOMERS SET ROLE = 'ADMIN' WHERE EMAIL = 'admin@example.com';
