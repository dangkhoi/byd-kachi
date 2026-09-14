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
 * ## Vì sao KHÔNG dựng `AlertDialog` tại chỗ
 * [SettingsDialogs.pick] đã là *"hộp thoại danh sách đơn giản"* dùng chung của dự án (9 chỗ gọi, xem KDoc của nó).
 * Dựng bản thứ hai ở đây là chép lại đúng ba quyết định mà lớp kia đã chốt (nút huỷ · danh sách rỗng nói gì · trả
 * **chỉ số** chứ không trả nhãn, vì hai hồ sơ có thể trùng nhãn sau khi dịch).
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
     * Bộ chọn hồ sơ: từng hồ sơ (hồ sơ **đang dùng** có dấu) rồi mục **"Quản lý hồ sơ…"** → [onManage].
     *
     * [onManage] là lối sang Cài đặt → Hồ sơ tài xế; chỗ gọi truyền `panels.openSettings(SettingsGroup.PROFILES)`
     * (đường đã có sẵn, không mở đường thứ hai).
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
        val labels = list.map { name ->
            if (name == s.activeProfile) {
                activity.getString(R.string.kachi_profile_pick_active, ProfileNames.display(name))
            } else {
                ProfileNames.display(name)
            }
        } + activity.getString(R.string.kachi_profile_manage)
        SettingsDialogs.pick(
            context = activity,
            title = activity.getString(R.string.kachi_profile_pick_title),
            labels = labels,
            // Lưới an toàn, không phải ca thường: `WorkspacePrefs` luôn giữ ít nhất một hồ sơ (`deleteProfile` mở đầu
            // bằng `list.size <= 1`), nên danh sách chỉ rỗng nếu nơi lưu hỏng. Nói ra vẫn hơn một hộp trắng.
            emptyText = activity.getString(R.string.kachi_profile_none),
        ) { which ->
            if (which == list.size) {
                onManage()
                return@pick
            }
            val name = list[which]
            if (name == s.activeProfile) return@pick
            viewModel.switchProfile(name)
            Toast.makeText(
                activity,
                activity.getString(R.string.kachi_profile_switched, ProfileNames.display(name)),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
}
