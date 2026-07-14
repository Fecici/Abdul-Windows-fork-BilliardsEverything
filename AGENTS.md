# Abdul Windows Agent Notes

This repo is the active Abdul Windows source. Future agents working here must treat
`docs/codex-project-study/bug-register.csv` as the live issue tracker and must keep
release-facing changes documented in code and in `docs/release/`.

Working rules:

- Preserve Abdul Windows behavior unless the user explicitly asks to port a NiShan Linux or Suryansh Mac change.
- Do not port the Linux database relocation without a separate migration plan.
- Keep user runtime memory defaults in `build.gradle`: `-Xms2g`, `-Xmx10g`, and `MaxDirectMemorySize=2g`.
- Add short comments for non-obvious code changes, especially Java/native ownership, threading, cancellation, memory, and math behavior.
- After source edits, run at least `.\gradlew.bat --no-daemon compileJava backendSharedLibrary` on Windows.
- For native changes, also run `.\gradlew.bat --no-daemon testBackend` when feasible.
- For Java-only changes, run `.\gradlew.bat --no-daemon test` when feasible.
- Update `docs/codex-project-study/bug-register.csv` for every bug, optimization, build fix, and release feature.
- Keep generated runtime files out of new commits unless the user explicitly wants to commit refreshed artifacts.
