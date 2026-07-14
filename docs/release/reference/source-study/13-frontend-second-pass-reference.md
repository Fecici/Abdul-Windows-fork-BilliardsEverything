# Frontend Second-Pass Reference

This document covers the JavaFX frontend under `src/java/billiards/viewer`. It should be read together with:

Current baseline note: use this as the base frontend reference for Abdul, then apply the Abdul viewer deltas in `17` and the `[MAIN]` viewer/runtime deltas in `18`. The most important Abdul-only frontend risk is repeated/default reflection transform handling.

- `docs/source-study/08-viewer-method-map.md`: first-pass `Viewer.java` map.
- `docs/source-study/10-viewer-support-map.md`: first-pass support-class map.
- `docs/source-study/viewer-internal-callgraph.txt`: generated internal call graph for `Viewer.java`.
- `docs/source-study/viewer-support-function-index.txt`: generated index of 404 support-class method rows.
- `docs/source-study/function-index-ctags.txt`: full exact line/signature index.

The frontend is not just display code. It orchestrates candidate generation, native backend calls, database loading, cover proof workflows, file parsing, and visual rendering.

## Frontend Architecture

The frontend has four main responsibilities:

- Input: accept code sequences, polygons, bounds files, branch/iteration patterns, and database choices.
- Computation orchestration: create Java tasks, call `Database`/`Wrapper`, track progress, and cancel work.
- Visualization: map radian/degree parameter space to pixels and render stable polygons, unstable curves/segments, covers, holes, and guide lines.
- Workflow state: remember on-screen sequences, cover colors, current code numbers, left/right expansions, and line-navigation state.

The main window is `Viewer.java`. Most other classes are modal windows, background tasks, progress dialogs, or helpers.

## Coordinate Systems

There are at least three coordinate systems in play:

- Mathematical parameter space: usually triangle-angle coordinates `(x,y)` in radians, with degree formatting for humans.
- Pixel space: JavaFX image/canvas coordinates.
- Normalized cover space: backend cover functions often use rational coordinates in `[0,1] x [0,1]`, then convert to degrees for reports.

`PixelRadianMap` is the core Java class mapping pixels to radians and back. The backend cover code has its own rational rectangle/polygon types.

## `Viewer.java` Main Window

`Viewer.java` is a large controller. It holds UI controls, event handlers, render state, database pool references, current code-number arrays, on-screen storage/color maps, cover windows, and long-running task orchestration.

### Lifecycle and Construction

`Viewer(primaryStage, version, executor, ...)` builds the main JavaFX UI, wires controls, initializes state, opens database resources, creates menus/windows, and registers input handlers. It is the constructor where most dependencies enter the frontend.

`start(executor)` starts or restarts work using the supplied executor. It is separate from JavaFX `Application.start` and belongs to the viewer controller.

### Code Expansion and Left/Right Arithmetic

`getExpandos(workingNumbers, iteration, position, repeated_elements)` generates expanded code sequences from a base sequence and an iteration/position rule. It feeds iteration-pattern and expando workflows.

`subtractLeftRight(lr1, lr2)` subtracts left/right branch strings. It treats branch text as symbolic arithmetic over branch counts.

`addLeftRight(lr1, lr2)` adds left/right branch strings.

`isleftRightLegal(leftRights)` validates a branch string before it is sent to database/native loading.

`getMultipleExpandos(iteration, position, repeated_elements)` generates multiple expanded classified code sequences from text inputs.

Mathematically, these methods manipulate symbolic branch choices for how an orbit crosses/refines stable and unstable regions under repeated iteration.

### Iteration Workflows

`iterateActionWithPolyIntersect(...)` iterates code sequences and filters loaded storages by intersection with a polygon. It returns both generated codes and storages that pass spatial filtering.

`iterateAction(workingNumbers, ...)` is the base iteration action. It generates candidate classified code sequences from current working numbers and iteration controls.

`iterateThru(...)` repeatedly iterates over a range or list of inputs. It is a batch driver.

`handleIterationIntersect(pattern)` runs the workflow for intersecting iteration patterns and likely delegates to `IterateToLimitWindow` or database lookup.

`getCodeSeqAndOEString()` extracts the current code-sequence text and odd/even string from UI state.

`addSubtract(textField, pool)`, `addSubtractReverse(textField, pool)`, `increase(textField, pool)`, and `decrease(textField, pool)` mutate numeric code fields in the UI and then reload/recalculate dependent storage.

`setOBO(...)`, `createVector(...)`, and `addMultiple(...)` build one-by-one/offset vectors used by iteration and pattern workflows.

`addToIterToLimitCover(classCodeSeq, addToAllPositive, addToPlusMinus, iterateToLimitWindow)` sends found codes into an iterate-to-limit cover workflow.

`getStartStepEnd()` and `getStartStepEnd(defaultEnd)` parse start/step/end line-navigation settings for batch operations.

### Vary Workflows

`recurseDrawVaryL(points, max, codeList, ...)` recursively explores a list/region of points and draws matching code sequences.

`drawVaryL(points, max, draw, ...)` starts the vary-L drawing/search workflow.

`varyLFunction(codesFound, points, ...)` runs a vary-L search and returns sorted classified codes.

`superPolyVaryFunction(polyVals, executor)` starts a larger polygon vary workflow, likely with magnification/subdivision.

`autoPolyVaryFunction(polyVals, executor, ...)` starts automated polygon vary with cover/subdivision options.

`polyVaryFunction(polyVals, executor)` overloads start polygon vary with different options.

`drawPolyVary(points, max, area, ...)` renders/storage-loads results of polygon vary.

`drawAutoPolyVary(max, maxSubdivisions, autoCover, autoSmallCover, overrideSS, ...)` overloads drive automatic subdivision, cover checking, and drawing for polygon vary.

`autoRecurse(xMin, xMax, yMin, yMax, ...)` recursively subdivides parameter rectangles during automated cover/vary search.

`queuedVaryTask(originalPoints, ...)` queues a vary task with saved parameter points and task configuration.

These workflows use `VaryLTask`, `PolyVaryTask`, `CycleVaryTask`, `VaryWindowL`, `PolyVaryLoad`, `AutoPolyVaryLoad`, and `SuperPolyVaryLoad`.

### File Loading and Parsing

`LoadFileAction(...)` overloads load code/polygon/search data from files and trigger drawing or calculation.

`parseOBOFile(path)` parses one-by-one formatted files into a list of strings.

`parseFile(path, print)` and `parseFile(path, print, addToGarbage)` parse general code files. The `addToGarbage` flag controls whether rejected/problem lines are recorded.

These functions are format-sensitive. If onboarding for a specific file format, inspect sample files and this parser together.

### Drawing and Storage Loading

`drawCodes(...)` overloads are central draw entry points. They accept tuples containing optional view rectangles, classified code sequences, and flags. They create background tasks to load `Storage` via `Database`/`Wrapper`, then render results.

`drawRegion(storages, ...)` draws or loads a group of optional storages and returns status text.

`drawSearch(list)` draws a list of search results.

`drawAndAddToCover(draw, addToCover, storages)` draws provided storages and optionally records them in cover windows.

`calculateCurrentCodeNumbers(pool, i)` calculates or loads storage for the current code-number field.

`buttonCalulator(code, pool, n)` is a button-triggered calculate path for one code. The misspelling is in source.

`btnCalculateAction(pool)` handles the Calculate button and orchestrates current-code validation/load/draw.

`setupButtons(pool, n)` binds button handlers to code-number fields.

`synchronize()` keeps UI fields/state arrays aligned after edits.

### View Navigation and Interaction

`zoomAction(executor)` handles zoom UI actions.

`zoom(xMax, xMin, yMax, yMin, executor)` changes the current view rectangle and triggers redraw.

`pan(initX, initY, finX, finY, executor)` shifts the view based on drag start/end pixels.

`click(pixelX, pixelY, executor)` handles map clicks, likely selecting/running code calculations at a coordinate.

`moveScreen(xString, yString)` parses text coordinates and moves the screen.

`moveScreen(x, y)` moves the screen directly to radian/degree coordinates.

`getOffset()` returns the current display offset used in coordinate transforms.

### Rendering Pipeline

`findHole(...)` overloads search pixel/parameter rectangles for unfilled holes.

`findHoles(area)` returns a list of hole points in a convex polygon.

`setImageColor(image, color)` fills a writable image with one color.

`renderColor(color)` creates a small color swatch image view.

`color(storages, ...)` chooses display color for a stable/unstable region based on existing map state and sequence characteristics.

`redoFromScratch(...)` rebuilds the full image from stored regions and current map settings.

`drawFills(writer)` draws background/fill pixels.

`addToOnScreenSequences(storage, color)` records a storage/color pair in the current on-screen map.

`callRenderRegions()` triggers rendering for current regions.

`renderRegions(regions, image, ...)` loops through storage/color entries and renders each.

`drawHorizontalLine(y, x1, x2, ...)`, `drawVerticalLine(x, y1, y2, ...)`, and `drawObliqueLine(yFunction, x1, x2, ...)` draw guide/boundary lines.

`renderGuideLines()` renders fixed mathematical guide lines in the current view.

`renderPolygon(...)` draws a stable convex polygon.

`renderRect(rect, image, ...)` draws a rectangle in current view.

`renderRectLoad(rect, image, ...)` draws a rectangle from loaded cover/search data.

`renderUnstable(unstable, pixelWriter, ...)` draws an unstable sequence's curve/line/points.

`renderRegion(region, image, color)` dispatches stable vs unstable rendering.

Rendering is mostly pixel-based. The logic transforms every pixel/edge through `PixelRadianMap`, tests geometric containment/intersection, and writes colors to `WritableImage`.

### Cover Loading and Cover UI

`loadCover(dir, executor)` loads a cover from a directory.

`loadCover(dir, executor, small)` loads either normal or small cover, depending on the flag.

`loadCoverWithoutTrim(dir, executor)` loads a cover without trimming by current polygon/view.

Cover-related actions also interact with `CoverWindow`, `CoverWindow2/3/4`, `SmallCoverWindow`, `StablesWindow`, `CoverInfoWindow`, `HashTriple`, and backend `Wrapper.coverWrapper...` APIs.

### Errors and Alerts

`showProgressWindow(progress)` displays a progress dialog.

`showEnterLineNumberError()`, `showEnterLineNumberErrorAutoVary()`, `showStepErrorAutoVary()`, `showInvalidLineNumberError(max)`, `showInvalidLineRangeError(max)`, and `showInvalidNumberError(invalidNumber)` are alert helpers for invalid line/bounds inputs.

## Viewer Support Classes

The support classes are easier to onboard by grouping them by workflow.

## Application and Utilities

`Main.init()` initializes the application, commonly executor/database/update state.

`Main.start(mainWindow)` creates the `Viewer` and shows the main stage.

`Main.stop()` shuts down executors/resources.

`Updater.checkForUpdates(currentVersion)` checks remote/local update metadata. Because this can involve network behavior in normal app use, confirm current behavior before changing.

### `Utils`

`splitString(textCodeSeq)` parses a text code sequence into an immutable primitive int list.

`safeShutdownExecutor(executor)` attempts graceful executor shutdown.

`convert(codeList)` creates a `ClassifiedCodeSequence` from an int list.

`writeToFile(path, contents)` writes text to a file.

`printToFile(path, iterable)` writes iterable items line by line.

`readFromFile(path)` reads file contents as text.

`copyInto(dest, source)` copies primitive int-list contents.

`isCoords(line)` tests whether a line looks like coordinate input.

`isDouble(string)` validates numeric text.

`ifGet(array, i)` safely gets an array item or fallback text.

`getIntersectionCodes(lists)` computes classified-code intersection across multiple collections.

`compare(seq)` compares multiple lists of strings and returns matching/differing results.

`standard(code, count)` overloads standardize code text for display/cover files.

`getCoverCodeString(storage)` formats a storage object for cover stable/triple text.

`getCoverCodeString(classifiedCodeSequence)` formats a classified sequence for cover text.

`hex(color)` formats a JavaFX color as hex.

`colorButton(button, color, clicked)` styles a button for color selection.

`toolTip(text)` constructs a tooltip.

`trimCodeLine(line)` normalizes a code text line.

`tripleTrimmer(line)` normalizes a stable/unstable/stable triple line.

`timeConvert(millis)` formats durations.

`runAndWait(action)` runs an action on the JavaFX thread and blocks until complete.

`modN(value, n)` returns mathematical modulo.

`setupCustomTooltipBehavior(openDelay, visibleDuration, closeDelay)` changes JavaFX tooltip timing globally.

`verifyInfo(infoAll, storage)` compares native info against Java storage/equation expectations.

`convertDecimalToFraction(x)` formats a decimal as a fraction string for equation/formula output.

`calculate_formula(sin, cos, radius, center)` prints/calculates formula information for non-MRR equations.

`calculate_formula_mrr(equation, radius, center)` does similar formula work for MRR equation text.

`verifyVector(infoAll, storage)` verifies vector/slope information against storage.

Risk: `Utils` mixes pure parsing helpers, JavaFX-thread helpers, file I/O, and mathematical verification. When debugging, identify whether a failure is UI-threading, file format, or math-format related.

## Navigation and View State Helpers

### `PixelRadianMap`

`PixelRadianMap(side)` constructs a square pixel/radian map.

`PixelRadianMap(other)` copies map state.

`radianX(pixelX)` and `radianY(pixelY)` convert pixels to parameter coordinates.

`pixelX(radianX)` and `pixelY(radianY)` convert parameter coordinates to pixels.

`getScale`, `setScale`, and `scaleBy` control zoom.

`setTranslateX`, `setTranslateY`, `translateXBy`, and `translateYBy` control pan.

`pixelSize()` returns current parameter-size per pixel.

`getViewRectangle()` returns the current visible rectangle in parameter coordinates.

`setViewRectangle(rect)` adjusts scale/translation so a rectangle fills the view.

`reset()` restores default view.

### `BackwardForward`

`create(initialElem)` constructs history.

`backward()` moves back through history.

`forward()` moves forward.

`add(elem)` pushes a new history state and discards forward history.

### `Cycle`

`Cycle(mod)` stores a cyclic counter.

`get()` returns current value and advances/wraps.

### `SideSum`

`create(x, y)` initializes side-sum coefficients for angle calculations.

`add(num)` and `sub(num)` update side counts.

`sum()` evaluates the weighted sum.

`copy()` clones current state.

`toString()` formats debug text.

## Progress and Executors

### `PriorityCallable`, `PriorityRunnable`, `PriorityExecutor`

`getPriority()` in `PriorityCallable` and `PriorityRunnable` exposes task priority.

`PriorityExecutor(poolSize)` constructs an executor backed by priority-aware tasks.

`newTaskFor(callable)` and `newTaskFor(runnable, ret)` wrap tasks as comparable future tasks.

`ComparableFutureTask(priority, call)` and `ComparableFutureTask(priority, run, ret)` store priority metadata.

`compareTo(other)` orders tasks by priority.

### Progress Windows

`Progress(task)` binds a simple progress window to one JavaFX task.

`Progress.close()`, `show()`, and `incrementWindowCount(count)` control its lifecycle.

`ProgressMultiTask(formatString, allowCancel, offset, total)` displays aggregate progress across many tasks.

`ProgressMultiTask.syncProgress()`, `changeTask(task)`, `increment(step)`, `resetProgress()`, `close()`, `show()`, and `isCancelled()` synchronize UI state and cancellation.

`ProgressWithStatus(task, formatString, offset)` displays progress plus status text.

`ProgressWithStatus.syncProgress()`, `close()`, and `show()` manage that dialog.

## Background Draw/Search Tasks

These classes extend JavaFX task patterns and usually run off the UI thread, then expose partial results for incremental rendering.

### `DrawPictureTask`

`DrawPictureTask(classCodeSeqs, pool, ...)` stores sequences and database/native dependencies.

`call()` loads storages for sequences.

`getPartials()` and `getPartialProperty()` expose incremental `Storage` results.

### `DontDrawPictureTask`

`DontDrawPictureTask(classCodeSeqs, pool)` stores sequences to calculate/load without drawing.

`call()` performs the non-drawing load/save work.

### `DrawPictureTaskShowLR`

`DrawPictureTaskShowLR(classCodeSeqs, pool)` stores sequences for left/right display.

`call()` loads storages plus left/right metadata.

### `DrawPictureTaskTriples`

`DrawPictureTaskTriples(...)` stores triple inputs.

`call()` loads arrays of storages for stable/unstable/stable triples.

`getPartials()` and `getPartialProperty()` expose incremental results.

### `DrawPictureTaskUseLR`

`DrawPictureTaskUseLR(...)` stores a base code, target codes, and left/right branch data.

`call()` loads storages under supplied left/right interpretation.

### `DrawPictureTaskUseLRTest`

`DrawPictureTaskUseLRTest(...)` is a test/diagnostic variant of left/right picture loading.

`call()` performs the test load.

### `VaryLTask`

`VaryLTask(...)` stores vary-L search bounds, display options, cover options, executor, and database pool.

`call()` runs the vary search, loads storage, updates partials, and optionally writes/prints results.

`addProcessedCode(...)` records a processed code in shared progress/sets.

`checkStatus(future, except)` consumes load futures and records errors.

`autoCodesFiltered(coords, executor)` generates candidate codes at coordinates and filters them.

`loadStorage(classCodeSeq)` loads one storage through database/native code.

`getPartials()` and `getPartialProperty()` expose incremental storage.

`addToIterToLimitCover(classCodeSeq)` sends results into iterate-to-limit cover sets.

`printMidFirstLast(...)` prints first/middle/last code groups for diagnostics.

`loadStorage(...)` overload loads and collects storages.

`printAndLoadStorage(...)` writes code output and loads storage.

### `PolyVaryTask`

`PolyVaryTask(...)` stores polygon-vary configuration.

`call()` runs polygon vary, loads storage, and updates progress.

`checkStatus(future)` converts future success/failure into `Either`.

`autoCodesFiltered(coords, executor)` generates and filters codes at polygon coordinates.

`toCoords(points)` converts flat point lists into vectors.

`pixelColor(point)` classifies point color/coverage.

`loadStorage(classCodeSeq)` loads one sequence.

`getPartials()` and `getPartialProperty()` expose incremental storage.

`loadStorageFromDB(classCodeSeq, usedCodes, ...)` tries database/native loading while tracking duplicates.

`loadPrintedCodesStorage(...)` loads storages for codes already printed/found.

### `CycleVaryTask`

`CycleVaryTask(...)` stores cycle-vary parameters.

`call()` runs cycle-vary search and storage loading.

`checkStatus(future)` handles future failures.

`autoCodesFiltered(coords, executor)` generates candidate codes at coordinates.

`toCoords(points)` converts point data to vectors.

`pixelColor(point)` classifies point coverage/color.

`loadStorage(classCodeSeq)` loads one sequence.

`getPartials()` and `getPartialProperty()` expose incremental storage.

`loadStorageFromDB(...)`, `loadPrintedCodesStorage(...)`, and `autoVary(...)` support batch loading and candidate filtering.

## Vary/Input Windows

### `VaryWindowL`

`VaryWindowL(...)` builds the vary-L configuration window.

`getBottomVBox()` builds lower controls.

`getPoints(x, y, onePoint)` parses coordinate inputs.

`getOverride()`, `getFirstLastSelected()`, `getAddToAllPositiveSelected()`, `getAddToPlusMinusSelected()`, `getDraw()`, and `getAddToSmallCover()` expose selected options.

`extractNumberFromTextField(textField)`, `getLineNumber()`, `showMoveScreenAlert(content)`, `moveScreenToLine(index)`, `setLineNumber(lineNumber)`, and `getCoordinatesListLength()` support navigating coordinate-file lines.

### `PolyVaryLoad`

`PolyVaryLoad(...)` builds the polygon-vary load/configuration dialog.

`updateBounds()` recalculates displayed/loaded bounds.

`getPolyVaryLoad()` returns polygon and numeric bounds as a tuple.

`getAutoSmallCover()` exposes whether small-cover automation is selected.

### `AutoPolyVaryLoad`

`AutoPolyVaryLoad(...)` builds automated polygon-vary settings.

`getLoad()` returns polygon/bounds tuple.

`getOverride()`, `allPositiveIsSelected()`, `plusMinusIsSelected()`, `getMode()`, `getNumGroupToPrint()`, and `getAutoSmallCover()` expose selected modes.

`getModesHBox()` builds the mode controls.

### `SuperPolyVaryLoad`

`SuperPolyVaryLoad(...)` builds a super/magnified polygon-vary settings dialog.

`getLoad()`, `getMagnificationIsSelected()`, `getMagnification()`, and `getAutoSmallCover()` expose settings.

### `CycleVaryWindow`

`CycleVaryWindow(...)` builds the cycle-vary configuration window.

`getCoordinatesHBox()`, `getModesHBox()`, and `getLineNavigateHBox()` construct UI sections.

`show()` displays the window.

`getMagnificationIsSelected()`, `getMagnification()`, `getUseReps()`, `allPositiveIsSelected()`, `plusMinusIsSelected()`, `getMode()`, and `getNumGroupToPrint()` expose options.

`extractNumberFromTextField`, `getLineNumber`, `showMoveScreenAlert`, `moveScreenToLine`, `setLineNumber`, `getCoordinatesListLength`, and `getStartStepEnd(defaultEnd)` implement line navigation and range parsing.

`CycleVaryFunction(polygon)` starts a cycle-vary run for one polygon.

`shutdown(cyclesProgress, repsProgress, executor)` cancels/closes cycle-vary work.

`autoCycleVaryFunction(polyVals, ...)` starts automated cycle vary.

`drawCycleVary(max, maxSubdivisions, autoCover, overrideSS, ...)` draws results and manages cover automation.

### `TetraBar`

`TetraBar(parentStage)` builds a tetra/parameter-bar dialog.

`getVaryParams()` returns point lists, numeric bounds, and boolean options.

## Cover Windows and Cover Helpers

### `CoverWindow`, `CoverWindow2`, `CoverWindow3`, `CoverWindow4`

These are related cover-configuration/result windows with similar method sets.

`CoverWindow(...)` constructors build the cover UI.

`setTriplesInfo(triples)`, `appendTriplesInfo(triple)`, and `appendStablesInfo(stable)` update text areas.

`saveToFile()` persists cover text inputs/results.

`cleanTriples(string, pool)` normalizes/validates triples and likely ensures sequences exist in database.

`cleanHalfTriples(string, pool)` normalizes half-triples.

`cleanHalfTriplesCorner(string, pool)` handles corner half-triple variants.

`cleanStables(string, pool)` normalizes/validates stable code list text.

`redoInfo()` regenerates cover info text.

`show()` displays the window.

The numbered variants have mostly private versions of these helpers and likely represent workflow forks or older UI versions. Compare exact behavior before consolidating.

### `SmallCoverWindow`

`SmallCoverWindow(...)` builds the small-cover workflow window.

`saveToFile()` persists small-cover output.

`appendStablesInfo(stable)` appends stable text.

`cleanTriples(string, pool)` and `cleanStables(string, pool)` normalize inputs.

`show()` displays it.

`addPolygons(newPolygons)` appends small-cover polygon text.

### `StablesWindow`

`StablesWindow(coverWindow)` builds a helper window for generating stable/triple lists.

`setConnectionPool(pool)` injects database/native access.

`calculateAction()` calculates stables.

`calculateTriplesAction()` calculates stable/unstable/stable triples.

`calculateHalfTriplesAction()` calculates half-triples.

`showFactorTriple(triple)` displays factors for a triple.

`cleanTriples(string, pool)` normalizes triples.

`showFactorHalfTriple(item)` displays factors for a half-triple.

`trimArrayList(stringList)` trims empty/duplicate strings.

`getCombinations(topStables, unStables, bottomStables)` builds triple combinations.

`getStables(triple)` extracts stable members from a triple.

`checkTriples(string, pool)` calls backend duplicate-stable checker.

`getTriplesAction()` populates triples from current inputs.

`saveToFile()` persists generated lists.

`show()` displays the window.

`cleanHalfTriples(string, pool)` normalizes half-triples.

### `HashTriple`

`HashTriple()` constructs maps for stable, triple, half-triple, and color associations keyed by rectangles.

`addStables`, `addTriples`, and `addHalfTriples` bulk-add cover maps with colors.

`clear()` clears all maps.

`stableEntrySet`, `tripleEntrySet`, and `HalfTripleEntrySet` return rectangle key lists.

`remove(rect)` removes all entries for a rectangle.

`getStable`, `getTriple`, `getHalfTriple`, and `getColor` return mapped values.

`put(rect, stable/triple/halfTriple/color)` overloads insert values.

### `CoverInfoWindow` and `LookAtMeWindow`

`CoverInfoWindow(windowTitle)`, `show()`, and `close()` display cover info.

`LookAtMeWindow(windowTitle)`, `show()`, and `close()` display a simple attention/status window.

## Database, Query, Info, and Pattern Windows

### `DBGui`

`DBGui()` builds database selection/admin UI.

`getDbName()` returns selected database.

`getProgram()` returns selected program/mode.

`newDatabase()`, `deleteDatabase()`, `clearDatabase()`, and `selectDatabase()` implement admin actions.

### `QueryStage`

`QueryStage(stageTitle, pool, viewer)` builds a database query window.

`query(pool, viewer)` executes search by length/type/even-odd and sends results to viewer.

`show()` displays it.

`verifyEvenOdd(evenOdd)` validates pattern text.

### `InfoWindow`, `InfoWindow2`, and `GradientWindow`

`InfoWindow(...)` and `InfoWindow2(...)` build info dialogs.

`close()` and `show()` manage lifecycle.

`showInfo(pool)` loads native info and displays formatted equations/points.

`infoString(...)` formats one code's native info.

`toString(XYEta)` formats symbolic coordinate labels.

`formatConstraint(constraint)` formats linear constraints.

`InfoWindow2.count(str, target)` counts substring occurrences and supports display formatting.

`GradientWindow(...)`, `showResult()`, `infoString(...)`, `toString(XYEta)`, and `formatConstraint(...)` focus on gradient/vector display.

### Pattern Tools

`PatternCalculator()` builds a window for pattern calculations.

`getSubstringAfterPattern(string, pattern)` extracts suffix text after a marker.

`calcCodePattern(textArea)` calculates code patterns from input text.

`calcSequencePattern(string1, string2)` compares sequence patterns.

`showAlert(content)` displays validation errors.

`PatternLookupWindow(codeSequence)` builds lookup UI for one code.

`getOEPattern(codeSequence)` computes odd/even pattern.

`retrievePatternByCodeSequence()` queries pattern DB by code.

`retrievePatternsByOEPattern()` queries pattern DB by odd/even pattern.

`codeNumbersToString(currentCodeNumbers)` formats current code arrays.

`CodeAndPatternLookupWindow(iterateToLimitWindow)` builds a table-based pattern lookup.

`ScrollableTableCell.updateItem(item, empty)` renders long table cells with scrolling.

`CodeAndPattern(column1, column2)`, `calcType()`, `getCodeSequence()`, `getIterationPattern()`, and `getType()` represent table rows.

`getVBox(iterateToLimitWindow)` builds table layout.

`lookUpIterPat(limit, offset)` queries a page of pattern rows.

`getVerticalScrollbar(tableView)` obtains the table scrollbar for paging/auto-load.

`show()` displays the window.

### `IterateToLimitWindow`

`IterateToLimitWindow(pool)` builds the iterate-to-limit workflow.

`getTextAreaHBox(labelText)` builds labeled text areas.

`getRoot()` builds the scrollable root UI.

`iterateTask(...)` creates the background task for iteration.

`iterate(...)` performs iteration and returns grouped storage results.

`calcCodeNumbers(...)` calculates next code-number arrays.

`run()` validates UI input and starts execution.

`getPatterns()` parses pattern input.

`execute()` exposes the run/execute property.

`getDraw()` and `getAddToCover()` expose selected actions.

`getResults()` returns generated result groups.

`nullifyFinish()` and `nullifyResult()` reset completion/result state.

`getStage()`, `isShowing()`, and `toFront()` expose window lifecycle.

`getInfoAlertDialogue(header, content)` builds alert dialogs.

`saveContentsToFile()` persists contents.

`addToContent(code, pattern)` overloads add pattern/code rows.

`addIterPatToGarbage(iterationPattern, codeSequences)` records rejected/bad patterns.

`getIterationPattern(codeString, allPositive)` computes iteration pattern text.

`findIterationPattern()` launches pattern-finding for current input.

`roundToLargestPlace(number)` rounds display/progress numbers.

## Polygon/Input Helpers

`IterationPolyWindow()` builds polygon input for iteration.

`IterationPolyWindow.show()` displays it.

`getPolygon()` returns parsed convex polygon.

`Parallelogram()` builds parallelogram input.

`getParallelogram()` returns a convex polygon if input is valid.

`PolyLoad(...)` builds a polygon-load dialog.

`getPolyLoad()` returns loaded polygon.

`PolyTrimmer(...)` builds polygon trimming UI over a full screen/database/on-screen map.

`parseConvexPolygon(content, fullScreen)` parses polygon content.

`trim(poly, pool, stage)` trims database/cover results by polygon.

`trim(poly, onScreenSequences, saveColors)` trims current on-screen regions.

`trim(polyStr, stage)` parses and trims from text.

## Menus and Search Orchestration

### `BoyanMenu`

`BoyanMenu(...)` builds a menu/toolbar integrating cycle vary, middle vary, polygon auto vary, vary-L, and related controls.

`varyAction(title, outFile, printMid)` triggers a vary workflow and handles output.

`click(xDeg, yDeg)` handles map click coordinates in degrees.

`varyTriangles(...)` overloads run triangle vary over coordinate lists/bounds.

`varyTrianglesL(...)` overloads run vary-L searches.

`autoVary(...)` overloads run automated vary.

`findCodes(...)`, `findCodes2(...)`, `findCodes3(...)`, and `findCodes4(...)` search for code sequences from points/branches. The numbered versions are algorithm variants.

`printCodes(allCodes, file, ...)` overloads write found codes to files.

`addFirstMidLast(...)` adds first/middle/last diagnostic groupings.

`typeString()` returns selected code-type filters as text.

`getRadianCoord()` returns current coordinates in radians.

### `ColorPicker`

`ColorPicker(x, y)` builds a color chooser near a screen position.

`next(inColor)` generates the next color.

`pickColor()` shows the picker and returns a chosen color.

### `SaveV3Window`

`SaveV3Window(windowTitle)` builds a save dialog for V3/vary output.

`show()` and `close()` manage lifecycle.

`browse()` chooses path.

`saveM()` and `saveL()` save different output modes.

`clear()` clears fields.

## Frontend Risks and Optimization Backlog

High-priority frontend risks:

- `Viewer.java` is doing too much: UI construction, parsing, task orchestration, rendering, cover management, and mathematical workflow state. Bugs can cross concerns.
- Long-running tasks update JavaFX state through several partial-result mechanisms. Thread ownership should be audited before changes.
- Many file formats are implicit and parsed with string splitting. Add format docs/tests before altering.
- Some workflows duplicate cover cleaning logic across `CoverWindow`, `CoverWindow2`, `CoverWindow3`, `CoverWindow4`, `SmallCoverWindow`, and `StablesWindow`.
- Rendering appears pixel-loop heavy; large zoomed regions or many storages can be slow.

Optimization ideas:

- Split `Viewer.java` into controllers: render controller, code-input controller, cover controller, vary controller, database/search controller.
- Add a typed `CodeFileParser` and `CoverTextParser` with tests.
- Centralize cover stable/triple cleaning in one service.
- Keep rendering data immutable per frame to reduce synchronization hazards.
- Cache rendered stable polygons by `(storage, view rectangle, color)` where feasible.
- Use spatial indexes for on-screen regions instead of scanning all storages during click/hole/render operations.
- Replace ad hoc tuples (`Tuple7`, arrays, parallel arrays) with named configuration records/classes.

## Frontend Debugging Guide

When a frontend action fails, classify it:

- Input parse failure: check `Utils.splitString`, `Viewer.parseFile`, specific window parser.
- Code invalid: check `CodeSequence.create` and `ClassifiedCodeSequence.create`.
- Native failure: check `Wrapper` return status and backend logs.
- Missing database row: check `Database.loadStorage` and native save path.
- Draw missing: check `Storage.intersects`, current `PixelRadianMap`, and `renderRegion`.
- Cover mismatch: check `CoverWindow.cleanStables/cleanTriples`, `Wrapper.coverWrapper...`, and backend `verify.cpp`.
- UI freeze: check whether work is running on JavaFX thread rather than executor.

## Resume Checklist

For future documentation agents:

1. Use `viewer-support-function-index.txt` to ensure every support method is either covered here or intentionally trivial.
2. Use `Viewer.java` rows in `function-index-ctags.txt` for exact line references.
3. If more depth is required, expand one workflow at a time: cover, vary, rendering, iteration, or database query.
4. Do not edit `src` unless the user changes the constraint.
