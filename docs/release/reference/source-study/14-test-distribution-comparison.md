# TEST Distribution And Patch Verification

Date of analysis: 2026-05-23

This note compares the source tree, the regular Windows finder distribution, and the `[TEST]` Windows finder distribution. It is intentionally documentation-only. No source files, jars, backend binaries, data files, or scripts were altered during this pass.

Current baseline note: this doc compares the earlier TEST distribution. For current consolidation, Abdul's source is the intended dev branch, `[MAIN]` is the decompiled Windows runtime reference, and the patched TEST `HashTriple` behavior is a Java-level runtime patch not present in Abdul source.

## Paths Studied

- Source tree: `sourcecode-billiards_everythingMay2,2026/billiards_everything`
- Regular distribution: `The Tokarsky-Marinov Finder, Tester and Prover (Windows-Kaiden jar) Aug 28,2025/BilliardsEverythingsWindowsJarAug28Backup`
- TEST distribution: `[TEST] The Tokarsky-Marinov Finder, Tester and Prover (Windows-Kaiden jar) Aug 28,2025 - Copy/BilliardsEverythingsWindowsJarAug28Backup`
- Temporary analysis workspace: `C:/tmp/billiards_test_reverse_engineering_20260523`

The temporary workspace contains generated inventories, `javap` disassemblies, jar extraction directories, source-vs-jar class comparisons, backend export listings, and cover-data token summaries. Those files are intermediate evidence, not runtime dependencies.

## Bottom Line

The user's stated patch is verified.

The TEST distribution contains a patched jar named `billiard-viewer-patched-hashtriple-java17.jar`. Compared with the regular `billiard-viewer.jar`, that patched jar changes exactly one class:

- `billiards/viewer/HashTriple.class`

The patch removes the memory-heavy per-rectangle color-map population done by `HashTriple.addStables`, `HashTriple.addTriples`, and `HashTriple.addHalfTriples`. It also avoids copying an incoming Eclipse `MutableMap` when the incoming map can be stored directly. The patched `getColor` method returns an explicit color override if present and otherwise falls back to a new default color field.

The regular jar in the TEST folder, `billiard-viewer.jar`, is byte-identical to the regular distribution jar. The patched behavior is used only when TEST `run2.bat` is used, because TEST `run2.bat` launches `billiard-viewer-patched-hashtriple-java17.jar`. TEST `run.bat` still launches the unpatched `billiard-viewer.jar`.

The source tree still contains the original unpatched `HashTriple.java`. Any future source rebuild from the current source tree would not automatically include the TEST memory patch unless that source file is changed later.

## Distribution File Differences

The regular and TEST distributions are mostly identical. The Java runtime, JavaFX libraries, original viewer jar, backend DLL, backend import library, and most support files match exactly. The TEST distribution differs mainly in:

- a much larger generated cover dataset
- the patched jar
- a `small_cover` directory
- a changed `run2.bat`
- several changed working-output text files

Generated evidence:

- `C:/tmp/billiards_test_reverse_engineering_20260523/original_file_inventory.csv`
- `C:/tmp/billiards_test_reverse_engineering_20260523/test_file_inventory.csv`
- `C:/tmp/billiards_test_reverse_engineering_20260523/original_vs_test_file_diff.csv`

TEST-only files:

| Path | Meaning |
| --- | --- |
| `billiard-viewer-patched-hashtriple-java17.jar` | Patched viewer jar used by TEST `run2.bat`. |
| `cover204.zip` | Additional cover archive in TEST distribution. |
| `small_cover/cover.txt` | Small-cover generated cover grid. |
| `small_cover/info.txt` | Small-cover metadata and run summary. |
| `small_cover/polygon.txt` | Small-cover polygon output. |
| `small_cover/precision.txt` | Small-cover precision value. |
| `small_cover/square.txt` | Small-cover square bounds. |
| `small_cover/stables.txt` | Small-cover stable list. |
| `small_cover/triples.txt` | Small-cover triple list. |
| `small_cover/unused.txt` | Small-cover uncovered/unused output. |

Selected TEST-different files:

| Path | Regular size | TEST size | Notes |
| --- | ---: | ---: | --- |
| `cover/cover.txt` | 966,392 | 295,713,321 | TEST cover grid is far larger. |
| `cover/info.txt` | 3,087 | 2,616,852 | TEST cover run is much larger and deeper. |
| `cover/polygon.txt` | 44 | 44 | Same size, different polygon content. |
| `cover/stables.txt` | 1,622 | 2,454,944 | TEST has many more stable entries. |
| `cover/triples.txt` | 0 | 306 | TEST has two triple entries. |
| `cover/unused.txt` | 38 | 1,939,550 | TEST has thousands of uncovered/unused entries. |
| `garbage.txt` | 0 | 978,960 | TEST contains generated working output. |
| `iterToLimit.txt` | 1,629 | 2,405 | Different iteration-pattern output. |
| `middleVary3.txt` | 58,014 | 2,619 | Different vary output. |
| `run2.bat` | 462 | 488 | TEST launches patched jar. |
| `vary3.txt` | 75 | 0 | Different working output. |

## Run Script Differences

`run.bat` is identical in both distributions:

```bat
"%DIR%java/bin/java.exe" -Xss1000m -Xms1000m -jar "%DIR%billiard-viewer.jar"
```

That means `run.bat` does not use the patched TEST jar.

Regular `run2.bat` launches the original jar:

```bat
"%DIR%java/bin/java.exe" ^
  -Xms2g ^
  -Xmx10g ^
  -Xss16m^
  -XshowSettings:vm ^
  -jar "%DIR%billiard-viewer.jar"
```

TEST `run2.bat` launches the patched jar:

```bat
"%DIR%java/bin/java.exe" ^
  -Xms2g ^
  -Xmx10g ^
  -Xss16m^
  -XshowSettings:vm ^
  -jar "%DIR%billiard-viewer-patched-hashtriple-java17.jar"
```

The absence of a space before the caret in `-Xss16m^` is unusual but should still concatenate with the following line's leading spaces in Windows batch syntax. The important behavioral difference is the jar path.

## Jar Comparison

Generated evidence:

- `C:/tmp/billiards_test_reverse_engineering_20260523/jar_original_inventory.csv`
- `C:/tmp/billiards_test_reverse_engineering_20260523/jar_patched_inventory.csv`
- `C:/tmp/billiards_test_reverse_engineering_20260523/jar_original_vs_patched_diff.csv`
- `C:/tmp/billiards_test_reverse_engineering_20260523/HashTriple_original_javap.txt`
- `C:/tmp/billiards_test_reverse_engineering_20260523/HashTriple_patched_javap.txt`
- `C:/tmp/billiards_test_reverse_engineering_20260523/HashTriple_original_api.txt`
- `C:/tmp/billiards_test_reverse_engineering_20260523/HashTriple_patched_api.txt`

Jar hashes:

| File | SHA-256 |
| --- | --- |
| Regular `billiard-viewer.jar` | `2905523F8EE505556F60D6D82704298268343D658F63BADDE3C536CF9D2EEF61` |
| TEST `billiard-viewer.jar` | `2905523F8EE505556F60D6D82704298268343D658F63BADDE3C536CF9D2EEF61` |
| TEST `billiard-viewer-patched-hashtriple-java17.jar` | `AAEEB0B26A2388EDD2B6E839D8901D781F84C300C608743BA40249EA270C077B` |

Changed class:

| Class | Regular size | Patched size | Regular SHA-256 | Patched SHA-256 |
| --- | ---: | ---: | --- | --- |
| `billiards/viewer/HashTriple.class` | 6,880 | 4,636 | `0924773EE8FF0E995693837E1792625202218B670C6A5799159F01893E5E042A` | `8F9C3E7748C59BFAC6C9FEE61317F793A05B50E86F33B3AF7C94F1776E85E499` |

No other jar entries differ.

## HashTriple Behavior

Source location:

- `src/java/billiards/viewer/HashTriple.java`

The source tree version declares four final maps:

```java
private final MutableMap<ConvexPolygon, CodePair> stableMap = Maps.mutable.empty();
private final MutableMap<ConvexPolygon, CoverSquare> tripleMap = Maps.mutable.empty();
private final MutableMap<ConvexPolygon, Color> colorMap = Maps.mutable.empty();
private final MutableMap<ConvexPolygon, CoverSquare> halfTripleMap = Maps.mutable.empty();
```

Original jar behavior:

```java
public void addStables(Map<ConvexPolygon, CodePair> otherMap, Color color) {
    this.stableMap.putAll(otherMap);
    for (ConvexPolygon rect : otherMap.keySet()) {
        this.colorMap.put(rect, color);
    }
}

public void addTriples(Map<ConvexPolygon, CoverSquare> otherMap, Color color) {
    this.tripleMap.putAll(otherMap);
    for (ConvexPolygon rect : otherMap.keySet()) {
        this.colorMap.put(rect, color);
    }
}

public void addHalfTriples(Map<ConvexPolygon, CoverSquare> otherMap, Color color) {
    this.halfTripleMap.putAll(otherMap);
    for (ConvexPolygon rect : otherMap.keySet()) {
        this.colorMap.put(rect, color);
    }
}

public Color getColor(ConvexPolygon rect) {
    return this.colorMap.get(rect);
}
```

For a huge cover, this creates two kinds of avoidable memory pressure:

- it duplicates every incoming rectangle-to-code or rectangle-to-cover-square mapping by copying entries into the `HashTriple` internal maps
- it creates an additional `colorMap` entry for every rectangle even when every rectangle shares the same display color

Patched jar behavior reconstructed from bytecode:

```java
public void addStables(Map<ConvexPolygon, CodePair> otherMap, Color color) {
    if (otherMap instanceof MutableMap) {
        this.stableMap = (MutableMap<ConvexPolygon, CodePair>) otherMap;
    } else {
        this.stableMap.clear();
        this.stableMap.putAll(otherMap);
    }
    this.defaultColor = color;
}

public void addTriples(Map<ConvexPolygon, CoverSquare> otherMap, Color color) {
    if (otherMap instanceof MutableMap) {
        this.tripleMap = (MutableMap<ConvexPolygon, CoverSquare>) otherMap;
    } else {
        this.tripleMap.clear();
        this.tripleMap.putAll(otherMap);
    }
    this.defaultColor = color;
}

public void addHalfTriples(Map<ConvexPolygon, CoverSquare> otherMap, Color color) {
    if (otherMap instanceof MutableMap) {
        this.halfTripleMap = (MutableMap<ConvexPolygon, CoverSquare>) otherMap;
    } else {
        this.halfTripleMap.clear();
        this.halfTripleMap.putAll(otherMap);
    }
    this.defaultColor = color;
}

public Color getColor(ConvexPolygon rect) {
    Color color = this.colorMap.get(rect);
    return color != null ? color : this.defaultColor;
}
```

The patched public API is unchanged. The private implementation changes are:

- `stableMap`, `tripleMap`, and `halfTripleMap` are no longer final
- a new private `Color defaultColor` field exists
- `addStables`, `addTriples`, and `addHalfTriples` no longer populate `colorMap` for every loaded cover rectangle
- if the incoming map is already an Eclipse `MutableMap`, the patched class stores that map directly
- `put(ConvexPolygon rect, Color color)` still supports explicit per-rectangle color overrides through `colorMap`
- `getColor` now returns either a per-rectangle override or the default color

This directly verifies the claimed memory fix.

### Behavioral Caution

The patched class stores the incoming mutable map directly when possible. This saves memory but means future mutation of that same map object would be visible through `HashTriple`. In the observed viewer loading path, the cover maps are built and then handed into `HashTriple` for display, so this is a reasonable memory tradeoff. If future code reuses or mutates those same map objects after handoff, it could affect the rendered cover.

## Cover Dataset Comparison

Generated evidence:

- `C:/tmp/billiards_test_reverse_engineering_20260523/cover_token_summary.csv`

Regular cover:

| Metric | Value |
| --- | ---: |
| `cover/cover.txt` size | 966,392 bytes |
| Total cover tokens | 451,627 |
| Stable tokens, `S` | 119,010 |
| Triple tokens, `T` | 0 |
| Decimal/descend tokens, `D` | 83,154 |
| Empty tokens, `E` | 130,453 |
| Unique stable ids | 31 |
| Unique triple ids | 0 |
| `stables.txt` lines | 31 |
| `triples.txt` lines | 0 |
| `unused.txt` lines | 2 |
| `info.txt` lines | 59 |
| Deepest magnification | 18 |
| Result | Not Covered |
| Runtime reported in `info.txt` | 3.656s |

TEST cover:

| Metric | Value |
| --- | ---: |
| `cover/cover.txt` size | 295,713,321 bytes |
| Total cover tokens | 117,042,642 |
| Stable tokens, `S` | 37,279,124 |
| Triple tokens, `T` | 93 |
| Decimal/descend tokens, `D` | 19,940,856 |
| Empty tokens, `E` | 22,543,352 |
| Unique stable ids | 7,280 |
| Unique triple ids | 2 |
| `stables.txt` lines | 7,280 |
| `triples.txt` lines | 2 |
| `unused.txt` lines | 5,245 |
| `info.txt` lines | 8,409 |
| Deepest magnification | 28 |
| Result | Not Covered |
| Runtime reported in `info.txt` | 51m 55.716s |

TEST `cover/polygon.txt`:

```text
1/18 1/3
1/15 29/90
1/15 1/3
1/18 31/90
```

TEST `cover/square.txt`:

```text
0 1 0 1
```

TEST `cover/precision.txt`:

```text
23
```

The TEST dataset is not merely a jar patch. It also ships with a much larger cover. That matters when evaluating memory use: the patched jar is specifically relevant because the TEST cover has tens of millions of stable square tokens and thousands of stable definitions.

## Small Cover Data

The TEST-only `small_cover` directory appears to be a generated small-cover working/output directory. Its current shipped state is tiny compared with the main cover:

| File | Size | Notes |
| --- | ---: | --- |
| `small_cover/cover.txt` | 4 | Contains one `E` token. |
| `small_cover/info.txt` | 527 | Reports one square not filled and `Not Covered`. |
| `small_cover/polygon.txt` | 266 | Polygon output for the small-cover result. |
| `small_cover/square.txt` | 132 | Square bounds. |
| `small_cover/unused.txt` | 1,173 | Uncovered output. |
| `small_cover/stables.txt` | 0 | No small-cover stable entries in this output. |
| `small_cover/triples.txt` | 0 | No small-cover triple entries in this output. |

## Backend Binary Comparison

The backend binaries are identical between the regular and TEST distributions:

| File | SHA-256 |
| --- | --- |
| `backend/shared/backend.dll` | `BECD0C3D59F8AE2BC48AF6020729FAF3A71B3EA7E2C515E352FE22BF33C37099` |
| `backend/static/backend.lib` | `75632A38270C585E2D4B0EDD73921540F331B81B2430256822C25CB7F15A0E7C` |

The TEST memory change is therefore not a backend-native change. It is a Java viewer patch.

Generated evidence:

- `C:/tmp/billiards_test_reverse_engineering_20260523/backend_dll_objdump_p.txt`
- `C:/tmp/billiards_test_reverse_engineering_20260523/backend_dll_relevant_strings.txt`

Observed backend DLL dependencies include:

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

Exported C/JNA entry points include:

- `backend_cancel`
- `bounding_polygon`
- `calculate_gradient`
- `cleanup_cinfo`
- `cleanup_cpicture`
- `cleanup_string`
- `code_search_even_odd`
- `code_search_length`
- `cover_wrapper`
- `cover_wrapper_all`
- `cover_wrapper_duplicate_stables`
- `cover_wrapper_half_duplicate_stables`
- `create_connection_pool`
- `database_clear`
- `database_create`
- `delete_from_database`
- `destroy_connection_pool`
- `getNotFilledCoordinates`
- `load_all_equations`
- `load_info`
- `load_info_all`
- `load_picture`
- `load_picture_lr`
- `load_picture_lr_expando`
- `load_slope_info`
- `merge_covers`
- `save_to_database`
- `small_cover_wrapper`
- `sqlite_error_logging`
- `trim_cover`
- `vary_3_cpp`
- `vary_4_cpp`
- `vary_cs_cpp`

These exports line up with the Java JNA wrapper in:

- `src/java/billiards/wrapper/Wrapper.java`

And the C++ wrapper declarations/definitions in:

- `app/backend/wrapper.hpp`
- `app/backend/wrapper.cpp`

Useful backend error strings found in the DLL:

- `cover::load_polygon: expected 2 coordinates, got`
- `cover::load_square: expected 4 coordinates, got`
- `cover::load_square: not a square`
- `parse_cover_recursive: unknown cover token:`
- `cover::load_singles: expected 3 components, got`
- `cover::load_singles: mismatched indices: expected`
- `cover::load_triples: expected 7 components, got`
- `cover::load_triples: mismatched indices: expected`

These strings confirm that the native backend owns parsing and validating cover files, stable entries, triple entries, squares, polygons, and recursive cover-token syntax.

## Source Tree Versus Runtime Jar

The source tree is close to the runtime jar but not byte-identical to it.

A comparison between compiled source classes and the finder jar classes found 18 different classes and one source-only class:

Source-only:

- `billiards/viewer/Updater.class`

Classes that differ:

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

Observed source-vs-jar differences include:

- Source `Main.java` uses version text `10.0.12`; the jar contains `BilliardsEverythingSpecialOpt`.
- The source tree includes an updater UI path, including `Check for Updates`; the jar does not contain `Updater.class`.
- The jar includes strings related to `Marco speed is ON` / `marcoSpeed` in vary/load paths that are not present in the source build.
- `SmallCoverWindow` differs: source has an `addPolygons(String)` path and a prompt for a fractional value of Pi; the jar has `replacePolygons(String)` and a prompt referring to Pi/2.
- Some vary-window constructor signatures and generated lambda signatures differ.

Practical implication:

- For exact runtime behavior of the TEST distribution, trust the jar/disassembly and backend binary evidence.
- For source-level understanding and future development, Abdul's fork is now the intended readable dev branch. This older source tree remains the base for this TEST comparison, but it does not exactly match the distributed jar and does not include the TEST `HashTriple` memory patch.

## PDF Reference Limitation

The 2025 instructions PDF and obtuse billiards paper are present in the broader workspace, but this environment did not have a working PDF text extraction tool or library available during this pass. Checked options included common Python PDF libraries, `pdftotext`, `mutool`, `tesseract`, ImageMagick, `qpdf`, PDFBox, and iText. The button/workflow reference therefore relies primarily on source, bytecode, backend exports, existing source-study docs, and distribution artifacts.

## Operational Guidance

Use TEST `run2.bat` when loading the large TEST cover. That is the script that launches the patched jar and applies the larger heap settings.

Use TEST `run.bat` only if you intentionally want to launch the original unpatched jar with smaller memory settings.

If rebuilding from source later, do not assume the patched memory behavior will be present. The source tree's `HashTriple.java` is still the original implementation.

If comparing cover-memory behavior, keep the data change separate from the code change:

- code change: one patched Java class, `HashTriple.class`
- data change: much larger TEST cover files
- runtime launch change: TEST `run2.bat` points at the patched jar
- backend change: none detected
