# Dual-Screen UI

Reimagined UI for dual-display devices (e.g., AYN Thor).

## Philosophy

- **Upper screen**: Visual context (art, title, stats, read-only info)
- **Lower screen**: Interaction (lists, actions, selection, editing)
- **Reduce cognitive load**: One screen to look at, one screen to control
- **Input clarity**: Lower screen owns focus; upper forwards input

## Packages

- `home/` - Dual-screen home with game carousel (lower) and showcase (upper)
- `gamedetail/` - Tabbed game details with Saves/Media/Options (lower) and info display (upper)
- `library/` - Dual-screen library browser
- `collections/` - Dual-screen collection management

## Input Handling

**Normal state**: Upper screen is display-only. Any input -> refocus to lower.

**Modal state**: Upper screen handles input directly (e.g., Start menu).

## Shared State

Both displays run in one process: `ArgosyActivity` and `SecondaryHomeActivity` declare no
`android:process`, so they share the same Hilt singletons. Live state is read from those singletons
directly; for example, `ArgosyApp` and `PresentationSlotContent` both read launch progress from
the one `LaunchProgressTracker`. `SessionStateStore` (SharedPreferences) holds what must outlive the
process: the active game session (including the display the game runs on) and display flags, read
back when the launcher restarts under a running game.
