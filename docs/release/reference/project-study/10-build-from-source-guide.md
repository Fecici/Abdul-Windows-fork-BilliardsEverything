# Build From Source Guide

This guide is for someone who is comfortable copying commands but may not know Java, Gradle, C++, or native library paths.

## What you are building

The project has two parts:

1. Java/JavaFX desktop app.
2. C++ native backend library called `backend`.

Gradle is the main build tool. The normal command:

```powershell
.\gradlew.bat run
```

does three things:

1. compiles the Java app,
2. builds the native backend library,
3. launches the app.

For build-only:

```powershell
.\gradlew.bat jar backendSharedLibrary
```

That builds the Java jar and native backend but does not launch the app.

## Requirements common to all platforms

You need:

- JDK 17
- Git, or a downloaded source folder
- Gradle wrapper from the repo (`gradlew` or `gradlew.bat`)
- C++ compiler and native libraries:
  - Boost
  - Eigen
  - GMP
  - MPFR
  - MPFI
  - SQLite
  - TBB

Use Java 17 even if a newer Java is installed. The project targets Java 17.

Check Java:

```powershell
java -version
```

You want output that mentions version `17`.

## Windows build

### 1. Install JDK 17

Install JDK 17, for example into:

```text
C:\Program Files\Java\jdk-17
```

Do not rely on whatever `java` happens to be first on your system path.

### 2. Install MSYS2

Install MSYS2 into:

```text
C:\msys64
```

### 3. Update MSYS2

Open PowerShell and run:

```powershell
C:\msys64\usr\bin\pacman.exe -Syu
```

If MSYS2 tells you to close the terminal and run the command again, do that.

Then run:

```powershell
C:\msys64\usr\bin\pacman.exe -Syu
```

### 4. Install UCRT64 native dependencies

Run:

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

These package names are important. `mingw-w64-ucrt-x86_64-*` installs into:

```text
C:\msys64\ucrt64
```

Do not mix this with:

```text
C:\msys64\mingw64
```

### 5. Verify Windows dependencies

Run:

```powershell
Test-Path C:\msys64\ucrt64\bin\g++.exe
Test-Path C:\msys64\ucrt64\include\eigen3\Eigen\Dense
Test-Path C:\msys64\ucrt64\include\boost\multiprecision\mpfr.hpp
Test-Path C:\msys64\ucrt64\include\gmp.h
Test-Path C:\msys64\ucrt64\include\mpfr.h
Test-Path C:\msys64\ucrt64\include\mpfi.h
Test-Path C:\msys64\ucrt64\bin\libgmp-10.dll
Test-Path C:\msys64\ucrt64\bin\libmpfr-6.dll
Test-Path C:\msys64\ucrt64\bin\libmpfi-0.dll
Test-Path C:\msys64\ucrt64\bin\libsqlite3-0.dll
Test-Path C:\msys64\ucrt64\bin\libtbb12.dll
```

Every line should print:

```text
True
```

### 6. Build and run on Windows

Open PowerShell:

```powershell
cd [PATH WHERE YOUR PROGRAM LIVES]

$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
$env:BILLIARDS_MSYS2_PREFIX = "C:\msys64\ucrt64"
$env:PATH = "$env:JAVA_HOME\bin;$env:BILLIARDS_MSYS2_PREFIX\bin;$env:PATH"

.\gradlew.bat --stop
.\gradlew.bat clean run
```

The environment variables above are temporary. They affect only this PowerShell window.

### 7. Windows output locations

Expected outputs:

```text
build\libs\billiard-viewer.jar
build\libs\backend\shared\backend.dll
```

The app is launched by Gradle after building.

### 8. Common Windows failures

#### Java version is wrong

Symptom:

```text
java -version
```

prints Java 24, Java 8, or something other than Java 17.

Fix:

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
java -version
```

#### Header not found

Examples:

```text
Eigen/Dense: No such file or directory
boost/multiprecision/mpfr.hpp: No such file or directory
mpfi.h: No such file or directory
```

Fix:

```powershell
C:\msys64\usr\bin\pacman.exe -S --needed mingw-w64-ucrt-x86_64-boost mingw-w64-ucrt-x86_64-eigen3 mingw-w64-ucrt-x86_64-mpfi
```

Then verify the files with `Test-Path`.

#### Linker cannot find Boost

Examples:

```text
cannot find -lboost_thread
cannot find -lboost_system
```

The Windows Gradle config should use MSYS2 names such as `boost_thread-mt`. If this fails, check:

```powershell
Get-ChildItem C:\msys64\ucrt64\lib\libboost*thread*
```

#### Missing Winsock symbols

Examples:

```text
undefined reference to __imp_WSAStartup
undefined reference to __imp_WSACleanup
```

Fix:

The Gradle Windows linker args must include:

```text
-lws2_32
```

#### App starts but cannot load backend

Symptom:

```text
Unable to load library 'backend'
```

Fix:

Make sure `gradlew run` is used, or make sure these are on `PATH`:

```text
build\libs\backend\shared
C:\msys64\ucrt64\bin
```

## Windows IDE setup

The build can work even if the IDE shows red include errors. The IDE has its own indexer.

For VS Code, create:

```text
.vscode\c_cpp_properties.json
```

with:

```json
{
  "configurations": [
    {
      "name": "Windows UCRT64",
      "compilerPath": "C:/msys64/ucrt64/bin/g++.exe",
      "intelliSenseMode": "windows-gcc-x64",
      "cppStandard": "c++14",
      "includePath": [
        "${workspaceFolder}/src/backend/headers",
        "C:/msys64/ucrt64/include",
        "C:/msys64/ucrt64/include/eigen3"
      ],
      "defines": [
        "NDEBUG"
      ]
    }
  ],
  "version": 4
}
```

For CLion, configure the toolchain:

```text
C compiler:   C:\msys64\ucrt64\bin\gcc.exe
C++ compiler: C:\msys64\ucrt64\bin\g++.exe
Debugger:     C:\msys64\ucrt64\bin\gdb.exe
```

## macOS build

The Gradle file has macOS paths for Homebrew:

```text
/opt/homebrew/include
/opt/homebrew/lib
/opt/homebrew/opt
```

That is the normal Homebrew location on Apple Silicon Macs.

### 1. Install JDK 17

Install a JDK 17 distribution.

Check:

```bash
java -version
```

### 2. Install Homebrew dependencies

Run:

```bash
brew update
brew install boost eigen gmp mpfr mpfi sqlite tbb
```

If `mpfi` is not available in the default Homebrew tap on your machine, install it from the tap or formula source your team uses. MPFI is required by the native backend.

### 3. Build and run on macOS

From the repo:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
export PATH="$JAVA_HOME/bin:$PATH"

./gradlew --stop
./gradlew clean run
```

Expected native output:

```text
build/libs/backend/shared/libbackend.dylib
```

If the app cannot load the native backend, check `DYLD_LIBRARY_PATH` and `-Djna.library.path`. The Gradle `run` task sets these for normal runs.

## Linux build

The Gradle file expects Linux JavaFX at:

```text
/usr/share/openjfx/lib
```

That path matches common Debian/Ubuntu packaging.

### 1. Install JDK 17 and dependencies

On Debian/Ubuntu-style systems:

```bash
sudo apt update
sudo apt install openjdk-17-jdk openjfx g++ make \
  libboost-thread-dev libboost-system-dev libboost-test-dev \
  libeigen3-dev libgmp-dev libmpfr-dev libmpfi-dev \
  libsqlite3-dev libtbb-dev
```

If your distribution names TBB differently, install the package that provides `libtbb.so` and TBB headers.

### 2. Build and run on Linux

From the repo:

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"

./gradlew --stop
./gradlew clean run
```

Expected native output:

```text
build/libs/backend/shared/libbackend.so
```

If JavaFX cannot be found, check:

```bash
ls /usr/share/openjfx/lib
```

If that directory does not exist, either install `openjfx` or adjust `build.gradle` for your JavaFX location.

## Test commands

Java tests:

```powershell
.\gradlew.bat test
```

Native tests:

```powershell
.\gradlew.bat testBackend
```

Full clean run:

```powershell
.\gradlew.bat clean run
```

On macOS/Linux, use `./gradlew` instead of `.\gradlew.bat`.

## Running without launching the app

Build only:

```powershell
.\gradlew.bat clean jar backendSharedLibrary
```

This is useful when you only want to know whether compilation succeeds.

## Debug run

Java debugger:

```powershell
.\gradlew.bat run --debug-jvm
```

Attach your IDE debugger to:

```text
localhost:5005
```

Native debugging on Windows:

```powershell
gdb -p <java-process-id>
```

Use `gdb` from:

```text
C:\msys64\ucrt64\bin\gdb.exe
```

## Do not mix environments

On Windows, keep these consistent:

```text
C:\msys64\ucrt64\bin
C:\msys64\ucrt64\include
C:\msys64\ucrt64\lib
```

Avoid mixing them with:

```text
C:\msys64\mingw64
C:\msys64\clang64
Visual Studio cl.exe
```

Mixing environments can compile some files and then fail at link or runtime.

