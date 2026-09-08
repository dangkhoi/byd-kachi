package com.byd.clusternav.modules.clustercast

import android.app.Activity
import android.widget.Button
import com.byd.clusternav.Lang
import com.byd.clusternav.modules.clustercast.simplified.SimpleCastCoordinator
import com.byd.clusternav.modules.clustercast.simplified.SimpleCastIntent

/**
 * Kênh cứu hộ MẠNH — "Dọn sạch cụm (gỡ kẹt DashCast)" (yêu cầu owner 2026-08-11).
 *
 * **Vì sao có phép này.** Xe đã cài DashCast rồi cài ClusterNav → HAI app dùng CHUNG một bộ API chiếm
 * cụm (ClusterNav reverse-engineer từ DashCast) → tranh nhau OEM projection + windowing + density trên
 * virtual-display của cụm → kẹt CẢ HAI, chủ xe phải flash lại firmware. Nút "Trả cụm về đồng hồ (cứu
 * hộ)" thường CHỈ Stop + đóng chiếu của RIÊNG app này rồi **mở lại** — vô dụng khi DashCast vẫn giành
 * lại cụm.
 *
 * Phép này khác ở ba điểm — và đó là toàn bộ lý do nó tồn tại:
 *  1. **Đứng hẳn xuống**: Stop + đóng chiếu + DỪNG service nút nổi của chính mình, **KHÔNG mở lại** chiếu
 *     (để ClusterNav thôi giành cụm — khác nút thường luôn reopen).
 *  2. **Force-stop bên tranh chấp**: `am force-stop com.byd.dashcast` (+ helper xdja) để DashCast thôi
 *     giật lại cụm.
 *  3. **Reset VD cụm** về mặc định (size/density/overscan) — đúng nguồn kẹt "rò state trên VD"
 *     (`ClusterCast.kt:547`).
 *
 * Best-effort + trung thực: chạy nền, không ném, nói rõ đã force-stop app nào, và **không hứa cứu được
 * mọi ca** — ca kẹt sâu ở OEM/MCU vẫn có thể phải flash. Thông điệp cuối luôn nhắc GỠ DashCast +
 * power-cycle. Ở riêng file để `MainActivityCastController` không vượt
 * ngưỡng 500 dòng.
 */
internal class CastDeepRescueAction(
    private val activity: Activity,
    private val coordinator: SimpleCastCoordinator,
    /** Làn nền cho thao tác cứu hộ — KHÔNG phải luồng vẽ (có I/O shell + ngủ chờ). */
    private val background: (() -> Unit) -> Unit,
    private val postUi: (() -> Unit) -> Unit,
    private val toast: (String) -> Unit,
    /** Dừng nút nổi/cast service của CHÍNH app này (để mình thôi giành cụm). */
    private val stopOwnServices: () -> Unit,
) {
    fun bind(button: Button) {
        button.setOnClickListener { confirm() }
    }

    private fun confirm() {
        android.app.AlertDialog.Builder(activity)
            .setTitle(Lang.t("Dọn sạch cụm (gỡ kẹt DashCast)?", "Deep-clean cluster (unstick DashCast)?"))
            .setMessage(
                Lang.t(
                    "Dùng khi cụm kẹt vì XUNG ĐỘT với DashCast (hoặc app chiếu cụm khác).\n\n" +
                        "Việc sẽ làm: dừng hẳn chiếu của ClusterNav (kể cả nút nổi), FORCE-STOP DashCast, và reset cụm về đồng hồ gốc.\n\n" +
                        "Sau đó nên GỠ DashCast rồi power-cycle xe. Nếu cụm vẫn kẹt, có thể phải flash lại firmware.",
                    "Use when the cluster is stuck due to a CONFLICT with DashCast (or another cluster-cast app).\n\n" +
                        "This will: fully stop ClusterNav casting (including the floating button), FORCE-STOP DashCast, and reset the cluster to the original clock.\n\n" +
                        "Afterwards, UNINSTALL DashCast and power-cycle. If the cluster is still stuck, a firmware reflash may be required.",
                ),
            )
            .setNegativeButton(Lang.t("Hủy", "Cancel"), null)
            .setPositiveButton(Lang.t("Dọn sạch", "Deep-clean")) { _, _ -> execute() }
            .show()
    }

    private fun execute() {
        toast(Lang.t("Đang dọn sạch cụm…", "Deep-cleaning cluster…"))
        background {
            // 1. Đứng hẳn xuống — thôi giành cụm. KHÔNG mở lại chiếu (khác nút thường).
            runCatching { coordinator.dispatch(SimpleCastIntent.Stop()) }
            runCatching { coordinator.closeProjection() }
            runCatching { stopOwnServices() }

            // 2. Force-stop bên tranh chấp để nó thôi giật lại cụm.
            val stopped = CONFLICT_PACKAGES.filter { pkg ->
                runCatching { coordinator.executeShell("am force-stop $pkg").success }.getOrDefault(false)
            }

            // 3. Reset VD cụm về mặc định (best-effort; đúng nguồn kẹt "rò state trên VD").
            val vd = runCatching { DisplayParse.clusterDisplayId(coordinator.executeShell("dumpsys display").stdout) }
                .getOrDefault(-1)
            if (vd >= 0) {
                runCatching { coordinator.executeShell("wm size reset -d $vd") }
                runCatching { coordinator.executeShell("wm density reset -d $vd") }
                runCatching { coordinator.executeShell("wm overscan reset -d $vd") }
            }

            postUi {
                val who = if (stopped.isEmpty()) {
                    Lang.t("(không thấy app tranh chấp đang chạy)", "(no conflicting app was running)")
                } else {
                    stopped.joinToString(", ")
                }
                toast(
                    Lang.t(
                        "Đã dọn: dừng chiếu + force-stop $who + reset cụm. Hãy GỠ DashCast rồi power-cycle xe.",
                        "Cleaned: stopped casting + force-stopped $who + reset cluster. Please UNINSTALL DashCast and power-cycle.",
                    ),
                )
            }
        }
    }

    companion object {
        /** App chiếu cụm dùng CHUNG API với ClusterNav — nguồn xung đột đã biết (findings + navopen spoof com.byd.dashcast). */
        internal val CONFLICT_PACKAGES = listOf("com.byd.dashcast", "com.xdja.clusterdemo")
    }
}
