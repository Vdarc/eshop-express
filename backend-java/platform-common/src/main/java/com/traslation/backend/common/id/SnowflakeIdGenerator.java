package com.traslation.backend.common.id;

/**
 * Snowflake ID generator: 41-bit timestamp + 10-bit worker + 12-bit sequence.
 *
 * <p>Layout (64 bits, sign bit always 0):
 *
 * <pre>
 *   0 | 41 bits epoch-millis | 10 bits worker | 12 bits sequence
 * </pre>
 *
 * <p>An ID is 19 digits in practice (~1.8e18), which exceeds JavaScript's
 * {@code Number.MAX_SAFE_INTEGER} (2^53-1). Serialising it as a JSON number would
 * silently lose precision in the browser, so {@link IdGeneratorConfiguration}
 * forces {@code Long} to be written as a string.
 *
 * <p>This class is intentionally framework-free so it can be unit tested without
 * a Spring context.
 */
public final class SnowflakeIdGenerator {

    /** 2024-01-01T00:00:00Z, keeps 41 bits usable for ~69 years. */
    private static final long EPOCH = 1704067200000L;

    private static final long WORKER_BITS = 10L;
    private static final long SEQUENCE_BITS = 12L;
    private static final long MAX_WORKER_ID = ~(-1L << WORKER_BITS); // 1023
    private static final long MAX_SEQUENCE = ~(-1L << SEQUENCE_BITS); // 4095
    private static final long WORKER_SHIFT = SEQUENCE_BITS;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_BITS;

    /** Tolerated clock drift before giving up; NTP corrections are usually a few ms. */
    private static final long MAX_BACKWARD_MS = 5L;

    private final long workerId;
    private long lastTimestamp = -1L;
    private long sequence = 0L;

    public SnowflakeIdGenerator(long workerId) {
        if (workerId < 0 || workerId > MAX_WORKER_ID) {
            throw new IllegalArgumentException(
                    "workerId must be in [0, " + MAX_WORKER_ID + "], got " + workerId);
        }
        this.workerId = workerId;
    }

    public long workerId() {
        return workerId;
    }

    /**
     * Returns the next unique ID, blocking at most a few milliseconds when the
     * sequence overflows or the clock drifts backwards.
     *
     * @throws IllegalStateException when the clock moved backwards by more than
     *     {@link #MAX_BACKWARD_MS}; refusing is safer than issuing duplicates.
     */
    public synchronized long nextId() {
        long timestamp = System.currentTimeMillis();

        if (timestamp < lastTimestamp) {
            long backward = lastTimestamp - timestamp;
            if (backward > MAX_BACKWARD_MS) {
                throw new IllegalStateException(
                        "Clock moved backwards by " + backward + "ms, refusing to generate ID");
            }
            timestamp = waitUntil(lastTimestamp);
        }

        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0L) {
                // 4096 IDs in the same millisecond: roll over to the next one
                timestamp = waitUntil(lastTimestamp + 1);
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp = timestamp;
        return ((timestamp - EPOCH) << TIMESTAMP_SHIFT)
                | (workerId << WORKER_SHIFT)
                | sequence;
    }

    /** Busy-waits until the clock reaches {@code target}; sub-millisecond in practice. */
    private long waitUntil(long target) {
        long now = System.currentTimeMillis();
        while (now < target) {
            Thread.onSpinWait();
            now = System.currentTimeMillis();
        }
        return now;
    }

    /** Exposed for tests and troubleshooting: recover the generation time from an ID. */
    public static long timestampOf(long id) {
        return (id >>> TIMESTAMP_SHIFT) + EPOCH;
    }
}
