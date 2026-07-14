# Version Comparison: Abdul vs Source vs Runtime

## Scope

Compared surfaces:

1. Abdul source: `-Abdul-s-fork-BilliardsEverything`
2. Other source: `sourcecode-billiards_everythingMay2,2026/billiards_everything`
3. Runtime artifacts: main patched/unpatched Windows runtime folders, especially `billiard-viewer.jar`, `billiard-viewer-patched-hashtriple-java17.jar`, and `backend/shared/backend.dll`

Existing generated reports under `docs/source-study` were read first, especially `16` through `20`. Key claims were checked against current source diffs, jar entry comparison, `javap`, hashes, runtime scripts, DLL imports/exports, and strings.

## File-level source comparison

Normalized file comparison between Abdul and the other source tree, excluding generated build/runtime directories, found:

| Result | Count |
|---|---:|
| Same normalized files | 252 |
| Different text files | 12 |
| Abdul-only files | 18 |
| Source-only files | 1 |
| Unreadable/binary-ish files | 19 |

Different text files:

- `package-mac.sh`
- `src/backend/cpp/bounding_inequalities.cpp`
- `src/backend/cpp/unfolding.cpp`
- `src/backend/cpp/vary4.cpp`
- `src/backend/cpp/wrapper.cpp`
- `src/java/billiards/viewer/AutoPolyVaryLoad.java`
- `src/java/billiards/viewer/BoyanMenu.java`
- `src/java/billiards/viewer/CoverWindow.java`
- `src/java/billiards/viewer/Main.java`
- `src/java/billiards/viewer/PolyVaryLoad.java`
- `src/java/billiards/viewer/SuperPolyVaryLoad.java`
- `src/java/billiards/viewer/Viewer.java`

Source-only file:

- `dist/Instructions.txt`

## Behavior-impacting source differences

| Area | File/class/function/artifact | Difference | Version containing it | Behavioral impact | Windows impact | Confidence | Evidence path/command |
|---|---|---|---|---|---|---|---|
| Packaging | `package-mac.sh` | App version changed `2.2` -> `2.3`. | Abdul | Packaging metadata only. | None for Windows directly. | High | `git diff --no-index source/package-mac.sh abdul/package-mac.sh` |
| Native math/performance | `src/backend/cpp/bounding_inequalities.cpp`, `eliminate_phi` | Per-thread `std::set<LinComArrZ<XYEta>>` changed to vector buffers with `MAX_BUFFER_SIZE = 1000000`, periodic sort/unique, final merge into set. | Abdul source | Intended memory/performance change in phi elimination. Math result should match if dedup/merge is correct. Risk is memory spikes or ordering/dedup bugs. | Important: affects native backend build/runtime behavior on Windows. | High for code difference, medium for semantic equivalence | Source diff plus function at `bounding_inequalities.cpp:126-275`. Runtime DLL strings did not show `MAX_BUFFER_SIZE`. |
| Native debug output | `src/backend/cpp/unfolding.cpp` | `std::cout << "comb"` debug prints are commented out. | Abdul source | Less console noise. No math impact. | Minor. | High | Source diff and `unfolding.cpp` lines near curve generation. |
| UI defaults | `AutoPolyVaryLoad.java` | Default small-cover checkbox changed selected `true` -> `false`. | Abdul source | Users must opt into small-cover behavior. | Affects workflow parity with source/runtime. | High | Source diff. |
| UI defaults | `CoverWindow.java:120` | `addToSmallCoverCB.setSelected(false)` in Abdul; source default was true. | Abdul source | Cover calculation no longer automatically appends to small-cover window. | Affects Windows UX and cover workflow. | High | `rg -n "addToSmallCoverCB.setSelected" CoverWindow.java`; source diff. |
| UI defaults | `PolyVaryLoad.java`, `SuperPolyVaryLoad.java` | Small-cover defaults changed true -> false. | Abdul source | Same as above for poly/super vary load paths. | Workflow difference. | High | Source diff. |
| UI version | `Main.java:21` | Version string is `10.0.14`; other source/app jar contains `10.0.12`; runtime main class contains `BilliardsEverythingSpecialOpt`. | Abdul source and runtime differ | UI title/version semantics differ. | Helps identify which build is running. | High | `Main.java:21`; `javap -verbose Main`; jar string search. |
| UI rendering | `Viewer.java:2647-2683`, `3928-3932` | Reflection checkbox defaults true and multiple paths add `Affine` reflection transforms. | Abdul source | View starts reflected. Risk: repeated transforms if listener/start/action all add transforms without clearing. | Important for visual parity/debugging. | High for code difference, medium for defect conclusion | Source diff and `rg -n "reflectCheckBox|Affine" Viewer.java`. |
| Current dirty edit | `src/backend/cpp/vary4.cpp` | Local uncommitted stray `2` after `starts;`. | Abdul worktree only | Compile failure. | Blocks Windows native build. | High | `git diff -- src/backend/cpp/vary4.cpp`. |
| Current dirty edit | `wrapper.cpp`, `BoyanMenu.java` | Current local diff appears formatting only in inspected hunks. | Abdul worktree only | No proven behavior impact. | Low. | Medium | `git diff -- wrapper.cpp BoyanMenu.java`. |

## Jar-level comparison

### Abdul app jar vs other source app jar

The checked-in Abdul `app/billiard-viewer.jar` and the other source tree `app/billiard-viewer.jar` have identical project-class entry hashes for the compared entries. This means the checked-in Abdul app jar is stale relative to Abdul source changes.

Evidence:

- Command: `jar tf` and SHA comparison of project class entries.
- Result: 136 project classes same by entry hash.

### Other source app jar vs main runtime jar

Project-class comparison found:

| Result | Count |
|---|---:|
| Same classes | 117 |
| Different classes | 18 |
| Source-only classes | 1 |

Different runtime classes:

- `billiards/viewer/AutoPolyVaryLoad.class`
- `billiards/viewer/BoyanMenu$2.class`
- `billiards/viewer/BoyanMenu.class`
- `billiards/viewer/CoverWindow.class`
- `billiards/viewer/CycleVaryTask$1.class`
- `billiards/viewer/CycleVaryTask.class`
- `billiards/viewer/CycleVaryWindow.class`
- `billiards/viewer/Main.class`
- `billiards/viewer/PolyVaryLoad.class`
- `billiards/viewer/PolyVaryTask$1.class`
- `billiards/viewer/PolyVaryTask.class`
- `billiards/viewer/SmallCoverWindow.class`
- `billiards/viewer/SuperPolyVaryLoad.class`
- `billiards/viewer/Utils.class`
- `billiards/viewer/VaryWindowL.class`
- `billiards/viewer/Viewer$1.class`
- `billiards/viewer/Viewer$2.class`
- `billiards/viewer/Viewer.class`

Source-only:

- `billiards/viewer/Updater.class`

Runtime-only behavior evidence from bytecode/string/decompile-oriented checks:

| Area | File/class/function/artifact | Difference | Version containing it | Behavioral impact | Windows impact | Confidence | Evidence path/command |
|---|---|---|---|---|---|---|---|
| Runtime branding | `billiards/viewer/Main.class` | Contains string `BilliardsEverythingSpecialOpt`; Abdul source version string is `10.0.14`; Abdul app jar has `10.0.12`. | Runtime patched/unpatched jar | Clear runtime identity mismatch. | Useful sanity check when running. | High | `javap -verbose billiards.viewer.Main`; `Main.java:21`. |
| Updater | `billiards/viewer/Updater.class` | Present in source/app jar; absent from main runtime jar. | Source/Abdul app only | Runtime removed update UI/path. | Simplifies Windows runtime but diverges from source. | High | `jar tf` entry diff. |
| Cover UI | `CoverWindow`, `SmallCoverWindow`, `Viewer`, load classes | Runtime jars differ in cover/vary UI classes. Existing docs identify `replacePolygons`, Pi/2 prompt changes, and other runtime edits. | Runtime | Cover workflow may differ substantially from source/Abdul. | Important for parity tests. | Medium to high | Jar entry diff plus source-study `18`; exact source not available. |
| Vary UI/performance | `BoyanMenu`, `PolyVaryTask`, `CycleVaryTask`, `VaryWindowL` | Runtime jars differ in vary/task classes; strings include runtime-only speed-related markers in generated docs. | Runtime | Vary behavior and prompts may not match source. | Important for porting behavior. | Medium | Jar class diff; source-study `18`. |

### Main runtime jar vs patched main runtime jar

The patched jar differs from the unpatched main runtime jar in exactly one project class:

| Area | File/class/function/artifact | Difference | Version containing it | Behavioral impact | Windows impact | Confidence | Evidence path/command |
|---|---|---|---|---|---|---|---|
| Cover memory/rendering | `billiards/viewer/HashTriple.class` | Patched class is smaller and adds a `defaultColor` field; map fields are no longer final. Existing reverse-engineering notes say it avoids duplicating large color maps and provides fallback color. | Patched runtime jar only | Major memory reduction and null-color safety for huge covers. | High: patched runtime should be preferred for cover loading tests. | High for bytecode difference, medium for exact intent | `jar tf`/entry hash diff; `javap -private HashTriple`; runtime `billiards_reverse_engineering_notes.md`. |

Patched runtime hashes:

| Artifact | SHA256 |
|---|---|
| `billiard-viewer.jar` | `2905523F8EE505556F60D6D82704298268343D658F63BADDE3C536CF9D2EEF61` |
| `billiard-viewer-patched-hashtriple-java17.jar` | `AAEEB0B26A2388EDD2B6E839D8901D781F84C300C608743BA40249EA270C077B` |

## Native runtime comparison

Runtime DLL:

```text
[MAIN] .../BilliardsEverythingsWindowsJarAug28Backup/backend/shared/backend.dll
SHA256 BECD0C3D59F8AE2BC48AF6020729FAF3A71B3EA7E2C515E352FE22BF33C37099
```

DLL import evidence:

- `libboost_thread-mt.dll`
- `libgcc_s_seh-1.dll`
- `libgmp-10.dll`
- `libmpfi-0.dll`
- `libmpfr-6.dll`
- `libsqlite3-0.dll`
- `libstdc++-6.dll`
- `libtbb12.dll`
- `libwinpthread-1.dll`
- `KERNEL32.dll`
- `msvcrt.dll`
- `WS2_32.dll`

Export/string evidence includes JNA-visible names:

- `backend_cancel`
- `cover_wrapper`
- `cover_wrapper_all`
- `cover_wrapper_duplicate_stables`
- `cover_wrapper_half_duplicate_stables`
- `create_connection_pool`
- `database_create`
- `delete_from_database`
- `getNotFilledCoordinates`
- `load_all_equations`
- `load_picture`
- `load_picture_lr`
- `load_picture_lr_expando`
- `load_slope_info`
- `save_to_database`
- `small_cover_wrapper`
- `sqlite_error_logging`
- `vary_3_cpp`
- `vary_4_cpp`
- `vary_cs_cpp`

Comparison conclusions:

| Area | File/class/function/artifact | Difference | Version containing it | Behavioral impact | Windows impact | Confidence | Evidence path/command |
|---|---|---|---|---|---|---|---|
| Native ABI | `backend.dll` exports | Runtime DLL exposes the API expected by `Wrapper.java` for core calls. | Runtime | Confirms JNA bridge shape. | Required for Windows runtime. | High | `objdump -p backend.dll`, `strings backend.dll`, `Wrapper.java`. |
| Native toolchain | `backend.dll` imports | DLL is MinGW/GCC style and depends on GCC runtime and MinGW-built GMP/MPFR/MPFI/TBB/Boost/SQLite DLLs. | Runtime | Strong evidence Windows port should prefer MSYS2/MinGW/UCRT64 over MSVC. | High. | High | `objdump -p backend.dll` import table. |
| Abdul native math | `eliminate_phi` | Prior Ghidra/decompile report and runtime string checks indicate main runtime DLL follows source-style set insertion, not Abdul vector-buffer implementation. | Source/runtime vs Abdul | Runtime backend likely does not include Abdul performance change. | Important when comparing results/perf. | Medium | `source-study/19-main-backend-dll-vs-abdul-source.md`; `strings` did not find `MAX_BUFFER_SIZE`. |
| Debug prints | `unfolding.cpp` `comb` prints | Runtime DLL strings did not show standalone `comb`; only Windows header-related `combaseapi.h`. | Runtime | Runtime likely does not print Abdul/source debug `comb` text. | Low. | Medium | `strings backend.dll`; source diff. |

## Build-system differences and Windows impact

| Area | File/class/function/artifact | Difference | Version containing it | Behavioral impact | Windows impact | Confidence | Evidence path/command |
|---|---|---|---|---|---|---|---|
| Java version | `build.gradle`, runtime `java/release` | Source targets Java 17; runtime bundles Java 17.0.16; local default `java` is Java 24. | Source/runtime/local env | Running with Java 24 may work but is not the intended target. | Use JDK 17 for build/debug. | High | `build.gradle:100-101`; runtime `java/release`; `java -version`. |
| JavaFX | `build.gradle`, runtime scripts | Source uses JavaFX 21.0.1 dependencies; runtime ships JavaFX 20.0.1 jars. | Source vs runtime | UI runtime dependencies differ. | Classpath/module path must be explicit. | High | `build.gradle:15`, runtime `run*.bat`. |
| Native deps | `build.gradle`, runtime DLL imports | Source links GMP, MPFR, MPFI, SQLite, TBB, Boost; runtime ships matching MinGW DLL families. | Source/runtime | Native dependency availability determines build/load success. | Major Windows blocker. | High | `build.gradle:118-120`; `objdump -p backend.dll`. |
| Package script | `package-windows.bat` | Uses `$(call gradlew.bat -q runtimeClasspathAsPath)` inside batch and copies `libbackend.dll`. | Abdul source | Likely broken/unverified packaging. | Must fix before packaging. | High for script content, medium for failure until run | `package-windows.bat:31-46`. |
| Gradle run env | `build.gradle` | `run` sets `JNA_LIBRARY_PATH` and platform-specific dynamic-library env, but Windows branch uses `LD_LIBRARY_PATH`. | Abdul source | JNA path may work; dependent DLL search still needs `PATH`. | Need explicit PATH for MinGW deps. | High | `build.gradle:187-213`; runtime `run*.bat`. |

## Practical reconciliation order

1. Fix Abdul current syntax error after approval.
2. Establish a reproducible Abdul Java-only build and native build using JDK 17 plus MSYS2 UCRT64.
3. Compare Abdul source runtime behavior against patched runtime jar for these workflows: startup, load code, save/load DB, vary3/vary4, MRR, cover, small cover, huge cover load.
4. Decide whether to port the patched `HashTriple` behavior into Abdul source. It is a strong candidate because the patched runtime differs only in that class and the patch appears targeted at huge-cover memory.
5. Decide whether runtime-only UI/vary changes are product requirements or one-off local runtime edits.
6. Keep Abdul native `eliminate_phi` vector-buffer change only if tests and runtime parity checks pass, because it is behavior-adjacent math/performance code.
