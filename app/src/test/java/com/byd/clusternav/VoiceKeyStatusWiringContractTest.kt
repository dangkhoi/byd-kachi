package com.byd.clusternav

import com.byd.clusternav.testsupport.KotlinSource
import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * WIRING contract for the voice-key BINDING STATUS line + "Kiểm tra / Sửa ngay" (Check / Fix now) button
 * (task `impl-vk-status`, 2026-09-04). The runtime behaviour needs Android (Activity/TextView/dadb), so —
 * like [NavCastUiWiringContractTest] and [LayoutVariantIdParityTest] — this locks the boundary by reading
 * source text across both ends. It exists for the reason [LayoutVariantIdParityTest] documents: a
 * behaviour-adding change whose lesson lives only in a commit message (no test) silently regresses. These
 * assertions go RED if the mapping, the heal reuse, the lifecycle guard, or the onResume wiring is removed.
 *
 * Locks:
 *  • STATUS mapping — `refreshVoiceKeyStatus` reads the ground-truth flag [NavAccessibilitySource.connected]
 *    plus [Prefs.voiceKeyEnabled] and maps the three states to the documented ARGB colors
 *    (grey off / green ACTIVE / red DISCONNECTED), with bilingual labels via [Lang.t] (NOT sealed
 *    `strings.xml`), and is null-safe on a missing view.
 *  • NO-REGRESSION — the recheck button REUSES the aggressive-reset heal
 *    `NavConnect.grantAccessibility(applicationContext, reset = true)` (same entry as the OFF→ON toggle),
 *    while onResume's existing grant stays `reset = false` (the documented double-dadb-session avoidance).
 *  • LIFECYCLE — the delayed recheck is `postDelayed` guarded by `!isFinishing && !isDestroyed`, and the
 *    button's async result posts back via `runOnUiThread` before touching views.
 *  • BOUNDARY — the consumer reads the SAME `@Volatile connected` field the producer
 *    ([NavAccessibilityService.onServiceConnected] sets true / `onUnbind` sets false) writes.
 *  • PARITY — both layout variants declare the two new ids right after `switch_voicekey_enabled`.
 *
 * ── LÀM-ĐỎ (P5.3): xoá `NavConnect.grantAccessibility(applicationContext, reset = true)` khỏi nút, hoặc đổi
 * onResume sang `reset = true`, hoặc bỏ một trong ba màu ⇒ test tương ứng ĐỎ.
 */
class VoiceKeyStatusWiringContractTest {

    private val mainActivity: String by lazy {
        KotlinSource.stripComments(SourceRoots.text("src/main/java/com/byd/clusternav/MainActivity.kt"))
    }
    private val service: String by lazy {
        SourceRoots.text("src/main/java/com/byd/clusternav/modules/navaccess/NavAccessibilityService.kt")
    }
    private val layoutNarrow: String by lazy { SourceRoots.text("src/main/res/layout/activity_main.xml") }
    private val layoutWide: String by lazy { SourceRoots.text("src/main/res/layout-w960dp/activity_main.xml") }

    /** Slice from a top-level method signature to the next top-level member (mirrors NavCastUiWiringContractTest). */
    private fun body(source: String, signature: String): String {
        val start = source.indexOf(signature)
        require(start >= 0) { "missing $signature" }
        val after = start + signature.length
        val next = listOf("\n    fun ", "\n    private fun ", "\n    override fun ", "\n    companion object", "\n}")
            .mapNotNull { source.indexOf(it, after).takeIf { i -> i >= 0 } }
            .minOrNull() ?: source.length
        return source.substring(start, next)
    }

    // ── STATUS mapping ───────────────────────────────────────────────────────
    @Test
    fun `refreshVoiceKeyStatus maps the three binding states to the documented colors`() {
        val b = body(mainActivity, "private fun refreshVoiceKeyStatus()")
        assertTrue(b.contains("Prefs.voiceKeyEnabled(this)"), "reads the voice-key enabled pref")
        assertTrue(
            b.contains("com.byd.clusternav.modules.navaccess.NavAccessibilitySource.connected"),
            "reads the bound ground-truth flag",
        )
        assertTrue(b.contains("?: return"), "null-safe when the status view is absent")
        // grey off / green ACTIVE / red DISCONNECTED — valid ARGB literals.
        assertTrue(b.contains("0xFF9E9E9E"), "grey 'off' color present")
        assertTrue(b.contains("0xFF2E7D32"), "green 'ACTIVE' color present")
        assertTrue(b.contains("0xFFC62828"), "red 'DISCONNECTED' color present")
        assertTrue(b.contains("setTextColor("), "applies a text color")
    }

    @Test
    fun `the status uses runtime bilingual labels not sealed string resources`() {
        val b = body(mainActivity, "private fun refreshVoiceKeyStatus()")
        assertTrue(b.contains("Lang.t("), "labels are bilingual via Lang.t")
        assertTrue(!b.contains("getString("), "must NOT pull from strings.xml (it is byte-sealed)")
        assertTrue(!b.contains("R.string."), "must NOT reference a sealed @string id")
    }

    // ── NO-REGRESSION: heal reuse + onResume stays reset=false ────────────────
    @Test
    fun `the recheck button reuses the aggressive reset grant and refreshes on the UI thread`() {
        val b = body(mainActivity, "private fun setupVoiceKeyControls()")
        assertTrue(b.contains("R.id.btn_voicekey_recheck"), "wires the recheck button")
        assertTrue(
            b.contains("NavConnect.grantAccessibility(applicationContext, reset = true)"),
            "REUSES the established aggressive-reset heal (same as OFF→ON toggle), not a new grant path",
        )
        assertTrue(b.contains("runOnUiThread"), "posts the async result back to the UI thread before touching views")
        assertTrue(b.contains("scheduleVoiceKeyStatusRecheck()"), "re-reads the async bind result after +2s/+5s")
    }

    @Test
    fun `onResume refreshes reschedules and keeps the existing grant at reset=false`() {
        val b = body(mainActivity, "override fun onResume()")
        assertTrue(b.contains("refreshVoiceKeyStatus()"), "onResume refreshes the status immediately")
        assertTrue(b.contains("scheduleVoiceKeyStatusRecheck()"), "onResume reschedules the delayed re-read")
        assertTrue(
            b.contains("NavConnect.grantAccessibility(applicationContext, reset = false)"),
            "onResume's existing voice-key grant stays reset=false (documented double-dadb-session avoidance)",
        )
        assertTrue(
            !b.contains("NavConnect.grantAccessibility(applicationContext, reset = true)"),
            "onResume must NOT use the aggressive reset (that belongs to the toggle / button only)",
        )
    }

    // ── LIFECYCLE ─────────────────────────────────────────────────────────────
    @Test
    fun `the delayed recheck is lifecycle-guarded on the main handler`() {
        val b = body(mainActivity, "private fun scheduleVoiceKeyStatusRecheck()")
        assertTrue(b.contains("postDelayed("), "uses a delayed post (async onServiceConnected)")
        assertTrue(
            b.contains("!isFinishing && !isDestroyed"),
            "guarded so a late callback can't crash / leak after the Activity is gone",
        )
        assertTrue(b.contains("refreshVoiceKeyStatus()"), "the delayed callback re-reads the status")
    }

    // ── BOUNDARY: consumer reads the SAME flag the producer writes ────────────
    @Test
    fun `status consumer reads the same connected flag the service writes`() {
        assertTrue(
            service.contains("NavAccessibilitySource.connected = true"),
            "producer: onServiceConnected sets connected=true",
        )
        assertTrue(
            service.contains("NavAccessibilitySource.connected = false"),
            "producer: onUnbind sets connected=false",
        )
        assertTrue(
            body(mainActivity, "private fun refreshVoiceKeyStatus()")
                .contains("NavAccessibilitySource.connected"),
            "consumer: refreshVoiceKeyStatus reads the same flag",
        )
    }

    // ── PARITY: both variants declare the two new ids after the switch ────────
    @Test
    fun `both layout variants declare the status line and recheck button after the switch`() {
        for ((name, xml) in listOf("narrow" to layoutNarrow, "wide" to layoutWide)) {
            val switch = xml.indexOf("@+id/switch_voicekey_enabled")
            val status = xml.indexOf("@+id/txt_voicekey_status")
            val button = xml.indexOf("@+id/btn_voicekey_recheck")
            assertTrue(switch >= 0, "$name: voice-key switch present")
            assertTrue(status >= 0, "$name: status line id present")
            assertTrue(button >= 0, "$name: recheck button id present")
            assertTrue(switch < status, "$name: status line comes AFTER the switch")
            assertTrue(status < button, "$name: recheck button comes AFTER the status line")
        }
    }
}
