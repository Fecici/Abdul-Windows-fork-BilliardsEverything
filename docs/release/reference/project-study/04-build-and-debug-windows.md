# Build and Debug on Windows

## Current status

The source is not currently ready for a clean Windows native build:

- Abdul worktree has a local syntax error in `src/backend/cpp/vary4.cpp` (`starts;2`).
- Prior Windows Gradle build attempts selected MSVC `cl.exe`, but the native build is written for GCC/Clang-style flags and Unix-style `-l...` library names.
- Required native headers/libraries were missing in that prior attempt: Eigen, Boost, TBB, Boost.Test, and likely GMP/MPFR/MPFI/SQLite link-time pieces.
- `package-windows.bat` has unverified and likely broken batch syntax around `$(call gradlew.bat -q runtimeClasspathAsPath)` and hard-codes `libbackend.dll`.

No production source was edited during this study.

## Required Java

Use JDK 17 for source builds and debugging.

Evidence:

- `build.gradle:100-101` sets `sourceCompatibility = 1.17` and `targetCompatibility = 1.17`.
- Runtime patched jar classes are major version 61, which is Java 17.
- Main runtime bundles Java `17.0.16`.
- Local default `java -version` reports Java 24, which is newer than the project target.

Recommended setup:

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
java -version
.\gradlew.bat --version
```

Expected Java output should identify a Java 17 runtime.

## Required native toolchain

Prefer MSYS2 UCRT64 GCC for the first Windows port.

Reason:

- `build.gradle:112-120` uses `-O3`, `-march=native`, `-flto`, `-ftrapv`, and libraries such as `-lgmp`, `-lmpfr`, `-lmpfi`, `-lsqlite3`, `-ltbb12`, `-lboost_thread`, `-lboost_system`.
- Runtime `backend.dll` imports MinGW/GCC-family DLLs such as `libgcc_s_seh-1.dll`, `libstdc++-6.dll`, `libwinpthread-1.dll`, `libgmp-10.dll`, `libmpfr-6.dll`, `libmpfi-0.dll`, and `libtbb12.dll`.
- MSVC would require a separate native dependency strategy, flag translation, and MPFI availability work.

Install dependencies from PowerShell or an MSYS2 shell:

```powershell
C:\msys64\usr\bin\pacman.exe -S --needed `
  mingw-w64-ucrt-x86_64-gcc `
  mingw-w64-ucrt-x86_64-boost `
  mingw-w64-ucrt-x86_64-eigen3 `
  mingw-w64-ucrt-x86_64-tbb `
  mingw-w64-ucrt-x86_64-gmp `
  mingw-w64-ucrt-x86_64-mpfr `
  mingw-w64-ucrt-x86_64-mpfi `
  mingw-w64-ucrt-x86_64-sqlite3
```

Then put UCRT64 first:

```powershell
$env:PATH = "C:\msys64\ucrt64\bin;$env:PATH"
g++ --version
where g++
where libgmp-10.dll
where libmpfr-6.dll
where libmpfi-0.dll
where libtbb12.dll
```

## Gradle build from Abdul source

Use Abdul as the working tree:

```powershell
cd "C:\Users\Owner\Documents\Programming Projects\research\-Abdul-s-fork-BilliardsEverything"
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
$env:PATH = "C:\msys64\ucrt64\bin;$env:JAVA_HOME\bin;$env:PATH"
```

After the current `vary4.cpp` syntax error is explicitly fixed, try:

```powershell
.\gradlew.bat --no-daemon clean jar backendSharedLibrary
```

Expected successful outputs:

- `build/libs/billiard-viewer.jar`
- A native backend library under `build/libs/backend/shared/`, likely `backend.dll` or `libbackend.dll` depending Gradle/native toolchain behavior.
- Gradle task `backendSharedLibrary` completes.

If Gradle still selects MSVC:

- Inspect `.\gradlew.bat --no-daemon --info backendSharedLibrary`.
- Ensure `C:\msys64\ucrt64\bin` precedes Visual Studio toolchain paths.
- Add explicit Gradle native toolchain configuration later if PATH ordering is insufficient.

## Native dependency PATH

Java/JNA needs two things:

1. A direct location for the backend DLL, through `-Djna.library.path` or `JNA_LIBRARY_PATH`.
2. A Windows `PATH` that contains all dependent native DLLs loaded by `backend.dll`.

Runtime scripts do this correctly:

```bat
set "NATIVE_LIB_DIR=%DIR%backend\shared"
set "PATH=%NATIVE_LIB_DIR%;%DIR%java\bin;%PATH%"
java -Djava.library.path="%NATIVE_LIB_DIR%" ...
```

For Abdul source builds:

```powershell
$backend = "C:\Users\Owner\Documents\Programming Projects\research\-Abdul-s-fork-BilliardsEverything\build\libs\backend\shared"
$env:PATH = "$backend;C:\msys64\ucrt64\bin;$env:PATH"
$env:JNA_LIBRARY_PATH = $backend
```

## Launch normally from source

Gradle launch:

```powershell
.\gradlew.bat --no-daemon run
```

Direct Java launch is possible but more brittle because the classpath and JavaFX module path must match Gradle runtime dependencies:

```powershell
.\gradlew.bat -q runtimeClasspathAsPath
```

Use that output as the runtime classpath/module path. Then launch with:

```powershell
java `
  -server `
  -Djna.library.path="build\libs\backend\shared" `
  -Xss1000m `
  --module-path "<runtime classpath from Gradle>" `
  --add-modules javafx.controls,javafx.fxml `
  -jar "build\libs\billiard-viewer.jar"
```

The direct command may need adjustment because `build.gradle` treats JavaFX on the module path and also builds a fat jar. Prefer `gradlew run` first.

## Java debug from source

Gradle has built-in debug support:

```powershell
.\gradlew.bat --no-daemon run --debug-jvm
```

Expected behavior:

- JVM starts suspended.
- Gradle prints that it is listening for a debugger, usually on port `5005`.
- Attach IntelliJ, VS Code, or Eclipse to `localhost:5005`.

Manual JDWP launch pattern:

```powershell
java `
  -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5005 `
  -Djna.library.path="build\libs\backend\shared" `
  --module-path "<JavaFX/module path>" `
  --add-modules javafx.controls,javafx.fxml `
  -Xss1000m `
  -jar "build\libs\billiard-viewer.jar"
```

Good Java breakpoints:

- `billiards.viewer.Main.init`
- `billiards.viewer.Main.start`
- `billiards.wrapper.Wrapper.<clinit>` or first call after `Native.register`
- `billiards.database.Admin.getConnectionPool`
- `billiards.database.Database.loadStorage`
- `billiards.wrapper.Wrapper.loadPicture`
- `billiards.wrapper.Wrapper.saveToDatabase`
- `billiards.viewer.Viewer.calculateCurrentCodeNumbers`
- `billiards.viewer.Viewer.renderRegions`
- `billiards.viewer.CoverWindow` calculate button handler
- `billiards.viewer.SmallCoverWindow` calculate button handler
- `billiards.wrapper.Wrapper.varyCSCpp`, `vary3Cpp`, `vary4Cpp`

## Debug existing runtime jar

Use the main patched runtime for comparison:

```powershell
cd "C:\Users\Owner\Documents\Programming Projects\research\[MAIN] The Tokarsky-Marinov Finder, Tester and Prover (Windows-Kaiden jar) Aug 28,2025 - Copy\BilliardsEverythingsWindowsJarAug28Backup"
```

Create a debug command based on `run2.bat`:

```powershell
.\java\bin\java.exe `
  -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5005 `
  -Djava.library.path="backend\shared" `
  --module-path "javafx" `
  --add-modules javafx.controls,javafx.fxml `
  -Xms2g -Xmx10g -Xss16m `
  -XshowSettings:vm `
  -jar "billiard-viewer-patched-hashtriple-java17.jar"
```

Before launching, ensure:

```powershell
$env:PATH = "$PWD\backend\shared;$PWD\java\bin;$env:PATH"
```

Attach a Java remote debugger to `localhost:5005`.

Important limitation: source stepping will not line up perfectly with Abdul source because the runtime jar differs from source and has a patched `HashTriple.class`.

## Native C++ debug

Best path for native debug:

1. Build `backend.dll` with the same MinGW/UCRT64 toolchain that will run it.
2. For a real debug build, later change the native build flags from `-O3 -flto -DNDEBUG` to debug-friendly flags such as `-g -O0` and remove LTO. Do this only after source edits are allowed.
3. Launch Java suspended with JDWP.
4. Attach native debugger to the Java process.

With MinGW:

```powershell
gdb -p <java-process-id>
```

Useful native breakpoints:

```gdb
break cover_wrapper
break small_cover_wrapper
break save_to_database
break load_picture
break load_info
break bounding_polygon
break vary_cs_cpp
break vary_3_cpp
break vary_4_cpp
break calculate_stable
break calculate_unstable
break check_cover
break check_small_cover
break eliminate_phi
break Unfolding::generate_curves
```

If symbols are stripped or optimized away, start with exported wrapper names from `wrapper.cpp`, then step inward.

## Java plus native together

Recommended combined session:

1. Start Java with JDWP suspended:

```powershell
.\gradlew.bat --no-daemon run --debug-jvm
```

2. Attach Java IDE debugger to `localhost:5005`.
3. Resume until just before a native call, for example `Wrapper.loadPicture`.
4. Attach `gdb` to the same Java process.
5. Set C++ breakpoints in wrapper exports.
6. Resume Java and step through the JNA call into C++.

This is feasible with MinGW debug symbols. It is much less useful with the optimized checked-in runtime DLL.

## Known failure modes and fixes

| Symptom | Likely cause | Fix |
|---|---|---|
| `javac`/Gradle uses Java 24 | `JAVA_HOME` or `PATH` points at local default Java 24. | Set `JAVA_HOME` to JDK 17 and put `%JAVA_HOME%\bin` first. |
| JavaFX runtime components missing | Running jar directly without module path or JavaFX dependencies. | Use `gradlew run` or pass `--module-path` and `--add-modules javafx.controls,javafx.fxml`. |
| `Unable to load library 'backend'` | JNA cannot find backend DLL. | Set `-Djna.library.path` and/or `JNA_LIBRARY_PATH` to `build/libs/backend/shared`. |
| `backend.dll` found but dependent DLL load fails | GMP/MPFR/MPFI/TBB/Boost/GCC runtime DLLs not on `PATH`. | Put `C:\msys64\ucrt64\bin` and backend directory on `PATH`, or copy dependency DLLs beside `backend.dll`. |
| MSVC warnings about `-O3`, `-march=native`, `-flto`, `-ftrapv` | Gradle selected MSVC. | Prefer UCRT64 GCC; adjust PATH/toolchain config. |
| Missing `Eigen/Dense`, `boost/...`, `tbb/...` | Native headers not installed or include path not found. | Install MSYS2 UCRT64 packages and ensure Gradle compiles with that toolchain. |
| Linker cannot find `-lmpfi` or similar | Native libs not installed for selected toolchain. | Install matching UCRT64 packages; do not mix MSVC and MinGW libs. |
| Native build fails in `vary4.cpp` near `starts;2` | Current Abdul dirty syntax typo. | Fix after approval. |
| Runtime behavior does not match source breakpoints | Runtime jar/DLL is not built from Abdul source. | Use runtime only for behavior comparison; build Abdul artifacts for source debugging. |

## Package script status

`package-windows.bat` is not ready to trust:

- It tries to use `$(call gradlew.bat -q runtimeClasspathAsPath)` in a Windows batch file.
- It copies `build\libs\backend\shared\libbackend.dll`, but runtime artifact uses `backend.dll`, and Gradle naming depends on toolchain.
- It passes `-Djna.library.path=$APPDIR/backend/shared`; quoting and slash behavior should be tested under `jpackage`.

Treat packaging as a later phase after a clean source build and debug launch work.
