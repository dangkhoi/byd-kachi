package com.byd.clusternav

import android.app.Application

/**
 * Application của Kachi — điểm dựng [AppContainer] (đồ thị DI thủ công phía launcher) sớm nhất trong tiến trình,
 * để `ShellTransport.get` / `WindowCommandDispatcher.get` (nay uỷ quyền về container) luôn phân giải về MỘT đồ thị.
 *
 * Tối giản: chỉ khởi tạo container; KHÔNG chạm mạng/dadb (các field container đều `by lazy` — chỉ dựng khi được
 * truy cập lần đầu). Đăng ký ở `AndroidManifest.xml` qua `android:name`.
 */
class KachiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContainer.get(this)
    }
}
