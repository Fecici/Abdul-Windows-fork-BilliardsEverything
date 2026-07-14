# Agent Instructions For This Repo

This repo is the Windows/Abdul working baseline. Before changing code, read:

1. `docs/codex-project-study/bug-register.csv`
2. `docs/codex-project-study/11-linux-improvement-crosswalk-and-plan.md`
3. `docs/codex-project-study/12-linux-consolidation-execution-plan.md`

Do not rely on chat history for project state. If context was compacted or a new
agent starts, those three files are the durable handoff.

## Current Non-Negotiables

- Do not port the Linux database-location change unless explicitly requested.
- Do not lower or overwrite the user's intentional JVM memory settings.
- Do not revert user or previous-agent changes just because the tree is dirty.
- Prefer the Gradle path for Windows validation:

```powershell
.\gradlew.bat --no-daemon compileJava backendSharedLibrary
```

## Commenting Philosophy

Every non-obvious implementation change must include nearby comments explaining:

- what program path uses it;
- why the change exists;
- how it interacts with Java/C++ ownership, threading, cancellation, memory, or math;
- where the implementation intentionally differs from the Linux port.

Do not add filler comments that restate a line of code. Add comments where a random
maintainer would otherwise have to read the docs or reverse-engineer intent.

## Change Process

Work in small batches. After each batch:

1. compile with Gradle;
2. update `docs/codex-project-study/bug-register.csv`;
3. update the applied/deferred state in the consolidation plan;
4. record any warnings or tests that still require manual UI validation.

