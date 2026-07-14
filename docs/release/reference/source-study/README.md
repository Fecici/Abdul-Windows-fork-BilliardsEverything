# Source Study Guide

This directory is documentation only. No source code was changed.

The project is a Java/JavaFX application backed by a native C++ library. It searches for, verifies, stores, and visualizes periodic billiard paths in triangular billiards. The central mathematical object is a code sequence: a finite integer sequence describing the pattern of side hits in an unfolded triangular billiard. The program classifies each code sequence, computes its maximal region of realization (MRR), stores results in SQLite, and uses cover algorithms to test whether collections of stable and unstable objects cover a polygonal region in angle space.

Current baseline: Abdul's fork is the source branch to treat as the main development branch. Docs `01` through `13` were originally written against the older `sourcecode-billiards_everythingMay2,2026/billiards_everything` tree; the architecture remains accurate for Abdul except for the explicit Abdul deltas called out in docs `16` through `20`. The compiled `[MAIN]` runtime is a separate specialized Windows build and should be compared through docs `18` and `19`, not assumed to match Abdul source line-for-line.

## How To Read This Documentation

Start here, then read in this order:

1. `00-progress-cache.md` - current documentation status, exact scope, commands already run, and handoff notes.
2. `01-architecture-and-math.md` - the global pipeline and mathematical concepts.
3. `02-backend-core.md` - C++ core data types, symbolic math, code sequence validation, and function-level notes.
4. `03-backend-algorithms.md` - C++ unfolding, equation generation, MRR refinement, database/JNA, cover, and vary algorithms.
5. `04-java-core-frontend.md` - Java model, database wrapper, JavaFX viewer, task flow, and patternfinder overview.
6. `05-risks-optimizations.md` - concrete correctness risks, likely bugs, and optimization opportunities found during this pass.
7. `06-tests-build.md` - Gradle/native build setup and existing test coverage.
8. `07-symbol-index.md` - generated all-symbol and function/method indexes for source navigation.
9. `08-viewer-method-map.md` - method-level documentation for every detected `Viewer.java` method.
10. `09-parser-sqlite-patternfinder.md` - parser, SQLite, database serialization, and PatternFinder helper documentation.
11. `10-viewer-support-map.md` - support-map for non-`Viewer.java` viewer classes and tasks.
12. `11-backend-second-pass-reference.md` - deeper backend pass covering C++ ABI, database, cover verification, geometry, symbolic math, billiard algorithms, and optimization risks.
13. `12-java-core-second-pass-reference.md` - deeper Java core/JNA pass covering code sequences, storage, database parsing, geometry, wrapper/native calls, and vary search.
14. `13-frontend-second-pass-reference.md` - deeper frontend pass covering `Viewer.java`, JavaFX tasks/windows, rendering, cover UI, vary UI, database/query tools, and debugging workflow.
15. `14-test-distribution-comparison.md` - TEST distribution comparison, patched jar verification, cover-data comparison, backend-binary comparison, and source-vs-runtime caveats.
16. `15-test-button-workflow-reference.md` - TEST UI/button workflow guide covering main viewer controls, cover tools, vary tools, pattern tools, diagnostics, and teaching workflow.
17. `16-source-runtime-fork-version-matrix.md` - source tree versus downloaded runtime versus Abdul fork version/feature matrix, jar/class differences, and applicability notes.
18. `17-source-vs-abdul-fork-line-diff.md` - normalized source-to-Abdul line-by-line diffs and impact analysis.
19. `18-main-vs-abdul-fork-comparison.md` - decompiled `[MAIN]` Java runtime versus Abdul/source comparison, build/launch status, and high-level differences.
20. `19-main-backend-dll-vs-abdul-source.md` - `[MAIN]` native `backend.dll` Ghidra/symbol/disassembly analysis versus Abdul C++ source, including the confirmed `eliminate_phi` backend difference.
21. `20-abdul-windows-build-dependencies.md` - why Abdul's Windows native build currently fails, which dependencies are missing, and the recommended MSYS2/UCRT64 package path.

## Source Size

The `/src` tree contains roughly 44,602 source lines across 242 source files:

| Extension | Files | Lines |
| --- | ---: | ---: |
| `.cpp` | 45 | 10,917 |
| `.hpp` | 80 | 7,399 |
| `.java` | 117 | 26,286 |

Most source is in:

| Area | Files | Lines | Purpose |
| --- | ---: | ---: | --- |
| `src/backend/cpp` | 33 | 9,776 | Core C++ algorithms, code sequences, MRRs, curve refinement, verification, JNI/JNA wrappers |
| `src/backend/headers` | 45 | 4,246 | Public C++ interfaces and reusable templates |
| `src/java/billiards/viewer` | 55 | 20,029 | JavaFX frontend, rendering, user workflows, long-running tasks |
| `src/java/billiards/*` except viewer | 50 | 3,442 | Java domain model, geometry, database parsing, wrappers, vary algorithms |
| `src/java/patternfinder` | 10 | 1,711 | Pattern search/extension UI and helpers |
| `src/test` | 13 | 926 | C++ and Java tests |

## High-Level System Map

The runtime flow is:

1. User enters or generates code sequences in JavaFX.
2. Java validates/canonicalizes via `billiards.codeseq`.
3. Java calls the C++ native library through `billiards.wrapper.Wrapper`.
4. C++ canonicalizes again, classifies the code, computes MRR data, and stores/loads SQLite rows.
5. Java parses returned points/equations into `Storage.Stable` or `Storage.Unstable`.
6. Viewer tasks render colored MRR regions, line segments, cover rectangles, and guide lines.
7. Cover tools use stored equations and interval arithmetic to prove positivity over rectangles.

The computational core is C++; Java exists mostly for UI, orchestration, parsing, and some older vary/search paths.

## Documentation Status

This is a substantial project. The first pass documented the architecture, core backend functions, Java model/wrapper/frontend structure, tests/build setup, generated source indexes, concrete risks, parser/SQLite/patternfinder helpers, the `Viewer.java` method map, and the non-`Viewer.java` viewer support map. The second pass added deeper backend, Java core/JNA, and frontend references plus narrower generated indexes for backend and Java core functions. Later passes compared the older source tree, Abdul's fork, the downloaded `[MAIN]` Java runtime, and the `[MAIN]` native backend DLL. The remaining work, if more depth is desired, is line-by-line commentary for the very large `Viewer.java` constructor body and selected large JavaFX windows, plus an actual Windows toolchain/build-file repair for Abdul. See `00-progress-cache.md` for exact continuation instructions.
