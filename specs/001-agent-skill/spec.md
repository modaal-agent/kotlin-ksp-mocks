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

**Amended 2026-09-10 — §14.1, §14.3.** Phase 3 rewrote §Wiring and added §"Agent skill". `README.md`
is 167 lines, and every range in the paragraph above has moved: §14.3 maps each one to where it
points now.

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

**Amended 2026-09-10 — §14.1.** Phase 3 wrote both. `grep -n 'modaal-agent.github.io' README.md`
returns `:20`, the `dependencyResolutionManagement` block, and `:45`, the `maven-metadata.xml` URL
that resolves `<version>`.

### 1.3 There is no skills tree and no plugin manifest

`ls skills .claude-plugin` returns two "No such file or directory". `.gitignore:11` ignores
`.claude/`; the pattern names that directory exactly, so a `.claude-plugin/` directory at the root is
tracked without a `.gitignore` edit.

**Amended 2026-09-10 — §12.1, §14.1.** Phase 1 wrote both directories, and no `.gitignore` edit was
needed. Phase 3 listed them in `CONTRIBUTING.md` §"Repository layout".

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

**Amended 2026-09-10 — §14.1, §14.3.** Two rows moved in phase 3. The Maven host URL is now also at
`README.md:20` and `:45`, which is what gives K11 its fourth source; the generated-source path is at
`CONTRIBUTING.md:83`, not `:58`.

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

**Amended 2026-09-10 — §11.3.** Phase 0 has run. This table becomes six rows, and two of the rows
above are superseded: a multiplatform module writes `add("kspJvmTest", …)` because the typed accessor
does not exist, and the Android row's candidate `kspTestDebugUnitTest` does not exist in either AGP
major. §11.3 carries what each shape needs.

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

**Amended 2026-09-10 — §11.8, §11.9.** Every diagnostic in this table reaches an adopter with an
`e: [ksp] ` prefix, measured in §11.8. The unclosed-channel row's symptom under `runTest` is
`UncompletedCoroutinesError` after the default timeout, measured in §11.9.

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
| K8 | every Gradle token the skill's wiring snippet writes — `kspTest`, `ksp {`, `arg("kspMocksTargets"` — appears in `receipt/build.gradle.kts` | the wiring the skill teaches is not the wiring this repository builds. **Amended 2026-09-10 — §12.3:** the third token is `kspMocksTargets`, because `receipt/build.gradle.kts:25-31` splits the call across lines |
| K9 | every relative link in the skill tree resolves to a file that exists | a moved or misspelled reference |
| K10 | no semver literal (`[0-9]+\.[0-9]+\.[0-9]+`) anywhere in the skill tree | a version was pinned in a snippet (§8, D7) |
| K11 | exactly one distinct Maven host URL across the skill tree, `README.md`, `.github/workflows/publish.yml` and `scripts/publish-maven.sh` | the host moved in one place |
| K12 | both `.claude-plugin/*.json` parse; the marketplace entry's `name` equals `plugin.json`'s `name`; its `source` resolves to a directory holding `skills/<name>/SKILL.md` | a manifest was edited on one side only |
| K13 | every `evals/<case>/` holds a prompt body and at least one grader whose `type` is one of `regex`, `tool_order`, `tool_used`, `file_exists`, `llm`, `baseline`; the eval directory's first segment is not a component-directory name | a case the runner would refuse |

K6, K7 and K8 each compare the skill against a file that a processor change edits, so a rename in
`MockRenderer.kt`, `KspMocksProcessor.kt` or `receipt/build.gradle.kts` reds the same push (§8, D10).

**Amended 2026-09-10 — §13.2.** K7 compares by containment after normalising both sides, K8 strips
comments before it searches, and K13 passes while `evals/` does not exist.

### 5.2 The CI job

One job added to `.github/workflows/ci.yml`, modelled on `rules` (`:14-23`): `ubuntu-latest`,
`actions/checkout@v5`, then `scripts/check-skill.sh`. No JDK step, no Gradle step. The `build` job is
left ungated (§9).

**Amended 2026-09-10 — §13.1.** The job landed as written, between `rules` and `build`.

---

## 6. The eval suite

### 6.1 Where the cases live

`evals/` at the repository root, which is the runner's default eval directory (§2.5), so
`.claude-plugin/plugin.json` needs no `experimental.evals` key. The runner refuses an eval directory
whose first path segment is a loaded component directory — `commands`, `skills`, `agents`, `hooks`,
`themes`, `output-styles`, `monitors`, `workflows` (twin spec 002 §13.2) — and `evals` is none of
them. `.gitignore` gains `evals/results/`, which a run writes.

**Landed 2026-09-10 in phase 4 — §15.1.** `evals/` holds six case directories, `.gitignore:14`
holds `evals/results/`, and `plugin.json` took no `experimental.evals` key.

### 6.2 The six cases

| case | the prompt's situation | the with-skill answer that is correct |
| --- | --- | --- |
| `wire-a-jvm-module` | a Kotlin/JVM Gradle module whose tests hand-write fakes | the host repository, the KSP plugin, `kspTest`, `kspMocksTargets`; no committed generated files; no MockK or Mockito |
| `interfaces-in-another-module` | interfaces in `:core`, tests in `:feature` | wire KSP in `:feature`, list the `:core` FQNs there, and keep `:core` on the test compile classpath |
| `multiplatform-module` | a KMP module with a JVM test target | whatever phase 0 measured; `kspJvmTest` is the candidate |
| `flow-test-hangs` | a test that never returns while collecting `events()` from a mock | `close()` the channel after `trySend`, or set `eventsHandler` to return `flowOf(…)` |
| `handler-expected-to-be-set` | `IllegalStateException: loadHandler expected to be set.` | set `loadHandler`; the four fallbacks and which return types get them |
| `generic-interface-refused` | generation fails with `declares type parameters` | generic interfaces are unsupported; wrap or hand-write, and do not hand-edit generated output |

**Amended 2026-09-10 — §11.3, §11.6.** The `multiplatform-module` case's correct answer is now
specified: `add("kspJvmTest", …)`, tests in `jvmTest`, and `commonTest` unable to see the mock.

Each case is a directory holding `prompt.md` — frontmatter with `description`, `tags`,
`allowed_tools`, `max_turns` and `expected_outcome`, body the user prompt — and `graders/*.md`, one
per grader: a `tool_used` grader on `Skill` marked `arm: with-only`, one `llm` grader on the last
message, and one or two `regex` graders on exact tokens (`kspTest`, `kspMocksTargets`, `close()`,
`loadHandler`).

**Landed 2026-09-10 in phase 4 — §15.1, §15.2, §15.5.** Six cases, twenty-one graders, in the shape
this section describes. The one departure: `wire-a-jvm-module` was written with a fourth grader
forbidding MockK and Mockito by regex, and the runs showed it reds an answer that only mentions
them, so it was removed before the commit.

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

**Run 2026-09-10 in phase 4 — §15.3.** Twelve runs, on model `sonnet`, at $0.9040. All three
conditions held as written, and no case needed `--add-dir`: every with-arm answered from `SKILL.md`
alone.

### 6.4 What the comparison is expected to show

The without-arm hypothesis is MockK or Mockito for the three setup cases (§1.1), and a correct
general diagnosis with the wrong vocabulary for the three diagnostic cases. An arm whose with-skill
answer is wrong is a defect in the skill's text: edit the skill, then re-run that prompt in a fresh
session.

**Superseded 2026-09-10 by §15.4.** The three setup cases' without-arms proposed neither MockK nor
Mockito: denied the web search that would confirm the library exists, each refused to answer and
asked for the README or the coordinates. MockK appeared once, in the `generic-interface-refused`
without-arm. No with-arm answer was wrong, so the second sentence's procedure was not exercised.

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

**All five phases landed 2026-09-10 — §11, §12, §13, §14, §15.**

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

**Answered 2026-09-10 in phase 1 — §12.2.** It loads. `claude plugin install
kotlin-ksp-mocks@kotlin-ksp-mocks` succeeds and `claude plugin details` reports one skill. D2's
fallback is dropped.

**10.2 — Which configurations do Kotlin Multiplatform and Android modules need?** §1.5, §4.1. Phase 0
measures both in scratch projects, and writes §4.1's two rows from what the builds accepted.

**Answered 2026-09-10 in phase 0 — §11.3, §11.6.** Multiplatform: `add("kspJvmTest", …)`, and
`commonTest` cannot see the generated mock. Android: `add("kspTest", …)` on both AGP majors, with
`kotlin("android")` applied on AGP 8 and refused on AGP 9.

**10.3 — Does `kspTestFixtures` produce a shared-mocks module?** §4.1's last row. If a `testFixtures`
source set can run the processor and publish the generated mocks to sibling modules' tests, the skill
gains a row and the reference gains a section; if it cannot, the skill says to wire each module.
Phase 0.

**Answered 2026-09-10 in phase 0 — §11.7.** It does, once `src/testFixtures/kotlin/` holds at least
one Kotlin file; without one the KSP task is `NO-SOURCE` and generates nothing.

**10.4 — What does an unclosed channel look like to the adopter?** §4.5 says the test hangs.
Under `kotlinx-coroutines-test`, `runTest` carries a default timeout, so the observable symptom is
probably a timeout message rather than an indefinite hang. Phase 0 measures the exact text, and the
failure-modes row is written from it.

**Answered 2026-09-10 in phase 0 — §11.9.** `kotlinx.coroutines.test.UncompletedCoroutinesError:
After waiting for 1m, the test body did not run to completion`, after 61 s of wall time at
`kotlinx-coroutines-test` 1.11.0 defaults.

**10.5 — Does `claude plugin eval` leave early access before phase 4?** §2.5. If it does, phase 4's
gate becomes `claude plugin eval . --ablation with-without` and §6.3's manual form becomes the
fallback. The cases are written for the runner either way.

---

## 11. Amendment — what phase 0 measured

Written 2026-09-10, after the commit carrying §1–§10 (`82a4bef`). Every fact below was produced by a
scratch project outside this repository, against the published artifact `dev.modaal:mocks-processor:0.2.1`
resolved from the static Maven host. §4.1, §4.5, §6.2, §10.2, §10.3 and §10.4 each carry a line
pointing here.

### 11.1 The five scratch projects

| # | shape | toolchain | outcome |
| --- | --- | --- | --- |
| A | two modules, `:core` interfaces and `:feature` tests, Kotlin/JVM | Gradle 9.7.1, JDK 25, Kotlin 2.4.10, KSP 2.3.11 | `:feature:test` green, 1 test |
| B | Kotlin Multiplatform, `jvm()` target only | same | `:jvmTest` green, 1 test |
| C | `:core` with `java-test-fixtures`, `:feature` consuming them | same | `:feature:test` green, 1 test |
| D | Android library, AGP 9.3.2, built-in Kotlin | Gradle 9.7.1, JDK 25, KSP 2.3.11 | `:library:testDebugUnitTest` green, 1 test |
| E | Android library, AGP 8.13.2, `kotlin("android")` | Gradle 8.14.3, JDK 21, Kotlin 2.4.10, KSP 2.3.11 | `:library:testDebugUnitTest` green, 1 test |

Each project declared the same two repositories and the same coroutines version (1.11.0):

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
  repositories {
    mavenCentral()
    maven { url = uri("https://modaal-agent.github.io/maven") }
  }
}
```

### 11.2 Omitting the host repository, measured

Removing the `maven { … }` line from project A and running `:feature:kspTestKotlin`:

```
> Could not find dev.modaal:mocks-processor:0.2.1.
  Searched in the following locations:
  Required by:
```

This is the error §4.5's first row names, and it confirms §1.2 and D9.

### 11.3 The configuration each module shape needs — answers §10.2

| shape | what to write | KSP task that runs | generated path |
| --- | --- | --- | --- |
| Kotlin/JVM | `kspTest("dev.modaal:mocks-processor:<version>")` | `:<module>:kspTestKotlin` | `<module>/build/generated/ksp/test/kotlin/` |
| Kotlin Multiplatform (JVM tests) | `dependencies { add("kspJvmTest", "dev.modaal:mocks-processor:<version>") }` | `:kspTestKotlinJvm` | `build/generated/ksp/jvm/jvmTest/kotlin/` |
| Android, AGP 9 | `dependencies { add("kspTest", …) }`, and **no** `kotlin("android")` plugin | `:<module>:kspDebugUnitTestKotlin` | `<module>/build/generated/ksp/debugUnitTest/kotlin/` |
| Android, AGP 8 | `kotlin("android")` applied, `add("kspTest", …)` | `:<module>:kspDebugUnitTestKotlin` | `<module>/build/generated/ksp/debugUnitTest/kotlin/` |
| shared across modules | `add("kspTestFixtures", …)` in the module that owns the interfaces, plus §11.7's placeholder file | `:<module>:kspTestFixturesKotlin` | `<module>/build/generated/ksp/testFixtures/kotlin/` |

The `ksp*` configurations each shape offers, read from `configurations.names`:

| shape | configurations, internal `*ProcessorClasspath` and `*PluginClasspath` entries omitted |
| --- | --- |
| Kotlin/JVM | `ksp`, `kspTest` |
| Kotlin Multiplatform, `jvm()` | `ksp`, `kspCommonMainMetadata`, `kspJvm`, `kspJvmTest` |
| Android, AGP 9.3.2 and AGP 8.13.2 alike | `ksp`, `kspDebug`, `kspRelease`, `kspTest`, `kspTestDebug`, `kspTestRelease`, `kspAndroidTest`, `kspAndroidTestDebug`, `kspAndroidTestRelease`, `kspTestFixtures`, `kspTestFixturesDebug`, `kspTestFixturesRelease` |

Four results that change what the skill writes:

1. **`kspJvmTest(…)` as a typed accessor does not exist in a multiplatform module.** The build script
   fails to compile: `Unresolved reference 'kspJvmTest'`. The dependency is added with
   `add("kspJvmTest", …)`. `README.md:19`'s comment names the configuration, and a snippet that
   writes it as a function call does not configure.
2. **`kspTestDebugUnitTest` does not exist**, in either AGP major. §4.1's Android row named it as a
   candidate; the per-variant names are `kspTestDebug` and `kspTestRelease`, and `kspTest` covers the
   unit tests of every variant.
3. **AGP 9 refuses `kotlin("android")`**: *"The 'org.jetbrains.kotlin.android' plugin is no longer
   required for Kotlin support since AGP 9.0."* AGP 8 requires it. The Android row is therefore two
   rows, one per AGP major.
4. **AGP 8.13.2 does not run on JDK 25.** The build fails with `What went wrong: 25.0.4.1`, so
   project E ran on JDK 21. The skill states the AGP-8 combination it was measured on rather than
   implying any JDK works.

### 11.4 The generated file — answers part of §7's phase 0 row

From project A, whose test module is `com.example.feature` and whose interfaces are
`com.example.core`:

```
feature/build/generated/ksp/test/kotlin/com/example/core/RepositoryMock.kt
```

```kotlin
// Generated by dev.modaal:mocks-processor. DO NOT EDIT.
package com.example.core

class RepositoryMock : com.example.core.Repository {
```

- The mock lands in the **interface's** package, not the test's, so a test in another package imports
  it — `import com.example.core.RepositoryMock`.
- The class is public: `class RepositoryMock`, no visibility modifier.
- The file lands under the **consuming** module's `build/`, so two modules that both name the same
  interface each generate their own copy.

### 11.5 Nested interfaces

`kspMocksTargets` accepts a nested interface written with dots — `com.example.core.Outer.Inner` — and
generates `InnerMock` in `com.example.core`, implementing `com.example.core.Outer.Inner`. The mock is
named from the **simple** name and lands in the enclosing package, so two nested interfaces named
`Inner` under different outers in one package both render as `InnerMock` and collide. The skill's
reference states the dot form and the collision.

### 11.6 A `commonTest` source set cannot see a JVM-generated mock — answers §10.2

Project B, with `LoaderMock` referenced from `src/commonTest`:

```
e: …/src/commonTest/kotlin/com/example/kmp/CommonMockTest.kt:9:18 Unresolved reference 'LoaderMock'.
```

The same reference from `src/jvmTest` compiles and passes. A multiplatform module's tests that use
mocks live in the platform test source set whose KSP configuration was wired.

### 11.7 `kspTestFixtures` works, after one placeholder file — answers §10.3

Project C wired `add("kspTestFixtures", …)` on `:core` and consumed it from `:feature` with
`testImplementation(testFixtures(project(":core")))`. The first run generated nothing:

```
> Task :core:kspTestFixturesKotlin NO-SOURCE
e: …/SharedMockTest.kt:4:25 Unresolved reference 'RepositoryMock'.
```

KSP skips a source set that holds no Kotlin file. Adding one file under
`core/src/testFixtures/kotlin/` made the task run, and `:feature:test` passed against the mock
generated into `core/build/generated/ksp/testFixtures/kotlin/`. So the shared-mocks module is
available, and it costs one placeholder file that the skill has to name.

### 11.8 The diagnostics as an adopter sees them

All four processor errors of §1.4, produced in project A by naming a missing type, a generic
interface, an interface with a `vararg` parameter and a data class in `kspMocksTargets`:

```
e: [ksp] kspMocksTargets: com.example.core.Missing is not resolvable in this compilation
e: [ksp] kspMocksTargets: com.example.core.Cache declares type parameters — generic interfaces are not supported
e: [ksp] kspMocksTargets: com.example.core.Bulk.save has a vararg parameter — not supported
e: [ksp] kspMocksTargets: com.example.core.Item is not an interface
```

The `e: [ksp] ` prefix is KSP's, not the processor's, so check K7 compares the text after it.

### 11.9 The unclosed channel under `runTest` — answers §10.4

Project A collected `events()` from a mock after one `trySend` and no `close()`:

```
kotlinx.coroutines.test.UncompletedCoroutinesError: After waiting for 1m, the test body did not run to completion
```

Wall time 61 s, with `kotlinx-coroutines-test` 1.11.0 defaults. §4.5's row says "the test hangs";
inside `runTest` the observable form is this error after the default timeout. Outside `runTest` no
measurement was made.

### 11.10 What this changes in §4.1, §4.5 and §6.2

- **§4.1's table becomes six rows**, per §11.3: Kotlin/JVM, Kotlin Multiplatform, Android AGP 9,
  Android AGP 8, the two-module case, and the shared-`testFixtures` case. Rows 2 and 3 of the
  original table are superseded — `kspJvmTest` is written with `add(…)`, and `kspTestDebugUnitTest`
  does not exist.
- **§4.5 gains no row and loses none.** The unclosed-channel row's symptom becomes §11.9's text, and
  the four diagnostic rows carry the `e: [ksp] ` prefix.
- **§6.2's `multiplatform-module` case** is now specified: the correct answer is `add("kspJvmTest",
  …)`, tests in `jvmTest`, and the note that `commonTest` does not see the mock.
- **`references/gradle-wiring.md` gains the AGP split**, the `add(…)` form, the `testFixtures`
  placeholder and the nested-interface collision.

### 11.11 Still open after phase 0

- **The `<fn>Args` visibility of a nested `<Fn>Args` data class across modules** was not exercised:
  project A's cross-module test used single-parameter functions only.
- **Whether a KMP module with a native or JS target changes the JVM wiring** was not measured;
  project B declared `jvm()` alone.
- **AGP 8 on Gradle 9** is not a supported combination here: AGP 8.13.2 failed to create a service on
  Gradle 9.7.1 before any KSP task ran. Project E therefore measures AGP 8 on Gradle 8.14.3 only.

---

## 12. Amendment — what phase 1 landed

Written 2026-09-10, after phase 0 (§11). §5.1's K8 row and §10.1 each take a line pointing here.

### 12.1 What was written

| path | lines |
| --- | --- |
| `skills/kotlin-ksp-mocks/SKILL.md` | 189, against the 400 budget |
| `skills/kotlin-ksp-mocks/references/gradle-wiring.md` | 236 |
| `skills/kotlin-ksp-mocks/references/generated-api.md` | 217 |
| `skills/kotlin-ksp-mocks/references/troubleshooting.md` | 201 |
| `.claude-plugin/marketplace.json`, `.claude-plugin/plugin.json` | the two manifests of §3 |

The `description` is 705 characters against the 1,024 budget, and carries no `: ` — the shape §2.3
measured the cross-agent CLI refusing.

### 12.2 The gate, run against a copy in a temporary directory

Nothing was installed into this repository or left in the user's settings: the tree was copied to a
scratch directory, the marketplace was added there with `--scope local`, and both were removed after
the run.

- **`claude plugin validate .`** — passes with one warning, `plugin.json → version: No version
  specified`. The version is left absent deliberately: a plugin version would have to be bumped on
  every skill edit, and no channel in §3 reads it.
- **`claude plugin marketplace add ./ --scope local`**, then **`claude plugin install
  kotlin-ksp-mocks@kotlin-ksp-mocks --scope local -y`** — both succeed. **This answers §10.1: a
  marketplace and its only plugin may carry the same name**, so D2's fallback of renaming the skill
  to `ksp-mocks` is not needed and is dropped.
- **`claude plugin details kotlin-ksp-mocks`** — component inventory `Skills (1) kotlin-ksp-mocks`,
  resolved from the plugin root's own `skills/` through `source: "./"`. Projected token cost ~294
  always-on and ~3.9k on invoke, under §2.1's 5,000-token compaction floor.
- **`npx skills add ./ --list`** — "Found 1 skill", listing `kotlin-ksp-mocks` with its full
  description. The CLI's stricter YAML parser accepted the frontmatter.

### 12.3 Where phase 1 departed from §4 and §5.1

- **K8's literal token does not exist.** §5.1 has K8 compare `arg("kspMocksTargets"` against
  `receipt/build.gradle.kts`; that file writes the call across three lines (`:25-31`), so the literal
  never appears and the check as written would be red on a correct tree. Phase 2 implements K8 over
  three separate tokens — `kspTest`, `ksp {` and `kspMocksTargets` — each of which does appear.
- **The skill carries six module rows, per §11.3**, and the wiring section of `SKILL.md` shows the
  Kotlin/JVM shape in full while the other five are one row each plus
  `references/gradle-wiring.md`.
- **`references/writing-mockable-interfaces.md` stayed unwritten**, as D4 decided: the interface
  shapes that mock cleanly are 20 lines of `SKILL.md` and one section of
  `references/generated-api.md`.
- **Two claims were cut during the gate** because no measurement backs them: that a type alias or an
  `expect` declaration produces the not-resolvable diagnostic, and that `kspTest` is the only KSP
  configuration a Kotlin/JVM module offers besides `ksp`. The first became the wrong-configuration
  cause; the second names the internal `*ProcessorClasspath` entries §11.3 measured.

### 12.4 Still open after phase 1

- **K11 has one side missing until phase 3.** `README.md` carries no Maven host URL yet (§1.2), so
  the check compares the skill against `.github/workflows/publish.yml` and
  `scripts/publish-maven.sh` only. Phase 3 adds README's copy under the same check.
- **Nothing verifies the skill's Gradle snippets compile.** §9 rules a scratch consumer project in
  CI out of scope; the snippets were written from phase 0's five projects, which did compile them.

**Answered 2026-09-10 in phase 3 — §14.1, §14.2.** `README.md:20` carries the host, so K11 compares
four sources. Seeding the host in README alone reds it.

---

## 13. Amendment — what phase 2 landed

Written 2026-09-10, after phase 1 (§12). §5.1 and §5.2 each take a line pointing here.

### 13.1 What was written

`scripts/check-skill.sh`, 523 lines, with `--self-test`; and the `skill` job in
`.github/workflows/ci.yml`, modelled on `rules` — `ubuntu-latest`, `actions/checkout@v5`, then the
script. No JDK step and no Gradle step, so the repository now has three jobs: `rules`, `skill` and
`build`.

Both runs are green on this checkout: thirteen checks pass, and each of the thirteen goes red
against a seeded violation.

| check | what the self-test seeds |
| --- | --- |
| K1 | a frontmatter value carrying `: ` |
| K2 | `name:` one character off the directory |
| K3 | a `when_to_use:` key |
| K4 | an empty `description:` |
| K5 | 400 lines of padding appended to `SKILL.md` |
| K6 | a backticked `<fn>CallCounter` |
| K7 | a diagnostic reworded to "is not resolvable in this build" |
| K8 | `kspTest(project` renamed in `receipt/build.gradle.kts` |
| K9 | a link to `references/missing.md` |
| K10 | a version literal in `SKILL.md` |
| K11 | a different host in the skill's URL |
| K12 | `plugin.json`'s `name` changed to `ksp-mocks` |
| K13 | an `evals/` case with a prompt and no grader |

### 13.2 Where phase 2 departed from §5.1

- **K7 compares by containment, not equality, and collapses placeholder runs.** A Kotlin string
  literal carries the call around it — the renderer's is
  `error(\"${fn}Handler expected to be set.\")\n` as one literal — so an equality test on the whole
  literal never matches the sentence the skill quotes. Both sides are normalised the same way
  (`$OPTION` to the option name, every interpolation and every `<fqn>`/`<fn>`/`<prop>` to `*`, then a
  run of `*` and `.` to one `*`), and the skill's text has to appear inside a logged literal. Without
  the run-collapsing, the vararg diagnostic compares `**` against `*.*` and fails on a correct tree.
- **K8 strips comments before it searches, and compares three tokens.** §12.3 already replaced the
  literal `arg("kspMocksTargets"`; the comment-stripping is new. `receipt/build.gradle.kts:6-7` names
  `kspTest` and `kspMocksTargets` in its header comment, so the first version of the check stayed
  green when the self-test removed the real `kspTest(project(":mocks-processor"))` call. A token
  named in a comment is not wiring the build runs.
- **K13 passes when `evals/` is absent**, reporting "no evals/ directory yet (phase 4)". Phase 4's
  commit makes it a live check; the seeded case proves it fires.
- **K11 has three sources until phase 3**, per §12.4 — the skill, the publish workflow and the
  publish script. `README.md` joins them when phase 3 adds the repository declaration.
  **Amended 2026-09-10 — §14.2.** It joined; K11 now compares four.

### 13.3 A bash detail the script is shaped by

A heredoc written inside `$( … )` is scanned for the closing parenthesis, and the quotes inside a
Python regex — `r'"((?:[^"\\]|\\.)*)"'` — make bash misread the substitution and fail with a syntax
error at a line it should never have parsed. Every Python checker is therefore a shell function whose
heredoc sits at statement level, called from the command substitution. The seven helpers at the top
of the script are that, and nothing else.

The self-test's cleanup trap holds the temporary directory in a global rather than a `local`, because
the trap runs after the function has returned and `set -u` would otherwise abort on an unbound name.

### 13.4 Still open after phase 2

- **No check reads `AGENTS.md`.** Phase 3 adds the rules that §4.6 states, and `cmp AGENTS.md
  CLAUDE.md` in the `rules` job is what keeps the two copies identical.
  **Answered 2026-09-10 in phase 3 — §14.1, §14.4.** `AGENTS.md` carries the rules; `cmp` is green;
  no check reads the file's content, and none is proposed.
- **`shellcheck` was not run** — it is not installed on this machine. `bash -n` parses the script,
  and both runs execute end to end.

---

## 14. Amendment — what phase 3 landed

Written 2026-09-10, after phase 2 (§13). §1.1, §1.2, §1.3, §1.4, §12.4, §13.2 and §13.4 each take a
line pointing here.

### 14.1 What was written

Four documents, no code. `./gradlew build` reads none of them.

| file | what changed |
| --- | --- |
| `README.md` (101 → 167 lines) | §Wiring opens with the `dependencyResolutionManagement` block naming `https://modaal-agent.github.io/maven` (`:15-23`) and closes with the `maven-metadata.xml` URL as the way to resolve `<version>` (`:44-45`); a new §"Agent skill" (`:105-149`) carries §3's four channels, between §"Not supported" and §"Releases" |
| `CONTRIBUTING.md` (59 → 96 lines) | §"Repository layout" (`:23-38`), one bullet per tracked directory; development rule 7 (`:68-74`), the skill held to the processor by `scripts/check-skill.sh`; and the two script invocations under §"Running the build" (`:86-96`) |
| `AGENTS.md` (173 → 204 lines) | §"The skill under `skills/` teaches adopters, and a gate holds it to the processor" (`:134-155`), six rules; a `SKILL.md` row in the read-first table (`:17`); §"State a rule once" naming the skill in the member-vocabulary bullet (`:159-163`) and the version-literal bullet (`:168-172`); a `skills/kotlin-ksp-mocks/` bullet in §"What goes in which document" (`:191-193`) |
| `CLAUDE.md` | `cp AGENTS.md CLAUDE.md` |

### 14.2 The gate

| command | result |
| --- | --- |
| `scripts/check-skill.sh` | thirteen green |
| `scripts/check-skill.sh --self-test` | thirteen red against their seeded violations, then thirteen green |
| `cmp AGENTS.md CLAUDE.md` | identical |
| `./gradlew build` | exit 0 |

K11's fourth source was verified separately, because the self-test seeds K11 in the skill and would
stay red with README's copy missing. The repository was copied to a temporary tree, the host in
`README.md` alone rewritten to `https://example.github.io/maven`, and the script run against that
tree:

```
$ scripts/check-skill.sh /tmp/…/k11-readme
✘ K11 — the Maven host is spelled 2 ways:
✘ skill checks failed
```

That answers §12.4's first bullet and §13.2's last.

### 14.3 The README line numbers this spec cites have moved

§Wiring gained 20 lines and §"Agent skill" 46, so every anchor after `:11` moved. The spec is
append-only, so the citations above stand as written; this table says where each one points now.

| cited as | cited in | now |
| --- | --- | --- |
| `README.md:19` — the `kspTest(…)` line | §0, §1.2, §1.5, §4.1, §11.3 | `:34` |
| `README.md:10-36` — the wiring block | §1.1 | `:10-56` |
| `README.md:27-33` — selection is a build-script list | §4.6 | `:47-52` |
| `README.md:34-37` — generation into `build/generated/ksp/` | §8 D3 | `:54-56` |
| `README.md:38-79` — the generated API | §1.1 | `:58-98` |
| `README.md:76-78` — the constructor-seeded bag | §4.5 | `:96-98` |
| `README.md:80-84` — the two unsupported constructs | §1.1 | `:100-103` |
| `README.md:87-90` — published to a static Maven host | §1.2 | `:153-156` |
| `README.md:92-97` — the class-file-major note | §1.1, §4.5 | `:158-163` |

`CONTRIBUTING.md:58`, the generated-source path cited in §1.4, is `:83`.

### 14.4 Where phase 3 departed from §7's row

- **§4.6's four items became six rules, and one of them went elsewhere.** The version-literal item
  (§4.6.3) is an instance of a rule `AGENTS.md` §"State a rule once" already states, so it extends
  that bullet rather than opening a seventh. The two rules with no §4.6 item are running
  `scripts/check-skill.sh` (and `--self-test` after editing a check) and the frontmatter-and-budget
  rule from §8 D6 and §2.1, both of which were already enforced by K3, K5 and K9 and stated nowhere
  a contributor reads.
- **`CONTRIBUTING.md` §"Repository layout" omits `evals/`.** The directory does not exist until
  phase 4; that commit adds the bullet.
- **README's multiplatform comment was rewritten, which §7's row did not ask for.** It read
  `// kspJvmTest in a multiplatform module`, which invites `kspJvmTest("dev.modaal:…")` — the typed
  accessor §11.3 measured does not exist. It now reads `// A multiplatform module has no typed
  accessor for its KSP configuration:` followed by `// add("kspJvmTest", "dev.modaal:…")`, matching
  the skill's row.

### 14.5 Still open after phase 3

- **Phase 4**, unchanged from §7: `evals/` with §6.2's six cases, `evals/results/` in `.gitignore`,
  K13's red control made live, and §6.3's twelve `claude -p` runs recorded as a further section here.
  It also adds the `evals/` bullet to `CONTRIBUTING.md` §"Repository layout".
  **Landed 2026-09-10 — §15.1.** All four, and the `CONTRIBUTING.md` bullet at `:37-39`.
- **`shellcheck` still has not run** against `scripts/check-skill.sh` (§13.4); it is not installed on
  this machine.

---

## 15. Amendment — what phase 4 landed

Written 2026-09-10, after phase 3 (§14). §6.1, §6.2, §6.3, §6.4, §7 and §14.5 each take a line
pointing here. Phase 4 is the last row of §7's table.

### 15.1 What was written

| file | what it holds |
| --- | --- |
| `evals/<case>/prompt.md`, six of them, 26–38 lines each | §6.2's six cases. Frontmatter carries `description`, `tags`, `allowed_tools`, `max_turns` and `expected_outcome`; the body is the user prompt, with the build script or the failure text inline, because a run has no repository to read (§6.3, condition 1) |
| `evals/<case>/graders/*.md`, twenty-one | per case: one `tool_used` grader on `Skill` marked `arm: with-only`, one `llm` grader whose body is the pass criteria, and one or two `regex` graders on exact tokens — `kspMocksTargets`, `kspTest\s*\(`, `add\(\s*"kspJvmTest"`, `eventsChannel\.close\(\)`, `loadHandler\s*=` |
| `.gitignore` (`:13-14`) | `evals/results/`, which a run writes |
| `CONTRIBUTING.md` (96 → 112 lines) | the `evals/` bullet in §"Repository layout" (`:37-39`), which §14.4 deferred to this phase, and the eval-suite paragraphs under §"Running the build" (`:101-112`) |

No file phase 4 wrote is read by `./gradlew build` or by CI. `scripts/check-skill.sh` reads
`evals/` through K13, which until this commit reported "no evals/ directory yet (phase 4)".

### 15.2 The case format, read from the binary

`claude plugin eval init --bare sample-case`, run 2026-09-10 on Claude Code 2.1.267 in an empty
directory, printed ``plugin eval` is currently in early access` and wrote nothing. So the runner
authors no template either, and the case files were written against the schema inside the binary:

- **`prompt.md` frontmatter** accepts `schema_version`, `name`, `description`, `tags`, `plugins`,
  `runs` and `expected_outcome` as case fields, and `model`, `max_turns`, `timeout_seconds`,
  `allowed_tools`, `artifact_publish`, `growthbook_overrides`, `append_system_prompt` and `env` as
  execution fields. Any other key is the error `prompt.md: unknown frontmatter key`.
- **`graders/<name>.md`** takes its grader name from the filename. `type` is required and is one of
  `regex`, `tool_order`, `tool_used`, `file_exists`, `llm`, `baseline`; each type's object is
  strict, so an unknown key fails the case rather than being ignored.
- **The body fills one field**: `pattern` for a `regex` grader, `criteria` for `llm` and `baseline`,
  when the frontmatter does not carry it. All twenty-one graders use the body for that field, so a
  pattern or a criteria paragraph is never quoted in YAML.
- **A case with no `case.yaml`** is given `schema_version` 1.1 and the directory name as its name.
- **Defaults that the cases rely on**: `runs` 3, `timeout_seconds` 300, a `regex` grader's `target`
  `last_message` and its `match` `contains`.

A scratch script mirroring those rules checked all twenty-eight files before the runs. It is not in
the repository: K13 is the check that ships, and the runner is the check when it opens (§15.7).

### 15.3 The twelve runs

Claude Code 2.1.267, model `sonnet`, 2026-09-10. Each arm ran in its own `mktemp -d`:

```bash
claude -p --restricted --strict-mcp-config --allowedTools "Read,Glob,Grep,Skill" \
  --permission-prompts none --model sonnet --output-format stream-json --verbose \
  [--plugin-dir /Volumes/…/kotlin-ksp-mocks] "<the prompt.md body>"
```

§6.3's three conditions, as they came out:

1. **The run directory was `/var/folders/kb/…/T/tmp.<random>`**, which does not name this
   repository. Four without-arms ran `Glob` or `Grep` there and matched nothing; the
   `generic-interface-refused` arm reported "no gradle files, no matches for `kspMocksTargets`".
2. **`--restricted` removed Bash, and `WebSearch` was denied five times** across the three setup
   cases' without-arms. The `wire-a-jvm-module` with-arm ran `ToolSearch` for `Bash`, `PowerShell`
   and `WebFetch`, found none, and wrote "I don't have shell access in this session, so I can't run
   that curl for you" — the behaviour condition 2 predicts.
3. **No with-arm opened a `references/*.md`.** Every one of the six fired `Skill` exactly once and
   answered from `SKILL.md` alone, so no case needed `--add-dir`.

| case | grader | without | with |
| --- | --- | --- | --- |
| `wire-a-jvm-module` | `skill-fired` | ✘ 0x | ✔ 1x |
|  | `names-the-test-configuration` | ✘ | ✔ |
|  | `names-the-target-option` | ✘ | ✔ |
|  | `criteria` (llm, read by hand) | ✘ | ✔ |
|  | the run | 4 turns, $0.1747 | 5 turns, $0.0928 |
| `interfaces-in-another-module` | `skill-fired` | ✘ 0x | ✔ 1x |
|  | `names-the-target-option` | ✘ | ✔ |
|  | `lists-the-core-fqn` | ✘ | ✔ |
|  | `criteria` | ✘ | ✔ |
|  | the run | 4 turns, $0.0710 | 3 turns, $0.0599 |
| `multiplatform-module` | `skill-fired` | ✘ 0x | ✔ 1x |
|  | `adds-the-jvm-test-configuration` | ✘ | ✔ |
|  | `names-the-jvm-test-source-set` | ✔ | ✔ |
|  | `criteria` | ✘ | ✔ |
|  | the run | 4 turns, $0.0717 | 3 turns, $0.0654 |
| `flow-test-hangs` | `skill-fired` | ✘ 0x | ✔ 1x |
|  | `names-the-close` | ✔ | ✔ |
|  | `criteria` | ✔ | ✔ |
|  | the run | 1 turn, $0.0378 | 4 turns, $0.0730 |
| `handler-expected-to-be-set` | `skill-fired` | ✘ 0x | ✔ 1x |
|  | `names-the-handler` | ✔ | ✔ |
|  | `criteria` | ✘ | ✔ |
|  | the run | 4 turns, $0.0704 | 3 turns, $0.0647 |
| `generic-interface-refused` | `skill-fired` | ✘ 0x | ✔ 1x |
|  | `names-a-way-forward` | ✔ | ✔ |
|  | `criteria` | ✘ | ✔ |
|  | the run | 3 turns, $0.0596 | 3 turns, $0.0628 |

Twelve runs, $0.9040. The `regex` and `tool_used` columns were scored mechanically from the
transcripts; each `criteria` column is a reading of that arm's last message against the grader's
numbered criteria.

### 15.4 What the comparison showed — supersedes §6.4

**The with-arm answered all six correctly**, on every numbered criterion. Three answers are the
ones §11 measured and §4 wrote down: `add("kspJvmTest", …)` with the note that the typed accessor
does not compile, plus "**Can the test live in `commonTest`? No.**"; the unset-handler fallbacks in
order (`Unit`, the `Flow` channel, `null`, a guessable default, then the throw); and
`RepositoryMock` in `com.example.core`, the interface's package rather than the test's.

**§6.4's without-arm hypothesis is wrong for the three setup cases.** It expected MockK or Mockito.
Instead all three refused to answer at all, having been denied the web search that would have
confirmed the library:

> I don't have reliable, verified knowledge of `dev.modaal` or its "mocks-processor" — it's not a
> library I can confirm from training (unlike e.g. MockK or Mockative), and I couldn't check
> because web search is blocked in this session. […] I don't want to hand you fabricated Gradle
> coordinates, plugin IDs, annotation names, or a guessed output path.

So the three setup cases measure whether the arm answers at all. §6.4 expected them to measure
which library the arm reaches for. Whether an unrestricted without-arm — one that can search the
web and read this repository's README — arrives at the same wiring is not measured here; §6.3's
condition 1 rules that arm out by construction.

**MockK did appear, in a diagnostic case.** The `generic-interface-refused` without-arm offered
`mockk<Cache<String>>()` as its second option, "since JVM generics are erased at runtime". Its
first option was `interface StringCache : Cache<String>`, a non-generic sub-interface rather than
the wrapper the skill teaches. `KspMocksProcessor.kt:88-91` reads `getAllFunctions()` and
`getAllProperties()`, which include inherited members, so that sub-interface is plausibly
renderable — phase 4 did not build one, and §15.7 keeps it open. The criterion this arm failed is
the first: it attributed the refusal to what "many codegen-based mock generators" do rather than to
this processor's rule, and asked whether `kspMocksTargets` accepts `"com.example.core.Cache<String>"`.

**`handler-expected-to-be-set` separates the arms on its second question.** Both arms reach
`environment.loadHandler = { id -> FeedPage(id) }`, and the without-arm then answers the second
question wrongly: "Realistically only for methods returning `Unit`" — missing the `Flow` channel,
the nullable return and the guessable defaults. The with-arm lists all five rules in order.

**`flow-test-hangs` does not discriminate.** Both arms name the unclosed channel and write
`environment.eventsChannel.close()`; the without-arm needed one turn and no tool call. Its prompt
hands over the vocabulary — `eventsChannel`, `FeedEnvironmentMock`, `UncompletedCoroutinesError` —
so what is left to measure is general Kotlin knowledge of `Channel`-backed flows. The case stays as
written, and §15.7 records what would have to change for it to measure the skill.

### 15.5 One grader removed after the runs

`evals/wire-a-jvm-module/graders/no-mocking-library.md` was written as a `not_contains` regex on
`\b(mockk|mockito)\b` over the last message. The without-arm named MockK while refusing to use it
(§15.4's quote), so the grader reds an answer for *mentioning* a library rather than for proposing
one — and it would red a correct with-arm answer that tells an adopter they can drop MockK. The
`criteria` grader in the same case already carries the intent: "It must not propose MockK, Mockito,
or a hand-written fake." The file was deleted before the commit; twenty-one graders remain, and
`wire-a-jvm-module` keeps four.

### 15.6 The gate

| command | result |
| --- | --- |
| `scripts/check-skill.sh` | thirteen green. K13 reads the six cases and reports "every eval case carries a prompt body and a usable grader" instead of "no evals/ directory yet (phase 4)" |
| `scripts/check-skill.sh --self-test` | thirteen red against their seeded violations, then thirteen green. K13's seed — an `evals/seeded-case/prompt.md` with no `graders/` — reds a tree that now also holds the six real cases, because `seed()` copies `evals/` when it exists |
| the twelve runs | §15.3 |
| `./gradlew build` | exit 0, unaffected |

### 15.7 Still open after phase 4

- **The runner has never scored these cases.** `claude plugin eval` and `claude plugin eval init`
  both refuse at 2.1.267 (§15.2). The frontmatter was checked against the binary's schema by a
  scratch script, not by the runner; the first real run is also the first check of the case files
  themselves.
- **`flow-test-hangs` measures general Kotlin knowledge, not the skill** (§15.4). Making it
  discriminate means a prompt that does not name `eventsChannel` — the symptom alone, and the
  interface — which is a rewrite of the case, not a grader change.
- **Whether `interface StringCache : Cache<String>` renders is unmeasured** (§15.4). If it does, the
  skill's generics row gains a second way forward; the measurement is one scratch project of the
  kind §11.1 used.
- **The runs measured one model.** `--model sonnet` for all twelve; no other model was run.
- **A case cannot ask for an edit.** The `flow-test-hangs` with-arm tried to `Edit`
  `FeedEnvironmentTest.kt` in the run directory and got "File does not exist" — the by-hand form
  scaffolds nothing. A case that grades a written edit needs the runner's `scaffold_script` and
  `--scaffold`.
- **`shellcheck` still has not run** against `scripts/check-skill.sh` (§13.4, §14.5); it is not
  installed on this machine.
