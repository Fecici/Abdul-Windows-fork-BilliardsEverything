# Used vs Unused

## Method

Classification used conservative static evidence:

- Direct references from `rg`.
- Build inclusion in `build.gradle`, `meson.build`, `Makefile`.
- JNA native declarations and exported DLL symbols.
- JavaFX event handlers and task creation.
- Test source inclusion.
- Runtime jar manifest and jar class entry differences.
- Runtime native exports/imports/strings.
- Existing generated docs were used only as guides.

Important limitation: Java reflection, JNA loading, JavaFX callbacks, database-driven behavior, file-driven cover parsing, and manually run scripts can hide usage. "Unused" claims below are conservative.

## Definitely used

| Area | Files/classes/functions | Evidence |
|---|---|---|
| Startup | `billiards.viewer.Main`, `DBGui`, `Viewer`, `PatternFinder` | `Main.java:34-55` constructs these based on UI choice. |
| Native bridge | `Wrapper.java`, `ConnectionPool.java`, `CString`, `CPicture`, `CInfo`, `CInfoAll` | `Wrapper.java:36-667` declares and wraps native calls; `ConnectionPool` calls create/destroy. |
| Native exports | `wrapper.cpp`, `wrapper.hpp` | Java native names match runtime DLL exports and C++ functions. |
| Code sequence Java | `CodeSequence`, `ClassifiedCodeSequence`, `CodeType`, `InvalidCodeSequence` | Used throughout viewer, tasks, database, vary, tests. |
| Code sequence native | `code_sequence.cpp`, `classified_code_sequence.cpp` | Used by wrapper/database/math paths; C++ tests reference them. |
| Database Java | `Admin`, `Database`, `Info`, `InfoAll`, `Picture`, `PictureStable`, `PictureUnstable`, `Storage`, `LeftRight` | Startup, load/save, render conversion. |
| Database native | `database.cpp`, `database/viewer.cpp`, serialization/deserialization headers/sources | Called by `wrapper.cpp` load/save/info functions. |
| Viewer rendering | `Viewer.renderRegions`, `HashTriple`, geometry/render support classes | Direct calls from many UI/task paths; cover loading uses `HashTriple`. |
| Cover UI | `CoverWindow`, `SmallCoverWindow`, `CoverStuff` | Constructed at `Viewer.java:590-596`; parse/load paths at `Viewer.java:7997-8042`. |
| Native cover | `verify.cpp`, `common.cpp`, `verify.hpp`, `common.hpp` | Called by wrapper cover functions; runtime DLL exports cover wrappers. |
| Native MRR/math | `equations.cpp`, `bounding_inequalities.cpp`, `bounding_region.cpp`, `unfolding.cpp`, `refine.cpp`, `shooting_vectors.cpp` | Called by native save/load and tested by C++ tests. |
| Vary Java | `VaryCS`, `Vary3`, `Vary4`, `BoyanMenu`, `PolyVaryTask`, `VaryLTask`, `CycleVaryTask` | Direct references from `BoyanMenu`, `Viewer`, and task classes. |
| Vary native | `vary_cs.cpp`, `vary3.cpp`, `vary4.cpp` | Native wrappers call `fireAwayCS`, `fireAway3`, `fireAway4`; runtime exports vary functions. |
| Gradle build | `build.gradle`, `gradlew.bat`, `gradlew` | Primary source build. |

## Probably used

| Item | Classification | Evidence | Confidence |
|---|---|---|---|
| `patternfinder/*` | Probably used | `Main.start` can launch `PatternFinder`; patternfinder classes call `Wrapper.search`, `Wrapper.saveToDatabase`, `Wrapper.loadPictureLR`. | High |
| `BatchLoadStorage.java` | Probably used | Utility directly calls `Database.loadStorage`; direct use should be checked before deletion. | Medium |
| `DrawPictureTaskShowLR`, `DrawPictureTaskUseLR`, `DrawPictureTaskUseLRTest`, `DrawPictureTaskTriples` | Probably used | `Viewer.java` creates these in multiple conditional UI paths. | High |
| `Updater.java` and updater scripts | Source-used or legacy-used, not runtime-used | Source/app jar contains `Updater.class`; runtime jar lacks it. Need UI menu trace before removing from source. | Medium |
| `meson.build` | Alternative native build | Builds backend/shared library and test executables outside Gradle. Not primary Gradle flow. | Medium |
| `Makefile` | Legacy/cover build | Builds cover-oriented executable with native sources. Not primary Gradle flow. | Medium |
| `src/cover/cpp/*` | Probably used by cover CLI or legacy flow | Included by `Makefile`/cover executable; not core Java runtime. | Medium |

## Possibly used dynamically

| Item | Why conservative |
|---|---|
| JavaFX button handlers and menu handlers in `Viewer.java`, `BoyanMenu.java`, `CoverWindow.java`, `SmallCoverWindow.java` | Many are only referenced through lambdas/event callbacks. Static search can undercount them. |
| JNA native declarations in `Wrapper.java` | Native methods are resolved by name at runtime. Lack of Java direct references is not enough to mark unused. |
| Runtime file parsers in `CoverStuff` | Behavior is driven by external text files such as `cover.txt`, `stables.txt`, `triples.txt`. |
| SQLite table and serialized data fields | Some behavior is database-row driven. |
| Scripts under runtime folders | Users may run them manually; absence from source build does not mean unused. |

## Test-only

| Item | Evidence |
|---|---|
| `src/test/java/billiards/codeseq/ClassifiedCodeSequenceTest.java` | Java test source. |
| `src/test/java/billiards/codeseq/CodeSequenceTest.java` | Java test source. |
| `src/test/cpp/main.cpp` | Native test executable entry. |
| `src/test/headers/*_test.hpp` | Included by native test build. |
| Boost.Test native link dependency | `build.gradle` native test component links `boost_unit_test_framework`. |

## Build-only

| Item | Evidence |
|---|---|
| `build.gradle`, `settings.gradle` if present, Gradle wrapper files | Build system. |
| `gradle/wrapper/gradle-wrapper.properties` | Wrapper config. |
| `package-windows.bat`, `package-mac.sh` | Packaging scripts. |
| `format.fish` | Formatting helper. |
| `meson.build`, `Makefile` | Alternative build paths. |

## Generated artifacts

| Item | Evidence |
|---|---|
| `.gradle/` | Gradle cache. Dirty in Abdul worktree. |
| `build/` | Gradle output including jar and scripts. |
| `app/*.jar`, `app/backend/shared/*` in source trees | Checked-in runtime/dependency artifacts; stale relative to Abdul source. |
| `docs/source-study/*` | Generated study docs and symbol indexes. |
| Runtime `java/`, `javafx/`, `backend/shared/*.dll`, logs, temp folders | Built/runtime artifact set. |
| `.ghidra_scratch/` | Reverse-engineering scratch artifacts. |

## Stale or suspicious

| Item | Classification | Evidence | Confidence |
|---|---|---|---|
| Abdul `app/billiard-viewer.jar` | Stale artifact | Project-class entries match the other source app jar; it does not include Abdul source changes such as `Main.java` version `10.0.14`. | High |
| Abdul `app/backend/shared/libbackend.dylib` | Stale/non-Windows artifact | macOS dylib in app folder; Windows port needs DLL built from current source. | High |
| Abdul `.gradle` modified files | Generated/noise | Git status shows dirty cache files. | High |
| Abdul `src/backend.7z` | Untracked archive | Not part of normal source tree until explicitly inspected. | Medium |
| `Backupfile/` | Suspicious backup/data | Abdul-only in comparison; not referenced in build evidence. | Medium |
| `garbage.txt`, `iterToLimit.txt`, `testcoord.txt`, `tmp/*.txt`, `cover/*.txt` in Abdul | Working data artifacts | Abdul-only or local data. Some may be manually used by UI workflows, so do not delete blindly. | Medium |
| `package-windows.bat` | Suspicious/unverified | Contains likely invalid batch syntax and hard-coded `libbackend.dll`. | High |
| Runtime `runDEBUG.bat` | Runtime helper, not source build | Useful for runtime debug, not evidence of source debug parity. | High |

## Probably unused or legacy

| Item | Claim | Evidence | Confidence |
|---|---|---|---|
| `CoverWindow2.java`, `CoverWindow3.java`, `CoverWindow4.java` | Probably stale UI variants in current Abdul source. | `Viewer.java:600-608` has their construction commented out; `Viewer` constructs only `CoverWindow` and `SmallCoverWindow` at `590-596`. | Medium |
| `dist/Instructions.txt` in other source tree | Source-only packaging/doc artifact. | File-level comparison found source-only. | Medium |
| `app/updater.*` in source tree | Stale for main runtime. | Main runtime jar lacks `Updater.class`; source still has updater artifacts. | Medium |
| `Makefile` cover build | Legacy relative to Gradle app build. | Gradle is primary app build; Makefile builds a cover executable. Could still be manually useful. | Low to medium |
| Meson debug executable path | Alternative/manual build path. | Not referenced by Gradle or runtime scripts. Could be useful for native debugging. | Low |

No file in this section should be deleted without a second pass that runs tests, searches reflection/resource usage, and checks whether external scripts or user workflows call it.

## Source-only vs runtime-only behavior

| Behavior | Classification | Evidence |
|---|---|---|
| `Updater` UI/code | Source-only relative to main runtime | Runtime jar lacks `Updater.class`; source/app jar has it. |
| Patched `HashTriple` default-color/memory behavior | Runtime patched only | Main patched jar differs from unpatched runtime jar by exactly `HashTriple.class`; `javap -private` shows `defaultColor`. |
| Abdul reflection default true | Abdul source only | `Viewer.java:2658` and related source diff; runtime class differs but exact runtime semantics require decompile/bytecode trace. |
| Abdul small-cover default false | Abdul source only compared with other source | Source diff and `CoverWindow.java:120`; runtime classes differ and need runtime behavior verification. |
| Abdul `eliminate_phi` vector-buffer implementation | Abdul source only relative to source/runtime evidence | Source diff; runtime DLL analysis points to source-style set insertion. |

