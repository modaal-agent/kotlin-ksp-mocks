# Eval cases for the agent skill

Seven prompts: six from spec 001 §6.2, and `list-a-mocks-members` from spec 003 §4 P3. Each is a question an adopter's agent is given in a repository that
*consumes* `dev.modaal:mocks-processor`, and each has one correct configuration or one correct
diagnosis. Every case is run twice — once with `kotlin-ksp-mocks` loaded and once without it — and
the two answers are compared. That comparison is the only measure of whether
`skills/kotlin-ksp-mocks/SKILL.md` changes what an agent does; a prompt whose with-skill answer is
wrong is a defect in the skill's text, so edit the skill and run that case again.

| case | the prompt asks | correct |
| --- | --- | --- |
| `wire-a-jvm-module` | generated mocks for a Kotlin/JVM module whose tests hand-write a fake | the host repository, the KSP plugin, `kspTest`, the FQN in `kspMocksTargets`; `<version>` read from `maven-metadata.xml`; nothing committed |
| `interfaces-in-another-module` | interfaces in `:core`, tests in `:feature` | wire `:feature` and list the `:core` FQN there; `implementation(project(":core"))` already puts it on the test compile classpath; the mock lands in the interface's package |
| `multiplatform-module` | which KSP configuration a KMP module with JVM tests needs | `add("kspJvmTest", …)`, because the typed accessor does not compile; the test in `jvmTest`, because `commonTest` cannot see the mock |
| `flow-test-hangs` | why a test collecting a mock's `Flow` never finishes | the channel was never closed — `close()` after the sends, or seed the handler with `flowOf(…)` |
| `handler-expected-to-be-set` | what `loadHandler expected to be set.` means, and when a mock returns without seeding | set `loadHandler`; the fallbacks are `Unit`, the `Flow` channel, `null` and a guessable default |
| `generic-interface-refused` | how to mock an interface the processor refused for declaring type parameters | generic interfaces are unsupported: wrap the use site in a non-generic interface, or hand-write the double |
| `list-a-mocks-members` | which members a mock will have, and how to see it, while `main` does not compile | `./gradlew -I <skill directory>/scripts/print-mock-api.init.gradle.kts :feed:printMockApi -q`, which compiles nothing of `:feed`; `measureCallCount`, `measureArgs` of `MeasureArgs`, `measureHandler` |

## Running them

From the repository root, which is the plugin root:

```bash
claude plugin eval ./ --ablation with-without
```

`evals/` is the runner's default eval directory, so `.claude-plugin/plugin.json` needs no
`experimental.evals` key and no `--eval-dir` is needed. Results land in `evals/results/`, which is
not committed. The runner is in early access: a Claude Code that has not been granted it answers
"`plugin eval` is currently in early access" and runs nothing. 2.1.267 answers that, and so does
`claude plugin eval init`, so the runner authors no template either.

Without the runner, run one case's two arms by hand and read both answers:

```bash
REPO=/path/to/kotlin-ksp-mocks
PROMPT="$(awk 'NR==1 && $0=="---" { fm=1; next } fm && $0=="---" { fm=0; next } !fm' \
  "$REPO/evals/wire-a-jvm-module/prompt.md")"
ARGS=(-p --restricted --strict-mcp-config --allowedTools "Read,Glob,Grep,Skill" --permission-prompts none)

cd "$(mktemp -d)" && claude "${ARGS[@]}" "$PROMPT"                       # without
cd "$(mktemp -d)" && claude "${ARGS[@]}" --plugin-dir "$REPO" "$PROMPT"  # with
```

`--plugin-dir` loads the plugin for that session only, so the without-arm needs nothing uninstalled.
Three things the arms are sensitive to, each measured on a run in the twin repository that got them
wrong (its spec 002 §13.3):

- **Run from a directory whose path does not name this repository.** The file tools take absolute
  paths, and a without-arm that reads the repository is answering with the skill's material by
  another route. `mktemp -d` gives `/var/folders/…/T/tmp.…` on macOS, which does not.
- **`--restricted` confines the file tools to the run directory**, which is what makes the point
  above hold. It also removes Bash, so an arm cannot `curl` the host's `maven-metadata.xml` to
  resolve `<version>` and names that step instead of running it.
- **Under `--restricted` the with-arm cannot open `references/*.md` either** — they are outside the
  run directory — so what it answers from is `SKILL.md` alone. Add
  `--add-dir "$REPO/skills/kotlin-ksp-mocks"` to the with-arm when a case is meant to exercise a
  reference file.

Each arm has to be a fresh session: context left over from writing the skill hides what the skill
does not say.

## The round of 2026-09-10

Twelve runs by hand, as above, on Claude Code 2.1.267, model `sonnet`, $0.9040 in total. The `regex`
and `tool_used` verdicts were scored from the transcripts; each `criteria` verdict is a reading of
that arm's last message against the grader's numbered criteria. `skill-fired` reports whether the
skill fired and is not part of the score.

| case | grader | without | with |
| --- | --- | --- | --- |
| `wire-a-jvm-module` | `skill-fired` | ✘ 0x | ✔ 1x |
|  | `names-the-test-configuration` | ✘ | ✔ |
|  | `names-the-target-option` | ✘ | ✔ |
|  | `criteria` | ✘ | ✔ |
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

Every with-arm fired `Skill` once and answered from `SKILL.md` alone, so no case needed `--add-dir`.

**`wire-a-jvm-module`.** The without-arm refused to answer: "it's not a library I can confirm from
training (unlike e.g. MockK or Mockative), and I couldn't check because web search is blocked in
this session … I don't want to hand you fabricated Gradle coordinates, plugin IDs, annotation names,
or a guessed output path." It asked for the README or the coordinates. The with-arm wrote the three
edits, listed `com.example.feature.FeedEnvironment`, left `<version>` to be read from the host's
`maven-metadata.xml`, said it could not run that `curl` itself, and named
`feature/build/generated/ksp/test/kotlin/com/example/feature/FeedEnvironmentMock.kt` and that it is
not committed.

**`interfaces-in-another-module`.** The without-arm refused on the same grounds. The with-arm wired
`:feature`, listed `com.example.core.Repository` there, said the existing
`implementation(project(":core"))` is what puts it on the test compile classpath, and put
`RepositoryMock` in `com.example.core` — the interface's package, so a test elsewhere imports it.

**`multiplatform-module`.** The without-arm described KSP-per-target correctly in general and could
not answer either question: the `commonTest` one "depends entirely on which of those the processor
hooks into". The with-arm wrote `add("kspJvmTest", …)` with the note that the typed accessor does
not exist and will not compile, gave `build/generated/ksp/jvm/jvmTest/kotlin/`, and answered "**Can
the test live in `commonTest`? No.**"

**`flow-test-hangs`.** Both arms are right, and this case does not discriminate. The without-arm
answered in one turn with no tool call: `toList()` waits for completion, a channel-backed flow
completes when the channel is closed, add `eventsChannel.close()`. The with-arm added the mechanism
— the mock replays `eventsChannel` while `eventsHandler` is unset — and the second fix,
`eventsHandler = { flowOf(FeedEvent.Tick) }`. The prompt hands over the vocabulary
(`eventsChannel`, `FeedEnvironmentMock`, `UncompletedCoroutinesError`), so what is left to measure
is general Kotlin knowledge of channel-backed flows. Making it measure the skill means a prompt
that names the symptom and the interface and nothing else.

**`handler-expected-to-be-set`.** Both arms reach `environment.loadHandler = { id -> FeedPage(id) }`.
The without-arm then answers the second question wrongly — "Realistically only for methods returning
`Unit`" — missing the `Flow` channel, the nullable return and the guessable defaults. The with-arm
lists all five rules in order.

**`generic-interface-refused`.** The with-arm wrapped the use site in a non-generic interface,
swapped the target in `kspMocksTargets`, and named hand-writing the double as the fallback. The
without-arm proposed `interface StringCache : Cache<String>` — a sub-interface rather than a wrapper
— and `mockk<Cache<String>>()`, and attributed the refusal to what "many codegen-based mock
generators" do rather than to this processor's rule, then asked whether `kspMocksTargets` accepts
`"com.example.core.Cache<String>"`. Whether that sub-interface renders is untested;
`KspMocksProcessor.kt` reads `getAllFunctions()` and `getAllProperties()`, which include inherited
members, so it plausibly does.

### What the round changed

- **Spec §6.4 expected the without-arm to reach for MockK or Mockito in the three setup cases.** It
  did not: denied the web search that would confirm the library exists, each of the three refused to
  answer. Those cases measure whether the arm answers at all. MockK appeared once, in
  `generic-interface-refused`. The spec's §15.4 supersedes §6.4 on this.
- **One grader was deleted.** `wire-a-jvm-module` carried a `not_contains` regex on
  `\b(mockk|mockito)\b`. The without-arm named MockK while refusing to use it, so the grader reds an
  answer for mentioning a library rather than for proposing one, and it would red a with-arm answer
  that tells an adopter they can drop MockK. The `criteria` grader in that case already forbids
  proposing one.

The transcripts are not kept: the answers above are the record, and a re-run samples fresh ones.
Spec 001 §15 carries the same round with the schema the case files were written against.

## The round of 2026-09-12

`list-a-mocks-members` alone, run by hand as above on Claude Code 2.1.268, model `sonnet`, $0.4824 in
total, scored the same way.

| case | grader | without | with |
| --- | --- | --- | --- |
| `list-a-mocks-members` | `skill-fired` | ✘ 0x | ✔ 1x |
|  | `runs-print-mock-api` | ✘ | ✔ |
|  | `names-the-init-script` | ✘ | ✔ |
|  | `criteria` | ✘ | ✔ |
|  | the run | 6 turns, $0.2908 | 3 turns, $0.1916 |

**`list-a-mocks-members`.** The without-arm reported that `WebSearch` was denied and that it could
not confirm the processor's names. It offered `measureCallCount` and `measureCalls: List<Pair<Int, Int>>`
as illustrations, and told the user to stub the error in `FeedService.kt` so that `kspTestKotlin`
could run, then read `feed/build/generated/ksp/test/kotlin/…/FeedEnvironmentMock.kt`. The with-arm
fired `Skill` once and answered from `SKILL.md` alone: `measureCallCount`, `measureArgs` of
`MeasureArgs(width, height)`, `measureHandler` returning `false` while unset, and
`./gradlew -I "<plugin directory>/skills/kotlin-ksp-mocks/scripts/print-mock-api.init.gradle.kts" :feed:printMockApi -q`,
with `${CLAUDE_SKILL_DIR}` already replaced by the directory `--plugin-dir` named. It said the task
compiles nothing of `:feed` and prints the whole generated file.

## Writing a case

One directory per case, holding `prompt.md` and `graders/`. The prompt's frontmatter carries
`description`, `tags`, `expected_outcome`, and the execution keys `allowed_tools` and `max_turns`;
its body is the user message, and it has to carry the build script or the failure text inline,
because a run has no repository to read. Each grader is one `graders/<name>.md`, the file name is
the grader's name, the frontmatter carries `type` — `regex`, `llm`, `tool_used`, `tool_order`,
`file_exists` or `baseline` — and the body is the criteria for an `llm` grader and the pattern for a
`regex` one. `arm: with-only` marks a grader that measures whether the skill fired rather than
whether the answer is right, and keeps it out of the score. Every type's object is strict, so an
unknown key fails the case instead of being ignored.

`scripts/check-skill.sh`'s K13 parses every case: a prompt body, at least one grader, and a `type:`
the runner accepts. It does not run them — the suite costs model calls, and no CI job runs it.

The eval directory cannot live under `skills/`: it is the plugin's skill component directory, and
the runner refuses a case directory inside one.
