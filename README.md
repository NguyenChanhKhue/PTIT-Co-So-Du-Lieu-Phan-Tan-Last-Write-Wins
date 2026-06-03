# Distributed Last Write Wins - Customer Replication Demo

## 1. Tổng quan
Dự án này mô phỏng một hệ thống cơ sở dữ liệu phân tán với mô hình Master-Master Replication, sử dụng chiến lược Last Write Wins (LWW) để xử lý các cập nhật trên nhiều node.

Mục tiêu chính của dự án là minh họa:
- cách dữ liệu được sao chép giữa các node;
- tác động của clock skew (độ lệch đồng hồ) lên quyết định “write nào thắng”;
- hiện tượng một cập nhật mới về mặt thời gian thực có thể bị bỏ qua do lỗi đồng hồ.

## 2. Mục tiêu nghiên cứu
- Xây dựng hệ thống replication giữa 3 node độc lập.
- Áp dụng chiến lược LWW để chọn bản ghi được cập nhật cuối cùng.
- Minh họa rủi ro khi đồng hồ giữa các node lệch nhau.
- Cung cấp giao diện demo giúp quan sát trạng thái cluster và kết quả replication.

## 3. Tính năng chính
- Quản lý khách hàng bằng API REST.
- Sao chép dữ liệu giữa 3 node: Node 1, Node 2, Node 3.
- Theo dõi clock skew và cảnh báo nguy cơ.
- Demo các tình huống clock skew: node nhanh, node chậm.
- Giao diện React/Vite để quan sát trạng thái cluster và thực hiện thao tác viết dữ liệu.

## 4. Kiến trúc hệ thống

### Backend
- Spring Boot 3.1.5
- Spring Web
- Spring Data JPA
- SQLite (mỗi node có cơ sở dữ liệu riêng)
- REST API phục vụ thao tác CRUD và replication

### Frontend
- React + Vite
- Giao diện demo để thao tác và theo dõi cluster

### Mô hình hoạt động
- Node 1: đồng hồ chuẩn
- Node 2: đồng hồ nhanh hơn 120 giây
- Node 3: đồng hồ chậm hơn 120 giây
- Khi một node nhận cập nhật, hệ thống sẽ áp dụng LWW và gửi bản ghi tới các peer còn lại.

## 5. Công nghệ sử dụng
- Java 17
- Spring Boot
- Maven
- SQLite
- React
- Vite
- REST API

## 6. Cấu trúc thư mục

backend/
- src/main/java: code logic backend
- src/main/resources/config: cấu hình cho 3 node
- run-nodes.bat: khởi động toàn bộ cluster

frontend/
- src/: giao diện React
- package.json: dependencies và script chạy app

## 7. Hướng dẫn chạy dự án

### 7.1 Chạy backend cluster
Mở terminal tại thư mục backend:

```bash
cd backend
.\run-nodes.bat
```

Script này sẽ:
1. build dự án Spring Boot;
2. khởi động 3 node trên các cổng 8081, 8082, 8083;
3. tạo dữ liệu SQLite riêng cho từng node.

### 7.2 Chạy frontend
Mở terminal riêng tại thư mục frontend:

```bash
cd frontend
npm install
npm run dev
```

Frontend thường chạy tại:
- http://localhost:5173

## 8. API chính

### Customer APIs
- POST /api/customer: tạo hoặc cập nhật khách hàng
- GET /api/customer/{cid}: lấy khách hàng theo CID
- GET /api/customers: lấy toàn bộ khách hàng
- DELETE /api/customers: xóa toàn bộ dữ liệu demo

### Replication APIs
- POST /api/replicate: nhận dữ liệu replication từ node khác

### Monitoring APIs
- GET /api/node/info: thông tin node, clock skew, số lượng bản ghi
- GET /api/health: trạng thái sức khỏe của node

## 9. Kịch bản demo để báo cáo
1. Khởi động toàn bộ 3 node bằng script backend.
2. Mở frontend và chọn node để ghi dữ liệu.
3. Thực hiện cập nhật trên Node 2 (fast clock) và Node 1 (normal clock).
4. Quan sát rằng dữ liệu có thể bị “thắng” hoặc “bị bỏ qua” do lệch đồng hồ.
5. Ghi nhận kết quả để minh họa vấn đề của LWW trong môi trường phân tán.

## 10. Kết quả mong đợi
Sau khi chạy demo, người xem sẽ thấy:
- dữ liệu được sao chép qua 3 node;
- clock skew ảnh hưởng đến quyết định chọn bản ghi thắng;
- mô hình LWW có thể dẫn đến kết quả không “thật sự đúng theo thời gian thực” khi đồng hồ lệch nhau.

## 11. Ghi chú
- Đây là một demo học thuật, không phải hệ thống production-ready.
- Mục tiêu chính là minh họa nguyên lý replication và nhược điểm của LWW khi clock skew tồn tại.
- Nếu cần, có thể mở rộng bằng cách thay thế LWW bằng vector clock, Lamport clock hoặc CRDT để cải thiện tính nhất quán.

## 12. Tóm tắt báo cáo
Dự án này trình bày một mô hình replication phân tán với 3 node, sử dụng SQLite và Spring Boot, và minh họa rõ ràng rằng trong hệ thống phân tán, độ lệch đồng hồ có thể làm sai lệch quyết định cập nhật cuối cùng. Đây là một ví dụ phù hợp để thuyết trình về cơ chế Last Write Wins, replication và giới hạn của nó trong môi trường thực tế.
