#!/usr/bin/env python3
"""Argosy takt PR gate. A pull request passes only when it carries an APPROVE review summary from
`scripts/review.sh --pr-body` for its exact head commit.

Shared by the Claude Code PreToolUse hook (`.claude/hooks/takt-guard.py`, which imports `decide` to
refuse `gh pr create`) and the `takt-summary` GitHub workflow (run as `takt_gate.py pr-check`), which
reads the summary pasted into the PR description. The workflow only reads text; it runs no review.

A bypass needs a `Takt-ack:` trailer on the head commit stating what was checked instead, so every
skipped review leaves a reason in history. Locally it also needs ARGOSY_SKIP_TAKT=1.
"""

import os
import re
import subprocess
import sys

REVIEWS_DIR = ".takt/reviews"
VERDICT_RE = re.compile(r"^## Verdict: (APPROVE|REJECT)\s*$", re.MULTILINE)
RANGE_RE = re.compile(r"^Range:\s*`?([0-9a-f]{7,40})\.\.([0-9a-f]{7,40})`?\s*$", re.MULTILINE)
ACK_RE = re.compile(r"^Takt-ack:\s*(.+?)\s*$", re.MULTILINE)
ACK_MIN_CHARS = 20
REVIEW_COMMAND = "scripts/review.sh --pr-body <description file>"


def git(args, root):
    try:
        r = subprocess.run(["git"] + args, cwd=root, capture_output=True, text=True, timeout=15)
        return r.stdout.strip() if r.returncode == 0 else None
    except Exception:
        return None


def summary_relpath(sha):
    return os.path.join(REVIEWS_DIR, sha + ".pr.md")


def verdict_in(text):
    found = VERDICT_RE.search(text or "")
    return found.group(1) if found else None


def ack_in(message):
    found = ACK_RE.search(message or "")
    if not found:
        return None
    value = found.group(1)
    return value if len(value) >= ACK_MIN_CHARS else None


def decide(root, sha, skip_requested):
    """Whether `gh pr create` may run for this commit, and the message explaining a refusal."""
    try:
        with open(os.path.join(root, summary_relpath(sha)), encoding="utf-8") as f:
            verdict = verdict_in(f.read())
    except OSError:
        verdict = None
    if verdict == "APPROVE":
        return True, None
    short = sha[:10]
    if skip_requested:
        if ack_in(git(["log", "-1", "--format=%B", sha], root)):
            return True, None
        return False, (
            "TAKT GATE -- ARGOSY_SKIP_TAKT=1 is set, but commit {} carries no usable `Takt-ack:` "
            "trailer.\n  Add a trailer of at least {} characters saying what was checked instead of "
            "the review."
        ).format(short, ACK_MIN_CHARS)
    if verdict == "REJECT":
        state = "The review of {} was REJECTED. Fix the blocking findings in {}, commit, and review again.".format(
            short, summary_relpath(sha)
        )
    else:
        state = "No PR review exists for {}.".format(short)
    return False, (
        "TAKT GATE -- blocking:\n"
        "  {}\n\n"
        "  Run: {}\n"
        "  Or acknowledge a false positive: ARGOSY_SKIP_TAKT=1 plus a `Takt-ack: <what you checked>` "
        "trailer on the commit."
    ).format(state, REVIEW_COMMAND)


def check_pr_description(body, head_sha, head_message):
    """The reason a PR description fails the gate, or None when it passes."""
    if ack_in(head_message):
        return None
    verdict = verdict_in(body)
    if verdict is None:
        return (
            "The PR description has no review summary. Run `{}` and paste its summary under "
            "Review summary."
        ).format(REVIEW_COMMAND)
    if verdict != "APPROVE":
        return "The pasted review summary is a REJECT. Fix the blocking findings and review again."
    reviewed = RANGE_RE.search(body)
    if reviewed is None:
        return "The pasted review summary has no `Range: <base>..<head>` line."
    reviewed_head = reviewed.group(2)
    if not head_sha.startswith(reviewed_head):
        return (
            "The pasted review summary covers {}, but the PR head is {}. Review the current head "
            "and paste the new summary."
        ).format(reviewed_head[:10], head_sha[:10])
    return None


def pr_check():
    problem = check_pr_description(
        os.environ.get("PR_BODY", ""),
        os.environ.get("PR_HEAD_SHA", ""),
        os.environ.get("PR_HEAD_MESSAGE", ""),
    )
    if problem is None:
        print("Review summary approves the PR head.")
        return 0
    print("::error title=Takt review summary::" + problem)
    return 1


def main():
    if len(sys.argv) < 2 or sys.argv[1] != "pr-check":
        sys.stderr.write("usage: PR_BODY=... PR_HEAD_SHA=... PR_HEAD_MESSAGE=... takt_gate.py pr-check\n")
        return 2
    return pr_check()


if __name__ == "__main__":
    sys.exit(main())
