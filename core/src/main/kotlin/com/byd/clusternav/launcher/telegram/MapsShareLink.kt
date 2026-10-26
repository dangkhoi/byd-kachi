package com.byd.clusternav.launcher.telegram

import java.net.URLDecoder

/**
 * Link chia sẻ của Google Maps → điểm đến — 3.1.20 (owner 26/10: *"người ta hay share location, lấy link đó quăng vô dẫn
 * đường"*). Thuần Kotlin (`:core`) ⇒ test bằng giá trị; việc mở link rút gọn (mạng) ở `:app`.
 *
 * ## Đã đo (26/10, link owner gửi)
 * `maps.app.goo.gl/<mã>` ⇒ 302 ⇒ `www.google.com/maps/place/<Tên,+địa+chỉ>/data=!4m2!3m1!1s0x…:0x…` — URL **không có toạ
 * độ**, chỉ tên + địa chỉ. Toạ độ `center=` trong HTML trang là vị trí của MÁY HỎI (đo được ở Sài Gòn cho một nơi ở Lâm
 * Đồng) ⇒ KHÔNG dùng. Nên: có toạ độ trong URL thì lấy, không thì đưa tên + địa chỉ cho Google Maps tự tra.
 */
object MapsShareLink {

    /** Kết quả: [name] hiển thị, [query] chữ để Google Maps tra, [coords] "lat, lng" nếu URL có. */
    data class Target(val name: String, val query: String, val coords: String?)

    private val URL_RE = Regex("""https?://\S+""")
    private val HOSTS = Regex("""^https?://(maps\.app\.goo\.gl|goo\.gl/maps|maps\.google\.[a-z.]+|(www\.)?google\.[a-z.]+/maps)(/|\?|$)""",
        RegexOption.IGNORE_CASE)

    /** Link Google Maps đầu tiên trong [text]; `null` nếu không có. */
    fun find(text: String): String? = URL_RE.findAll(text).map { it.value.trimEnd(')', '.', ',', '>') }
        .firstOrNull { HOSTS.containsMatchIn(it) }

    /** Link rút gọn — phải mở (theo chuyển hướng) mới biết nơi. */
    fun isShort(url: String): Boolean = Regex("""^https?://(maps\.app\.goo\.gl|goo\.gl)/""", RegexOption.IGNORE_CASE).containsMatchIn(url)

    /**
     * Đọc URL Google Maps ĐẦY ĐỦ. Toạ độ theo độ tin: ghim nơi `!3d<lat>!4d<lng>` > tham số `q`/`query`/`destination`/`daddr`/`ll`
     * dạng số > tâm khung `@lat,lng`. Tên: đoạn `/place/<…>/` > tham số chữ. `null` khi không ra được gì để dẫn.
     */
    fun parse(url: String): Target? {
        val pin = Regex("""!3d(-?\d{1,2}\.\d+)!4d(-?\d{1,3}\.\d+)""").find(url)?.let { "${it.groupValues[1]}, ${it.groupValues[2]}" }
        val params = query(url)
        val paramCoords = listOf("q", "query", "destination", "daddr", "ll").firstNotNullOfOrNull { k -> params[k]?.let(::coordsOf) }
        val at = Regex("""@(-?\d{1,2}\.\d+),(-?\d{1,3}\.\d+)""").find(url)?.let { "${it.groupValues[1]}, ${it.groupValues[2]}" }
        val coords = pin ?: paramCoords ?: at
        val place = Regex("""/maps/place/([^/?#]+)""").find(url)?.groupValues?.get(1)?.let(::decode)?.takeIf { it.isNotBlank() }
        val text = place ?: listOf("q", "query", "destination", "daddr").firstNotNullOfOrNull { k ->
            params[k]?.takeIf { coordsOf(it) == null && it.isNotBlank() }
        }
        val query = text ?: coords ?: return null
        val name = text?.substringBefore(',')?.trim()?.takeIf { it.isNotEmpty() } ?: "Shared place"
        return Target(name.take(60), query, coords)
    }

    private fun coordsOf(s: String): String? =
        Regex("""^\s*(-?\d{1,2}\.\d+)\s*,\s*(-?\d{1,3}\.\d+)\s*$""").find(s)?.let { "${it.groupValues[1]}, ${it.groupValues[2]}" }

    private fun query(url: String): Map<String, String> = url.substringAfter('?', "").substringBefore('#')
        .split('&').mapNotNull { kv -> val i = kv.indexOf('='); if (i <= 0) null else kv.substring(0, i) to decode(kv.substring(i + 1)) }
        .toMap()

    private fun decode(s: String): String = runCatching { URLDecoder.decode(s, "UTF-8") }.getOrDefault(s.replace('+', ' ')).trim()
}
