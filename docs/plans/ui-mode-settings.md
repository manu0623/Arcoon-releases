# Plan: one UI choice, and settings that follow it

Goal: Cocoon (board) and the original Argosy home become two modes of one launcher, chosen
in Settings ▸ Interface. The options that belong to whichever mode is active are the ones
that are shown, so Personalization stops being a Theme sub-page describing a UI the user may
not be running.

## What exists today

- `SettingsSection` (`ui/screens/settings/SettingsModels.kt`) holds `THEME`, `INTERFACE`,
  `HOME_SCREEN`, `LIBRARY_VIEW`, `BOX_ART`, `PRESENTATION`, `NAVIGATION`, ...
- Theme rows (`sections/ThemeSection.kt`, `ThemeItem`): Mode, Accent, AccentFooter,
  SecondaryColor, TintBleed, then ThemeThemes, **CocoonPersonal**, Backdrop, Fonts, IconPack.
  `CocoonPersonal` sets `CocoonThemeEngine.personalOpen`; `SettingsScreen.kt:1366-1375` renders
  `CocoonPersonalizationScreen` as an overlay on top of settings.
- Interface rows (`sections/InterfaceSection.kt`, `InterfaceItem`): language, ui scale, compact
  footer, system bars, quick panel in emulators, controller grip; then Home Screen, Library
  View, Box Art, Presentation, Lock Screen Art, Home app; status bar; recaps.
- Home Screen rows (`sections/HomeScreenSection.kt`, `HomeScreenItem`): background art, blur,
  saturation, opacity, video wallpaper, layout selector and layout fields — all original-UI
  concerns.
- The mode itself: `CocoonMode.useClassicHome` (`ui/cocoon/CocoonHomeModels.kt:311`) is a plain
  `MutableStateFlow(false)`, **not persisted** — it resets to Cocoon on every process start, and
  the only writer is `CocoonHomeScreen`'s own `onUseClassicHome` (line 189).
- Readers of that flow (the contact set): `navigation/NavGraph.kt:84` (which home to draw),
  `ui/ArgosyApp.kt:1049` (bumper handling), `ui/ArgosyViewModel.kt:453`,
  `screens/library/LibraryScreen.kt:361`, `screens/library/LibraryViewModel.kt:1962`,
  `screens/library/LibraryQuickMenu.kt:36`, `components/QuickSettingsModels.kt:199`.
- Settings root grouping: `mainSettingsCategoryRanges` (`MainSettingsSection.kt:232`) groups root
  rows by their `section` tag; `CocoonSettingsCategory.forKey` (`ui/cocoon/CocoonSettings.kt`)
  maps those tags to the left-hand categories (launcher/library/gameplay/connections/system/about).
  `CocoonSettingsSearch` enumerates sections for search.

## Target model

1. **One persisted mode.** New preference key `ui_mode` with literal tokens `COCOON` /
   `CLASSIC` (never a label), domain enum next to `ThemeMode` in `data/preferences/`.
   `CocoonMode.useClassicHome` becomes a view of it: keep the name and type so the seven readers
   do not move, but hydrate it from preferences at startup and write it through the repository.
   That also fixes the reset-on-relaunch bug for free.
2. **The choice lives in Interface**, as its own first row (new "Launcher" group):
   `Cocoon`, `Classic`. Subtitle states what the choice changes. Switching is immediate, no
   restart: every reader already collectAsState on the flow.
3. **Personalization moves to Interface** as a `NavigationPreference` opening the existing
   `CocoonPersonalizationScreen`, visible only in Cocoon mode. Theme keeps the rows that apply
   to both: themes, backdrop, fonts, icon pack.
4. **Rows follow the mode.** `InterfaceItem` and `HomeScreenItem` gain `visibleWhen` clauses on
   the mode: classic-only rows (Home Screen, Library View, Box Art, Presentation, Lock Screen
   Art, Home app) hide in Cocoon; Cocoon-only rows (Personalization, and any Cocoon option that
   later wants a home) hide in Classic. `HomeScreenItem`'s original rows gate the same way.
5. Labels are new string resources; keys stay literal tokens; no new literal dimensions.

## Settings chain for the new preference (all of these move together)

| Step | File |
| --- | --- |
| DataStore key + default + mapper + setter | `data/preferences/DisplayPreferencesRepository.kt` (pattern: `COMPACT_FOOTER`, lines 189/306/614) |
| Aggregated state | `data/preferences/UserPreferencesRepository.kt` (pattern: `compactFooter` 135/939) |
| Settings backup key | `data/preferences/SettingsBackupKeys.kt` |
| UI state | `ui/screens/settings/SettingsModels.kt` (`DisplayState`) |
| Hydrate | `ui/screens/settings/SettingsInitRouter.kt:907-945` (`DisplayState(...)`, field-by-field from prefs) |
| Setter (delegate) | `delegates/DisplaySettingsDelegate.kt` (`setCompactFooter` pattern) |
| Setter entry point | `ui/screens/settings/SettingsViewModel.kt:1183` (thin forwarder) |
| Render | `sections/InterfaceSection.kt`, `sections/ThemeSection.kt`, `sections/HomeScreenSection.kt` |
| Consumption | the seven `useClassicHome` readers above |
| Search | none needed: `CocoonSettingsSearch` indexes **sections** with keyword hints, not rows; add keywords to the Interface entry only if the mode row should be findable by "cocoon" |

## Contact set (what each reader does after the change)

- `NavGraph.kt:84` — draws `CocoonHomeScreen` or `HomeScreen`; now follows a persisted value,
  so the home the user picked survives a relaunch.
- `ArgosyApp.kt:1049` — bumpers: board gets L1 friends / R1 panel; classic keeps section paging.
- `ArgosyViewModel.kt:453`, `LibraryViewModel.kt:1962`, `LibraryQuickMenu.kt:36`,
  `QuickSettingsModels.kt:199`, `LibraryScreen.kt:361` — behaviour that is already Cocoon-only.

None of these writes or deletes data, so the change is behaviour-only; the write path is the
repository setter.

## Phases

1. Preference + toggle + persistence. No layout moved yet: Interface gains the mode row;
   `CocoonMode.useClassicHome` starts following it and survives relaunch.
2. Move Personalization from Theme to Interface (row + gating), Theme keeps theme-wide rows.
3. Gate the classic-only rows in Interface and Home Screen on the mode; keep the Cocoon
   settings categories resolving (the section tags the root rows carry must stay valid).
4. On-device verification, both panels captured.

## Proof obligations

- Toggling the mode in Interface changes the home immediately, and the choice survives
  `am force-stop` + relaunch.
- Personalization is reachable from Interface and absent from Theme in both modes.
- In Cocoon mode no classic-only Home Screen row can be focused; in Classic mode no Cocoon-only
  row can be focused (check focus index bounds, not just rendering).
- New rows work by pad and by touch, have footer hints only where non-obvious, and are
  reachable on TV (no touch).
- Settings still navigates correctly on the companion display; no focus-ring or input hand-off
  regressions (the panel work from this session must stay green).
- Every new string is a resource id; tokens stay literal; no literal dimensions or colours.

## Decisions (defaults chosen, say so before implementation if any is wrong)

1. **Immediate switch**, not "applies on next launch" — the readers are already reactive.
2. **Personalization stays a page**, reached from Interface, rather than its 20+ rows being
   flattened into Interface.
3. **Classic-only sections are hidden** in Cocoon mode rather than shown disabled with an
   explanation; the mode row is where the explanation lives.

## Out of scope

- Any change to what the individual options do inside Cocoon (`CocoonThemeEngine`), beyond
  where their entry point lives.
- Moving Library View / Box Art into a Cocoon-specific page.
