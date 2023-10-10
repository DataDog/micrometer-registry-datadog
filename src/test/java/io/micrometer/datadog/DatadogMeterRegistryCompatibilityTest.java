package io.micrometer.datadog;

import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.tck.MeterRegistryCompatibilityKit;

import java.time.Duration;

class DatadogMeterRegistryCompatibilityTest extends MeterRegistryCompatibilityKit {

    private final DatadogConfig config = new DatadogConfig() {
        @Override
        public boolean enabled() {
            return false;
        }

        @Override
        @Nullable
        public String get(String key) {
            return null;
        }
    };

    @Override
    public MeterRegistry registry() {
        return new DatadogMeterRegistry(config, new MockClock());
    }

    @Override
    public Duration step() {
        return config.step();
    }

}
