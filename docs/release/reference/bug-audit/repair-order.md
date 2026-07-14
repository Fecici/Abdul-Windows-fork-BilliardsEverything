# Repair Order

This is the working order for the next coding pass. It prioritizes bugs before optimization and memory before polish.

## Pass 1: Native Memory Ownership

1. `BUG-007` - add and call `cleanup_cinfo_all`; do not reorder `CInfoAll` fields in the same patch.
2. `BUG-009` - replace static cover return buffers with caller-owned `CString` or another explicit cleanup API.
3. `BUG-003` and `BUG-004` - clean up all returned `CString` objects from Java in `finally`.

## Pass 2: Native Lifetimes, Cancellation, and Worker Safety

1. `BUG-031` - fixed; native DB exports now catch exceptions and expose `backend_last_error`.
2. `BUG-030` - make vary worker lambdas exception-safe and make `inflight` decrement through RAII.
3. `BUG-018` - mitigated; Java now serializes native VaryCS/Vary3/Vary4 calls. Long-term fix is still per-operation native cancellation tokens.
4. `BUG-020` - fixed for Java thread-count floor and overrides.
5. `BUG-021` - fixed; native worker selection is centralized and bounded with `BILLIARDS_NATIVE_THREADS` support.

## Pass 3: Java Task and Shutdown Safety

1. `BUG-013` - fixed; main executor now stops before native pool destroy.
2. `BUG-036` - fixed from Linux-port crosscheck; cover calculations now run off the JavaFX thread.
3. `BUG-022` - fixed; JavaFX callbacks use async executor shutdown instead of blocking the UI thread.
4. `BUG-024` - fixed; Cancel now follows the progress-saving contract by canceling queued work while preserving completed in-flight Storage results.
5. `BUG-027` - fixed; local executors now clean up in `finally` on errors and cancellation.
6. `BUG-014` - fixed; `ProgressMultiTask.cancelled` is now volatile for cross-thread visibility.
7. `BUG-025` - fixed; Use-LR stop point is now task-local and lock-protected.
8. `BUG-026` - fixed; saved cover/stables text is now instance-local and loaded when each window is constructed.

## Pass 4: Correctness Bugs

1. `BUG-019` - fix Vary4 start-depth argument order and compare results against known output.
2. `BUG-017` - fixed; `Database.codeAndOEMatch` now uses the inner-loop index.
3. `BUG-012` and `BUG-029` - fixed; string identity comparisons replaced in the known release paths.
4. `BUG-023` - fixed; empty code conversions return empty and malformed native vary output is visible.
5. `BUG-008` - fixed from Linux-port crosscheck; still run ABI slot/all-equation parity tests.

## Pass 5: Portability and Operator Experience

1. `BUG-032` - fixed; Gradle now has temporary memory overrides and native debug/CPU tuning switches while preserving the intentional 2g/10g/2g app defaults.
2. `BUG-016` - fixed; Java file helpers now create parent directories and use UTF-8.
3. `BUG-015`, `BUG-028` - centralize app data paths and robust native file I/O.
4. `BUG-034` - make backend tests runnable on Windows.
5. `BUG-033` - fix Intel Mac JavaFX classifier.
6. `BUG-037` - clean up unused native half-triple helper functions that fail a pure `-O0` debug link.

## Optimization After Bugs

1. `OPT-001` - stream vary results instead of building several full copies.
2. `OPT-003` - replace per-pixel futures with row/block rendering tasks.
3. `OPT-002` - replace busy-wait memory throttling with bounded queues/backpressure.

## First Validation Matrix

| Scenario | Must prove |
|---|---|
| Large OSNO all-equation run | Native/virtual memory returns near baseline after result is copied or window is closed. |
| Large cover/not-filled run | Peak memory does not remain pinned by static buffers. |
| Cancel VaryCS/Vary3/Vary4 | Only the selected operation stops; other operations keep correct state. |
| Close app during long task | No crash, no DB use-after-free, no lingering Java process. |
| Windows fresh shell build | `.\gradlew.bat clean run` builds backend and launches UI. |
| Temporary reduced-heap launch | App starts with explicit `-PbilliardsXmx`/`-PbilliardsXms` overrides when a smaller test run is needed. |
