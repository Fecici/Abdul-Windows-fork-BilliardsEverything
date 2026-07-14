# Viewer Support Classes And Tasks

This document covers `src/java/billiards/viewer` files other than `Viewer.java`. The raw checklist for every detected non-`Viewer.java` viewer function is:

Current baseline note: this support map remains the base for Abdul, with small-cover checkbox defaults changed in Abdul and more extensive `[MAIN]` viewer changes in load windows, `SmallCoverWindow`, `HashTriple`, vary tasks, and updater removal. See `18` for the exact changed class list.

- `viewer-support-function-index.txt`

That index had 404 method/prototype rows when generated. The notes below explain the important classes and nontrivial methods. Trivial accessors and stage `show/close` wrappers are intentionally summarized by class.

## Coordinate And State Utilities

| Class/Method | Purpose | Interactions |
| --- | --- | --- |
| `PixelRadianMap` | Converts between pixel coordinates in the 600x600 JavaFX image and radian coordinates in the angle plane. | Used by `Viewer`, `PolyVaryTask`, `CycleVaryTask`, and rendering helpers. |
| `PixelRadianMap.radianX/radianY` | Maps pixel position to angle coordinate using `pixel*pi/(side*scale)+translate`. | Central for mouse clicks, rendering, and hole detection. |
| `PixelRadianMap.pixelX/pixelY` | Maps angle coordinate back to pixel coordinate. | Central for drawing lines, rectangles, polygons, and task filtering. |
| `PixelRadianMap.getViewRectangle` | Returns the current radian-space rectangle visible on screen. | Used to cull/draw storages and covers. |
| `PixelRadianMap.setViewRectangle/reset/scaleBy/translate*` | Updates view transform. | Used by zoom, pan, navigation, and auto-vary workflows. |
| `BackwardForward<T>` | Maintains browser-style backward/forward history over immutable snapshots. | `Viewer` uses it for view-rectangle history. |
| `BackwardForward.backward/forward/add` | Moves through history or pushes a new value while truncating stale forward entries. | UI navigation controls. |
| `Cycle` | Small modulo counter. | Used to cycle display colors. |
| `Cycle.get` | Returns current index then advances modulo `mod`. | Every region color assignment. |
| `SideSum` | Mutable coefficient accumulator for a linear combination of the three triangle angles `x,y,z=pi-x-y`. | Used by search/vary code to reason about side-sum expressions. |
| `SideSum.add/sub/sum/copy` | Mutates coefficients, evaluates the linear combination, or copies state. | Mathematical helper for angle-side sums. |
| `HashTriple` | Holds cover rectangles for stable codes, triples, half-triples, and display colors in parallel maps. | Used by `Viewer.renderRegions`, `click`, and cover loading. |
| `HashTriple.addStables/addTriples/addHalfTriples` | Bulk-adds rectangle mappings and assigns a color to each rectangle. | Cover parsers and loaders. |
| `HashTriple.stableEntrySet/tripleEntrySet/HalfTripleEntrySet` | Returns rectangle lists for iteration/rendering. | `Viewer.renderRegions` and click selection. |
| `HashTriple.get*/put/remove/clear` | Accesses or mutates rectangle assignments. | Cover recoloring, deletion, and drawing. |
| `PriorityCallable` and `PriorityRunnable` | Interfaces exposing `getPriority`. | Used by `PriorityExecutor`. |
| `PriorityExecutor` | Thread pool backed by a priority queue. Smaller priority values execute first. | Used for storage-loading tasks where shorter code length is used as priority. |
| `PriorityExecutor.newTaskFor` | Wraps callables/runnables in comparable future tasks, defaulting to priority 0 if no priority is provided. | Java executor internals. |

Math note: `PixelRadianMap` is the bridge between numerical geometry and the UI. Every visual proof about a billiards region is ultimately a test over radian coordinates derived from pixels.

## General Viewer Utilities

File:

- `src/java/billiards/viewer/Utils.java`

Important methods:

| Method | Purpose |
| --- | --- |
| `splitString` | Parses whitespace-separated integer code sequences into immutable int lists. |
| `safeShutdownExecutor` | Attempts orderly executor shutdown and reports whether termination succeeded. |
| `convert` | Converts an int list to a `ClassifiedCodeSequence` if valid. |
| `writeToFile`, `printToFile`, `readFromFile` | File helpers used for temporary cover, iteration, and output files. |
| `copyInto` | Replaces a mutable int list with another int list. |
| `isCoords`, `isDouble`, `ifGet` | Input parsing helpers. |
| `getIntersectionCodes` | Intersects collections of classified codes, used for tetrahedron/bar matching. |
| `compare` | Compares nested string lists for shared sequence content. |
| `standard` overloads | Formats code sequences with type/length/sum/count output for console/cover usage. |
| `getCoverCodeString` overloads | Formats stable code entries for cover windows. |
| `hex`, `colorButton`, `toolTip`, `setupCustomTooltipBehavior` | JavaFX styling and tooltip helpers. |
| `trimCodeLine`, `tripleTrimmer` | Removes display prefixes/comments from code lines while preserving triple syntax. |
| `timeConvert` | Formats elapsed milliseconds. |
| `runAndWait` | Runs an action on the JavaFX application thread and blocks until complete. |
| `modN` | Positive modulo helper. |
| `verifyInfo`, `verifyVector` | Cross-checks native `InfoAll` data against Java `Storage`. Used by LR testing. |
| `calculate_formula`, `calculate_formula_mrr` | Diagnostic formula helpers for circles/equations around a center. |

Risk notes:

- `runAndWait` must not be called from contexts where blocking the JavaFX thread would deadlock.
- `calculate_formula` and `calculate_formula_mrr` are diagnostic/experimental and print rather than returning structured results.
- Several file helpers write into relative paths such as `tmp/`; callers assume that directory exists.

## Picture Loading Tasks

These are JavaFX `Task` subclasses used by `Viewer.drawCodes`, iteration workflows, and file/database loads.

| Class/Method | Purpose | Interactions |
| --- | --- | --- |
| `DrawPictureTask` | Loads `Storage` for many classified codes, exposes partial results, optionally prints each result. | Calls `Database.loadStorage`; partials are added via `Platform.runLater`. |
| `DrawPictureTask.getPartials/getPartialProperty` | Exposes an observable list for UI listeners. | `Viewer` listens and draws incrementally. |
| `DrawPictureTask.call` | Submits one callable per code to a supplied executor, tracks progress, collects successful storages, prints empty-set messages, and propagates execution failures. | Used for normal drawing. |
| `DrawPictureTaskShowLR` | Like normal draw, but loads left/right data and prints when left/right patterns change. | Calls `Database.loadStorageShowLR`. |
| `DrawPictureTaskUseLR` | Uses the first code as the LR base for all later codes. Stops when an empty set/breakpoint is reached. | Calls `Database.loadStorageUseLR`, `Wrapper.deleteFromDatabase`. |
| `DrawPictureTaskUseLRTest` | More defensive LR path: tries LR-derived load against the best known base, verifies returned `InfoAll`, deletes bad rows, then falls back to full storage loading. | Calls `Wrapper.loadInfoAll`, `Utils.verifyInfo`, `Database.loadStorage`. |
| `DrawPictureTaskTriples` | Loads triples while preserving component grouping, returning an array of three storages per triple. | Used when cover logic requires all three components to intersect as a triple. |
| `DontDrawPictureTask` | Computes/saves many codes without drawing them. | Calls `Wrapper.saveToDatabase`, prints non-empty or empty result. |

Risk notes:

- The draw task family repeats a large amount of executor/progress/error logic.
- Several tasks call `future.cancel(false)`, so already-running native/database work continues.
- `DrawPictureTaskUseLR.breakPoint` is static mutable state shared across task instances.
- LR tasks perform database deletion during load failure; this is useful for cleanup but dangerous if a transient verification failure occurs.

## Vary Tasks

| Class/Method | Purpose | Interactions |
| --- | --- | --- |
| `PolyVaryTask` | Searches many angle points for code sequences and loads/draws storages for selected codes. | Called by PolyVary and AutoPolyVary workflows. |
| `PolyVaryTask.call` | Converts sampled points to shuffled coordinates, skips already colored pixels, asks `BoyanMenu.autoVary` for codes, filters by type/length bounds, submits storage loading, and publishes partial results. | Uses `shotExecutor` for code search and `storageExecutor` for MRR loading. |
| `PolyVaryTask.autoCodesFiltered` | Converts radians to degrees, runs `boyanMenu.autoVary`, then filters `CS`, `OSO`, and `OSNO` by configured maxima. | Mathematical search point is an angle pair. |
| `PolyVaryTask.pixelColor` | Reads the current rendered pixel on the JavaFX thread to skip filled points. | Uses `Platform.runLater` and `FutureTask`. |
| `PolyVaryTask.loadStorage` | Loads storage for one code with cancellation checks before and after database/native work. | Calls `Database.loadStorage`; partials via `Platform.runLater`. |
| `PolyVaryTask.loadStorageFromDB/loadPrintedCodesStorage` | Enqueues priority storage loads and handles selected print modes. | Uses `PriorityCallable` with code length priority. |
| `CycleVaryTask` | Similar to `PolyVaryTask`, but uses its own `autoVary` implementation and type-selection flags for cycle-vary workflows. | Called by `CycleVaryWindow`. |
| `CycleVaryTask.autoVary` | Runs `BoyanMenu.findCodes3` for CS-only and non-CS searches, then filters OSO/OSNO by side sum. | Uses shot count and side-sum bounds. |
| `VaryLTask` | Processes one VaryL coordinate line, prints selected codes, optionally selects middle/first/last of groups, and loads storages. | Called recursively from `Viewer.recurseDrawVaryL`. |
| `VaryLTask.addProcessedCode` | Groups codes by `(CodeType, odd-even pattern)` and increments group counts. | Used by middle-code printing. |
| `VaryLTask.autoCodesFiltered` | Calls `boyanMenu.varyTrianglesL`, filters by length and excludes codes already in cover text. | Main VaryL search. |
| `VaryLTask.addToIterToLimitCover` | Adds all-positive or plus/minus iteration patterns to `IterateToLimitWindow`. | Calls `IterateToLimitWindow.getIterationPattern`. |
| `VaryLTask.printMidFirstLast` | Prints the middle code of each grouped block, and optionally first/last. | Used for compact representative output. |
| `VaryLTask.loadStorage/printAndLoadStorage` | Priority-loads storages for printed codes and publishes partials. | Same concurrency shape as PolyVary. |

Optimization notes:

- `PolyVaryTask` and `CycleVaryTask` are nearly duplicated. A strategy parameter for "find codes at point" would remove duplication.
- Both task families poll pixel color by dispatching a `FutureTask` to the JavaFX thread per point. Bulk snapshotting the image reader before the task starts may reduce cross-thread overhead.
- The `emptyMax = 8` stop condition is a hard-coded heuristic.

## BoyanMenu And Code Search Frontend

`BoyanMenu` is the Java-side control panel that bridges UI parameters to Java vary algorithms.

| Method Group | Purpose |
| --- | --- |
| Constructor and fields | Builds text fields, checkboxes, buttons, and parameter controls for varying triangle coordinates. |
| `varyAction` | Reads UI fields and starts a vary workflow with a title/output path and print mode. |
| `click` | Updates coordinate fields from a clicked angle point. |
| `varyTriangles` overloads | Search for codes around one or more triangle points using configured side-sum ranges and shots. |
| `varyTrianglesL` overloads | VaryL-specific search at a point, with optional side-sum override. |
| `autoVary` overloads | Automated code search at a point using current or overridden maxima. |
| `findCodes`, `findCodes2`, `findCodes3`, `findCodes4` | Static/private search kernels that call Java `Vary3`/`Vary4` style algorithms and classify returned codes. |
| `printCodes` overloads and `addFirstMidLast` | Output helpers that print all codes or representative first/middle/last codes from grouped results. |
| `typeString` | Human-readable selected type summary. |
| `getRadianCoord` | Converts UI coordinate fields to radians. |

Interaction: `Viewer`, `PolyVaryTask`, `CycleVaryTask`, `VaryLTask`, and tetra/bar workflows depend on `BoyanMenu` for search settings and code generation.

## Load Windows And Parameter Dialogs

| Class | Purpose |
| --- | --- |
| `AutoPolyVaryLoad` | Dialog for loading polygon/bounds/max parameters for AutoPolyVary, including output mode, iterate-to-limit choices, and small-cover option. |
| `PolyVaryLoad` | Similar polygon/max loader for ordinary PolyVary. |
| `SuperPolyVaryLoad` | Adds repetition, bound-step, color-cycle, and optional magnification settings for repeated PolyVary/AutoPolyVary. |
| `CycleVaryWindow` | Large cycle-vary controller: coordinate navigation, line-range parsing, mode selection, repetition handling, and task launching. |
| `VaryWindowL` | VaryL/MiddleVaryL loader: points, bounds, draw/override/first-last flags, small-cover and iterate-to-limit flags. |
| `TetraBar` | Collects bar/tetrahedron point sets and print/draw/cover options. |
| `SaveV3Window` | UI for selecting paths and saving vary3/varyL output files. |
| `DBGui` | Database selection, creation, deletion, and clearing GUI. |
| `QueryStage` | Query UI for searching code sequences and drawing matching results in the viewer. |
| `GradientWindow`, `InfoWindow`, `InfoWindow2`, `PatternLookupWindow`, `PatternCalculator`, `CodeAndPatternLookupWindow`, `IterationPolyWindow`, `LookAtMeWindow` | Auxiliary dialogs for specialized inspection, lookup, polygon entry, and calculation workflows. |

Important repeated pattern: most loader windows parse text fields into tuples or optional values. On invalid input they show JavaFX alerts and return `Optional.empty()` or a tuple containing nulls rather than throwing.

## Cover Windows

| Class/Method Group | Purpose |
| --- | --- |
| `CoverWindow`, `CoverWindow2`, `CoverWindow3`, `CoverWindow4` | Variants of the cover editor/checker. They hold cover polygon, square, stable codes, triple codes, and generated cover text. |
| `CoverWindow.appendStablesInfo/appendTriplesInfo/setTriplesInfo` | Mutate cover text areas from viewer/vary results. |
| `CoverWindow.saveToFile` | Writes cover text components to files. |
| `CoverWindow.cleanTriples/cleanHalfTriples/cleanHalfTriplesCorner/cleanStables` | Parse cover text, validate/load referenced storage, and remove unusable entries. |
| `CoverWindow.redoInfo` | Recomputes global cover info text after cleaning. |
| `SmallCoverWindow` | Smaller cover collector tied back to a full `CoverWindow`; can save, clean, append stables, and add polygons. |
| `StablesWindow` | Tool for calculating, factoring, and extracting stable/triple/half-triple combinations. |
| `CoverInfoWindow` | Simple display window for cover information. |

Risk notes:

- `CoverWindow2`, `CoverWindow3`, and `CoverWindow4` are highly duplicated variants of `CoverWindow`.
- Cleaning functions combine parsing, database loads, cover logic, and UI state; they are hard to unit test in current form.
- Several methods are static but still conceptually depend on UI/global cover conventions.

## Progress And UI Feedback

| Class | Purpose |
| --- | --- |
| `Progress` | Basic JavaFX progress window bound to one task. |
| `ProgressWithStatus` | Progress window with formatted status text. |
| `ProgressMultiTask` | Progress window that can change the current task and track multi-step workflows. |
| `ColorPicker` and `Colors` | Custom color selection and color-name mapping used for region and cover coloring. |
| `Updater` | Checks for updates against a remote/current version source. Network behavior should be reviewed before use in restricted environments. |

## Remaining Line-By-Line Work

The support classes now have a class/method map and a generated method checklist, but not every 404 method has body-level commentary. If continuing, use:

```powershell
Get-Content docs/source-study/viewer-support-function-index.txt
```

Recommended next files for body-level expansion:

- `BoyanMenu.java`
- `CycleVaryWindow.java`
- `CoverWindow.java`
- `IterateToLimitWindow.java`
- `StablesWindow.java`
- `VaryWindowL.java`
