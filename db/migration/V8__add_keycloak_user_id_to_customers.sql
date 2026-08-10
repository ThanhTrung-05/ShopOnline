
-- ============================================================
-- V8__add_keycloak_user_id_to_customers.sql
-- Add KEYCLOAK_USER_ID to CUSTOMERS for ATS-22 Keycloak provisioning
-- ============================================================
-- Nullable for now: existing rows (seed admin, pre-ATS-22 registrations)
-- have no Keycloak identity yet. PASSWORD_HASH is kept as-is (still
-- NOT NULL) for backward compatibility; cleanup is a separate task.

ALTER TABLE CUSTOMERS ADD KEYCLOAK_USER_ID VARCHAR2(36) NULL;

CREATE UNIQUE INDEX UX_CUSTOMERS_KEYCLOAK_USER_ID
    ON CUSTOMERS (KEYCLOAK_USER_ID);

COMMIT;
