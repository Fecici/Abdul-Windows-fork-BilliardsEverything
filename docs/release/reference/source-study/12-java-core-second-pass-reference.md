# Java Core and JNA Second-Pass Reference

This document covers the Java non-viewer core under:

Current baseline note: Abdul's Java core/JNA surface is still effectively the older source implementation; the material Abdul Java deltas are in viewer defaults and reflection handling. `[MAIN]` keeps non-viewer computational Java packages byte-identical to the source app jar, while changing several `billiards.viewer` classes.

- `src/java/billiards/codeseq`
- `src/java/billiards/database`
- `src/java/billiards/geometry`
- `src/java/billiards/wrapper`
- `src/java/billiards/vary`

Use `docs/source-study/java-core-function-index.txt` for exact line numbers and signatures. It contains 268 method rows from these packages.

## Core Role

The Java core is the frontend's typed model layer and native bridge. It is not just UI support:

- `codeseq` canonicalizes and classifies billiard side-hit words.
- `geometry` gives the viewer and search tools lightweight double-precision geometry.
- `database` converts database/native strings into Java objects.
- `wrapper` is the JNA bridge into C++.
- `vary` searches for candidate code sequences by simulating billiard transitions in Java.

The Java core mirrors many C++ concepts, but not all Java calculations are proof-grade. Exact/rational/interval certification is primarily in C++. Java geometry is mostly for display, filtering, and candidate generation.

## Code Sequence Package

Package: `src/java/billiards/codeseq`

This package is foundational. Almost every viewer/backend workflow starts by turning text or integer arrays into a `CodeSequence`, then into a `ClassifiedCodeSequence`.

### `CodeSequence`

`CodeSequence(dirtyCodeNumbers)` is private. Construction is forced through `create` so validation, repeater elimination, and standard ordering always happen.

`create(dirtyCodeNumbers)` validates a raw list of side labels, removes repeated periodic structure, rotates it into standard order, and returns either `InvalidCodeSequence` or a canonical `CodeSequence`. This is the main entry point called by `Utils.convert`, `Database`, `Wrapper`, and vary workflows.

`subList(list, start, end)` returns a cyclic or bounded slice of an integer list depending on implementation details. It supports repeated-pattern checks and standard-order rotation.

`isRepeated(codeNumbers, subLength)` tests whether the sequence is a repetition of a shorter block. This is important mathematically because a billiard orbit code should usually be represented by its primitive period rather than a repeated traversal.

`validate(dirtyCodeNumbers)` checks that the code is nonempty, uses valid side labels, avoids illegal adjacent repeats, and likely satisfies cyclic legality. It returns an error object rather than throwing.

`eliminateRepeaters(codeNumbers)` returns the primitive code by reducing repeated blocks. For example, a sequence that is two copies of the same word is normalized to one copy.

`evenOddSequence(IntList)` creates the parity pattern string for a code sequence. This pattern is used for classification, database lookup, and pattern-search tools.

`evenOddSequence(List<Integer>)` is the boxed-list overload. It exists for UI/database code that works with Java collections rather than Eclipse primitive lists.

`nextAngle(prev, curr, number)` updates the symbolic triangle-angle state after a side hit. It is part of validating whether a code represents a legal billiard itinerary.

`isLegal(codeNumbers)` checks side-transition legality. In billiard terms, consecutive reflections cannot hit the same side in a physically meaningful code.

`standardOrder(codeNumbers)` rotates/canonicalizes the cyclic word to a unique representative. This is necessary because a periodic orbit code has no intrinsic starting side hit; database keys must not depend on which occurrence was typed first.

`compareIntList(list1, list2)` lexicographically compares primitive integer lists. It underlies ordering for code sequences and sorted sets.

`rotateLeft(list)` mutates a list by moving the first element to the end. It is used while searching for the standard cyclic representative.

`toString()` formats the canonical code as text. UI text areas and database strings rely on this representation.

`hashCode()` and `equals(obj)` make canonical sequences usable as map/set keys.

`compareTo(other)` orders sequences, generally via canonical integer-list comparison.

Interactions:

- `Utils.convert` calls `CodeSequence.create`, then `ClassifiedCodeSequence.create`.
- `Database` parsing creates code sequences from text.
- `Wrapper` passes `ClassifiedCodeSequence.codeSequence.codeNumbers` to native code.
- Vary packages generate raw lists and depend on `create` to discard illegal/noncanonical candidates.

Risk: Java and C++ both implement canonicalization/classification. If their rules diverge, the same typed sequence can map to different database identities across layers.

### `ClassifiedCodeSequence`

`ClassifiedCodeSequence(codeSequence)` is private. It stores a canonical `CodeSequence` plus derived metadata.

`create(codeSequence)` computes classification and returns either invalid or classified result. It is the main Java-side classifier.

`calculateOddEvenPattern(codeNumbers)` computes the odd/even string used for pattern search and code-type logic.

`calculateCodeType(codeNumbers)` determines the `CodeType`. This is the core mathematical classification: code type determines whether backend should expect a stable polygon, unstable curve/segment, open/closed behavior, and which equation family applies.

`calculateCodeSum(codeNumbers)` sums side labels or side weights. The sum is used in display, sorting, and classification heuristics.

`isStableCodeType(codeType)` returns whether a code type represents stable behavior. Many UI paths use this to decide whether to render a polygon or unstable data.

`isOdd(codeNumbers)` determines parity of the sequence. Oddness changes unfolding behavior; in the backend, odd sequences can require doubling before closure.

`isClosed(codeNumbers)` determines whether the symbolic orbit closes under the sequence rather than remaining open/unstable.

`isStable(codeNumbers)` combines parity/closure/type rules to determine stable vs unstable classification.

`length()` returns primitive code length.

`toString()`, `equals`, `hashCode`, and `compareTo` delegate to canonical sequence plus classification. Sorted sets in the viewer depend on this stable ordering.

Interactions:

- `Storage` embeds `ClassifiedCodeSequence`.
- `Database.loadStorage` and `Wrapper.loadPicture/loadInfo/saveToDatabase` consume it.
- Search/vary tasks return collections of it.
- UI controls often display it directly via `toString`.

### `InvalidCodeSequence`

`errorMessage(...)` turns validation failure categories into a user-facing message. It is used when text input cannot become a valid code sequence.

### `Storage`

`Storage` is the Java wrapper for drawable/calculable sequence data. It has abstract behavior with stable and unstable concrete variants.

The private `Storage(classCodeSeq, equations)` constructor stores shared fields for stable/unstable data.

The private `Storage(classCodeSeq, equations, points)` constructor also stores serialized points.

`Storage.Stable(classCodeSeq, equations, points/polygon)` represents a stable code whose valid parameter set is a 2D polygon. It is drawn as a filled region in the viewer.

`Stable.intersects(Rectangle)` tests whether the stable polygon intersects a rectangular view/search region.

`Stable.intersects(ConvexPolygon)` tests stable-polygon intersection against an arbitrary convex polygon.

`Stable.getMinX/getMaxX/getMinY/getMaxY` expose the stable region bounding box for rendering and spatial filtering.

`Storage.Unstable(classCodeSeq, equations, initialAngles, points)` represents an unstable code. It typically renders as a line/curve/segment rather than a filled polygon.

`Unstable.intersects(Rectangle)` tests whether the unstable figure intersects the view rectangle.

`Unstable.intersects(ConvexPolygon)` tests arbitrary polygon intersection.

`Unstable.getMinX/getMaxX/getMinY/getMaxY` expose its bounding box.

`Storage.intersects(...)` abstract overloads define the common filtering API used by draw/search tasks.

`isPositive(rx, ry)` numerically evaluates stored equations at a point. This is not the same as backend interval proof; it is a Java-side point test.

`isPositiveProver(...)` evaluates equations in a more proof-oriented or diagnostic way using supplied parameters. Viewer utilities use it to compare native info with Java storage.

`codeType()`, `codeLength()`, `codeSum()`, and `oddEvenPattern()` expose classification metadata.

`compareTo`, `toString`, `equals`, and `hashCode` allow storage objects to be sorted/deduplicated and shown in UI lists.

Interaction:

- `Database.convertToStorage` creates `Storage` from SQLite strings.
- `DrawPictureTask`, `VaryLTask`, `PolyVaryTask`, and `CycleVaryTask` collect and render `Storage`.
- Viewer region maps use `Storage` as keys.

## Database Package

Package: `src/java/billiards/database`

This package is a mix of admin utilities, parsing/conversion logic, and DTOs wrapping native structures.

### `Admin`

`getDatabasePath(dbName)` returns the filesystem path for a named database.

`getConnectionPool(dbName, poolSize)` constructs a Java `ConnectionPool` backed by a native pool pointer. It calls into `Wrapper.createConnectionPool`.

`initDatabaseDirectory()` ensures the configured database directory exists.

`listDatabases()` enumerates available database files/names for UI selection.

`getUrl(dbName)` builds a JDBC URL for Java-side SQLite access.

`newDatabase(dbName)` creates a new database, usually through the native backend schema path.

`newJavaDB(dbName)` creates the Java-side database schema/tables for pattern lookup. This is distinct from the native backend's MRR/all data tables.

`deleteDatabase(dbName)` deletes a selected database file.

`clearDatabase(dbName)` clears contents while keeping the database itself.

Interaction: `DBGui` calls these methods from the frontend. `Wrapper` handles native creation/clearing for backend-compatible schema.

### `Database`

`findConstraintEta(codeNumbers, first, second)` computes an eta-coordinate linear constraint between two triangle-angle states. It is a Java mirror/helper for backend symbolic constraints.

`findConstraint(codeNumbers, first, second)` computes a pi-coordinate linear constraint.

`parseInitialAngles(string)` parses initial-angle text into a pair of `XYZ` symbolic labels.

`parsePoints(string)` parses point text into an immutable list of `Vector2`.

`parseEquations(string)` parses equation text into Java equation objects.

`parseLeftRights(string)` parses left/right branch metadata.

`convertToStorage(codeSeq, initialAnglesStr, pointsStr, equationsStr)` chooses stable vs unstable `Storage` construction. It calls the parsers above and uses classification metadata on `codeSeq`.

`loadStorage(codeSeq, pool)` loads native picture data through `Wrapper.loadPicture`, then converts it into a `Storage`.

`loadStorageShowLR(codeSeq, pool)` loads picture data plus left/right metadata for diagnostic display.

`loadStorageUseLR(lr, baseCodeSeq, codeSeq, pool)` loads picture data using a supplied left/right branch string. It is used by expando/iteration workflows where the same code can be interpreted under branch choices.

`exists(codeSeq, dbName)` checks whether a code sequence has persisted database data.

`saveToDatabase(codeSeq, dbName)` asks native code to compute/save the sequence.

`deleteBaseFromDatabase(base, db)` is currently an empty stub in the index. Treat it as not implemented.

`saveTripleToDatabase(Triple, db)` saves triple text for cover/stable workflows.

`codeAndOEMatch(codeSeq, OEPattern)` checks whether a code string and odd/even pattern are compatible.

`saveIterationPatternToDatabase(codeSeq, OEPattern, iterPattern, dbName)` writes iteration pattern data for later lookup.

`lookUpIterPatByCodeSeq(codeSeq, dbName)` returns iteration patterns matching a code sequence.

`lookUpIterPatByOEPat(oePattern, dbName)` returns iteration patterns matching an odd/even pattern.

`getPatternsFromDB(pstmt)` executes a prepared statement and collects string results.

Mathematical role: Java `Database` is mostly parsing/transport. It does not prove billiard existence; it asks native code for proof data and turns returned strings into typed Java objects.

### DTOs

`Info(CInfo)` copies native `CInfo` into a Java object. It is used by info windows.

`InfoAll(CInfoAll)` copies the larger native all-equations/vector info structure.

`LeftRight(leftNumber, leftBranch, rightNumber, rightBranch)` represents a branch-choice relationship for a boundary equation. `toString`, `equals`, and `hashCode` make it displayable and usable in sets/maps.

`Picture(CPicture)` copies native picture fields into Java.

`PictureStable(points, equations)` is a simple stable-picture DTO.

`PictureUnstable(initialAngles, points, equations)` is the unstable-picture DTO.

These DTOs are intentionally simple. The risk is not object complexity; the risk is exact agreement with native string formats and JNA struct field order.

## Geometry Package

Package: `src/java/billiards/geometry`

Java geometry is double-based and aimed at frontend rendering, filtering, and candidate search. It should not be confused with backend exact rational/interval proof geometry.

### `Vector2`

`Vector2(x, y)` is private; use `create`.

`sub(v)` returns component-wise difference.

`add(v)` returns component-wise sum.

`scale(scale)` scales both coordinates.

`norm()` returns Euclidean length.

`perp(v)` returns a perpendicular-like operation used in projection/reflection geometry.

`create(x, y)` constructs a vector.

`dot(v, w)` returns dot product.

`cross(v, w)` returns 2D signed area/cross product.

`reflect(v, axis/normal arguments)` reflects a vector across a line/edge. This supports billiard reflection simulation.

`hashCode`, `equals`, and `toString` support collections and display.

### `Interval`

`Interval(min, max)` is private; use `create`.

`create(a, b)` builds an interval with normalized min/max ordering.

`center()` returns midpoint.

`length()` returns width.

`contains(x)` checks scalar containment.

`intersects(a, b)` tests interval overlap.

`hashCode`, `equals`, and `toString` are standard value-object methods.

### `Project`

`project(axis)` is the interface used by separating-axis tests. `ConvexPolygon`, `LineSegment`, and `Rectangle` implement projection onto an arbitrary axis.

### `LineSegment`

`LineSegment(start, end)` is private; use `create`.

`create(points)` constructs a segment from two points.

`intersects(ConvexPolygon)` delegates to polygon/axis intersection checks.

`intersects(Rectangle)` tests segment-rectangle overlap.

`separatingAxis(figure)` checks whether this segment provides a separating axis against another projectable figure.

`project(axis)`, `projectX()`, and `projectY()` produce scalar intervals.

`hashCode`, `equals`, and `toString` are value-object methods.

### `Rectangle`

`Rectangle(intervalX, intervalY)` is private; use `create`.

`create(xMin, xMax, yMin, yMax)` constructs an axis-aligned rectangle.

`setTrimable(trim)` marks whether the rectangle can be trimmed. This flag is used by cover/trim UI workflows.

`center()` returns the center vector.

`contains(x, y)` checks point containment.

`intersects(a, b)` tests rectangle overlap.

`project(axis)`, `projectX()`, and `projectY()` support separating-axis geometry.

`toConvexPolygon()` converts the rectangle to a four-vertex convex polygon.

`subdivide()` returns four quadrant rectangles. Viewer cover recursion and pixel/hole logic use this shape.

`hashCode`, `equals`, and `toString` are value-object methods.

### `ConvexPolygon`

`ConvexPolygon(vertices)` is private; use `create`.

`create(points)` validates convexity/order and returns a polygon.

`giftWrapCheck(points)` verifies that the points form the same convex hull/order expected by the polygon. It is a validation step, not a full computational geometry library.

`location(x, y)` classifies a point as inside/outside/on the polygon.

`sign(v0, v1, x, y)` computes the orientation sign of point `(x,y)` relative to edge `v0 -> v1`.

`separatingAxis(figure)` checks whether any polygon edge normal separates this polygon from the other figure.

`intersects(ConvexPolygon)` uses separating-axis tests for convex polygon intersection.

`intersects(Rectangle)` tests against a rectangle, usually via rectangle-as-projectable or rectangle-as-polygon projection.

`projectX()`, `projectY()`, and `project(axis)` return projection intervals.

`hashCode`, `equals`, and `toString` are value-object methods.

### `TriangleBilliard`

`TriangleBilliard(vertexA, vertexB, vertexC, ...)` is private. It stores one state in a billiard simulation.

`create(xAngle, yAngle, pos)` constructs an initial triangle billiard state from angles and a starting position.

`getNext(left)` advances to the next reflected state using a left/right branch decision.

`getSpecialAngle()` returns the next boundary/special angle relevant to branch decisions.

`toString()` displays the state for debugging.

Mathematical role: this class simulates side-hit dynamics in a triangle for candidate generation. It is not the final exact proof mechanism.

### `TriangleBilliard4`

`TriangleBilliard4(...)` constructors hold richer state for a four-parameter or branch-aware simulation.

`create(xAngle, yAngle)` constructs initial state.

`getNext(left)` advances one branch step and returns empty if the next state is invalid.

`getSpecialAngle()` returns the transition angle for branch ordering.

`between(perfectAngle)` tests whether a perfect/special angle lies in the current interval.

`interval()` returns the size of the current valid angle interval.

`reconfigure(...)` adjusts/reorders points after reflection.

`atan3(y, x, left)` computes a branch-aware angle. The extra boolean resolves ambiguity that ordinary `atan2` cannot express for this simulation.

`mod3(value)` returns a modulo-3 side index.

`toString()` displays state.

## Wrapper Package

Package: `src/java/billiards/wrapper`

This package is the JNA bridge. The Java signatures must exactly match `src/backend/cpp/wrapper.cpp`.

### Native Struct Wrappers

`CInfo.getFieldOrder()`, `CInfoAll.getFieldOrder()`, `CPicture.getFieldOrder()`, and `CString.getFieldOrder()` define native struct field ordering for JNA. Changing order or names can corrupt native interop.

`ConnectionPool(dbPath, poolSize)` calls `Wrapper.createConnectionPool` and stores the returned native pointer.

`ConnectionPool.destroy()` calls `Wrapper.destroyConnectionPool`. Always pair construction with destroy in long-running UI paths.

### `Wrapper`: Database/Pool

Static initialization calls `Native.register("backend")`. The backend native library must be loadable on the Java library path.

`sqlite_error_logging`, `database_create`, `database_clear`, `create_connection_pool`, and `destroy_connection_pool` are private native declarations.

`errorLogging()`, `createDatabase(dbPath)`, `clearDatabase(dbPath)`, `createConnectionPool(dbPath, poolSize)`, and `destroyConnectionPool(dbPtr)` are thin public wrappers.

`backend_cancel()` is a public native cancellation hook for long-running backend jobs.

### `Wrapper`: Cover APIs

`cover_wrapper`, `small_cover_wrapper`, `cover_wrapper_duplicate_stables`, `cover_wrapper_half_duplicate_stables`, and `cover_wrapper_all` are private native declarations.

`coverWrapper(polygon, codes, unstables, digits, subdivide, empty, mrr, pool)` calls native cover verification and returns sampled hole coordinates.

`smallCoverWrapper(...)` calls native small-cover verification and returns the small-cover report text.

`getNotFilledCoordinates(...)` is a public native method used directly for automatic cover subdivision.

`coverWrapperDuplicateStables(...)` translates native status integers into Java integers/exceptions.

`coverWrapperHalfDuplicateStables(...)` translates native status integers into boolean/exceptions.

`coverWrapperAll(mrrDir, pool, depth)` checks an entire MRR cover directory and returns boolean success.

### `Wrapper`: Info/Picture APIs

`load_info_all`, `load_all_equations`, `delete_from_database`, `save_to_database`, `load_picture`, `cleanup_cpicture`, `load_picture_lr`, `load_picture_lr_expando`, `load_info`, `cleanup_cinfo`, and `load_slope_info` are native declarations.

`loadAllEquation(codeSeq, pool)` returns all equation data as `Optional<InfoAll>`. It treats native `0` and `-1` as empty and logs failures.

`loadInfoAll(codeSeq, pool)` returns broad info data as `Optional<InfoAll>`.

`deleteFromDatabase(codeSeq, pool)` deletes a sequence and returns boolean success.

`saveToDatabase(codeSeq, pool)` asks native code to compute/save one classified code sequence.

`saveToDatabase(codeNumbersArray, pool)` is the array overload used by pattern-finder code.

`loadPicture(codeSeq, pool)` returns a `Picture` and calls `cleanup_cpicture`.

`loadPictureLR(baseCodeSeq, codeSeq, pool, lr)` returns a picture under left/right branch interpretation. Current implementation uses `lr == "empty"`, which is a Java reference-comparison risk.

`loadInfo(codeSeq, pool)` returns `Info` and calls `cleanup_cinfo`.

`loadSlopeInfo(codeSequence, pool)` returns vector/slope `InfoAll`.

### `Wrapper`: Cover Management/Search/Gradient/Vary`

`merge_covers`, `code_search_length`, `code_search_even_odd`, `cleanup_string`, `trim_cover`, `bounding_polygon`, `calculate_gradient`, `vary_cs_cpp`, `vary_3_cpp`, and `vary_4_cpp` are native declarations.

`mergeCovers(mergeDir, coverDirs, pool)` newline-joins directories and asks native code to merge them.

`search(type, length, pool)` searches by code type and length.

`search(type, evenOdd, pool)` searches by code type and odd/even pattern.

`trimCover(polygonStr, inDir, outDir)` calls native cover trimming.

`boundingPolygon(codeSeq, pool)` asks native code for a bounding polygon text representation.

`calculateGradient(equation_str, x_str, y_str, from_database)` converts degree strings to radians, calls native gradient calculation, returns the first output string.

`calculateGradient2(...)` returns the second native output string.

`varyCSCpp(movesMin, movesMax, xAngle, yAngle, reqTypes)` calls native vary-CS search, parses newline-separated code sequences, and returns classified codes.

`vary3Cpp(movesMin, movesMax, initPosition, xAngle, yAngle, reqTypes)` calls native three-parameter vary search.

`vary4Cpp(movesMin, movesMax, xAngle, yAngle, reqTypes)` calls native four-parameter vary search.

Important risks:

- `vary3Cpp` and `vary4Cpp` add to `ArrayList` from a parallel stream. That is not thread-safe.
- `varyCSCpp` uses a synchronized list; it is safer.
- All vary wrappers silently skip malformed lines.
- Native result strings should be cleaned if ownership requires it; confirm C++ allocation path before changing.

## Vary Package

Package: `src/java/billiards/vary`

The vary package searches for candidate sequences by recursively walking billiard states. These are candidate finders; backend computation/database insertion is still needed for proof data.

### `Convert`

`convert(codeList)` turns a raw integer list into `Optional<ClassifiedCodeSequence>`. It calls the same canonicalization/classification path as the rest of Java. Invalid sequences return empty.

### `AutoVary`

`AutoVary(min, max, numShots, type, depth, ...)` stores search bounds and type filters.

`recurseFireAway(depth, square, ...)` recursively subdivides/searches parameter space and accumulates candidate codes. It interacts with cover-square concepts at the Java level.

`fireaway()` starts the recursive search and returns classified candidates.

### `Vary3`

`iterateFireAway(...)` is the recursive worker. It advances `TriangleBilliard` states, appends code numbers, applies branch choices, and collects valid classified sequences in the requested move range.

`fireAway(movesMin, movesMax, ...)` overloads are public search entry points with different parameter sets/defaults.

### `Vary4`

`iterateFireAway(...)` is the branch-aware recursive search worker for `TriangleBilliard4`.

`makeStarts(...)` creates initial states/partial codes/side sums for the recursion.

`fireAway(...)` overloads start the search for different caller needs.

`lazySort(...)` orders starting states without doing a full expensive sort.

`doneIteration(code, sideSumArray, leftArray, rightArray, billiards, iterationDepth, sideSum)` handles end-of-iteration bookkeeping and returns a status/count.

`removeLast(code, sideSumArray, leftArray, rightArray, billiards)` backtracks one recursion step.

### `VaryCS`

`iterateFireAway(...)` is the cycle-search recursive worker.

`doneIteration(code, sideSumArray, specMinArray, specMaxArray, leftArray, rightArray, billiards, iterationDepth, sideSum)` handles the end of one recursive branch.

`removeLast(...)` backtracks arrays/lists after a branch returns.

`fireAway(...)` overloads start cycle search and return classified candidates.

## Java Core Optimization Notes

High-value improvements once code edits are allowed:

- Centralize code-sequence normalization between Java and C++ or add cross-language golden tests.
- Replace text formats between native and Java with versioned structured DTOs.
- Fix Java string comparison in `Wrapper.loadPictureLR`.
- Fix non-thread-safe `ArrayList` use in `vary3Cpp` and `vary4Cpp`.
- Add explicit native ownership documentation for every `CString`, `CInfo`, `CInfoAll`, and `CPicture`.
- Avoid swallowing all parse exceptions in vary wrappers; count and expose malformed native lines.
- Split `Database` into parsing, native-loading, and Java-pattern-DB responsibilities.

## How This Layer Interacts With Frontend

The viewer classes do not usually call C++ directly except through `Wrapper` and `Database`. The main frontend path is:

1. User text/file input becomes an `IntList`.
2. `Utils.convert` or `Database` creates a `ClassifiedCodeSequence`.
3. Viewer task calls `Database.loadStorage` or `Wrapper.saveToDatabase`.
4. Native backend computes/loads picture/info.
5. Java `Database.convertToStorage` builds `Storage`.
6. Viewer rendering code draws `Storage.Stable` polygons and `Storage.Unstable` segments/curves.

For debugging, always identify which stage failed: text parsing, canonicalization, native save/load, storage conversion, geometry filtering, or rendering.
