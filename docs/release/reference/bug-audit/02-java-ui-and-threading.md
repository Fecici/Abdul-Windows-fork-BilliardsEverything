# Java UI and Threading Audit

## BUG-001: reflection toggle transform duplication

Status: `fixed`, pending manual UI verification.

### Evidence

Current fixed source locations:

```text
src/java/billiards/viewer/Viewer.java:468
src/java/billiards/viewer/Viewer.java:2659-2667
src/java/billiards/viewer/Viewer.java:3911-3919
```

Previous behavior, verified from source diff:

- The reflect checkbox setup created an `Affine` in the action handler.
- The selected-property listener created another `Affine`.
- Startup/run-later code also forced the selected state or updated reflection.
- Turning reflection off used `imageStack.getTransforms().clear()`.

Observed user symptom:

```text
UI bug when retoggling the Reflect checkbox.
```

Why it is a bug:

- Multiple toggle/start paths can add multiple reflection transforms.
- Repeating a Y-axis reflection transform can cancel, reapply, or move the rendered map unexpectedly depending on count and translation.
- Clearing the full transform list is broader than the checkbox owns.

### Applied Fix

The source now owns one transform:

```text
private final Affine reflectionTransform = new Affine();
```

`updateReflection()` now:

- sets `Myy = -1` and `Ty = imageStack.getBoundsInLocal().getHeight()` when selected.
- adds the transform only if it is not already present.
- removes only `reflectionTransform` when unselected.

### Risk

Medium.

The safer ownership model is correct for this checkbox, but if some unrelated code intentionally relied on `clear()` to remove other stack transforms, that behavior changed. A direct search currently shows reflection as the only direct source user of `imageStack.getTransforms()`.

### Manual Verification

Run:

```powershell
.\gradlew.bat clean run
```

Then verify:

1. The app opens with Reflect selected.
2. The rendered map appears in the expected orientation.
3. Toggle Reflect off, on, off, on.
4. The view changes exactly once per toggle.
5. The view does not drift vertically after repeated toggles.
6. Mouse/click interactions still line up with rendered geometry.
7. Loading a new cover or changing view state does not add another reflection transform.

### Useful Breakpoints

```text
src/java/billiards/viewer/Viewer.java:2661
src/java/billiards/viewer/Viewer.java:3911
```

Watch:

```text
reflectCheckBox.isSelected()
imageStack.getTransforms().size()
imageStack.getTransforms().contains(reflectionTransform)
reflectionTransform.getMyy()
reflectionTransform.getTy()
```

Expected behavior:

- transform count should not increase after repeated on toggles.
- turning off should remove `reflectionTransform`.

## Next UI Audit Targets

These are not yet registered as confirmed bugs:

| Target | Why inspect |
|---|---|
| `Viewer.java` task callbacks | Long-running tasks and UI updates can cross JavaFX thread boundaries. |
| `BoyanMenu.java` static mutable lists | Shared static state can leak across runs or windows. |
| cover load/draw buttons | User workflows combine file IO, native calls, database writes, and rendering. |
| text-field parsing | Many handlers call `Integer.parseInt`/`Double.parseDouble` directly. Need distinguish acceptable validation failures from crashes. |
