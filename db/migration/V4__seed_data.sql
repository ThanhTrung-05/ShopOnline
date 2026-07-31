-- ============================================================
-- V4__seed_data.sql
-- Initial seed data: Categories + Sample Products + Inventory
-- ============================================================

-- ============================================================
-- CATEGORIES
-- ============================================================
INSERT INTO CATEGORIES (CATEGORY_CODE, CATEGORY_NAME, DESCRIPTION, SORT_ORDER) VALUES
    ('BEVERAGES',    'Đồ uống',           'Nước giải khát, nước suối, bia, rượu', 1);
INSERT INTO CATEGORIES (CATEGORY_CODE, CATEGORY_NAME, DESCRIPTION, SORT_ORDER) VALUES
    ('DAIRY',        'Sữa & Sản phẩm từ sữa', 'Sữa tươi, sữa chua, phô mai', 2);
INSERT INTO CATEGORIES (CATEGORY_CODE, CATEGORY_NAME, DESCRIPTION, SORT_ORDER) VALUES
    ('SNACKS',       'Bánh kẹo & Đồ ăn vặt', 'Bánh cookies, kẹo, chips', 3);
INSERT INTO CATEGORIES (CATEGORY_CODE, CATEGORY_NAME, DESCRIPTION, SORT_ORDER) VALUES
    ('FRESH',        'Thực phẩm tươi sống',   'Rau củ, thịt, hải sản', 4);
INSERT INTO CATEGORIES (CATEGORY_CODE, CATEGORY_NAME, DESCRIPTION, SORT_ORDER) VALUES
    ('FROZEN',       'Thực phẩm đông lạnh',   'Thực phẩm đông lạnh tiện lợi', 5);
INSERT INTO CATEGORIES (CATEGORY_CODE, CATEGORY_NAME, DESCRIPTION, SORT_ORDER) VALUES
    ('HOUSEHOLD',    'Gia dụng & Vệ sinh',    'Tẩy rửa, vệ sinh nhà cửa', 6);
INSERT INTO CATEGORIES (CATEGORY_CODE, CATEGORY_NAME, DESCRIPTION, SORT_ORDER) VALUES
    ('PERSONAL',     'Chăm sóc cá nhân',      'Mỹ phẩm, chăm sóc cơ thể', 7);
INSERT INTO CATEGORIES (CATEGORY_CODE, CATEGORY_NAME, DESCRIPTION, SORT_ORDER) VALUES
    ('INSTANT',      'Mì & Thực phẩm ăn liền','Mì gói, cháo ăn liền, phở', 8);

-- ============================================================
-- PRODUCTS (Beverages)
-- ============================================================
INSERT INTO PRODUCTS (PRODUCT_SLUG, CATEGORY_ID, PRODUCT_NAME, PRICE, IMAGE_URL, STATUS) VALUES
    ('nuoc-suoi-lavie-500ml',
     (SELECT CATEGORY_ID FROM CATEGORIES WHERE CATEGORY_CODE='BEVERAGES'),
     'Nước suối Lavie 500ml',
     5000, '/images/lavie-500ml.jpg', 'ACTIVE');

INSERT INTO PRODUCTS (PRODUCT_SLUG, CATEGORY_ID, PRODUCT_NAME, PRICE, IMAGE_URL, STATUS) VALUES
    ('nuoc-ngot-pepsi-330ml',
     (SELECT CATEGORY_ID FROM CATEGORIES WHERE CATEGORY_CODE='BEVERAGES'),
     'Nước ngọt Pepsi lon 330ml',
     12000, '/images/pepsi-330ml.jpg', 'ACTIVE');

INSERT INTO PRODUCTS (PRODUCT_SLUG, CATEGORY_ID, PRODUCT_NAME, PRICE, IMAGE_URL, STATUS) VALUES
    ('tra-xanh-o-long-c2-500ml',
     (SELECT CATEGORY_ID FROM CATEGORIES WHERE CATEGORY_CODE='BEVERAGES'),
     'Trà xanh Ô Long C2 500ml',
     8000, '/images/c2-500ml.jpg', 'ACTIVE');

INSERT INTO PRODUCTS (PRODUCT_SLUG, CATEGORY_ID, PRODUCT_NAME, PRICE, IMAGE_URL, STATUS) VALUES
    ('bia-tiger-lon-330ml',
     (SELECT CATEGORY_ID FROM CATEGORIES WHERE CATEGORY_CODE='BEVERAGES'),
     'Bia Tiger lon 330ml',
     18000, '/images/tiger-330ml.jpg', 'ACTIVE');

-- ============================================================
-- PRODUCTS (Dairy)
-- ============================================================
INSERT INTO PRODUCTS (PRODUCT_SLUG, CATEGORY_ID, PRODUCT_NAME, PRICE, IMAGE_URL, STATUS) VALUES
    ('sua-tuoi-vinamilk-1l',
     (SELECT CATEGORY_ID FROM CATEGORIES WHERE CATEGORY_CODE='DAIRY'),
     'Sữa tươi Vinamilk không đường 1L',
     35000, '/images/vinamilk-1l.jpg', 'ACTIVE');

INSERT INTO PRODUCTS (PRODUCT_SLUG, CATEGORY_ID, PRODUCT_NAME, PRICE, IMAGE_URL, STATUS) VALUES
    ('sua-chua-vinamilk-hop-400g',
     (SELECT CATEGORY_ID FROM CATEGORIES WHERE CATEGORY_CODE='DAIRY'),
     'Sữa chua Vinamilk hộp 4x100g',
     28000, '/images/vinamilk-yogurt.jpg', 'ACTIVE');

-- ============================================================
-- PRODUCTS (Snacks)
-- ============================================================
INSERT INTO PRODUCTS (PRODUCT_SLUG, CATEGORY_ID, PRODUCT_NAME, PRICE, IMAGE_URL, STATUS) VALUES
    ('keo-chupa-chups-hop-50-cai',
     (SELECT CATEGORY_ID FROM CATEGORIES WHERE CATEGORY_CODE='SNACKS'),
     'Kẹo Chupa Chups hộp 50 cái',
     65000, '/images/chupa-chups.jpg', 'ACTIVE');

INSERT INTO PRODUCTS (PRODUCT_SLUG, CATEGORY_ID, PRODUCT_NAME, PRICE, IMAGE_URL, STATUS) VALUES
    ('banh-oreo-vi-vani-137g',
     (SELECT CATEGORY_ID FROM CATEGORIES WHERE CATEGORY_CODE='SNACKS'),
     'Bánh Oreo vị Vani 137g',
     25000, '/images/oreo-vanilla.jpg', 'ACTIVE');

-- ============================================================
-- PRODUCTS (Instant Food)
-- ============================================================
INSERT INTO PRODUCTS (PRODUCT_SLUG, CATEGORY_ID, PRODUCT_NAME, PRICE, IMAGE_URL, STATUS) VALUES
    ('mi-hao-hao-tom-chua-cay-75g',
     (SELECT CATEGORY_ID FROM CATEGORIES WHERE CATEGORY_CODE='INSTANT'),
     'Mì Hảo Hảo tôm chua cay 75g',
     5000, '/images/haohao.jpg', 'ACTIVE');

INSERT INTO PRODUCTS (PRODUCT_SLUG, CATEGORY_ID, PRODUCT_NAME, PRICE, IMAGE_URL, STATUS) VALUES
    ('mi-3-mien-huong-ga-65g',
     (SELECT CATEGORY_ID FROM CATEGORIES WHERE CATEGORY_CODE='INSTANT'),
     'Mì 3 Miền hương gà 65g',
     4500, '/images/3mien-ga.jpg', 'ACTIVE');

-- ============================================================
-- INVENTORY (Initial stock for all products)
-- ============================================================
INSERT INTO INVENTORY (PRODUCT_ID, QUANTITY, RESERVED_QUANTITY)
SELECT PRODUCT_ID, 100, 0 FROM PRODUCTS WHERE STATUS = 'ACTIVE';

-- Set specific high stock for stress test product
UPDATE INVENTORY SET QUANTITY = 1000
WHERE PRODUCT_ID = (SELECT PRODUCT_ID FROM PRODUCTS WHERE PRODUCT_SLUG = 'nuoc-suoi-lavie-500ml');

-- Set low stock for testing insufficient inventory scenario
UPDATE INVENTORY SET QUANTITY = 10
WHERE PRODUCT_ID = (SELECT PRODUCT_ID FROM PRODUCTS WHERE PRODUCT_SLUG = 'bia-tiger-lon-330ml');

COMMIT;
