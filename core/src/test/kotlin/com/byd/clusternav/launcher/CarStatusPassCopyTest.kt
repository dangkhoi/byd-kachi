package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 3.1.10 · CARSTATUS-PASS-COPY — mỗi nhịp đọc chỉ ghi field CỦA NÓ, mọi field khác giữ THEO CẤU TRÚC ═══════════════════════════
 *
 * Soát Pass 10: `readSlow`/`readFast` còn dựng MỚI sáu cụm (`Climate(...)` · `Tyres(...)` · `Lights(...)` · `Identity(...)` ·
 * `Infotainment(...)` · `Drivetrain(...)`) — đúng hình đã làm rơi `tailgateOpen` (`TrunkLidStaleTest`). Hôm nay mọi field của sáu cụm
 * thuộc đúng nhịp dựng nó nên dựng-mới chưa sai; nhưng field thêm sau cho NHỊP KHÁC sẽ rơi im lặng.
 *
 * Hai lớp khoá:
 *  1. hành vi — [filled] đặt MỌI field của MỌI cụm bằng phản chiếu (field thêm sau tự vào bài, không chép tay); ba nhịp + lượt sự
 *     kiện với cổng nhu cầu rỗng phải trả NGUYÊN ảnh;
 *  2. dây — `CarDataAdapter.kt` không còn một lời gọi dựng cụm `CarStatus.X(` nào (đỏ khi trả một cụm về dựng-mới).
 */
class CarStatusPassCopyTest {

    @Test fun `ba nhip va luot su kien voi cong rong tra NGUYEN anh - moi field moi cum`() {
        val full = filled()
        val a = CarDataAdapter(HalBindingTable(FakeHalGateway()), { emptySet() })
        assertEquals(full, a.readFast(full), "nhịp nhanh")
        assertEquals(full, a.readSlow(full), "nhịp chậm")
        assertEquals(full, a.readBody(full), "nhịp thân xe")
        assertTrue(a.readEvents(full, setOf("pm25_value", "door_lf", "speed")) === full, "lượt sự kiện ngoài nhu cầu: không đối tượng mới")
    }

    @Test fun `CarDataAdapter khong dung moi cum CarStatus nao`() {
        val code = SourceRoots.codeOf("src/main/kotlin/com/byd/clusternav/launcher/CarDataAdapter.kt")
        val built = Regex("""CarStatus\.(Energy|Drivetrain|Climate|Tyres|Body|Lights|Identity|Infotainment)\(""").findAll(code).map { it.value }.toList()
        assertEquals(emptyList<String>(), built, "nhịp phải đi `prev.x.copy(...)` — dựng mới là bỏ rơi field của nhịp khác")
    }

    companion object {
        /** [CarStatus] với MỌI field của mọi cụm khác mặc định (phản chiếu constructor đầy đủ của data class). */
        fun filled(): CarStatus {
            fun <T : Any> make(c: Class<T>): T {
                val k = c.declaredConstructors.filter { !it.isSynthetic }.maxBy { it.parameterCount }
                val args: List<Any> = k.parameterTypes.map { t ->
                    when (t) {
                        Integer::class.java, Int::class.javaPrimitiveType -> 7
                        java.lang.Double::class.java, Double::class.javaPrimitiveType -> 7.5
                        java.lang.Boolean::class.java, Boolean::class.javaPrimitiveType -> true
                        String::class.java -> "x"
                        else -> error("kiểu field chưa biết: $t trong ${c.simpleName}")
                    }
                }
                @Suppress("UNCHECKED_CAST") return k.newInstance(*args.toTypedArray()) as T
            }
            return CarStatus(
                energy = make(CarStatus.Energy::class.java), drivetrain = make(CarStatus.Drivetrain::class.java),
                climate = make(CarStatus.Climate::class.java), tyres = make(CarStatus.Tyres::class.java),
                body = make(CarStatus.Body::class.java), lights = make(CarStatus.Lights::class.java),
                identity = make(CarStatus.Identity::class.java), infotainment = make(CarStatus.Infotainment::class.java),
                controls = mapOf("temp" to 22, "trunk" to 1),
            )
        }
    }
}
