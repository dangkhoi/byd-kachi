package com.byd.clusternav.vehicleprobe

import kotlin.system.exitProcess

/** No-argument process boundary for the fixed dadb T10 runner. */
object T10RunnerMain {
    @JvmStatic
    fun main(args: Array<String>) {
        val code = HudSignSessionRunner().run(args)
        if (code != 0) exitProcess(code)
    }
}
