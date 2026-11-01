# Argosy architecture policy

Select for changes to ViewModels, delegates, repositories, use cases, models or Compose state.
Sources: AGENTS.md architecture, code-quality skill. All rules are blocking.

## Layers

- Dependencies point inward: `ui/` -> `domain/` -> `data/`. A `ui/` type imported into `domain/` or
  `data/` is REJECT.
- A new `android.*` import in `domain/`, or any Compose import (`Color`, `ImageVector`, `Icons`) in
  `data/` or `domain/`, is REJECT.
- `ui/` reaches data through repositories. Any DAO in `ui/` (import, alias, wildcard, fully
  qualified constructor parameter, another ViewModel's DAO field) is REJECT. A new DAO method the UI
  needs is exposed through a repository.

## Compose stability

- `app/compose_stability_config.conf` declares `data.model.**`, `data.local.entity.**`,
  `domain.model.**`, `ui.screens.**`, `ui.components.**`, `ui.dualscreen.**`,
  `core.game.AchievementUi` and `hardware.CompanionInGameState` stable. A `var`, or a collection
  mutated in place, in a state or model class there is REJECT.
- A composable reading a plain `var` of a ViewModel or delegate is REJECT.

## State updates

- `_uiState.value = _uiState.value.copy(...)` is REJECT; use `update { }`.
- Writing back a state snapshot taken before a suspend call is REJECT.

## Composition cost

- A `.map`, `.filter`, `.sortedBy`, `.groupBy` or similar transform in a composable body, outside
  `remember(keys)` or `derivedStateOf`, is REJECT.

## Recovery and guards

Source: AGENTS.md "Handling user data that looks wrong".

- Code that meets malformed, unexpected or mis-keyed user data repairs it, then ignores it, and
  discards only with a stated reason repair is impossible. A discard branch without that reason is
  REJECT. Data that identifies nothing may be refused.
- A new validation, gate or repair on one path to a mutation covers every other path to the same
  mutation (download and cache restore, upload and local cache). A guard on one path only is
  REJECT; name the uncovered path.
- Code that forms paths or keys from a value handles the value having no mapping as a reported
  outcome, not a silent drop.

## Storage

- A persisted SAF tree grant (`OpenDocumentTree` plus `takePersistableUriPermission` for storage
  access) is REJECT; storage goes through `FileAccessLayer`.
- Treating an unreadable file as absent is REJECT.

## Play sessions

- Argosy UI in the foreground ends the play session; changes keep the single-screen grace period
  and the dual-screen no-grace rule.
