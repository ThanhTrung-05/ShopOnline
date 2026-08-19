# Keycloak — T1 Infrastructure (dev only)

Hạ tầng Keycloak cho Epic quản lý người dùng/phân quyền. Realm `shoponline`
được import tự động từ `keycloak/import/shoponline-realm.json` khi container
khởi động (`start-dev --import-realm`).

Không commit mật khẩu hoặc client secret thật vào Git. Mọi giá trị nhạy cảm
chỉ nằm trong file `.env` (đã bị `.gitignore` chặn) — sao chép từ
`.env.example` và tự điền giá trị.

## 1. Chuẩn bị `.env`

```powershell
Copy-Item .env.example .env
# Mở .env và thay tất cả giá trị "changeme" bằng giá trị thật của bạn.
```

## 2. Khởi động

```powershell
docker compose config --quiet
docker compose up -d keycloak-db keycloak
docker inspect --format='{{.State.Health.Status}}' bhtt-keycloak-db
docker inspect --format='{{.State.Health.Status}}' bhtt-keycloak
```

## 3. Nạp biến từ `.env` vào Process Environment (PowerShell)

Các lệnh ở Phần 4-5 cần đọc giá trị thật (bootstrap admin, mật khẩu test) từ
`.env`. Đoạn dưới nạp từng dòng `.env` vào `$env:*` của session PowerShell
hiện tại — **không in giá trị ra console**.

```powershell
Get-Content .env | ForEach-Object {
    if ($_ -match '^\s*#' -or $_ -notmatch '=') { return }
    $name, $value = $_ -split '=', 2
    [System.Environment]::SetEnvironmentVariable($name.Trim(), $value.Trim(), 'Process')
}
```

Sau khi chạy, các biến sau có sẵn trong session (không echo ra để tránh lộ
mật khẩu/secret):

- `$env:KC_BOOTSTRAP_ADMIN_USERNAME`
- `$env:KC_BOOTSTRAP_ADMIN_PASSWORD`
- `$env:KEYCLOAK_TEST_CUSTOMER_PASSWORD`
- `$env:KEYCLOAK_TEST_ADMIN_PASSWORD`
- `$env:KEYCLOAK_USER_ADMIN_CLIENT_SECRET`

## 4. Lấy admin token và kiểm tra realm/client/role (PowerShell)

```powershell
$adminResponse = Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8081/realms/master/protocol/openid-connect/token" `
  -Body @{
    client_id  = "admin-cli"
    username   = $env:KC_BOOTSTRAP_ADMIN_USERNAME
    password   = $env:KC_BOOTSTRAP_ADMIN_PASSWORD
    grant_type = "password"
  }
$adminToken = $adminResponse.access_token
$headers = @{ Authorization = "Bearer $adminToken" }

# Realm đã import
Invoke-RestMethod -Uri "http://localhost:8081/admin/realms/shoponline" -Headers $headers | Select-Object realm

# Ba client tồn tại
Invoke-RestMethod -Uri "http://localhost:8081/admin/realms/shoponline/clients" -Headers $headers |
  Select-Object clientId

# Ba client role trong shoponline-backend
$backend = Invoke-RestMethod -Uri "http://localhost:8081/admin/realms/shoponline/clients?clientId=shoponline-backend" -Headers $headers
$backendUuid = $backend[0].id
Invoke-RestMethod -Uri "http://localhost:8081/admin/realms/shoponline/clients/$backendUuid/roles" -Headers $headers |
  Select-Object name
```

## 5. Đặt mật khẩu test (PowerShell, không hard-code, không echo)

```powershell
$customer = Invoke-RestMethod -Uri "http://localhost:8081/admin/realms/shoponline/users?username=test-customer" -Headers $headers
$customerId = $customer[0].id

Invoke-RestMethod -Method Put `
  -Uri "http://localhost:8081/admin/realms/shoponline/users/$customerId/reset-password" `
  -Headers $headers -ContentType "application/json" `
  -Body (@{ type = "password"; value = $env:KEYCLOAK_TEST_CUSTOMER_PASSWORD; temporary = $true } | ConvertTo-Json)

$admin = Invoke-RestMethod -Uri "http://localhost:8081/admin/realms/shoponline/users?username=test-admin" -Headers $headers
$adminId = $admin[0].id

Invoke-RestMethod -Method Put `
  -Uri "http://localhost:8081/admin/realms/shoponline/users/$adminId/reset-password" `
  -Headers $headers -ContentType "application/json" `
  -Body (@{ type = "password"; value = $env:KEYCLOAK_TEST_ADMIN_PASSWORD; temporary = $true } | ConvertTo-Json)
```

## 6. Kiểm tra Authorization Code + PKCE bằng Postman (khuyến nghị chính)

Frontend React chưa chạy trong T1, nên dùng callback URL của Postman
(`https://oauth.pstmn.io/v1/browser-callback`) — URL này đã được khai báo
sẵn trong `redirectUris` của `shoponline-frontend` trong realm JSON.

1. Tạo request mới → tab **Authorization** → Type: **OAuth 2.0**.
2. Grant Type: **Authorization Code (With PKCE)**.
3. Callback URL: `https://oauth.pstmn.io/v1/browser-callback`
   (chọn **Authorize using browser** trong Postman để dùng callback này).
4. Auth URL: `http://localhost:8081/realms/shoponline/protocol/openid-connect/auth`
5. Access Token URL: `http://localhost:8081/realms/shoponline/protocol/openid-connect/token`
6. Client ID: `shoponline-frontend`. Client Authentication: **None** (public client).
7. Scope: `openid`. Code Challenge Method: **SHA-256** (Postman tự sinh verifier/challenge).
8. Bấm **Get New Access Token** → đăng nhập bằng `test-admin` hoặc
   `test-customer` (mật khẩu đã đặt ở Phần 5) → Postman nhận code, tự đổi
   lấy token qua callback Postman.
9. Trong panel token nhận được, bấm **decode payload** (hoặc dán token vào
   [jwt.io](https://jwt.io) — chỉ dùng cho token test, không dán token thật)
   và kiểm tra:
   - `aud` chứa `"shoponline-backend"`
   - `resource_access.shoponline-backend.roles` chứa đúng role của user vừa
     đăng nhập.

> Khi frontend React thật (`http://localhost:3000/*`) đã chạy, có thể dùng
> redirect URI đó thay cho callback Postman — cả hai đều đã được khai báo
> sẵn trong realm JSON.

## 7. Decode JWT bằng PowerShell thuần (không cần jq/cut, dùng khi không có Postman)

```powershell
function Decode-JwtPayload($token) {
  $payload = ($token -split '\.')[1].Replace('-', '+').Replace('_', '/')
  switch ($payload.Length % 4) { 2 { $payload += '==' } 3 { $payload += '=' } }
  [System.Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($payload)) | ConvertFrom-Json
}
Decode-JwtPayload $accessToken | Select-Object aud, resource_access
```

## 8. Phụ lục Bash (chỉ dành cho Git Bash / WSL / Linux, không phải cách chính)

```bash
# Ghi chú: chạy trong Git Bash, WSL hoặc máy Linux. Trên PowerShell thuần, dùng Phần 3-7 ở trên.
set -a; source .env; set +a

ADMIN_TOKEN=$(curl -s -X POST http://localhost:8081/realms/master/protocol/openid-connect/token \
  -d "client_id=admin-cli" -d "username=$KC_BOOTSTRAP_ADMIN_USERNAME" -d "password=$KC_BOOTSTRAP_ADMIN_PASSWORD" \
  -d "grant_type=password" | jq -r .access_token)
```

## 9. Ghi chú vận hành

- Không thêm mật khẩu vào `shoponline-realm.json` — mọi giá trị nhạy cảm chỉ
  nằm trong `.env` (đã bị `.gitignore` chặn).
- `resource_access.shoponline-backend.roles` phụ thuộc `clientScopeMappings`
  trong realm JSON, không phải `fullScopeAllowed`. Thêm client mới cần role
  của `shoponline-backend` → thêm entry vào `clientScopeMappings`, không bật
  lại `fullScopeAllowed`.
- **Cần verify sau import**: vào Admin Console → Clients →
  `shoponline-user-admin` → Credentials, xác nhận secret đã resolve từ
  `${KEYCLOAK_USER_ADMIN_CLIENT_SECRET}` (không hiển thị nguyên văn
  placeholder).
- Service account `shoponline-user-admin` hiện chỉ có `manage-users`,
  `view-users`. **Ghi chú cho T5**: chỉ bổ sung `query-clients`/
  `view-clients` (từ `realm-management`) nếu khi viết `KeycloakAdminClient`
  (T5, gọi Admin REST API) chứng minh thực sự cần tra cứu client/role qua
  API — không mở rộng quyền trước khi có nhu cầu thực tế.

## 10. Dừng / Rollback

```powershell
docker compose stop keycloak keycloak-db
docker compose rm -f keycloak keycloak-db
# Xóa dữ liệu Keycloak hoàn toàn (KHÔNG ảnh hưởng oracle-data/redis-data):
docker volume rm shoponline_keycloak-db-data
```
