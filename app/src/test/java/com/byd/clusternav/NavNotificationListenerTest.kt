package com.byd.clusternav

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Khoá tập gói đi qua **KÊNH NOTIFICATION** = CHỈ GMaps + ReVanced (thu hẹp 2026-08-23).
 *
 * NavNotificationListener.onNotificationPosted/onNotificationRemoved lọc ở MAPS_PACKAGES TRƯỚC KHI gọi
 * parser — một notification không nằm trong tập này không bao giờ tới được parser.
 *
 * ── VÌ SAO ĐỔI (trước 08-23 tập này = `NavApps.ALL`, có cả Waze + VietMap) ────────────────────────────
 * `handle()` gọi `SourceArbiter.shouldFeed(pkg, …)` với kênh mặc định DATA ⇒ đóng mốc `lastDataByPkg[pkg]`
 * ⇒ `isDataFresh(pkg)` = true 6 s ⇒ kênh IMAGE của CHÍNH gói đó bị chặn. Với VietMap — app mà notification
 * KHÔNG mang mũi tên (đo 08-20, `multi-app-nav-source-channels-2026-08-20.md` §1) — hệ quả là mũi tên
 * screen-capture không bao giờ lên được cụm (backlog B3.42, owner chốt: VietMap đi HẲN screen-capture).
 * Với họ Waze thì notification lúc đang dẫn chỉ có `tickerText=Waze` (đo 08-22, B3.32) ⇒ kênh này không
 * cho Waze byte nào, chỉ để lại cái bẫy DATA→IMAGE y hệt. Lý do đầy đủ + bằng chứng: KDoc `NavApps.NOTIFICATION`.
 *
 * ⚠ Ra khỏi roster này KHÔNG mất kênh nào khác: `NavApps.ALL` (a11y) vẫn đủ 5 gói —
 * `NavPackageRosterSyncTest` khoá điều đó.
 */
class NavNotificationListenerTest {
    @Test
    fun `notification channel roster is GMaps only (VietMap and Waze go other channels)`() {
        assertEquals(
            setOf(
                "com.google.android.apps.maps",
                "app.revanced.android.apps.maps",
            ),
            NavNotificationListener.MAPS_PACKAGES,
        )
    }

    /**
     * KHOÁ HỒI QUY B3.42 (mặt roster): VietMap/Waze KHÔNG được quay lại kênh notification. Đưa lại một trong
     * hai là bật lại đúng cái khoá DATA→IMAGE đã giết mũi tên screen-capture của chúng.
     */
    @Test
    fun `VietMap and Waze are NOT on the notification channel`() {
        assertTrue("com.google.android.apps.maps" in NavNotificationListener.MAPS_PACKAGES)
        assertTrue("vn.vietmap.live" !in NavNotificationListener.MAPS_PACKAGES)
        assertTrue("com.waze" !in NavNotificationListener.MAPS_PACKAGES)
        assertTrue("com.chisadin.wazemod" !in NavNotificationListener.MAPS_PACKAGES)
    }

    /**
     * Hai cổng của `onNotificationPosted` phải đúng THỨ TỰ: roster ĐỌC-ĐƯỢC (`NavApps.ALL`) → `Prefs.enabled`
     * → `ensureBridgeStarted()` (lưới an toàn cầu widget VietMap, KHÔNG chạm SourceArbiter) → roster KÊNH
     * (`MAPS_PACKAGES`) → `handle()`.
     *
     * VÌ SAO khoá bằng đọc source: chạy thật cần `NotificationListenerService` (Android). Thứ tự này là thứ
     * giữ cho badge tốc độ VietMap không chết theo lần thu hẹp roster — đặt `ensureBridgeStarted()` SAU cổng
     * kênh là lặng lẽ bỏ đường hồi phục của một máy chỉ dùng VietMap.
     */
    @Test
    fun `onNotificationPosted gates in order - ALL roster, bridge safety net, THEN notification roster`() {
        val body = functionBody(listenerSrc, "override fun onNotificationPosted(sbn: StatusBarNotification?)")
        val iAll = body.indexOf("!in com.byd.clusternav.navigation.NavApps.ALL")
        val iBridge = body.indexOf("ensureBridgeStarted()")
        val iChannel = body.indexOf("!in MAPS_PACKAGES")
        val iHandle = body.indexOf("handle(sbn)")
        assertTrue(iAll >= 0, "thiếu cổng roster đọc-được NavApps.ALL")
        assertTrue(iBridge > iAll, "ensureBridgeStarted phải SAU cổng ALL")
        assertTrue(iChannel > iBridge, "cổng kênh MAPS_PACKAGES phải SAU ensureBridgeStarted (giữ badge VietMap)")
        assertTrue(iHandle > iChannel, "handle() chỉ chạy SAU cổng kênh")
    }

    // ── B1 Lỗ 2 (handoff 2026-08-15): disconnect PHẢI là tín hiệu DƯƠNG "nguồn dừng" ──────────
    // Runtime của onListenerDisconnected cần Android (NotificationListenerService/requestRebind), nên — như
    // NavCastUiWiringContractTest — khoá WIRING bằng cách đọc source: binding rớt phải stop() phiên
    // authoritative (⇒ hudOwner.stop() huỷ nhịp keep-alive, không ghim frame cũ vô hạn) + idle làn cụm.
    private val listenerSrc by lazy {
        SourceRoots.text("src/main/java/com/byd/clusternav/NavNotificationListener.kt")
    }

    /** Trích thân MỘT hàm để khỏi khớp nhầm NavRepository.stop ở nhánh khác (onNotificationRemoved/handle). */
    private fun functionBody(source: String, signature: String): String {
        val start = source.indexOf(signature)
        require(start >= 0) { "missing $signature" }
        val after = start + signature.length
        val next = listOf("\n    fun ", "\n    private fun ", "\n    override fun ", "\n    companion object", "\n}")
            .mapNotNull { source.indexOf(it, after).takeIf { i -> i >= 0 } }
            .minOrNull() ?: source.length
        return source.substring(start, next)
    }

    /**
     * KHOÁ B3.48 (vòng sửa) — **cắt nguồn thì phải kèm lệnh DỪNG**.
     *
     * Từ khi `PREFER_X` rút về `pkg in X`, một gói đang GIỮ cụm có thể bị cổng loại giữa chuyến (user đổi
     * chế độ). Cổng chỉ bỏ khung; nếu không ai phát STOP thì `AmapEmissionArbiter.heartbeat` (400 ms, trần
     * `ClusterBroadcaster.STALE_MS` = 180 s) và `NavigationHudOwner.keepAliveTick` (250 ms, trần
     * `HudKeepAlivePolicy.DEFAULT_MAX_AGE_MS` = 180 s) vẫn phát lại **khung cuối** ⇒ cụm + HUD hiện mũi tên
     * rẽ với cự ly ĐỨNG YÊN suốt 3 phút xe chạy. Owner chốt "không hiện gì", không phải "hiện sai".
     *
     * Runtime cần `NotificationListenerService` nên khoá WIRING bằng đọc source, y như các test wiring khác
     * trong repo. Bất biến an-toàn-AUTO của call site (`release()` không bao giờ true khi cổng từ chối ở
     * AUTO) được khoá riêng ở `:core` — `SourceArbiterAllowsTest`.
     */
    @Test
    fun `handle() - cong nguon TU CHOI dung nguon dang giu thi phai phat lenh dung`() {
        val body = functionBody(listenerSrc, "private fun handle(sbn: StatusBarNotification)")
        // Bất biến phát biểu ở dạng "MỌI cổng", không phải "cổng thứ n": thêm một cổng nguồn thứ ba mà quên
        // lệnh dừng thì test này đỏ, không cần ai nhớ sửa nó.
        val gates = Regex(Regex.escape("if (!SourceArbiter.shouldFeed(")).findAll(body).map { it.range.first }.toList()
        val revokes = Regex(Regex.escape("if (SourceArbiter.release(sbn.packageName)) stopClusterOwnedBy("))
            .findAll(body).map { it.range.first }.toList()
        assertTrue(gates.isNotEmpty(), "thiếu cổng nguồn trong handle()")
        assertEquals(
            gates.size, revokes.size,
            "mỗi cổng nguồn phải có ĐÚNG một nhánh phát lệnh dừng — thiếu nó là để nhịp tim ghim khung cuối " +
                "tới 180 s sau khi user đổi chế độ (cổng=$gates dừng=$revokes)",
        )
        for ((i, g) in gates.withIndex()) {
            assertTrue(revokes[i] > g, "lệnh dừng phải nằm TRONG nhánh bị chặn của cổng thứ ${i + 1}")
            if (i + 1 < gates.size) {
                assertTrue(revokes[i] < gates[i + 1], "lệnh dừng của cổng thứ ${i + 1} bị đẩy sang cổng sau")
            }
        }
    }

    /**
     * KHOÁ: nhánh "đã đến nơi" (R7/#2) KHÔNG được nuốt lệnh dừng khi chính app báo-đến vừa bị cổng nguồn
     * loại. Nó gọi `shouldFeed` TRƯỚC khi stop, nên ở `PREFER_X` một app ngoài nhóm báo "đã đến" sẽ bị bỏ
     * qua — kể cả khi nó đang là nguồn giữ cụm. Đó đúng là lỗi hiện trường R7/#2 quay lại.
     */
    @Test
    fun `handle() - nhanh da-den-noi van dung cum khi nguon giu vua bi cong loai`() {
        val body = functionBody(listenerSrc, "private fun handle(sbn: StatusBarNotification)")
        val arrival = body.indexOf("if (NavArrivalGuard.isArrivalText(title, text, big)) {")
        assertTrue(arrival >= 0, "thiếu nhánh đã-đến-nơi")
        val gate = body.indexOf("if (!SourceArbiter.shouldFeed(", arrival)
        val revoke = body.indexOf("if (SourceArbiter.release(sbn.packageName)) stopClusterOwnedBy(", gate)
        val normal = body.indexOf("stopClusterOwnedBy(sbn.packageName, \"đã đến nơi\")", revoke)
        assertTrue(gate > arrival, "nhánh đã-đến vẫn phải qua trọng tài (R3)")
        assertTrue(revoke > gate, "bị cổng loại mà đang giữ cụm ⇒ vẫn phải dừng")
        assertTrue(normal > revoke, "qua được cổng ⇒ dừng như cũ")
    }

    /**
     * KHOÁ chuỗi teardown dùng chung (CLAUDE.md §8 — hàm mới phải có call site, và phải làm ĐÚNG việc): một
     * lệnh dừng phải gồm cả `NavRepository.stop` (huỷ nhịp keep-alive của cả hai owner) LẪN
     * `ClusterNavLaneWidget.onNavIdle` (hạ bề mặt op-39). Thiếu vế đầu là cụm vẫn ghim khung.
     */
    @Test
    fun `stopClusterOwnedBy dung dung chuoi teardown da proven cua nhanh da-den-noi`() {
        val body = functionBody(listenerSrc, "private fun stopClusterOwnedBy(pkg: String, why: String)")
        assertTrue(body.contains("arrivalGuard.reset()"), "phải reset arrival guard")
        assertTrue(body.contains("TurnDistanceInterpolator.reset()"), "phải reset nội suy cự ly")
        assertTrue(body.contains("NavRepository.stop(applicationContext)"), "phải stop phiên authoritative")
        assertTrue(body.contains("ClusterNavLaneWidget.onNavIdle()"), "phải idle bề mặt làn cụm")
    }

    @Test
    fun `onListenerDisconnected stops the authoritative session (positive source-ended signal)`() {
        val body = functionBody(listenerSrc, "override fun onListenerDisconnected()")
        assertTrue(
            body.contains("NavRepository.stop(applicationContext)"),
            "disconnect phải stop() phiên nav → hudOwner.stop() huỷ nhịp keep-alive (nếu thiếu, frame cũ ghim vô hạn)",
        )
        assertTrue(
            body.contains("ClusterNavLaneWidget.onNavIdle()"),
            "disconnect phải idle làn cụm như nhánh onNotificationRemoved",
        )
    }
}
