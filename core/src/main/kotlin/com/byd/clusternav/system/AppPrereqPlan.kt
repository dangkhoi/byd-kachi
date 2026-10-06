package com.byd.clusternav.system

/**
 * ═══ 2.89 · B2 VM-PREREQ-TRUTH — PHẠM VI + lượt "đọc → áp phần thiếu → đọc lại" (thuần, `:core`) ═══════════════════
 *
 * Hai điều kiện nền mà một app Kachi TỰ mở ở nền cần có:
 *  • [Prereq.DOZE_EXEMPT] — miễn tối ưu pin. Thiếu ⇒ VietMap tự xin `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` lúc
 *    `MainActivity.onCreate` ⇒ ROM 2602030 trả hộp thoại "Hệ thống IVI không hỗ trợ hoạt động này" [ĐO nguồn — KDoc
 *    [DozeWhitelistRead]]; cũng là điều app cần để không bị Doze/App Standby ghìm khi chạy nền.
 *  • [Prereq.OVERLAY] — appop `SYSTEM_ALERT_WINDOW` = `allow`: bóng VietMap là cửa sổ `TYPE_APPLICATION_OVERLAY`
 *    (cổng AOSP thường, `PhoneWindowManager.checkAddPermission` — spec `kachi-289-field-fixes.html` §B2) [ĐO nguồn ROM].
 *
 * ## Phạm vi = bảng dữ liệu, không `if (gói == …)` (CLAUDE.md §4 · §7)
 * Chỉ các gói Kachi TỰ khởi động ở nền, mỗi lý do ([Role]) kéo theo một bộ điều kiện; `:app` chỉ gom sự thật
 * (công tắc, gói tự chiếu của hồ sơ, gói đã cài) vào [Facts]. Bốn câu hỏi §4: không đụng display/stack; app = đúng các
 * gói của [targets] (allow-list, đã lọc tên gói hợp lệ); hoàn tác = [revertStale] (`cmd deviceidle whitelist -<gói>` /
 * `appops set <gói> SYSTEM_ALERT_WINDOW default`, chỉ cho gói có dấu [Marks]) — và ROM tự gỡ cả hai khi gỡ app.
 *
 * ## Không cờ (CLAUDE.md §5)
 * Trạng thái này sống ngoài tiến trình (`/data/system/deviceidle.xml`, `appops.xml`) và bị ROM xoá khi gỡ app ⇒ mỗi
 * lượt ĐỌC trước, chỉ ghi phần [Truth.NO], rồi đọc lại để kết quả nói SỰ THẬT sau khi ghi. [Truth.UNKNOWN] ⇒ không ghi.
 *
 * ## Đường trả lại (review 2.89 Pass 2 · vietmap-dock-r1-2, CLAUDE.md §5)
 * Mục tiêu có thể là gói BẤT KỲ của hồ sơ (app tự chiếu — khoá hồ sơ xuất/nhập được), và hai thứ trên sống qua khởi động lại
 * ⇒ phải có đường trả chạy được cả khi tiến trình đã chết. [Marks] = dấu bền "chính Kachi đã thêm": ghi TRƯỚC lệnh ghi (ghi dấu
 * hỏng ⇒ không ghi điều kiện), chỉ khi bản đọc "trước" là [Truth.NO]. Lượt SẴN (`AppPrereqs.onReady`) gọi [revertStale]: gói có
 * dấu mà đã RỜI phạm vi ([keep]) ⇒ lệnh trả ([revertCommand]) → đọc lại → bỏ dấu CHỈ khi bản đọc lại xác nhận đã trả. Gói được
 * miễn/cho phép TỪ TRƯỚC khi Kachi chạm vào không bao giờ có dấu ⇒ không bao giờ bị trả.
 */
enum class Prereq { DOZE_EXEMPT, OVERLAY }

object AppPrereqPlan {

    /** Vì sao Kachi tự mở app này ở nền ⇒ app cần gì. */
    enum class Role(val needs: Set<Prereq>) {
        /** Bóng VietMap trên cụm: app chạy nền + vẽ nổi. */
        BUBBLE(setOf(Prereq.DOZE_EXEMPT, Prereq.OVERLAY)),
        /** Biển tốc độ: VietMap là nguồn (widget/thông báo) ⇒ tiến trình phải sống ở nền. */
        SPEED_BADGE(setOf(Prereq.DOZE_EXEMPT)),
        /** Khoá V1 `clustercast/autoCast` trỏ VietMap (nhánh ACTIVE của `VietMapAutostart`). */
        CAST_DEFAULT(setOf(Prereq.DOZE_EXEMPT)),
        /** App tự chiếu lên cụm của hồ sơ (`autostart_package` / trái / phải khi bật Cast). */
        AUTO_CAST(setOf(Prereq.DOZE_EXEMPT)),
        /**
         * Một lượt `VietMapAutostart` (nổ máy / bật công tắc) với VietMap ĐÃ CÀI — chạy TRƯỚC cổng sớm của lượt đó, để cả
         * người đã tắt bóng/biển mà tự mở VietMap cũng hết hộp thoại (spec `kachi-289-field-fixes.html` §B2).
         */
        AUTOSTART_PASS(setOf(Prereq.DOZE_EXEMPT)),
        /** Lượt mở chiếu cụm: giữ đúng PHẠM VI đường cũ (`+vn.vietmap.live` mỗi lần mở chiếu đầu tiên), đổi cờ → sự thật. */
        CAST_OPEN(setOf(Prereq.DOZE_EXEMPT)),
    }

    /** Sự thật `:app` gom được (prefs + PackageManager) — không shell. */
    data class Facts(
        val vietMapPkg: String,
        val vietMapInstalled: Boolean,
        val bubbleOn: Boolean,
        val badgeOn: Boolean,
        val vietMapCastDefault: Boolean,
        /** Gói tự chiếu của hồ sơ ĐANG có hiệu lực (đã lọc theo Cast bật + công tắc tự chiếu). */
        val autoCastPkgs: List<String>,
        /** Gói đã cài chưa — gói chưa cài không vào phạm vi (lệnh `+gói` chỉ ra `Unknown package`). */
        val installed: (String) -> Boolean,
    )

    /** Một gói + đủ điều kiện nó cần (hợp các lý do) + các lý do (để log/Chẩn đoán nói VÌ SAO). */
    data class Target(val pkg: String, val needs: Set<Prereq>, val roles: Set<Role>)

    /** Phạm vi CHUẨN (móc kênh sẵn sàng): VietMap theo bóng/biển/cast-default + app tự chiếu của hồ sơ. */
    fun baseRoles(f: Facts): List<Pair<String, Role>> = buildList {
        if (f.vietMapInstalled) {
            if (f.bubbleOn) add(f.vietMapPkg to Role.BUBBLE)
            if (f.badgeOn) add(f.vietMapPkg to Role.SPEED_BADGE)
            if (f.vietMapCastDefault) add(f.vietMapPkg to Role.CAST_DEFAULT)
        }
        f.autoCastPkgs.forEach { add(it to Role.AUTO_CAST) }
    }

    /**
     * Gộp theo gói (thứ tự xuất hiện đầu), bỏ tên gói không hợp lệ (đi thẳng vào lệnh shell) và gói chưa cài.
     * [extra] = lý do của riêng lượt gọi ([Role.AUTOSTART_PASS] · [Role.CAST_OPEN]).
     */
    fun targets(f: Facts, extra: List<Pair<String, Role>> = emptyList()): List<Target> {
        val roles = LinkedHashMap<String, MutableSet<Role>>()
        (baseRoles(f) + extra).forEach { (pkg, role) ->
            if (isShellSafePackage(pkg) && f.installed(pkg)) roles.getOrPut(pkg) { LinkedHashSet() }.add(role)
        }
        return roles.map { (pkg, rs) -> Target(pkg, rs.flatMapTo(LinkedHashSet()) { it.needs }, rs) }
    }

    // ─── Lượt đọc / quyết / áp ───────────────────────────────────────────────────────────────────────

    /** Sự thật của MỘT gói. [overlayMode] `null` = không đọc (không cần) hoặc đọc hỏng — xem [overlay]. */
    data class State(
        val doze: DozeWhitelistRead.Entry,
        val overlayMode: String?,
        val overlayRead: Boolean,
    ) {
        val dozeExempt: Truth get() = doze.exempt
        val overlay: Truth get() = if (overlayRead) OverlayOpRead.allowed(overlayMode) else Truth.UNKNOWN

        fun met(p: Prereq): Truth = when (p) {
            Prereq.DOZE_EXEMPT -> dozeExempt
            Prereq.OVERLAY -> overlay
        }
    }

    /** Kết quả một lượt: trước · đã ghi gì · sau. [notInstalled] = ROM trả `Unknown package` cho lệnh thêm. */
    data class Result(
        val target: Target,
        val before: State,
        val applied: List<Prereq>,
        val after: State,
        val notInstalled: Boolean,
    ) {
        /** Mọi điều kiện cần đều [Truth.YES] SAU lượt này (đọc lại, không tin lệnh đã gửi). */
        val ok: Boolean get() = target.needs.all { after.met(it) == Truth.YES }

        /** Điều kiện còn thiếu/không rõ sau lượt — để log/Chẩn đoán nói đúng chỗ hụt. */
        val unmet: List<Prereq> get() = target.needs.filter { after.met(it) != Truth.YES }

        fun describe(): String = buildString {
            append(target.pkg).append(" cần=").append(target.needs.joinToString(",") { it.name })
            append(" vì=").append(target.roles.joinToString(",") { it.name })
            append(" · trước[").append(stateText(before, target.needs)).append(']')
            append(" · ghi=").append(if (applied.isEmpty()) "-" else applied.joinToString(",") { it.name })
            append(" · sau[").append(stateText(after, target.needs)).append(']')
            if (notInstalled) append(" · ROM: Unknown package")
            append(if (ok) " → ĐỦ" else " → THIẾU ${unmet.joinToString(",") { it.name }}")
        }
    }

    fun stateText(s: State, needs: Set<Prereq> = Prereq.entries.toSet()): String = buildList {
        if (Prereq.DOZE_EXEMPT in needs) add("miễn pin=${truthText(s.dozeExempt)}(${s.doze.name.lowercase()})")
        if (Prereq.OVERLAY in needs) add("${OverlayOpRead.OP}=${if (s.overlayRead) s.overlayMode ?: "?" else "-"}")
    }.joinToString(" ")

    fun truthText(t: Truth): String = when (t) {
        Truth.YES -> "CÓ"
        Truth.NO -> "KHÔNG"
        Truth.UNKNOWN -> "?"
    }

    /** QUYẾT (thuần): chỉ điều kiện [Truth.NO]. [Truth.UNKNOWN] ⇒ không ghi (không chứng minh được thiếu). */
    fun missing(needs: Set<Prereq>, state: State): List<Prereq> =
        Prereq.entries.filter { it in needs && state.met(it) == Truth.NO }

    /** Lệnh ghi cho một điều kiện — đúng một lệnh, đúng một gói. */
    fun applyCommand(p: Prereq, pkg: String): String = when (p) {
        Prereq.DOZE_EXEMPT -> DozeWhitelistRead.addCommand(pkg)
        Prereq.OVERLAY -> OverlayOpRead.allowCommand(pkg)
    }

    /**
     * Đọc sự thật cho [needs] (không đọc appop nếu không cần). [sh] trả stdout+stderr, `null` = phiên/lệnh hỏng.
     * [dozeListing] cho phép dùng lại MỘT lần đọc danh sách cho nhiều gói trong cùng lượt.
     */
    fun read(pkg: String, needs: Set<Prereq>, sh: (String) -> String?, dozeListing: String? = null): State {
        val listing = if (Prereq.DOZE_EXEMPT in needs) dozeListing ?: sh(DozeWhitelistRead.READ) else null
        val doze = if (Prereq.DOZE_EXEMPT in needs) DozeWhitelistRead.entry(listing, pkg) else DozeWhitelistRead.Entry.UNKNOWN
        val overlayRead = Prereq.OVERLAY in needs
        val mode = if (overlayRead) OverlayOpRead.effectiveMode(sh(OverlayOpRead.readCommand(pkg))) else null
        return State(doze, mode, overlayRead)
    }

    /**
     * MỘT lượt cho MỘT gói: đọc → ghi đúng phần [Truth.NO] → đọc lại (chỉ khi đã ghi). Không vòng lặp, không thử lại:
     * lượt kế (kênh sẵn sàng lần sau / lượt autostart sau / lần mở chiếu sau) tự đọc lại sự thật.
     *
     * [marks] (Pass 2 · vietmap-dock-r1-2): dấu "Kachi đã thêm" ghi TRƯỚC từng lệnh ghi; ghi dấu hỏng ⇒ KHÔNG ghi điều kiện đó
     * (CLAUDE.md §5 — không có đường trả thì không đổi). `null` chỉ ở test cũ của luật đọc/ghi.
     */
    fun ensure(target: Target, sh: (String) -> String?, dozeListing: String? = null, marks: Marks? = null): Result {
        val before = read(target.pkg, target.needs, sh, dozeListing)
        val todo = missing(target.needs, before).filter { p -> marks?.mark(p, target.pkg) ?: true }
        if (todo.isEmpty()) return Result(target, before, emptyList(), before, notInstalled = false)
        var notInstalled = false
        todo.forEach { p ->
            val out = sh(applyCommand(p, target.pkg))
            if (p == Prereq.DOZE_EXEMPT && DozeWhitelistRead.unknownPackage(out, target.pkg)) notInstalled = true
        }
        val after = read(target.pkg, target.needs, sh)
        return Result(target, before, todo, after, notInstalled)
    }

    /** Nhiều gói, MỘT lần đọc danh sách miễn pin cho lượt "trước" (đọc lại sau khi ghi vẫn riêng từng gói). */
    fun ensureAll(targets: List<Target>, sh: (String) -> String?, marks: Marks? = null): List<Result> {
        if (targets.isEmpty()) return emptyList()
        val listing = if (targets.any { Prereq.DOZE_EXEMPT in it.needs }) sh(DozeWhitelistRead.READ) else null
        return targets.map { ensure(it, sh, listing, marks) }
    }

    // ─── Đường trả lại (Pass 2 · vietmap-dock-r1-2) ──────────────────────────────────────────────────────────────

    /** Dấu bền "chính Kachi đã thêm [Prereq] cho gói" (`:app`: SharedPreferences `commit()`, phạm vi XE). */
    interface Marks {
        fun added(p: Prereq): Set<String>

        /** Thêm dấu; `false` = không ghi được ⇒ bên gọi KHÔNG được ghi điều kiện. Đã có ⇒ `true`. */
        fun mark(p: Prereq, pkg: String): Boolean

        /** Bỏ dấu; `false` = không ghi được (lượt sau thử lại — lệnh trả là idempotent). Không có ⇒ `true`. */
        fun unmark(p: Prereq, pkg: String): Boolean
    }

    /** Khoá prefs của dấu — một tập gói mỗi điều kiện. Đổi tên = mất dấu cũ (không bao giờ đổi). */
    fun markKey(p: Prereq): String = when (p) {
        Prereq.DOZE_EXEMPT -> "prereq_added_doze"
        Prereq.OVERLAY -> "prereq_added_overlay"
    }

    /** Lệnh trả lại cho một điều kiện — đúng một lệnh, đúng một gói. */
    fun revertCommand(p: Prereq, pkg: String): String = when (p) {
        Prereq.DOZE_EXEMPT -> DozeWhitelistRead.removeCommand(pkg)
        Prereq.OVERLAY -> OverlayOpRead.defaultCommand(pkg)
    }

    /**
     * Phạm vi GIỮ: mọi gói × điều kiện mà BẤT KỲ lối vào nào còn đòi — chuẩn ([baseRoles]) + [Role.AUTOSTART_PASS] /
     * [Role.CAST_OPEN] của VietMap (hai lối vào đó luôn kéo VietMap đã cài vào miễn pin) ⇒ không bao giờ gỡ thứ lượt sau sẽ
     * thêm lại (không lật qua lật lại mỗi lần nổ máy).
     */
    fun keep(f: Facts): Map<String, Set<Prereq>> = everyEntryTargets(f).associate { it.pkg to it.needs }

    /** Mọi gói × lý do mà BẤT KỲ lối vào nào đòi: chuẩn + hai lối luôn kéo VietMap đã cài (lượt autostart, lượt mở chiếu). */
    private fun everyEntryTargets(f: Facts): List<Target> =
        targets(f, listOf(f.vietMapPkg to Role.AUTOSTART_PASS, f.vietMapPkg to Role.CAST_OPEN))

    /**
     * 2.93 · VM-PREREQ-PKG-ADDED — gói [pkg] vừa CÀI trong lúc Kachi sống (`ACTION_PACKAGE_ADDED`) ⇒ phạm vi của RIÊNG gói đó, đúng
     * thứ mà lượt kế tiếp (SẴN · autostart · mở chiếu) sẽ chữa — chỉ làm SỚM hơn, không thêm phạm vi mới. Cùng tập với [keep] ⇒
     * lượt ready sau KHÔNG bao giờ trả lại thứ lượt này vừa thêm (không lật qua lật lại).
     *
     * Vì sao cần [ĐO nguồn r47]: gỡ hẳn một gói (không phải cập nhật) ⇒ `DeviceIdleController` xoá nó khỏi danh sách miễn pin
     * (`DeviceIdleController.java:585-592`, `ACTION_PACKAGE_REMOVED` không `EXTRA_REPLACING`); cài lại ⇒ `ACTION_PACKAGE_ADDED`
     * (`PackageManagerService.java:1919-1935` · `:13192-13206`). Gói ngoài phạm vi / chưa cài / tên lạ ⇒ rỗng (không đọc, không ghi).
     */
    fun forAddedPackage(f: Facts, pkg: String): List<Target> = everyEntryTargets(f).filter { it.pkg == pkg }

    /** Dấu đã RỜI phạm vi (thuần — lượt sẵn sàng biết có việc trả mà không mở phiên shell vô ích). */
    fun stale(f: Facts, marks: Marks): List<Pair<Prereq, String>> {
        val keep = keep(f)
        return Prereq.entries.flatMap { p -> marks.added(p).sorted().filter { p !in keep[it].orEmpty() }.map { p to it } }
    }

    /** Một lượt trả cho MỘT (gói, điều kiện): [after] = sự thật đọc lại; [cleared] = đã bỏ dấu. */
    data class Revert(val pkg: String, val prereq: Prereq, val after: Truth, val cleared: Boolean, val notInstalled: Boolean) {
        fun describe(): String = "$pkg ${prereq.name} trả lại → sau=${truthText(after)}" +
            (if (notInstalled) " (đã gỡ app — ROM tự xoá)" else "") + if (cleared) " · bỏ dấu" else " · GIỮ dấu, lượt sau thử lại"
    }

    /**
     * Trả lại mọi dấu [stale]: gói đã gỡ ⇒ bỏ dấu, 0 lệnh (ROM tự xoá cả hai khi gỡ app — doze [ĐO nguồn r47
     * `DeviceIdleController.java:586-590`], appops [SUY]); còn lại ⇒ [revertCommand] → đọc lại → bỏ dấu CHỈ khi đọc lại là
     * [Truth.NO]. Đọc lại [Truth.UNKNOWN] ⇒ giữ dấu (lệnh trả idempotent, lượt sau làm lại). Tên gói lạ (dấu bị sửa tay) ⇒ bỏ
     * dấu, KHÔNG bao giờ đưa vào shell.
     */
    fun revertStale(f: Facts, marks: Marks, sh: (String) -> String?): List<Revert> = stale(f, marks).map { (p, pkg) ->
        when {
            !isShellSafePackage(pkg) -> Revert(pkg, p, Truth.UNKNOWN, marks.unmark(p, pkg), notInstalled = false)
            !f.installed(pkg) -> Revert(pkg, p, Truth.NO, marks.unmark(p, pkg), notInstalled = true)
            else -> {
                sh(revertCommand(p, pkg))
                val after = read(pkg, setOf(p), sh).met(p)
                Revert(pkg, p, after, after == Truth.NO && marks.unmark(p, pkg), notInstalled = false)
            }
        }
    }
}
