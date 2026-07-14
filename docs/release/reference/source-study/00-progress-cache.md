# Progress Cache And Handoff

This file is the cache for future agents. Read it before continuing documentation work.

## Non-Negotiable Constraint

The user explicitly requested: do not alter any code. Documentation files under `docs/source-study/` are allowed and were created. No source files under `src/` were edited.

## Workspace Root

`C:\Users\Owner\Documents\Programming Projects\research\sourcecode-billiards_everythingMay2,2026\billiards_everything`

## Current Baseline And Comparison State

Treat Abdul's fork as the main development source branch:

```text
C:\Users\Owner\Documents\Programming Projects\research\-Abdul-s-fork-BilliardsEverything
```

The older source tree in this folder remains useful for architecture and line-by-line source study, but Abdul has source deltas documented in `17-source-vs-abdul-fork-line-diff.md`. The compiled `[MAIN]` Windows runtime is a separate specialized build documented in `18-main-vs-abdul-fork-comparison.md` and `19-main-backend-dll-vs-abdul-source.md`.

Important current facts:

- Abdul's checked-in `app/billiard-viewer.jar` and `app/backend/shared/libbackend.dylib` are stale relative to Abdul source and match the older source tree's packaged app artifacts.
- `[MAIN]` Java differs only in a concentrated set of viewer classes; retained decompiled references are under `artifacts/main-patched-cfr-20260615/java-main-changed-classes/`.
- The full noisy CFR dependency dump was removed after the changed classes and diffs were retained.
- `[MAIN]` native `backend.dll` was decompiled with Ghidra 12.1.2. The output is under `artifacts/main-patched-cfr-20260615/backend-ghidra/output/`.
- Ghidra reported 2,284 detected functions, 2,284 successful decompiles, and 0 failed decompiles.
- The focused native file for the main backend delta is `artifacts/main-patched-cfr-20260615/backend-ghidra/output/backend.dll.ghidra-eliminate_phi-lambda-do_complete.c`.
- Abdul's `eliminate_phi` uses vector buffering plus `sort/unique`; `[MAIN]` uses source-style `std::set` / `_Rb_tree` insertion there.
- Abdul does not currently build a complete Windows app in this workspace. See `20-abdul-windows-build-dependencies.md`.

## Commands Already Run

Use these as reproducible inventory commands:

```powershell
rg --files
Get-ChildItem -Recurse -File src | Where-Object { $_.Extension -in '.java','.cpp','.hpp','.h','.c','.cc' } | Sort-Object FullName | ForEach-Object { $rel = Resolve-Path -Relative $_.FullName; $lines = (Get-Content -LiteralPath $_.FullName | Measure-Object -Line).Lines; [PSCustomObject]@{Lines=$lines; Path=$rel} } | Format-Table -AutoSize
Get-ChildItem -Recurse -File src | Where-Object { $_.Extension -in '.java','.cpp','.hpp','.h','.c','.cc' } | Group-Object Extension
ctags -x --sort=no --languages=C++,Java src
ctags -x --sort=no --languages=Java src\java\billiards\viewer\Viewer.java | Select-String ' method '
ctags -R -x --sort=no --languages=C++,Java src | Out-File -FilePath docs/source-study/symbol-index-ctags.txt -Encoding utf8
Get-Content -Path docs/source-study/symbol-index-ctags.txt | Where-Object { $_ -match '\s(method|function|prototype)\s+' } | Out-File -FilePath docs/source-study/function-index-ctags.txt -Encoding utf8
Select-String -Path docs/source-study/function-index-ctags.txt -Pattern 'src/java/billiards/viewer' | Where-Object { $_.Line -notmatch 'Viewer.java' } | Select-Object -ExpandProperty Line | Set-Content -Path docs/source-study/viewer-support-function-index.txt -Encoding utf8
Select-String -Path docs/source-study/function-index-ctags.txt -Pattern 'src/backend' | Select-Object -ExpandProperty Line | Set-Content -Path docs/source-study/backend-function-index.txt -Encoding utf8
Select-String -Path docs/source-study/function-index-ctags.txt -Pattern 'src/java/billiards/(codeseq|database|geometry|wrapper|vary)' | Select-Object -ExpandProperty Line | Set-Content -Path docs/source-study/java-core-function-index.txt -Encoding utf8
```

Later comparison/build commands run from the research workspace:

```powershell
.\gradlew.bat --no-daemon build
java -jar build\libs\billiard-viewer.jar
C:\msys64\usr\bin\pacman.exe -Q
C:\msys64\usr\bin\pacman.exe -Sl ucrt64
```

The Gradle command above was run in Abdul's fork and failed in native C++ tasks because MSVC could not find Boost/Eigen/TBB headers and because the build is using GCC-style flags with MSVC.

`ctags` exists at:

`C:\winlibs-x86_64-posix-seh-gcc-14.2.0-llvm-18.1.8-mingw-w64ucrt-12.0.0-r1\mingw64\bin\ctags.exe`

Do not run commands that write into `src`. If generating symbol indexes, write only under `docs/source-study/` and make that clear.

## Files Read In Detail

Existing context docs:

- `README.md`
- `VaryDocs.md`
- `Vary4Docs.md`
- `build.gradle`

C++ core:

- `src/backend/headers/common.hpp`
- `src/backend/headers/code_sequence.hpp`
- `src/backend/cpp/code_sequence.cpp`
- `src/backend/headers/classified_code_sequence.hpp`
- `src/backend/cpp/classified_code_sequence.cpp`
- `src/backend/headers/code_type.hpp`
- `src/backend/headers/general.hpp`
- `src/backend/cpp/general.cpp`
- `src/backend/headers/numbers.hpp`
- `src/backend/headers/math/symbols.hpp`
- `src/backend/cpp/math/symbols.cpp`
- `src/backend/headers/math/lin_com_arr.hpp`
- `src/backend/headers/math/lin_com_map.hpp`
- `src/backend/headers/math/polynomial.hpp`
- `src/backend/headers/math/trig.hpp`
- `src/backend/headers/math/xyz.hpp`
- `src/backend/headers/diff.hpp`
- `src/backend/cpp/diff.cpp`
- `src/backend/headers/division.hpp`
- `src/backend/cpp/division.cpp`
- `src/backend/headers/trig_identities.hpp`
- `src/backend/cpp/trig_identities.cpp`
- `src/backend/headers/equations.hpp`
- `src/backend/cpp/equations.cpp`
- `src/backend/headers/unfolding.hpp`
- `src/backend/cpp/unfolding.cpp`
- `src/backend/headers/shooting_vectors.hpp`
- `src/backend/cpp/shooting_vectors.cpp`
- `src/backend/headers/bounding_region.hpp`
- `src/backend/cpp/bounding_region.cpp`
- `src/backend/headers/refine.hpp`
- `src/backend/cpp/refine.cpp`
- `src/backend/headers/database.hpp`
- `src/backend/cpp/database.cpp`
- `src/backend/headers/database/viewer.hpp`
- `src/backend/headers/parse.hpp`
- `src/backend/cpp/parse.cpp`
- `src/backend/headers/sqlite.hpp`
- `src/backend/headers/database/admin.hpp`
- `src/backend/cpp/database/admin.cpp`
- `src/backend/headers/database/serialize.hpp`
- `src/backend/cpp/database/serialize.cpp`
- `src/backend/headers/database/deserialize.hpp`
- `src/backend/cpp/database/deserialize.cpp`
- `src/backend/cpp/database/viewer.cpp`
- `src/backend/headers/wrapper.hpp`
- `src/backend/cpp/wrapper.cpp`
- `src/backend/headers/vary_cs.hpp`
- `src/backend/cpp/vary_cs.cpp`
- `src/backend/headers/vary3.hpp`
- `src/backend/cpp/vary3.cpp`
- `src/backend/headers/vary4.hpp`
- `src/backend/cpp/vary4.cpp`
- `src/backend/headers/triangle_billiard.hpp`
- `src/backend/cpp/triangle_billiard.cpp`
- `src/backend/headers/triangle_billiard4.hpp`
- `src/backend/cpp/triangle_billiard4.cpp`
- `src/backend/cpp/verify.cpp` was revisited in the second pass, especially parsing, cover recursion, cover report generation, small-cover handling, duplicate-stable checking, and restore/update functions.
- `src/backend/headers/cover/cover.hpp`
- `src/backend/headers/geometry/algorithms.hpp`
- `src/backend/headers/geometry/convex_polygon.hpp`

Java core:

- `src/java/billiards/codeseq/CodeSequence.java`
- `src/java/billiards/codeseq/ClassifiedCodeSequence.java`
- `src/java/billiards/codeseq/Storage.java`
- `src/java/billiards/database/Database.java`
- `src/java/billiards/database/Info.java`
- `src/java/billiards/database/Picture.java`
- `src/java/billiards/wrapper/Wrapper.java`
- `src/java/billiards/geometry/TriangleBilliard.java`
- `src/java/billiards/geometry/TriangleBilliard4.java`
- `src/java/billiards/geometry/ConvexPolygon.java`
- `src/java/billiards/vary/VaryCS.java`
- `src/java/billiards/vary/Vary3.java`
- `src/java/billiards/vary/Vary4.java`
- `src/java/billiards/viewer/Utils.java`
- `src/java/billiards/viewer/Viewer.java` was indexed by method list, not read line-by-line.
- `src/java/billiards/viewer/Viewer.java` lines 3895-8278 were read for method-level documentation after index generation; the constructor body lines 569-3894 were not read line-by-line.
- `src/java/billiards/viewer/CycleVaryWindow.java` was indexed by method list, not fully read.
- `src/java/billiards/viewer/DrawPictureTask.java`
- `src/java/billiards/viewer/DrawPictureTaskShowLR.java`
- `src/java/billiards/viewer/DrawPictureTaskUseLR.java`
- `src/java/billiards/viewer/DrawPictureTaskUseLRTest.java`
- `src/java/billiards/viewer/DrawPictureTaskTriples.java`
- `src/java/billiards/viewer/DontDrawPictureTask.java`
- `src/java/billiards/viewer/PolyVaryTask.java`
- `src/java/billiards/viewer/VaryLTask.java`
- `src/java/billiards/viewer/CycleVaryTask.java`
- `src/java/billiards/viewer/PixelRadianMap.java`
- `src/java/billiards/viewer/HashTriple.java`
- `src/java/billiards/viewer/BackwardForward.java`
- `src/java/billiards/viewer/Cycle.java`
- `src/java/billiards/viewer/SideSum.java`
- `src/java/billiards/viewer/PriorityExecutor.java`
- `src/java/patternfinder/PatternFinder.java`
- `src/java/patternfinder/PatUtils.java`
- `src/java/patternfinder/SuperCheckTask.java`
- `src/java/patternfinder/SearchWindow.java`
- `src/java/patternfinder/OneCodeWindow.java`
- `src/java/patternfinder/Single.java`
- `src/java/patternfinder/Triple.java`
- `src/java/patternfinder/Spattern.java`
- `src/java/patternfinder/Tpattern.java`
- `src/java/patternfinder/ThreeState.java`

## Documentation Completed In This Pass

Completed in detail:

- Mathematical object model: triangle angle space, code sequences, stable/unstable/open/closed types.
- C++ symbolic algebra primitives and exact/inexact numeric types.
- C++ and Java code sequence validation/canonicalization/classification.
- C++ unfolding, shooting vector selection, curve generation, bounding region computation, and MRR refinement.
- C++ database loading/saving patterns and Java/JNA wrapper flow.
- C++ and Java vary algorithms at a functional level.
- Java domain model, geometry model, wrapper model, and viewer task grouping at a functional level.
- Concrete correctness and optimization log.
- Build/test setup and existing test coverage.
- Generated all-symbol and function/method/prototype ctags indexes.
- Method-level documentation for every detected `Viewer.java` method.
- Function-level documentation for parser, SQLite, database serialization/admin/viewer helpers, and the full `patternfinder` package.
- Generated a non-`Viewer.java` viewer support method index and documented the major support/task/window classes.
- Second pass: generated `backend-function-index.txt` with 1,069 backend function/prototype/method rows.
- Second pass: generated `java-core-function-index.txt` with 268 Java core/JNA method rows.
- Second pass: added `11-backend-second-pass-reference.md` with deeper C++ ABI, database, cover verification, geometry, symbolic math, billiard-algorithm, and optimization notes.
- Second pass: added `12-java-core-second-pass-reference.md` with deeper method-level Java core/JNA documentation.
- Second pass: added `13-frontend-second-pass-reference.md` with deeper frontend/viewer method and workflow documentation.

Not yet complete:

- Full line-by-line commentary for the `Viewer.java` constructor body and selected large viewer methods if desired.
- Full line-by-line commentary for selected large JavaFX windows in `src/java/billiards/viewer` (`BoyanMenu`, `CycleVaryWindow`, `CoverWindow`, `IterateToLimitWindow`, `StablesWindow`, `VaryWindowL`).

## Next Agent Pickup Plan

Continue without editing source code:

1. Generate or manually create `docs/source-study/14-viewer-constructor-line-notes.md`.
2. Use `Get-Content -LiteralPath src\java\billiards\viewer\Viewer.java` in chunks covering lines 569-3894.
3. For each constructor chunk, document fields, event handlers, rendering controls, task controls, and state mutation.
4. Then create `docs/source-study/15-large-viewer-window-line-notes.md` covering selected large viewer windows/tasks not already expanded in `10-viewer-support-map.md` or `13-frontend-second-pass-reference.md`.
5. Update this progress cache after each chunk with exact line ranges completed.

Recommended chunk order for `Viewer.java`:

- Lines 1-568: imports, fields, UI component inventory, constructor setup.
- Lines 569-3894: constructor body and UI wiring.
- Lines 3895-4315: start, expando, iteration, and left/right helpers.
- Lines 4316-4950: vary loading, file loading, draw codes, progress windows.
- Lines 4951-5616: zoom, calculate, pan/click, hole finding, color selection.
- Lines 5617-6637: full rendering pipeline.
- Lines 6638-end: file parsing, poly-vary, auto-vary, iteration utilities, cover loading.

## Important Findings To Preserve

High-confidence correctness risks are documented in `05-risks-optimizations.md`. Most important:

- C++ `CodeSequence::create` misuses `std::string::find`, likely misclassifying exceptions.
- Java `Wrapper.vary3Cpp` and `Wrapper.vary4Cpp` append to a non-thread-safe `ArrayList` inside a parallel stream.
- Java `Wrapper.loadPictureLR` compares strings with `==`.
- C++ `Unfolding::generate_curves_lr(..., left_rights)` merges maps with `insert`, likely dropping duplicate equation keys from different threads.
- C++ `TriangleBilliard4` port appears to discard `Vector2D::sub` return values, so some angle calculations use un-subtracted vectors.
- C++ `Vary4::fireAway4` appears to call `makeStarts(startBilliard, movesMax, cores, ...)`, which likely reverses the intended `depth=0, maxDepth=cores` call.
- Java `Database.codeAndOEMatch` appears to index `codeNumbers[i]` and `charAt(i)` inside the inner loop where `j` is intended.
- Second-pass additional suspected issue: in C++ `verify.cpp`, `UpdateCover::operator()(cover::Divide)` appears to update all four quarter lambdas through `cover0` and `quarter_covers.get<0>()`. If source edits are later allowed and cover updating behaves incorrectly, inspect this first.

## Testing

Earlier passes did not run tests. In the 2026-06-15 Abdul follow-up, `.\gradlew.bat --no-daemon build` was run in Abdul's fork to verify Windows build status. Java tasks were up to date, but native C++ tasks failed because MSVC could not find Boost/Eigen/TBB headers and was ignoring GCC-style flags. `java -jar build\libs\billiard-viewer.jar` was also run and failed with missing JavaFX runtime components. The build run wrote normal Gradle cache/build metadata under Abdul's `.gradle/` and `build/`; no source files were intentionally edited.
