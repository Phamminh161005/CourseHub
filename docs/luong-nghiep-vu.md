# Luồng nghiệp vụ

Tài liệu này mô tả các luồng xử lý chính của DiagramIO. Những chức năng đơn giản như đăng ký, đăng nhập, đổi mật khẩu, tạo, xóa hay xem danh sách sơ đồ không được vẽ lại ở đây, có thể xem trong [openapi.yaml](openapi.yaml) hoặc Swagger UI.

1. [Xác thực một request](#1-xác-thực-một-request)
2. [Phân quyền theo Caller](#2-phân-quyền-theo-caller)
3. [Làm mới token (rotation)](#3-làm-mới-token-rotation)
4. [Cập nhật sơ đồ (optimistic locking)](#4-cập-nhật-sơ-đồ-optimistic-locking)
5. [Lưu phiên bản (snapshot)](#5-lưu-phiên-bản-snapshot)
6. [Khôi phục phiên bản](#6-khôi-phục-phiên-bản)
7. [Vòng đời link chia sẻ](#7-vòng-đời-link-chia-sẻ)

Trong các sơ đồ, hình bo tròn là điểm bắt đầu hoặc kết thúc, hình thoi là điều kiện, hình chữ nhật là bước xử lý. Khi có lỗi, API trả về JSON gồm `timestamp`, `status`, `error`, `message`, `path`.

| Mã | Khi nào |
|---|---|
| 400 | Dữ liệu gửi lên sai: thiếu trường, sai định dạng, `data` sai cấu trúc, `size` lớn hơn 100 |
| 401 | Chưa xác thực: không có JWT hoặc token chia sẻ hợp lệ, sai email/mật khẩu, refresh token không dùng được |
| 403 | Đã biết người gọi là ai nhưng không đủ quyền với sơ đồ |
| 404 | Không tìm thấy sơ đồ, phiên bản, link, hoặc token chia sẻ đã bị thu hồi |
| 409 | Xung đột: version sơ đồ đã thay đổi, email hoặc username đã tồn tại |

---

## 1. Xác thực một request

Mỗi request đi qua hai filter trước khi tới controller: filter JWT, sau đó là filter `X-Share-Token`. Hai filter này không tự chặn request. Thiếu hoặc sai token thì chỉ không xác định được người gọi, còn việc trả 401 do `SecurityConfig` quyết định. Kết quả cuối cùng là một `Caller`, gồm `userId` nếu đã đăng nhập và sơ đồ cùng quyền của link chia sẻ nếu có.

```mermaid
flowchart TD
    A(["Request đến"]) --> B{"Có header Authorization: Bearer JWT<br/>với chữ ký đúng, chưa hết hạn?"}
    B -- Có --> C["Người gọi = userId trong JWT"]
    B -- "Không / sai / hết hạn" --> D["Chưa có người gọi"]
    C --> E{"Có header X-Share-Token<br/>và token còn trong DB?"}
    D --> E
    E -- Có --> F["Người gọi = Caller(<br/>userId nếu đã đăng nhập,<br/>sơ đồ của link, quyền view/edit)"]
    E -- "Không / token sai / đã thu hồi" --> G["Giữ nguyên người gọi"]
    F --> H{"Endpoint công khai?<br/>auth, health, /shares/**, swagger,<br/>GET /diagrams/public, GET /diagrams/id"}
    G --> H
    H -- Có --> K["Controller nhận Caller<br/>(chưa đăng nhập = anonymous)"]
    H -- Không --> I{"Đã có người gọi?"}
    I -- Không --> E401(["401 Unauthorized<br/>Authentication required"])
    I -- Có --> K
    K --> L["Service kiểm tra quyền bằng Caller<br/>(xem mục 2)"]
    L --> OK(["Kết quả hoặc 403/404/409"])
```

- Người đã đăng nhập mà vẫn gửi `X-Share-Token` thì hai thông tin được gộp lại. Nhờ vậy chủ sơ đồ mở link của chính mình vẫn giữ quyền chủ, và người sửa có đăng nhập vẫn được ghi vào `updated_by`.
- Token chia sẻ được tra trong DB ở mỗi request, nên khi chủ đổi quyền hay thu hồi link thì thay đổi có hiệu lực ngay từ request tiếp theo.

## 2. Phân quyền theo Caller

Toàn bộ luật quyền nằm trong `domain/access/Caller`. Service chỉ cần gọi `canView`, `canViewHistory`, `canEdit` hoặc `canManage`, không cần biết người gọi dùng JWT hay link chia sẻ.

```mermaid
flowchart TD
    A(["Caller muốn thao tác trên sơ đồ D"]) --> O{"Là chủ của D?<br/>userId = ownerId"}
    O -- Có --> ALL["Mọi thao tác<br/>xem, xem lịch sử, sửa, đổi isPublic,<br/>lưu/khôi phục phiên bản,<br/>xóa sơ đồ, quản lý link chia sẻ"]
    O -- Không --> L{"Cầm link chia sẻ<br/>của chính D?"}
    L -- "Link edit" --> ED["Xem, xem lịch sử, sửa nội dung,<br/>lưu/khôi phục phiên bản<br/>(không đổi isPublic, không xóa,<br/>không quản lý link)"]
    L -- "Link view" --> VW["Xem, xem lịch sử"]
    L -- "Không có, hoặc link của sơ đồ khác" --> P{"D công khai?"}
    P -- Có --> PV["Chỉ xem nội dung<br/>(không xem lịch sử)"]
    P -- Không --> NO(["Không có quyền: 403<br/>(401 nếu endpoint bắt buộc đăng nhập)"])
```

| Hàm | Đúng khi | Dùng cho |
|---|---|---|
| `canManage` | là chủ | `DELETE /diagrams/{id}`, mọi `/diagrams/{id}/shares...`, đổi `isPublic` |
| `canEdit` | `canManage`, hoặc có link edit của chính sơ đồ | `PUT /diagrams/{id}`, `POST .../versions`, `POST .../restore` |
| `canViewHistory` | `canEdit`, hoặc có link view của chính sơ đồ | `GET .../versions`, `GET .../versions/{versionId}` |
| `canView` | `canViewHistory`, hoặc sơ đồ đang công khai | `GET /diagrams/{id}` |

- Sơ đồ công khai không mở lịch sử phiên bản cho người ngoài. Chủ sơ đồ có thể đã xóa thông tin nhạy cảm trước khi công khai, nhưng các snapshot cũ vẫn còn giữ thông tin đó.
- Link của sơ đồ A không có tác dụng gì với sơ đồ B.

## 3. Làm mới token (rotation)

Access token chỉ có hạn 15 phút. Refresh token có hạn 7 ngày nhưng chỉ dùng được một lần: mỗi lần làm mới, token cũ bị thu hồi và người dùng nhận token mới. DB chỉ lưu SHA-256 của refresh token, nên dù DB bị lộ thì cũng không dùng được các token này.

```mermaid
flowchart TD
    A(["POST /auth/refresh-token"]) --> B{"Body có refreshToken?"}
    B -- Không --> E400(["400 Bad Request"])
    subgraph TX ["Một transaction: lỗi ở bất kỳ bước nào thì rollback toàn bộ"]
        C["Băm SHA-256 token gửi lên"] --> D["Tìm trong refresh_tokens theo hash"]
        D --> E{"Tìm thấy, chưa thu hồi<br/>và chưa hết hạn?"}
        F["Thu hồi token cũ: revoked = true"] --> G["Tạo access token JWT mới"]
        G --> H["Sinh refresh token mới 32 byte,<br/>lưu hash, hạn 7 ngày"]
    end
    B -- Có --> C
    E -- Có --> F
    E -- Không --> E401(["401 Unauthorized<br/>Invalid or expired refresh token"])
    H --> OK(["200 OK<br/>accessToken, refreshToken mới"])
```

- Gửi lại một refresh token đã dùng sẽ nhận 401. Đăng xuất (`POST /auth/logout`) cũng chỉ là đặt `revoked = true` cho token được gửi lên.
- Việc thu hồi token cũ và lưu token mới nằm chung một transaction, nên không xảy ra trường hợp token cũ đã mất mà token mới chưa được lưu.

## 4. Cập nhật sơ đồ (optimistic locking)

Nhiều người có thể sửa cùng một sơ đồ. Vì vậy client phải gửi kèm `version` mà mình đang thấy. Nếu người khác đã lưu trước đó (version đã tăng), server từ chối thay vì ghi đè lên thay đổi của họ.

```mermaid
flowchart TD
    A(["PUT /diagrams/id<br/>title, diagramType, version, data, isPublic"]) --> AU{"Đã xác thực?<br/>JWT hoặc X-Share-Token"}
    AU -- Không --> E401(["401 Unauthorized"])
    AU -- Có --> B{"Body hợp lệ?<br/>title không rỗng, UML/ERD,<br/>có version, có data"}
    B -- Không --> E400a(["400 Bad Request"])
    B -- Có --> C{"Sơ đồ tồn tại?"}
    C -- Không --> E404(["404 Not Found"])
    C -- Có --> D{"caller.canEdit?<br/>chủ hoặc link edit"}
    D -- Không --> E403(["403 Forbidden"])
    D -- Có --> V{"version gửi lên =<br/>version trong DB?"}
    V -- Không --> E409a(["409 Conflict<br/>Diagram version conflict"])
    V -- Có --> J{"data là object có mảng nodes, edges<br/>và không quá 2 MB?"}
    J -- Không --> E400b(["400 Bad Request"])
    J -- Có --> M{"Người gọi là chủ?"}
    M -- Có --> P1["Cập nhật cả isPublic"]
    M -- Không --> P2["Giữ nguyên isPublic cũ"]
    P1 --> S["Ghi title, diagramType, data,<br/>updatedBy = userId (NULL nếu chỉ có link),<br/>updatedAt = bây giờ"]
    P2 --> S
    S --> W["UPDATE ... WHERE version = version cũ<br/>và tăng version thêm 1"]
    W --> R{"Có người khác ghi chen vào<br/>ngay trước lúc UPDATE?"}
    R -- Có --> E409b(["409 Conflict"])
    R -- Không --> OK(["200 OK<br/>sơ đồ với version mới"])
```

- Có hai lớp chống ghi đè: so sánh version trong code, và điều kiện `WHERE version = ...` của JPA `@Version` lúc ghi. Lớp thứ hai bắt được trường hợp hai request cùng qua bước so sánh gần như cùng lúc.
- Khi nhận 409, client tải lại sơ đồ bằng `GET` để lấy version mới rồi sửa lại.

## 5. Lưu phiên bản (snapshot)

Snapshot là bản chụp cố định của `data` tại thời điểm lưu, được đánh số 1, 2, 3... riêng cho từng sơ đồ. Luồng này phải xử lý trường hợp nhiều người lưu cùng lúc, đồng thời bảo đảm bản chụp đúng là nội dung người lưu đang thấy.

```mermaid
flowchart TD
    A(["POST /diagrams/id/versions<br/>note, version (không bắt buộc)"]) --> B{"note không quá 255 ký tự?"}
    B -- Không --> E400(["400 Bad Request"])
    subgraph TX ["Một transaction, khóa dòng sơ đồ tới khi commit"]
        L["SELECT ... FOR UPDATE dòng sơ đồ<br/>request khác trên cùng sơ đồ phải chờ"] --> C{"Sơ đồ tồn tại?"}
        D{"caller.canEdit?"}
        V{"Có gửi version và<br/>khác version hiện tại?"}
        N["Số thứ tự = số lớn nhất + 1"] --> S["Lưu snapshot: data hiện tại, note,<br/>createdBy = userId (NULL nếu chỉ có link)"]
    end
    B -- Có --> L
    C -- Không --> E404(["404 Not Found"])
    C -- Có --> D
    D -- Không --> E403(["403 Forbidden"])
    D -- Có --> V
    V -- Có --> E409(["409 Conflict<br/>sơ đồ đã bị sửa sau version client thấy"])
    V -- Không --> N
    S --> OK(["201 Created<br/>Location: .../versions/versionId"])
```

- Dòng sơ đồ bị khóa để các request lưu cùng lúc phải xếp hàng, không ai lấy trùng số thứ tự. Ràng buộc `UNIQUE (diagram_id, version_number)` là lớp bảo vệ cuối cùng, nếu vẫn trùng thì trả 409.
- Trường `version` gửi kèm giúp tránh tình huống: An vừa `PUT` xong, Bình sửa chen vào, rồi request lưu của An lại chụp nhầm nội dung của Bình. Việc so version diễn ra sau khi đã khóa, nên không ai chen vào được giữa lúc so và lúc chụp.

## 6. Khôi phục phiên bản

Khôi phục là chép `data` của một snapshot cũ vào sơ đồ hiện tại. Theo đặc tả, trạng thái hiện tại phải được tự động sao lưu trước, để nếu khôi phục nhầm thì vẫn lấy lại được.

```mermaid
flowchart TD
    A(["POST /diagrams/id/versions/versionId/restore"]) --> L
    subgraph TX ["Một transaction: lỗi ở bất kỳ bước nào thì rollback toàn bộ"]
        L["Khóa dòng sơ đồ (SELECT ... FOR UPDATE)"] --> C{"Sơ đồ tồn tại?"}
        D{"caller.canEdit?"}
        F{"Phiên bản tồn tại<br/>và thuộc sơ đồ này?"}
        B["Tạo snapshot sao lưu trạng thái hiện tại<br/>note = Auto backup before restoring version N"] --> G["Chép data của phiên bản đã chọn vào sơ đồ,<br/>updatedBy, updatedAt"]
        G --> S["Lưu sơ đồ, version tăng thêm 1"]
    end
    C -- Không --> E404a(["404 Not Found"])
    C -- Có --> D
    D -- Không --> E403(["403 Forbidden"])
    D -- Có --> F
    F -- Không --> E404b(["404 Not Found"])
    F -- Có --> B
    S --> OK(["200 OK<br/>sơ đồ sau khôi phục + snapshot sao lưu"])
```

- Bản sao lưu được tạo trước rồi mới ghi đè, và cả hai bước nằm trong cùng một transaction. Vì vậy không bao giờ có lúc sơ đồ đã bị ghi đè mà chưa có bản sao lưu.
- Chỉ phần `data` được khôi phục; tiêu đề, loại sơ đồ và `isPublic` giữ nguyên. Các snapshot cũ không bị sửa hay xóa.

## 7. Vòng đời link chia sẻ

Chia sẻ hoạt động giống Google Docs: chủ sơ đồ tạo link với quyền view hoặc edit, ai có link thì dùng được, không bắt buộc đăng nhập.

```mermaid
flowchart TD
    A(["Chủ: POST /diagrams/id/shares<br/>role = view hoặc edit"]) --> B["Sinh token ngẫu nhiên 24 byte (48 ký tự hex),<br/>lưu vào diagram_shares"]
    B --> C(["201 Created<br/>shareToken, shareUrl, role"])
    C --> D["Chủ gửi shareUrl cho người khác"]
    D --> E["Người nhận mở shareUrl<br/>GET /shares/token"]
    E --> F{"Token còn trong DB?"}
    F -- Không --> E404(["404 Not Found"])
    F -- Có --> G["200 OK: thông tin sơ đồ, data, role"]
    G --> H["Thao tác tiếp trên /diagrams/id/...<br/>kèm header X-Share-Token"]
    H --> R{"Quyền của link?"}
    R -- view --> V["Xem sơ đồ, xem lịch sử phiên bản"]
    R -- edit --> W["Thêm: sửa nội dung,<br/>lưu và khôi phục phiên bản"]

    P(["Chủ: PATCH /diagrams/id/shares/shareId<br/>đổi view sang edit hoặc ngược lại"]) --> P2["Có hiệu lực từ request tiếp theo"]
    X(["Chủ: DELETE /diagrams/id/shares/shareId"]) --> X2["Xóa dòng trong diagram_shares"]
    X2 --> X3["Từ request sau: GET /shares/token trả 404,<br/>header X-Share-Token bị bỏ qua"]
```

- `shareUrl` được tạo từ địa chỉ server đang nhận request (localhost, Docker hay máy chủ thật) và trỏ tới `GET /api/v1/shares/{token}`, vì pha 1 chưa có frontend.
- Người dùng link mà không đăng nhập thì `updated_by` và `created_by` để NULL. Nhiều người dùng chung một link thì không phân biệt được ai với ai; việc này để sang pha 2, khi làm cộng tác thời gian thực.
- Chỉ chủ sơ đồ quản lý được link. Người cầm link edit mà gọi các endpoint `/shares` sẽ nhận 401 hoặc 403.
