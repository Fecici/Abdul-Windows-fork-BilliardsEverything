package billiards.viewer;

import billiards.codeseq.ClassifiedCodeSequence;
import billiards.codeseq.CodeType;
import billiards.codeseq.Storage;
import billiards.database.Database;
import billiards.wrapper.ConnectionPool;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javaslang.collection.Array;
import javaslang.control.Either;

import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * Zhao Yu Li, May 13, 2025.
 * This is a sibling class of the DrawPictureTask that specifically handles triples. In particular, this class preserves
 * the grouping of the components in a triple, whereas DrawPictureTask separates a triple into its components, and
 * perform computations (e.g. check for intersection) on the components individually.
 */
public final class DrawPictureTaskTriples extends Task<Array<Storage[]>> implements GracefullyCancelable {
    protected final Array<Callable<ArrayList<Either<String, Storage>>>> tasks;
    protected final boolean print;
    protected final boolean detailed;

    private final ExecutorService executor;
    private volatile boolean gracefulCancelRequested = false;
    private ReadOnlyObjectWrapper<ObservableList<Storage>> partialResults =
            new ReadOnlyObjectWrapper<>(
                    this,
                    "partialResults",
                    FXCollections.observableArrayList(
                            new ArrayList<Storage>()
                    )
            );

    // These expose partialResults to the FX application thread
    public final ObservableList<Storage> getPartials() {
        return this.partialResults.get();
    }

    public final ReadOnlyObjectProperty<ObservableList<Storage>> getPartialProperty() {
        return this.partialResults.getReadOnlyProperty();
    }

    @Override
    public void requestGracefulCancel() {
        this.gracefulCancelRequested = true;
    }

    public DrawPictureTaskTriples(
        final Array<ClassifiedCodeSequence[]> triples,
        final ConnectionPool pool, final ExecutorService executor, boolean print, boolean detailed) {
        this.print = print;
        this.detailed = detailed;
        this.executor = executor;
        this.tasks = triples.map(triple -> () -> {
            // Respect cancellation before entering the native/database path.
            // Queued triple work should not start after the user cancels the
            // JavaFX task, even if an already-running native call cannot stop.
            if (this.gracefulCancelRequested || this.isCancelled() || Thread.currentThread().isInterrupted()) {
                return new ArrayList<>();
            }
            ArrayList<Either<String, Storage>> results = new ArrayList<>();

            for (ClassifiedCodeSequence classCodeSeq : triple) {
                if (this.gracefulCancelRequested || this.isCancelled() || Thread.currentThread().isInterrupted()) {
                    break;
                }

                // Load from database if code already exists. If not, calculate
                final Optional<Storage> opt = Database.loadStorage(classCodeSeq, pool);

                if (opt.isPresent()) {
                    final Storage storage = opt.get();
                    if (!this.isCancelled()) {
                        Platform.runLater(() -> {
                            // The cancel can arrive after scheduling runLater;
                            // guard again so stale partials are not rendered.
                            if (!this.isCancelled()) {
                                this.partialResults.get().add(storage);
                            }
                        });
                    }
                    results.add(Either.right(storage));
                } else {
                     results.add(Either.left("//empty set " + classCodeSeq));
                }
            }

            return results;
        });
    }

    @Override
    protected Array<Storage[]> call() {
        //final ExecutorService executor = Executors.newFixedThreadPool(Utils.numThreads);

        final Array<Future<ArrayList<Either<String, Storage>>>> futures =
            this.tasks.map(this.executor::submit);

        int progress = 0;
        final int todo = futures.size();

        this.updateProgress(progress, todo);

        final ArrayList<Storage[]> storages = new ArrayList<>();

        Optional<ExecutionException> except = Optional.empty();
        boolean queuedWorkCancelled = false;

        // If one of the futures throws an exception (like a failed to
        // calculate exception), we need to save it, cancel the rest of
        // the futures, and then throw that exception to bubble up the stack

        // This is where we do checking to see if we were cancelled
        for (final Future<ArrayList<Either<String, Storage>>> future : futures) {
            if (this.isCancelled() || except.isPresent()) {
                // If the task was cancelled, or one of the futures threw an
                // exception, we need to cancel the rest of the futures

                // Interrupting will not stop every native calculation, but it
                // does stop queued Java work and any interrupt-aware database
                // path before more memory is allocated.
                future.cancel(true);
            } else {
                try {
                    if (this.gracefulCancelRequested && !queuedWorkCancelled) {
                        // Cancel only triples that have not started. A triple
                        // already being loaded can finish and be preserved.
                        Utils.cancelQueuedFutures(futures);
                        queuedWorkCancelled = true;
                    }

                    final ArrayList<Either<String, Storage>> res = future.get();
                    if (this.isCancelled() || res.isEmpty()) {
                        break;
                    }
                    if (res.size() < 3) {
                        // Graceful cancel can stop a triple between components.
                        // Only complete triples are safe for the viewer's
                        // triple rendering path.
                        continue;
                    }
                    final Storage[] storageTriple = new Storage[3];
                    final StringBuilder msg = new StringBuilder();

                    for (int i = 1; i < res.size() + 1; i++) {
                        Either<String, Storage> either = res.get(i - 1);

                        if (either.isLeft()) {
                            msg.append("Component ").append(i).append(": ").append(either.getLeft()).append("\n");
                        } else {
                            final Storage storage = either.get();
                            storageTriple[i - 1] = storage;
                            if(detailed) {
                                // print the triple, whether it covered the pixel or not
                                final CodeType type = storage.codeType();

                                String codeStr = "" + type;
                                // String codeStr = "xxx " + type; //george july 26 2017 -
                                // type whatever you want between the quotes in the line above
                                // make sure to add a space after the xxx
                                if (codeStr.equals("CS")) {
                                    codeStr += "  ";
                                } else if (!codeStr.equals("OSNO")) {
                                    codeStr += " ";
                                }
                                msg.append("Component ").append(i).append(": ").append(codeStr).append(" (")
                                        .append(storage.codeLength()).append(", ").append(storage.codeSum())
                                        .append(") ").append(storage).append("\n");
                            } else {
                                msg.append(storage.toString());

                                if (i < 3) msg.append(", ");
                                else msg.append("\n");
                            }
                        }

                        if (print) {
                            System.out.println(msg);
                        }
                        // this.updateMessage(msg);

                        progress += 1;
                        this.updateProgress(progress, todo);
                    }

                    storages.add(storageTriple);
                } catch (final ExecutionException e) {
                    // One of the futures threw an exception during its calculation,
                    // so we need to cancel the rest of the futures
                    except = Optional.of(e);
                } catch (final CancellationException e) {
                    // Expected for queued futures suppressed by graceful cancel.
                } catch (final InterruptedException e) {
                    Utils.cancelFutures(futures);
                    Thread.currentThread().interrupt();
                    if (!this.isCancelled()) {
                        throw new RuntimeException(e);
                    }
                    break;
                }
            }
        }

        // If there is an exception that happened, throw it now after shutting down the executor
        if (except.isPresent()) {
            Utils.cancelFutures(futures);
            throw new RuntimeException(except.get());
        }

        return Array.ofAll(storages);
    }
}
