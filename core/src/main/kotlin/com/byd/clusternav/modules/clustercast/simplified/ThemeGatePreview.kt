package com.byd.clusternav.modules.clustercast.simplified

/**
 * ═══ CLUSTER-THEME-SAFE B1a (2.89) — "Cổng theme (chỉ đọc)" cho `ClusterDiag` / màn Chẩn đoán (CLAUDE.md §11) ═════════════
 *
 * Chạy ĐÚNG các lượt đọc + quyết định của lượt mở chiếu thật ([ClusterThemeGuard.inspect] → [ClusterStylePlan]) rồi in ra —
 * KHÔNG gửi gì. "Không gửi" được ép ở tầng THI HÀNH (CLAUDE.md §5), không chỉ bằng lời hứa của mã gọi:
 *  • kênh shell bọc [ReadOnlyShell] — chỉ cho qua [READS]; mọi lệnh khác bị từ chối và ghi lại (báo ⛔ trong bản in);
 *  • sổ theme bọc [ReadOnlyLedger] — `write` luôn từ chối;
 *  • [ClusterThemeGuard.inspect] không gỡ placeholder, không đổi dấu RAM.
 * Thứ người lái chụp màn hình gửi về: kiểu gốc / opcode / sổ / khoảng 15 s / lời cổng / kế hoạch — đủ để đối chiếu bước đo
 * V1 (quyết định khớp `dumpsys display` + `getprop`, 0 lệnh ghi).
 */
object ThemeGatePreview {

    /** Lệnh ĐỌC duy nhất được qua — đúng các lệnh của cổng + mã đời xe. */
    val READS: Set<String> = setOf(
        ClusterDisplayResolver.DETECT_CMD,
        ClusterThemeGuard.STACK_CMD,
        ClusterThemeGuard.WINDOWS_CMD,
        ClusterCarType.CMD,
    )

    /** Kênh shell chỉ đọc: lệnh ngoài [READS] ⇒ exit 1, KHÔNG tới thiết bị, ghi vào [refused]. */
    class ReadOnlyShell(private val inner: SimpleCastShell) : SimpleCastShell {
        val refused: MutableList<String> = java.util.Collections.synchronizedList(ArrayList())

        override fun execute(command: String): ShellResult =
            if (command in READS) {
                inner.execute(command)
            } else {
                refused += command
                ShellResult(1, "", "chỉ đọc — từ chối lệnh ngoài danh sách đọc")
            }
    }

    /** Sổ theme chỉ đọc. */
    class ReadOnlyLedger(private val inner: ThemeLedger.Store) : ThemeLedger.Store {
        override fun read(): String? = inner.read()
        override fun write(value: String): Boolean = false
    }

    /**
     * @param header các dòng đầu do `:app` cấp (vd mã đời xe đọc trong tiến trình / qua dadb).
     * @return bản in nhiều dòng; dòng cuối luôn nói số lệnh ghi bị chặn (phải là 0).
     */
    fun report(
        shell: SimpleCastShell,
        selfPackage: String,
        recipe: ProjectionRecipe,
        store: ThemeLedger.Store,
        clock: ThemeLedger.Clock,
        desired: CastStyle = CastStyle.CURVED,
        header: List<String> = emptyList(),
    ): String {
        val ro = ReadOnlyShell(shell)
        val guard = ClusterThemeGuard(
            ro, selfPackage, sleepMs = {}, store = ReadOnlyLedger(store), clock = clock,
            vacantVdAllowed = { recipe.themeOnVacantVd },
        )
        val now = clock.now()
        val entry = guard.ledger()
        val want = ClusterStylePlan.wanted(recipe, desired, entry)
        val insp = want?.let { guard.inspect(it) }
        val plan = ClusterStylePlan.decide(recipe, desired, entry, want, insp?.verdict, now)
        return buildString {
            appendLine("── CỔNG THEME (chỉ đọc · 0 lệnh ghi) ──")
            header.forEach { appendLine(it) }
            appendLine(
                "hồ sơ: kiểu gốc=${recipe.nativeStyle ?: "chưa biết"} · styleOps=${recipe.styleOps} · " +
                    "RECT ${if (recipe.offers(CastStyle.RECT)) "hiện" else "ẩn"} · themeOnVacantVd=${recipe.themeOnVacantVd}",
            )
            val gap = ThemeLedger.remainingGapMs(entry, now)
            appendLine(
                "sổ: ${entry?.let(ThemeLedger::encode) ?: "trống"}" +
                    (entry?.let { " · cùng tiến trình=${ThemeLedger.sameProcess(it, now)}" } ?: "") +
                    " · kiểu theo sổ=${ThemeLedger.believed(entry, recipe, now)} · 15 s: ${gap?.let { "còn $it ms" } ?: "đủ"}",
            )
            appendLine("kiểu muốn=$desired → áp=${ClusterStylePlan.effective(recipe, desired) ?: "-"} → opcode cần=${want ?: "không"}")
            appendLine("cổng: ${insp?.line ?: "không hỏi (không cần opcode theme)"}")
            appendLine(
                "kế hoạch: " + when {
                    plan.abort -> "DỪNG lượt mở (0 lệnh chiếu)"
                    plan.themeOp != null -> "gửi ${plan.themeOp} rồi ${recipe.castSeq.joinToString(" → ")}"
                    else -> recipe.castSeq.joinToString(" → ")
                } + " · kiểu tin sau mở=${plan.believed} · ${plan.why}",
            )
            append(if (ro.refused.isEmpty()) "lệnh ghi bị chặn: 0" else "⛔ lệnh ghi bị chặn: ${ro.refused.size} — ${ro.refused}")
        }
    }
}
