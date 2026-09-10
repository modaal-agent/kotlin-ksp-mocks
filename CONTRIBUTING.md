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
`<fn>Handler`, `<prop>SetCount`, `<prop>GetCount`/`<prop>GetHandler`, the
channel-backed `Flow` shape, the constructor-seeded bag, and the exact
unset-handler failure string `"<fn>Handler expected to be set."`) is shared
with the Swift twin,
[swift-sourcery-templates](https://github.com/modaal-agent/swift-sourcery-templates).
Changing any of it is a cross-repo decision, not a local refactor: the unit
tests in `mocks-processor` pin the vocabulary and the strings on purpose.

## Repository layout

- `mocks-processor/` — the KSP processor and the unit tests that render
  hand-built models.
- `receipt/` — the consumer module. Its tests compile and run mocks the
  processor generated during this build's own test compilation.
- `skills/kotlin-ksp-mocks/` — the agent skill an adopter installs, and its
  three `references/` files. README.md §"Agent skill" lists the four channels
  it installs through.
- `.claude-plugin/` — `marketplace.json` and `plugin.json`. They make the
  repository root one plugin, because a plugin reads `skills/` inside its own
  root and cannot be pointed above it.
- `scripts/` — `publish-maven.sh` for a release, `check-skill.sh` for the
  skill gate.
- `evals/` — six cases that measure what an agent answers with the skill
  loaded and without it. One directory per case, holding `prompt.md` and
  `graders/*.md`. A run writes `evals/results/`, which is git-ignored.
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
   the skill edit in the same commit.

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
scripts/check-skill.sh              # the thirteen checks the `skill` job runs
scripts/check-skill.sh --self-test  # each check against a seeded violation
```

Run `--self-test` after editing a check: it copies the tree to a temporary
directory thirteen times, seeds one violation of one check in each copy, and
fails if the check that violation targets stays green.

The six cases under `evals/` measure the skill rather than the processor: each
holds a prompt an adopter's agent might be given, and is run twice — once with
the skill loaded, once without — so the two answers can be compared.

```
claude plugin eval ./
```

The runner is in early access at Claude Code 2.1.267 and refuses to run, so
`specs/001-agent-skill/spec.md` §15 carries the by-hand form of the run and what
the twelve runs measured. No CI job runs the cases: they cost model calls, and
`check-skill.sh`'s K13 parses them instead.
