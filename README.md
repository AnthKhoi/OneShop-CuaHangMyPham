# OneShop — Website Bán Mỹ Phẩm Theo Mô Hình Chuỗi Cửa Hàng

> **Đồ án môn học / Hệ thống thương mại điện tử chuyên nghiệp**  
> **Ngôn ngữ & Nền tảng:** Java 21 (LTS) • Spring Boot 3.3.4 • Spring Data JPA • Microsoft SQL Server • Thymeleaf • SiteMesh 3 • Spring Security & JWT • Cloudinary • Bootstrap 5

---

## 1. Giới thiệu tổng quan đề tài

**OneShop** là hệ thống website thương mại điện tử chuyên ngành mỹ phẩm cao cấp được thiết kế theo đúng **mô hình chuỗi cửa hàng (Multi-branch Retail Chain)**:
- **Dữ liệu danh mục dùng chung:** Danh mục sản phẩm, thương hiệu, giá niêm yết được đồng bộ toàn hệ thống.
- **Tồn kho phân tán theo chi nhánh (`BranchInventory`):** Số lượng tồn kho vật lý (`quantity_on_hand`) và tồn khả dụng (`availableQuantity`) được theo dõi chi tiết theo từng cặp `(branch_id, product_id)` có khóa duy nhất (`uk_branch_product`).
- **Snapshot đơn hàng bất biến (`OrderItem`):** Mọi dòng hàng trong đơn lưu snapshot tên sản phẩm, SKU và đơn giá tại thời điểm khách đặt mua. Khi danh mục thay đổi giá hoặc thông tin, lịch sử đơn hàng cũ hoàn toàn không bị ảnh hưởng.
- **Nhật ký biến động kho (`StockMovement`):** Mọi thao tác nhập hàng, xuất bán lẻ, hoàn trả do hủy đơn hoặc điều chỉnh kiểm kê đều được ghi nhận tự động vào bảng nhật ký với người thực hiện, lý do bắt buộc và số lượng biến động.
- **Phân quyền và cô lập phạm vi chi nhánh:** Nhân viên hoặc Quản lý chi nhánh A chỉ được xem và xử lý dữ liệu đơn hàng/kho hàng thuộc chi nhánh A. Chỉ tài khoản `ROLE_ADMIN` mới có toàn quyền trên toàn hệ thống.

---

## 2. Công nghệ bắt buộc & Kiến trúc hệ thống

| Thành phần | Công nghệ sử dụng | Ghi chú kỹ thuật |
| :--- | :--- | :--- |
| **Ngôn ngữ** | Java 21 (LTS) | Phiên bản Java tiêu chuẩn hiện đại |
| **Framework** | Spring Boot 3.3.4 (Spring MVC) | Cấu hình tự động, RESTful & MVC |
| **ORM / Data** | Spring Data JPA / Hibernate 6 | Mapping quan hệ, Optimistic Locking `@Version` |
| **Database** | Microsoft SQL Server 2022 | Database chính thức, tiền tệ dùng `DECIMAL(18,2)` / `BigDecimal` |
| **View Engine** | Thymeleaf 3 | Server-side HTML rendering |
| **Layout Decorator** | SiteMesh 3 (Jakarta compatible) | Quản lý layout Storefront & Admin riêng biệt |
| **Bảo mật & Auth** | Spring Security 6 + JJWT (0.12.6) | Access Token lưu trong HttpOnly Cookie an toàn |
| **Lưu trữ hình ảnh** | Cloudinary Service | Upload ảnh mỹ phẩm, lưu URL an toàn kèm cơ chế fallback |
| **Giao diện** | Bootstrap 5 + Custom CSS OneShop | Gam màu mỹ phẩm cao cấp: Trắng kem, Charcoal, Hồng đất |
| **Build Tool** | Apache Maven & Maven Wrapper (`mvnw`) | Tương thích hoàn toàn IntelliJ IDEA |

---

## 3. Cấu trúc thư mục mã nguồn

```text
Project_ITWeb/
├── pom.xml                               # File cấu hình build Maven & thư viện
├── mvnw / mvnw.cmd                       # Maven wrapper giúp chạy không cần cài sẵn maven
├── .env.example                          # File mẫu cấu hình biến môi trường
├── src/
│   ├── main/
│   │   ├── java/com/oneshop/
│   │   │   ├── OneShopApplication.java   # Lớp khởi chạy Spring Boot chính
│   │   │   ├── config/                   # Cấu hình SiteMesh, WebMvc, DecoratorController
│   │   │   ├── security/                 # JWT Token Provider, JWT Filter, UserPrincipal, SecurityConfig
│   │   │   ├── common/                   # DataInitializer (Seed Data), ExceptionHandler, ModelAttributes
│   │   │   ├── auth/                     # Controller & Service Đăng ký, Đăng nhập, Đăng xuất
│   │   │   ├── catalog/                  # Product, Category, Brand, ProductImage & Controllers
│   │   │   ├── branch/                   # Quản lý 4 chi nhánh cửa hàng
│   │   │   ├── inventory/                # BranchInventory, StockMovement, Quản lý kho, Cảnh báo tồn thấp
│   │   │   ├── cart/                     # Giỏ hàng Session, cập nhật số lượng, kiểm tra tồn
│   │   │   ├── order/                    # Đơn hàng, OrderItem snapshot, thanh toán COD/Demo, Checkout
│   │   │   ├── promotion/                # Quản lý Voucher / Coupon giảm giá (WELCOME50, ONESHOP10)
│   │   │   ├── dashboard/                # Báo cáo doanh thu, AOV, cảnh báo tồn kho Dashboard
│   │   │   ├── user/                     # Quản lý tài khoản, User, Role
│   │   │   └── storage/                  # Cloudinary Service upload hình ảnh
│   │   └── resources/
│   │       ├── application.yml           # Cấu hình SQL Server, JWT, HikariCP, Cloudinary
│   │       ├── schema.sql                # Script DDL SQL Server chuẩn
│   │       ├── static/
│   │       │   ├── css/oneshop.css       # Design System nhận diện thương hiệu OneShop
│   │       │   ├── css/admin.css         # Giao diện Back-office Admin hiện đại
│   │       │   └── js/oneshop.js         # Xử lý tăng giảm số lượng, alerts, tương tác
│   │       └── templates/
│   │           ├── decorators/           # SiteMesh layout Storefront & Admin
│   │           ├── fragments/            # Header, Footer, Admin Sidebar, Topbar, Alerts
│   │           ├── store/                # Trang chủ, Danh mục, Chi tiết sp, Giỏ hàng, Checkout, Account
│   │           ├── admin/                # Dashboard, CRUD Sản phẩm, Kho, Đơn hàng, Chi nhánh, Users
│   │           └── error/                # Trang lỗi thân thiện 403, 404, 500
│   └── test/                             # 10 bài Test JUnit 5 kiểm thử giá, tồn kho, bảo mật
```

---

## 4. Hướng dẫn mở và chạy dự án bằng IntelliJ IDEA

### Bước 1: Mở dự án trong IntelliJ IDEA
1. Khởi động **IntelliJ IDEA**.
2. Chọn **File -> Open...** và duyệt đến thư mục `Project_ITWeb` (`c:\Users\One-Tura\Downloads\Project_ITWeb`).
3. Chọn mở dự án theo dạng **Maven Project**.
4. IntelliJ sẽ tự động nhận diện tệp `pom.xml` và tải các dependencies.

### Bước 2: Thiết lập JDK 21 trong IntelliJ
1. Vào **File -> Project Structure... -> Project**.
2. Tại mục **SDK**, chọn **JDK 21** (đã cài đặt tại `C:\Program Files\Java\jdk-21`).
3. Tại **Language level**, chọn **21 - Record patterns, pattern matching...**.
4. Nhấn **Apply -> OK**.

### Bước 3: Cấu hình cơ sở dữ liệu SQL Server
Hệ thống đã tự động tạo sẵn database `OneShopDB` và tài khoản `oneshop_user` trên SQL Server local:
- **Server:** `localhost:1433`
- **Database:** `OneShopDB`
- **Username:** `oneshop_user`
- **Password:** `OneShop@2026#Strong`

*(Nếu cần kiểm tra hoặc chạy lại bằng Windows Authentication, mở SQL Server Management Studio (SSMS) và thực thi script tại `src/main/resources/schema.sql`).*

### Bước 4: Chạy ứng dụng
- Trong cửa sổ IntelliJ IDEA, mở tệp `src/main/java/com/oneshop/OneShopApplication.java`.
- Nhấp chuột phải và chọn **Run 'OneShopApplication'** (hoặc nhấn tổ hợp phím `Shift + F10`).
- Hoặc mở terminal trong IntelliJ và chạy:
  ```bash
  .\mvnw.cmd spring-boot:run
  ```
- Ứng dụng sẽ khởi động tại địa chỉ: **`http://localhost:8080`**

---

## 5. Danh sách tài khoản mẫu để trình diễn & chấm điểm

Hệ thống đã tự động nạp sẵn dữ liệu khởi tạo (`DataInitializer`) với mật khẩu được mã hóa BCrypt:

| Vai trò (Role) | Email đăng nhập | Mật khẩu | Chi nhánh phân công | Quyền hạn chính |
| :--- | :--- | :--- | :--- | :--- |
| **ROLE_ADMIN** | `admin@oneshop.vn` | `Admin@123` | *Toàn hệ thống* | Xem báo cáo toàn chuỗi, quản lý sản phẩm, danh mục, chi nhánh, phân quyền người dùng. |
| **ROLE_BRANCH_MANAGER** | `manager.q1@oneshop.vn` | `Manager@123` | **OneShop Quận 1 - TP.HCM** | Báo cáo doanh số chi nhánh Q1, quản lý đơn hàng & kho hàng của riêng chi nhánh Q1. |
| **ROLE_SALES_STAFF** | `sales.q1@oneshop.vn` | `Sales@123` | **OneShop Quận 1 - TP.HCM** | Xem và cập nhật trạng thái xử lý đơn hàng tại chi nhánh Q1. |
| **ROLE_CUSTOMER** | `customer@gmail.com` | `Customer@123` | *Khách hàng* | Đặt hàng, áp dụng coupon, xem và quản lý lịch sử đơn hàng cá nhân. |

> **Mẹo hữu ích khi demo:** Tại trang đăng nhập `/auth/login`, hệ thống có sẵn các nút bấm **"Tài khoản Demo đồ án"** giúp nhấp một chạm để tự động điền thông tin đăng nhập mà không cần gõ phím thủ công.

---

## 6. Danh sách 4 chi nhánh cửa hàng OneShop trong hệ thống

1. **OneShop Quận 1 - TP. Hồ Chí Minh** (`CN-HCM-Q1`): 120 Hai Bà Trưng, Phường Bến Nghé, Quận 1, TP. HCM (SĐT: 028 3822 9999).
2. **OneShop Cầu Giấy - Hà Nội** (`CN-HN-CG`): 245 Cầu Giấy, Phường Dịch Vọng, Quận Cầu Giấy, Hà Nội (SĐT: 024 3765 8888).
3. **OneShop Hải Châu - Đà Nẵng** (`CN-DN-HC`): 78 Lê Duẩn, Phường Thạch Thang, Quận Hải Châu, Đà Nẵng (SĐT: 0236 388 7777).
4. **OneShop Ninh Kiều - Cần Thơ** (`CN-CT-NK`): 56 Đường 30 Tháng 4, Phường An Phú, Quận Ninh Kiều, Cần Thơ (SĐT: 0292 376 6666).

---

## 7. Mã giảm giá (Coupons) dùng thử

- **`WELCOME50`**: Giảm ngay `50.000đ` cho đơn hàng từ `300.000đ`.
- **`ONESHOP10`**: Giảm `10%` tối đa `100.000đ` cho đơn hàng từ `500.000đ`.
- **`BEAUTYVIP`**: Giảm `15%` tối đa `200.000đ` cho đơn hàng VIP từ `1.000.000đ`.

---

## 8. Kết quả kiểm thử tự động (Unit & Integration Tests)

Hệ thống đi kèm bộ kiểm thử toàn diện thực thi với JUnit 5:
- **`TC-01 -> TC-04` (`OneShopPricingAndCartTest`):** Tính toán tổng tiền ở backend, mức phí vận chuyển phân tầng (miễn phí đơn &ge; 500k, 30k đơn nhỏ), chiết khấu voucher phần trăm có trần giới hạn và voucher cố định.
- **`TC-05 -> TC-08` (`OneShopInventoryAndOrderTest`):** Khấu trừ tồn kho giao dịch, chặn đặt hàng vượt số lượng tồn khả dụng (ngăn chặn hoàn toàn tồn âm), bắt buộc nhập lý do khi điều chỉnh kho, hoàn trả tồn kho tự động khi hủy đơn.
- **`TC-09 -> TC-10` (`OneShopSecurityAndAccessTest`):** Kiểm tra phân quyền `UserPrincipal`, sinh và giải mã chữ ký xác thực JWT HMAC-SHA256.

Chạy kiểm thử bằng lệnh:
```bash
.\mvnw.cmd test
```
*Kết quả:* `Tests run: 10, Failures: 0, Errors: 0, Skipped: 0` &mdash; **BUILD SUCCESS**.
