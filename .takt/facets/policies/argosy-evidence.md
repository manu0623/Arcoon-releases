# Argosy evidence policy

Some correctness can only be shown on a device or against a live server. The reviewer cannot run
either, so this policy checks that the proof is declared, not that it is true.

## Required proof

Check that the PR description carries the proof the diff requires. Missing proof is **blocking**.

| The diff touches | Required proof in the description |
|------------------|-----------------------------------|
| Any UI, input or navigation | Hardware named; the flow driven by gamepad AND by touch; screenshots |
| A setting | Before and after showing the behavior changed at its consumption site |
| Dual-screen behavior | Evidence in both role arrangements (built-in primary and swapped) |
| Save sync, archiving, restore, pre-launch negotiation, session-end archiving, reconcile, a platform save handler | `GET /api/saves` output; negotiate returning `no_op` twice; the save present at the path `localSavePath` claims |
| RomM API calls or Moshi models | A live response compared against the model, with no field nulled by drift |
| Hardcore, cheats, rewind, save states in RA sessions | The ra-compliance manual checklist results |
| HW-rendered cores, GL context loss, `/Android/data` paths | A physical device run, not an emulator |
| A new core or platform | The core or platform flagged untested or unstable |

Also check the template sections exist and are filled: Summary, Behavior changes (with "None"
as an explicit claim), Hot paths, Testing evidence, AI assistance. Missing sections are blocking.
The Review summary section is filled after this review runs, so an empty one is not a finding.
The title is lowercase imperative with a scope prefix (`sync: ...`); a wrong title is a suggestion.
