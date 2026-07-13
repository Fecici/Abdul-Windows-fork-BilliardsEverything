package billiards.wrapper;

import com.sun.jna.Pointer;
import com.sun.jna.Structure;

import org.eclipse.collections.impl.list.mutable.FastList;

import java.util.List;

// This class must be public for JNA to work
// Make sure the names and orders of the class and fields
// match those in the struct
// The fields here change obviously, so don't make them final
public final class CInfoAll extends Structure {

    public Pointer initial_angles;
    public Pointer points;
    public Pointer equations;
    public Pointer left_rights;
    public Pointer code_seq_lr;
    public Pointer sinEquations;
    public Pointer cosEquations;
    public Pointer vectorX;
    public Pointer vectorY;

    @Override
    public List<String> getFieldOrder() {
        // JNA lays out native structures in this exact order. Keep this in
        // sync with CInfoAll in src/backend/headers/wrapper.hpp or Java will
        // read and free the wrong native char* fields.
        return FastList.newListWith("initial_angles", "points", "equations",
                                    "sinEquations", "cosEquations",
                                    "left_rights", "code_seq_lr",
                                    "vectorX", "vectorY");
    }
}
