# Compile and Run Guide for Windows, macOS, and Linux

This guide is for building the Abdul source tree:

```text
C:\Users\Owner\Documents\Programming Projects\research\-Abdul-s-fork-BilliardsEverything
```

The Gradle `run` task builds the Java frontend and the native C++ backend, then launches the JavaFX app. Evidence: `build.gradle:242` has `run.dependsOn "backendSharedLibrary"`, and `build.gradle:245-270` sets native-library paths before launch.

## What Gradle Builds

| Command | What it does |
|---|---|
| `.\gradlew.bat run` | Compiles Java, builds `backend.dll`, sets `PATH`/`JNA_LIBRARY_PATH`, launches the app. |
| `.\gradlew.bat backendSharedLibrary` | Builds only the native shared backend. |
| `.\gradlew.bat classes` | Compiles Java classes. |
| `.\gradlew.bat clean run` | Deletes build outputs, rebuilds backend/frontend, launches. |

Expected Windows native output:

```text
build\libs\backend\shared\backend.dll
```

## Java 17: Temporary but Automatic

The project targets Java 17 bytecode (`build.gradle:118-119`). The cleanest temporary setup is per PowerShell session:

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
java -version
```

This lasts only for that PowerShell window. It does not change Windows globally and disappears after closing the shell or restarting Windows.

For project-only persistence, create a local `gradle.properties` entry later if desired:

```properties
org.gradle.java.home=C:/Program Files/Eclipse Adoptium/jdk-17
```

That affects this Gradle project when run from this checkout. It is more automatic than setting `$env:JAVA_HOME` every time, but less global than editing Windows system environment variables.

## Windows: MSYS2 UCRT64

Use UCRT64 as the default target unless you have a reason not to. The current Gradle script looks for:

```text
C:\msys64\ucrt64\bin\g++.exe
```

Install/update MSYS2 from PowerShell:

```powershell
C:\msys64\usr\bin\pacman.exe -Syu
```

If MSYS2 says core packages were updated and asks you to close the terminal, close all MSYS2 shells and run the same command again.

Install project dependencies:

```powershell
C:\msys64\usr\bin\pacman.exe -S --needed `
  mingw-w64-ucrt-x86_64-gcc `
  mingw-w64-ucrt-x86_64-boost `
  mingw-w64-ucrt-x86_64-eigen3 `
  mingw-w64-ucrt-x86_64-tbb `
  mingw-w64-ucrt-x86_64-gmp `
  mingw-w64-ucrt-x86_64-mpfr `
  mingw-w64-ucrt-x86_64-mpfi `
  mingw-w64-ucrt-x86_64-sqlite3 `
  mingw-w64-ucrt-x86_64-gdb `
  mingw-w64-ucrt-x86_64-pkgconf
```

Then build and run from a fresh PowerShell:

```powershell
cd "C:\Users\Owner\Documents\Programming Projects\research\-Abdul-s-fork-BilliardsEverything"
$env:BILLIARDS_MSYS2_PREFIX = "C:\msys64\ucrt64"
.\gradlew.bat --stop
.\gradlew.bat clean run
```

`BILLIARDS_MSYS2_PREFIX` is temporary for that PowerShell window. The Gradle script also accepts `MSYS2_ROOT`, but `BILLIARDS_MSYS2_PREFIX` is more direct.

## Windows Debugging

Java debug:

```powershell
.\gradlew.bat run --debug-jvm
```

Then attach IntelliJ, VS Code, or Eclipse to port `5005`.

Native debug is currently blocked by `BUG-032`: the build uses `-O3`, `-march=native`, and `NDEBUG`. For useful C++ debugging, add a Gradle debug switch later that uses `-g -O0` and no `NDEBUG`. After that, attach:

```powershell
C:\msys64\ucrt64\bin\gdb.exe -p <java-process-id>
```

Useful native breakpoints:

- `wrapper.cpp:create_connection_pool`
- `wrapper.cpp:load_all_equations`
- `wrapper.cpp:cleanup_string`
- `vary_cs.cpp:fireAway`
- `vary3.cpp:fireAway3`
- `vary4.cpp:fireAway4`
- `verify.cpp:cover`

## Common Windows Failures

| Failure | Likely cause | Fix |
|---|---|---|
| Boost headers missing in IDE | IDE is not using MSYS2 include paths | Add `C:\msys64\ucrt64\include` and `C:\msys64\ucrt64\include\eigen3` to the IDE C++ include paths or import the Gradle native project. |
| `cannot find -lboost_thread` | Wrong Boost library name for MSYS2 UCRT64 | Use `-lboost_thread-mt`, already reflected in current Gradle. |
| `WSAStartup`/`WSACleanup` unresolved | Winsock library missing | Link `-lws2_32`, already reflected in current Gradle. |
| duplicate Boost `wrapexcept` symbols | Windows LTO conflict | Do not use `-flto` on Windows, already reflected in current Gradle. |
| app launches but cannot find backend | DLL search path missing | Run through Gradle `run`, or put `build\libs\backend\shared` and `C:\msys64\ucrt64\bin` on `PATH`. |

## macOS

Requirements:

- Java 17.
- Homebrew native dependencies.
- Current Gradle script assumes Apple Silicon Homebrew paths under `/opt/homebrew`.

Install likely dependencies:

```bash
brew install openjdk@17 boost eigen tbb gmp mpfr sqlite
```

`mpfi` may require a tap or source build depending on Homebrew availability.

Run:

```bash
cd "/path/to/-Abdul-s-fork-BilliardsEverything"
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
./gradlew clean run
```

Known macOS issue: `BUG-033`. The Gradle script currently always selects JavaFX `mac-aarch64` on macOS. Intel Macs need the `mac` classifier.

## Linux

On Debian/Ubuntu-style systems:

```bash
sudo apt update
sudo apt install openjdk-17-jdk build-essential g++ \
  libboost-all-dev libeigen3-dev libtbb-dev libgmp-dev \
  libmpfr-dev libmpfi-dev libsqlite3-dev openjfx
```

Run:

```bash
cd "/path/to/-Abdul-s-fork-BilliardsEverything"
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
./gradlew clean run
```

The Linux `run` task currently uses `/usr/share/openjfx/lib` as the JavaFX module path (`build.gradle:260-264`). If your distro installs JavaFX elsewhere, adjust that path or use JavaFX artifacts from Gradle runtime classpath as the Windows/macOS branches do.

## First Smoke Test

After the UI launches:

1. Open the app and confirm no native library load error appears.
2. Toggle the reflect checkbox several times and confirm the view changes once per toggle.
3. Run a small code calculation that touches the database.
4. Run a small VaryCS/Vary3/Vary4 job.
5. Close the app and confirm the Java process exits.

Do not use a huge OSNO or cover job as the first test. The tracker still has confirmed native memory bugs, especially `BUG-007`, `BUG-009`, `BUG-003`, and `BUG-004`.
