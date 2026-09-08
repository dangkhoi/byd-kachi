package com.byd.clusternav

import android.view.View
import android.view.ViewGroup
import android.widget.TextView

/**
 * FULL bilingual coverage for the STATIC layout text (Deliverable 1 · task `polish-stageB-i18n`).
 *
 * [Lang.t] only translates text that MainActivity sets at runtime (≈99 dynamic call sites). The rebuilt
 * `activity_main` layouts (both variants) hardcode ~71 Vietnamese `android:text` / `android:contentDescription`
 * values (row titles, subtitles, section headers, hero leads, static button labels) that have NO `@+id` and NO
 * runtime setter — so `Lang.t` never reaches them and they stayed Vietnamese even in English mode.
 *
 * This closes the gap WITHOUT editing the byte-sealed layout and WITHOUT adding ids: [localizeTree] walks the
 * inflated view hierarchy once and, when the current language is English, swaps any TextView text /
 * contentDescription whose Vietnamese value is a key in [VI_TO_EN] for its English translation.
 *
 * ── Contract ──────────────────────────────────────────────────────────────────────────────────────
 *  • Called ONCE in `MainActivity.onCreate` right after `setContentView` + `Lang.load`, and BEFORE the dynamic
 *    `Lang.t` setters run — so any view a dynamic setter also owns is simply overwritten by that setter (its
 *    own bilingual value wins; no conflict).
 *  • Idempotent: after a swap the text is English, which is not a Vietnamese key, so re-running is a no-op.
 *  • Null-safe: null text/contentDescription and non-EN languages short-circuit; never throws.
 *  • VI mode: no-op — the layout's Vietnamese values are already correct.
 *
 * The dictionary is the single source of truth for these static strings; keep it in step with the layout when
 * a static label changes (the seal test flags layout edits, so the two move together intentionally).
 */
object BilingualLabels {

    /** Vietnamese (as authored in `activity_main.xml`) → concise English (automotive / car-UI register). */
    val VI_TO_EN: Map<String, String> = mapOf(
        // ── Page header + section headers ───────────────────────────────────────────────────────
        "Mỗi tính năng là một thẻ — chạm tiêu đề để mở/gập"
            to "Each feature is a card — tap its title to expand/collapse",
        "Trạng thái sống" to "Live status",
        "Bảng tính năng" to "Features",

        // ── HERO cards (NAV · CAST · QUICK) ─────────────────────────────────────────────────────
        "Đang dẫn · Google Maps" to "Navigating · Google Maps",
        "Đang chờ nguồn" to "Waiting for a source",
        "Cluster Cast" to "Cluster Cast",
        "Chiếu cụm: —" to "Cast: —",
        "Nhanh" to "Quick",
        "Chiếu full cụm" to "Cast full cluster",
        "Trái" to "Left",
        "Phải" to "Right",
        "Phím-thoại: —" to "Voice key: —",

        // ── Navigation + HUD group ──────────────────────────────────────────────────────────────
        "Dẫn đường trên cụm + HUD" to "Guidance on cluster + HUD",
        "Cụm: tắt" to "Cluster: off",
        "HUD: tắt" to "HUD: off",
        "Đang dẫn: —" to "Active: —",
        "Cụm: chờ dẫn đường" to "Cluster: waiting for navigation",
        "Bật đầu ra HUD độc lập" to "Enable independent HUD output",
        "Chế độ cụm" to "Cluster mode",
        "Chạy chữ tên đường dài" to "Scroll long road names",
        "Cấp quyền / kết nối lại" to "Grant / reconnect",

        // ── Cluster Cast group ──────────────────────────────────────────────────────────────────
        "Sẵn sàng" to "Ready",
        "Đang tắt · cụm vẫn hiện dẫn đường và HUD như thường. Bật để chiếu app lên cụm."
            to "Off · the cluster still shows navigation and HUD as usual. Turn on to cast an app to the cluster.",
        "Chạm nút nổi để chiếu app đang mở · chạm lại để về màn chính"
            to "Tap the floating button to cast the current app · tap again to return home",
        "Tỉ lệ chia đôi cụm — dùng khi chạm ô nửa trái/nửa phải của nút nổi"
            to "Cluster split ratio — used when tapping the left/right half of the floating button",
        "Tự khởi động: chiếu full cụm" to "Auto-start: cast full cluster",
        "Tự khởi động: chia đôi (trái + phải)" to "Auto-start: split (left + right)",

        // ── Physical button → assistant group ───────────────────────────────────────────────────
        "Nút vật lý → Trợ lý" to "Physical button → Assistant",
        "Nút vật lý → mở app / trợ lý" to "Physical button → open app / assistant",
        "Kiểm tra / Sửa ngay" to "Check / fix now",
        "Gán nút (vô-lăng/táp-lô) để mở app. Gán được NHIỀU nút cho NHIỀU app. Không đổi chức năng gốc của nút chưa gán."
            to "Map a button (steering wheel / dashboard) to open an app. You can map MANY buttons to MANY apps. Unmapped buttons keep their original function.",
        "1 · Chọn nút" to "1 · Choose a button",
        "Nút đang chọn: —" to "Selected button: —",
        "Học phím mới…" to "Learn a new button…",
        "2 · Chọn app sẽ mở" to "2 · Choose the app to open",
        "3 · Thêm gán" to "3 · Add binding",
        "Đã gán (app chỉ nghe theo danh sách này)" to "Bound (the app only listens to this list)",
        "Chưa gán nút nào — hiện KHÔNG có nút nào mở app. Chọn nút + app rồi bấm “Thêm gán”."
            to "No buttons bound yet — currently NO button opens an app. Choose a button + app, then tap “Add binding”.",

        // ── On-cluster: speed sign + VietMap bubble group ───────────────────────────────────────
        "Biển báo tốc độ" to "Speed limit sign",
        "Biển báo tốc độ + cảnh báo trên cụm" to "Speed sign + alerts on cluster",
        "Hiện giới hạn sắp tới" to "Show upcoming limit",
        "Hiện cảnh báo/camera VietMap" to "Show VietMap alerts / cameras",
        "Nguồn: chưa kết nối" to "Source: not connected",
        "Kết nối / Dữ liệu VietMap" to "VietMap connection / data",
        "Cỡ" to "Size",
        "Bóng VietMap" to "VietMap bubble",
        "Vị trí bóng VietMap trên cụm (cần Cast)" to "VietMap bubble position on cluster (needs Cast)",
        "Bật Cluster Cast để chỉnh vị trí" to "Turn on Cluster Cast to adjust the position",

        // ── Seats: cool / heat group ────────────────────────────────────────────────────────────
        "Ghế: làm mát / sưởi" to "Seats: cool / heat",
        "Làm mát / sưởi ghế tự động" to "Automatic seat cooling / heating",
        "Tự áp dụng ~5 giây sau khi mở app / nổ máy. Làm mát và sưởi loại trừ nhau."
            to "Applies automatically ~5 seconds after opening the app / starting the engine. Cooling and heating are mutually exclusive.",

        // ── PM2.5 auto filter group ─────────────────────────────────────────────────────────────
        "Tự lọc bụi mịn" to "Auto fine-dust filter",
        "Lọc ngay" to "Clean now",
        "Lọc bụi mịn ngay bây giờ" to "Clean the fine dust now",

        // ── System + Advanced group ─────────────────────────────────────────────────────────────
        "Tự khởi động nền" to "Background auto-start",
        "Giao diện" to "Theme",
        "Nâng cao · cập nhật" to "Advanced · updates",
        "⬇ Kiểm tra cập nhật" to "⬇ Check for updates",
        "Khắc phục sự cố" to "Troubleshooting",
        "Dừng toàn bộ" to "Stop everything",
        "Dừng — trả đồng hồ" to "Stop — restore gauges",
        "Chẩn đoán" to "Diagnostics",
        "Trả cụm về đồng hồ (cứu hộ)" to "Restore cluster to gauges (rescue)",
        "Dọn sạch cụm (gỡ kẹt DashCast)" to "Clean cluster (unstick DashCast)",

        // ── contentDescription values (Vietnamese) ──────────────────────────────────────────────
        "Hướng rẽ" to "Turn direction",
        "Tốc độ" to "Speed",
        "Xem trước cụm" to "Cluster preview",
        "Nguồn dẫn đường đang hoạt động" to "Active navigation source",
        "Trạng thái hiển thị dẫn đường trên cụm" to "Cluster navigation display status",
        "Cần quyền truy cập thông báo để đọc dẫn đường" to "Grant notification access to read navigation",
        "Chế độ hiển thị trên cụm (Bật: Giữa + ETA / Tắt)" to "Cluster display mode (On: Centre + ETA / Off)",
        "Bật hoặc tắt Cluster Cast" to "Turn Cluster Cast on or off",
        "Tỉ lệ chia đôi cụm khi chiếu hai app" to "Cluster split ratio when casting two apps",
        "App tự chiếu bên trái" to "App auto-cast on the left",
        "App tự chiếu bên phải" to "App auto-cast on the right",
        "Bật hoặc tắt kích hoạt trợ lý bằng nút vật lý" to "Turn the physical-button assistant trigger on or off",
        "Kiểm tra và sửa kết nối phím thoại" to "Check and fix the voice-key connection",
        "Học một nút vật lý mới trên xe" to "Learn a new physical button in the car",
        "Thêm cặp nút và app đang chọn vào danh sách gán" to "Add the selected button-and-app pair to the binding list",
        "Bật hoặc tắt biển báo tốc độ trên cụm" to "Turn the cluster speed limit sign on or off",
        "Bật hoặc tắt biển giới hạn tốc độ sắp tới trên cụm"
            to "Turn the upcoming speed limit sign on the cluster on or off",
        "Bật hoặc tắt chip cảnh báo/camera VietMap trên cụm"
            to "Turn the VietMap alert / camera chip on the cluster on or off",
        "Cỡ biển báo tốc độ" to "Speed limit sign size",
        "Bật hoặc tắt bong bóng VietMap trên cụm" to "Turn the VietMap bubble on the cluster on or off",
        "Chế độ ghế: làm mát / sưởi" to "Seat mode: cool / heat",
        "Bật hoặc tắt làm mát / sưởi ghế tự động" to "Turn automatic seat cooling / heating on or off",
        "Sơ đồ ghế nhìn từ trên — chạm ghế để chọn mức 0/1/2"
            to "Top-down seat diagram — tap a seat to set level 0/1/2",
        "Đồng hồ mức bụi mịn PM2.5" to "PM2.5 fine-dust level gauge",
        "Bật hoặc tắt tự lọc bụi mịn PM2.5" to "Turn the PM2.5 fine-dust auto filter on or off",
        "Mở hoặc đóng nhóm hành động khắc phục sự cố" to "Open or close the troubleshooting actions group",
        "Dừng Cluster Cast và trả đồng hồ" to "Stop Cluster Cast and restore the gauges",
        "Mở chẩn đoán Cluster Cast chỉ đọc" to "Open read-only Cluster Cast diagnostics",
        "Trả cụm về đồng hồ: đưa mọi app trên cụm về màn chính rồi đóng đường chiếu"
            to "Restore the cluster to gauges: return every app on the cluster to the main screen, then close the cast path",
        "Dọn sạch cụm khi kẹt do DashCast: dừng hẳn chiếu của app này, force-stop DashCast, reset cụm về đồng hồ. Không mở lại chiếu."
            to "Clean the cluster when stuck due to DashCast: fully stop this app's casting, force-stop DashCast, reset the cluster to gauges. Does not resume casting.",
    )

    /**
     * Walk [root] and, when the current language is English, translate every mapped static TextView text /
     * contentDescription in place. No-op in Vietnamese mode or when [root] is null. Recursive over any
     * [ViewGroup]; idempotent and null-safe (see the class contract).
     */
    fun localizeTree(root: View?) {
        root ?: return
        if (Lang.cur(root.context) != Lang.L.EN) return
        walk(root)
    }

    private fun walk(view: View) {
        // contentDescription applies to any View (ImageView / Spinner / custom views / TextView alike).
        view.contentDescription?.let { cd -> VI_TO_EN[cd.toString()]?.let { view.contentDescription = it } }
        // Visible text applies to TextView and its subclasses (Button, Switch, CheckBox, EditText…).
        if (view is TextView) {
            view.text?.let { t -> VI_TO_EN[t.toString()]?.let { view.text = it } }
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) walk(view.getChildAt(i))
        }
    }
}
