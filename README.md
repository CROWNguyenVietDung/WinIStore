# WinIStore (Main Project)

WinIStore la ung dung Spring Boot chinh trong repository, phu trach:

- Cung cap REST API cho user/admin
- Serve giao dien web tinh tai `src/main/resources/static`
- Quan ly du lieu voi MySQL qua JPA
- Tich hop thanh toan VNPay (sandbox)

## 1) Tech stack

- Java 17
- Spring Boot
- Spring Web MVC
- Spring Data JPA
- Spring Security
- Hibernate + MySQL Connector
- Maven Wrapper (`mvnw`, `mvnw.cmd`)

## 2) Project structure

```text
WinIStore/
|- src/main/java/com/winistore/win/
|  |- config/         # Security, CORS, static uploads mapping
|  |- controller/     # Public/Admin/Auth APIs
|  |- service/        # Business logic (order placement, auth, vnpay)
|  |- repository/     # JPA repositories
|  |- model/
|  |  |- entity/      # JPA entities
|  |  |- enums/       # OrderStatus, Role, PaymentMethod...
|  |- dto/            # Request/Response DTOs
|  |- WinIStoreApplication.java
|- src/main/resources/
|  |- application.properties
|  |- static/         # login.html, user.html, admin/dashboard.html...
|- mvnw
|- mvnw.cmd
|- pom.xml
```

## 3) Main modules

### Authentication

- `POST /api/auth/register`
- `POST /api/auth/login`
- `GET /api/auth/users/{id}`
- `PUT /api/auth/users/{id}/profile`
- `POST /api/auth/avatar`
- `POST /api/auth/avatar/upload`

### Public APIs

- Products: `/api/public/products/**`
- Orders: `/api/public/orders/**`
- Vouchers: `/api/public/vouchers/**`
- Repair appointments: `/api/public/repair-appointments/**`
- VNPay: `/api/public/payments/vnpay/**`

### Admin APIs

- Products: `/api/admin/products/**`
- Orders: `/api/admin/orders/**`
- Vouchers: `/api/admin/vouchers/**`
- Customers: `/api/admin/customers/**`
- Repair appointments: `/api/admin/repair-appointments/**`
- Product review monitor: `/api/admin/**` (one-star alerts, mark read)

## 4) Database

Script chinh nam o `../DataBase/schema.sql`, bao gom:

- Tao schema `WinIStore`
- Tao bang: `User`, `Address`, `Category`, `Product`, `ProductReview`, `Order`, `OrderDetail`, `Voucher`, `RepairAppointment`, `RepairAppointmentImage`, `DeletedCustomerHistory`
- Seed du lieu test ban dau

Neu cap nhat tren DB cu, chay them:

- `../DataBase/alter_order_voucher_receipt_2026.sql`

## 5) Configuration

File: `src/main/resources/application.properties`

Can dieu chinh toi thieu:

- `spring.datasource.url`
- `spring.datasource.username`
- `spring.datasource.password`

VNPay co the set qua env vars:

- `VNPAY_TMN_CODE`
- `VNPAY_HASH_SECRET`
- `VNPAY_PAY_URL`
- `VNPAY_RETURN_URL`
- `VNPAY_FE_RETURN_URL`

## 6) Run locally

### Mac/Linux

```bash
./mvnw spring-boot:run
```

### Windows PowerShell

```powershell
.\mvnw.cmd spring-boot:run
```

Sau khi app chay:

- Login: `http://localhost:8080/login.html`
- User page: `http://localhost:8080/user.html`
- Admin dashboard: `http://localhost:8080/admin/dashboard.html`

## 7) Default test accounts

Tu seed trong `../DataBase/schema.sql`:

- Admin: `admin@gmail.com` / `admin123`
- User: `nguyendung@gmail.com` / `user123`

## 8) Uploads

App map static uploads qua `UploadsResourceConfig`:

- URL: `/uploads/**`
- Folder thuc te: `<project_root>/uploads/`

Bao gom:

- Anh san pham: `/uploads/products/...`
- Anh lich sua: `/uploads/repair-appointments/...`

## 9) Security note

Hien tai `SecurityConfig` dang `permitAll` cho request ngoai tru flow auth chi tiet theo role.
Neu deploy production, can bo sung:

- JWT/session auth day du
- Role-based authorization cho toan bo `/api/admin/**`
- Validation/cac rule hardening bo sung

