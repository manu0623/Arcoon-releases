"""Stability contract for in-repo classes named one by one in
app/compose_stability_config.conf.

An entry there tells the Compose compiler to treat the class as stable without checking
it. That holds only while the class exposes fixed references: no public `var`, and no
public property holding a mutable collection or snapshot state. This flags member
declarations that break it in any listed class's file.
"""

import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
CONF = os.path.join(ROOT, "app", "compose_stability_config.conf")
SOURCE_ROOT = "app/src/main/kotlin"

ENTRY_RE = re.compile(r"^(com\.nendo\.argosy(?:\.\w+)+)\s*$")


def _listed_paths():
    try:
        with open(CONF) as f:
            entries = [m.group(1) for m in map(ENTRY_RE.match, f.read().splitlines()) if m]
    except OSError:
        return []
    paths = []
    for entry in entries:
        package, _, name = entry.rpartition(".")
        package_dir = "{}/{}".format(SOURCE_ROOT, package.replace(".", "/"))
        declaration = re.compile(r"\bclass\s+{}\b".format(re.escape(name)))
        try:
            files = sorted(f for f in os.listdir(os.path.join(ROOT, package_dir)) if f.endswith(".kt"))
        except OSError:
            continue
        for file in files:
            path = "{}/{}".format(package_dir, file)
            with open(os.path.join(ROOT, path), errors="replace") as f:
                if declaration.search(f.read()):
                    paths.append(path)
                    break
    return paths


PATHS = _listed_paths()

RULES = {
    "stability-public-var-in-listed-class": (
        "public `var` in a class listed in compose_stability_config.conf",
        "The conf file declares this class stable, so Compose skips recomposition when a "
        "mutable property on it changes. Expose the value as a StateFlow, make it private, "
        "or remove the class from the conf file.",
    ),
    "stability-public-mutable-state-in-listed-class": (
        "public mutable collection or snapshot state in a class listed in "
        "compose_stability_config.conf",
        "The conf file declares this class stable, so in-place changes to this property never "
        "reach composition. Expose it as a StateFlow or a read-only type, or remove the class "
        "from the conf file.",
    ),
}

MEMBER = r"^    (?:@\w+(?:\([^)]*\))?\s+)*(?:(?:public|internal|override|open|lateinit)\s+)*"
PUBLIC_VAR_RE = re.compile(MEMBER + r"var\s+\w+")
PUBLIC_MUTABLE_RE = re.compile(
    MEMBER + r"val\s+\w+\s*(?::\s*(?:kotlin\.collections\.)?Mutable(?:List|Set|Map|Collection|State)\b"
    r"|=\s*mutable(?:ListOf|SetOf|MapOf|StateOf|StateListOf|StateMapOf)\b)"
)


def findings(lines, touched=None):
    out = []
    for index, line in enumerate(lines):
        if line is None:
            continue
        if touched is not None and not touched(index):
            continue
        if PUBLIC_VAR_RE.match(line):
            rule_id = "stability-public-var-in-listed-class"
        elif PUBLIC_MUTABLE_RE.match(line):
            rule_id = "stability-public-mutable-state-in-listed-class"
        else:
            continue
        out.append((rule_id, RULES[rule_id][1], line.strip()[:120], index))
    return out
