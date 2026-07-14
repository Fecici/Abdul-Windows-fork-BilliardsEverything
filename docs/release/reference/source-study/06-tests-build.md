# Tests And Build Notes

This file documents the build/test setup and the existing tests under `/src/test`.

Current baseline note: Abdul's Java jar tasks are up to date, but a full Windows build still fails in native C++ tasks and does not produce `backend.dll`. The follow-up build/dependency investigation is in `20-abdul-windows-build-dependencies.md`.

No source code was altered during this documentation pass. Build commands did write normal Gradle build outputs under Abdul's `build/` directory.

## Build System

### `build.gradle`

The project uses Gradle with both Java and native C++ plugins:

- `application`
- `cpp`
- `TestingModelBasePlugin`

Main Java entry:

```groovy
mainClassName = 'billiards.viewer.Main'
```

Java source root:

```groovy
sourceSets.main.java.srcDir 'src/java'
```

Java version:

```groovy
sourceCompatibility = 1.17
targetCompatibility = 1.17
```

JavaFX version:

```groovy
javafxVersion = '21.0.1'
```

Runtime native library path:

```groovy
applicationDefaultJvmArgs = ['-server', '-Djna.library.path=./build/libs/backend/shared/', '-Xss1000m']
```

The `run` task depends on:

```groovy
run.dependsOn "backendSharedLibrary"
```

This means the C++ native library should be built before launching the JavaFX app.

### C++ Build Configuration

The native component is declared as:

```groovy
backend(NativeLibrarySpec)
```

Important compiler flags:

- `-O3`
- `-march=native`
- `-flto`
- `-ftrapv`
- `-std=c++14`
- `-Werror` on Clang/GCC paths

Native dependencies linked:

- GMP
- MPFR
- MPFI
- SQLite3
- TBB
- Boost thread/system

macOS has Homebrew include/library paths. Linux uses system linker names. The README says the original project was developed mainly for Unix/macOS/Linux, but this workspace is on Windows with packaged artifacts present.

### Java Dependencies

Key dependencies:

- JavaFX base/controls/graphics/fxml
- JNA
- SQLite JDBC
- Eclipse Collections
- Guava
- Apache Commons Math
- Javaslang
- RichTextFX
- JUnit Jupiter

### Test Tasks

Java tests:

```groovy
test {
    useJUnitPlatform()
}
```

C++ tests:

```groovy
task testBackend(type: Exec, dependsOn: 'testExecutable') {
    commandLine 'build/exe/test/test'
}
```

The native backend binary is marked as checked by `testBackend`.

## C++ Test Harness

### `src/test/cpp/main.cpp`

This is the Boost.Test entrypoint:

```cpp
#define BOOST_TEST_DYN_LINK
#define BOOST_TEST_MODULE test_backend
#include <boost/test/unit_test.hpp>
```

It includes all C++ test headers into one translation unit. The comments say this is simpler for the build system and compiles faster.

Included test headers:

- `bounding_region_test.hpp`
- `code_sequence_test.hpp`
- `diff_test.hpp`
- `division_test.hpp`
- `equations_test.hpp`
- `general_test.hpp`
- `gradient_test.hpp`
- `parse_test.hpp`
- `shooting_angles_test.hpp`
- `trig_identities_test.hpp`

## C++ Test Coverage By File

### `src/test/headers/code_sequence_test.hpp`

Tests:

- Empty sequences throw.
- Non-positive sequences throw.
- Illegal parity patterns throw.
- Repeated legal sub-sequences canonicalize to shortest legal representative.
- Rotations and reversed rotations canonicalize to the same representative.
- Type classification examples for `OSO`, `CNS`, `ONS`, `CS`, and `OSNO`.
- Angle-label evolution for representative code sequences.
- Constraint computation in `XYEta`.

Important examples:

- `{1,1,1}` -> `OSO`, constraint `0`.
- `{2,2}` -> `CNS`, constraint `x-y`.
- `{1,1,1,1,2,1,1,1,1,2}` -> `CS`.
- `{1,1,2,2,1,1,3,3}` -> `OSNO`.

Gap: does not test `CodeSequence::create` invalid enum return in C++ factory, which is where the `std::string::find` bug is.

### `src/test/headers/bounding_region_test.hpp`

Active tests:

- `test_calculate_bounding_polygon`
- `test_calculate_bounding_line_segment`

The disabled tests would directly test line sign and intersection. Active tests verify exact rational vertices for known nonempty stable polygons and unstable segments.

Mathematical coverage:

- Base triangle clipping.
- Linear inequality refinement.
- Exact rational expected points.

Gap: empty polygon/segment cases and all 27 sign cases in `initial_line_segment` are not exhaustively tested.

### `src/test/headers/trig_identities_test.hpp`

Tests:

- `simplify_sin_xypi`
- `simplify_cos_xypi`
- `simplify_sin_xyeta`
- `simplify_cos_xyeta`
- corner evaluation of sine/cosine linear combinations at `(0,0)`, `(0,pi/2)`, `(pi/2,0)`, `(pi/2,pi/2)`

Disabled tests cover direct `sin(n*pi/2)` and `cos(n*pi/2)` helpers.

Mathematical coverage:

- Sign normalization.
- `pi` periodicity.
- `eta = pi/2` shifts between sine and cosine.
- Corner sign simplification used in bounding/refinement logic.

### `src/test/headers/division_test.hpp`

Tests one example for each trig division combination:

- sine divided by sine -> cosine quotient.
- sine divided by cosine -> sine quotient.
- cosine divided by sine -> sine quotient.
- cosine divided by cosine -> cosine quotient.

Gap: only `divide_once` is covered. Heuristic line factoring in `divide_out_lines` and order-dependent factor removal is not directly regression-tested.

### `src/test/headers/diff_test.hpp`

Tests symbolic derivatives:

- `d/dx` and `d/dy` for sine equations.
- `d/dx` and `d/dy` for cosine equations.

Coverage includes signs, coefficients, and multi-term sums.

### `src/test/headers/equations_test.hpp`

Tests:

- `test_calculate_empty`: codes from `src/test/resources/empty_codes_to_15.txt` should produce empty MRR results.
- `test_calculate_nonempty`: codes from `src/test/resources/nonempty_codes_to_15.txt` should produce stable or unstable MRR data.

The helper `parse_file` skips blank lines and lines containing `s`/`S`, then parses code sequences.

Importance: this is the highest-level C++ computational test because it calls `calculate_stable` or `calculate_unstable`.

Gap: failures are caught and printed, but the catch block does not fail the test explicitly after logging. If an exception occurs, the test may continue without a hard failure depending on control path.

### `src/test/headers/gradient_test.hpp`

Active test:

- `test_gradient_bound`

It verifies that gradient-bound sums match expected coefficient-weighted bounds for symbolic trig equations.

Disabled tests cover special gradient cases at `(pi/2,pi/2)`.

### `src/test/headers/parse_test.hpp`

Tests parser/printer round trips:

- `LinComArr<XY>`
- `LinComArr<XYPi>`
- `LinComArr<XYEta>`
- sine/cosine `LinComMap<XY>` equations

This is important because many database and UI paths depend on string serialization.

### `src/test/headers/shooting_angles_test.hpp`

Currently disabled under `#if 0`. It appears to test an older `calculate_closed_index` API.

The active equivalent logic is now in `CodeSequence::closed_index`, but the disabled test vectors could be revived and adapted.

### Other C++ Test Headers

`general_test.hpp` was included but not deeply inspected in this pass. It likely tests basic helpers such as `other_angle`.

## Java Tests

### `src/test/java/billiards/codeseq/CodeSequenceTest.java`

Tests:

- Empty sequence returns `InvalidCodeSequence.EMPTY`.
- Non-positive sequences return `NEGATIVE_OR_ZERO_NUMBERS`.
- Illegal parity patterns return `ILLEGAL_PATTERN`.
- Repeated legal sequences canonicalize to shortest representative.
- Rotations canonicalize to standard order.

Gap: Java test does not include the `{1,3,1}` rotation case that C++ tests include.

### `src/test/java/billiards/codeseq/ClassifiedCodeSequenceTest.java`

Tests classification examples:

- `{1,1,1}` -> `OSO`
- `{2,2}` -> `CNS`
- `{1,1,2,1,3,2}` -> `ONS`
- `{1,1,1,1,2,1,1,1,1,2}` -> `CS`
- `{1,1,2,2,1,1,3,3}` -> `OSNO`

Gap: no tests for `oddEvenPattern`, `codeSum`, closed symmetry edge cases, or overflow behavior in `calculateCodeSum`.

## Recommended Regression Tests To Add

High priority:

- C++ test for `CodeSequence::create` invalid reason mapping.
- Java test for `Database.codeAndOEMatch`.
- Java test for `Wrapper` vary parsing using a parallel stream or replace with sequential/synchronized behavior.
- C++ test for `TriangleBilliard4` against Java known values for `getNext`, `between`, and `interval`.
- C++ test for `Vary4::makeStarts` expected number/depth of starts.
- C++ test for `generate_curves_lr(..., left_rights)` duplicate equation keys preserving all `LeftRight` witnesses.

Medium priority:

- Cross-language classification fixture: same list of code sequences must produce same canonical string, type, sum, length, and stable flag in Java and C++.
- End-to-end wrapper smoke test with a temporary SQLite DB and known simple code.
- Cover smoke test on a tiny cover fixture.
- Parser round trip for database serialization/deserialization of `InitialAngles`, `LeftRight`, `Stable`, and `Unstable` payloads.

## Build/Run Caveats

- `README.md` was originally written for Java 8 and Unix-like systems, but `build.gradle` now targets Java 17 and JavaFX 21. Treat README setup details as historically useful but not fully current.
- Native dependencies must be discoverable by the platform linker and by JNA at runtime.
- The Java `run` task sets `JNA_LIBRARY_PATH` and platform-specific library path variables.
- Windows support may be incomplete despite this workspace being on Windows; build comments and README emphasize macOS/Linux.
- The build uses old Gradle native model APIs, which may be fragile with newer Gradle versions.
- `-march=native` makes native binaries machine-specific.
- `-flto` and `-Werror` can make compiler/toolchain changes fail builds.
