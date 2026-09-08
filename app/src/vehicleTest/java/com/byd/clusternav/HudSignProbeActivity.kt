package com.byd.clusternav
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import com.byd.clusternav.modules.hal.BydHal
import java.lang.reflect.Array as ReflectArray
import java.lang.reflect.Method
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
/** vehicleTest-only, fixed T10 matrix. Nothing runs until a human confirms one case. */
class HudSignProbeActivity : Activity() {
    private lateinit var sessionStatus: TextView
    private val caseButtons = mutableListOf<Button>()
    private val outcomeGroups = mutableMapOf<String, RadioGroup>()
    private val outcomeLabels = mutableMapOf<String, TextView>()
    private val completedOutcomes = mutableSetOf<String>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!isAcceptedLauncherIntent(intent)) {
            finish()
            return
        }
        setContentView(buildContent())
    }
    private fun isAcceptedLauncherIntent(incoming: Intent?): Boolean {
        incoming ?: return false
        if (incoming.action != Intent.ACTION_MAIN) return false
        if (incoming.component?.packageName != packageName ||
            incoming.component?.className != HudSignProbeActivity::class.java.name
        ) return false
        if (incoming.data != null || incoming.type != null || incoming.identifier != null) return false
        if (incoming.clipData != null || incoming.extras != null || incoming.selector != null) return false
        if (incoming.`package` != null) return false
        val categories = incoming.categories ?: return false
        return categories.size == 1 && categories.contains(Intent.CATEGORY_LAUNCHER)
    }
    private fun buildContent(): View = ScrollView(this).apply {
        addView(LinearLayout(this@HudSignProbeActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(20), dp(24), dp(28))
            addView(text("T10 fast fixed vehicle matrix", 24f))
            addView(text("OFF-CAR preparation never runs a case. On-car, a human must confirm exactly one fixed case.", 16f))
            addView(text("No IDs, packages, values, selectors, or commands are accepted at runtime.", 14f))
            addView(text("Every write requires a synchronous prior and is restored in finally. Restore failure stops all later cases.", 14f))
            sessionStatus = text(
                if (RECOVERY_BLOCKED.get()) STOP_RESTORE_FAILED else "READY — zero mutation until explicit confirmation",
                16f,
            )
            addView(sessionStatus)
            FIELD_CASES.forEach { fieldCase ->
                addView(caseRow(fieldCase))
            }
            addView(sectionTitle("Direct observation outcomes"))
            addView(text("Choose only after that case finishes. Results stay in memory; no tracked evidence file is written.", 14f))
            OUTCOME_ROWS.forEach { addView(outcomeRow(it)) }
        })
        updateControls()
    }
    private fun caseRow(fieldCase: FieldCase): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dp(16), 0, dp(8))
        addView(text(fieldCase.title, 18f))
        addView(text(fieldCase.detail, 14f))
        addView(Button(this@HudSignProbeActivity).apply {
            isAllCaps = false
            text = "Confirm and run ${fieldCase.buttonLabel}"
            setOnClickListener { confirmCase(fieldCase) }
            caseButtons += this
        }, matchWidth())
    }
    private fun outcomeRow(row: OutcomeRow): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dp(10), 0, dp(10))
        addView(text(row.label, 16f))
        val selection = text("NOT_RUN — select only after direct observation", 13f)
        val choices = RadioGroup(this@HudSignProbeActivity).apply {
            orientation = RadioGroup.HORIZONTAL
            ObservationOutcome.entries.forEach { outcome ->
                addView(RadioButton(this@HudSignProbeActivity).apply {
                    id = View.generateViewId()
                    text = outcome.name
                    tag = outcome
                    isEnabled = false
                })
            }
            setOnCheckedChangeListener { group, checkedId ->
                val button = group.findViewById<RadioButton>(checkedId) ?: return@setOnCheckedChangeListener
                val outcome = button.tag as ObservationOutcome
                selection.text = "${row.id}: ${outcome.name} — in-memory observation only"
            }
        }
        outcomeGroups[row.id] = choices
        outcomeLabels[row.id] = selection
        addView(choices, matchWidth())
        addView(selection)
    }
    private fun confirmCase(fieldCase: FieldCase) {
        if (RECOVERY_BLOCKED.get()) {
            sessionStatus.text = STOP_RESTORE_FAILED
            updateControls()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Confirm fixed ${fieldCase.buttonLabel} case")
            .setMessage(fieldCase.confirmation)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Confirm case") { _, _ -> startCase(fieldCase) }
            .show()
    }
    private fun startCase(fieldCase: FieldCase) {
        if (RECOVERY_BLOCKED.get()) {
            sessionStatus.text = STOP_RESTORE_FAILED
            updateControls()
            return
        }
        if (!CASE_RUNNING.compareAndSet(false, true)) {
            sessionStatus.text = "BLOCKED/CASE_ALREADY_RUNNING"
            return
        }
        fieldCase.outcomeIds.forEach { id ->
            completedOutcomes.remove(id)
            outcomeGroups[id]?.clearCheck()
            outcomeLabels[id]?.text = "RUNNING — wait for restore verification"
        }
        sessionStatus.text = "RUNNING ${fieldCase.buttonLabel} — no overlapping case"
        updateControls()
        try {
            WORKER.execute {
                val result = executeCase(fieldCase.id)
                CASE_RUNNING.set(false)
                runOnUiThread {
                    if (RECOVERY_BLOCKED.get()) {
                        sessionStatus.text = STOP_RESTORE_FAILED
                    } else {
                        completedOutcomes += fieldCase.outcomeIds
                        sessionStatus.text = result
                        fieldCase.outcomeIds.forEach { id ->
                            outcomeLabels[id]?.text = "$id: case complete — select direct observation outcome"
                        }
                    }
                    updateControls()
                }
            }
        } catch (_: RejectedExecutionException) {
            CASE_RUNNING.set(false)
            sessionStatus.text = "BLOCKED/WORKER_BUSY"
            updateControls()
        }
    }
    private fun executeCase(caseId: CaseId): String = try {
        when (caseId) {
            CaseId.M1_M2 -> runM1M2()
            CaseId.M3_S1 -> runSignCase(BydHal.STATISTIC, S1_ID)
            CaseId.M3_S5 -> runSignCase(BydHal.SETTING, S5_ID)
            CaseId.M4_GATE -> runM4Gate()
        }
    } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
        terminalFailure()
    } catch (_: ReflectiveOperationException) {
        terminalFailure()
    } catch (_: RuntimeException) {
        terminalFailure()
    } catch (_: LinkageError) {
        terminalFailure()
    }
    private fun terminalFailure(): String =
        if (RECOVERY_BLOCKED.get()) STOP_RESTORE_FAILED else "FAIL/OPERATION — exact prior restored"
    private fun runM1M2(): String {
        val instrument = strictDevice(BydHal.INSTRUMENT) ?: return "BLOCKED/DEVICE_UNAVAILABLE — zero write"
        val setting = strictDevice(BydHal.SETTING) ?: return "BLOCKED/DEVICE_UNAVAILABLE — zero write"
        val config = readInt(instrument, H1_CONFIG_ID)
        val h1Prior = readInt(instrument, H1_STATUS_ID) ?: return "BLOCKED/PRIOR_OR_CONFIG_INVALID — zero write"
        val h2Prior = readInt(setting, H2_STATUS_ID) ?: return "BLOCKED/PRIOR_OR_CONFIG_INVALID — zero write"
        if (config != H1_CONFIG_REQUIRED || h1Prior !in H1_VALUES || h2Prior !in H2_VALUES) {
            return "BLOCKED/PRIOR_OR_CONFIG_INVALID — zero write"
        }
        var rollbackArmed = false
        var operationOk = false
        var resetOk = true
        try {
            rollbackArmed = true
            operationOk = writeAndVerify(instrument, H1_SET_ID, H1_STATUS_ID, H1_ON)
            if (operationOk) operationOk = writeAndVerify(setting, H2_SET_ID, H2_STATUS_ID, H2_ON)
            if (operationOk) operationOk = sendTwoFixedGuidanceFrames(deadlineFromNow())
        } finally {
            if (rollbackArmed) {
                resetOk = sendFieldProvenStopResetPair()
                val h2Restored = restoreAndVerify(setting, H2_SET_ID, H2_STATUS_ID, h2Prior)
                val h1Restored = restoreAndVerify(instrument, H1_SET_ID, H1_STATUS_ID, h1Prior)
                if (!h2Restored || !h1Restored) lockRecovery()
            }
        }
        if (RECOVERY_BLOCKED.get()) return STOP_RESTORE_FAILED
        return if (operationOk && resetOk) {
            "READY/M1_M2_RESTORED — select M1 and M2 independently"
        } else {
            "FAIL/M1_M2_OR_RESET — exact H2 then H1 prior restored"
        }
    }
    private fun runSignCase(deviceName: String, featureId: Int): String {
        val device = strictDevice(deviceName) ?: return "BLOCKED/DEVICE_UNAVAILABLE — zero write"
        val prior = readInt(device, featureId) ?: return "BLOCKED/PRIOR_UNAVAILABLE — zero write"
        val deadline = deadlineFromNow()
        var rollbackArmed = false
        var operationOk = false
        try {
            rollbackArmed = true
            operationOk = writeAndVerify(device, featureId, featureId, SIGN_FIRST)
            if (operationOk) boundedObservation(deadline)
            if (operationOk) operationOk = writeAndVerify(device, featureId, featureId, SIGN_SECOND)
            if (operationOk) boundedObservation(deadline)
        } finally {
            if (rollbackArmed && !restoreAndVerify(device, featureId, featureId, prior)) lockRecovery()
        }
        if (RECOVERY_BLOCKED.get()) return STOP_RESTORE_FAILED
        return if (operationOk) {
            "READY/M3_RESTORED — select direct cluster observation"
        } else {
            "FAIL/M3_WRITE_OR_READBACK — exact prior restored"
        }
    }
    private fun runM4Gate(): String {
        val setting = strictDevice(BydHal.SETTING) ?: return "BLOCKED/DEVICE_UNAVAILABLE — zero write"
        val prior = readInt(setting, M4_STATUS_ID)
            ?: return "BLOCKED/PRIOR_INVALID — zero write"
        if (prior !in M4_VALUES) return "BLOCKED/PRIOR_INVALID — zero write"
        val target = if (prior == M4_ON) M4_OFF else M4_ON
        val deadline = deadlineFromNow()
        var rollbackArmed = false
        var operationOk = false
        try {
            rollbackArmed = true
            operationOk = writeAndVerify(setting, M4_SET_ID, M4_STATUS_ID, target)
            if (operationOk) boundedObservation(deadline)
        } finally {
            if (rollbackArmed && !restoreAndVerify(setting, M4_SET_ID, M4_STATUS_ID, prior)) lockRecovery()
        }
        if (RECOVERY_BLOCKED.get()) return STOP_RESTORE_FAILED
        return if (operationOk) {
            "READY/M4_GATE_RESTORED — gate only; no HUD-sign claim without visible sign content"
        } else {
            "FAIL/M4_GATE_WRITE_OR_READBACK — exact prior restored"
        }
    }
    private fun strictDevice(deviceName: String): Any? {
        BydHal.exemptHiddenApis()
        val app = applicationContext
        return try {
            Class.forName(deviceName)
                .getMethod("getInstance", Context::class.java)
                .invoke(null, app)
        } catch (_: ReflectiveOperationException) { null } catch (_: SecurityException) { null } catch (_: LinkageError) { null }
    }
    private fun readInt(device: Any, featureId: Int): Int? {
        val methods = device.javaClass.methods
        val eventValueClass = try {
            Class.forName(BydHal.EV)
        } catch (_: ReflectiveOperationException) {
            return null
        } catch (_: LinkageError) {
            return null
        }
        val typed = methods.firstOrNull {
            it.name == "get" && it.parameterTypes.contentEquals(
                arrayOf(IntArray::class.java, Class::class.java),
            )
        }
        val plain = methods.firstOrNull {
            it.name == "get" && it.parameterTypes.contentEquals(arrayOf(IntArray::class.java))
        }
        for (method in listOfNotNull(typed, plain)) {
            val result = invokeFixedRead(method, device, featureId, eventValueClass) ?: continue
            extractIntValue(result)?.let { return it }
        }
        return null
    }
    private fun invokeFixedRead(method: Method, device: Any, featureId: Int, eventValueClass: Class<*>): Any? = try {
        if (method.parameterTypes.size == 2) {
            method.invoke(device, intArrayOf(featureId), eventValueClass)
        } else {
            method.invoke(device, intArrayOf(featureId))
        }
    } catch (_: ReflectiveOperationException) {
        null
    } catch (_: RuntimeException) {
        null
    }
    private fun extractIntValue(result: Any): Int? {
        val eventValue = if (result.javaClass.isArray) {
            if (ReflectArray.getLength(result) == 0) return null
            ReflectArray.get(result, 0) ?: return null
        } else {
            result
        }
        return try {
            eventValue.javaClass.getField("intValue").getInt(eventValue)
        } catch (_: ReflectiveOperationException) { null } catch (_: RuntimeException) { null }
    }
    private fun writeAndVerify(device: Any, setId: Int, statusId: Int, value: Int): Boolean {
        BydHal.setInt(device, setId, value)
        return readInt(device, statusId) == value
    }
    private fun restoreAndVerify(device: Any, setId: Int, statusId: Int, prior: Int): Boolean = try {
        BydHal.setInt(device, setId, prior)
        readInt(device, statusId) == prior
    } catch (_: ReflectiveOperationException) {
        false
    } catch (_: RuntimeException) {
        false
    } catch (_: LinkageError) {
        false
    }
    private fun sendTwoFixedGuidanceFrames(deadline: Long): Boolean {
        val roadA = AmapFrameBuilder.buildGuidanceFrame(
            ROAD_A_STATE, byd = false, segOverride = ROAD_A_METERS, hasDistance = true,
        ) ?: return false
        applicationContext.sendBroadcast(roadA)
        boundedObservation(deadline)
        val roadB = AmapFrameBuilder.buildGuidanceFrame(
            ROAD_B_STATE, byd = false, segOverride = ROAD_B_METERS, hasDistance = true,
        ) ?: return false
        applicationContext.sendBroadcast(roadB)
        boundedObservation(deadline)
        return true
    }
    private fun sendFieldProvenStopResetPair(): Boolean {
        var success = true
        try {
            applicationContext.sendBroadcast(
                AmapFrameBuilder.buildStateFrame(
                    AmapFrameBuilder.KEY_TYPE_STATE, AmapFrameBuilder.STATE_STOP, true,
                ),
            )
        } catch (_: RuntimeException) {
            success = false
        }
        try {
            applicationContext.sendBroadcast(
                AmapFrameBuilder.buildStateFrame(
                    AmapFrameBuilder.KEY_TYPE_STATE, AmapFrameBuilder.STATE_STOP, false,
                ),
            )
        } catch (_: RuntimeException) {
            success = false
        }
        return success
    }
    private fun deadlineFromNow(): Long = SystemClock.elapsedRealtime() + CASE_TIMEOUT_MS
    private fun boundedObservation(deadline: Long) {
        val remaining = deadline - SystemClock.elapsedRealtime()
        check(remaining >= OBSERVATION_MS) { "case deadline" }
        Thread.sleep(OBSERVATION_MS)
    }
    private fun lockRecovery() {
        RECOVERY_BLOCKED.set(true)
    }
    private fun updateControls() {
        val enabled = !CASE_RUNNING.get() && !RECOVERY_BLOCKED.get()
        caseButtons.forEach { it.isEnabled = enabled }
        outcomeGroups.forEach { (id, group) ->
            val outcomeEnabled = enabled && id in completedOutcomes
            for (index in 0 until group.childCount) group.getChildAt(index).isEnabled = outcomeEnabled
        }
        if (RECOVERY_BLOCKED.get()) sessionStatus.text = STOP_RESTORE_FAILED
    }
    private fun sectionTitle(value: String): TextView = text(value, 20f).apply {
        setPadding(0, dp(22), 0, dp(4))
    }
    private fun text(value: String, size: Float): TextView = TextView(this).apply {
        text = value
        textSize = size
        setPadding(0, dp(4), 0, dp(4))
    }
    private fun matchWidth() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    )
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private enum class CaseId { M1_M2, M3_S1, M3_S5, M4_GATE }
    private enum class ObservationOutcome { PASS, FAIL, INCONCLUSIVE, BLOCKED }
    private data class FieldCase(
        val id: CaseId, val title: String, val detail: String, val buttonLabel: String,
        val confirmation: String, val outcomeIds: List<String>,
    )
    private data class OutcomeRow(val id: String, val label: String)
    companion object {
        private const val H1_CONFIG_ID = 0x38B00030
        private const val H1_STATUS_ID = 0x38B0002E
        private const val H1_SET_ID = 0x32B1102E
        private const val H1_CONFIG_REQUIRED = 1
        private const val H1_ON = 2
        private const val H1_OFF = 1
        private val H1_VALUES = setOf(H1_ON, H1_OFF)
        private const val H2_STATUS_ID = 0x38B00028
        private const val H2_SET_ID = 0x4C10E03A
        private const val H2_ON = 1
        private const val H2_OFF = 2
        private val H2_VALUES = setOf(H2_ON, H2_OFF)
        private const val S1_ID = 0x4B40001C
        private const val S5_ID = 0x4B4000AA
        private const val SIGN_FIRST = 50
        private const val SIGN_SECOND = 80
        private const val M4_STATUS_ID = 0x38B0001E
        private const val M4_SET_ID = 0x4C10E030
        private const val M4_ON = 1
        private const val M4_OFF = 2
        private val M4_VALUES = setOf(M4_ON, M4_OFF)
        private const val ROAD_A_METERS = 120
        private const val ROAD_B_METERS = 80
        private const val OBSERVATION_MS = 1_000L
        private const val CASE_TIMEOUT_MS = 10_000L
        private const val STOP_RESTORE_FAILED = "STOP/RESTORE_FAILED — recovery lock active; run no later case"
        private val ROAD_A_STATE = NavState(
            active = true,
            distance = "120 m",
            road = "Road A",
            maneuverText = "Continue straight",
            maneuverIcon = 9,
            updatedAt = 0L,
        )
        private val ROAD_B_STATE = NavState(
            active = true,
            distance = "80 m",
            road = "Road B",
            maneuverText = "Continue straight",
            maneuverIcon = 9,
            updatedAt = 0L,
        )
        private val FIELD_CASES = listOf(
            FieldCase(
                CaseId.M1_M2,
                "1. M1 + M2 — navigation HUD and road",
                "Requires H1 config=1 plus readable H1/H2 priors; sends only Road A then Road B and the proven stop/reset pair.",
                "M1 + M2",
                "This writes only the fixed H1/H2 gates, sends two fixed Amap frames, then restores H2 and H1. It never changes the physical HUD master switch. Confirm direct cluster/HUD observation is ready.",
                listOf("M1", "M2"),
            ),
            FieldCase(
                CaseId.M3_S1,
                "2. M3-S1 — statistic speed sign",
                "Exact statistic ID; fixed 50 then 80; blocked with zero write if its exact prior cannot be read.",
                "M3-S1",
                "This writes only statistic 0x4B40001C: prior → 50 → 80 → exact prior. Confirm direct cluster observation is ready.",
                listOf("M3-S1"),
            ),
            FieldCase(
                CaseId.M3_S5,
                "3. M3-S5 — setting speed sign",
                "Exact setting ID; fixed 50 then 80; blocked with zero write if its exact prior cannot be read.",
                "M3-S5",
                "This writes only setting 0x4B4000AA: prior → 50 → 80 → exact prior. Confirm direct cluster observation is ready.",
                listOf("M3-S5"),
            ),
            FieldCase(
                CaseId.M4_GATE,
                "4. M4 — safe-driving gate only",
                "Toggles only the fixed safe-driving gate, observes briefly, then restores. It cannot prove HUD sign without directly visible sign content.",
                "M4 gate",
                "This toggles only setting gate 0x4C10E030 after reading status 0x38B0001E, then restores it. Never infer M4 from M3. Confirm direct HUD observation is ready.",
                listOf("M4-GATE"),
            ),
        )
        private val OUTCOME_ROWS = listOf(
            OutcomeRow("M1", "M1 HUD navigation — observe independently"),
            OutcomeRow("M2", "M2 HUD road name — observe independently"),
            OutcomeRow("M3-S1", "M3 cluster sign via S1 — one dimension"),
            OutcomeRow("M3-S5", "M3 cluster sign via S5 — one dimension"),
            OutcomeRow("M4-GATE", "M4 safe-driving gate only — visible sign content required for any HUD-sign claim"),
        )
        private val CASE_RUNNING = AtomicBoolean(false)
        private val RECOVERY_BLOCKED = AtomicBoolean(false)
        private val WORKER = ThreadPoolExecutor(
            1,
            1,
            10L,
            TimeUnit.SECONDS,
            ArrayBlockingQueue(1),
            { task -> Thread(task, "T10FastField").apply { isDaemon = true } },
            ThreadPoolExecutor.AbortPolicy(),
        ).apply {
            allowCoreThreadTimeOut(true)
        }
    }
}
