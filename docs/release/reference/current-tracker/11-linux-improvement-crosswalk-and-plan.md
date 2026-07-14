# Linux Improvement Crosswalk And Release Plan

Scope: compare `Project_Improvement_Report.md` against the actual Linux port under
`../[LINUX_PORT]Billards_Stable (Release)/Billards_Stable (Release)` and the current
Windows/Abdul source tree. This is an implementation checklist, not a design essay.

Database relocation note: the Linux `Admin.databaseDir` change is intentionally not
being ported now. The Windows/Abdul build keeps the current user-home database
location until there is a deliberate migration plan.

## Evidence Roots

| Surface | Path |
| --- | --- |
| Windows/Abdul source | `-Abdul-s-fork-BilliardsEverything/` |
| Linux port source | `[LINUX_PORT]Billards_Stable (Release)/Billards_Stable (Release)/` |
| Shared report copy | `Project_Improvement_Report.md` |
| Report SHA-256 | `C5BD363447D9478B9AFD289FF033228B904659E5B50D8EB65D385A14C27130E8` |

Status legend:

| Status | Meaning |
| --- | --- |
| Ported | Current Abdul source already has the improvement. |
| Ported differently | Abdul solves the same problem with a deliberately different implementation. |
| Partial | Some of the Linux change exists, but a material part is still missing. |
| Missing | Linux has it and Abdul does not. |
| Deferred | Not being ported in this pass. |

## Full Crosswalk

| Report item | Linux implementation evidence | Current Abdul status | Risk if missing | Action |
| --- | --- | --- | --- | --- |
| Load Holes feature | `src/backend/cpp/cover/save.cpp`, `src/backend/cpp/verify.cpp`, `src/java/billiards/viewer/Viewer.java` | Ported | Low if present; feature support for cover holes | No action. |
| P1 parallel polygon refinement | `src/backend/cpp/equations.cpp`, `src/backend/cpp/refine.cpp`, `src/backend/headers/refine.hpp` | Ported differently | Performance and memory pressure on large OSNO/cover work | Abdul already had the main parallel/TBB implementation; only a behavior-neutral move assignment and clearer comments were applied 2026-07-14. |
| P2 TBB corner evaluation | `src/backend/cpp/refine.cpp`, `src/backend/headers/refine.hpp` | Ported | Slow curve refinement | No action. |
| P3 O(n) minimal rotation | Linux `src/backend/cpp/code_sequence.cpp` adds `least_rotation_index`; Linux header adds cached type | Ported | Large code sequences spend unnecessary O(n^2) time canonicalizing | Applied 2026-07-14 with native rotation/reversal regression tests. |
| P4 AutoVary pixel-reader caching and shuffle removal | Linux `PolyVaryTask.java`, `CycleVaryTask.java` add cached `PixelReader`, dimensions, bounds checks, and remove `Collections.shuffle` | Ported | UI-thread bottleneck, nondeterministic traversal, possible pixel OOB crash | Applied 2026-07-14; manual AutoVary/CycleVary UI test still needed. |
| P5 row-batched `redoFromScratch` | Linux `Viewer.java` render pipeline changes | Ported differently | Render slowness and possible stale render commits | Row batching and guarded coalescing applied 2026-07-14; coalescing is only used with the long-lived viewer executor and is disabled when `billiards.threads=1`. |
| P6 StringBuilder in pattern finder | Linux `src/java/patternfinder/*.java` | Ported | Pattern finder can become O(n^2) on large output | Applied 2026-07-14; manual output comparison still useful. |
| P7 thread-local `Evaluator` reuse | Linux `src/backend/cpp/evaluator.cpp`, `src/backend/headers/evaluator.hpp`, call sites in `common.cpp`/`verify.cpp` | Ported | MPFR/MPFI allocation churn and memory pressure during cover recursion | Applied 2026-07-14; cover memory stress test still needed. |
| P8 faster vary serialization | Linux `src/backend/cpp/wrapper.cpp` | Ported | Native string formatting overhead | No action. |
| P9 `CoverWindow.redoInfo` O(n+m) | Linux `CoverWindow.java` builds `HashMap<String,String>` before annotating `cover/info.txt` | Ported | Cover info rewrite can become seconds-long on large stable/triple lists | Applied 2026-07-14. |
| A1 release DB connection during C++ compute | Linux `src/backend/cpp/wrapper.cpp` picture load functions | Ported | DB pool exhaustion during batch compute | Applied 2026-07-14 for picture-loading entry points; concurrent AutoVary DB test still needed. |
| A2 single render pipeline/coalescing | Linux `Viewer.java` render future | Ported differently | Stale UI renders and wasted work | Applied 2026-07-14 with Abdul-specific guards for one-thread builds and short-lived draw executors. |
| A3 reflection consolidation | Linux `Viewer.java`; Abdul has a stronger `reflectionTransform`-based fix | Ported differently | Duplicate transforms when toggling reflect | No action; keep Abdul implementation. |
| A4 portable database location | Linux `Admin.java` | Deferred by user | User-data migration risk | Do not port now. |
| B1 `CInfoAll` field order | Linux `CInfoAll.java`; Abdul has matching order and comments | Ported | Native pointer corruption | No action. |
| B2 `CInfoAll` cleanup | Linux `wrapper.cpp/.hpp`, `Wrapper.java`; Abdul has cleanup calls in `finally` | Ported | Native memory leak | No action. |
| B3 `calculateGradient` string cleanup | Linux `Wrapper.java`; Abdul cleanup both native strings | Ported | Native string leak | No action. |
| B4 vary CString leak/list race | Linux cleanup plus synchronized list; Abdul uses cleanup plus a native vary lock | Ported differently | Native leak and race/cancel corruption | No action; keep Abdul lock because native vary cancel state is global. |
| B5 static cover return buffers | Linux changed static strings to `thread_local`; Abdul moved cover returns to caller-owned `CString` cleanup | Ported differently | Return-buffer races between cover calls | No action; Abdul implementation is safer for JNA ownership. |
| B6 exception-safe native allocation | Linux copy helpers; Abdul copy helpers catch partial allocation failure | Ported | Native leak under allocation failure | No action. |
| B7 Newton/intersection tolerance | Linux `newton.hpp` uses `1e-25`; `intersection.hpp` uses `1e-25` | Missing | False non-convergence or under-wide interval around roots | Apply now; known numeric stability fix. |
| B8 executor shutdown | Linux task/window finally blocks; Abdul has `Utils.safeShutdownExecutor`/`shutdownExecutorAsync` in many paths | Partial | Thread leaks after exceptions/cancel | Continue audit later; not all paths checked. |
| B9 PolyVary cancellation/reinterrupt | Linux reinterrupts interrupted workers; Abdul has graceful cancel but some catch blocks still swallow interrupt | Partial | Cancel can take longer or thread interrupt state can be lost | Defer small cleanup until after current compile; lower than correctness fixes. |
| B10 CoverWindow work off JavaFX thread | Linux `CoverWindow.java`; Abdul has JavaFX `Task` and daemon cover threads | Ported | UI freeze during cover | No action. |
| B11 stale cover index bounds | Linux `CoverStuff.parseCover` guards stable/triple indexes | Missing | Opening stale cover files can crash with `IndexOutOfBoundsException` | Apply now; low-risk input validation. |
| B12 Poly/Cycle pixel OOB | Linux pixel readers check bounds | Missing | AutoVary can crash on zoom/reflection/edge rounding | Apply now; low-risk input validation. |
| TriangleBilliard4 R/L typo | Linux `triangle_billiard4.cpp::reconfigure(false)` uses `R[i]`; Abdul uses `L[i]` while iterating `R` | Missing | Wrong Vary4 geometry and possible out-of-bounds read | Apply now; known bug. |

## Immediate Release-Safe Fix Plan

These are the fixes for this pass. They are either direct bug fixes already present
in the Linux source, or obvious correctness fixes verified by comparing the same
function in both trees.

1. Change `Newton::solve` tolerance and `intersection_unchecked` fudge from the old
   overly-tight values to `1e-25`.
2. Fix `TriangleBilliard4::reconfigure(false)` so the right-hand branch indexes `R[i]`.
3. Guard stale stable/triple indexes in `CoverStuff.parseCover`.
4. Guard off-image pixel reads in `PolyVaryTask.pixelColor` and `CycleVaryTask.pixelColor`,
   and preserve interrupt state when those probes are interrupted.
5. Port the `CoverWindow.redoInfo` map lookup to avoid repeated full scans.
6. Compile with `./gradlew.bat --no-daemon compileJava backendSharedLibrary`.

## Applied In This Pass

Date: 2026-07-14.

| Fix | Files changed | Result |
| --- | --- | --- |
| Newton/intersection tolerance | `src/backend/headers/newton.hpp`, `src/backend/headers/intersection.hpp` | Applied `1e-25` tolerance/fudge from Linux source, with comments explaining the long-code convergence reason. |
| Vary4 right-list typo | `src/backend/cpp/triangle_billiard4.cpp` | Applied Linux `R[i]` fix in the `reconfigure(false)` branch and documented the out-of-bounds/wrong-angle failure mode. |
| Stale cover index guard | `src/java/billiards/cover/CoverStuff.java` | Added stable/triple index bounds checks so stale cover artifacts warn and skip the bad square instead of crashing the viewer. |
| Pixel probe bounds | `src/java/billiards/viewer/PolyVaryTask.java`, `src/java/billiards/viewer/CycleVaryTask.java` | Added image-null and pixel bounds checks; preserve interrupt state when pixel reads are interrupted. |
| Cover info lookup | `src/java/billiards/viewer/CoverWindow.java` | Added a `HashMap` pre-pass so `redoInfo` is O(n+m), matching the Linux improvement while preserving Abdul's existing cover-window flow. |

Validation command:

```powershell
.\gradlew.bat --no-daemon compileJava backendSharedLibrary
```

Validation result:

```text
BUILD SUCCESSFUL in 1m 54s
3 actionable tasks: 3 executed
```

Warnings observed: Gradle/native-access warning from the wrapper runtime, one dangling doc
comment warning in `Viewer.java`, and `this-escape` warnings in `CycleVaryWindow.java` and
`StablesWindow.java`. None are introduced by this pass.

## Remaining Backlog

| Item | Current status | Why not done |
| --- | --- | --- |
| Interrupt/executor lifecycle audit | Open | Stability cleanup across Java task paths; lower risk than the already-fixed correctness and memory items, but still worth doing before release if cancel/shutdown testing exposes issues. |
| Linux database relocation | Deferred by user | Explicitly deferred to avoid user-data path churn and migration risk. |

## Ported But Needs Manual Validation

| Item | Manual validation still needed |
| --- | --- |
| Full AutoVary cached `PixelReader` plus shuffle removal | Run AutoPolyVary/CycleVary in normal, zoomed, and reflected views; verify cancel preserves partial progress. |
| Thread-local `Evaluator` reuse | Run cover on small and large regions; watch native memory plateau after worker warmup. |
| Row-batched/coalesced render pipeline | Rapid zoom/toggle/load/cover actions; verify no stale image flashes and no missing guide/region/bounds/OBO layers. |
| DB connection ownership split | Run normal draw/load picture plus concurrent AutoVary storage workers; watch for duplicate-save SQLite conflicts. |
| Code sequence O(n) canonicalization | Run known long code sequence calculations in the UI and compare against prior/runtime output. Native rotation/reversal tests already pass. |
| Pattern finder `StringBuilder` sweep | Run pattern finder on a known small input and compare text output to the previous build. |
| Polygon refinement micro-consolidation | Optional large OSNO/MRR timing comparison; native regression tests already pass. |
