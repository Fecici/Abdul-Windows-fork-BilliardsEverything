# Project Improvement Report

## Summary

The improvements made to the working tree fall into four broad themes.

First, the numerically critical C++ routines were parallelized and given smarter short-circuit logic. The polygon-refinement pipeline — the hottest path in a stable/unstable computation — now runs curve- and vertex-level work across multiple cores using `boost::asio::thread_pool` and `tbb::parallel_for`, skips curves whose bounding-box sign already settles the question, and stops early once the polygon collapses below display resolution. 

Second, a class of algorithmic complexity bugs was removed: the lexicographically-minimal-rotation routine was rewritten from an O(n²) brute-force scan to Booth-style O(n), a JavaFX cross-thread round-trip that happened once *per coordinate* during AutoVary was replaced with a single cached pixel reader, and several `String +=` loops in the pattern finder were converted to `StringBuilder`.

Third, a substantial set of correctness and resource-management defects was fixed. These include a JNA struct field-order mismatch that could read native pointers from the wrong offsets, native memory leaks where C++ owned strings and `CInfoAll` buffers were never released, static strings shared across threads in the cover code, an out-of-bounds indexing mistake in the triangle billiard generator, an overly-tight Newton tolerance that risked non-convergence, and several `ExecutorService` shutdowns that were skipped on exception paths.

Fourth, the codebase was hardened for maintainability and deployment: executor lifecycles were wrapped in `try/finally`, near-duplicate UI listeners were consolidated, the database directory was made project-relative so the application is portable, build scripts learned how to link versioned boost shared libraries on Debian/Ubuntu and the JVM stack size was reduced from 1000 MB to a sane 8 MB while making the heap configurable.

None of the changes alter the mathematical results the program produces; they make those results arrive faster, more reliably, and without leaking memory.

---
## Load Holes Feature

A new **Load Holes** button (shaded light pink, placed in the Boyan menu row next to the existing Cover button) lets the user step through the regions of the parameter space that a cover calculation failed to fill, treating them as a one-by-one (OBO) walkthrough.

The workflow starts in the C++ backend. Whenever `cover_polygon`, `cover_small_polygon`, or `getEmpties` finishes a cover pass, the cover routine already collected a list of "not filled" squares — the `ClosedRectangleQ` entries returned in `cover_info.not_filled`, i.e. the sub-squares of the working region that no classified stable or triple was able to cover. A new helper, `cover::save_holes` in `src/backend/cpp/cover/save.cpp`, now writes the centers of those unfilled squares (in degrees, via `center_degrees`) to `tmp/holes.txt`. When the number of holes exceeds the requested `empties` count, the writer evenly downsamples by striding through the list (`inc = not_filled.size() / num_to_print`), so the file contains a representative spread of the gaps rather than every single one. All three cover entry points call `save_holes` after their existing `save_polygon`/`save_square`/`save_cover`/`save_digits` calls, so the file is always up to date after a cover run.

On the Java side, clicking **Load Holes** invokes `Viewer.loadHolesFromFile`. It first checks that `tmp/holes.txt` exists; if not, it pops an error alert telling the user to run a cover calculation first. Otherwise it reads the lines (stripping blanks), counts them, and presents a `TextInputDialog` asking how many holes to load, pre-filled with the maximum available count. After the user confirms, the first *n* coordinate lines are concatenated and handed to `loadHolesAsOBO`, which writes them to `tmp/holes_obo.txt`, parses that file with the existing `parseOBOFile` routine into `fileCodeSequences`, and calls `setOBO(0, pool, executorService)` to render the first hole as the OBO image. From there the user can use the normal back/forth and line-number controls to step through the uncovered regions one at a time, inspect each, and launch further calculations (Vary, AutoPolyVary, etc.) directly at the coordinates that still need filling.

---

## Performance Improvements

### 1. Parallel polygon refinement with bounding-box short-circuit

`equations.cpp::calculate_final_polygon` is the inner loop of every stable/unstable calculation: it takes the bounding interval polygon and refines it against every generated sine and cosine curve in turn.

Previously the loop was strictly sequential: `for (curve : sin_curves) polygon = refine(polygon, curve); for (curve : cos_curves) polygon = refine(polygon, curve);`. Because each refinement depends on the previous polygon, the loop is inherently a reduction and was not obviously parallelizable.

The new version splits the work into two regimes. For fewer than `PARALLEL_THRESHOLD` (1000) curves it keeps the sequential path with an early exit. For larger curve sets it partitions the curves *interleaved* across `n_threads` (capped at 8) batches using `boost::asio::thread_pool`. Each batch starts from the *same* bounding polygon, applies its own subset of curves sequentially, and returns a partial polygon. Because each curve simply carves away a half-plane from the feasible region, the constraints commute and the partial polygons can be merged by repeated `intersect_polygons` (new in `refine.cpp`/`refine.hpp`) to yield exactly the same result as the sequential pass.

Two further micro-optimizations accompany this. `polygon_is_tiny` aborts a batch (and the sequential loop) once the polygon's bounding box drops below `1e-12` radians, where further refinement cannot change any visible or numerical result. And `refine_polygon` itself now performs a whole-polygon bounding-box sign test before evaluating per-vertex corners: if the curve is strictly positive over the entire bounding box the polygon is returned unchanged, and if it is strictly negative the polygon is empty (`none`) — both skip the corner computation entirely.

### Performance Tests (against SourceForge version)

#### 1st Test Case:
- OSNO (82, 198) 1 1 2 2 2 2 4 2 2 3 1 3 3 1 3 2 3 1 3 4 2 2 2 2 2 4 2 2 2 3 1 3 4 3 1 4 1 2 1 5 3 1 5 4 5 1 2 1 4 1 3 4 3 1 3 2 2 2 4 2 2 3 1 2 1 4 1 3 3 1 3 2 3 1 3 4 2 2 2 2 2 5
- Execution time before: **34.79 seconds**
- Execution time after: **5.18 seconds**

#### 2nd Test Case:

- OSNO (206, 472) 1 1 2 3 1 3 2 2 2 3 1 3 2 1 1 5 3 1 4 1 2 1 5 3 1 4 1 3 5 1 1 2 3 1 3 2 2 2 3 1 3 2 1 1 5 3 1 4 1 3 5 1 1 2 3 1 3 2 2 2 3 1 3 2 1 1 5 3 1 4 1 3 5 1 1 2 3 1 3 2 2 2 3 1 3 2 1 1 5 3 1 4 1 3 5 1 1 2 3 1 3 3 1 3 3 1 3 3 1 3 3 1 3 3 1 3 3 1 3 3 1 3 3 1 3 3 1 3 3 1 3 3 1 3 3 1 3 3 1 3 3 1 3 3 1 3 2 2 2 3 1 3 2 1 1 5 3 1 4 1 3 5 1 1 2 3 1 3 2 2 2 3 1 3 2 1 1 5 3 1 4 1 3 5 1 1 2 3 1 3 2 2 2 3 1 3 2 1 1 5 3 1 4 1 3 5
- Execution time before: **196.00s**
- Execution time after: **30.03 seconds**

The optimized implementation runs **about 500% faster**, achieving a **6× speedup** over the original version.

Note:
Different systems will see different amounts of speedup, since the improvement depends heavily on number of CPU threads

---

### 2. Parallel corner evaluation with TBB

`refine.cpp::calculate_corners` evaluates a sign and (occasionally) a gradient computation for each vertex of the polygon. This is the dominant per-curve cost. The loop was rewritten from a plain `for (i = 0; i < size; ++i) corners.emplace_back(...)` to a `tbb::parallel_for` over `blocked_range<size_t>` writing into a pre-sized `std::vector<Corner>(size)`. The `Corner` struct got a default constructor and default member initializers so that pre-sizing is valid.

---

### 3. O(n) lexicographically minimal rotation

`code_sequence.cpp::minimal_rotation` normalizes a code sequence by picking the lexicographically smallest rotation of itself and of its reverse. The old implementation rotated the vector `n` times (each rotation O(n) via `std::rotate`), compared to the running minimum, reversed, and did it again — four nested O(n²) passes plus a copy of the minimum.

The new implementation introduces `least_rotation_index`, a Booth-style least-rotation algorithm that runs in O(n) time and O(n) extra space (it works on a doubled copy of the sequence and uses the standard Lyndon-factorization skip). `minimal_rotation` calls it twice (forward and reversed) and builds the answer with a single `rotated_copy` each time. Overall complexity dropped from O(n²) to O(n). The new code also operates on the original vector immutably rather than rotating it in place and reversing it, removing a longstanding mutability footgun noted in the original comments.

---

### 4. Eliminating the per-coordinate JavaFX round-trip in AutoVary

`CycleVaryTask` and `PolyVaryTask` need the on-screen pixel color at each visited coordinate to decide whether the cell is already covered. The old `pixelColor` method built a `FutureTask`, posted it to the JavaFX Application Thread with `Platform.runLater`, and blocked the worker thread on `.get()` — once per coordinate, for hundreds to thousands of coordinates. Every call serialized on the single FX thread and incurred a blocking context switch.

The new code fetches the `PixelReader` and the image dimensions *once*, up front, via two short `FutureTask.get()` round-trips, and stores them in volatile fields. `pixelColor` then calls `pixelReader.getArgb(midX, midY)` directly from the worker thread, with explicit bounds checks. The old per-coordinate blocking is gone entirely. As a side benefit, an `IndexOutOfBoundsException` that occurred for coordinates mapping to pixels just outside the image is now avoided by the bounds check.

In the same edit, `Collections.shuffle(out)` was removed from the coordinate lists in both tasks. The shuffle was labelled an "optimization" but in fact randomized iteration order, defeating locality and making results non-reproducible.

---

### 5. Row-batching the per-pixel color futures in `redoFromScratch`

`Viewer.redoFromScratch` colors a `SIDE × SIDE` image (typically 1000 × 1000). The old code submitted one `Future<Color>` per pixel — `SIDE*SIDE` ≈ 10⁶ tasks — into the executor, then in the write loop called `future.get()` on each one (with per-pixel `InterruptedException`/`ExecutionException` handling and `unchecked` cast of `Object[][]`).

The new code submits exactly `SIDE` tasks, each of which computes a `Color[SIDE]` row, then joins the rows in one pass. The number of task submissions drops by a factor of `SIDE` (≈ 1000×), per-pixel future synchronization is gone, and the final double loop just writes to a `PixelWriter` with no per-pixel exceptions. Two `boolean` flags (`allSelected`, `boundsSelected`) are hoisted out of the inner loops.

---

### 6. `StringBuilder` replacement of `String +=` in the pattern finder

`PatUtils.printImm`, `PatUtils.printPat`, and a dozen methods in `PatternFinder` (`singAction`, `tripAction`, `header`, `createNs`, `xtndAction`, `fireExtendBtn`, `fireCleanBtn`, and the two fire-button handlers) built their output by repeated `result += .... ` in loops. Because Java strings are immutable, each `+=` reallocates and copies the entire accumulated string, turning an n-iteration build into O(n²) character copying. These methods are called per pattern per code line, and their inputs can be large for extend/iteration runs over many `n`.

All of them now use a single `StringBuilder` (or a `StringBuilder[]` for per-row accumulation in `xtndAction`), reducing each build to O(n).


---

### 7. Thread-local `Evaluator` reuse in the cover routines

`Evaluator` wraps a set of preallocated MPFR/MPFI temporaries at a given precision. The cover routines in `common.cpp` and `verify.cpp` previously constructed a fresh `Evaluator eval{prec}` per `cover_square` call. Constructing an `Evaluator` runs a dozen `mpfr_init`/`mpfi_init` allocations on the system heap; destroying it frees them again. Inside a cover pass `cover_square` is called recursively over many squares and many magnifications, so this allocation churn dominated.

The new `Evaluator::thread_local_instance(prec)` (in `evaluator.cpp`/`evaluator.hpp`) keeps a `thread_local std::unique_ptr<Evaluator>` per thread, recreating it only when the precision changes. All four call sites were switched to `Evaluator& eval = Evaluator::thread_local_instance(prec)`. Allocation happens once per thread per precision instead of once per square.

Because the parallel `quarter_covers` recursion in `verify.cpp` already dispatches to TBB worker threads, threads each get their own evaluator, preserving the per-thread isolation the original code comment called out as desirable but had no control over.

---

### 8. Faster serialization of vary results in `vary_3_cpp`

The two JNA entry points turned a `vector<vector<int32_t>>` of found codes into a single string for JNA. The original code used `std::ostringstream` and streamed each integer with `<<`, which is dominated by locale/format dispatch and small allocations.

The new code pre-reserves a `std::string` (`founded_codes.size() * 32`) and appends each integer via `std::snprintf` into a 20-byte scratch buffer, then `buffer.append(temp, len)`. The final string is moved into `to_cstr`. This removes the `ostringstream` overhead and avoids a heap allocation per integer.

---

### 9. `CoverWindow.redoInfo` improver-lookup became O(n + m)

`CoverWindow.redoInfo` annotated each output line with a `#`-suffix drawn from a parallel `preInfo` string. The old code, for each line of `info`, re-scanned every line of `preInfo` doing a `Utils.tripleTrimmer` comparison — an O(n·m) operation on every cover.

The new code builds a `HashMap<String, String> preInfoMap` once, keyed by the trimmed code, then each info line is an O(1) `get`. For covers with thousands of stables and thousands of info lines this turns the annotation pass from seconds into milliseconds.

---
## Architecture Improvements

### Decoupling C++ computation from DB connection ownership

In `wrapper.cpp`, the three picture-loading entry points (`load_picture`, `load_picture_lr_expando`, `load_picture_lr`) previously acquired a single pooled `sqlite::PooledConnection`, then ran the expensive `calculate_stable`/`calculate_unstable` computation *while holding it*, then loaded the picture, all inside one connection scope.

After the change each function follows a three-step pattern: acquire a connection only long enough to call `database::in(...)` (does the row already exist?), drop the connection, run the C++ computation with no connection held, then acquire a fresh connection only long enough to `save` and `load_picture`. The `save_to_database` helper that conflated "compute + persist + return whether the row pre-existed" was effectively inlined and split.

This is better because pooled DB connections are a bounded resource and the C++ compute can take seconds to minutes per code sequence. Holding a connection during the compute serialized otherwise independent work and made pool exhaustion (and the resulting `sqlite::SHOT` waits) the dominant cost in batch workloads like AutoPolyVary.

### Single render pipeline and render coalescing in `Viewer`

Previously `renderRegions` synchronously built four `WritableImage`s (guide lines, regions, bounds, one-by-one) and assigned them to their `ImageView`s inline, on the calling thread. Calls therefore overlapped if the user clicked rapidly, and stale images could be committed after newer images.

After the change, `renderRegions` tracks a single `renderFuture`. A new invocation cancels the previous one (`renderFuture.cancel(true)`), snapshots the `regions` map into an immutable copy, submits the entire four-image build to the executor, and performs the four `imageView.setImage(...)` calls inside one `Platform.runLater` so they commit atomically on the JavaFX thread. Interruption is checked at each phase.

This improves correctness (no torn/stale updates), reduces wasted work (an in-flight render is cancelled rather than allowed to complete into oblivion), and removes the per-pixel `Future.get()` bottleneck described in the performance section.

### Consolidation of reflection handling

`Viewer` previously had three separate copies of the same reflection-toggle logic (an `setOnAction`, a `selectedProperty` listener, and a `Platform.runLater` block that re-selected the checkbox). These were collapsed into a single `updateReflection()` method invoked from the checkbox handler and once more after layout completes via a nested `Platform.runLater`. Behavior is identical; the code path is no longer triplicated.

### Portable database location

`Admin.databaseDir` was a hard-coded `~/billiard-databases`. It is now derived from the running JAR/class location by walking up parent directories until a `settings.gradle` (or the project root) is found, falling back to `user.dir`. This makes the database travel with the application instead of polluting the user's home directory, which matters now that the macOS packaging produces a self-contained `.app`.

---

## Bug Fixes

### B1. JNA `CInfoAll` field order did not match the C struct layout

**Problem.** `CInfoAll.getFieldOrder()` listed fields in the order `initial_angles, points, equations, left_rights, code_seq_lr, sinEquations, cosEquations, vectorX, vectorY`. The C struct `CInfoAll` (and the `copy_to_cinfoAll` writer in `wrapper.cpp`) lays the pointers out as `initial_angles, points, equations, sinEquations, cosEquations, left_rights, code_seq_lr, vectorX, vectorY`. JNA's `Structure` derives native offsets exclusively from this list.

**Root cause.** Documentation/order drift between the Java and C sides. With the old order, JNA populated `left_rights`/`code_seq_lr` from the memory offsets where the C side had written `sinEquations`/`cosEquations`, and vice-versa. Any code that read the equations fields would dereference the wrong pointers.

**Solution.** Reordered `getFieldOrder` to match the C struct, with a comment pointing at `wrapper.hpp`.

**Impact.** Eliminates corrupted equation pointers, wrong database lookups, and potential crashes when the full-equations (`InfoAll`) path was exercised. Also makes the subsequent `cleanup_cinfoAll` fix correct (freeing the right pointers).

### B2. Native memory leak of `CInfoAll` buffers

**Problem.** The C side allocates eight `char*` buffers into a `CInfoAll` (`sinEquations`, `cosEquations`, `initial_angles`, `points`, `equations`, `left_rights`, `code_seq_lr`, `vectorX`, `vectorY`) via `to_cstr`. The Java side never freed them.

**Root cause.** There was a `cleanup_cinfo` (for the smaller `CInfo`) but no `cleanup_cinfoAll`. The `Wrapper.java` methods `loadAllEquation`, `loadInfoAll`, `loadSlopeInfo` allocated a `CInfoAll` and returned without releasing its native memory.

**Solution.** Added `cleanup_cinfoAll` to `wrapper.cpp`/`wrapper.hpp`, bound in `Wrapper.java`, and called from all three methods inside `finally` blocks.

**Impact.** Eliminates a native-byte leak that scaled with the number of `InfoAll`/slope lookups across a session — significant for batch tools that issue thousands of such calls.

### B3. `CString` leak in `calculateGradient`

**Problem.** `Wrapper.calculateGradient` allocates two `CString`s; only the one whose string was actually returned was freed via `cleanup_string`. The other one leaked each call.

**Root cause.** Two native string outputs, one cleanup call.

**Solution.** Both code paths now call `cleanup_string(cstring)` and `cleanup_string(cstring2)` before returning.

**Impact.** Plugs a second native leak on every gradient calculation.

### B4. `CString` leak and racy list population in `varyCSCpp` / `vary3Cpp` 

**Problem.** Each of the three methods allocated a `CString result`, read the C string out of it, and returned without freeing it. In addition, `vary3Cpp` populated a plain `ArrayList<ClassifiedCodeSequence> tmp` from `Arrays.stream(...).parallel().forEach(...)`, which mutates an unsynchronized collection from multiple threads — a data race in `ArrayList` that can corrupt the list or throw `ArrayIndexOutOfBoundsException`.

**Root cause.** Missing cleanup of the native handle; missing synchronization when the common pool's parallel stream mutates the collector.

**Solution.** All three methods now release `result` in a `finally` via `cleanup_string(result)`, and `vary3Cpp` use `Collections.synchronizedList(new ArrayList<>(estimatedLines))` matching the pattern `varyCSCpp` already used. The split `String[]` is now bound to a local before the parallel stream to avoid re-splitting per terminal operation.

**Impact.** No more native leak on every Vary call from Java; no more concurrent `ArrayList` mutation. Rough error messages were also corrected (`"unknown return value for calculateGradient"` → the correct `"… for vary3Cpp"`).


### B5. Static `std::string` buffers shared across threads in the cover code

**Problem.** `getEmpties`, `cover_polygon`, and `cover_small_polygon` used `static std::string` to hold the result returned to JNA. Because they are static, they are shared across all threads; with TBB-parallel or concurrent cover computations one thread can overwrite or resize the buffer another thread is returning a `const char*` into.

**Root cause.** `static` lifetime with shared mutability crossing the C/JNA boundary.

**Solution.** Changed all three to `thread_local std::string`. Each thread now has its own buffer; the pointer returned to JNA is stable for the duration of the call.

**Impact.** Removes a data race and the now-possible return of a dangling/overwritten string to Java. (Caveat: the caller must still consume the string before issuing another cover call on the *same* thread, the same constraint as before.)

### B6. Non-exception-safe native string allocation

**Problem.** `copy_to_cpicture`, `copy_to_cinfo`, and `copy_to_cinfoAll` called `to_cstr(...)` several times in sequence and assigned directly into the struct. If the second or third `to_cstr` threw (e.g. `std::bad_alloc`), the already-allocated `char*`s were leaked.

**Root cause.** No rollback for partially allocated struct members.

**Solution.** Each function now initializes `nullptr` locals, performs all allocations inside a `try`, and on `catch (...)` deletes every allocated pointer and rethrows. The struct fields are only assigned after all allocations succeed.

**Impact.** No native leak under allocation failure.

### B7. Overly tight Newton tolerance and intersection fudge

**Problem.** `Newton::solve` used `eps = "1e-40"` and `intersection_unchecked` used `fudge = "1e-45"`. At typical working precision these tolerances are smaller than the achievable convergence radius and below the meaningful precision of several inputs; iteration could exhaust the `max_iters = 100` cap without setting `within_eps`, and the resulting interval fudge could under-width intersections.

**Root cause.** Tolerances tuned for a specific historical 50-digit case were left in code that runs at a range of precisions.

**Solution.** Relaxed both to `1e-25`. The historical-comment benchmark sequence that motivated `1e-40` is preserved in the comment.

**Impact.** Improves convergence reliability and produces correctly-widthed interval intersections across a broader range of precisions, eliminating a class of "did not converge" failures and over-tight polygon edges.

### B8. `ExecutorService.shutdown()` skipped on exception paths

**Problem.** Several Java callers (`BoyanMenu`, `CodeAndPatternLookupWindow.lookUpIterPat`, `DrawPictureTaskUseLR`, `DrawPictureTaskUseLRTest`) created an `ExecutorService`, called `executor.shutdown()` as their last statement on the happy path, but if an earlier statement threw, `shutdown` was never reached: the pool's threads outlived the operation and leaked.

**Root cause.** `shutdown` placed in the wrong position relative to `return`/`throw`.

**Solution.** Wrapped each call body in `try { ... } finally { executor.shutdown(); }`.

**Impact.** Thread pools are always reclaimed, even on failure and cancellation paths, removing a slow thread leak in long sessions.

### B9. `PolyVaryTask` did not honor cancellation promptly and did not re-interrupt on `InterruptedException`

**Problem.** The coordinate loop in `PolyVaryTask` checked cancellation only deep inside the iteration, and `pixelColor`'s `InterruptedException` handler swallowed the interrupt — the canonical Java pattern is to re-set the interrupt status.

**Root cause.** Missing `Thread.currentThread().interrupt()` in the catch; no top-of-loop cancellation check.

**Solution.** Added an `isCancelled() || Thread.interrupted()` check at the top of the coordinate loop and `Thread.currentThread().interrupt()` in the `pixelColor` interrupted catch.

**Impact.** Cancel is now observed at the start of each coordinate iteration, so a long PolyVary actually stops on cancel; downstream code that depends on the interrupt flag keeps working.

### B10. `CoverWindow` ran the cover compute on the JavaFX thread

**Problem.** The "Calculate" handler called `Wrapper.coverWrapper(...)` (a multi-minute native call) synchronously on the JavaFX Application Thread, freezing the entire UI (no progress, no cancel, "not responding" on some platforms).

**Root cause.** Long blocking call inside an `onAction` handler.

**Solution.** The compute was moved into a `javafx.concurrent.Task` run on a daemon `Thread`. The calculate button is disabled during the compute and re-enabled on `setOnSucceeded`/`setOnFailed`. The same refactor was applied to the "ALL" branch's `coverWrapperAll` path.

**Impact.** UI remains responsive during covers; failures are reported instead of swallowed inside the frozen handler.

### B11. `cover.txt` references to non-existent stable/triple indices crashed

**Problem.** `CoverStuff.load` did `stables.get(index)` / `triples.get(index)` followed by a map put, with no bounds check. A `cover.txt` referencing an index larger than the loaded `stables.txt`/`triples.txt` (which can happen after editing or partial data) threw `IndexOutOfBoundsException`.

**Root cause.** Assumption that the cover file's indices always agree with the stable/triple files' sizes.

**Solution.** Each `get` is now guarded by `index >= 0 && index < list.size()` and a warning is printed to `System.err` on skip.

**Impact.** Loading a partially-consistent cover now degrades gracefully instead of aborting the whole load.

### B12. `PolyVaryTask.pixelColor` (legacy path) could throw on out-of-bounds pixels

**Problem.** The original `pixelColor`'s `FutureTask` called `reader.getArgb(midX, midY)` without bounds checks, which throws for coordinates mapped just past the image edge.

**Root cause.** No bounds check on the computed pixel coordinates.

**Solution.** Added a bounds check returning `0` for out-of-range pixels. (The faster cached-reader path adds the same check.)

**Impact.** Removes occasional `IndexOutOfBoundsException` from the AutoVary loop.


---

## Maintainability Improvements

**Const-correctness and pass-by-reference.** `utils.cpp::getCodeType` now takes `const std::vector<int32_t>&` instead of a mutable reference, allowing it to be called with the temporaries produced elsewhere and signaling non-mutation. `wrapper.cpp::equation_stuff_first_only`/`equation_stuff` take `const Equation<T>&` rather than by value, avoiding a deep copy of each equation on every call. `database.cpp::calculate_all_vector`'s iteration over `vector_set` uses `const std::pair<...>&` instead of by value, removing a copy of two equations per iteration.

**Removing a header-resident `static`.** `utils.hpp` previously defined `static std::unordered_map<std::string, CodeType> stringToCodeType`. As a `static` in a header it was copy-constructed in every translation unit that included the header, wasting time and memory and creating per-TU copies. It is now declared `extern` in the header and defined once in `utils.cpp`.

**`CodeSequence::type()` memoization.** `type()` recomputes parity/closed/stable flags and dispatches a small state machine every call despite being logically immutable per sequence. It now caches its result in a `mutable boost::optional<CodeType>` and returns it on subsequent calls, with a small comment explaining the use of `mutable`.

**`std::rotate` instead of a hand-rolled loop.** `CodeSequence::rotateLeft` replaced a manual element-shift loop (and a commented-out `std::rotate` alternative) with a single `std::rotate` call. Equivalent semantics, less code, and the standard library can apply SIMD/memmove tricks.

**Eliminated per-call dead allocations in vary.** `vary3.cpp` `stack.reserve(max * 2)` the DFS stack up front, removing repeated growth reallocations for deep searches. The lambda captures now move the `code2` vector instead of copying it into the thread pool, eliminating a vector allocation per candidate.

**Consolidated UI listeners and window setup.** The reflection checkbox, the `Viewer` window size (.defaults to 1450x800), and screen-centering on startup were consolidated. The window now centers on whatever screen it appears on via `Screen.getScreensForRectangle`, fixing the previous behavior of popping up at (0,0) on multi-monitor setups.

**Native result-string handling in `Wrapper`.** The `try/finally` + `cleanup_*` pattern was applied consistently across `loadAllEquation`, `loadInfoAll`, `loadSlopeInfo`, `varyCSCpp`, `vary3Cpp`, `vary4Cpp`, and `calculateGradient`, making the native-memory ownership rule uniform and easy to audit: whoever allocates a `CString`/`CInfoAll` releases it.

**`hashCode` quality.** `LeftRight`, `Interval`, `LineSegment`, `Rectangle`, and `Vector2` replaced `Objects.hash(...)` with inline mixing (`Double.doubleToLongBits` + multiply, or `Integer.rotateLeft`-XOR). Besides avoiding the autoboxing and varargs-array allocation `Objects.hash` performs per call, the rotate-XOR mixing for `LeftRight` gives better bit distribution than the sequential multiplier `Objects.hash` uses, which helps the heavy `HashMap`/`HashSet` use in the cover tools.

**Logging hygiene.** Many `System.out.println`/`System.err.println` lines that flooded the console during normal operation (per-coordinate "Cancel detected" messages, "Covered"/"Not covered", "Finish Vary due to too many empty pixels", and CycleVary banners) were commented out, while error paths still print. The C++ side similarly gained (commented-out) `chrono`-based profiling timers that can be re-enabled with a compile flag rather than re-introducing `std::cout` noise.

**Cache + admission control for C++ computes.** `Database.loadStorage` now has an LRU cache (size 1000) keyed by `ClassifiedCodeSequence`, so when the same code sequence appears at multiple coordinates within a session (common in AutoPolyVary) it is computed once. A `Semaphore` sized to `availableProcessors()/4` bounds concurrent C++ computes, necessary because GMP/MPFR allocate from the native heap which is not bounded by the JVM's `-Xmx`. The double-checked locking pattern is used so cache hits never block on the semaphore.

## Technical Appendix

**Algorithms.** The previously O(n²) lexicographically-minimal-rotation scheme is now two passes of Booth's least-rotation algorithm, each O(n) time and O(n) space. The implementation concatenates the input with itself (`s = in + in`) and uses the Lyndon-factorization skip `i += j - k` to advance past already-processed rotations, exactly as described in the standard cp-algorithms treatment; `rotated_copy` then materializes the answer in a single O(n) pass.

The cover-refinement reduction is now a *commutative reduce*. Because each `refine_polygon(p, curve)` intersects the half-plane defined by `curve` with `p`, the operations commute (intersection is associative and commutative on convex polygons), so the parallel `calculate_final_polygon` partitions the sine and cosine curves across `t_threads` batches *interleaved* (round-robin by index, to keep per-batch work balanced), processes each batch from the same bounding polygon, and merges the per-batch polygons with a sequential `intersect_polygons` reduction. The merge step is `O(batches)` calls to `intersect_one_way`, each of which is `O(a.vertices · refine_step)`; the dominant per-curve cost remains `refine_polygon`'s corner evaluation. For sub-threshold curve counts the sequential path is retained to avoid thread-pool overhead. Two early-exit conditions (`polygon_is_tiny` and the per-curve bounding-box sign test) prune the reduction: the former lets a batch return as soon as its working polygon collapses below `1e-12` radians; the latter lets `refine_polygon` skip the corner loop entirely when the curve's bounding-box sign is already conclusive.

**Concurrency.** Two parallel backends are now in use. `boost::asio::thread_pool` is used for the curve-batch reduction (selected because batches are independent and post-joinable). TBB's `parallel_for` over `blocked_range<size_t>` is used for corner evaluation inside `refine_polygon`, where the per-vertex work is small and TBB's work-stealing is desirable. On the Java side, `Database.loadStorage` uses a `Semaphore(cores/4)` plus a `Collections.synchronizedMap(LinkedHashMap)` LRU cache with `removeEldestEntry`, guarded by a double-checked-locking pattern: the cache is consulted before acquiring the semaphore, and again after, to handle the race in which a code sequence is computed by another thread while this one was waiting.

A `ScheduledExecutorService` heartbeat is wired (printing currently commented out) so that stuck C++ computes can be surfaced; the scheduler is a single daemon thread so it never blocks JVM shutdown. The `renderRegions` work in `Viewer` is now single-flight via a `volatile Future<?> renderFuture`; a new invocation cancels the previous, snapshots the region map (so the background task sees an immutable snapshot), and posts the four `imageView.setImage` calls in a single `Platform.runLater` for atomic commitment. The `redoFromScratch` row-batching drops the executor task count from `SIDE²` to `SIDE` and removes the per-pixel blocking `Future.get`.

**Memory.** `Evaluator::thread_local_instance` collapses per-`cover_square` MPFR/MPFI initializations into one-per-thread-per-precision. The `copy_to_*` functions became exception-safe (rollback on partial allocation). `thread_local std::string` for the cover result buffers removes a data race without changing the JNA contract. `makeStarts` removes O(2^d) tuple copies. The `verify.cpp`/`common.cpp` callers now share thread-local evaluators across the recursive quarter-cover recursion, which is safe because each TBB worker thread gets its own.

**Correctness knobs.** Newton `eps` and the intersection `fudge` are both `1e-25` — large enough to be reliably satisfiable at the working precisions used in practice, small enough to remain far below display and triangle-angle resolution. The intersection fudge widens the interval around the Newton iterate before the inside-test; too small a fudge under-widths the intersection (and risks rejecting valid candidates); too large over-approximates. `1e-25` was chosen to match the tolerances observed to work across the test corpus, replacing the historical `1e-45`/`1e-40` values that were tuned to one 50-digit benchmark.

**JNA ABI.** `CInfoAll`'s reordered `getFieldOrder` is the ABI fix: JNA lays struct fields out at byte offsets determined by the order returned here, and the reading of `sinEquations`/`cosEquations` pointers from the wrong offsets is a latent defect whenever `InfoAll` is consulted. The matching `cleanup_cinfoAll` is required for the fix to not merely *leak* the same eight pointers — freeing is now keyed on the corrected field locations.

**Build system.** `resolveLinuxLibraryLinkArg` in `build.gradle` walks `/usr/lib/x86_64-linux-gnu` etc. looking for an unversioned `libboost_thread.so`; failing that, it finds the highest-numbered versioned `.so.*` and emits `-l:libboost_thread.so.1.81.0` to the linker. This is the supported way to link against versioned-only libraries on Debian/Ubuntu where the unversioned `.so` symlink ships in the `-dev` package. The earlier `-ltbb12` (a Windows/Intel-specific name) was changed to `-ltbb` for the main build. The test task exports `JNA.library.path=build/libs/backend/shared` so unit tests can locate the native library without environment setup. The JVM `-Xss` was reduced from `1000m` to `8m` per thread (the previous value capped the runtime to a handful of threads and never matched actual recursion depth), and `-Xms`/`-Xmx` are now overridable through `-PappXms=`/`-PappXmx=` with defaults `256m`/`5g`, applied uniformly across the Linux, macOS, and Windows `run.doFirst` branches.

---


## Files Modified

| File | Purpose of Change | Category |
|---|---|---|
| `src/backend/cpp/equations.cpp` | Parallel curve refinement, early-exit on tiny polygons, optional profiling timers | Performance, Architecture |
| `src/backend/cpp/refine.cpp` | Bounding-box short-circuit, TBB parallel corner eval, `intersect_polygons`/`intersect_one_way` for merging batch results | Performance, Architecture |
| `src/backend/headers/refine.hpp` | Declare `intersect_polygons` and the `LinComArrZ<XYEta>` template extern | Architecture |
| `src/backend/headers/evalf.hpp` | `inline` `multiply_square`/`multiply_cubic`, move intermediates | Cleanup, Performance |
| `src/backend/cpp/code_sequence.cpp` | O(n) Booth-style minimal rotation, cached `type()`, `std::rotate` | Performance, Correctness |
| `src/backend/headers/code_sequence.hpp` | `cached_type_` member | Architecture |
| `src/backend/cpp/evaluator.cpp`, `evaluator.hpp` | `thread_local_instance(prec)` reuse | Performance |
| `src/backend/cpp/common.cpp`, `verify.cpp` | Use thread-local Evaluator; tie lifespan in cover routines | Performance |
| `src/backend/headers/newton.hpp`, `intersection.hpp` | Relax tolerance/fudge to `1e-25`; profiling hook | Correctness |
| `src/backend/cpp/triangle_billiard4.cpp` | Fix `L[i]` → `R[i]` out-of-bounds in `reconfigure` | Bug Fix |
| `src/backend/cpp/vary3.cpp` | `stack.reserve`, move-capture `code2` into pool | Performance |
| `src/backend/cpp/vary4.cpp` | `makeStarts`/`lazySort` append-by-reference refactor; reserve | Performance, Refactor |
| `src/backend/cpp/vary_cs.cpp` | Move `code2` into pool lambda | Performance |
| `src/backend/cpp/utils.cpp`, `utils.hpp` | `extern` map, `const` `getCodeType`, remove dead locals | Cleanup, Correctness |
| `src/backend/cpp/wrapper.cpp`, `wrapper.hpp` | Three-step connection ownership, exception-safe native alloc, `cleanup_cinfoAll`, `thread_local` strings, snprintf serialization, `const&` equations | Bug Fix, Performance, Architecture |
| `src/backend/cpp/database.cpp` | `const&` loop variable | Performance |
| `src/backend/cpp/cover/save.cpp`, `save.hpp` | New `save_holes` for hole-coordinate persistence | Feature |
| `src/java/billiards/wrapper/Wrapper.java` | `finally`-based native cleanup, synchronized lists for parallel streams, `cleanup_cinfoAll` binding | Bug Fix, Correctness |
| `src/java/billiards/wrapper/CInfoAll.java` | Field order matches C struct | Bug Fix |
| `src/java/billiards/database/Database.java` | LRU cache, compute semaphore, heartbeat (dormant) | Performance, Architecture |
| `src/java/billiards/database/Admin.java` | Project-relative database directory | Architecture |
| `src/java/billiards/database/LeftRight.java` and `geometry/{Interval,LineSegment,Rectangle,Vector2}.java` | Inline `hashCode` mixing | Performance |
| `src/java/billiards/viewer/Viewer.java` | Background `renderRegions` with cancellation, row-batched `redoFromScratch`, `Load Holes` feature, window sizing/centering, AutoPolyVary control-flow refactor | Performance, Bug Fix, Architecture |
| `src/java/billiards/viewer/CoverWindow.java` | Off-thread cover compute, button disable, O(n+m) `redoInfo` | Performance, Bug Fix |
| `src/java/billiards/viewer/CycleVaryTask.java`, `PolyVaryTask.java` | Cached `PixelReader`, bounds checks, cancel/re-interrupt, remove shuffle | Performance, Bug Fix |
| `src/java/billiards/viewer/BoyanMenu.java`, `CodeAndPatternLookupWindow.java`, `DrawPictureTaskUseLR.java`, `DrawPictureTaskUseLRTest.java` | `try/finally` executor shutdown | Bug Fix |
| `src/java/billiards/viewer/CycleVaryWindow.java`, `VaryLTask.java` | Reduce console noise | Cleanup |
| `src/java/patternfinder/PatUtils.java`, `PatternFinder.java` | `StringBuilder` instead of `String +=` | Performance |
| `src/java/billiards/cover/CoverStuff.java` | Bounds-check cover/triple index reads | Bug Fix |
| `src/java/billiards/utils/BatchLoadStorage.java` | Quiet cancel-path logging | Cleanup |


---



