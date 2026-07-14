package billiards.viewer;

import billiards.codeseq.ClassifiedCodeSequence;
import billiards.codeseq.Storage;
import billiards.database.LeftRight;
import billiards.database.Database;
import billiards.wrapper.ConnectionPool;

import javaslang.Tuple2;
import javaslang.collection.Array;
import javaslang.control.Either;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.eclipse.collections.api.list.ImmutableList;

import javafx.concurrent.Task;

public final class DrawPictureTaskShowLR extends Task<Array<Storage>> implements GracefullyCancelable {
    private final Array<Callable<Either<String, Tuple2<Storage, ImmutableList<LeftRight>>>>> tasks;
    private volatile boolean gracefulCancelRequested = false;

    @Override
    public void requestGracefulCancel() {
        this.gracefulCancelRequested = true;
    }

    public DrawPictureTaskShowLR(final Array<ClassifiedCodeSequence> classCodeSeqs, final ConnectionPool pool) {

        this.tasks = classCodeSeqs.map(classCodeSeq -> () -> {

            // Respect cancellation before entering the native/database path.
            // The backend may not stop an active calculation immediately, but
            // queued work should not begin after a user cancel.
            if (this.gracefulCancelRequested || this.isCancelled() || Thread.currentThread().isInterrupted()) {
                return Either.left("// cancelled " + classCodeSeq);
            }

            final Optional<Tuple2<Storage, ImmutableList<LeftRight>>> opt = Database.loadStorageShowLR(classCodeSeq, pool);

            if (opt.isPresent()) {
                return Either.right(opt.get());
            } else {
                return Either.left("// empty set " + classCodeSeq);
            }
        });
    }

    @Override
    protected Array<Storage> call() {
        final ExecutorService executor = Executors.newFixedThreadPool(Utils.numThreads);

        // Ya know, an auto would be really nice here
        final Array<Future<Either<String, Tuple2<Storage, ImmutableList<LeftRight>>>>> futures =
            this.tasks.map(task -> executor.submit(task));

        int progress = 0;
        final int todo = futures.size();

        this.updateProgress(progress, todo);

        final ArrayList<Storage> storages = new ArrayList<>();

        Optional<ExecutionException> except = Optional.empty();
        boolean queuedWorkCancelled = false;

        ImmutableList<LeftRight> leftRights = null;

        try {
            // This is where we do checking to see if we were cancelled
            for (final Future<Either<String, Tuple2<Storage, ImmutableList<LeftRight>>>> future : futures) {
                if (this.isCancelled() || except.isPresent()) {
                    // Interrupt queued or interrupt-aware workers so canceled
                    // LR display work does not keep allocating behind the UI.
                    future.cancel(true);
                } else {
                    try {
                        if (this.gracefulCancelRequested && !queuedWorkCancelled) {
                            // Keep already-running LR loads so completed
                            // progress is returned through the success path.
                            Utils.cancelQueuedFutures(futures);
                            queuedWorkCancelled = true;
                        }

                        final Either<String, Tuple2<Storage, ImmutableList<LeftRight>>> either = future.get();

                        final String msg;
                        if (either.isLeft()) {
                            msg = either.getLeft();
                        } else {
                            final Tuple2<Storage, ImmutableList<LeftRight>> tup = either.get();

                            final Storage storage = tup._1;

                            storages.add(storage);

                            final ImmutableList<LeftRight> dbLeftRights = tup._2;

                            if (leftRights == null) {
                                leftRights = dbLeftRights;
                                msg =storage+ " "+ leftRights ;
                            } else if (leftRights.equals(dbLeftRights)) {
                                msg = "same " + storage;
                            } else {
                                // not equal
                                leftRights = dbLeftRights;
                                msg =storage+ " "+ leftRights ;

                            }
                        }

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

        return Array.ofAll(storages);
    }
}
