package billiards.viewer;

import billiards.codeseq.ClassifiedCodeSequence;
import billiards.codeseq.Storage;
import billiards.database.Database;
import billiards.database.InfoAll;
import billiards.wrapper.ConnectionPool;
import billiards.database.Info;

import billiards.wrapper.Wrapper;
import javaslang.collection.Array;
import javaslang.control.Either;

import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.*;

import javafx.concurrent.Task;

// This task is almost identical to the other one. Hmmm, is there some way
// of dealing with that?
//
// Who wrote this stupid code?
public final class DrawPictureTaskUseLR extends Task<Array<Storage>> implements GracefullyCancelable {
    private final Array<Callable<Either<String, Storage>>> tasks;
    //public CopyOnWriteArrayList<ClassifiedCodeSequence> baseCodeSeq = new CopyOnWriteArrayList<>();
    //public ArrayList<ClassifiedCodeSequence> baseCodeSeq = new ArrayList<>();
    // The stop point belongs to one Use-LR run. Keeping it static leaked an
    // empty-result stop from one viewer operation into the next operation.
    private ClassifiedCodeSequence breakPoint = null;
    private final Object breakPointLock = new Object();
    private volatile boolean gracefulCancelRequested = false;

    @Override
    public void requestGracefulCancel() {
        this.gracefulCancelRequested = true;
    }

    private ClassifiedCodeSequence currentBreakPoint() {
        synchronized (breakPointLock) {
            return breakPoint;
        }
    }

    private void clearBreakPoint() {
        synchronized (breakPointLock) {
            breakPoint = null;
        }
    }

    public DrawPictureTaskUseLR(
            final Array<ClassifiedCodeSequence> classCodeSeqs, final ConnectionPool pool) {

        // The first code seq in the array is the one we use the left rights from
        //baseCodeSeq.add(classCodeSeqs.get(0));
        final ClassifiedCodeSequence baseCodeSeq = classCodeSeqs.get(0);
        this.tasks =  classCodeSeqs.map(classCodeSeq -> () -> {

            // Respect cancellation before entering the native/database path.
            // This avoids starting queued LR reuse work after the viewer has
            // already canceled the JavaFX task.
            if (this.gracefulCancelRequested || this.isCancelled() || Thread.currentThread().isInterrupted()) {
                return Either.left("// cancelled " + classCodeSeq);
            }
            final Optional<Storage> opt = Database.loadStorageUseLR("empty",baseCodeSeq, classCodeSeq, pool);
            if (opt.isPresent()) {
                return Either.right(opt.get());
            } else {
                synchronized (breakPointLock) {
                    if (breakPoint == null || classCodeSeq.compareTo(breakPoint) < 0) {
                        breakPoint = classCodeSeq;
                    }
                }
                Wrapper.deleteFromDatabase(classCodeSeq,pool);
                return Either.left("// empty set " + classCodeSeq);
            }

        });
    }

    @Override
    protected Array<Storage> call() {
        final ExecutorService executor = Executors.newFixedThreadPool(Utils.numThreads);

        final Array<Future<Either<String, Storage>>> futures =
                this.tasks.map(task -> executor.submit(task));

        int progress = 0;
        final int todo = futures.size();

        this.updateProgress(progress, todo);

        final ArrayList<Storage> storages = new ArrayList<>();

        Optional<ExecutionException> except = Optional.empty();
        boolean queuedWorkCancelled = false;

        try {
            // This is where we do checking to see if we were cancelled
            for (final Future<Either<String, Storage>> future : futures) {
                if (this.isCancelled() || except.isPresent()) {
                    // Interrupt queued or interrupt-aware workers so canceled
                    // LR reuse work does not continue behind the UI.
                    future.cancel(true);
                } else {
                    try {
                        if (this.gracefulCancelRequested && !queuedWorkCancelled) {
                            // User cancel should preserve any LR reuse work
                            // already running, while preventing queued codes.
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
                            msg = storage.toString();

                            //aug 25,2019 george thia shows the use left right and prints 1 3 3 for example as storage
                            // System.out.print("storage: " + storage + "\n");

                        }
                        final ClassifiedCodeSequence stopPoint = currentBreakPoint();
                        String temp = "// empty set " + stopPoint;
                        //System.out.println("temp"+temp);
                        //System.out.println("msg"+msg);
                        if (msg.equals(temp)) {
                            System.out.println("Stop at " + stopPoint);
                            clearBreakPoint();
                            Utils.cancelQueuedFutures(futures);
                            break;
                        }
                        else if (msg.contains("// empty set ")) {
                            String msg2 = msg.replace("// empty set ","Stop at ");
                            System.out.println(msg2);
                            clearBreakPoint();
                            Utils.cancelQueuedFutures(futures);
                            break;
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
