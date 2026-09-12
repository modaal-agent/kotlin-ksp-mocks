# Agent rules

Rules for working in this repository. They are the additions to [CONTRIBUTING.md](CONTRIBUTING.md),
not a summary of it.

`AGENTS.md` and `CLAUDE.md` are one file kept in two places, byte for byte. Edit `AGENTS.md`, then
`cp AGENTS.md CLAUDE.md`; [`ci.yml`](.github/workflows/ci.yml)'s `rules` job runs `cmp` on the two
and fails if they differ.

**Read first, by question:**

| you need | read |
| --- | --- |
| what the processor generates, how a consumer wires it, the member vocabulary | [README.md](README.md) |
| how the processor is built, the decided rules, testing, the release procedure | [CONTRIBUTING.md](CONTRIBUTING.md) |
| what changed in a release and what it breaks | [CHANGELOG.md](CHANGELOG.md) |
| what an adopter's agent is told to write in a consuming repository | [SKILL.md](skills/kotlin-ksp-mocks/SKILL.md) |
| the plan for a change too big to carry in a commit message | `specs/` |

Run from the repository root:

```bash
./gradlew build          # unit tests in :mocks-processor, end-to-end in :receipt,
                         # and checkPublishedBytecodeVersion over the shipped jar
```

## Writing style: state facts and actions, no aphorisms

**Scope: every character of prose you produce for this project.** Specs, docs, code comments, commit
messages, PR bodies, review findings, and **your replies in chat**. There is no "informal" channel
where this relaxes.

**The test, applied to each sentence:** does it give the reader **a fact they can verify** or **an
action they can take**, with the referent named — the file, the line, the setting, the command, the
number? If it does neither, delete it. A sentence that only characterizes the work, dramatizes a
finding, or summarizes how significant something is carries no information the reader can act on.

Habits to avoid (common LLM-isms):

- **Mannered prose** substitutes metaphor and flourish for direct statement. Instead of "a parameter
  worth varying," the mannered writer produces "a dial worth turning." Instead of "this point still
  matters," they write "this point earns its keep." The phrases exist to display the writer, not to
  convey the idea, and readers can tell. That is why mannered prose irritates: it makes the reader
  work harder so the writer can perform. It is also imprecise — metaphors drag in connotations the
  writer did not choose and cannot control. **The fix is to say what you mean. When a literal phrase
  is available, use it.**
- **Aphoristic juxtapositions** ("Free now, a second migration later"). State the trade-off
  explicitly: what it costs now, what it costs later, which option you recommend.
- **Dramatic reversals and punchlines** ("that direction has reversed"; "upgraded those steps from
  redundant to breaking"). Give the before value, the after value, and the date measured.
- **Negative-space phrasing** ("checked by nobody"; "not cosmetic"; "not the thing to move"). Say
  which check is missing, in which file, what it costs, and when to add it. If the point is that X
  is wrong, name what to do instead — "keep `jvmToolchain(25)` and lower `compilerOptions.jvmTarget`",
  not "the toolchain is not the thing to move".
- **Metaphor or personification as the load-bearing content** ("a fresh module has no code to
  fight"; "the gate now has teeth"; "what the spec still owes"). A metaphor may decorate a point
  already stated literally; it may not be the only statement of that point. Documents do not owe,
  want, or know things — name who does the work, in which file, by when.
- **Rhetorical contrast standing in for content** ("verified, not merely committed"; "it is not that
  X, it is that Y"). State both facts separately and drop the contrast.
- **The closing paragraph that generalizes the lesson.** This is where aphorisms concentrate: a
  section ends, and the urge is to extract a portable moral. Either write a concrete rule with a
  named home — the gate to add, the file to add it to — or write nothing.

## Git state — confirm every commit

- **Never commit, amend, push or rewrite history without confirmation in the current turn.**
  "Implement X", or approval of a *previous* commit, is not authorization for the next one. When
  work is ready: stop, summarize what changed, ask.
- **Never touch the index or restore the tree.** `git add`, `git reset`, `git stash`,
  `git checkout -- <path>`: off-limits unless asked for in this turn. Staged versus unstaged is the
  reviewer's record of how far they have read, and reverting your own edits to "recover" discards
  work they have not seen. If a commit is authorized and the index is partly staged, ask which scope
  before running anything.
- **Subject line:** imperative, naming the change — "Emit property accessors name-sorted". Work
  backed by a spec carries the slug, **and the commit that writes the spec is the first such
  commit**. So a spec numbered 002 produces:

  ```
  [002-suspend-property-accessors] Specify the accessor shape and the seeding members
  [002-suspend-property-accessors] Emit the accessors from the renderer
  [002-suspend-property-accessors] Cover the shape in :receipt
  ```

  The slug names the feature and the rest names what that commit does, so the subject after the
  bracket does not repeat the slug. No spec in play, no prefix — do not invent one.

## Changes reach `main` through a pull request

- Code goes on a branch and through a PR, so [`ci.yml`](.github/workflows/ci.yml)'s two jobs run
  before it lands. They trigger on `pull_request` and on push to `main`: a push to a branch with no
  PR open runs nothing, so open the PR to get a build.
- Any merge strategy — merge, rebase or squash — chosen for the nature of the PR.
- **A new spec starts its feature's branch, and the spec is that branch's first commit.** Offer the
  branch when the spec is asked for and create it before writing, named `spec/NNN-slug` after the
  spec directory. The feature's code then lands on the same branch. A follow-up file beside a closed
  spec follows this rule too when it plans new work.
- A change touching no code may go straight to `main`: README, CONTRIBUTING or CHANGELOG wording,
  `AGENTS.md`/`CLAUDE.md`, the skill's Markdown under `skills/` or a `.claude-plugin/` manifest, or
  an addition to a spec whose branch has already merged. `mocks-processor/`, `receipt/`, `scripts/`,
  `skills/**/scripts/`, `.github/`, `gradle/`, `build.gradle.kts`, `settings.gradle.kts` and
  `gradle.properties` are code.
- Branch when the work starts. If code is ready and the checkout is `main`, ask which branch.
- Pushing, opening a PR and merging one each need their own go-ahead.

## Specs are an append-only decision ledger

- **A spec is amended by addition, never by revision.** While the feature is being worked on, add
  what was measured, what a phase actually landed and what the plan became — as new lines, new
  paragraphs or new sections. Do not rewrite or delete a line that is already there, not to fix a
  path a later change moved, not to correct a decision since superseded, not to tidy. Revising in
  place destroys the record of what was decided and when.
- **An addition that supersedes an existing claim names it**, by section, and the superseded section
  takes a line pointing forward to the addition. Both then read as one document.
- **A closed spec may take a follow-up file beside it** instead of an appended section —
  `specs/001-<slug>/followup-<topic>.md`. The rule inside it is the same: additions only, and it
  names by section what it supersedes.
- **A new spec names what it obsoletes**, by number and section ("obsoletes 001 §4.6").
- **Writing a spec is not authorization to implement it.** When the task is a spec, produce only the
  spec document — no processor, build-script or workflow edit, not even the one line that looks
  ready. When it is written, stop and ask.

## Read the generated output, do not commit it

- **Run `./gradlew build` before and after every edit to the processor.** It is the only place the
  generated mocks are compiled and run: `:mocks-processor`'s unit tests read hand-built models, and
  `:receipt` compiles what the processor emitted during this build's own test compilation. A change
  that looks local to one member shape routinely moves another.
- **Read the emitted sources under `receipt/build/generated/ksp/`** when iterating on
  `MockRenderer.kt`. They are build products: never commit them, never hand-edit one to see a shape
  you want — change the renderer and rebuild.
- **A construct the processor cannot handle gets an error naming the interface member and what to do
  about it**, not a partial emission that fails later at the consumer's compile. Generic interfaces
  and `vararg` parameters are the two that fail this way today.
- **A change to the generated member vocabulary is a cross-repo decision** with
  [swift-sourcery-templates](https://github.com/modaal-agent/swift-sourcery-templates) — the names,
  the channel-backed `Flow` shape and the unset-handler failure string are shared with it. Stop and
  ask before changing one; do not fold a rename into a refactor.

## The skill under `skills/` teaches adopters, and a gate holds it to the processor

- **Run `scripts/check-skill.sh` after every edit under `skills/` or `.claude-plugin/`**, and
  `scripts/check-skill.sh --self-test` after editing a check itself.
  [`ci.yml`](.github/workflows/ci.yml)'s `skill` job runs the first of the two; neither needs a JDK.
- **Run `scripts/check-print-mock-api.sh` after `./gradlew build` and after every edit to
  `skills/kotlin-ksp-mocks/scripts/print-mock-api.init.gradle.kts`**, and its `--self-test` after
  editing a check. It runs the script on `:receipt` and compares what it prints with
  `receipt/build/generated/ksp/test/kotlin/`; `ci.yml`'s `build` job runs it after the build.
  Check K14 in `scripts/check-skill.sh` compares the script path, the task, the `-P` property and the
  `printMockApi:` failure lines the skill quotes against the script, so a rename there lands with the
  skill edit in the same commit.
- **A rename in `MockRenderer.kt`, or a reworded diagnostic in `KspMocksProcessor.kt`, lands with the
  skill edit in the same commit.** Checks K6 and K7 compare every member name and every diagnostic
  string the skill quotes against those two files, and K8 compares the wiring it teaches against
  `receipt/build.gradle.kts`.
- **The skill's audience is an agent in a repository that consumes the processor.** How this
  processor is built, the two test layers, `checkPublishedBytecodeVersion` and the release procedure
  are CONTRIBUTING.md's subject and this file's; none of it goes in the skill.
- **The skill states what to write and does not re-derive why.** The measurement behind a rule stays
  where it already is — `specs/001-agent-skill/spec.md` §11 for the five wiring shapes,
  `KspMocksProcessor.kt:24-37` and README.md §Wiring for build-script selection.
- **`SKILL.md`'s frontmatter carries the Agent Skills standard's six keys only** — `name`,
  `description`, `license`, `compatibility`, `metadata`, `allowed-tools`. Claude Code's extension
  keys, `when_to_use` among them, are rejected when the directory is packaged for the Skills API, and
  check K3 names the offending key.
- **A new fact for an adopter goes in `SKILL.md` while it stays under 400 lines**, and in the
  reference file for its subject — each under 250 lines — once it does not. Checks K5 and K9 hold
  the two budgets and every link between the files.
- **`SKILL.md` has a second budget no check reads: 5,000 tokens, the compaction floor.** K5 counts
  lines and has passed while the body was over it. Measure after an addition to the body and before
  a release, then put the tree back:

  ```bash
  claude plugin marketplace add ./
  claude plugin install kotlin-ksp-mocks@kotlin-ksp-mocks --scope local
  claude plugin details kotlin-ksp-mocks     # the on-invoke cost, read from the working tree
  claude plugin marketplace remove kotlin-ksp-mocks
  ```

  Keep the body near 4.8k so the next addition has room, and pay for one by compressing what a
  `references/` file already carries.

## State a rule once

- The member vocabulary is spelled in `mocks-processor/src/main/kotlin/dev/modaal/mocks/MockRenderer.kt`
  and pinned by `MockRendererTest.kt`. Restating a name or a failure string anywhere else is how the
  processor, its tests and the Swift twin come to disagree about the same member. README.md and
  `skills/kotlin-ksp-mocks/` are the two places that restate them for a reader who is not editing the
  processor, and checks K6 and K7 hold the skill's copy to the renderer. Write a member name in that
  file as `${name}Suffix`, `${fn}Suffix`, `${capitalized}Suffix` or `_${name}`, the store's spelling:
  K6 reads the emitted set out of exactly those interpolations, so a name assembled any other way is
  missing from the set it compares the skill against, and the skill can then name a member the
  renderer never emits while K6 stays green.
- The published class-file target is `publishedBytecodeTarget` in
  `mocks-processor/build.gradle.kts`, read from there by `compilerOptions.jvmTarget`,
  `sourceCompatibility`/`targetCompatibility` and `checkPublishedBytecodeVersion`. Write the number
  17 into a fourth place and one of them stops agreeing.
- The development version literal is the `-SNAPSHOT` default in the root `build.gradle.kts`, and a
  published version comes from the tag through `-PpublishVersion`. Do not add a version literal to a
  README snippet, a workflow or the skill: the snippets write `<version>` and send the reader to
  `https://modaal-agent.github.io/maven/dev/modaal/mocks-processor/maven-metadata.xml`, whose
  `<release>` element is the newest published version. Check K10 fails a number under `skills/`.

## Do not tag without measuring

Follow [CONTRIBUTING.md](CONTRIBUTING.md)'s release rules in order. Write the `CHANGELOG.md` entry
**before** tagging — a consumer reads it to decide whether to bump — and state in it what the release
changes for the generated output, or that it changes nothing for any input, and the published jar's
class-file major. `:receipt` is the consumer measurement this repository can run: `./gradlew build`
on the commit that will carry the tag.

## What goes in which document

- **README.md** — what the processor generates and how to consume it. A new generated member adds a
  line to its Generated API section; a newly unsupported construct adds one to "Not supported".
- **CONTRIBUTING.md** — how the processor is built, decided design rules, the two test layers,
  pitfalls, release procedure.
- **AGENTS.md / CLAUDE.md** — rules only, and one file in two places. If you are about to write a
  paragraph explaining what something *is*, it belongs in one of the other two.
- **CHANGELOG.md** — what a release changes and what it breaks, written before the tag.
- **skills/kotlin-ksp-mocks/** — what an agent writes in a repository that *consumes* the processor:
  the three build edits, the configuration for each module shape, the generated members, the failure
  table, and the `printMockApi` command. Everything longer than the body's budget goes in one of its
  four `references/` files.
- **specs/`NNN-slug`/spec.md** — the plan for a change too big to carry in a commit message: what is
  true now (measured, with file and line references), what the rule becomes, the phasing, the
  decisions and what stays open. Written before the change and left in place after it, as the record
  of why. It never becomes the place a *rule* is stated — that is here.

## Scope

- Generated output in a consumer repo is that repo's build product. Do not hand-edit a consumer's
  generated mock to work around a processor gap — fix the processor and regenerate.
- Nothing this processor needs may land on a consumer's main classpath. A feature that requires an
  annotation artifact there is out of scope; say so rather than designing around it.
