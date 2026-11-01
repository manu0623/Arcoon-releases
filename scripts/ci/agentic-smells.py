#!/usr/bin/env python3
"""Argosy agentic-smell check. Flags house-rule violations on ADDED lines only,
so pre-existing code is never blamed. Driven by scripts/ci/smell-rules.json and
the KDoc and block-comment checks in scripts/ci/comment_checks.py.

Usage:
  agentic-smells.py --range origin/main...HEAD   # CI: diff a git range
  agentic-smells.py --diff-file some.diff        # test: parse a saved diff

Exit 0 when clean, 1 when findings exist, 0 on internal errors (fails open)."""

import argparse
import importlib
import json
import os
import re
import subprocess
import sys

HUNK_RE = re.compile(r"^@@ -\d+(?:,\d+)? \+(\d+)(?:,\d+)? @@")


def glob_to_re(glob):
    out, i = [], 0
    while i < len(glob):
        c = glob[i]
        if glob[i : i + 3] == "**/":
            out.append("(?:.*/)?")
            i += 3
        elif glob[i : i + 2] == "**":
            out.append(".*")
            i += 2
        elif c == "*":
            out.append("[^/]*")
            i += 1
        elif c == "?":
            out.append("[^/]")
            i += 1
        else:
            out.append(re.escape(c))
            i += 1
    return re.compile("".join(out) + r"\Z")


def matches_any(path, globs):
    return any(glob_to_re(g).match(path) for g in globs)


def parse_added_with_lines(diff_text):
    added, current, lineno = [], None, 0
    for line in diff_text.splitlines():
        if line.startswith("+++ "):
            path = line[4:].strip()
            current = path[2:] if path.startswith("b/") else path
            if current == "/dev/null":
                current = None
        elif line.startswith("@@"):
            m = HUNK_RE.match(line)
            if m:
                lineno = int(m.group(1))
        elif current and line.startswith("+") and not line.startswith("+++"):
            added.append((current, lineno, line[1:]))
            lineno += 1
        elif current and not line.startswith("-") and not line.startswith("\\"):
            lineno += 1
    return added


def load_rules(root):
    with open(os.path.join(root, "scripts", "ci", "smell-rules.json")) as f:
        return json.load(f)["rules"]


def evaluate(added, rules):
    findings = []
    for rule in rules:
        pat = re.compile(rule["added_regex"])
        skip = re.compile(rule["skip_line_regex"]) if rule.get("skip_line_regex") else None
        for path, lineno, text in added:
            if not matches_any(path, rule["path_include"]):
                continue
            if matches_any(path, rule.get("path_exclude", [])):
                continue
            if skip and skip.search(text):
                continue
            if pat.search(text):
                findings.append((rule, path, lineno, text.strip()))
    return findings


def load_ci_module(name):
    ci_dir = os.path.dirname(os.path.abspath(__file__))
    if ci_dir not in sys.path:
        sys.path.insert(0, ci_dir)
    return importlib.import_module(name)


def load_comment_checks():
    return load_ci_module("comment_checks")


def file_lines(path, rows, root):
    try:
        with open(os.path.join(root, path), errors="replace") as f:
            lines = f.read().splitlines()
        if all(n - 1 < len(lines) and lines[n - 1] == text for n, text in rows.items()):
            return lines
    except OSError:
        pass
    sparse = [None] * (max(rows) + 1)
    for n, text in rows.items():
        sparse[n - 1] = text
    return sparse


def evaluate_comments(added, root):
    comment_checks = load_comment_checks()
    return evaluate_module(comment_checks, comment_checks.comment_findings, added, root)


def evaluate_stability(added, root):
    stability_checks = load_ci_module("stability_checks")
    service_checks = load_ci_module("service_stability_checks")
    return (
        evaluate_module(stability_checks, stability_checks.stability_findings, added, root)
        + evaluate_module(service_checks, service_checks.findings, added, root)
    )


def evaluate_module(module, run, added, root):
    by_path = {}
    for path, lineno, text in added:
        if matches_any(path, module.PATHS):
            by_path.setdefault(path, {})[lineno] = text
    findings = []
    for path, rows in by_path.items():
        lines = file_lines(path, rows, root)
        for rule_id, message, snippet, index in run(lines, lambda i: (i + 1) in rows):
            summary = module.RULES[rule_id][0]
            rule = {"id": rule_id, "summary": summary, "message": message}
            findings.append((rule, path, index + 1, snippet))
    return findings


def report(findings):
    by_rule = {}
    for rule, path, lineno, text in findings:
        by_rule.setdefault(rule["id"], (rule, []))[1].append((path, lineno, text))
    lines = ["AGENTIC SMELL CHECK: {} finding(s)".format(len(findings)), ""]
    for rule_id, (rule, hits) in by_rule.items():
        lines.append("[{}] {}".format(rule_id, rule["summary"]))
        lines.append("    {}".format(rule["message"]))
        for path, lineno, text in hits:
            lines.append("    {}:{}: {}".format(path, lineno, text[:120]))
        lines.append("")
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--range")
    parser.add_argument("--diff-file")
    args = parser.parse_args()

    root = os.environ.get("GITHUB_WORKSPACE") or os.getcwd()
    try:
        rules = load_rules(root)
    except Exception as e:
        print("smell check: could not load rules ({}), skipping".format(e))
        sys.exit(0)

    if args.diff_file:
        with open(args.diff_file) as f:
            diff_text = f.read()
    elif args.range:
        try:
            diff_text = subprocess.run(
                ["git", "diff", "--unified=0", args.range],
                cwd=root, capture_output=True, text=True, timeout=60,
            ).stdout
        except Exception as e:
            print("smell check: git diff failed ({}), skipping".format(e))
            sys.exit(0)
    else:
        parser.error("one of --range or --diff-file is required")

    added = parse_added_with_lines(diff_text)
    findings = evaluate(added, rules)
    try:
        findings += evaluate_comments(added, root)
    except Exception as e:
        print("smell check: comment checks could not run ({})".format(e))
        sys.exit(2)
    try:
        findings += evaluate_stability(added, root)
    except Exception as e:
        print("smell check: stability checks could not run ({})".format(e))
        sys.exit(2)
    if not findings:
        print("smell check: clean")
        sys.exit(0)
    print(report(findings))
    sys.exit(1)


if __name__ == "__main__":
    main()
