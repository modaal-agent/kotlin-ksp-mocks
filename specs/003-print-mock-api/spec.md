# 003 — Print a mock's members without compiling the module

**Status:** Proposed. Nothing here is implemented. §1 is what was measured, §2 states the rule this
proposes, §3 records eight decisions with the options considered and a recommendation in each —
none is ruled — and §4 phases the work.

**Ruled on 2026-09-12: D1 (a) and D3 (c). P1 and the release are not taken; the work is the init
script and the skill text — §9.**

**Implemented on 2026-09-12 — §10:** the init script, its gate, K14, the skill text and the seventh
eval case, on `spec/003-print-mock-api`.

**Measurements:** every number, path, quoted line and listing in §1 was produced on 2026-09-12 on
macOS (Darwin 25.5.0) with Temurin 25.0.4.1, Gradle 9.7.1, Kotlin 2.4.10, KSP 2.3.11 and
kotlinx-coroutines 1.11.0, against `main` at `a7c995c`. Every time is the `real` time of one
`./gradlew` invocation on a warm daemon, configuration included, taken once unless the row says
otherwise. Two measurements ran in this checkout and changed no tracked file: the dry run of §1.1 and
the `:receipt` row of §1.4. Everything else ran in a scratch Gradle build outside the repository — "the
probe" — consuming `dev.modaal:mocks-processor:0.3.0` from `https://modaal-agent.github.io/maven`.
The probe's modules:

| module | what it is |
| --- | --- |
| `:feed` | Kotlin/JVM, one 43-line `src/main/kotlin/probe/Feed.kt`: `FeedEnvironment` (the ten requirements `Receipt.kt`'s `ReceiptEnvironment` declares), `FeedDependency` (two read-only properties), `Updater : Named` (two overloads of `update` and an inherited `name`), and a `FeedService` class using `FeedEnvironment`. `kspTest(…)`, one test |
| `:feedleak` | `:feed`'s main sources with the processor on `ksp(…)` instead of `kspTest(…)` |
| `:feedc` | `:feed`'s main sources in a synthetic `mockApi` compilation (§1.7) |
| `:core`, `:feature` | `probe.core.Repository` in `:core`; `:feature` depends on it with `implementation(project(":core"))` and wires `kspTest(…)` |
| `:kmp` | Kotlin Multiplatform with `jvm()`: `Loader` in `commonMain` (a `suspend` function and a `Flow` property), `Clock` in `jvmMain` (returns `java.time.Instant`), `add("kspJvmTest", …)` |
| `:big` | Kotlin/JVM, 400 generated files `F0.kt`–`F399.kt` of 43 lines each, 17,200 lines: each an interface `Service<i>` with three requirements and a class implementing it with 30 functions of the form `(0..x).map { … }.filter { … }.sum()` |

**Scope of the change:** a new `skills/kotlin-ksp-mocks/scripts/print-mock-api.init.gradle.kts`;
`mocks-processor/src/main/kotlin/dev/modaal/mocks/KspMocksProcessor.kt` and `MockRenderer.kt`, and
`MockRendererTest.kt`; a new `scripts/check-print-mock-api.sh`; the `build` job of
`.github/workflows/ci.yml`; `scripts/check-skill.sh` (one check, D8); `skills/kotlin-ksp-mocks/SKILL.md`,
a new `references/printing-members.md` and `references/troubleshooting.md`; a seventh case under
`evals/` and its row in `evals/README.md`; `README.md` §"Agent skill"; `CONTRIBUTING.md`
§"Repository layout", §"Development rules" and §"Running the build"; `AGENTS.md` and its byte copy
`CLAUDE.md`; `CHANGELOG.md`; and the `-SNAPSHOT` literal at `build.gradle.kts:17`.

**Narrowed on 2026-09-12 — §9.3:** no file under `mocks-processor/` changes, and neither `CHANGELOG.md`
nor `build.gradle.kts:17` does; §9.4 lists the files outside `skills/` that wait on a ruling.

**Not in scope:** the generated `<Interface>Mock.kt` a consumer's test compilation writes, which no
phase changes for any input (§2.6); the member vocabulary; `MockModel.kt`; how `kspTest` is wired;
a published Gradle plugin (D1 (b)); Android until P0 has measured it (D6).

**Obsoletes:** nothing.

**Follows:** a proposal made while working in an adopter's repository: "a small Gradle plugin exposing
`printMockApi`, running the processor into `build/tmp/mock-api/` outside every compiled source set,
printing the declarations". It reported the test-side KSP task at 3 s with `compileKotlin` in its
task graph, and the main-side `kspKotlin` at under 1 s with no task dependency and a byte-identical
mock. §1 re-measures each of its claims in the probe; D1 and D2 record where this spec departs from it.

---

## 0. TL;DR

1. To learn a mock's members today an agent runs the test-side KSP task and reads the generated file.
   That task requires `compileKotlin` (§1.1): on `:big` it took 23.1 s from `clean` and 23.1 s after a
   one-function edit, and it fails whenever `main` does not compile (§1.6).
2. The processor on `ksp(…)` has no such dependency and compiles 12 mock classes into the module's main
   jar (§1.2), which `CONTRIBUTING.md:55-58` rules out. The wiring cannot be a README snippet.
3. KSP 2.3.11 ships `com.google.devtools.ksp.cmdline.KSPJvmMain`, a command-line entry point that runs
   processors over source roots and a library classpath (§1.3). What a build has to supply is the
   classpath and the roots.
4. A Gradle task handing it the module's main and test source roots and the test compile classpath
   **configuration** depends on no task of the module, and wrote files byte-identical to the test
   compilation's in five cases: Kotlin/JVM with main roots, with main and test roots, multiplatform,
   interfaces in another module, and this repository's `:receipt` (§1.4).
5. It takes 1.6–1.8 s on a one-file module and 2.2 s on `:big`, from `clean` and after an edit (§1.5).
   It succeeds while `main` does not compile, and it generates for an interface the build does not list
   yet (§1.6).
6. The prototype that measured this is an **init script** passed with `-I`: it reads the processor
   version from `kspTest`, the targets from `ksp { arg(…) }` and the KSP version from the applied KSP
   Gradle plugin, so it edits no build file and names no version (§1.10).
7. The member declarations alone are 59 lines of a 154-line generated file (§1.9).
8. **The rule this proposes:** the skill ships `scripts/print-mock-api.init.gradle.kts`, and
   `./gradlew -I <that file> :<module>:printMockApi -q` prints, for each target, a member listing the
   processor writes when a second option is set; a processor that predates the option has its whole
   generated file printed instead (§2).
9. Eight decisions carry a recommendation and none is ruled (§3). Android, `testFixtures`, the Gradle
   version floor and the configuration cache are P0's measurements (§4, §7).
10. **Ruled on 2026-09-12 (§9):** D1 (a) and D3 (c) — the task prints the whole generated file, as the
    Swift twin's `mock-templates generate` writes its whole generated file. No processor change and no
    release; a later P1 would be 0.3.1.
11. **Implemented on 2026-09-12 (§10).** The script resolves the test compile classpath when the task
    runs and drops the module's own outputs, because declared as an input it compiled a multiplatform
    module's `main` and a `testFixtures` module's jar (§10.2).

---

## 1. Measured

### 1.1 What an agent runs today, and what that runs

`SKILL.md:33-40` gives the `find` that locates a generated mock, `references/gradle-wiring.md:234-236`
says to read the generated file when a member name is in question, and
`references/troubleshooting.md:243-244` says the same. The file exists after the test-side KSP task
has run.

In this checkout, `./gradlew :receipt:kspTestKotlin --dry-run` lists `:receipt:kspKotlin`,
`:receipt:compileKotlin`, `:receipt:compileJava`, `:receipt:processResources` and `:receipt:classes`
before `:receipt:kspTestKotlin`, beside `:mocks-processor`'s chain to `jar`. The probe's
`:feed:kspTestKotlin --dry-run` lists the same five `:feed` tasks. The cause is the one
`KspMocksProcessor.kt:31-36` records for selection: an interface declared in `main` reaches the test
compilation's KSP pass as a compiled binary, so that pass needs `main` compiled.

| module | `kspTestKotlin`, `compileKotlin` included |
| --- | --- |
| `:feed`, after appending one top-level function to `Feed.kt` | 0.71 s |
| `:big`, `compileKotlin` alone, first run of the daemon | 39.6 s |
| `:big`, from `clean` | 23.1 s |
| `:big`, after appending one top-level function to `F7.kt` | 23.1 s |
| `:big`, after two more top-level functions in `F7.kt` and one changed literal in `F8.kt` since its previous run | 44.8 s |

### 1.2 The processor on `ksp` compiles the mocks into the main artifact

`:feedleak` wires `ksp("dev.modaal:mocks-processor:0.3.0")` over `:feed`'s sources and targets.
`./gradlew :feedleak:kspKotlin --dry-run` lists no other task — the proposal's measurement holds. After
`./gradlew :feedleak:jar`, `build/classes/kotlin/main/probe/` holds 12 `*Mock*.class` files and
`build/libs/feedleak.jar` carries the same 12 entries: `FeedEnvironmentMock.class` (23,020 bytes),
`FeedDependencyMock.class`, `UpdaterMock.class`, `FeedEnvironmentMock$MeasureArgs.class`,
`UpdaterMock$UpdateIdForceArgs.class`, and seven lambda classes of `FeedEnvironmentMock`.

`CONTRIBUTING.md:55-58` (rule 3, test-classpath only) and `README.md:3-8` state that nothing reaches
the main classpath. A README snippet that moves the processor to `ksp` breaks both for any adopter who
copies it.

### 1.3 KSP ships a command-line entry point

`symbol-processing-aa-embeddable-2.3.11.jar` contains `com/google/devtools/ksp/cmdline/KSPJvmMain.class`
(beside `KSPCommonMain`, `KSPJsMain` and `KSPNativeMain`) and
`com/google/devtools/ksp/impl/KotlinSymbolProcessing.class`. Its POM declares three runtime
dependencies — `kotlin-stdlib` 2.3.20, `symbol-processing-api` 2.3.11 and
`symbol-processing-common-deps` 2.3.11 — so the one coordinate resolves the whole tool classpath.

`java -cp <those four jars> com.google.devtools.ksp.cmdline.KSPJvmMain --help` prints 28 options and
the processor classpath as the last positional argument. Twelve options are required:
`-module-name`, `-jvm-target`, `-language-version`, `-api-version`, `-source-roots`,
`-project-base-dir`, `-output-base-dir`, `-caches-dir`, `-class-output-dir`, `-kotlin-output-dir`,
`-java-output-dir`, `-resource-output-dir`. The optional ones used below are `-libraries`,
`-common-source-roots`, `-jdk-home` and `-processor-options`, a `key=value` map separated by the
platform path separator.

The KSP Gradle plugin's own task assembles the same classpath. `KspAATask$Companion.class` in
`symbol-processing-gradle-plugin-2.3.11.jar` carries the strings
`com.google.devtools.ksp:symbol-processing-aa-embeddable:`, `…:symbol-processing-api:`,
`…:symbol-processing-common-deps:` and `KSP_VERSION`; `KspAAWorkerAction` and
`IsolatedClassLoaderCacheBuildService` are classes in the same package; and
`com.google.devtools.ksp.gradle.KSPVersionsKt` declares `public static String getKSP_VERSION()`.

### 1.4 A run over the module's sources: no task of the module, the same bytes

A `JavaExec` task with `KSPJvmMain` as the main class, `symbol-processing-aa-embeddable:2.3.11` as
its classpath, the processor jar (resolved non-transitively) as the processor classpath,
`kspMocksTargets=<targets>` as the processor options, and every output directory under
`build/tmp/<task>/`. Each run was compared with `cmp` against the file the module's test-side KSP task
wrote.

| case | source roots | `-libraries` | files | `cmp` |
| --- | --- | --- | --- | --- |
| `:feed` | `src/main/kotlin` | `compileClasspath` | 3 | identical |
| `:feed`, test roots added — the test references `FeedEnvironmentMock`, which is unresolved in that run | `src/main/kotlin`, `src/test/kotlin` | `testCompileClasspath` | 3 | identical |
| `:kmp` | `commonMain`, `jvmMain`; `-common-source-roots` `commonMain` | `jvmCompileClasspath` | 2, against `build/generated/ksp/jvm/jvmTest/kotlin/` | identical |
| `:feature`, the interface in `:core` (init script, §1.10) | `main`, `test` | `testCompileClasspath` | 1 | identical |
| `:receipt` in this checkout, processor as `kspTest(project(":mocks-processor"))` (init script) | `main`, `test` | `testCompileClasspath` | 2 | identical |

What each run's dry run lists:

- `:feed`'s and `:kmp`'s tasks: themselves only.
- `:feature:printMockApi`: `:core:compileKotlin`, `:core:compileJava`, `:core:processResources`,
  `:core:classes`, `:core:jar`, then itself — nothing of `:feature`.
- `:receipt:printMockApi`: `:mocks-processor`'s chain to `jar`, then itself — nothing of `:receipt`.

The libraries have to be declared as a task input for Gradle to build a project dependency's jar. The
first `:feature` prototype passed the configuration inside the argument provider alone, and its dry
run listed no `:core` task; declared with `inputs.files(…).withNormalizer(ClasspathNormalizer::class.java)`,
it lists the five above. `:feature:kspTestKotlin` generated nothing while `:feature` held no test
source file (`references/troubleshooting.md:110-117` states that rule for `testFixtures`); the
comparison ran after one was added.

The resolvable **configuration** is what keeps the module's own compilation out. The test source set's
`compileClasspath` file collection adds `main`'s output to it, which is the dependency §1.1 measures.

### 1.5 What a run costs

| module | task | from `clean` | after an edit |
| --- | --- | --- | --- |
| `:feed` | `JavaExec` prototype, main roots | 1.68 s; a second run 1.63 s | 1.64 s |
| `:feed` | `JavaExec` prototype, main and test roots | 1.65 s | — |
| `:feed` | init script, `printMockApi` | 1.76 s | — |
| `:kmp` | `JavaExec` prototype / init script | 1.61 s / 1.70 s | — |
| `:receipt` | init script | 1.73 s | — |
| `:big` | init script | 2.18 s | 2.18 s after one top-level function; 2.22 s after an interface member |
| `:big` | synthetic compilation, `kspMockApiKotlin` (§1.7) | 1.23 s | 0.80 s |

Set against §1.1: on the one-file `:feed`, `kspTestKotlin` with `compileKotlin` (0.71 s) is faster
than the standalone run (1.64 s), whose `JavaExec` starts a JVM each time while the KSP Gradle plugin
runs in a worker inside the warm daemon. On `:big`, the standalone run is 2.18 s against 23.1 s.

An init script is compiled on the first run after its content changes: a dry run with a copy of the
prototype carrying one new comment line took 1.16 s, against 0.43 s for the same dry run without
`-I`.

### 1.6 A run while `main` does not compile, and a run for an unlisted interface

On `:feed`, after inserting `fun ping(): Int` into `FeedEnvironment` and appending
`fun broken(): Int = "not an int"` to `Feed.kt` — outside every interface:

- `./gradlew :feed:kspTestKotlin` exited 1 with
  `e: file:///…/feed/src/main/kotlin/probe/Feed.kt:46:21 Return type mismatch: expected 'Int', actual 'String'.`
- The standalone run exited 0, and its `FeedEnvironmentMock.kt` declares `pingCallCount`.

`-PmockApiTargets=probe.Named` — an interface `feed/build.gradle.kts:17` does not list in
`kspMocksTargets` — printed `NamedMock` and exited 0. The run passes the targets to the processor
directly and does not read the module's `ksp {}` block when the property is set.

### 1.7 A synthetic compilation runs KSP's own task

`:feedc` declares `kotlin.target.compilations.create("mockApi")`, adds `src/main/kotlin` of `:feed` to
its default source set, and `add("kspMockApi", "dev.modaal:mocks-processor:0.3.0")`. The KSP Gradle
plugin registers `kspMockApiKotlin` for it.

- `./gradlew :feedc:kspMockApiKotlin --dry-run` lists that task alone; it ran in 0.70 s and wrote 3
  files identical to `:feed`'s test compilation.
- `./gradlew :feedc:build --dry-run` lists no `mockApi` task.
- `./gradlew :feedc:tasks --all` lists five that did not exist before: `compileMockApiJava`,
  `compileMockApiKotlin`, `kspMockApiKotlin`, `mockApiClasses`, `processMockApiResources`.
- The module gains a source set named `mockApi`, whose Kotlin directory is also `main`'s, and a second
  copy of each mock under `build/generated/ksp/mockApi/kotlin/`, which `SKILL.md:36`'s `find` returns
  beside the test compilation's copy.

How IntelliJ imports a directory that two source sets share was not measured.

### 1.8 What a failure prints

- **A target that does not resolve.** With `-PmockApiTargets=probe.Nope,probe.FeedDependency` the run
  prints `e: [ksp] kspMocksTargets: probe.Nope is not resolvable in this compilation` — the
  processor's own diagnostic, `KspMocksProcessor.kt:63` — writes `FeedDependencyMock.kt` for the other
  target, and `KSPJvmMain` exits 1. The `JavaExec` prototype then fails with Gradle's
  `Process 'command '/Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home/bin/java'' finished with non-zero exit value 1`.
  The init script sets `isIgnoreExitValue = true`, prints `// probe.Nope: no mock generated` and the
  `FeedDependency` listing, and then fails with its own line.
- **A module whose processor is not on the test-side configuration.** On `:feedleak` the init script
  fails at task creation: `Could not create task ':feedleak:printMockApi'.` /
  `printMockApi: :feedleak has no mocks-processor on kspTest or kspTestFixtures`.
- **JDK warnings.** On Temurin 25 every run prints four `WARNING:` lines naming
  `sun.misc.Unsafe::objectFieldOffset` called by `ksp.com.intellij.util.containers.Unsafe`. The JVM
  argument `--sun-misc-unsafe-memory-access=allow` removes all four. A JDK older than 23 does not
  accept that argument; the prototype adds it only for a launcher of 23 or newer, and the older branch
  was not run.
- **Configuration cache.** The `JavaExec` build-script prototype reported
  `2 problems were found storing the configuration cache`. The init script was not run with
  `--configuration-cache`.

### 1.9 The member declarations alone

The prototype's rule: keep the class header and constructor; keep each line at two-space indentation
that starts with `override `, `var `, `val `, `data class ` or `// `, and a nested data class's
parameter lines; drop a trailing ` {`, a trailing ` =` and a trailing ` = <initializer>`. It is the
member-indentation rule `DeclaredNames.DECLARATION` reads (`MockRenderer.kt:178-184`) plus
`override fun`, the naming comment and the class header.

| generated file | lines / characters | listing | ratio |
| --- | --- | --- | --- |
| `FeedEnvironmentMock.kt` | 154 / 6,100 | 59 / 2,911 | 2.6× lines |
| `UpdaterMock.kt` | 42 / 1,662 | 18 / 823 | 2.3× |
| `FeedDependencyMock.kt` | 31 / 1,060 | 11 / 372 | 2.8× |
| `:receipt`'s two files | 185 lines | 74 lines printed, two `// <fqn>` lines and two blank lines included | 2.5× |

`UpdaterMock`'s listing as the init script printed it:

```
// probe.Updater
class UpdaterMock : probe.Updater
  override val name: kotlin.String
  var nameGetCount: kotlin.Int
  var nameGetHandler: (() -> kotlin.String)?
  var _name: kotlin.String
  override fun update(id: kotlin.String)
  var updateCallCount: kotlin.Int
  val updateArgs: kotlin.collections.MutableList<kotlin.String>
  var updateHandler: ((kotlin.String) -> kotlin.Unit)?
  data class UpdateIdForceArgs(
    val id: kotlin.String,
    val force: kotlin.Boolean,
  )
  // `update(id, force)` members are named updateIdForce* — overload of update, parameter names appended
  override fun update(id: kotlin.String, force: kotlin.Boolean)
  var updateIdForceCallCount: kotlin.Int
  val updateIdForceArgs: kotlin.collections.MutableList<UpdateIdForceArgs>
  var updateIdForceHandler: ((kotlin.String, kotlin.Boolean) -> kotlin.Unit)?
```

### 1.10 The init-script prototype, and what it reads from the build

One Kotlin DSL file, passed as `./gradlew -I <file> :<module>:printMockApi -q`. It registers
`printMockApi` on every project that applies `com.google.devtools.ksp`, and configures it when the task
is realized, after the module's build script has run. Output printed from the task action appears under
`-q`.

| input | where it comes from | access |
| --- | --- | --- |
| the processor | the `mocks-processor` dependency declared on `kspTest` or `kspTestFixtures` (`kspJvmTest` in a multiplatform module), copied into a detached, non-transitive configuration | Gradle API |
| the targets | `-PmockApiTargets`, else `kspMocksTargets` from `KspExtension.getArguments()` | reflection |
| the KSP version | `KSPVersionsKt.getKSP_VERSION()`, loaded through the KSP plugin instance's class loader | reflection |
| `-language-version`, `-api-version` | major and minor of the Kotlin extension's `getCoreLibrariesVersion()` | reflection |
| source roots, Kotlin/JVM | the `kotlin` `SourceDirectorySet` on `SourceSetContainer`'s `main` and `test` | Gradle API |
| source roots, multiplatform | `getSourceSets()` on the Kotlin extension, `getKotlin()` on `commonMain`, `jvmMain`, `commonTest`, `jvmTest`; the two `common` sets as `-common-source-roots` | reflection |
| `-libraries` | `testCompileClasspath`, or `jvmTestCompileClasspath`, declared as a classpath input | Gradle API |
| `-jdk-home`, the launcher | the `java.toolchain` of the module | Gradle API |

An init script is compiled against the Gradle API. The KSP and Kotlin Gradle plugin classes are loaded
by the build's own plugin class loaders, so the script reaches them by name, and a rename of any of the
five reflective members in a later plugin release fails the lookup.

No version literal appears in the file: every version comes from the build it runs in.

§10.2: the `-libraries` row, declared as a classpath input, makes the task compile `:kmp`'s `main`
and `:fix`'s jar. The script that shipped resolves the configuration when the task runs — §10.1.

### 1.11 How an agent finds a script bundled with a skill

Documented, not run in a session here:

- `https://code.claude.com/docs/en/skills.md`: "`${CLAUDE_SKILL_DIR}` — Directory containing the
  skill's `SKILL.md` file, e.g. `${CLAUDE_SKILL_DIR}/scripts/helper.py`", substituted in skill content
  and in `allowed-tools` Bash rules; and `${CLAUDE_PLUGIN_ROOT}` for a skill installed with a plugin.
- `https://agentskills.io/specification`: "`scripts/` — Contains executable code that agents can run",
  referenced "using relative paths from the skill root". The standard names no variable, so an agent
  other than Claude Code resolves the path against the directory it read `SKILL.md` from.

### 1.12 The budgets a skill change lands against

- `SKILL.md` is 227 lines and 13,409 characters — the size `specs/002-property-accessors-and-stream-counters/spec.md`
  §13.5 measured at **~4.8k tokens** on invoke. `AGENTS.md:161-173` holds it near 4.8k against the
  5,000-token floor, and check K5 holds it under 400 lines.
- The three references are 250 (`generated-api.md`, at K5's cap), 236 (`gradle-wiring.md`) and 244
  (`troubleshooting.md`) lines against 250.
- The frontmatter `description` is 705 characters against K4's 1,024.
- K10 greps every file under `skills/` for `x.y.z` (`scripts/check-skill.sh:395`), a script included.
- `scripts/check-skill.sh` runs with no JDK (`:15-16`), so it cannot run a Gradle script; the `build`
  job provisions JDK 25 (`.github/workflows/ci.yml:50-53`).
- `AGENTS.md:98-101` lists the skill tree among the changes that touch no code and may go straight to
  `main`.

---

## 2. The rule this proposes

### 2.1 One init script in the skill, one task

```bash
./gradlew -I "${CLAUDE_SKILL_DIR}/scripts/print-mock-api.init.gradle.kts" :<module>:printMockApi -q
```

`skills/kotlin-ksp-mocks/scripts/print-mock-api.init.gradle.kts` registers `printMockApi` on every
project that applies `com.google.devtools.ksp`. An adopter edits no `settings.gradle.kts` and no
`build.gradle.kts`, and nothing in the adopter's repository names the script.

### 2.2 What it reads from the module

The processor dependency and the targets the module already declares (§1.10), and:

| module shape | processor looked up on | source roots | `-common-source-roots` | `-libraries` |
| --- | --- | --- | --- | --- |
| Kotlin/JVM; interfaces in another module | `kspTest` | `main`, `test` | — | `testCompileClasspath` |
| `testFixtures` | `kspTestFixtures` | `main`, `testFixtures` — P0 | — | `testFixturesCompileClasspath` — P0 |
| Kotlin Multiplatform, JVM tests | `kspJvmTest` | `commonMain`, `jvmMain`, `commonTest`, `jvmTest` | `commonMain`, `commonTest` | `jvmTestCompileClasspath` |
| Android, AGP 9 or AGP 8 | `kspTest` | P0 | — | P0 |

`-PmockApiTargets=<fqn>,<fqn>` replaces `kspMocksTargets` for that run, and may name an interface the
module does not list yet.

§10.2 measured the `testFixtures` row as written. The Android row is not in the script — §10.3.

### 2.3 What it runs

`KSPJvmMain` in a `JavaExec`, with `symbol-processing-aa-embeddable` at the applied KSP plugin's
`KSP_VERSION` as its classpath and the module's own processor dependency as the processor classpath.
Every output goes under `build/tmp/printMockApi/`, which no source set reads. The task is never up to
date, depends on no task of the module — only on the jars of project dependencies on the test compile
classpath — and passes `--sun-misc-unsafe-memory-access=allow` to a launcher of JDK 23 or newer.

### 2.4 What it prints

For each target, in the order given: a line `// <fqn>`, the target's member listing, a blank line.

**The listing is the processor's.** When its options carry `kspMocksMemberListing=true`, the processor
writes `<package path>/<Interface>Mock.members.txt` beside each `<Interface>Mock.kt`, built from the
text it rendered by the rule of §1.9. The rule lives in `MockRenderer.kt` beside
`DeclaredNames.DECLARATION`, so one expression decides which lines are member declarations for the
collision check and for the listing. Only `printMockApi` sets the option.

**A processor that does not know the option** — 0.3.0 and every release before the one P1 lands in —
writes no listing, and the task prints the whole generated `<Interface>Mock.kt` for that target.

§1.9's listing is the prototype's output. P1's `MockRendererTest` cases pin the processor's.

**Superseded by §9.2:** the task prints the whole generated `<Interface>Mock.kt` for every target, and
no listing option exists.

### 2.5 How it fails

- The processor's diagnostics print as the build prints them, `e: [ksp] kspMocksTargets: …`. The task
  prints the listings of the targets that generated, then fails with one line naming the module and
  the number of targets that did not generate.
- A module with no `mocks-processor` dependency on the configuration §2.2 names fails when the task is
  created, naming the module and the configurations looked at.
- A module that applies KSP without the Kotlin JVM or multiplatform plugin — Android until P4 — fails
  naming the two shapes the task supports.
- A generic interface and a `vararg` parameter fail as they fail in the test compilation
  (`KspMocksProcessor.kt:71`, `:133`).

§9.2 changes the first bullet: the task prints the generated files of the targets that generated.

### 2.6 What does not change

Every `<Interface>Mock.kt` a consumer's test compilation writes, byte for byte, for every input — the
listing option is set by `printMockApi` alone. `kspMocksTargets`, the three wiring edits
(`SKILL.md:42-92`), the member vocabulary and the sets checks K6 and K7 compare, the diagnostics the
processor logs, `README.md:3-8`'s statement that nothing is committed and nothing reaches the main
classpath, and the published jar's class-file major 61.

§9.2: with no listing option, the consumer's `.kt` is unchanged because the processor is unchanged.

---

## 3. Decisions

None is ruled. Each lists the option recommended first.

**D1 and D3 ruled on 2026-09-12 — §9.1.** D2 and D4 to D8 are not ruled.

§10.1 records the option the implementation took in each of D2 and D4 to D8.

### D1 — where the task's code lives, and how a build gets it

- **(a) An init script under `skills/kotlin-ksp-mocks/scripts/`, passed with `-I` (recommended).**
  An adopter's build is not edited, and an agent in a repository wired before this change can print
  without touching it. The script reaches adopters when it lands on `main`, as the rest of the skill
  does (`README.md:153-157`), and reads every version from the build, so K10 holds over it. Costs:
  - code under `skills/`, so `AGENTS.md:98-101` has to name `skills/**/scripts/` as code and route it
    through a pull request;
  - five reflective lookups into KSP and Kotlin Gradle plugin classes (§1.10), each of which the
    script turns into a failure naming the member it looked for and the plugin version it found;
  - the adopter's Gradle compiles the script, and the oldest Gradle it compiles on is P0's measurement;
  - an agent outside Claude Code resolves the path without `${CLAUDE_SKILL_DIR}` (§1.11).
- **(b) A published Gradle plugin, `dev.modaal.mocks`, applied in the module's build script** — the
  proposal §"Follows" names. Typed access to the KSP and Kotlin extensions wherever the plugin shares
  their class loader scope, and TestKit tests in a new module. Costs:
  - a fourth wiring edit in every row of `SKILL.md:21-28` — the host in `pluginManagement` and the
    plugin id — before an agent can print, and an edit to the build of every repository wired today;
  - a second artifact and a plugin marker publication, whose group path `dev/modaal/mocks/` is a
    directory `scripts/publish-maven.sh:75-80` rejects as an unlisted coordinate today and `:125`'s
    metadata set omits;
  - a second jar for `checkPublishedBytecodeVersion` to read, and a floor on the Gradle API the plugin
    compiles against.
- **(c) The processor on `ksp(…)`, documented as wiring.** No new code. Measured to put 12 mock classes
  in the main jar (§1.2); `CONTRIBUTING.md:55-58` rules it out.
- **(d) A command-line tool outside Gradle.** `KSPJvmMain` is that tool (§1.3). The classpath it needs
  is resolved by the build, so a tool outside it would re-implement dependency resolution.

Moving from (a) to (b) later carries the script's behaviour into a plugin unchanged. The trigger is an
adopter asking for `printMockApi` without the skill installed.

**Ruled on 2026-09-12: (a) — §9.1.**

### D2 — how the task runs KSP

- **(a) `KSPJvmMain` in a `JavaExec` (recommended).** Every row of §1.4 and the init-script rows of
  §1.5 ran this way. It uses the options `--help` documents (§1.3) and starts a JVM per run: 1.6–2.2 s.
- **(b) A synthetic `mockApi` compilation, so the KSP Gradle plugin runs its own task (§1.7).** 0.8–1.2 s
  on `:big`, and the compilation's compiler settings come from the Kotlin Gradle plugin rather than
  from the script. It adds a source set, five tasks and a second generated copy to every module the
  script touches; an init script adds them whether or not `printMockApi` was requested unless it reads
  Gradle's start parameters; and AGP creates an Android module's compilations itself (not measured).
- **(c) `KotlinSymbolProcessing` in a Gradle worker with an isolated class loader cached across
  builds** — the arrangement `KspAAWorkerAction` and `IsolatedClassLoaderCacheBuildService` give KSP's
  own task (§1.3). Removes the per-run JVM start. Not measured, and more code than (a) in a file that
  no compiler reads before an adopter's Gradle does. The trigger to take it: an adopter reporting
  (a)'s wall time as the reason not to run the task.

### D3 — what the task prints

- **(a) The processor's member listing under an opt-in option, and the whole file from a processor
  that predates it (recommended).** The rule for which lines are declarations stays in
  `MockRenderer.kt` beside `DeclaredNames` and is pinned by `MockRendererTest`, so a later change to
  the rendered layout moves the listing in the same commit. An adopter on 0.3.0 gets the whole file:
  154 lines instead of 59 for `FeedEnvironmentMock` (§1.9). It needs a processor release, and a second
  processor option where `KspMocksProcessor.kt:19-22` documents one.
- **(b) The member listing, cut from the generated `.kt` by the script.** No processor change or
  release. The script then encodes the renderer's layout, while the processor it runs is whatever
  version the adopter declares; no gate compares the script's rule with a processor release other than
  the one on `main`.
- **(c) The whole generated file.** No listing rule anywhere; 2.3–2.8× the lines of (a) in §1.9's
  three files.

**Ruled on 2026-09-12: (c), where (a) was recommended — §9.1.**

### D4 — the source roots and the classpath

- **(a) The main and test source roots, and the test compile classpath configuration (recommended).**
  The view the test compilation's KSP pass has, minus the module's own compiled classes. Identical in
  every row of §1.4 that used it.
- **(b) The main source roots and `compileClasspath`.** Identical on `:feed` (§1.4 row 1). It does not
  see an interface declared in a test source set, or a supertype from a test-only dependency; no probe
  module declared either.

### D5 — which processor and which KSP the run uses

- **(a) Both read from the build (recommended):** the processor dependency the module declares, and
  `KSP_VERSION` of the KSP Gradle plugin the module applies. The printed members are those of the file
  the test compilation writes (§1.4), and the skill tree gains no version literal (K10).
- **(b) Versions named in the script.** K10 fails on the script, and the printed members can differ
  from what the adopter's build generates whenever the two versions differ.

### D6 — Android

- **(a) P0 measures AGP 9 and AGP 8 in scratch projects, and P4 adds the shape if the output is
  identical (recommended).** Until P4, the task on an Android module fails with the line §2.5 names.
  `references/gradle-wiring.md:104-139` carries the two majors' wiring and was measured in
  `specs/001-agent-skill/spec.md` §11.3.
- **(b) Android stays out of this spec.** `SKILL.md:25-26` carries two Android rows, and an agent in
  either would have no way to print.

### D7 — where the skill states it

- **(a) A section in `SKILL.md` and a new reference (recommended).** The section goes after §"What the
  generated mock gives a test" (`SKILL.md:94-146`), in at most ten lines: the command, that it compiles
  nothing and runs while `main` does not compile, and `-PmockApiTargets` for an interface not listed
  yet. The `description` gains the trigger "print or list a mock's members". `references/printing-members.md`
  carries each module shape of §2.2, the listing's format, the whole-file fallback and the failure lines
  of §2.5. `references/troubleshooting.md` gains a pointer to it in its 6 remaining lines. The tokens
  the section adds are paid for by compressing body text a reference already carries
  (`AGENTS.md:161-173`), and measured with `claude plugin details`.
- **(b) The reference alone, linked from §"References".** Spends none of the body's budget. An agent
  that does not open the reference does not learn the task exists.

### D8 — the gate

- **(a) A script the `build` job runs against `:receipt`, and one skill check (recommended).**
  `scripts/check-print-mock-api.sh`, run after `./gradlew build`, runs the init script on `:receipt`
  and fails unless it exits 0, every `.kt` under `receipt/build/tmp/printMockApi/kotlin/` is
  byte-identical to its counterpart under `receipt/build/generated/ksp/test/kotlin/`, and the printed
  output holds every declaration `DeclaredNames.DECLARATION` reads from those files. `--self-test`
  seeds one violation of each. `scripts/check-skill.sh` gains K14: the script path, the task name and
  the property name the skill quotes exist in the script. This gates Kotlin/JVM on Gradle 9.7.1;
  multiplatform stays measured in P0 and not in CI, as `specs/001-agent-skill/spec.md:560` decided for
  the wiring snippets.
- **(b) (a), plus a multiplatform fixture module in this build.** CI covers a second shape, and 001 §9's
  non-goal "Executable wiring fixtures" is reversed for that shape.

§9.4 records what D3 (c) changes in D7 (a) and D8 (a).

---

## 4. Phasing

Every phase lands on `spec/003-print-mock-api`, the branch this spec's commit starts, with the prefix
`[003-print-mock-api]`, and reaches `main` through one pull request (`AGENTS.md` §"Changes reach `main`
through a pull request"). The gate for every phase: `./gradlew build` before and after,
`scripts/check-skill.sh`, and from P2 on `scripts/check-print-mock-api.sh`.

**P0 — measure what §7 leaves open**, in scratch projects outside the repository, and append what it
found to this spec:

- Android on AGP 9 and on AGP 8: the source roots, the classpath configuration and the boot classpath
  a run needs, and whether its output is identical to `kspDebugUnitTestKotlin`'s.
- `testFixtures`: the two rows §2.2 marks P0.
- The oldest Gradle version the init script compiles and runs on, starting from the oldest the Kotlin
  and KSP Gradle plugins of an adopter's build support.
- A launcher older than JDK 23, where the warning argument is omitted.
- `--configuration-cache` on the init script.
- A `claude -p` session with the plugin installed that is asked for a mock's members: whether the agent
  runs the script through `${CLAUDE_SKILL_DIR}`, and which path an agent writes after
  `npx skills add`.
- An interface that only compiles with a compiler flag `KSPJvmMain` has no option for.

**P1 — the member listing in the processor (D3 (a)).**

- `kspMocksMemberListing`, spelled once beside `OPTION` (`KspMocksProcessor.kt:19-22`), and the KDoc
  updated from "the processor's only option".
- `MockRenderer` builds the listing from the rendered text with `DeclaredNames.DECLARATION` and the
  additions of §1.9; `KspMocksProcessor.generate` (`:60-97`) writes it through the `CodeGenerator` only
  when the option is `true`. Confirm on KSP 2.3.11 that a `txt` extension lands in the resource output
  directory.
- `MockRendererTest`: a listing for a property, a `Flow` member, a nested `<Fn>Args` class, a renamed
  overload and its comment, and the determinism test extended to it.
- `./gradlew build`: `:receipt`'s generated files unchanged byte for byte, because
  `receipt/build.gradle.kts` sets no such option.
- `CONTRIBUTING.md` §"Development rules" states the second option and that only `printMockApi` sets it.

**Not taken — §9.3.**

**P2 — the script (D1 (a), D2 (a), D4 (a), D5 (a)).**

- `skills/kotlin-ksp-mocks/scripts/print-mock-api.init.gradle.kts` for Kotlin/JVM, multiplatform and
  `testFixtures`, printing the listing or the whole file (§2.4), failing as §2.5 states.
- `scripts/check-print-mock-api.sh` and its `--self-test` (D8 (a)); a step in `ci.yml`'s `build` job.
- `AGENTS.md` §"Changes reach `main` through a pull request" names `skills/**/scripts/` as code, and
  §"The skill under `skills/` teaches adopters" names the gate that runs the script; then
  `cp AGENTS.md CLAUDE.md`.
- `CONTRIBUTING.md` §"Repository layout" and §"Running the build" name the script and its check.

P1 and P2 are independent: P2 without P1 prints whole files.

§10.1 records what P2 and P3 landed, and §10.2 which of P0's measurements ran.

**P3 — the skill text (D7 (a)).** `SKILL.md`'s section and `description`,
`references/printing-members.md`, the pointer in `references/troubleshooting.md`, K14 and its
`--self-test` seed, the token measurement of `AGENTS.md:161-173` with the body compressed to near
4.8k, and a seventh eval case, `list-a-mocks-members` — an adopter whose `main` does not compile asks
which members `FeedEnvironmentMock` will have — with a `regex` grader for `printMockApi`, an `llm`
criteria grader and a `skill-fired` grader, plus its row in `evals/README.md`. `README.md` §"Agent
skill" gains the paragraph naming the script. After P2.

**P4 — Android**, if P0 measured an identical output: the shape in the script, the row in §2.2, and the
skill's reference. After P0.

**P5 — release the processor part (§6).** After P1; P2 to P4 reach adopters when they land on `main`.

**Not taken — §9.3.** §9.3 lists what the shorthand keeps of P2 and P3, and §9.4 what waits on a
ruling.

---

## 5. What an adopter's agent does

1. **Nothing in the build.** The script reads the wiring the module already carries.
2. **Run it for the module whose tests need the mock:**
   `./gradlew -I "${CLAUDE_SKILL_DIR}/scripts/print-mock-api.init.gradle.kts" :<module>:printMockApi -q`.
3. **Read the listing** — the class header and constructor, then each member with its type — and write
   the test against those names.
4. **Before adding an interface to `kspMocksTargets`,** preview its mock with
   `-PmockApiTargets=<fqn>`.
5. **On a failure,** the `e: [ksp]` line is the processor's diagnostic and
   `references/troubleshooting.md` has its section; a module without the processor on its test-side
   configuration is wired first (`SKILL.md:42-92`).

§9.2: in item 3 the agent reads the whole generated file.

---

## 6. Release

The processor part (P1) is a release; the script and the skill text are not (`README.md:153-157`).
In order, per `CONTRIBUTING.md` §"Development rules" 4 and 6 and `AGENTS.md` §"Do not tag without
measuring":

1. **The `CHANGELOG.md` entry first, as 0.4.0.** *Generated output:* unchanged for every input unless
   `kspMocksMemberListing=true` is among the processor's options; with it, one
   `<Interface>Mock.members.txt` per generated mock in KSP's resource output directory. *Breaking:*
   nothing. *Adopting:* nothing to change; the skill's `printMockApi` prints listings from this
   version and whole files from 0.3.0 and earlier. The published jar stays class-file major 61.
2. **The development version literal** at `build.gradle.kts:17` moves to `0.4.0-SNAPSHOT` in the
   commit that will carry the tag.
3. **`./gradlew clean build` on that commit**, `scripts/check-skill.sh`,
   `scripts/check-print-mock-api.sh`, and `cmp AGENTS.md CLAUDE.md`.
4. **Tag `0.4.0`**; `.github/workflows/publish.yml` runs `scripts/publish-maven.sh`, whose coordinate
   set does not change under D1 (a).

**Not taken — §9.3**, which records 0.3.1 as the version a later P1 takes.

---

## 7. Not measured

- Android, on either AGP major (P0).
- The `testFixtures` shape through the script (P0).
- Any Gradle version other than 9.7.1, for the script's compilation and for its Gradle API calls (P0).
- A launcher older than JDK 23 (P0).
- The init script under `--configuration-cache` (P0); the `JavaExec` build-script prototype reported
  two problems.
- `${CLAUDE_SKILL_DIR}` in a running session, and an agent other than Claude Code finding the script
  (P0).
- An interface requiring a compiler flag, such as one declaring context parameters (P0).
- An interface declared in a test source set, and a supertype from a test-only dependency (D4).
- D2 (c)'s in-daemon worker, and any timing on a module larger than `:big` or written by people.
- `-language-version` taken from `getCoreLibrariesVersion()` in a module that sets `languageVersion`
  or `coreLibrariesVersion` itself.
- A build with `RepositoriesMode.FAIL_ON_PROJECT_REPOS`, where the script's detached configurations
  resolve through the settings repositories.
- Windows, where every list argument of `KSPJvmMain` is `;`-separated.
- How IntelliJ imports D2 (b)'s shared source directory.
- The 44.8 s row of §1.1 was run once.

§10.2 measured the `testFixtures` shape, a JDK 21 launcher, Gradle 8.14.3, the configuration cache,
`FAIL_ON_PROJECT_REPOS` and `${CLAUDE_SKILL_DIR}` in a session; §10.3 lists what is still not measured.

## 8. Open questions

1. **Does an adopter want `printMockApi` without the skill installed?** D1 (b)'s trigger.
2. **Should the listing shorten fully-qualified types** (`kotlin.Int` to `Int`)? It would make every
   listing line stop being a substring of the generated file, which D8 (a)'s comparison reads.
3. **Does `SKILL.md:33-40`'s `find` stay** once the task exists? It answers where the test
   compilation's file lands, which the task does not print.
4. **Does `printMockApi` belong in the Swift twin's skill as well?** Its generated output is a build
   product of a different tool; nothing in this spec changes a shared member name, so it is not a
   vocabulary decision under `AGENTS.md` §"Read the generated output, do not commit it".

§9.2 closes question 2.

---

## 9. The rulings of 2026-09-12, and the shorthand taken

Added 2026-09-12, after §1 to §8 were written.

### 9.1 The two rulings

**D1 — (a).** The init script under `skills/kotlin-ksp-mocks/scripts/`, passed with `-I`. No Gradle
plugin is published.

**D3 — (c), where §3 recommended (a).** `printMockApi` prints the whole generated `<Interface>Mock.kt`
for each target. The reason given with the ruling is the Swift twin's command-line lane, read in
`swift-sourcery-templates` at `b743b17`: `skills/swift-sourcery-mocks/references/cli-lane.md:28-44`
— `mock-templates generate` runs the engine and writes its output to `--output` under a fingerprint
block — and `:116-126`, which runs the engine directly with the same `--output`. Neither lane prints a
declarations-only form; an agent reads the generated file on both platforms.

The cost, from §1.9: 154 lines instead of 59 for `FeedEnvironmentMock`, and 185 lines for `:receipt`'s
two mocks.

### 9.2 What D3 (c) changes in §2

- **§2.4 is replaced.** For each target, in the order given: a line `// <fqn>`, the generated
  `<Interface>Mock.kt` from `build/tmp/printMockApi/kotlin/` as the processor wrote it, and a blank
  line. The run passes the processor no option beyond `kspMocksTargets`, so every processor release
  prints the same way.
- **§2.5's first bullet** prints the generated files of the targets that generated, then fails.
- **§2.6 lists the same unchanged things.** The consumer's `.kt` is unchanged because the processor is
  unchanged, rather than because an option is withheld.
- **§1.10's prototype printed through its `mockApiView` function.** The script P2 ships prints the file
  and carries no such function.
- **§8 question 2 is closed:** nothing shortens or cuts the printed file.

### 9.3 The shorthand: the init script and the skill, no release

Taken on 2026-09-12: the change is the init script and the skill text.

- **P1 is not taken.** `KspMocksProcessor.kt`, `MockRenderer.kt` and `MockRendererTest.kt` do not
  change, and `kspMocksMemberListing` does not exist.
- **P5 and §6 are not taken.** No `CHANGELOG.md` entry, no move of the literal at
  `build.gradle.kts:17`, no tag. The script reads the processor version from the adopter's build, so it
  runs whichever release the module declares; §1.4 measured it with 0.3.0 and with this repository's
  project dependency only.
- **The version a P1 takes, if one is taken later, is 0.3.1 — not §6's 0.4.0.** P1 changes no
  generated `.kt` for any input and adds an option nothing sets by default, so it breaks nothing for
  an adopter. The releases so far moved the minor version for a change an adopter has to act on and
  the patch version otherwise:
  - 0.2.0 raised the published jar to class-file major 69, which the consumer's compile JVM had to
    load (`CHANGELOG.md:74-89`);
  - 0.2.1 lowered it to 61 with no processor behaviour change (`:56-72`);
  - 0.3.0 changed the generated output and broke `mock.<prop> = value` (`:3-54`).
- **What the shorthand keeps from §4:** P2's `skills/kotlin-ksp-mocks/scripts/print-mock-api.init.gradle.kts`,
  and P3's text under `skills/kotlin-ksp-mocks/` — the `SKILL.md` section and `description`, the new
  reference, the pointer in `references/troubleshooting.md`. Both reach adopters when they land on
  `main` (`README.md:153-157`).

§9.4's closing ruling adds the four pieces it lists.

### 9.4 What the rulings leave open

Not ruled: D2, D4, D5, D6, D7, D8. The prototype §1 measured takes (a) in D2, D4 and D5, and the
script P2 ships starts from that prototype.

Four pieces of §4 sit outside `skills/` and wait on a ruling under the shorthand:

1. **The gate (D8).** `scripts/check-print-mock-api.sh`, its step in `ci.yml`'s `build` job, and K14
   in `scripts/check-skill.sh`. Under D3 (c), D8 (a)'s comparison is the byte identity of each
   generated file with the file the script printed. Without the gate, no CI job runs the script.
2. **`AGENTS.md:98-101`**, which lists the skill tree among the changes that touch no code. D1 (a)
   names `skills/**/scripts/` as code so that a change to the script goes through a pull request and
   its gate; that is an edit to `AGENTS.md` and its `CLAUDE.md` copy.
3. **P3's files outside `skills/`:** the `README.md` §"Agent skill" paragraph, the seventh case under
   `evals/` and its `evals/README.md` row, and `CONTRIBUTING.md` §"Repository layout".
4. **P0's measurements** — Android, `testFixtures`, the Gradle floor, the configuration cache,
   `${CLAUDE_SKILL_DIR}` in a session — run in scratch projects and change no file here. D6 decides
   whether Android enters the script.

Under D3 (c), D7 (a)'s reference drops two of its items, the listing's format and the whole-file
fallback, and keeps the command for each module shape and the failure lines of §2.5.

**Ruled on 2026-09-12:** the four pieces above are taken with the shorthand, each where the init script
and the skill text need it.

---

## 10. What landed, 2026-09-12

Added 2026-09-12, after §9. Four commits on `spec/003-print-mock-api` follow this spec's:
"Add the printMockApi init script to the skill", "Gate the init script against :receipt in the build
job", "Teach printMockApi in the skill, and check what it quotes", and "Add the list-a-mocks-members
eval case and the README paragraph".

### 10.1 The files, and where they depart from §2 to §4

**`skills/kotlin-ksp-mocks/scripts/print-mock-api.init.gradle.kts` (P2).** It registers `printMockApi`
as §2.1 states, reads §1.10's inputs, and prints §9.2's output. It differs from §1.10's prototype in
seven places:

1. **The libraries.** The test compile classpath configuration is resolved when the task runs, and
   every file under the module's build directory is dropped. The task depends on that
   configuration's build dependencies minus the module's own tasks — other projects' jars. §1.10's
   classpath input compiled two probe modules (§10.2).
2. **The source roots** drop every directory under `build/generated/ksp/`, which the KSP Gradle plugin
   adds to the source sets, so a run does not read a mock an earlier test compilation wrote.
3. **The `testFixtures` shape** reads `main` and `testFixtures` with `testFixturesCompileClasspath`
   when the processor is on `kspTestFixtures` and not on `kspTest`.
4. **No `mockApiView`.** Each target prints `// <fqn>`, the file, and a blank line. A target with no file
   prints `// <fqn>: no mock generated`, and the task fails after printing when a target is missing
   or KSP exits non-zero.
5. **Three failures the prototype did not have:** a module that applies neither Kotlin plugin, a module
   whose targets are empty, and a reflective lookup that fails, each naming the module.
6. **The launcher** is the module's Java toolchain, or Gradle's own JVM when the module has no
   `JavaPluginExtension`.
7. **The processor classpath** is wrapped in a file collection for the configuration cache (§10.2).

`-jvm-target=17` stays the constant §1.10's prototype passed.

**The decisions §9.4 left unruled, as implemented:**
- D2 (a): `KSPJvmMain` in a `JavaExec`.
- D4 (a): main and test roots with the test compile classpath, resolved as item 1 describes.
- D5 (a): both versions read from the build.
- D6: Android is not in the script, and P4 is not taken. The task fails on an Android module with the
  line naming the two supported shapes, and `references/printing-members.md` sends that module to
  `kspDebugUnitTestKotlin` and `find`.
- D7 (a): a `SKILL.md` section and a new reference.
- D8 (a), plus G1: a dry-run check that the task lists no `:receipt` task but itself. Under D3 (c), G4
  compares whole files rather than declarations.

**`scripts/check-print-mock-api.sh` (D8 (a)).**
- G1: the dry run lists no `:receipt` task but `printMockApi`, and the dry run itself exits 0.
- G2: the task exits 0.
- G3: `receipt/build/tmp/printMockApi/kotlin/` holds the files of `receipt/build/generated/ksp/test/kotlin/`,
  byte for byte.
- G4: stdout carries each of those files after its `// <fqn>` line and before a blank line.

`--self-test` seeds four violations:
- G1: the script copy gains `task.dependsOn("compileKotlin")`;
- G2: `-PmockApiTargets=dev.modaal.mocks.receipt.NotDeclared`;
- G3: one byte appended to a copy of one printed file;
- G4: one line dropped from a copy of stdout.

`ci.yml`'s `build` job runs the script after `./gradlew build`.

**K14 in `scripts/check-skill.sh`.** It checks what D8 (a) names and one more thing:
- every `scripts/…` path under `skills/kotlin-ksp-mocks/` exists;
- every task in a `-I` command is registered by a script;
- every `-P` property is read by a script;
- every `printMockApi: …` failure line the skill quotes is contained in a string literal a script
  throws, both normalised as K7 normalises. This is the addition to D8 (a).

The self-test seeds `K14`, a task named `printMockMembers`, and `K14_failure`, a reworded failure
line.

**`AGENTS.md` and `CLAUDE.md`:**
- §"Changes reach `main` through a pull request" names `skills/**/scripts/` as code, and calls the
  rest of the skill tree "the skill's Markdown".
- §"The skill under `skills/` teaches adopters" gains the rule to run `check-print-mock-api.sh` and
  names K14.
- §"What goes in which document" counts four references.

**`CONTRIBUTING.md`:**
- §"Repository layout" names the script and its check.
- Rule 7 states what the check fails on.
- §"Running the build" gives the two commands and G1–G4.
- The check count is fourteen, the self-test count sixteen, and the eval count seven.

**The skill text (P3):**
- **`SKILL.md`:**
  - the section "Print a mock before writing a test against it", after §"What the generated mock
    gives a test": the command, what it prints, that it compiles nothing of the module and runs while
    `main` does not compile, `-PmockApiTargets`, and the link;
  - a `description` that reads "print or read the members of a generated <Interface>Mock";
  - the fourth entry in §"References".
- **What `SKILL.md` gave up, per `AGENTS.md`'s rule:** the Properties paragraph and the selection
  sentence are compressed, and two sentences `generated-api.md` carries are dropped — the stream
  counters' concurrency sentence and the `<prop>GetHandler` timing sentence.
- **`references/printing-members.md`**, 89 lines:
  - the command, and `${CLAUDE_SKILL_DIR}` for other agents;
  - the output;
  - `-PmockApiTargets`;
  - the three module shapes of §2.2 that the script supports;
  - Android;
  - seven failure lines.
- **`references/troubleshooting.md`** and **`references/gradle-wiring.md`** each replace the sentence
  that said to read the generated file with one that points to `printMockApi`. Neither gains a line.

**The eval case and the README.** The seventh case, `evals/list-a-mocks-members`, has its table row and
the round of 2026-09-12 in `evals/README.md`. `README.md` §"Agent skill" gains the paragraph naming
the script, the command and `scripts/check-print-mock-api.sh`.

### 10.2 Measured while landing it

Same environment as the header, and the probe with three more modules:

| module | what it is |
| --- | --- |
| `:fix` | Kotlin/JVM with `java-test-fixtures`: `Store` in `main`, one `Samples` object under `src/testFixtures/kotlin/`, a test using `StoreMock`, `add("kspTestFixtures", …)` |
| `:j21` | `:feed`'s sources and wiring with `jvmToolchain(21)` |
| `:nochain` | `:feed`'s sources and wiring with no `kotlin { jvmToolchain(…) }` block |

The shipped script on each module, from `clean`, after one dry run had compiled the script. Each run's
files were compared with `cmp` against the module's test-side KSP task, run afterwards. A second run
of the script, with `build/generated/ksp/` then present, printed stdout identical to the first on
every row.

| module | the dry run lists | from `clean` | files | `cmp` |
| --- | --- | --- | --- | --- |
| `:feed` | itself | 1.67 s | 3, against `kspTestKotlin` | identical |
| `:feature` | `:core`'s five tasks to `jar`, then itself | 1.62 s | 1 | identical |
| `:kmp` | itself | 1.59 s | 2, against `kspTestKotlinJvm` | identical |
| `:fix` | itself | 1.53 s | 1, against `kspTestFixturesKotlin` | identical |
| `:j21` | itself | 1.77 s | 3 | identical |
| `:nochain` | itself | 1.58 s | 3 | identical |
| `:big` | itself | 2.12 s | — `:big:kspTestKotlin` fails in `compileKotlin` with `Class 'Impl0' is not abstract…`, left by §1.5's interface-member edit | — |
| `:receipt`, this checkout | `:mocks-processor`'s chain to `jar`, then itself | 1.67 s | 2 | identical |

On `:big` the script exited 0 while `main` does not compile, as §1.6 measured on `:feed`.

Found on the way to that table:

- **§1.10's classpath input compiled two modules.** With `jvmTestCompileClasspath` declared as a task
  input, `:kmp`'s dry run listed `:kmp:kspKotlinJvm`, `compileKotlinJvm`, `compileJvmMainJava`,
  `jvmProcessResources`, `processJvmMainResources` and `jvmMainClasses`. The configuration's hierarchy
  holds a file dependency in `jvmTestCompilationCompileOnly` on `build/classes/kotlin/jvm/main` and
  `build/classes/java/jvmMain`. An artifact view whose component filter kept only module components and
  other projects listed the same tasks. On `:fix`, `testFixturesCompileClasspath` holds a
  `testFixturesApi` dependency on `:fix` itself. Without a filter, the dry run listed `:fix:kspKotlin`,
  `compileKotlin`, `compileJava`, `processResources`, `classes` and `jar`.
- **`Configuration.copyRecursive`**, used to drop that file dependency, failed on every module with the
  processor on `kspTest`: `Dependency constraints can not be declared against the `testCompileClasspath`
  configuration.`
- **The configuration cache.** With the detached processor configuration captured by the argument
  provider, `--configuration-cache` reported
  `cannot serialize object of type 'org.gradle.api.internal.artifacts.configurations.DefaultLegacyConfiguration'`.
  Wrapped in `files(…)`, the entry was stored and then reused on `:feed` and on `:feature`, and the
  reused run's stdout was identical.
- **A JDK 21 launcher** (`:j21`) gets no `--sun-misc-unsafe-memory-access` argument; the run printed
  nothing to stderr under `-q`.
- **Gradle 8.14.3** on Temurin 21.0.12.1: `:feature:printMockApi` exited 0 with stdout identical to
  Gradle 9.7.1's. Started on Temurin 25, Gradle 8.14.3 fails with `25.0.4.1` as its whole message
  before any script is read. No Gradle older than 8.14.3 was run.
- **`RepositoriesMode.FAIL_ON_PROJECT_REPOS`**, added to the probe's settings: `:feed` and `:kmp` exited
  0 with stdout identical to the runs without it.
- **The failure output.** `-PmockApiTargets=probe.Nope,probe.FeedDependency` on `:feed` printed, in order:
  - `e: [ksp] kspMocksTargets: probe.Nope is not resolvable in this compilation` as the first line of
    stdout;
  - `// probe.Nope: no mock generated`, then a blank line;
  - `// probe.FeedDependency` and its file.

  The build then failed with
  `printMockApi: :feed — 1 of 2 targets generated no mock; KSP exited 1, and its diagnostics are printed above`.
  `:feedleak` failed with `printMockApi: :feedleak has no mocks-processor on kspTest or kspTestFixtures`.
- **The gate on this checkout** after `./gradlew build`: G1 to G4 pass, and `--self-test` reds each.
- **The skill's budget**, from `claude plugin details`:

  | `SKILL.md` | lines | characters | on invoke | always on |
  | --- | --- | --- | --- | --- |
  | before | 227 | 13,409 | ~4.8k | ~290 |
  | with the section | 235 | 13,838 | ~5k | ~300 |
  | after compressing | 228 | 13,393 | ~4.8k | ~300 |

- **`${CLAUDE_SKILL_DIR}` in a session (§7).** The eval round is in `evals/README.md`. In both its
  with-arm and a second session, Claude Code 2.1.268 replaced the variable with the skill directory of
  the plugin `--plugin-dir` loaded.

  The second session, model `sonnet`, 7 turns, $0.3028:
  - **Setup:** a scratch consumer holding `:feed` with `fun broken(): Int = "not an int"` appended to
    `Feed.kt`. Tools: `Bash(./gradlew:*)`, `Read`, `Glob`, `Grep`, `Skill`.
  - **Prompt:** asked for `FeedEnvironmentMock`'s members while `main` does not compile.
  - **What it ran:** it fired `Skill`, read `kspMocksTargets` from `feed/build.gradle.kts`, and ran
    `./gradlew -I "<skill directory>/scripts/print-mock-api.init.gradle.kts" :feed:printMockApi -q`.
  - **Result:** the command wrote the three mocks under `feed/build/tmp/printMockApi/kotlin/probe/`,
    and the answer tabulated `FeedEnvironmentMock`'s members from the printed file.

### 10.3 What is still open

- **Android (D6).** It is not in the script, and no run of the script on an Android module was made.
- **From §7:**
  - Gradle older than 8.14.3, and Windows;
  - `-language-version` in a module that sets `languageVersion` or `coreLibrariesVersion`;
  - an interface that needs a compiler flag, or one declared in a test source set;
  - the path an agent writes after `npx skills add`, and an agent other than Claude Code;
  - IntelliJ, and D2 (c).
- **`-jvm-target=17`** was not varied.
- **§8:** questions 1, 3 and 4 are open. `SKILL.md`'s `find` stays, at `SKILL.md:30-37`.
