package com.byd.clusternav.telegram

import android.content.Context
import com.byd.clusternav.AppContainer
import com.byd.clusternav.Prefs
import com.byd.clusternav.launcher.MediaBridge
import com.byd.clusternav.launcher.WorkspacePrefs
import com.byd.clusternav.launcher.telegram.PendingDestinations
import com.byd.clusternav.launcher.telegram.TelegramCallback
import com.byd.clusternav.launcher.telegram.TelegramCommand
import com.byd.clusternav.launcher.telegram.TelegramMenu
import com.byd.clusternav.launcher.telegram.TelegramStatusLine
import com.byd.clusternav.automation.AutomationService
import com.byd.clusternav.automation.PendingNavStore
import com.byd.clusternav.telegramPinnedPlaces
import com.byd.clusternav.setTelegramPinnedPlaces

/**
 * Phân giải + thi hành nút MENU (TELEGRAM-BUTTONS §4) — tách khỏi [TelegramBridgeService] theo VAI (trần 500 dòng).
 * Extension của service: dùng chung `runControl`/`readCtl`/`liveNonce`/`pinnedPlaces` (internal) — KHÔNG mở đường
 * thi hành thứ hai. Lệnh xe qua `VoiceControlDispatch` (cổng tốc độ), nhạc qua `MediaBridge`, dẫn đường qua
 * `PendingNavStore` — đúng các đường đã có.
 */
/** Thi hành MỘT nút; trả toast ngắn. Lệnh TUYỆT ĐỐI, qua đúng đường có sẵn (xe/nhạc/dẫn đường). */
internal fun TelegramBridgeService.dispatchMenu(app: Context, cb: TelegramCallback): String = when (cb.domain) {
    "menu" -> ""                                              // chỉ đổi bảng, không thi hành
    "page" -> ""                                              // phân trang, không thi hành
    "ac" -> dispatchAc(app, cb)
    "seat" -> dispatchSeat(app, cb)
    "misc" -> dispatchMisc(app, cb)
    "music" -> dispatchMusic(app, cb)
    "nav" -> dispatchNav(app, cb)
    else -> ""
}

internal fun TelegramBridgeService.dispatchAc(app: Context, cb: TelegramCallback): String = when (cb.action) {
    "open", "noop" -> ""                                      // mở bảng / nút chết
    // Review 26/10 P2-2: mọi nút trả CÂU CỦA XE (runControl chờ VoiceReadback), không câu cố định.
    "auto" -> runControl(app, TelegramCommand.Control("ac_auto", if (cb.id == "on") 1 else 0))
    "temp" -> cb.id.toIntOrNull()?.let { runControl(app, TelegramCommand.Control("temp", it)) } ?: ""
    "tempup", "tempdn" -> stepTemp(app, cb.action == "tempup") ?: "❄️ temp unchanged (too fast or unreadable)"
    "fanup", "fandn" -> stepFan(app, cb.action == "fanup") ?: "💨 fan unchanged (too fast or unreadable)"
    // P2-3: nấc đáy quạt của nút có autoId bị xe BỎ QUA ⇒ "Quạt tắt" (fan 0) phát ac_auto=1 (AUTO), mức khác set thẳng.
    "fan" -> cb.id.toIntOrNull()?.let { lv ->
        if (lv == 0) runControl(app, TelegramCommand.Control("ac_auto", 1))
        else runControl(app, TelegramCommand.Control("fan", lv))
    } ?: ""
    "defrost" -> runControl(app, TelegramCommand.Control("defrost", if (cb.id == "on") 1 else 0))
    "pm25" -> runControl(app, TelegramCommand.Control("pm25", if (cb.id == "on") 1 else 0))
    "macro" -> listOf(
        runControl(app, TelegramCommand.Control("ac_auto", 1)),
        runControl(app, TelegramCommand.Control("temp", 24)),
        runControl(app, TelegramCommand.Control("fan", 4)),
    ).joinToString(" · ")
    else -> ""
}

/** Đọc giá trị MỘT control (Int) qua đường readState (dùng readKey). null = chưa đọc được. */
internal fun TelegramBridgeService.readCtl(app: Context, id: String): Int? {
    val cache = readCache
    if (cache != null && cache.containsKey(id)) return cache[id]          // P2-6: trong pha vẽ, mỗi control đọc một lần
    val v = runCatching { AppContainer.get(app).carControl.readState(id) }.getOrNull()
    cache?.put(id, v)
    return v
}

// QD-08: −/+ đọc tươi rồi gửi MỘT lệnh TUYỆT ĐỐI (không lệnh tương đối). Debounce ~600ms RIÊNG cho nhiệt và quạt
// (review 26/10 P2-5: dùng chung một mốc thì "+1°" rồi "+fan" trong 600ms làm mất lệnh quạt). Trả câu của xe, null = không gửi.
internal fun TelegramBridgeService.stepTemp(app: Context, up: Boolean): String? {
    val now = android.os.SystemClock.uptimeMillis()
    if (now - lastTempStepMs < 600) return null
    lastTempStepMs = now
    val cur = readCtl(app, "temp") ?: return null
    val next = (cur + if (up) 1 else -1).coerceIn(17, 33)
    return runControl(app, TelegramCommand.Control("temp", next))
}

/** P2-8: −/+ quạt (0..7), cùng luật. Mức 0 ⇒ ac_auto (P2-3: xe bỏ qua fan=0). Trả câu của xe, null = không gửi. */
internal fun TelegramBridgeService.stepFan(app: Context, up: Boolean): String? {
    val now = android.os.SystemClock.uptimeMillis()
    if (now - lastFanStepMs < 600) return null
    lastFanStepMs = now
    val cur = readCtl(app, "fan") ?: return null
    val next = (cur + if (up) 1 else -1).coerceIn(0, 7)
    return if (next == 0) runControl(app, TelegramCommand.Control("ac_auto", 1))
    else runControl(app, TelegramCommand.Control("fan", next))
}

internal fun TelegramBridgeService.dispatchSeat(app: Context, cb: TelegramCallback): String {
    if (cb.action != "d" && cb.action != "p") return ""
    val suffix = if (cb.action == "p") "_r" else ""           // lái = seatc/seath, phụ = seatc_r/seath_r
    val side = if (cb.action == "d") "Driver" else "Passenger"
    // P1-4: Tắt phải tắt CẢ mát LẪN sưởi (không chỉ seatc=0, bỏ sót sưởi đang chạy).
    if (cb.id == "off") {
        val a = runControl(app, TelegramCommand.Control("seatc$suffix", 0))
        val b = runControl(app, TelegramCommand.Control("seath$suffix", 0))
        return "🪑 $side: $a · $b"
    }
    // Ghế chỉ có 3 mức Tắt/1/2 (owner 26/10) ⇒ chỉ nhận 1 và 2 ở đây; mức khác không có nút.
    val level = when (cb.id) { "cool1", "heat1" -> 1; "cool2", "heat2" -> 2; else -> return "" }
    val realId = if (cb.id.startsWith("cool")) "seatc$suffix" else "seath$suffix"
    return "🪑 $side: " + runControl(app, TelegramCommand.Control(realId, level))
}

internal fun TelegramBridgeService.dispatchMisc(app: Context, cb: TelegramCallback): String = when (cb.action) {
    "open" -> ""
    "readl" -> runControl(app, TelegramCommand.Control("readl", if (cb.id == "on") 1 else 0))
    "pm25" -> runControl(app, TelegramCommand.Control("pm25", if (cb.id == "on") 1 else 0))
    // Cốp: cổng 0 km/h của VoiceControlDispatch (qua runControl). Kính: không cổng tốc độ — như chạm/giọng nói.
    "trunk" -> runControl(app, TelegramCommand.Control("trunk", if (cb.id == "on") 1 else 0))
    "windows" -> runControl(app, TelegramCommand.Control("windows_all", if (cb.id == "on") 1 else 0))
    else -> ""
}

internal fun TelegramBridgeService.dispatchMusic(app: Context, cb: TelegramCallback): String {
    val media = MediaBridge(app)
    return when (cb.action) {
        "open" -> ""
        "prev" -> if (media.prev()) "⏮ previous" else "no music playing"
        "next" -> if (media.next()) "⏭ next" else "no music playing"
        "pause" -> if (media.pause()) "⏸ paused" else "no music playing"
        "resume" -> if (media.play()) "▶️ playing" else "no music playing"
        else -> ""
    }
}

internal fun TelegramBridgeService.dispatchNav(app: Context, cb: TelegramCallback): String = when (cb.action) {
    "open", "pinned", "go", "also" -> handleNavPick(app, cb)
    "pin" -> pinPlace(app, cb.id)
    "cancel" -> { PendingNavStore.consume(app); "↩️ destination cleared" }
    else -> ""
}

/** Bấm một nơi (go/pinned) hoặc "mở thêm" app (also) ⇒ đặt pending qua đúng đường có sẵn. */
internal fun TelegramBridgeService.handleNavPick(app: Context, cb: TelegramCallback): String {
    if (cb.action == "open") return ""                        // chỉ mở bảng
    val places = WorkspacePrefs(app).savedPlaces()
    val (index, appKey) = when (cb.action) {
        "also" -> { val parts = cb.id.split(":"); (parts.getOrNull(1)?.toIntOrNull() ?: return "") to parts.getOrNull(0) }
        else -> (cb.id.toIntOrNull() ?: return "") to null
    }
    // "pinned" dùng danh sách ghim; "go"/"also" dùng toàn sổ.
    val list = if (cb.action == "pinned") pinnedPlaces(app) else places
    val place = list.getOrNull(index) ?: return "place not found"
    val apps = if (appKey != null) listOf(appKey) else listOf(Prefs.voiceNavDefaultApp(app))
    val dest = PendingDestinations.of(place.name, place.query, apps,
        if (place.hasCoords) "${place.lat}, ${place.lng}" else null) ?: return "could not set"
    PendingNavStore.write(app, dest, requireGps = true)       // GPS bắt buộc (owner 26/10)
    AutomationService.sync(app)
    return "🧭 ${place.name} → ${apps.joinToString("+")}"
}

/** Ghim/bỏ ghim một nơi vào hàng nhanh (tối đa 2 — QD-04/OQ1 `/places`). Toggle: đã ghim ⇒ bỏ; chưa ⇒ thêm. */
internal fun TelegramBridgeService.pinPlace(app: Context, idStr: String): String {
    val index = idStr.toIntOrNull() ?: return ""
    val place = WorkspacePrefs(app).savedPlaces().getOrNull(index) ?: return "place not found"
    val cur = Prefs.telegramPinnedPlaces(app).toMutableList()
    val msg = if (place.name in cur) { cur.remove(place.name); "unpinned ${place.name}" }
              else if (cur.size >= 2) { "📌 already 2 pinned — unpin one first" }
              else { cur.add(place.name); "📌 pinned ${place.name}" }
    Prefs.setTelegramPinnedPlaces(app, cur)
    return msg
}

/** Địa điểm đã ghim cho hàng nhanh (tối đa 2). Dựa khoá prefs; trống ⇒ 2 nơi đầu sổ. */
internal fun TelegramBridgeService.pinnedPlaces(app: Context): List<com.byd.clusternav.launcher.SavedPlace> {
    val all: List<com.byd.clusternav.launcher.SavedPlace> = WorkspacePrefs(app).savedPlaces()
    val pins: List<String> = Prefs.telegramPinnedPlaces(app)   // list tên đã ghim
    val byPin: List<com.byd.clusternav.launcher.SavedPlace> =
        pins.mapNotNull { name -> all.firstOrNull { it.name == name } }
    val chosen: List<com.byd.clusternav.launcher.SavedPlace> = byPin.ifEmpty { all.take(2) }
    return chosen.take(2)
}

/**
 * Dựng (text trạng thái sống + markup) cho một bảng theo callback. markup null ⇒ không vẽ lại. [toast] = kết quả
 * lệnh vừa thi hành, chèn vào cuối dòng trạng thái (P2-2: kết quả thật về người dùng).
 */
internal fun TelegramBridgeService.renderBoard(app: Context, cb: TelegramCallback, toast: String, n: Int = boardNonce(app)): Pair<String, String?> {
    val places = WorkspacePrefs(app).savedPlaces()
    val status = statusText(app) + (if (toast.isNotBlank()) "\n↳ $toast" else "")
    return when {
        cb.domain == "menu" -> status to TelegramMenu.main(n, pinnedPlaces(app))
        cb.domain == "nav" && cb.action == "open" -> status to TelegramMenu.nav(n, places, 0)
        cb.domain == "page" && cb.action == "nav" -> status to TelegramMenu.nav(n, places, cb.id.toIntOrNull() ?: 0)
        // P1-5: nút từ GHIM mang index danh sách ghim ⇒ đổi sang index TUYỆT ĐỐI trong sổ trước khi dựng navConfirm.
        cb.domain == "nav" && cb.action == "pinned" -> status to TelegramMenu.navConfirm(n, absoluteIndexOfPinned(app, cb.id.toIntOrNull() ?: 0))
        cb.domain == "nav" && cb.action == "go" -> status to TelegramMenu.navConfirm(n, cb.id.toIntOrNull() ?: 0)
        cb.domain == "ac" -> status to acBoard(app, n)
        cb.domain == "seat" -> status to seatBoard(app, n)
        cb.domain == "music" -> status to TelegramMenu.music(n, MediaBridge(app).read()?.playing)
        cb.domain == "misc" -> status to TelegramMenu.misc(n, readBool(app, "readl"), readBool(app, "pm25"))
        cb.domain == "nav" && cb.action == "pin" -> status to TelegramMenu.placesManage(n, places, Prefs.telegramPinnedPlaces(app).toSet())
        else -> status to null   // nav:cancel/also ⇒ không đổi bảng
    }
}

/** P1-5: index trong danh sách GHIM → index tuyệt đối trong sổ địa chỉ (để nút ➕ mở thêm dẫn đúng nơi). */
internal fun TelegramBridgeService.absoluteIndexOfPinned(app: Context, pinnedIndex: Int): Int {
    val pinnedName = pinnedPlaces(app).getOrNull(pinnedIndex)?.name ?: return pinnedIndex
    return WorkspacePrefs(app).savedPlaces().indexOfFirst { it.name == pinnedName }.takeIf { it >= 0 } ?: pinnedIndex
}

internal fun TelegramBridgeService.acBoard(app: Context, n: Int): String = TelegramMenu.ac(n,
    autoOn = readBool(app, "ac_auto"),
    tempC = readCtl(app, "temp"),
    fan = readCtl(app, "fan"),
    defrostOn = readBool(app, "defrost"))

internal fun TelegramBridgeService.seatBoard(app: Context, n: Int): String = TelegramMenu.seat(n,
    driver = seatStateStr(app, "seatc", "seath"),
    passenger = seatStateStr(app, "seatc_r", "seath_r"))

/** Trạng thái một ghế: null = chưa đọc được (cả hai datum null); "off" = đọc được và tắt; cool/heat N = đang bật. */
internal fun TelegramBridgeService.seatStateStr(app: Context, coolId: String, heatId: String): String? {
    val cool = readCtl(app, coolId)
    val heat = readCtl(app, heatId)
    if (cool == null && heat == null) return null       // P3: chưa đọc được ≠ tắt
    return when { (cool ?: 0) > 0 -> "cool$cool"; (heat ?: 0) > 0 -> "heat$heat"; else -> "off" }
}

internal fun TelegramBridgeService.readBool(app: Context, id: String): Boolean? = readCtl(app, id)?.let { it != 0 }

/** Xe có nối HAL không (P1-7, không bịa): đọc được bất kỳ control ⇒ nối; không ⇒ false. */
internal fun TelegramBridgeService.carLinked(app: Context): Boolean = readCtl(app, "temp") != null || readCtl(app, "ac_auto") != null

/** Text bảng trạng thái sống (QD-03) — đọc tươi, không đọc được ⇒ "—" (OQ2), không bịa (P1-7/P2-1). */
internal fun TelegramBridgeService.statusText(app: Context): String {
    val media = runCatching { MediaBridge(app).read() }.getOrNull()
    val pending = runCatching { PendingNavStore.read(app)?.dest?.place?.name }.getOrNull()
    val hhmm = java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(java.util.Date())
    return TelegramStatusLine.render(TelegramStatusLine.Snapshot(
        carLinked = carLinked(app),                                 // P1-7: thật, không hardcode true
        gpsOk = runCatching { com.byd.clusternav.automation.GpsAvailability.isAvailable(app) }.getOrNull(),  // P2-1: Boolean? (null→"—")
        acAuto = readBool(app, "ac_auto"),
        tempC = readCtl(app, "temp"),
        fan = readCtl(app, "fan"),
        seatDriver = seatStateStr(app, "seatc", "seath"),
        seatPassenger = seatStateStr(app, "seatc_r", "seath_r"),
        pendingPlace = pending,
        nowPlaying = media?.let { m -> m.title?.let { t -> t + (m.artist?.let { " — $it" } ?: "") } },
        updatedHhmm = hhmm,
    ))
}
