package com.byd.clusternav.launcher

import android.app.Activity
import android.widget.Toast
import com.byd.clusternav.R

/**
 * ═══ S4 · R7 — BỘ CHỌN HỒ SƠ sau cú chạm chip hồ sơ trên thanh trên ══════════════════════════════════════════
 *
 * Thay cho `ProfileBar` (S1): tệp đó có **hai** việc — `cycle()` (chạm avatar = xoay sang hồ sơ kế tiếp) và
 * `addDialog()` (hộp thoại tạo hồ sơ, được màn Cài đặt dùng lại). S4 bỏ cả hai và để lại đúng một việc: **mở bộ
 * chọn**.
 *
 * ## Vì sao BỎ xoay vòng (`cycle`)
 * Ở S1 một hồ sơ giữ bố cục · ô · thanh nút · chip. Từ S4 (R3) nó giữ **tất cả**: thêm chủ đề · đơn vị · ngôn ngữ ·
 * hình nền · tự-mở-khi-nổ-máy và toàn bộ cấu hình ClusterNav (dẫn đường · biển báo · bong bóng · phím vô-lăng ·
 * tiện nghi). Một cú chạm vào đích 48dp ngay cạnh pill "Cài đặt" mà đổi ngần ấy thứ sang **một hồ sơ người dùng
 * không chọn tên** là cú chạm nguy hiểm nhất của launcher — và nó xảy ra khi xe **đang chạy**.
 *
 * Xoay vòng còn sai cả về hình dạng, đúng như lý do pill "Thanh" bị bỏ (xem KDoc [KachiTopStrip]): danh sách 4 hồ sơ
 * thì muốn tới hồ sơ đứng trước phải bấm ba lần, và không lần nào nói trước mình sắp đi đâu.
 *
 * ## Vì sao KHÔNG dựng `AlertDialog` tại chỗ — và vì sao gọi THẲNG [KachiDialogs.pick] (KACHI-301 R10)
 * Hộp chọn dùng chung của dự án là khuôn **Chọn** của [KachiDialogs] (R9). Trước 301 chỗ này đi qua
 * [SettingsDialogs.pick] — nhưng chữ ký đó chỉ nhận `List<String>` (nhãn trần), nên KHÔNG mang được thứ R10 đòi: ô
 * chữ cái đầu ([PickItem.initial]), dòng phụ, dấu đang dùng ([PickItem.current]), lưới 2 cột và lối chân "Quản lý hồ
 * sơ…". Thêm một bản quá tải vào `SettingsDialogs` chỉ là chuyển tiếp nguyên văn sang `KachiDialogs.pick` — một tầng
 * vô ích. Ba quyết định mà lớp dùng chung đã chốt vẫn giữ nguyên vì chúng nằm ở chính khuôn: nút đóng · danh sách rỗng
 * thì NÓI ([KachiDialogs.notice]) · trả **chỉ số** chứ không trả nhãn (hai hồ sơ có thể trùng nhãn sau khi dịch).
 * Bài canh `TopStripSurfaceContractTest` đổi theo (đòi `KachiDialogs.pick(`, cấm `AlertDialog`).
 *
 * ## Tạo/xoá hồ sơ KHÔNG ở đây
 * §4.5 (giữ nguyên ở S4): *tạo* và *xoá* chỉ ở Cài đặt → Hồ sơ tài xế. Mục cuối của bộ chọn — **"Quản lý hồ sơ…"** —
 * là đường tới đó, nên cú chạm chip vẫn dẫn được tới mọi việc mà không cần một hộp thoại tạo thứ hai ở thanh trên.
 * Nút "Thêm hồ sơ (bản sao…)" nay dùng [SettingsDialogs.askName] ngay trong màn Cài đặt.
 *
 * Chạm `android` (dialog/toast) nên đây là đơn vị `:app` hợp lệ; quyết định thì đẩy hết về [HomeViewModel] (một
 * chiều: `switchProfile` → collector nạp lại workspace/dock/chip/chủ đề/đơn vị → `render`).
 */
class ProfileChip(
    private val activity: Activity,
    private val viewModel: HomeViewModel,
) {

    /**
     * Bộ chọn hồ sơ (R10): mỗi hồ sơ một **thẻ** — ô chữ cái đầu · tên · dòng phụ — hồ sơ **đang dùng** tô nổi + "✓";
     * từ 4 hồ sơ thì lưới 2 cột ([KachiDialogs.GRID_MIN_ITEMS]); chân trái là lối **"Quản lý hồ sơ…"** → [onManage].
     *
     * Dòng phụ là **dữ liệu thật** của hồ sơ đó: câu tóm tắt bố cục [HomeViewModel.profileSummary] (cùng câu thẻ hồ sơ
     * ở Cài đặt đang dùng). Mẫu spec §4.4 vẽ *"Sửa lần cuối 10/10"* nhưng [ĐO] nơi lưu (`WorkspacePrefs`) KHÔNG có mốc
     * sửa nào theo hồ sơ ⇒ không bịa. Hồ sơ đang dùng mang thêm "· đang dùng" ở dòng phụ (đúng mẫu: dòng phụ + ✓).
     *
     * [onManage] là lối sang Cài đặt → Hồ sơ tài xế; chỗ gọi truyền `panels.openSettings(SettingsGroup.PROFILES)`
     * (đường đã có sẵn, không mở đường thứ hai). Nay nó là NÚT CHÂN chứ không còn là "mục cuối danh sách" — nên không
     * còn phép so `which == list.size` dễ lệch một khi danh sách đổi.
     *
     * Chọn đúng hồ sơ **đang dùng** thì không làm gì: `switchProfile` sang chính nó vẫn kéo theo một lượt chụp–áp +
     * nạp lại toàn bộ (R5), tức là một cú chạm vô hại lại làm cả màn hình dựng lại.
     *
     * ⚠ [SOÁT P3-4] `switchProfile` nhận tên **GỐC** (nó là tiền tố khoá lưu); chỉ nhãn trên hộp thoại và câu thông
     * báo mới đi qua [ProfileNames.display].
     */
    fun picker(onManage: () -> Unit) {
        val s = viewModel.uiState.value
        val list = s.profiles
        val items = list.map { name ->
            val label = ProfileNames.display(name)
            val active = name == s.activeProfile
            // QA-301 (P3): dòng phụ CHỈ là tóm tắt — dấu "✓ Đang dùng" của khuôn đã nói ý đó (bản trước lặp "· đang dùng"
            // ⇒ thẻ đang dùng xuống 2 dòng, cao lệch hàng kia).
            PickItem(
                label = label,
                subtitle = viewModel.profileSummary(name),
                current = active,
                initial = initialOf(label),
            )
        }
        KachiDialogs.pick(
            context = activity,
            title = activity.getString(R.string.kachi_profile_pick_title),
            items = items,
            subtitle = activity.getString(R.string.kprof_pick_subtitle),
            columns = 2,
            // Lưới an toàn, không phải ca thường: `WorkspacePrefs` luôn giữ ít nhất một hồ sơ (`deleteProfile` mở đầu
            // bằng `list.size <= 1`), nên danh sách chỉ rỗng nếu nơi lưu hỏng. Nói ra vẫn hơn một hộp trắng.
            emptyText = activity.getString(R.string.kachi_profile_none),
            linkLabel = activity.getString(R.string.kachi_profile_manage),
            onLink = { onManage() },
        ) { which ->
            val name = list.getOrNull(which) ?: return@pick
            if (name == s.activeProfile) return@pick
            viewModel.switchProfile(name)
            Toast.makeText(
                activity,
                activity.getString(R.string.kachi_profile_switched, ProfileNames.display(name)),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    private companion object {
        /**
         * Chữ cái đầu cho ô đại diện: **một điểm mã** (không cắt đôi cặp surrogate của emoji/chữ ngoài BMP), viết hoa
         * bằng `uppercase()` (Locale.ROOT — "đ" → "Đ" đúng, không phụ thuộc ngôn ngữ máy như ca "i" tiếng Thổ).
         * Nhãn trống ⇒ `null` ⇒ thẻ không có ô đầu (khuôn tự bỏ) thay vì một ô tròn rỗng.
         */
        fun initialOf(label: String): String? {
            val t = label.trim()
            if (t.isEmpty()) return null
            return String(Character.toChars(t.codePointAt(0))).uppercase()
        }
    }
}
