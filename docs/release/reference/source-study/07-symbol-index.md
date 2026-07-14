# Generated Symbol Indexes

This document explains the generated indexes in this directory. These are documentation artifacts only; no source files were modified.

Current baseline note: these generated indexes were created from the older source tree, not directly from Abdul's fork. They are still useful for navigation because Abdul's source deltas are small; use `17`, `18`, and `19` for exact Abdul/`[MAIN]` differences before trusting a line number in changed files.

## Files

- `symbol-index-ctags.txt` contains all C++ and Java symbols detected by Universal Ctags under `src`.
- `function-index-ctags.txt` contains only ctags rows whose kind is `method`, `function`, or `prototype`.
- `viewer-support-function-index.txt` contains the non-`Viewer.java` subset of viewer methods for continuing frontend documentation.

At generation time:

- `symbol-index-ctags.txt` had 4,063 rows.
- `function-index-ctags.txt` had 2,032 rows.
- `viewer-support-function-index.txt` had 404 rows.

## Generation Commands

```powershell
ctags -R -x --sort=no --languages=C++,Java src | Out-File -FilePath docs/source-study/symbol-index-ctags.txt -Encoding utf8
Get-Content -Path docs/source-study/symbol-index-ctags.txt | Where-Object { $_ -match '\s(method|function|prototype)\s+' } | Out-File -FilePath docs/source-study/function-index-ctags.txt -Encoding utf8
Select-String -Path docs/source-study/function-index-ctags.txt -Pattern 'src/java/billiards/viewer' | Where-Object { $_.Line -notmatch 'Viewer.java' } | Select-Object -ExpandProperty Line | Set-Content -Path docs/source-study/viewer-support-function-index.txt -Encoding utf8
```

The `--sort=no` flag is required in this workspace because ctags attempts to call an external `sort -u` command when sorting, and that fails on this Windows setup.

## How To Read A Row

Rows use ctags `-x` format:

```text
symbolName    kind    lineNumber    sourcePath    sourceLine
```

Example:

```text
create           method       46 src/java/billiards/codeseq/CodeSequence.java public static Either<InvalidCodeSequence, CodeSequence> create(final IntList dirtyCodeNumbers) {
```

This means `CodeSequence.create(...)` starts at line 46 of `src/java/billiards/codeseq/CodeSequence.java`.

## Recommended Use For Future Documentation

Use `function-index-ctags.txt` as the checklist for proving that each function/method/prototype has documentation. When a later document covers a file, search the index for that file path and mark each listed function as documented in the progress cache.

Useful commands:

```powershell
Select-String -Path docs/source-study/function-index-ctags.txt -Pattern 'src/java/billiards/viewer/Viewer.java'
Select-String -Path docs/source-study/function-index-ctags.txt -Pattern 'src/backend/cpp/unfolding.cpp'
Select-String -Path docs/source-study/function-index-ctags.txt -Pattern 'src/backend/headers/sqlite.hpp'
```

For line-by-line viewer work, prefer chunked reads of the source file and use the index as a method boundary map. The raw index is not a substitute for explanation: it only tells a future agent where symbols are.
