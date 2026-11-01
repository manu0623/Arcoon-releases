# Argosy core policy

Applies to every review. Domain policies add to it.

## Scope and severity

- Review added and changed lines only. Pre-existing violations in untouched lines are not
  findings, and a nearby violation never justifies a new one.
- Every finding is either **blocking** or a **suggestion**. Only blocking findings allow REJECT.
  A rule marked "suggestion" below is never blocking.
- Plain forms already blocked by the CI rules job (see knowledge) are not re-reported. Forms the
  regex cannot see are findings.
- Each finding names `file:line`, the rule, and the concrete fix.

## No raw user-facing text (blocking)

Source: CONTRIBUTING.md laws and AS-7, AGENTS.md label vs token, code-quality skill.

- Is any user-visible text a literal instead of a string resource? This covers composable text,
  titles, labels, content descriptions, placeholders, dialog and confirm labels, footer hint
  pairs, toasts, snackbars, notifications, error messages, and strings built by concatenation or
  templates. REJECT.
- Is one string both rendered to the user and used as a stored value, map key, `indexOf` entry,
  route, identity or path segment? REJECT. The stored value is a literal token; the label is a
  resource id.
- Does `data/` or `domain/` reference `R`, or carry a label as `(Context) -> String`? Labels for
  inner types attach as `@StringRes Int` extension properties in `ui/common/`. REJECT.
- Does a string contain a gamepad button letter ("Press A", "(B)") instead of an `InputGlyph` or a
  `FooterBar` hint? REJECT, unless the row remaps that same button.
- Upstream identifiers (core ids, option tokens, emulator and platform names, paths, package
  names) stay literal and byte-exact, and `libretro/coreoptions/**` stays English. Moving them into
  translatable resources is REJECT.

## Touch and gamepad on every interactive element (blocking)

Source: CONTRIBUTING.md law 1, AGENTS.md feature completeness, code-quality skill.

- Does every new interactive element have a touch path through `clickableNoFocus`? Plain
  `clickable`, `.clickable { }` and `combinedClickable` are REJECT.
- Does every new interactive element have a gamepad path: an `InputHandler` route with a focus
  index owned by the ViewModel and an `isFocused` visual? A touch-only or gamepad-only element is
  REJECT.
- Does every new action have a controller mapping or a navigable menu path? TV has no touch, so an
  action reachable only by tap is REJECT.
- Does new code use Compose focus (`focusable()`, `onFocusChanged`, `FocusRequester` to move
  selection) to decide navigation or selection? REJECT. `FocusRequester` for soft-keyboard text
  entry is allowed.

## Comments are rare and valid KDoc only (blocking)

Source: CONTRIBUTING.md law 2, AS-1, code-quality skill.

- Any `//` comment, one-line `/* */` or `/** */`, or prose comment block in any language or file
  type (Kotlin, C++, Gradle, XML, JSON, shell, Python)? REJECT.
- A KDoc is allowed only as a multi-line block directly above a public declaration whose contract
  is not obvious from its name and types. REJECT when a KDoc: sits on a private or internal
  declaration; restates the name; narrates, argues or explains history ("because", "previously",
  "we", "to avoid", "note that", "for now", "temporarily"); runs past four content lines; carries
  debugging numbers; or contains TODO, FIXME or STOPSHIP.
- Rationale belongs in the commit or PR description, never the diff.

## Design and concision (blocking unless marked)

Source: code-quality skill, AGENTS.md decomposition.

- Does the diff re-implement something a shared utility already does (`rememberLongPressAnimationState` and `longPressGesture`,
  `toHomePlatformUi`, `Modal`/`CenteredModal`/`NestedModal`, `GradientColorExtractor`,
  `CompletionStatusUi`, `SectionPaneLayout`, `FooterBar`, `InputGlyph`, `clickableNoFocus`)?
  REJECT with the utility to use.
- Does the diff add a second accessor or derivation for state an existing accessor already exposes,
  without removing the old one and migrating callers? REJECT.
- When the diff extends a derivation (emptiness, changed-ness, a label, a reducer), did it update
  every existing implementation of it? A missed copy is REJECT.
- Does the diff follow the pattern the relevant skill prescribes? Copying a pattern from nearby
  code that the skill forbids is REJECT.
- Is there speculative code: unused parameters, "just in case" branches, dead fields, abstractions
  with one caller and no second use in sight? REJECT.
- Does the diff add a new responsibility (a new method group, state block or feature concern) to a
  ViewModel or Activity over ~500 lines, a repository over ~300, or a composable file over ~400,
  instead of extracting a delegate, service or component? REJECT. A fix inside existing code in
  such a file is at most a suggestion.
- Silent failure: an empty `catch`, an ignored `runCatching` result, a swallowed error with no log
  or state? REJECT.

## Honest change description (blocking)

Source: CONTRIBUTING.md AS-2, AS-3, AS-5.

- When a commit message or PR description is provided: does every observable behavior change in the
  diff appear in it, and does every claim in it match the diff? A behavior change shipped as
  "refactor" is REJECT.
- Agent task numbers, private plan names, `.local-notes` paths, other projects' issue numbers or
  tool artifacts in code, comments, docs or user-facing strings? REJECT.
- Does each new test assert an outcome or invariant rather than only that a mock was called? Does a
  test double set two values that share one source into a state production cannot produce?
  REJECT.

## Maintainer-locked changes (reported, never REJECT)

List every changed file under a maintainer-locked path (see knowledge) in the summary's
"Needs maintainer discussion" section, with one line on what changed. This is informational.
