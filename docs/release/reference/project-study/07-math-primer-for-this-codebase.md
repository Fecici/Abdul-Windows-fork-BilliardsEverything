# Math Primer for This Codebase

This primer explains the math as implemented by the code, not as a standalone theory text. Names below are source names where possible.

## Problem domain

The project studies triangular billiards through symbolic code sequences. A code sequence is a list of positive integers that represents a billiard path pattern. The software classifies sequences, computes regions in angle space where the sequence is valid, stores those computations in SQLite, varies/searches for sequences, verifies covers, and renders the result in JavaFX.

Code representation:

- Java: `CodeSequence`, `ClassifiedCodeSequence`, `Storage.Stable`, `Storage.Unstable`.
- C++: `CodeSequence`, `CodeType`, stable/unstable structs, equations and exact arithmetic types.

Important files:

- `src/java/billiards/codeseq/CodeSequence.java`
- `src/java/billiards/codeseq/ClassifiedCodeSequence.java`
- `src/backend/cpp/code_sequence.cpp`
- `src/backend/cpp/classified_code_sequence.cpp`
- `src/backend/cpp/equations.cpp`
- `src/backend/cpp/unfolding.cpp`

## Code sequences

Plain-English meaning:

A code sequence is a canonicalized legal list of side-hit counts or moves. The code first checks that all entries are positive and that the sequence is legal under an angle-walk rule. It then canonicalizes by removing repeated legal sublists and choosing a minimal representative across rotations and reversed rotations.

Code representation:

- Java entry: `CodeSequence.create(IntList)`.
- Validation: `CodeSequence.validate`.
- Legality: `CodeSequence.isLegal`.
- Classification wrapper: `ClassifiedCodeSequence.create`.

Concrete examples from tests:

- `1 1 1` classifies as `OSO`.
- `2 2` classifies as `CNS`.
- `1 1 2 2 1 1 3 3` classifies as `OSNO`.

Evidence:

- `src/test/java/billiards/codeseq/ClassifiedCodeSequenceTest.java:17-21`
- `src/test/headers/code_sequence_test.hpp:93-97`

Common failure modes:

- Empty sequence or nonpositive entries are invalid.
- Legal-looking text can be rejected after the angle-walk rule.
- Java and C++ implementations must stay in sync. The tests cover some examples but not every edge case.

## Stable and unstable classification

Plain-English meaning:

Stable sequences describe two-dimensional regions in angle space. Unstable sequences describe lower-dimensional constraints, usually a line segment with a symbolic constraint. Odd legal sequences are treated as stable by the Java classifier.

Code representation:

- `ClassifiedCodeSequence.calculateCodeType` computes closed, stable, and odd flags, then selects `OSO`, `OSNO`, `ONS`, `CS`, or `CNS`.
- `ClassifiedCodeSequence.isStable` builds coefficient sums and returns stable when the three relevant coefficients are zero.
- `Storage.Stable` holds equations, a `ConvexPolygon`, and points.
- `Storage.Unstable` holds equations, a constraint, a `LineSegment`, and points.

Important files/functions:

- `ClassifiedCodeSequence.java:76-92`
- `ClassifiedCodeSequence.java:156` `isClosed`
- `ClassifiedCodeSequence.java:204` `isStable`
- `Storage.java`
- `equations.cpp:340` `calculate_stable`
- `equations.cpp:386` `calculate_unstable`

Common failure modes:

- Stable/unstable mismatch between Java and C++ causes wrong database table, wrong rendering shape, or native exceptions.
- Unstable constraints can be malformed if angle coefficient conversion is wrong.

## Open and closed classification

Plain-English meaning:

Open and closed describe symmetry/return behavior of the code. The Java closed check first excludes odd-length codes, then checks paired halves and reverse relationships.

Code representation:

- Java: `ClassifiedCodeSequence.isClosed`.
- Code types:
  - `OSO`: open stable odd
  - `OSNO`: open stable not odd
  - `ONS`: open not stable
  - `CS`: closed stable
  - `CNS`: closed not stable

Important files:

- `src/java/billiards/codeseq/CodeType.java`
- `src/java/billiards/codeseq/ClassifiedCodeSequence.java`
- `src/test/java/billiards/codeseq/ClassifiedCodeSequenceTest.java`

Common failure modes:

- Closed detection depends on sequence structure after canonicalization.
- Tests cover known examples, but larger generated sequences should be checked against C++ too.

## Symbolic/equation representation

Plain-English meaning:

The native backend represents trigonometric and angle constraints symbolically with exact integer/rational coefficient structures. It then converts, refines, and serializes equations for storage and display.

Code representation:

- `LinComArrZ<XYEtaPhi>`, `LinComArrZ<XYEta>`, `LinComArrZ<XYPi>`, `Equation<Sin>`, `Equation<Cos>`.
- Java parses serialized equations into `Equation` and geometry objects for display.

Important files/functions:

- `src/backend/headers/math/*`
- `src/backend/cpp/equations.cpp`
- `src/backend/cpp/shooting_vectors.cpp`
- `src/backend/cpp/unfolding.cpp`
- `src/java/billiards/database/Database.java`

Common failure modes:

- Exact arithmetic can grow large and stress memory/time.
- String serialization must match Java parsers.
- Floating conversion can hide exact boundary problems in rendering.

## Unfolding

Plain-English meaning:

Unfolding converts a billiards path into equations by reflecting or transforming the trajectory through the code path. The backend produces curve equations and left/right side data used to determine the valid region.

Code representation:

- `Unfolding::shooting_vector_general`
- `Unfolding::generate_curves`
- `Unfolding::generate_curves_lr`
- `shooting_vector_open`
- `shooting_vector_closed`

Important files/functions:

- `src/backend/headers/unfolding.hpp`
- `src/backend/cpp/unfolding.cpp:264`, `345`, `415`, `491`, `579`
- `src/backend/cpp/shooting_vectors.cpp:86`, `110`
- `src/backend/cpp/equations.cpp:340-424`

Common failure modes:

- Large shooting vectors trigger parallel generation paths and memory pressure.
- Left/right versions must match the display/refinement path.
- Debug print differences exist between source and Abdul in `unfolding.cpp`.

## Angle space

Plain-English meaning:

The UI renders regions in a coordinate space based on triangle angles. Java often works in radians for display and geometry. Some UI text says fractions of pi, while `CoverStuff.rationalToRadians` multiplies by `Math.PI / 2.0`, so cover-square input semantics must be checked carefully.

Code representation:

- Java geometry: `Point`, `Vector2`, `ConvexPolygon`, `Rectangle`, `LineSegment`.
- Native exact geometry: rational points, polygons, line segments, interval polygons.
- Cover parser: `CoverStuff.rationalToRadians`.

Important files:

- `src/java/billiards/cover/CoverStuff.java`
- `src/java/billiards/geometry/*`
- `src/backend/headers/geometry/*`

Common failure modes:

- Pi vs Pi/2 prompt mismatch.
- Radian/degree conversion in vary paths.
- UI rendering reflection can invert the apparent coordinate system.

## MRR

Plain-English meaning:

In this codebase, an MRR is the region or object computed for a code sequence and then rendered. Stable codes produce polygonal regions. Unstable codes produce line-segment-like regions constrained by an equation.

Code representation:

- Java loads MRR-like objects through `Database.loadStorage`.
- Native computes missing rows through `save_to_database`, `calculate_stable`, and `calculate_unstable`.
- Stable output becomes `Storage.Stable`.
- Unstable output becomes `Storage.Unstable`.

Important files/functions:

- `Database.java:313` `loadStorage`
- `Wrapper.java:299` `loadPicture`
- `wrapper.cpp:477` `load_picture`
- `wrapper.cpp:350` `save_to_database`
- `equations.cpp:340` `calculate_stable`
- `equations.cpp:386` `calculate_unstable`
- `bounding_region.cpp:612` `calculate_bounding_polygon`
- `bounding_region.cpp:481` `calculate_bounding_line_segment`

Concrete example:

Source comments in `Database.java` document that entering `1 3 3` produces polygon coordinates approximately:

```text
(0.39269908169872414, 0.7853981633974483)
(0.7853981633974483, 0.39269908169872414)
(0.7853981633974483, 0.7853981633974483)
```

This is source-comment evidence, not a fresh calculation in this pass.

Common failure modes:

- `loadStorage` can trigger expensive native calculation when DB rows are missing.
- Wrong native DLL means stepping source may not match runtime behavior.
- Bounding inequalities and refinement are the highest-risk math paths for subtle correctness bugs.

## Covers

Plain-English meaning:

A cover verifies whether a polygon/square region is covered by stable and unstable code objects. Native code recursively subdivides squares and emits a cover tree. Java parses that tree and renders rectangles with associated codes/colors.

Code representation:

- Java UI: `CoverWindow`, `SmallCoverWindow`.
- Native verification: `check_cover`, `check_small_cover`, `check_cover_all`.
- Cover tree tokens:
  - `E`: empty
  - `H`: handled/no-op token in parser
  - `S <index>`: stable code covers rectangle
  - `T <index>`: triple covers rectangle
  - `D`: subdivide rectangle into quarters
- Java storage: `HashTriple`.

Important files/functions:

- `src/java/billiards/viewer/CoverWindow.java`
- `src/java/billiards/viewer/SmallCoverWindow.java`
- `src/java/billiards/cover/CoverStuff.java`
- `src/java/billiards/viewer/HashTriple.java`
- `src/backend/cpp/verify.cpp:766`, `1053`, `1295`
- `src/backend/cpp/common.cpp:785`, `840`

Common failure modes:

- Huge `cover.txt` files stress memory and parse time.
- Source `HashTriple` stores colors per rectangle; patched runtime changed this class, likely to reduce memory.
- `HashTriple.remove` in source does not remove `colorMap`, which can leave stale color entries.

## Vary algorithms

Plain-English meaning:

Vary algorithms search for code sequences near supplied angles/points or under side-sum constraints. Some paths use Java helpers and UI filtering, but the heavy searches call native `fireAway*` functions.

Code representation:

- Java UI: `BoyanMenu`, `VaryWindowL`, `CycleVaryWindow`, `PolyVaryTask`, `VaryLTask`, `CycleVaryTask`.
- Java wrappers: `VaryCS`, `Vary3`, `Vary4`, `Wrapper.varyCSCpp`, `Wrapper.vary3Cpp`, `Wrapper.vary4Cpp`.
- Native: `vary_cs.cpp`, `vary3.cpp`, `vary4.cpp`.

Important files/functions:

- `BoyanMenu.java:567`, `613`, `657`, `693`
- `Wrapper.java:581-667`
- `vary_cs.cpp:209` `fireAwayCS`
- `vary3.cpp:178` `fireAway3`
- `vary4.cpp:200` `fireAway4`

Common failure modes:

- Radian/degree conversion errors.
- Too many generated candidates causing memory pressure.
- Current Abdul `vary4.cpp` syntax typo blocks native build.
- Java parallel parsing races in `vary3Cpp` and `vary4Cpp`.

## Exact arithmetic vs floating point

Plain-English meaning:

Native code prefers exact/rational and interval arithmetic for mathematical decisions. Java rendering and UI use doubles/radians for display and input. The transition between exact computation and rendered coordinates is a correctness boundary.

Code representation:

- C++ exact/rational types under `src/backend/headers/math` and geometry headers.
- Java `double` coordinates in geometry/render classes.
- SQLite serialization bridges exact/native results into strings consumed by Java.

Important files:

- `bounding_inequalities.cpp`
- `bounding_region.cpp`
- `refine.cpp`
- `database/serialize.cpp`
- `database/deserialize.cpp`
- `Database.java`

Common failure modes:

- Exact computation succeeds but rendered polygon appears slightly off because of conversion.
- Overflow/large intermediate expressions, especially in inequality elimination.
- `-ftrapv` can affect signed overflow behavior in debug vs release experiments.

## Polygons/regions

Plain-English meaning:

Stable codes become polygons. Unstable codes become line segments plus constraints. Covers use rectangles and recursively subdivided squares. Rendering layers combine these into JavaFX images.

Code representation:

- Java: `ConvexPolygon`, `Rectangle`, `LineSegment`, `PixelRadianMap`.
- C++: `RationalPolygon`, `ClosedConvexPolygonQ`, `ClosedRectangleQ`, `IntervalPolygon`, line segments.

Important files/functions:

- `src/java/billiards/geometry/*`
- `src/backend/headers/geometry/*`
- `Viewer.renderRegions`
- `CoverStuff.parseRectangle`
- `bounding_region.cpp`
- `verify.cpp`

Common failure modes:

- Boundary inclusion/exclusion bugs.
- Empty polygon after refinement.
- Rendering order hiding regions or covers.
- Reflection transform changing perceived coordinates.

## Database representation of mathematical objects

Plain-English meaning:

The database stores code sequences and computed info so expensive native calculations are not repeated. Java has lightweight code-list tables, while native code stores richer serialized mathematical objects.

Code representation:

- Java database path: `${user.home}/billiard-databases/<dbName>.sqlite`.
- Startup always creates Java-side `garbage` tables for code sequence lists.
- Native code saves computed stable/unstable info, equations, points, constraints, and left/right data.

Important files/functions:

- `Admin.java:23-93`
- `Database.java:313-397`
- `Wrapper.java:253-383`
- `wrapper.cpp:350`, `477`, `673`, `694`, `734`
- `database.cpp:398`
- `database/viewer.cpp:145`, `157`

Common failure modes:

- DB rows generated by one backend version may not match another source/runtime version.
- Missing DB row triggers native computation during a load path.
- Stale DB data can mask source changes during debugging.

