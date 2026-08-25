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
5. **A release is atomic and immutable.** `scripts/publish-maven.sh` stages,
   asserts completeness, and refuses to overwrite a published version; a bad
   release is followed by a new version, never a rewrite.

## Running the build

```
./gradlew build
```

JDK 25. The `:receipt` module's generated sources land under
`receipt/build/generated/ksp/` — read them there when iterating on the
renderer; they are build products and never committed.
