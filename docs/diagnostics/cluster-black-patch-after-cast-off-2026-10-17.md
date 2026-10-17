# Mảng đen giữa cụm sau khi tắt chiếu (Seal, 17/10): CHƯA sửa, cần một phép đo

> **Loại:** điều tra off-car từ logcat xe 17/10 + source. **Không sửa code.** **Ngày:** 2026-10-17.
> **Triệu chứng [ĐO, owner]:** Kachi tắt chiếu, sau đó bấm "dọn cụm". Giữa cụm (vùng chiếu) còn **một mảng ĐEN**, hai bên đồng hồ vẫn bình thường.
> Lúc đó màn ảo `fission_bg_xdjaVirtualSurface` (display 1, 1920×720) vẫn còn và trống (`am stack list`: display 1 không có
> stack nào). Bật chiếu lại trong Kachi thì mảng đen biến mất.
> Mức bằng chứng: **[ĐO]** = log/source thật · **[SUY]** · **[ĐOÁN]** · **[CHƯA BIẾT]**.

## 1. Trình tự trong log (giờ 17/10)

| Giờ | Việc | Nguồn |
|---|---|---|
| 11:23:03–04 | Tắt chiếu (`setCastEnabled(false)` → `dispatch(Stop)` + `closeProjection()`, `ClusterNavBridgeCast.kt:112-117`): trả Maps về display 0, opcode **0** (`refreshCluster`, `SimpleCastCoordinatorIntents.kt:233`) | [ĐO] log · trình kích hoạt [SUY] |
| 11:23:05.016 / .507 | opcode **18** rồi **0** = `ProjectionManager.close` (`SEAL_TEARDOWN=[18,0]`, `ProjectionRecipe.kt:133`) | [ĐO] |
| 11:23:06.100 | `closeProjection: gỡ ClusterBlack khỏi màn ảo cụm [1]` (gỡ SAU 18→0, `SimpleCastCoordinator.kt:265`). `closeProjection` **không** reset wm trước 18 (chỉ `closeProjectionSync` reset) | [ĐO] |
| 11:23:05–11:26:23 | Không lệnh AutoContainer nào khác. Không display thêm/bớt; xdja VD còn suốt | [ĐO] |
| 11:23:45–52 | "Dọn cụm" (`deepRescue`): `closeProjection` lần hai thấy đã đóng ⇒ **không opcode**. `am force-stop com.byd.dashcast` / `com.xdja.clusterdemo` ⇒ `ActivityManager: Invalid packageName` (11:23:52.119 / .284), **hai gói không có trên xe** ⇒ không tác dụng. `wm size/density/overscan reset -d 1` (đóng băng 68 ms rồi mở) | [ĐO] |
| 11:26:23 | Bật chiếu lại: `theme 30 cụm=[1] → SEND`, rồi 16, 35, wm 1920×720/240, ClusterBlack, Maps chiếu | [ĐO] |

Phía cụm (Qt, `fission_host`) **không có log** trong logcat này.

## 2. Vì sao có mảng đen

- **Lớp cụm** [ĐO firmware, `cluster-rect-navitype-layouts-2026-10-06.md` §2/§6]:
  - Lớp màn ảo chiếu nằm ở z −3 và luôn bật. Nền nằm ở z −2, phủ toàn màn.
  - Ở naviType 0/1/2, nền đục 100 %. Chỉ ở 4 (chiếu toàn màn) hoặc 3 (cửa sổ nhỏ) nền mới có lỗ.
  - Opcode 18 xin về state 0.
- **Suy ra [SUY mạnh]:** mảng đen ở đúng vùng chiếu, hai bên đồng hồ vẫn nguyên ⇒ cụm **còn ở layout có lỗ** trong khi màn ảo trống. Lúc đó là ban ngày: ClusterBlack màu ngày `#FFC0CFDB` chứ không đen ⇒ thứ lộ qua lỗ là màn ảo trống.
- **"Dọn cụm" không gây ra [SUY mạnh]:**
  - Hai lệnh force-stop đều không làm gì [ĐO].
  - Không có opcode nào được gửi [ĐO].
  - Reset wm của một màn ảo trống không thể xuyên qua nền đục.
  - Nhiều nhất đó chỉ là lúc owner để ý thấy.
- **Thiếu opcode, hay sai thứ tự gỡ ClusterBlack: [CHƯA BIẾT].** Trong cả hai trường hợp, gốc là 18 không đưa cụm về state 0, hoặc có thứ đưa nó về lại 4. Không có dữ liệu phía cụm, cũng không có ảnh trong khoảng 11:23:06–11:23:45. Nếu 18 bị bỏ qua, việc gỡ ClusterBlack sau 18→0 chỉ đổi mảng từ xám-xanh sang đen.
- **Dữ kiện ngược [ĐO 05/10, `oncar-2026-10-05-slot-cluster.md:48`]:** "Tắt chiếu → cụm thường" — một lượt 18→0 đã trả đồng hồ thường. Hôm đó ClusterBlack còn nằm trên VD. Từ 2.89 Kachi gỡ ClusterBlack sau 18→0; trình tự mới này **chưa đo** trên xe.
- **Khác DashCast:** thứ tự đóng của DashCast là "app khỏi VD → reset density/overscan → 18 → 0" (`docs/reference/dashcast-projection-recipe.md:18,26`). Kachi gửi 18→0 khi ClusterBlack còn trên VD và không reset wm trước.
- **Đường 2.90 "theme khi VD cụm có nhưng trống" không dính** vào lượt đóng. Nó chỉ quyết lượt MỞ kế tiếp (vì thế 11:26:23 gửi 30).

## 3. Quyết định: KHÔNG sửa ở 3.1.12

Có hai cách sửa không cần opcode mới. Không cách nào có bằng chứng:

- **(1) Đổi thứ tự `closeProjection` theo DashCast:** gỡ ClusterBlack + `configurator.reset` TRƯỚC `projection.close`. Cách này đảo một đường đóng đã chạy ngoài hiện trường (CLAUDE.md §6).
- **(2) Giữ ClusterBlack trên VD sau khi đóng:** `ClusterThemeGuard` đã gỡ nó trước mọi opcode theme. Cách này chỉ đổi màu triệu chứng.

CLAUDE.md §2/§14 không cho chọn khi chưa đo. Memory "thấy sập lần đầu là dừng": không thử opcode mới nào.

## 4. Phép đo duy nhất để chốt (chỉ thao tác thường của người lái, opcode có sẵn 0/18/0)

1. Chiếu bình thường (GMaps toàn màn).
2. **Tắt chiếu** trong Kachi. **KHÔNG** bấm "dọn cụm".
3. Chụp cụm ở khoảng **+3 s** và **+15 s**.
4. Cùng lúc, chạy các lệnh chỉ đọc:
   ```
   logcat -d -v time | grep -E "SimpleCast|AutoContainer"
   am stack list
   dumpsys display | grep -iE 'Display [0-9]+:|fission|xdja'
   dumpsys window windows | grep -E 'Window #|mDisplayId=1'
   ```

Đọc kết quả:
- **Đã đen ở +3 s** ⇒ lượt đóng hiện tại (0 · 18 · 0 · gỡ ClusterBlack) để cụm ở layout có lỗ.
  - Ứng viên sửa: (1).
  - Test hồi quy: dãy lệnh shell giả của `closeProjection`, khẳng định thứ tự "gỡ placeholder → wm reset trên id đã xác minh → 18 → 0". Lệnh kỳ vọng lấy nguyên văn từ log chuyến này.
- **Đồng hồ thường ở +15 s** ⇒ nguyên nhân là thứ khác. Lặp đúng bước "dọn cụm" rồi chụp lại.
- **Nếu không muốn thao tác gì thêm:** mốc giờ trên ảnh/video của owner cũng xếp được mảng đen vào trước hay sau 11:23:45.

## Quyết định owner 17/10 — xếp ưu tiên thấp nhất, không sửa

Owner (17/10, sau khi đọc doc này): *"ko must gì, trước giờ vẫn thế thôi … hiếm khi xài, không thích thì restart"* ⇒ dòng backlog
`CLUSTER-BLACK-AFTER-CAST-OFF` chuyển **Deferred, ưu tiên thấp nhất**; 3.1.12 **không đổi code** cho việc này. Phép đo chỉ đọc ở trên
giữ nguyên để tra cứu, chỉ làm khi tiện trong một chuyến xe khác — không chặn bản nào.
