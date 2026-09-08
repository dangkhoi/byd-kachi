package com.byd.clusternav.comfort

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Device-free unit tests for [SeatComfort] (pure :core model). Locks the HAL contract the on-car fix
 * (2026-09-06) settled on: named `BYDAutoSettingDevice` methods, a 1-based seatID, and the 1/2/3 state map
 * (NOT the old raw feature-ids on the AC device, which returned NOT_PROVISIONED on the owner car).
 *
 * Red-green: flipping [SeatComfort.stateForLevel] to a 0-based map, dropping the +1 in [SeatComfort.seatId],
 * or renaming a method constant makes the relevant assertion RED — this table is the single source of truth.
 */
class SeatComfortTest {

    @Test fun `stateForLevel maps user level to HAL state OFF-1 L1-2 L2-3`() {
        assertEquals(1, SeatComfort.stateForLevel(0))   // OFF  → state 1
        assertEquals(2, SeatComfort.stateForLevel(1))   // Mức 1 → state 2
        assertEquals(3, SeatComfort.stateForLevel(2))   // Mức 2 → state 3
        // Degrade-safe clamp (never throws for a corrupt stored level).
        assertEquals(1, SeatComfort.stateForLevel(-1))
        assertEquals(3, SeatComfort.stateForLevel(99))
        // The named state constants are the source of truth.
        assertEquals(1, SeatComfort.STATE_OFF)
        assertEquals(2, SeatComfort.STATE_L1)
        assertEquals(3, SeatComfort.STATE_L2)
    }

    @Test fun `OFF level is a sendable HAL turn-off command, never a skip sentinel`() {
        // Bug on-car v1.33: tapping a seat to OFF never reached the HAL — the bulk apply path skips OFF
        // (correct for apply-on-start: don't force every seat off on boot), so the INTERACTIVE tap must use a
        // per-seat write that sends OFF. The contract that makes that possible: OFF (level 0) maps to a REAL,
        // sendable HAL state (STATE_OFF = 1), NOT a sentinel meaning "do not write".
        assertEquals(SeatComfort.STATE_OFF, SeatComfort.stateForLevel(SeatComfort.LEVEL_OFF))
        assertEquals(1, SeatComfort.stateForLevel(SeatComfort.LEVEL_OFF))
        // Every user level 0/1/2 maps to a distinct, sendable 1..3 state (none is skipped / out of range).
        val states = (0..2).map { SeatComfort.stateForLevel(it) }
        assertEquals(listOf(1, 2, 3), states)
        states.forEach { assertTrue(it in SeatComfort.STATE_OFF..SeatComfort.STATE_L2, "state $it must be sendable 1..3") }
    }

    @Test fun `seatId maps 0-based app index to 1-based HAL seatID`() {
        assertEquals(1, SeatComfort.seatId(0))   // driver
        assertEquals(2, SeatComfort.seatId(1))   // passenger
        assertEquals(3, SeatComfort.seatId(2))   // rear-left (Han)
        assertEquals(4, SeatComfort.seatId(3))   // rear-right (Han)
    }

    @Test fun `methodFor picks the named SETTING-device method per mode`() {
        assertEquals("setSeatVentilatingState", SeatComfort.methodFor(SeatComfort.SeatMode.COOL))
        assertEquals("setSeatHeatingState", SeatComfort.methodFor(SeatComfort.SeatMode.HEAT))
        // Constants match the OEM decompile (airseating module).
        assertEquals("setSeatVentilatingState", SeatComfort.METHOD_VENTILATING)
        assertEquals("setSeatHeatingState", SeatComfort.METHOD_HEATING)
    }

    @Test fun `seat table has four seats indexed 0 to 3 in order`() {
        assertEquals(4, SeatComfort.SEATS.size)
        SeatComfort.SEATS.forEachIndexed { i, seat -> assertEquals(i, seat.index, "seat $i index") }
        assertEquals(listOf("driver", "passenger", "rear_left", "rear_right"), SeatComfort.SEATS.map { it.labelKey })
    }

    @Test fun `seatsForModel gives 2 for Seal and 4 for Han`() {
        assertEquals(listOf(0, 1), SeatComfort.seatsForModel(isHan = false))
        assertEquals(listOf(0, 1, 2, 3), SeatComfort.seatsForModel(isHan = true))
        assertEquals(2, SeatComfort.seatCountForModel(false))
        assertEquals(4, SeatComfort.seatCountForModel(true))
    }

    @Test fun `isHanModel is case-insensitive contains han and safe on null`() {
        assertTrue(SeatComfort.isHanModel("Han"))
        assertTrue(SeatComfort.isHanModel("BYD HAN EV"))
        assertTrue(SeatComfort.isHanModel("byd_han_dmi"))
        assertFalse(SeatComfort.isHanModel("Seal"))
        assertFalse(SeatComfort.isHanModel("Sealion 6"))
        assertFalse(SeatComfort.isHanModel(""))
        assertFalse(SeatComfort.isHanModel(null))   // unknown → Seal (2 seats), the safe default
    }
}
