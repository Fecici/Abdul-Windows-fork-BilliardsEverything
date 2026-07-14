# Java Core And Frontend

This file documents the Java side: code-sequence model, database parsing, JNA wrapper, geometry, vary ports, viewer/task organization, and patternfinder tools.

Current baseline note: Abdul's non-viewer Java core remains effectively the older source implementation. Abdul changes viewer behavior by defaulting reflection on and defaulting several small-cover checkboxes off; `[MAIN]` has additional viewer/runtime differences including updater removal, `HashTriple` memory behavior, and `replacePolygons`. See `18-main-vs-abdul-fork-comparison.md`.

## Package Map

| Package | Purpose |
| --- | --- |
| `billiards.codeseq` | Java validation/classification and stable/unstable storage model |
| `billiards.math` | Java-side symbolic equation and linear-combination model |
| `billiards.geometry` | Display/runtime geometry: vectors, intervals, polygons, rectangles, triangle unfoldings |
| `billiards.database` | Parse native/database strings into Java model objects; direct SQLite helpers |
| `billiards.wrapper` | JNA bindings to C++ backend |
| `billiards.vary` | Java vary implementations and wrapper calls to C++ vary ports |
| `billiards.viewer` | JavaFX UI, rendering, task orchestration, cover/vary windows |
| `patternfinder` | Separate UI/helpers for finding and testing code patterns |

## Code Sequence Model

### `src/java/billiards/codeseq/CodeSequence.java`

This class is immutable from the public API perspective. It stores an `ImmutableIntList` named `codeNumbers` and canonicalizes input in the private constructor.

Function-level notes:

| Function | Purpose | Calls | Called by |
| --- | --- | --- | --- |
| Private constructor | Eliminates repeated legal periods and chooses standard order. | `eliminateRepeaters`, `standardOrder` | `create` |
| `create` | Validates dirty list and returns `Either<InvalidCodeSequence,CodeSequence>`. | `validate`, constructor | UI, wrapper parsing, tests |
| `subList` | Workaround for primitive-list missing subList. | loop | classification, repeat detection |
| `isRepeated` | Checks whether code is repeated blocks of a prefix. | `subList` | `eliminateRepeaters` |
| `validate` | Rejects empty, nonpositive, or illegal patterns. | `isLegal` | `create` |
| `eliminateRepeaters` | Reduces to shortest repeated legal sub-code. | `isRepeated`, `isLegal`, `subList` | constructor |
| `evenOddSequence(IntList)` | Converts parity to `E/O` string. | loop | UI/search |
| `evenOddSequence(List<Integer>)` | Same for boxed Java lists. | loop | UI/search |
| `nextAngle` | Applies parity transition among `XYZ` labels. | `XYZ.otherAngle` | `isLegal` |
| `isLegal` | Walks parity transitions and requires return to `(X,Y)`. | `nextAngle` | `validate`, repeat detection |
| `standardOrder` | Chooses lexicographically minimal rotation or reversed rotation. | `rotateLeft`, `compareIntList` | constructor |
| `compareIntList` | Total order by length then lexicographic entries. | loops | standard order and compare |
| `rotateLeft` | Mutates a mutable primitive list one step left. | manual loop | standard order |
| `toString` | Space-separated sequence. | `makeString` | UI/DB |
| `hashCode` | Delegates to immutable list. | list hash | collections |
| `equals` | Casts and compares underlying list. | list equality | collections |
| `compareTo` | Orders by `compareIntList`. | `compareIntList` | sorted sets |

Design note: `equals` assumes `obj` is a `CodeSequence`; passing another type throws `ClassCastException`. The comments say this was an intentional bug-surfacing choice.

### `src/java/billiards/codeseq/ClassifiedCodeSequence.java`

Wraps a `CodeSequence` and caches code type, length, sum, stable flag, and odd-even pattern.

Function-level notes:

| Function | Purpose | Calls | Called by |
| --- | --- | --- | --- |
| Private constructor | Computes all cached classification fields. | helpers below | `create` |
| `create` | Validates/canonicalizes then classifies. | `CodeSequence.create`, constructor | UI/wrapper/vary |
| `calculateOddEvenPattern` | Produces `E/O` string. | loop | constructor |
| `calculateCodeType` | Computes `OSO/OSNO/ONS/CS/CNS`. | `isOdd`, `isClosed`, `isStable` | constructor |
| `calculateCodeSum` | Checked long sum of code numbers. | `Math.addExact` | constructor |
| `isStableCodeType` | Stable type predicate. | switch | constructor/UI |
| `isOdd` | Odd length test. | size | type |
| `isClosed` | Closed-code symmetry test around opposite even entries. | `CodeSequence.subList`, reverse | type |
| `isStable` | Alternating signed sum by evolving `XYZ` labels. | `XYZ.otherAngle`, `Math.addExact/subtractExact` | type |
| `length` | Code length. | field/list size | UI |
| `toString` | Delegates to `CodeSequence`. | `toString` | UI/DB |
| `equals` | Compares underlying code sequence. | `CodeSequence.equals` | collections |
| `hashCode` | Delegates to code sequence. | hash | collections |
| `compareTo` | Delegates to code sequence ordering. | `compareTo` | sorted sets |

`isStable` mirrors the C++ `constraint` idea but does not build an explicit `XYEta` expression. It accumulates coefficients of `X,Y,Z` and checks all are zero.

### `src/java/billiards/codeseq/Storage.java`

`Storage` is an abstract Java substitute for a sum type:

- `Storage.Stable`: two-dimensional MRR polygon.
- `Storage.Unstable`: one-dimensional MRR line segment plus constraint.

Function-level notes:

| Function | Purpose |
| --- | --- |
| Private constructors | Store classified code and equation list, optionally raw points string. |
| `Stable` constructors | Store stable polygon. |
| `Stable.intersects(Rectangle/ConvexPolygon)` | Delegate to polygon intersection. |
| `Stable.getMin/MaxX/Y` | Bounding box from polygon vertices. |
| `Unstable` constructors | Store constraint and line segment. |
| `Unstable.intersects(Rectangle/ConvexPolygon)` | Delegate to line segment intersection. |
| `Unstable.getMin/MaxX/Y` | Bounding box from segment endpoints. |
| `isPositive` | Evaluates all equations at a point and requires nonnegative. |
| `isPositiveProver` | Uses equation bounds over square radius/offset. |
| `codeType`, `codeLength`, `codeSum`, `oddEvenPattern` | Delegates to cached classification. |
| `compareTo`, `toString`, `equals`, `hashCode` | Uses classified code sequence identity. |

Viewer rendering and vary tasks work against `Storage`, so they can treat stable and unstable objects uniformly until geometry-specific rendering is needed.

## Java Geometry

### `src/java/billiards/geometry/TriangleBilliard.java`

Used by Java `VaryCS` and `Vary3`.

Function-level notes:

| Function | Purpose |
| --- | --- |
| Private constructor | Store vertices, current side, and orientation. |
| `create` | Construct base triangle from angles and starting side position. |
| `getNext(left)` | Reflect the triangle left or right in the unfolding. |
| `getSpecialAngle` | Angle from shot origin to vertex `C`; this splits the beam. |
| `toString` | Debug representation. |

### `src/java/billiards/geometry/TriangleBilliard4.java`

Used by Java `Vary4`. It tracks trails of previous left/right vertices so it can compute beam bounds from worst lines.

Function-level notes:

| Function | Purpose |
| --- | --- |
| Continuation constructor | Copy triangle plus left/right trails and recompute `specMin/specMax`. |
| Initial constructor | Build start triangle and initialize trails with A/B. |
| `create` | Construct base triangle from angles. |
| `getNext(left)` | Return reflected triangle if that side remains visible under current beam bounds. |
| `getSpecialAngle` | Angle to vertex `C`. |
| `between` | Checks if a candidate perfect angle is inside `(specMin,specMax)`. |
| `interval` | Width of surviving beam interval. |
| `reconfigure` | Drops trail points that no longer determine worst-line bounds. |
| `atan3` | `atan2` with negative-angle clamping. |
| `mod3` | Robust side-label modulo. |
| `toString` | Debug representation including trails. |

### `src/java/billiards/geometry/ConvexPolygon.java`

Represents display polygons and intersection tests.

Function-level notes:

| Function | Purpose |
| --- | --- |
| Private constructor | Store immutable vertex list. |
| `create` | Validates at least 3 points and convexity. |
| `giftWrapCheck` | Attempts convexity check by local angle consistency. |
| `location` | Classifies point as inside, outside, or boundary using side signs. |
| `sign` | Orientation sign of point relative to an edge. |
| `separatingAxis` | SAT separating-axis test against another projectable figure. |
| `intersects(ConvexPolygon)` | Polygon intersection by SAT both ways. |
| `intersects(Rectangle)` | Rectangle/polygon intersection using axis projections and polygon axes. |
| `projectX` / `projectY` | Project polygon onto coordinate axes. |
| `project` | Project polygon onto arbitrary axis. |
| `hashCode`, `equals`, `toString` | Vertex-list identity and debug output. |

Risk: `giftWrapCheck` uses `acos(dot/(norms))`; duplicate points can yield division by zero and NaN. Some logged examples in comments show zero vectors.

## Database Conversion

### `src/java/billiards/database/Database.java`

This class bridges native/database text into Java model objects and also contains direct SQLite helpers for UI-only tables.

Function-level notes:

| Function | Purpose | Calls | Called by |
| --- | --- | --- | --- |
| `findConstraintEta` | Java version of unstable constraint in `XYEta`. | `XYZ.otherAngle`, `LinCom` normalization | diagnostics/storage |
| `findConstraint` | Constraint in `XYPi` for Java `Storage.Unstable`. | same as above | `convertToStorage` |
| `parseInitialAngles` | Parses strings like `xy`, `xz`, ... into `XYZ` pair. | switch | `convertToStorage` |
| `parsePoints` | Parses newline-separated `x y` doubles into `Vector2` list. | `StringUtils.split` | `convertToStorage` |
| `parseEquations` | Parses serialized equation lines into `SinEquation` or `CosEquation`. | constructors | `convertToStorage` |
| `parseLeftRights` | Parses native left/right vertex references. | `LeftRight` constructor | show LR workflows |
| `convertToStorage` | Converts `Picture/Info` string fields into `Storage.Stable` or `Storage.Unstable`. | parse helpers, `findConstraint` | load methods |
| `loadStorage` | Loads normal picture through native wrapper and converts to storage. | `Wrapper.loadPicture` | Viewer/tasks |
| `loadStorageShowLR` | Loads info including left/right references. | `Wrapper.loadInfo`, `parseLeftRights` | Viewer show-LR |
| `loadStorageUseLR` | Loads picture using specified LR data. | `Wrapper.loadPictureLR` | Viewer use-LR |
| `exists` | Direct SQLite existence check in type table. | JDBC | manual save |
| `saveToDatabase` | Directly insert code sequence into type table if missing. | `exists`, JDBC | UI/manual flows |
| `deleteBaseFromDatabase` | Empty placeholder. | none | none currently |
| `saveTripleToDatabase` | Creates and inserts into `triple` table. | JDBC | cover/pattern flows |
| `codeAndOEMatch` | Validates parity pattern matches code sequence. | string parsing | iteration pattern save |
| `saveIterationPatternToDatabase` | Upserts iteration pattern and last-used timestamp. | `codeAndOEMatch`, JDBC | Viewer iteration UI |
| `lookUpIterPatByCodeSeq` | Retrieves iteration patterns by exact code sequence. | JDBC, `getPatternsFromDB` | lookup UI |
| `lookUpIterPatByOEPat` | Retrieves distinct iteration patterns by parity pattern. | JDBC, `getPatternsFromDB` | lookup UI |
| `getPatternsFromDB` | Reads `iter_pattern` column into `ArrayList`. | JDBC ResultSet | lookup helpers |

Correctness risk: `codeAndOEMatch` appears to use the outer index `i` inside the inner `j` loop. See `05-risks-optimizations.md`.

### `src/java/billiards/database/Info.java`, `Picture.java`, `InfoAll.java`

These classes copy native JNA pointer fields into Java `String` fields.

| Class | Purpose |
| --- | --- |
| `Picture` | Minimal display data: initial angles, points, equations |
| `Info` | Display data plus left/right lists and LR code sequence |
| `InfoAll` | Extended equation/vector data for all-equation and verification views |

They should be constructed before native cleanup is called.

## JNA Wrapper

### `src/java/billiards/wrapper/Wrapper.java`

This is the Java surface for native C++ calls. `Native.register("backend")` binds methods to the C++ shared library.

Function groups:

| Group | Java methods |
| --- | --- |
| Database setup | `errorLogging`, `createDatabase`, `clearDatabase` |
| Pool lifecycle | `createConnectionPool`, `destroyConnectionPool` |
| Cancellation | `backend_cancel` |
| Cover checks | `coverWrapper`, `smallCoverWrapper`, `coverWrapperDuplicateStables`, `coverWrapperHalfDuplicateStables`, `coverWrapperAll`, `getNotFilledCoordinates` |
| Load/save/delete | `saveToDatabase`, `deleteFromDatabase`, `loadPicture`, `loadPictureLR`, `loadInfo`, `loadInfoAll`, `loadAllEquation`, `loadSlopeInfo` |
| Search | `search(CodeType,int)`, `search(CodeType,String)` |
| Cover maintenance | `mergeCovers`, `trimCover`, `boundingPolygon` |
| Formula/gradient UI | `calculateGradient`, `calculateGradient2` |
| Vary C++ ports | `varyCSCpp`, `vary3Cpp`, `vary4Cpp` |

Important method notes:

| Function | Purpose | Risk/notes |
| --- | --- | --- |
| `loadPicture` | Native load plus `cleanup_cpicture`. | Good cleanup pattern. |
| `loadPictureLR` | Loads with base-code LR or expando LR string. | Uses `lr == "empty"` instead of `.equals`. |
| `loadInfo` | Native load plus `cleanup_cinfo`. | Good cleanup pattern. |
| `loadInfoAll` / `loadAllEquation` / `loadSlopeInfo` | Native load into `CInfoAll`. | No visible cleanup function for `CInfoAll`. |
| `calculateGradient` / `calculateGradient2` | Return derivative report or min-radius result. | `calculateGradient2` cleans `cstring`, not `cstring2`. |
| `varyCSCpp` | Parses native newline-separated codes in parallel into synchronized list. | Better than vary3/4. |
| `vary3Cpp` / `vary4Cpp` | Parse native codes in parallel into plain `ArrayList`. | Data race/corruption risk. |

## Java Vary Algorithms

### `src/java/billiards/vary/VaryCS.java`

Function-level notes:

| Function | Purpose |
| --- | --- |
| `iterateFireAway` | Iterative DFS version of original recursive CS beam search. |
| `doneIteration` | Pops DFS stack state and updates side sum/branch flags. |
| `removeLast` | Removes current path state from all parallel stacks. |
| `fireAway(movesMin,movesMax,x,y)` | Java implementation: build triangle and search half-depth, then mirror code. |
| `fireAway(..., reqTypes)` | New path: delegates to C++ `Wrapper.varyCSCpp`. |

### `src/java/billiards/vary/Vary3.java`

Function-level notes:

| Function | Purpose |
| --- | --- |
| `iterateFireAway` | Iterative DFS over angle intervals from one starting position. |
| `fireAway(movesMin,movesMax,x,y,pos)` | Java implementation. |
| `fireAway(..., reqTypes)` | Delegates to C++ `Wrapper.vary3Cpp`. |

### `src/java/billiards/vary/Vary4.java`

Function-level notes:

| Function | Purpose |
| --- | --- |
| `iterateFireAway` | Iterative DFS using `TriangleBilliard4` beam bounds. |
| `makeStarts` | Creates independent partial-search starts for parallelism. |
| `fireAway(movesMin,movesMax,x,y)` | Java parallel Vary4 implementation. |
| `lazySort` | Sorts starts by interval width. |
| `doneIteration` | Pops DFS state for Vary4. |
| `removeLast` | Removes current path state. |
| `fireAway(..., reqTypes)` | Delegates to C++ `Wrapper.vary4Cpp`. |

## Viewer Utilities

### `src/java/billiards/viewer/Utils.java`

Function-level notes from ctags and source inspection:

| Function | Purpose |
| --- | --- |
| `splitString` | Parse a text code sequence into immutable int list. |
| `safeShutdownExecutor` | Gracefully stop executor. |
| `convert` | Convert int list into optional classified code sequence. |
| `writeToFile`, `printToFile`, `readFromFile` | Simple file I/O helpers for UI artifacts. |
| `copyInto` | Copy primitive list contents. |
| `isCoords`, `isDouble`, `ifGet` | Parsing helpers for coordinate/text files. |
| `getIntersectionCodes` | Intersect multiple collections of classified codes. |
| `compare` | Compare string sequences across lists. |
| `standard` overloads | Repeat and canonicalize code sequences for display. |
| `getCoverCodeString` overloads | Convert `Storage` or `ClassifiedCodeSequence` into cover-file string. |
| `hex` | Convert JavaFX color to CSS hex. |
| `colorButton` | Update button colors. |
| `toolTip` | Build tooltip. |
| `trimCodeLine`, `tripleTrimmer` | Trim code/triple text lines. |
| `timeConvert` | Millisecond duration formatting. |
| `runAndWait` | Run action on JavaFX thread and wait. |
| `modN` | Safe modulo for side labels. |
| `setupCustomTooltipBehavior` | Globally adjust JavaFX tooltip delays via reflection. |
| `verifyInfo` | Checks all-equation info against storage. |
| `convertDecimalToFraction` | Approximate decimal as fraction string. |
| `calculate_formula`, `calculate_formula_mrr` | Derivative/formula reporting helpers. |
| `verifyVector` | Checks vector info against storage. |

## Viewer And Task Structure

### `src/java/billiards/viewer/Viewer.java`

`Viewer` is the central JavaFX controller. It owns:

- Main stage/window and code-entry controls.
- Current code number buffers.
- Connection pool.
- Pixel/radian map and current view rectangle history.
- Active `Storage` objects and colors.
- Cover rectangles, cover polygons, screen fills, loaded MRR bounds.
- Many secondary windows and task launchers.
- Rendering layers as `ImageView`s.

The file is very large. It should be documented line-by-line in the next pass. Current method-level map:

| Method/group | Purpose |
| --- | --- |
| Constructor | Builds all UI controls, menus, event handlers, task hooks, and initial render state. |
| `start` | Starts main viewer workflow with executor. |
| Expando helpers: `getExpandos`, `getMultipleExpandos`, `addLeftRight`, `subtractLeftRight`, `isleftRightLegal` | Generate and validate expanded repeated code/LR patterns. |
| Iteration helpers: `iterateActionWithPolyIntersect`, `iterateAction`, `iterateThru` | Generate code sequences through iterative code transformations, optionally clipped by polygon intersection. |
| Vary loading: `recurseDrawVaryL`, `drawVaryL`, `varyLFunction`, `queuedVaryTask` | Generate/load/render vary results over coordinate lists. |
| File loading: `LoadFileAction`, `parseOBOFile`, `parseFile` | Load saved code/coordinate files into viewer state. |
| Drawing task entry: `drawCodes`, `drawRegion`, `drawSearch`, `drawAndAddToCover` | Load and render selected storage objects. |
| Navigation: `zoomAction`, `zoom`, `pan`, `click`, `moveScreen` | Map user interactions to view rectangle changes and info lookup. |
| Rendering: `redoFromScratch`, `drawFills`, `renderRegions`, `renderGuideLines`, `renderPolygon`, `renderRect`, `renderRectLoad`, `renderUnstable`, `renderRegion` | Build JavaFX images from storage/cover geometry. |
| UI setup: `makeRightScrollPane`, `setupButtons`, `synchronize`, `calculateCurrentCodeNumbers` | Build/update controls and synchronized code fields. |
| Poly/auto vary: `superPolyVaryFunction`, `autoPolyVaryFunction`, `polyVaryFunction`, `drawPolyVary`, `drawAutoPolyVary`, `autoRecurse` | Automated vary and cover workflows over polygonal regions. |
| Iteration pattern DB: `handleIterationIntersect`, `getCodeSeqAndOEString`, `addToIterToLimitCover` | Save and use iteration patterns. |
| Arithmetic UI: `addSubtract`, `addSubtractReverse`, `increase`, `decrease`, `createVector`, `addMultiple` | Manipulate current code sequence fields. |
| Cover loading: `loadCover`, `loadCoverWithoutTrim` | Load and render cover directory output. |

### Task Classes

Important task classes in `src/java/billiards/viewer`:

| Class | Purpose |
| --- | --- |
| `DrawPictureTask` | Load and draw normal picture data. |
| `DrawPictureTaskShowLR` | Load with left/right data for display. |
| `DrawPictureTaskUseLR` | Load using existing LR witnesses. |
| `DrawPictureTaskTriples` | Draw triple-related data. |
| `DontDrawPictureTask` | Load/check without rendering. |
| `PolyVaryTask` | Vary over polygon region. |
| `VaryLTask` | Long vary over coordinate lists and optional cover insertion. |
| `CycleVaryTask` | Cycle-vary work. |
| `Progress`, `ProgressMultiTask`, `ProgressWithStatus` | JavaFX progress-window helpers. |
| `PriorityExecutor`, `PriorityCallable`, `PriorityRunnable` | Priority scheduling wrapper. |

### `src/java/billiards/viewer/VaryLTask.java`

Function-level notes:

| Function | Purpose |
| --- | --- |
| Constructor | Captures coordinate list, max bounds, flags, executors, DB pool, and viewer context. |
| `call` | Main JavaFX `Task` body for generating/filtering/loading vary results. |
| `addProcessedCode` | Static helper to record processed code in output buckets. |
| `checkStatus` | Handles future result/error status. |
| `autoCodesFiltered` | Gets vary codes at coordinate and filters according to cover/printing state. |
| `loadStorage(ClassifiedCodeSequence)` | Loads storage for one code. |
| `getPartials`, `getPartialProperty` | Expose partial results for UI binding. |
| `addToIterToLimitCover` | Adds code to iteration-limit cover UI. |
| `printMidFirstLast` | Selects first/mid/last codes for printing. |
| `loadStorage(...)` | Batch load helper. |
| `printAndLoadStorage(...)` | Print selected codes and load them for drawing. |

### `src/java/billiards/viewer/CycleVaryWindow.java`

Function-level notes:

| Function | Purpose |
| --- | --- |
| Constructor | Builds cycle-vary control window. |
| `getCoordinatesHBox` | UI for coordinate file/line controls. |
| `show` | Display window. |
| `getMagnificationIsSelected`, `getMagnification`, `getUseReps` | Read window options. |
| `allPositiveIsSelected`, `plusMinusIsSelected` | Read cover-addition options. |
| `getModesHBox`, `getMode`, `getNumGroupToPrint` | Read printing mode. |
| `getLineNavigateHBox`, `moveScreenToLine`, `setLineNumber` | Navigate coordinate file by line. |
| `extractNumberFromTextField`, `getLineNumber`, `getCoordinatesListLength` | Parse and validate numeric controls. |
| `getStartStepEnd` | Parse auto-vary range. |
| Error helpers | Show JavaFX alerts for bad line/range/number inputs. |
| `CycleVaryFunction` | Manual cycle-vary trigger. |
| `shutdown` | Stop cycle/repetition executors and close progress. |
| `autoCycleVaryFunction` | Automated cycle-vary over polygon settings. |
| `drawCycleVary` | Execute and draw cycle-vary outputs. |

## Pattern Finder

Package `src/java/patternfinder` is a separate UI/toolset for finding extension and triple/single patterns.

Important classes:

| Class | Purpose |
| --- | --- |
| `PatternFinder` | Main patternfinder UI and actions. |
| `PatUtils` | Parsing, GCD, validation, printing helpers. |
| `SearchWindow` | Search UI by length/parity/type. |
| `OneCodeWindow` | Single code interaction window. |
| `Single`, `Triple` | Parsed single/triple code models. |
| `Spattern`, `Tpattern` | Single/triple pattern models. |
| `SuperCheckTask` | Background task to verify many patterns. |
| `ThreeState` | Boolean/unknown enum. |

Function-level notes for `PatUtils`:

| Function | Purpose |
| --- | --- |
| `trimCodeLine`, `tripleTrimmer`, `removeEmpty` | Normalize raw text lines. |
| `listGCD`, `GCD` | Compute integer GCDs and normalized pattern vectors. |
| `printAndTestTrip` | Print triple and verify against DB/native pool. |
| `repeat`, `printImm`, `printPat` | Formatting helpers. |
| `intListCompare` | Primitive-list comparison. |
| `emptyVerify`, `emptyVerifyLR` | Verify emptiness/left-right conditions through backend. |
| `addImm` | Add repeated pattern to base code. |
| `xtndValidate` | Validate extension-line syntax. |
| `splitString` | Parse text into immutable int list. |
