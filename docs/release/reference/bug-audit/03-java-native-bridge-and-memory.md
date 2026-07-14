# Java Native Bridge and Memory Audit

This note covers confirmed native-memory bugs found while setting up the register. It does not change source yet.

## Native String Ownership Pattern

C++ helper:

```text
src/backend/cpp/wrapper.cpp:46-52
```

allocates returned strings with:

```cpp
char* const c_str = new char[str.size() + 1];
```

C++ cleanup function:

```text
src/backend/cpp/wrapper.cpp:1230-1231
```

uses:

```cpp
delete[] cstring->string;
```

Java must therefore call `cleanup_string(CString)` after it has copied the native string into a Java `String`.

## BUG-003: vary result strings are never freed

Status: `confirmed`.

### Evidence

C++ allocates output strings:

```text
src/backend/cpp/wrapper.cpp:1256  result->string = to_cstr(...)
src/backend/cpp/wrapper.cpp:1281  result->string = to_cstr(...)
src/backend/cpp/wrapper.cpp:1307  result->string = to_cstr(...)
```

Java reads the strings:

```text
src/java/billiards/wrapper/Wrapper.java:589
src/java/billiards/wrapper/Wrapper.java:626
src/java/billiards/wrapper/Wrapper.java:661
```

but `cleanup_string(result)` is not called in `varyCSCpp`, `vary3Cpp`, or `vary4Cpp`.

### User Impact

Large vary results can allocate large native strings. Repeated UI runs can grow native memory even after Java objects are garbage-collected.

This is especially relevant because current JVM args include large heap/native-memory settings and Vary3/Vary4 are high-output workflows.

### Proposed Fix

After copying:

```java
String strseq = result.string.getString(0);
```

free in a `finally` block:

```java
boolean allocated = result.string != null;
try {
    String strseq = result.string.getString(0);
    ...
} finally {
    if (allocated) {
        cleanup_string(result);
    }
}
```

The exact implementation should avoid reading after free and avoid freeing null/uninitialized pointers on native error.

### Fix Risk

Low to medium.

The ownership rule is clear, but JNA pointer lifetime mistakes can crash the JVM. The fix must copy the native string before cleanup and should not double-free.

### Verification

1. Run a large `varyCSCpp`, `vary3Cpp`, and `vary4Cpp` workflow.
2. Repeat the workflow several times.
3. Watch native memory with JVM Native Memory Tracking, Process Explorer, or VisualVM.
4. Expected result after fix: no linear native-memory growth caused by returned vary strings.

## BUG-004: calculateGradient leaks second native string

Status: `confirmed`.

### Evidence

C++ always allocates two strings on success:

```text
src/backend/cpp/wrapper.cpp:1221  cstring->string = to_cstr(oss.str())
src/backend/cpp/wrapper.cpp:1222  cstring2->string = to_cstr(min_r)
```

Java path 1:

```text
src/java/billiards/wrapper/Wrapper.java:557-558
```

reads `cstring` and frees `cstring`, but never frees `cstring2`.

Java path 2:

```text
src/java/billiards/wrapper/Wrapper.java:573-574
```

reads `cstring2` but frees `cstring`, leaving `cstring2` allocated.

### User Impact

Gradient calculations can leak native memory each time they succeed. If these methods are used in hover/inspection workflows, the leak could be visible during long UI sessions.

### Proposed Fix

For both Java methods:

- copy any needed native string first.
- call `cleanup_string(cstring)` if `cstring.string` was set.
- call `cleanup_string(cstring2)` if `cstring2.string` was set.
- perform cleanup in `finally`.

### Fix Risk

Low to medium.

The main risk is freeing uninitialized/native-null pointers. Check pointer state before cleanup.

### Verification

Run both gradient UI paths or a small harness that calls:

```text
Wrapper.calculateGradient(...)
Wrapper.calculateGradient2(...)
```

many times. Native memory should stabilize after fix.

## Related Open Question

`CInfoAll` has now been confirmed as a leak source for MAIN and Abdul. See:

```text
docs/codex-project-study/bug-audit/04-main-runtime-osno-memory.md
```

Several other structures contain native `Pointer` fields:

```text
CInfo
CInfoAll
CPicture
CString
```

`CInfo` and `CPicture` have visible cleanup calls in `Wrapper.java`; `CInfoAll` does not. Future bridge-memory passes should still verify exceptional paths and partial allocation behavior.
