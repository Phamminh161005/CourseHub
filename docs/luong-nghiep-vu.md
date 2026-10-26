# Luồng nghiệp vụ

Flowchart các luồng chính, viết bằng [Mermaid](https://mermaid.js.org/) (GitHub tự vẽ thành hình). Mỗi thành viên bổ sung mục mình phụ trách.

- [1. Xác thực và Người dùng](#1-xác-thực-và-người-dùng)
- [2. Quản lý sơ đồ](#2-quản-lý-sơ-đồ)
- [3. Lịch sử phiên bản](#3-lịch-sử-phiên-bản)
- [4. Chia sẻ và phân quyền](#4-chia-sẻ-và-phân-quyền)

---

## 1. Xác thực và Người dùng

### 1.1. Đăng ký

```mermaid
flowchart TD
    A(["POST /auth/register"]) --> B{"Dữ liệu hợp lệ?<br/>email đúng định dạng,<br/>mật khẩu 8-72 ký tự"}
    B -- Không --> E400["400 Bad Request"]
    B -- Có --> C["Chuẩn hóa email<br/>(bỏ khoảng trắng, chữ thường)"]
    C --> D{"Email đã tồn tại?"}
    D -- Có --> E409a["409 Conflict<br/>Email is already registered"]
    D -- Không --> F{"Username đã tồn tại?"}
    F -- Có --> E409b["409 Conflict<br/>Username is already taken"]
    F -- Không --> G["Băm mật khẩu bằng BCrypt"]
    G --> H["Lưu user mới vào bảng users"]
    H --> I(["201 Created<br/>id, username, email, createdAt"])
```

### 1.2. Đăng nhập

```mermaid
flowchart TD
    A(["POST /auth/login"]) --> B{"Dữ liệu hợp lệ?"}
    B -- Không --> E400["400 Bad Request"]
    B -- Có --> C["Tìm user theo email đã chuẩn hóa"]
    C --> D{"Tìm thấy và<br/>mật khẩu khớp BCrypt?"}
    D -- Không --> E401["401 Unauthorized<br/>Invalid email or password"]
    D -- Có --> F["Tạo access token JWT<br/>subject = id user, hạn 15 phút"]
    F --> G["Sinh refresh token ngẫu nhiên 32 byte"]
    G --> H["Lưu SHA-256 của refresh token<br/>vào refresh_tokens, hạn 7 ngày"]
    H --> I(["200 OK<br/>accessToken, refreshToken, tokenType"])
```

### 1.3. Làm mới token (rotation)

```mermaid
flowchart TD
    A(["POST /auth/refresh-token"]) --> B{"Có refreshToken?"}
    B -- Không --> E400["400 Bad Request"]
    B -- Có --> C["Băm SHA-256 token gửi lên"]
    C --> D["Tìm trong refresh_tokens theo hash"]
    D --> E{"Tìm thấy, chưa thu hồi<br/>và chưa hết hạn?"}
    E -- Không --> E401["401 Unauthorized<br/>Invalid or expired refresh token"]
    subgraph TX ["Trong một transaction"]
        F["Đánh dấu token cũ: revoked = true"] --> G["Tạo access token mới"]
        G --> H["Sinh và lưu hash refresh token mới"]
    end
    E -- Có --> F
    H --> I(["200 OK<br/>cặp token mới"])
```

### 1.4. Đăng xuất

```mermaid
flowchart TD
    A(["POST /auth/logout"]) --> B{"Access token hợp lệ?<br/>(filter JWT)"}
    B -- Không --> E401["401 Unauthorized"]
    B -- Có --> C{"Có refreshToken?"}
    C -- Không --> E400["400 Bad Request"]
    C -- Có --> D["Tìm refresh token theo hash"]
    D --> E{"Tồn tại, thuộc user đang đăng nhập<br/>và chưa thu hồi?"}
    E -- Có --> F["Đánh dấu revoked = true"]
    E -- Không --> G["Bỏ qua, không làm gì"]
    F --> H(["204 No Content"])
    G --> H
```

### 1.5. Xem thông tin và đổi mật khẩu

```mermaid
flowchart TD
    A(["PATCH /users/me/password"]) --> B{"Access token hợp lệ?"}
    B -- Không --> E401["401 Unauthorized"]
    B -- Có --> C{"Dữ liệu hợp lệ?<br/>mật khẩu mới 8-72 ký tự"}
    C -- Không --> E400a["400 Bad Request"]
    C -- Có --> D["Lấy user theo id trong token"]
    D --> E{"Mật khẩu hiện tại đúng?"}
    E -- Không --> E400b["400 Bad Request<br/>Current password is incorrect"]
    E -- Có --> F["Băm mật khẩu mới bằng BCrypt và lưu"]
    F --> G(["204 No Content"])

    M(["GET /users/me"]) --> N{"Access token hợp lệ?"}
    N -- Không --> E401b["401 Unauthorized"]
    N -- Có --> O["Lấy user theo id trong token"]
    O --> P(["200 OK<br/>id, username, email, createdAt"])
```

### 1.6. Filter xác thực JWT (chạy trước mọi request)

```mermaid
flowchart TD
    A(["Request đến"]) --> B{"Có header<br/>Authorization: Bearer ...?"}
    B -- Không --> E["Không đặt người dùng"]
    B -- Có --> C{"Chữ ký đúng<br/>và chưa hết hạn?"}
    C -- Không --> E
    C -- Có --> D["Đặt người dùng hiện tại = id trong token"]
    D --> F{"Endpoint công khai?"}
    E --> F
    F -- Có --> OK(["Chạy controller"])
    F -- Không --> G{"Đã có người dùng?"}
    G -- Có --> OK
    G -- Không --> E401(["401 Unauthorized<br/>Authentication required"])
```

Filter không tự chặn request: thiếu hoặc sai token thì chỉ không đặt người dùng, còn việc trả 401 do `SecurityConfig` quyết định. Nhờ vậy filter `X-Share-Token` (link chia sẻ) có thể gắn thêm mà không sửa filter này.

