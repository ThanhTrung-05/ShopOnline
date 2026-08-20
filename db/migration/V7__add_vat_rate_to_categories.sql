-- ============================================================
-- V7__add_vat_rate_to_categories.sql
-- ATS-4: Add VAT_RATE to CATEGORIES table
-- VAT rate (%) applied to all products within a category.
-- Vietnamese VAT Law: essential food = 5%, general goods = 10%
-- ============================================================

-- 1. Add column with default 10% (most common rate)
ALTER TABLE CATEGORIES ADD (
    VAT_RATE NUMBER(5,2) DEFAULT 10 NOT NULL
);

COMMENT ON COLUMN CATEGORIES.VAT_RATE IS
    'Thue suat VAT (%) ap dung cho toan bo san pham thuoc danh muc nay. Vi du: 5.00 hoac 10.00';

-- 2. Set VAT rates per Vietnamese tax regulations
-- 5% -- Essential food items (Decree 15/2022/ND-CP)
UPDATE CATEGORIES SET VAT_RATE = 5  WHERE CATEGORY_CODE IN ('FRESH', 'DAIRY');

-- 10% -- Standard consumer goods
UPDATE CATEGORIES SET VAT_RATE = 10 WHERE CATEGORY_CODE IN ('BEVERAGES', 'SNACKS', 'FROZEN', 'HOUSEHOLD', 'PERSONAL', 'INSTANT');

COMMIT;
