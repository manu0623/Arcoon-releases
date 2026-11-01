# Argosy dual-screen policy

Select for changes under `ui/dualscreen/`, `SecondaryHomeActivity`, `DualScreenManager`,
`CompanionHost`, `PresentationSlot`, screen layouts or display handling.
Source: dual-screen skill, AGENTS.md. All rules are blocking.

## Single source of truth

- Shared state lives in `DualScreenManager` StateFlows. State anchored in an activity
  (`mutableStateOf` on an activity, a flag duplicated from DSM) is REJECT.
- A consumer of "who hosts PRIMARY", for rendering or key routing, reads
  `DualScreenManager.companionHoldsPrimary`, never its own flag.
- `_isRolesSwapped` is written only by `swapRoles()` and `applyDisplayRoleOverride()`. A new role
  writer debounces and refuses to commit during a live session.
- A new DSM `StateFlow` or `combine` is declared after the flows it combines.

## Components and parity

- Features render shared components on both surfaces. A dual-screen-only copy of a component is
  REJECT.
- Parity is built, or the PR states what the other surface cannot host. Hiding an entry point is
  not a deferral.
- Presentation-screen content is a `PresentationSlot` case, published through
  `PresentOnCompanion` (composable), `setCompanionDetail` (ViewModel detail: publish on focus
  change, republish on resume, clear on dispose), or `DualScreenManager.presentSlot`/`releaseSlot`
  (ViewModel-owned slot). A second render path is REJECT.

## Displays and input

- Code under `ArgosyApp` reaches DSM through `DualScreenManagerHolder.instance`, not
  `context as? ArgosyActivity`.
- Screen layout resolves only in `DualScreenManager.applyStoredScreenLayout`.
- Displays enumerate through `ScreenCatalog.attachedScreens()`; `getRealSize()` is not used for a
  target panel's size. Screens are not addressed by display id.
- `ScreenLayout.withRole` keeps "only PRIMARY and PRESENTATION trade" and updates `ScreenLayoutTest`.
- A key or motion dispatch path calls `dsm.claimInput` first; a non-hosting activity declines.
- Forwarded keys feed `gamepadInputHandler.handleKeyEvent`.
- Companion init wiring lives in `SecondaryHomeActivity`'s `initializeCompanion()` path, and the
  companion is disabled through `SecondaryHomeComponent.setEnabled`, never `finish()`.
- A new `CompanionHost` method has a real implementation. `= Unit` or `{}` is REJECT; a method with
  no companion job leaves the interface.
- References to deleted classes (DualHomeViewModel, DualHomeLowerScreen, DualHomeUpperScreen,
  ShowcaseViewModel, CarouselNavContext, SecondaryHomeInputHandler, ActiveModal, ForwardingMode and
  the rest of the dual-screen skill's deleted list) are REJECT.
