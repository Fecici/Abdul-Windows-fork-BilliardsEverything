# Executive Summary

## What this project does

This is a JavaFX desktop application backed by a native C++ library for studying triangular billiards code sequences. The application lets a user enter, generate, vary, save, load, cover, and visualize code sequences and regions such as MRRs and covers. Java owns the UI, tasks, parsing, rendering, database-facing model conversion, and JNA calls. C++ owns the heavy mathematical algorithms, exact arithmetic, SQLite persistence of computed objects, cover verification, and native vary/search routines.

Evidence:

- `-Abdul-s-fork-BilliardsEverything/src/java/billiards/viewer/Main.java:21-65` starts JavaFX, creates the `garbage` database, creates a native connection pool, and starts `Viewer` or `PatternFinder`.
- `-Abdul-s-fork-BilliardsEverything/src/java/billiards/wrapper/Wrapper.java:36` registers the native library named `backend` through JNA.
- `-Abdul-s-fork-BilliardsEverything/src/backend/cpp/wrapper.cpp` implements exported C entry points that call `calculate_stable`, `calculate_unstable`, `check_cover`, `check_small_cover`, `fireAwayCS`, `fireAway3`, and `fireAway4`.
- `-Abdul-s-fork-BilliardsEverything/src/java/billiards/viewer/Viewer.java:5787` renders model objects and cover rectangles into JavaFX images.

## Working baseline

Use `-Abdul-s-fork-BilliardsEverything` as the working source baseline because that is the active fork the user named. Do not treat its checked-in `app/` runtime, its `build/` output, or the separate Windows runtime folders as matching the current source. The active Abdul worktree is dirty and has a current syntax-breaking local edit in `src/backend/cpp/vary4.cpp`.

Evidence:

- Command: `git -C -Abdul-s-fork-BilliardsEverything status --short --branch`
- Result: branch `main...origin/main`; modified `.gradle/*`, `src/backend/cpp/vary4.cpp`, `src/backend/cpp/wrapper.cpp`, `src/java/billiards/viewer/BoyanMenu.java`; untracked `src/backend.7z` and Gradle cache binaries.
- Command: `git -C -Abdul-s-fork-BilliardsEverything diff -- src/backend/cpp/vary4.cpp`
- Key result: line with `std::vector<std::tuple<...>> starts;2`, which will not compile.

## The three project surfaces

| Surface | Path | Role | Trust level |
|---|---|---|---|
| Abdul source | `-Abdul-s-fork-BilliardsEverything` | Active working source tree. Prioritize for porting. | Source baseline, but current worktree is dirty. |
| Other source | `sourcecode-billiards_everythingMay2,2026/billiards_everything` | Earlier/source comparison tree with generated study docs. | Useful source comparison. Not active baseline. |
| Built/runtime artifacts | `[MAIN] The Tokarsky-Marinov Finder, Tester and Prover (Windows-Kaiden jar) Aug 28,2025 - Copy/BilliardsEverythingsWindowsJarAug28Backup` plus unpatched and older runtime folders | Windows runtime with jar, patched jar, backend DLL, dependency DLLs, bundled Java/JavaFX, run scripts, cover data. | Runtime evidence only. Does not match either source tree exactly. |

## Biggest risks

1. Abdul currently has a local C++ syntax error in `src/backend/cpp/vary4.cpp`; no reliable Windows native build can start until this is resolved.
2. The native build is Unix/GCC shaped. `build.gradle` uses GCC/Clang flags (`-O3`, `-march=native`, `-flto`, `-ftrapv`) and Unix-style link names (`-lgmp`, `-lmpfr`, `-lmpfi`, `-lsqlite3`, `-ltbb12`, `-lboost_thread`, `-lboost_system`) while Gradle selected MSVC in a prior Windows attempt.
3. The runtime jar includes behavior not present in Abdul source, especially patched `HashTriple.class`, missing `Updater.class`, and changed UI/vary classes.
4. Abdul source changed UI defaults for small-cover behavior and reflection. The reflection implementation adds transforms in multiple places and may add repeated transforms.
5. `Wrapper.vary3Cpp` and `Wrapper.vary4Cpp` parse native results with `parallel()` into a plain `ArrayList`, unlike `varyCSCpp`, which uses a synchronized list. This is a likely race in Java parsing of native vary results.
6. Generated docs under `source-study` are useful and often accurate, but they are not source truth. Their key claims were checked against source, jar contents, hashes, exports, strings, and scripts in this pass.

## Known, unknown, needs verification

Known:

- Main Java class: `billiards.viewer.Main`.
- Native library name loaded by Java: `backend`.
- Database directory: `${user.home}/billiard-databases`.
- Core source path: `src/java`, `src/backend/cpp`, `src/backend/headers`, `src/test`.
- Runtime patched jar differs from unpatched main runtime jar by `billiards/viewer/HashTriple.class`.
- Runtime DLL is a MinGW/GCC-style PE DLL and exports the JNA-visible backend API.
- Abdul checked-in `app/billiard-viewer.jar` matches the other source tree app jar at project-class entry level and is stale relative to current Abdul source.

Unknown or still needs verification:

- Exact intended semantics of every runtime-only UI change such as `BilliardsEverythingSpecialOpt`, `Marco speed is ON`, and `replacePolygons`. They are runtime evidence, not Abdul source.
- Whether the Abdul reflection default should be retained when porting, or reconciled with runtime behavior.
- Whether a fully clean Abdul Windows build succeeds after the syntax error and toolchain/dependency issues are fixed.
- Whether native cover wrapper strings returned through JNA have acceptable ownership/leak behavior for long sessions.

## Most important files

| Area | Files/classes/functions |
|---|---|
| Startup | `src/java/billiards/viewer/Main.java`, `src/java/billiards/viewer/Viewer.java`, `src/java/patternfinder/PatternFinder.java` |
| Java/native bridge | `src/java/billiards/wrapper/Wrapper.java`, `ConnectionPool.java`, `CInfo*.java`, `CPicture.java`, `CString.java`; `src/backend/cpp/wrapper.cpp`, `src/backend/headers/wrapper.hpp` |
| Code sequence logic | `CodeSequence.java`, `ClassifiedCodeSequence.java`, `src/backend/cpp/code_sequence.cpp`, `classified_code_sequence.cpp` |
| Database/model | `Admin.java`, `Database.java`, `Info*.java`, `Picture*.java`, `Storage.java`; `src/backend/cpp/database.cpp`, `src/backend/cpp/database/viewer.cpp` |
| MRR/math | `equations.cpp`, `bounding_inequalities.cpp`, `bounding_region.cpp`, `unfolding.cpp`, `refine.cpp`, `shooting_vectors.cpp` |
| Covers | `CoverWindow.java`, `SmallCoverWindow.java`, `CoverStuff.java`, `HashTriple.java`; `verify.cpp`, `common.cpp` |
| Vary/search | `BoyanMenu.java`, `VaryCS.java`, `Vary3.java`, `Vary4.java`, `PolyVaryTask.java`, `VaryLTask.java`, `CycleVaryTask.java`; `vary_cs.cpp`, `vary3.cpp`, `vary4.cpp` |
| Build/debug | `build.gradle`, `gradle/wrapper/gradle-wrapper.properties`, `meson.build`, `Makefile`, `package-windows.bat`, runtime `run*.bat` |

## Shortest path to a Windows debug build

1. Use JDK 17, not the current default Java 24 visible on this machine. The runtime bundles Java 17.0.16, the source targets Java 17, and class files are major version 61.
2. Fix only the current local syntax typo in `src/backend/cpp/vary4.cpp` before building, after explicit approval because production source edits are out of scope for this study.
3. Install MSYS2 UCRT64 native dependencies: GCC, Boost, Eigen, TBB, GMP, MPFR, MPFI, SQLite.
4. Put `C:\msys64\ucrt64\bin` first on `PATH` and make Gradle use UCRT64 GCC instead of MSVC.
5. Build native backend and Java jar from Abdul:

```powershell
cd "C:\Users\Owner\Documents\Programming Projects\research\-Abdul-s-fork-BilliardsEverything"
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
$env:PATH = "C:\msys64\ucrt64\bin;$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat --no-daemon clean backendSharedLibrary jar
.\gradlew.bat --no-daemon run --debug-jvm
```

6. Attach a Java debugger to `localhost:5005`. For native debugging, run the JVM suspended with JDWP, then attach `gdb` from the same MinGW/UCRT64 toolchain to the Java process and set breakpoints in `wrapper.cpp` exports such as `load_picture`, `save_to_database`, `cover_wrapper`, `small_cover_wrapper`, `vary_4_cpp`, and deeper functions such as `calculate_stable`, `calculate_unstable`, `check_cover`, `eliminate_phi`, and `Unfolding::generate_curves`.

The existing main runtime can already be launched with Java debugging by inserting JDWP into `run2.bat` before `-jar`, using the patched jar and shipped `backend/shared` DLL directory. That is useful for behavior comparison, not for stepping Abdul source exactly.
