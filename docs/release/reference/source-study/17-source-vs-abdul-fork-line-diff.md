# Source Versus Abdul Fork Line Diff

Date of analysis: 2026-05-23

Follow-up note from 2026-06-15: Ghidra decompilation of `[MAIN]` `backend.dll` confirms that `[MAIN]` does not use Abdul's vector-buffer `eliminate_phi` implementation. `[MAIN]` matches the older source-style per-thread `std::set` / `_Rb_tree` insertion shape for that worker. See `19-main-backend-dll-vs-abdul-source.md`.

This note compares the source tree against the Abdul fork at:

```text
-Abdul-s-fork-BilliardsEverything
```

This is a normalized text comparison. CRLF/LF line-ending differences were ignored so only real text/code changes are counted.

No code was changed. Generated evidence was written to:

```text
C:/tmp/billiards_source_runtime_abdul_compare_20260523
```

## Git Metadata

The Abdul fork is a git checkout with two commits:

```text
093d4d9 (HEAD -> main, origin/main, origin/HEAD) rename
6212490 Initial commit (without large binaries)
```

Fuller metadata:

| Commit | Author date | Commit date | Message |
| --- | --- | --- | --- |
| `093d4d9be72e66c478a5bfbd13df86d1be01f7ad` | 2026-05-23 16:51:40 -0600 | 2026-05-23 16:51:40 -0600 | `rename` |
| `6212490eb6f8790ed50a3f7372d64545cd842ecc` | 2026-05-21 13:51:19 -0600 | 2026-05-21 13:51:19 -0600 | `Initial commit (without large binaries)` |

`git status --short` in the Abdul fork was clean.

## Normalized Comparison Summary

Raw hashes made many files look different because line endings differ. After normalizing line endings:

```text
ABDUL_ONLY: 4
BINARY_OR_UNREADABLE: 8
DIFFERENT_TEXT: 9
SAME_NORMALIZED: 248
```

The real text differences are:

| File | Hunks | Added in Abdul | Deleted from source | Meaning |
| --- | ---: | ---: | ---: | --- |
| `package-mac.sh` | 1 | 1 | 1 | macOS package version 2.2 to 2.3. |
| `src/backend/cpp/bounding_inequalities.cpp` | 2 | 35 | 16 | Abdul changes zero-phi accumulation from per-thread set to vector plus periodic sort/unique buffer. |
| `src/backend/cpp/unfolding.cpp` | 2 | 2 | 2 | Abdul comments out two `comb` debug prints. |
| `src/java/billiards/viewer/AutoPolyVaryLoad.java` | 1 | 1 | 1 | Abdul defaults small-cover add checkbox to false. |
| `src/java/billiards/viewer/CoverWindow.java` | 1 | 1 | 1 | Abdul defaults add-to-small-cover checkbox to false. |
| `src/java/billiards/viewer/Main.java` | 1 | 1 | 1 | Abdul version string changes `10.0.12` to `10.0.14`. |
| `src/java/billiards/viewer/PolyVaryLoad.java` | 1 | 1 | 1 | Abdul defaults small-cover add checkbox to false. |
| `src/java/billiards/viewer/SuperPolyVaryLoad.java` | 1 | 1 | 1 | Abdul defaults small-cover add checkbox to false. |
| `src/java/billiards/viewer/Viewer.java` | 2 | 39 | 1 | Abdul makes reflection selected by default and adds reflection-update calls/listener. |

Abdul-only non-code/artifact files:

| File | Meaning |
| --- | --- |
| `.gitignore` | Ignores build outputs and large archives. |
| `src/backend/.DS_Store` | macOS metadata, not source. |
| `src/backend/cpp/.DS_Store` | macOS metadata, not source. |
| `src/no.txt` | Captured run/output text ending with `BilliardViewer 10.0.14`; not code. |

Binary/unreadable files were mostly packaged jars/dylibs. The source and Abdul packaged `app/billiard-viewer.jar` files are byte-identical, and the source and Abdul packaged `app/backend/shared/libbackend.dylib` files are byte-identical. Abdul's source edits are therefore not reflected in those packaged binaries.

Generated evidence:

```text
C:/tmp/billiards_source_runtime_abdul_compare_20260523/source_vs_abdul_normalized_text_summary.csv
C:/tmp/billiards_source_runtime_abdul_compare_20260523/source_vs_abdul_normalized_text_diff.csv
C:/tmp/billiards_source_runtime_abdul_compare_20260523/source_to_abdul_diffstat.csv
C:/tmp/billiards_source_runtime_abdul_compare_20260523/source_to_abdul_normalized_unified.diff
```

## Line-By-Line Diff: `package-mac.sh`

```diff
--- source/package-mac.sh
+++ abdul/package-mac.sh
@@ -141,9 +141,9 @@
   --runtime-image "$RUNTIME_IMAGE" \
   --dest "$DIST_DIR" \
   --icon "$ICON_PATH" \
   --java-options "-Djna.library.path=\$APPDIR/backend/shared" \
   --java-options "--add-modules=javafx.controls,javafx.fxml,java.sql" \
-  --app-version 2.2
+  --app-version 2.3
```

Meaning: Abdul bumps only the macOS packaging app-version value. This does not affect Java or C++ logic.

## Line-By-Line Diff: `src/backend/cpp/bounding_inequalities.cpp`

```diff
--- source/src/backend/cpp/bounding_inequalities.cpp
+++ abdul/src/backend/cpp/bounding_inequalities.cpp
@@ -127,52 +127,71 @@
     unsigned int concurrency = std::thread::hardware_concurrency();
     if (concurrency == 0) concurrency = 4;
 
     // Convert positive_phi to vector for indexing/chunking
     std::vector<LinComArrZ<XYEtaPhi>> pos_vec(positive_phi.begin(), positive_phi.end());
-    // assign max compuatation thread according to computer performence
-    // use block size to serpate the word.
     std::size_t n = pos_vec.size();
-    // 0 < equation for each equation in the inequalities
-    // so add the ones with negative theta to the ones with positive theta
-    // this is sorted so the iteration order when refining the region is always the same
 
     // detect number of thread in computer
-    // if large set, small blocksize to allow time for memory swap
     std::size_t block_size;
     std::size_t task_num;
-    if (n<200){
+    if (n < 200) {
         block_size = (n + concurrency - 1) / concurrency;
         task_num = concurrency;
-    }else{
+    } else {
         block_size = 1; 
-        task_num = (n/block_size)+1;
+        task_num = (n / block_size) + 1;
     }
-    // Each thread accumulates in its own set to avoid locking
-    std::vector<std::set<LinComArrZ<XYEta>>> thread_zero_phi(task_num);
+
+    // Each thread accumulates in a chunked vector instead of a set to save RAM
+    std::vector<std::vector<LinComArrZ<XYEta>>> thread_zero_phi(task_num);
     boost::asio::thread_pool pool(task_num);
+
+    // Memory budget: ~24MB per thread
+    const size_t MAX_BUFFER_SIZE = 1000000;
 
     for (unsigned int t = 0; t < task_num; ++t) {
         std::size_t begin = t * block_size;
         std::size_t end = std::min(begin + block_size, n);
 
-        boost::asio::post(pool, [begin, end, t, &pos_vec, &negative_phi, &thread_zero_phi] {
+        boost::asio::post(pool, [begin, end, t, &pos_vec, &negative_phi, &thread_zero_phi, MAX_BUFFER_SIZE] {
+            
+            std::vector<LinComArrZ<XYEta>> local_buffer;
+            local_buffer.reserve(MAX_BUFFER_SIZE);
+
             for (std::size_t i = begin; i < end; ++i) {
                 auto& positive_equation = pos_vec[i];
                 for (const auto& negative_equation : negative_phi) {
+                    
+                    // Original math logic completely intact
                     auto zero_equation = LinComArrZ<XYEtaPhi>::add(positive_equation, negative_equation);
                     auto no_phi = remove_phi(zero_equation);
                     no_phi.divide_content();
-                    thread_zero_phi[t].insert(no_phi);
+                    
+                    local_buffer.push_back(no_phi);
+
+                    // Safety valve: clean up buffer when it gets too large
+                    if (local_buffer.size() >= MAX_BUFFER_SIZE) {
+                        std::sort(local_buffer.begin(), local_buffer.end());
+                        auto last = std::unique(local_buffer.begin(), local_buffer.end());
+                        local_buffer.erase(last, local_buffer.end());
+                    }
                 }
             }
+
+            // Final clean up of remaining items in the buffer
+            std::sort(local_buffer.begin(), local_buffer.end());
+            auto last = std::unique(local_buffer.begin(), local_buffer.end());
+            local_buffer.erase(last, local_buffer.end());
+
+            thread_zero_phi[t] = std::move(local_buffer);
         });
     }
 
     pool.join();
 
-    // Merge per-thread results
+    // Merge flat vectors back into the expected std::set return type
     std::set<LinComArrZ<XYEta>> zero_phi;
     for (unsigned int t = 0; t < task_num; ++t) {
         zero_phi.insert(thread_zero_phi[t].begin(), thread_zero_phi[t].end());
     }
```

Meaning:

- Source stores each thread's zero-phi equations in `std::set<LinComArrZ<XYEta>>`.
- Abdul stores each thread's zero-phi equations in `std::vector<LinComArrZ<XYEta>>`.
- Abdul periodically sorts and deduplicates the vector when it reaches `MAX_BUFFER_SIZE`.
- Abdul still returns a final `std::set`, so final duplicate elimination remains.
- The mathematical expression being generated is unchanged: add positive and negative phi equations, remove phi, divide content.

Risk notes:

- This is intended as a memory/performance tradeoff.
- It can reduce per-insertion tree overhead from `std::set`.
- It may increase temporary duplicate storage between cleanups.
- If the unique vector remains near or above `MAX_BUFFER_SIZE`, the loop may sort/unique very frequently.
- `task_num = (n / block_size) + 1` when `block_size = 1` still creates roughly one task per positive-phi equation plus one extra empty task. That was already present in the source logic after block size becomes one.
- Abdul packaged `libbackend.dylib` is identical to the source packaged `libbackend.dylib`, so this source change was not built into Abdul's packaged native binary.

## Line-By-Line Diff: `src/backend/cpp/unfolding.cpp`

```diff
--- source/src/backend/cpp/unfolding.cpp
+++ abdul/src/backend/cpp/unfolding.cpp
@@ -536,11 +536,11 @@
     pool.join();
-std::cout<< "comb" << std::endl;
+//std::cout<< "comb" << std::endl;
     // Merge thread_curves into the final curves
@@ -617,11 +617,11 @@
     pool.join();
-    std::cout<< "comb" << std::endl;
+    //std::cout<< "comb" << std::endl;
     // Merge results
```

Meaning:

- Abdul comments out two debug prints.
- Source packaged macOS `libbackend.dylib` and Abdul packaged macOS `libbackend.dylib` both still contain the `comb` string, confirming Abdul's packaged dylib was not rebuilt from this source edit.

## Line-By-Line Diff: Small-Cover Defaults

### `AutoPolyVaryLoad.java`

```diff
--- source/src/java/billiards/viewer/AutoPolyVaryLoad.java
+++ abdul/src/java/billiards/viewer/AutoPolyVaryLoad.java
@@ -163,11 +163,11 @@
 		autoSmallCoverBox.setIndeterminate(false);
 		autoSmallCoverBox.setAllowIndeterminate(false);
-		autoSmallCoverBox.setSelected(true);
+		autoSmallCoverBox.setSelected(false);
 		autoSmallCoverBox.setText("Add codes to small cover");
```

### `CoverWindow.java`

```diff
--- source/src/java/billiards/viewer/CoverWindow.java
+++ abdul/src/java/billiards/viewer/CoverWindow.java
@@ -115,11 +115,11 @@
         // squaresCheckBox.setSelected(true);
-        addToSmallCoverCB.setSelected(true);
+        addToSmallCoverCB.setSelected(false);
```

### `PolyVaryLoad.java`

```diff
--- source/src/java/billiards/viewer/PolyVaryLoad.java
+++ abdul/src/java/billiards/viewer/PolyVaryLoad.java
@@ -147,11 +147,11 @@
         autoSmallCoverBox.setIndeterminate(false);
         autoSmallCoverBox.setAllowIndeterminate(false);
-        autoSmallCoverBox.setSelected(true);
+        autoSmallCoverBox.setSelected(false);
         autoSmallCoverBox.setText("Add codes to small cover");
```

### `SuperPolyVaryLoad.java`

```diff
--- source/src/java/billiards/viewer/SuperPolyVaryLoad.java
+++ abdul/src/java/billiards/viewer/SuperPolyVaryLoad.java
@@ -205,11 +205,11 @@
 		autoSmallCoverBox.setIndeterminate(false);
 		autoSmallCoverBox.setAllowIndeterminate(false);
-		autoSmallCoverBox.setSelected(true);
+		autoSmallCoverBox.setSelected(false);
```

Meaning:

- Source defaults these workflows to adding generated codes to small cover.
- Abdul defaults them not to add generated codes to small cover unless the user opts in.
- This is a real workflow/default change, not a cosmetic edit.
- Abdul's packaged app jar is identical to source's packaged app jar, so this changed default is not present in Abdul's packaged jar until rebuilt.

## Line-By-Line Diff: `src/java/billiards/viewer/Main.java`

```diff
--- source/src/java/billiards/viewer/Main.java
+++ abdul/src/java/billiards/viewer/Main.java
@@ -16,11 +16,11 @@
     private final ExecutorService executor = Executors.newFixedThreadPool(Utils.numThreads);
     private ConnectionPool pool = null;
 
-    private final String versionNumber = "10.0.12";
+    private final String versionNumber = "10.0.14";
```

Meaning:

- Abdul source identifies itself as `10.0.14`.
- Source tree identifies itself as `10.0.12`.
- Abdul packaged jar still hashes identically to source packaged jar and contains `10.0.12`, not `10.0.14`.

## Line-By-Line Diff: `src/java/billiards/viewer/Viewer.java`

```diff
--- source/src/java/billiards/viewer/Viewer.java
+++ abdul/src/java/billiards/viewer/Viewer.java
@@ -2653,20 +2653,39 @@
-        reflectCheckBox.setSelected(false);
+        reflectCheckBox.setSelected(true);
         reflectCheckBox.setOnAction(event -> {
             if (reflectCheckBox.isSelected()) {
                 final Affine reflectTransform = new Affine();
                 reflectTransform.setMyy(-1);
                 reflectTransform.setTy(imageStack.getBoundsInLocal().getHeight());
                 imageStack.getTransforms().add(reflectTransform);
             } else {
                 imageStack.getTransforms().clear();
             }
+        });
+
+        reflectCheckBox.selectedProperty().addListener((observable, oldValue, newValue) -> {
+            if (newValue) { // If true (selected)
+                final Affine reflectTransform = new Affine();
+                reflectTransform.setMyy(-1);
+                reflectTransform.setTy(imageStack.getBoundsInLocal().getHeight());
+                imageStack.getTransforms().add(reflectTransform);
+            } else {
+                imageStack.getTransforms().clear();
+            }
+        });
+
+        Platform.runLater(() -> {
+    // If you want it checked on boot:
+            reflectCheckBox.setSelected(true); 
+            
+            // OR, if you want it to process its default state (even if false):
+            // Just extract to a method like in Option 2 and call it here.
         });
```

Second hunk:

```diff
--- source/src/java/billiards/viewer/Viewer.java
+++ abdul/src/java/billiards/viewer/Viewer.java
@@ -3893,10 +3912,29 @@
     // Do initial rendering
     public void start(final ExecutorService executor) {
         renderRegions(onScreenSequences, guideLinesImageView, regionsImageView, executor);
         mainWindow.show();
+        Platform.runLater(() -> {
+            
+        // If you used Option 2 (The Best Practice) from the previous answer:
+        updateReflection();
+        
+        /* * OR, if you used Option 1 (The Quick Fix) and didn't make a new method:
+            * reflectCheckBox.getOnAction().handle(null);
+            */
+    });
+    }
+    private void updateReflection() {
+    if (reflectCheckBox.isSelected()) {
+        final Affine reflectTransform = new Affine();
+        reflectTransform.setMyy(-1);
+        reflectTransform.setTy(imageStack.getBoundsInLocal().getHeight());
+        imageStack.getTransforms().add(reflectTransform);
+    } else {
+        imageStack.getTransforms().clear();
+    }
     }
```

Meaning:

- Source default: reflection checkbox starts unselected.
- Abdul default: reflection checkbox starts selected.
- Abdul adds both a selected-property listener and a later `updateReflection()` call during `start`.

Risk notes:

- The implementation can add reflection transforms in more than one place: action handler, selected-property listener, `Platform.runLater` in the constructor area, and `updateReflection()` in `start`.
- When selected becomes true repeatedly, the code adds a transform without first clearing existing transforms.
- If the selected-property listener and `updateReflection()` both run during startup, duplicate transforms may be added.
- Two vertical reflections cancel visually if both are applied, while still leaving transform-stack state changed. This needs runtime testing before treating Abdul's reflection change as safe.
- This change is not present in Abdul's packaged app jar because that jar is byte-identical to the source app jar.

## Abdul-Only `src/no.txt`

`src/no.txt` is not code. It appears to be captured output from a run. It includes stable-square listings, uncovered-coordinate data, and ends with:

```text
260464 stable squares used in the cover
0 triple squares used in the cover
26 stables used in the cover
0 triples used in the cover
MRR at 20 decimals, deepest magnification 20
Total stable cost: 18248475
475899 squares were not filled in
Not Covered
Time elapsed: 15.113s
BilliardViewer 10.0.14
<===========--> 85% EXECUTING [4m 14s]
> :run
```

Meaning:

- It is evidence that someone ran or captured output from something labeled `BilliardViewer 10.0.14`.
- It is not source code and does not prove Abdul's packaged jar was rebuilt.
- The actual Abdul packaged jar hash equals the source packaged jar hash and contains `10.0.12`.

## Feature Impact Summary

| Change | Source behavior | Abdul source behavior | Built into Abdul packaged jar/dylib? | Impact |
| --- | --- | --- | --- | --- |
| Java version string | `10.0.12` | `10.0.14` | No | Label/version only until rebuilt. |
| AutoPoly small-cover default | true | false | No | Workflow default change. |
| PolyVary small-cover default | true | false | No | Workflow default change. |
| SuperPoly small-cover default | true | false | No | Workflow default change. |
| CoverWindow add-to-small-cover default | true | false | No | Workflow default change. |
| Viewer reflection default | false | true | No | Startup display behavior change, possible duplicate-transform risk. |
| Bounding inequalities zero-phi storage | per-thread `std::set` | vector plus periodic sort/unique | No | Intended native memory/perf change, needs benchmark. |
| Unfolding `comb` debug prints | printed | commented out | No | Removes console noise if rebuilt. |
| macOS app package version | 2.2 | 2.3 | Script only | Packaging metadata. |

## Does Abdul Include Marco Speed?

No, not the compiled runtime's `marcoSpeed` feature.

Abdul source contains many comments with `Marco Mai`, inherited from the source tree. That is not the same as the regular/TEST runtime jar's `Marco speed is ON` / `marcoSpeed` UI feature.

The exact markers:

```text
Marco speed is ON
marcoSpeed
```

are absent from Abdul source and source tree source. They are present in the regular and TEST runtime jars.

## Does Abdul Include The TEST HashTriple Patch?

No.

Abdul source still has the source-tree `HashTriple.java` behavior, not the patched TEST jar behavior. The TEST patch exists only in:

```text
[TEST] ... /billiard-viewer-patched-hashtriple-java17.jar
```

Specifically, only the patched jar changes `billiards/viewer/HashTriple.class` to avoid per-rectangle color-map storage and to use a default color fallback.

## Which Source Should Be Considered Newest?

For source text:

- Abdul source is newer than the source tree by version string (`10.0.14` vs `10.0.12`) and by git commit date.

For packaged executable behavior:

- Abdul app jar is not newer than the source app jar; it is byte-identical to it.
- Abdul app dylib is not newer than the source app dylib; it is byte-identical to it.
- Regular/TEST Windows runtime has different compiled Java behavior not represented by Abdul source.
- TEST patched runtime is still the only compiled artifact with the verified `HashTriple` memory patch.

Therefore:

- latest source branch found locally: Abdul source
- latest/specialized downloaded runtime: TEST patched jar for the TEST cover
- most internally consistent source/package pair: original source tree plus its source app jar/build classes
- most incomplete pair: Abdul source plus Abdul packaged jar/dylib, because the source edits are not reflected in the packaged binaries
