package com.nendo.argosy.ui.screens.settings

import com.nendo.argosy.ui.dualscreen.PresentationSlot

/**
 * The showcase Settings publishes for a section that authors no presentation of its own: the
 * section (or platform) name straight away, with the cover collage filled in once the library
 * answers.
 *
 * The name arrives before the covers on purpose. Publishing only after the covers were looked up
 * left the lower screen on the presentation fallback for the length of that query - and for good
 * on a section whose covers came back empty - and the fallback is Cocoon's near-black wallpaper,
 * so the screen read as broken rather than idle.
 */
internal fun settingsAmbientSlot(
    name: String,
    coverPaths: List<String>
): PresentationSlot.PlatformShowcase = PresentationSlot.PlatformShowcase(
    name = name,
    yearSpan = null,
    coverPaths = coverPaths,
    facts = emptyList()
)
