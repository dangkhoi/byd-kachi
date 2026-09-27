package com.byd.clusternav.launcher

// ═══ Mồi dùng chung của các bài `TelemetryReadout*Test` — tách ra 2.76 (pure move) để mỗi tệp test ≤ 500 dòng ═══

/** Feature-id GIẢ, ổn định theo tên hằng — cho binding `FeatureName` khi gateway giả không có bảng tên thật. */
internal fun fakeId(constName: String): Int = constName.hashCode() or Int.MIN_VALUE

/**
 * [CarStatus] đã nạp **mọi** datum nối được, bằng gateway giả trả [seed] cho mọi binding — cùng cách hai bài
 * FULL WIRE / wired-tier ở trên dựng, gom lại để bài mới không chép lần thứ ba.
 */
internal fun wiredStatus(seed: String): CarStatus {
    val getters = mutableMapOf<String, String?>()
    val features = mutableMapOf<Int, String?>()
    val settings = mutableMapOf<String, String?>()
    val names = mutableMapOf<String, Int>()
    val locals = mutableMapOf<String, String?>()
    TelemetryRegistry.ALL.forEach { spec ->
        when (val r = HalBindingTable.routeOf(spec.bindingKey)) {
            is BindingRoute.NamedMethod -> getters[r.method] = seed
            is BindingRoute.Feature -> features[r.id] = seed
            is BindingRoute.Setting -> settings[r.key] = seed
            is BindingRoute.Local -> locals[r.method] = seed
            is BindingRoute.FeatureName -> fakeId(r.constName).let { names[r.constName] = it; features[it] = seed }
            BindingRoute.None -> {}
        }
    }
    getters["getPM2p5Value"] = "[$seed, $seed]"   // getter trả MẢNG — xem chú thích ở bài FULL WIRE
    val adapter = CarDataAdapter(
        HalBindingTable(
            FakeHalGateway(
                getters = getters, features = features, settings = settings,
                featureNames = names, locals = locals,
            ),
        ),
    )
    return adapter.readSlow(adapter.readFast(CarStatus()))
}
