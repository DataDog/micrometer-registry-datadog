package io.micrometer.datadog;

import com.timgroup.statsd.NonBlockingStatsDClientBuilder;
import com.timgroup.statsd.StatsDClient;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.cumulative.CumulativeCounter;
import io.micrometer.core.instrument.cumulative.CumulativeDistributionSummary;
import io.micrometer.core.instrument.cumulative.CumulativeFunctionCounter;
import io.micrometer.core.instrument.cumulative.CumulativeFunctionTimer;
import io.micrometer.core.instrument.cumulative.CumulativeTimer;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.internal.CumulativeHistogramLongTaskTimer;
import io.micrometer.core.instrument.internal.DefaultGauge;
import io.micrometer.core.instrument.internal.DefaultMeter;
import io.micrometer.core.instrument.push.PushMeterRegistry;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import io.micrometer.core.lang.NonNull;
import io.micrometer.core.lang.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;

import static io.micrometer.core.instrument.util.StringEscapeUtils.escapeJson;
import static java.util.stream.StreamSupport.stream;

/**
 * {@link MeterRegistry} for Datadog.
 */
public class DatadogMeterRegistry extends PushMeterRegistry {
    private static final ThreadFactory DEFAULT_THREAD_FACTORY = new NamedThreadFactory("datadog-metrics-publisher");

    private final Logger logger = LoggerFactory.getLogger(DatadogMeterRegistry.class);

    private final DatadogConfig config;

    private final StatsDClient statsDClient;


    /**
     * @param config Configuration options for the registry that are describable as
     * properties.
     * @param clock The clock to use for timings.
     */
    @SuppressWarnings("deprecation")
    public DatadogMeterRegistry(DatadogConfig config, Clock clock) {
        this(config, clock, DEFAULT_THREAD_FACTORY, null);
    }

    /**
     * @param config Configuration options for the registry that are describable as
     * properties.
     * @param clock The clock to use for timings.
     */
    @SuppressWarnings("deprecation")
    public DatadogMeterRegistry(DatadogConfig config, Clock clock, @Nullable StatsDClient customClient) {
        this(config, clock, DEFAULT_THREAD_FACTORY, customClient);
    }

    @SuppressWarnings({ "Var", "Varifier" })
    private DatadogMeterRegistry(DatadogConfig config, Clock clock, ThreadFactory threadFactory, @Nullable StatsDClient statsDClient) {
        super(config, clock);

        config().namingConvention(new DatadogNamingConvention());

        if (statsDClient == null) {

            NonBlockingStatsDClientBuilder builder = new NonBlockingStatsDClientBuilder();
            builder = builder.prefix(config.prefix());

            // performance tune-ables
            builder = builder.enableTelemetry(config.enableClientSideTelemetry())
                    .enableAggregation(config.enableAggregation());
            if (config.enableAggregation()) {
                builder = builder.aggregationFlushInterval(config.aggregationFlushInterval())
                        .aggregationShards(config.aggregationShards());
            }

            if (config.maxPacketSizeBytes() != -1) {
                builder = builder.maxPacketSizeBytes(config.maxPacketSizeBytes());
            }

            // where to report data comes from "DD_DOGSTATSD_URL" environment variable

            statsDClient = builder.build();
        }

        this.config = config;
        this.statsDClient = statsDClient;

        start(threadFactory);
    }

    public static Builder builder(DatadogConfig config) {
        return new Builder(config);
    }

    public static class Builder {

        private final DatadogConfig config;

        private Clock clock = Clock.SYSTEM;

        private ThreadFactory threadFactory = DEFAULT_THREAD_FACTORY;

        private StatsDClient statsDClient;

        @SuppressWarnings("deprecation")
        Builder(DatadogConfig config) {
            this.config = config;
        }

        public Builder clock(Clock clock) {
            this.clock = clock;
            return this;
        }

        public Builder threadFactory(ThreadFactory threadFactory) {
            this.threadFactory = threadFactory;
            return this;
        }

        public Builder statsDClient(StatsDClient statsDClient) {
            this.statsDClient = statsDClient;
            return this;
        }

        public DatadogMeterRegistry build() {
            return new DatadogMeterRegistry(config, clock, threadFactory, statsDClient);
        }

    }

    @Override
    public void start(ThreadFactory threadFactory) {
        logger.info("publishing metrics for Datadog");
        super.start(threadFactory);
    }

    @Override
    protected void publish() {
        for (Meter meter : getMeters()) {
            meter.match(this::writeMeterViaStatsd, // visitGauge
                    this::writeMeterViaStatsd, // visitCounter
                    this::writeTimerViaStatsd, // visitTimer
                    this::writeSummaryViaStatsd, // visitSummary
                    this::writeMeterViaStatsd, // visitLongTaskTimer
                    this::writeMeterViaStatsd, // visitTimeGauge
                    this::writeMeterViaStatsd, // visitFunctionCounter
                    this::writeTimerViaStatsd, // visitFunctionTimer
                    this::writeMeterViaStatsd // visitMeter
            );
        }
    }

    private Integer writeTimerViaStatsd(FunctionTimer timer) {
        Meter.Id id = timer.getId();

        // we can't know anything about max and percentiles originating from a function
        // timer
        timer.measure().forEach(measurement -> {writeMetricViaStatsd(id, "", measurement.getValue(), Statistic.VALUE);});
        return 3;
    }

    private Integer writeTimerViaStatsd(Timer timer) {
        Meter.Id id = timer.getId();

        // we can't know anything about max and percentiles originating from a function
        // timer
        timer.measure().forEach(measurement -> {writeMetricViaStatsd(id, "", measurement.getValue(), Statistic.VALUE);});
        return 3;
    }

    private Integer writeSummaryViaStatsd(DistributionSummary summary) {
        Meter.Id id = summary.getId();
        summary.measure().forEach(measurement -> {writeMetricViaStatsd(id, "", measurement.getValue(), Statistic.VALUE);});
        return 4;
    }

    private Integer writeMeterViaStatsd(Meter m) {
        long count = stream(m.measure().spliterator(), false).map(ms -> {
            Meter.Id id = m.getId().withTag(ms.getStatistic());
            writeMetricViaStatsd(id, null, ms.getValue(), ms.getStatistic());
            return 1;
        }).count();
        return (int) count;
    }

    // VisibleForTesting
    void writeMetricViaStatsd(Meter.Id id, @io.micrometer.common.lang.Nullable String suffix, double value, Statistic statistic) {
        Meter.Id fullId = id;
        if (suffix != null) {
            fullId = idWithSuffix(id, suffix);
        }

        Iterable<Tag> tags = getConventionTags(fullId);

        List<String> tagsList = tags.iterator().hasNext() ? stream(tags.spliterator(), false)
                .map(t -> "\"" + escapeJson(t.getKey()) + ":" + escapeJson(t.getValue()) + "\"")
                .toList() : new ArrayList<>();
        String[] tagsArray = new String[tagsList.size()];
        tagsList.toArray(tagsArray);

        String metricName = getConventionName(fullId);

        // Create type attribute
        switch (fullId.getType()) {
            case COUNTER:
                statsDClient.count(metricName, value, 1.0, tagsArray);
                break;
            case LONG_TASK_TIMER:
                // fall through
            case TIMER:
                // fall through
            case DISTRIBUTION_SUMMARY:
                statsDClient.distribution(metricName, value, 1.0, tagsArray);
                break;
            default:
                statsDClient.gauge(metricName, value, 1.0, tagsArray);
        }
    }

    private Meter.Id idWithSuffix(Meter.Id id, String suffix) {
        return id.withName(id.getName() + "." + suffix);
    }

    @Override
    public Counter newCounter(Meter.Id id) {
        return new CumulativeCounter(id);
    }

    @Override
    public DistributionSummary newDistributionSummary(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig, double scale) {
        return new CumulativeDistributionSummary(id, clock, distributionStatisticConfig, scale);
    }

    @Override
    protected Timer newTimer(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig, PauseDetector pauseDetector) {
        return new CumulativeTimer(id, clock, distributionStatisticConfig, pauseDetector, getBaseTimeUnit());
    }

    @Override
    protected <T> Gauge newGauge(Meter.Id id, @Nullable T obj, ToDoubleFunction<T> valueFunction) {
        return new DefaultGauge<>(id, obj, valueFunction);
    }

    @Override
    protected LongTaskTimer newLongTaskTimer(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig) {
        return new CumulativeHistogramLongTaskTimer(id, clock, getBaseTimeUnit(), distributionStatisticConfig);
    }

    @Override
    protected <T> FunctionTimer newFunctionTimer(Meter.Id id, T obj, ToLongFunction<T> countFunction, ToDoubleFunction<T> totalTimeFunction, TimeUnit totalTimeFunctionUnit) {
        return new CumulativeFunctionTimer<>(id, obj, countFunction, totalTimeFunction, totalTimeFunctionUnit, getBaseTimeUnit());
    }

    @Override
    protected <T> FunctionCounter newFunctionCounter(Meter.Id id, T obj, ToDoubleFunction<T> countFunction) {
        return new CumulativeFunctionCounter<>(id, obj, countFunction);
    }

    @Override
    protected Meter newMeter(Meter.Id id, Meter.Type type, Iterable<Measurement> measurements) {
        return new DefaultMeter(id, type, measurements);
    }

    @Override
    @NonNull
    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.MILLISECONDS;
    }

    @Override
    protected DistributionStatisticConfig defaultHistogramConfig() {
        return DistributionStatisticConfig.builder()
                .expiry(config.step())
                .build()
                .merge(DistributionStatisticConfig.DEFAULT);
    }
}