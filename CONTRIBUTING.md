# Contributing

## This repository is public

Everything here — code, comments, commit messages, test names, release
notes — is world-readable the moment it is pushed. Write for a reader who
has never seen the projects this tool is used in: no private project names,
no internal file paths, no references to internal planning documents or
their numbering. Findings and measurements are welcome; describe them
without naming where they came from.

## The mock dialect is a contract

The generated member vocabulary (`<fn>CallCount` / `<fn>Args` /
`<fn>Handler`, `<prop>GetCount`/`<prop>GetHandler`/`<prop>SetCount`/
`<prop>SetArgs`/`<prop>SetHandler` and the `_<prop>` store, the channel-backed `Flow` shape and the six members that count
what crosses it — `SubscribeCount`, `SubscribeCancelCount`, `OutputCount`,
`Outputs`, `OutputHandler`, `CompletionCount` — the constructor-seeded bag, and
the exact unset-handler failure string
`"<fn>Handler expected to be set."`) is shared with the Swift twin,
[swift-sourcery-templates](https://github.com/modaal-agent/swift-sourcery-templates).
Changing any of it is a cross-repo decision, not a local refactor: the unit
tests in `mocks-processor` pin the vocabulary and the strings on purpose.

## Repository layout

- `mocks-processor/` — the KSP processor and the unit tests that render
  hand-built models.
- `receipt/` — the consumer module. Its tests compile and run mocks the
  processor generated during this build's own test compilation.
- `skills/kotlin-ksp-mocks/` — the agent skill an adopter installs, and its
  four `references/` files. README.md §"Agent skill" lists the four channels
  it installs through.
- `gradle/print-mock-api.init.gradle.kts` — the Gradle init script an
  adopter's agent downloads from a release tag and passes with `-I` to run
  `printMockApi`. It is code.
- `.claude-plugin/marketplace.json` — the marketplace, whose one plugin is
  `skills/`. `skills/.claude-plugin/` holds that plugin's `plugin.json` and the
  icon its directory listing shows. The plugin root is `skills/`, so an install
  or a directory listing reads the skill and none of the processor's sources.
- `scripts/` — `publish-maven.sh` for a release, `check-skill.sh` for the
  skill gate, `check-print-mock-api.sh` for the init script.
- `evals/` — eight cases that measure what an agent answers with the skill
  loaded and without it. One directory per case, holding `prompt.md` and
  `graders/*.md`; `evals/README.md` carries the case table and the last round.
  A run writes `evals/results/`, which is git-ignored.
- `specs/NNN-slug/spec.md` — the plan, the measurements and the decisions
  behind a change too big to carry in a commit message.

## Development rules

1. **Two test layers, both required.** Emission rules are unit-tested in
   `mocks-processor` against hand-built models; `:receipt` compiles and runs
   the real generated output. A new emission shape lands with a case in
   each.
2. **Byte-deterministic output.** Members are emitted name-sorted; nothing
   in rendering may read clocks, unstable-ordered maps, or absolute paths.
   The determinism test renders shuffled models and asserts identical bytes.
3. **Test-classpath only.** The processor must stay consumable via
   `kspTest`/`kspJvmTest` with no artifact on the consumer's main
   classpath. Features that require a main-classpath annotation are out of
   scope by design.
4. **Published versions are derived from the tag.** The publish workflow
   passes `-PpublishVersion=<tag>`; the `-SNAPSHOT` literal in
   `build.gradle.kts` is the `publishToMavenLocal` development default and
   moves in the commit that gets tagged.
5. **The published jar targets Java 17, not the build's toolchain.**
   `publishedBytecodeTarget` in `mocks-processor/build.gradle.kts` is the one
   place that number lives; the toolchain a maintainer builds on is separate
   and may move on its own. `checkPublishedBytecodeVersion` reads the shipped
   jar and fails above the target, so raising it is a deliberate edit with a
   changelog line, not a side effect of a toolchain bump.

6. **A release is atomic and immutable.** `scripts/publish-maven.sh` stages,
   asserts completeness, and refuses to overwrite a published version; a bad
   release is followed by a new version, never a rewrite.

7. **The skill is held to the processor it documents.**
   `skills/kotlin-ksp-mocks/` is written for an agent in a repository that
   consumes the processor, not for a contributor. `scripts/check-skill.sh`
   compares every member name and every diagnostic string it quotes against
   `MockRenderer.kt` and `KspMocksProcessor.kt`, and the wiring it teaches
   against `receipt/build.gradle.kts`, so a rename in the renderer lands with
   the skill edit in the same commit. `scripts/check-print-mock-api.sh` runs
   `gradle/print-mock-api.init.gradle.kts`, the init script the skill
   downloads, on `:receipt` and fails unless it compiles nothing of the module
   and prints the files the test compilation generated.

8. **A change under `skills/` raises the plugin's version in the same PR.**
   `skills/` is the root of the Claude Code plugin `kotlin-ksp-mocks`, and
   `version` in `skills/.claude-plugin/plugin.json` is the plugin's own semver,
   independent of the processor's release tags. Raise the patch number for a
   correction, the minor number for new guidance, a new reference or a change
   to a command the skill tells the agent to run, and the major number when a
   skill is renamed or removed. A PR that leaves `skills/` unchanged
   leaves the version. Claude Code installs a plugin update only when the
   version string changes, so an edit without a raise reaches no user;
   `scripts/check-skill.sh` fails (K12) when a file under `skills/` changed
   since the commit that set the current version.

9. **`skills/` holds Markdown, text, JSON, YAML and images only.** The
   plugin directory refuses a version from an organization it has not
   verified when a file under the plugin's root has another type, starts with
   `#!`, is a package manager's configuration file, or is not UTF-8. A tool
   the skill runs lives outside `skills/`, is published by the release tag,
   and is downloaded by the command the skill teaches:
   `gradle/print-mock-api.init.gradle.kts`, fetched from
   `raw.githubusercontent.com` at the tag equal to the module's processor
   version, so it stays at that path. `scripts/check-skill.sh` fails (K15) on
   a file under `skills/` the directory would refuse.

## Running the build

```
./gradlew build
```

JDK 25. The `:receipt` module's generated sources land under
`receipt/build/generated/ksp/` — read them there when iterating on the
renderer; they are build products and never committed.

The skill and the two plugin manifests are checked separately, with no JDK and
no Gradle:

```
scripts/check-skill.sh              # the fifteen checks the `skill` job runs
scripts/check-skill.sh --self-test  # each check against a seeded violation
```

Run `--self-test` after editing a check: it copies the tree to a temporary
directory twenty-three times, seeds one violation of one check in each copy, and
fails if the check that violation targets stays green.

`gradle/print-mock-api.init.gradle.kts` needs the JDK and the build's output,
so it is checked after the build:

```
./gradlew build
scripts/check-print-mock-api.sh              # G1–G4, which the `build` job runs
scripts/check-print-mock-api.sh --self-test  # each check against a seeded violation
```

G1 fails when the task's dry run lists a `:receipt` task other than
`printMockApi`, G2 when the task exits non-zero, G3 when a file it wrote
differs from the one under `receipt/build/generated/ksp/test/kotlin/`, and G4
when its output does not carry one of those files whole.

The eight cases under `evals/` measure the skill rather than the processor: each
holds a prompt an adopter's agent might be given, and is run twice — once with
the skill loaded, once without — so the two answers can be compared.

```
run=$(mktemp -d) && cp -R skills/. "$run" && cp -R evals "$run/evals"
claude plugin eval "$run" --ablation with-without --runs 1 --no-publish \
  --output-dir "evals/results/$(date +%Y-%m-%dT%H-%M-%S)"
```

The runner reads eval cases only below the plugin it runs, and the plugin root
is `skills/`, so the run copies the plugin and `evals/` into one temporary
directory. [evals/README.md](evals/README.md) carries the options, the by-hand
form of the run and what the last round measured, case by case; `specs/001-agent-skill/spec.md` §15
carries the same round with the schema the case files were written against. No
CI job runs the cases: they cost model calls, and `check-skill.sh`'s K13 parses
them instead.
