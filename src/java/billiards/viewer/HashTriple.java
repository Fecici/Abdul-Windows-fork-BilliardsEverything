package billiards.viewer;

import billiards.codeseq.ClassifiedCodeSequence;
import billiards.cover.Triple;
import billiards.cover.HalfTriple;
import billiards.geometry.Rectangle;

import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.impl.list.mutable.FastList;
import org.eclipse.collections.impl.map.mutable.UnifiedMap;

import java.util.Map;

import javafx.scene.paint.Color;

public class HashTriple {

    private MutableMap<Rectangle, ClassifiedCodeSequence> stableMap = new UnifiedMap<>();
    private MutableMap<Rectangle, Triple> tripleMap = new UnifiedMap<>();
    private MutableMap<Rectangle, Color> colorMap = new UnifiedMap<>();
    private MutableMap<Rectangle, HalfTriple> halfTripleMap = new UnifiedMap<>();
    private Color defaultColor;

    public HashTriple() {
        this.defaultColor = Color.BLACK;
    }

    // INFO: (Abdul, Jul 2026) changed to use default cover.
    // Purpose: reduce memory spikes by a lot, 
    // caused by stale entries in the colour map. 
    // Did not yet test with different colours; nobody 
    // uses them anyways, but this needs to be done
    // I am still learning the codebase, so I will come back
    // to this once I know more.
    public void addStables(Map<Rectangle, ClassifiedCodeSequence> otherMap, Color color) {
        if (otherMap instanceof MutableMap) {
            this.stableMap = (MutableMap<Rectangle, ClassifiedCodeSequence>) otherMap;
        } else {
            this.stableMap.clear();
            this.stableMap.putAll(otherMap);
        }

        this.defaultColor = color;
    }

    public void addTriples(final Map<Rectangle, Triple> otherMap, final Color color) {
        if (otherMap instanceof MutableMap) {
            this.tripleMap = (MutableMap<Rectangle, Triple>) otherMap;
        } else {
            this.tripleMap.clear();
            this.tripleMap.putAll(otherMap);
        }
        this.defaultColor = color;
    }
    public void addHalfTriples(final Map<Rectangle, HalfTriple> otherMap, final Color color){
        if (otherMap instanceof MutableMap) {
            this.halfTripleMap = (MutableMap<Rectangle, HalfTriple>) otherMap;
        } else {
            this.halfTripleMap.clear();
            this.halfTripleMap.putAll(otherMap);
        }

        this.defaultColor = color;
    }

    public void clear() {
        this.stableMap.clear();
        this.tripleMap.clear();
        this.colorMap.clear();
        this.halfTripleMap.clear();

    }

    public MutableList<Rectangle> stableEntrySet() {
        final MutableList<Rectangle> entries = new FastList<>();
        stableMap.entrySet().forEach(rect -> entries.add(rect.getKey()));
        return entries;
    }

    public MutableList<Rectangle> tripleEntrySet() {
        final MutableList<Rectangle> entries = new FastList<>();
        tripleMap.entrySet().forEach(rect -> entries.add(rect.getKey()));
        return entries;
    }

    public MutableList<Rectangle> HalfTripleEntrySet() {
        final MutableList<Rectangle> entries = new FastList<>();
        halfTripleMap.entrySet().forEach(rect -> entries.add(rect.getKey()));
        return entries;
    }

    public void remove(Rectangle rect) {
        this.stableMap.remove(rect);
        this.tripleMap.remove(rect);
        this.halfTripleMap.remove(rect);
        this.colorMap.remove(rect);
    }

    public ClassifiedCodeSequence getStable(final Rectangle rect) {
        return stableMap.get(rect);
    }

    public Triple getTriple(final Rectangle rect) {
        return tripleMap.get(rect);
    }

    public HalfTriple getHalfTriple(final Rectangle rect) { return halfTripleMap.get(rect); }

    public Color getColor(final Rectangle rect) {
        Color color = colorMap.get(rect);
        return color != null ? color : this.defaultColor;
    }

    public void put(final Rectangle rect, final ClassifiedCodeSequence stable) {
        stableMap.put(rect, stable);
    }

    public void put(final Rectangle rect, final Triple triple) {
        tripleMap.put(rect, triple);
    }

    public void put(final Rectangle rect, final HalfTriple halfTriple) {
        halfTripleMap.put(rect, halfTriple);
    }

    public void put(final Rectangle rect, final Color color) {
        colorMap.put(rect, color);
    }
}
