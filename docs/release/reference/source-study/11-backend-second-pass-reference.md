# Backend Second-Pass Reference

This document is the second-pass onboarding reference for the C++ backend under `src/backend`. It is intentionally more explicit than the first pass, but it should be read together with the exact symbol indexes:

Current baseline note: Abdul uses this same backend structure, but its `eliminate_phi` implementation is the major confirmed backend source divergence. `[MAIN]` was decompiled with Ghidra and matches the older source-style `std::set` path for that worker, not Abdul's vector-buffer path.

- `docs/source-study/backend-function-index.txt`: backend-only ctags extraction, 1,069 function-like rows.
- `docs/source-study/function-index-ctags.txt`: full project function extraction, 2,032 rows.
- `docs/source-study/symbol-index-ctags.txt`: broader symbol extraction, 4,063 rows.

The generated indexes are the durable checklist for exact line numbers and signatures. This prose file explains intent, data flow, mathematical meaning, call relationships, and maintenance risks.

## Backend Purpose

The backend is the computational engine for the billiard-ball triangle periodic-path problem. Given a symbolic side-hit sequence, it decides whether that sequence can occur as a periodic billiard orbit in a triangle, computes the equations/regions where it occurs, stores those results in SQLite, and uses stored results to certify polygonal regions of triangle-parameter space.

The main mathematical objects are:

- A triangle is parameterized by two angular/radian coordinates, usually represented as `x` and `y`; the third angle is dependent because triangle angles sum to pi.
- A `CodeSequence` is a cyclic word over triangle sides. It represents a sequence of side impacts.
- A classified code sequence carries dynamical metadata: stable/unstable, open/closed, odd/even, and length/sum statistics.
- Stable sequences produce 2D regions in `(x,y)` parameter space. In the database/UI these appear as polygons plus equations.
- Unstable sequences produce curve or line-segment constraints. In cover checking they often act as boundaries between two stable regions.
- A cover proof subdivides a polygonal parameter region into dyadic rectangles and colors each rectangle using a stable region or a stable/unstable/stable triple.

## Backend Call Flow

The normal UI-to-backend path is:

1. Java calls `billiards.wrapper.Wrapper`, which is a JNA facade.
2. JNA enters `src/backend/cpp/wrapper.cpp`, which exposes `extern "C"` functions with simple C ABI types.
3. Wrapper functions convert Java arrays/strings/pointers into C++ `CodeSequence`, `ConnectionPool`, `CString`, `CInfo`, or `CPicture` objects.
4. Database-oriented wrapper calls use `sqlite::PooledConnection` and the functions in `src/backend/cpp/database.cpp`.
5. If a requested code sequence is not already persisted, the backend computes it from symbolic billiard dynamics using `code_sequence`, `classified_code_sequence`, `unfolding`, `shooting_vectors`, `equations`, `refine`, `division`, `trig_identities`, and the `math` templates.
6. Cover-oriented wrapper calls use `src/backend/cpp/verify.cpp`, which parses UI text, obtains `StableInfo` and `TripleInfo` from the database layer, recursively subdivides parameter rectangles, and writes cover artifacts under `cover` or `small_cover`.

The main dependency direction is:

`wrapper.cpp` -> `database.cpp` / `verify.cpp` / `vary*.cpp` -> symbolic computation and geometry headers -> math primitives.

The backend is not purely functional. Several functions write files, allocate C strings for Java, use static result buffers, and update SQLite. These side effects are important when debugging.

## C ABI Boundary: `cpp/wrapper.cpp`

`wrapper.cpp` is the backend's public API. It should be treated as a narrow ABI boundary: Java passes primitive arrays, strings, structs, and opaque pointers; C++ returns integer status codes, allocated strings, or filled structs.

### Memory and Status Conventions

`to_cstr(str)` allocates a new C string and copies a C++ `std::string` into it. It is used when ownership is expected to cross the ABI boundary. Java-side cleanup is handled through native cleanup functions for known structs/strings.

Most exported functions return:

- `1`: success and output is valid.
- `0`: no result/empty mathematical object/false condition.
- `-1`: computation failure or exception path.

Some functions return a native pointer, a boolean-like integer, or a raw `const char*` string. The documentation below notes the pattern.

### Database and Pool Functions

`sqlite_error_logging()` enables SQLite logging. It has no mathematical role; it is a diagnostic hook.

`database_create(db_path)` creates/initializes a SQLite database using backend schema code. It is called from Java `Wrapper.createDatabase` and indirectly from admin UI code.

`database_clear(db_path)` clears the database contents without deleting the database file. It is called from Java database administration UI code.

`create_connection_pool(db_path, pool_size)` constructs a backend `sqlite::ConnectionPool` and returns an opaque pointer to Java. Internally it wraps allocation in a lambda and exception guard. Java stores the pointer in `billiards.wrapper.ConnectionPool`.

`destroy_connection_pool(pool)` deletes/frees the native connection pool. It is the matching cleanup path for Java `ConnectionPool.destroy`.

### Cover Functions

`cover_wrapper(poly_str, codes_str, unstables_str, digits, subdivide, empty, mrr, pool)` calls `check_cover` in `verify.cpp`. It parses a polygon and sets of stable/triple candidates, computes a cover to the requested depth/precision, writes `cover/info.txt`, `cover/unused.txt`, serialized cover files, and returns selected unfilled square coordinates as a string.

`small_cover_wrapper(...)` calls `check_small_cover`. It is the small-cover variant used by the UI's small-cover workflow. It writes to `small_cover` and returns a richer text block containing used stables, used triples, and sample holes separated by `-----`.

`getNotFilledCoordinates(...)` asks the verification layer for holes/unfilled rectangles without the full cover-side reporting path. In the Java viewer it is used by automatic cycle/subdivision workflows to keep drilling into uncovered regions.

`cover_wrapper_duplicate_stables(...)` calls `check_cover_duplicate_stables`. It checks whether a list of triples has repeated stable sides or factorization issues, depending on the mode passed from UI. Java interprets return values `0`, `1`, `2`, and `-1`.

`cover_wrapper_half_duplicate_stables(...)` calls `check_cover_half_duplicate_stables` for half-triple cases.

`cover_wrapper_all(mrr_dir, pool, extra_depth)` calls `check_cover_all`. It loads/uses an MRR directory and attempts to certify all cover pieces, usually in a batch verification workflow.

### Save/Delete/Load Picture Functions

There are three overloads named `save_to_database` in `wrapper.cpp`:

- The internal `save_to_database(code_sequence, code_type, db)` computes or retrieves a `Stable`/`Unstable` result and writes it to the database. It chooses calculation path based on classification.
- The internal `save_to_database(base_code_sequence, left_rights, code_sequence, code_type, db)` computes using a left/right branch expansion relative to a base sequence.
- The exported `save_to_database(code_numbers_ptr, code_numbers_len, pool)` is the C ABI entry. It converts the integer array into a `CodeSequence`/classification and delegates to the internal version.

`delete_from_database(code_sequence, code_type, db)` removes an entry if the database supports that action. The exported overload converts Java arrays and returns an integer status.

`copy_to_cpicture(picture, cpicture)` copies backend `Picture` strings into a JNA structure. It is a marshalling function; the `Picture` itself represents either stable polygon/equation data or unstable initial-angle/point/equation data.

`load_picture(code_numbers_ptr, code_numbers_len, cpicture, pool)` loads or computes the drawable picture for one classified code sequence. Java `Wrapper.loadPicture` calls this and wraps the result as `billiards.database.Picture`.

`cleanup_cpicture(cpicture)` releases string memory owned by the native `CPicture` fields. It must be called after Java copies the data.

`load_picture_lr_expando(code_numbers_ptr, code_numbers_len, cpicture, pool, lr)` loads a picture using a left/right expansion string rather than a base-code comparison.

`load_picture_lr(base_code_numbers_ptr, base_code_numbers_len, code_numbers_ptr, code_numbers_len, cpicture, pool)` loads a picture for a sequence relative to a base sequence and inferred left/right data.

### Load Info Functions

`copy_to_cinfoAll(info, cinfoAll)` copies a `CodeInfo` object into the larger all-equations JNA structure. This includes points, equations, left/right metadata, and related strings.

`load_all_equations(code_numbers_ptr, code_numbers_len, cinfoAll, pool)` returns all equation data for a sequence. It is used by Java `Wrapper.loadAllEquation` and by info/gradient UI tools.

`load_info_all(code_numbers_ptr, code_numbers_len, cinfoAll, pool)` returns full `CodeInfo` for a code sequence. It is a broad information request rather than just drawing data.

`copy_to_cinfo(info, cinfo)` copies the smaller `Info` structure used by `InfoWindow`.

`load_info(code_numbers_ptr, code_numbers_len, cinfo, pool)` fills a `CInfo` for one sequence. Java `Wrapper.loadInfo` consumes it.

`copy_to_cinfoAll_2(info, cinfoAll)` is a slope/vector-specific copier. It has the same ABI purpose as `copy_to_cinfoAll`, but the source data is from vector computation.

`load_slope_info(code_numbers_ptr, code_numbers_len, cinfoAll, pool)` computes or loads vector/slope information used by viewer verification utilities.

`cleanup_cinfo(cinfo)` releases native strings owned by a `CInfo`.

### Search, Merge, Trim, Bounding, Gradient, Vary

`merge_covers(merge_dir_ptr, cover_dirs_ptr, pool)` merges multiple serialized cover directories. Java passes a newline-joined list of directories.

`code_search_length(code_type_int, length_int, cstring, pool)` searches the database by code type and length and returns text through `CString`.

`code_search_even_odd(code_type_int, even_odd, cstring, pool)` searches by code type and odd/even pattern string.

`cleanup_string(cstring)` releases a native string returned through `CString`.

`trim_cover(poly_str, in_dir, out_dir)` reads a serialized cover, restricts it to a polygon, and writes a trimmed cover to a new directory.

`bounding_polygon(code_seq, db)` computes a text representation of a stable/unstable bounding polygon for one sequence using database info.

`bounding_polygon(code_numbers_ptr, code_numbers_len, cstring, pool)` is the exported ABI wrapper around the previous function.

`parse_equation_database(equation_str)` parses equation strings in the older database text format into symbolic `Equation<T>` objects.

`replaceAll(str, from, to)` is a string normalizer used while parsing equation text.

`parse_term(coeff, buffer)` parses one symbolic coefficient/term chunk.

`parse_equation_info(equation_str)` parses equation strings from info-window text into symbolic equations. This path exists because database and UI equation formatting differ.

`equation_stuff_first_only(equation, x_value, y_value, oss, is_p)` evaluates/formats the first relevant part of an equation at a parameter point. It contributes to gradient/derivative output.

`equation_stuff(equation, x_value, y_value, oss, is_sin)` evaluates/formats a full equation. It is a display/calculation bridge, not part of the core cover proof.

`calculate_gradient(equation_cstr, x_value, y_value, from_database, cstring, cstring2)` parses an equation, evaluates derivative/gradient-like quantities at a point, and writes two output strings. Java exposes this as `calculateGradient` and `calculateGradient2`.

`vary_cs_cpp(movesMin, movesMax, xAngle, yAngle, result, reqTypes)` calls the C++ cycle-search/vary code for starting angles. It returns newline-separated code sequences.

`vary_3_cpp(movesMin, movesMax, initPosition, xAngle, yAngle, result, reqTypes)` calls the three-parameter vary search.

`vary_4_cpp(movesMin, movesMax, xAngle, yAngle, result, reqTypes)` calls the four-parameter vary search.

`backend_cancel()` sets a backend cancellation flag used by long-running searches/covers. Treat it as a global side effect.

### Wrapper Risks

The C ABI layer mixes raw pointers, native allocation, status codes, and static result strings. When modifying it, verify both sides of the ABI at once: Java declaration, C++ exported signature, struct field order, cleanup function, and return-value handling.

Observed risks:

- Java uses `lr == "empty"` in `Wrapper.loadPictureLR`; this is reference comparison rather than value comparison. Use `.equals` if source changes are later allowed.
- Java `vary3Cpp` and `vary4Cpp` collect results into a plain `ArrayList` from a parallel stream, which is a data race.
- `calculateGradient2` reads from `cstring2` but calls `cleanup_string(cstring)` rather than explicitly cleaning `cstring2`; confirm ownership before changing.
- Several backend functions return `const char*` backed by static `std::string` storage. This is not thread-safe and can be overwritten by a later call.

## Database Computation: `cpp/database.cpp`

This file is the bridge between symbolic computation and persisted SQLite rows.

`parse_mrr_equations(equations_str)` parses a database equation blob into separate sine and cosine equation sets. It is used when reconstructing MRR information from stored text.

`parse_mrr_polygon(points_str)` parses stored polygon point text into `PointQ` rational vertices.

`bounding_line_segment(code_sequence, initial_angles)` constructs the rational line-segment bound for an unstable code sequence from its initial angles.

`bounding_polygon(code_sequence, initial_angles)` constructs the rational polygon bound for a stable code sequence. The local lambda `get_vertices` converts interval/rational coordinate pairs into vertices.

`load_stable_mrr_from_database(code_sequence, db)` loads stable MRR data directly from SQLite and reconstructs `InitialAngles` plus `CodeInfo`.

`load_unstable_mrr_from_database(code_sequence, db)` does the same for unstable data.

`load_all_from_database(code_sequence, db)` loads the broader all-equations representation rather than just MRR data.

`calculate_stable_all_info(code_sequence, initial_angles)` recomputes all stable information without relying on MRR rows. It calls symbolic equation and polygon construction, then formats/stores it as `CodeInfo`.

`calculate_unstable_all_info(code_sequence, initial_angles)` recomputes all unstable curve/line information.

`calculate_all_info(code_sequence, initial_angles)` dispatches stable vs unstable calculation based on code classification.

`calculate_all_vector(code_sequence, initial_angles)` calculates vector/slope-oriented information used by gradient/vector verification UI paths.

`save_to_database(code_sequence, info, db)` persists a computed `CodeInfo` row.

`ensure_stable_all_in_database(code_sequence, db)` checks for stable all-equation data and computes/saves it if absent.

`ensure_unstable_all_in_database(code_sequence, db)` does the same for unstable data.

`get_stable_info(code_sequence, mrr, db)` is the primary stable lookup. In MRR mode it loads minimal region data; in all mode it ensures and loads all-equation data.

`get_unstable_info(code_sequence, mrr, db)` is the unstable analogue.

`get_single_info(code_seq, mrr, db)` packages one stable code as a `SinglePair` plus `StableInfo`. Cover code consumes this shape.

`get_single_infos(code_seqs, mrr, db)` maps a set of stable sequences through `get_single_info` and sorts them by cost. The local comparator favors lower-cost candidates during cover coloring.

`get_triple_info(triple, mrr, db)` loads/calculates the negative stable, unstable, and positive stable members of a stable/unstable/stable triple.

`get_triple_info_duplicate_stables(triple, mrr, db, show)` checks duplicate-stable conditions for one triple.

`get_triple_info_half_duplicate_stables(half_triple, mrr, db)` checks the half-triple variant.

`get_triple_infos(triples, mrr, db)` maps a set of triples into `TriplePair`/`TripleInfo` values.

`get_triple_infos_duplicate_stables(triples, mrr, db, show)` returns aggregate duplicate-stable status for a set of triples.

`get_triple_infos_half_duplicate_stables(half_triples, mrr, db)` returns aggregate half-triple status.

`get_single_infos_map(code_pairs, mrr, db)` loads a map keyed by `SinglePair`. It is used when loading/merging serialized covers.

`get_triple_infos_map(triples, mrr, db)` loads a map keyed by `TriplePair`.

Mathematical role: this file decides when a symbolic proof object is reused from SQLite and when it must be regenerated. Performance and correctness depend heavily on stable text serialization of code sequences, equations, points, and left/right data.

## Cover Verification: `cpp/verify.cpp`

This is the heart of region certification. It takes a polygon in triangle-parameter space and a finite list of known stable/triple certificates, then recursively attempts to cover the polygon.

### Parsers and Setup

`parse_singles(str)` parses whitespace/newline-delimited stable code strings into a `std::set<CodeSequence>`. It is called by cover entry points.

`parse_triples(str)` parses stable/unstable/stable triples from UI text. Each parsed triple is later resolved to `TripleInfo` through the database layer.

`parse_triples_half(str)` parses half-triples used by the half-duplicate-stables checker.

`parse_polygon(str)` parses the UI polygon string into a `ClosedConvexPolygonQ`. The UI generally sends rational coordinates in normalized parameter space, not degree text.

`to_degrees(rect)` formats a rational rectangle as degrees for logs/UI output.

`rationalToDegrees(rat)` converts a rational coordinate to a decimal-degree real. It is used for cover info files.

`parse_fraction_to_double(fractionString)` parses strings like `3/7` or `0.25` into a double. It is a newer helper used by small-cover input parsing.

`get_or(map, key, default)` is a template utility for count maps. It avoids repeated lookup boilerplate while producing report totals.

### Positivity Tests

`any_singles_positive(single_infos, single_indices, square, eval)` scans candidate stable regions and returns the index of the first one whose equations prove positivity over the square. In cover terms, a positive stable means the entire rectangle can be colored by that stable code.

`any_triples_positive(triple_infos, triple_indices, square, eval)` scans stable/unstable/stable triples and returns the first triple that certifies the square. Conceptually this handles regions where one unstable boundary separates two stable regions and the combination certifies the rectangle.

`any_half_triples_positive(triple_infos, triple_indices, square, eval)` is the half-triple variant.

`any_unstables_positive(triple_infos, triple_indices, square, eval)` scans unstable constraints. It is used for duplicate/half duplicate workflows rather than ordinary final cover coloring.

These functions depend on interval evaluation. They are not sampling. They attempt to prove sign conditions over an entire rectangle using interval arithmetic and symbolic equations.

### Candidate Trimming

`trim_single_indices(square, single_infos, single_indices)` filters stable candidates to those whose stored region can geometrically intersect the current square. This reduces expensive positivity checks.

`trim_triple_indices(square, triple_infos, triple_indices)` filters triple candidates similarly.

`trim_half_triple_indices(square, triple_infos, triple_indices)` filters half-triples.

The optimization idea is simple: if a stored polygon/segment is disjoint from a square, its equations do not need to be tested on that square. The geometry routines are exact/rational where possible.

### Recursive Cover Construction

`cover_square(square, polygon, single_infos, triple_infos, prec, max_mag, single_indices, triple_indices)` is the recursive cover engine. It works approximately as follows:

1. If `square` is disjoint from the target polygon, return `cover::Empty`.
2. If a stable candidate is positive on the square, return `cover::Single`.
3. If a triple candidate is positive, return `cover::Triple`.
4. If maximum magnification/depth is reached, return `cover::Empty` or an uncovered marker.
5. Otherwise subdivide the square into four quarters, recursively cover each, and return `cover::Divide`.

The anonymous lambdas at lines 344, 348, 352, and 356 compute the four quarters. They exist only to structure the recursive calls.

`UpdateCover` is a visitor over an existing `cover::Cover` tree. It lets a saved cover be updated with new singles/triples or a different polygon without discarding already colored subtrees.

`UpdateCover::operator()(cover::Empty)` recovers an empty/uncovered square by calling `cover_square`.

`UpdateCover::operator()(cover::Single)` keeps a stable-covered square as-is unless the polygon relation requires recomputation.

`UpdateCover::operator()(cover::Triple)` keeps a triple-covered square as-is unless the polygon relation requires recomputation.

`UpdateCover::operator()(cover::Divide)` recurses into the four children. The generated lambdas at lines 445, 454, 463, and 472 compute quarter updates.

Important suspected bug: in the currently read code, the four lambdas inside `UpdateCover::operator()(cover::Divide)` all assign through `cover0` and access `quarter_covers.get<0>()`. That appears inconsistent with the intended four-quarter update and may cause only one child to be updated correctly. Do not change it unless source edits are allowed; if later debugging cover-update behavior, inspect this first.

### Cover Entry Points and Reports

`getEmpties(polygon_str, singles_str, triples_str, digits, max_depth, empty, mrr, pool, isLastCycle)` returns selected empty square coordinates. It shares much of the cover setup path with `check_cover`.

`cover_polygon(old_cover, square, polygon, single_infos, triple_infos, digits, max_mag, empties, mrr)` computes or updates a full cover and writes `cover` directory artifacts. It also writes:

- Polygon vertices in degrees.
- Used stable codes with type/length/sum/cost/count.
- Used triples.
- Unused singles/triples.
- Number and sample locations of uncovered squares.
- Total stable cost and deepest magnification.

It returns a string containing sampled unfilled square intervals.

`check_cover(polygon_str, singles_str, triples_str, digits, max_depth, empty, mrr, pool)` is the primary JNA target for ordinary cover verification. It opens a pooled SQLite connection, parses inputs, obtains `StableInfos` and `TripleInfos`, starts from `cover::Empty`, and calls `cover_polygon`.

`cover_small_polygon(old_cover, square, polygon, single_infos, triple_infos, digits, max_mag, empties, mrr, printInfo)` is the small-cover variant. It writes under `small_cover`, optionally suppresses console logging, and returns a text report divided into used stables, used triples, and holes.

`check_small_cover(polygon_str, singles_str, triples_str, digits, max_depth, empty, mrr, pool, printInfo)` is the small-cover JNA target. It parses a polygon and computes the square bounds differently from ordinary cover mode, then calls `cover_small_polygon`.

`check_cover_duplicate_stables(polygon_str, singles_str, triples_str, digits, max_depth, empty, mrr, pool, show)` checks whether triples contain problematic duplicate stable behavior. It calls database duplicate-check helpers and returns one of the status codes Java interprets.

`update_equation(code_sequence, stable_neg, db)` updates equation text for a sequence during duplicate-stable repair/restore workflows.

`update_initial_angles(code_sequence, db)` updates stored initial-angle text.

`update_points(code_sequence, db)` updates stored point/polygon text.

`update_unstable_equations(code_sequence, db)` updates unstable equation text.

`restore(sequence_equations, sequence_init_angles, sequence_points, sequence_unstable_equations, db)` restores database rows after temporary duplicate-stable modifications.

`check_cover_half_duplicate_stables(polygon_str, singles_str, triples_str, digits, max_depth, empty, mrr, pool)` checks half-triple duplicate-stable behavior.

`check_cover_all(mrr_dir, pool, extra_depth)` loads an MRR cover directory and verifies all pieces to an extra depth. Its local lambda processes MRR triples.

### Cover Optimizations

The comments in `verify.cpp` already identify a major optimization: once an equation is proved positive on a rectangle, descendants should not have to re-test that same equation. A future implementation could carry per-rectangle proven-positive masks or candidate states through recursion.

Other useful optimizations:

- Cache interval evaluation results keyed by `(equation_id, rectangle)` during one cover run.
- Make `single_indices`/`triple_indices` persistent through recursive calls and trim incrementally.
- Avoid repeated database parsing by storing pre-parsed equations in `StableInfo`/`TripleInfo`.
- Avoid string formatting during core cover recursion; do reporting only after `cover_to_info`.
- Replace static return strings with owned ABI buffers or Java-side direct copies to make concurrent cover calls safe.

## Cover Data Model: `headers/cover/cover.hpp`

The cover tree is a serialized proof object.

`CodePair` stores a code sequence and stable/unstable type identity. It is the base comparable key used in cover maps.

`UniquePtr<T>` and `SharedPtr<T>` are comparison/printing wrappers around smart pointers. They let cover nodes be stored in variants and maps while preserving stable identity semantics.

`SinglePair` represents one stable code used to color a square.

`TriplePair` represents a stable-negative/unstable/stable-positive triple used to color a square.

`HalfTriplePair` is a reduced triple form for half-duplicate-stable workflows.

`Quarters<T>` stores four child values and provides `get<0>()` through `get<3>()`. It models dyadic subdivision of a square.

`cover::Empty` marks a square that is outside the polygon, unfilled, or not yet refined depending on context.

`cover::Single` stores a `SinglePair` for a square certified by one stable region.

`cover::Triple` stores a `TriplePair` for a square certified by a stable/unstable/stable triple.

`cover::HalfTriple` stores a `HalfTriplePair`.

`cover::Divide` stores four child covers for recursively subdivided squares.

`cover::Cover` is a boost variant over these node types.

Interaction:

- `verify.cpp` constructs and updates `cover::Cover`.
- `cover.cpp`/cover headers save/load it to disk.
- Java viewer code displays cover rectangles, unfilled holes, and cover summary text.

## Geometry Layer

Geometry routines are used by both computation and cover pruning. C++ geometry is generally templated over coordinate type (`Rational`, `Interval`, `DecReal`, etc.), which lets the same concepts support exact rational geometry and interval arithmetic.

### `headers/geometry/algorithms.hpp`

`collinear_element(...)` tests whether a point/segment lies on a collinear geometric object.

`element(...)` overloads test membership: point in interval, point on segment, point in rectangle, point in polygon, and related variants.

`subset(...)` overloads prove geometric containment. Cover code uses `subset(polygon, square)` and related checks before subdividing.

`disjoint(...)` overloads prove separation between two objects. The `Disjoint` visitor supports variant-shaped objects.

`separating_axis(segment, figure)`, `separating_axis(rectangle, figure)`, and `separating_axis(polygon, figure)` implement separating-axis tests. Mathematically, two convex bodies in the plane are disjoint if some axis has disjoint projections.

`intersects(...)` is generally the negation of proven disjointness, but pay attention to open/closed interval types.

`intersection(closed_interval, closed_interval)` returns the common interval if one exists.

`intersection(rectangle, closed_segment)` uses line clipping, effectively a Liang-Barsky style algorithm.

`special_intersection(rectangle, open_segment)` handles open-segment edge cases where endpoints should not count.

### `headers/geometry/convex_polygon.hpp`

`ConvexPolygon(...)` constructs a normalized polygon. The private constructor assumes validation has already succeeded.

`intersects(segment)` checks edge intersection against a segment.

`check(points)` validates polygon constraints, including convexity and nondegenerate ordering.

`normalize(points)` canonicalizes vertex order so equality/comparison are stable.

`print_points(os)` emits vertices for debugging/serialization.

`begin/end/size/vertex` expose read-only access to vertices.

`operator==`, ordering operators, and `operator<<` allow polygons to be keys or formatted text.

### Geometry Meaning in This Project

The frontend displays regions in degree/radian space, but the backend proof work wants exactness. Rational polygons/rectangles prevent cover artifacts from depending on floating point sampling. Interval polygons/segments are used when equations define regions implicitly and the backend needs rigorous sign/containment evidence.

## Symbolic Math Layer

The math templates define symbolic linear combinations, monomials, polynomials, trig wrappers, and interval/real/rational numeric aliases.

Important families:

- `LinComArr` and `LinComMap` represent linear combinations over a fixed array or sparse map of symbolic basis variables.
- `Monomial` represents products of symbolic factors with exponents.
- `Polynomial` represents sums of monomials.
- `Trig`, `Sin`, and `Cos` wrap symbolic angle expressions.
- `Equation<T>` represents an equality/zero condition over symbolic terms.
- `Sign` and interval helpers classify positivity/negativity/zero over intervals.

Mathematically, the backend avoids numerically tracing every billiard orbit. It unfolds reflections into symbolic straight-line constraints, derives equations in triangle-angle coordinates, simplifies them with trigonometric identities, and then evaluates/proves signs over parameter regions.

## Code Sequence and Classification

The C++ code-sequence layer mirrors Java concepts, but it is the authoritative path for backend computation.

`CodeSequence` stores the canonical, repeat-eliminated, rotation-standardized side-hit word.

The validation path checks:

- Only side labels 0, 1, and 2 are used.
- Adjacent equal side hits are illegal.
- Repeated periodic subwords are eliminated.
- Cyclic rotations are normalized to a standard representative.

`ClassifiedCodeSequence` computes:

- Odd/even pattern.
- Code type.
- Stability.
- Closed/open classification.
- Length and sum.

Backend functions that accept raw integer arrays almost always immediately build these objects. If sequence normalization differs between Java and C++, database keys can become inconsistent, so keep the two implementations aligned.

## Billiard Dynamics Algorithms

### `cpp/unfolding.cpp`

`find_path(start, end)` finds a path in the unfolded triangular tiling between vertices.

`double_if_odd(vec)` doubles a vector/list when the code has odd parity. This reflects the fact that some billiard paths only close after traversing the symbolic sequence twice.

`Unfolding::Unfolding(code_numbers, code_angles)` builds the unfolded geometry for a code sequence.

`Unfolding::path_vector(path)` computes symbolic sine/cosine path vector equations along a path in the unfolded tiling.

`Unfolding::shooting_vector_general()` derives the general shooting vector for the current unfolding.

`Unfolding::get_all_vectors()` enumerates relevant path vectors, using thread workers for batches.

`Unfolding::generate_curves(shooting_vector_x, shooting_vector_y, initial_angles)` derives curve equations for candidate billiard paths.

`Unfolding::generate_curves(..., center, rx, ry)` is the localized variant around a rational center/radius window.

`Unfolding::generate_curves_lr(shooting_vector_x, shooting_vector_y)` generates left/right curve metadata.

`Unfolding::generate_curves_lr(..., left_rights)` constrains generation to a supplied left/right branch sequence.

Mathematical role: instead of reflecting the moving particle at every side hit, the triangle is reflected. The billiard path becomes a straight segment through reflected triangle copies. Periodicity becomes a condition on the endpoint vector.

### `cpp/shooting_vectors.cpp`

`shooting_angle_odd(code_sequence, code_angles)` computes the symbolic shooting angle expression for odd sequences.

`shooting_angle_closed(code_sequence, code_angles)` computes the closed-orbit shooting angle expression. The local lambda chooses a perpendicular-angle case.

`shooting_vector_open(code_sequence, code_angles)` returns symbolic cosine/sine vector components for open paths.

`shooting_vector_closed(code_sequence, code_angles)` returns symbolic sine/cosine vector components for closed paths.

These functions are the symbolic bridge from side-hit words to equations in triangle-angle variables.

### `cpp/equations.cpp`

`LeftRightVariant(curves)` is a visitor helper for deciding whether an equation/gradient lies left or right of a curve family.

`LeftRightVariant::operator()(...)` overloads handle eta-linear, sine, and cosine equation-gradient types.

`stable_left_right(polygon, curves)` computes left/right classifications for a stable polygon.

`unstable_left_right(line_seg, curves)` computes left/right classifications for unstable line segments.

`stable_equations_to_string(polygon, inverse_perm_eta, inverse_perm_pi)` serializes stable boundary equations.

`unstable_equations_to_string(line_seg, inverse_perm_eta, inverse_perm_pi)` serializes unstable boundary equations.

`point_to_vector(point)` converts a rational point into interval-vector form.

`convert_to_interval(rat_line_seg)` and `convert_to_interval(rat_polygon)` lift exact rational geometry into interval geometry.

`calculate_final_polygon(code_numbers, code_angles, curves)` computes the final stable polygon by iterating/refining all curve constraints.

`calculate_final_line_segment(code_numbers, code_angles, constraint, curves)` computes the final unstable line segment.

`convex_counterexample_checker(polygon)` is a defensive validation helper.

`points_and_stuff_stable(code_numbers, code_angles, curves)` packages stable points, equations, and left/right data.

`points_and_stuff_unstable(code_numbers, code_angles, constraint, curves)` packages unstable data.

`calculate_stable(code_sequence, code_type)` computes a stable result from scratch.

`calculate_unstable(code_sequence, code_type)` computes an unstable result from scratch.

`calculate_stable(code_sequence, code_type, left_rights)` recomputes stable data under a supplied branch selection.

`calculate_unstable(code_sequence, code_type, left_rights)` recomputes unstable data under a supplied branch selection.

### `cpp/refine.cpp`

`median(point)` chooses midpoint coordinates from interval-valued points.

`print_region(region)` overloads format polygons and line segments for debugging.

`refine_line_segment(line_segment, curve, constraint)` intersects/refines an unstable interval line segment against a curve and a constraint.

`ZeroInfo(prev_sign, next_sign)` records how a zero crossing behaves relative to adjacent signs.

`Corner(corner_sign)` and `Corner(corner_sign, zero_info)` represent sign state at polygon corners.

`operator==(ZeroInfo)` and `operator<<(Corner)` are comparison/debug helpers.

`correct_zeros(corners)` repairs ambiguous zero-sign patterns at polygon corners.

`calculate_corners(polygon, curve)` evaluates signs/zeros at polygon vertices.

`refine_polygon(polygon, curve)` clips a polygon against one curve constraint. Repeated refinement across curves yields the stable parameter polygon.

Mathematical role: equations define half-plane/curved-side inequalities in parameter space. Refinement uses interval sign reasoning to keep only the region where all required inequalities hold.

## Vary/Search Backend

The vary functions generate candidate code sequences by simulating triangle billiard dynamics from parameter values or parameter boxes.

High-level responsibilities:

- `vary3` searches with an initial position plus two angles.
- `vary4` searches in a four-parameter/branch-aware variant.
- `vary5`, `vary6`, and `vary7` appear to be experimental or later variants.
- `triangle_billiard.cpp` models one billiard state and transitions.

These searches are candidate finders, not final proofs. Generated code sequences still need classification, database computation, and often cover verification.

Optimization opportunities:

- Avoid repeated canonicalization of the same partial sequences.
- Use cancellation checks consistently in deep recursion.
- Replace text-based return of large result sets with a binary/structured ABI once frontend compatibility permits.
- Keep branch-state structs immutable or explicitly copied to avoid recursion bugs.

## SQLite Layer

`headers/sqlite.hpp` and related implementation files provide:

- `sqlite::Database`: RAII wrapper around a database connection.
- Statement preparation and parameter binding.
- Query helpers for code sequences, pictures, info, and cover data.
- `sqlite::ConnectionPool`: pool of database handles used by Java long-running tasks.
- `sqlite::PooledConnection`: scoped checkout/checkin wrapper.

The backend frequently computes missing rows and then immediately saves them. That means database calls are not always read-only even when Java method names look like `load...`.

## Cross-Cutting Risks and Optimization Backlog

Priority risks:

- The `UpdateCover::operator()(cover::Divide)` quarter-update code appears suspicious, as noted above.
- Several functions return pointers into static strings. This is not reentrant and not safe for concurrent Java tasks.
- Some parsing paths use free-form text from UI files and have weak validation. Bad input can become exceptions or silently skipped data.
- Database text formats are duplicated across Java and C++; mismatches can break loading without compile errors.
- JNA struct field order must exactly match native memory layout.

Optimization candidates:

- Cache equation parse results and interval evaluations during cover runs.
- Add stable IDs for equations and cover candidates so cover trees store compact integers rather than repeated object graphs.
- Make cover serialization versioned. Current formats look implicit and fragile.
- Split `wrapper.cpp` into ABI, database, cover, search, and gradient modules while preserving exported names.
- Move frontend-only formatting out of backend proof loops.
- Replace repeated string parsing of code sequences with typed DTOs or structured files.

## How To Resume This Pass

If a future agent continues, start here:

1. Read `docs/source-study/00-progress-cache.md`.
2. Use `docs/source-study/backend-function-index.txt` as the exact backend checklist.
3. For any function not described at desired depth here, add a per-file subsection rather than modifying source.
4. Keep source untouched unless the user explicitly lifts the no-code-change constraint.
