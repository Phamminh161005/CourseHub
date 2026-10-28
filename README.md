# DiagramIO

DiagramIO là backend REST API (JSON) cho một web vẽ sơ đồ UML và ERD trực tuyến: quản lý tài khoản, lưu trữ sơ đồ, lịch sử phiên bản và chia sẻ sơ đồ qua link. Đây là bài tập lớn môn Kiến trúc phần mềm, pha 1. Bản này là baseline, ưu tiên chạy đúng và đơn giản; hiệu năng được đo trên Kaggle CPU để làm mốc so sánh cho pha 2.

| Thành viên | Phụ trách |
|---|---|
| Hoàng Gia Bảo | Quản lý sơ đồ |
| Phạm Minh | Khung dự án, xác thực người dùng
| Chí Long | Lịch sử phiên bản |
| Hưng Hoàng | Chia sẻ và phân quyền |

## 1. Bài toán và phạm vi

Sinh viên công nghệ thường xuyên vẽ sơ đồ UML và ERD khi làm đồ án. Mỗi bản vẽ có cấu trúc riêng, cần lưu lại lịch sử chỉnh sửa và chia sẻ cho người khác xem hoặc cùng sửa. Hệ thống có hai loại người dùng:

- **Khách (chưa đăng nhập):** xem sơ đồ công khai; xem hoặc sửa sơ đồ qua link chia sẻ, tùy quyền của link.
- **Người dùng đã đăng nhập:** tạo, sửa, xóa sơ đồ của mình; lưu và khôi phục phiên bản; tạo và quản lý link chia sẻ.

Bốn đối tượng chính là User (tài khoản), Diagram (tên, loại UML/ERD, dữ liệu JSON gồm `nodes` và `edges`, trạng thái công khai, chủ sở hữu, `version`), Diagram Version (bản chụp cố định của dữ liệu sơ đồ kèm ghi chú) và Diagram Share (link chia sẻ bằng token với quyền `view` hoặc `edit`).

Pha 1 làm: đăng ký, đăng nhập bằng JWT; quản lý sơ đồ; lịch sử phiên bản và khôi phục; chia sẻ công khai và qua link; chống ghi đè bằng optimistic locking; tài liệu OpenAPI; đóng gói Docker.

Pha 1 không làm: giao diện frontend và canvas; cộng tác thời gian thực (WebSocket, CRDT); đăng nhập qua bên thứ ba; khôi phục mật khẩu qua email; xuất file PNG/SVG/PDF; mẫu sơ đồ.

## 2. Công nghệ

| Thành phần | Lựa chọn |
|---|---|
| Ngôn ngữ, framework | Java 21, Spring Boot 3.4 (Web, Validation, Security) |
| CSDL | PostgreSQL 15, Spring Data JPA, Flyway quản lý schema |
| Xác thực | JWT (jjwt), BCrypt, refresh token lưu dạng SHA-256 |
| Tài liệu API | springdoc-openapi (Swagger UI) |
| Đóng gói | Docker, Docker Compose |
| Kiểm thử | JUnit 5, Mockito, MockMvc, Testcontainers, ArchUnit |

## 3. Kiến trúc

Ứng dụng chia thành ba tầng. Phụ thuộc chỉ đi theo một chiều: tầng API và tầng truy cập dữ liệu đều phụ thuộc vào tầng nghiệp vụ, còn tầng nghiệp vụ không phụ thuộc vào ai.

| Tầng | Package | Vai trò | Được dùng |
|---|---|---|---|
| API | `api` | Controller, DTO, kiểm tra đầu vào, `GlobalExceptionHandler` đổi ngoại lệ nghiệp vụ thành mã HTTP | Spring Web |
| Nghiệp vụ | `domain` | Model, service, interface repository, ngoại lệ nghiệp vụ, luật phân quyền | Chỉ Java thuần |
| Truy cập dữ liệu | `infrastructure` | Entity JPA, Spring Data repository, adapter cài đặt interface của domain, BCrypt, JWT, filter xác thực | Spring Data JPA, Spring Security |
| Lắp ghép | `config` | Tạo service của domain bằng `@Bean`, cấu hình bảo mật và OpenAPI | Spring |

Tầng nghiệp vụ không import Spring, JPA hay Servlet (không có `@Service`, `@Entity`, `@Transactional`). Quy tắc này được `ArchitectureTest` (ArchUnit) kiểm tra mỗi lần chạy test.

Trong mỗi tầng, code được chia theo tính năng:

```
src/main/java/com/diagramio/
├── api/             auth, user, diagram, version, share (mỗi phần có controller và dto/), dto/ dùng chung
├── domain/          auth, user, diagram, version, share, access (Caller, ShareRole), exception
├── infrastructure/  auth, user, diagram, version, share (entity, JpaRepository, RepositoryAdapter), security
└── config/          AuthConfig, DiagramConfig, DiagramVersionConfig, ShareConfig, SecurityConfig, OpenApiConfig
src/main/resources/db/migration/   V1__create_tables.sql, V2__seed_sample_user.sql, V3__create_refresh_tokens.sql
```

Một số điểm thiết kế chính:

- Xác thực nằm trong hai filter thay vì lặp lại ở từng endpoint. Filter JWT đọc `Authorization: Bearer <token>`, filter thứ hai đọc `X-Share-Token` của link chia sẻ. Hai filter không tự chặn request; việc trả 401 do `SecurityConfig` quyết định.
- Thông tin người gọi được gom vào `Caller` (người dùng đăng nhập và/hoặc link chia sẻ). Mọi luật quyền nằm trong `Caller` với bốn hàm `canView`, `canViewHistory`, `canEdit`, `canManage`, nên service không cần biết người gọi đến bằng cách nào.
- Transaction được truyền vào domain qua interface `TransactionRunner`; tầng infrastructure cài đặt bằng `TransactionTemplate`. Nhờ vậy service vẫn là Java thuần mà vẫn bọc được nhiều bước trong một transaction (làm mới token, lưu và khôi phục phiên bản).
- Chống ghi đè khi nhiều người cùng sửa bằng optimistic locking (`@Version`). Lưu và khôi phục phiên bản khóa dòng sơ đồ (`SELECT ... FOR UPDATE`) để các yêu cầu đồng thời xếp hàng.
- Schema CSDL do Flyway quản lý, Hibernate chỉ kiểm tra schema khớp với entity (`ddl-auto: validate`). CSDL gồm năm bảng: `users`, `refresh_tokens`, `diagrams`, `diagram_versions`, `diagram_shares`.

## 4. Đặc tả API

Mọi endpoint có tiền tố `/api/v1`. Đặc tả đầy đủ (request, response, mã lỗi) nằm trong [docs/openapi.yaml](docs/openapi.yaml); khi app đang chạy có thể xem và gọi thử trên Swagger UI tại `http://localhost:8080/swagger-ui.html`. Các luồng xử lý phức tạp được vẽ trong [docs/luong-nghiep-vu.md](docs/luong-nghiep-vu.md).

| Nhóm | Method | Endpoint | Mô tả | Quyền |
|---|---|---|---|---|
| Xác thực | POST | `/auth/register` | Đăng ký tài khoản | Công khai |
| | POST | `/auth/login` | Đăng nhập, nhận access token và refresh token | Công khai |
| | POST | `/auth/refresh-token` | Đổi refresh token lấy cặp token mới | Công khai |
| | POST | `/auth/logout` | Thu hồi refresh token | Đăng nhập |
| | GET | `/users/me` | Thông tin cá nhân | Đăng nhập |
| | PATCH | `/users/me/password` | Đổi mật khẩu, cần mật khẩu cũ | Đăng nhập |
| Sơ đồ | GET | `/diagrams` | Sơ đồ của tôi: lọc theo loại, tìm theo tên, phân trang | Đăng nhập |
| | POST | `/diagrams` | Tạo sơ đồ mới | Đăng nhập |
| | GET | `/diagrams/public` | Danh sách sơ đồ công khai | Công khai |
| | GET | `/diagrams/{id}` | Chi tiết sơ đồ kèm dữ liệu | Sơ đồ công khai, chủ, hoặc có link |
| | PUT | `/diagrams/{id}` | Cập nhật sơ đồ, kèm `version` | Chủ hoặc link edit |
| | DELETE | `/diagrams/{id}` | Xóa sơ đồ | Chủ |
| Phiên bản | GET | `/diagrams/{id}/versions` | Danh sách phiên bản, mới nhất trước | Chủ hoặc có link |
| | POST | `/diagrams/{id}/versions` | Lưu phiên bản mới kèm ghi chú | Chủ hoặc link edit |
| | GET | `/diagrams/{id}/versions/{versionId}` | Chi tiết một phiên bản | Chủ hoặc có link |
| | POST | `/diagrams/{id}/versions/{versionId}/restore` | Khôi phục về phiên bản đã chọn | Chủ hoặc link edit |
| Chia sẻ | GET | `/diagrams/{id}/shares` | Danh sách link chia sẻ | Chủ |
| | POST | `/diagrams/{id}/shares` | Tạo link quyền `view` hoặc `edit` | Chủ |
| | PATCH | `/diagrams/{id}/shares/{shareId}` | Đổi quyền của link | Chủ |
| | DELETE | `/diagrams/{id}/shares/{shareId}` | Thu hồi link | Chủ |
| | GET | `/shares/{token}` | Mở link chia sẻ: trả sơ đồ và quyền | Công khai |
| Hệ thống | GET | `/health` | Trạng thái dịch vụ và kết nối CSDL | Công khai |

Có hai cách xác thực. Người dùng đăng nhập gửi `Authorization: Bearer <accessToken>`. Người cầm link chia sẻ gửi `X-Share-Token: <shareToken>` khi gọi các endpoint `/diagrams/{id}...`, không cần đăng nhập. Gửi cả hai thì được gộp quyền.

Các quy tắc nghiệp vụ chính:

- Mật khẩu băm bằng BCrypt. Access token có hạn 15 phút. Refresh token có hạn 7 ngày, chỉ dùng được một lần và DB chỉ lưu bản băm. Sai email hay sai mật khẩu đều trả cùng một thông báo.
- Sơ đồ mới luôn ở chế độ riêng tư, dữ liệu khởi tạo là `{"nodes": [], "edges": []}`, loại chỉ nhận `UML` hoặc `ERD`.
- Khi cập nhật, client gửi `version` đang thấy. Lệch với CSDL thì trả 409 và dữ liệu giữ nguyên; khớp thì `version` tăng thêm 1. Trường `data` phải là object có mảng `nodes` và `edges`, tối đa 2 MB. Chỉ chủ được đổi `isPublic`.
- Phiên bản đã lưu không sửa được. Khi khôi phục, hệ thống tự lưu một phiên bản sao lưu trạng thái hiện tại rồi mới chép dữ liệu cũ vào, cả hai bước trong một transaction.
- Chỉ chủ quản lý được link chia sẻ. Link `view` cho xem sơ đồ và lịch sử; link `edit` cho sửa thêm sơ đồ, lưu và khôi phục phiên bản. Link bị thu hồi thì mất hiệu lực ngay, mở lại nhận 404.

Mọi lỗi trả cùng một khung JSON:

```json
{ "timestamp": "2026-10-28T07:30:00Z", "status": 409, "error": "Conflict",
  "message": "Diagram version conflict: requested 1, current 2", "path": "/api/v1/diagrams/..." }
```

| Mã | Khi nào |
|---|---|
| 400 | Thiếu trường, sai định dạng, `title` dài hơn 255 ký tự, `data` sai cấu trúc hoặc quá 2 MB, `size` lớn hơn 100, mật khẩu cũ sai |
| 401 | Chưa xác thực, token sai hoặc hết hạn, sai email hoặc mật khẩu; chỉ cầm `X-Share-Token` khi gọi endpoint cần tài khoản (`/users/me`, đăng xuất, sơ đồ của tôi, quản lý link) |
| 403 | Đã biết người gọi nhưng không đủ quyền với sơ đồ |
| 404 | Không tìm thấy sơ đồ, phiên bản, link, đường dẫn; token chia sẻ đã bị thu hồi |
| 405 | Sai method HTTP (header `Allow` liệt kê method hợp lệ) |
| 409 | Lệch `version` khi cập nhật hoặc lưu phiên bản; email hay username đã tồn tại |
| 415 | Sai `Content-Type` (API nhận `application/json`) |
| 503 | `/health` khi mất kết nối CSDL |

## 5. Cách chạy

Chỉ cần Docker để chạy toàn bộ hệ thống. Muốn chạy app từ IDE hoặc chạy test thì cần thêm JDK 21.

Chạy toàn bộ bằng Docker:

```bash
cp .env.example .env            # đổi mật khẩu, JWT_SECRET, cổng nếu cần
docker compose up -d --build    # build image app, chạy PostgreSQL và app
```

App chạy ở `http://localhost:8080` (đổi bằng `APP_PORT`). Flyway tự tạo bảng và tài khoản mẫu `uet@gmail.com` / `uet123456` ở lần chạy đầu. Container app có healthcheck gọi `/api/v1/health`.

Chạy app từ IDE khi đang phát triển: chạy riêng CSDL bằng `docker compose up -d postgres`, rồi chạy `DiagramioApplication` (hoặc `./mvnw spring-boot:run`). App tự đọc file `.env`, nên `SPRING_DATASOURCE_URL` phải trỏ đúng cổng `POSTGRES_PORT`. Hai cách chạy dùng chung cổng 8080, không chạy cùng lúc.

| Biến trong `.env` | Ý nghĩa |
|---|---|
| `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_DB` | Tài khoản và tên CSDL; mật khẩu chỉ có tác dụng ở lần đầu tạo volume |
| `POSTGRES_PORT`, `APP_PORT` | Cổng mở ra máy cho CSDL và app |
| `SPRING_DATASOURCE_*` | Kết nối CSDL khi chạy app từ IDE |
| `JWT_SECRET` | Khóa ký JWT, ít nhất 32 ký tự; bắt buộc đổi khi triển khai thật |

Thử nhanh bằng `curl`:

```bash
TOKEN=$(curl -s -X POST localhost:8080/api/v1/auth/login -H "Content-Type: application/json" \
  -d '{"email":"uet@gmail.com","password":"uet123456"}' | jq -r .accessToken)
curl -s -X POST localhost:8080/api/v1/diagrams -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -d '{"title":"Shop ERD","diagramType":"ERD"}'
```

Một số việc thường làm khác:

- Chạy test: `./mvnw test` (cần Docker đang chạy). Có ba nhóm test:
  - Unit test và test controller (MockMvc): chạy được ở mọi máy.
  - Test CSDL trong `infrastructure` (Testcontainers): không có Docker thì tự bỏ qua.
  - Test E2E hộp đen trong `src/test/java/com/diagramio/e2e`: khởi động cả app trên cổng ngẫu nhiên với PostgreSQL thật và chỉ gọi API qua HTTP, kiểm tra mọi endpoint với từng vai trò (chủ, link edit, link view, người khác, khách). Nhóm này cố ý báo lỗi khi không có Docker để không "xanh giả". Chạy riêng: `./mvnw test -Dtest='com.diagramio.e2e.**'`; bỏ qua: `./mvnw test -Dtest='!com.diagramio.e2e.**'`.
  - Dùng JDK mới hơn 21 (ví dụ 25) thì thêm `-DargLine="-Dnet.bytebuddy.experimental=true"` để Mockito chạy được.
- Xuất lại đặc tả API sau khi đổi endpoint (app đang chạy): `curl -o docs/openapi.yaml http://localhost:8080/v3/api-docs.yaml`.
- Thay đổi CSDL: thêm file migration mới dạng `V<số>__mo_ta.sql` (hai dấu gạch dưới) trong `src/main/resources/db/migration`, không sửa các file đã có.

## 6. Kiểm thử tải trên Kaggle

Kaggle không hỗ trợ Docker nên app được chạy trực tiếp bằng `java -jar` trong notebook. Kết quả dưới đây là mốc baseline; pha 2 cần chạy lại cùng cấu hình và cùng kịch bản để so sánh.

| Thông số | Giá trị |
|---|---|
| Máy | Kaggle Notebook CPU: 4 vCPU, 16 GB RAM |
| Phần mềm | Java 21, PostgreSQL 15, công cụ tải Locust (headless) |
| Dữ liệu ban đầu | Khởi tạo CSDL trống, tự động tạo 50 tài khoản người dùng động trong quá trình chạy |
| Kịch bản | Kịch bản tuần tự 8 bước (Đăng ký → Đăng nhập → Tạo sơ đồ → Sửa sơ đồ → Lưu Snapshot → Rollback → Tạo Share → Truy cập Public Share), 50 người dùng ảo (Virtual Users), thời gian chạy 30s |

**Cách vận hành:**
1. **Tại Server local:** Khởi chạy Backend & Database bằng `docker compose up -d`, sau đó tạo tunnel mã hóa bằng lệnh `npx cloudflared tunnel --protocol http2 --url http://127.0.0.1:8080`. Copy Cloudflare Tunnel (`https://<subdomain>.trycloudflare.com`).
2. **Tại Kaggle Notebook (bật Internet):** Cài đặt Locust (`pip install locust`), nạp kịch bản `locustfile.py` và chạy lệnh ép tải trỏ `--host` về địa chỉ Cloudflare Tunnel (`https://<subdomain>.trycloudflare.com`).

Link notebook: https://www.kaggle.com/code/hoanggiabao1/diagramio

| Kịch bản | RPS | p95 (ms) | p99 (ms) | Tỉ lệ lỗi |
|---|---|---|---|---|
| POST /api/v1/auth/register | 12.63 | 1000 | 1100 | 0.0% |
| POST /api/v1/auth/login | 12.63 | 890 | 900 | 0.0% |
| POST /api/v1/diagrams | 12.63 | 470 | 870 | 0.0% |
| PUT /api/v1/diagrams/[id] | 12.63 | 420 | 820 | 0.0% |
| POST /api/v1/diagrams/[id]/versions | 12.63 | 400 | 790 | 0.0% |
| POST /api/v1/diagrams/[id]/versions/[version_id]/restore | 12.63 | 430 | 780 | 0.0% |
| POST /api/v1/diagrams/[id]/shares | 12.63 | 350 | 790 | 0.0% |
| GET /api/v1/shares/[token] | 12.63 | 420 | 520 | 0.0% |

## 7. Hướng cải tiến cho pha 2

Baseline cố ý chưa tối ưu. Các hướng dưới đây đều đo được trước và sau bằng cùng kịch bản ở mục 6.

| Thuộc tính | Hiện trạng | Hướng cải tiến |
|---|---|---|
| Hiệu năng truy vấn | Chỉ có index trên `diagrams.owner_id`. Tìm theo tên dùng `LIKE '%...%'` nên phải quét toàn bộ sơ đồ của người dùng | Thêm index cho `diagram_shares.diagram_id` và `diagrams.is_public`; dùng `pg_trgm` cho tìm kiếm theo tên |
| Hiệu năng danh sách | API danh sách không trả `data` nhưng vẫn đọc cả cột này (tới 2 MB mỗi sơ đồ). Mỗi lần lấy danh sách có thể chạy tới 4 câu lệnh (hai lần SELECT, hai lần COUNT) | Chỉ đọc các cột cần thiết; gộp phần đếm vào một câu lệnh hoặc chuyển sang phân trang theo con trỏ, kèm sắp xếp theo `updated_at` |
| Hiệu năng đọc | Mọi lần xem sơ đồ và mọi request có `X-Share-Token` đều truy vấn CSDL | Cache sơ đồ hay được đọc và kết quả tra token (Caffeine khi chạy một bản, Redis khi chạy nhiều bản), xóa cache khi sơ đồ được sửa hoặc link bị thu hồi |
| Dung lượng dữ liệu | Mỗi lần sửa gửi lại toàn bộ `data`; mỗi phiên bản lưu nguyên một bản sao `data` | Nén response bằng gzip; chỉ gửi phần thay đổi (JSON Patch); lưu phiên bản dạng phần khác biệt hoặc nén |
| Khả năng mở rộng | Chạy một bản app. App không giữ trạng thái phiên (JWT), khóa và `version` nằm trong CSDL | Chạy nhiều bản app sau load balancer (ví dụ `docker compose up --scale app=3` cùng nginx); khi CSDL thành nút thắt thì thêm bản sao chỉ đọc và chỉnh pool kết nối |
| Cộng tác thời gian thực | Hai người cùng sửa thì người lưu sau nhận 409 và phải tải lại sơ đồ | Dùng CRDT (ví dụ Yjs hoặc Automerge) qua WebSocket để các thay đổi tự gộp mà không mất dữ liệu; cấp danh tính khách cho từng người mở link để biết ai đang sửa; khi chạy nhiều bản app cần kênh đồng bộ chung giữa các bản (ví dụ Redis pub/sub) |
| Bảo mật | Không giới hạn số lần đăng nhập sai; link chia sẻ không có hạn dùng; chưa cấu hình CORS nên frontend ở domain khác chưa gọi được API | Giới hạn tần suất đăng nhập; phát hiện refresh token bị dùng lại để thu hồi cả chuỗi token; cho link chia sẻ có thời hạn; cấu hình CORS chỉ cho phép domain của frontend |
| Đo lường | Chỉ có `/health` | Thêm Spring Boot Actuator và Prometheus để theo dõi thời gian xử lý, pool kết nối, tỉ lệ lỗi trong lúc kiểm thử tải |

## 8. Tài liệu liên quan

- [docs/openapi.yaml](docs/openapi.yaml): đặc tả API dạng OpenAPI 3.1. Xem trực quan bằng cách mở file trong [Swagger Editor](https://editor.swagger.io) (File, Import file) hoặc extension OpenAPI của VS Code.
- [docs/luong-nghiep-vu.md](docs/luong-nghiep-vu.md): flowchart các luồng xác thực, phân quyền, cập nhật sơ đồ, phiên bản và chia sẻ.
