package billiards.viewer;

import billiards.codeseq.ClassifiedCodeSequence;
import billiards.wrapper.ConnectionPool;
import billiards.wrapper.Wrapper;

import javaslang.collection.Array;

import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javafx.concurrent.Task;

public final class DontDrawPictureTask extends Task<Void> implements GracefullyCancelable {
    private final Array<Callable<String>> tasks;
    private volatile boolean gracefulCancelRequested = false;

    @Override
    public void requestGracefulCancel() {
        this.gracefulCancelRequested = true;
    }

    public DontDrawPictureTask(final Array<ClassifiedCodeSequence> classCodeSeqs, final ConnectionPool pool) {

        this.tasks = classCodeSeqs.map(classCodeSeq -> () -> {
            // Stop queued saves before they enter the native backend after the
            // user cancels a no-draw database load.
            if (this.gracefulCancelRequested || this.isCancelled() || Thread.currentThread().isInterrupted()) {
                return "//cancelled " + classCodeSeq;
            }

            final boolean nonEmpty = Wrapper.saveToDatabase(classCodeSeq, pool);

            if (nonEmpty) {
                return classCodeSeq.toString();
            } else {
                return "//empty set " + classCodeSeq;
            }
        });
    }

    @Override
    protected Void call() {
        // You know, we could use the structures in javaslang. They are functional,
        // so they might not have the performance of the standard library collections,
        // but they are far better designed, and honestly the performance won't
        // matter. All the high performance code is in C++, and the one place where
        // I think I will use it for basic maps, folds, and filters, but things
        // that could update the data structure, such as add, append, etc that
        // often find in maps I'll avoid.
        // Javaslang is really just a replacement for the whole builder, add to builder,
        // build pattern that I see quite a bit. Replacing it with a map is quite nice.

        final ExecutorService executor = Executors.newFixedThreadPool(Utils.numThreads);

        // Beauty.
        final Array<Future<String>> futures = this.tasks.map(task -> executor.submit(task));

        Optional<ExecutionException> except = Optional.empty();
        boolean queuedWorkCancelled = false;

        int progress = 0;
        final int todo = futures.size();

        this.updateProgress(progress, todo);

        try {
            for (final Future<String> future : futures) {
                if (this.isCancelled() || except.isPresent()) {
                    // Interrupt queued or interrupt-aware saves so canceling a
                    // large import does not continue filling the database.
                    future.cancel(true);
                } else {
                    try {
                        if (this.gracefulCancelRequested && !queuedWorkCancelled) {
                            // For no-draw imports, Cancel means save the DB
                            // writes already running and suppress queued writes.
                            Utils.cancelQueuedFutures(futures);
                            queuedWorkCancelled = true;
                        }

                        // When running the futures on multiple threads, it seems
                        // that we update the message and progress too often
                        // for the event loop in the Viewer to register it.
                        // So we have updated this several times by the time
                        // the Viewer gets around to checking that it has been
                        // modified. So we just print it.
                        final String msg = future.get();
                        System.out.println(msg);
                        // this.updateMessage(msg);

                        progress += 1;
                        this.updateProgress(progress, todo);
                    } catch (final ExecutionException e) {
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
        } finally {
            if (this.isCancelled() || except.isPresent()) {
                Utils.cancelFutures(futures);
            }
            Utils.safeShutdownExecutor(executor, 30, TimeUnit.SECONDS);
        }

        if (except.isPresent()) {
            throw new RuntimeException(except.get());
        }

        return null;
    }
}
