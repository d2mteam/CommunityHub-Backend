package com.app.communityhub.common;

import com.app.communityhub.config.AppProperties;
import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SnowflakeIdGenerator {

    private static final Instant CUSTOM_EPOCH = Instant.parse("2026-01-01T00:00:00Z");
    private static final long WORKER_ID_BITS = 10L;
    private static final long SEQUENCE_BITS = 12L;
    private static final long MAX_WORKER_ID = (1L << WORKER_ID_BITS) - 1;
    private static final long SEQUENCE_MASK = (1L << SEQUENCE_BITS) - 1;
    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;

    private final long workerId;
    private final Clock clock;
    private long lastTimestamp = -1L;
    private long sequence = 0L;

    @Autowired
    public SnowflakeIdGenerator(AppProperties appProperties) {
        this(appProperties.getIds().getWorkerId(), Clock.systemUTC());
    }

    SnowflakeIdGenerator(long workerId, Clock clock) {
        if (workerId < 0 || workerId > MAX_WORKER_ID) {
            throw new IllegalArgumentException("Snowflake worker id must be between 0 and " + MAX_WORKER_ID);
        }
        this.workerId = workerId;
        this.clock = clock;
    }

    public synchronized long nextId() {
        long timestamp = currentTimestampMillis();
        if (timestamp < lastTimestamp) {
            throw new AppException(
                    org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "System clock moved backwards; refusing to generate Snowflake id"
            );
        }

        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                timestamp = waitForNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0;
        }

        lastTimestamp = timestamp;
        return (timestamp << TIMESTAMP_SHIFT) | (workerId << WORKER_ID_SHIFT) | sequence;
    }

    private long waitForNextMillis(long previousTimestamp) {
        long timestamp = currentTimestampMillis();
        while (timestamp <= previousTimestamp) {
            Thread.onSpinWait();
            timestamp = currentTimestampMillis();
        }
        return timestamp;
    }

    private long currentTimestampMillis() {
        long timestamp = clock.millis() - CUSTOM_EPOCH.toEpochMilli();
        if (timestamp < 0) {
            throw new AppException(
                    org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "Current time is before the Snowflake custom epoch"
            );
        }
        return timestamp;
    }
}
