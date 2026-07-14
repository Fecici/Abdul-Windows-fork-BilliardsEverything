# Source, Runtime, And Fork Version Matrix

Date of analysis: 2026-05-23

This note answers the practical version question: what is newer, what has which features, and how the downloaded compiled regular/TEST runtime differs from the source tree and from the Abdul fork.

Current baseline note: this matrix remains the high-level version map, but `19-main-backend-dll-vs-abdul-source.md` now contains Ghidra native backend evidence and `20-abdul-windows-build-dependencies.md` contains the current Abdul Windows dependency/build result.

No code, jars, DLLs, dylibs, scripts, or generated data files were altered. All generated comparison artifacts for this pass were written under:

```text
C:/tmp/billiards_source_runtime_abdul_compare_20260523
```

## Compared Surfaces

| Name used here | Path / artifact | Meaning |
| --- | --- | --- |
| Source tree | `sourcecode-billiards_everythingMay2,2026/billiards_everything` | The source checkout already documented in `docs/source-study`. |
| Source app jar | `sourcecode-billiards_everythingMay2,2026/billiards_everything/app/billiard-viewer.jar` | Packaged app jar shipped inside the source tree. |
| Source build classes | `sourcecode-billiards_everythingMay2,2026/billiards_everything/build/classes/java/main` | Existing compiled classes in the source tree. |
| Regular runtime | `The Tokarsky-Marinov Finder, Tester and Prover (Windows-Kaiden jar) Aug 28,2025/BilliardsEverythingsWindowsJarAug28Backup` | Downloaded Windows runtime folder. |
| TEST runtime | `[TEST] The Tokarsky-Marinov Finder, Tester and Prover (Windows-Kaiden jar) Aug 28,2025 - Copy/BilliardsEverythingsWindowsJarAug28Backup` | TEST runtime folder with large cover data and patched jar. |
| Abdul fork source | `-Abdul-s-fork-BilliardsEverything` | New fork source checkout. |
| Abdul app jar | `-Abdul-s-fork-BilliardsEverything/app/billiard-viewer.jar` | Packaged app jar inside Abdul fork. |

## High Confidence Bottom Line

There is no single artifact that is simply "the latest" in every sense.

| Artifact | Best description |
| --- | --- |
| Regular Windows runtime jar | A specialized compiled runtime branch. It has runtime-only UI/vary behavior not present in the source tree, including `Marco speed is ON` / `marcoSpeed`, `BilliardsEverythingSpecialOpt`, and `replacePolygons`. |
| TEST patched Windows jar | Regular Windows runtime jar plus exactly one patched class, `billiards.viewer.HashTriple`, for cover-display memory behavior. This is the best runtime for the huge TEST cover. |
| Source tree source | Readable development source for version `10.0.12`. It has `Updater` / `Check for Updates`, but does not have the runtime jar's `marcoSpeed` feature or TEST `HashTriple` patch. |
| Source app jar | Byte-for-byte consistent with the source tree's current compiled project classes. It is not the same as the downloaded regular Windows runtime jar. |
| Abdul source | A small source fork of the source tree with version string `10.0.14` and a few real code changes. It is the newest by source version string and git commit date, but not by packaged compiled jar behavior. |
| Abdul app jar and dylib | Byte-identical to the source tree's packaged app jar and macOS backend dylib. They do not include Abdul's own source edits. |

Practical conclusion:

- For running the large TEST cover: use the TEST patched jar through TEST `run2.bat`.
- For source development: Abdul source has the newest visible source version number, but it is missing the regular/TEST runtime jar's `marcoSpeed` feature and the TEST `HashTriple` source patch.
- For understanding the compiled downloaded Windows app: the regular/TEST runtime jars must be treated as a separate compiled branch from the source tree.

## Hash Evidence

Viewer jars:

| Artifact | SHA-256 |
| --- | --- |
| Source app jar | `0C9A67AE775057BE461D3579B76EE515EE8D110A5DE4078BBED57FC70D7303B8` |
| Abdul app jar | `0C9A67AE775057BE461D3579B76EE515EE8D110A5DE4078BBED57FC70D7303B8` |
| Regular runtime `billiard-viewer.jar` | `2905523F8EE505556F60D6D82704298268343D658F63BADDE3C536CF9D2EEF61` |
| TEST patched `billiard-viewer-patched-hashtriple-java17.jar` | `AAEEB0B26A2388EDD2B6E839D8901D781F84C300C608743BA40249EA270C077B` |

Native binaries:

| Artifact | SHA-256 |
| --- | --- |
| Source app `libbackend.dylib` | `F3BE37A56BD2BD74D91E9E37023594E4931713973B29C0D0C5B42BFF7D66713A` |
| Abdul app `libbackend.dylib` | `F3BE37A56BD2BD74D91E9E37023594E4931713973B29C0D0C5B42BFF7D66713A` |
| Regular runtime `backend.dll` | `BECD0C3D59F8AE2BC48AF6020729FAF3A71B3EA7E2C515E352FE22BF33C37099` |
| TEST runtime `backend.dll` | `BECD0C3D59F8AE2BC48AF6020729FAF3A71B3EA7E2C515E352FE22BF33C37099` |

The source and Abdul packaged macOS backend libraries are identical. The regular and TEST Windows backend DLLs are identical. The Windows DLL cannot be directly hash-compared to the macOS dylib because they are different platform binaries.

## Source Build Versus Source App Jar

The source tree's existing compiled project classes match the source app jar exactly:

```text
source_build_project_vs_source_app_project:
  SAME: 136
```

This matters because it means the source tree's `build/classes/java/main` and `app/billiard-viewer.jar` represent the same Java project compilation. The source app jar is not just an unrelated stale jar inside the folder.

Generated evidence:

```text
C:/tmp/billiards_source_runtime_abdul_compare_20260523/source_build_project_classes_vs_source_app_project_classes_diff.csv
C:/tmp/billiards_source_runtime_abdul_compare_20260523/project_class_comparison_summary.csv
```

## Source App Jar Versus Regular Runtime Jar

Project-class-only comparison:

```text
source_app_project_vs_regular_project:
  SAME: 117
  DIFFERENT: 18
  source_app_ONLY: 1
```

The one source-only project class is:

```text
billiards/viewer/Updater.class
```

The 18 differing project classes are:

| Class | Source app size | Regular runtime size | Meaning |
| --- | ---: | ---: | --- |
| `billiards/viewer/AutoPolyVaryLoad.class` | 13,622 | 13,731 | Regular has `marcoSpeed` UI/behavior. |
| `billiards/viewer/BoyanMenu$2.class` | 1,372 | 1,372 | Lambda/inner behavior differs. |
| `billiards/viewer/BoyanMenu.class` | 43,034 | 43,031 | Vary/menu wiring differs. |
| `billiards/viewer/CoverWindow.class` | 23,547 | 23,555 | Source uses `addPolygons`; runtime uses `replacePolygons`. |
| `billiards/viewer/CycleVaryTask$1.class` | 2,262 | 2,262 | Task listener/lambda differs. |
| `billiards/viewer/CycleVaryTask.class` | 18,030 | 18,297 | Cycle-vary task behavior differs. |
| `billiards/viewer/CycleVaryWindow.class` | 47,256 | 47,560 | Runtime has Boyan step, magnify, coordinate-count, and `marcoSpeed` strings. |
| `billiards/viewer/Main.class` | 3,592 | 3,614 | Source has `10.0.12`; runtime has `BilliardsEverythingSpecialOpt`. |
| `billiards/viewer/PolyVaryLoad.class` | 11,182 | 11,296 | Regular has `marcoSpeed` UI/behavior. |
| `billiards/viewer/PolyVaryTask$1.class` | 2,255 | 2,255 | Task listener/lambda differs. |
| `billiards/viewer/PolyVaryTask.class` | 17,314 | 17,537 | Poly-vary task behavior differs. |
| `billiards/viewer/SmallCoverWindow.class` | 18,031 | 17,983 | Source prompt says Pi; runtime prompt says Pi/2 and uses `replacePolygons`. |
| `billiards/viewer/SuperPolyVaryLoad.class` | 13,594 | 13,758 | Regular has `marcoSpeed` UI/behavior. |
| `billiards/viewer/Utils.class` | 24,426 | 24,355 | Utility implementation differs. |
| `billiards/viewer/VaryWindowL.class` | 16,655 | 16,795 | Regular has `marcoSpeed` UI/behavior. |
| `billiards/viewer/Viewer$1.class` | 2,794 | 2,794 | Generated listener/inner behavior differs. |
| `billiards/viewer/Viewer$2.class` | 2,258 | 2,258 | Generated listener/inner behavior differs. |
| `billiards/viewer/Viewer.class` | 251,456 | 251,007 | Main UI wiring differs; source has updater UI, runtime does not. |

Generated evidence:

```text
C:/tmp/billiards_source_runtime_abdul_compare_20260523/source_app_project_classes_vs_regular_project_classes_diff.csv
C:/tmp/billiards_source_runtime_abdul_compare_20260523/source_app_vs_regular_javap_diffstat.csv
C:/tmp/billiards_source_runtime_abdul_compare_20260523/javap_source_app_vs_regular_diffs/
```

The `javap_source_app_vs_regular_diffs` directory contains line-by-line bytecode/API diffs for every changed class. The largest one is `Viewer.diff`, about 2.8 MB, so the full bytecode diff is referenced as generated evidence rather than pasted into this note.

## Authored String Differences In Changed Classes

The most useful project-authored string differences are:

| Class | Source app only | Regular runtime only |
| --- | --- | --- |
| `AutoPolyVaryLoad` | none detected | `Marco speed is ON`, `marcoSpeed` |
| `PolyVaryLoad` | none detected | `Marco speed is ON`, `marcoSpeed` |
| `SuperPolyVaryLoad` | none detected | `Marco speed is ON`, `marcoSpeed` |
| `VaryWindowL` | none detected | `Marco speed is ON`, `marcoSpeed` |
| `CycleVaryWindow` | `Subdivisions Step:` | `# of coordinates`, `Boyan Step`, `BoyanVary #`, `Change # of coordinates by`, `Magnify x`, `Marco speed is ON`, `marcoSpeed` |
| `CoverWindow` | `addPolygons` | `replacePolygons` |
| `SmallCoverWindow` | `// Small Cover`, `addPolygons`, prompt says fractional value of Pi | `replacePolygons`, prompt says fractional value of Pi/2 |
| `Main` | `10.0.12` | `BilliardsEverythingSpecialOpt` |
| `Viewer` | `Check for Updates`, `versionNumber` | no matching runtime-only user-facing string from this filter |

Generated evidence:

```text
C:/tmp/billiards_source_runtime_abdul_compare_20260523/source_app_vs_regular_project_class_string_diffs.csv
```

## Feature Presence Matrix

| Feature marker | Source source | Abdul source | Source app jar | Regular runtime jar | TEST patched runtime jar |
| --- | --- | --- | --- | --- | --- |
| `10.0.12` | yes | no | yes | no | no |
| `10.0.14` | no | yes | no | no | no |
| `BilliardsEverythingSpecialOpt` | no | no | no | yes | yes |
| `Marco speed is ON` | no | no | no | yes | yes |
| `marcoSpeed` | no | no | no | yes | yes |
| `addPolygons` | yes | yes | yes | no | no |
| `replacePolygons` | no | no | no | yes | yes |
| `Check for Updates` | yes | yes | yes | no | no |
| TEST `HashTriple.defaultColor` patch | no | no | no | no | yes |
| Abdul `MAX_BUFFER_SIZE` C++ source change | no | yes | not built | not detectable | not detectable |
| Abdul default small-cover-off source change | no | yes | not built | no evidence | no evidence |
| Abdul default reflect-on source change | no | yes | not built | no evidence | no evidence |

Generated evidence:

```text
C:/tmp/billiards_source_runtime_abdul_compare_20260523/feature_presence_matrix.csv
```

Note on `defaultColor`: JavaFX itself contains unrelated `defaultColor` strings. The meaningful TEST patch is specifically `billiards/viewer/HashTriple.class` in `billiard-viewer-patched-hashtriple-java17.jar`.

## Marco Speed Answer

The source tree has many comments and code sections credited to Marco Mai, especially around July/August 2025 backend and vary work. That is separate from the runtime jar feature named `marcoSpeed`.

The exact runtime feature markers:

```text
Marco speed is ON
marcoSpeed
```

are present in:

- regular runtime jar
- TEST patched runtime jar

and absent from:

- source tree source
- source app jar
- Abdul source
- Abdul app jar

Therefore, the source you have does not contain the compiled runtime's `marcoSpeed` UI/behavior. Abdul source also does not contain it.

## Updater Answer

The source tree has an updater path:

- `src/java/billiards/viewer/Updater.java`
- `Check for Updates` in `Viewer.java`
- `Updater.class` in source build/source app jar

The regular runtime jar does not contain `Updater.class` and does not contain the `Check for Updates` string.

This is one of the clearest signs that the source tree and the downloaded Windows runtime jar are different branches/builds rather than identical source and binary.

## Small Cover Answer

There are three separate small-cover behaviors to keep distinct:

1. Source tree / source app jar:
   - has `addPolygons`
   - `SmallCoverWindow` prompt says the square numbers are fractional values of Pi
   - source default for "Add codes to small cover" is selected in `AutoPolyVaryLoad`, `PolyVaryLoad`, `SuperPolyVaryLoad`, and `CoverWindow`

2. Regular/TEST runtime jar:
   - has `replacePolygons`
   - `SmallCoverWindow` prompt says fractional values of Pi/2
   - differs in `CoverWindow` and `SmallCoverWindow` bytecode

3. Abdul source:
   - keeps source's `addPolygons`
   - changes the default "Add codes to small cover" selections from true to false
   - this change is not reflected in Abdul's packaged app jar

## Native Backend Answer

The regular and TEST Windows backend DLLs are byte-identical. The TEST memory patch is not native; it is Java-only in `HashTriple.class`.

The source and Abdul packaged macOS dylibs are byte-identical. Abdul's C++ source edits are therefore not reflected in Abdul's packaged `libbackend.dylib`.

Marker string scan:

| Marker | Regular Windows DLL | Source macOS dylib | Abdul macOS dylib |
| --- | --- | --- | --- |
| cover load errors | present | present | present |
| `vary_3_cpp`, `vary_4_cpp`, `vary_cs_cpp` | present | present with Mach-O underscore naming | present with Mach-O underscore naming |
| `comb` debug string from source `unfolding.cpp` | not found | present | present |
| `MAX_BUFFER_SIZE` from Abdul source `bounding_inequalities.cpp` | not found | not found | not found |

Interpretation:

- The Windows runtime DLL is a separate platform binary from the source/Abdul macOS dylib.
- The Windows DLL exports match the Java wrapper surface. A later Ghidra pass is documented in `19-main-backend-dll-vs-abdul-source.md`; it confirms the main native backend difference is Abdul's `eliminate_phi` vector-buffer change versus `[MAIN]` source-style `std::set` insertion.
- The Abdul source changes were not rebuilt into Abdul's packaged macOS dylib.
- The source macOS dylib still contains the `comb` debug string that Abdul source comments out, reinforcing that Abdul packaged native binaries are stale relative to Abdul source.

## Applicability Of Existing Source-Study Docs To The Downloaded Runtime

The existing source-study docs remain useful, but they should not be treated as an exact decompilation of the downloaded Windows runtime.

| Doc area | Applicability to regular/TEST runtime |
| --- | --- |
| Architecture and math | High. The conceptual pipeline, code sequences, MRRs, covers, database, and frontend/backend split still apply. |
| Backend wrapper/JNA ABI | High. Export names and Java wrapper surface line up with the DLL. |
| Backend algorithm descriptions | Medium-high. Good for understanding intent, but exact Windows DLL source cannot be proven from the source tree alone. |
| Java core/domain classes | High for most classes. 117 project classes match source app jar versus runtime jar; changed classes are concentrated in viewer/vary UI. |
| Viewer/button docs | Medium-high. Most controls apply, but exact vary/small-cover/updater behavior differs. |
| TEST patch docs | Exact for TEST patched jar. The `HashTriple` patch was verified at bytecode level. |
| Abdul fork docs | Exact for Abdul source, but not for Abdul packaged jar/dylib unless rebuilt. |

If forced to assign a rough number:

- existing docs for architecture/core concepts: about 90-95% applicable to the regular runtime
- existing docs for exact frontend behavior: about 75-85% applicable
- existing docs for exact source-vs-runtime facts: superseded by docs `14`, `16`, and `17`

## Which One Looks Newer?

By source version string and git date:

- Abdul source looks newest: `10.0.14`, git commit date 2026-05-23.
- Source tree source is `10.0.12`.

By compiled Windows runtime features:

- Regular/TEST runtime jar looks like a separate specialized build, not represented by either source tree.
- It has `BilliardsEverythingSpecialOpt`, `marcoSpeed`, and `replacePolygons`.

By practical runnable artifact:

- TEST patched runtime jar is the most capable compiled artifact for the large TEST cover because it combines the regular specialized runtime with the verified `HashTriple` memory patch.

By rebuild readiness:

- Abdul source is the newest source branch, but its packaged jar/dylib are stale relative to its source edits.
- The current source tree is internally consistent between source build classes and source app jar.

## Generated Evidence Index

Important files in `C:/tmp/billiards_source_runtime_abdul_compare_20260523`:

| File/directory | Purpose |
| --- | --- |
| `project_class_comparison_summary.csv` | Project-class-only summary across source app, regular runtime, TEST patched runtime, and source build classes. |
| `source_app_project_classes_vs_regular_project_classes_diff.csv` | Exact list of source app jar vs regular runtime project class differences. |
| `source_build_project_classes_vs_source_app_project_classes_diff.csv` | Proof source build classes equal source app jar project classes. |
| `source_app_vs_regular_project_class_string_diffs.csv` | Authored string differences for changed project classes. |
| `source_app_vs_regular_javap_diffstat.csv` | Bytecode/API diff size by changed class. |
| `javap_source_app_vs_regular_diffs/` | Full line-by-line `javap` bytecode/API diffs for changed source app vs regular runtime classes. |
| `feature_presence_matrix.csv` | Feature marker counts across source, Abdul source, source app jar, regular runtime jar, and TEST patched jar. |
| `source_vs_abdul_normalized_text_summary.csv` | Normalized source-to-Abdul text comparison summary. |
| `source_to_abdul_normalized_unified.diff` | Full normalized source-to-Abdul unified diff. |
