-- H2 seed data for the 'local' profile.
-- The Oracle scripts in db/migration/*.sql never run here (no Flyway/Liquibase configured);
-- Hibernate builds the schema from JPA entities, so this file supplies matching sample rows.

INSERT INTO CATEGORIES (CATEGORY_CODE, CATEGORY_NAME, DESCRIPTION, VAT_RATE, SORT_ORDER, STATUS, CREATED_AT, UPDATED_AT) VALUES
  ('THUC_PHAM', 'Thực phẩm', 'Thực phẩm tươi sống và đóng gói', 5, 1, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('DIEN_MAY',  'Điện máy',  'Thiết bị điện tử, điện gia dụng', 10, 2, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('SANH_SU',   'Sành sứ',   'Đồ sành sứ, gia dụng',            10, 3, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO PRODUCTS (PRODUCT_SLUG, CATEGORY_ID, PRODUCT_NAME, PRICE, IMAGE_URL, STATUS, VERSION, CREATED_AT, UPDATED_AT) VALUES
  ('nuoc-suoi-lavie-500ml',  (SELECT CATEGORY_ID FROM CATEGORIES WHERE CATEGORY_CODE='THUC_PHAM'), 'Nước suối Lavie 500ml',            5000,     '/images/lavie-500ml.jpg',  'ACTIVE', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('nuoc-ngot-pepsi-330ml',  (SELECT CATEGORY_ID FROM CATEGORIES WHERE CATEGORY_CODE='THUC_PHAM'), 'Nước ngọt Pepsi lon 330ml',        12000,    '/images/pepsi-330ml.jpg',  'ACTIVE', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('sua-tuoi-vinamilk-1l',   (SELECT CATEGORY_ID FROM CATEGORIES WHERE CATEGORY_CODE='THUC_PHAM'), 'Sữa tươi Vinamilk không đường 1L', 35000,    '/images/vinamilk-1l.jpg',  'ACTIVE', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('tivi-samsung-55inch',    (SELECT CATEGORY_ID FROM CATEGORIES WHERE CATEGORY_CODE='DIEN_MAY'),  'Tivi Samsung 55 inch 4K',           12000000, '/images/samsung-tv.jpg',   'ACTIVE', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('may-giat-lg-9kg',        (SELECT CATEGORY_ID FROM CATEGORIES WHERE CATEGORY_CODE='DIEN_MAY'),  'Máy giặt LG 9kg Inverter',          8500000,  '/images/lg-washer.jpg',    'ACTIVE', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('bo-bat-dia-minh-long',   (SELECT CATEGORY_ID FROM CATEGORIES WHERE CATEGORY_CODE='SANH_SU'),   'Bộ bát đĩa sứ Minh Long 20 món',    850000,   '/images/minhlong-set.jpg', 'ACTIVE', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO INVENTORY (PRODUCT_ID, QUANTITY, RESERVED_QUANTITY, VERSION, UPDATED_AT)
SELECT PRODUCT_ID, 100, 0, 0, CURRENT_TIMESTAMP FROM PRODUCTS;