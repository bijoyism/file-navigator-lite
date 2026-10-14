package com.bijoyism.filenavigator.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanDiffTest {
    @Test
    fun firstScanEstablishesBaselineWithoutAlertingForExistingFiles() {
        val result = ScanDiff.compare(emptySet(), setOf("a", "b"))
        assertTrue(result.initializedNow)
        assertTrue(result.newKeys.isEmpty())
        assertTrue(ScanDiff.BASELINE_KEY in result.savedKeys)
    }

    @Test
    fun nextScanFindsOnlyNewFileKeys() {
        val previous = setOf(ScanDiff.BASELINE_KEY, "a", "b")
        val result = ScanDiff.compare(previous, setOf("b", "c"))
        assertFalse(result.initializedNow)
        assertEquals(setOf("c"), result.newKeys)
        assertEquals(setOf(ScanDiff.BASELINE_KEY, "b", "c"), result.savedKeys)
    }

    @Test
    fun repeatedScanDoesNotDuplicateNotifications() {
        val previous = setOf(ScanDiff.BASELINE_KEY, "a", "b")
        val result = ScanDiff.compare(previous, setOf("a", "b"))
        assertTrue(result.newKeys.isEmpty())
    }
}
