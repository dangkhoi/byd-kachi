package com.byd.clusternav.launcher.camera

/**
 * ═══ RÀO CAMERA — bộ dựng DUY NHẤT cho mọi lệnh đưa thứ gì lên TRƯỚC display 0 (thuần, `:core`) ═══════════════════
 *
 * Spec `docs/specs/kachi-launcher-shortcuts-autostart.html` §4.5 · §4.8 (C8). Trước đây chuỗi rào chỉ có một bản viết
 * tay ở `AccessibilityRebind.GO_HOME_UNLESS_CAMERA` (2.83). Tính năng mới (lối tắt, chuyến lên xe) cần CÙNG phép đo
 * với đích khác (mở một app, không phải Home) ⇒ dựng chuỗi ở một chỗ; `GO_HOME_UNLESS_CAMERA` nay là MỘT lời gọi hàm
 * này và `ForceStopReturnHomeTest` khoá nó trùng từng byte với chuỗi 2.83 (CLAUDE.md §6 — đường đang chạy tốt không
 * được đổi một byte).
 *
 * ## Phép đo (kế thừa nguyên văn KDoc `GO_HOME_UNLESS_CAMERA`)
 * `grep -A2 "displayId=0 "` lấy mọi stack của display 0 (tiêu đề · `configuration=` · dòng task đầu), grep thứ hai chỉ
 * giữ dòng `visible=true`. Cờ `visible` và `topActivity` là của STACK (A10 `RootActivityContainer.java:1276,1303-1304`)
 * và được in trên mọi dòng task ⇒ dòng task đầu là đủ.
 *
 * ## Hai dạng
 *  - [homeComp] `null` — dạng 2.83: không đọc được (chuỗi rỗng) ⇒ không làm gì; thấy camera ⇒ không làm gì; còn lại ⇒
 *    [cmd]. Chỉ dùng cho lệnh mà việc chạy khi một app khác đang ở trước là ĐÚNG (Home).
 *  - [homeComp] khác `null` (hoặc [unlessCameraOnHome]) — dạng K10 (spec §4.5): chỉ chạy [cmd] khi một dòng
 *    `visible=true` có chính HOME của Kachi VÀ không dòng nào có camera. App khác đang ở trước (vd lịch dẫn đường vừa
 *    mở GMaps) ⇒ không có HOME trong `c` ⇒ không chạy: "mở bình thường" không giành màn hình với app khác.
 *
 * Thứ tự nhánh `case` là một phần của rào: nhánh camera đứng TRƯỚC nhánh HOME, vì khi camera nằm trên HOME thì `c` chứa
 * CẢ HAI chuỗi và nhánh khớp đầu tiên thắng.
 *
 * ## Ràng buộc chuỗi
 * Không có dấu `'` ở bất cứ đâu — cả chuỗi có thể nằm trong `sh -c '…'` (đuôi lượt chữa của 2.83). [sig]/[homeComp]
 * phải là tên gói/component hợp lệ (chữ, số, `.`, `_`, `/`) — chuỗi khác ⇒ [unlessCamera] ném, không bao giờ trả
 * một lệnh có thể chèn shell. Dấu `$` trong tên lớp lồng (`Outer$Inner`) bị từ chối luôn: trong `case` nó là biến.
 */
object CameraGuard {

    /** Tên gói / component an toàn để nằm trong mẫu `case` (không ký tự đặc biệt của sh). */
    private val SAFE = Regex("[A-Za-z0-9_./]+")

    /**
     * Dựng lệnh "chạy [cmd] trừ khi màn camera [sig] đang hiện trên display 0".
     *
     * @param sig dấu hiệu màn camera của đời xe (`ClusterProfile.cameraSignature`), vd `"com.byd.avc/"`.
     * @param homeComp component HOME của Kachi (`pkg/cls`) ⇒ dạng K10; `null` ⇒ dạng 2.83.
     * @param cmd lệnh chạy khi rào mở. Không được chứa `'`.
     */
    fun unlessCamera(sig: String, homeComp: String?, cmd: String): String =
        build(sig, homeComp?.let { listOf(it) }, cmd)

    /**
     * Dạng K10 với NHIỀU dạng in của màn nhà Kachi ([homeComps], mỗi cái `pkg/cls`): nhánh mở là `*"a "*|*"b "*)` — một
     * dòng `visible=true` mang BẤT KỲ dạng nào trong số đó là đủ. Vì sao cần ([ĐO máy ảo 02/10, E2E `c5a-trip-generic`]):
     * sau `KachiAutostart` (`am start -n …KachiHomeActivity`, lượt `MY_PACKAGE_REPLACED`) màn nhà đang hiện là task
     * `…KachiHomeActivity` trong một stack `standard` (stack `home` rỗng) và sống qua nhiều lần BYD giết Kachi; chỉ khớp
     * alias `…KachiHome` thì K10 không bao giờ mở. Một phần tử ⇒ trùng TỪNG BYTE [unlessCamera] dạng K10.
     */
    fun unlessCameraOnHome(sig: String, homeComps: List<String>, cmd: String): String {
        require(homeComps.isNotEmpty()) { "dạng K10 cần ít nhất một component HOME" }
        return build(sig, homeComps, cmd)
    }

    /**
     * Rào cho lệnh NGƯỜI DÙNG vừa chạm trên màn nhà (lối tắt *Toàn màn* khi app đang ở ô — K7, spec §4.4.3 dòng 9): chỉ chạy
     * [cmd] khi màn nhà Kachi ([homeComps]) đang hiện trên display 0. [sig] đã biết (Seal DL3) ⇒ đúng dạng K10
     * ([unlessCameraOnHome], nhánh camera đứng trước). [sig] `null` (DL5 / hồ sơ chung — [CHƯA BIẾT] dấu hiệu camera) ⇒ chỉ
     * cổng "màn nhà đang hiện": một màn camera toàn màn đè lên làm stack màn nhà `visible=false` ⇒ không chạy; còn màn camera
     * dạng lớp phủ trong suốt thì không đo được — cùng mức phơi bày với đường Intent của ngăn kéo (cú chạm diễn ra TRÊN màn
     * nhà, không phải tự động lúc nổ máy như K10, nên không cần cấm hẳn như R2.5).
     */
    fun onHomeUnlessCamera(sig: String?, homeComps: List<String>, cmd: String): String {
        if (sig != null) return unlessCameraOnHome(sig, homeComps, cmd)
        require(homeComps.isNotEmpty() && homeComps.all { it.matches(SAFE) }) { "component HOME không hợp lệ: $homeComps" }
        require(cmd.isNotBlank() && !cmd.contains('\'')) { "lệnh rỗng hoặc có dấu ' — không nằm được trong sh -c" }
        return READ + "case \"\$c\" in \"\") ;; " + homeComps.joinToString("|") { "*\"$it \"*" } + ") $cmd ;; esac"
    }

    private const val READ = "c=\$(am stack list | grep -A2 \"displayId=0 \" | grep \"visible=true\") ; "

    private fun build(sig: String, homeComps: List<String>?, cmd: String): String {
        require(sig.matches(SAFE)) { "dấu hiệu camera không hợp lệ: $sig" }
        require(homeComps == null || homeComps.all { it.matches(SAFE) }) { "component HOME không hợp lệ: $homeComps" }
        require(cmd.isNotBlank() && !cmd.contains('\'')) { "lệnh rỗng hoặc có dấu ' — không nằm được trong sh -c" }
        val read = READ
        // `"$comp "` có dấu cách cuối: dòng task in `<comp> bounds=…`/`<comp> userId=…` ⇒ khớp ĐÚNG component, không
        // dính tên dài hơn cùng tiền tố (`…KachiHome` ≠ `…KachiHomeActivity`) — dạng nào được nhận do bên gọi liệt kê.
        val tail = if (homeComps == null) "*) $cmd ;;" else homeComps.joinToString("|") { "*\"$it \"*" } + ") $cmd ;;"
        return read + "case \"\$c\" in \"\") ;; *\"$sig\"*) ;; $tail esac"
    }
}
