# Contributing to Argosy

Argosy is a controller-first launcher for emulation handhelds and Android TV.
PRs are welcome, including AI-assisted ones. What matters is that you own what
you submit: you've personally reviewed it and understand what it does.

Structural law for coding agents lives in [AGENTS.md](AGENTS.md); this file
covers the human side of contributing.

## The laws

These are enforced, not suggested. The `rules` CI check runs two steps. The
smell check fails PRs that break the mechanically-checkable rules. The coupling
sweep only reports coupled locations a change may have missed and never fails
the build. Reviewers enforce the rest.

1. **Dual-modality input.** Every interactive element must work with touch AND
   gamepad, with no exceptions. Touch uses `clickableNoFocus`
   (ui/util/Modifiers.kt), never plain `clickable()`. Gamepad focus is
   index-driven via `InputHandler`, not Compose focus. A component with one
   input modality is incomplete.
2. **Comments are rare.** No prose comments anywhere. Zero `//` inside
   function bodies, and no single-line `/* */` or `/** */` anywhere - the
   one-line block form is an inline comment with different delimiters. A KDoc
   is allowed only to document a non-obvious public contract, written as a
   noun phrase in the multi-line block form above the declaration. It never
   narrates, argues, or explains history; rationale goes in the PR description,
   not the diff.
3. **Tokens, not literals.** Dimensions, text sizes and colors route through
   tokens.json and the theme (`Dimens` and the generated theme values). No
   hardcoded `.dp` or `.sp` values (0.dp excepted) and no `Color(0x...)`
   literals in UI code.
4. **Off-main-thread work.** File, network, DB, and blocking native calls run
   on `Dispatchers.IO`. A progress spinner over a blocked main thread is a bug.
5. **Save handling is high-risk.** Anything touching save sync, archiving, or
   restore paths requires live verification against a real device and server,
   not code-reading. Expect a higher review bar and requests for proof.
6. **Hardcore rules are strict by intent.** No save states, no cheats, no
   rewind, save isolation. Do not loosen these as a side effect of another
   change; propose lockout changes on their own.
7. **No raw user-facing text.** Every string a user can see is a string
   resource, including footer hints, toasts, snackbars and dialogs. A label and
   a stored token are always separate strings. See AS-7 and AS-8 below.

## Agentic code smells (AS taxonomy)

Reviews reference these by number. If your tooling produced one, fix it before
submitting. These are the patterns that turn a decent PR into a slow one.

- **AS-1 Reviewer-persuasion comments.** Rationale prose in the diff, arguing
  the change's correctness to the reader. Belongs in the PR description.
- **AS-2 Leaked internal referents.** Agent task numbers, private plan names,
  or tool artifacts in code, comments, or docs.
- **AS-3 Smuggled behavior change.** Any observable delta shipped under
  "refactor" or "behavior-preserving" without being declared in the Behavior
  changes section.
- **AS-4 Hot-path cost blindness.** Network calls, DB writes, or blocking work
  added to launch, frame, or sync paths without acknowledging the cost.
- **AS-5 Coverage theater.** Tests that assert a mock was called instead of
  defending an invariant. Every new test should have an answer to "what breaks
  if this is deleted?"
- **AS-6 Context-window responsiveness.** Addressing whichever review thread is
  most recent while standing maintainer asks go unhandled. Check the full
  review state before pushing.
- **AS-7 Raw user-facing string.** English prose written straight into any
  user-visible text instead of `stringResource(R.string.<key>)`. Arguments such
  as `text =`, `title =`, `label =`, `placeholder =` and `confirmLabel =` are
  examples, not the full list. The
  app ships in several languages and the catalogue only stays complete if
  nothing bypasses it. Identifiers are not prose: core ids, option tokens,
  emulator and platform names, paths and package names stay literals, and the
  upstream ones must stay byte-exact.
- **AS-8 Display string doing a second job.** A string that is rendered to the
  user *and* is also a stored value, a map key, a route, an identity or a path
  component. This is a defect independent of translation, because renaming the
  label to read better silently invalidates stored data. Give the value a token
  and the label a resource id; never make the label carry both. The reverse
  failure is a deduplicating extractor fusing two byte-identical English
  strings that mean different things, so identical text at two usage sites gets
  two keys, always.

## Building and installing

Build with `./gradlew assembleDebug`. The debug build installs as a separate
app (package `com.nendo.argosy.debug`) next to the official one and keeps its
own data. Never uninstall the official app to install a local build.
Uninstalling deletes its library, settings and save cache. Release builds are
maintainer-only.

## Review before opening a PR

Every contributor PR goes through an automated review against these laws. It
runs [takt](https://github.com/nrslib/takt) with the
`review-argosy` workflow in `.takt/`, on your machine.

1. Install takt (`npm install -g takt`) and log in to an agent CLI. Each
   review step runs as that CLI, driven by the workflow's prompts; the script
   refuses API keys and SDK providers. Codex is the default reviewer; without
   it the review uses Claude Code. `TAKT_PROVIDER` picks Cursor, Copilot or
   Kiro instead.
2. Write your PR description from the template into a file and commit your
   work.
3. Run `scripts/review.sh --pr-body <file>`. It reviews your branch against
   `origin/main`, including the testing evidence and behavior changes the
   description declares. Fix blocking findings, commit, and run it again.
4. Paste the summary it prints under Review summary and open the PR.

The Takt summary check on the PR reads that pasted summary. It runs no review
and costs nothing; it fails when the verdict is not APPROVE or the summary
covers an older commit than the PR head. Pushing new commits means reviewing
again and pasting the new summary.

A false positive can go through with a `Takt-ack: <what you checked instead>`
trailer on the head commit. Expect the maintainer to read that trailer.

## AI assistance notice

If you used AI to help with your contribution, mention it in the PR along with
how much it did (docs only, code generation, most of the work... whatever's
accurate). Same policy as the rest of the rommapp org.

This just helps me figure out how much scrutiny to apply and where to focus
when reviewing. That's all it's for.

## Process

- PRs need the template filled in: behavior-change inventory, hot-path
  declaration, and testing evidence (real hardware, real flow). PRs missing
  these, or failing the `rules` check, may be closed with a pointer here.
  Reopen once fixed, no hard feelings.
- The `rules-exempt` label (maintainer-applied) skips the whole `rules` job,
  smell check and coupling sweep both, for the rare legitimate exception.
- Squash-merge is the house style; keep PR titles in lowercase-imperative
  scope-prefixed form (`sync: ...`, `ui: ...`, `libretro: ...`).
- New emulator cores or platforms ship flagged as untested/unstable until
  verified on hardware.
