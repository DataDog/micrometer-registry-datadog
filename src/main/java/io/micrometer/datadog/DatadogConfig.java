package io.micrometer.datadog;

import io.micrometer.core.instrument.config.validate.Validated;
import io.micrometer.core.instrument.step.StepRegistryConfig;

import static io.micrometer.core.instrument.config.MeterRegistryConfigValidator.checkAll;
import static io.micrometer.core.instrument.config.validate.PropertyValidator.*;
import static io.micrometer.core.instrument.config.validate.PropertyValidator.getInteger;

/**
 * Configuration for {@link DatadogMeterRegistry}.
 *
 */
public interface DatadogConfig extends StepRegistryConfig {
    @Override
    default String prefix() {
        return "datadog";
    }

    /**
     * @return {@code true} if client-side telemetry should be enabled when in Dogstatsd
     * mode.
     * <a href="https://docs.datadoghq.com/developers/dogstatsd/high_throughput/?code-lang=java#client-side-telemetry">Client-Side Telemetry</a>
     */
    default boolean enableClientSideTelemetry() {
        return getBoolean(this, "disableClientSideTelemetry").orElse(true);
    }

    /**
     * @return The maximum packet size to buffer before sending metrics when using
     * Dogstatsd mode.
     * <a href="https://docs.datadoghq.com/developers/dogstatsd/high_throughput/?code-lang=java#enable-buffering-on-your-client">Enable Buffering on your Client</a>
     */
    default Integer maxPacketSizeBytes() {
        return getInteger(this, "maxPacketSizeBytes").orElse(-1);
    }

    /**
     * @return {@code false} if client-side aggregation should be enabled when in
     * Dogstatsd mode.
     * <a href="https://docs.datadoghq.com/developers/dogstatsd/high_throughput/?code-lang=java#client-side-aggregation">Client Side Aggregation</a>
     */
    default boolean enableAggregation() {
        return getBoolean(this, "enableAggregation").orElse(false);
    }

    /**
     * @return {@code 3000} sets the period of time in milliseconds in which the
     * aggregator will flush its metrics into the sender when in Dogstatsd mode.
     * <a href="https://github.com/DataDog/java-dogstatsd-client#configuration-1">Aggregation Flush Settings</a>
     */
    default Integer aggregationFlushInterval() {
        return getInteger(this, "aggregationFlushInterval").orElse(3000);
    }

    /**
     * @return {@code 4} determines the number of shards in the aggregator, this feature
     * is aimed at mitigating the effects of map locking in highly concurrent scenarios
     * when in Dogstatsd mode.
     * <a href="https://github.com/DataDog/java-dogstatsd-client#configuration-1">Aggregation Flush Settings</a>
     */
    default Integer aggregationShards() {
        return getInteger(this, "aggregationShards").orElse(4);
    }

    @Override
    default Validated<?> validate() {
        return checkAll(this, c -> StepRegistryConfig.validate(c));
    }
}