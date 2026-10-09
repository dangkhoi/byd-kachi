package com.byd.clusternav.system.inputd

import android.app.ActivityManager
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.SystemClock
import com.byd.clusternav.launcher.escape.EscapeReturnApi
import com.byd.clusternav.launcher.escape.EscapeReturnGuard

/**
 * ═══ 2.98 · R18 — cơ chế framework của "app thoát ô ⇒ stack về lại màn ảo ô", CHỈ chạy trong daemon uid 2000 ═════════════════
 *
 * Hai phần, cả hai bằng phản chiếu (hàm ẩn; `app_process` của shell không bị chặn hidden-API — cùng cách `EventInjector` gọi
 * `InputManager.injectInputEvent`):
 *  - [Listener] — một `Binder` THÔ đóng vai `ITaskStackListener`. Không biên dịch theo lớp ẩn `android.app.TaskStackListener`
 *    (không có trong SDK): mã giao dịch của `onActivityLaunchOnSecondaryDisplayFailed` đọc lúc chạy từ hằng AIDL sinh ra
 *    (`ITaskStackListener$Stub.TRANSACTION_…`, tên trong [EscapeReturnApi]) ⇒ đúng mọi ROM có hằng đó, không ghi cứng số. Mọi giao
 *    dịch khác (đều `oneway`, `ITaskStackListener.aidl:23`) nhận rồi bỏ.
 *  - [System] — `ActivityTaskManager.getService()` · `registerTaskStackListener` (`ActivityTaskManagerService.java:3452`,
 *    `MANAGE_ACTIVITY_STACKS`) · `getAllStackInfos` (`:2763`) · `moveStackToDisplay` (`:3395`, `INTERNAL_SYSTEM_WINDOW`) ·
 *    `IWindowManager.getWindowingMode` (`WindowManagerService.java:6847`, `INTERNAL_SYSTEM_WINDOW`). Shell có cả hai quyền
 *    ([ĐO nguồn `Shell AndroidManifest` r47]; [ĐO máy ảo 09/10] nguyên mẫu).
 *
 * KHÔNG quyết gì ở đây — quyết ở [EscapeReturnGuard] (`:core`), keo ở [EscapeReturnDaemon].
 */
internal object TaskStackBinder {

    private const val DESCRIPTOR = "android.app.ITaskStackListener"

    /** Sự kiện thô từ [Listener] (mốc [atMs] = `uptimeMillis` lúc nhận, để đo độ trễ tới lúc dời xong). */
    data class Failed(val escape: EscapeReturnGuard.Escape, val atMs: Long)

    class Listener(api: EscapeReturnApi, private val onFailed: (Failed) -> Unit) : Binder() {

        private val failedCode: Int = Class.forName("$DESCRIPTOR\$Stub").getDeclaredField(api.failedTxnField)
            .apply { isAccessible = true }.getInt(null)

        init {
            // owner = null ⇒ `queryLocalInterface` trả null ⇒ `Stub.asInterface` bọc Proxy quanh chính Binder này (đúng thứ ATMS cần).
            attachInterface(null, DESCRIPTOR)
        }

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == failedCode) {
                val at = SystemClock.uptimeMillis()
                data.enforceInterface(DESCRIPTOR)
                // Mã hoá AIDL của `in RunningTaskInfo` (cờ khác-null rồi parcelable) + `int requestedDisplayId` (`ITaskStackListener.aidl:80`).
                val ti = if (data.readInt() != 0) ActivityManager.RunningTaskInfo.CREATOR.createFromParcel(data) else null
                val requested = data.readInt()
                if (ti != null) {
                    val pkg = ti.baseActivity?.packageName ?: ti.baseIntent?.component?.packageName
                    onFailed(Failed(EscapeReturnGuard.Escape(ti.taskId, pkg, requested), at))
                }
                return true
            }
            if (code in FIRST_CALL_TRANSACTION..LAST_CALL_TRANSACTION) return true   // callback khác: oneway, bỏ qua
            return super.onTransact(code, data, reply, flags)
        }
    }

    /** Lời gọi hệ — mỗi hàm có thể ném (phản chiếu / quyền / lỗi phía system_server truyền qua binder); bên gọi bắt. */
    class System(private val api: EscapeReturnApi) {
        private val atm: Any = Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null)
            ?: error("ActivityTaskManager.getService() = null")
        private val listenerIface: Class<*> = Class.forName(DESCRIPTOR)
        private val wm: Any? by lazy {
            Class.forName("android.view.WindowManagerGlobal").getMethod("getWindowManagerService").invoke(null)
        }

        fun register(l: Listener): Any {
            val proxy = Class.forName("$DESCRIPTOR\$Stub").getMethod("asInterface", IBinder::class.java).invoke(null, l)
                ?: error("ITaskStackListener.Stub.asInterface = null")
            unwrap { atm.javaClass.getMethod("registerTaskStackListener", listenerIface).invoke(atm, proxy) }
            return proxy
        }

        fun unregister(proxy: Any) {
            unwrap { atm.javaClass.getMethod("unregisterTaskStackListener", listenerIface).invoke(atm, proxy) }
        }

        fun stacks(): List<EscapeReturnGuard.StackFact> {
            val list = unwrap { atm.javaClass.getMethod(api.stackInfos).invoke(atm) } as? List<*> ?: return emptyList()
            return list.mapNotNull { info -> info?.let(::fact) }
        }

        /** Chế độ cửa sổ của màn [displayId] (`WINDOWING_MODE_UNDEFINED` = 0 khi màn không tồn tại — `WindowManagerService.java:6854-6858`). */
        fun windowingMode(displayId: Int): Int {
            val w = wm ?: error("WindowManagerGlobal.getWindowManagerService() = null")
            return unwrap { w.javaClass.getMethod("getWindowingMode", Int::class.javaPrimitiveType).invoke(w, displayId) } as Int
        }

        fun move(stackId: Int, displayId: Int) {
            unwrap {
                atm.javaClass.getMethod(api.moveStack, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType).invoke(atm, stackId, displayId)
            }
        }

        private fun fact(info: Any): EscapeReturnGuard.StackFact {
            val c = info.javaClass
            val config = c.getField("configuration").get(info)
            val wc = config.javaClass.getField("windowConfiguration").get(config)   // `Configuration.java:341` (@hide)
            return EscapeReturnGuard.StackFact(
                stackId = c.getField("stackId").getInt(info),
                displayId = c.getField("displayId").getInt(info),
                windowingMode = wc.javaClass.getMethod("getWindowingMode").invoke(wc) as Int,
                activityType = wc.javaClass.getMethod("getActivityType").invoke(wc) as Int,
                taskIds = (c.getField("taskIds").get(info) as? IntArray)?.toList().orEmpty(),
            )
        }

        /** Gỡ vỏ `InvocationTargetException` ⇒ bên gọi thấy NGUYÊN lỗi hệ (NPE 08-01 phải được nhận đúng tên lớp). */
        private inline fun <T> unwrap(block: () -> T): T = try {
            block()
        } catch (e: java.lang.reflect.InvocationTargetException) {
            throw e.targetException ?: e
        }
    }
}
