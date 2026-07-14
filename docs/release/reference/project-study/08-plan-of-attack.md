# Plan of Attack

## Working rule

Use Abdul source as the porting baseline, but compare behavior against the patched main runtime. Treat the generated `source-study` docs as a map, not truth. Do not delete or refactor stale-looking files until a clean source build, tests, and runtime parity checks exist.

## What to read first

1. `docs/codex-project-study/00-executive-summary.md`
2. `docs/codex-project-study/04-build-and-debug-windows.md`
3. `src/java/billiards/viewer/Main.java`
4. `src/java/billiards/wrapper/Wrapper.java`
5. `src/backend/cpp/wrapper.cpp`
6. `src/java/billiards/codeseq/CodeSequence.java`
7. `src/java/billiards/codeseq/ClassifiedCodeSequence.java`
8. `src/java/billiards/database/Database.java`
9. `src/backend/cpp/equations.cpp`
10. `src/backend/cpp/bounding_inequalities.cpp`
11. `src/java/billiards/viewer/Viewer.java`
12. `src/java/billiards/viewer/CoverWindow.java`
13. `src/java/billiards/viewer/SmallCoverWindow.java`
14. `src/java/billiards/viewer/HashTriple.java`

## What to run first

From Abdul:

```powershell
cd "C:\Users\Owner\Documents\Programming Projects\research\-Abdul-s-fork-BilliardsEverything"
git status --short --branch
git diff -- src/backend/cpp/vary4.cpp src/backend/cpp/wrapper.cpp src/java/billiards/viewer/BoyanMenu.java
```

Then, after approval to fix the current syntax typo:

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
$env:PATH = "C:\msys64\ucrt64\bin;$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat --no-daemon clean test
.\gradlew.bat --no-daemon backendSharedLibrary testBackend
.\gradlew.bat --no-daemon run --debug-jvm
```

Also run the patched runtime as a behavior reference:

```powershell
cd "C:\Users\Owner\Documents\Programming Projects\research\[MAIN] The Tokarsky-Marinov Finder, Tester and Prover (Windows-Kaiden jar) Aug 28,2025 - Copy\BilliardsEverythingsWindowsJarAug28Backup"
$env:PATH = "$PWD\backend\shared;$PWD\java\bin;$env:PATH"
.\run2.bat
```

## What to debug first

1. Startup and native loading:
   - Java: `Main.init`, `Wrapper.Native.register`, `Admin.getConnectionPool`
   - Native: `sqlite_error_logging`, `create_connection_pool`

2. One small stable code:
   - Use test example `1 1 1` or source-comment example `1 3 3`.
   - Java: `CodeSequence.create`, `ClassifiedCodeSequence.calculateCodeType`, `Database.loadStorage`
   - Native: `load_picture`, `save_to_database`, `calculate_stable`

3. One small unstable/closed code:
   - Use test example `2 2` (`CNS`) or comment example `1 2 1 4`.
   - Native: `calculate_unstable`, `calculate_bounding_line_segment`

4. One cover calculation with a small cover file.
   - Java: `CoverWindow`, `SmallCoverWindow`, `Viewer.loadCover`, `CoverStuff.parseCover`, `HashTriple`
   - Native: `check_cover`, `check_small_cover`

5. One vary path:
   - Java: `BoyanMenu.varyTriangles`, `Wrapper.vary3Cpp` or `vary4Cpp`
   - Native: `vary_3_cpp`, `vary_4_cpp`, `fireAway3`, `fireAway4`

## Files that matter most

| Priority | Files |
|---|---|
| Highest | `Main.java`, `Viewer.java`, `Wrapper.java`, `wrapper.cpp`, `build.gradle`, `package-windows.bat` |
| Math core | `CodeSequence.java`, `ClassifiedCodeSequence.java`, `equations.cpp`, `bounding_inequalities.cpp`, `bounding_region.cpp`, `unfolding.cpp`, `refine.cpp`, `shooting_vectors.cpp` |
| Persistence | `Admin.java`, `Database.java`, `database.cpp`, `database/viewer.cpp`, serialization/deserialization files |
| Cover | `CoverWindow.java`, `SmallCoverWindow.java`, `CoverStuff.java`, `HashTriple.java`, `verify.cpp`, `common.cpp` |
| Vary | `BoyanMenu.java`, `VaryCS.java`, `Vary3.java`, `Vary4.java`, `PolyVaryTask.java`, `VaryLTask.java`, `CycleVaryTask.java`, `vary_cs.cpp`, `vary3.cpp`, `vary4.cpp` |

## Parts to ignore initially

- `CoverWindow2.java`, `CoverWindow3.java`, `CoverWindow4.java`, unless a workflow proves they are needed.
- `app/` checked-in jars/libs as source truth.
- `.gradle/` and `build/`.
- Old packaging and updater flow until the app runs from source.
- Large historical data files until a specific cover/debug case requires them.
- Meson/Makefile paths until Gradle is understood, unless Gradle native toolchain selection blocks progress.

## Verifying behavior against existing runtime

Use the patched main runtime as the reference for user-visible behavior:

1. Record exact runtime jar hash and backend DLL hash.
2. Launch `run2.bat`.
3. Exercise the same inputs in runtime and Abdul source build:
   - Startup and DB selection.
   - Enter `1 1 1`, `2 2`, and `1 3 3`.
   - Save/load database entries.
   - Load a small cover.
   - Run a small vary task.
4. Compare:
   - UI defaults.
   - Code classification.
   - Rendered region shape.
   - Cover rectangle counts and colors.
   - Console output.
   - SQLite rows generated.
   - Performance and memory.

Do not expect line-by-line debug parity with the runtime jar because runtime classes differ and `HashTriple.class` is patched.

## Consolidating differences

Recommended order:

1. Fix current Abdul local compile typo after approval.
2. Make the Abdul build reproducible on Windows.
3. Port the patched `HashTriple` behavior into source if runtime huge-cover tests confirm it is needed.
4. Decide whether Abdul's small-cover default false is intentional.
5. Decide whether Abdul's reflection default true is intentional and fix repeated transform behavior if retained.
6. Reconcile runtime-only cover UI changes such as `replacePolygons` if they are needed for the current workflow.
7. Validate Abdul `eliminate_phi` vector-buffer implementation against source/runtime behavior with tests and a known cover workload.
8. Only then remove or quarantine stale UI variants or packaging leftovers.

## Windows port approach

Stage 1: build and run unmodified behavior.

- Use JDK 17.
- Use MSYS2 UCRT64 GCC and matching native dependencies.
- Make Gradle build the native backend with UCRT64 GCC.
- Make Java find `backend.dll` and its dependencies.
- Launch with JavaFX module path and JNA path.

Stage 2: debug workflow.

- Add a debug native build profile later with `-g -O0` and no LTO.
- Use `gradlew run --debug-jvm` for Java.
- Attach MinGW `gdb` to the Java process for native.

Stage 3: package.

- Fix `package-windows.bat` after the build is stable.
- Generate a clean runtime with JDK 17, JavaFX, jar, backend DLL, and native dependency DLLs.
- Verify that a fresh machine/user profile can launch it.

## Tests after each change

After fixing build/toolchain:

- `.\gradlew.bat --no-daemon test`
- `.\gradlew.bat --no-daemon testBackend`
- Startup smoke test.
- `CodeSequence` classification examples: `1 1 1`, `2 2`, `1 1 2 2 1 1 3 3`.
- `Database.loadStorage` for one stable and one unstable code.
- Cover parse/load for a small known cover.
- One small vary run.

After `HashTriple` or cover changes:

- Load small cover.
- Load large patched-runtime cover if available.
- Check memory growth.
- Check color fallback/null behavior.
- Compare rectangle counts with runtime.

After native math changes:

- Native tests.
- Stable/unstable storage generation.
- Known source-comment `1 3 3` polygon.
- A cover verification smoke test.
- Compare against runtime backend output where possible.

## What not to touch yet

- Do not delete stale-looking classes or scripts.
- Do not rewrite `Viewer.java` broadly.
- Do not port native build to MSVC first.
- Do not trust checked-in `app/` artifacts as build output from Abdul.
- Do not change math code until the current behavior is captured by tests.
- Do not package before source run/debug works.

## Staged schedule

Day 1:

- Read this study and the source-study comparison docs.
- Record current Git status.
- Fix only the `vary4.cpp` syntax typo after approval.
- Set JDK 17 and install/check MSYS2 UCRT64 dependencies.
- Try Java tests and native build.
- Launch patched runtime for reference.

Day 2-3:

- Make Gradle consistently use UCRT64 GCC.
- Get `backend.dll` built from Abdul.
- Launch Abdul with `gradlew run --debug-jvm`.
- Step startup and one `Database.loadStorage` call in Java.
- Attach native debugger at `load_picture` and `calculate_stable`.

Week 1:

- Build a small parity test checklist against runtime.
- Add or run focused tests for code classification, stable/unstable computation, cover parse, and vary parsing.
- Decide on `HashTriple` patch import.
- Decide on small-cover and reflection UI defaults.
- Create a debug-native build mode.
- Repair `package-windows.bat` only after source launch is reliable.

Later:

- Consolidate runtime-only UI/vary differences.
- Broaden native math tests.
- Audit stale files after usage is proven.
- Build a clean Windows package and test it from a fresh user profile.
- Document reproducible release steps.

## Risk register

| Risk | Impact | Mitigation |
|---|---|---|
| Current Abdul dirty syntax error | Native build fails immediately. | Fix after approval; keep diff small. |
| Gradle selects MSVC | Native build fails or ignores flags. | Use MSYS2 UCRT64 GCC and explicit toolchain config if needed. |
| Missing MPFI/GMP/MPFR/TBB/Boost DLLs | Runtime load failure. | Keep UCRT64 `bin` and backend dir on `PATH`; package DLLs later. |
| Runtime jar differs from source | Debugging wrong code. | Build Abdul artifacts for source stepping; use runtime only for reference. |
| `HashTriple` memory behavior | Huge cover loading can fail. | Port runtime patch after tests confirm behavior. |
| Java vary parsing race | Intermittent missing/corrupt results. | Replace `ArrayList` with synchronized or sequential parsing after source edits are allowed. |
| `eliminate_phi` Abdul change | Math/performance regression. | Add parity tests and run known workloads. |
| Reflection transform duplication | UI coordinate/render confusion. | Audit and centralize reflection transform after source edits are allowed. |
| Stale database rows | False confidence during testing. | Use fresh test DB names and record DB paths. |
| Package script broken | Release artifact unusable. | Defer packaging until build/run/debug are stable. |

