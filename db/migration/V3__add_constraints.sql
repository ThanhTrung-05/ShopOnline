-- ============================================================
-- V3__add_constraints.sql
-- Additional check constraints + triggers for audit fields
-- ============================================================

-- ============================================================
-- Trigger: Auto-update UPDATED_AT on CUSTOMERS
-- ============================================================
CREATE OR REPLACE TRIGGER TRG_CUSTOMERS_UPDATED_AT
    BEFORE UPDATE ON CUSTOMERS
    FOR EACH ROW
BEGIN
    :NEW.UPDATED_AT := SYSTIMESTAMP;
END;
/

-- ============================================================
-- Trigger: Auto-update UPDATED_AT on PRODUCTS
-- ============================================================
CREATE OR REPLACE TRIGGER TRG_PRODUCTS_UPDATED_AT
    BEFORE UPDATE ON PRODUCTS
    FOR EACH ROW
BEGIN
    :NEW.UPDATED_AT := SYSTIMESTAMP;
    :NEW.VERSION    := :OLD.VERSION + 1;
END;
/

-- ============================================================
-- Trigger: Auto-update UPDATED_AT on CARTS + version
-- ============================================================
CREATE OR REPLACE TRIGGER TRG_CARTS_UPDATED_AT
    BEFORE UPDATE ON CARTS
    FOR EACH ROW
BEGIN
    :NEW.UPDATED_AT := SYSTIMESTAMP;
    :NEW.VERSION    := :OLD.VERSION + 1;
END;
/

-- ============================================================
-- Trigger: Auto-update on ORDERS
-- ============================================================
CREATE OR REPLACE TRIGGER TRG_ORDERS_UPDATED_AT
    BEFORE UPDATE ON ORDERS
    FOR EACH ROW
BEGIN
    :NEW.UPDATED_AT := SYSTIMESTAMP;
END;
/

-- ============================================================
-- Trigger: Auto-update on PAYMENTS
-- ============================================================
CREATE OR REPLACE TRIGGER TRG_PAYMENTS_UPDATED_AT
    BEFORE UPDATE ON PAYMENTS
    FOR EACH ROW
BEGIN
    :NEW.UPDATED_AT := SYSTIMESTAMP;
END;
/

-- ============================================================
-- Trigger: Inventory safety - prevent QUANTITY going negative
-- (Defense in depth on top of application-level check)
-- ============================================================
CREATE OR REPLACE TRIGGER TRG_INVENTORY_QTY_CHECK
    BEFORE UPDATE OF QUANTITY ON INVENTORY
    FOR EACH ROW
BEGIN
    IF :NEW.QUANTITY < 0 THEN
        RAISE_APPLICATION_ERROR(-20001,
            'INVENTORY QUANTITY cannot be negative. Product: ' || :NEW.PRODUCT_ID ||
            ', Attempted: ' || :NEW.QUANTITY);
    END IF;
    :NEW.UPDATED_AT := SYSTIMESTAMP;
    :NEW.VERSION    := :OLD.VERSION + 1;
END;
/

-- ============================================================
-- Trigger: CART_ITEMS - update CART.UPDATED_AT on item change
-- ============================================================
CREATE OR REPLACE TRIGGER TRG_CART_ITEMS_UPDATED_AT
    BEFORE INSERT OR UPDATE ON CART_ITEMS
    FOR EACH ROW
BEGIN
    :NEW.UPDATED_AT := SYSTIMESTAMP;
END;
/

COMMIT;
