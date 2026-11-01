package com.nendo.argosy.ui.dualscreen

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nendo.argosy.domain.model.PresentationArt
import com.nendo.argosy.domain.model.PresentationStyle
import com.nendo.argosy.ui.theme.BoxArtStyleConfig
import com.nendo.argosy.ui.theme.LocalBoxArtStyle
import com.nendo.argosy.ui.theme.LocalUiScale
import com.nendo.argosy.ui.theme.AspectRatioClass
import com.nendo.argosy.ui.theme.UiScaleConfig
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class GameHeroLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun flatArtworkUsesSelectedShapeAndNativeModeKeepsImageProportions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("hero-cover", ".png", context.cacheDir)
        val bitmap = Bitmap.createBitmap(120, 240, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.RED)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val shape = mutableStateOf(BoxArtStyleConfig(aspectRatio = 1f))
        try {
            compose.setContent {
                CompositionLocalProvider(
                    LocalBoxArtStyle provides shape.value,
                    LocalUiScale provides UiScaleConfig(aspectRatioClass = AspectRatioClass.WIDE)
                ) {
                    MaterialTheme {
                        GameHero(
                            detail = CompanionDetail(title = "Title", artUrl = file.absolutePath),
                            style = PresentationStyle(art = PresentationArt.COVER),
                            coverMaxHeight = 240.dp,
                            modifier = Modifier.width(540.dp).testTag("hero")
                        )
                    }
                }
            }
            fun coverRatio(): Float? {
                val pixels = compose.onNodeWithTag("hero").captureToImage().toPixelMap()
                var left = pixels.width
                var right = -1
                var top = pixels.height
                var bottom = -1
                for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                    val color = pixels[x, y]
                    if (color.red > 0.95f && color.green < 0.05f && color.blue < 0.05f) {
                        left = minOf(left, x)
                        right = maxOf(right, x)
                        top = minOf(top, y)
                        bottom = maxOf(bottom, y)
                    }
                }
                return if (right < left) null else (right - left + 1f) / (bottom - top + 1f)
            }
            compose.waitUntil(5_000) { coverRatio() != null }
            for ((ratio, native) in listOf(1f to false, 0.75f to false, 1.5f to false, 0.5f to true)) {
                compose.runOnIdle {
                    shape.value = BoxArtStyleConfig(aspectRatio = if (native) 1f else ratio, nativeAspectRatio = native)
                }
                assertEquals(ratio, coverRatio()!!, 0.02f)
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun wideDisplayKeepsMetadataBesideCoverWithinDetailsPane() {
        compose.setContent {
            CompositionLocalProvider(LocalUiScale provides UiScaleConfig(aspectRatioClass = AspectRatioClass.WIDE)) {
                MaterialTheme {
                    GameHero(
                        detail = CompanionDetail(title = "Game title"),
                        style = PresentationStyle(),
                        coverMaxHeight = 240.dp,
                        modifier = Modifier.width(540.dp).testTag("hero"),
                        metadataModifier = Modifier.testTag("metadata")
                    )
                }
            }
        }

        val hero = compose.onNodeWithTag("hero").fetchSemanticsNode().boundsInRoot
        val metadata = compose.onNodeWithTag("metadata").fetchSemanticsNode().boundsInRoot
        assertTrue(metadata.left >= hero.left + hero.width / 3)
        compose.onNodeWithText("Game title").assertIsDisplayed()
    }

    @Test
    fun narrowHeroAtLargeScaleScrollsWithTheDetailsPage() {
        compose.setContent {
            CompositionLocalProvider(
                LocalUiScale provides UiScaleConfig(scale = 1.5f),
                LocalDensity provides Density(LocalDensity.current.density, fontScale = 1.5f)
            ) {
                MaterialTheme {
                    Box(Modifier.width(320.dp).height(220.dp)) {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            GameHero(
                                detail = CompanionDetail(
                                    title = "A game with a long title",
                                    subtitle = "A handheld platform",
                                    stats = CompanionGameStats(
                                        releaseYear = 2001,
                                        genre = "Role-playing",
                                        communityRating = 85f,
                                        userRating = 8,
                                        playTimeMinutes = 120,
                                        timeToBeatMainSec = 3600,
                                        timeToBeatCompletionistSec = 7200,
                                        status = "finished"
                                    )
                                ),
                                style = PresentationStyle(),
                                coverMaxHeight = 240.dp
                            )
                            Text("Description section")
                        }
                    }
                }
            }
        }

        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
            .assertCountEquals(1)
        compose.onNodeWithText("Description section").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("A game with a long title").performScrollTo().assertIsDisplayed()
    }
}
