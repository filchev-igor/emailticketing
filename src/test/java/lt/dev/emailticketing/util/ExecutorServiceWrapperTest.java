package lt.dev.emailticketing.util;

import org.junit.jupiter.api.Test;

import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExecutorServiceWrapperTest {

    @Test
    void submit_shouldExecuteAllTasksBeforeClose() {
        AtomicInteger counter = new AtomicInteger(0);

        try (ExecutorServiceWrapper wrapper = new ExecutorServiceWrapper(Executors.newFixedThreadPool(2))) {
            wrapper.submit(() -> {
                try {
                    Thread.sleep(100);
                    counter.incrementAndGet();
                } catch (InterruptedException ignored) {}
            });

            wrapper.submit(() -> {
                try {
                    Thread.sleep(150);
                    counter.incrementAndGet();
                } catch (InterruptedException ignored) {}
            });
        }

        assertEquals(2, counter.get());
    }
}
