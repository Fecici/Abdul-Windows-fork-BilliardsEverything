# Abdul Windows Build Dependencies

Date: 2026-06-15

## Bottom Line

Abdul's fork does not currently produce a fully functioning Windows build in this workspace.

`.\gradlew.bat --no-daemon build` was rerun from:

```text
-Abdul-s-fork-BilliardsEverything
```

Result:

- Java compilation and jar tasks are up to date.
- `build/libs/billiard-viewer.jar` exists.
- Native C++ tasks fail, so no Windows `backend.dll` is produced.
- A direct `java -jar build/libs/billiard-viewer.jar` launch fails because JavaFX is not bundled into that jar.
- A real application launch also cannot work until the native backend DLL is built or supplied.

The failing Gradle tasks are:

- `:compileBackendSharedLibraryBackendCpp`
- `:compileBackendStaticLibraryBackendCpp`
- `:compileTestExecutableTestCpp`

The immediate compiler errors are missing native headers:

- `eigen3/Eigen/Dense`
- `boost/algorithm/string.hpp`
- `boost/format.hpp`
- `tbb/parallel_invoke.h`
- `boost/test/unit_test.hpp`

The current compiler selected by Gradle is MSVC `cl.exe`, which also warns that it is ignoring GCC-style flags:

- `-O3`
- `-march=native`
- `-flto`
- `-ftrapv`

That means the problem is not just "download one missing jar." Java dependencies come from Maven. The missing pieces are native C++ headers, import libraries, runtime DLLs, and a consistent Windows C++ toolchain.

## Why Some Dependencies Are Missing

The repo's `README.md` says the project was developed for Unix/macOS/Linux and explicitly warns that Windows was not the intended environment. The Gradle native build reflects that history:

- macOS gets explicit Homebrew include/library paths.
- non-macOS gets linker names such as `-lgmp`, `-lmpfr`, `-lmpfi`, `-lsqlite3`, `-ltbb12`, `-lboost_thread`, and `-lboost_system`.
- compiler flags are GCC/Clang-style flags.
- there is no Windows-specific include path for Boost, Eigen, TBB, GMP, MPFR, MPFI, or SQLite.
- Gradle's native toolchain detection is selecting MSVC on this machine.

This workspace has partial MSYS2/MinGW dependencies:

- present under `C:\msys64\mingw64`: Boost, Boost libraries, GCC, GMP, MPFR, SQLite.
- missing there: Eigen3, TBB, MPFI.
- Abdul `app/backend/shared` contains macOS `.dylib` files, not Windows `.dll`/headers/import libraries.

Those macOS `.dylib` files cannot satisfy a Windows build.

## Recommended Download Path

Use MSYS2 `ucrt64` as the clean Windows native dependency source. It has the complete package set needed here, including MPFI. Official MSYS2 package pages list these packages and installation commands:

- `mingw-w64-ucrt-x86_64-gcc`: GNU Compiler Collection for MinGW-w64.
- `mingw-w64-ucrt-x86_64-boost`: Boost headers/libraries.
- `mingw-w64-ucrt-x86_64-eigen3`: provides `include/eigen3/Eigen/Dense`.
- `mingw-w64-ucrt-x86_64-tbb`: provides `include/tbb/parallel_invoke.h` and `bin/libtbb12.dll`.
- `mingw-w64-ucrt-x86_64-gmp`
- `mingw-w64-ucrt-x86_64-mpfr`
- `mingw-w64-ucrt-x86_64-mpfi`: provides `include/mpfi.h`, `lib/libmpfi.dll.a`, and `bin/libmpfi-0.dll`.
- `mingw-w64-ucrt-x86_64-sqlite3`

From an MSYS2 shell or PowerShell:

```powershell
C:\msys64\usr\bin\pacman.exe -Syu
C:\msys64\usr\bin\pacman.exe -S --needed mingw-w64-ucrt-x86_64-gcc mingw-w64-ucrt-x86_64-boost mingw-w64-ucrt-x86_64-eigen3 mingw-w64-ucrt-x86_64-tbb mingw-w64-ucrt-x86_64-gmp mingw-w64-ucrt-x86_64-mpfr mingw-w64-ucrt-x86_64-mpfi mingw-w64-ucrt-x86_64-sqlite3
```

Official references checked during this pass:

- MSYS2 package management: `https://www.msys2.org/docs/package-management/`
- `mingw-w64-ucrt-x86_64-gcc`: `https://packages.msys2.org/packages/mingw-w64-ucrt-x86_64-gcc`
- `mingw-w64-ucrt-x86_64-boost`: `https://packages.msys2.org/packages/mingw-w64-ucrt-x86_64-boost`
- `mingw-w64-ucrt-x86_64-eigen3`: `https://packages.msys2.org/packages/mingw-w64-ucrt-x86_64-eigen3`
- `mingw-w64-ucrt-x86_64-tbb`: `https://packages.msys2.org/packages/mingw-w64-ucrt-x86_64-tbb`
- `mingw-w64-ucrt-x86_64-mpfi`: `https://packages.msys2.org/packages/mingw-w64-ucrt-x86_64-mpfi`

## What Still Needs Fixing After Installing

Installing packages is necessary but probably not sufficient.

The current Gradle native build is selecting MSVC. The code and `build.gradle` are written for GCC/Clang-style flags and `-l...` linker names. To get a fully functioning Windows build, Abdul's fork needs one of these toolchain paths:

1. Force/use MSYS2 UCRT64 GCC for the Gradle native build.

   This is the most compatible path with the current flags and library names. The build should see:

   ```text
   C:\msys64\ucrt64\bin\g++.exe
   C:\msys64\ucrt64\include
   C:\msys64\ucrt64\lib
   C:\msys64\ucrt64\bin
   ```

   Runtime launch then needs the generated `backend.dll` plus required UCRT64 runtime DLLs reachable through `PATH` or copied beside the backend DLL.

2. Port the native build to MSVC.

   This would require replacing GCC flags, adding MSVC include/library paths, and using MSVC-compatible builds of Boost, Eigen, TBB, GMP, MPFR, MPFI, and SQLite. MPFI is the awkward part. This is more work and less aligned with the current project.

3. Use the existing `[MAIN]` Windows backend DLL only as a runtime substitute.

   This can make the Java app load a backend for experiments, but it is not an Abdul backend build. It also would not include Abdul's `eliminate_phi` vector-buffer change.

## Practical Next Build Checklist

1. Install the MSYS2 UCRT64 packages above.
2. Ensure `C:\msys64\ucrt64\bin` is first on `PATH` when building.
3. Make Gradle use UCRT64 GCC rather than MSVC, or add an explicit native toolchain configuration.
4. Build:

   ```powershell
   .\gradlew.bat --no-daemon build
   ```

5. Confirm a Windows native backend exists under `build\libs\backend\shared`.
6. Launch with JavaFX on the module path and with JNA pointed at the built backend directory.

Until steps 3-6 are done, Abdul's fork has source code that can be analyzed, but it does not have a verified fully functioning Windows build.
