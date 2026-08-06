-- ============================================================
-- V8__add_category_name_unique.sql
-- ATS-5: Enforce uniqueness on CATEGORIES.CATEGORY_NAME
-- Category Name is a business identifier and must remain unique.
-- ============================================================

ALTER TABLE CATEGORIES ADD CONSTRAINT UK_CATEGORIES_NAME UNIQUE (CATEGORY_NAME);

COMMIT;
