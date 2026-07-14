# [MAIN] vs Abdul Fork Comparison

Date: 2026-06-15

## Scope

Compared artifacts:

- `[MAIN]`: `The Tokarsky-Marinov Finder, Tester and Prover (Windows-Kaiden jar) Aug 28,2025 - Copy/BilliardsEverythingsWindowsJarAug28Backup`
- Abdul fork: `-Abdul-s-fork-BilliardsEverything`
- Source tree: `sourcecode-billiards_everythingMay2,2026/billiards_everything`

The unpatched `[MAIN]` jar was not analyzed in detail because the patched jar differs only by the known `HashTriple` class patch. The main comparison below is the patched `[MAIN]` runtime jar decompiled with CFR, then compared against the source runtime and Abdul source.

Generated evidence files were originally created under `C:/tmp` and are now preserved in:

- `docs/source-study/artifacts/main-patched-cfr-20260615/java-main-changed-classes`
- `docs/source-study/artifacts/main-patched-cfr-20260615/diffs-vs-source-app`
- `docs/source-study/artifacts/main-patched-cfr-20260615/backend-dll-analysis`
- `docs/source-study/artifacts/main-patched-cfr-20260615/backend-ghidra/output`
- `C:/tmp/billiards_source_app_cfr_20260615`
- `C:/tmp/billiards_source_vs_abdul_current_20260615`

The full noisy CFR output tree was removed after the changed project classes were copied into `java-main-changed-classes/`. The retained Java references are the classes that actually differ.

## Bottom Line

Abdul's checked-in `app` runtime is not actually Abdul's changed program. Its `app/billiard-viewer.jar` and `app/backend/shared/libbackend.dylib` are byte-identical to the source tree's `app` runtime artifacts. That means Abdul's packaged app does not include Abdul's Java or C++ source edits unless it is rebuilt.

Abdul's source fork is close to the source tree, not close to `[MAIN]`. It picks up only part of `[MAIN]` behavior: the changed default for several "Add codes to small cover" checkboxes. It does not include `[MAIN]`'s special-optimization version string, UI rearrangement, updater removal, `HashTriple` memory patch, `replacePolygons` small-cover behavior, Cycle/Poly vary logging and flow changes, or the SuperPoly load checkbox wiring.

On Windows, `[MAIN]` can launch using its bundled runtime scripts and native DLL. Abdul's fork did not complete a full Windows build here because the native C++ build could not find required Boost, Eigen, TBB, and Boost.Test headers under the MSVC toolchain. A Java-only jar was produced, but it does not launch as the same program because no Windows `backend.dll` was produced. Dependency details are in `20-abdul-windows-build-dependencies.md`.

## Artifact Identity

| Artifact | SHA-256 |
| --- | --- |
| `[MAIN]/billiard-viewer.jar` | `2905523F8EE505556F60D6D82704298268343D658F63BADDE3C536CF9D2EEF61` |
| `[MAIN]/billiard-viewer-patched-hashtriple-java17.jar` | `AAEEB0B26A2388EDD2B6E839D8901D781F84C300C608743BA40249EA270C077B` |
| `[MAIN]/backend/shared/backend.dll` | `BECD0C3D59F8AE2BC48AF6020729FAF3A71B3EA7E2C515E352FE22BF33C37099` |
| Abdul `app/billiard-viewer.jar` | `0C9A67AE775057BE461D3579B76EE515EE8D110A5DE4078BBED57FC70D7303B8` |
| Source `app/billiard-viewer.jar` | `0C9A67AE775057BE461D3579B76EE515EE8D110A5DE4078BBED57FC70D7303B8` |
| Abdul `app/backend/shared/libbackend.dylib` | `F3BE37A56BD2BD74D91E9E37023594E4931713973B29C0D0C5B42BFF7D66713A` |
| Source `app/backend/shared/libbackend.dylib` | `F3BE37A56BD2BD74D91E9E37023594E4931713973B29C0D0C5B42BFF7D66713A` |
| Abdul rebuilt Java jar, `build/libs/billiard-viewer.jar` | `2DAFC110D28C57DCD2EBB9014FCE2E33F6341B3FBC720845364F990A1C41B42B` |

The matching Abdul/source `app` hashes are important: the packaged Abdul app is a stale copy of the source app runtime.

## Build And Launch Status

### `[MAIN]`

`[MAIN]` has its own bundled Windows Java runtime, JavaFX SDK, and `backend.dll`. A run-script style launch probe using the bundled Java/JavaFX/native directory started successfully and remained running until the probe killed it after 8 seconds. Output included:

```text
Threads available: 10
```

This confirms `[MAIN]` can launch far enough to initialize JavaFX and load its native backend on Windows.

### Abdul Fork

`./gradlew.bat --no-daemon build` did not complete.

Java compilation succeeded with warnings and a Java jar was produced, but the native C++ build failed in these Gradle tasks:

- `:compileBackendStaticLibraryBackendCpp`
- `:compileBackendSharedLibraryBackendCpp`
- `:compileTestExecutableTestCpp`

The failing compiler was MSVC `cl.exe`. It ignored GCC-style flags such as `-O3`, `-march=native`, `-flto`, and `-ftrapv`, then failed on missing native headers:

- `tbb/parallel_invoke.h`
- `boost/format.hpp`
- `eigen3/Eigen/Dense`
- `boost/algorithm/string.hpp`
- `boost/cstdfloat.hpp`
- `boost/test/unit_test.hpp`

Local dependency state found during the follow-up pass:

- `C:\msys64\mingw64` has Boost, GCC, GMP, MPFR, and SQLite.
- `C:\msys64\mingw64` is missing Eigen3, TBB, and MPFI.
- MSYS2 `ucrt64` has packages for the complete required native stack.
- Abdul's checked-in `app/backend/shared` native libraries are macOS `.dylib` files, not Windows build inputs.

Plain `java -jar build/libs/billiard-viewer.jar` also fails because JavaFX is not bundled into that jar:

```text
Error: JavaFX runtime components are missing
```

A Gradle-style launch probe with JavaFX on the module path got past JavaFX, but failed during application init because the Windows native backend was not built:

```text
UnsatisfiedLinkError: Unable to load library 'backend'
Native library (win32-x86-64/backend.dll) not found in resource path
```

Conclusion: Abdul's current source can produce a Java jar on this machine, but it cannot currently build and launch the full Windows application without fixing or providing the native Windows backend dependencies and `backend.dll`.

## Source Tree vs Abdul Fork

The current source-to-Abdul source diff is saved at:

```text
C:/tmp/billiards_source_vs_abdul_current_20260615/source_src_to_abdul_src_current.diff
```

Summary: 14 files changed, with 288 insertions and 39 deletions. Some of that is non-code or formatting.

### Real Abdul Source Changes

#### Version String

`billiards.viewer.Main`:

- Source: `10.0.12`
- Abdul source: `10.0.14`
- `[MAIN]`: `BilliardsEverythingSpecialOpt`

This is visible branding only unless other code branches on the version string.

#### Small-Cover Defaults

Abdul source changes the initial state of "Add codes to small cover" from selected to unselected in:

- `AutoPolyVaryLoad.java`
- `PolyVaryLoad.java`
- `SuperPolyVaryLoad.java`
- `CoverWindow.java`

This matches one part of `[MAIN]`: those flows no longer add to the small cover by default. The capability still exists in Abdul source if the user manually checks the box.

Important distinction: `[MAIN]` does more than change the default in `CoverWindow`. When selected, `[MAIN]` replaces the small-cover text with the new polygon list. Source and Abdul source append/prepend through `SmallCoverWindow.addPolygons`.

#### Reflection Default And Transform Handling

Abdul source changes `Viewer` so the reflect checkbox defaults to selected and adds reflection transforms through listener/startup hooks:

- `reflectCheckBox` default changes from `false` to `true`.
- A selected-property listener adds an `Affine` reflection transform when selected and clears transforms when unselected.
- Startup calls schedule reflection setup with `Platform.runLater`.
- A new `updateReflection()` method adds another reflection transform when selected.

This is not in `[MAIN]` and not in the source tree. It is worth inspecting because startup can add reflection transforms more than once. Two vertical reflections can visually cancel while still leaving a changed transform stack.

#### Native `eliminate_phi` Container Change

In `src/backend/cpp/bounding_inequalities.cpp`, Abdul changes per-thread accumulation from `std::set<LinComArrZ<XYEta>>` to `std::vector<LinComArrZ<XYEta>>` with a `MAX_BUFFER_SIZE` cleanup step:

- Add candidates to a vector.
- When the vector grows past 1,000,000 entries, sort and `unique` it.
- Merge thread vectors into a final `std::set`.

The symbolic computation being performed is the same: add positive/negative phi terms, remove phi, divide content. The difference is memory and performance behavior. Duplicates can accumulate between cleanup passes, and each cleanup does a vector sort/unique.

#### Native Debug Prints Removed

In `src/backend/cpp/unfolding.cpp`, two debug `std::cout << "comb"` lines are commented out. Behavior is otherwise unchanged.

#### Current Uncommitted Formatting/Comment Changes

Abdul currently has uncommitted edits in:

- `src/backend/cpp/vary4.cpp`
- `src/backend/cpp/wrapper.cpp`
- `src/java/billiards/viewer/BoyanMenu.java`

The observed hunks are formatting, line wrapping, spacing around a ternary expression, and a comment. No behavior change was found in those hunks.

#### Non-Code Artifacts

Abdul also has non-code artifacts such as `.DS_Store` and `src/no.txt`. These are not functional program changes.

## `[MAIN]` Decompiled Runtime vs Source/Abdul

The patched `[MAIN]` jar was decompiled with CFR. Decompiled file-level diffs against the source app jar are saved in:

```text
docs/source-study/artifacts/main-patched-cfr-20260615/diffs-vs-source-app
```

Readable decompiled references for just those changed `[MAIN]` Java classes are saved in:

```text
docs/source-study/artifacts/main-patched-cfr-20260615/java-main-changed-classes
```

Classes changed in `[MAIN]` relative to the source app runtime are concentrated in `billiards.viewer`:

- `AutoPolyVaryLoad`
- `BoyanMenu`
- `CoverWindow`
- `CycleVaryTask`
- `CycleVaryWindow`
- `HashTriple`
- `Main`
- `PolyVaryLoad`
- `PolyVaryTask`
- `SmallCoverWindow`
- `SuperPolyVaryLoad`
- `Utils`
- `VaryWindowL`
- `Viewer`

The source app jar has `Updater.class`; `[MAIN]` does not.

All checked non-viewer computational Java packages were byte-identical between the source app jar and patched `[MAIN]` jar, including:

- `billiards.codeseq`
- `billiards.cover`
- `billiards.database`
- `billiards.geometry`
- `billiards.math`
- `billiards.wrapper`
- `billiards.vary`
- `billiards.patternfinder`

The `[MAIN]` native backend was also decompiled with Ghidra. See `19-main-backend-dll-vs-abdul-source.md` for backend details. The main confirmed backend difference is Abdul's `eliminate_phi` vector-buffer path versus `[MAIN]`'s source-style `std::set` / `_Rb_tree` insertion path.

### `[MAIN]` Version And Updater Removal

`[MAIN]` changes the application version text to:

```text
BilliardsEverythingSpecialOpt
```

`[MAIN]` removes the updater path:

- No `Updater.class`
- No updater field in `Viewer`
- No "Check for Updates" button/action path in the normal UI

Source and Abdul source still contain the updater code path.

### `[MAIN]` Small-Cover Behavior

`[MAIN]` changes the small-cover default and behavior:

- Auto/poly/super/vary load windows display a "Marco speed is ON" label.
- Their "Add codes to small cover" default is `false`.
- `CoverWindow` also defaults small-cover addition to `false`.
- When selected in `CoverWindow`, `[MAIN]` calls `smallCoverWindow.replacePolygons(res)`.
- Source and Abdul source call `smallCoverWindow.addPolygons(res)`.

`SmallCoverWindow` in `[MAIN]` is changed:

- `addPolygons` is replaced by `replacePolygons`.
- The polygon text is replaced instead of prepended/appended to existing text.
- `appendStablesInfo("// Small Cover")` and `appendTriplesInfo("// Small Cover")` calls are removed.
- The prompt text changes from fractional value of Pi to Pi/2.

Abdul source does not implement this `[MAIN]` `replacePolygons` behavior.

### `[MAIN]` HashTriple Patch

The patched `[MAIN]` jar has a changed `HashTriple` class.

Differences from source and Abdul:

- `stableMap`, `tripleMap`, and `halfTripleMap` are no longer final.
- Adds a `defaultColor`, initialized to black.
- `addStables`, `addTriples`, and `addHalfTriples` store the incoming `MutableMap` directly when possible.
- If direct assignment is not possible, they clear and `putAll`.
- They no longer populate `colorMap` for every rectangle on add.
- `remove` also removes the corresponding color entry.
- `getColor` returns a per-rectangle override from `colorMap` or falls back to `defaultColor`.

This patch reduces color-map population and can reduce memory pressure. Abdul source and Abdul packaged runtime do not include it.

### `[MAIN]` Boyan Menu/UI Rearrangement

`[MAIN]` changes `BoyanMenu` layout:

- Some text widths are changed.
- `Triplescb` exists but is removed from the code type checkbox row.
- `cycleVaryButton` moves from the vary row to the super row.
- Spacing in one row shrinks from 10 to 3.

This affects what controls are visible and naturally reachable, not the underlying mathematical classes.

### `[MAIN]` Viewer UI And Reachability Changes

`Viewer` changes in `[MAIN]` include:

- Removes updater wiring and update button.
- Changes colors of `iterateToLimitBtn` and `patternCalculatorBtn`.
- Changes code-sequence text column width from 10 to 14.
- Clears `smallCoverAreas` when clearing the current/OBO view.
- Moves/removes several buttons from normal visible rows.

Notable visible-row differences:

- `[MAIN]` hbox2 contains `reflectCheckBox`, `allCheckBox`, `infoButton`, `patternCalculatorBtn`, `iterateToLimitBtn`.
- Source/Abdul hbox2 contains `reflectCheckBox`, `allCheckBox`, `infoButton`, `polyLoadButton`, `polyLoadDBButton`, `parallelogramButton`.
- `[MAIN]` Boyan menu row contains `smallCoverButton` where source/Abdul expose `loadDirectoryButton`.
- `[MAIN]` OBO row contains only `btnLoadOBOFile`, `lineNumberTxt`, `btnGo`.
- Source/Abdul OBO row also contains `smallCoverButton`, `patternCalculatorBtn`, and `updateButton`.

Result: in `[MAIN]`, several source/Abdul functions still exist in code but are no longer exposed through the same normal UI path.

### `[MAIN]` Cycle Vary Flow

`CycleVaryTask` in `[MAIN]` changes computation/logging flow:

- Adds an `AtomicInteger codeNum`.
- Iterates coordinates by index.
- Skips the pixel-color covered check for the first coordinate.
- Logs `// pixel N`.
- Logs standard code output for the first newly loaded code using `Utils.standard(...)`.

`CycleVaryWindow` changes:

- Label names are changed for the Boyan workflow.
- The stage is no longer closed immediately after starting.
- The original viewer scale is not restored after "Not covered".
- Logs current cycle/representative/point information.
- Adds the selected coordinate itself to `pointsFiltered` before auto-recursed points.
- Partial results append cover info only when `autoCover` in one path where source also printed in `mode == 0`.

The important computation difference is that `[MAIN]` always includes the selected coordinate in the filtered point list for that cycle-vary path, and it changes when covered points are skipped.

### `[MAIN]` Poly Vary Flow

`PolyVaryTask` in `[MAIN]` adds a counter and logs standard code output with `Utils.standard(...)`.

This mostly affects emitted progress/output text rather than the lower-level polygon or code-sequence algorithms.

### `[MAIN]` Tooltip Behavior

`Utils.setupCustomTooltipBehavior` in `[MAIN]` no longer sets the tooltip show duration to 5 seconds.

## Functions No Longer Used Or Normally Reachable

### In `[MAIN]`

Removed or not present:

- `Updater.class`
- `Viewer` updater field/wiring
- "Check for Updates" button/action path
- `SmallCoverWindow.addPolygons`, replaced by `replacePolygons`

Still present but no longer normally exposed through the same UI rows:

- `polyLoadButton`
- `polyLoadDBButton`
- `parallelogramButton`
- `loadDirectoryButton`
- Source/Abdul OBO-row `smallCoverButton`
- Source/Abdul OBO-row `patternCalculatorBtn`

Effectively not user-toggleable in the changed Boyan menu row:

- `Triplescb`, because `[MAIN]` removes it from the displayed code-type checkbox row.

### In Abdul Source

No major method was removed from Abdul source relative to the source tree in the inspected diffs. The meaningful disabled-by-default behavior is:

- Auto/poly/super/cover workflows no longer add codes to the small cover by default.

The native `comb` debug prints are commented out, so those debug outputs are no longer used if Abdul's native code is rebuilt.

### In Abdul Packaged `app`

Abdul's packaged `app` does not use Abdul's source changes at all because its jar and dylib are identical to the source tree's packaged app artifacts.

## Same Behavior Between `[MAIN]` And Source/Abdul

The substantial Java computation packages are the same between `[MAIN]` patched runtime and the source app runtime at bytecode level:

- Code sequences
- Cover data structures
- Database package
- Geometry package
- Math package
- Java wrapper declarations
- Vary package
- Pattern finder package

So, for Java-side mathematical primitives and most non-viewer computation, `[MAIN]` is not a different algorithmic branch. The differences are concentrated in viewer orchestration, UI reachability, logging/output, small-cover handling, and the patched `HashTriple` storage behavior.

Native code cannot be line-for-line equated from source because `[MAIN]` ships an optimized Windows `backend.dll`. A symbol/string/export/full-disassembly backend analysis is now preserved in `docs/source-study/artifacts/main-patched-cfr-20260615/backend-dll-analysis`, with a backend-specific comparison in `19-main-backend-dll-vs-abdul-source.md`.

The confirmed native backend difference found so far is Abdul's `src/backend/cpp/bounding_inequalities.cpp:146-195`: Abdul uses vector buffering plus periodic sort/unique in `eliminate_phi`, while `[MAIN]`'s DLL disassembly shows source-style `std::set` insertion through `std::_Rb_tree_insert_and_rebalance`.

## What Is Worth Looking At

Highest-value Abdul changes:

1. `Viewer` reflection changes. This is Abdul-only and likely needs review because duplicate reflection transforms can be added during startup.
2. `bounding_inequalities.cpp` vector buffering in `eliminate_phi`. This is Abdul-only and could matter for memory/performance.
3. Small-cover default changes. These match a small piece of `[MAIN]`, but Abdul lacks `[MAIN]`'s `replacePolygons` behavior.

Highest-value `[MAIN]` changes not present in Abdul:

1. `HashTriple` memory patch.
2. `SmallCoverWindow.replacePolygons` and related cover-window routing.
3. Cycle-vary selected-coordinate and pixel-skip flow changes.
4. UI reachability changes, especially removed updater and hidden/moved poly/load controls.
5. `SuperPolyVaryLoad` receiving the `superAutoCb` checkbox.

Lower-value/noise:

- Version string changes by themselves.
- Java formatting changes.
- Native debug-print comments.
- Non-code files such as `.DS_Store` and `src/no.txt`.

## Verification Commands Run

Build and launch:

- `./gradlew.bat --no-daemon build` in Abdul fork: failed in native C++ tasks.
- `./gradlew.bat --no-daemon jar` in Abdul fork: succeeded.
- Abdul JavaFX launch probe: failed on missing `backend` native library.
- `[MAIN]` run-script style launch probe: succeeded to application startup and was manually stopped after the probe window.

Decompile/diff:

- CFR 0.152 was used to decompile patched `[MAIN]` and source app jars.
- Decompiled changed classes were diffed into `C:/tmp/billiards_main_vs_source_cfr_diffs_20260615`.
- Current source-to-Abdul source diff was generated into `C:/tmp/billiards_source_vs_abdul_current_20260615`.
