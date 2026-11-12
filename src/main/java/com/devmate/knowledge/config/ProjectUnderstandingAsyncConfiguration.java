package com.devmate.knowledge.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class ProjectUnderstandingAsyncConfiguration {
    @Bean("projectUnderstandingTaskExecutor")
    public TaskExecutor projectUnderstandingTaskExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(2);
        executor.setThreadNamePrefix("devmate-understanding-");
        executor.initialize();
        return executor;
    }
}
