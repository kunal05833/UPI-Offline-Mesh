package com.upi.mesh;

import com.upi.mesh.service.IdempotencyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that exactly ONE thread wins the idempotency race even under
 * high concurrency — the core safety property of the system.
 */
@SpringBootTest
class IdempotencyConcurrencyTest {

    @Autowired
    private IdempotencyService idempotency;

    @BeforeEach
    void reset() {
        idempotency.clear();
    }

    @Test
    void exactlyOneWinnerUnderHighConcurrency() throws InterruptedException {
        final int THREADS = 50;
        final String hash = "test-hash-abc123";

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch go    = new CountDownLatch(1);
        AtomicInteger wins   = new AtomicInteger(0);

        for (int i = 0; i < THREADS; i++) {
            pool.submit(() -> {
                ready.countDown();
                try { go.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                if (idempotency.claim(hash)) wins.incrementAndGet();
            });
        }

        ready.await();
        go.countDown();
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);

        assertEquals(1, wins.get(),
                "Exactly one thread must win the idempotency race, got: " + wins.get());
    }

    @Test
    void differentHashesAreIndependent() {
        assertTrue(idempotency.claim("hash-A"));
        assertTrue(idempotency.claim("hash-B"));
        assertFalse(idempotency.claim("hash-A")); // duplicate
        assertFalse(idempotency.claim("hash-B")); // duplicate
    }
}
