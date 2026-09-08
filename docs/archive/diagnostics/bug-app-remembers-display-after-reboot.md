# Bug: App nhớ display cũ sau reboot

## Triệu chứng
- Tối chiếu VietMap + GMaps lên cụm → tắt máy xe
- Sáng mở xe → VietMap/GMaps task vẫn ở display 1
- Mở ClusterNav → cleanDisplay1() chạy → nhưng app VẪN nhảy lại cụm khi mở
- Phải "Trả cụm về đồng hồ (cứu hộ)" mới kéo được về

## Root cause (giả thuyết)
- Android ghi nhận "task này thuộc display 1" vào persistent state
- `am stack move-task` move về display 0 tạm thời
- Nhưng khi `am start` (user tap icon) không có `--display 0` → Android resume task ở display ghi nhớ (1)

## Fix candidates
1. Sau move-task, bắn thêm `am start --display 0 --windowingMode 1 -n 'pkg/activity'` để overwrite display ghi nhớ
2. Hoặc `am task lock <taskId> 0`? (nếu tồn tại)
3. Hoặc force-stop app sau move → lần mở tiếp = fresh task trên display 0

## Status: NOTED, chưa fix
