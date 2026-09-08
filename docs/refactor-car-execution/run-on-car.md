# Chạy đánh giá trên xe — không cần APK

Mọi lệnh chạy từ laptop, không cài gì lên xe. Verdict ghi vào
`docs/refactor-car-execution/verdicts.tsv` (append-only). Mọi lần chạy tự lưu log vào
`oncar-carexec-<ngày>/`.

```bash
export CAR_HOST=<ip-xe>
scripts/vehicle/carexec.sh <lệnh>
```

## Chuẩn bị off-car (không cần xe)

| Lệnh | Làm gì |
|---|---|
| `carexec.sh steps` | liệt kê 24 step và 37 candidate |
| `carexec.sh scenarios` | 23 kịch bản, cái nào ráp được |
| `carexec.sh scenario <id>` | từng bước, state phải đúng, ai kiểm |
| `carexec.sh plan <id>` | in đúng chuỗi lệnh sẽ gửi, **không gửi gì** |
| `carexec.sh run <candidate> --dry-run` | in lệnh của một candidate, không gửi |
| `carexec.sh observe --recorded` | quan sát trên fixture đã ghi của xe thật |

Duyệt `plan` trước khi lên xe. Thiếu tham số nó nói ngay — rẻ hơn nhiều so với phát hiện lúc đang trong xe.

## Trên xe: chạy và đánh dấu

```bash
carexec.sh scenario cast.cold-first --run \
  --pkg vn.vietmap.live --comp vn.vietmap.live/vn.vietmap.live.MainActivity \
  --display 1 --task <taskId>
```

Nó chạy tới mốc cần nhìn cụm rồi **dừng**, in ra điều cần nhìn và hai câu lệnh sẵn:

```bash
carexec.sh verdict open.seal-30-16-35 ok   --note "cụm hiện VietMap"
carexec.sh verdict open.seal-30-16-35 fail --note "cụm vẫn hiện đồng hồ"
carexec.sh scenario cast.cold-first --run --from 5     # chạy tiếp
```

Lệnh lỗi hoặc step chưa OK thì nó dừng, không chạy bừa.

## Thứ tự phiên tới (≤ 10 phút, xe đang chạy)

| # | Kịch bản | Được gì |
|---|---|---|
| 1 | `cast.observable-hunt` | **trả lời Q1** — chụp 4 mốc: đồng hồ → có task chưa mở chiếu → chiếu mở → sau khi đóng. Trường nào đổi đúng lúc cụm đổi là observable cần tìm |
| 2 | `cast.cold-first` | đánh OK cho 5 step nền: probe-profile, probe-target, bootstrap-cold, place, open-projection |
| 3 | `cast.stop-paths` | teardown + restore |
| 4 | `cast.rotate-a-b-c-a` | switch ba chiều, soát mồ côi |
| 5 | `cast.geometry-persist` | bốn cạnh + DPI, và kiểm **giữ thiết lập** sau khi cast lại |

Bước 1 chạy step `capture-state` bốn lần (đó là cách nó chụp bốn mốc), và cũng lấp fixture còn thiếu: `appops get` — thiếu nó nên `observe --recorded` hiện dừng ở
`APP_OPS_STATE`. Sau phiên này quan sát chạy trọn vẹn off-car.

## Bẫy đã biết

- Runner nối bằng dadb nên `service call ... s16 ""` gửi trực tiếp. Gõ tay qua `adb shell` thì phải bọc cả
  lệnh trong nháy kép, không thì nhận `Parcel(fffffffc ...)`.
- Máy phải nổ trong phiên dài: 26/7 mất cả phiên vì ACC standby.
- Phiên trên xe **chỉ đo và ghi**. Không sửa code rồi cài trong phiên.

## Bản dựng mang ra xe mai

```
ClusterNav-0.72-vehicle-test-56b676e20567-release.apk
sha256 f44ef7ed25f34378921850d250dc295775e94de8eea2d22a2923a43ec1925317
```

Đã kiểm off-car: DEX **không còn** vdmap/DadbBridge/PersistentDeviceShell/deadreckon, **có**
NavScreenScan/SpeedReading/ClusterAttestation; Manifest không còn VdMapActivity; E2E emulator 18/18.

Lưu ý: bản này **chưa sửa** điều kiện phát `30,16,35` — cố ý, vì luật đúng phụ thuộc kết quả
`reissue-policy`. Nên đừng kỳ vọng bấm Chiếu trong app là cụm lên; phần chiếu hôm nay vẫn chạy bằng
shell qua `carexec.sh`.

## Phiên 2026-07-28 — thứ tự chạy

Mức rủi ro giờ in kèm mỗi candidate. Đọc nó trước khi bấm: `READ_ONLY` chạy được lúc đang lái,
`MAY_HANG_SYSTEM` **chỉ khi đỗ**.

### A. Biển báo giới hạn tốc độ — khám phá (chạy được lúc đang lái)

```
carexec.sh plan sign.discover-chain --pkg <ứng-viên>
carexec.sh run sign-inventory.packages
carexec.sh run sign-inventory.services
carexec.sh run sign-inventory.hal
carexec.sh run sign-inventory.processes
```

Rồi phần quyết định — phải đi qua **ít nhất hai biển khác số**, nhớ số để đối chiếu:

```
carexec.sh run sign-watch.logcat-keywords
carexec.sh run sign-watch.logcat-raw-window     # nếu lọc từ khoá ra rỗng
carexec.sh run sign-watch.props-diff            # chụp ở hai vùng biển khác nhau
carexec.sh run sign-watch.settings-diff
```

Nếu cả bốn ra rỗng thì kết luận thẳng: giá trị không đi qua đường nào của Android mà app đọc được,
và hướng "gửi lén tín hiệu" phải đổi cách hoặc dừng. Đó cũng là một kết quả.

### B. Nguồn từ VietMap (đang lái, chỉ đọc)

```
carexec.sh run sign-source.notification         # rẻ nhất, dùng lại đường notification đã có
carexec.sh run sign-source.exported-surface
carexec.sh run sign-source.logcat
```

### C. Chỉ khi ĐỖ — ghi vào và tắt nguồn camera

Thứ tự bắt buộc: tắt camera trước, rồi ghi, rồi kiểm giá trị cũ có dính lại.

```
carexec.sh run sign-mute.settings-key --key <khoá tìm được>
carexec.sh run sign-inject.broadcast --key <action tìm được> --value 60
carexec.sh run sign-stale.stop-sending
```

### D. Chỉ khi ĐỖ — luật phát lại chuỗi mở chiếu

Câu hỏi chặn đường app. **Chuẩn bị sẵn khả năng khởi động lại head unit.** Chạy từng cái, dừng lại
đánh cờ ngay sau mỗi cái, đừng chạy liền một mạch:

```
carexec.sh run reissue.35-only-while-warm       # ít bị nghi nhất, thử trước
carexec.sh run reissue.16-only-while-warm       # V1 bảo cái này tái tạo display
carexec.sh run reissue.full-while-warm          # ca V1 bảo sẽ treo
carexec.sh run reissue.return-then-recast       # đường phục hồi giả định
```

Nếu `full-while-warm` không treo thì đường app đơn giản hẳn: cứ phát lại vô điều kiện, không cần
đoán chiếu đang mở hay đóng — mà Q1 đã chứng minh là không đoán được.

### Hai fixture còn thiếu — chụp ngay đầu phiên

```
adb shell am get-current-user            > fixtures/am-get-current-user.txt
adb shell settings get global window_animation_scale   # đối chiếu globals-occupied.txt
```

`observe --recorded` đang dừng ở `PROFILE_STATE` vì chưa có bản ghi thật cho `am get-current-user`.
Có nó là cả chuỗi quan sát chạy được off-car, không cần xe.

### Đánh cờ ngay tại chỗ

```
carexec.sh verdict <candidate> ok|fail --note "chủ xe thấy ..."
```

Ghi chú phải dẫn đúng điều đã thấy. Sổ ghi rõ verdict là `MEASURED` hay `HUMAN`, đừng để lẫn.
