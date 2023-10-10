package io.micrometer.datadog;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import io.micrometer.core.instrument.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ru.lanwen.wiremock.ext.WiremockResolver;

import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

@ExtendWith(WiremockResolver.class)
class DatadogMeterRegistryTest {

    @Test
    void testTCPStatsdConfiguration(@WiremockResolver.Wiremock WireMockServer server) {
        Clock clock = new MockClock();
        DatadogMeterRegistry registry = new DatadogMeterRegistry(new DatadogConfig() {
            @Override
            public String get(String key) {
                return null;
            }

            @Override
            public boolean enabled() {
                return false;
            }
        }, clock);

        server.stubFor(any(anyUrl()));

        Counter.builder("my.counter#abc")
                .baseUnit(TimeUnit.MICROSECONDS.toString().toLowerCase())
                .description("metric description")
                .register(registry)
                .increment(Math.PI);
        registry.publish();

        // statsd will be fine under this test scenario, just verify we're not sending out
        // to the API.
        server.verify(0, RequestPatternBuilder.allRequests());

        registry.close();
    }

}
