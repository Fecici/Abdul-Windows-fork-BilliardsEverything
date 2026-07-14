# Risks And Optimization Opportunities

This file records concrete issues and improvement opportunities found while documenting. Source code was not changed.

Current baseline note: for Abdul, add these current risks to the older source-tree risk list: `eliminate_phi` may trade memory pressure/performance differently because duplicates accumulate in vectors before `sort/unique`; `Viewer` reflection setup can apply transforms more than once; and Windows builds currently fail before producing `backend.dll`. `[MAIN]` does not have Abdul's reflection default or vector-buffer `eliminate_phi`, but it does have its own `HashTriple` memory patch and small-cover replacement behavior.

Severity labels:

- Critical: likely wrong results, data loss, or crashes in ordinary use.
- High: likely wrong results under common workflows or serious scalability problems.
- Medium: localized bug, leak, race, or maintainability issue.
- Low: cleanup, style, or future performance improvement.

## Correctness Risks

### Critical: C++ exception mapping in `CodeSequence::create`

File: `src/backend/cpp/code_sequence.cpp`

Problem:

```cpp
} else if (msg.find("non-positive")){
    return InvalidCodeSequence::NEGATIVE_OR_ZERO_NUMBERS;
} else if (msg.find("illegal")){
    return InvalidCodeSequence::ILLEGAL_PATTERN;
}
```

`std::string::find` returns `std::string::npos` when not found, and `npos` converts to true. These branches should compare with `!= std::string::npos`. As written, an exception that is not `"empty"` is likely classified as non-positive even if it is illegal or unknown.

Impact: invalid-code error reporting and any caller depending on exact invalid reason may be wrong.

### Critical: Java parallel parse writes to non-thread-safe `ArrayList`

File: `src/java/billiards/wrapper/Wrapper.java`

Methods:

- `vary3Cpp`
- `vary4Cpp`

Problem: both methods use `Arrays.stream(...).parallel().forEach(...)` and call `tmp.add(...)` on a plain `ArrayList`. `ArrayList` is not thread-safe.

Impact: missing codes, corrupted list state, rare exceptions, nondeterministic result order.

`varyCSCpp` uses `Collections.synchronizedList`, so this risk is already solved there.

### Critical: C++ `Unfolding::generate_curves_lr(..., left_rights)` drops duplicate keys

File: `src/backend/cpp/unfolding.cpp`

Problem: the no-left-right overload merges per-thread `CurvesLR` maps by appending vectors for duplicate equation keys. The left-right overload uses:

```cpp
curves.first.insert(tc.first.begin(), tc.first.end());
curves.second.insert(tc.second.begin(), tc.second.end());
```

`std::map::insert` ignores entries whose key already exists. If multiple threads produce the same equation with different `LeftRight` vectors, later vectors are dropped.

Impact: show/use-LR workflows can lose witnesses. This can make regenerated MRR data incomplete or inconsistent.

### Critical: C++ `TriangleBilliard4` port discards vector subtraction results

File: `src/backend/cpp/triangle_billiard4.cpp`

Problem examples:

```cpp
Vector2D direc1 = vertexC;
direc1.sub(tempR.front());
```

`Vector2D::sub` returns a new `Vector2D`; it does not mutate `direc1`. The returned vector is discarded. Similar patterns appear in `reconfigure`.

Impact: `Vary4` C++ visibility angle calculations can be wrong because they use `vertexC` or `end` directly rather than `vertexC - trailPoint`.

### Critical: C++ `Vary4::fireAway4` likely calls `makeStarts` with swapped arguments

File: `src/backend/cpp/vary4.cpp`

Problem:

```cpp
std::vector<TriangleStart> starts = makeStarts(startBilliard,movesMax, cores, startCode, sideSum);
```

Signature:

```cpp
makeStarts(TriangleBilliard4& billiard, int32_t depth, int32_t maxDepth, ...)
```

The Java version calls `makeStarts(startBilliard, 0, numThreads, ...)`. The C++ version appears to pass `depth=movesMax`, so if `movesMax >= cores`, it immediately returns a single start instead of generating parallel starts.

Impact: Vary4 C++ search may be much less parallel than intended and may begin with incorrect depth semantics.

### Critical: C++ `iterateFireAway4` uses stale `billiard` for beam check

File: `src/backend/cpp/vary4.cpp`

Problem:

```cpp
if (billiard.between(perfectAngle)) {
```

This is inside the loop over `frame.cbilliard`. It likely should check `frame.cbilliard.between(perfectAngle)`.

Impact: candidates may be accepted or rejected using the initial triangle's beam interval rather than the current unfolded state.

### High: Java `Wrapper.loadPictureLR` compares strings with `==`

File: `src/java/billiards/wrapper/Wrapper.java`

Problem:

```java
if (lr == "empty") {
```

Java string content comparison requires `.equals`.

Impact: depending on whether `lr` is interned, the wrong native function may be called.

### High: Java `Database.codeAndOEMatch` indexes the wrong loop variable

File: `src/java/billiards/database/Database.java`

Problem inside the inner `j` loop:

```java
Integer.parseInt(codeNumbers[i])
OEPatternArray[i].charAt(i)
```

It appears intended to use `codeNumbers[j]` and `OEPatternArray[i].charAt(j)`.

Impact: iteration-pattern validation can reject valid entries, accept invalid entries, or throw indexing exceptions.

### High: C++ `Vector2D::equals` epsilon values are enormous

File: `src/backend/cpp/triangle_billiard.cpp`

Problem:

```cpp
return std::abs(this->x - other.x) < 1e9 && std::abs(this->y - other.y) < 1e12;
```

These thresholds make nearly any normal billiards coordinates equal.

Impact: if used in real checks, equality is meaningless. It may currently be debug-only, but it should not be trusted.

### High: C++ thread-count zero handling is inconsistent

Files:

- `src/backend/cpp/unfolding.cpp`
- `src/backend/cpp/vary3.cpp`
- `src/backend/cpp/vary4.cpp`
- `src/backend/cpp/vary_cs.cpp`

Some functions handle `std::thread::hardware_concurrency() == 0`, some do not. Several places divide by `concurrency` or create a thread pool with `cores` directly.

Impact: rare but possible crashes or invalid task partitioning on platforms returning 0.

### Medium: Java native string cleanup appears incomplete for `CString` and `CInfoAll`

File: `src/java/billiards/wrapper/Wrapper.java`

Examples:

- `calculateGradient2` reads from `cstring2` but calls `cleanup_string(cstring)`.
- `loadInfoAll`, `loadAllEquation`, and `loadSlopeInfo` construct `InfoAll` from `CInfoAll`, but no cleanup function is visible in the Java wrapper.

Impact: native memory leaks during repeated gradient/all-equation workflows.

### Medium: C++ debug prints in hot backend paths

File: `src/backend/cpp/unfolding.cpp`

Problem:

```cpp
std::cout << "comb" << std::endl;
```

appears in `generate_curves_lr` paths.

Impact: UI/CLI noise and severe slowdown if called frequently.

### Medium: Java `ConvexPolygon.giftWrapCheck` can divide by zero

File: `src/java/billiards/geometry/ConvexPolygon.java`

Problem: angle calculation divides by `back.norm() * forth.norm()`. Duplicate adjacent points or repeated first/current points can make a zero vector.

Impact: `NaN` angle, false convexity behavior, or unexpected runtime errors.

### Medium: C++ `VaryCS` ignores requested code types

File: `src/backend/cpp/vary_cs.cpp`

Problem: `parse_code_types(reqType, ...)` is computed but candidate insertion checks only:

```cpp
if (codeType == CodeType::CS)
```

Maybe intentional because VaryCS only returns closed stable codes. If UI exposes requested types for CS search, this is confusing.

Impact: request filters are misleading for VaryCS.

### Medium: Public mutable `CodeSequence::code_numbers` weakens invariants

File: `src/backend/headers/code_sequence.hpp`

Problem: comments describe immutability and validated canonical form, but `code_numbers` is public and mutable.

Impact: any caller can mutate a valid sequence into an invalid one after construction.

## Optimization Opportunities

### High: Factor/refine curves while generating them

Files:

- `src/backend/cpp/unfolding.cpp`
- `src/backend/cpp/equations.cpp`
- `src/backend/cpp/database.cpp`

Current flow often generates all curves, stores them in sets/maps, then refines later. Comments already suggest generating/refining in one pass could reduce memory.

Proposal:

- For MRR calculation, generate one curve at a time.
- Immediately factor it and test whether it cuts the current region.
- Drop curves that are positive on the current region.
- Stop early if region becomes empty.

Expected benefit: lower peak memory and faster failure for empty regions.

### High: Improve line-factor division order

File: `src/backend/cpp/division.cpp`

Comments note that the order of dividing `sin(x)`, `sin(y)`, and `sin(x+y)` affects final equation length.

Proposal:

- Try all valid division orders for a curve.
- Choose the resulting equation with smallest term count and coefficient cost.
- Cache by original equation.

Expected benefit: shorter equations, faster evaluation, smaller DB rows.

### High: Cache path vectors in `Unfolding`

File: `src/backend/cpp/unfolding.cpp`

`generate_curves`, `generate_curves_lr`, and `get_all_vectors` repeatedly call `find_path` and `path_vector` for many left/right pairs. Some paths may be recomputed across workflows.

Proposal:

- Memoize `(left_vertex,right_vertex) -> path_vector`.
- Keep cache local to one `Unfolding` instance.
- Use immutable map after precomputation or thread-local fill plus merge.

Expected benefit: faster repeated all-equation, LR, and vector generation.

### Medium: Replace some `std::map` equation stores with deterministic hash maps

Files:

- `src/backend/headers/math/lin_com_map.hpp`
- equation-set users across backend

`std::map` gives deterministic order but has high overhead. For hot internal stages, deterministic order is not always required until serialization.

Proposal:

- Keep `std::map` for serialized canonical equations.
- Consider hash maps for transient accumulation if profiling shows term insertion dominates.

Risk: must preserve deterministic output where DB equality/order matters.

### Medium: Avoid copying large code/vector structures in threaded lambdas

Files:

- `src/backend/cpp/vary3.cpp`
- `src/backend/cpp/vary4.cpp`
- `src/backend/cpp/vary_cs.cpp`
- `src/backend/cpp/unfolding.cpp`

Many lambdas capture by value using `[=, ...]`, which can copy vectors/objects. Some copies are necessary for thread safety, but others may be avoidable.

Proposal:

- Audit captures.
- Move immutable shared data into `shared_ptr<const T>` or references whose lifetime is guaranteed.
- Keep per-task mutable data local.

Expected benefit: lower memory pressure during deep searches.

### Medium: Use stable task partitioning and append-safe merges

Files:

- `src/backend/cpp/unfolding.cpp`
- C++ vary files

Current block sizing is heuristic and sometimes uses equation term count to choose task sizes. Some merge logic differs between overloads.

Proposal:

- Centralize partition calculation.
- Always handle `concurrency == 0`.
- Always merge maps by appending values for duplicate keys.
- Consider `tbb::parallel_for` consistently if TBB is already a dependency.

### Medium: Reduce duplicate Java/C++ classification logic

Files:

- `src/java/billiards/codeseq/*.java`
- `src/backend/cpp/code_sequence.cpp`
- `src/backend/cpp/classified_code_sequence.cpp`

The same definitions of legal, closed, stable, and type exist in several places.

Proposal:

- Choose C++ `CodeSequence` as the canonical computational classifier.
- Keep Java classifier for UI responsiveness only if tested against C++.
- Add cross-language test vectors.

Expected benefit: fewer drift bugs.

### Medium: Make `Viewer.java` smaller by responsibility

File: `src/java/billiards/viewer/Viewer.java`

`Viewer` owns UI construction, event handling, domain transformations, file parsing, rendering, cover loading, vary orchestration, and iteration-pattern database behavior.

Proposal:

- Extract rendering into `ViewerRenderer`.
- Extract code-sequence manipulation into `CodeSequenceUiModel`.
- Extract cover loading/rendering into `CoverLayerController`.
- Extract vary orchestration into `VaryController`.
- Keep `Viewer` as a coordinator.

Expected benefit: easier testing and line-by-line comprehension.

### Low: Replace manual vector/list helpers with standard algorithms where safe

Examples:

- C++ `CodeSequence::rotateLeft` can use `std::rotate`.
- Java and C++ sublist/rotation helpers can be centralized.

Keep manual versions only where primitive-list libraries make this necessary.

### Low: Remove or gate old debug comments/prints

Many files include long dated comments and commented-out prints. Some are valuable mathematical breadcrumbs. Others obscure current logic.

Proposal:

- Preserve mathematically meaningful examples in docs.
- Gate debug prints behind a logging flag.
- Remove obsolete commented-out code only after tests exist.

## Testing Gaps

Existing tests cover:

- C++ code sequence behavior.
- C++ parsing.
- C++ trig identities.
- C++ equation/division/diff basics.
- C++ bounding region and gradient basics.
- Java code sequence and classified code sequence tests.

Gaps to add before refactoring:

- Cross-language parity tests: Java and C++ should classify the same representative codes identically.
- VaryCS/Vary3/Vary4 small known-output tests.
- `TriangleBilliard4` Java-vs-C++ reflection and beam-bound equivalence tests.
- `generate_curves_lr(..., left_rights)` duplicate-key merge regression test.
- Wrapper parse tests for large parallel vary output.
- Database `codeAndOEMatch` tests.
- Native memory cleanup stress test for repeated `InfoAll` and gradient calls.
