# Bug Audit Plan

## Goal

Find and document real bugs in the Java and C++/header code before doing optimization work. For each bug, record:

- exact file and line
- what is wrong
- why it is wrong
- how to reproduce or reason about it
- proposed fix
- risk of the fix
- test or manual verification needed
- whether to fix now, defer, or reject as not a bug

This is not a refactor pass. Do not rename, reformat, or modernize code unless a bug fix requires it.

## Current code volume

Current Abdul source inventory:

| Type | Files |
|---|---:|
| Java | 117 |
| C++ `.cpp` | 45 |
| C++ headers `.hpp` | 80 |

Largest files:

| File | Approx lines |
|---|---:|
| `src/java/billiards/viewer/Viewer.java` | 6966 |
| `src/backend/cpp/wrapper.cpp` | 1041 |
| `src/java/billiards/viewer/BoyanMenu.java` | 1040 |
| `src/backend/cpp/verify.cpp` | 1016 |
| `src/java/billiards/viewer/CycleVaryWindow.java` | 995 |
| `src/java/patternfinder/PatternFinder.java` | 783 |
| `src/backend/cpp/common.cpp` | 752 |
| `src/java/billiards/viewer/IterateToLimitWindow.java` | 720 |
| `src/java/billiards/viewer/Utils.java` | 685 |
| `src/java/billiards/viewer/CoverWindow.java` | 648 |

## Baseline freeze before audit

Before starting the full audit:

1. Capture `git status --short --branch`.
2. Capture `git diff --stat`.
3. Capture the exact Gradle command that compiles and runs on Windows.
4. Record Java version, Gradle version, and native compiler version.
5. Record whether `.\gradlew.bat clean run` successfully starts the app.
6. Decide whether current local changes are the audit baseline or whether some should be committed/stashed first.

Reason: the worktree already has many local changes. A bug audit on a moving baseline will mix real source bugs with transient local edits.

## Output files for the audit

Create these files:

```text
docs/codex-project-study/bug-audit/
  00-baseline.md
  01-bug-register.md
  02-java-ui-and-threading.md
  03-java-native-bridge-and-memory.md
  04-java-database-and-model.md
  05-java-math-and-geometry.md
  06-cpp-wrapper-and-abi.md
  07-cpp-database-and-serialization.md
  08-cpp-math-core.md
  09-cpp-cover-and-vary.md
  10-tests-needed.md
```

The central register should use this table:

| ID | Severity | Status | File:line | Area | Bug | Proposed fix | Fix risk | Verification |
|---|---|---|---|---|---|---|---|---|

Severity:

- `S0`: data loss, crash on normal use, wrong mathematical result with high confidence
- `S1`: likely wrong result, native crash risk, memory corruption/leak in common path, severe UI break
- `S2`: intermittent issue, concurrency risk, resource leak, bad defaults, platform-specific failure
- `S3`: minor UI defect, unclear edge case, defensive cleanup

Status:

- `open`
- `confirmed`
- `fixed`
- `needs repro`
- `not a bug`
- `deferred`

## Review order

### Phase 1: Known and high-signal bugs

Start with known or strongly suspected issues:

1. `Viewer.java` reflection toggle/re-toggle bug.
2. `Wrapper.vary3Cpp` and `Wrapper.vary4Cpp` parallel parsing into plain `ArrayList`.
3. `HashTriple` color-map behavior and runtime patched `HashTriple.class` difference.
4. Windows portability fixes already encountered:
   - `M_PI` dependency
   - 32-bit native target selection
   - `-flto` duplicate Boost symbols
   - MSYS2 Boost `-mt` library names
   - Winsock `-lws2_32`
5. JNA string/memory ownership in `Wrapper.java` and `wrapper.cpp`.

### Phase 2: Java UI and task threading

Files:

- `Viewer.java`
- `BoyanMenu.java`
- `CoverWindow.java`
- `SmallCoverWindow.java`
- task classes under `billiards.viewer`
- `patternfinder/*`

Bug patterns:

- JavaFX UI updates outside the FX thread.
- `Platform.runLater` ordering bugs.
- duplicate transforms or duplicate event handlers.
- stale state after toggles/check boxes.
- tasks that keep running after cancel.
- shared mutable collections accessed by background tasks.
- exceptions swallowed in background tasks.
- memory growth from images, maps, or listeners.

### Phase 3: Java native bridge

Files:

- `Wrapper.java`
- `ConnectionPool.java`
- `CString.java`
- `CPicture.java`
- `CInfo.java`
- `CInfoAll.java`
- `wrapper.cpp`
- `wrapper.hpp`

Bug patterns:

- JNA struct layout mismatch.
- native memory allocated but not freed.
- Java `String` returned from `char*` with unclear ownership.
- null pointer handling.
- native return codes ignored.
- native functions called after pool destruction.
- thread-unsafe Java collections used during native result parsing.
- Windows DLL search path or dependency DLL failures.

### Phase 4: Database and storage

Files:

- `Admin.java`
- `Database.java`
- `Info*.java`
- `Picture*.java`
- `Storage.java`
- `database.cpp`
- `database/viewer.cpp`
- `database/serialize.cpp`
- `database/deserialize.cpp`
- SQLite wrapper headers

Bug patterns:

- stale database rows hiding code changes.
- SQL table mismatch between Java and native.
- serialization/deserialization mismatch.
- missing transaction/connection cleanup.
- path handling on Windows.
- computed object loaded with wrong type.
- save-if-missing causing expensive work in read paths unexpectedly.

### Phase 5: Java math and geometry

Files:

- `CodeSequence.java`
- `ClassifiedCodeSequence.java`
- `Storage.java`
- `billiards.geometry/*`
- `billiards.math/*`
- Java tests under `src/test/java`

Bug patterns:

- Java/C++ classifier divergence.
- boundary inclusion errors.
- invalid canonicalization.
- exact-to-double conversion issues.
- geometry equality/hash issues.
- degenerate polygon/line cases.

### Phase 6: C++ math core

Files:

- `code_sequence.cpp`
- `classified_code_sequence.cpp`
- `equations.cpp`
- `bounding_inequalities.cpp`
- `bounding_region.cpp`
- `unfolding.cpp`
- `refine.cpp`
- `shooting_vectors.cpp`
- `trig_identities.cpp`
- math headers

Bug patterns:

- overflow or exact-arithmetic blowups.
- invalid assumptions about polygon convexity.
- empty optional handling.
- thrown exceptions crossing JNA boundary.
- platform-specific constants.
- signed/unsigned conversions.
- parallel mutation bugs.
- unstable ordering from maps/sets affecting reproducibility.

### Phase 7: Cover and vary

Files:

- `CoverStuff.java`
- `HashTriple.java`
- `CoverWindow*.java`
- `SmallCoverWindow.java`
- `verify.cpp`
- `common.cpp`
- `vary_cs.cpp`
- `vary3.cpp`
- `vary4.cpp`
- related headers

Bug patterns:

- cover parser token mismatch.
- huge file memory usage causing crash.
- color/default handling.
- stale color entries after rectangle removal.
- degree/radian mismatch.
- missing cancellation checks.
- duplicate or lost vary candidates.
- recursion/subdivision boundary cases.

## Static searches to run first

Run these before manual line-by-line review:

```powershell
rg -n "TODO|FIXME|HACK|throw new RuntimeException|catch \\(|printStackTrace|Platform.runLater|parallel\\(\\)|new ArrayList|Collections\\.synchronized|Native\\.register|cleanup_|delete\\[\\]|new char|malloc|free|return nullptr|boost::none|M_PI|4L \\* 1024" src
```

Then targeted searches:

```powershell
rg -n "getTransforms\\(\\)\\.add|reflectCheckBox|Affine" src/java/billiards/viewer/Viewer.java
rg -n "parallel\\(\\).*forEach|new ArrayList" src/java/billiards/wrapper/Wrapper.java
rg -n "to_cstr|cleanup_string|cleanup_cpicture|cleanup_cinfo" src/backend/cpp/wrapper.cpp src/java/billiards/wrapper/Wrapper.java
rg -n "Platform.runLater|Task<|ExecutorService|cancel" src/java/billiards/viewer src/java/patternfinder
```

## How to review each file

For each file:

1. Read imports/includes first.
2. Identify public API and callers.
3. Review state fields and ownership.
4. Review constructors/init paths.
5. Review each method/function in order.
6. For every suspected bug, find at least one caller or execution path.
7. If a fix is obvious, write the exact patch in the register but do not apply until approved.
8. Add a test or manual verification step for every accepted bug.

## First bug to fix: reflection toggle

Known symptom:

- Re-toggling the `Reflect` checkbox causes bad UI behavior.

Likely source:

- `Viewer.java` adds new `Affine` transforms in more than one path.
- The selected listener and startup/update path add transforms without a single owner or replacement logic.

Expected safe fix direction:

- Create one `Affine` field for the reflection transform, or create one helper that first removes the previous reflection transform.
- On selected true: apply exactly one reflection transform.
- On selected false: remove that transform.
- On image stack size change: update that transform instead of adding another.

Fix risk:

- High enough to test carefully because coordinate mapping, click handling, and rendering may depend on reflected state.

Verification:

- Start app with reflect selected.
- Toggle off/on/off/on.
- Confirm transform count does not grow.
- Confirm displayed coordinate orientation is correct.
- Confirm click/selection maps still align with drawn regions.

## Optimization policy

Do not include optimizations in the main audit unless:

- the optimization is required to fix a crash or severe memory failure, or
- the code is already being changed for a bug and the optimization reduces bug risk.

Otherwise record optimization ideas separately in:

```text
docs/codex-project-study/bug-audit/99-optimization-backlog.md
```

## Stop conditions

Pause before applying fixes if:

- a proposed fix changes mathematical results.
- a fix requires changing the native ABI.
- a fix changes database serialization.
- a fix touches more than one major subsystem.
- the issue might be runtime-only behavior not present in source.

