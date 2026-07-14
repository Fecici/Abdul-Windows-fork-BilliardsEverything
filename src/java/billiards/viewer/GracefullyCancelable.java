package billiards.viewer;

/**
 * Marker for JavaFX tasks whose Cancel button should preserve completed work.
 * A graceful cancel requests that no new work be started, while already-running
 * database/native calls may finish and publish their completed Storage results.
 */
public interface GracefullyCancelable {
    void requestGracefulCancel();
}
