# TEST Button And Workflow Reference

Date of analysis: 2026-05-23

This note is a second-pass user-interface and workflow reference for the TEST distribution. It focuses on what each visible control does and how the program is meant to be used effectively.

Current baseline note: this workflow guide is still useful for UI behavior, but Abdul source differs by small-cover defaults and reflection defaults, while `[MAIN]` differs further in updater removal, `HashTriple`, `SmallCoverWindow.replacePolygons`, and vary-window/task behavior. See `18` before treating a button workflow as exact for `[MAIN]`.

## Scope And Accuracy

The TEST distribution has two viewer jars:

- `billiard-viewer.jar`: byte-identical to the regular distribution jar
- `billiard-viewer-patched-hashtriple-java17.jar`: identical except for the patched `billiards.viewer.HashTriple` class

The `HashTriple` patch affects cover-display memory behavior, not button wiring. Therefore, nearly all UI behavior is the same between the regular jar and the patched TEST jar.

This guide is based on:

- source review under `src/java/billiards/viewer`
- source review under `src/java/billiards/wrapper`
- jar inventory and `javap` bytecode inspection of the TEST patched jar
- backend DLL exports and strings
- existing source-study docs in this directory
- generated control indexes in `C:/tmp/billiards_test_reverse_engineering_20260523/java_ui_controls_index.tsv` and `C:/tmp/billiards_test_reverse_engineering_20260523/java_button_action_index.csv`

Important caveat: the source tree is close to the shipped jar but not byte-identical. The jar lacks the source-tree updater class, contains some vary-window differences, and differs in `SmallCoverWindow` behavior. Where source and jar differ, this guide calls that out.

## Startup And Database Selection

The application starts in `Main`, which loads the JavaFX UI, loads the native backend through JNA, and opens the database selection flow.

### DBGui

`DBGui` is the first important operational window. It controls which SQLite-backed equation database the viewer and search tools use.

| Control | Effect |
| --- | --- |
| `New` | Creates a new database entry/name for later selection. |
| `Delete` | Deletes the selected database entry. |
| `Clear` | Clears the selected database through the backend database-clear path. |
| `Select` | Opens the main tool using the selected database. |
| `Viewer` radio | Starts the main graphical viewer workflow. |
| `Pattern Finder` radio | Starts the pattern finder workflow instead of the main viewer. |

Teaching point: the database matters because many searches, saves, and code lookups go through backend calls that query or update the selected database.

## Main Viewer Layout

The main viewer is implemented primarily by `Viewer`. It is the central canvas, code viewer, cover viewer, and launcher for specialized workflows.

The UI is dense. The practical way to teach it is by grouping controls into tasks:

- draw or inspect a polygon/code region
- calculate billiards codes and patterns
- load, create, and inspect covers
- run vary/search workflows to generate stables/triples
- save or export current views and generated results

## Canvas Navigation And View Controls

| Control | Effect |
| --- | --- |
| `Zoom` | Applies the current zoom fields to the display. Used after entering a zoom region or selecting a region to inspect. |
| `Backward` | Moves backward through the stored square/region navigation history. |
| `Forward` | Moves forward through the stored square/region navigation history. |
| `Clear` | Clears current temporary display overlays and selections. |
| `Reset` | Resets the view toward the default full-region state. |
| `Zoom To` | Zooms directly to a chosen region. |
| `Fill Screen` | Adjusts the current render to fill the visible canvas area. |
| `Clear Fills` | Clears saved fill overlays. |
| `S` | Saves the current fill/region overlay state. |
| `L` | Loads a previously saved fill/region overlay state. |
| `Show Fills` checkbox | Toggles display of stored fill overlays. |
| `AutoFill` checkbox | Enables automatic fill behavior when navigating/selecting. |
| `Select` radio | Makes canvas clicks select/inspect regions. |
| `Magnify` radio | Makes canvas clicks magnify the selected area. |
| `Demagnify` radio | Makes canvas clicks move out to a coarser view. |
| `Center` radio | Makes canvas clicks recenter the display. |

Teaching point: use `Select` for inspection, `Magnify` for local exploration, and `Reset` or `Fill Screen` when the canvas is no longer framed usefully.

## Coordinate, Polygon, And Drawing Controls

| Control | Effect |
| --- | --- |
| `draw` | Draws a square or rectangular region from the coordinate fields. |
| `Polygon` near lookup/intersection controls | Computes or displays a polygon associated with the current code/search context. |
| `Polygon` load button | Opens the polygon loading workflow. |
| `PolygonDB` | Loads polygon information from the database-backed path. |
| `Para` | Opens the parallelogram workflow. |
| `LiBainT/B` | Opens the tetrabar workflow. |
| `Trimmer` | Opens trimming tools for reducing a screen/file/cover polygon. |

The viewer has text fields for coordinates, bounds, slope/code parameters, and magnification. Many of the buttons read those fields directly, so invalid text commonly fails at parse/validation time rather than through a separate form wizard.

## Code And Pattern Calculation Controls

| Control | Effect |
| --- | --- |
| `Calculate` near code fields | Calculates code, polygon, or region information for the current input fields. |
| `Calculate Iterations` | Runs an iterative calculation for the current code/pattern setup. |
| `Calculate Add/Subtract Iterations` | Runs iterations while applying the selected add/subtract code transforms. |
| `Calculate expando` | Calculates an expanded code/region variant. |
| `Calculate expando Iteration` | Runs the expando calculation iteratively. |
| `Lookup Patterns` | Opens/uses pattern lookup for the current code or region context. |
| `LiPattern` | Opens the iterate-to-limit or pattern-limit workflow. |
| `LiPattern Calc.` | Opens the standalone pattern calculator. |
| `manual check` | Runs a manual validation/check for the current code or selected region. |
| `Add 2` / `Subtract 2` controls | Modify selected code components by two. These are used for parity-preserving code exploration. |
| `Add/Subtract 2` | Applies the forward add/subtract transform to the third code box. |
| `Add/Subtract -2` | Applies the reverse add/subtract transform to the third code box. |
| dynamic `+` buttons | Increase a displayed code component or stored code entry. |
| dynamic `-` buttons | Decrease a displayed code component or stored code entry. |
| `X` on a stored code row | Removes that stored code/selection row. |

Teaching point: the add/subtract controls are not generic arithmetic widgets. They support structured exploration of billiards code families where parity and symbolic pattern shape matter.

## Color And Display Toggles

| Control | Effect |
| --- | --- |
| `Black`, `Red`, and other color choice boxes | Choose display colors for regions, cover rectangles, selected codes, or one-by-one overlays. |
| `Red` near `Zoom To` | Sets the zoom-region display color. |
| `Red` in one-by-one controls | Sets the one-by-one overlay color. |
| cover rectangle color choice | Sets the color used for loaded cover rectangles. In the patched TEST jar this commonly becomes the `HashTriple` default color instead of being stored once per rectangle. |
| `reflect` checkbox | Toggles reflected/symmetric display or calculation behavior where supported. |
| `all` checkbox | Requests all relevant results rather than a restricted/default result where the surrounding workflow supports it. |
| `bounds` checkbox | Toggles bounds display/use in calculations. |
| `prover` checkbox | Enables prover-oriented behavior in supported calculations. |
| `offset` checkbox | Enables offset handling in supported code/region workflows. |

## File Loading And General Data Controls

| Control | Effect |
| --- | --- |
| `Load Directory` | Loads a directory-form result, typically a cover or generated output directory. |
| `Load File` | Loads a single file-form result. |
| `Merge` | Opens or runs the cover merge workflow through the backend merge path. |
| `Save Image` | Exports the current canvas image. |
| `Save V3` | Opens the save-vary3 window for saving matching or latest vary results. |
| `Load One By One File` | Loads a file for one-by-one line navigation and display. |
| `Go` in one-by-one area | Jumps to the entered one-by-one line/index. |
| `OBO Forward` | Advances to the next one-by-one entry. |
| `OBO Backward` | Moves to the previous one-by-one entry. |

Teaching point: many workflows write plain text files in the distribution directory. The UI then reloads those outputs into the viewer. The `Load Directory`, `Load File`, and one-by-one controls are how users inspect those generated artifacts after backend calculations.

## Cover Controls In The Main Viewer

| Control | Effect |
| --- | --- |
| `Cover` | Opens the main cover calculation window. |
| `Load Cover` | Loads a cover directory into the viewer. This is the critical action for viewing the shipped TEST cover. |
| `CoverInfo` | Shows or recomputes cover info/reporting for the currently loaded cover. |
| `Find Holes` | Searches for uncovered regions/holes in the current cover. |
| cover color choice | Sets the rendered cover rectangle color. |
| `Half Triple` | Present in source as a commented-out control; not expected as an active main-layout button in the current runtime. |
| `Unstable` | Present in source as a commented-out control; not expected as an active main-layout button in the current runtime. |
| `Corner` | Present in source as a commented-out control; not expected as an active main-layout button in the current runtime. |

The TEST patched jar matters most here. A large loaded cover creates very many rectangles. In the original jar, `HashTriple` stores a color entry for each loaded rectangle. In the patched jar, the cover color is stored as a default color unless a specific rectangle is explicitly recolored.

Recommended TEST cover viewing workflow:

1. Start the TEST distribution with `run2.bat`, not `run.bat`.
2. Select the desired database in `DBGui`.
3. Use `Load Cover`.
4. Select the TEST `cover` directory.
5. Use `Fill Screen`, `Zoom`, `Select`, and `Magnify` to inspect regions.
6. Use `CoverInfo` and `Find Holes` to understand what remains uncovered.

## Main Cover Window

`CoverWindow` is the main cover-generation form.

| Control | Effect |
| --- | --- |
| `Calculate` | Runs the cover calculation. In MRR mode it cleans/canonicalizes the polygon, stables, and triples, saves codes to the database, calls `Wrapper.coverWrapper`, reloads the cover, writes output files, and refreshes cover info. In All mode it calls `Wrapper.coverWrapperAll` using an existing MRR cover directory. |
| `AutoVary` | In the reviewed source this button has only commented-out code and should be treated as a stub/no-op unless the runtime jar differs. |
| `MRR` radio | Runs minimum-relevant-region style cover generation. |
| `All` radio | Runs all-cover generation from an existing MRR cover directory. |
| `Add to Small Cover` checkbox | Sends generated cover polygon data to the small-cover workflow. In the jar this path appears to replace polygons; in the source it appends/adds polygons. |

Important fields:

- polygon input
- stable-code input
- triple-code input
- digit/precision input
- magnification input
- empty/uncovered input
- directory/name fields used for output

Backend calls used by this workflow include `cover_wrapper`, `cover_wrapper_all`, database save calls, and cover loading/parsing functions.

Teaching point: `Calculate` is a write-heavy operation. It produces or updates cover files, reloads them into the viewer, and may also save related equations into the selected database.

## Small Cover Window

`SmallCoverWindow` supports small-cover calculations inside or alongside the larger cover workflow.

| Control | Effect |
| --- | --- |
| `Calculate` | Runs small-cover generation. It parses square/polygon fields, calls `Wrapper.smallCoverWrapper`, appends generated stables/triples to the main cover window, loads the resulting small-cover polygon for display, reloads the cover, and optionally prints info. |
| `MRR` radio | Runs small-cover MRR behavior. |
| `All` radio | Requests all small-cover behavior where supported. |
| `Print Info` checkbox | Prints or displays small-cover info output after calculation. |

Source-vs-jar caveat:

- source uses `addPolygons(String)` and asks for a fractional value of Pi
- jar evidence shows `replacePolygons(String)` and prompt text referring to Pi/2

Teaching point: small cover is not just a viewer overlay. It can feed generated stable/triple text back into the main cover window.

## Stables And Triples Window

`StablesWindow` is used for calculating stable and triple combinations.

| Control | Effect |
| --- | --- |
| `Calculate` | Calculates stable-related output from entered code/polygon data. |
| `Half Triples` or generated half-triple label | Computes half-triple variants where supported. |
| `Print Triple Combinations` checkbox | Prints detailed triple combination output. |

This window supports the cover workflow by producing candidate stable or triple code lists.

## Vary Workflows

The vary tools generate families of related codes, polygons, or covers. These are central to efficient exploration.

### Main Viewer Vary Launch Buttons

| Control | Effect |
| --- | --- |
| `Auto Vary3` | Launches an automated vary-3 workflow. |
| `BoyanVary` | Launches the Boyan/poly vary workflow. |
| `varyL` | Launches length-vary workflow. |
| `LiMVL` | Launches middle-vary-by-length workflow. |
| `LiCycle` | Launches cycle-vary workflow. |
| `AutoPolyVary` / `LiLuMaxVary` | Launches automated polygon vary workflow. Label differs between source/jar contexts. |
| `SuperLiLuVary` | Launches a higher-level/super polygon vary workflow. |

### VaryWindowL

| Control | Effect |
| --- | --- |
| constructor-provided main button, such as `Vary` or `Middle Vary` | Runs the configured vary task using the form fields. |
| `Backward` | Moves backward through loaded vary output lines. |
| `Forward` | Moves forward through loaded vary output lines. |
| `Go` | Jumps to a specific vary output line/index. |

### PolyVaryLoad And AutoPolyVaryLoad

| Control | Effect |
| --- | --- |
| main load/calculate button | Parses polygon-vary settings and starts the vary task. |
| checkboxes in the form | Enable optional vary modes such as speed, filtering, cover interaction, or task-specific constraints. Exact choices differ slightly between source and jar. |

Jar evidence includes strings such as `Marco speed is ON`, `Boyan Step`, `BoyanVary #`, `Magnify x`, and `Change # of coordinates by`. These indicate extra runtime options in the shipped jar that are not fully represented in the current source tree.

### SuperPolyVaryLoad

| Control | Effect |
| --- | --- |
| main load/calculate button | Starts the super polygon vary workflow with the entered bounds and options. |

Source-vs-jar caveat: the source constructor accepts an extra checkbox compared with the jar constructor signature found by `javap`.

### CycleVaryWindow

| Control | Effect |
| --- | --- |
| main calculate/load button | Starts the cycle vary task. |
| `Load OBO` | Loads one-by-one output for cycle-vary inspection. |
| `Clear` | Clears loaded cycle-vary/one-by-one state. |
| `Backward` | Moves backward through cycle-vary output entries. |
| `Forward` | Moves forward through cycle-vary output entries. |
| `Go` | Jumps to a selected cycle-vary output entry. |

## Boyan Menu

`BoyanMenu` is a launcher around several vary and polygon-generation tasks.

| Control | Effect |
| --- | --- |
| `Vary` | Starts a Boyan vary operation. |
| `LiMV` | Starts a length/middle-vary variant. |
| `Vary3B` | Starts a vary-3B operation. |
| `V4` | Starts a vary-4 operation. |
| `Make Poly` | Builds or loads a polygon from the current Boyan-menu fields. |
| type radio buttons or choices | Select the family/type of vary or polygon operation. |

Teaching point: Boyan-menu actions are launchers. The detailed effect depends heavily on the numeric fields and selected type.

## Iterate-To-Limit And Pattern Tools

### IterateToLimitWindow

| Control | Effect |
| --- | --- |
| `Find Pattern` | Searches for a limiting or repeating pattern from the entered sequence/settings. |
| `Lookup` | Looks up pattern data for the entered code/context. |
| `Run` | Runs the iterate-to-limit calculation. |
| `Clear` | Clears the window output/input state. |
| `Draw` checkbox | Draws resulting regions/patterns in the viewer. |
| `Add To Cover` checkbox | Sends discovered output into the cover workflow. |

### PatternFinder

| Control | Effect |
| --- | --- |
| `Calculate` | Calculates pattern data from the current inputs. |
| `Clean` | Cleans or canonicalizes the current pattern/code input. |
| `Super Check` | Runs a stronger validation/check path. |
| `^` | Moves selected pattern/result upward. |
| `v` | Moves selected pattern/result downward. |
| `Calc & Ext` | Calculates and extends the current pattern. |
| `Search` | Searches for matching patterns/codes. |
| `1 Code` | Opens or runs a single-code analysis path. |
| `DUPLICATES` | Searches for duplicate pattern/code output. |
| `Add +` | Adds a plus/extension entry. |
| `LR` checkbox | Enables left-right pattern behavior. |
| `check empties` checkbox | Includes empty-region checks. |
| type filters | Restrict the pattern search by selected type/family. |

### Other Pattern Windows

| Window | Control | Effect |
| --- | --- | --- |
| `OneCodeWindow` | `Calculate` | Calculates data for one code. |
| `PatternLookupWindow` | `Copy` | Copies selected lookup output. |
| `CodeAndPatternLookupWindow` | `Add` | Adds selected code/pattern data into the active context. |
| `PatternCalculator` | `Calculate pattern` | Calculates the pattern for entered code data. |
| `SearchWindow` | `Search` | Searches for entered pattern/code terms. |
| `QueryStage` | `Search` | Runs a database-backed query. |

## Information And Diagnostics

| Control | Effect |
| --- | --- |
| `Info` | Opens or refreshes information about the selected/current region, code, or cover. |
| `Info2` | Opens a second information/calculation window with additional fields. |
| `Show` in info windows | Displays the requested information. |
| `gradient` | Opens gradient calculation. |
| `Calculate` in GradientWindow | Calls the gradient backend path and displays the result. |
| `LookatMe` | Opens or runs a focused inspection path for the current region/code. |
| progress `Cancel` | Calls cancellation logic for a running backend task. |

The backend exposes `backend_cancel`, so long-running native tasks are designed to be cancellable through progress windows.

## PolyTrimmer

`PolyTrimmer` reduces polygon data using screen, file, or cover inputs.

| Control | Effect |
| --- | --- |
| `Trim Screen` | Trims using the current screen/view polygon. |
| `Trim File` | Trims polygon data from a file. |
| `Trim Cover` | Trims based on cover data. |

## SaveV3Window

| Control | Effect |
| --- | --- |
| `Browse` | Chooses a destination file/path. |
| `Save Matching` | Saves matching vary-3 results. |
| `Save Latest Vary` | Saves the latest vary output. |
| clear-file button | Clears the selected output file/path field. |

## Update Button Caveat

The source tree contains a `Check for Updates` button and `Updater.class` source/build artifact. The shipped finder jar comparison did not contain `Updater.class`. Treat update UI behavior as source-tree-only unless a different runtime jar is provided.

## Practical Teaching Workflow

A productive teaching order is:

1. Start with TEST `run2.bat` so the patched jar is used.
2. Select a database in `DBGui`.
3. Use `Load Cover` to load the TEST `cover` directory.
4. Use `Fill Screen`, `Select`, `Magnify`, `Zoom`, `Backward`, and `Forward` to teach canvas navigation.
5. Use cover color controls to show how rendered cover rectangles are layered.
6. Use `CoverInfo` and `Find Holes` to explain what the cover proves and what remains uncovered.
7. Open `Cover` to show how a cover run is configured: polygon, stables, triples, precision, magnifications, and empty regions.
8. Open `LiCover` / `SmallCoverWindow` to show how local small-cover calculations can feed generated stables/triples back into the main cover workflow.
9. Use one-by-one loading and vary windows to inspect generated candidate lists line by line.
10. Use pattern tools only after the user understands that codes, patterns, covers, and database saves are connected.

For the TEST distribution specifically, make this distinction explicit:

- the large shipped cover is data
- the backend DLL is unchanged
- the patched jar changes Java memory behavior while loading/rendering cover rectangles
- `run2.bat` is the launcher that combines the larger heap settings with the patched jar

## Complete Detected Main-Viewer Control Inventory

The following inventory is the source-indexed main-window button/control list from `Viewer.java`. Line numbers refer to the reviewed source tree, not necessarily the patched TEST jar bytecode. The TEST jar differs in a small number of UI/vary classes, but the patched `HashTriple` class does not change these controls.

| Source line | Control text / variable | Operational role |
| ---: | --- | --- |
| 248 | `Lookup Patterns` / `lookupButton` | Looks up patterns for the current code/context. |
| 250 | `Polygon` / `intersectPolygonButton` | Computes or displays an intersecting polygon for the current lookup/context. |
| 262 | `Add 2` / `increaseBox1` | Adds two to the first selected code component. |
| 263 | `Subtract 2` / `decreaseBox1` | Subtracts two from the first selected code component. |
| 266 | `Add 2` / `increaseBox2` | Adds two to the second selected code component. |
| 267 | `Subtract 2` / `decreaseBox2` | Subtracts two from the second selected code component. |
| 269 | `Triples` / `TokarskyTriples` / `stablesButton` | Opens stable/triple calculation tools. |
| 272 | `Add/Subtract 2` / `addSubtractBox3` | Applies the forward add/subtract transform to the third code box. |
| 273 | `Add/Subtract -2` / `addSubtractReverseBox3` | Applies the reverse add/subtract transform to the third code box. |
| 288 | `draw` / `drawSquareButton` | Draws the entered square/rectangle. |
| 321 | `Calculate Add/Subtract Iterations` / `addSubtractIterationsButton` | Runs add/subtract iteration calculation. |
| 333 | `Calculate expando` / `expandoButton` | Calculates the expanded code/region form. |
| 342 | `Calculate expando Iteration` / `expandoCalculateButton` | Iterates the expando calculation. |
| 346 | `Calculate Iterations` / `iterationsCalculateButton` | Runs normal iteration calculation. |
| 349 | `Calculate` / `btnCalculate2` | Runs the nearby calculation group. |
| 369 | `LiPattern` / `iterateToLimitBtn` | Opens iterate-to-limit/pattern-limit workflow. |
| 427 | `Zoom` / `zoomButton` | Applies zoom settings to the canvas. |
| 440 | `Backward` / `backwardSquareButton` | Moves backward in square/view history. |
| 441 | `Forward` / `forwardSquareButton` | Moves forward in square/view history. |
| 444 | `Clear` / `clearBtn` | Clears temporary display state. |
| 445 | `Reset` / `resetBtn` | Resets the view state. |
| 446 | `Load Directory` / `loadDirectoryButton` | Loads a directory-form output. |
| 447 | `Load File` / `btnLoadFile` | Loads a single file-form output. |
| 448 | `Info` / `infoButton` | Opens current info/details. |
| 449 | `gradient` / `gradientButton` | Opens gradient calculation. |
| 450 | `LookatMe` / `lookAtMeButton` | Runs focused inspection for the current region/code. |
| 451 | `Classify` / `classifyBtn` | Classifies the current code/region. |
| 459 | `Calculate` / `btnCalculate` | Runs the main calculation group. |
| 462-464 | `Black` color boxes / `cboxRegionColor0..2` | Set region display colors. |
| 470 | `Search` / `queryButton` | Opens/runs database query search. |
| 471 | `Polygon` / `polyLoadButton` | Opens polygon loading workflow. |
| 472 | `PolygonDB` / `polyLoadDBButton` | Loads polygon data from database path. |
| 473 | `Para` / `parallelogramButton` | Opens parallelogram workflow. |
| 474 | `Merge` / `mergeButton` | Runs/opens cover merge workflow. |
| 476 | `Trimmer` / `newPolyTrimBtn` | Opens polygon/cover trimming tools. |
| 478 | `Zoom To` / `zoomRegionButton` | Zooms to an entered or selected region. |
| 479 | `Red` / `zoomColorButton` | Sets zoom-region color. |
| 481 | `Fill Screen` / `fillScreenBtn` | Fits current drawing to screen. |
| 482 | `Clear Fills` / `clearFillsBtn` | Clears saved fill overlays. |
| 483 | `S` / `saveFillBtn` | Saves fill overlay state. |
| 484 | `L` / `loadFillBtn` | Loads fill overlay state. |
| 487 | `LiBainT/B` / `tetrabarButton` | Opens tetrabar workflow. |
| 488 | `Load One By One File` / `btnLoadOBOFile` | Loads one-by-one inspection file. |
| 490 | `Go` / `btnGo` | Jumps to a one-by-one entry. |
| 492 | `OBO Forward` / `btnOBOForward` | Moves to next one-by-one entry. |
| 493 | `OBO Backward` / `btnOBOBackward` | Moves to previous one-by-one entry. |
| 495 | `Red` / `oboCBoxColor` | Sets one-by-one overlay color. |
| 499 | `Find Holes` / `holeFinderButton` | Finds uncovered holes in a cover. |
| 501 | `Load Cover` / `loadCoverButton` | Loads a cover directory into the viewer. |
| 505 | `Cover` / `coverBtn` | Opens main cover calculation window. |
| 506 | `Half Triple` / `halfTripleBtn` | Commented out in reviewed source; not expected active. |
| 507 | `Unstable` / `unstableBtn` | Commented out in reviewed source; not expected active. |
| 508 | `Corner` / `cornerBtn` | Commented out in reviewed source; not expected active. |
| 509 | `Black` / `covRectsColorBox` | Sets loaded cover rectangle color. |
| 513 | `Save V3` / `saveV3Btn` | Opens save-vary3 workflow. |
| 515 | `CoverInfo` / `coverInfoBtn` | Displays/recomputes cover information. |
| 529 | `Auto Vary3` / `autoVaryBtn` | Launches automated vary-3 workflow. |
| 530 | `BoyanVary` / `polyVaryBtn` | Launches Boyan/poly vary workflow. |
| 531 | `varyL` / `varyLBtn` | Launches length-vary workflow. |
| 535 | `LiMVL` / `middleVaryLBtn` | Launches middle-vary-by-length workflow. |
| 538 | `LiCycle` / `cycleVaryButton` | Launches cycle-vary workflow. |
| 545 | `AutoPolyVary` / `LiLuMaxVary` / `autoPolyVaryBtn` | Launches automated polygon vary workflow. |
| 547 | `SuperLiLuVary` / `superPolyVaryBtn` | Launches super polygon vary workflow. |
| 552 | `LiCover` / `smallCoverButton` | Opens small-cover workflow. |
| 563 | `Check for Updates` / `updateButton` | Source-tree-only updater path; not found in shipped jar class set. |
| 567 | `LiPattern Calc.` / `patternCalculatorBtn` | Opens standalone pattern calculator. |
| 1039 | `manual check` / `checkButton` | Runs manual validation/check path. |
| 1744 | `Save Image` / `btnSaveImage` | Exports current canvas image. |
| 3515 | `Info2` / `info2Button` | Opens secondary information window. |
| 3559 | `calculate` / `cal` | Runs a local calculation in the surrounding info/region context. |
| 5462 | selected-region color choice / `cboxCodeSequence` | Sets color for selected code/region display. |
| 5488 | `X` / `xBtn` | Deletes a selected stored code row. |
| 5820-5821 | generated `+` / `-` buttons | Increment/decrement generated per-code components. |

## Complete Detected Child-Window Button Families

The generated control index found the following non-main windows with active buttons or button-like controls. Their grouped behavior is described above; this list is included as an audit checklist.

| Window/class | Controls detected |
| --- | --- |
| `DBGui` | `New`, `Delete`, `Clear`, `Select`, `Viewer`, `Pattern Finder` |
| `CoverWindow` | `Calculate`, `AutoVary`, `MRR`, `All`, `Add to Small Cover` |
| `SmallCoverWindow` | `Calculate`, `MRR`, `All`, `Print Info` |
| `StablesWindow` | `Calculate`, half-triples button, `Print Triple Combinations` |
| `VaryWindowL` | constructor-provided main action, `Backward`, `Forward`, `Go` |
| `CycleVaryWindow` | constructor-provided main action, `Load OBO`, `Clear`, `Backward`, `Forward`, `Go` |
| `AutoPolyVaryLoad` | main load/calculate action plus task-option checkboxes |
| `PolyVaryLoad` | main load/calculate action plus task-option checkboxes |
| `SuperPolyVaryLoad` | main load/calculate action plus task-option checkboxes |
| `PolyLoad` | polygon load/parse controls |
| `Parallelogram` | parallelogram load/parse controls |
| `TetraBar` | tetrabar load/parse controls |
| `IterateToLimitWindow` | `Find Pattern`, `Lookup`, `Run`, `Clear`, `Draw`, `Add To Cover` |
| `PatternFinder` | `Calculate`, `Clean`, `Super Check`, `^`, `v`, `Calc & Ext`, `Search`, `1 Code`, `DUPLICATES`, `Add +`, `LR`, `check empties`, type filters |
| `SearchWindow` | `Search` |
| `OneCodeWindow` | `Calculate` |
| `PatternLookupWindow` | `Copy` |
| `CodeAndPatternLookupWindow` | `Add` |
| `QueryStage` | `Search` |
| `GradientWindow` | `Calculate` |
| `InfoWindow` | `Show` |
| `InfoWindow2` | `Show` and local calculation controls |
| `PatternCalculator` | `Calculate pattern` |
| `PolyTrimmer` | `Trim Screen`, `Trim File`, `Trim Cover` |
| `SaveV3Window` | `Browse`, `Save Matching`, `Save Latest Vary`, clear-file control |
| progress windows/tasks | `Cancel` |
