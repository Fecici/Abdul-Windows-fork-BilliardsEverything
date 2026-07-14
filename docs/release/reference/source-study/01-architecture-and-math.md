# Architecture And Math

Current baseline note: this architecture still applies to Abdul's fork. Abdul's known changes are implementation-level deltas around `eliminate_phi`, reflection defaults, small-cover defaults, and a few debug/formatting edits; `[MAIN]` keeps the same broad architecture but differs in the viewer runtime and native `eliminate_phi` implementation as documented in `18` and `19`.

## Problem Domain

The program studies periodic billiard paths in triangles. A billiard path is a straight-line motion inside a triangle with mirror reflection at sides. A path is periodic if, after finitely many reflections, it returns to its initial geometric state. The standard trick is unfolding: instead of reflecting the path at each side, reflect the whole triangle across the hit side. Then the billiard path becomes a straight line through a chain of reflected triangles.

The input triangle is represented by two angles:

- `x`: one triangle angle.
- `y`: another triangle angle.
- `z = pi - x - y`: the third angle.

The natural parameter space is the open triangle:

```text
x > 0
y > 0
x + y < pi
```

In the C++ rational geometry layer this is scaled by `eta = pi / 2`, so the basic coordinate triangle becomes:

```text
x > 0
y > 0
z = 2*eta - x - y > 0
```

That is why many backend linear equations use symbols `XYEta::{X,Y,Eta}` and the starting rational triangle has vertices `(0,0)`, `(2,0)`, and `(0,2)`. Multiplying by `pi/2` converts these rational coordinates back to radians.

## Code Sequences

A code sequence is a finite list of positive integers. Each number is a run-length-style encoding of how the unfolded orbit crosses or reflects through triangle sides. The parity of each number determines how the associated angle label evolves:

- If the current number is even, the next angle label repeats the previous previous angle.
- If the current number is odd, the next angle label is the third angle not equal to the previous two.

The legal sequence condition is implemented by walking angle labels from `(X,Y)` and requiring the final pair to return to `(X,Y)`. This is equivalent to saying the parity pattern forms a valid closed symbolic transition in the three-angle alphabet.

After validation, sequences are canonicalized:

- Repeated legal periods are reduced to the shortest repeated legal sub-sequence.
- All rotations are considered.
- The reversed sequence is also considered.
- The lexicographically smallest representative is stored.

This matters because the same periodic billiard orbit can be written from different starting sides or directions. Canonicalization avoids duplicate database entries and duplicate drawings.

## Code Types

The code type enum exists in both Java and C++:

| Type | Meaning |
| --- | --- |
| `OSO` | Open, stable, odd |
| `OSNO` | Open, stable, not odd |
| `ONS` | Open, not stable |
| `CS` | Closed, stable |
| `CNS` | Closed, not stable |

Open/closed is a geometric symmetry classification. In code, a closed sequence has even length and contains two opposite even entries such that the intervening blocks are reverse copies. This corresponds to a perpendicular/closed structure in the orbit.

Stable/unstable is detected by an alternating signed sum of code numbers assigned to angle labels. Stable means the resulting linear constraint is identically zero. Unstable means the code is only realized on a one-dimensional constraint line in triangle angle space.

Odd sequences are treated as automatically stable. For legal sequences, odd length and odd sum have the same parity behavior.

## Stable Versus Unstable Geometry

Stable code sequences have two-dimensional realization regions in `(x,y)` angle space. The backend represents them as polygons:

- C++ exact bounding region: `RationalPolygon`.
- C++ interval-refined MRR: `IntervalPolygon`.
- Java display object: `Storage.Stable` with a `ConvexPolygon`.

Unstable code sequences have one-dimensional realization regions. The backend represents them as line segments:

- C++ exact bounding segment: `RationalLineSegment`.
- C++ interval-refined MRR segment: `IntervalLineSegment`.
- Java display object: `Storage.Unstable` with a `LineSegment` plus a linear constraint.

The "MRR" is the maximal region of realization. It is the part of angle space where a given symbolic code actually corresponds to a valid billiard trajectory with all required inequalities positive.

## Symbolic Equations

The backend represents trigonometric boundary equations symbolically:

```text
sum_i c_i * sin(a_i*x + b_i*y)
sum_i c_i * cos(a_i*x + b_i*y)
```

Core C++ types:

- `LinComArrZ<XY>` stores linear arguments `a*x + b*y`.
- `Sin<LinComArrZ<XY>>` and `Cos<LinComArrZ<XY>>` tag arguments by trig function.
- `Equation<Sin>` is a map from `Sin(argument)` to integer coefficient.
- `Equation<Cos>` is the analogous cosine map.

These equations are generated from the unfolding. The high-level derivation is:

1. Build all relevant paths between left-side and right-side vertices in the unfolded triangle graph.
2. Compute each path vector as a symbolic `(x,y)` vector.
3. Compute the shooting vector for the code.
4. Use a determinant/cross product condition:

```text
path_vector_x * shooting_vector_y - shooting_vector_x * path_vector_y = 0
```

5. Factor out obvious line factors such as `sin(x)`, `sin(y)`, and `sin(x+y)` where appropriate.
6. Use the remaining equations as boundary curves for the MRR.

The program uses exact integer symbolic manipulation for equations and interval arithmetic for rigorous sign decisions during refinement.

## Main Backend Pipeline

For a single code sequence:

1. Java or C++ receives integer code numbers.
2. `CodeSequence` validates and canonicalizes the list.
3. `CodeSequence::type()` classifies the code as `OSO`, `OSNO`, `ONS`, `CS`, or `CNS`.
4. `calculate_stable` or `calculate_unstable` chooses the right path.
5. The backend constructs `Unfolding`.
6. It computes a shooting vector:
   - `OSO`: odd/open shooting angle formula.
   - `CS` or `CNS`: closed/perpendicular shooting formula.
   - `OSNO` or `ONS`: general unfolding path vector.
7. It generates boundary curves from all left/right path combinations.
8. It computes a rational bounding polygon or segment from linear inequalities.
9. It refines that exact bounding shape by nonlinear sine/cosine curves using interval arithmetic and Newton/intersection routines.
10. It serializes initial angles, points, equations, and left/right references into SQLite.
11. Java loads points/equations and renders the result.

## Main Java Pipeline

Java is the user-facing application:

1. `Main` launches JavaFX.
2. `Viewer` builds the main UI and holds the current visual state.
3. `Wrapper` exposes native C++ functions through JNA.
4. `Database` converts raw strings returned by C++ into Java geometry and equations.
5. `Storage` wraps stable and unstable objects behind one common display API.
6. Task classes such as `DrawPictureTask`, `PolyVaryTask`, `VaryLTask`, and `CycleVaryTask` run long operations off the JavaFX thread.
7. Rendering methods in `Viewer` paint stable polygons, unstable segments, guide lines, cover rectangles, and loaded cover artifacts into JavaFX `WritableImage` layers.

## Vary Algorithms

The project contains three main search families:

### VaryCS

`VaryCS` searches closed stable codes. It uses the fact that CS paths contain a 90-degree reflection. This fixes the shooting angle and converts the beam into an interval on a side of the triangle. Recursion/unfolding branches when the beam interval is split by a vertex. Candidate codes are formed by mirroring the half-code.

This is fast because it searches beam intervals, not arbitrary shooting angles.

### Vary3

`Vary3` shoots from a fixed starting position on a side. It tracks an interval of possible shooting angles. At each unfolded triangle, the angle to the opposite vertex splits the interval into left and right branches. It can find more than CS codes but is slower and depends on the chosen starting positions.

### Vary4

`Vary4` searches all possible codes at a coordinate without depending on a finite number of initial shots. It tracks left and right vertex trails and bounds the admissible beam by worst lines from one side trail to the other. A candidate is accepted when the final triangle is aligned with the initial one and the perfect shooting angle lies inside the surviving interval.

## Cover Verification

Cover code tries to prove that selected stable/unstable data covers a polygonal region in parameter space. The rough process is:

1. Load a polygon and square from cover files.
2. Load stable singles and unstable triples from text/database.
3. Convert stored equations into `StableInfo`, `UnstableInfo`, and `TripleInfo`.
4. Recursively subdivide the square.
5. Use interval/gradient bounds to decide whether equations are positive over each sub-square.
6. Mark squares as covered by stable codes, triple transitions, or empty/unfilled.
7. Save a cover tree and summary counts.

This is mathematically a positivity proof over a partition of angle space. It depends heavily on interval arithmetic and conservative gradient bounds.

## Important Naming Conventions

| Name | Meaning |
| --- | --- |
| `XY` | Symbols `x,y` in final angle coordinates |
| `XYZ` | Abstract angle labels before substituting `z = pi - x - y` |
| `XYPi` | Linear expressions in `x,y,pi` |
| `XYEta` | Linear expressions in `x,y,eta`, with `eta = pi/2` |
| `XYEtaPhi` | Linear expressions in `x,y,eta,phi`, used while eliminating auxiliary shooting-angle variables |
| `LeftRight` | Pair of unfolded vertices defining a path between left and right sides |
| `StableInfo` | Stable polygon plus equations and gradient bounds for cover checking |
| `UnstableInfo` | Unstable segment plus equations and gradient bounds |
| `TripleInfo` | Two neighboring stables and one unstable between them |
| `MRR` | Maximal region of realization |
