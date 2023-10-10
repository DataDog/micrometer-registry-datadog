package io.micrometer.datadog;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DatadogConfigTest {

    private final Map<String, String> props = new HashMap<>();

    private final DatadogConfig config = props::get;

    @Test
    void valid() {
        assertThat(config.validate().failures().stream().count()).isEqualTo(0);
    }

}
