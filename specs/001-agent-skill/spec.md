# 001 — An agent skill that teaches an adopter's agent to wire and use this processor

**Status:** Written 2026-09-10, not implemented. **Baseline:** `main` at `f8d27f8`. **Obsoletes:**
nothing.

**Relates to:**

- [README.md](../../README.md) — §Wiring (`:10-36`), §"Generated API" (`:38-79`), §"Not supported"
  (`:80-84`). The skill tells an adopter's agent what to write; it does not restate why the
  selection mechanism is a build-script list.
- [CONTRIBUTING.md](../../CONTRIBUTING.md) §"The mock dialect is a contract" (`:12-25`) and rule 3,
  test-classpath only (`:32-36`).
- [AGENTS.md](../../AGENTS.md) §"State a rule once" — the member vocabulary is stated in
  `MockRenderer.kt` and pinned by `MockRendererTest.kt`. §5 of this spec is that rule applied to a
  document that ships outside this repository.
- [swift-sourcery-templates](https://github.com/modaal-agent/swift-sourcery-templates) spec
  `002-annotation-registry-and-agent-skill` §3–§6 (the skill, the four channels, the checks), §12
  (what phases B1–B3 measured) and §13 (the eval suite). The twin repository shipped this pattern on
  2026-09-10; this spec is its translation to Kotlin and KSP, and cites its measurements where they
  are properties of the loaders rather than of that repository.

Every repository fact in §1 was read from this checkout on 2026-09-10. Every fact about the skill
loaders in §2 was either read from the Claude Code CLI at 2.1.267 on that date, or is cited to the
twin repository's spec by section.

---

## 0. TL;DR

1. **An adopter's agent has README.md and nothing else, and README's wiring block does not resolve.**
   `README.md:19` writes `kspTest("dev.modaal:mocks-processor:<version>")`, and no document in this
   repository names the Maven host that serves it — `https://modaal-agent.github.io/maven` appears
   only in `.github/workflows/publish.yml:3` and `scripts/publish-maven.sh:4`. A build that copies
   README's block fails at dependency resolution (§1.2).
2. **The skill is one directory, `skills/kotlin-ksp-mocks/`**, holding `SKILL.md` and three
   `references/*.md`, reachable through four install channels over one tree (§3).
3. **What it teaches:** which KSP configuration a module shape needs, the three edits that wire the
   processor, the generated member vocabulary, how to seed and assert, and twelve failure modes with
   the exact diagnostic text (§4).
4. **What gates it:** `scripts/check-skill.sh`, thirteen checks, run by a new `skill` job on
   `ubuntu-latest` beside `rules` — markdown and JSON only, no JDK (§5). Three of the thirteen
   compare the skill's text against `MockRenderer.kt`, `KspMocksProcessor.kt` and
   `receipt/build.gradle.kts`, which is why the skill lives in this repository (§8, D10).
5. **What measures it:** six eval cases under `evals/`, written for `claude plugin eval`, run by hand
   with `claude -p` until the runner leaves early access — measured today: it answers "`plugin eval`
   is currently in early access" at 2.1.267 (§6).
6. **Five phases**, the first of which writes no skill: phase 0 measures the Kotlin Multiplatform,
   Android and `testFixtures` wiring in scratch projects, because this repository builds one shape
   only (§7).

---

## 1. Current state (verified 2026-09-10)

### 1.1 What an adopter's agent has to read to set up generation today

`README.md`, 101 lines, is the whole adopter-facing surface. It carries the wiring block
(`:10-36`), the generated API (`:38-79`), the two unsupported constructs (`:80-84`) and the
class-file-major note (`:92-97`). `CONTRIBUTING.md` and `AGENTS.md` address someone editing the
processor.

The hypothesis the without-skill arm of §6.4 tests is that an agent which has not read this
repository reaches for MockK or Mockito.

### 1.2 README's wiring block cannot resolve the artifact

`README.md:19` declares the dependency and `README.md:87-90` says the artifact is "published to a
static Maven host". The host URL appears in two files, both of them part of publishing:

- `.github/workflows/publish.yml:3` — `https://modaal-agent.github.io/maven`
- `scripts/publish-maven.sh:4` — the same URL

`grep -n 'modaal-agent.github.io' README.md` returns nothing. Copying README's block into a consumer
build produces `Could not find dev.modaal:mocks-processor`, because the coordinate is on neither
Maven Central nor Google's repository. The host serves anonymously: at 2026-09-10
`https://modaal-agent.github.io/maven/dev/modaal/mocks-processor/maven-metadata.xml` returns 200 and
lists `0.1.0`, `0.2.0`, `0.2.1` with `<release>0.2.1</release>`.

Two files therefore need the repository declaration: the skill, because an agent writing a consumer
build needs it inline, and `README.md`, for the reader who never installs the skill (§8, D9).

### 1.3 There is no skills tree and no plugin manifest

`ls skills .claude-plugin` returns two "No such file or directory". `.gitignore:11` ignores
`.claude/`; the pattern names that directory exactly, so a `.claude-plugin/` directory at the root is
tracked without a `.gitignore` edit.

### 1.4 Where each fact the skill states is stated today

| fact | file:line |
| --- | --- |
| the option name `kspMocksTargets` | `KspMocksProcessor.kt:194` |
| `<fn>CallCount`, `<fn>Args`, `<fn>Handler`, `<fn>Channel` | `MockRenderer.kt:169-201` |
| `<prop>GetCount`, `<prop>GetHandler`, `<prop>Channel` | `MockRenderer.kt:114-121` |
| `<prop>SetCount` | `MockRenderer.kt:134-137` |
| the nested `<Fn>Args` data class for two or more recordable parameters | `MockRenderer.kt:161-175` |
| `"<fn>Handler expected to be set."` | `MockRenderer.kt:186`, asserted at `MockRendererTest.kt:104` |
| the unset-handler fallbacks — Unit, nullable, guessable default, `Flow` | `MockRenderer.kt:178-186`, defaults at `KspMocksProcessor.kt:175-191` |
| `<fqn> is not resolvable in this compilation` | `KspMocksProcessor.kt:58` |
| `<fqn> is not an interface` | `KspMocksProcessor.kt:62` |
| `declares type parameters — generic interfaces are not supported` | `KspMocksProcessor.kt:66` |
| `has a vararg parameter — not supported` | `KspMocksProcessor.kt:117-118` |
| overload disambiguation by appending capitalized parameter names | `MockRenderer.kt:83-104` |
| the constructor-seeded bag for non-defaultable stored properties | `MockRenderer.kt:59-67` |
| the wiring a consumer copies — `kspTest`, `ksp { arg(...) }` | `receipt/build.gradle.kts:21-31` |
| the generated-source path | `receipt/build/generated/ksp/test/kotlin/…`, `CONTRIBUTING.md:58` |
| the Maven host URL | `.github/workflows/publish.yml:3`, `scripts/publish-maven.sh:4` |

The skill restates every row of this table for a reader who does not have the checkout. Checks K6,
K7, K8 and K11 (§5.1) compare what it restates against these files.

### 1.5 The one wiring shape this repository builds

`receipt/build.gradle.kts` is a Kotlin/JVM module using `kspTest` (`:21`) and
`ksp { arg("kspMocksTargets", …) }` (`:24-31`). Nothing here builds a Kotlin Multiplatform module, an
Android module, or a `testFixtures` source set, so the configuration names those shapes need
(`kspJvmTest` at `README.md:19`, and whatever Android and `testFixtures` require) are unmeasured in
this repository. Phase 0 measures them (§7).

### 1.6 CI today

`.github/workflows/ci.yml` runs two jobs on `pull_request` and on push to `main`:

- `rules` — `ubuntu-latest`, `cmp AGENTS.md CLAUDE.md`, no JDK, no Gradle (`:14-23`).
- `build` — `ubuntu-latest`, JDK 25, `./gradlew build` (`:25-49`).

There is no change filter: every push runs both. The `skill` job of §5.2 follows `rules` — same
runner, no toolchain, seconds.

---

## 2. What an agent skill is

### 2.1 The file, the frontmatter, the load path

A skill is a directory holding `SKILL.md`: YAML frontmatter between `---` markers, then markdown. The
opening `---` must be the file's first line; otherwise the file is loaded as content and the
frontmatter is never parsed.

Three load stages and the budget each spends (twin spec 002 §3.1, read from
`code.claude.com/docs/en/skills` on 2026-09-09):

1. **`description` — resident.** In context every turn whether the skill is used or not, and the only
   text auto-invocation is decided from. Capped at 1,536 characters combined with `when_to_use`.
2. **The body — loaded on invocation, and it stays** across turns. Documented guidance: under 500
   lines. Under auto-compaction the first 5,000 tokens of each loaded skill survive, within a
   25,000-token budget across all of them.
3. **Linked files — loaded when the body sends the agent to them.** A `[references/x.md](…)` link
   costs nothing until opened.

### 2.2 Frontmatter keys, and which ones survive an upload

Claude Code accepts 20 keys. Six are the Agent Skills standard — `name`, `description`, `license`,
`compatibility`, `metadata`, `allowed-tools`. Packaging for the Skills API or claude.ai rejects each
extension key by name with a hard error. A skill that has to stay uploadable carries the six only,
which costs `when_to_use` and moves its trigger phrases into `description` (§8, D6).

### 2.3 Two loaders parse the frontmatter differently

Measured in the twin repository, spec 002 §12.2: `npx skills add` refused a `SKILL.md` whose
description carried `: ` inside a plain YAML scalar — *"Nested mappings are not allowed in compact
mappings"* — while Claude Code's own loader accepted the same file. Check K1 refuses the shape
(§5.1).

### 2.4 What the loaders accept, measured in the twin repository

Both facts are properties of the loaders, not of that repository, and phase 1's gate re-runs them
against this tree:

- A plugin whose `source` is `"./"`, rooted at the marketplace root, loads; its skill resolves from
  the plugin root's own `skills/` (spec 002 §12.2, §10.1).
- `npx skills add` finds a `SKILL.md` at any depth, so `skills/` at the root is the plugin loader's
  requirement rather than the cross-agent CLI's (spec 002 §12.2, §10.2).

### 2.5 The eval runner

`claude plugin eval --help` at 2.1.267 documents the case layout (`<eval dir>/**/case.yaml`, or
`prompt.md` plus `graders/*.md`), the default eval directory `evals/` unless the manifest's
`experimental.evals` says otherwise, and `--ablation with-without`, under which graders marked
`with-only` — including `tool_used: Skill` — are a plugin-fired indicator rather than part of the
score. Run on this account against an empty directory on 2026-09-10, it prints "`plugin eval` is
currently in early access" and runs nothing. §6.3 is the form phase 4 uses instead.

---

## 3. Distribution: four channels over one `skills/` tree

| # | channel | what the adopter runs |
| --- | --- | --- |
| 1 | the cross-agent `skills` CLI | `npx skills add modaal-agent/kotlin-ksp-mocks` |
| 2 | the Claude Code plugin marketplace | `/plugin marketplace add modaal-agent/kotlin-ksp-mocks`, then `/plugin install kotlin-ksp-mocks@kotlin-ksp-mocks` |
| 3 | manual copy | `git clone …`, then `cp -r kotlin-ksp-mocks/skills/* ~/.claude/skills/` |
| 4 | claude.ai / the Skills API | upload the directory; nothing to edit, because of §2.2 |

Channels 1, 3 and 4 read `skills/<name>/` at the repository root. Channel 2 reads `skills/` inside
the plugin root and cannot be pointed above it, so the plugin root is the repository root:

```
.claude-plugin/
  marketplace.json     name: kotlin-ksp-mocks; plugins: [{ name: kotlin-ksp-mocks, source: "./" }]
  plugin.json          name: kotlin-ksp-mocks
skills/
  kotlin-ksp-mocks/
    SKILL.md
    references/gradle-wiring.md
    references/generated-api.md
    references/troubleshooting.md
evals/
  <case>/prompt.md
  <case>/graders/*.md
```

The marketplace name and the plugin name are the same string here, which the twin repository's pair
(`swift-sourcery-templates` and `swift-sourcery-mocks`) does not exercise. Phase 1 verifies it and
§10.1 names the fallback.

---

## 4. What the skill teaches

### 4.1 The first decision: which configuration the module needs

There is one generation mode — the test compilation, nothing committed — so the decision an adopter's
agent makes first is which module and which KSP configuration to wire, not whether to generate at
build time.

| the module in front of the agent | what to write |
| --- | --- |
| a Kotlin/JVM Gradle module whose own tests need the mocks | `kspTest(…)` in that module, `ksp { arg("kspMocksTargets", …) }` beside it — the shape `receipt/build.gradle.kts:21-31` builds |
| a Kotlin Multiplatform module | `kspJvmTest(…)` for the JVM test target (`README.md:19`); the exact configuration set and whether a common test source set can see the generated mock are phase 0's measurement |
| an Android module | phase 0's measurement; the candidate names are `kspTest` and the per-variant `kspTestDebugUnitTest` |
| interfaces in module `:core`, tests in module `:feature` | wire KSP in `:feature` and list the `:core` FQNs there; `:core` must be on `:feature`'s test compile classpath, which `implementation(project(":core"))` already gives |
| mocks shared by several modules' tests | phase 0's measurement of `kspTestFixtures`; until it is measured the skill says to wire each module that needs them |

The last four rows are written from phase 0's measurements and from nothing else (§7). Phase 0
runs each shape in a scratch project and records the configuration name the build accepted.

### 4.2 The three edits the body walks through

1. The repository declaration — `maven { url = uri("https://modaal-agent.github.io/maven") }` in
   `settings.gradle.kts`'s `dependencyResolutionManagement`, without which resolution fails (§1.2).
2. The KSP plugin on the consuming module, plus `kspTest("dev.modaal:mocks-processor:<version>")`.
   The version is resolved from the host's `maven-metadata.xml`, never written as a literal (§8, D7).
3. `ksp { arg("kspMocksTargets", "com.example.A,com.example.B") }` — comma-separated
   fully-qualified interface names.

### 4.3 `SKILL.md` — the resident part

```yaml
---
name: kotlin-ksp-mocks
description: <one paragraph, <=1024 characters>
license: MIT
metadata:
  repository: https://github.com/modaal-agent/kotlin-ksp-mocks
---
```

`license: MIT` matches [LICENSE](../../LICENSE). The `description` budget is 1,024 characters against
the 1,536 cap. It names what the processor generates and carries the trigger phrases: generate mocks
for a Kotlin interface, mock an interface for tests, replace a hand-written fake, `kspTest`,
`kspJvmTest`, `kspMocksTargets`, `<Interface>Mock`, `Handler expected to be set.`, a test that hangs
collecting a mock's `Flow`, and `is not resolvable in this compilation`.

Body, budgeted at **under 400 lines** against the documented 500, and under the 5,000-token
compaction floor of §2.1 — the twin repository's body measured ~5k tokens at 249 lines
(spec 002 §12.2), so the line budget here is the binding one:

| section | what it holds | budget |
| --- | --- | --- |
| what it generates | test-compilation only, no main-classpath artifact, nothing committed | ~15 lines |
| the module table | §4.1 | ~20 lines |
| the three edits | §4.2, as one complete `settings.gradle.kts` + `build.gradle.kts` pair | ~55 lines |
| what the generated mock gives a test | the member table, one seeded example, the unset-handler fallbacks | ~70 lines |
| streams | the channel: `trySend`, then `close` to end the stream | ~25 lines |
| shaping an interface | generics and `vararg` refused; function-typed parameters stay out of `<fn>Args`; a pure-property interface becomes a constructor bag | ~35 lines |
| failure modes | the table of §4.5 | ~50 lines |
| references | one line each | ~8 lines |

### 4.4 `references/` — loaded when the body sends the agent there

| file | what it holds | budget |
| --- | --- | --- |
| `references/gradle-wiring.md` | every module shape phase 0 measured; the repository declaration; resolving the newest version; the KSP-to-Kotlin version pairing; the compile-worker JVM floor and the `UnsupportedClassVersionError` it produces; where generated sources land; `kspMocksTargets` formatting, including nested interfaces | ≤250 lines |
| `references/generated-api.md` | one section per emitted shape with the rendered Kotlin, the guessable-default table (`KspMocksProcessor.kt:175-191`), overload disambiguation, name-sorted emission, the constructor bag, channel semantics, and the Kotlin-to-Swift member map for a codebase that runs both twins | ≤250 lines |
| `references/troubleshooting.md` | one section per symptom of §4.5, with the diagnostic text to match | ≤250 lines |

### 4.5 The failure modes the body names

| symptom | cause | action |
| --- | --- | --- |
| `Could not find dev.modaal:mocks-processor` | the static Maven host is not in the build's repositories | add `maven { url = uri("https://modaal-agent.github.io/maven") }` |
| `kspMocksTargets: <fqn> is not resolvable in this compilation` | the interface is not on the test compile classpath of the module KSP runs in, or the FQN is misspelled | add the dependency on the declaring module; for a nested interface write `Outer.Inner` (phase 0 measures this) |
| `kspMocksTargets: <fqn> is not an interface` | the target is a class or an object | interfaces only |
| `kspMocksTargets: <fqn> declares type parameters — generic interfaces are not supported` | a generic interface | wrap the use site in a non-generic interface, or hand-write the double |
| `kspMocksTargets: <fqn>.<fn> has a vararg parameter — not supported` | a `vararg` parameter | take a `List` instead, or hand-write the double |
| `Unresolved reference: <Interface>Mock` in a test | KSP did not run for that source set, the FQN is missing from `kspMocksTargets`, or the test is in another package and needs the import | check the configuration against the module table, then the option list |
| `IllegalStateException: <fn>Handler expected to be set.` | the return type has no guessable default and no handler was set | set `<fn>Handler`; the fallbacks are Unit, `null`, a guessable default and the `Flow` channel |
| the test hangs collecting `<fn>()` or a `Flow` property | the channel fallback stays open | `close()` the channel after `trySend`, or set the handler to return `flowOf(…)` |
| `<fn>Args` does not exist | every parameter is function-typed, or the function takes none | assert through `<fn>Handler` |
| a member named `<fn><Param>CallCount` appears | overloads collide on bookkeeping names, so all but the narrowest overload carry their capitalized parameter names | use the disambiguated name |
| `UnsupportedClassVersionError` naming `KspMocksProcessorProvider` | the consuming build's Kotlin compile worker runs a JVM older than 17 | point the daemon at a newer JDK — `gradle/gradle-daemon-jvm.properties` with `toolchainVersion=<major>` (`README.md:92-97`) |
| adding an interface member breaks the mock's constructor call | a non-defaultable stored property is constructor-seeded | pass the new argument in the test; `README.md:76-78` states the intent |

### 4.6 What the skill must not contain

1. **The skill says what to write and does not re-derive why.** "Selection is a build-script list"
   belongs in the skill; the measurement showing that KDoc does not survive to the test
   compilation's KSP pass is `KspMocksProcessor.kt:24-37` and `README.md:27-33`, where it already is.
2. **No contributor material.** How the processor is built, the two test layers, the release
   procedure, `checkPublishedBytecodeVersion`: CONTRIBUTING.md and AGENTS.md (§8, D5).
3. **No version literal** anywhere in the tree (§8, D7), enforced by check K10.
4. **Every member name and every diagnostic string the skill writes is compared by checks K6 and K7**
   against `MockRenderer.kt` and `KspMocksProcessor.kt`. A spelling the renderer does not emit reds
   the `skill` job.

---

## 5. The gate

### 5.1 `scripts/check-skill.sh`

Reads markdown and JSON with `grep`, `awk` and `python3`. No JDK, no Gradle, no network.
`--self-test` seeds one violation per check and asserts each goes red.

| # | check | fails when |
| --- | --- | --- |
| K1 | frontmatter: `---` on line 1, one `key: value` per line, a closing `---`, no unquoted value carrying `: ` | either loader would refuse the file (§2.3) |
| K2 | `name:` equals the containing directory, and is not `synced` | they differ |
| K3 | frontmatter keys ⊆ the six standard keys | an extension key is present (§2.2) |
| K4 | `description` is 1–1,024 characters | empty, or over budget |
| K5 | `SKILL.md` ≤ 400 lines; each `references/*.md` ≤ 250 | over budget |
| K6 | every member suffix the skill writes is one `MockRenderer.kt` emits — the two sets are extracted with `grep -o '\${fn}[A-Za-z]*'` and `'\${name}[A-Za-z]*'` from the renderer, and from backticked `<fn>X` / `<prop>X` spellings in the skill | the skill names a member the renderer does not emit |
| K7 | every diagnostic the skill quotes appears in `KspMocksProcessor.kt` or `MockRenderer.kt` after `<fqn>`, `<fn>` and `<prop>` are replaced by a wildcard | a message was reworded on one side only |
| K8 | every Gradle token the skill's wiring snippet writes — `kspTest`, `ksp {`, `arg("kspMocksTargets"` — appears in `receipt/build.gradle.kts` | the wiring the skill teaches is not the wiring this repository builds |
| K9 | every relative link in the skill tree resolves to a file that exists | a moved or misspelled reference |
| K10 | no semver literal (`[0-9]+\.[0-9]+\.[0-9]+`) anywhere in the skill tree | a version was pinned in a snippet (§8, D7) |
| K11 | exactly one distinct Maven host URL across the skill tree, `README.md`, `.github/workflows/publish.yml` and `scripts/publish-maven.sh` | the host moved in one place |
| K12 | both `.claude-plugin/*.json` parse; the marketplace entry's `name` equals `plugin.json`'s `name`; its `source` resolves to a directory holding `skills/<name>/SKILL.md` | a manifest was edited on one side only |
| K13 | every `evals/<case>/` holds a prompt body and at least one grader whose `type` is one of `regex`, `tool_order`, `tool_used`, `file_exists`, `llm`, `baseline`; the eval directory's first segment is not a component-directory name | a case the runner would refuse |

K6, K7 and K8 each compare the skill against a file that a processor change edits, so a rename in
`MockRenderer.kt`, `KspMocksProcessor.kt` or `receipt/build.gradle.kts` reds the same push (§8, D10).

### 5.2 The CI job

One job added to `.github/workflows/ci.yml`, modelled on `rules` (`:14-23`): `ubuntu-latest`,
`actions/checkout@v5`, then `scripts/check-skill.sh`. No JDK step, no Gradle step. The `build` job is
left ungated (§9).

---

## 6. The eval suite

### 6.1 Where the cases live

`evals/` at the repository root, which is the runner's default eval directory (§2.5), so
`.claude-plugin/plugin.json` needs no `experimental.evals` key. The runner refuses an eval directory
whose first path segment is a loaded component directory — `commands`, `skills`, `agents`, `hooks`,
`themes`, `output-styles`, `monitors`, `workflows` (twin spec 002 §13.2) — and `evals` is none of
them. `.gitignore` gains `evals/results/`, which a run writes.

### 6.2 The six cases

| case | the prompt's situation | the with-skill answer that is correct |
| --- | --- | --- |
| `wire-a-jvm-module` | a Kotlin/JVM Gradle module whose tests hand-write fakes | the host repository, the KSP plugin, `kspTest`, `kspMocksTargets`; no committed generated files; no MockK or Mockito |
| `interfaces-in-another-module` | interfaces in `:core`, tests in `:feature` | wire KSP in `:feature`, list the `:core` FQNs there, and keep `:core` on the test compile classpath |
| `multiplatform-module` | a KMP module with a JVM test target | whatever phase 0 measured; `kspJvmTest` is the candidate |
| `flow-test-hangs` | a test that never returns while collecting `events()` from a mock | `close()` the channel after `trySend`, or set `eventsHandler` to return `flowOf(…)` |
| `handler-expected-to-be-set` | `IllegalStateException: loadHandler expected to be set.` | set `loadHandler`; the four fallbacks and which return types get them |
| `generic-interface-refused` | generation fails with `declares type parameters` | generic interfaces are unsupported; wrap or hand-write, and do not hand-edit generated output |

Each case is a directory holding `prompt.md` — frontmatter with `description`, `tags`,
`allowed_tools`, `max_turns` and `expected_outcome`, body the user prompt — and `graders/*.md`, one
per grader: a `tool_used` grader on `Skill` marked `arm: with-only`, one `llm` grader on the last
message, and one or two `regex` graders on exact tokens (`kspTest`, `kspMocksTargets`, `close()`,
`loadHandler`).

### 6.3 How the comparison runs until the runner opens

`claude plugin eval` is in early access on this account (§2.5), so phase 4 runs each prompt twice by
hand and compares the transcripts:

```bash
claude -p --restricted --strict-mcp-config --allowedTools "Read,Glob,Grep,Skill" \
  --permission-prompts none [--plugin-dir <repo>] "<the prompt.md body>"
```

Three conditions, each of which the twin repository measured on a first pass that got them wrong
(spec 002 §13.3):

1. **The run directory's path must not name this repository.** File tools take absolute paths, and an
   arm run under a path carrying the repository name answered out of this repository's own files. Run
   from `mktemp -d`.
2. **`--restricted` confines the file tools to the run directory**, which is what makes condition 1
   hold. It also removes Bash, so an arm cannot fetch `maven-metadata.xml` and names that step
   instead of running it.
3. **Under `--restricted` the with-arm cannot open `references/*.md`.** The answers measure what
   `SKILL.md` alone produces. A case meant to exercise a reference file adds
   `--add-dir <repo>/skills/kotlin-ksp-mocks` to the with-arm.

### 6.4 What the comparison is expected to show

The without-arm hypothesis is MockK or Mockito for the three setup cases (§1.1), and a correct
general diagnosis with the wrong vocabulary for the three diagnostic cases. An arm whose with-skill
answer is wrong is a defect in the skill's text: edit the skill, then re-run that prompt in a fresh
session.

---

## 7. Phasing

Each phase is one reviewable commit, prefixed `[001-agent-skill]`.

| phase | what lands | gate |
| --- | --- | --- |
| 0 | no repository file except this spec: measurements appended as a new section — the two-module case (`:core` interfaces, `:feature` tests), the KMP wiring and whether a common test source set sees a JVM-generated mock, the Android wiring, `kspTestFixtures`, the nested-interface FQN, the exact symptom of an unclosed channel under `runTest`, and the generated file's package, visibility and path | each measurement carries the scratch project's `settings.gradle.kts` and `build.gradle.kts`, the command run and its output. §4.1's four unmeasured rows and §6.2's `multiplatform-module` case are written from this section and from nothing else |
| 1 | `skills/kotlin-ksp-mocks/SKILL.md`, the three `references/*.md`, `.claude-plugin/marketplace.json`, `.claude-plugin/plugin.json` | `claude plugin marketplace add ./ --scope local`, `claude plugin install kotlin-ksp-mocks@kotlin-ksp-mocks`, `claude plugin details` — one skill in the inventory, body under 5,000 tokens; `npx skills add ./ --list` lists the skill. §10.1 is answered here |
| 2 | `scripts/check-skill.sh` with K1–K13 and `--self-test`; the `skill` job in `.github/workflows/ci.yml` | every check red against its seeded violation, then green |
| 3 | `README.md` §"Agent skill" with the four channels, and the repository declaration in §Wiring (§1.2); `CONTRIBUTING.md` layout and testing entries; the `AGENTS.md` rules from §4.6, then `cp AGENTS.md CLAUDE.md` | `scripts/check-skill.sh`; `cmp AGENTS.md CLAUDE.md`; `./gradlew build` unaffected |
| 4 | `evals/` with the six cases; `.gitignore` gains `evals/results/`; check K13's red control | the twelve runs of §6.3, recorded as a new section of this spec |

Phase 0 gates phase 1: one of §4.1's five rows is the shape this repository builds, and phase 0
measures the other four (§1.5). Phase 4 is separable and may be dropped without affecting phases
1–3.

---

## 8. Decisions

**D1 — One skill, not one per module shape.** The first thing the agent does is pick a configuration
from the module table (§4.1), and with two skills that choice would be made by whichever
`description` matched, before either body loaded. Cost: an adopter on a Kotlin/JVM module carries the
KMP and Android rows in context for the session — about 20 lines.

**D2 — The skill is named `kotlin-ksp-mocks`, matching the repository, the plugin and the
marketplace.** An adopter listing `~/.claude/skills/` sees what the directory generates. Cost: the
install id reads `kotlin-ksp-mocks@kotlin-ksp-mocks`. Rejected: `ksp-mocks` for the skill, which gives the id
`ksp-mocks@kotlin-ksp-mocks` and a directory name that says KSP without saying Kotlin. §10.1 holds
the case where the loader refuses the identical pair.

**D3 — The lane decision is the module shape, not generate-versus-commit.** The twin repository has
two generation modes because Sourcery can write into the repository; this processor generates into
`build/generated/ksp/` during test compilation and never elsewhere (`README.md:34-37`). The decision here is
which module and which configuration, so §4.1 replaces the twin's lane table rather than translating
it row for row.

**D4 — Three reference files, not four.** The twin ships a fourth, `writing-testable-protocols.md`,
because annotating a protocol is its own subject there. Selection here is a build-script list, so
what remains — which interface shapes generate a usable mock — is 35 lines in the body and one
section of `references/generated-api.md`.

**D5 — The skill teaches adopters, not contributors.** Its audience is an agent in a repository that
consumes the processor. Editing the processor is CONTRIBUTING.md's and AGENTS.md's subject, and an
agent working in this repository already loads both.

**D6 — Frontmatter restricted to the six standard keys.** The cost is `when_to_use`, whose content
moves into `description` under the same cap. What it buys is that the same directory uploads to
claude.ai and packages for the Skills API unedited (§2.2). Check K3 enforces it.

**D7 — No version literal anywhere in the skill tree.** A pinned `0.2.1` in a snippet is wrong the
day after the next tag, and no check in the adopter's repository reads it. The snippets carry a
placeholder and the instruction to read
`https://modaal-agent.github.io/maven/dev/modaal/mocks-processor/maven-metadata.xml`, whose
`<release>` element is the newest published version — 200 with `0.2.1` on 2026-09-10 (§1.2). Rejected:
allow literals and gate them against the tag list, which re-breaks CI at every release. Check K10
enforces this.

**D8 — The gate is a shell script under `scripts/`, run by a job with no JDK.** `scripts/` already
holds `publish-maven.sh`, and `rules` is the precedent for a markdown-only job that reports in
seconds (`ci.yml:14-23`). Rejected: a Gradle task in `:mocks-processor`, which puts a markdown check
behind JDK 25 and Gradle provisioning and makes a skill-only push wait for the compile.

**D9 — `README.md` gains the repository declaration in the same phase as the skill's install
section.** §1.2 measured that README's wiring block does not resolve without it. Check K11 then holds
the URL to one spelling across README, the skill, the workflow and the publish script. Rejected:
leaving README as it is and stating the URL in the skill alone, which leaves a reader who never
installs the skill with a build that fails at resolution.

**D10 — One repository, and it is this one.** Checks K6, K7 and K8 compare the skill's text against
`MockRenderer.kt`, `KspMocksProcessor.kt` and `receipt/build.gradle.kts`. In one repository they run
on the push that breaks them. Split into a skills repository, the comparison would run against a
pinned or freshly cloned copy, so a renderer edit here would go green here and red on that
repository's next push or scheduled run.

**D11 — `evals/` at the repository root, and no `experimental.evals` key.** It is the runner's
default (§2.5), and this repository has no `Tests/` tree to sit beside, which is why the twin chose
`Tests/Evals` and named it in the manifest.

**D12 — The Kotlin-to-Swift member map is a section of `references/generated-api.md`, not a fourth
reference file.** It is a table of about 12 rows, and it is read by the same agent, in the same
session, that has just read the Kotlin vocabulary. CONTRIBUTING.md `:12-25` already states that
changing either side is a cross-repo decision; the section points there and adds no rule.

---

## 9. Non-goals

- **A contributor-facing skill.** Editing the processor, the two test layers, the release procedure:
  CONTRIBUTING.md and AGENTS.md.
- **A `.claude/skills/` copy inside this repository.** The skill teaches consuming the processor; an
  agent working here is editing it. `.gitignore:11` ignores `.claude/` in any case.
- **Shipping the skill in the published jar.** The jar is what a consumer's KSP pass loads. A skill
  is installed through one of §3's four channels.
- **A change filter on the `build` job.** The twin repository added one because a push that touches
  only markdown would otherwise boot a simulator and run four macOS lanes. Here `build` is one
  ubuntu job and the only proof this repository has that the generated mocks compile and run.
- **Executable wiring fixtures.** No scratch consumer project is added to CI to compile the skill's
  Gradle snippets. `:receipt` is the executable proof of the generated API; the snippets are held to
  `receipt/build.gradle.kts` by check K8 and to nothing else.
- **An MCP server.** Wiring this processor is a repository declaration, a dependency and one option.

---

## 10. Open questions

**10.1 — Does a marketplace whose name equals its only plugin's name load?** §3. The twin repository
names them differently and so did not test it. Answered by phase 1 against a local checkout. If the
loader refuses the pair, the fallback is plugin and skill named `ksp-mocks` inside marketplace
`kotlin-ksp-mocks`, which costs one directory rename and the two `name` fields in the manifests.

**10.2 — Which configurations do Kotlin Multiplatform and Android modules need?** §1.5, §4.1. Phase 0
measures both in scratch projects, and writes §4.1's two rows from what the builds accepted.

**10.3 — Does `kspTestFixtures` produce a shared-mocks module?** §4.1's last row. If a `testFixtures`
source set can run the processor and publish the generated mocks to sibling modules' tests, the skill
gains a row and the reference gains a section; if it cannot, the skill says to wire each module.
Phase 0.

**10.4 — What does an unclosed channel look like to the adopter?** §4.5 says the test hangs.
Under `kotlinx-coroutines-test`, `runTest` carries a default timeout, so the observable symptom is
probably a timeout message rather than an indefinite hang. Phase 0 measures the exact text, and the
failure-modes row is written from it.

**10.5 — Does `claude plugin eval` leave early access before phase 4?** §2.5. If it does, phase 4's
gate becomes `claude plugin eval . --ablation with-without` and §6.3's manual form becomes the
fallback. The cases are written for the runner either way.
