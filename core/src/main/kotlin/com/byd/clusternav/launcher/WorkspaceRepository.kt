package com.byd.clusternav.launcher

/**
 * Tầng-dữ-liệu (data-layer seam) cho trạng thái HOME của launcher — ranh giới giữa [HomeViewModel] và nơi lưu bền.
 *
 * Interface THUẦN (:core) nên test được off-car bằng một bản giả in-memory; bản thật `PrefsWorkspaceRepository` (:app)
 * bọc `WorkspacePrefs`/SharedPreferences. Cùng khuôn mẫu Port/Adapter như [AppLauncher]/[CarDataPort] trong :core.
 *
 * Mọi hàm trả/nhận [HomeUiState] (immutable). Ghi bền theo HỒ SƠ đang chọn (khoá prefs scope theo tên hồ sơ).
 */
interface WorkspaceRepository {
    /** Nạp trạng thái đầy đủ của hồ sơ đang chọn (workspace + dock + danh sách hồ sơ + theme). */
    fun load(): HomeUiState

    /** Ghi bền phần lưu-được của [state] (workspace + dock + theme) vào hồ sơ đang chọn. */
    fun persist(state: HomeUiState)

    /** Đổi hồ sơ đang chọn sang [name] rồi trả trạng thái đã nạp lại theo hồ sơ đó. */
    fun switchProfile(name: String): HomeUiState

    /** Thêm hồ sơ [name], đặt làm hồ sơ đang chọn, rồi trả trạng thái đã nạp lại. */
    fun addProfile(name: String): HomeUiState

    /** Xoá hồ sơ [name] (không xoá nếu chỉ còn 1) rồi trả trạng thái đã nạp lại. */
    fun deleteProfile(name: String): HomeUiState

    /**
     * App **mở gần đây** (U3, đường mở-thường) — mới nhất trước. CỐ Ý **không** nằm trong [HomeUiState]: nó chỉ
     * được đọc lúc MỞ ngăn kéo, không tham gia render nên không phải "trạng thái màn hình"; đưa vào state sẽ ép
     * render lại cả HOME mỗi lần mở app mà không được gì.
     *
     * Có thân MẶC ĐỊNH (rỗng / không làm gì) ⇒ bản giả in-memory trong test không phải sửa.
     */
    fun recentApps(): List<String> = emptyList()

    /** Ghi nhận vừa mở [pkg] (đưa lên đầu danh sách gần đây). Mặc định: không nhớ. */
    fun touchRecentApp(pkg: String) {}

    /**
     * Lựa chọn ĐƠN VỊ của người dùng (R11–R13). S4 · R3a: **theo HỒ SƠ** ([ProfileScope.LAUNCHER_PERSONAL_SUFFIXES])
     * — đơn vị là lựa chọn của MỘT người lái, cùng lối với chủ đề/hình nền/ngôn ngữ.
     *
     * Giống [recentApps]: CỐ Ý **không** nằm trong [HomeUiState]. Nó chỉ đổi khi người dùng vào chọn, nên nhét vào
     * state sẽ bắt cả HOME so-sánh-lại mỗi nhịp trạng thái xe mà chẳng được gì. Có thân MẶC ĐỊNH ⇒ bản giả
     * in-memory trong test không phải sửa.
     */
    /** Cấu hình chip thanh trên (RW0 vùng thứ ba). Thân mặc định ⇒ bản giả trong test không phải sửa. */
    fun topStrip(): TopStripConfig = TopStripConfig.DEFAULT

    fun setTopStrip(config: TopStripConfig) {}

    fun unitPrefs(): UnitPrefs = UnitPrefs.DEFAULT

    /** Ghi bền lựa chọn đơn vị. Mặc định: không lưu (bản giả). */
    fun setUnitPrefs(prefs: UnitPrefs) {}

    /**
     * U4 — hình nền + trình chiếu. S4 · R3a: **theo HỒ SƠ** ([ProfileScope.LAUNCHER_PERSONAL_SUFFIXES]).
     * Có thân MẶC ĐỊNH ⇒ bản giả in-memory trong test không phải sửa.
     */
    /** P9 — bố cục tự vẽ. Thân mặc định = chưa vẽ, để bản giả trong test không phải sửa. */
    fun gridLayout(): GridLayout = GridLayout(emptyList())

    /** P9 — lưu bố cục tự vẽ. `null` = bỏ, quay về bố cục sẵn. */
    fun setGridLayout(layout: GridLayout?) {}

    /**
     * Bố cục **đang hiệu lực** + số ô có nội dung của hồ sơ [name] — cho thẻ hồ sơ ở Cài đặt nói ra hồ sơ đó giữ gì
     * (owner 2026-09-14: *"chưa thấy hồ sơ nó gắn với bố cục chỗ nào?"*). `null` preset = hồ sơ đó đang dùng **tự vẽ**.
     *
     * ⚠ Đây là **đường đọc bền của một hồ sơ KHÔNG phải hồ sơ đang dùng**, nên nó không thể lấy từ [HomeUiState] —
     * state chỉ mang dữ liệu của hồ sơ đang dùng. Nó vẫn phải đi qua cổng dữ liệu này chứ không mở một cửa
     * `WorkspacePrefs` thứ hai ở tầng UI: bài học [SOÁT P1-1] (*"đường đọc bền nằm trong tầng UI"* ⇒ hai đường song
     * song) và luật R6 *tầng UI 0 lần chạm nơi lưu*.
     *
     * Có thân MẶC ĐỊNH ⇒ bản giả in-memory trong test không phải sửa.
     */
    fun profileLayout(name: String): Pair<LayoutPreset?, Int> = LayoutPreset.THREE to 0

    fun wallpaperPrefs(): WallpaperPrefs = WallpaperPrefs.DEFAULT

    /** Ghi bền lựa chọn hình nền. Mặc định: không lưu (bản giả). */
    fun setWallpaperPrefs(prefs: WallpaperPrefs) {}

    /**
     * S1·T4 — **tự mở khi nổ máy** (cờ đọc bởi `KachiAutostart.runBoot`). S4 · R3a: **theo HỒ SƠ**
     * ([ProfileScope.LAUNCHER_PERSONAL_SUFFIXES]) — một tài xế muốn Kachi tự lên, người kia thì không.
     *
     * Mặc định `true` để KHỚP mặc định của nơi lưu bền — bản giả trong test không phải sửa, và quan trọng hơn: hai
     * mặc định lệch nhau thì ô tick nói sai trước cả khi có gì được ghi.
     */
    fun autostart(): Boolean = true

    /** Ghi bền cờ tự mở khi nổ máy. Mặc định: không lưu (bản giả). */
    fun setAutostart(on: Boolean) {}

    /**
     * U5 · T3 — NGÔN NGỮ launcher. S4 · R3a: **theo HỒ SƠ** — ngôn ngữ là thuộc tính của người **đọc màn hình**, và
     * từ S4 thì "người đọc" chính là hồ sơ đang dùng (nguồn `<hồ sơ>__lang`, bản phát ở `clusternav_lang`).
     *
     * Thân MẶC ĐỊNH ⇒ bản giả in-memory trong test không phải sửa, và mặc định [LangMode.AUTO] **khớp** mặc định của
     * nơi lưu bền — hai mặc định lệch nhau thì bộ chọn nói sai trước cả khi có gì được ghi (bài học của cờ tự-mở).
     */
    fun langMode(): LangMode = LangMode.AUTO

    /** Ghi bền lựa chọn ngôn ngữ. Mặc định: không lưu (bản giả). */
    fun setLangMode(mode: LangMode) {}

    /**
     * S4 · R6 — **hồ sơ lúc nổ máy**, `null` = *"hồ sơ dùng gần nhất"*. Thay cho `sceneBook()`/`setSceneBook()` của
     * P7/P6 (R1: bỏ hẳn khái niệm cảnh).
     *
     * Theo **XE**, không theo hồ sơ ([ProfileScope.DEVICE_KEYS]): nó CHỌN hồ sơ nên phải đọc được **trước khi** biết
     * hồ sơ nào — để nó trong hồ sơ là đệ quy, đúng ca `active_profile`.
     *
     * ⚠ Bản thi hành phải **gỡ con trỏ treo** (trỏ tới hồ sơ đã xoá) lúc đọc, cùng luật `SceneBook.normalised()` cũ:
     * một con trỏ treo thì launcher nổ máy lên với hồ sơ mặc định mà không ai hiểu vì sao.
     *
     * Thân MẶC ĐỊNH ⇒ bản giả in-memory trong test không phải sửa, và `null` **khớp** mặc định của nơi lưu bền
     * (chưa chọn gì ⇒ dùng hồ sơ gần nhất) — hai mặc định lệch nhau thì màn Cài đặt nói sai trước cả khi có gì được
     * ghi (bài học của cờ tự-mở).
     */
    fun bootProfile(): String? = null

    /** Ghi bền hồ sơ lúc nổ máy; `null` = bỏ chọn (quay về "hồ sơ gần nhất"). Mặc định: không lưu (bản giả). */
    fun setBootProfile(name: String?) {}

    /**
     * S4 · R8 — **Thêm hồ sơ = BẢN SAO của hồ sơ đang dùng**, rồi đặt làm hồ sơ đang chọn.
     *
     * ## Vì sao là một hàm riêng chứ không để UI gọi [addProfile] rồi chép tay
     * R5 nói hồ sơ mới *"chưa có bộ ⇒ giữ nguyên giá trị hiện tại"*, tức **bản sao** là hành vi đúng của cả hai
     * đường. Nhưng phép chép phải chạm tới **mọi** hậu tố của [ProfileScope.LAUNCHER_SUFFIXES] cộng ảnh chụp
     * ClusterNav — để tầng UI làm việc đó là mở lại đúng cái cửa mà luật *"tầng UI 0 lần chạm nơi lưu"* đã đóng, và
     * là chỗ chắc chắn sẽ bỏ sót một hậu tố ở bản sau.
     *
     * Thân MẶC ĐỊNH = [addProfile] (hồ sơ trống): bản giả in-memory trong test không phải sửa, và ca xấu nhất của
     * mặc định này là *"hồ sơ mới trống"* — khó chịu nhưng không mất dữ liệu của ai.
     */
    fun duplicateProfile(name: String): HomeUiState = addProfile(name)
}
