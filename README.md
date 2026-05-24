# Auction System Project
<!-- Fixed auction session persistence and UI navigation issues -->
Hệ thống đấu giá trực tuyến được xây dựng trên nền tảng **Java OOP**, áp dụng các Design Patterns chuyên sâu và mô hình **Client-Server (Socket)** để đảm bảo tính thời gian thực.

## Vai trò và Phân công nhiệm vụ

### 1. Quản lý Người dùng (User System) - Triệu Quốc Huân
* **Trách nhiệm:** Thiết kế cấu trúc phân cấp User (Admin, Bidder, Seller).
* **Nhiệm vụ:** Xây dựng logic đăng ký/đăng nhập, phân quyền truy cập và giao diện người dùng bằng JavaFX.

### 2. Quản lý Hàng hóa (Item System) - Đặng Gia Khánh
* **Trách nhiệm:** Lo toàn bộ kho hàng và danh mục sản phẩm.
* **Nhiệm vụ:** Áp dụng **Factory Pattern** để khởi tạo các loại hàng hóa đa dạng (Electronics, Art, Vehicle...). Quản lý chức năng đăng bài và sửa thông tin cho Seller.

### 3. Vận hành Đấu giá (Auction Engine) - Trần Mạnh Đức
* **Trách nhiệm:** "Trọng tài" điều phối toàn bộ phiên đấu giá.
* **Nhiệm vụ:** Xử lý logic đặt giá (người sau cao hơn người trước), cơ chế gia hạn tự động (`extendTime`) và cập nhật trạng thái phiên thực tế.

### 4. Kết nối & Nâng cao (Network & Features) - Tống Trung Kiên
* **Trách nhiệm:** Kỹ sư hệ thống và kết nối mạng.
* **Nhiệm vụ:** Thiết kế khung **Socket (Client-Server)**, xây dựng tính năng tự động đấu giá (Auto-bidding) và trực quan hóa dữ liệu bằng biểu đồ JavaFX.

---

## 🏗 Kiến trúc Hệ thống (System Architecture)

### 1. Quản lý Người dùng & Ví tiền
* **Phân quyền:** Admin (duyệt đồ, ban user), Seller (điểm danh tiếng), Bidder (địa điểm nhận hàng).
* **Ví tiền (Wallet):** Tích hợp cơ chế **frozenAmount** (tạm khóa tiền) khi người dùng đặt giá để đảm bảo tính minh bạch và an toàn giao dịch.

### 2. Phân loại Hàng hóa đa dạng
Sử dụng kế thừa để quản lý các thuộc tính đặc thù:
* **Electronics:** Warranty, Model.
* **Vehicle:** Mileage, Engine type.
* **Art:** Artist, Year, Technique.

### 3. Luồng Vận hành (Auction Flow)
* **Trạng thái phiên:** Kiểm soát chặt chẽ qua các trạng thái: `Pending`, `Open`, `Extended`, `Success`, `Failed`, `Cancelled`.
* **Nhật ký giao dịch:** Mọi lượt đặt giá đều được lưu vết qua `BidTransaction` để phục vụ việc tra cứu và đối soát.
* **Thông báo:** Hệ thống tự động gửi Notification khi người dùng bị vượt giá hoặc thắng đấu giá.

---

## 🛠 Công nghệ sử dụng
* **Ngôn ngữ:** Java 17+
* **Giao diện:** JavaFX
* **Kết nối:** TCP Socket
* **Design Patterns:** Singleton, Factory, Observer