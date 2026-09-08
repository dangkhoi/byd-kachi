# HUD-nav coding recipe & honest end-state (xe owner) — 2026-08-19

> **Loại:** Diagnostics · **Trạng thái:** Current · Kết quả sau khi RE cạn toàn bộ image local + khảo sát cộng đồng.
> Bổ sung cho `factory-hud-nav-RE-avenues-2026-08-19.md`. Chốt: transport MỞ, nhưng **3 bí mật coding NẰM NGOÀI image** (MCU firmware/dealer ODX).

## A. TRANSPORT để coding cụm — ĐÃ CÓ (proven, no-root)
- **CAN inject**: `am broadcast -a com.byd.cluster.spi --es normal '<bytes>'` (hoặc `--es wholeFrame`) → `ClusterDebug` → `BYDAutoTestDevice.set(0x AA00020F, frame)` → MCU → CAN. **Không root, không OBD dongle.** (`jadx-ClusterDebug` xe mình có `broadcastToCAN()` + receiver.)
- **UDS trên đó**: frame format đã giải mã (checksum · total/packet · header `00 03 E8` · req/recv CAN-ID · payload); lệnh `3E 80`, `10 03/05`, `22` read-DID, `2E` write-DID, `27` security.
- **Seed→key gateway** LỘ: `key = t ^ 0x6500001E`, `t=(s>>2)^(((s>>1)^s)<<3)` (`ObdDataManager.k`) — cho **gateway `0x720/0x747`**.

## B. 3 BÍ MẬT còn THIẾU (không có trong image — đã RE cạn)
| # | Bí mật | Trạng thái |
|---|--------|-----------|
| 1 | **Diag CAN-ID của CỤM instrument** (trong 6 mạng) | ❌ chưa định danh — image chỉ có gateway `0x720/0x747`, IPB `0x782` |
| 2 | **Security-access seed→key của CỤM** | ❌ chỉ có key **gateway**; key cụm không có trong app nào |
| 3 | **Coding DID + giá trị bật HUD-nav** (vehicleCode/equipment) | ❌ proprietary; cơ chế biết (`BusinessSelfStudy` vehicleCode `40d`: 138=0x8A/162=0xA2) nhưng bảng/giá-trị KHÔNG có |

**Đã RE cạn local:** `BydDevelopmentTools` (UDS transport + gateway key/DID), `BydHealthDiagnostic` (chỉ app xem DTC, `DiagnosticSecretCodeActivity`=viewer, không coding), `DiCarServer` (định nghĩa feature-id, coding ở MCU), cluster libs (render + config store, mapping ở MCU firmware). **3 bí mật nằm ở: cluster MCU firmware / ODX dealer / DB dealer — KHÔNG ở tầng Android.**

## C. RECIPE tốt nhất hiện tại (đánh dấu GIẢ ĐỊNH)
Nếu/khi có 3 bí mật, chuỗi thử (no-root, qua ClusterDebug CAN-inject đóng gói UDS):
1. `10 03` extended session tới **[diag-ID cụm — CHƯA BIẾT]**.
2. `27 01` xin seed → `27 02` gửi key **[thuật toán key cụm — CHƯA BIẾT]**.
3. `22 <DID>` đọc trạng thái equipment/vehicleCode hiện tại (**đọc-only, an toàn**).
4. `2E <DID> <value>` ghi cờ HUD-nav = **[DID + value — CHƯA BIẾT]** → trigger self-study → `0x38B00030=1`.
5. Bật app → xem HUD.
**Bước AN TOÀN chạy ĐƯỢC NGAY (không cần bí mật):** `getraw instr 30100030` (CONFIG_STATUS) + `38B0002E` (STATUS) + `getHudSupportedModes` (C6) — chẩn đoán, không ghi.

## D. Lấy 3 bí mật ở đâu (điều kiện mở khoá)
1. **Sniff CAN/UDS lúc tool chính hãng/CN code** HUD-nav trên 1 xe (cần tiếp cận phiên coding thật) → lộ diag-ID + DID + value; key có thể suy từ seed/key trao đổi.
2. **RE firmware MCU CỤM** từ community factory image (`github.com/BYDcar/BYDPackagesByChip*`, `BYDRepairManual`) — bảng vehicleCode→equipment nằm trong đây; deep Ghidra, chưa chắc, và **KHÔNG có trong cache hiện tại**.
3. **Cộng đồng/CN公布** giá trị equipment coding (chưa thấy công khai; XDA `byd-seal-hud-missing-features` là thread tham chiếu; `DeBondor/byd-unlocking-guide` cho ADB-unlock, không phải coding cụm).

## E. Bối cảnh (thành thật)
- HUD-nav là feature **CN mặc định BẬT / bản xuất khẩu TẮT** (XDA xác nhận) → coding-gated theo vùng, **không phải giới hạn phần cứng**.
- BYD đang **siết theo vùng** (khoá cổng OBD, chặn app third-party — evparts4x4 2026) → cửa coding **hẹp dần**.
- **Kết:** đường coding **kỹ thuật là THẬT + transport đã mở**, nhưng **bế tắc ở 3 bí mật proprietary** — cần **sniff phiên coding thật / RE MCU firmware / cộng đồng công bố**. Ngoài khả năng suy ra chỉ từ image local. **KHÔNG phải "không thể", mà là "chưa có 3 bí mật + cần nguồn ngoài".**

## F. Phương án chắc chắn (nhắc lại)
- **HUD Taobao (HUD BYD rời)** — anh em đã chạy với app này (mũi tên+cự ly); né hẳn coding cụm. Đây là đường **đã chứng minh** nếu mục tiêu là "nav lên kính".

---

## Cập nhật 2026-08-19 (chiều-3) — TOOL coding thực tế: VDS2100 (đào `github.com/BYDcar`)

**`github.com/BYDcar`** (7 repo, cập nhật 2023): `opendbc-byd` chỉ có **1 DBC BYD thật = `byd_tang_phev_2015.dbc`** (Tang PHEV 2016, cũ) — **KHÔNG có Seal/DiLink3, không tín hiệu HUD-nav/cụm**. `BYDRepairManual` có `仪表盘固件` (firmware cụm) + `维修手册` (repair manual) + `软件` — firmware/PDF cũ, chục GB, tiếng Trung; coding table có thể chôn trong đó nhưng là đào tay lớn.

**2 intel giá trị:**
1. **`VDS2100`** = máy chẩn đoán chính hãng BYD (do **Autel** làm, **"full coding programming system"**), **tiệm độc lập MUA được** (Alibaba/Maverick/KKS); third-party **XTOOL** cũng hỗ trợ BYD. ⇒ **Dealer từ chối = chính sách, nhưng thợ độc lập với VDS2100/Autel/XTOOL có thể coding equipment/vehicleCode của cụm.**
2. **CAN cổng OBD2 bị LỌC** (opendbc-byd README: candump→canplay không phản ứng) → inject từ OBD ngoài khó (gateway lọc); **ClusterDebug CAN-inject trong xe (sau gateway, no-root) là đường tốt hơn** cho phần test.

**⇒ Đường thực tế nhất cho HUD-nav xe owner:** mang xe tới **tiệm có VDS2100/Autel/XTOOL**, yêu cầu **bật equipment/coding "HUD-navigation" cho cụm đồng hồ**. Đích cụ thể (nói với thợ): cờ `0x38B00030` / equipment self-study `vehicleCode`.

**Caveat (trace-den-tan-cung):** chưa có quy trình công khai "VDS2100 → HUD-nav trên Seal"; thợ phải tự tìm mục equipment. Và **nếu firmware cụm trim này KHÔNG có option HUD-nav** thì tool cũng chịu → **phải chạy C6 probe trước** (`getHudSupportedModes` / status regs) để biết cụm có hỗ trợ nav không. Nếu không → HUD BYD rời (đường anh em).

**Trạng thái đào:** RE local CẠN + community (byd-dolphin-hacking / BYDcar / XDA / VDS2100) CẠN. Đích + tool + transport đều đã định danh; phần còn lại là **việc thế giới thực** (chạy C6 probe → nếu cụm hỗ trợ nav thì tìm thợ VDS2100; nếu không thì HUD rời).
