"""Compose stability contract checks.

app/compose_stability_config.conf declares com.nendo.argosy.ui.** stable, which
promises every data class under ui/ is val-only. A `var` property or an in-place
mutated collection in one of those classes silently skips recomposition, so this
flags them. ViewModels, delegates and holders are not data classes and are not
checked.
"""

import re

PATHS = ["app/src/main/**/ui/**/*.kt"]

RULES = {
    "stability-var-in-data-class": (
        "`var` in a data class under ui/",
        "app/compose_stability_config.conf declares com.nendo.argosy.ui.** stable, so a "
        "mutable property here silently skips recomposition. Make it a `val` and replace "
        "the instance with copy(), or move the class out of ui/.",
    ),
    "stability-mutable-collection-in-data-class": (
        "mutable collection type in a data class under ui/",
        "A MutableList/Set/Map field can be mutated in place, which the stability promise "
        "for com.nendo.argosy.ui.** forbids. Use the read-only interface and replace it "
        "with copy().",
    ),
}

MODIFIER = r"(?:(?:public|internal|private|protected)\s+)*"
ANNOTATION = r"(?:@\w+(?:\([^)]*\))?\s+)*"

DATA_CLASS_RE = re.compile(r"^\s*" + ANNOTATION + MODIFIER + r"data\s+class\s+\w+")
VAR_RE = re.compile(r"(?:^|[(,])\s*" + ANNOTATION + MODIFIER + r"var\s+\w+")
MUTABLE_COLLECTION_RE = re.compile(r":\s*(?:kotlin\.collections\.)?Mutable(?:List|Set|Map|Collection)\s*<")


def _spans(lines):
    spans = []
    for index, line in enumerate(lines):
        if line is None or not DATA_CLASS_RE.match(line):
            continue
        depth = 0
        opened = False
        for cursor in range(index, len(lines)):
            text = lines[cursor]
            if text is None:
                break
            depth += text.count("(") - text.count(")")
            opened = opened or "(" in text
            if opened and depth <= 0:
                spans.append((index, cursor))
                break
    return spans


def stability_findings(lines, touched=None):
    findings = []
    for start, end in _spans(lines):
        for index in range(start, end + 1):
            line = lines[index]
            if line is None:
                continue
            if touched is not None and not touched(index):
                continue
            if VAR_RE.search(line):
                rule_id = "stability-var-in-data-class"
            elif MUTABLE_COLLECTION_RE.search(line):
                rule_id = "stability-mutable-collection-in-data-class"
            else:
                continue
            findings.append((rule_id, RULES[rule_id][1], line.strip()[:120], index))
    return findings
