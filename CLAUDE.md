# CLAUDE.md

Hướng dẫn ngữ cảnh cho Claude khi làm việc trong repo này.

## Tổng quan dự án

- Tên project: `banhangtructuyen` (ShopOnline) — ứng dụng bán hàng trực tuyến.
- Backend: Spring Boot 3.3.4, Java 17, Gradle (wrapper `gradlew`).
- Frontend: React + TypeScript, nằm trong thư mục `frontend/` (service riêng, Dockerfile riêng).
- Database: Oracle (chạy qua Docker image `gvenzl/oracle-xe:21-slim` trong môi trường dev/compose).
- Cache: Redis 7 (`spring-boot-starter-data-redis`).
- Messaging: Kafka + Zookeeper (images Confluent) — dùng cho outbox pattern.

## Cấu trúc package backend

Root package: `com.example.banhangtructuyen`

- `domain/model` — JPA entity (`Customer`, `Product`, `Category`, `Inventory`, ...)
- `domain/repository` — Spring Data JPA repository interface
- `domain/exception` — exception nghiệp vụ (`EmailAlreadyExistsException`, `ResourceNotFoundException`)
- `application/dto` — request/response DTO, chia theo module (`dto/auth`, `dto/product`)
- `application/service`, `application/service/impl` — interface và implementation business logic
- `presentation/controller` — REST controller
- `presentation/exception` — `GlobalExceptionHandler`
- `config` — Spring configuration bean (`PasswordEncoderConfig`, `AuditingConfig`, `AppProperties`)
- `infrastructure/cache` — cache key constants

## Database

- Toàn bộ schema được định nghĩa bằng SQL thuần trong `db/migration/V1__...sql` đến `V7__...sql`.
- **Dự án KHÔNG dùng Flyway.** Không có dependency Flyway trong `build.gradle`, không có config `spring.flyway.*`. Các file `V1`–`V7` chỉ là quy ước đặt tên thủ công, không được tool migration nào tự động áp dụng.
- Các file này được chạy **thủ công bằng SQL*Plus** vào Oracle. Khi thêm thay đổi schema mới, tạo file `Vn__description.sql` tiếp theo và chạy tay tương tự — không giả định có migration tool tự động.
- Bảng chính: `CUSTOMERS`, `REFRESH_TOKENS`, `CATEGORIES`, `PRODUCTS`, `INVENTORY`, `CARTS`, `CART_ITEMS`, `ORDERS`, `ORDER_ITEMS`, `PAYMENTS`, `OUTBOX_EVENTS`, `PROCESSED_EVENTS`.
- `hibernate.ddl-auto` ở profile prod là `validate` (không tự tạo/sửa schema) — entity phải khớp đúng với schema đã chạy tay.

## Authentication hiện tại

- ATS-20 (đăng ký tài khoản) đã triển khai: `AuthController` → `AuthServiceImpl` → `CustomerRepository`, lưu vào bảng `CUSTOMERS`.
- Password được hash bằng BCrypt (`spring-security-crypto`, strength 12, cấu hình tại `PasswordEncoderConfig`).
- `build.gradle` hiện chỉ có `spring-security-crypto` (cho BCrypt). Chưa có `spring-boot-starter-security`, chưa có JWT hoặc session implementation nào trong source.
- Cách triển khai login/logout/session (JWT, session, cookie, ...) chưa được quyết định trong repo — sẽ được xác định khi triển khai story liên quan, không giả định trước ở đây.

## Docker

- `docker-compose.yml` gồm các service: `oracledb`, `redis`, `zookeeper`, `kafka`, `backend`, `frontend`.
- `Dockerfile` backend: multi-stage build — build bằng Gradle trên `eclipse-temurin:17-jdk-alpine`, runtime trên `eclipse-temurin:17-jre-alpine` với user non-root.
- Backend chạy trong Docker Compose phải kết nối Oracle qua service DNS `jdbc:oracle:thin:@oracledb:1521/XEPDB1`.
- Backend chạy trực tiếp trên host dùng cổng publish của Oracle Compose: `jdbc:oracle:thin:@localhost:1522/XEPDB1`.
- Local backend dùng profile `local` (`src/main/resources/application-local.yml`) để tránh fallback sang H2:
  `$env:SPRING_PROFILES_ACTIVE="local"; $env:ORACLE_USER="<user>"; $env:ORACLE_PASSWORD="<password>"; .\gradlew.bat bootRun`.
- Restart bình thường dùng `docker compose stop/start` hoặc `docker compose up -d`; không dùng `docker compose down -v` trừ khi cố ý xóa dữ liệu. Mapping `JWT sub -> CUSTOMERS.KEYCLOAK_USER_ID` phụ thuộc vào việc giữ nguyên named volumes `oracle-data` và `keycloak-db-data`.

## Testing

- JUnit 5 qua `spring-boot-starter-test`.
- Test dùng H2 in-memory (`MODE=Oracle`) thay Oracle thật, cấu hình tại `src/test/resources/application-test.yml`.
