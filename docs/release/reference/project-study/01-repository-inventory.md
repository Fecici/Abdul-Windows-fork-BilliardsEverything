# Repository Inventory

## Inventory commands

The inventory was built with read-only commands:

- `Get-ChildItem -Force`
- `git status`, `git branch --all`, `git log --oneline --decorate -n 30`
- `rg --files`
- extension counts and line counts with PowerShell
- `Get-ChildItem -Recurse -File -Include ...`
- `jar tf`, `javap`, `Get-FileHash`, `objdump -p`, `strings`

Command failures were recorded:

- `git status` at workspace root failed because `C:\Users\Owner\Documents\Programming Projects\research` is not a Git repository.
- The `sourcecode-billiards_everythingMay2,2026/billiards_everything` tree is not a Git repository in this workspace.
- A first attempt to glob runtime `run*.bat` through a bracketed path failed with PowerShell path syntax; explicit `-LiteralPath` reads succeeded.

## Top-level workspace

| Entry | Meaning |
|---|---|
| `.ghidra_scratch/` | Local reverse-engineering scratch area. Contains a copy/input `backend.dll`. |
| `.metadata/`, `.vscode/` | IDE metadata. |
| `-Abdul-s-fork-BilliardsEverything/` | Active Abdul source tree. Git repo. |
| `sourcecode-billiards_everythingMay2,2026/billiards_everything/` | Other source tree, plus generated `docs/source-study` reports. |
| `[MAIN] The Tokarsky-Marinov Finder, Tester and Prover (Windows-Kaiden jar) Aug 28,2025 - Copy/BilliardsEverythingsWindowsJarAug28Backup/` | Main Windows runtime artifact set with patched jar and native backend. |
| `[UNPATCHED] The Tokarsky-Marinov Finder, Tester and Prover (Windows-Kaiden jar) Aug 28,2025/BilliardsEverythingsWindowsJarAug28Backup/` | Unpatched version of the main runtime artifact set. |
| `The Tokarsky-Marinov Covers Aug 27,2025 (Windows-Kaiden jar)/` | Older Windows runtime focused on covers; uses older Java bundle. |
| `TokarskySolverPatchTestingSpace/`, `billiards_review_bundle/`, `squarestxt/` | Supporting analysis/test artifacts. |
| PDFs, zips, named archive folders | Project notes, historical packages, data dumps, or experiments. |

## Abdul Git state

Abdul is the only source tree here with Git metadata.

Evidence:

- Command: `git -C -Abdul-s-fork-BilliardsEverything branch --all`
- Result: local `main`, remote `origin/main`.
- Command: `git -C -Abdul-s-fork-BilliardsEverything log --oneline --decorate -n 30`
- Result: `093d4d9 (HEAD -> main, origin/main, origin/HEAD) rename`, `6212490 Initial commit (without large binaries)`.
- Command: `git -C -Abdul-s-fork-BilliardsEverything status --short --branch`
- Result: dirty Gradle cache files, modified `vary4.cpp`, `wrapper.cpp`, `BoyanMenu.java`, untracked `src/backend.7z`.

Important current dirty source changes:

| File | Current local state | Impact |
|---|---|---|
| `src/backend/cpp/vary4.cpp` | Contains a stray `2` after a declaration: `starts;2`. | Compile blocker. |
| `src/backend/cpp/wrapper.cpp` | Current diff appears line wrapping only in inspected hunks. | No proven behavior change. |
| `src/java/billiards/viewer/BoyanMenu.java` | Current diff appears formatting/line wrapping only in inspected hunks. | No proven behavior change. |
| `.gradle/*` | Modified binary/cache files. | Build cache noise. Do not use as source evidence. |

## Abdul source roots

| Path | Role |
|---|---|
| `src/java/billiards/codeseq/` | Java code sequence validation, canonicalization, classification. |
| `src/java/billiards/wrapper/` | JNA bridge declarations and data structs. |
| `src/java/billiards/database/` | Java-side database setup, model conversion, storage parsing. |
| `src/java/billiards/viewer/` | JavaFX UI, rendering, task orchestration, cover/vary windows. |
| `src/java/billiards/vary/` | Java-side vary helpers calling native vary implementations. |
| `src/java/billiards/cover/` | Cover file parsing and cover tree decoding. |
| `src/java/patternfinder/` | Pattern finder UI/workflows. |
| `src/backend/headers/` | C++ headers for math, geometry, database, cover, wrappers. |
| `src/backend/cpp/` | C++ backend implementation. |
| `src/cover/cpp/` | Cover CLI/helper sources. |
| `src/test/java/` | Java tests for code sequence behavior. |
| `src/test/cpp`, `src/test/headers` | Boost.Test C++ tests. |

Source size summary for Abdul `src` from local counting:

| Extension | Files | Approx lines |
|---|---:|---:|
| `.java` | 117 | 32021 |
| `.cpp` | 45 | 14322 |
| `.hpp` | 80 | 9823 |

The counts include the current dirty worktree and should be treated as inventory, not an exact stable metric.

## Build files and scripts

| File | Role | Evidence |
|---|---|---|
| `build.gradle` | Primary Gradle Java/native build. Defines JavaFX 21.0.1, JNA 5.13.0, SQLite JDBC, Java 17 target, native backend library, `run`, `copyRuntimeLibs`, `runtimeClasspathAsPath`. | `build.gradle:15-17`, `35-43`, `98-101`, `103-169`, `187-232`. |
| `gradle/wrapper/gradle-wrapper.properties` | Gradle wrapper. Distribution is Gradle 8.0. | File inspection. |
| `gradlew`, `gradlew.bat` | Gradle wrapper launchers. | File inventory. |
| `meson.build` | Alternative native build for shared `backend`, `cover`, tests, and debug executable. | File inspection. |
| `Makefile` | Legacy/Unix make flow for cover-oriented executable. | File inspection. |
| `package-windows.bat` | Windows packaging script using `jlink` and `jpackage`. Contains unverified or likely invalid batch syntax and hard-coded DLL name. | `package-windows.bat:31-46`, `50-62`. |
| `package-mac.sh` | macOS packaging script. Abdul changes app version 2.2 -> 2.3. | Source-vs-Abdul diff. |
| `format.fish` | Formatting helper. | File inventory. |
| `updater.bat`, `updater.sh`, `app/updater.*` | Updater artifacts. Runtime main jar lacks `Updater.class`; source has updater UI. | Jar comparison. |

## Java frontend files

Important Java classes:

| Area | Files/classes |
|---|---|
| Startup | `billiards.viewer.Main`, `billiards.viewer.DBGui`, `patternfinder.PatternFinder` |
| Main UI | `billiards.viewer.Viewer`, `BoyanMenu`, `InfoWindow`, `CoverWindow`, `SmallCoverWindow`, `VaryWindowL`, `CycleVaryWindow` |
| Tasks | `DrawPictureTask`, `DontDrawPictureTask`, `PolyVaryTask`, `VaryLTask`, `CycleVaryTask`, `DrawPictureTaskShowLR`, `DrawPictureTaskUseLR`, `DrawPictureTaskTriples` |
| Code sequences | `CodeSequence`, `ClassifiedCodeSequence`, `CodeType`, `InvalidCodeSequence`, `CompositionGenerator`, `Storage` |
| Data/model | `Info`, `InfoAll`, `Picture`, `PictureStable`, `PictureUnstable`, `LeftRight`, `Storage.Stable`, `Storage.Unstable` |
| Bridge | `Wrapper`, `ConnectionPool`, `CString`, `CPicture`, `CInfo`, `CInfoAll` |
| Cover parse/render support | `CoverStuff`, `HashTriple`, `Rectangle`, `TriplePair`, `HalfTriple` |

## Native backend files

Important C++ files:

| Area | Files/functions |
|---|---|
| JNA exports | `wrapper.cpp`, `wrapper.hpp`: `database_create`, `create_connection_pool`, `cover_wrapper`, `small_cover_wrapper`, `save_to_database`, `load_picture`, `load_info`, `bounding_polygon`, `vary_*_cpp`. |
| Code sequence math | `code_sequence.cpp`, `classified_code_sequence.cpp`. |
| MRR/equations | `equations.cpp`, `unfolding.cpp`, `shooting_vectors.cpp`, `refine.cpp`, `bounding_inequalities.cpp`, `bounding_region.cpp`. |
| Database | `database.cpp`, `database/viewer.cpp`, `database/serialize.cpp`, `database/deserialize.cpp`, headers under `headers/database`. |
| Cover verification | `verify.cpp`, `common.cpp`, `headers/verify.hpp`, `headers/common.hpp`. |
| Vary/search | `vary_cs.cpp`, `vary3.cpp`, `vary4.cpp`, matching headers. |
| Tests | `src/test/cpp/main.cpp`, `src/test/headers/*_test.hpp`. |

## Database files and paths

Java stores databases under:

```text
${user.home}/billiard-databases/<dbName>.sqlite
```

Evidence:

- `src/java/billiards/database/Admin.java:23-26`.
- `Admin.newJavaDB("garbage")` is called during startup at `Main.java:41`.
- Native connection pool is created through `Admin.getConnectionPool` and `Wrapper.createConnectionPool`.

Java-side table names created for the `garbage` database are `oso`, `osno`, `cs`, `ons`, and `cns`, each with a `code_sequence text primary key` schema.

## Tests

Java tests:

- `src/test/java/billiards/codeseq/ClassifiedCodeSequenceTest.java`
- `src/test/java/billiards/codeseq/CodeSequenceTest.java`

C++ tests:

- `src/test/cpp/main.cpp`
- `src/test/headers/bounding_region_test.hpp`
- `src/test/headers/code_sequence_test.hpp`
- `src/test/headers/diff_test.hpp`
- `src/test/headers/division_test.hpp`
- `src/test/headers/equations_test.hpp`
- `src/test/headers/general_test.hpp`
- `src/test/headers/gradient_test.hpp`
- `src/test/headers/parse_test.hpp`
- `src/test/headers/shooting_angles_test.hpp`
- `src/test/headers/trig_identities_test.hpp`

Build evidence:

- `build.gradle` defines Java `test` with JUnit Jupiter and native `test(NativeExecutableSpec)` under `model.components`.
- `testBackend` depends on `runTestExecutableTestGoogleTestExe`.

## Existing generated docs

Generated docs were found under:

```text
sourcecode-billiards_everythingMay2,2026/billiards_everything/docs/source-study/
```

Important files:

- `README.md`
- `01-architecture-and-math.md`
- `02-backend-core.md`
- `03-backend-algorithms.md`
- `04-java-core-frontend.md`
- `05-risks-optimizations.md`
- `06-tests-build.md`
- `07-symbol-index.md`
- `08-viewer-method-map.md`
- `09-parser-sqlite-patternfinder.md`
- `10-viewer-support-map.md`
- `11-backend-second-pass-reference.md`
- `12-java-core-second-pass-reference.md`
- `13-frontend-second-pass-reference.md`
- `14-test-distribution-comparison.md`
- `15-test-button-workflow-reference.md`
- `16-source-runtime-fork-version-matrix.md`
- `17-source-vs-abdul-fork-line-diff.md`
- `18-main-vs-abdul-fork-comparison.md`
- `19-main-backend-dll-vs-abdul-source.md`
- `20-abdul-windows-build-dependencies.md`
- function and symbol indexes such as `backend-function-index.txt`, `java-core-function-index.txt`, `viewer-internal-callgraph.txt`

These docs are useful as a guide. This study verified key claims directly against source, jar entries, class bytecode, DLL exports/strings, hashes, scripts, and build files.

## Runtime artifacts

Main runtime folder:

```text
[MAIN] The Tokarsky-Marinov Finder, Tester and Prover (Windows-Kaiden jar) Aug 28,2025 - Copy/BilliardsEverythingsWindowsJarAug28Backup/
```

Important artifacts:

| Artifact | SHA256 |
|---|---|
| `billiard-viewer.jar` | `2905523F8EE505556F60D6D82704298268343D658F63BADDE3C536CF9D2EEF61` |
| `billiard-viewer-patched-hashtriple-java17.jar` | `AAEEB0B26A2388EDD2B6E839D8901D781F84C300C608743BA40249EA270C077B` |
| `backend/shared/backend.dll` | `BECD0C3D59F8AE2BC48AF6020729FAF3A71B3EA7E2C515E352FE22BF33C37099` |

Main runtime includes:

- `backend/shared/backend.dll`
- `libboost_thread-mt.dll`
- `libgcc_s_seh-1.dll`
- `libgmp-10.dll`
- `libmpfi-0.dll`
- `libmpfr-6.dll`
- `libsqlite3-0.dll`
- `libstdc++-6.dll`
- `libtbb12.dll`
- `libtbbmalloc.dll`
- `libtbbmalloc_proxy.dll`
- `libwinpthread-1.dll`
- JavaFX 20.0.1 jars
- Bundled Java runtime with `JAVA_VERSION="17.0.16"`
- `run.bat`, `run2.bat`, `runDEBUG.bat`, `procwatch.ps1`
- cover and small-cover data folders, temp folders, logs

Runtime launch scripts:

- `run.bat` launches `billiard-viewer.jar` with bundled Java, JavaFX module path, `-Djava.library.path`, `PATH` pointing at `backend/shared`, `-Xss1000m`, and `-Xms1000m`.
- `run2.bat` launches `billiard-viewer-patched-hashtriple-java17.jar` with `-Xms2g`, `-Xmx10g`, `-Xss16m`.
- `runDEBUG.bat` launches the patched jar with `-Xss2m`, `MaxDirectMemorySize=2g`, Native Memory Tracking, and diagnostic options.

## Duplicated, stale, suspicious, generated

| Item | Classification | Evidence |
|---|---|---|
| Abdul `app/billiard-viewer.jar` | Stale/generated artifact | Project-class entries match other source tree app jar; source has newer Abdul edits not represented in this jar. |
| Abdul `app/backend/shared/libbackend.dylib` | Stale/generated artifact | macOS dylib in source tree; not a current Windows backend. |
| Abdul `build/` | Generated build output | Gradle output path. Do not treat as source. |
| Abdul `.gradle/` | Generated Gradle cache | Dirty local cache files. |
| `src/backend.7z` | Untracked archive | Not source truth unless unpacked and inspected later. |
| `Backupfile/`, `garbage.txt`, `iterToLimit.txt`, `tmp/*.txt`, `cover/*.txt` in Abdul | Data/working artifacts | Abdul-only in normalized source comparison. |
| `CoverWindow2.java`, `CoverWindow3.java`, `CoverWindow4.java` | Probably stale UI variants | Constructors are present, but `Viewer.java:600-608` comments out the window creation paths. |
| Existing `docs/source-study` | Generated docs | Useful guide; verified before using. |

