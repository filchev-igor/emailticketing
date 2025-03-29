package lt.dev.emailticketing.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public class ExecutorServiceWrapper implements AutoCloseable {
    private final ExecutorService executor;

    public ExecutorServiceWrapper(ExecutorService executor) {
        this.executor = executor;
    }

    public void submit(Runnable task) {
        executor.submit(task);
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
