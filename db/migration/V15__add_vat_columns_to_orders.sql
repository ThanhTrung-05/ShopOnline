-- ============================================================
-- V15__add_vat_columns_to_orders.sql
-- ATS-26: Persist VAT breakdown at time of order placement.
-- Captures vat_rate, vat_amount per line item and totals on ORDERS.
-- Rationale: if Category.vatRate changes in the future,
--   historical invoices must still show the correct VAT that was
--   charged at order time.
-- ============================================================

-- -----------------------------------
-- ORDER_ITEMS: add per-line VAT fields
-- -----------------------------------
ALTER TABLE ORDER_ITEMS ADD VAT_RATE   NUMBER(5,2)  DEFAULT 0 NOT NULL;
ALTER TABLE ORDER_ITEMS ADD VAT_AMOUNT NUMBER(19,2) DEFAULT 0 NOT NULL;

COMMENT ON COLUMN ORDER_ITEMS.VAT_RATE   IS 'VAT rate (%) captured from Category at order time. E.g. 5.00 or 10.00';
COMMENT ON COLUMN ORDER_ITEMS.VAT_AMOUNT IS 'VAT amount for this line = subtotal_before_vat * vat_rate / 100, rounded HALF_UP to 0 decimals';

-- -----------------------------------
-- ORDERS: add invoice-level totals
-- -----------------------------------
ALTER TABLE ORDERS ADD TOTAL_BEFORE_VAT NUMBER(19,2) DEFAULT 0 NOT NULL;
ALTER TABLE ORDERS ADD TOTAL_VAT_AMOUNT NUMBER(19,2) DEFAULT 0 NOT NULL;

COMMENT ON COLUMN ORDERS.TOTAL_BEFORE_VAT IS 'Sum of all (unit_price * quantity) across order items — merchandise subtotal before VAT';
COMMENT ON COLUMN ORDERS.TOTAL_VAT_AMOUNT  IS 'Sum of all VAT_AMOUNT across order items';

COMMIT;
