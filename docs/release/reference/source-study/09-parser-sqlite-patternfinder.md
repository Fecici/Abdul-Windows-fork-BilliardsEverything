# Parser, SQLite, And PatternFinder Helpers

This document covers smaller helper subsystems that were not fully expanded in the architecture documents: C++ parsing and database serialization helpers, the lightweight SQLite wrapper, and the Java `patternfinder` package.

Current baseline note: no Abdul-specific parser, SQLite, or PatternFinder source change was found in the source-to-Abdul diff. `[MAIN]` keeps the same checked non-viewer Java packages by bytecode comparison; backend database/wrapper exports are covered in `19`.

## C++ User/Input Parsing

Files:

- `src/backend/headers/parse.hpp`
- `src/backend/cpp/parse.cpp`

These functions parse strings coming from user input or files. Database-specific parsing is separated into `database/deserialize.cpp`.

| Function | Purpose | Calls | Called By |
| --- | --- | --- | --- |
| `parse_code_sequence(str)` | Splits a space-separated integer string into `CodeNumber`s and builds a `CodeSequence`. | `split`, `boost::lexical_cast`, `CodeSequence` constructor. | CLI/tests/wrapper-style input paths that need a raw `CodeSequence`. |
| `parse_initial_angles(str)` | Converts two-letter angle names such as `xy`, `xz`, `zy` into `InitialAngles{XYZ, XYZ}`. | None beyond enum construction. | Input parsing and tests; database deserialization has a parallel implementation. |
| `is_digit(ch)` | Local helper for parser state machines. | None. | `parse_lin_com_arr_*` and trig-map parser. |
| `parse_number(num)` | Converts an optional coefficient prefix into an integer coefficient. Empty means `1`; `-` means `-1`. | `boost::lexical_cast`. | Linear-combination parsers. |
| `parse_lin_com_arr_xy(str)` | Parses expressions such as `x`, `-x+2y`, `3x-y`, or `0` into integer linear combinations over symbols `x,y`. | `parse_number`, `LinComArrZ<XY>::add`. | Tests and input-facing expression parsing. |
| `parse_lin_com_arr_xypi(str)` | Parses integer linear combinations over `x,y,pi`. It recognizes `pi` by seeing `p` followed by `i`. | `parse_number`, `LinComArrZ<XYPi>::add`. | Tests and equation/constraint input parsing. |
| `parse_lin_com_arr_xyeta(str)` | Parses integer linear combinations over `x,y,eta`. It recognizes `eta` by seeing `e` followed by `ta`. | `parse_number`, `LinComArrZ<XYEta>::add`. | Tests and eta-constraint input parsing. |
| `parse_lin_com_map_xy<Trig>(str, trig)` | Template parser for sums of trig terms like `2sin(x-y)-3sin(y)`. It expects a trig token followed by a parenthesized `XY` linear argument. | `parse_number`, `parse_lin_com_arr_xy`, `LinComMapZ::add`. | Specialized by `parse_lin_com_map_sin_xy` and `parse_lin_com_map_cos_xy`. |
| `parse_lin_com_map_sin_xy(str)` | Parses a sum of sine terms over `XY` arguments. | `parse_lin_com_map_xy<Sin>`. | Tests and input-facing equation tools. |
| `parse_lin_com_map_cos_xy(str)` | Parses a sum of cosine terms over `XY` arguments. | `parse_lin_com_map_xy<Cos>`. | Tests and input-facing equation tools. |

Math note: these parsers feed the same symbolic algebra layer used by the billiard equations. A linear form like `3x-y+pi` represents an angle expression. A trig map is a sparse sum of terms such as `c*sin(a*x+b*y)`.

Risk notes:

- `parse_lin_com_arr_xypi` reads `str.at(i + 1)` when it sees `p`; malformed trailing `p` throws `out_of_range` instead of the custom parser error.
- `parse_lin_com_arr_xyeta` reads `i + 1` and `i + 2` when it sees `e`; malformed trailing `e` or `et` throws `out_of_range`.
- The three linear-combination parsers are mostly duplicated and could be unified with a small token table.

## C++ SQLite Wrapper

File:

- `src/backend/headers/sqlite.hpp`

This is a small RAII wrapper over `sqlite3`, prepared statements, and a blocking connection pool.

| Function/Class | Purpose | Calls | Called By |
| --- | --- | --- | --- |
| `result_code_string(result_code)` | Converts SQLite result codes into readable names. | SQLite constants only. | Error formatting helpers. |
| `error_message(error_code)` | Formats a SQLite result code and `sqlite3_errstr` message. | `result_code_string`, `sqlite3_errstr`. | Statement/database error paths. |
| `error_message(error_code, db)` | Adds `sqlite3_errmsg(db)` to the formatted error. | `error_message`, `sqlite3_errmsg`. | Database open/prepare errors. |
| `error_log_callback(...)` | Logs SQLite config-level errors to stderr. | `fprintf`. | Registered by `sqlite::error_logging`. |
| `sqlite::error_logging()` | Enables SQLite global log callback. Must be called before opening connections. | `sqlite3_config`. | Application setup if enabled. |
| `sqlite::Statement` | RAII owner for a `sqlite3_stmt*`. Finalizes the statement in its destructor. | SQLite bind/step/column APIs. | All database query paths. |
| `Statement::Statement(sqlite3_stmt*)` | Wraps a prepared SQLite statement pointer. | None. | `Database::prepare`. |
| `Statement::Statement(Statement&&)` | Transfers statement ownership and nulls the source pointer. | None. | Returning statements by value. |
| `Statement::~Statement()` | Finalizes the statement. Logs errors because destructors cannot safely throw. | `sqlite3_finalize`. | Automatic cleanup. |
| `Statement::bind_value(index, double)` | Binds a double parameter. | `sqlite3_bind_double`. | `bind_unpack`. |
| `Statement::bind_value(index, int64_t)` | Binds an integer parameter. | `sqlite3_bind_int64`. | `bind_unpack`. |
| `Statement::bind_value(index, string)` | Binds text with `SQLITE_TRANSIENT`, so SQLite copies the data. | `sqlite3_bind_text`. | `bind_unpack`. |
| `Statement::bind_unpack(index)` | Base-case no-op for variadic binding. | None. | `bind`. |
| `Statement::bind_unpack(index, val, args...)` | Recursively binds values starting at SQLite’s 1-based parameter index. | `bind_value`. | `bind`. |
| `Statement::bind(args...)` | Verifies parameter count, binds all arguments, and returns an rvalue reference to allow `prepare(...).bind(...).exec(...)`. | `sqlite3_bind_parameter_count`, `bind_unpack`. | All query call sites. |
| `Statement::get_value(col, double&)` | Reads a floating column after type-checking for `SQLITE_FLOAT`. | `sqlite3_column_type`, `sqlite3_column_double`. | `column_unpack`. |
| `Statement::get_value(col, int64_t&)` | Reads an integer column after type-checking for `SQLITE_INTEGER`. | `sqlite3_column_type`, `sqlite3_column_int64`. | `column_unpack`. |
| `Statement::get_value(col, string&)` | Reads a text column after type-checking for `SQLITE_TEXT`; uses byte length to preserve embedded zeros if present. | `sqlite3_column_type`, `sqlite3_column_text`, `sqlite3_column_bytes`. | `column_unpack`. |
| `Statement::column_unpack(index)` | Base-case no-op for variadic column extraction. | None. | `exec`, `step`. |
| `Statement::column_unpack(index, val, args...)` | Recursively extracts row columns into references. | `get_value`. | `exec`, `step`. |
| `Statement::exec(args&...)` | Executes a statement expected to return zero rows or exactly one row. For result statements, it steps once for a row and once more to ensure no second row exists. | `sqlite3_column_count`, `sqlite3_step`, `column_unpack`. | Single-row selects, inserts, deletes, schema commands. |
| `Statement::step(args&...)` | Iterates a multi-row result set. Returns `true` per row and `false` at `SQLITE_DONE`. | `sqlite3_column_count`, `sqlite3_step`, `column_unpack`. | Search/list query paths. |
| `sqlite::Open` and `operator|` | Type-safe wrapper for SQLite open flags. | Bit operations. | `Database` construction. |
| `sqlite::Database` | RAII owner for a `sqlite3*` connection. | `sqlite3_open_v2`, `sqlite3_close`, `sqlite3_prepare_v2`. | Admin/database code and connection pool. |
| `Database::Database(db_path, flags)` | Opens a SQLite connection with the requested flags. | `sqlite3_open_v2`. | Pool factories and admin creation. |
| `Database::Database(Database&&)` | Transfers connection ownership and nulls the source. | None. | Connection pool queue moves. |
| `Database::~Database()` | Closes the SQLite connection and logs close errors. | `sqlite3_close`. | Automatic cleanup. |
| `Database::prepare(sql)` | Prepares SQL and returns a `Statement` owner. | `sqlite3_prepare_v2`. | All SQL execution. |
| `sqlite::ConnectionPool` | Fixed-size pool of `Database` objects protected by a mutex and condition variable. | `Database` factory function. | C++ wrapper tasks and database helpers. |
| `ConnectionPool::borrow()` | Blocks until a connection is available, moves it out of the queue, and returns it. | `condition_variable::wait`. | `PooledConnection`. |
| `ConnectionPool::unborrow(db)` | Moves a connection back into the queue and wakes one waiter. | `notify_one`. | `PooledConnection` destructor. |
| `ConnectionPool::ConnectionPool(func, pool_size)` | Constructs `pool_size` connections using a supplied factory. | The factory callback. | Application startup. |
| `ConnectionPool::curr_size()` | Returns queue size without locking. | `connections.size`. | Diagnostics. |
| `ConnectionPool::start_size()` | Returns configured pool size. | None. | Diagnostics. |
| `sqlite::PooledConnection` | Scope guard that borrows a connection on construction and returns it on destruction. | `ConnectionPool::borrow/unborrow`. | Database operations that need temporary exclusive connection ownership. |

Risk notes:

- `Statement::~Statement()` calls `sqlite3_finalize(stmt)` even if `stmt` is null after move. SQLite treats null finalization as benign in common builds, but this should be verified against the target SQLite API contract.
- `ConnectionPool::curr_size()` reads `connections.size()` without locking; it is only safe as a diagnostic in non-racing contexts.
- `Statement::exec` assumes a statement returns zero or one row. Callers must use `step` for multi-row queries.
- The wrapper retries `SQLITE_BUSY` by tight spinning; a busy timeout or backoff would be more CPU-friendly.

## C++ Database Serialization And Admin Helpers

Files:

- `src/backend/headers/database/serialize.hpp`
- `src/backend/cpp/database/serialize.cpp`
- `src/backend/headers/database/deserialize.hpp`
- `src/backend/cpp/database/deserialize.cpp`
- `src/backend/headers/database/admin.hpp`
- `src/backend/cpp/database/admin.cpp`
- `src/backend/headers/database/viewer.hpp`
- `src/backend/cpp/database/viewer.cpp`

Serialization format is intentionally compact:

- Code types serialize to table names: `oso`, `osno`, `ons`, `cs`, `cns`.
- Code sequences serialize as space-separated integers through `operator<<`.
- Initial angles serialize as two symbol characters such as `xy`.
- Rational points serialize as `x y`, one point per line for polygons.
- Trig equations serialize as triples `trig_coeff x_coeff y_coeff`; several terms are space-separated and several equations are newline-separated.

| Function/Class | Purpose | Calls | Called By |
| --- | --- | --- | --- |
| `database::serialize(CodeType)` | Maps code type enum to database table name. | `invalid_enum_value` on impossible enum. | All SQL table selection. |
| `database::serialize(CodeSequence)` | Streams a code sequence to text. | `operator<<`. | Save/load SQL bindings. |
| `database::serialize(InitialAngles)` | Writes two initial-angle symbols. | `operator<<` for `XYZ`. | Save paths. |
| `database::serialize(PointQ)` | Writes rational point as `x y`. | Stream operators. | Polygon serialization. |
| `database::serialize(vector<PointQ>)` | Writes points separated by newlines. | `serialize(PointQ)`. | MRR polygon persistence. |
| `database::serialize(LinComMapZ<Trig<XY>>)` | Writes one trig equation as repeated coefficient triples. | Iterates sparse trig map. | Equation persistence. |
| `database::serialize(set<Equation<Trig>>)` | Writes several equations separated by newlines. | `serialize(equation)`. | Stable equation set persistence. |
| `database::serialize<T>(t)` string overload | Serializes into an `ostringstream` and returns a string. | The stream overload for `T`. | SQL code throughout. |
| `table_name(code_sequence)` | Convenience wrapper returning the serialized code type. | `code_sequence.type`, `database::serialize(CodeType)`. | SQL callers. |
| `database::deserialize<InitialAngles>` | Parses `xy`, `xz`, `yx`, `yz`, `zx`, `zy`. | None. | Load paths. |
| `database::deserialize<PointQ>` | Parses rational `x y` strings into exact rational coordinates. | `split`, `Rational`. | Polygon deserialization. |
| `database::deserialize<vector<PointQ>>` | Parses newline-separated rational points. | `deserialize<PointQ>`. | Polygon deserialization. |
| `parse_equation<T>` | Parses database coefficient triples into a trig equation map. | `split`, `boost::lexical_cast`, `LinComArrZ`, `LinComMapZ::add`. | Equation specializations. |
| `database::deserialize<Equation<Sin>>` | Parses one sine equation. | `parse_equation<Sin>`. | Load paths. |
| `database::deserialize<Equation<Cos>>` | Parses one cosine equation. | `parse_equation<Cos>`. | Load paths. |
| `parse_equations<T>` | Parses newline-separated equations into a set. | `parse_equation<T>`. | Equation-set specializations. |
| `database::deserialize<set<Equation<Sin>>>` | Parses sine equation set. | `parse_equations<Sin>`. | Stable load paths. |
| `database::deserialize<set<Equation<Cos>>>` | Parses cosine equation set. | `parse_equations<Cos>`. | Stable load paths. |
| `database::create(db_path)` | Creates the five code-type tables with columns for computed MRR/viewer data. | `sqlite::Database`, `prepare`, `bind`, `exec`. | Admin setup. |
| `database::clear(db_path)` | Deletes all rows from the five code-type tables. | `sqlite::Database`, `prepare`, `exec`. | Admin cleanup. |
| `convert_points(points)` | Converts interval points to midpoint double strings for Java display. | `boost::multiprecision::median`. | `save_impl`. |
| `convert_equations(equations)` | Joins equation strings with newlines. | String append. | `save_impl`. |
| `convert_left_rights(left_rights)` | Serializes left/right vertex pairs as four integers per line. | Stream output. | `save_impl`. |
| `database::in(code_seq, code_type, db)` | Checks whether a code sequence exists in its code-type table. | SQL `select exists`. | Database algorithms/wrapper. |
| `save_impl(code_seq, code_type, info, db)` | Inserts stable or unstable info computed from scratch, using `insert or ignore` to tolerate duplicate concurrent saves. | Serialization helpers, SQL insert. | `database::save` stable/unstable overloads. |
| `database::delete_from_db(...)` | Deletes one row by code sequence. | SQL delete. | Wrapper/admin cleanup. |
| `database::save(code_seq, code_type, Stable/Unstable, db)` | Public save overloads for direct computed data. | `save_impl`. | Calculation pipeline. |
| `save_impl(base_code_seq, code_seq, code_type, info, db)` | Inserts an LR-derived row and records the base LR code sequence. | Serialization helpers, SQL insert. | LR save overloads. |
| `database::save(base_code_seq, code_seq, code_type, Stable/Unstable, db)` | Public save overloads for LR-derived data. | LR `save_impl`. | Left/right test/use workflows. |
| `database::load_picture(...)` | Loads initial angles, points, and equations for rendering. | SQL select. | Native wrapper load-picture calls. |
| `database::load_info(...)` | Loads render data plus left/right data and LR base code. | SQL select. | Native wrapper info calls. |
| `parse_left_rights(str)` | Parses serialized four-integer left/right rows into `LeftRight` objects. | `split`, `boost::lexical_cast`. | `load_left_rights`. |
| `database::load_left_rights(...)` | Loads and parses left/right data for one code sequence. | SQL select, `parse_left_rights`. | Left/right workflows. |
| `Picture` | Simple C++ DTO carrying initial-angle, point, and equation strings. | None. | `load_picture`, C wrapper structs. |
| `Info` | Simple C++ DTO carrying picture strings plus left/right metadata. | None. | `load_info`, C wrapper structs. |
| `StableRef` | Pair of `LeftRight` and original index used to rotate stable data into canonical left/right order. | Comparison operators delegate to `LeftRight`. | `Stable` constructor. |
| `Stable` | Viewer-facing stable data: initial angles, interval points, equation strings, and left/right list rotated to minimal order. | `falgo::min_rotation`, `falgo::for_each`. | Database save paths. |
| `Unstable` | Viewer-facing unstable data with exactly two endpoints/equations/left-right entries, sorted by `LeftRight`. | `LeftRight` comparison. | Database save paths. |

Risk notes:

- `database::save(base_code_seq, ...)` uses plain `insert`, not `insert or ignore`; concurrent LR saves can still conflict.
- `Stable::operator<<` and `Unstable::operator<<` write equations to `std::cout` instead of the supplied stream `os`.
- `parse_left_rights` rejects any row that does not split into exactly four tokens, so blank trailing lines would be fatal if serialization ever emits them.

## Java PatternFinder Package

Files:

- `src/java/patternfinder/PatternFinder.java`
- `src/java/patternfinder/PatUtils.java`
- `src/java/patternfinder/SuperCheckTask.java`
- `src/java/patternfinder/SearchWindow.java`
- `src/java/patternfinder/OneCodeWindow.java`
- `src/java/patternfinder/Single.java`
- `src/java/patternfinder/Triple.java`
- `src/java/patternfinder/Spattern.java`
- `src/java/patternfinder/Tpattern.java`
- `src/java/patternfinder/ThreeState.java`

PatternFinder is a JavaFX tool for discovering arithmetic families of code sequences. It compares given codes pairwise; compatible differences define a pattern vector. A pattern says which positions should be incremented by 2 per iteration. The base is chosen so generated codes can be represented as `base + 2*n*pattern`.

### Data Objects

| Class/Method | Purpose | Calls | Called By |
| --- | --- | --- | --- |
| `Single` constructor | Stores one parsed immutable code list. Private to enforce optional factory. | None. | `Single.create`. |
| `Single.create(line)` | Trims/parses one code line into a `Single`. | `Utils.tripleTrimmer`, `Utils.splitString`. | `PatternFinder.fireCalcBtn`. |
| `Single.getCode()` | Returns the immutable code list. | None. | Pattern comparison and output. |
| `Single.isEmpty()` | Reports whether the code list is empty. | `code.isEmpty`. | `singAction`. |
| `Single.setCoef(newCoef)` | Stores a positive coefficient once. | None. | Currently little-used. |
| `Single.compareTo(...)` | Sorts singles by code length then lexicographic integer list comparison. | `PatUtils.intListCompare`. | Sorted pattern output. |
| `Single.toString()` | Prints `(coef)` plus code list. | `PatUtils.printImm`. | Debug/output. |
| `Triple(String line)` | Parses a comma-separated triple of code lists. | `Utils.splitString`. | `Triple.create`. |
| `Triple.create(line)` | Optional factory around the throwing constructor. | `new Triple`. | `PatternFinder.fireCalcBtn/fireDownBtn`. |
| `Triple.getCode(i)` | Returns component `i`. | None. | Pattern comparison and output. |
| `Triple.getCodes()` | Returns all three immutable lists. | None. | `Tpattern.setBase`. |
| `Triple.setCoef(newCoef)` | Stores a positive coefficient once. | None. | Currently little-used. |
| `Triple.compare(t1,t2)` | Compares triples by component lengths. | `getCode`. | `compareTo`, `PatternFinder.subtCodes`. |
| `Triple.compareTo(...)` | Sorts triples by `compare`, then first-code lexicographic order. | `Triple.compare`, `PatUtils.intListCompare`. | Sorted pattern output. |
| `Triple.toString()` | Prints three code lists separated by commas. | `PatUtils.printImm`. | Output. |
| `Spattern(pat, ex)` | Represents one single-code arithmetic pattern and its derived base. Rejects all-zero patterns. | `makeBase`. | `PatternFinder.subtCodes`. |
| `Spattern.makeBase(p, ex)` | Computes the smallest base for a pattern/example pair by finding the minimum valid coefficient across nonzero pattern positions. | Integer arithmetic. | Constructor. |
| `Spattern.same(p1,p2)` | Static equality helper comparing pattern and base. | Getters. | Potential callers; equality override is primary. |
| `Spattern.size/getPat/getBase` | Accessors. | None. | Pattern output and triple composition. |
| `Spattern.getN(single)` | Computes the iteration index `n` for a single code by comparing the first nonzero pattern position to the base. | Integer arithmetic. | `singAction`. |
| `Spattern.equals/hashCode/toString` | Defines map identity and printable pattern/base blocks. | `PatUtils.printPat`, `printImm`. | Pattern maps and output. |
| `Tpattern()` | Empty constructor for a three-component pattern. | None. | `PatternFinder.subtCodes(Triple,Triple)`. |
| `Tpattern.makeBase(ex)` | Computes bases for all three triple components using the shared minimum coefficient over all nonzero pattern positions. | Integer arithmetic. | `setBase`. |
| `Tpattern.setPat(Spattern/i)` | Copies a single-component pattern into component `i`. | `Spattern.getPat`. | Triple subtraction. |
| `Tpattern.setPat(ImmutableIntList/i)` | Directly assigns component pattern `i`. | None. | Potential external construction. |
| `Tpattern.setBase(ex)` | Computes and stores all three bases. | `makeBase`. | Triple subtraction. |
| `Tpattern.size/getPat/getBase` | Accessors by component index. | None. | Pattern output/comparison. |
| `Tpattern.patString/baseString` | Prints triple pattern/base strings. | `PatUtils.printPat`, `printImm`. | `tripAction`. |
| `Tpattern.getN(triple)` | Computes iteration index using component 0’s first nonzero pattern position. | Integer arithmetic. | `tripAction`. |
| `Tpattern.equals/hashCode/toString/compareTo` | Defines identity and sorting for triple patterns. `compareTo` only distinguishes equal vs non-equal. | `PatUtils` print helpers. | Pattern map/output. |
| `ThreeState` | Tiny enum `TRUE/FALSE/UNSET` used to track subtraction sign consistency. | None. | `PatternFinder.subtCodes(ImmutableIntList, ImmutableIntList)`. |

### Pattern Utilities

| Function | Purpose | Calls | Called By |
| --- | --- | --- | --- |
| `PatUtils.trimCodeLine(line)` | Removes comments and display decorations from a code line. | Regex splitting. | Pattern input parsing. |
| `PatUtils.tripleTrimmer(line)` | Preserves bare triple lines while otherwise delegating to `trimCodeLine`. | `trimCodeLine`. | Pattern input parsing. |
| `PatUtils.removeEmpty(array)` | Removes empty/blank strings from an array. | ArrayList. | Utility callers. |
| `PatUtils.listGCD(l)` | Normalizes an integer vector by its gcd. | `listGCD(l,1)`. | Pattern subtraction. |
| `PatUtils.listGCD(l, coef)` | Divides entries by gcd and scales by `coef`. | `GCD`. | Pattern subtraction. |
| `PatUtils.GCD(a1,a2)` | Computes gcd by repeated subtraction. | None. | `listGCD`. |
| `PatUtils.printAndTestTrip(trip,pool)` | Prints a triple, prefixing components that fail empty verification. | `emptyVerify`, `printImm`. | `tripAction`. |
| `PatUtils.repeat(str,times)` | Returns `str` repeated `times`. | `new char[times]`. | `printPat`. |
| `PatUtils.printImm(imm)` | Prints an immutable int list as space-separated numbers. | Iteration. | Output throughout patternfinder. |
| `PatUtils.printPat(pat)` | Converts a compact count-vector pattern into repeated 1-based positions. | `repeat`. | Output and extension lines. |
| `PatUtils.intListCompare(l1,l2)` | Lexicographic integer-list comparison, assuming compatible lengths. | None. | Sorting and pattern sign checks. |
| `PatUtils.emptyVerify(pat,pool)` | Calls native/database save path and returns whether the code is non-empty/computable. | `Wrapper.saveToDatabase`. | Verification options. |
| `PatUtils.emptyVerifyLR(base,code,pool)` | Checks LR-derived picture existence for a base/code pair. | `ClassifiedCodeSequence.create`, `Wrapper.loadPictureLR`. | `SuperCheckTask`. |
| `PatUtils.addImm(code,pat,times)` | Adds `2*times` to each 1-based position listed in `pat`. | Mutable int array/list conversion. | Extension and super-check workflows. |
| `PatUtils.xtndValidate(line)` | Validates `code # pattern` extension lines for singles or triples. Ensures pattern indices fit code lengths. | `Utils.tripleTrimmer`, `splitString`. | `fireCalcBtn`, `fireExtendBtn`, `fireDownBtn`. |
| `PatUtils.splitString(textCodeSeq)` | Parses whitespace-separated integers into an immutable int list. | `Integer.parseInt`. | Patternfinder parsing. |

Risk notes:

- `PatUtils.GCD(0, nonzero)` returns `0` if the first argument is zero. This can make `listGCD` divide by zero for all-zero or certain zero-leading arrays. `Spattern` rejects all-zero patterns later, but earlier normalization can still be fragile.
- `intListCompare` assumes the two lists have the same size.
- `addImm` treats `pat` as a list of 1-based positions, not a count vector. This is correct for extension-line patterns printed by `printPat`, but different from the compact gcd vector used internally.

### PatternFinder Window

| Method | Purpose | Calls | Called By |
| --- | --- | --- | --- |
| `PatternFinder(...)` | Builds the main Pattern Finder JavaFX window, input/output areas, code type filters, buttons, duplicate-removal importer, search windows, and calculation workflows. | `Utils` UI helpers, `fire*` methods, `SearchWindow`, `OneCodeWindow`. | Application menu/startup. |
| `start()` | Shows the main PatternFinder window. | `Stage.show`. | Application startup. |
| `fireCalcBtn()` | Parses input lines into singles, triples, and extension tasks; validates code types; finds patterns; verifies empties if requested; and writes grouped output. | `Single.create`, `Triple.create`, `PatUtils.xtndValidate`, `ClassifiedCodeSequence.create`, `singAction`, `tripAction`, `xtndAction`. |
| `fireDownBtn()` | Reformats raw input into PatternFinder result blocks without searching for new patterns. | `header`, `Single.create`, `Triple.create`, `PatUtils.xtndValidate`. |
| `fireSuperCheckBtn(showProgress)` | Runs `SuperCheckTask` on the last calculated result, removes failed pattern blocks, optionally reports how many were removed, and optionally cleans output. | `SuperCheckTask`, `Progress`, `fireCleanBtn`. |
| `fireExtendBtn()` | Adds a leading `+` to valid extension lines that do not already have one. | `PatUtils.xtndValidate`. |
| `fireCleanBtn()` | Extracts clean single/triple code lines from the result field, skipping empty results and comments. | Regex splitting. |
| `correctType(type)` | Checks the selected code-type filters. | CodeType comparisons. | `fireCalcBtn`. |
| `xtndAction(lines,eMin,eMax,pool,verify)` | Expands explicit `code # pattern` lines from `n=eMin` to `n=eMax-1`, optionally verifying non-empty codes. Handles singles and triples. | `PatUtils.addImm`, `Utils.standard`, `PatUtils.emptyVerify`, `PatUtils.printImm`. |
| `singAction(lines,minLen,printing,pool,verify)` | Pairwise compares single codes, groups compatible arithmetic patterns, filters groups by minimum length, and prints according to selected output mode. | `subtCodes`, `Spattern.getN`, `Utils.standard`, `PatUtils.emptyVerify`, `createNs`. |
| `keySetContains(map,key)` | Manual map-key lookup for `Tpattern`, used because normal key lookup was unreliable with the existing equality/hash behavior. | `Tpattern.equals`. | `tripAction`. |
| `tripAction(lines,minLen,printing,pool,verify)` | Pairwise compares triples, groups compatible triple patterns, filters by minimum length, and prints triples or extension lines. | `subtCodes(Triple,Triple)`, `keySetContains`, `PatUtils.printAndTestTrip`, `createNs`. |
| `header(pat,code,num)` | Builds a standard result block header. | `PatUtils.printImm`. | `fireDownBtn`. |
| `createNs(ns)` | Compresses sorted iteration indices into strings like `1 -> 5, 7`. | Mutable int list access. | `singAction`, `tripAction`. |
| `subtCodes(Triple,Triple)` | Computes a compatible `Tpattern` if all three component pairs differ by same-direction even changes and have matching component lengths. | `Triple.compare`, `PatUtils.intListCompare`, `subtCodes(single pair)`, `Tpattern.setBase`. |
| `subtCodes(line1,line2)` | Computes a compatible `Spattern` if two same-length codes differ by nonzero even changes all in one direction. Normalizes by gcd and derives base. | `PatUtils.listGCD`, `Spattern`. |

### SuperCheckTask

| Method | Purpose | Calls | Called By |
| --- | --- | --- | --- |
| `SuperCheckTask(inputText, lr, len, pool)` | Splits result blocks into parallel callable checks. Each block extends its base by `len` iterations and verifies that the extrapolated code still exists, either ordinary or LR-based. | `PatUtils.addImm`, `emptyVerify`, `emptyVerifyLR`, `Utils.splitString`. | `PatternFinder.fireSuperCheckBtn`. |
| `call()` | Runs all check callables on a fixed thread pool, accumulates kept blocks, writes removed blocks to `tmp/superFails.txt`, and updates task progress. | `Executors`, futures, `Utils.writeToFile`. | JavaFX task executor. |

### Search And One-Code Windows

| Method | Purpose | Calls | Called By |
| --- | --- | --- | --- |
| `SearchWindow(...)` | Builds a small search UI for database lookup by exact code length or even/odd pattern and code type. | `Wrapper.search`, `parseEvenOdds`, JavaFX alerts. | PatternFinder search button. |
| `SearchWindow.parseEvenOdds(code)` | Accepts an `O`/`E` pattern directly or converts a numeric code line into parity letters. | `Integer.parseInt`. | Search button handler. |
| `SearchWindow.close/show` | Closes or shows the stage. | JavaFX Stage. | UI callers. |
| `OneCodeWindow(...)` | Builds a tool that derives extension patterns for one code or triple based on configurable value intervals and coefficients. | `PatUtils.splitString`, `Utils.tripleTrimmer`. | PatternFinder one-code button. |
| `OneCodeWindow.close/show` | Closes or shows the stage. | JavaFX Stage. | UI callers. |

Optimization notes:

- PatternFinder pairwise search is `O(n^2)` for singles and triples. For large lists, hash by length/sign-normalized difference shape before pairwise grouping.
- `PatUtils.GCD` uses repeated subtraction; Euclidean modulo would be much faster and safer.
- Many string-building paths use `+=` in loops; `StringBuilder` should be used consistently.
- `SuperCheckTask` creates its own executor inside `call`; cancellation only cancels futures after loop checks. A cooperative cancellation strategy would make long checks more responsive.
