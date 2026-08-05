package com.devmate.review.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "devmate.review-evaluation")
public class ReviewEvaluationProperties {

    private int maxCasesPerReview = 100;
    private int maxRunsReturned = 100;

    public int getMaxCasesPerReview() {
        return maxCasesPerReview;
    }

    public void setMaxCasesPerReview(int maxCasesPerReview) {
        this.maxCasesPerReview = maxCasesPerReview;
    }

    public int getMaxRunsReturned() {
        return maxRunsReturned;
    }

    public void setMaxRunsReturned(int maxRunsReturned) {
        this.maxRunsReturned = maxRunsReturned;
    }
}
