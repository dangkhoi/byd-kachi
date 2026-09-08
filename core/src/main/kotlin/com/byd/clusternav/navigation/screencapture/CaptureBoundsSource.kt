package com.byd.clusternav.navigation.screencapture

/**
 * Snapshot bounds ĐỘNG mà `NavAccessibilityService` (:app) đọc được (`getBoundsInScreen` của node mũi tên
 * Waze / icon camera VietMap) — nguồn cho TẦNG 1 của [CaptureRouter.computeBounds] (§4.4 spec
 * waze-vietmap-screen-capture). Service GHI, [com.byd.clusternav.navigation.screencapture] ĐỌC qua router.
 * Thuần `@Volatile`, không khoá — cùng khuôn với `NavAccessibilitySource` (Q1: state holder thuần ở :core).
 *
 * Toạ độ TUYỆT ĐỐI trong không gian ảnh của display app dẫn đang ở; router tự clamp về nửa app khi split.
 * Nếu a11y KHÔNG publish (không tìm được node / chưa cấp quyền hỗ trợ) thì snapshot cũ hết tươi → router tự
 * rớt xuống tầng cố-định (degrade-safe, R-nf1).
 *
 * ⚠ VERIFY-ON-CAR (OQ2): việc a11y đọc ĐÚNG node mũi tên/camera + bounds ổn định trên xe (nhất là trên
 * display cụm) CHƯA xác minh. Off-car chỉ khoá được đường publish→snapshot + gate freshness (test thuần).
 */
object CaptureBoundsSource {

    /** a11y coi là "tươi" trong bao lâu; router chấm lại bằng `now - capturedAtMs` (đây chỉ là hằng tham chiếu). */
    const val FRESH_MS = 1500L

    /** MỘT snapshot bất biến (không phải 5 `@Volatile` rời) ⇒ rect / chủ / mốc không bao giờ lệch nhau. */
    @Volatile private var snap: CaptureBounds? = null

    /**
     * a11y GHI: vùng node mũi tên/camera (toạ độ màn tuyệt đối) + mốc đơn điệu [now], kèm **chủ sở hữu**
     * [pkg] (§R-BI, package RUNTIME của cửa sổ đo được rect — xem [CaptureBounds.pkg]) và **mục tiêu đã đo
     * cho** [target] (xem [CaptureBounds.target]). Cả hai đều KHÔNG có mặc định: mặc định = cho phép caller
     * lặng lẽ bỏ danh tính / bỏ mục tiêu, mà rect vô-chủ hoặc sai-mục-tiêu là đường ra SAI HƯỚNG.
     */
    fun publish(pkg: String, target: CaptureTarget, l: Int, t: Int, r: Int, b: Int, now: Long) {
        val rect = CropRect(l, t, r, b)
        snap = if (pkg.isEmpty() || rect.isEmpty() || now <= 0L) null
        else CaptureBounds(rect, now, pkg, target)
    }

    /**
     * Snapshot cho router (tầng 1). null nếu chưa từng publish HOẶC rect rỗng HOẶC vô chủ. Router tự chấm
     * freshness bằng `now - capturedAtMs <= freshMs` VÀ **mục tiêu** ([CaptureBounds.target] phải khớp target
     * của plan), nên ở đây KHÔNG lọc theo thời gian (giữ hàm thuần,
     * không đọc đồng hồ); danh tính cũng do consumer chốt (`NavFrameIdentity.sameFrame`).
     */
    fun snapshot(): CaptureBounds? = snap

    /** Xoá (nav idle / mất foreground) → router rớt về tầng cố-định. */
    fun clear() {
        snap = null
    }
}

/**
 * Snapshot package dẫn đang FOREGROUND theo a11y — cho GATE của nguồn screen-capture (R5) khi KHÔNG có kênh
 * data (Waze/VietMap không noti → `SourceArbiter` không giữ khoá). `SourceArbiter` chỉ tươi khi có frame
 * data; đây là đường thứ hai để gate mở cho ca ẢNH-thuần.
 *
 * `NavAccessibilitySource.foreground` chỉ theo GMaps; holder này theo MỌI nav package (Waze/VietMap/WazeMod/
 * GMaps) để gate ảnh mở đúng cho app đang thật sự ở foreground.
 *
 * ⚠ VERIFY-ON-CAR (OQ2): phụ thuộc a11y được cấp quyền + app bắn event trên xe.
 */
object CaptureForegroundSource {

    /** Cùng ngưỡng tươi với `NavAccessibilitySource.FRESH_MS` (3s) — foreground event thưa hơn bounds. */
    const val FRESH_MS = 3000L

    @Volatile var pkg: String? = null
        private set
    @Volatile private var lastEventAt = 0L

    /** a11y GHI khi có event của một nav package (foreground trên display của nó). */
    fun publish(pkg: String, now: Long) {
        this.pkg = pkg
        lastEventAt = now
    }

    /** Foreground còn tươi không (gate). */
    fun isFresh(now: Long): Boolean = lastEventAt > 0L && now - lastEventAt <= FRESH_MS

    fun clear() {
        pkg = null
        lastEventAt = 0L
    }
}

/**
 * B3.7 — quyết THUẦN (không Android): một AccessibilityEvent có được phép (RE)ĐỊNH nghĩa package dẫn đang
 * FOREGROUND (ghi vào [CaptureForegroundSource]) không, hay nó chỉ đến từ một cửa sổ NỔI/overlay/hệ thống
 * và KHÔNG được đè lên app foreground THẬT?
 *
 * Bối cảnh (multi-app overlay contamination): khi WazeMod vẽ overlay NỔI đè lên trong lúc VietMap mới là app
 * foreground thật, các event nội-dung/announcement của overlay (packageName = WazeMod) từng CHIẾM tín hiệu
 * foreground → B3 định tuyến VietMap sang ARROW thay vì CAMERA. Sửa: overlay/hệ-thống KHÔNG bao giờ là
 * foreground; cửa sổ APP chỉ tính khi hệ thống đánh dấu active/focused.
 *
 * ⚠ Window info (type/active/focused) chỉ có khi service bật `flagRetrieveInteractiveWindows` — cờ này ĐANG
 * TẮT vì lý do hiệu năng (xem `nav_accessibility_config.xml`: nó bắt system_server theo dõi MỌI cửa sổ trên
 * MỌI display, gấp đôi khi đang chiếu). Vì vậy trên xe [windowType] thường = [TYPE_UNKNOWN] và quyết định RỚT
 * về NGỮ NGHĨA EVENT: một `TYPE_WINDOW_STATE_CHANGED` là chuyển-foreground thật; ngoài ra chỉ LÀM TƯƠI lại
 * đúng package đang là foreground (keep-alive) — nên overlay của package KHÁC không thể cướp qua cập-nhật
 * nội-dung thụ động. Nhánh window-info vẫn ĐÚNG nếu sau này bật cờ (đã unit-test cả hai nhánh).
 */
object ForegroundWindowFilter {

    /** Window info không đọc được (cờ interactive-windows tắt) → dùng ngữ nghĩa event. */
    const val TYPE_UNKNOWN = -1

    // Trùng giá trị AccessibilityWindowInfo.TYPE_* (API) để :app truyền thẳng `window.type` vào đây.
    const val TYPE_APPLICATION = 1
    const val TYPE_INPUT_METHOD = 2
    const val TYPE_SYSTEM = 3
    const val TYPE_ACCESSIBILITY_OVERLAY = 4
    const val TYPE_SPLIT_SCREEN_DIVIDER = 5
    const val TYPE_MAGNIFICATION_OVERLAY = 6

    /**
     * @param windowType                AccessibilityWindowInfo.type, hoặc [TYPE_UNKNOWN] khi không đọc được.
     * @param isActive                  window.isActive (chỉ có nghĩa khi windowType != UNKNOWN).
     * @param isFocused                 window.isFocused (chỉ có nghĩa khi windowType != UNKNOWN).
     * @param isWindowStateChange       event.eventType == TYPE_WINDOW_STATE_CHANGED (chuyển foreground thật).
     * @param isSameAsCurrentForeground event.packageName == package [CaptureForegroundSource] đang giữ.
     * @param isFromActiveWindow        event.packageName == package của `rootInActiveWindow` (cửa sổ ACTIVE
     *                                  thật = app foreground; overlay KHÔNG phải active window). Đây là tín
     *                                  hiệu foreground THẬT ở đường UNKNOWN (không cần chờ WINDOW_STATE_CHANGED)
     *                                  → BOOTSTRAP đúng ngay cả khi ClusterNav khởi động lúc nav app đã mở sẵn.
     */
    fun shouldPublishForeground(
        windowType: Int,
        isActive: Boolean,
        isFocused: Boolean,
        isWindowStateChange: Boolean,
        isSameAsCurrentForeground: Boolean,
        isFromActiveWindow: Boolean = false,
    ): Boolean {
        // 1) Cửa sổ overlay/hệ thống (IME · system · a11y-overlay · divider · magnifier) KHÔNG bao giờ là
        //    foreground app — đây là ca WazeMod overlay khi CÓ window info.
        if (isKnownNonAppWindow(windowType)) return false
        // 2) Cửa sổ APP thật: chỉ có thẩm quyền khi hệ thống đánh dấu active HOẶC focused. Overlay báo
        //    TYPE_APPLICATION nhưng không active/không focused → loại.
        if (windowType == TYPE_APPLICATION) return isActive || isFocused
        // 3) Không có window info (UNKNOWN — đường hiệu-năng trên xe/emulator). Tin cửa sổ ACTIVE thật
        //    ([isFromActiveWindow]) nếu đọc được → bootstrap + loại overlay (overlay ≠ active window). Rớt về
        //    ngữ nghĩa event khi không đọc được: WINDOW_STATE_CHANGED (chuyển foreground) hoặc keep-alive
        //    đúng package đang giữ (overlay package KHÁC không cướp được).
        return isWindowStateChange || isFromActiveWindow || isSameAsCurrentForeground
    }

    private fun isKnownNonAppWindow(t: Int): Boolean =
        t == TYPE_INPUT_METHOD || t == TYPE_SYSTEM || t == TYPE_ACCESSIBILITY_OVERLAY ||
            t == TYPE_SPLIT_SCREEN_DIVIDER || t == TYPE_MAGNIFICATION_OVERLAY
}
