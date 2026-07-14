# Bug Audit Baseline

## Scope

Primary source tree:

```text
C:\Users\Owner\Documents\Programming Projects\research\-Abdul-s-fork-BilliardsEverything
```

Documentation/output tree:

```text
C:\Users\Owner\Documents\Programming Projects\research\docs\codex-project-study\bug-audit
```

This baseline is intentionally taken on a dirty worktree because the Windows build fixes and the reflection-toggle fix are already in progress.

## Captured Commands

| Item | Command | Result |
|---|---|---|
| Git status | `git status --short --branch` | Branch `main...origin/main`; many local source/cache changes present. |
| Diff size | `git diff --stat` | 25 tracked files changed, 276 insertions, 160 deletions, plus binary Gradle cache churn. |
| Java version in this shell | `java -version` | Java `24`; note that Gradle source/target compatibility is configured as Java 17. |
| Native compiler | `C:\msys64\ucrt64\bin\g++.exe --version` | MSYS2 UCRT64 GCC/G++ `16.1.0`. |
| File inventory | `rg --files -g "*.java" -g "*.cpp" -g "*.hpp" -g "*.h" src` | 242 Java/C++/header files: 117 Java, 45 C++, 80 headers. |
| Gradle wrapper version | `.\gradlew.bat --version` | Not captured in sandbox; wrapper attempted to download Gradle 8.0 and network is blocked here. |

## Current Dirty Files

The audit begins with these tracked source/build files modified:

```text
build.gradle
src/backend/cpp/vary4.cpp
src/backend/cpp/wrapper.cpp
src/java/billiards/geometry/ConvexPolygon.java
src/java/billiards/geometry/Interval.java
src/java/billiards/geometry/LineSegment.java
src/java/billiards/geometry/Project.java deleted
src/java/billiards/geometry/Rectangle.java
src/java/billiards/math/Equation.java
src/java/billiards/math/LinCom.java
src/java/billiards/viewer/BackwardForward.java
src/java/billiards/viewer/BoyanMenu.java
src/java/billiards/viewer/HashTriple.java
src/java/billiards/viewer/Utils.java
src/java/billiards/viewer/Viewer.java
```

Also present:

```text
src/java/billiards/geometry/Projectable.java
middleVary3.txt
.gradle/... generated/cache files
```

## Known Local Build State

User-reported state after Gradle/Windows fixes:

```powershell
.\gradlew.bat clean run
```

compiled far enough to launch/run after these Windows-specific build issues were addressed:

- x64 native target selection instead of the old accidental 32-bit compile path.
- MSYS2 UCRT64 include/library paths.
- Windows LTO disabled to avoid duplicate Boost symbols from the GCC LTO plugin.
- MSYS2 Boost thread library name changed to `boost_thread-mt`.
- Winsock linked with `-lws2_32`.
- Runtime `PATH` updated for the built backend DLL and UCRT64 dependency DLLs.

This sandbox did not re-run the successful full build because the Gradle wrapper attempted a network download and network access is unavailable here.

## Audit Policy

- Treat Abdul source as the active baseline.
- Do not delete or revert unrelated local edits during the bug audit.
- Record every confirmed or suspected bug in `01-bug-register.md`.
- Prefer source evidence, exact lines, and command output over memory.
- Use `fixed` only when the source change is applied; still record whether runtime verification is pending.
- Be conservative with "bug" labels when behavior may be intentional or runtime-patched.
