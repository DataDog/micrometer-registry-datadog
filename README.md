## Send Custom metrics from your Java application to Datadog using Micrometer
A [Micrometer metrics](https://micrometer.io/) registry for sending dimensional metrics to Datadog.

This registry can be used by any application that uses micrometer for recording metrics.

## Usage:

**Note:** you will need java 8 or above

#### Via maven:

```xml
<dependency>
    <groupId>com.datadoghq.micrometer</groupId>
    <artifactId>micrometer-registry-datadog</artifactId>
    <version>1.0.0</version>
</dependency>
```

#### Via gradle groovy:
```groovy
implementation 'com.datadoghq.micrometer:micrometer-registry-datadog:1.0.0'
```

#### Via gradle Kotlin:
```kotlin
implementation("com.datadoghq.micrometer:micrometer-registry-datadog:1.0.0")
```

#### Import in your package:
```java
import io.micrometer.datadog.DatadogConfig;
import io.micrometer.datadog.DatadogMeterRegistry;
```

## Quick start:

Replace the placeholders in the code (indicated by the double angle brackets `<< >>`) to match your specifics.

| Environment variable | Description | Required/Default |
|-|---|------------------|
|DD_DOGSTATSD_URL|  The URL to use to connect the Datadog agent for Dogstatsd metrics. The url can start with `udp://` to connect using UDP or with `unix://` to use a Unix Domain Socket. | Required         |

#### In your package:
```java
package your_package;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.Timer;
import io.micrometer.datadog.DatadogConfig;
import io.micrometer.datadog.DatadogMeterRegistry;

class MicrometerDatadog {

   public static void main(String[] args) {
       // initialize config
     DatadogConfig datadogConfig = new DatadogConfig() {
         @Override
         public String get(String key) {
            return null;
         }
         
         @Override
         public Duration step() {
           return Duration.ofSeconds(<<interval>>);
           // example:
           // return Duration.ofSeconds(30);                    
         }
         
      };
      // Initialize registry
     DatadogMeterRegistry registry = new DatadogMeterRegistry(datadogConfig, Clock.SYSTEM);
       // Define tags (labels)
       ArrayList<Tag> tags = new ArrayList<>();
       tags.add(Tag.of("env","dev-micrometer"));

      // Create counter
      Counter counter = Counter
              .builder("counter_example")
              .description("a description of what this counter does") // optional
              .tags(tags) // optional
              .register(registry);
      // Increment your counter
      counter.increment(); 
      counter.increment(2); 
   }
}
```

## Meter binders
Micrometer provides a set of binders for monitoring JVM metrics out of the box, for example:
```java
// Initialize registry
DatadogMeterRegistry registry = new DatadogMeterRegistry(datadogConfig, Clock.SYSTEM);

// Gauges buffer and memory pool utilization
new JvmMemoryMetrics().bindTo(registry);
// Gauges max and live data size, promotion and allocation rates, and times GC pauses
new JvmGcMetrics().bindTo(registry);
// Gauges current CPU total and load average.
new ProcessorMetrics().bindTo(registry);
// Gauges thread peak, number of daemon threads, and live threads
new JvmThreadMetrics().bindTo(registry);
// Gauges loaded and unloaded classes
new ClassLoaderMetrics().bindTo(registry);

// File descriptor metrics gathered by the JVM
new FileDescriptorMetrics(tags).bindTo(registry);
// Gauges The uptime and start time of the Java virtual machine
new UptimeMetrics(tags).bindTo(registry);

// Counter of logging events
new LogbackMetrics().bindTo(registry);
new Log4j2Metrics().bindTo(registry);
```
For more information about other binders check out [Micrometer-core](https://github.com/micrometer-metrics/micrometer/tree/main/micrometer-core/src/main/java/io/micrometer/core/instrument/binder) Github repo.

## Types of metrics

Refer to the Micrometer [documentation](https://micrometer.io/docs/concepts) for more details.


| Name | Behavior | 
| ---- | ---------- | 
| Counter           | Metric value can only go up or be reset to 0, calculated per `counter.increment(value); ` call. |
| Gauge             | Metric value can arbitrarily increment or decrement, values can set automaticaly by tracking `Collection` size or set manually by `gauge.set(value)`  | 
| DistributionSummary | Metric values captured by the `summary.record(value)` function, the output is a distribution of `count`,`sum` and `max` for the recorded values during the push interval. |
| Timer       | Mesures timing, metric values can be recorded by `timer.record()` call. |

## Change log

- **1.0.0**:
    - Initial release.


