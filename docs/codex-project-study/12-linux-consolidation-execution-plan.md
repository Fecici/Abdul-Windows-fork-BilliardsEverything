# Linux Consolidation Execution Plan

Purpose: finish consolidating the useful Linux-port changes into the Windows/Abdul
source without losing progress across context compaction. This plan is based on
direct source comparisons between:

- Windows/Abdul: `-Abdul-s-fork-BilliardsEverything/`
- Linux port: `[LINUX_PORT]Billards_Stable (Release)/Billards_Stable (Release)/`

Non-goal: do not port the Linux project-relative database directory. The user
explicitly deferred that because database location changes need a migration plan.

## State Checkpoint

Already fixed in the current Windows/Abdul tree:

- B7 Newton/intersection tolerance: `newton.hpp`, `intersection.hpp`
- TriangleBilliard4 right-list typo: `triangle_billiard4.cpp`
- B11 stale cover index guards: `CoverStuff.java`
- B12 pixel bounds guards: `PolyVaryTask.java`, `CycleVaryTask.java`
- P9 `CoverWindow.redoInfo` map lookup: `CoverWindow.java`
- P7 thread-local `Evaluator` reuse: `evaluator.hpp`, `evaluator.cpp`,
  `common.cpp`, `verify.cpp`
- A1 native DB connection ownership split: `wrapper.cpp`
- P5 row-batched `redoFromScratch` and guarded render coalescing:
  `Viewer.java`, `Utils.java`
- P4 cached AutoVary pixel reader and deterministic traversal:
  `PolyVaryTask.java`, `CycleVaryTask.java`

Already present or ported differently:

- Load Holes feature
- P1/P2 core parallel polygon refinement and TBB corner evaluation
- P8 faster native vary serialization
- A3 reflection handling, using Abdul's stronger single-transform implementation
- B1-B6 JNA/native ownership fixes, with Abdul using caller-owned `CString` cleanup
- B10 cover calculation moved off the JavaFX thread

Compile checkpoint after those fixes:

```text
.\gradlew.bat --no-daemon compileJava backendSharedLibrary
BUILD SUCCESSFUL in 1m 54s
```

Additional compile checkpoint after P7:

```text
.\gradlew.bat --no-daemon compileJava backendSharedLibrary
BUILD SUCCESSFUL in 1m 28s
```

Additional compile checkpoint after A1:

```text
.\gradlew.bat --no-daemon compileJava backendSharedLibrary
BUILD SUCCESSFUL in 1m 24s
```

Additional compile checkpoint after P5 row batching:

```text
.\gradlew.bat --no-daemon compileJava backendSharedLibrary
BUILD SUCCESSFUL in 16s
```

Render coalescing note: Abdul now ports this differently from Linux. The Linux
`renderFuture = executor.submit(...)` idea is used only when `Utils.renderCoalescing`
is enabled, `Utils.numThreads > 1`, and the caller passed the long-lived viewer
executor. Short-lived draw executors remain synchronous because AutoVary/cover
cleanup paths shut them down after render completion.

Additional compile checkpoint after P4:

```text
.\gradlew.bat --no-daemon compileJava backendSharedLibrary
BUILD SUCCESSFUL in 14s
```

Additional compile checkpoint after guarded render coalescing:

```text
.\gradlew.bat --no-daemon compileJava backendSharedLibrary
BUILD SUCCESSFUL in 15s
```

Additional checkpoint after the remaining optimization sweep:

```text
.\gradlew.bat --no-daemon compileJava backendSharedLibrary testBackend
BUILD SUCCESSFUL in 2m 22s
testBackend: Running 31 test cases... No errors detected

.\gradlew.bat --no-daemon test
BUILD SUCCESSFUL in 8s
```

## Significance Ranking

| Rank | Item | Structural significance | Why |
| --- | --- | --- | --- |
| 1 | P7 thread-local `Evaluator` reuse | High | Changes native MPFR/MPFI object lifetime in cover recursion and TBB worker threads. Big memory/allocation payoff, but must be isolated and stress-tested. |
| 2 | P5/A2 render row-batching and render coalescing | High | Changes JavaFX render scheduling from synchronous to async/cancellable. Risk is stale or missing image commits if callers assumed immediate render completion. |
| 3 | A1 DB connection release during native compute | High | Changes wrapper/database ownership timing. Important for concurrency, but could expose duplicate-save races. |
| 4 | P4 cached AutoVary pixel reader and shuffle removal | Medium | Local to `PolyVaryTask`/`CycleVaryTask`; improves UI responsiveness and determinism. Needs interactive UI validation. |
| 5 | P3 O(n) minimal rotation and cached `CodeSequence::type()` | Medium | Core math canonicalization path, but deterministic and local. Needs canonicalization comparison tests. |
| 6 | B8/B9 executor shutdown and interrupt preservation | Medium | Stability/cancel responsiveness. Mostly Java task lifecycle cleanup. |
| 7 | P6 pattern finder `StringBuilder` sweep | Low/medium | Broad but mostly mechanical UI-output performance work. |
| 8 | P1 micro-consolidation | Low | Abdul already has the important parallel refinement implementation. Remaining Linux differences appear mostly profiling comments and move semantics. |

## Phase 1: Local, Lower-Risk Consolidation

Goal: port changes with small blast radius before touching render scheduling or
native database ownership.

### 1A. P3 O(n) Code Sequence Canonicalization

Status: applied 2026-07-14.

Files:

- `src/backend/cpp/code_sequence.cpp`
- `src/backend/headers/code_sequence.hpp`
- `src/test/headers/code_sequence_test.hpp`

Linux evidence:

- `code_sequence.cpp::least_rotation_index`
- `code_sequence.cpp::rotated_copy`
- O(n) `minimal_rotation`
- `CodeSequence::type()` cache via `mutable boost::optional<CodeType> cached_type_`

Applied Abdul state:

- `minimal_rotation` uses an O(n) least-rotation pass on the forward sequence
  and another O(n) pass on the reversed sequence, preserving the old equivalence
  under cyclic shift and reversal.
- `CodeSequence::type()` caches the computed type because `CodeSequence` is
  immutable after construction.
- Native tests include a rotation/reversal canonicalization regression case.

Implementation record:

1. Adapted Linux `least_rotation_index`, `rotated_copy`, and O(n)
   `minimal_rotation`.
2. Added a comment above `least_rotation_index` explaining that this keeps canonical
   code sequence selection identical to the old forward-plus-reverse minimum, but
   avoids repeated mutation.
3. Added `mutable boost::optional<CodeType> cached_type_` to the header.
4. In `CodeSequence::type()`, returns the cached value when present and caches before
   returning.
5. Did not change public output formatting or validation behavior.

Validation:

1. `.\gradlew.bat --no-daemon compileJava backendSharedLibrary testBackend`
2. Native `testBackend` ran 31 Boost test cases with no errors.
3. Manual UI comparison on known long sequences is still useful before release.

Rollback point: revert only `code_sequence.cpp/.hpp`.

### 1B. P4 Full AutoVary Pixel Cache And Deterministic Traversal

Status: applied 2026-07-14. Keep this section as the implementation record and
manual-test checklist.

Files:

- `src/java/billiards/viewer/PolyVaryTask.java`
- `src/java/billiards/viewer/CycleVaryTask.java`

Linux evidence:

- Fields: `private volatile PixelReader pixelReader;`, `imgWidth`, `imgHeight`
- One JavaFX-thread initialization in `call()`
- Direct `pixelReader.getArgb(...)` inside the worker loop
- `Collections.shuffle(out)` removed

Applied Abdul state:

- Each task snapshots `PixelReader` and image dimensions once on the JavaFX thread.
- Per-coordinate `Platform.runLater` pixel reads were removed.
- Coordinate shuffling was removed so AutoVary/CycleVary runs are reproducible.
- Abdul's `GracefullyCancelable` behavior was preserved.

Implementation plan:

1. Add cached `PixelReader` and image dimensions to both tasks.
2. Initialize the cache once at the start of `call()`.
3. If initialization is interrupted, restore interrupt state and let cancellation
   flow stop the task.
4. Replace per-coordinate `pixelColor` JavaFX round trips with direct cached reads.
5. Remove `Collections.shuffle(out)` in both tasks.
6. Remove now-unused `Collections` import only if nothing else in the file uses it.
7. Comment the cache once per class: the image is snapshotted/read for covered-pixel
   checks so AutoVary does not serialize every coordinate through the JavaFX thread.
8. Preserve Abdul's graceful-cancel checks and progress behavior.

Validation:

1. Compile.
2. Launch app.
3. Run AutoPolyVary and CycleVary near normal view, zoomed view, and reflect mode.
4. Confirm no UI freeze from per-coordinate JavaFX calls.
5. Confirm cancel still saves already completed partial results.

Rollback point: revert only `PolyVaryTask.java` and `CycleVaryTask.java`.

### 1C. B9 Interrupt Preservation Audit

Files:

- `PolyVaryTask.java`
- `CycleVaryTask.java`
- `VaryLTask.java`
- draw task classes under `src/java/billiards/viewer/`

Current Abdul state:

- Some interrupted catches now restore interrupt state, but the audit is incomplete.
- Abdul has newer asynchronous shutdown helpers in `Utils`; do not blindly replace
  them with the Linux version.

Implementation plan:

1. Search for `catch (InterruptedException` and `Thread.interrupted()`.
2. For each catch, decide whether it is a cancel path or a real error path.
3. In cancel paths, call `Thread.currentThread().interrupt()` before returning or
   breaking unless the code deliberately consumed the interrupt.
4. Add a short comment only where the interrupt is preserved to propagate task cancel
   back through executor/future callers.

Validation:

1. Compile.
2. Start AutoVary/CycleVary and cancel while storage tasks are running.
3. Confirm partial results remain and no executor threads keep doing work for long.

## Phase 2: Native Memory And Allocation Pressure

Goal: port the highest-value memory optimization after Phase 1 compiles cleanly.

### 2A. P7 Thread-Local Evaluator Reuse

Status: applied 2026-07-14. Keep this section as the implementation record and
manual native-memory test checklist.

Files:

- `src/backend/headers/evaluator.hpp`
- `src/backend/cpp/evaluator.cpp`
- `src/backend/cpp/common.cpp`
- `src/backend/cpp/verify.cpp`

Linux evidence:

- `Evaluator::thread_local_instance(uint32_t prec)`
- `common.cpp` call sites use `Evaluator& eval = Evaluator::thread_local_instance(prec)`
- `verify.cpp` call site uses the same

Applied Abdul state:

- `common.cpp` uses `Evaluator::thread_local_instance(prec)` in the two
  cover-positive paths.
- `verify.cpp` uses `Evaluator::thread_local_instance(prec)` in the recursive
  cover path.
- These paths now reuse one MPFR/MPFI scratch evaluator per worker thread and
  precision.

Implementation plan:

1. Add `static Evaluator& thread_local_instance(uint32_t prec)` to the class.
2. Implement it with `thread_local std::unique_ptr<Evaluator>` and a stored precision.
3. Include `<memory>` if needed by the translation unit.
4. Replace the three hot `Evaluator eval{prec}` call sites with references.
5. Comment that each worker thread gets its own MPFR/MPFI scratch state, preserving
   thread isolation while avoiding per-square heap churn.
6. Do not change `division.hpp` until its `Evaluator eval;` use is understood; it
   may be a different non-precision path.

Validation:

1. Compile.
2. Run cover on a small region and a larger region.
3. Watch Windows Task Manager memory while canceling and restarting cover.
4. Verify memory plateaus after thread-pool warmup instead of growing per square.

Risks:

- Thread-local evaluator memory persists until the worker thread exits, so peak
  retained memory can be higher per active worker but allocation churn should drop.
- If a function stores references beyond the call stack, this would be unsafe; verify
  call sites use `Evaluator&` only synchronously.

Rollback point: revert evaluator header/source plus three call sites.

## Phase 3: Render Pipeline And UI Responsiveness

Goal: port the Linux rendering improvements only after auditing synchronous render
assumptions in Abdul.

### 3A. Pre-Audit Render Callers

Files:

- `src/java/billiards/viewer/Viewer.java`

Audit command:

```powershell
rg -n "renderRegions\\(|renderRegion\\(|getImage\\(\\)" src/java/billiards/viewer/Viewer.java
```

Questions to answer before editing:

- Does any caller call `renderRegions(...)` and immediately mutate
  `(WritableImage) regionsImageView.getImage()`?
- Does any flow require the image to be committed before showing a modal/window?
- Does canceling a previous render drop important in-progress user work?

### 3B. P5 Row-Batched `redoFromScratch`

Status: row batching applied 2026-07-14.

Linux evidence:

- `redoFromScratch` submits one `Future<Color[]>` per row instead of one
  `Future<Color>` per pixel.
- `allCheckBox.isSelected()` and `boundsCheckBox.isSelected()` are hoisted.

Implementation plan:

1. Port row-future structure first, without making `renderRegions` async.
2. Add comments explaining that `SIDE * SIDE` per-pixel futures overload the executor
   and heap; row futures keep work parallel with only `SIDE` submissions.
3. Compile and manually render several regions.

Validation:

- Compile.
- Use a large render and compare responsiveness.
- Confirm bounds/fills/unstables still draw.

Rollback point: revert only `redoFromScratch`.

### 3C. A2 Render Coalescing

Status: applied 2026-07-14 with Abdul-specific guards.

Linux evidence:

- `private volatile Future<?> renderFuture`
- `renderRegions` cancels prior render and commits four images via `Platform.runLater`

Applied Abdul state:

1. `Utils.renderCoalescing` defaults on, but can be disabled with
   `-Dbilliards.renderCoalescing=false` or `BILLIARDS_RENDER_COALESCING=false`.
2. Coalescing is automatically disabled when `Utils.numThreads <= 1` because the
   parent render job submits row jobs to the same executor and would otherwise
   deadlock waiting on itself.
3. `Viewer.renderRegions` uses coalescing only when the passed executor is the
   long-lived `Viewer.executorService`; all transient draw executors remain
   synchronous so cancel/progress-saving cleanup paths keep their old ordering.
4. Each coalesced render receives a generation number, cancels the previous
   `renderFuture`, snapshots the region map, builds detached images off the UI
   thread, and commits only if its generation is still current.
5. Render toggles (`all`, `bounds`, `show fills`, `prover`) are snapshotted before
   drawing so the worker renderer does not repeatedly read JavaFX controls inside
   pixel loops.
6. `redoFromScratch` now cancels row futures when the parent render is interrupted,
   so old renders stop competing with the newest redraw.

Validation:

- Rapidly change view/zoom and verify stale images do not appear.
- Test OBO, cover bounds, MRR bounds, screen fills, and reflect mode.

Risks:

- Manual UI validation is still needed because JavaFX image generation now happens
  off-thread for the main viewer executor. The final `ImageView` commit remains on
  the JavaFX thread.

Rollback point: revert `Viewer.renderRegions` helper split, `renderFuture` fields,
row-future cancellation additions, and `Utils.renderCoalescing` helpers.

## Phase 4: Native DB Connection Ownership

Goal: stop holding SQLite pooled connections during expensive native computation.

Files:

- `src/backend/cpp/wrapper.cpp`

Linux evidence:

- `load_picture`
- `load_picture_lr_expando`
- `load_picture_lr`

Applied Abdul state:

- Applied 2026-07-14 for `load_picture`, `load_picture_lr_expando`, and
  `load_picture_lr`.
- These functions now check database existence with a short-lived pooled
  connection, compute without holding SQLite, save with a new short-lived
  connection, and load the final picture with a short-lived connection.

Implementation plan:

1. Do not delete existing `save_to_database` helpers yet; other wrapper entry points
   may still depend on their exact return semantics.
2. Inline the split flow only inside the three picture-loading entry points:
   check `database::in` using a short-lived connection, release it, compute without
   a connection, save with a new short-lived connection, then load picture with a
   short-lived connection.
3. Preserve Abdul's existing native memory cleanup and error return behavior.
4. Add comments at each split point explaining the reason: pooled DB connections are
   scarce; C++ geometry computation can run for seconds/minutes and should not hold
   a database handle.

Validation:

1. Compile.
2. Run normal draw/load picture.
3. Run AutoVary with multiple storage workers.
4. Watch for SQLite duplicate-key/save conflicts if two workers compute the same
   missing code concurrently.

Risks:

- Race: two workers can both see a code missing and compute it. If `database::save`
  does not tolerate duplicates, this needs an insert-or-ignore or retry/load pattern.
- Behavior: `load_picture_lr` must preserve left/right mismatch checks.

Rollback point: revert only the three wrapper functions.

## Phase 5: Pattern Finder StringBuilder Sweep

Status: applied 2026-07-14.

Goal: eliminate O(n^2) Java string concatenation in pattern finder output paths.

Files:

- `src/java/patternfinder/PatUtils.java`
- `src/java/patternfinder/PatternFinder.java`

Linux evidence:

- `PatUtils.printImm`
- `PatUtils.printPat`
- `PatternFinder.fireExtendBtn`
- `PatternFinder.fireCleanBtn`
- `PatternFinder.xtndAction`
- `PatternFinder.singAction`
- `PatternFinder.tripAction`
- `PatternFinder.header`
- `PatternFinder.createNs`

Implementation record:

1. Ported builder changes function by function.
2. Kept output byte-for-byte identical in intent; did not change separators, comments, or blank
   lines.
3. Added short comments near the largest builder loops explaining that pattern
   output can be large and Java `String +=` copies accumulated text every iteration.
4. Also converted `PatUtils.printAndTestTrip`, which had the same repeated string
   concatenation pattern in the verified triple-output path.

Validation:

- `.\gradlew.bat --no-daemon compileJava backendSharedLibrary`
- Compile passed 2026-07-14.
- `.\gradlew.bat --no-daemon test` passed 2026-07-14.
- Manual pattern finder before/after text comparison on a known small input is still
  recommended because this is UI output formatting.

## Phase 6: P1 Micro-Consolidation

Status: applied/closed 2026-07-14.

Goal: decide whether any leftover Linux polygon-refinement changes are worth porting.

Finding:

- Abdul already has `PARALLEL_THRESHOLD`, `polygon_is_tiny`, Boost thread pool,
  TBB corner evaluation, and `intersect_polygons`.
- Linux has minor move/profiling differences in `equations.cpp` and `refine.cpp`;
  most are not useful to port because Abdul's implementation has Windows-specific
  worker-count and comment improvements.

Implementation record:

1. Ran a narrow diff on `equations.cpp`, `refine.cpp`, and `refine.hpp`.
2. Ported the only useful behavior-neutral copy reduction: `intersect_one_way`
   moves the freshly refined polygon into the running result.
3. Replaced ambiguous `QUESTION` comments in the parallel refinement/intersection
   path with concrete explanations of worker-local polygons, half-plane
   constraint intersection, and explicit template instantiations.
4. Did not add profiling-only comments/macros.

Validation:

- `.\gradlew.bat --no-daemon compileJava backendSharedLibrary testBackend`
- Native `testBackend` ran 31 Boost test cases with no errors.
- Optional: run the two OSNO examples from `Project_Improvement_Report.md` for
  timing comparison.

## Context-Compaction Guard

Future agent startup checklist:

1. Read `AGENTS.md`.
2. Read `docs/codex-project-study/bug-register.csv`.
3. Read this file.
4. Run `rg -n "deferred|planned|in_progress" docs/codex-project-study`.
5. Pick exactly one phase or subphase.
6. After edits, compile and update the CSV plus this plan.

Do not restart analysis from scratch unless the files above are missing or contradicted
by source.
