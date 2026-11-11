package com.devmate.agent.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompatibleModelPropertiesTest {
    @Test
    void disabledProviderRequiresNoEndpointAndEnabledProviderRequiresValidConfig() {
        CompatibleModelProperties properties = new CompatibleModelProperties();
        assertThatCode(properties::validate).doesNotThrowAnyException();
        properties.setEnabled(true);
        assertThatThrownBy(properties::validate).isInstanceOf(IllegalArgumentException.class);
        properties.setBaseUrl("https://compatible.example/v1");
        properties.setModels(List.of("test-model"));
        assertThatCode(properties::validate).doesNotThrowAnyException();
        for (String invalid : List.of("http://example.com/v1", "https://user:password@example.com/v1",
                "https://example.com/v1?key=secret", "https://example.com/v1#fragment")) {
            properties.setBaseUrl(invalid);
            assertThatThrownBy(properties::validate).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageNotContaining("password").hasMessageNotContaining("secret");
        }
        properties.setBaseUrl("https://compatible.example/v1");
        properties.setModels(List.of("duplicate", "duplicate"));
        assertThatThrownBy(properties::validate).isInstanceOf(IllegalArgumentException.class);
    }
}
