# Argosy save sync policy

Select for changes to save sync, archiving, restore, save or state caches, save path resolution,
save channels or platform save handlers. This is a fragile zone: code reading never proves it
correct, so the evidence policy's live proof applies. Read `docs/save-id-to-path.md` before judging
any handler or path change; it holds the per-platform rules. Sources: AGENTS.md fragile zones,
`docs/save-id-to-path.md`. All rules are blocking.

## Archive shapes and paths

- A handler that changes what it zips (archive root names or layout), or which roots
  `matchArchiveRoot`/`matchArchive` accept, keeps every previously uploaded shape matchable. A
  change that orphans saves already on a server is REJECT.
- A change to an id-to-path rule (3DS extdata id shift, PS2 region prefixes, Xbox 360 content type
  `00000001`, Xbox partition LBA, Switch nesting) cites upstream and matches
  `docs/save-id-to-path.md`. A PS2 region prefix change lands in both `territoryPrefixFor` and the
  sigil `ps2_region_prefix`.
- Discovered path levels (3DS id0/id1, Switch user and profile, Xbox 360 XUID) are enumerated, never
  constructed. `constructSavePath` never invents a profile directory.
- `SavePathResolver.discoverSavePath` and `constructSavePath` still agree for the affected platform.
- A title-id keyed layout is registered only for platforms in `PlatformDefinitions.TITLE_ID_PLATFORMS`
  (not the unrelated `VariantCategory.TITLE_ID_PLATFORMS`).
- A single-platform emulator's config id is not platform-qualified; a multi-platform emulator never
  shares one config id across platforms. Platform-qualified ids never reach
  `EmulatorRegistry.familyBaseIdFor`.
- A handler nesting under a per-install id overrides `isValidCachedSavePath`; no new
  `platformSlug == "switch"` checks outside the registry.
- PSP and PS3 stay on `PrefixBundleFolderHandler`, and PSP bundling keeps its `PARAM.SFO` test.
- Save paths are registered only for emulators whose layout was read from the emulator's source.

## Doors and data safety

- A 3DS restore never deletes a component the archive does not carry, and every door (upload, local
  cache, cache restore, hardcore downgrade, download by id) goes through `namedArchiveRoots` and
  `placeArchive`.
- Sync never deletes content on disk as a side effect.
- `planReconcile` and `ConflictResolutionService` handle every action and every
  `ConflictResolution`, with no silent fall-through to `NO_OP`.
- The Secure Saves OFF path leaves the ON path byte-identical, and `RefreshOutcome.Unreadable` stays
  distinct from `Absent`.
- Variant launches still skip pre-launch and session-end sync.
- State-shaped saves never route through the save pipeline, and saves never through the state
  pipeline. State sync never waits on a failing save.
- A state restore or validate entry point resolves its core through
  `CoreVersionExtractor.getCoreIdForEmulator`, and a state write that resolves no core never writes
  to the parent directory.

## Xbox

- `XboxSaveHandler` restages from hakuX's `UDATA` folder through root on each read.
- Every restore door reaches hakuX through `unpackArchive`'s root write-back, owned by hakuX's uid.
- `xbox_hdd.qcow2` stays retired firmware (`BiosPathRegistry.isRetiredFirmware`), never tracked or kept.
