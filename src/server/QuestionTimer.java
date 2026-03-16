package server;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class QuestionTimer {
    private final int totalSeconds;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    public QuestionTimer(int totalSeconds) {
        if (totalSeconds <= 0) {
            throw new IllegalArgumentException("totalSeconds must be > 0");
        }
        this.totalSeconds = totalSeconds;
    }

    public void cancel() {
        cancelled.set(true);
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    public void await(Consumer<Integer> onSecondsRemainingAnnouncement) throws InterruptedException {
        CountDownLatch done = new CountDownLatch(1);
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "QuestionTimer");
            t.setDaemon(true);
            return t;
        });

        try {
            scheduleAnnouncement(scheduler, done, 15, onSecondsRemainingAnnouncement);
            scheduleAnnouncement(scheduler, done, 5, onSecondsRemainingAnnouncement);

            scheduler.schedule(() -> {
                if (!cancelled.get()) {
                    done.countDown();
                }
            }, totalSeconds, TimeUnit.SECONDS);

            while (!cancelled.get()) {
                if (done.await(250, TimeUnit.MILLISECONDS)) {
                    return;
                }
            }
        } finally {
            scheduler.shutdownNow();
        }
    }

    private void scheduleAnnouncement(
            ScheduledExecutorService scheduler,
            CountDownLatch done,
            int secondsRemaining,
            Consumer<Integer> onSecondsRemainingAnnouncement
    ) {
        if (secondsRemaining <= 0 || secondsRemaining >= totalSeconds) return;
        long delaySeconds = totalSeconds - secondsRemaining;
        scheduler.schedule(() -> {
            if (cancelled.get()) return;
            if (done.getCount() == 0) return;
            onSecondsRemainingAnnouncement.accept(secondsRemaining);
        }, delaySeconds, TimeUnit.SECONDS);
    }
}

