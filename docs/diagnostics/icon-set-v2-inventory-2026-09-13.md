# U7 — Kiểm kê mã CÓ VỊ TRÍ → khung xe → vùng tô (bộ icon v2)

> **Status**: Current · **Date**: 2026-09-13 · **Type**: Diagnostics (kiểm kê + bảng tra, phục vụ implement) ·
> **Purpose**: Bảng kê MỌI mã trong `TelemetryRegistry` / `ControlRegistry` / `ActionMacros` có mang **vị trí**
> (cửa · kính · lốp · ghế · gương · xi-nhan · điểm mù · cảm biến đỗ · đèn viền trước/sau…), **loại đèn**, và
> **vùng ADAS**, cùng ánh xạ sang *khung xe nào* (top/front/rear) và *vùng nào được tô*. Đây là đầu vào T1 của
> spec `docs/specs/kachi-icon-set-v2.html`; T2 (vẽ) và T3 (bảng tra) đọc thẳng từ bảng này.
> **Spec**: `docs/specs/kachi-icon-set-v2.html` · **Owner**: KhoiPD

> **(EN)** Inventory of every capability id that carries a POSITION, mapped to one of three shared car frames
> (top / front / rear) and the region that gets filled. Input for the U7 positional icon set.

## 0 · Kết luận một dòng

[ĐO] 2026-09-13 — đo trên `CapabilityCatalog.all()` (ô bộ chọn thật sự bày ra, đã lọc mã cố ý ẩn):

| nhóm | ô | hình TRƯỚC | hình SAU | max ô/hình TRƯỚC | max ô/hình SAU |
|---|---:|---:|---:|---:|---:|
| Năng lượng | 32 | 15 | 15 | 3 | 3 |
| Động lực | 19 | 12 | 12 | 3 | 3 |
| Khí hậu | 23 | 12 | 12 | 3 | 3 |
| **Lốp** | 8 | 2 | **8** | **4** | **1** |
| **Thân xe** | 40 | 15 | **26** | **10** | **3** |
| **Đèn** | 22 | 4 | **16** | **14** | **2** |
| **An toàn · ADAS** | 28 | 9 | **24** | **9** | **2** |
| **Danh tính** | 9 | 4 | **7** | **4** | **3** |
| Giải trí | 9 | 5 | 5 | 3 | 3 |
| **TỔNG** | **190** | **65** | **117** | — | — |

Năm nhóm in đậm là đúng năm nhóm `CapabilityIconsDiversityTest.pendingCeiling` ghi nợ sau U6. Cả năm nay **về
trong trần 3** ⇒ bảng nợ đó rỗng, và trần cứng áp cho **cả 8 lĩnh vực**.

## 1 · Ba KHUNG XE dùng chung

Khai **một lần** trong `scripts` sinh icon (xem §5) rồi mọi tệp dùng lại nguyên văn — không tệp nào tự vẽ lại
thân xe, vì lệch 0.2 đơn vị là nhìn ra được khi hai icon nằm cạnh nhau trong lưới.

| khung | path (viewport 24) | dùng cho |
|---|---|---|
| **TOP** (nhìn từ trên) | thân thuôn mũi, `x 7.9..16.1 · y 3.4..20.6`, + hai vệt kính lái/kính hậu | cửa · kính · lốp · ghế · người ngồi · dây an toàn · gương · cốp · ca-pô · nóc · rèm · khoá · đèn viền · đèn đọc · mọi vùng ADAS · cảm biến đỗ |
| **FRONT** (nhìn từ trước) | vỏ `M4.4,19.4 L4.4,12.4 L6.6,7.4 L17.4,7.4 L19.6,12.4 L19.6,19.4 Z` + đường nắp ca-pô | đèn pha · cốt · sương mù trước · ban ngày · xi-nhan · đèn hông · chế độ đèn · gạt mưa · cảnh báo va chạm trước |
| **REAR** (nhìn từ sau) | cùng vỏ + kính hậu + **biển số** (chi tiết duy nhất phân biệt với FRONT) | đèn sương mù sau (và — khi registry có mã — đèn hậu · phanh · lùi) |

**Ngữ pháp hình**: `NÉT = vật thể` (thân xe, khung), `VÙNG TÔ = bộ phận đang được nói tới`. Không gradient,
không màu riêng, không đổ bóng — icon phải tint được theo chủ đề sáng/tối và theo trạng thái ô.

**Dùng lại cho widget board**: ba path khung này là **cùng hình học** mà `TyreBoardView` · `RadarBoardView` ·
`SideBoardView` đang vẽ tay bằng Canvas ở cỡ lớn. Lượt sau (task riêng — U7 **không** chạm Canvas view) nên rút
ba path này ra một hằng dùng chung để bảng lớn và icon nhỏ là **một chiếc xe**, không phải hai chiếc khác nhau.

## 2 · Quy ước hậu tố THẬT trong registry (đọc từ mã, không suy đoán)

⚠ Bộ đăng ký dùng **bốn** quy ước khác nhau cho cùng một góc xe — đây là bẫy số 1 của lượt này:

| họ | quy ước | ví dụ |
|---|---|---|
| TPMS | `fl · fr · rl · rr` | `tyre_p_fl` · `tyre_t_rr` |
| thân xe (kính/cửa) | `lf · rf · lr · rr` (**đảo thứ tự chữ!**) | `window_lf` · `door_rr` · `win_lr` |
| ADAS | `left · right` | `lca_left` · `dow_right` · `rcta_left` |
| điểm mù | `fl · fr` **giữa** mã | `bsd_fl_alarm` |
| ghế | `driver · passenger` | `seatbelt_driver` · `oms_passenger` |
| đèn viền | `front · rear` | `ambient_front_color` |

`CapabilityIconPositionTest` chuẩn hoá cả sáu về một thang `Corner` rồi so với hậu tố tên hình — nên thêm một mã
ở quy ước nào cũng bị soi, và không có danh sách chép tay nào phải giữ đồng bộ.

## 3 · Bảng kê đầy đủ — mã ↔ nhãn ↔ loại ↔ hình (5 nhóm có vị trí)

```
## TYRES
  tyre_p_fl                  Áp lốp trước-trái            xem  ic-car-top-tyre-fl               VỊ TRÍ
  tyre_p_fr                  Áp lốp trước-phải            xem  ic-car-top-tyre-fr               VỊ TRÍ
  tyre_p_rl                  Áp lốp sau-trái              xem  ic-car-top-tyre-rl               VỊ TRÍ
  tyre_p_rr                  Áp lốp sau-phải              xem  ic-car-top-tyre-rr               VỊ TRÍ
  tyre_t_fl                  Nhiệt lốp trước-trái         xem  ic-car-top-tyre-temp-fl          VỊ TRÍ
  tyre_t_fr                  Nhiệt lốp trước-phải         xem  ic-car-top-tyre-temp-fr          VỊ TRÍ
  tyre_t_rl                  Nhiệt lốp sau-trái           xem  ic-car-top-tyre-temp-rl          VỊ TRÍ
  tyre_t_rr                  Nhiệt lốp sau-phải           xem  ic-car-top-tyre-temp-rr          VỊ TRÍ
## BODY
  window_lf                  Kính trước-trái              xem  ic-car-top-window-lf             VỊ TRÍ
  window_rf                  Kính trước-phải              xem  ic-car-top-window-rf             VỊ TRÍ
  window_lr                  Kính sau-trái                xem  ic-car-top-window-lr             VỊ TRÍ
  window_rr                  Kính sau-phải                xem  ic-car-top-window-rr             VỊ TRÍ
  door_lf                    Cửa trước-trái               xem  ic-car-top-door-lf               VỊ TRÍ
  door_rf                    Cửa trước-phải               xem  ic-car-top-door-rf               VỊ TRÍ
  door_lr                    Cửa sau-trái                 xem  ic-car-top-door-lr               VỊ TRÍ
  door_rr                    Cửa sau-phải                 xem  ic-car-top-door-rr               VỊ TRÍ
  tailgate_status            Cốp sau                      xem  ic-car-top-trunk                 
  tailgate_position          Vị trí cốp                   xem  ic-car-top-trunk                 
  sunroof_state              Cửa sổ trời                  xem  ic-car-top-sunroof               
  sunroof_pos                Vị trí cửa sổ trời           xem  ic-car-top-sunroof               
  sunshade_pct               Rèm che nắng                 xem  ic-car-top-sunshade              
  mirror_fold                Gương chiếu hậu              xem  ic-car-top-mirror                
  wiper_state                Gạt mưa                      xem  ic-wiper                         
  power_level                Nguồn xe                     xem  ic-bolt                          
  vehicle_type               Mẫu xe                       xem  ic-car                           
  emergency_alarm            Cảnh báo khẩn                xem  ic-alert                         
  lock                       Khoá / mở khoá               bấm  ic-car-top-lock                  
  window                     Kính cửa lái                 bấm  ic-car-top-window-lf             
  trunk                      Cốp sau                      bấm  ic-car-top-trunk                 
  door                       Mở khoá cửa                  bấm  ic-car-top-door-all              
  hood                       Ca-pô                        bấm  ic-car-top-hood                  
  sunroof                    Cửa sổ trời                  bấm  ic-car-top-sunroof               
  wiper                      Gạt mưa                      bấm  ic-wiper                         
  win_lf                     Kính trước-trái              bấm  ic-car-top-window-lf             VỊ TRÍ
  win_rf                     Kính trước-phải              bấm  ic-car-top-window-rf             VỊ TRÍ
  win_lr                     Kính sau-trái                bấm  ic-car-top-window-lr             VỊ TRÍ
  win_rr                     Kính sau-phải                bấm  ic-car-top-window-rr             VỊ TRÍ
  windows_all                Tất cả kính                  bấm  ic-car-top-window-all            VỊ TRÍ
  sunshade                   Rèm che nắng                 bấm  ic-car-top-sunshade              
  child_lock                 Khoá trẻ em                  bấm  ic-lock                          
  rain_close                 Tự đóng kính khi mưa         bấm  ic-car-top-window-rain           
  mirror_auto                Gập gương khi khoá           bấm  ic-car-top-mirror                
  mirror_fold_btn            Gập gương                    bấm  ic-car-top-mirror                
  seat_memory                Nhớ ghế lái                  bấm  ic-car-top-seat-fl               
## LIGHTS
  light_low_beam             Đèn cốt                      xem  ic-car-front-lowbeam             
  light_high_beam            Đèn pha                      xem  ic-car-front-highbeam            
  light_front_fog            Đèn sương mù trước           xem  ic-car-front-fog                 VỊ TRÍ
  light_rear_fog             Đèn sương mù sau             xem  ic-car-rear-fog                  VỊ TRÍ
  light_left_turn            Xi-nhan trái                 xem  ic-car-front-turn-l              VỊ TRÍ
  light_right_turn           Xi-nhan phải                 xem  ic-car-front-turn-r              VỊ TRÍ
  light_side                 Đèn hông                     xem  ic-car-front-sidelight           
  light_drl                  Đèn ban ngày                 xem  ic-car-front-drl                 
  headlight_feedback         Chế độ đèn pha               xem  ic-car-front-headlight-mode      
  ambient_enabled            Đèn viền cabin               xem  ic-car-top-ambient               
  ambient_front_color        Màu viền trước               xem  ic-car-top-ambient-color-front   VỊ TRÍ
  ambient_rear_color         Màu viền sau                 xem  ic-car-top-ambient-color-rear    VỊ TRÍ
  ambient_front_brightness   Độ sáng viền trước           xem  ic-car-top-ambient-bright-front  VỊ TRÍ
  ambient_rear_brightness    Độ sáng viền sau             xem  ic-car-top-ambient-bright-rear   VỊ TRÍ
  readl                      Đèn đọc                      bấm  ic-readlight                     
  headl                      Đèn pha                      bấm  ic-car-front-highbeam            
  drl                        Đèn ban ngày                 bấm  ic-car-front-drl                 
  ambient_power              Đèn viền cabin               bấm  ic-car-top-ambient               
  ambient_color              Màu đèn viền                 bấm  ic-car-top-ambient-color-front   
  ambient_brightness         Độ sáng viền                 bấm  ic-car-top-ambient-bright-front  
  ambient_music              Đèn viền theo nhạc           bấm  ic-car-top-ambient-music         
  headlight_mode             Chế độ đèn pha               bấm  ic-car-front-headlight-mode      
## SAFETY
  seatbelt_driver            Dây an toàn lái              xem  ic-car-top-belt-fl               VỊ TRÍ
  seatbelt_passenger         Dây an toàn phụ              xem  ic-car-top-belt-fr               VỊ TRÍ
  oms_driver                 Nhận diện tài xế             xem  ic-car-top-occupant-fl           VỊ TRÍ
  oms_passenger              Nhận diện ghế phụ            xem  ic-car-top-occupant-fr           VỊ TRÍ
  child_presence             Phát hiện trẻ em             xem  ic-car-top-occupant-rear         
  speed_limit_warning        Cảnh báo quá tốc             xem  ic-speed                         
  bsd_fl_alarm               Điểm mù trước-trái           xem  ic-car-top-bsd-l                 VỊ TRÍ
  bsd_fr_alarm               Điểm mù trước-phải           xem  ic-car-top-bsd-r                 VỊ TRÍ
  lca_left                   Chuyển làn trái              xem  ic-car-top-lca-l                 VỊ TRÍ
  lca_right                  Chuyển làn phải              xem  ic-car-top-lca-r                 VỊ TRÍ
  rcta_left                  Cắt ngang sau trái           xem  ic-car-top-rcta-l                VỊ TRÍ
  rcta_right                 Cắt ngang sau phải           xem  ic-car-top-rcta-r                VỊ TRÍ
  dow_left                   Mở cửa cảnh báo trái         xem  ic-car-top-dow-l                 VỊ TRÍ
  dow_right                  Mở cửa cảnh báo phải         xem  ic-car-top-dow-r                 VỊ TRÍ
  radar_zones                Cảm biến đỗ (8 vùng)         xem  ic-car-top-park-all              
  radar_volume               Âm lượng cảm biến            xem  ic-volume                        
  esp_state                  Cân bằng điện tử (ESP)       xem  ic-esp                           
  mcu_status                 Trạng thái nguồn (MCU)       xem  (lùi nhóm)                       
  volt_12v                   Ắc-quy 12V                   xem  ic-bolt                          
  volt_12v_level             Mức ắc-quy 12V               xem  ic-bolt                          
  adas_slw                   Cảnh báo quá tốc             bấm  ic-speed                         
  adas_esp                   Cân bằng điện tử (ESP)       bấm  ic-esp                           
  adas_tsr                   Nhận diện biển báo           bấm  ic-sign                          
  adas_lane                  Hỗ trợ giữ làn               bấm  ic-car-top-lane                  
  adas_fcw                   Cảnh báo va chạm trước       bấm  ic-car-front-fcw                 
  adas_rcta                  Cắt ngang phía sau           bấm  ic-car-top-rcta-all              
  adas_dow                   Cảnh báo mở cửa              bấm  ic-car-top-dow-all               
  adas_cpd                   Phát hiện trẻ em             bấm  ic-car-top-occupant-rear         
## IDENTITY
  vin                        Số VIN                       xem  (lùi nhóm)                       
  key_bluetooth              Chìa Bluetooth               xem  ic-lock                          
  engine_code                Mã máy                       xem  ic-hood                          
  engine_coolant_level       Mức nước làm mát             xem  ic-hood                          
  oil_level                  Mức dầu                      xem  ic-hood                          
  gps_lat                    Vĩ độ                        xem  ic-gps-lat                       
  gps_lon                    Kinh độ                      xem  ic-gps-lon                       
  gps_elevation              Cao độ                       xem  ic-gps-alt                       
  gps_heading                Hướng                        xem  ic-gps-heading
```

## 4 · Mã có vị trí nhưng **cố ý** không vẽ theo vị trí

| mã | vì sao |
|---|---|
| `motor_front_rpm` · `motor_rear_rpm` · `motor_front_torque` | "trước/sau" là MÔ-TƠ nào, không phải góc nào trên thân xe. Đây là đại lượng quay ⇒ giữ glyph trừu tượng (OQ1) |
| `defrost_rear` | sấy kính sau dùng chung `ic-defrost` với sấy trước — **nợ nhìn thấy được**, cần một khung REAR cho sấy kính |
| `mac_win_open_all` · `mac_win_close_all` | hình ĐÃ vẽ bốn ô kính trên khung xe + mũi tên; chỉ **tên tệp** giữ tên cũ (`ic_window_open/close`) để khoá lưu bền của người dùng không đổi |

Danh sách này nằm trong `CapabilityIconPositionTest.noPositionShape`, mỗi dòng bắt buộc có lý do ≥ 40 ký tự và
**tự rữa** (mã biến mất khỏi registry ⇒ test đỏ).

## 5 · Mười hình đã dựng xong nhưng **chưa ghi ra tệp**

Spec R5 cấm "icon mồ côi" (tệp không chỗ nào dùng) và `IconStyleContractTest.khong co tep icon mo coi` khoá điều
đó. Mười hình dưới đây đã có hình học hoàn chỉnh trong script sinh icon nhưng **registry chưa có mã** tương ứng,
nên ghi ra là thành mồ côi. Thêm mã → xoá tên khỏi `PENDING_NO_CODE` → chạy lại script là có tệp:

| hình | thiếu mã nào |
|---|---|
| `ic_car_rear_tail` · `ic_car_rear_brake` · `ic_car_rear_reverse` | registry không có datum/nút cho đèn hậu · đèn phanh · đèn lùi |
| `ic_car_top_park_front` · `_rear` · `_l` · `_r` | chỉ có `radar_zones` (8 vùng gộp làm một); chưa có mã đọc theo từng phía |
| `ic_car_top_seat_fr` · `_rl` · `_rr` | chỉ `seat_memory` (ghế lái) là có nút; ba ghế kia chưa có mã |

⚠ **Cần owner chốt** (xem §OQ của spec): có thêm mã registry cho ba họ này không? Nếu có thì đây là việc của
`car-integration` trước (rule §14: shell thô trên xe thật ⇒ transport ⇒ core ⇒ UI), không phải việc của icon.

## 6 · Năm tệp icon cũ bị XOÁ (thay 1:1 bằng hình xe)

`ic_trunk.xml` · `ic_sunroof.xml` · `ic_mirror.xml` · `ic_seatbelt.xml` · `ic_radar.xml` — cả năm được hình xe
thay đúng nghĩa, và giữ lại là giữ **hai hình cho một khái niệm** (trái luật "một khái niệm một hình" của U6).
`ic_gps.xml` cũng xoá: bốn mục GPS nay có bốn hình riêng (`ic_gps_lat/lon/alt/heading`).

Bốn **tên** tra cứu chết mà **tệp vẫn sống** (không xoá tệp): `ic-adas` (tệp là icon nhóm ADAS) ·
`ic-turn-left`/`ic-turn-right` (hai tệp là mũi tên rẽ của màn dẫn đường) — chỉ gỡ dòng trong `KachiTheme.iconRes`.

## 7 · Liên quan

- Spec: `docs/specs/kachi-icon-set-v2.html` (R1–R6, §4, §5)
- Bối cảnh U1/U6: `docs/specs/kachi-capability-icons.html`
- Bài canh: `core CapabilityIconsDiversityTest` · `core CapabilityIconPositionTest` (mới) ·
  `app IconStyleContractTest` · `app IconGeometryContractTest` (mới — ô quang học 20×20 + cạnh lớn nhất ≥ 16)
