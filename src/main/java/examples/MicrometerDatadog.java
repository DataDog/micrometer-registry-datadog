package examples;


import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.Timer;
import io.micrometer.datadog.DatadogConfig;
import io.micrometer.datadog.DatadogMeterRegistry;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.ToDoubleFunction;

public class MicrometerDatadog {

    public static void main(String[] args) {
        DatadogConfig datadogConfig = new DatadogConfig() {
            @Override
            public String get(String key) {
                return null;
            }
            @Override
            public Duration step() {
                return Duration.ofSeconds(5);
            }

        };
        // Initialize registry
        DatadogMeterRegistry registry = new DatadogMeterRegistry(datadogConfig, Clock.SYSTEM);
        // Define tags (labels)
        ArrayList<Tag> tags = new ArrayList<>();
        tags.add(Tag.of("env","dev"));

        // Create counter
        Counter counter = registry.newCounter(Counter
                .builder("counter_example")
                .description("a description of what this counter does") // optional
                .tags(tags) // optional
                .register(registry).getId());
        // Increment your counter
        counter.increment();
        counter.increment(2);

        // Create DistributionSummary
        DistributionSummary summary = DistributionSummary
                .builder("summary_example")
                .description("a description of what this summary does") // optional
                .tags(tags) // optional
                .register(registry);
        // Record values to distributionSummary
        summary.record(10);
        summary.record(20);
        summary.record(30);
        // Output to Logz.io: summary_example_count, summary_example_max, summary_example_sum

        // Create Gauge
        List<String> cache = new ArrayList<>(4);
        // Track list size
        Gauge gauge = Gauge
                .builder("cache_size_gauge_example", cache, List::size)
                .tags(tags)
                .register(registry);
        cache.add("1");
        // Track map size
        Map<String, Integer> map_gauge = registry.gaugeMapSize("map_gauge_example", tags, new HashMap<>());
        map_gauge.put("key",1);
        // set value manually
        AtomicInteger manual_gauge = registry.gauge("manual_gauge_example", new AtomicInteger(0));
        manual_gauge.set(83);


        // Create Timer
        Timer timer = Timer
                .builder("timer_example")
                .description("a description of what this timer does") // optional
                .tags(tags) // optional
                .register(registry);
        // You can set a value manually
        timer.record(1500,TimeUnit.MILLISECONDS);
        // You can record the timing of a function
        timer.record(()-> {
            try {
                Thread.sleep(1500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });

        Runnable r = new Runnable() {
            @Override
            public void run() {
                while (true) {
                    counter.increment(1);
                    manual_gauge.set(42);
                    summary.record(10);

                    try {
                        Thread.sleep(1500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        };
        ExecutorService executor = Executors.newFixedThreadPool(1);
        executor.submit(r);


        try {
            Thread.sleep(10000000);
            executor.shutdown();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
