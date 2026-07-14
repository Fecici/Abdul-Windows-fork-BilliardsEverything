# Codex Project Study Index

Generated reports:

- [00-executive-summary.md](00-executive-summary.md)
- [01-repository-inventory.md](01-repository-inventory.md)
- [02-version-comparison-abdul-src-runtime.md](02-version-comparison-abdul-src-runtime.md)
- [03-architecture-graph.md](03-architecture-graph.md)
- [04-build-and-debug-windows.md](04-build-and-debug-windows.md)
- [05-main-code-paths.md](05-main-code-paths.md)
- [06-used-vs-unused.md](06-used-vs-unused.md)
- [07-math-primer-for-this-codebase.md](07-math-primer-for-this-codebase.md)
- [08-plan-of-attack.md](08-plan-of-attack.md)
- [09-bug-audit-plan.md](09-bug-audit-plan.md)
- [09-compile-run-guide-windows-mac-linux.md](09-compile-run-guide-windows-mac-linux.md)
- [10-build-from-source-guide.md](10-build-from-source-guide.md)
- [bug-audit/00-baseline.md](bug-audit/00-baseline.md)
- [bug-audit/01-bug-register.md](bug-audit/01-bug-register.md)
- [bug-audit/02-java-ui-and-threading.md](bug-audit/02-java-ui-and-threading.md)
- [bug-audit/03-java-native-bridge-and-memory.md](bug-audit/03-java-native-bridge-and-memory.md)
- [bug-audit/04-main-runtime-osno-memory.md](bug-audit/04-main-runtime-osno-memory.md)
- [bug-audit/tracker/README.md](bug-audit/tracker/README.md)
- [bug-audit/tracker/issues.csv](bug-audit/tracker/issues.csv)
- [bug-audit/tracker/repair-order.md](bug-audit/tracker/repair-order.md)

Most important findings:

1. Abdul source is the working baseline; no production source was changed during the latest audit pass.
2. The structured tracker is now the durable working surface: `bug-audit/tracker/issues.csv` and `issues.jsonl`.
3. The tracker currently has 37 rows: 34 bugs and 3 optimization items.
4. Windows source build/run should use JDK 17 plus MSYS2 UCRT64 GCC by default.
5. Gradle `run` builds both Java and the native backend, then launches the app.
6. Java loads native code through JNA `Native.register("backend")`.
7. Java needs `JNA_LIBRARY_PATH` plus `PATH` entries for `backend.dll` and MSYS2 dependency DLLs.
8. The most urgent memory bug is `BUG-007`: `CInfoAll` native strings are never freed after large OSNO/all-equation paths.
9. `BUG-009` is also critical: cover/not-filled native APIs return static `std::string` buffers that retain peak capacity.
10. `BUG-003` and `BUG-004` are additional native string leaks in vary and gradient paths.
11. The main/runtime build appears to share the large OSNO memory ownership problem; Abdul still needs the fix.
12. `BUG-008` is a field-order ABI trap: do not reorder `CInfoAll` while adding cleanup.
13. `BUG-030` and `BUG-031` cover native exception safety; C++ exceptions must not cross JNA or worker boundaries.
14. `BUG-018`, `BUG-020`, and `BUG-021` cover unsafe global cancellation and unbounded/zero native thread counts.
15. `BUG-013`, `BUG-022`, `BUG-024`, and `BUG-027` cover Java executor shutdown, cancellation, and UI blocking.
16. `BUG-019` is a high-priority Vary4 correctness/performance issue in `fireAway4`.
17. `BUG-017`, `BUG-012`, `BUG-023`, and `BUG-029` are smaller but concrete Java correctness bugs.
18. `OPT-001` is the main performance/memory redesign: stream vary results instead of duplicating huge native/Java strings.
19. `OPT-003` is the main rendering redesign: replace per-pixel futures with row/block rendering tasks.
20. `BUG-032` tracks the need for low-memory JVM defaults and a real native debug build mode.
21. `BUG-015`, `BUG-016`, and `BUG-028` track runtime file path robustness for Windows shortcuts, IDEs, and packaged runs.
22. `BUG-034` tracks likely Windows backend test-task issues.
23. `BUG-033` tracks Intel Mac JavaFX dependency resolution.
24. Runtime `backend.dll` is MinGW/GCC-style and imports GCC/MinGW dependency DLLs.
25. Do not start with an MSVC port unless UCRT64 is rejected.
26. Runtime debugging can use patched JDWP launchers, but source stepping may not match Abdul.
27. Source debugging should use an Abdul-built jar/classes and Abdul-built backend DLL.
28. Native debugging should attach MSYS2 UCRT64 `gdb` to the Java process after adding debug-symbol build flags.
29. Packaging should wait until source build, source run, Java debug, native debug, and memory fixes are verified.
30. The safest next coding order is in `bug-audit/tracker/repair-order.md`.
