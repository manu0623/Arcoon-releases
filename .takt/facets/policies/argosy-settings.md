# Argosy settings policy

Select for changes to preferences, DataStore keys, settings sections or settings routers.
Sources: AGENTS.md settings chain, menu-patterns and code-quality skills. All rules are blocking.

## No ghost settings

- A new or changed setting moves the whole chain: DataStore key in the owning domain prefs repo
  under `data/preferences/` -> `UserPreferences` aggregation -> `SettingsModels` state ->
  `SettingsInitRouter` hydrate -> delegate or router (`SettingsConfirmRouter` for A-press) ->
  section render -> the consumption site where behavior changes. Trace the value to the site that
  reads it. A setting nothing reads is REJECT.
- Hiding a setting's entry point on one surface (dual screen, TV, a size class) instead of
  rendering it there is a ghost setting. REJECT, unless the PR names what that surface cannot host.
- A new key goes in the owning domain repo, never `UserPreferencesRepository`. Enums persist
  through `fromString`, and display names never live in `data/`.

## Sections and navigation

- A new section is a `SettingsSection` enum entry with navigation state in the ViewModel.
- Sections render through `SectionPaneLayout` with the `SettingsLayout` sealed-item pattern
  (`visibleWhen`, `isFocusable`, `sectionOf`) and look up focus with `focusIndexOf`, never raw list
  positions.
- Navigation pushes and pops through `routePushSection`/`routePopSection`. A destination never
  declares its own parent.
- When visibility or disabled state changes, the focus index clamps to the last focusable item and
  moves to the next focusable one.
- A plain section (up, down, confirm only) joins `LightSectionsInput` instead of adding a new
  `*SectionInput` file.
- LB/RB section jumps call `jumpToPrevSection`/`jumpToNextSection`; a handler that does nothing
  returns `UNHANDLED`.

## Rows

- An enum row's A or tap opens the full option list (`CyclePreference` with `options` and
  `onSelect`); Left/Right cycles.
- Cycle options and `indexOf` entries are persisted literal tokens; only the rendered label comes
  from a resource. A translated string inside `options` is REJECT.
- A track slider puts the track in the right half on wide and ultra-wide aspect classes and full
  width below the title otherwise.
