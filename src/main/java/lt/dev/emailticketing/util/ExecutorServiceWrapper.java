package lt.dev.emailticketing.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public class ExecutorServiceWrapper implements AutoCloseable {
    private final ExecutorService executorService;

    public ExecutorServiceWrapper(ExecutorService executorService) {
        this.executorService = executorService;
    }

    public void submit(Runnable task) {
        executorService.submit(task);
    }

    @Override
    public void close() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
