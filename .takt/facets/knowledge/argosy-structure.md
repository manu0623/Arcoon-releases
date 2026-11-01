# Argosy structure

Argosy is a controller-first Android game launcher (Kotlin, Jetpack Compose) for emulation
handhelds, dual-screen handhelds and Android TV, built around the RomM backend. TV has no touch.

## Authority

The tracked skills under `.claude/skills/`, `AGENTS.md`, `CONTRIBUTING.md` and
`docs/save-id-to-path.md` are the project's law. Existing code is not: the tree carries known
violations, so a pattern found nearby is never evidence that a new instance is allowed.

## Layers

`ui/` -> `domain/` -> `data/`. Dependencies point inward only. `domain/` is Compose-free.

## Where things live

| Concern | Location |
|---------|----------|
| Platform, emulator and core registries | `PlatformDefinitions`, `EmulatorRegistry`, `LibretroCoreRegistry`, plus `app/src/main/**/*Registry*.kt` |
| Emulator resolution (two resolvers that must agree) | `EmulatorResolver.getEmulatorPackageForGame`, `GameLauncher.resolveEmulator` |
| Save sync | `data/sync/`, `SyncCoordinator`, `SaveSyncOrchestrator`, `SaveSyncRepository` and services, platform handlers in `data/sync/platform/PlatformSaveHandlerRegistry.kt` |
| Save config lookup | `SavePathAuthority` (never `SavePathRegistry.getConfig` outside `savepath/`) |
| Input | `InputDispatcher`, per-screen `InputHandler`, `clickableNoFocus` in `ui/util/Modifiers.kt` |
| Footer hints | `FooterBar`, `FooterHintItem` in `FooterHint.kt` |
| Settings | DataStore key -> domain prefs repo -> `UserPreferences` -> `SettingsModels` -> `SettingsInitRouter` hydrate -> delegate or router (`SettingsConfirmRouter` for A-press, `SettingsGeneralRouter` for section push and pop) -> section render -> consumption site |
| Tokens | `design-system-docs/tokens.json` -> `scripts/gen-tokens.mjs` -> `ui/theme/generated/*` |
| Dual screen | `DualScreenManager` StateFlows (single source of truth), `CompanionHost`, `PresentationSlot` |
| Database | `data/local/migrations/Migrations.kt`, `MigrationRegistry.ALL`, `ALauncherDatabase` version, `app/schemas/**` |
| File access | `FileAccessLayer` + Manage Storage; never a persisted SAF tree |
| Strings | `app/src/main/res/values/strings_<area>.xml`, keys `<area>_<component>_<role>` |

## Maintainer-locked paths

Changes here need prior maintainer discussion. They are reported, not rejected, because the
maintainer's own changes pass through the same review.

- `libretrodroid/**`
- Social: `data/social/**`, `ui/screens/social/**`, `ui/components/friends/**`, `data/sync/SocialSync*`
- Netplay: `data/netplay/**`, `libretro/LibretroNetplayCoordinator.kt`
- Music and BGM: `data/music/**`, `domain/usecase/music/**`, `ui/screens/musicbrowser/**`, `ui/audio/**`
- Release and build identity: release notes, `proguard-rules.pro`, and in `app/build.gradle.kts` the
  `applicationId`, `applicationIdSuffix`, `signingConfigs`, `versionCode` and `versionName` lines

## Mechanical checks that already block

The CI `rules` job (`scripts/ci/smell-rules.json`) blocks the plain forms of: `//` comments and
one-line block comments, plain `clickable`, raw `.dp` in `ui/`, capitalised string literals in
named text arguments, Compose imports in `domain/`, DAO imports in `ui/`, save-channel primitives
outside their use cases, `SavePathRegistry` config lookups outside `savepath/`, system-bar calls
outside `ImmersiveMode.kt`, box-art glow outside `BoxArtFrame.kt`, default-locale number
formatting, `Color(0x...)` and `.sp` literals in UI code, `(index - 1) % size` wraps,
`.focusable(`, `x.value = x.value.copy(`, and in Kotlin under `app/src/main` the KDoc shape checks
and TODO/FIXME/STOPSHIP in block comments. Review the forms those regexes cannot see; do not re-report the plain forms.
