package billiards.viewer;

import billiards.codeseq.ClassifiedCodeSequence;
import billiards.codeseq.CodeType;
import billiards.codeseq.Storage;
import billiards.database.Database;
import billiards.wrapper.ConnectionPool;

import javaslang.collection.Array;
import javaslang.control.Either;

import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;

public final class DrawPictureTask extends Task<Array<Storage>> implements GracefullyCancelable {
    protected final Array<Callable<Either<String, Storage>>> tasks;
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

    public DrawPictureTask(
        final Array<ClassifiedCodeSequence> classCodeSeqs, final ConnectionPool pool, final ExecutorService executor, boolean print, boolean detailed) {
        this.print = print;
        this.detailed = detailed;
        this.executor = executor;
        this.tasks = classCodeSeqs.map(classCodeSeq -> () -> {
            // Respect cancellation before entering the native/database path.
            // Some backend calls cannot be interrupted once they are inside
            // GMP/MPFR, but queued work should not start after a user cancel.
            if (this.gracefulCancelRequested || this.isCancelled() || Thread.currentThread().isInterrupted()) {
                return Either.left("//cancelled " + classCodeSeq);
            }

            // Load from database if code already exists. If not, calculate
            final Optional<Storage> opt = Database.loadStorage(classCodeSeq, pool);

            if (opt.isPresent()) {
                final Storage storage = opt.get();
                if (!this.isCancelled()) {
                    Platform.runLater(() -> {
                        // The cancel can arrive after scheduling runLater; guard
                        // again so stale partials are not added to the viewer.
                        if (!this.isCancelled()) {
                            this.partialResults.get().add(storage);
                        }
                    });
                }
                return Either.right(storage);
            } else {
                return Either.left("//empty set " + classCodeSeq);
            }
        });
    }

    @Override
    protected Array<Storage> call() {
        //final ExecutorService executor = Executors.newFixedThreadPool(Utils.numThreads);

        final Array<Future<Either<String, Storage>>> futures =
            this.tasks.map(task -> this.executor.submit(task));

        int progress = 0;
        final int todo = futures.size();

        this.updateProgress(progress, todo);

        final ArrayList<Storage> storages = new ArrayList<>();

        Optional<ExecutionException> except = Optional.empty();
        boolean queuedWorkCancelled = false;

        // If one of the futures throws an exception (like a failed to
        // calculate exception), we need to save it, cancel the rest of
        // the futures, and then throw that exception to bubble up the stack

        // This is where we do checking to see if we were cancelled
        for (final Future<Either<String, Storage>> future : futures) {
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
                        // Cancel only work that has not started. Running work is
                        // allowed to finish so the Cancel button still saves
                        // completed progress.
                        Utils.cancelQueuedFutures(futures);
                        queuedWorkCancelled = true;
                    }

                    final Either<String, Storage> either = future.get();

                    final String msg;
                    if (either.isLeft()) {
                        msg = either.getLeft();
                    } else {
                        final Storage storage = either.get();
                        storages.add(storage);
                        if(detailed) {
                            // print the code, whether it covered the pixel or not
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
                            msg = codeStr + " (" + storage.codeLength() + ", " + storage.codeSum() + ") " + storage.toString();
                        } else {
                            msg = storage.toString();
                        }
                    }
                    
                    if (print) {
                    	System.out.println(msg);
                    }
                    // this.updateMessage(msg);

                    progress += 1;
                    this.updateProgress(progress, todo);
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
