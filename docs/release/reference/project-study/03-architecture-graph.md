# Architecture Graph

## Evidence anchors

These diagrams are based on verified source/build/runtime evidence:

- `build.gradle:15-17`, `35-43`, `98-120`, `187-232`
- `src/java/billiards/viewer/Main.java:21-65`
- `src/java/billiards/wrapper/Wrapper.java:36-667`
- `src/backend/cpp/wrapper.cpp:123-1296`
- `src/java/billiards/database/Admin.java:23-93`
- `src/java/billiards/database/Database.java:313-397`
- `src/java/billiards/viewer/Viewer.java:590-608`, `5787-5968`, `7997-8042`
- `src/backend/cpp/equations.cpp`, `database.cpp`, `unfolding.cpp`, `bounding_inequalities.cpp`, `bounding_region.cpp`, `verify.cpp`, `vary_cs.cpp`, `vary3.cpp`, `vary4.cpp`
- Runtime `run.bat`, `run2.bat`, `runDEBUG.bat`

## A. Full project component graph

```mermaid
flowchart TD
    User[User] --> JavaFX[JavaFX UI]
    JavaFX --> Main[billiards.viewer.Main]
    Main --> DBGui[billiards.viewer.DBGui]
    Main --> Viewer[billiards.viewer.Viewer]
    Main --> PatternFinder[patternfinder.PatternFinder]

    Viewer --> BoyanMenu[BoyanMenu and vary windows]
    Viewer --> CoverUI[CoverWindow and SmallCoverWindow]
    Viewer --> Render[JavaFX rendering and image layers]
    Viewer --> Tasks[Task classes]
    Viewer --> JavaDB[Java database/model layer]

    Tasks --> JavaDB
    BoyanMenu --> VaryJava[billiards.vary Java helpers]
    VaryJava --> Wrapper[Wrapper.java JNA bridge]
    CoverUI --> Wrapper
    JavaDB --> Wrapper
    PatternFinder --> Wrapper

    Wrapper --> BackendDLL[backend native library]
    BackendDLL --> WrapperCpp[wrapper.cpp exports]
    WrapperCpp --> CodeSeqCpp[code_sequence/classified_code_sequence]
    WrapperCpp --> MathCpp[equations/unfolding/refine/bounding]
    WrapperCpp --> CoverCpp[verify/common cover code]
    WrapperCpp --> VaryCpp[vary_cs/vary3/vary4]
    WrapperCpp --> SQLiteCpp[C++ SQLite persistence]

    JavaDB --> SQLiteFile[(~/billiard-databases/*.sqlite)]
    SQLiteCpp --> SQLiteFile
    CoverUI --> CoverFiles[(cover, small_cover, tmp text files)]
    Viewer --> CoverFiles
```

## B. Build/dependency graph

```mermaid
flowchart TD
    BuildGradle[build.gradle] --> JavaCompile[compileJava]
    BuildGradle --> JarTask[jar fat jar]
    BuildGradle --> RunTask[run]
    BuildGradle --> NativeModel[Gradle native model]

    JavaDeps[Java deps: JavaFX, JNA, SQLite JDBC, RichTextFX, Guava, Eclipse Collections] --> JavaCompile
    JavaCompile --> Classes[build/classes/java/main]
    Classes --> JarTask
    JavaDeps --> JarTask
    JarTask --> ViewerJar[build/libs/billiard-viewer.jar]

    NativeModel --> BackendShared[backendSharedLibrary]
    NativeModel --> BackendStatic[backendStaticLibrary]
    NativeModel --> NativeTest[test native executable]
    CppSources[src/backend/cpp + headers] --> BackendShared
    CppSources --> BackendStatic
    NativeDeps[GMP MPFR MPFI SQLite TBB Boost Eigen] --> BackendShared
    NativeDeps --> BackendStatic
    BackendShared --> NativeLib[build/libs/backend/shared/backend.dll or platform library]
    BackendStatic --> NativeTest

    RunTask --> BackendShared
    RunTask --> JavaFXModulePath[JavaFX module path]
    RunTask --> JnaPath[-Djna.library.path and JNA_LIBRARY_PATH]
    RunTask --> ViewerJar
    RunTask --> NativeLib

    PackageWindows[package-windows.bat] -. unverified .-> CopyRuntimeLibs[copyRuntimeLibs]
    PackageWindows -. unverified .-> JLink[jlink runtime]
    PackageWindows -. unverified .-> JPackage[jpackage MSI]

    Meson[meson.build] -. alternative .-> MesonBackend[shared_library backend]
    Makefile[Makefile] -. legacy/cover .-> CoverExe[cover executable]
```

Notes:

- `run.dependsOn "backendSharedLibrary"` is in `build.gradle:187`.
- `applicationDefaultJvmArgs` includes `-Djna.library.path=./build/libs/backend/shared/` at `build.gradle:98`.
- On Windows, dependent DLL discovery must also be handled through `PATH`, as the runtime scripts do.

## C. Runtime graph

```mermaid
sequenceDiagram
    participant User
    participant Main as Main.java
    participant DBGui as DBGui
    participant Admin as Admin.java
    participant Viewer as Viewer.java
    participant Wrapper as Wrapper.java
    participant Native as backend.dll/libbackend
    participant DB as SQLite database

    User->>Main: Launch JavaFX app
    Main->>Wrapper: errorLogging()
    Wrapper->>Native: sqlite_error_logging()
    Main->>DBGui: choose mode and database
    Main->>Admin: newJavaDB("garbage")
    Admin->>DB: create Java-side code tables
    Main->>Admin: getConnectionPool(dbName, numThreads)
    Admin->>Wrapper: createConnectionPool(dbPath, poolSize)
    Wrapper->>Native: create_connection_pool
    Native->>DB: open WAL SQLite connections
    Main->>Viewer: new Viewer(...).start(executor)
    Viewer->>User: JavaFX controls and render surfaces
```

## D. Java-to-C++ bridge graph

```mermaid
flowchart LR
    subgraph Java["Java"]
        Wrapper[Wrapper.java]
        Pool[ConnectionPool.java]
        DB[Database.java]
        UI[Viewer/CoverWindow/BoyanMenu]
    end

    subgraph NativeAPI["JNA exported names"]
        N1[database_create]
        N2[create_connection_pool]
        N3[save_to_database]
        N4[load_picture / load_info]
        N5[cover_wrapper / small_cover_wrapper]
        N6[vary_cs_cpp / vary_3_cpp / vary_4_cpp]
        N7[bounding_polygon / calculate_gradient]
    end

    subgraph Cpp["C++ implementation"]
        W[wrapper.cpp]
        DBcpp[database.cpp and database/viewer.cpp]
        Eq[equations.cpp]
        Bound[bounding_inequalities.cpp and bounding_region.cpp]
        Unfold[unfolding.cpp and shooting_vectors.cpp]
        Refine[refine.cpp]
        Verify[verify.cpp/common.cpp]
        Vary[vary_cs.cpp/vary3.cpp/vary4.cpp]
    end

    UI --> Wrapper
    DB --> Wrapper
    Pool --> Wrapper
    Wrapper --> N1
    Wrapper --> N2
    Wrapper --> N3
    Wrapper --> N4
    Wrapper --> N5
    Wrapper --> N6
    Wrapper --> N7
    N1 --> W
    N2 --> W
    N3 --> W
    N4 --> W
    N5 --> W
    N6 --> W
    N7 --> W
    W --> DBcpp
    W --> Eq
    Eq --> Bound
    Eq --> Unfold
    Eq --> Refine
    W --> Verify
    Verify --> DBcpp
    W --> Vary
```

## E. Math/data pipeline graph

```mermaid
flowchart TD
    Input[User input, file input, or vary-generated ints] --> JavaCodeSeq[CodeSequence.create]
    JavaCodeSeq --> Validate[positive/nonempty/legal/minimal/canonical]
    Validate --> Classify[ClassifiedCodeSequence: OSO/OSNO/ONS/CS/CNS]
    Classify --> StorageDecision{Need computed storage?}

    StorageDecision -->|yes| DatabaseLoad[Database.loadStorage]
    DatabaseLoad --> WrapperLoad[Wrapper.loadPicture]
    WrapperLoad --> NativeSave[wrapper.cpp save_to_database if missing]
    NativeSave --> CalcType{stable?}
    CalcType -->|stable| CalcStable[calculate_stable]
    CalcType -->|unstable| CalcUnstable[calculate_unstable]

    CalcStable --> BoundPoly[calculate_bounding_polygon]
    CalcUnstable --> BoundSeg[calculate_bounding_line_segment]
    BoundPoly --> Inequalities[calculate_bounding_inequalities and eliminate_phi]
    BoundSeg --> Inequalities
    CalcStable --> Shooting[shooting_vector_open/closed/general]
    CalcUnstable --> Shooting
    Shooting --> Unfold[Unfolding.generate_curves or generate_curves_lr]
    Unfold --> Refine[refine_polygon with curve equations]
    Refine --> SaveSQLite[save serialized equations, points, regions]

    WrapperLoad --> Deserialize[database::load_picture]
    Deserialize --> JavaStorage[Storage.Stable or Storage.Unstable]
    JavaStorage --> Render[Viewer.renderRegions]

    Classify --> CoverInput[CoverWindow/SmallCoverWindow cleaned stables/triples]
    CoverInput --> CoverWrapper[Wrapper.coverWrapper/smallCoverWrapper]
    CoverWrapper --> Verify[check_cover/check_small_cover]
    Verify --> CoverTree[cover.txt tree: E/H/S/T/D tokens]
    CoverTree --> CoverStuff[CoverStuff.parseCover]
    CoverStuff --> HashTriple[HashTriple maps rectangles to codes/colors]
    HashTriple --> Render
```

## F. Database persistence graph

```mermaid
flowchart TD
    Admin[Admin.java] --> DbPath["${user.home}/billiard-databases/<db>.sqlite"]
    Admin --> JavaTables[Java tables: oso osno cs ons cns]
    Admin --> NativePool[Wrapper.createConnectionPool]
    NativePool --> CppPool[sqlite::ConnectionPool]
    CppPool --> Pragmas[WAL and sync settings]

    SaveReq[Wrapper.saveToDatabase] --> CppSave[wrapper.cpp save_to_database]
    CppSave --> Calculate[calculate_stable or calculate_unstable]
    Calculate --> Serialize[database serialize code info]
    Serialize --> DbPath

    LoadReq[Database.loadStorage] --> LoadPic[Wrapper.loadPicture]
    LoadPic --> CppLoad[database::load_picture]
    CppLoad --> Deserialize[deserialize C++ DB rows]
    Deserialize --> CPicture[CPicture JNA struct]
    CPicture --> JavaStorage[Database.convertToStorage]
```

## G. Vary/search graph

```mermaid
flowchart TD
    UI[BoyanMenu / Viewer vary controls] --> JavaVary[VaryCS, Vary3, Vary4 Java helpers]
    UI --> TaskVary[PolyVaryTask, VaryLTask, CycleVaryTask]
    JavaVary --> WrapperVary[Wrapper.varyCSCpp/vary3Cpp/vary4Cpp]
    TaskVary --> WrapperVary
    WrapperVary --> NativeVary[vary_cs_cpp/vary_3_cpp/vary_4_cpp]
    NativeVary --> Fire[fireAwayCS/fireAway3/fireAway4]
    Fire --> CodeLists[lines of code-number ints]
    CodeLists --> ParseJava[parallel Java parsing into ClassifiedCodeSequence]
    ParseJava --> Filter[type and max side-sum filters]
    Filter --> SaveLoad[Wrapper.saveToDatabase / Database.loadStorage]
    SaveLoad --> Render[Viewer.renderRegions]
```

Warning: `Wrapper.varyCSCpp` uses a synchronized list while parsing native result lines. `Wrapper.vary3Cpp` and `Wrapper.vary4Cpp` use `parallel()` with a plain `ArrayList`, which is a likely concurrency bug.

