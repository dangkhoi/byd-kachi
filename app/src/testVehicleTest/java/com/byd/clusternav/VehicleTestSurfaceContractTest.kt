package com.byd.clusternav

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VehicleTestSurfaceContractTest {
    private val root: Path by lazy { repositoryRoot() }
    private val overlay by lazy { read("app/src/vehicleTest/AndroidManifest.xml") }
    private val receiver by lazy { read("app/src/vehicleTest/java/com/byd/clusternav/HudSignProbeReceiver.kt") }
    private val activity by lazy { read("app/src/vehicleTest/java/com/byd/clusternav/HudSignProbeActivity.kt") }
    private val localAuthorization by lazy {
        read("car-integration/src/main/kotlin/com/byd/clusternav/vehicleprobe/T10LocalAuthorization.kt")
    }
    private val script by lazy { read("scripts/vehicle/run-seal-hud-sign-matrix.sh") }

    @Test
    fun `manifest keeps receiver private and activity launcher-only`() {
        val receiverElement = manifestElement("receiver", ".HudSignProbeReceiver")
        assertTrue(receiverElement.contains("android:exported=\"false\""))
        assertFalse(receiverElement.contains("intent-filter"))
        assertFalse(receiverElement.contains("android:permission"))
        assertFalse(overlay.contains("<uses-permission"))
        assertFalse(overlay.contains("android.permission.DUMP"))

        val activityElement = manifestElement("activity", ".HudSignProbeActivity")
        assertTrue(activityElement.contains("android:exported=\"true\""))
        assertTrue(activityElement.contains("android:noHistory=\"true\""))
        assertTrue(activityElement.contains("android:excludeFromRecents=\"true\""))
        assertEquals(1, Regex("android.intent.action.MAIN").findAll(activityElement).count())
        assertEquals(1, Regex("android.intent.category.LAUNCHER").findAll(activityElement).count())
    }

    @Test
    fun `HAL probe receiver is vehicleTest-only exported with exactly one fixed action`() {
        // The HAL probe is a NEW intended vehicleTest surface (on-car HAL fire tool). It is exported so an adb
        // `am broadcast` from the shell uid can reach it, but it must carry exactly ONE fixed action and add no
        // extra manifest surface. main/release exclusion is guarded by the freedom test below + source-set split.
        val element = manifestElement("receiver", ".HalProbeReceiver")
        assertTrue(element.contains("android:exported=\"true\""), "probe must be reachable by adb on-car")
        assertFalse(element.contains("android:permission"))
        assertEquals(
            1,
            Regex("<intent-filter").findAll(element).count(),
            "HAL probe receiver must declare exactly one intent-filter",
        )
        assertEquals(
            1,
            Regex("com\\.byd\\.clusternav\\.vehicletest\\.HAL_PROBE").findAll(element).count(),
            "exactly one fixed HAL_PROBE action, no other actions",
        )
        assertFalse(overlay.contains("<uses-permission"), "the vehicleTest overlay adds no permissions")
    }

    @Test
    fun `receiver remains private inert and fixed`() {
        val expected = linkedMapOf(
            "ACTION_LIST_PACKAGE_METADATA" to "com.byd.clusternav.vehicleTest.T10_LIST_PACKAGE_METADATA",
            "ACTION_LIST_PROPERTY_CONFIGS" to "com.byd.clusternav.vehicleTest.T10_LIST_PROPERTY_CONFIGS",
            "ACTION_LIST_SERVICE_METADATA" to "com.byd.clusternav.vehicleTest.T10_LIST_SERVICE_METADATA",
            "ACTION_READ_PACKAGE_METADATA" to "com.byd.clusternav.vehicleTest.T10_READ_PACKAGE_METADATA",
            "ACTION_READ_PROPERTY_CONFIG" to "com.byd.clusternav.vehicleTest.T10_READ_PROPERTY_CONFIG",
            "ACTION_READ_SERVICE_METADATA" to "com.byd.clusternav.vehicleTest.T10_READ_SERVICE_METADATA",
        )
        val constants = Regex("internal const val (ACTION_[A-Z_]+)\\s*=\\s*\"([^\"]+)\"")
            .findAll(receiver)
            .associate { it.groupValues[1] to it.groupValues[2] }
        assertEquals(expected, constants)
        assertTrue(receiver.contains("T10FixedOperationCatalog.resolve(probeId)"))
        assertTrue(receiver.contains("BindingBlockReason.MISSING_OPERATIONAL_AUTHORIZATION"))
        assertTrue(receiver.contains("goAsync()"))
        assertTrue(receiver.contains("pending.finish()"))
        assertTrue(receiver.contains("ArrayBlockingQueue(1)"))
        assertNoRuntimePayload(receiver)
        assertFalse(receiver.contains("BydHal."))
        assertFalse(receiver.contains("sendBroadcast("))
    }

    @Test
    fun `activity accepts launcher only and exposes exactly four confirmed fixed cases`() {
        listOf(
            "incoming.data != null",
            "incoming.type != null",
            "incoming.identifier != null",
            "incoming.clipData != null",
            "incoming.extras != null",
            "incoming.selector != null",
            "incoming.`package` != null",
            "categories.size == 1",
            "Intent.CATEGORY_LAUNCHER",
        ).forEach { token -> assertTrue(activity.contains(token), "missing activity guard: $token") }

        assertEquals(4, Regex("FieldCase\\(\\s*CaseId\\.").findAll(activity).count())
        listOf("CaseId.M1_M2", "CaseId.M3_S1", "CaseId.M3_S5", "CaseId.M4_GATE").forEach {
            assertTrue(activity.contains(it), "missing fixed case $it")
        }
        assertEquals(5, Regex("OutcomeRow\\(\"").findAll(activity).count())
        assertTrue(activity.contains("OutcomeRow(\"M1\""))
        assertTrue(activity.contains("OutcomeRow(\"M2\""))
        assertTrue(activity.contains("enum class ObservationOutcome { PASS, FAIL, INCONCLUSIVE, BLOCKED }"))
        assertTrue(activity.contains("AlertDialog.Builder(this)"))
        assertTrue(activity.contains(".setPositiveButton(\"Confirm case\")"))
        assertTrue(activity.contains("zero mutation until explicit confirmation"))
        assertTrue(activity.contains("no tracked evidence file is written"))
    }

    @Test
    fun `matrix pins every source proven id and value exactly`() {
        val expected = linkedMapOf(
            "H1_CONFIG_ID" to "0x38B00030",
            "H1_STATUS_ID" to "0x38B0002E",
            "H1_SET_ID" to "0x32B1102E",
            "H1_CONFIG_REQUIRED" to "1",
            "H1_ON" to "2",
            "H1_OFF" to "1",
            "H2_STATUS_ID" to "0x38B00028",
            "H2_SET_ID" to "0x4C10E03A",
            "H2_ON" to "1",
            "H2_OFF" to "2",
            "S1_ID" to "0x4B40001C",
            "S5_ID" to "0x4B4000AA",
            "SIGN_FIRST" to "50",
            "SIGN_SECOND" to "80",
            "M4_STATUS_ID" to "0x38B0001E",
            "M4_SET_ID" to "0x4C10E030",
            "M4_ON" to "1",
            "M4_OFF" to "2",
        )
        expected.forEach { (name, value) ->
            assertTrue(
                Regex("private const val $name = ${Regex.escape(value)}\\b").containsMatchIn(activity),
                "missing exact constant $name=$value",
            )
        }
        assertTrue(activity.contains("runSignCase(BydHal.STATISTIC, S1_ID)"))
        assertTrue(activity.contains("runSignCase(BydHal.SETTING, S5_ID)"))
        assertTrue(activity.contains("config != H1_CONFIG_REQUIRED"))
        assertTrue(activity.contains("prior !in M4_VALUES"))
    }

    @Test
    fun `matrix uses strict app context fixed reflection and no bypass or runtime execution input`() {
        assertTrue(activity.contains("val app = applicationContext"))
        assertTrue(activity.contains("getMethod(\"getInstance\", Context::class.java)"))
        assertTrue(activity.contains(".invoke(null, app)"))
        assertTrue(activity.contains("BydHal.exemptHiddenApis()"))
        assertTrue(activity.contains("BydHal.setInt("))
        assertTrue(activity.contains("it.name == \"get\""))
        assertTrue(activity.contains("arrayOf(IntArray::class.java, Class::class.java)"))
        assertTrue(activity.contains("arrayOf(IntArray::class.java)"))
        assertTrue(activity.contains("getField(\"intValue\").getInt(eventValue)"))

        listOf(
            "BydHal.bypass(",
            "BydHal.systemBypassContext(",
            "BydHal.device(",
            "ContextWrapper",
            "com.byd.dashcast",
            "Runtime.getRuntime(",
            "ProcessBuilder(",
            "Dadb",
            "EditText",
            "registerReceiver(",
            "SharedPreferences",
            "FileOutputStream",
        ).forEach { token -> assertFalse(activity.contains(token), "forbidden matrix surface $token") }
        assertNoRuntimePayload(activity)
    }

    @Test
    fun `one bounded serialized worker prevents overlapping cases`() {
        assertTrue(activity.contains("ThreadPoolExecutor("))
        assertTrue(activity.contains("ArrayBlockingQueue(1)"))
        assertTrue(activity.contains("CASE_RUNNING.compareAndSet(false, true)"))
        assertTrue(activity.contains("CASE_RUNNING.set(false)"))
        assertTrue(activity.contains("no overlapping case"))
        assertTrue(activity.contains("private const val CASE_TIMEOUT_MS = 10_000L"))
        assertTrue(activity.contains("private const val OBSERVATION_MS = 1_000L"))
        assertTrue(activity.contains("check(remaining >= OBSERVATION_MS)"))
        assertTrue(activity.contains("Thread.sleep(OBSERVATION_MS)"))
    }

    @Test
    fun `every mutation path captures prior arms rollback and restores in finally`() {
        val m1 = function("runM1M2", "runSignCase")
        assertOrdered(
            m1,
            "val h1Prior = readInt(instrument, H1_STATUS_ID)",
            "val h2Prior = readInt(setting, H2_STATUS_ID)",
            "rollbackArmed = true",
            "writeAndVerify(instrument, H1_SET_ID, H1_STATUS_ID, H1_ON)",
            "finally",
            "restoreAndVerify(setting, H2_SET_ID, H2_STATUS_ID, h2Prior)",
            "restoreAndVerify(instrument, H1_SET_ID, H1_STATUS_ID, h1Prior)",
        )

        val sign = function("runSignCase", "runM4Gate")
        assertOrdered(
            sign,
            "val prior = readInt(device, featureId)",
            "rollbackArmed = true",
            "writeAndVerify(device, featureId, featureId, SIGN_FIRST)",
            "writeAndVerify(device, featureId, featureId, SIGN_SECOND)",
            "finally",
            "restoreAndVerify(device, featureId, featureId, prior)",
        )

        val m4 = function("runM4Gate", "strictDevice")
        assertOrdered(
            m4,
            "val prior = readInt(setting, M4_STATUS_ID)",
            "rollbackArmed = true",
            "writeAndVerify(setting, M4_SET_ID, M4_STATUS_ID, target)",
            "finally",
            "restoreAndVerify(setting, M4_SET_ID, M4_STATUS_ID, prior)",
        )
        assertTrue(activity.contains("RECOVERY_BLOCKED.set(true)"))
        assertTrue(activity.contains("STOP/RESTORE_FAILED"))
        assertTrue(activity.contains("caseButtons.forEach { it.isEnabled = enabled }"))
    }

    @Test
    fun `M1 M2 sends exactly two fixed frames then proven reset and restores gates`() {
        assertEquals(2, Regex("AmapFrameBuilder\\.buildGuidanceFrame\\(").findAll(activity).count())
        assertEquals(2, Regex("AmapFrameBuilder\\.buildStateFrame\\(").findAll(activity).count())
        assertTrue(activity.indexOf("ROAD_A_STATE") < activity.indexOf("ROAD_B_STATE"))
        assertTrue(activity.indexOf("road = \"Road A\"") < activity.indexOf("road = \"Road B\""))
        val reset = function("sendFieldProvenStopResetPair", "deadlineFromNow")
        assertOrdered(reset, "STATE_STOP, true", "STATE_STOP, false")
        val m1 = function("runM1M2", "runSignCase")
        assertOrdered(
            m1,
            "sendTwoFixedGuidanceFrames",
            "sendFieldProvenStopResetPair",
            "restoreAndVerify(setting, H2_SET_ID",
            "restoreAndVerify(instrument, H1_SET_ID",
        )
        assertFalse(activity.contains("physical HUD master switch") && activity.contains("H0_SET_ID"))
    }

    @Test
    fun `M4 is labelled gate only and cannot infer HUD sign from M3`() {
        assertTrue(activity.contains("safe-driving gate only"))
        assertTrue(activity.contains("gate only; no HUD-sign claim without visible sign content"))
        assertTrue(activity.contains("Never infer M4 from M3"))
        assertTrue(activity.contains("val target = if (prior == M4_ON) M4_OFF else M4_ON"))
    }

    @Test
    fun `future vehicle wrapper remains fixed no-argument and authorization-gated`() {
        assertEquals("#!/bin/bash -p", script.lineSequence().first())
        assertTrue(script.contains("set -euo pipefail"))
        assertTrue(script.contains("if (( \$# != 0 )); then"))
        val fixedAuthorizationPath = Regex("""const val FIXED_RELATIVE_PATH = "([^"]+)"""")
            .find(localAuthorization)?.groupValues?.get(1) ?: error("fixed authorization path missing")
        assertEquals(".t10-local/session-n-read-only-authorization.json", fixedAuthorizationPath)
        assertTrue(script.contains(fixedAuthorizationPath))
        assertFalse(Regex("\\$\\{?[1-9]").containsMatchIn(script))
    }

    @Test
    fun `main and release remain free of vehicleTest matrix surface`() {
        val forbidden = listOf(
            "HudSignProbeReceiver",
            "HudSignProbeActivity",
            "com.byd.clusternav.vehicleTest.T10_",
            "T10FastField",
            "HalProbeReceiver",
            "HalProbePresets",
            "com.byd.clusternav.vehicletest.HAL_PROBE",
        )
        listOf("app/src/main", "app/src/release").map(root::resolve).filter(Files::exists).forEach { sourceRoot ->
            Files.walk(sourceRoot).use { paths ->
                paths.filter(Files::isRegularFile)
                    .filter { file ->
                        file.toString().endsWith(".kt") || file.toString().endsWith(".java") ||
                            file.toString().endsWith(".xml")
                    }
                    .forEach { file ->
                        val content = Files.readString(file)
                        forbidden.forEach { token ->
                            assertFalse(content.contains(token), "$file exposes forbidden vehicleTest token $token")
                        }
                    }
            }
        }
    }

    private fun assertNoRuntimePayload(source: String) {
        listOf(
            "getStringExtra(",
            "getIntExtra(",
            "getLongExtra(",
            "getSerializableExtra(",
            "getParcelableExtra(",
            "putExtra(",
        ).forEach { token -> assertFalse(source.contains(token), "forbidden runtime payload $token") }
    }

    private fun assertOrdered(source: String, vararg tokens: String) {
        var previous = -1
        tokens.forEach { token ->
            val index = source.indexOf(token)
            assertTrue(index > previous, "missing or out-of-order token: $token")
            previous = index
        }
    }

    private fun function(name: String, nextName: String): String {
        val start = activity.indexOf("private fun $name")
        val end = activity.indexOf("private fun $nextName", start + 1)
        assertTrue(start >= 0 && end > start, "cannot isolate $name")
        return activity.substring(start, end)
    }

    private fun manifestElement(tag: String, name: String): String = manifestElement(overlay, tag, name)

    private fun manifestElement(xml: String, tag: String, name: String): String {
        val paired = Regex("<$tag\\b[^>]*android:name=\"${Regex.escape(name)}\"[^>]*>[\\s\\S]*?</$tag>")
            .find(xml)?.value
        val single = Regex("<$tag\\b[^>]*android:name=\"${Regex.escape(name)}\"[^>]*/>")
            .find(xml)?.value
        // Prefer the self-closing (single) match: a self-closing element followed by a later paired element of
        // the same tag would otherwise let the greedy `paired` regex span across both. Paired elements never
        // match `single` (their opening tag ends in `>`, not `/>`), so they still resolve via `paired`.
        return single ?: paired ?: error("manifest element missing: $tag $name")
    }

    private fun read(relative: String): String = Files.readString(root.resolve(relative))

    private fun repositoryRoot(): Path {
        var current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
        while (!Files.exists(current.resolve("settings.gradle.kts"))) {
            current = current.parent ?: error("repository root not found")
        }
        return current
    }
}
