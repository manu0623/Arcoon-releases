#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage: scripts/review.sh --pr-body <file> [--base <ref>] [--force]

Reviews the commits between the base and HEAD with takt's review-argosy workflow and records the
summary the PR gate reads. Paste that summary into the PR description.

  --pr-body <file>  PR description to check for evidence and declared behavior changes.
  --base <ref>      Start of the range. Defaults to the merge base with origin/main.
  --force           Review again even when HEAD already has a recorded summary.

Each step runs as a local agent CLI you are logged in to, driven by the workflow's prompts. API
keys and SDK providers are refused. The provider is codex when the Codex CLI is installed,
otherwise claude. TAKT_PROVIDER picks cursor, copilot or kiro instead.
EOF
}

base=""
pr_body=""
force=0
while [ $# -gt 0 ]; do
  case "$1" in
    --base) base="${2:?--base needs a ref}"; shift 2 ;;
    --pr-body) pr_body="${2:?--pr-body needs a file}"; shift 2 ;;
    --force) force=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) usage >&2; exit 2 ;;
  esac
done

if [ -z "$pr_body" ]; then
  usage >&2
  exit 2
fi
if [ ! -f "$pr_body" ]; then
  echo "PR description file not found: $pr_body" >&2
  exit 2
fi

root="$(git rev-parse --show-toplevel)"
cd "$root"

if ! command -v takt >/dev/null 2>&1; then
  echo "takt is not installed. Install it with: npm install -g takt" >&2
  exit 2
fi

head="$(git rev-parse HEAD)"
if [ -z "$base" ]; then
  if git rev-parse --verify -q origin/main >/dev/null; then
    base="$(git merge-base origin/main HEAD)"
  else
    echo "No origin/main to review against. Pass --base <ref>." >&2
    exit 2
  fi
fi
base="$(git rev-parse "$base")"

if [ "$base" = "$head" ]; then
  echo "Nothing to review: HEAD is $base."
  exit 0
fi

reviews_dir=".takt/reviews"
summary_out="$reviews_dir/$head.pr.md"

verdict_of() {
  sed -nE 's/^## Verdict: (APPROVE|REJECT)[[:space:]]*$/\1/p' "$1" | head -n 1
}

finish() {
  cat "$summary_out"
  if [ "$(verdict_of "$summary_out")" = "APPROVE" ]; then
    exit 0
  fi
  exit 1
}

if [ "$force" -eq 0 ] && [ -f "$summary_out" ] && [ -n "$(verdict_of "$summary_out")" ]; then
  echo "Using the recorded review for $head."
  finish
fi

if ! git diff --quiet HEAD; then
  echo "Note: uncommitted changes are not part of this review. Only $base..$head is reviewed." >&2
fi

provider="${TAKT_PROVIDER:-}"
if [ -z "$provider" ]; then
  if command -v codex >/dev/null 2>&1; then
    provider="codex"
  else
    provider="claude"
    echo "Codex CLI not found; reviewing with claude. A different model family than the author reviews best." >&2
  fi
fi

case "$provider" in
  claude) cli="claude"; cli_path_var="TAKT_CLAUDE_CLI_PATH" ;;
  codex) cli="codex"; cli_path_var="TAKT_CODEX_CLI_PATH" ;;
  cursor) cli="cursor-agent"; cli_path_var="TAKT_CURSOR_CLI_PATH" ;;
  copilot) cli="copilot"; cli_path_var="TAKT_COPILOT_CLI_PATH" ;;
  kiro) cli="kiro-cli"; cli_path_var="TAKT_KIRO_CLI_PATH" ;;
  *)
    echo "Provider '$provider' does not run a local agent CLI. Use claude, codex, cursor, copilot or kiro." >&2
    exit 2
    ;;
esac

cli_path="$(command -v "$cli" || true)"
if [ -z "$cli_path" ]; then
  echo "The $provider provider needs the '$cli' CLI on PATH, logged in." >&2
  exit 2
fi

for config in "$HOME/.takt/config.yaml" ".takt/config.yaml"; do
  if [ -f "$config" ] && grep -Eq '^[[:space:]]*[a-z_]*(api_key|github_token):' "$config"; then
    echo "$config sets an API key. Reviews run only through a logged-in agent CLI; remove the key." >&2
    exit 2
  fi
done

branch="$(git rev-parse --abbrev-ref HEAD | tr '/' '-')"
latest_pointer="$reviews_dir/latest-$branch"

task="Review the committed changes in the range $base..$head of this repository.
Read the change with \`git diff $base..$head\` and the commit messages with \`git log $base..$head\`.
Ignore uncommitted working tree changes. Report findings only on lines this range adds or modifies.

PR description:
$(cat "$pr_body")"

if [ -f "$latest_pointer" ]; then
  previous="$(cat "$latest_pointer")"
  if [ -f "$previous" ] && [ "$previous" != "$summary_out" ]; then
    task="$task

Previous summary for this branch: $previous"
  fi
fi

marker="$(mktemp)"
trap 'rm -f "$marker"' EXIT

env \
  -u TAKT_ANTHROPIC_API_KEY -u TAKT_OPENAI_API_KEY -u TAKT_OPENCODE_API_KEY \
  -u TAKT_CURSOR_API_KEY -u TAKT_COPILOT_GITHUB_TOKEN -u TAKT_KIRO_API_KEY \
  -u ANTHROPIC_API_KEY -u OPENAI_API_KEY -u CODEX_API_KEY \
  "$cli_path_var=$cli_path" \
  takt --pipeline --skip-git --quiet --workflow review-argosy --provider "$provider" --task "$task"

summaries=()
while IFS= read -r found; do
  summaries+=("$found")
done < <(find .takt/runs -type f -path '*/reports/review-summary.md' -newer "$marker" 2>/dev/null)

if [ "${#summaries[@]}" -ne 1 ]; then
  echo "Expected one review summary from this run, found ${#summaries[@]}." >&2
  exit 2
fi
if [ -z "$(verdict_of "${summaries[0]}")" ]; then
  echo "The review summary has no APPROVE or REJECT verdict: ${summaries[0]}" >&2
  exit 2
fi

mkdir -p "$reviews_dir"
cp "${summaries[0]}" "$summary_out"
printf '%s\n' "$summary_out" > "$latest_pointer"
finish
