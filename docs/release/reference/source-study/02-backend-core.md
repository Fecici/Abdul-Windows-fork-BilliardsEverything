# Backend Core

This file documents the C++ core that everything else builds on: numeric types, symbolic algebra, code-sequence validation, and classification. Paths are relative to the repository root.

Current baseline note: these core type and symbolic-math descriptions remain accurate for Abdul's fork. The important Abdul native delta is not in the basic numeric/type layer; it is in `src/backend/cpp/bounding_inequalities.cpp::eliminate_phi`, covered in `03`, `17`, and `19`.

## Numeric Layer

### `src/backend/headers/numbers.hpp`

This header centralizes numeric meaning:

| Type | Definition | Purpose |
| --- | --- | --- |
| `CodeNumber` | `int32_t` | One integer in a code sequence |
| `Coeff16` | `int16_t` | Compact coefficients for evaluated equation vectors |
| `Coeff32` | `int32_t` | Medium integer coefficients |
| `Coeff64` | `int64_t` | Main symbolic coefficient type |
| `Float` | `boost::float64_t` | Ordinary floating evaluation and Java interop |
| `Integer` | GMP-backed arbitrary precision integer | Exact integer arithmetic |
| `Rational` | GMP-backed rational | Exact rational geometry |
| `Real` | MPFR 50-bit float | High precision point estimates |
| `Interval` | MPFI 50-bit interval | Rigorous sign/containment checks |

Important implementation point: comments warn that Boost multiprecision expression templates require explicitly naming the result type for intervals. This is why backend code often writes `const Interval x = ...` instead of `auto x = ...`.

Optimization notes:

- `Coeff64` is safe for many symbolic operations but can overflow for very long code sequences. The code sometimes uses `-ftrapv`, but not all arithmetic is protected equally across compilers.
- Some equation vectors are downcast into `Coeff16` in `common.hpp::map_to_vec`; this is a possible overflow point for very large expressions.

## Symbol Enums

### `src/backend/headers/math/symbols.hpp`

This header defines enum classes used as typed coordinates in linear combinations:

| Enum | Members | Meaning |
| --- | --- | --- |
| `XY` | `X,Y` | Final two triangle angle variables |
| `XYZ` | `X,Y,Z` | Abstract triangle angle labels |
| `XYPi` | `X,Y,Pi` | Linear expressions with explicit `pi` |
| `XEta` | `X,Eta` | One-variable expression with `eta = pi/2` |
| `YEta` | `Y,Eta` | One-variable expression with `eta = pi/2` |
| `XYEta` | `X,Y,Eta` | Two-variable expression with `eta = pi/2` |
| `XYEtaPhi` | `X,Y,Eta,Phi` | Adds auxiliary variable `phi` |
| `ST` | `S,T` | Laurent/polynomial substitution variables for division |

`Enum<T>::size` tells `LinComArr` how many coefficients an enum has. The enums are intentionally `size_t`-backed so they can index `std::array`.

### `src/backend/cpp/math/symbols.cpp`

Function-level notes:

| Function | Purpose | Calls | Called by |
| --- | --- | --- | --- |
| `operator<<(XY)` | Prints `x` or `y`. | `invalid_enum_value` on bad enum | Debug/serialization output |
| `operator<<(XYZ)` | Prints `x`, `y`, or `z`. | `invalid_enum_value` | Code sequence, unfolding, errors |
| `operator<<(XYPi)` | Prints `x`, `y`, or `pi`. | `invalid_enum_value` | Equation printing |
| `operator<<(XEta)` | Prints `x` or `eta`. | `invalid_enum_value` | One-variable parsing/eval helpers |
| `operator<<(YEta)` | Prints `y` or `eta`. | `invalid_enum_value` | One-variable parsing/eval helpers |
| `operator<<(XYEta)` | Prints `x`, `y`, or `eta`. | `invalid_enum_value` | Bounding lines and constraints |
| `operator<<(XYEtaPhi)` | Prints `x`, `y`, `eta`, or `phi`. | `invalid_enum_value` | Bounding inequality elimination |
| `operator<<(ST)` | Prints `s` or `t`. | `invalid_enum_value` | Polynomial division diagnostics |
| `other_angle(XYZ,XYZ)` | Returns the third angle label. | none | CodeSequence, Unfolding, shooting vector logic |

`other_angle` is mathematically the map `{X,Y,Z} \ {angle1, angle2}`. It throws if both inputs are equal or invalid.

## Linear Combination Templates

### `src/backend/headers/math/lin_com_arr.hpp`

`math::LinComArr<E,N>` represents a fixed-size linear combination over an enum `E` with coefficient type `N`.

Example:

```cpp
LinComArrZ<XYEta>{1, -2, 3}
```

represents:

```text
x - 2y + 3eta
```

Function-level notes:

| Function | Purpose | Calls | Called by |
| --- | --- | --- | --- |
| Default constructor | Builds the zero linear combination. | `coeffs.fill(0)` | Everywhere |
| Variadic constructor | Builds from exactly one coefficient per enum member. | static size check | Conversions, constraints, tests |
| `begin/end` | Iteration over coefficients. | std begin/end | Normalization, printing, algorithms |
| `add(E)` / `sub(E)` | Add or subtract one symbol. | scalar `add/sub` | Conversion and symbolic construction |
| `add(N,E)` / `sub(N,E)` | Add or subtract a scaled enum symbol. | `std::array::at` | Constraint and angle formulas |
| `add(LinComArr)` / `sub(LinComArr)` | Add or subtract another linear expression. | scaled overloads | Symbolic equation assembly |
| `add(N,LinComArr)` / `sub(N,LinComArr)` | Add or subtract scaled expression. | loop over coefficients | CodeSequence constraints, Unfolding |
| Static `add/sub` | Functional helpers returning a new object. | mutating overloads | Trig identity multiplication |
| `scale` | Multiply all coefficients. | loop | Sign normalization, shooting vectors |
| `unit` | Sign of first nonzero coefficient. | none | `divide_unit` |
| `divide_unit` | Normalizes the first nonzero coefficient to positive. | `unit`, `scale` | Constraints and equations |
| `content` | GCD of all coefficients. | `gcd` | `divide_content` |
| `divide_content` | Divides by coefficient GCD. | `content` | Constraints and curves |
| `coeff(E)` / `coeff<Enum>()` | Access one coefficient. | array lookup | Almost all symbolic algorithms |
| `is_zero` | Tests all coefficients equal zero. | loop | Stability and special cases |
| `operator==` / `operator<` | Value comparison and map ordering. | array comparison | Sets/maps |
| `operator<<` | Human-readable expression. | `is_zero`, enum `operator<<` | Logs and DB/string output |

Key invariant: unlike `LinComMap`, `LinComArr` stores zeros because the dimension is fixed.

### `src/backend/headers/math/lin_com_map.hpp`

`math::LinComMap<T,N>` represents sparse symbolic sums where keys are arbitrary expressions, usually `Sin<arg>` or `Cos<arg>`.

Invariant: the private `std::map<T,N>` never stores zero coefficients. All mutating methods maintain this by erasing terms that become zero.

Function-level notes:

| Function | Purpose | Calls | Called by |
| --- | --- | --- | --- |
| Initializer-list constructor | Builds from explicit term pairs. | map init | Tests and some direct construction |
| `begin/end` | Iterate terms. | std begin/end | All equation algorithms |
| `add(T)` / `sub(T)` | Add/subtract term with coefficient 1. | scalar overloads | Equation assembly |
| `add(N,T)` / `sub(N,T)` | Add/subtract scaled term and erase zero sums. | `map::find`, `erase`, `emplace` | Trig multiplication, differentiation, unfolding |
| `add(LinComMap)` / `sub(LinComMap)` | Combine two equations. | term loop | Determinants and curve formulas |
| `scale` | Scale all coefficients, or clear for zero scale. | loop | Division and formula generation |
| `content` | GCD of coefficients. | `gcd` | curve normalization |
| `divide_content` | Divide by GCD if nonzero. | `content` | Unfolding curve generation |
| `size` | Number of nonzero terms. | `map::size` | Heuristics and division |
| `is_zero` | Empty map test. | `map::empty` | Refinement and division edge cases |
| `coeff(T)` | Returns stored coefficient or zero. | `map::find` | Tests and simplification |
| `operator==` / `operator<` | Value comparison and deterministic ordering. | map comparison | `std::set` and DB order |
| `operator<<` | Pretty-prints symbolic equation. | term loop | Logs/UI formatting |

Performance note: `std::map` gives deterministic ordering but can be slow for very large equations. `unordered_map` is mentioned as a possible future optimization, but it would need deterministic serialization if used in DB-related paths.

### `src/backend/headers/math/trig.hpp`

`Sin<T>` and `Cos<T>` are small typed wrappers around an argument. They exist so `LinComMap<Sin<Arg>,Coeff>` and `LinComMap<Cos<Arg>,Coeff>` are distinct compile-time types.

Function-level notes:

| Function | Purpose |
| --- | --- |
| `Sin(T)` / `Cos(T)` | Store trig argument |
| `operator==` | Compare by argument |
| `operator<` | Order by argument for `std::map` |
| `operator<<` | Print as `sin(arg)` or `cos(arg)` |

### `src/backend/headers/math/polynomial.hpp`

`math::Polynomial<E,N>` is a sparse polynomial map from `Monomial<E,N>` to coefficient. It is used mainly in `division.cpp` to factor trigonometric equations by converting sin/cos sums into polynomial/Laurent-like expressions in `s` and `t`.

Function-level notes:

| Function | Purpose | Called by |
| --- | --- | --- |
| Initializer-list constructor | Build explicit polynomial | Tests and division helpers |
| `begin/end` | Iterate largest-to-smallest because division wants leading terms first | `division.cpp` |
| `rbegin/rend` | Iterate smallest-to-largest | `to_sin`, `to_cos` |
| `leading_term` | Return first term if nonzero | `poly_divide` |
| `add/sub(Polynomial)` | Combine polynomials | Division routines |
| `add/sub(Monomial, coeff)` | Mutate one term, erasing zero coefficients | All polynomial division |
| `scale` | Multiply polynomial by scalar or clear on zero | Division sign handling |
| `is_zero` | Empty polynomial test | Division loops |
| `size` | Term count | Partial division heuristics |
| `coeff` | Lookup coefficient or zero | Tests |
| `operator==` / `operator<` | Deterministic value comparison | Containers/tests |
| `operator<<` | Human-readable polynomial output | Diagnostics |

## General Backend Types

### `src/backend/headers/general.hpp`

This header pulls together geometry, symbolic math, and numeric types. It defines commonly used aliases:

| Alias | Meaning |
| --- | --- |
| `PointQ` | Exact rational 2D point |
| `OpenSegmentQ` / `ClosedSegmentQ` | Rational segments with topology marker |
| `OpenRectangleQ` / `ClosedRectangleQ` | Rational rectangles |
| `OpenConvexPolygonQ` / `ClosedConvexPolygonQ` | Rational polygons |
| `LinComArrZ<T>` | Fixed linear combination with `Coeff64` |
| `LinComArrQ<T>` | Fixed linear combination with `Rational` |
| `LinComMapZ<T>` | Sparse symbolic sum with `Coeff64` |
| `Equation<Sin/Cos>` | Trigonometric equation in `x,y` |
| `Curves` | Pair of sine-equation set and cosine-equation set |
| `CurvesLR` | Pair of equation-to-left/right maps |
| `Vector2<T>` | Eigen 2-vector without alignment |
| `Matrix2<T>` | Eigen 2x2 matrix without alignment |

Important classes/enums:

| Name | Purpose |
| --- | --- |
| `InitialAngles` | Records which two `XYZ` labels were chosen as initial `x,y` axes |
| `Sign` | Three-valued sign enum: negative, zero, positive |
| `Order` | Interval comparison result: less/equal/greater |

Function-level notes:

| Function | Purpose | Calls | Called by |
| --- | --- | --- | --- |
| `operator<<(InitialAngles)` | Prints two angle labels such as `xy`. | `XYZ` printer | DB serialization/logging |
| `operator==` / `operator<` for `InitialAngles` | Value equality/order. | `std::tie` | Maps/sets |
| `operator<<(Sign)` | Prints sign names. | `invalid_enum_value` | Diagnostics |
| `sign(Interval)` | Rigorous sign classification of an MPFI interval. | `lower`, `upper`, `zero_in` | Refinement and gradient code |
| `compare_interval` | Conservative interval ordering. | MPFI bounds/overlap | Geometry and sorting contexts |
| `decimal_to_rational` | Exact decimal string to rational. | local `parse_int` in `general.cpp` | Parsers |
| `parse_rational` | Parse rational syntax. | implementation elsewhere | Parsers |
| `gradient_bounds` | Bounds derivative size of trig equation. | `abs`, term loop | Positivity/cover |
| `subdivide(ClosedSegmentQ)` | Split segment in half. | segment midpoint | Cover recursion |
| `subdivide(Rectangle)` | Split rectangle into four quarters. | rectangle center | Cover recursion |

### `src/backend/cpp/general.cpp`

Function-level notes:

| Function | Purpose | Calls | Called by |
| --- | --- | --- | --- |
| Template instantiations | Force common geometry/symbolic types into one translation unit. | compiler feature | Link-time compile-size optimization |
| `parse_int` | Safe signed 64-bit decimal parser with overflow checks. | `numeric_limits` | `decimal_to_rational` |
| `decimal_to_rational` | Converts decimal string exactly to `Rational`, avoiding binary float rounding. | `parse_int` | parse helpers |
| `gradient_bounds` | Returns separate x and y coefficient-weighted derivative bounds. | term loop | cover/refine positivity |
| `subdivide(ClosedSegmentQ)` | Returns two closed half-segments. | `midpoint` | cover |

## Code Type

### `src/backend/headers/code_type.hpp`

`CodeType` encodes the five mathematical classes. The file is intentionally tiny but central.

Function-level notes:

| Function | Purpose | Called by |
| --- | --- | --- |
| `operator<<(CodeType)` | Converts enum to DB/UI strings: `OSO`, `OSNO`, `ONS`, `CS`, `CNS`. | serialization, logs, UI |
| `is_stable(CodeType)` | True for `OSO`, `OSNO`, `CS`; false for `ONS`, `CNS`. | database save paths, cover loading |

## C++ `CodeSequence`

### `src/backend/headers/code_sequence.hpp`

`CodeSequence` is a validated/canonicalized wrapper around `std::vector<CodeNumber>`. The important design intent is in the comments: the type proves "this sequence has already been validated and put in standard form."

Public fields:

- `code_numbers`: currently public, despite comments emphasizing immutability. This weakens the invariant because callers can mutate a valid sequence after construction.

Public methods are implemented in `code_sequence.cpp`.

### `src/backend/cpp/code_sequence.cpp`

Function-level notes:

| Function | Purpose | Calls | Called by |
| --- | --- | --- | --- |
| `is_repeated` | Tests whether the whole sequence repeats the prefix of length `sub_length`. | vector indexing | `smallest_index` |
| `next_angle` | Applies parity transition rule to angle labels. | `other_angle` | legality, angles, constraints |
| `is_legal(vector,end)` | Walks angle labels for first `end` entries and requires final pair `(X,Y)`. | `next_angle` | validation and repeater detection |
| `smallest_index` | Finds shortest legal repeated prefix. | `is_repeated`, `is_legal` | constructor |
| `minimal_rotation` | Chooses lexicographically minimal rotation among rotations and reversed rotations. | `std::rotate`, `std::reverse` | constructor |
| `validate` | Throws if sequence is empty, non-positive, or illegal. | `is_legal` | constructor |
| `CodeSequence::CodeSequence` | Validates, removes repeated legal period, canonicalizes rotation/reflection. | `validate`, `smallest_index`, `minimal_rotation` | all backend code sequence construction |
| `begin/end` | Const iteration through stored numbers. | vector begin/end | printing, algorithms |
| `is_palindrome` | Tests reverse symmetry around a candidate closed pair. | vector indexing | `closed_index` |
| `closed_index` | Returns index of first even entry that forms the closed-code symmetry, or none. | `is_odd`, `is_palindrome` | `is_closed`, shooting vector |
| `constraint` | Builds alternating sum constraint in `XYEta`; zero means stable. | `next_angle`, `xyz_to_xyeta`, `divide_content`, `divide_unit` | `is_stable`, unstable MRR |
| `create` | Exception-safe factory returning `InvalidCodeSequence` or `CodeSequence`. | constructor | C++ Java-port code |
| `isLegal` | Public Java-port legality check on `int32_t`. | `next_angle` | `ClassifiedCodeSequence` C++ port |
| `rotateLeft` | Java-port mutable left rotation. | manual loop | C++ classified port |
| `subList` | Java-port subvector helper. | loop | C++ classified port |
| `angles` | Returns the `XYZ` angle label for each code number. | `other_angle` | equations, unfolding, DB |
| `length` | Number of entries. | vector size | sorting/type info |
| `sum` | Sum of entries. | loop | display/cover cost |
| `is_odd` | Checks odd length. | vector size | type and closed logic |
| `is_closed` | True if `closed_index` exists. | `closed_index` | type |
| `is_stable` | True if `constraint(X,Y)` is zero. | `constraint` | type |
| `type` | Computes `CodeType` from odd/closed/stable booleans. | `is_odd`, `is_closed`, `is_stable` | save/load/classification |
| `number` | Bounds-checked element access. | vector `at` | compare |
| `numbers` | Const reference to stored vector. | none | almost every backend algorithm |
| `toString` | Space-separated code string. | loop | wrappers/debug |
| `equals` | Value equality against mutable reference. | vector equality | C++ classified port |
| `compare` | Total order by length then lexicographic elements. | `length`, `number` | operators, maps |
| `operator==` | Equality via `compare`. | `compare` | containers |
| `operator<` | Strict order via `compare`. | `compare` | maps/sets/DB |
| `operator<<` | Space-separated stream output. | iteration | logs/DB serialization |

Mathematical purpose of `constraint`:

For an even code, stability is determined by the alternating signed sum:

```text
c0*theta0 - c1*theta1 + c2*theta2 - c3*theta3 + ...
```

where each `theta_i` is one of `X,Y,Z`, evolved by parity. Substituting `Z = 2*eta - X - Y` gives a linear expression in `x,y,eta`. If this expression is zero after normalization, the code is stable over a two-dimensional region. If nonzero, the code lies on the line where this constraint vanishes.

Correctness risk:

`CodeSequence::create` checks `msg.find("non-positive")` and `msg.find("illegal")` as booleans instead of comparing to `std::string::npos`. Since `npos` converts to true, this can misclassify errors. See `05-risks-optimizations.md`.

## C++ `ClassifiedCodeSequence`

### `src/backend/headers/classified_code_sequence.hpp`

This is a C++ port of Java classification logic. It owns a `shared_ptr<CodeSequence>` and caches:

- `codeLength`
- `codeSum`
- `codeType`
- `stable`
- `oddEvenPattern`

It overlaps heavily with `CodeSequence::type()` and exists mainly for compatibility with Java-side expectations.

### `src/backend/cpp/classified_code_sequence.cpp`

Function-level notes:

| Function | Purpose | Calls | Called by |
| --- | --- | --- | --- |
| `create` | Builds `CodeSequence`, wraps success into `ClassifiedCodeSequence`, forwards invalid result. | `CodeSequence::create` | C++ vary/type-filter code |
| `calculateOddEvenPattern` | Converts each number to `O` or `E`. | modulo | constructor |
| `calculateCodeType` | Recomputes type from odd/closed/stable. | `isOdd`, `isClosed`, `isStable` | constructor |
| `calculateCodeSum` | Sum code entries as `long`. | loop | constructor |
| `isStableCodeType` | Stable type predicate. | switch | constructor |
| `isOdd` | Odd length test. | vector size | `calculateCodeType` |
| `isClosed` | Java-style closed symmetry test. | `CodeSequence::subList`, `std::reverse` | `calculateCodeType` |
| `isStable` | Java-style alternating sum stability test over `XYZ`. | `otherAngle` from C++ Java-port header | `calculateCodeType` |
| `length` | Stored sequence length. | vector size | callers |
| `toString` | Delegates to `CodeSequence`. | `toString` | logs/UI |
| `operator==` | Sequence equality. | `CodeSequence::operator==` | containers |
| `equals` | Sequence equality by value. | `CodeSequence::equals` | Java-port users |
| `operator<` | Sequence ordering. | `CodeSequence::operator<` | containers |
| `compareTo` | Java-style compare. | free `compare` | callers |

Design note: classification logic is duplicated between `CodeSequence` and `ClassifiedCodeSequence`. This increases the chance of Java/C++ and C++/C++ drift. Prefer one canonical implementation long term.

## Common Cover Data Structures

### `src/backend/headers/common.hpp`

This header is both core type glue and cover-verification support.

Function-level notes:

| Function/Type | Purpose | Calls | Called by |
| --- | --- | --- | --- |
| `gradient_bound` | One-number derivative bound: sum of `abs(coeff)*(abs(x_coeff)+abs(y_coeff))`. | `math::abs` | `StableInfo`, `UnstableInfo` |
| `map_to_vec` | Converts sparse equation map to compact `EqVec` with `Coeff16` args/coeffs. | `numeric_cast` | info structs |
| `Triple` | Holds stable-negative, unstable, stable-positive code sequences. | comparisons/printing | cover triple loading |
| `HalfTriple` | Holds stable-negative plus unstable. | comparisons/printing | half-triple cover code |
| `CodeInfo` | Raw points plus sine/cosine equations. | constructors | database and cover conversion |
| `StableInfo` | Converts stable `CodeInfo` to polygon plus compact equation vectors and gradient bounds. | `gradient_bound`, `map_to_vec` | cover checking |
| `UnstableInfo` | Converts unstable `CodeInfo` to segment plus compact equations/bounds. | `gradient_bound`, `map_to_vec` | cover checking |
| `TripleInfo` | Groups two stable infos and one unstable info. | constructors | cover checking |
| `HalfTripleInfo` | Groups stable-negative and unstable info. | constructors | half-triple cover checking |
| `remove_factor*` declarations | Remove shared factors from stable/unstable equations. | implemented in `common.cpp` | database triple-info loading |
| `digits_to_bits` | Converts decimal precision target to bit precision. | implemented in `common.cpp` | cover |
| `is_positive` overloads | Prove positivity of stable/triple/half-triple over a rectangle. | implemented in `common.cpp` | cover recursion |
| `get_cost` | Estimate cost of a stable info. | implemented in `common.cpp` | stable cover sorting |
| `CoverAll` | Visitor that recursively verifies loaded cover nodes. | `cover_square`, visitors | `cover_square_all` |
| `CoverInfo` | Accumulates unfilled rectangles and square counts. | maps/vectors | `cover_to_info` |
| `CoverVisitor` | Walks cover tree and fills `CoverInfo`. | `subdivide`, geometry intersects | `cover_to_info` |
| `cover_to_info` | Summarizes a cover tree. | `CoverVisitor` | cover output |
| `center_degrees` | Formats rectangle center in degrees. | implemented in `common.cpp` | cover logging |
| `get_index_info` | Converts count maps into deterministic index maps. | map iteration | cover saving |
| `cover_square_all` | End-to-end load MRR cover, verify all cover nodes, write output cover and info. | cover load/save, `CoverAll`, `cover_to_info` | `check_cover_all` path |

`cover_square_all` deserves special attention. It is a template because the caller supplies a loader that populates `stable_infos` and `triple_infos`. Its main stages are:

1. Open `info.txt` in output directory.
2. Load polygon, square, code lists, cover tree, and precision digits from MRR directory.
3. Convert digits to bit precision.
4. Load equation info from database.
5. Apply `CoverAll` visitor to the MRR cover tree.
6. Convert cover tree to `CoverInfo`.
7. Print stable/triple usage counts and sample holes.
8. Save polygon, square, singles, triples, cover tree, and digits to output directory.
9. Return whether the region is fully covered.
