package com.devmate.review.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@ConfigurationProperties(prefix = "devmate.review-workflow")
public class ReviewWorkflowProperties {

    private Duration staleTimeout = Duration.ofMinutes(30);

    public Duration getStaleTimeout() {
        return staleTimeout;
    }

    public void setStaleTimeout(Duration staleTimeout) {
        this.staleTimeout = staleTimeout;
    }
}
