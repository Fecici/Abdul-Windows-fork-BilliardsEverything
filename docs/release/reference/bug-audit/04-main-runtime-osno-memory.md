# MAIN Runtime OSNO Memory Investigation

## Question

Observed bug:

```text
In the [MAIN] build, after calculating a large OSNO and about 65 GB of virtual memory later, memory was not deallocated.
```

Goal:

- Check saved MAIN decompiled/Ghidra artifacts.
- Save missing decompiled artifacts.
- Determine whether the same bug persists in Abdul source.

## Artifacts Checked

Existing saved artifacts:

```text
docs/docs/source-study/artifacts/main-patched-cfr-20260615/java-main-changed-classes/
docs/docs/source-study/artifacts/main-patched-cfr-20260615/backend-ghidra/output/
docs/docs/source-study/artifacts/main-patched-cfr-20260615/backend-dll-analysis/
```

Existing Ghidra status:

```text
docs/docs/source-study/artifacts/main-patched-cfr-20260615/README.md
```

states that Ghidra 12.1.2 decompiled all 2,284 detected `backend.dll` functions with zero decompile failures.

New artifacts saved in this pass:

```text
docs/codex-project-study/bug-audit/artifacts/tools/lib/cfr.jar
docs/codex-project-study/bug-audit/artifacts/main-runtime-class-extract/
docs/codex-project-study/bug-audit/artifacts/main-runtime-cfr-selected/
docs/codex-project-study/bug-audit/artifacts/main-runtime-javap/
```

The selected CFR output includes:

```text
docs/codex-project-study/bug-audit/artifacts/main-runtime-cfr-selected/billiards/wrapper/Wrapper.java
docs/codex-project-study/bug-audit/artifacts/main-runtime-cfr-selected/billiards/wrapper/CInfoAll.java
docs/codex-project-study/bug-audit/artifacts/main-runtime-cfr-selected/billiards/database/InfoAll.java
docs/codex-project-study/bug-audit/artifacts/main-runtime-cfr-selected/billiards/viewer/Viewer.java
docs/codex-project-study/bug-audit/artifacts/main-runtime-cfr-selected/billiards/viewer/Utils.java
docs/codex-project-study/bug-audit/artifacts/main-runtime-cfr-selected/billiards/viewer/HashTriple.java
```

## Conclusion

The large-OSNO memory-retention bug is real in `[MAIN]` and persists in Abdul.

The strongest confirmed cause is not the old `HashTriple` huge-cover issue. The likely direct cause for the described OSNO calculation is `CInfoAll` native string ownership:

- native code allocates multiple heap `char[]` strings through `to_cstr`;
- Java copies them into Java `String` fields in `InfoAll`;
- Java never calls a cleanup function for the native `CInfoAll` strings;
- there is no exported `cleanup_cinfo_all` / `cleanup_cinfoall` function in `[MAIN]` runtime or Abdul source.

For a huge OSNO, the duplicated native strings can be enormous. Java may eventually collect the Java `String` objects, but the native `char[]` allocations are lost after `Wrapper.loadAllEquation`, `loadInfoAll`, or `loadSlopeInfo` returns.

## MAIN Evidence

### Java UI Path

MAIN decompiled `Viewer.java`:

```text
docs/codex-project-study/bug-audit/artifacts/main-runtime-cfr-selected/billiards/viewer/Viewer.java
```

Relevant behavior:

```text
Wrapper.loadAllEquation(code, pool)
InfoAll all = opt2.get()
String allSin = all.leftRights
String allCos = all.codeSeqLR
Utils.calculate_formula(allSin, allCos, radius_r, centerList)
```

The previously retained MAIN decompile has the same path:

```text
docs/docs/source-study/artifacts/main-patched-cfr-20260615/java-main-changed-classes/Viewer.java:2448-2457
```

### Java Wrapper Path

MAIN decompiled `Wrapper.java`:

```text
docs/codex-project-study/bug-audit/artifacts/main-runtime-cfr-selected/billiards/wrapper/Wrapper.java
```

Relevant methods:

```text
Wrapper.loadAllEquation: creates CInfoAll, calls load_all_equations, constructs InfoAll, returns Optional.
Wrapper.loadInfoAll: creates CInfoAll, calls load_info_all, constructs InfoAll, returns Optional.
Wrapper.loadSlopeInfo: creates CInfoAll, calls load_slope_info, constructs InfoAll, returns Optional.
```

There is no cleanup call in those methods.

Bytecode confirmation:

```text
docs/codex-project-study/bug-audit/artifacts/main-runtime-javap/billiards_wrapper_Wrapper.javap.txt:1057-1152
docs/codex-project-study/bug-audit/artifacts/main-runtime-javap/billiards_wrapper_Wrapper.javap.txt:1154-1249
docs/codex-project-study/bug-audit/artifacts/main-runtime-javap/billiards_wrapper_Wrapper.javap.txt:2111-2205
```

### Native Backend Path

MAIN Ghidra decompile:

```text
docs/docs/source-study/artifacts/main-patched-cfr-20260615/backend-ghidra/output/backend.dll.ghidra-decompiled-all-functions.c
```

Native allocation evidence:

```text
copy_to_cinfoAll: lines 169316-169402
load_all_equations: lines 172093-172245
load_info_all: lines 172260-172330
load_slope_info: lines 172465-172569
```

`copy_to_cinfoAll` serializes sine/cosine equation sets, calls `to_cstr`, and stores returned pointers into the `CInfoAll` output slots. `load_slope_info` does the same for vector equation strings.

Export evidence:

```text
docs/docs/source-study/artifacts/main-patched-cfr-20260615/backend-ghidra/output/backend.dll.ghidra-function-index.tsv
```

contains `cleanup_cinfo` and `cleanup_string`, but no `cleanup_cinfo_all` / `cleanup_cinfoall`.

## Abdul Evidence

Abdul source has the same leak pattern.

Java wrapper:

```text
-Abdul-s-fork-BilliardsEverything/src/java/billiards/wrapper/Wrapper.java:160-193
-Abdul-s-fork-BilliardsEverything/src/java/billiards/wrapper/Wrapper.java:195-229
-Abdul-s-fork-BilliardsEverything/src/java/billiards/wrapper/Wrapper.java:520-544
```

These methods allocate/use `CInfoAll` and construct `InfoAll`, but do not call any cleanup function.

Native backend:

```text
-Abdul-s-fork-BilliardsEverything/src/backend/cpp/wrapper.cpp:650-660
-Abdul-s-fork-BilliardsEverything/src/backend/cpp/wrapper.cpp:664-690
-Abdul-s-fork-BilliardsEverything/src/backend/cpp/wrapper.cpp:694-710
-Abdul-s-fork-BilliardsEverything/src/backend/cpp/wrapper.cpp:768-794
```

These paths call `to_cstr` into `CInfoAll` fields.

Header/API:

```text
-Abdul-s-fork-BilliardsEverything/src/backend/headers/wrapper.hpp:82-118
```

declares `cleanup_cinfo` and `cleanup_string`, but no cleanup function for `CInfoAll`.

## Why This Matches The 65 GB Symptom

For large OSNO/all-equation calculations:

1. Java calls `Wrapper.loadAllEquation`.
2. Native computes `calculate_stable_all_info` for stable code sequences, including OSNO.
3. Native serializes potentially huge equation sets to C++ strings.
4. Native copies those strings into heap `char[]` using `to_cstr`.
5. Java copies the native data into Java `String` fields.
6. The native `char[]` pointers are not freed.
7. After method return, Java no longer has a reliable cleanup path to those native allocations.

This can leave the process with very high virtual memory even after the UI calculation finishes. The JVM garbage collector cannot free native memory allocated by C++ `new[]`.

## Separate Existing Memory Bug: HashTriple Huge Cover

The `[MAIN]` bundle also has an older documented cover-load memory issue:

```text
[MAIN] .../BilliardsEverythingsWindowsJarAug28Backup/billiards_reverse_engineering_notes.md
```

That issue was about `HashTriple.addStables` duplicating huge cover maps and color maps. The patched MAIN jar replaced only `HashTriple.class` to reduce cover-load memory.

That patch does not fix the OSNO/all-equation `CInfoAll` native leak.

## CInfoAll Field-Order Hazard

There is a separate ABI/naming hazard.

Abdul C++ struct order:

```text
initial_angles, points, equations, sinEquations, cosEquations, left_rights, code_seq_lr, vectorX, vectorY
```

MAIN and Abdul Java `CInfoAll.getFieldOrder()`:

```text
initial_angles, points, equations, left_rights, code_seq_lr, sinEquations, cosEquations, vectorX, vectorY
```

Ghidra confirms MAIN native `copy_to_cinfoAll` writes serialized equations to slots 3 and 4. Java names those slots `left_rights` and `code_seq_lr`. Current UI code compensates by reading:

```text
InfoAll.leftRights
InfoAll.codeSeqLR
```

as all-equation strings.

Do not "fix" the field order casually. The first memory fix should add native cleanup for all slots without changing this behavioral contract. Renaming/reordering should be a separate compatibility fix with tests.

## Correct Fix Shape

Minimum safe source fix:

1. Add a native cleanup export:

```cpp
void cleanup_cinfo_all(const CInfoAll* const cinfoAll) {
    delete[] cinfoAll->initial_angles;
    delete[] cinfoAll->points;
    delete[] cinfoAll->equations;
    delete[] cinfoAll->sinEquations;
    delete[] cinfoAll->cosEquations;
    delete[] cinfoAll->left_rights;
    delete[] cinfoAll->code_seq_lr;
    delete[] cinfoAll->vectorX;
    delete[] cinfoAll->vectorY;
}
```

2. Declare it in `wrapper.hpp`.
3. Add a Java native declaration:

```java
private static native void cleanup_cinfo_all(CInfoAll cinfoAll);
```

4. In `Wrapper.loadAllEquation`, `Wrapper.loadInfoAll`, and `Wrapper.loadSlopeInfo`, call it after `new InfoAll(cinfoAll)` has copied the strings.
5. Use `finally` so cleanup runs if `InfoAll` construction or downstream parsing throws after native allocation.
6. Guard the C++ cleanup against null pointers if future code can partially allocate.

Follow-up fixes:

- Fix `Wrapper.calculateGradient` / `calculateGradient2` cleanup.
- Fix `Wrapper.varyCSCpp` / `vary3Cpp` / `vary4Cpp` cleanup.
- Decide whether to repair `CInfoAll` field order and then update all Java consumers/tests together.

## Verification Plan

Use a large OSNO that reproduces the issue.

Before fix:

- Run with Native Memory Tracking enabled.
- Trigger the OSNO formula/all-equation calculation.
- Observe high native/virtual memory retained after calculation.

After fix:

- Repeat the same calculation.
- Watch native memory after method returns and after a forced Java GC.
- Expected result: the native payload allocated by `CInfoAll` is released. JVM/CRT may still retain some arenas, but repeated runs should not grow linearly by the size of the equation payload.

Useful commands:

```powershell
jcmd <pid> VM.native_memory summary
jcmd <pid> GC.run
jcmd <pid> VM.native_memory summary.diff
```

On Windows, also compare Process Explorer:

```text
Private Bytes
Commit Size
Virtual Size
Working Set
```
