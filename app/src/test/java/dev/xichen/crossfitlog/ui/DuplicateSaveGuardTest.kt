package dev.xichen.crossfitlog.ui

import org.junit.Assert.*
import org.junit.Test

class DuplicateSaveGuardTest {
    @Test fun repeatedTapCannotStartSecondSave() {
        val guard = DuplicateSaveGuard()
        assertTrue(guard.tryStart())
        assertFalse(guard.tryStart())
    }

    @Test fun failedSaveCanBeRetried() {
        val guard = DuplicateSaveGuard()
        assertTrue(guard.tryStart())
        guard.reset()
        assertTrue(guard.tryStart())
    }
}
