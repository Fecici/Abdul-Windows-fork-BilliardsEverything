# `[MAIN]` Backend DLL vs Abdul C++ Source

Date: 2026-06-15

## Scope

This note compares the native Windows backend used by `[MAIN]` against Abdul's current C++ backend source.

`[MAIN]` native backend:

```text
[MAIN] The Tokarsky-Marinov Finder, Tester and Prover (Windows-Kaiden jar) Aug 28,2025 - Copy/BilliardsEverythingsWindowsJarAug28Backup/backend/shared/backend.dll
```

Abdul backend source:

```text
-Abdul-s-fork-BilliardsEverything/src/backend/cpp
```

Artifacts are preserved here:

```text
docs/source-study/artifacts/main-patched-cfr-20260615/backend-dll-analysis
docs/source-study/artifacts/main-patched-cfr-20260615/backend-ghidra/output
```

## Native Decompile Status

The earlier Java decompile was a CFR decompile of the jar. That did not cover `backend.dll`.

The native backend was later analyzed with Ghidra 12.1.2 headless decompiler. Ghidra reported:

```text
Function count: 2284
Successful decompiles: 2284
Failed decompiles: 0
Key functions exported: 101
```

The preserved native artifacts now include:

- full Ghidra C-like pseudocode for all detected functions
- a smaller Ghidra key-function pseudocode export
- a focused Ghidra extraction for the `eliminate_phi(...)` Boost.Asio worker
- Ghidra function/export indexes
- PE import/export metadata
- public wrapper/API symbol lists
- focused assembly excerpts for changed areas

This is enough to identify exported functions, linked libraries, function presence, and some code-shape differences. It is not the original C++ source code. Optimized C++ cannot generally be recovered exactly into the original source, comments, local variable names, or line structure.

## What `[MAIN]` Backend Contains

The DLL is a MinGW/GCC-built PE DLL with many C++ symbols still present. It imports and ships with:

- Boost thread/runtime support
- GMP
- MPFI
- MPFR
- SQLite
- TBB
- libstdc++
- winpthread

The public wrapper/API exports that line up with Abdul's `wrapper.cpp` are:

- `sqlite_error_logging`
- `database_create`
- `database_clear`
- `create_connection_pool`
- `destroy_connection_pool`
- `cover_wrapper`
- `small_cover_wrapper`
- `getNotFilledCoordinates`
- `cover_wrapper_duplicate_stables`
- `cover_wrapper_half_duplicate_stables`
- `cover_wrapper_all`
- `save_to_database`
- `delete_from_database`
- `load_picture`
- `cleanup_cpicture`
- `load_picture_lr_expando`
- `load_picture_lr`
- `load_all_equations`
- `load_info_all`
- `load_info`
- `load_slope_info`
- `cleanup_cinfo`
- `merge_covers`
- `code_search_length`
- `code_search_even_odd`
- `trim_cover`
- `bounding_polygon`
- `calculate_gradient`
- `cleanup_string`
- `vary_cs_cpp`
- `vary_3_cpp`
- `vary_4_cpp`
- `backend_cancel`

These are listed in:

```text
artifacts/main-patched-cfr-20260615/backend-dll-analysis/backend.dll.public-api-likely-wrapper-symbols.txt
```

They correspond to Abdul source line locations in `src/backend/cpp/wrapper.cpp`, for example:

- `cover_wrapper`: Abdul `wrapper.cpp:112`
- `small_cover_wrapper`: Abdul `wrapper.cpp:139`
- `getNotFilledCoordinates`: Abdul `wrapper.cpp:160`
- `cover_wrapper_half_duplicate_stables`: Abdul `wrapper.cpp:208`
- `vary_cs_cpp`: Abdul `wrapper.cpp:1237`
- `vary_3_cpp`: Abdul `wrapper.cpp:1267`
- `vary_4_cpp`: Abdul `wrapper.cpp:1293`
- `backend_cancel`: Abdul `wrapper.cpp:1318`

The DLL also exports major backend algorithm symbols present in Abdul's source, including:

- `check_cover`
- `check_small_cover`
- `getEmpties`
- `check_cover_duplicate_stables`
- `check_cover_half_duplicate_stables`
- `check_cover_all`
- `code_search`
- `calculate_stable`
- `calculate_unstable`
- `bounding_polygon`
- `refine_polygon`
- `intersection`
- `divide_once`
- `divide_generic`
- `Unfolding::generate_curves`
- `Unfolding::generate_curves_lr`
- `fireAwayCS`
- `fireAway3`
- `fireAway4`
- `iterateFireAway4`

So `[MAIN]` is using the same broad native backend architecture as Abdul/source: wrapper entry points call cover verification, database load/save, code search, equation parsing/evaluation, gradient/refinement, unfolding, and vary/fire-away routines.

## Confirmed Difference: `eliminate_phi`

The strongest confirmed backend code difference is in Abdul:

```text
-Abdul-s-fork-BilliardsEverything/src/backend/cpp/bounding_inequalities.cpp
```

Relevant Abdul lines:

- `126`: `eliminate_phi(...)`
- `146`: `std::vector<std::vector<LinComArrZ<XYEta>>> thread_zero_phi(task_num);`
- `150`: `const size_t MAX_BUFFER_SIZE = 1000000;`
- `156`: lambda captures `MAX_BUFFER_SIZE`
- `158-159`: creates `local_buffer` and reserves `MAX_BUFFER_SIZE`
- `170`: `local_buffer.push_back(no_phi);`
- `173-184`: periodic and final `std::sort`, `std::unique`, `erase`
- `186`: moves `local_buffer` into `thread_zero_phi[t]`
- `195`: inserts each thread vector into final `zero_phi`

The source tree's version uses a per-thread `std::set`:

- Source `bounding_inequalities.cpp:151`: `std::vector<std::set<LinComArrZ<XYEta>>> thread_zero_phi(task_num);`
- Source `bounding_inequalities.cpp:165`: `thread_zero_phi[t].insert(no_phi);`
- Source `bounding_inequalities.cpp:176`: final merge into `zero_phi`

The `[MAIN]` DLL matches the source-style per-thread `std::set` shape, not Abdul's vector-buffer shape.

Evidence:

```text
artifacts/main-patched-cfr-20260615/backend-ghidra/output/backend.dll.ghidra-eliminate_phi-lambda-do_complete.c
artifacts/main-patched-cfr-20260615/backend-dll-analysis/backend.dll.eliminate_phi_lambda_disassembly_excerpt.txt
```

Key lines in the Ghidra focused pseudocode:

- The worker is the `eliminate_phi(...)::{lambda()#1}` `do_complete` function.
- It calls `remove_phi` behavior and divides content, matching the source-level math path.
- It calls `std::_Rb_tree<math::LinComArr<XYEta,...>>::_M_get_insert_unique_pos(...)`.
- It calls `std::_Rb_tree_insert_and_rebalance(...)`.
- It does not contain Abdul's `local_buffer.push_back(...)`, `MAX_BUFFER_SIZE`, or `std::sort/std::unique` buffer cleanup path.

Key lines in the assembly excerpt:

- Calls `std::_Rb_tree<math::LinComArr<XYEta, long long>...>::_M_get_insert_unique_pos(...)`
- Allocates an RB-tree node with `operator new(0x38)`
- Calls `std::_Rb_tree_insert_and_rebalance(...)`
- Increments the RB-tree size

That is the compiled shape of `std::set::insert`, not Abdul's `std::vector::push_back` plus sort/unique buffer.

Practical effect:

- `[MAIN]`: deduplicates `no_phi` terms continuously during insertion through a tree.
- Abdul: buffers `no_phi` terms in vectors, deduplicates only when the buffer reaches 1,000,000 entries and again at the end, then merges into a final set.
- Mathematical result should be intended to match because both end in a `std::set<LinComArrZ<XYEta>>`.
- Runtime behavior can differ substantially in memory profile and performance.
- Abdul can temporarily hold many duplicate inequalities before cleanup; `[MAIN]` does not.

This is the line range in Abdul most likely to behave differently from `[MAIN]`:

```text
-Abdul-s-fork-BilliardsEverything/src/backend/cpp/bounding_inequalities.cpp:146-195
```

## Debug Print Difference: `unfolding.cpp`

Abdul has two `comb` debug prints commented out:

- Abdul `unfolding.cpp:541`
- Abdul `unfolding.cpp:622`

The source tree has the same two prints active at the same lines. `[MAIN]`'s DLL string table does not contain a standalone `comb` string; only unrelated Windows header strings such as `combaseapi.h` appear.

Evidence:

```text
artifacts/main-patched-cfr-20260615/backend-dll-analysis/backend.dll.comb-string-search.txt
```

This suggests `[MAIN]` probably does not emit those `comb` debug prints, so it is likely closer to Abdul than the source tree for this small output-only difference. This is lower confidence than the `eliminate_phi` finding because optimized native builds can remove strings when code is not linked or instantiated.

Practical effect if real:

- No algorithmic difference.
- Console/debug output differs only.

## Abdul Wrapper Edits

Abdul currently has line-wrapping changes in calls inside `wrapper.cpp`:

- Abdul `wrapper.cpp:123-125`: `check_cover(...)`
- Abdul `wrapper.cpp:171-173`: `getEmpties(...)`
- Abdul `wrapper.cpp:221-223`: `check_cover_half_duplicate_stables(...)`

The exported `[MAIN]` symbols for these functions match Abdul/source signatures:

- `check_cover(...)`
- `getEmpties(...)`
- `check_cover_half_duplicate_stables(...)`

No ABI or behavior difference was found from those wrapper edits. They should be treated as formatting noise.

## Abdul `vary4.cpp` Edits

Abdul `vary4.cpp` differs from the source tree only in spacing and one explanatory comment in the observed lines:

- Abdul `vary4.cpp:59` and `127`: spacing around `frame.goLeft ? ...`
- Abdul `vary4.cpp:203`: comment `// get cores`

The `[MAIN]` DLL exports the same relevant symbols:

- `iterateFireAway4(...)`
- `fireAway4(...)`
- `cancel_flag()::f`
- `vary_4_cpp`

No behavior difference was found here.

## Backend Same-As-`[MAIN]` Areas

Based on exported symbols and matching Abdul source structure, `[MAIN]` and Abdul use the same backend areas for:

- Database creation/clearing/connection pooling
- Cover checks
- Small cover checks
- Not-filled coordinate generation
- Duplicate-stable and half-duplicate-stable cover checks
- Code search
- Database save/delete/load picture/load info
- Equation parsing and gradient calculation
- Cover merging and trimming
- Bounding polygon calculation
- VaryCS, Vary3, Vary4 native code search
- Backend cancellation flag

The caveat is that this is symbol/source-shape matching, not a rebuilt binary diff against Abdul, because Abdul's native backend did not build successfully on Windows in this workspace.

## Backend Build Context

Abdul's full Windows build was rerun on 2026-06-15 and failed in native C++ tasks because required headers/libraries were not available to the selected MSVC toolchain:

- TBB headers
- Boost headers
- Eigen headers
- Boost.Test headers

That means there is no Abdul-built `backend.dll` here to compare binary-to-binary against `[MAIN]`.

Dependency details and the recommended MSYS2/UCRT64 package path are in `20-abdul-windows-build-dependencies.md`.

## Most Important Line Targets In Abdul

These are the Abdul backend lines worth inspecting first if the goal is to reconcile Abdul with `[MAIN]`:

1. `src/backend/cpp/bounding_inequalities.cpp:146-195`

   This is the confirmed real backend logic/performance difference. `[MAIN]` uses source-style `std::set` insertion; Abdul uses vector buffering plus periodic sort/unique.

2. `src/backend/cpp/unfolding.cpp:541` and `src/backend/cpp/unfolding.cpp:622`

   These are debug-output-only `comb` prints. `[MAIN]` does not expose a `comb` string in the DLL string table, so it probably does not print them.

3. `src/backend/cpp/wrapper.cpp:123-125`, `171-173`, `221-223`

   These are current Abdul formatting changes around calls into `check_cover`, `getEmpties`, and `check_cover_half_duplicate_stables`. They are not likely to differ from `[MAIN]` behavior.

4. `src/backend/cpp/vary4.cpp:59`, `127`, `203`

   These are current Abdul spacing/comment changes. They are not likely to differ from `[MAIN]` behavior.

## Decompile Limits

The Ghidra pass is a full native decompile pass, but not recovered original source. It does not prove exact original line numbers, comments, source file boundaries, or original local variable names. For behavioral questions, use the focused Ghidra output, the retained assembly excerpt, and Abdul source together. For exact runtime behavior, the `[MAIN]` DLL bytes remain authoritative.
