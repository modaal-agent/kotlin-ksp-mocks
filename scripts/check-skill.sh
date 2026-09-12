#!/usr/bin/env bash
# Agent-skill checks — the tree under `skills/` and the two `.claude-plugin/`
# manifests that publish it.
#
# What it holds the skill to: frontmatter every install channel can parse, a
# body inside the documented budgets, and no claim about this repository that
# this repository does not back. K6, K7 and K8 are that last part — every
# generated member the skill names is one MockRenderer.kt emits, every
# diagnostic it quotes is one KspMocksProcessor.kt logs, and the wiring it
# teaches is the wiring receipt/build.gradle.kts builds. They compare sets of
# names, so rewording a sentence on either side leaves them green, and a rename
# in the processor reds the same push. That comparison is why the skill lives in
# this repository rather than in one of its own (specs/001-agent-skill, §8 D10).
#
# Markdown and JSON only, through grep, awk and python3 — no JDK, no Gradle, no
# network — so the `skill` job reports in seconds beside `rules`.
#
# Usage:
#   scripts/check-skill.sh                # check this checkout
#   scripts/check-skill.sh <root>         # check a tree elsewhere
#   scripts/check-skill.sh --self-test    # each check red against a seeded violation
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

if [ "${1:-}" = "--self-test" ]; then
  SELF_TEST=1
  ROOT="$REPO_DIR"
else
  SELF_TEST=0
  ROOT="${1:-$REPO_DIR}"
fi

SKILLS_DIR="$ROOT/skills"
MANIFEST_DIR="$ROOT/.claude-plugin"
RENDERER="$ROOT/mocks-processor/src/main/kotlin/dev/modaal/mocks/MockRenderer.kt"
PROCESSOR="$ROOT/mocks-processor/src/main/kotlin/dev/modaal/mocks/KspMocksProcessor.kt"
RECEIPT_BUILD="$ROOT/receipt/build.gradle.kts"
README="$ROOT/README.md"
PUBLISH_WORKFLOW="$ROOT/.github/workflows/publish.yml"
PUBLISH_SCRIPT="$ROOT/scripts/publish-maven.sh"
EVALS_DIR="$ROOT/evals"

# The Agent Skills standard's six keys. Claude Code accepts fourteen more; the
# Skills API and claude.ai reject every one of them by name, so a skill that has
# to stay uploadable carries none (§2.2, §8 D6).
STANDARD_KEYS="name description license compatibility metadata allowed-tools"

# The eval runner refuses an eval directory whose first segment is a component
# directory it already loads.
COMPONENT_DIRS="commands skills agents hooks themes output-styles monitors workflows"

SKILL_BODY_MAX=400
REFERENCE_MAX=250
DESCRIPTION_MAX=1024

# ── python helpers ───────────────────────────────────────────────
# Each is a function so its heredoc is parsed at statement level; a heredoc
# written inside $( … ) is scanned for the closing paren and misreads quotes.

py_k1() {
  python3 - "$1" <<'PY'
import pathlib, re, sys
for skill in sorted(pathlib.Path(sys.argv[1]).glob('*/SKILL.md')):
    lines = skill.read_text().split('\n')
    if not lines or lines[0] != '---':
        print(f"{skill.name} in {skill.parent.name}: line 1 is not ---"); continue
    try:
        end = lines.index('---', 1)
    except ValueError:
        print(f"{skill.parent.name}: no closing ---"); continue
    for n, line in enumerate(lines[1:end], start=2):
        if not line.strip():
            continue
        m = re.match(r'^(\s*)([A-Za-z0-9_-]+):(\s*)(.*)$', line)
        if not m:
            print(f"{skill.parent.name}:{n}: not a key: value line"); continue
        value = m.group(4)
        if value and value[0] not in '"\'' and ': ' in value:
            print(f"{skill.parent.name}:{n}: unquoted value carrying ': '")
PY
}

py_parsed() {
  python3 - "$1" <<'PY'
import pathlib, re, sys
for skill in sorted(pathlib.Path(sys.argv[1]).glob('*/SKILL.md')):
    lines = skill.read_text().split('\n')
    if not lines or lines[0] != '---' or '---' not in lines[1:]:
        continue
    parent = ''
    for line in lines[1:lines.index('---', 1)]:
        m = re.match(r'^(\s*)([A-Za-z0-9_-]+):(\s*)(.*)$', line)
        if not m:
            continue
        indent, key, value = len(m.group(1)), m.group(2), m.group(4)
        if indent == 0:
            parent = key
            print(f"{skill.parent.name}\t{key}\t{value}")
        else:
            print(f"{skill.parent.name}\t{parent}.{key}\t{value}")
PY
}

py_k6() {
  python3 - "$1" "$2" <<'PY'
import pathlib, re, sys
renderer = pathlib.Path(sys.argv[2]).read_text()
# Two shapes carry a member name in the renderer: a suffix after the
# placeholder (`${name}GetCount`), and the store's underscore prefix
# (`_${name}`), which both sides record as the bare key `_`.
emitted = set(re.findall(r'\$\{(?:fn|name|capitalized)\}([A-Za-z]+)', renderer))
emitted |= {'_' + s for s in re.findall(r'_\$\{(?:fn|name)\}([A-Za-z]*)', renderer)}
written = {}
for f in sorted(pathlib.Path(sys.argv[1]).rglob('*.md')):
    text = f.read_text()
    for m in re.finditer(r'<(?:fn|prop|name|Fn)>([A-Za-z]+)', text):
        written.setdefault(m.group(1), f.name)
    for m in re.finditer(r'_<(?:fn|prop|name)>([A-Za-z]*)', text):
        written.setdefault('_' + m.group(1), f.name)
for suffix, where in sorted(written.items()):
    if suffix not in emitted:
        shown = f"_<…>{suffix[1:]}" if suffix.startswith('_') else f"<…>{suffix}"
        print(f"{shown} in {where}")
PY
}

py_k7() {
  python3 - "$1" "$2" "$3" <<'PY'
import pathlib, re, sys

def normalise(text):
    # Both sides lose their placeholders the same way — a Kotlin interpolation
    # and a `<fqn>` alike become `*` — and a run of them collapses, so
    # `$fqn.${fn…}` and `<fqn>.<fn>` compare equal.
    text = re.sub(r'\$OPTION', 'kspMocksTargets', text)
    text = re.sub(r'\$\{[^}]*\}|\$[A-Za-z_][A-Za-z0-9_.]*', '*', text)
    text = re.sub(r'<(?:fqn|fn|prop)>', '*', text)
    text = re.sub(r'\*[.*]*', '*', text)
    return re.sub(r'\s+', ' ', text).strip()

source = ''
for path in sys.argv[2:]:
    source += pathlib.Path(path).read_text()
logged = set()
for literal in re.findall(r'"((?:[^"\\]|\\.)*)"', source):
    if 'OPTION:' in literal or 'expected to be set' in literal:
        logged.add(normalise(literal))

quoted = {}
for f in sorted(pathlib.Path(sys.argv[1]).rglob('*.md')):
    for m in re.finditer(r'(kspMocksTargets: [^\x60|\n]+|<fn>Handler expected to be set\.)', f.read_text()):
        quoted.setdefault(normalise(m.group(1)), f.name)

# Containment, not equality: a Kotlin literal carries the call around it —
# `error(\"${fn}Handler expected to be set.\")` is one string in the source.
for text, where in sorted(quoted.items()):
    if not any(text in logged_text for logged_text in logged):
        print(f"{where}: {text}")
PY
}

py_k9() {
  python3 - "$1" <<'PY'
import pathlib, re, sys
root = pathlib.Path(sys.argv[1])
for f in sorted(root.rglob('*.md')):
    for m in re.finditer(r'\]\((?!https?://|#)([^)#]+)', f.read_text()):
        if not (f.parent / m.group(1)).exists():
            print(f"{f.relative_to(root)} → {m.group(1)}")
PY
}

py_k12() {
  python3 - "$1" "$2" <<'PY'
import json, pathlib, sys
manifests, root = pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2])
try:
    marketplace = json.loads((manifests / 'marketplace.json').read_text())
    plugin = json.loads((manifests / 'plugin.json').read_text())
except Exception as error:
    print(f"a manifest does not parse: {error}"); raise SystemExit
name = plugin.get('name')
entries = [p for p in marketplace.get('plugins', []) if p.get('name') == name]
if not entries:
    listed = ', '.join(p.get('name', '?') for p in marketplace.get('plugins', []))
    print(f"plugin.json is named {name!r}; the marketplace lists {listed!r}")
    raise SystemExit
source = entries[0].get('source', './')
if not isinstance(source, str):
    print(f"the marketplace entry's source is not a path: {source!r}"); raise SystemExit
skill = (root / source / 'skills' / name / 'SKILL.md').resolve()
if not skill.exists():
    print(f"source {source!r} does not resolve to a directory holding skills/{name}/SKILL.md")
PY
}

py_k13() {
  python3 - "$1" "$2" <<'PY'
import pathlib, re, sys
evals = pathlib.Path(sys.argv[1])
if evals.name in sys.argv[2].split():
    print(f"the eval directory {evals.name!r} is a loaded component directory")
types = {'regex', 'tool_order', 'tool_used', 'file_exists', 'llm', 'baseline'}
cases = sorted(evals.glob('*/prompt.md'))
if not cases:
    print("no case holds a prompt.md")
for prompt in cases:
    case = prompt.parent
    text = prompt.read_text()
    body = re.sub(r'^---\n.*?\n---\n', '', text, flags=re.S)
    if not body.strip():
        print(f"{case.name}: prompt.md carries no body")
    graders = sorted((case / 'graders').glob('*.md')) if (case / 'graders').is_dir() else []
    if not graders:
        print(f"{case.name}: no graders/*.md")
    for grader in graders:
        m = re.search(r'^type:\s*(\S+)\s*$', grader.read_text(), flags=re.M)
        if not m:
            print(f"{case.name}/{grader.name}: no type: in the frontmatter")
        elif m.group(1) not in types:
            print(f"{case.name}/{grader.name}: type {m.group(1)!r} is not one the runner accepts")
PY
}

py_k14() {
  python3 - "$1" <<'PY'
import pathlib, re, sys
skills = pathlib.Path(sys.argv[1])

def interpolations_to_star(source):
    # `${…}` may nest braces and quotes — `${shapes.joinToString(" or ") { … }}` — so it is
    # scanned for its closing brace rather than matched, before the string literals are read.
    out, i = [], 0
    while i < len(source):
        if source.startswith('${', i):
            depth, j = 1, i + 2
            while j < len(source) and depth:
                depth += {'{': 1, '}': -1}.get(source[j], 0)
                j += 1
            out.append('*')
            i = j
        else:
            out.append(source[i])
            i += 1
    return re.sub(r'\$[A-Za-z_][A-Za-z0-9_]*', '*', ''.join(out))

def normalise(text):
    # `:<module>` and `$path` alike become `*`.
    text = re.sub(r':?<[^>]+>', '*', text)
    text = re.sub(r'\*[.*]*', '*', text)
    return re.sub(r'\s+', ' ', text).strip()

for skill in sorted(skills.glob('*/SKILL.md')):
    root = skill.parent
    sources = {p.name: p.read_text() for p in sorted((root / 'scripts').glob('*.init.gradle.kts'))}
    code = '\n'.join(sources.values())
    tasks = set(re.findall(r'register\("([A-Za-z]+)"', code))
    properties = set(re.findall(r'gradleProperty\("([A-Za-z]+)"\)', code))
    logged = {normalise(l) for l in re.findall(r'"((?:[^"\\]|\\.)*)"', interpolations_to_star(code))
              if l.startswith('printMockApi: ')}
    for f in sorted(root.rglob('*.md')):
        text = f.read_text().replace('\\\n', ' ')
        for m in re.finditer(r'scripts/[A-Za-z0-9._-]+', text):
            if not (root / m.group(0)).exists():
                print(f"{f.name}: {m.group(0)} is not in {root.name}/")
        for line in text.splitlines():
            if '-I ' not in line:
                continue
            for task in re.findall(r':<module>:([A-Za-z]+)', line):
                if task not in tasks:
                    print(f"{f.name}: no script registers the task {task}")
        for prop in sorted(set(re.findall(r'-P([A-Za-z]+)=', text))):
            if prop not in properties:
                print(f"{f.name}: no script reads the property {prop}")
        for m in re.finditer(r'printMockApi: [^\x60|\n]+', text):
            quoted = normalise(m.group(0))
            if not any(quoted in l for l in logged):
                print(f"{f.name}: {quoted}")
PY
}

FAILED=0
fail() {
  printf '✘ %s — %s\n' "$1" "$2" >&2
  FAILED=1
}
ok() { printf '✔ %s — %s\n' "$1" "$2"; }

# ── the checks ───────────────────────────────────────────────────

run_checks() {
  [ -d "$SKILLS_DIR" ] || { fail K0 "no skills/ directory under $ROOT"; return; }

  # ── K1: the frontmatter every channel can parse ────────────────
  # `---` on line 1, one `key: value` per line, a closing `---`, and no
  # unquoted value carrying `: `. The last rule is not pedantry: the cross-agent
  # `skills` CLI parses this block with a strict YAML parser and refuses the
  # file with "Nested mappings are not allowed in compact mappings", while
  # Claude Code's own loader accepts it (§2.3).
  local k1
  k1="$(py_k1 "$SKILLS_DIR")"
  if [ -n "$k1" ]; then
    fail K1 "the frontmatter does not parse for every loader:
$k1"
  else
    ok K1 "frontmatter opens at line 1 and parses"
  fi

  # Parse each skill's top-level frontmatter into <dir>\t<key>\t<value> for the
  # checks below. Nested keys are recorded under their parent as `parent.key`.
  local parsed
  parsed="$(py_parsed "$SKILLS_DIR")"

  # ── K2: `name:` is the directory, and is not `synced` ───────────
  # A skill is invoked by its directory name, and `synced` is reserved under
  # ~/.claude/skills/.
  local k2=""
  while IFS= read -r dir; do
    [ -n "$dir" ] || continue
    local name
    name="$(printf '%s\n' "$parsed" | awk -F'\t' -v d="$dir" '$1==d && $2=="name" {print $3}')"
    [ "$name" = "$dir" ] || k2="$k2 $dir(name=$name)"
    [ "$dir" != "synced" ] || k2="$k2 $dir(reserved)"
  done <<< "$(printf '%s\n' "$parsed" | awk -F'\t' '{print $1}' | sort -u)"
  if [ -n "$k2" ]; then
    fail K2 "a skill is invoked by its directory name, so the two cannot differ:$k2"
  else
    ok K2 "name: equals the containing directory"
  fi

  # ── K3: only the Agent Skills standard's keys ──────────────────
  local k3=""
  while IFS=$'\t' read -r dir key _; do
    [ -n "$key" ] || continue
    case "$key" in *.*) continue ;; esac
    case " $STANDARD_KEYS " in
      *" $key "*) ;;
      *) k3="$k3 $dir/$key" ;;
    esac
  done <<< "$parsed"
  if [ -n "$k3" ]; then
    fail K3 "packaging for the Skills API rejects a Claude Code extension key by name:$k3"
  else
    ok K3 "frontmatter carries only the six standard keys"
  fi

  # ── K4: the description is present and within budget ───────────
  # It is resident in every session, used or not.
  local k4=""
  while IFS= read -r dir; do
    [ -n "$dir" ] || continue
    local description length
    description="$(printf '%s\n' "$parsed" | awk -F'\t' -v d="$dir" '$1==d && $2=="description" {print $3}')"
    length=${#description}
    if [ "$length" -eq 0 ]; then
      k4="$k4 $dir(empty)"
    elif [ "$length" -gt "$DESCRIPTION_MAX" ]; then
      k4="$k4 $dir($length>$DESCRIPTION_MAX)"
    fi
  done <<< "$(printf '%s\n' "$parsed" | awk -F'\t' '{print $1}' | sort -u)"
  if [ -n "$k4" ]; then
    fail K4 "the description is what auto-invocation is decided from:$k4"
  else
    ok K4 "description present and within $DESCRIPTION_MAX characters"
  fi

  # ── K5: the body and each reference within budget ──────────────
  # The body stays in context across every turn after the skill is invoked; a
  # reference costs nothing until the agent opens it.
  local k5=""
  while IFS= read -r file; do
    [ -n "$file" ] || continue
    local lines limit
    lines="$(wc -l < "$file" | tr -d ' ')"
    case "$file" in
      */SKILL.md) limit=$SKILL_BODY_MAX ;;
      *) limit=$REFERENCE_MAX ;;
    esac
    [ "$lines" -le "$limit" ] || k5="$k5 ${file#$ROOT/}($lines>$limit)"
  done <<< "$(find "$SKILLS_DIR" -name '*.md' | sort)"
  if [ -n "$k5" ]; then
    fail K5 "over budget:$k5"
  else
    ok K5 "SKILL.md ≤ $SKILL_BODY_MAX lines, each reference ≤ $REFERENCE_MAX"
  fi

  # ── K6: every member the skill names is one the renderer emits ─
  # The renderer builds member names by interpolation — `${fn}CallCount`,
  # `${name}GetCount`, `${capitalized}Args`, `_${name}` — so the emitted set is
  # read out of those, and the skill's `<fn>X` / `<prop>X` / `<name>X` / `<Fn>X`
  # spellings, `_<prop>` included, are compared against it. `<method>` and
  # `<var>` are the Swift twin's placeholders and are deliberately not compared
  # here.
  local k6
  k6="$(py_k6 "$SKILLS_DIR" "$RENDERER")"
  if [ -n "$k6" ]; then
    fail K6 "the skill names a member ${RENDERER#$ROOT/} does not emit:
$k6"
  else
    ok K6 "every member the skill names is emitted by the renderer"
  fi

  # ── K7: every diagnostic the skill quotes is one the code logs ─
  # Both sides are normalised the same way: the interpolations in the Kotlin
  # string literals and the `<fqn>` / `<fn>` / `<prop>` placeholders in the
  # skill both become `*`, so rewording either side without the other reds this.
  local k7
  k7="$(py_k7 "$SKILLS_DIR" "$PROCESSOR" "$RENDERER")"
  if [ -n "$k7" ]; then
    fail K7 "the skill quotes a diagnostic the processor does not log:
$k7"
  else
    ok K7 "every diagnostic the skill quotes is logged by the processor"
  fi

  # ── K8: the wiring the skill teaches is the wiring built here ──
  # Three tokens, compared one at a time: receipt/build.gradle.kts writes the
  # option through a multi-line `arg(` call, so `kspMocksTargets` is compared on
  # its own rather than as part of a call expression.
  # Comments are stripped first: a token named in a comment is not wiring the
  # build runs, and :receipt's header comment names two of these three.
  local k8="" receipt_code
  receipt_code="$(sed 's|//.*||' "$RECEIPT_BUILD")"
  for token in 'kspTest' 'ksp {' 'kspMocksTargets'; do
    if grep -rqF "$token" "$SKILLS_DIR" && ! printf '%s\n' "$receipt_code" | grep -qF "$token"; then
      k8="$k8 [$token]"
    fi
  done
  if [ -n "$k8" ]; then
    fail K8 "the skill teaches wiring ${RECEIPT_BUILD#$ROOT/} does not build:$k8"
  else
    ok K8 "the wiring tokens the skill writes are the ones :receipt builds"
  fi

  # ── K9: every relative link resolves ───────────────────────────
  local k9
  k9="$(py_k9 "$SKILLS_DIR")"
  if [ -n "$k9" ]; then
    fail K9 "a link in the skill tree points at a file that does not exist:
$k9"
  else
    ok K9 "every relative link resolves"
  fi

  # ── K10: no version literal anywhere in the skill tree ─────────
  # A pinned version is wrong the day after the next tag, and no check in the
  # adopter's repository reads it. The snippets carry a placeholder and the
  # instruction to read the host's maven-metadata.xml (§8 D7).
  local k10
  k10="$(grep -rnE '[0-9]+\.[0-9]+\.[0-9]+' "$SKILLS_DIR" || true)"
  if [ -n "$k10" ]; then
    fail K10 "a version literal was written into the skill tree:
$k10"
  else
    ok K10 "no version literal in the skill tree"
  fi

  # ── K11: one spelling of the Maven host ────────────────────────
  # The skill, the README, the publish workflow and the publish script all name
  # the host that serves the artifact. A move that edits one of them reds this.
  local k11 distinct
  k11="$(
    {
      grep -rhoE 'https://[A-Za-z0-9.-]+/maven' "$SKILLS_DIR" || true
      for f in "$README" "$PUBLISH_WORKFLOW" "$PUBLISH_SCRIPT"; do
        [ -f "$f" ] && { grep -hoE 'https://[A-Za-z0-9.-]+/maven' "$f" || true; }
      done
    } | sort -u
  )"
  distinct="$(printf '%s\n' "$k11" | grep -c . || true)"
  if [ "$distinct" -ne 1 ]; then
    fail K11 "the Maven host is spelled $distinct ways:
$k11"
  else
    ok K11 "one Maven host URL across the skill, README and the publish path"
  fi

  # ── K12: the two manifests agree with each other and the tree ──
  local k12
  k12="$(py_k12 "$MANIFEST_DIR" "$ROOT")"
  if [ -n "$k12" ]; then
    fail K12 "the plugin root and the skill tree came apart:
$k12"
  else
    ok K12 "both manifests parse and name the same plugin"
  fi

  # ── K13: every eval case is one the runner would read ──────────
  # A parse check, not a run: the suite costs model calls and no CI job runs it.
  if [ ! -d "$EVALS_DIR" ]; then
    ok K13 "no evals/ directory yet (phase 4)"
  else
    local k13
    k13="$(py_k13 "$EVALS_DIR" "$COMPONENT_DIRS")"
    if [ -n "$k13" ]; then
      fail K13 "an eval case the runner would refuse:
$k13"
    else
      ok K13 "every eval case carries a prompt body and a usable grader"
    fi
  fi

  # ── K14: the init script the skill runs is the one it ships ────
  # Every `scripts/…` path the skill names exists in the skill directory, every task a `-I` command
  # runs is one a script registers, every `-P` property is one a script reads, and every
  # `printMockApi: …` failure line quoted is one a script throws — normalised as K7 normalises.
  # scripts/check-print-mock-api.sh runs the script; this compares names only, with no JDK.
  local k14
  k14="$(py_k14 "$SKILLS_DIR")"
  if [ -n "$k14" ]; then
    fail K14 "the skill names a script, task, property or failure line its scripts/ do not carry:
$k14"
  else
    ok K14 "every script, task, property and failure line the skill names is in its scripts/"
  fi
}

# ── the self-test ────────────────────────────────────────────────
# Each check is run against a tree seeded with exactly one violation of it. A
# check that has never gone red is a check that has not been run.

seed() {
  local target="$1"
  mkdir -p "$target/mocks-processor/src/main/kotlin/dev/modaal/mocks" \
           "$target/receipt" "$target/.github/workflows" "$target/scripts"
  cp -R "$REPO_DIR/skills" "$REPO_DIR/.claude-plugin" "$target/"
  cp "$RENDERER" "$PROCESSOR" "$target/mocks-processor/src/main/kotlin/dev/modaal/mocks/"
  cp "$RECEIPT_BUILD" "$target/receipt/"
  cp "$README" "$target/"
  cp "$PUBLISH_WORKFLOW" "$target/.github/workflows/"
  cp "$PUBLISH_SCRIPT" "$target/scripts/"
  [ -d "$EVALS_DIR" ] && cp -R "$EVALS_DIR" "$target/"
  return 0
}

expect_red() {
  local check="$1" root="$2" seed_case="${3:-$1}" output status
  set +e
  output="$("$SCRIPT_DIR/$(basename "${BASH_SOURCE[0]}")" "$root" 2>&1)"
  status=$?
  set -e
  if [ "$status" -eq 0 ]; then
    printf '✘ %s stayed green against its seeded violation\n' "$check" >&2
    return 1
  fi
  if ! printf '%s\n' "$output" | grep -q "✘ $check "; then
    printf '✘ %s did not report; the run said:\n%s\n' "$check" "$output" >&2
    return 1
  fi
  if [ "$seed_case" = "$check" ]; then
    printf '✔ %s red against its seeded violation\n' "$check"
  else
    printf '✔ %s red against its seeded violation: %s\n' "$check" "$seed_case"
  fi
}

WORK=""
self_test() {
  local skill red=0
  WORK="$(mktemp -d)"
  # The trap runs after the function returns, so the directory it removes
  # cannot be a local.
  trap 'rm -rf "$WORK"' EXIT

  for seed_case in K1 K2 K3 K4 K5 K6 K6_store K7 K8 K9 K10 K11 K12 K13 K14 K14_failure; do
    local root="$WORK/$seed_case" check="${seed_case%%_*}"
    mkdir -p "$root"
    seed "$root"
    skill="$root/skills/kotlin-ksp-mocks/SKILL.md"
    case "$seed_case" in
      K1)  perl -0pi -e 's/^license:.*$/license: MIT: the file is unparseable now/m' "$skill" ;;
      K2)  perl -0pi -e 's/^name: .*$/name: kotlin-ksp-mock/m' "$skill" ;;
      K3)  perl -0pi -e 's/^license:/when_to_use: whenever\nlicense:/m' "$skill" ;;
      K4)  perl -0pi -e 's/^description: .*$/description:/m' "$skill" ;;
      K5)  for _ in $(seq 1 "$SKILL_BODY_MAX"); do echo "padding" >> "$skill"; done ;;
      K6)  echo 'The mock also carries `<fn>CallCounter`.' >> "$skill" ;;
      K6_store) echo 'The store is spelled `_<prop>Value`.' >> "$skill" ;;
      K7)  echo 'It fails with `kspMocksTargets: <fqn> is not resolvable in this build`.' >> "$skill" ;;
      K8)  perl -0pi -e 's/kspTest\(project/removed(project/' "$root/receipt/build.gradle.kts" ;;
      K9)  echo 'See [references/missing.md](references/missing.md).' >> "$skill" ;;
      K10) echo 'Use version 0.2.1 of the processor.' >> "$skill" ;;
      K11) perl -0pi -e 's|https://modaal-agent\.github\.io/maven|https://example.github.io/maven|' "$skill" ;;
      K12) perl -0pi -e 's/"name": "kotlin-ksp-mocks"/"name": "ksp-mocks"/' "$root/.claude-plugin/plugin.json" ;;
      K13) mkdir -p "$root/evals/seeded-case" && echo "A prompt with no grader." > "$root/evals/seeded-case/prompt.md" ;;
      K14) echo 'Run `./gradlew -I "${CLAUDE_SKILL_DIR}/scripts/print-mock-api.init.gradle.kts" :<module>:printMockMembers -q`.' >> "$skill" ;;
      K14_failure) echo 'It fails with `printMockApi: :<module> has no processor`.' >> "$skill" ;;
    esac
    expect_red "$check" "$root" "$seed_case" || red=1
  done

  if [ "$red" -ne 0 ]; then
    printf '\n✘ self-test: a check did not go red\n' >&2
    exit 1
  fi
  printf '\n✔ self-test: every check red against its seeded violation\n'
}

if [ "$SELF_TEST" -eq 1 ]; then
  self_test
  printf '\nnow the checkout itself:\n'
fi

run_checks
if [ "$FAILED" -ne 0 ]; then
  printf '\n✘ skill checks failed\n' >&2
  exit 1
fi
printf '\n✔ skill checks passed\n'
