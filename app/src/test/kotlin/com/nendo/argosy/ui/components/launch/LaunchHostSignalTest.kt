package com.nendo.argosy.ui.components.launch

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchHostSignalTest {

    @Test
    fun realStopReports() {
        assertTrue(shouldReportHostStopped(false, LaunchHostSignal.STOPPED, stopRecorded = false))
    }

    @Test
    fun stopDuringRecreationDoesNotReport() {
        assertFalse(shouldReportHostStopped(true, LaunchHostSignal.STOPPED, stopRecorded = false))
    }

    @Test
    fun realDepartureReports() {
        assertTrue(shouldReportHostStopped(false, LaunchHostSignal.LEFT_COMPOSITION, stopRecorded = false))
    }

    @Test
    fun departureDuringRecreationDoesNotReport() {
        assertFalse(shouldReportHostStopped(true, LaunchHostSignal.LEFT_COMPOSITION, stopRecorded = true))
    }

    @Test
    fun recreatedOverlayComposingWithNoStopDoesNotReport() {
        assertFalse(shouldReportHostStopped(false, LaunchHostSignal.COMPOSED, stopRecorded = false))
    }

    @Test
    fun overlayComingBackAfterARealStopReports() {
        assertTrue(shouldReportHostStopped(false, LaunchHostSignal.COMPOSED, stopRecorded = true))
    }

    @Test
    fun hostStartingWithNoStopDoesNotReport() {
        assertFalse(shouldReportHostStopped(false, LaunchHostSignal.STARTED, stopRecorded = false))
    }

    @Test
    fun hostStartingAfterARealStopReports() {
        assertTrue(shouldReportHostStopped(false, LaunchHostSignal.STARTED, stopRecorded = true))
    }

    @Test
    fun stopsRecordAndReturnsConsume() {
        assertTrue(recordsHostStop(LaunchHostSignal.STOPPED))
        assertTrue(recordsHostStop(LaunchHostSignal.LEFT_COMPOSITION))
        assertFalse(recordsHostStop(LaunchHostSignal.COMPOSED))
        assertFalse(recordsHostStop(LaunchHostSignal.STARTED))
    }

    @Test
    fun recreationAfterTheWindowRequestNeverReports() {
        var recorded = false
        val sequence = listOf(
            true to LaunchHostSignal.STOPPED,
            true to LaunchHostSignal.LEFT_COMPOSITION,
            false to LaunchHostSignal.COMPOSED,
            false to LaunchHostSignal.STARTED
        )
        sequence.forEach { (changing, signal) ->
            val reports = shouldReportHostStopped(changing, signal, recorded)
            assertFalse("$signal", reports)
            if (reports) recorded = recordsHostStop(signal)
        }
    }
}
