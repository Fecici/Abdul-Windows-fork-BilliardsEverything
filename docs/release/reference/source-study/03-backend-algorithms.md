# Backend Algorithms

This file documents the C++ algorithmic layer: trig simplification, division/factoring, unfolding, shooting vectors, bounding regions, MRR refinement, database persistence, native wrappers, and vary searches.

Current baseline note: Abdul's backend follows this same algorithm map, but `bounding_inequalities.cpp::eliminate_phi` differs materially from both the older source tree and `[MAIN]`. Abdul buffers `no_phi` results in vectors with periodic `sort/unique`; `[MAIN]` uses source-style per-thread `std::set` insertion. See `17-source-vs-abdul-fork-line-diff.md` and `19-main-backend-dll-vs-abdul-source.md`.

## Trigonometric Identities

### Files

- `src/backend/headers/trig_identities.hpp`
- `src/backend/cpp/trig_identities.cpp`

These functions normalize symbolic sine/cosine equations so equivalent expressions compare equal and so product formulas can expand into canonical sums.

Function-level notes:

| Function | Purpose | Calls | Called by |
| --- | --- | --- | --- |
| `signum` | Returns -1, 0, or 1 for signed integer. | comparisons | simplifiers |
| `simplify_sin_xy` | Applies `sin(-u) = -sin(u)` and makes leading coefficient nonnegative. | `signum`, `scale` | multiplication, conversion |
| `simplify_cos_xy` | Applies `cos(-u) = cos(u)` and makes leading coefficient nonnegative. | `signum`, `scale` | multiplication, conversion |
| `simplify_sin_xypi` | Simplifies `sin(a*x+b*y+n*pi)` to signed `sin(a*x+b*y)`. | parity of pi coefficient | unfolding path vectors, shooting vector |
| `simplify_cos_xypi` | Simplifies `cos(a*x+b*y+n*pi)` to signed `cos(a*x+b*y)`. | parity of pi coefficient | unfolding path vectors, shooting vector |
| `simplify_sin_xyeta` | Simplifies `sin(u+n*pi/2)` with odd `n` to signed cosine. | `simplify_cos_xy` | closed shooting vector |
| `simplify_cos_xyeta` | Simplifies `cos(u+n*pi/2)` with odd `n` to signed sine. | `simplify_sin_xy` | closed shooting vector |
| `multiply_lin_com(cos,sin)` | Expands `cos(a)sin(b)` using product-to-sum. | `simplify_sin_xy` | curve determinant generation |
| `multiply_lin_com(sin,cos)` | Same as above with operands reversed. | `simplify_sin_xy` | curve determinant generation |
| `multiply_lin_com(sin,sin)` | Expands `sin(a)sin(b)` to cosines. | `simplify_cos_xy` | curve determinant and derivative formulas |
| `multiply_lin_com(cos,cos)` | Expands `cos(a)cos(b)` to cosines. | `simplify_cos_xy` | formula and derivative helpers |
| `get_final_result_formula` | Computes `-A + 2B - C` for generated numerator formulas. | `add/sub` | gradient UI formulas |
| `simplify_sin_pi2` | Evaluates `sin(n*pi/2)` for integer `n`. | parity | corner evaluations |
| `simplify_cos_pi2` | Evaluates `cos(n*pi/2)` for integer `n`. | parity | corner evaluations |
| `simplify_lin_com_zero_zero` | Evaluates equation at `(0,0)`. | term loop | bounding/refinement tests |
| `simplify_lin_com_zero_pi2` | Evaluates equation at `(0,pi/2)`. | `simplify_*_pi2` | bounding/refinement tests |
| `simplify_lin_com_pi2_zero` | Evaluates equation at `(pi/2,0)`. | `simplify_*_pi2` | bounding/refinement tests |
| `simplify_lin_com_pi2_pi2` | Evaluates equation at `(pi/2,pi/2)`. | `simplify_*_pi2` | bounding/refinement tests |

Mathematical role: These routines keep trig expressions in a canonical algebraic form. Without them, the same curve could appear in different signs or shifted forms and would not deduplicate in `std::set`.

## Differentiation

### Files

- `src/backend/headers/diff.hpp`
- `src/backend/cpp/diff.cpp`

Function-level notes:

| Function/Type | Purpose | Calls | Called by |
| --- | --- | --- | --- |
| `diff<XY::X/Y>(LinComArrZ<XYEta>)` | Derivative of a linear boundary line with respect to x or y. | coefficient lookup | `EquationGradient` |
| `diff<XY::X/Y>(Equation<Sin>)` | Symbolic derivative of sine equation to cosine equation. | term loop | gradients/intersections/formula UI |
| `diff<XY::X/Y>(Equation<Cos>)` | Symbolic derivative of cosine equation to sine equation with negative sign. | term loop | gradients/intersections/formula UI |
| Rational `diff` overloads for `XEta` and `YEta` | One-variable rational derivative helpers. | coefficient lookup | linear derivative/intersection code |
| `EquationGradient<XY,Equation>` | Stores an equation and its x/y derivatives together. | `diff` | boundary equations and refinement |

`EquationGradient` is central in refinement because every active boundary curve must carry both the curve and its gradient for Newton/intersection operations.

## Polynomial Division And Factoring

### Files

- `src/backend/headers/division.hpp`
- `src/backend/cpp/division.cpp`

The division module factors obvious line factors out of trigonometric equations. It converts sin/cos sums into polynomial-like expressions in variables `s` and `t`, where terms represent exponentials of `x` and `y`.

Useful identities:

```text
sin(bx+cy) ~ s^b t^c - s^-b t^-c
cos(bx+cy) ~ s^b t^c + s^-b t^-c
```

The code shifts by a denominator monomial so negative exponents become nonnegative. It then divides by variants of `x^2 - 1`, corresponding to line factors.

Function-level notes:

| Function/Type | Purpose | Calls | Called by |
| --- | --- | --- | --- |
| `max_coeffs` | Finds maximum absolute x/y argument coefficients. | term loop | `to_poly` |
| `to_poly(Sin)` | Converts sine equation into polynomial numerator plus denominator shift. | `max_coeffs`, `Polynomial::add/sub` | division |
| `to_poly(Cos)` | Converts cosine equation into polynomial numerator plus denominator shift. | `max_coeffs`, `Polynomial::add` | division |
| `first_term_divides<Sym>` | Finds first term divisible by `s^2` or `t^2`. | monomial exponent | `divide_x2m1_*` |
| `first_term_divides<Sym0,Sym1>` | Finds first term divisible by `(st)^2`. | monomial exponent | `divide_x2m1_*` |
| `divide_x2m1_full` | Repeated exact division by a line factor until zero remainder. | `first_term_divides` | older/strict factoring |
| `divide_x2m1_partial` | Heuristic bounded division; returns none if quotient grows too much or factor not clean. | `first_term_divides` | active factoring |
| `to_sin` | Converts polynomial quotient back to sine equation. | monomial pairing | division outputs |
| `to_cos` | Converts polynomial quotient back to cosine equation. | monomial pairing | division outputs |
| `divide_out_lines_general_lr` | Factors equation and records left/right origin for resulting curve. | `to_poly`, partial divisions, `to_sin/to_cos` | `generate_curves_lr` |
| `divide_out_lines_lr(Sin/Cos)` | Handles zero/single-term special cases then factors. | `divide_out_lines_general_lr` | unfolding LR generation |
| `Division` class | Stateful factorer for prover paths that respects initial angle order. | `divide_angle`, `to_sin/to_cos` | `divide_out_lines` |
| `divide_out_lines(Sin/Cos, Curves)` | Factors and inserts curves into sets. | `Division` | `Unfolding::generate_curves` |
| `divide_out_lines(Sin/Cos, Inserter)` | Same but filters with positivity before insertion. | `Division`, `Inserter` | optimized curve generation |
| `term_reducible_by` | Finds polynomial term reducible by divisor leading term. | monomial divides, coeff divisibility | `poly_divide` |
| `poly_divide` | Multivariate polynomial long division requiring zero remainder. | `leading_term`, `term_reducible_by` | `divide_generic` |
| `divide_generic` | Divides one trig equation by another after polynomial conversion. | `to_poly`, `poly_divide` | `divide_once` overloads |
| `divide_once` overloads | Divide a sine/cos equation by one sine/cos factor once. | `divide_generic`, `to_sin/to_cos` | factor-removal in `common.cpp` |

Optimization note: comments say factor order affects equation length. A future improvement is to choose the shortest result among possible factor orders instead of using fixed order.

## Unfolding

### Files

- `src/backend/headers/unfolding.hpp`
- `src/backend/cpp/unfolding.cpp`

`Unfolding` constructs a graph of reflected triangle vertices and directed edges. A directed edge records:

- `edge_type`: which original triangle side/angle label the edge corresponds to.
- `polar_angle`: the edge's symbolic polar angle as a linear expression in `x,y,pi`.

Function-level notes:

| Function/Type | Purpose | Calls | Called by |
| --- | --- | --- | --- |
| `Edge` | Directed edge metadata in unfolded graph. | comparison/printing | `Unfolding` |
| `find_path` | Builds a path through main chain and optional branches between two vertices. | loops over vertex numbers | `path_vector`, curve generation |
| `double_if_odd` | Duplicates vectors of odd length so unfolding can close evenly. | vector insert | `Unfolding` constructor |
| `Unfolding::Unfolding` | Builds all directed edges and left/right vertex lists from code numbers and angle labels. | `other_angle`, `xyz_to_xypi` | equation generation |
| `path_vector` | Converts a vertex path into symbolic x/y displacement equations. | trig simplifiers | shooting vector general, curve generation |
| `shooting_vector_general` | Chooses the shorter of first-left-to-last-left and first-right-to-last-right path vectors. | `find_path`, `path_vector` | OSNO/ONS cases |
| `get_all_vectors` | Parallel enumeration of all left/right path vectors. | thread pool, `find_path`, `path_vector` | `calculate_all_vector` |
| `generate_curves` | Parallel generation of all determinant curves as `Curves`. | `path_vector`, `multiply_lin_com`, `divide_out_lines` | all-equation DB generation |
| `generate_curves` with center/radii | Same, but filters using `Inserter` positivity checks. | `Inserter` | prover/cover paths |
| `generate_curves_lr` | Generates curves mapped to their left/right witnesses. | `divide_out_lines_lr` | MRR stable/unstable calculation |
| `generate_curves_lr(..., left_rights)` | Regenerates only specified left/right paths. | `find_path`, `path_vector` | show/use left-right workflows |

Core equation generated for each left/right path:

```text
path_x * shoot_y - path_y * shoot_x
```

This is zero when the path vector and shooting vector are parallel, so the chosen shooting direction can realize that left/right combinatorial path.

Correctness risks:

- Some overloads do not handle `std::thread::hardware_concurrency() == 0`.
- `generate_curves_lr(..., left_rights)` merges per-thread maps with `insert`, which drops duplicate keys rather than appending their `LeftRight` vectors.
- There are unconditional debug prints `std::cout << "comb"` in two curve-generation paths.

## Shooting Vectors

### Files

- `src/backend/headers/shooting_vectors.hpp`
- `src/backend/cpp/shooting_vectors.cpp`

Function-level notes:

| Function | Purpose | Calls | Called by |
| --- | --- | --- | --- |
| `shooting_angle_odd` | Computes the shooting angle for odd/open stable codes from alternating code-angle sum. | `xyz_to_xypi`, coefficient parity checks | `shooting_vector_open` |
| `shooting_angle_closed` | Computes perpendicular closed-code shooting angle using the closed index. | `closed_index`, `xyz_to_xyeta` | `shooting_vector_closed` |
| `shooting_vector_open` | Converts odd/open shooting angle to `(cos(angle), sin(angle))`. | `simplify_cos_xypi`, `simplify_sin_xypi` | `calculate_stable(OSO)` |
| `shooting_vector_closed` | Converts closed shooting angle to `(sin(...), cos(...))` after eta shift. | `simplify_cos_xyeta`, `simplify_sin_xyeta` | `calculate_stable(CS)`, `calculate_unstable(CNS)` |

Mathematical role:

- Open odd stable paths have a direct shooting angle formula from symmetry.
- Closed paths have a perpendicular reflection point; `closed_index` identifies the even number where that perpendicular condition applies.
- General stable/unstable non-odd cases use `Unfolding::shooting_vector_general`.

## Bounding Inequalities And Regions

### Files

- `src/backend/headers/bounding_inequalities.hpp`
- `src/backend/cpp/bounding_inequalities.cpp`
- `src/backend/headers/bounding_region.hpp`
- `src/backend/cpp/bounding_region.cpp`

`bounding_inequalities.cpp` computes linear inequalities that define the initial rational region before nonlinear curve refinement. It uses a temporary `phi` variable for shooting angle and then eliminates it.

Main functions in `bounding_inequalities.cpp`:

| Function | Purpose |
| --- | --- |
| `calculate_angles` | Pairs code numbers with angle labels in linear forms. |
| `calculate_even_equations` | Builds inequality candidates for even code numbers. |
| `calculate_odd_equations` | Builds inequality candidates for odd code numbers. |
| `remove_phi` | Drops auxiliary `phi` when coefficient is zero. |
| `eliminate_phi` | Combines positive/negative phi inequalities to eliminate `phi`. |
| `first_inequalities` | Initializes inequality set. |
| `calculate_bounding_inequalities` | Public entry returning `XYEta` inequalities. |

`bounding_region.cpp` applies those inequalities to exact rational shapes.

Function-level notes for `bounding_region.cpp`:

| Function/Type | Purpose | Calls | Called by |
| --- | --- | --- | --- |
| `intersection` | Exact intersection of two `XYEta` lines by Cramer's rule. | rational arithmetic | segment/polygon clipping |
| `line_sign_at_point` | Exact sign of line at rational point. | `Rational::sign` | all rational clipping |
| `initial_line_segment` | Clips unstable constraint line against base triangle. | `line_sign_at_point`, `intersection` | `calculate_bounding_line_segment` |
| `refine_line_segment` | Clips rational unstable segment by one linear inequality. | signs, `intersection` | `calculate_bounding_line_segment` |
| `calculate_bounding_line_segment` | Builds unstable rational segment from all inequalities. | `calculate_bounding_inequalities`, clipping | unstable MRR |
| `refine_polygon` | Clips rational polygon by one linear inequality. | signs, `intersection` | `calculate_bounding_polygon` |
| `calculate_bounding_polygon` | Builds stable rational polygon inside base angle triangle. | inequalities, `refine_polygon` | stable MRR |
| `RationalLineSegment` | Segment endpoints and boundary lines. | constructor | unstable bounding |
| `RationalPair` | Polygon point and outgoing side line. | constructor | stable bounding |
| `RationalPolygon` | Vector of `RationalPair`. | vector | stable bounding |

The many `CASE1`/`CASE2` branches are explicit sign cases for robust exact clipping. This is verbose but mathematically straightforward half-plane intersection.

## MRR Refinement

### Files

- `src/backend/headers/refine.hpp`
- `src/backend/cpp/refine.cpp`

The refinement layer takes rational bounding shapes and cuts them by nonlinear sine/cosine curves. It uses interval arithmetic to classify signs and Newton-style intersection routines to insert new vertices where curves cross boundaries.

Core types:

| Type | Purpose |
| --- | --- |
| `BoundaryEquation` | Variant of linear boundary, sine curve, or cosine curve, all with gradients |
| `IntervalLineSegment` | Two endpoints plus boundary equations at endpoints |
| `IntervalPair` | One polygon vertex plus outgoing boundary equation |
| `IntervalPolygon` | Vector of `IntervalPair` |
| `EquationPrinter` | Visitor returning string form of a boundary equation |
| `ZeroInfo` | Extra sign information around a zero corner |
| `Corner` | Corner sign plus optional `ZeroInfo` |

Function-level notes:

| Function | Purpose | Calls | Called by |
| --- | --- | --- | --- |
| `median` | Converts interval point to midpoint real vector. | MPFI median | Newton seeds |
| `print_region` overloads | Debug-print polygon or segment with boundary equations. | `EquationPrinter` | diagnostics |
| `refine_line_segment` | Clips an interval segment by a nonlinear curve. | `curve_sign_at_point`, `linear_derivative_sign`, `intersection`, `intersection_zero` | unstable final MRR |
| `correct_zeros` | Repairs ambiguous zero-corner derivative signs using neighboring nonzero signs. | mutates `Corner` list | `calculate_corners` |
| `calculate_corners` | Sign-classifies each polygon corner and computes tangent-side signs at zeroes. | `curve_sign_at_point`, gradients, `sign` | `refine_polygon` |
| `refine_polygon` | Clips interval polygon by nonlinear curve, handling many sign/zero cases. | `calculate_corners`, intersection visitors | stable final MRR |

Mathematical role:

- A positive side of each curve is kept.
- If both endpoints are negative, that boundary part is discarded.
- If signs differ, an intersection point is inserted.
- If an endpoint is zero, gradient and linear-derivative tests decide whether the curve crosses, touches, or runs tangent to the boundary.

The comments in this file preserve many hard examples. They are important: they explain why simple sign tests fail at zero-gradient or self-intersection cases.

## Equation Calculation

### Files

- `src/backend/headers/equations.hpp`
- `src/backend/cpp/equations.cpp`

These are the public MRR calculation entry points.

Function-level notes:

| Function/Type | Purpose | Calls | Called by |
| --- | --- | --- | --- |
| `LeftRightVariant` | Maps a boundary equation back to its first left/right witness. | `boost::apply_visitor` | `stable_left_right`, `unstable_left_right` |
| `stable_left_right` | Extracts left/right references for polygon boundary curves. | `LeftRightVariant` | `points_and_stuff_stable` |
| `unstable_left_right` | Extracts two left/right references for segment endpoints. | `LeftRightVariant` | `points_and_stuff_unstable` |
| `stable_equations_to_string` | Reorders stable equations into chosen initial-angle coordinates and serializes. | `RearrangeVariant` | `points_and_stuff_stable` |
| `unstable_equations_to_string` | Same for two unstable boundary equations. | `RearrangeVariant` | `points_and_stuff_unstable` |
| `point_to_vector` | Converts rational `eta`-scaled point to interval radian vector. | multiply by half pi | conversion helpers |
| `convert_to_interval(RationalLineSegment)` | Converts exact unstable segment to interval segment with line gradients. | `point_to_vector`, `EquationGradient` | final segment calculation |
| `convert_to_interval(RationalPolygon)` | Converts exact stable polygon to interval polygon with line gradients. | `point_to_vector`, `EquationGradient` | final polygon calculation |
| `calculate_final_polygon` | Builds rational polygon then refines it by all sine and cosine curves. | `calculate_bounding_polygon`, `refine_polygon` | stable calculation |
| `calculate_final_line_segment` | Builds rational segment then refines it by all sine and cosine curves. | `calculate_bounding_line_segment`, `refine_line_segment` | unstable calculation |
| `convex_counterexample_checker` | Detects possible non-convex zero-crossing/parallel-gradient problems. | gradients, interval signs | stable final validation |
| `points_and_stuff_stable` | Converts final stable polygon into DB-ready `Stable`. | `calculate_final_polygon`, point permutation, equation strings, left-rights | `calculate_stable` |
| `points_and_stuff_unstable` | Converts final unstable segment into DB-ready `Unstable`. | `calculate_final_line_segment`, permutation, equation strings, left-rights | `calculate_unstable` |
| `calculate_stable` | Main stable-code MRR function. | shooting vector selection, `Unfolding::generate_curves_lr`, `points_and_stuff_stable` | wrapper/database save |
| `calculate_unstable` | Main unstable-code MRR function. | constraint, shooting vector selection, `Unfolding::generate_curves_lr`, `points_and_stuff_unstable` | wrapper/database save |
| `calculate_stable(... left_rights)` | Recomputes stable MRR using given left/right witnesses. | left/right overload | show/use LR flows |
| `calculate_unstable(... left_rights)` | Recomputes unstable MRR using given left/right witnesses. | left/right overload | show/use LR flows |

Stable path behavior:

- `OSO`: use `shooting_vector_open`.
- `CS`: use `shooting_vector_closed`.
- `OSNO`: use `Unfolding::shooting_vector_general`.

Unstable path behavior:

- `CNS`: use `shooting_vector_closed`.
- `ONS`: use `Unfolding::shooting_vector_general`.

## Database Algorithm Layer

### Files

- `src/backend/headers/database.hpp`
- `src/backend/cpp/database.cpp`
- `src/backend/headers/database/viewer.hpp`
- `src/backend/cpp/database/viewer.cpp`
- `src/backend/headers/database/serialize.hpp`
- `src/backend/cpp/database/serialize.cpp`
- `src/backend/headers/database/deserialize.hpp`
- `src/backend/cpp/database/deserialize.cpp`
- `src/backend/headers/database/admin.hpp`
- `src/backend/cpp/database/admin.cpp`

The database layer serializes code sequences, points, equations, and left/right data into SQLite tables named after code types.

Important public types in `database/viewer.hpp`:

| Type | Purpose |
| --- | --- |
| `Picture` | Minimal Java display payload: initial angles, points, equations |
| `Info` | Full Java payload: initial angles, points, equations, left/right data, code-seq LR |
| `StableRef` | Stable boundary equation reference used to canonicalize stable order by left/right |
| `Stable` | DB-ready stable object with ordered points/equations/left-rights |
| `Unstable` | DB-ready unstable object with two ordered endpoints/equations/left-rights |

Function-level notes for `database.cpp`:

| Function | Purpose |
| --- | --- |
| `parse_mrr_equations` | Parses newline-separated DB equation strings into sine/cosine sets. |
| `parse_mrr_polygon` | Parses stored radian points into exact rational eta-scaled points. |
| `bounding_line_segment` | Recomputes unstable exact bounding segment from code and initial angles. |
| `bounding_polygon` | Recomputes stable exact bounding polygon from code and initial angles. |
| `load_stable_mrr_from_database` | Loads stable initial angles, points, equations for MRR cover use. |
| `load_unstable_mrr_from_database` | Loads unstable initial angles and equations, recomputes exact segment. |
| `load_all_from_database` | Loads all-equation polygon/sin/cos data. |
| `calculate_stable_all_info` | Generates full stable equation set and rational bounding polygon. |
| `calculate_unstable_all_info` | Generates full unstable equation set and rational segment. |
| `calculate_all_info` | Generates all left/right curves for either stable or unstable code. |
| `calculate_all_vector` | Generates all path vectors from an unfolding. |
| `save_to_database(CodeInfo)` | Updates all-equation columns. |
| `ensure_stable_all_in_database` | Lazily computes stable all-equation data if missing. |
| `ensure_unstable_all_in_database` | Lazily computes unstable all-equation data if missing. |
| `get_stable_info` | Returns either MRR or all stable info for cover calculations. |
| `get_unstable_info` | Returns either MRR or all unstable info for cover calculations. |
| `get_single_info(s)` | Converts stable code(s) into `StableInfo`, sorted by cost. |
| `get_triple_info(s)` | Loads stable/unstable/stable triple info and removes shared unstable factor. |
| `get_triple_infos_duplicate_stables` | Duplicate-stable factor-removal check path. |
| `get_triple_infos_half_duplicate_stables` | Half-triple duplicate-stable check path. |
| `get_single_infos_map` | Loads stable info preserving explicit initial-angle pairs. |
| `get_triple_infos_map` | Loads triple info preserving explicit triple pairs. |

Design note: MRR unstable point data is not trusted directly because of rounding, so exact bounding segments are recomputed from the code and stored initial angles.

## Native Wrapper

### Files

- `src/backend/headers/wrapper.hpp`
- `src/backend/cpp/wrapper.cpp`
- `src/java/billiards/wrapper/Wrapper.java`

The C++ wrapper exposes `extern "C"` functions so Java can call the native library through JNA. Data crosses the boundary as primitive arrays, `char*`, and simple C structs.

Important C structs:

| Struct | Purpose |
| --- | --- |
| `CPicture` | `initial_angles`, `points`, `equations` char pointers |
| `CInfo` | `CPicture` plus `left_rights` and `code_seq_lr` |
| `CInfoAll` | Full all-equation/vector payload |
| `CString` | One returned string pointer |

Important C++ wrapper functions:

| Function | Purpose |
| --- | --- |
| `to_cstr` | Allocates nul-terminated C string from `std::string`; caller must cleanup. |
| `sqlite_error_logging` | Enables SQLite error logging. |
| `database_create` / `database_clear` | Manage schema/data. |
| `create_connection_pool` | Opens WAL/full-synchronous SQLite pool. |
| `destroy_connection_pool` | Deletes pool and prints connection counts. |
| `cover_wrapper` / `small_cover_wrapper` | Parse cover args and call cover checkers. |
| `getNotFilledCoordinates` | Returns uncovered square coordinates. |
| `cover_wrapper_duplicate_stables` | Runs duplicate-stables cover check. |
| `cover_wrapper_half_duplicate_stables` | Runs half-duplicate-stables cover check. |
| `cover_wrapper_all` | Checks all-cover output from an MRR dir. |
| `save_to_database` | Native entry to compute/save one code. |
| `delete_from_database` | Native entry to delete one code. |
| `load_picture` | Native entry to load minimal display payload. |
| `load_picture_lr` / `load_picture_lr_expando` | Load picture using left/right path data. |
| `load_info` / `load_info_all` / `load_all_equations` / `load_slope_info` | Load progressively richer payloads. |
| `cleanup_cpicture` / `cleanup_cinfo` / `cleanup_string` | Free native strings allocated by wrapper. |
| `merge_covers` | Merge cover directories. |
| `trim_cover` | Trim a cover to a polygon. |
| `code_search_length` / `code_search_even_odd` | DB search by type/length or parity string. |
| `bounding_polygon` | Return exact bounding polygon points for a code. |
| `calculate_gradient` | Parse equation and compute derivative/bound report for UI. |
| `vary_cs_cpp` / `vary_3_cpp` / `vary_4_cpp` | Native vary search entry points. |
| `backend_cancel` | Sets shared cancellation flag. |

Interop contract:

- Return value `1` often means success/non-empty.
- Return value `0` often means empty/not covered.
- Return value `-1` means calculation failure.
- Some string-returning functions return `""` on failure instead of a numeric code.

Risk: Java must call cleanup functions after reading native strings. Some Java methods do this consistently for `CPicture`/`CInfo`, but `CInfoAll` paths appear less clearly cleaned.

## C++ Vary Search Ports

### Files

- `src/backend/headers/triangle_billiard.hpp`
- `src/backend/cpp/triangle_billiard.cpp`
- `src/backend/headers/triangle_billiard4.hpp`
- `src/backend/cpp/triangle_billiard4.cpp`
- `src/backend/headers/vary_cs.hpp`
- `src/backend/cpp/vary_cs.cpp`
- `src/backend/headers/vary3.hpp`
- `src/backend/cpp/vary3.cpp`
- `src/backend/headers/vary4.hpp`
- `src/backend/cpp/vary4.cpp`

### `Vector2D`

Function-level notes:

| Function | Purpose |
| --- | --- |
| Constructor / `create` | Build 2D point/vector. |
| `sub`, `add`, `scale` | Return vector arithmetic results. |
| `norm` | Euclidean length. |
| `dot`, `cross` | Basic vector products. |
| `reflect` | Reflect a point across line through two points. |
| `equals` | Approximate equality. Current epsilon values are suspiciously huge. |
| `to_string` | Debug output. |

### `TriangleBilliard`

Function-level notes:

| Function | Purpose |
| --- | --- |
| Constructor | Store triangle vertices, current side, orientation. |
| `create` | Builds starting triangle from `xAngle`, `yAngle`, and side position. |
| `getNext(left)` | Returns reflected triangle after left or right branch. |
| `getNext2` | Mutating version for reversibility tests. |
| `getNextReverse` | Attempts to undo `getNext`. |
| `equals` | Approximate equality/debug comparison. |
| `getSpecialAngle` | Angle from origin to splitting vertex `C`. |
| `to_string` | Debug output. |

### `TriangleBilliard4`

Adds left/right vertex trails and beam angle bounds `specMin/specMax`.

Function-level notes:

| Function | Purpose |
| --- | --- |
| Constructors | Build initial or continued triangle with trails. |
| `create` | Builds base triangle with side AB on x-axis. |
| `getNext(left)` | Returns optional reflected triangle if branch remains visible in beam bounds. |
| `getSpecialAngle` | Angle to vertex `C`. |
| `between` | Tests if perfect shooting angle lies inside beam interval. |
| `interval` | Beam interval width. |
| `toString` | Debug output with trails. |
| `reconfigure` | Drops obsolete trail points based on worst-line angles. |
| `atan3` | Clamps negative angles to 0 or pi depending on side. |
| `mod3` | Safe modulo for side labels. |

Correctness risks in C++ port:

- `Vector2D::sub` returns a new vector, but `TriangleBilliard4::getNext` and `reconfigure` sometimes call it without assigning the result.
- `reconfigure(false)` appears to subtract `L[i]` while iterating over `R`, unlike the Java version.

### `VaryCS`

Function-level notes:

| Function | Purpose |
| --- | --- |
| `get_total_physical_memory` | OS-specific memory query. |
| `compute_max_inflight` | Converts memory budget to max queued verification tasks. |
| `iterateFireAwayCS2` | Iterative DFS search for CS half-codes, with asynchronous type filtering. |
| `fireAwayCS` | Builds start triangle/side sum and calls `iterateFireAwayCS2` with half move bounds. |

The algorithm tracks:

- `specMin/specMax`: beam interval on the side.
- `sideSum`: signed angle/side sum used as periodicity heuristic.
- `code`: current half-code.
- stack frames: reflected triangle state and branch exploration state.

Candidate handling:

- If depth exceeds min and `sideSum` is near zero, mirror the half-code into a full code.
- Convert/classify code.
- Keep only `CS` candidates, regardless of `reqType` in current C++ implementation.

### `Vary3`

Function-level notes:

| Function | Purpose |
| --- | --- |
| `iterateFireAway3` | Iterative DFS over angle intervals for shots from one starting position. |
| `fireAway3` | Builds start triangle at position `pos`, start side sum, and initial angle interval `[0,pi]`. |

Candidate handling:

- Checks side-sum near zero, final side/orientation equals base state, and perfect shooting angle lies in current interval.
- Verifies code type asynchronously and filters by requested types.

### `Vary4`

Function-level notes:

| Function | Purpose |
| --- | --- |
| `iterateFireAway4` | Iterative DFS using `TriangleBilliard4` visibility intervals. |
| `makeStarts` | Generates independent starting states for parallel exploration. |
| `lazySort` | Sorts starts by interval width. |
| `fireAway4` | Builds starts, submits worker tasks, and merges found codes. |

Candidate handling:

- Checks side-sum near zero, final side/orientation equals base state, and perfect angle lies in interval.
- Converts/classifies and filters by requested types.

Correctness risks:

- `fireAway4` appears to call `makeStarts(startBilliard, movesMax, cores, ...)`; Java calls `makeStarts(startBilliard, 0, numThreads, ...)`. The C++ call likely uses arguments in the wrong order.
- `iterateFireAway4` checks `if (billiard.between(perfectAngle))` using the original function parameter, not `frame.cbilliard`, so the acceptance test may use stale beam bounds.
