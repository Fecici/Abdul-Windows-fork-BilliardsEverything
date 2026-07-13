package billiards.geometry;

// QUESTION: why is it called project? is it a 'projection?'
// NOTE: I have renamed this from Project to Projectable.
public interface Projectable { 
    Interval project(final Vector2 axis); 
}
