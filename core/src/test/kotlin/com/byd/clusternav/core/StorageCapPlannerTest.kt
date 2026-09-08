package com.byd.clusternav.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pure prune/cap logic behind the always-on storage cap: given a list of files (id + size + last-modified) and
 * a byte cap, decide WHICH to delete — OLDEST first — so the survivors fit. The Android enumeration + deletion
 * (off-thread, degrade-safe) live in com.byd.clusternav.DiagStorageCap; this pins the arithmetic.
 */
class StorageCapPlannerTest {

    private fun entry(id: String, mb: Long, ageMs: Long) =
        StorageCapPlanner.Entry(id, mb * 1024L * 1024L, ageMs)

    @Test
    fun `under cap deletes nothing`() {
        val entries = listOf(
            entry("a", mb = 40, ageMs = 1),
            entry("b", mb = 40, ageMs = 2),
        )
        assertTrue(StorageCapPlanner.selectForDeletion(entries, capBytes = 150L * 1024 * 1024).isEmpty())
    }

    @Test
    fun `exactly at cap deletes nothing`() {
        val entries = listOf(
            entry("a", mb = 75, ageMs = 1),
            entry("b", mb = 75, ageMs = 2),
        )
        assertTrue(StorageCapPlanner.selectForDeletion(entries, capBytes = 150L * 1024 * 1024).isEmpty())
    }

    @Test
    fun `over cap deletes oldest first until under cap`() {
        // total 200 MB, cap 150 MB → must free 50 MB → delete the single oldest (60 MB) is enough.
        val entries = listOf(
            entry("newest", mb = 70, ageMs = 300),
            entry("middle", mb = 70, ageMs = 200),
            entry("oldest", mb = 60, ageMs = 100),
        )
        val deleted = StorageCapPlanner.selectForDeletion(entries, capBytes = 150L * 1024 * 1024)
        assertEquals(listOf("oldest"), deleted)
    }

    @Test
    fun `deletes multiple oldest when one is not enough`() {
        // total 240 MB, cap 150 MB → free 90 MB → delete oldest (50) + next-oldest (50) = 100 MB freed.
        val entries = listOf(
            entry("d", mb = 80, ageMs = 400),
            entry("c", mb = 60, ageMs = 300),
            entry("b", mb = 50, ageMs = 200),
            entry("a", mb = 50, ageMs = 100),
        )
        val deleted = StorageCapPlanner.selectForDeletion(entries, capBytes = 150L * 1024 * 1024)
        assertEquals(listOf("a", "b"), deleted)
    }

    @Test
    fun `stops as soon as survivors fit (fewest deletions)`() {
        // total 300 MB, cap 150 MB → free 150 MB → oldest 100 + next 60 = 160 freed is enough (not the 3rd).
        val entries = listOf(
            entry("keep", mb = 40, ageMs = 40),
            entry("also", mb = 100, ageMs = 30),
            entry("mid", mb = 60, ageMs = 20),
            entry("old", mb = 100, ageMs = 10),
        )
        val deleted = StorageCapPlanner.selectForDeletion(entries, capBytes = 150L * 1024 * 1024)
        assertEquals(listOf("old", "mid"), deleted)
    }

    @Test
    fun `ties on lastModified break deterministically by id`() {
        // two 100 MB entries with the SAME age, total 200 MB, cap 150 MB → delete one; id order decides which.
        val entries = listOf(
            entry("zeta", mb = 100, ageMs = 500),
            entry("alpha", mb = 100, ageMs = 500),
        )
        val deleted = StorageCapPlanner.selectForDeletion(entries, capBytes = 150L * 1024 * 1024)
        assertEquals(listOf("alpha"), deleted)
    }

    @Test
    fun `negative sizes are floored to zero and never selected on their own`() {
        // "glitch" reports -10 MB; real data 200 MB over a 150 cap → only real files chosen, glitch ignored.
        val entries = listOf(
            entry("glitch", mb = -10, ageMs = 1),
            entry("big", mb = 200, ageMs = 2),
        )
        val deleted = StorageCapPlanner.selectForDeletion(entries, capBytes = 150L * 1024 * 1024)
        assertEquals(listOf("big"), deleted)
    }

    @Test
    fun `empty input deletes nothing`() {
        assertTrue(StorageCapPlanner.selectForDeletion(emptyList(), capBytes = 150L * 1024 * 1024).isEmpty())
    }

    @Test
    fun `negative cap evicts everything`() {
        val entries = listOf(entry("a", mb = 1, ageMs = 1), entry("b", mb = 1, ageMs = 2))
        val deleted = StorageCapPlanner.selectForDeletion(entries, capBytes = -1)
        assertEquals(setOf("a", "b"), deleted.toSet())
    }

    @Test
    fun `default cap is 150 MB`() {
        assertEquals(150L * 1024 * 1024, StorageCapPlanner.DEFAULT_CAP_BYTES)
    }
}
