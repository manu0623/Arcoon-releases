# Argosy platform and emulator policy

Select for changes to platform, emulator, core, BIOS or launch registries and resolvers.
Sources: platform-support skill, AGENTS.md upstream mandate. All rules are blocking.

## Upstream identifiers

- A new or changed core id, `.so` file name, option token, file extension or BIOS file name cites its
  upstream source (the libretro `.info` file, the core repository, docs.libretro.com, the emulator's
  source). An id that looks inferred from a sibling (x64 exists, so x128 is added) without a citation
  is REJECT.
- Upstream-exact maps stay exact. Renaming a value in `EmulatorRegistry.getRetroArchSaveDirName` to
  match a display name, or "completing" it with cores whose folder differs only by case, is REJECT.
- Core option manifests match buildbot tokens byte for byte.

## Registries move together

- A new platform or core moves its lockstep set: `PlatformDefinitions`, `EmulatorRegistry`
  `supportedPlatforms` backed by a `LibretroCoreRegistry` core, `SavePathRegistry`,
  `BiosPathRegistry` with `GameLauncher.mandatoryBios`, `PlatformSaveHandlerRegistry`,
  `M3uManager.SUPPORTED_PLATFORMS`, and slug aliases. The author also checks the state path, core
  option, core control, touch layout, shader, frame and platform weight registries. A missing entry
  is REJECT.
- A `LocalPlatformIds` constant has a `localPlatformIdMap` entry and a `PlatformDef`.
- New cores and platforms ship flagged untested or unstable.

## Resolvers agree

- `EmulatorResolver.getEmulatorPackageForGame` and `GameLauncher.resolveEmulator` change precedence
  and built-in gating together. A change to one without the other is REJECT.
- The variant asymmetry stays: EmulatorResolver returns base package strings and GameLauncher
  returns variant definitions. "Unifying" it is REJECT.
- A fork or nightly package joins its family's `packagePatterns` with an id starting
  `<baseId>_`, never a separate hand-written entry.
- An emulator's `activityClass` is load-bearing and has no launch fallback.

## Platform specifics

- Android, Steam and iOS are not emulated platforms, and the android platform keeps its apk and xapk
  extensions.
- Arcade romset zips are never extracted, and fbneo and mame stay split from arcade.
