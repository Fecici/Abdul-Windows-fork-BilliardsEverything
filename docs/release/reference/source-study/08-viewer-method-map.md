# `Viewer.java` Method Map

This document covers every ctags-detected method in `src/java/billiards/viewer/Viewer.java`. It is intentionally method-level, not full line-by-line commentary for the enormous constructor body. Use it with `viewer-internal-callgraph.txt` and `function-index-ctags.txt`.

Current baseline note: this map was generated from the older source tree's `Viewer.java`. Abdul adds default-on reflection handling and an `updateReflection()` path; `[MAIN]` has broader UI rearrangements and updater removal. Use this map for base structure, then apply the deltas in `17` and `18`.

## Mathematical Context

The viewer renders the triangle-angle parameter plane. A point `(x, y)` represents two triangle angles in radians; the third is `pi - x - y`. The valid triangle domain is the open simplex `x > 0`, `y > 0`, `x + y < pi`. Several viewer modes fold permutations of the three angles into a canonical chamber by sorting `(x, y, pi - x - y)` and using the two smallest values.

Stable code sequences are rendered as polygonal MRR regions plus positivity tests. Unstable code sequences are rendered as line segments constrained by a linear equation of the form `a*x + b*y + c*pi = 0`, also filtered by positivity. Cover rectangles are visual proof artifacts: they indicate portions of angle space that are certified by stable codes or stable/unstable triples.

## Generated Companion Files

- `viewer-internal-callgraph.txt` contains method line ranges and mechanically detected calls from one `Viewer` method to another.
- `function-index-ctags.txt` contains the raw ctags method boundaries for `Viewer.java` and all other source files.

The callgraph is heuristic. It is useful for navigation, but it does not understand overload resolution or Java lambdas.

## Lifecycle And Construction

| Method | Lines | Functionality | Important Interactions |
| --- | ---: | --- | --- |
| `Viewer(...)` | 569-3894 | Builds the main JavaFX window, declares and wires controls, initializes button maps and child windows, installs event handlers, and connects UI controls to the methods below. This constructor is the central frontend composition root. | Calls most action/rendering methods through JavaFX event handlers. Owns shared state such as `currentCodeNumbers`, `onScreenSequences`, `coverRects`, `map`, code input fields, cover windows, and vary windows. |
| `start(...)` | 3895-3900 | Performs initial render and shows the main window. | Calls `renderRegions`; invoked by application startup after construction. |

## Expando And Iteration Generation

| Method | Lines | Functionality | Important Interactions |
| --- | ---: | --- | --- |
| `getExpandos(...)` | 3901-3943 | Inserts a repeated integer block into a mutable code sequence at given positions for a requested number of iterations, classifies each generated sequence, and returns the valid classifications. | Calls `ClassifiedCodeSequence.create`. Mutates `workingNumbers` and `position`; consumers should not assume inputs are preserved. |
| `subtractLeftRight(...)` | 3944-3967 | Parses two newline-separated left/right-count matrices and returns `lr2 - lr1` entrywise. | Used by constructor/event handlers for left/right comparison workflows. Returns `null` if row counts differ. |
| `addLeftRight(...)` | 3968-3988 | Parses two newline-separated left/right-count matrices and returns their entrywise sum. | Used by constructor/event handlers for combining left/right data. Assumes compatible dimensions. |
| `isleftRightLegal(...)` | 3989-4003 | Checks that every parsed left/right count is nonnegative. | Used before accepting a computed left/right difference or sum. |
| `getMultipleExpandos(...)` | 4004-4110 | Expands a symbolic template containing placeholders like `A`, `B`, etc. Each placeholder is repeatedly replaced by a configured block plus itself, producing a sequence family. | Calls `ClassifiedCodeSequence.create`. The method accumulates valid classifications in insertion order without duplicates. |
| `iterateActionWithPolyIntersect(...)` | 4111-4180 | Generates iterated code sequences, computes their `Storage` objects synchronously, and returns both classifications and storages for polygon-intersection workflows. | Calls `iterateThru`, chooses one of `DrawPictureTask`, `DrawPictureTaskShowLR`, `DrawPictureTaskUseLR`, or `DrawPictureTaskUseLRTest`, calls `synchronize`, writes `iterations.txt`. |
| `iterateAction(...)` | 4181-4278 | Generates iterated code sequences and draws/loads their regions asynchronously. | Calls `iterateThru`, draw tasks, `addToOnScreenSequences`, `synchronize`, `renderRegions`, writes `iterations.txt`. |
| `iterateThru(...)` | 4279-4315 | Recursive generator for additive iteration patterns. At each depth it applies a vector scaled by each integer in a range, recurses, then reverses the change. | Calls `createVector`, `addMultiple`, and `ClassifiedCodeSequence.create`. This is effectively a lattice walk through code-sequence space. |

## VaryL And File Loading

| Method | Lines | Functionality | Important Interactions |
| --- | ---: | --- | --- |
| `recurseDrawVaryL(...)` | 4316-4547 | Processes one coordinate line at a time for VaryL/MiddleVaryL. It moves the screen, skips points already covered by the previous stable code, launches `VaryLTask`, draws partial results, appends cover text, and recurses to the next line. | Calls `moveScreen`, `addToOnScreenSequences`, `renderRegion`, `renderRegions`. Manages `storageExecutor`, `shotExecutor`, `ProgressMultiTask`, cover windows, and cancellation. |
| `drawVaryL(...)` | 4548-4579 | Reads user-selected start/step/end line bounds, loads existing cover stable strings, creates progress UI, and starts `recurseDrawVaryL`. | Calls `getStartStepEnd` and `recurseDrawVaryL`. Creates `IterateToLimitWindow` if requested. |
| `LoadFileAction(poly, all, file, executor)` | 4580-4584 | Convenience overload that loads from a file without database mode and without adding loaded codes to garbage. | Delegates to the full `LoadFileAction`. |
| `LoadFileAction(poly, all, file, executor, loadDB, addToGarbage)` | 4585-4677 | Loads code sequences from either a selected SQLite database or a text file. Database loading batches rows by selected code type; file loading delegates to `parseFile`. | Calls `drawCodes`, `parseFile`, `ClassifiedCodeSequence.create`, JDBC APIs, and cover-window append methods. |
| `drawCodes(tup, executor, all, poly)` | 4678-4684 | Convenience overload for drawing parsed codes without triples and without auto-cover insertion. | Delegates to the full `drawCodes`. |
| `drawCodes(tup, executor, all, poly, autoCover)` | 4685-4691 | Convenience overload for drawing parsed codes without triples but with optional cover insertion. | Delegates to the full `drawCodes`. |
| `drawCodes(tup, triples, executor, all, poly, autoCover)` | 4692-4950 | Computes/draws parsed code sequences and optional triples. It can run iteration specs found in input files, filter results against a polygon, load/draw storages, update cover text, zoom to a parsed rectangle, or compute storage without drawing. | Calls `iterateAction`, draw tasks, triple draw tasks, `addToOnScreenSequences`, `showProgressWindow`, `zoomAction`, and `renderRegions`. |
| `showProgressWindow(...)` | 4951-4957 | Shows a progress window only if the global limit of simultaneous progress windows has not been exceeded. | Mutates `progressWindows`. |

## Navigation, Calculation, Selection

| Method | Lines | Functionality | Important Interactions |
| --- | ---: | --- | --- |
| `zoomAction(...)` | 4958-4968 | Parses degree bounds from text fields, converts to radians, validates against `[0, pi]`, and zooms the map. | Calls `zoom`. |
| `zoom(...)` | 4969-4995 | Updates `PixelRadianMap` scale and translation for a point or rectangle, stores the view history, and rerenders. | Calls `renderRegions`. Used by click zooming, one-by-one mode, file rectangle loading, and `moveScreen`. |
| `btnCalculateAction(...)` | 4996-5043 | Parses the main code input as a single code or triple, clears current code state, validates input shape, and prints/draws the calculated result. | Calls `buttonCalulator` and `setupButtons`. |
| `buttonCalulator(...)` | 5044-5062 | Parses one code string into `currentCodeNumbers[n]`, rebuilds its plus/minus controls, and computes/draws storage information. | Calls `setupButtons` and `calculateCurrentCodeNumbers`. |
| `showEnterLineNumberError()` | 5063-5071 | Shows an alert for a missing line number. | UI helper. |
| `showEnterLineNumberErrorAutoVary()` | 5072-5080 | Shows an alert for missing AutoPolyVary line bounds. | Called by `getStartStepEnd`. |
| `showStepErrorAutoVary()` | 5081-5090 | Shows an alert when the AutoPolyVary step is less than 1. | Called by `getStartStepEnd`. |
| `showInvalidLineNumberError(...)` | 5091-5099 | Shows an alert when a requested line is outside the valid file range. | Legacy or constructor-triggered helper. |
| `showInvalidLineRangeError(...)` | 5100-5107 | Shows an alert when start/end bounds are inconsistent or outside the file range. | Called by `getStartStepEnd`. |
| `showInvalidNumberError(...)` | 5108-5116 | Shows an alert when a numeric text field cannot be parsed. | Called by `getStartStepEnd`. |
| `pan(...)` | 5117-5144 | Interprets a mouse drag as panning if movement exceeds 5 pixels, otherwise as a click. Optionally marks the screen filled if auto-fill finds no holes. | Calls `click`, `findHoles`, `renderRegions`. |
| `click(...)` | 5145-5443 | Handles selection and zoom clicks. In select mode it converts pixel to angle coordinates, finds stable/unstable regions and cover rectangles containing the point, optionally loads cover storages, updates selected polygon overlays, recolors cover rectangles, and rebuilds the right-hand selected-code panel. In zoom modes it rescales around the clicked point. | Calls `getOffset`, `makeRightScrollPane`, `renderRegions`, `Database.loadStorage`, `Storage.isPositive`, and `Storage.isPositiveProver`. |
| `makeRightScrollPane(...)` | 5444-5519 | Rebuilds the selected-code sidebar. Each selected storage gets a color button and delete button. | Calls `addToOnScreenSequences`, `renderRegion`, `renderRegions`, and recursively rebuilds itself after deletion. |
| `findHole(...)` overload | 5520-5524 | Convenience overload for hole search with an empty exclusion list. | Delegates to the full `findHole`. |
| `findHole(...)` full | 5525-5546 | Scans a pixel rectangle for a transparent pixel whose angle-coordinate point lies inside a given polygon and is not already excluded. | Reads `regionsImageView`; uses `map.radianX/Y` and `ConvexPolygon.location`. |
| `findHoles(...)` | 5547-5571 | Scans the full rendered image for transparent pixels inside a polygon and returns their angle coordinates in degrees. | Used by auto-fill and PolyVary progress reporting. |

## Rendering Pipeline

| Method | Lines | Functionality | Important Interactions |
| --- | ---: | --- | --- |
| `setImageColor(...)` | 5572-5581 | Fills a `WritableImage` with a single color. | Called by `renderColor`; loops over all `SIDE * SIDE` pixels. |
| `renderColor(...)` | 5582-5595 | Creates a solid-color `ImageView`. | Calls `setImageColor`; used for UI swatches. |
| `color(...)` | 5596-5616 | Finds the topmost stable storage whose polygon contains `(rx, ry)` and whose positivity test passes; returns its display color or transparent. | Called many times by `redoFromScratch`; uses prover or ordinary positivity depending on UI checkbox. |
| `redoFromScratch(...)` | 5617-5718 | Rebuilds the complete region image for the current view. It filters visible storages, computes stable-region colors for every pixel in parallel, optionally draws bounding fills, then draws unstable segments serially. | Calls `color`, `drawFills`, `getOffset`, `renderUnstable`. Heavy method: it submits one task per pixel. |
| `drawFills(...)` | 5719-5737 | Draws rectangles previously marked as screen fills. | Uses current `map` to convert rectangle bounds to pixel bounds. |
| `addToOnScreenSequences(...)` | 5738-5744 | Removes and reinserts a storage/color pair so newly added regions draw on top. | Central mutation point for `onScreenSequences`. |
| `callRenderRegions()` | 5745-5748 | No-argument render helper using the viewer’s own state. | Calls `renderRegions`. |
| `renderRegions(...)` | 5749-5809 | Reconstructs the layered display: guide lines, stable/unstable regions, cover rectangles, polygon boundaries, cover areas, MRR bounds, and one-by-one image. Updates image views together. | Calls `redoFromScratch`, `renderGuideLines`, `renderRectLoad`, `renderPolygon`, and `renderRegion`. |
| `setupButtons(...)` | 5810-5853 | Rebuilds plus/minus buttons for the current code sequence at index `n`. Buttons change one code entry by `+2` or `-2`, synchronize the UI, and recalculate. | Calls `synchronize` and `calculateCurrentCodeNumbers`. |
| `synchronize()` | 5854-5884 | Writes `currentCodeNumbers` into the main text field and updates all plus/minus button labels. | Called after code sequence mutation. |
| `calculateCurrentCodeNumbers(...)` | 5885-5944 | Classifies current code numbers, loads storage from database/native layer, optionally draws region/MRR/bounding polygon, and returns a printable result string. | Calls `ClassifiedCodeSequence.create`, `Database.loadStorage`, `Wrapper.boundingPolygon`, `addToOnScreenSequences`, `renderRegion`, `renderPolygon`. |
| `drawHorizontalLine(...)` | 5945-5960 | Draws a horizontal angle-space line segment into a pixel image. | Used by guide and polygon rendering. |
| `drawVerticalLine(...)` | 5961-5974 | Draws a vertical angle-space line segment into a pixel image. | Used by guide and polygon rendering. |
| `drawObliqueLine(...)` | 5975-6009 | Draws an oblique line segment by sampling both pixel columns and rows to avoid gaps. | Used for simplex boundaries, diagonal guides, and polygon edges. |
| `renderGuideLines()` | 6010-6113 | Renders the fixed mathematical guide lines in angle space: axes, `x=y`, `x+y` thresholds, and selected special-angle lines. | Calls horizontal, vertical, and oblique line helpers. |
| `renderPolygon(...)` | 6114-6181 | Renders polygon edges by dispatching each edge to horizontal, vertical, or oblique line drawing. | Used for MRR boundaries, cover regions, and selected polygons. |
| `renderRect(...)` | 6182-6291 | Draws a cover rectangle with boundary and interior colors. If the rectangle corresponds to a trimable stable or half-triple rectangle, it can load the stable polygon and avoid drawing outside it. | Calls `Database.loadStorage`; uses `coverRects`, `map`, and all-angle symmetry expansion. |
| `renderRectLoad(...)` | 6292-6366 | Draws a cover rectangle without the trimming logic used in `renderRect`. | Called by `renderRegions` when painting loaded cover rectangles. |
| `renderUnstable(...)` | 6367-6568 | Renders an unstable storage as points along a constrained line segment. It solves `a*x + b*y + c*pi = 0` as horizontal, vertical, or oblique, tests positivity, then paints valid segment pixels. | Uses `Storage.Unstable.constraint`, `lineSegment`, `isPositive`, and `isPositiveProver`; called by `redoFromScratch` and `renderRegion`. |
| `renderRegion(...)` | 6569-6637 | Incrementally draws one storage into an existing image. Stable regions are filled pixelwise after polygon and positivity tests; unstable regions delegate to `renderUnstable`. | Called whenever a task produces a new storage and by one-by-one mode. |

## Parsing And Vary Workflows

| Method | Lines | Functionality | Important Interactions |
| --- | ---: | --- | --- |
| `parseOBOFile(...)` | 6638-6678 | Parses one-by-one files. It keeps coordinate lines, normalizes valid code sequence lines, ignores comments/metadata, and rejects malformed lines. | Calls `Utils.trimCodeLine`, `Utils.isCoords`, `Utils.splitString`, `ClassifiedCodeSequence.create`. |
| `parseFile(path, print)` | 6679-6688 | Convenience parser overload. | Delegates to `parseFile(path, print, false)`. |
| `parseFile(path, print, addToGarbage)` | 6689-6815 | Parses the main load-file format. It supports single code lines, triples separated by commas, optional color values, `rectangle` lines, and iteration lines marked with `+` and `#`. It preserves order and removes duplicate singles through `LinkedHashMap`. | Calls `ClassifiedCodeSequence.create`, `Utils.splitString`, `Database.saveTripleToDatabase`, `Database.saveToDatabase`, and JavaFX alerts. |
| `varyLFunction(...)` | 6816-6870 | Older synchronous VaryL loop over explicit angle points. It asks `boyanMenu` for codes at each point, filters by code type/length bounds, excludes codes already in cover text, prints standardized codes, and accumulates results. | Calls `boyanMenu.varyTrianglesL`, `Utils.standard`, and `Platform.runLater`. |
| `superPolyVaryFunction(...)` | 6871-6940 | Repeats PolyVary or AutoPolyVary several times, optionally scaling the view and increasing side-sum bounds between repetitions. | Calls `autoPolyVaryFunction` or `polyVaryFunction`; uses `SuperPolyVaryLoad` static configuration and a progress object. |
| `autoPolyVaryFunction(...)` | 6941-6988 | Prepares AutoPolyVary over a range of coordinate lines. It reads start/step/end, builds max arrays, progress UI, and worker executors, then starts recursive hole processing. | Calls `getStartStepEnd` and `drawAutoPolyVary`. |
| `polyVaryFunction(...)` overload | 6989-6995 | Convenience overload without small-cover support. | Delegates to full `polyVaryFunction`. |
| `polyVaryFunction(...)` full | 6996-7048 | Preprocesses a polygon for PolyVary. It intersects the polygon with the current view, generates quadtree sample points, filters already colored pixels, starts worker executors, and calls `drawPolyVary`. | Calls `autoRecurse` and `drawPolyVary`. |
| `drawPolyVary(...)` | 7049-7219 | Runs `PolyVaryTask` on sampled points, streams partial storages into the image, prints codes, adds cover text, tracks hole count, handles success/cancel/failure, and advances super-poly progress. | Calls `findHoles`, `addToOnScreenSequences`, `renderRegion`, `renderRegions`, and cover-window append methods. |
| `drawAutoPolyVary(...)` overload | 7220-7229 | Starts AutoPolyVary recursion with an empty previous-code list. | Delegates to full `drawAutoPolyVary`. |
| `drawAutoPolyVary(...)` full | 7230-7536 | Processes one coordinate line for AutoPolyVary, skips it if the previous stable code already covers the coordinate, generates and filters sample points, launches `PolyVaryTask`, streams partial results, updates cover/iterate-to-limit windows, recurses to the next line, and handles cancellation/failure. | Calls `setOBO`, `autoRecurse`, `addToIterToLimitCover`, `addToOnScreenSequences`, `renderRegion`, and `renderRegions`. |
| `autoRecurse(...)` | 7537-7566 | Quadtree sampler for a polygonal angle region. It prunes rectangles with no transparent pixels, adds the midpoint if it is inside the triangle domain and polygon, then recurses into four quadrants. | Calls `findHole` and `ConvexPolygon.location`. Mathematically this searches unresolved holes in angle space. |

## Iteration, Cover, And Utility Methods

| Method | Lines | Functionality | Important Interactions |
| --- | ---: | --- | --- |
| `drawRegion(...)` | 7567-7619 | Shared helper for drawing loaded storages and building a printable result for single/triple iteration operations. | Calls `addToOnScreenSequences`, `renderRegion`, `renderPolygon`, `Wrapper.boundingPolygon`. |
| `handleIterationIntersect(...)` | 7620-7683 | After an iteration pattern mutates the current codes, it loads each resulting storage, checks optional polygon intersection, draws if eligible, and adds singles/triples to the cover with the pattern comment. | Calls `drawRegion`, `Database.loadStorage`, and cover-window append methods. |
| `getCodeSeqAndOEString()` | 7684-7718 | Serializes current code numbers exactly as displayed and computes their odd/even pattern string. | Used before mutation so iteration patterns are stored against the pre-mutation code. |
| `addSubtract(...)` | 7719-7759 | Applies a comma-separated pattern where positive indices add 2 and negative indices subtract 2. Stores the iteration pattern, handles intersection/cover logic, and synchronizes UI. | Calls `getCodeSeqAndOEString`, `Database.saveIterationPatternToDatabase`, `handleIterationIntersect`, `synchronize`. |
| `addSubtractReverse(...)` | 7760-7794 | Reverse of `addSubtract`: positive indices subtract 2 and negative indices add 2. | Same interactions as `addSubtract`. |
| `increase(...)` | 7795-7827 | Adds 2 to all indexed entries in each comma-separated pattern section. | Calls database iteration-pattern save, `handleIterationIntersect`, and `synchronize`. |
| `decrease(...)` | 7828-7853 | Subtracts 2 from all indexed entries in each comma-separated pattern section. | Calls database iteration-pattern save, `handleIterationIntersect`, and `synchronize`. |
| `setOBO(...)` | 7854-7894 | One-by-one display helper. A coordinate line recenters the screen; a code line loads and renders just that code into the OBO image layer. | Calls `zoom`, `Database.loadStorage`, and `renderRegion`. |
| `createVector(...)` | 7895-7917 | Converts a 1-based index pattern into an integer vector of a given code length. Negative indices contribute `-increment`; positive indices contribute `+increment`. | Used by `iterateThru`. |
| `addMultiple(...)` | 7918-7936 | Performs `destination += scale * vector` entrywise with overflow-checked arithmetic. | Used by `iterateThru` for reversible recursion. |
| `drawSearch(...)` | 7937-7946 | Draws a list of storages returned from the search UI. | Calls `addToOnScreenSequences` and `renderRegions`. |
| `getOffset()` | 7947-7958 | Parses the prover offset text field, returning 0 for an empty field. | Used by stable and unstable prover-based rendering/selection. |
| `loadCover(dir, executor)` | 7959-7962 | Convenience overload for ordinary cover loading. | Delegates to `loadCover(dir, executor, false)`. |
| `loadCover(dir, executor, small)` | 7963-7984 | Loads polygon, square, stable list, triple list, and cover rectangle assignments from a cover directory. Adds rectangles to `coverRects`, optionally sets the main cover area, and rerenders. | Calls `CoverStuff.parsePolygon`, `parseRectangle`, `parseStables`, `parseTriples`, `parseCover`, `renderRegions`. |
| `loadCoverWithoutTrim(...)` | 7985-8011 | Loads a cover like `loadCover`, but always sets `coverArea` and does not distinguish the small-cover flag. | Calls the same `CoverStuff` parsers and `renderRegions`. |
| `queuedVaryTask(...)` | 8012-8151 | Runs Vary tasks sequentially for bar/tetrahedron workflows. Every `step` tasks, it intersects result code sets, prints/draws matching codes, optionally appends stable cover text, then recursively starts the next task. | Calls `boyanMenu.varyTriangles`, `Database.saveToDatabase`, `Utils.getIntersectionCodes`, `PrintMid.printMid`, `BatchLoadStorage.batchLoadStorage`, `addToOnScreenSequences`, `renderRegions`. |
| `drawAndAddToCover(...)` | 8152-8180 | Draws a preloaded storage list and/or adds it to cover text as either a single stable or triple. | Calls `addToOnScreenSequences` and cover-window append methods. It does not rerender by itself. |
| `addToIterToLimitCover(...)` | 8181-8202 | Finds all-positive and/or plus-minus iteration patterns for a classified code string and adds valid pairs to `IterateToLimitWindow`. | Calls `IterateToLimitWindow.getIterationPattern` and `addToContent`. |
| `getStartStepEnd()` | 8203-8206 | Convenience overload using the current file line count as default end. | Delegates to `getStartStepEnd(int)`. |
| `getStartStepEnd(int)` | 8207-8254 | Reads line-start, line-step, and line-end fields. If all are empty, defaults to full file range. Otherwise validates parseability, range, and positive step. | Calls AutoPolyVary alert helpers. Returns a tuple of `null`s on validation failure. |
| `moveScreen(String, String)` | 8255-8272 | Parses degree strings and recenters the screen. | Delegates to `moveScreen(double, double)`. |
| `moveScreen(double, double)` | 8273-8278 | Converts degree coordinates to radians and centers the view at that point. | Calls `zoom` with a degenerate rectangle. |

## Important Risks In `Viewer.java`

- The constructor is too large to reason about safely. It mixes UI construction, event binding, state initialization, and workflow orchestration across more than 3,000 lines.
- `redoFromScratch` submits one future per pixel, which means 360,000 tasks for a 600-by-600 image. A chunked row/stripe renderer would have much lower scheduling overhead.
- Several methods mutate JavaFX/UI state from task callbacks and partial listeners. Review thread confinement carefully before refactoring.
- Many workflows duplicate code for printing, color selection, cover-window append logic, and executor shutdown.
- `getExpandos` increments `position` twice in consecutive loops, which may be intentional for a specific expando pattern but is suspicious.
- `drawAutoPolyVary` can `return -1` after creating or reusing executors if `numGroupToPrint` is null; make sure that path does not leak executors or leave progress state stale.
