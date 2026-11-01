"""Argosy shell-command reader for Bash hooks. Answers which git subcommands a command
line runs, and whether it runs `gh pr create`, by splitting on ; && || | & and newlines
outside quotes and reading each segment's program and arguments. Text that only
mentions git inside an argument, such as `echo "git commit"` or a path ending in
pre-push, does not count."""

import os
import re
import shlex

ENV_ASSIGNMENT_RE = re.compile(r"\A[A-Za-z_][A-Za-z0-9_]*=")

LEADING_WORDS = {
    "!", "if", "then", "else", "elif", "do", "while", "until",
    "time", "command", "exec", "nohup", "env",
}

GIT_OPTIONS_WITH_VALUE = {
    "-C", "-c", "--git-dir", "--work-tree", "--namespace", "--super-prefix", "--config-env",
}

GH_OPTIONS_WITH_VALUE = {"-R", "--repo"}


def split_segments(command):
    segments, current, quote, i = [], [], None, 0
    n = len(command)
    while i < n:
        c = command[i]
        if quote == "'":
            current.append(c)
            if c == "'":
                quote = None
            i += 1
        elif quote == '"':
            current.append(c)
            if c == "\\" and i + 1 < n:
                current.append(command[i + 1])
                i += 2
                continue
            if c == '"':
                quote = None
            i += 1
        elif c in "'\"":
            quote = c
            current.append(c)
            i += 1
        elif c == "\\" and i + 1 < n:
            current.append(c)
            current.append(command[i + 1])
            i += 2
        elif c == "#" and (not current or current[-1].isspace()):
            while i < n and command[i] != "\n":
                i += 1
        elif c in ";&|\n":
            segments.append("".join(current))
            current = []
            i += 1
            while i < n and command[i] in "&|":
                i += 1
        else:
            current.append(c)
            i += 1
    segments.append("".join(current))
    return [s for s in segments if s.strip()]


def tokenize(segment):
    try:
        return shlex.split(segment)
    except ValueError:
        return segment.split()


def command_words(segment):
    words = tokenize(segment)
    for i, word in enumerate(words):
        bare = word.lstrip("$({")
        if not bare or bare in LEADING_WORDS or ENV_ASSIGNMENT_RE.match(bare):
            continue
        return [bare] + words[i + 1:]
    return []


def program_is(words, name):
    return bool(words) and os.path.basename(words[0]) == name


def positional_after_options(words, options_with_value, limit):
    positional, i = [], 1
    while i < len(words) and len(positional) < limit:
        word = words[i]
        if word in options_with_value:
            i += 2
        elif word.startswith("-"):
            i += 1
        else:
            positional.append(word.rstrip(")}"))
            i += 1
    return positional


SHELLS = {"bash", "sh", "zsh", "dash"}
MAX_SHELL_DEPTH = 3


def command_word_lists(command, depth=0):
    """Program and arguments of every segment, following `bash -c "..."` into its script."""
    for segment in split_segments(command):
        words = command_words(segment)
        if words and os.path.basename(words[0]) in SHELLS and depth < MAX_SHELL_DEPTH:
            script = shell_script_argument(words)
            if script is not None:
                yield from command_word_lists(script, depth + 1)
                continue
        yield words


def shell_script_argument(words):
    for i, word in enumerate(words[1:], start=1):
        if word == "-c" or (word.startswith("-") and not word.startswith("--") and "c" in word[1:]):
            return words[i + 1] if i + 1 < len(words) else None
    return None


def git_invocations(command):
    """Each git call in `command` as (subcommand, arguments after the subcommand)."""
    found = []
    for words in command_word_lists(command):
        if not program_is(words, "git"):
            continue
        i = 1
        while i < len(words) and words[i].startswith("-"):
            i += 2 if words[i] in GIT_OPTIONS_WITH_VALUE else 1
        if i < len(words):
            found.append((words[i].rstrip(")}"), words[i + 1:]))
    return found


def git_subcommands(command):
    return [subcommand for subcommand, _ in git_invocations(command)]


def runs_git(command, subcommand):
    return subcommand in git_subcommands(command)


def runs_gh_pr_create(command):
    for words in command_word_lists(command):
        if not program_is(words, "gh"):
            continue
        if positional_after_options(words, GH_OPTIONS_WITH_VALUE, 2) == ["pr", "create"]:
            return True
    return False
