-- ============================================================
-- V2__add_indexes.sql
-- Oracle: Indexes for performance + FK support
-- Every index is justified below.
-- ============================================================

-- ============================================================
-- CUSTOMERS Indexes
-- ============================================================

-- IDX_CUSTOMERS_EMAIL: Already covered by UK_CUSTOMERS_EMAIL (Oracle creates implicit index)
-- Additional: status for filtering active users
CREATE INDEX IDX_CUSTOMERS_STATUS ON CUSTOMERS(STATUS);
-- Justification: Admin queries like "SELECT active customers" -> INDEX RANGE SCAN

-- ============================================================
-- REFRESH_TOKENS Indexes
-- ============================================================

-- Required for FK lookup (Oracle does NOT auto-create FK indexes)
CREATE INDEX IDX_REFRESH_TOKENS_CUSTOMER ON REFRESH_TOKENS(CUSTOMER_ID);
-- Justification: On logout, DELETE FROM REFRESH_TOKENS WHERE CUSTOMER_ID = ? -> FULL SCAN without this

CREATE INDEX IDX_REFRESH_TOKENS_EXPIRES ON REFRESH_TOKENS(EXPIRES_AT, REVOKED);
-- Justification: Cleanup job: DELETE expired tokens -> uses composite index

-- ============================================================
-- PRODUCTS Indexes
-- ============================================================

-- Category-based browsing (most common query)
CREATE INDEX IDX_PRODUCTS_CATEGORY_STATUS ON PRODUCTS(CATEGORY_ID, STATUS);
-- Justification: GET /products?category=xxx -> PRODUCTS JOIN CATEGORIES with STATUS='ACTIVE'
-- Explain Plan: INDEX RANGE SCAN on IDX_PRODUCTS_CATEGORY_STATUS

-- Search by name (case-insensitive full-text search approximation)
CREATE INDEX IDX_PRODUCTS_NAME_UPPER ON PRODUCTS(UPPER(PRODUCT_NAME));
-- Justification: GET /products?search=nước -> WHERE UPPER(PRODUCT_NAME) LIKE 'NƯỚC%'
-- Function-based index enables INDEX RANGE SCAN instead of FULL TABLE SCAN

-- Price range filtering
CREATE INDEX IDX_PRODUCTS_PRICE ON PRODUCTS(PRICE, STATUS);
-- Justification: Price range queries in product listing

-- Slug lookup for SEO URLs
-- Already covered by UK_PRODUCTS_SLUG (unique constraint creates index)

-- ============================================================
-- INVENTORY Indexes
-- ============================================================

-- FK index (UK_INVENTORY_PRODUCT already creates unique index)
-- No additional indexes needed - PRODUCT_ID has UK constraint = index

-- ============================================================
-- CARTS Indexes
-- ============================================================

-- Customer's active cart lookup (most frequent operation)
CREATE INDEX IDX_CARTS_CUSTOMER_STATUS ON CARTS(CUSTOMER_ID, STATUS);
-- Justification: CartService.findActiveCart: WHERE CUSTOMER_ID=? AND STATUS='ACTIVE'
-- Explain Plan: INDEX RANGE SCAN, returns at most 1 row per customer

-- ============================================================
-- CART_ITEMS Indexes
-- ============================================================

-- FK index for CART_ID
CREATE INDEX IDX_CART_ITEMS_CART ON CART_ITEMS(CART_ID);
-- Justification: SELECT * FROM CART_ITEMS WHERE CART_ID = ? (get cart contents)
-- Without this: FULL TABLE SCAN on CART_ITEMS

-- FK index for PRODUCT_ID
CREATE INDEX IDX_CART_ITEMS_PRODUCT ON CART_ITEMS(PRODUCT_ID);
-- Justification: ON DELETE product -> find all cart items for that product

-- ============================================================
-- ORDERS Indexes (Partitioned table)
-- ============================================================

-- Customer order history (LOCAL partition index for partition pruning)
CREATE INDEX IDX_ORDERS_CUSTOMER_CREATED ON ORDERS(CUSTOMER_ID, CREATED_AT DESC)
    LOCAL;
-- Justification: GET /orders (customer order history, sorted by date)
-- LOCAL index: each partition has its own index segment -> partition pruning works
-- Explain Plan: PARTITION RANGE ALL -> INDEX RANGE SCAN LOCAL

-- Status-based admin queries
CREATE INDEX IDX_ORDERS_STATUS ON ORDERS(STATUS, CREATED_AT DESC)
    LOCAL;
-- Justification: Admin: SELECT orders WHERE STATUS='PENDING' ORDER BY CREATED_AT

-- Order number lookup (already UK, no extra index needed)

-- ============================================================
-- ORDER_ITEMS Indexes
-- ============================================================

-- FK index for ORDER_ID (most critical: load order details)
CREATE INDEX IDX_ORDER_ITEMS_ORDER ON ORDER_ITEMS(ORDER_ID);
-- Justification: SELECT * FROM ORDER_ITEMS WHERE ORDER_ID = ? -> INDEX RANGE SCAN
-- Without this: FULL TABLE SCAN on ORDER_ITEMS for every order detail request

-- FK index for PRODUCT_ID (for product sales analytics)
CREATE INDEX IDX_ORDER_ITEMS_PRODUCT ON ORDER_ITEMS(PRODUCT_ID);
-- Justification: Analytics: top-selling products query

-- ============================================================
-- PAYMENTS Indexes (Partitioned table)
-- ============================================================

-- Status polling query
CREATE INDEX IDX_PAYMENTS_STATUS_CREATED ON PAYMENTS(STATUS, CREATED_AT DESC)
    LOCAL;
-- Justification: Payment status polling, admin monitoring of pending payments
-- LOCAL index for partition pruning

-- ============================================================
-- OUTBOX_EVENTS Indexes
-- ============================================================

-- Relay query: select pending events in order
CREATE INDEX IDX_OUTBOX_STATUS_CREATED ON OUTBOX_EVENTS(STATUS, CREATED_AT ASC);
-- Justification: OutboxRelay: SELECT TOP 50 WHERE STATUS='PENDING' ORDER BY CREATED_AT
-- Without this: FULL TABLE SCAN on potentially large OUTBOX_EVENTS table
-- Explain Plan: INDEX RANGE SCAN, fetches only PENDING rows

-- Aggregate type + ID for targeted queries
CREATE INDEX IDX_OUTBOX_AGGREGATE ON OUTBOX_EVENTS(AGGREGATE_TYPE, AGGREGATE_ID);
-- Justification: Find all events for a specific order: WHERE AGGREGATE_TYPE='ORDER' AND AGGREGATE_ID='1001'

-- ============================================================
-- PROCESSED_EVENTS Indexes
-- ============================================================

-- Already covered by UK_PROCESSED_EVENTS (EVENT_ID, CONSUMER_GROUP) -> unique index
-- No additional indexes needed

-- ============================================================
-- EXPLAIN PLAN ANALYSIS (Top 5 Critical Queries)
-- ============================================================
-- Run these after table creation to verify index usage:
--
-- 1. Product listing (most frequent):
-- EXPLAIN PLAN FOR
--   SELECT p.PRODUCT_ID, p.PRODUCT_NAME, p.PRICE, i.QUANTITY
--   FROM PRODUCTS p
--   JOIN INVENTORY i ON i.PRODUCT_ID = p.PRODUCT_ID
--   WHERE p.CATEGORY_ID = :cat AND p.STATUS = 'ACTIVE'
--   ORDER BY p.CREATED_AT DESC
--   FETCH NEXT 20 ROWS ONLY;
-- SELECT * FROM TABLE(DBMS_XPLAN.DISPLAY);
-- Expected: INDEX RANGE SCAN on IDX_PRODUCTS_CATEGORY_STATUS
--
-- 2. Active cart lookup:
-- EXPLAIN PLAN FOR
--   SELECT * FROM CARTS WHERE CUSTOMER_ID = :cid AND STATUS = 'ACTIVE';
-- Expected: INDEX RANGE SCAN on IDX_CARTS_CUSTOMER_STATUS
--
-- 3. Inventory SELECT FOR UPDATE:
-- EXPLAIN PLAN FOR
--   SELECT * FROM INVENTORY WHERE PRODUCT_ID = :pid FOR UPDATE NOWAIT;
-- Expected: INDEX UNIQUE SCAN on UK_INVENTORY_PRODUCT
--
-- 4. Order history:
-- EXPLAIN PLAN FOR
--   SELECT * FROM ORDERS WHERE CUSTOMER_ID = :cid ORDER BY CREATED_AT DESC
--   FETCH NEXT 20 ROWS ONLY;
-- Expected: INDEX RANGE SCAN LOCAL on IDX_ORDERS_CUSTOMER_CREATED
--
-- 5. Outbox relay:
-- EXPLAIN PLAN FOR
--   SELECT * FROM OUTBOX_EVENTS WHERE STATUS = 'PENDING'
--   ORDER BY CREATED_AT ASC FETCH NEXT 50 ROWS ONLY;
-- Expected: INDEX RANGE SCAN on IDX_OUTBOX_STATUS_CREATED
