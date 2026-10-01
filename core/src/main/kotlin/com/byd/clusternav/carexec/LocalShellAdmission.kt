package com.byd.clusternav.carexec

/**
 * ═══ READY-AT-HOME — CỔNG THI HÀNH ở chỗ lệnh rời tiến trình (spec §4.6, CLAUDE.md §5) ═══════════════════════════
 *
 * Mọi lần Kachi nối adbd `localhost:5555` chỉ đi qua BA cửa [ĐO grep `Dadb.create` 01/10]: `LocalShellSessions.run`
 * (mọi phiên `LocalDeviceShell`), `LocalDeviceShell.installApk` (`:car-integration`) và `ShellTransport.exec` (`:app`). Móc ở ba cửa đó thì
 * phủ mọi bên gọi hôm nay và mai sau — guard ở tầng THI HÀNH, không ở UI (CLAUDE.md §5).
 *
 * Thuần (`:core`): chỉ giữ móc + luật báo kết quả; ba cửa ở `:car-integration`/`:app` gọi vào đây. Mặc định = [ALLOW_ALL]
 * ⇒ test và đường T10 niêm phong (`vehicleprobe`, không đi qua đây) không đổi.
 * `:app` cài móc thật (`ShellReadiness`) ở `KachiApplication.onCreate`, TRƯỚC mọi lượt tự chữa có thể mở phiên.
 *
 * Bên gọi được nhận diện bằng NHÃN [labeled] nếu có (`early`, `f4`), không thì TÊN LUỒNG (rẻ, không dựng stack):
 * `kachi-a11y-lifecycle`, `kachi-window-shell`, `bridge-set-home`… — đủ để dòng `KachiReady deny` nói đường nào bị chặn.
 */
object LocalShellAdmission {

    /** Móc của tầng app. Phải TỰ bắt lỗi của mình — một ngoại lệ lọt ra đây là phiên shell chết theo. */
    interface Hook {
        /** `true` = cho nối. Được phép CHẶN (chờ có hạn) — chỉ khi chính nó xác định đang ở luồng nền. */
        fun admit(kind: ShellSessionKind, caller: String): Boolean

        /** Một sự kiện đo được từ một phiên vừa xong ([ShellReadinessPolicy.outcomeEvent]). */
        fun onEvent(event: ReadyEvent, src: String)
    }

    /** Mặc định: cho tất cả, không nghe gì — đúng hành vi trước READY-AT-HOME. */
    val ALLOW_ALL: Hook = object : Hook {
        override fun admit(kind: ShellSessionKind, caller: String): Boolean = true
        override fun onEvent(event: ReadyEvent, src: String) = Unit
    }

    @Volatile private var hook: Hook = ALLOW_ALL

    fun install(h: Hook) { hook = h }

    /** Chỉ cho test: trả về mặc định. */
    fun reset() { hook = ALLOW_ALL }

    /**
     * Nhãn NGUỒN của các phiên mở trong [block] trên luồng này (vd `"early"`, `"f4"`) — để dòng `KachiReady up src=…` và
     * `deny caller=…` nói ĐƯỜNG nào, không phải tên luồng (`pool-1-thread-1`). Review lượt 1 [P3]: E2E 02/10 ghi
     * `up src=kachi-ready` / `pool-1-thread-1` thay vì `early` / `f4` (spec §4.10). Không nhãn ⇒ tên luồng như cũ.
     */
    fun <T> labeled(src: String, block: () -> T): T {
        val prev = label.get()
        label.set(src)
        try {
            return block()
        } finally {
            label.set(prev)
        }
    }

    private val label = ThreadLocal<String?>()

    private fun caller(): String = label.get() ?: Thread.currentThread().name

    fun admit(kind: ShellSessionKind): Boolean = hook.admit(kind, caller())

    /**
     * Báo kết quả một phiên. Thuần ở phần quyết ([ShellReadinessPolicy.outcomeEvent]); móc chỉ nhận sự kiện đã quyết.
     */
    fun report(
        kind: ShellSessionKind,
        handshook: Boolean,
        failure: LocalShellFailure?,
        dispatched: Boolean,
        eagerHandshake: Boolean,
    ) {
        val event = ShellReadinessPolicy.outcomeEvent(kind, handshook, failure, dispatched, eagerHandshake) ?: return
        hook.onEvent(event, caller())
    }
}
