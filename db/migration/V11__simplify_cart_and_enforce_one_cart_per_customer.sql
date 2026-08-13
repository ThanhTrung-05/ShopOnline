-- ============================================================
-- V11__simplify_cart_and_enforce_one_cart_per_customer.sql
-- ATS-8: One lifetime cart per customer
-- ============================================================

DROP INDEX IDX_CARTS_CUSTOMER_STATUS;

ALTER TABLE CARTS DROP CONSTRAINT CHK_CARTS_STATUS;

ALTER TABLE CARTS DROP COLUMN STATUS;

ALTER TABLE CARTS ADD CONSTRAINT UK_CARTS_CUSTOMER UNIQUE (CUSTOMER_ID);

COMMIT;
