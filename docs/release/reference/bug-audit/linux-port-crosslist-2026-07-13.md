# Linux Port Crosslist - 2026-07-13

Scope: imported only the Linux-port changes tied to the user's request: hole-coordinate loading, memory-leak/ABI fixes, and OSNO/MRR parallelization.

| Tracker | Linux-port claim | Abdul action | Windows status | Remaining validation |
|---|---|---|---|---|
| `BUG-008` | `CInfoAll` Java order did not match C++ struct order. | Reordered `CInfoAll.getFieldOrder()` to match `wrapper.hpp`. | Compiles with UCRT64 backend. | Runtime all-equation comparison and ABI slot test. |
| `BUG-035` | Native struct copy helpers leaked partial `char*` allocations if `to_cstr` threw. | Added rollback cleanup in `copy_to_cpicture`, `copy_to_cinfo`, `copy_to_cinfoAll`, and slope `CInfoAll` copy. | Compiles with UCRT64 backend. | Allocation-failure injection. |
| `OPT-004` | Large OSNO/MRR polygon refinement was parallelized. | Added sequential fallback, capped Boost.Asio batch reducer, TBB corner evaluation, and polygon result intersection. | Compiles and links on Windows with existing MSYS2/UCRT64 Boost/TBB link setup. | Compare large OSNO results and timings against Linux port and prior runtime. |
| `FEAT-001` | Cover calculations write uncovered square centers and UI can load them. | Added `cover::save_holes`, hooked cover/save paths, added `Load Holes` button, and reused OBO navigation. | Compiles on Windows. Uses relative `tmp/holes.txt`, so cwd differences still fall under `BUG-015`. | Run cover, verify `tmp/holes.txt`, click `Load Holes`, step OBO coordinates. |
| `BUG-036` | `CoverWindow` ran long cover calculations on the JavaFX Application Thread. | Moved normal cover and all-cover native calls into daemon `javafx.concurrent.Task`s; UI completion remains on task callbacks. | Compiles on Windows. | Run a real cover and confirm the window remains responsive; force bad input and confirm failure alert. |

Build command used:

```powershell
.\gradlew.bat --no-daemon compileJava backendSharedLibrary
```

Result: passed after one retry. The first failure was a transient Windows file lock on the generated `billiards_wrapper_Wrapper.h` header, likely from IDE/indexing.
