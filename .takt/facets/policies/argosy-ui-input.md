# Argosy UI and input policy

Select for changes to composables, screens, modals, input handlers or footer hints.
Sources: code-quality, menu-patterns and dual-screen skills. All rules are blocking.

## Input routing

- A new modal, dialog or overlay captures input through exactly one mechanism:
  `ModalInputEffect`/`pushModal` pushed on open, host guards in every navigation method returning
  `InputResult.HANDLED`, or a stored handler the host delegates to. Rendering `Modal`,
  `CenteredModal` or `ModalScaffold` alone captures nothing. Missing capture is REJECT.
- Handlers check modals first, deepest first, for every direction and Back.
- A stacked second modal uses `NestedModal`, not a second `Modal`.
- Opening a modal resets its focus to 0 (a picker may preselect the current value) and keeps the
  underlying list's focus.
- An `InputHandler` subscribes on `DisposableEffect`/resume and unsubscribes on dispose.
- Index wrap uses `.mod(size)`, never `%`.
- An intercept-tier handler declines (`UNHANDLED`) every button it does not own.
- A new Material3 `Switch`, `Checkbox`, `RadioButton` or `Slider` sets
  `Modifier.focusProperties { canFocus = false }`.

## Face buttons and adjustment

- A is primary/confirm, B is back/cancel, X is secondary (edit, filter, preview), Y is tertiary
  (favorite, delete, clear).
- A never adjusts a value. Left and Right adjust sliders, steppers, counters and enums. A flips a
  toggle and opens an enum's picker. This holds inside modals too.
- A stepper, counter or track slider clamps at its bounds and fires `HapticPattern.BOUNDARY_HIT`;
  it does not wrap or silently ignore the press.

## Footer hints

- Hints follow control-is-the-guide: a bar never exists only for A, B, the d-pad or Back. Keep
  non-obvious hints (X, Y, triggers, LB/RB paging, screen-global verbs); drop obvious ones.
- An adjustable row never hints A.
- Footers render through `FooterBar` with `onHintClick` wired, so hints are tappable.
- A modal renders its own footer only with `inlineFooterHints = true` and only when it sits above
  an overlay covering the root footer bar.

## Focus visuals and sound

- Focus never moves or scales an element. Use `FocusIndicators` (fill, halo, stripe, ring). Only
  cover tiles lift.
- Sound feedback uses the `InputDispatcher` defaults or an explicit `InputResult` sound override;
  new input with no feedback is REJECT.

## Lists and states

- A scrolling collection of repeated items uses `LazyColumn`, `LazyRow` or `LazyVerticalGrid`,
  never `Column`/`Row` with `verticalScroll`.
- Every `items(`, `itemsIndexed(` and grid call passes a stable `key =`.
- Lists use center focus (`animateScrollToItemCentered`).
- Every new screen or async data view has empty, error and loading states.
- Every new tappable element, including app bar actions, is also reachable by d-pad.

## Lifecycle

- New UI state that must survive rotation or process death lives in the ViewModel or saved state,
  not `remember`.
- A long-running operation started from UI handles the app going to the background.
- A new feature defines its first-run state: no library, no server configured, no data.
