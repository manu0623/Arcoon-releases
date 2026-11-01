package com.nendo.argosy.ui.dualscreen

import com.nendo.argosy.hardware.CompanionInGameState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CompanionSlotGateTest {

    private val inGame = PresentationSlot.InGame(CompanionInGameState(isLoaded = true), emptyList())
    private val detail = PresentationSlot.Detail(CompanionDetail(title = "Metroid Fusion", isGameTitle = true))
    private val platform = PresentationSlot.PlatformShowcase("Game Boy Advance", null, emptyList(), emptyList())

    @Test
    fun `a game's dashboard renders once the in-game slot arrives`() {
        assertEquals(inGame, companionSlotToRender(showsDashboard = true, slot = inGame))
    }

    @Test
    fun `the game hero is held black while the in-game state loads`() {
        assertNull(companionSlotToRender(showsDashboard = true, slot = detail))
    }

    @Test
    fun `a platform showcase is held black while the in-game state loads`() {
        assertNull(companionSlotToRender(showsDashboard = true, slot = platform))
    }

    @Test
    fun `the fallback is held black while the in-game state loads`() {
        assertNull(companionSlotToRender(showsDashboard = true, slot = PresentationSlot.Fallback))
    }

    @Test
    fun `a swapped companion without a game dashboard renders every slot as published`() {
        listOf(inGame, detail, platform, PresentationSlot.Fallback).forEach {
            assertEquals(it, companionSlotToRender(showsDashboard = false, slot = it))
        }
    }

    @Test
    fun `the dashboard fades in over the hold for the arrival duration`() {
        assertEquals(400, dashboardArrivalMs(showsDashboard = true, reduced = false, arrivalMs = 400))
    }

    @Test
    fun `reduced motion cuts the dashboard in without a fade`() {
        assertEquals(0, dashboardArrivalMs(showsDashboard = true, reduced = true, arrivalMs = 400))
    }

    @Test
    fun `a swapped companion without a game dashboard switches slots without a fade`() {
        assertEquals(0, dashboardArrivalMs(showsDashboard = false, reduced = false, arrivalMs = 400))
    }

    @Test
    fun `the launcher dissolves into the hold at the veil's rate when a game goes active`() {
        assertEquals(700, companionHandoffMs(showsDashboard = true, reduced = false, veilMs = 700))
    }

    @Test
    fun `reduced motion cuts the launcher to the hold`() {
        assertEquals(0, companionHandoffMs(showsDashboard = true, reduced = true, veilMs = 700))
    }

    @Test
    fun `without a game dashboard the companion switches branches without a dissolve`() {
        assertEquals(0, companionHandoffMs(showsDashboard = false, reduced = false, veilMs = 700))
    }
}
