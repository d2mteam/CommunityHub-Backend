package com.app.communityhub.common;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SnowflakeIdGeneratorTest {

    @Test
    void generatesIncreasingIdsInSameJvm() {
        MutableClock clock = new MutableClock(Instant.parse("2026-04-23T00:00:00Z"));
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1, clock);

        long first = generator.nextId();
        clock.advanceMillis(1);
        long second = generator.nextId();

        assertThat(second).isGreaterThan(first);
    }

    @Test
    void generatesUniqueIdsWithinTheSameMillisecond() {
        MutableClock clock = new MutableClock(Instant.parse("2026-04-23T00:00:00Z"));
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1, clock);

        List<Long> ids = new ArrayList<>();
        for (int index = 0; index < 128; index += 1) {
            ids.add(generator.nextId());
        }

        assertThat(new HashSet<>(ids)).hasSize(ids.size());
        assertThat(ids).isSorted();
    }

    @Test
    void rejectsInvalidWorkerId() {
        MutableClock clock = new MutableClock(Instant.parse("2026-04-23T00:00:00Z"));

        assertThatThrownBy(() -> new SnowflakeIdGenerator(1024, clock))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("worker id");
    }

    @Test
    void failsWhenClockMovesBackwards() {
        MutableClock clock = new MutableClock(Instant.parse("2026-04-23T00:00:00Z"));
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1, clock);

        generator.nextId();
        clock.advanceMillis(-1);

        assertThatThrownBy(generator::nextId)
                .isInstanceOf(AppException.class)
                .hasMessageContaining("clock moved backwards");
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advanceMillis(long millis) {
            instant = instant.plusMillis(millis);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
