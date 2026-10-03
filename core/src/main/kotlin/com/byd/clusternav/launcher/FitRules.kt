package com.byd.clusternav.launcher

import kotlin.math.roundToInt

/**
 * ═══ L5 WIDGET-FIT-ALL — các QUYẾT ĐỊNH thuần của tầng vẽ (`FitScale` · `FitProbe` · `FitGridLayout`) ═══════════════
 *
 * Soát vòng 1 (2.87): tầng vẽ chỉ có bài canh CHUỖI NGUỒN, nên lỗi lật ngang — con `MATCH_PARENT` của khối dọc giữ
 * nguyên bề rộng khi khối lật ngang ⇒ con đầu nuốt hết hàng, mọi con sau rộng 0 (`LinearLayout.java:1929-1936` +
 * `:1339-1375` r47) — lọt qua mọi bài. Phép quyết nào của tầng vẽ không cần `View` thật được kéo về đây để test thuần
 * (dự án không dùng Robolectric):
 *  - [lp]: `LayoutParams` đích của một view khi áp `k` + dạng;
 *  - [freeTextCut]: chữ MỘT dòng tự `…` (giá trị · tên bài) khi nào mới tính là bị cắt;
 *  - [spills]: con tràn khỏi khung cha (con cỡ cố định — nút nhạc, thanh tiến trình, vạch mức — hoặc hàng hết chỗ);
 *  - [usable]: dạng nào được đưa vào phép khớp;
 *  - [reprobe] + [settle]: khi nào đo dò lại một ô vì chữ đã đổi, và nhận số đo mới ra sao (không giật).
 *
 * Soát vòng 2 (2.87) thêm: [Cell] + [known] (trình tự đo dò lại của một ô — trạng thái cắt CHƯA BIẾT không được chốt,
 * lượt thưa không nuốt vết cắt thật), [splitsNumber] (số bị bẻ đôi qua hai dòng), [sigStep] (dấu chữ không phụ thuộc dạng),
 * [weight] (bộ áp chỉ ghi weight nó sở hữu), [freeText] + [cut] (ngân sách `…` chỉ cho chữ tự do được khai).
 * QA + soát vòng 3: [iconScale] (icon không bao giờ to hơn ô, giữ tỉ lệ), [Cell.fitted] chỉ chốt "kẹt" trên chữ đã đo dò.
 *
 * Số dp KHÔNG sống ở đây (`SpacingScaleContractTest`): mọi đầu vào là px do tầng vẽ đo/đổi.
 */
object FitRules {

    /** = `ViewGroup.LayoutParams.MATCH_PARENT` — `FitRulesWiringContractTest` (:app) ghim bằng nhau. */
    const val MATCH = -1

    /** = `ViewGroup.LayoutParams.WRAP_CONTENT`. */
    const val WRAP = -2

    /** `LayoutParams` rút gọn: bề rộng/cao (px > 0, [MATCH], [WRAP], hoặc 0 khi chia `weight`) + `weight`. */
    data class Lp(val width: Int, val height: Int, val weight: Float = 0f)

    /**
     * `LayoutParams` đích của một view có [base] (giá trị của bộ dựng, thang 1) khi áp hệ số [k]. [rotated] = view là
     * con TRỰC TIẾP của khối chính và lưới đang ở dạng NGANG (khối dọc đã lật thành hàng).
     *  - cỡ cố định (px > 0) × k, tối thiểu 1px; [MATCH]/[WRAP]/0 giữ nguyên;
     *  - con dùng `weight` đổi trục khi lật (đang chia phần còn lại theo chiều dọc ⇒ chia theo chiều ngang);
     *  - con [MATCH] bề rộng (mặc định của `LinearLayout` dọc khi `addView` không kèm LP) ⇒ `0 + weight 1`: các con co
     *    giãn CHIA hàng ngang. Giữ [MATCH] là con đầu lấy hết, con sau rộng 0 (lỗi soát vòng 1). Không dùng [WRAP]:
     *    `TextView` bề rộng [WRAP] mà đổi chữ mỗi nhịp thì `checkForRelayout` rơi nhánh "bề rộng động" ⇒
     *    `requestLayout` MỖI NHỊP (`TextView.java:9641-9692` r47) — đúng cú đo lại cả màn mỗi giây owner báo 09-25;
     *    bề rộng 0 + weight là bề rộng TĨNH ⇒ đổi chữ chỉ dựng lại chữ, không đo lại;
     *  - không lật ⇒ trả đúng giá trị gốc × k (đổi dạng về DỌC là khôi phục trọn vẹn).
     */
    fun lp(base: Lp, k: Double, rotated: Boolean): Lp {
        fun sc(v: Int): Int = if (v > 0) (v * k).roundToInt().coerceAtLeast(1) else v
        val w = sc(base.width)
        val h = sc(base.height)
        return when {
            !rotated -> Lp(w, h, base.weight)
            base.weight > 0f -> Lp(h, w, base.weight)
            base.width == MATCH -> Lp(0, h, 1f)
            else -> Lp(w, h, base.weight)
        }
    }

    /**
     * Hệ số áp cho một ICON cỡ cố định [w]×[h] (px gốc) khi lưới áp [k]: không lớn hơn chỗ [roomW]×[roomH] mà ô dành
     * cho nó (ô trừ lề trong của các khung bọc + lề ngoài của icon, đã nhân `k`), CÙNG một hệ số cho hai trục ⇒ icon
     * giữ tỉ lệ, nằm giữa ô (cha căn giữa). QA 04/10 ([ĐO] `l5/icononly-zoom.png`, bản 149dcab): icon 60px trong ô 36px
     * bị khung lề cắt còn một dải 12px giữa (`LinearLayout.java:1653-1655` canh giữa ra lề âm + `clipToPadding` mặc định
     * `ViewGroup.java:686-687` r47) — trông như hình bị bóp. Lưới đọc được thì hộp đo dò đã vừa ô ⇒ chặn này không đổi
     * gì; nó chỉ chặn ca lưới KHÔNG đọc được (giữ sàn, tràn ô). Tối thiểu 1px.
     */
    fun iconScale(k: Double, w: Int, h: Int, roomW: Int, roomH: Int): Double {
        if (w <= 0 || h <= 0) return k
        val fit = minOf(roomW.toDouble() / w, roomH.toDouble() / h).coerceAtLeast(1.0 / maxOf(w, h))
        return minOf(k, fit)
    }

    /**
     * `weight` bộ áp phải GHI cho một con (`null` = không đụng). [base] = weight của bộ dựng lúc chụp, [target] = weight
     * [lp] trả, [current] = weight đang có, [held] = weight bộ áp đã ghi đè lần trước (`null` = không giữ).
     *
     * Bộ áp chỉ SỞ HỮU weight khi phép lật ĐỔI nó (con [MATCH] ⇒ `0 + weight 1`) — và trả lại [base] khi về dạng không
     * lật, nếu weight đang có vẫn đúng là số nó đã ghi. Ngoài lúc đó weight là của BỘ DỰNG: thanh tiến trình nhạc đổi
     * weight `done`/`rest` mỗi nhịp (`MediaWidgetView.fillMedia`); ghi weight = số lúc chụp ở mỗi lượt áp là kéo thanh
     * về vị trí lúc dựng tới nhịp sau (soát vòng 2, P3).
     */
    fun weight(base: Float, target: Float, current: Float, held: Float?): Float? = when {
        target != base -> target.takeIf { it != current }
        held != null && current == held && current != base -> base
        else -> null
    }

    /**
     * Ngân sách của chữ TỰ DO một dòng (`maxLines = 1` + `ellipsize`, bộ dựng KHAI là tự do — [freeText]) tính theo em
     * của chính nó: chữ ngắn hơn ngân sách phải hiện TRỌN; chữ dài hơn (tên bài hát 100 ký tự) được `…` sau
     * [FREE_TEXT_EM] em. Không có trần này thì một chuỗi dài quyết cỡ CẢ lưới (hộp chung = MAX các ô) và kéo mọi ô
     * xuống sàn (soát vòng 1, P2). [ĐỀ XUẤT, owner chốt].
     */
    const val FREE_TEXT_EM = 6.0

    /**
     * Chữ nào được hưởng ngân sách [FREE_TEXT_EM]: CHỈ chữ bộ dựng khai là tự do ([optIn] — tên bài, nghệ sĩ: dài vô hạn
     * theo thiết kế) VÀ là một dòng có `…`. Giá trị (VIN, `418 km`), chú thích (nhãn làm dòng phụ khi không có đơn vị),
     * dấu "chưa kiểm" KHÔNG: R-WF2 — còn bố cục đọc được thì phải hiện TRỌN. Bản soát vòng 1 cho MỌI chữ
     * `maxLines = 1` + `ellipsize` hưởng ngân sách ⇒ `Chế độ vận h…`, `LGXC…` dù co nhỏ hơn vẫn đọc trọn (soát vòng 2, P3).
     */
    fun freeText(optIn: Boolean, maxLines: Int, ellipsized: Boolean): Boolean = optIn && maxLines == 1 && ellipsized

    /** Chữ đang `…` ([ellipsized]) có tính là BỊ CẮT không: chữ thường ⇒ luôn; chữ tự do ([free]) ⇒ [freeTextCut]. */
    fun cut(ellipsized: Boolean, free: Boolean, availPx: Float, textPx: Float): Boolean =
        ellipsized && (!free || freeTextCut(availPx, textPx))

    /**
     * Dòng mới bắt đầu ở [at] có BẺ ĐÔI một con số không (`100` → `10` / `0`). `TextView` không `maxLines` mà hẹp hơn
     * một "từ" thì bộ ngắt dòng bẻ ở MỌI ranh giới chữ (desperate break — `OptimalLineBreaker.cpp:163-176`, `:252-256`
     * minikin android-10). Phép kiểm cắt chữ cũ chỉ thấy chữ bị cắt/tràn: số `WRAP` (hàng `AxisRow` ô tốc độ) dài thêm
     * một chữ số mà ô còn chỗ theo chiều dọc thì hiện `10`/`0` mà không bị coi là cắt ⇒ không bao giờ đo dò lại
     * (soát vòng 2, P1). Chỉ xét SỐ (chữ số; `.`/`,`/`:` kẹp giữa hai chữ số): mọi ngôn ngữ đều không xuống dòng giữa
     * một con số, còn chữ Thái/Hán xuống dòng giữa hai chữ cái là bình thường.
     */
    fun splitsNumber(text: CharSequence, at: Int): Boolean {
        fun numeric(i: Int): Boolean {
            if (i !in text.indices) return false
            val c = text[i]
            if (c.isDigit()) return true
            return c in ".,:" && i - 1 >= 0 && i + 1 < text.length && text[i - 1].isDigit() && text[i + 1].isDigit()
        }
        return at in 1 until text.length && numeric(at - 1) && numeric(at)
    }

    /**
     * Một bước của dấu nội dung chữ của ô ([h] = dấu tới chữ trước): chữ + hiện/ẩn. Hiện/ẩn của NHÃN ([label]) KHÔNG vào
     * dấu: bộ áp sở hữu nó (chỉ-icon ẩn nhãn, dạng khác trả lại). Tính vào thì dấu chụp lúc đo dò — đo dò để ô ở dạng
     * cuối, chỉ-icon, nhãn `GONE` — không bao giờ khớp dấu ở dạng đang hiện ⇒ mọi ô nút/gói lệnh đo dò lại mỗi
     * [RECHECK_MS] mãi mãi, kèm một lượt đo cả màn (soát vòng 2, P2). Không phụ thuộc dạng ⇒ dấu lúc đo dò = dấu ở dạng
     * đang hiện, ở MỌI dạng.
     */
    fun sigStep(h: Int, text: String, visibility: Int, label: Boolean): Int =
        31 * (31 * h + text.hashCode()) + if (label) 0 else visibility

    /**
     * Chữ tự do một dòng đang `…` có tính là BỊ CẮT không: chỉ khi chỗ dành cho nó ([availPx]) còn hẹp hơn ngân sách
     * [FREE_TEXT_EM] × cỡ chữ ([textPx]). Chữ ngắn hơn ngân sách mà bị `…` thì chỗ < bề rộng tự nhiên < ngân sách ⇒
     * vẫn tính là cắt. Đơn điệu theo [availPx] ⇒ phép tìm nhị phân bề rộng nhỏ nhất của `FitProbe` vẫn đúng.
     */
    fun freeTextCut(availPx: Float, textPx: Float): Boolean = availPx + 1f < FREE_TEXT_EM * textPx

    /**
     * Một khung cha có để con TRÀN ra ngoài không, trên MỘT trục: [parentPx] cỡ đo được của cha, [paddingPx] tổng lề
     * trong hai phía, [childPx] cỡ đo được + lề ngoài của từng con đang hiện. [stacked] = các con xếp NỐI TIẾP trên
     * trục này (`LinearLayout` cùng hướng) ⇒ cộng dồn; còn lại (trục chéo, `FrameLayout`, khung tự đặt) ⇒ từng con.
     * Sai số 1px (làm tròn). Con cỡ cố định vẫn được đo đúng cỡ của nó dù cha hẹp hơn (`ViewGroup.getChildMeasureSpec`
     * EXACTLY) và chỉ đơn giản tràn ra — phép kiểm cắt chữ không thấy được, phép này thấy.
     */
    fun spills(parentPx: Int, paddingPx: Int, childPx: List<Int>, stacked: Boolean): Boolean {
        if (childPx.isEmpty()) return false
        val need = if (stacked) childPx.sum() else childPx.max()
        return need + paddingPx > parentPx + 1
    }

    /**
     * Dạng được đưa vào phép khớp: trong [candidates] (chỉ số của `FitProbe.OPTIONS`), chỉ những dạng mà MỌI ô có bề
     * rộng nào đó vẽ trọn ([ok]). Dạng không ô nào vẽ trọn được ở bất kỳ bề rộng nào (vd hàng ngang mà một con không
     * bao giờ có chỗ) KHÔNG được làm ứng viên với hộp giả. Không dạng nào đạt ⇒ giữ nguyên danh sách (đường lùi cũ:
     * phép kiểm báo nhầm thì vẫn phải vẽ một cái gì đó, dạng gốc đứng đầu).
     */
    fun usable(candidates: List<Int>, ok: (Int) -> Boolean): List<Int> = candidates.filter(ok).ifEmpty { candidates }

    /** Chờ tối thiểu giữa hai lượt đo dò một ô khi chữ MỚI bị cắt (một nhịp trạng thái xe). */
    const val GROW_GAP_MS = 1_000L

    /**
     * Chờ tối thiểu giữa hai lượt đo dò một ô cho mọi trường hợp còn lại: chữ đổi mà KHÔNG cắt (hộp có thể đã nhỏ đi
     * ⇒ cho lưới giãn lại), lưới đang không đọc được (khung quá nhỏ — tự phục hồi khi chữ ngắn lại), hoặc lượt đo dò
     * trước không chữa được vết cắt ([reprobe] `stuck`). [ĐỀ XUẤT].
     */
    const val RECHECK_MS = 30_000L

    /**
     * Có đo dò lại ô mà chữ đã đổi (dấu nội dung khác lúc đo) không. [clipped] = ô đang cắt chữ/tràn; [legible] =
     * lưới hiện ở tầng đọc được; [stuck] = lượt đo dò trước của ô vẫn để lại vết cắt; [sinceProbeMs] = từ lượt đo dò
     * trước của ô. Cắt + đọc được + chưa kẹt ⇒ nhịp kế tiếp (chữ dài ra giữa chuyến); còn lại ⇒ thưa ([RECHECK_MS]) —
     * không bao giờ đo dò theo từng nhịp 1 Hz khi việc đo không chữa được gì (R-WF6).
     */
    fun reprobe(clipped: Boolean, legible: Boolean, stuck: Boolean, sinceProbeMs: Long): Boolean =
        sinceProbeMs >= if (clipped && legible && !stuck) GROW_GAP_MS else RECHECK_MS

    /** Hộp phải nhỏ đi hơn tỉ lệ này mới nhận ở lượt đo dò KHÔNG cắt — chống giật (99 ↔ 100 km/h không đổi cỡ lưới). */
    const val SHRINK_HYSTERESIS = 0.15

    /**
     * Nhận số đo mới [new] của một dạng thay cho số cũ [old] ra sao. [grow] = lượt đo dò vì chữ bị cắt ⇒ nhận [new]
     * (nội dung thật cần chỗ đó). Lượt đo dò thưa (không cắt) ⇒ chỉ nhận khi hộp NHỎ ĐI rõ rệt (không lớn hơn ở trục
     * nào và nhỏ hơn [SHRINK_HYSTERESIS] ở ít nhất một trục): chữ đang vừa thì không có lý do bóp cả lưới, và dao động
     * nhỏ không làm cỡ chữ cả lưới đổi theo.
     */
    fun settle(old: GridFit.Shape, new: GridFit.Shape, grow: Boolean): GridFit.Shape {
        if (grow) return new
        val notBigger = new.widthPx <= old.widthPx && new.heightPx <= old.heightPx
        val keep = 1.0 - SHRINK_HYSTERESIS
        val shrank = new.widthPx < old.widthPx * keep || new.heightPx < old.heightPx * keep
        return if (notBigger && shrank) new else old
    }

    /** Trạng thái cắt chữ của một ô lúc xét đo dò lại. */
    enum class Clip {
        NO,
        YES,

        /**
         * CHƯA BIẾT: một chữ đang hiện CHƯA có bố cục. `TextView` bề rộng `WRAP` đổi chữ ⇒ `checkForRelayout` rơi nhánh
         * bề rộng động ⇒ `nullLayouts()` + `requestLayout()` (`TextView.java:9686-9691`, `:8848-8861` r47; `:9817-9822`,
         * `:8978-8992` 12_r34) ⇒ ngay sau `setText`, `getLayout()` là `null` tới lượt đo kế. Đọc "không cắt" lúc ấy là
         * đọc nhầm (soát vòng 2, P1).
         */
        UNKNOWN,
    }

    /**
     * Trạng thái cắt đọc NGAY SAU một lượt đo ([measured]): chữ vẫn không có bố cục nghĩa là khung cha không đo nó (không
     * vẽ) ⇒ không thể cắt ⇒ [Clip.NO]. [Clip.UNKNOWN] chỉ có nghĩa TRƯỚC lượt đo (đổ tại chỗ). Không có bước này thì một
     * chữ mà khung cha không bao giờ đo sẽ chặn MÃI việc xét lại ô — gate đường phục hồi bằng dữ liệu mà chỉ chính đường
     * đó làm mới được (CLAUDE.md §3).
     */
    fun known(clip: Clip, measured: Boolean): Clip = if (measured && clip == Clip.UNKNOWN) Clip.NO else clip

    /** Kết luận một lần xét ([Cell.check]). */
    enum class Verdict {
        /** Không làm gì. */
        NONE,

        /** Chưa biết — KHÔNG quyết gì; lượt đo kế (chữ đã tự xin) xét lại trên bố cục thật. */
        WAIT,

        /** Phải đo dò lại ô ở lượt khớp kế ([Cell.stale]). */
        DUE,
    }

    /**
     * Nhịp đo dò lại của MỘT ô co giãn (`FitGridLayout` giữ một cái cho mỗi ô). Kéo về `:core` ở soát vòng 2 (P1) để
     * test thuần cả TRÌNH TỰ sự kiện (đổ tại chỗ → lượt đo → lượt khớp) — lỗi nằm ở thứ tự, không ở một phép tính:
     *  - [check]: chữ đổi ⇒ theo nhịp [reprobe]; trạng thái cắt [Clip.UNKNOWN] ⇒ [Verdict.WAIT], KHÔNG chốt gì. Bản
     *    trước chốt "lượt thưa" (`grow = false`) ngay chỗ đổ tại chỗ (bố cục `null` ⇒ "không cắt"); lượt đo sau thấy
     *    cắt thật cũng không đổi được ⇒ [settle] giữ hộp cũ ⇒ số `100` kẹt `10`/`0`;
     *  - ô đã [stale] mà lần xét sau THẤY cắt ⇒ nâng thành lượt nhận số mới ([grow]) — lượt thưa không bao giờ nuốt
     *    một vết cắt thật;
     *  - [fitted]: sau lượt khớp ô còn cắt/tràn mà số đo đang dùng là số CŨ do [settle] giữ ([kept]) ⇒ báo chỗ gọi
     *    nhận số đo thật NGAY (không đo dò thêm), thay vì ghi dấu của chữ đang cắt rồi không bao giờ xét lại.
     */
    class Cell {
        /** Đến lượt đo dò lại — lượt khớp kế đo rồi gộp bằng [settle]. */
        var stale = false
            private set

        /** Lượt đo dò đó vì chữ bị CẮT (nhận số mới) hay lượt thưa (chỉ nhận khi hộp nhỏ đi rõ). */
        var grow = false
            private set

        /** Sau lượt khớp trước ô vẫn còn cắt/tràn ⇒ lượt đo dò sau phải thưa ([reprobe]). */
        var stuck = false
            private set

        /** Số đo đang dùng có dạng là số CŨ do [settle] giữ (lượt thưa) — chỗ gọi còn giữ số đo thật để nhận. */
        var kept = false
            private set

        /** Thời điểm (ms, đồng hồ đơn điệu) của lượt đo dò gần nhất. */
        var probedAt = 0L
            private set

        /**
         * Xét ô: [sigSame] = dấu chữ trùng lúc đo, [legible] = lưới đang ở tầng đọc được, [nowMs] = giờ, [clip] = đọc
         * trạng thái cắt (chỉ gọi khi cần — đọc bố cục chữ, không đo).
         */
        fun check(sigSame: Boolean, legible: Boolean, nowMs: Long, clip: () -> Clip): Verdict {
            if (stale) {
                if (!grow && clip() == Clip.YES) grow = true
                return Verdict.DUE
            }
            if (sigSame) return Verdict.NONE
            val c = clip()
            if (c == Clip.UNKNOWN) return Verdict.WAIT
            if (!reprobe(c == Clip.YES, legible, stuck, nowMs - probedAt)) return Verdict.NONE
            stale = true
            grow = c == Clip.YES
            return Verdict.DUE
        }

        /** Lượt khớp vừa đo dò ô lúc [nowMs]; [kept] = [settle] giữ số cũ ở ít nhất một dạng. */
        fun probed(nowMs: Long, kept: Boolean) {
            stale = false
            grow = false
            probedAt = nowMs
            this.kept = kept
        }

        /**
         * Sau lượt khớp (đã áp + kiểm lại): ô còn cắt/tràn ([clipped])? Trả `true` (một lần) khi phải nhận NGAY số đo
         * thật vì số đang dùng là số cũ [settle] giữ — hysteresis không được đổi lấy chữ bị cắt (R-WF2).
         *
         * [probedContent] = chữ đang hiện là chữ ô đã được ĐO DÒ (dấu chữ trùng lúc đo). Chỉ khi đó vết cắt mới nói
         * "lượt đo dò không chữa được" ([stuck]); chữ MỚI chưa đo dò mà bị cắt (lượt khớp do ô khác/đổi khung chạy trước
         * khi ô này đến lượt) thì không — chốt [stuck] lúc ấy biến nhịp nở 1 s thành chờ 30 s (soát vòng 3, P3).
         */
        fun fitted(clipped: Boolean, probedContent: Boolean = true): Boolean {
            stuck = clipped && probedContent
            if (!clipped || !kept) return false
            kept = false
            return true
        }
    }
}
