package com.trading.core.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class AsyncConfig {

    @Bean(name = "eventSenderExecutor")
    public Executor eventSenderExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // Core pool size: Keep a few threads ready for normal load
        executor.setCorePoolSize(4);
        // Max pool size: Handle bursts
        executor.setMaxPoolSize(20);
        // Queue capacity: Buffer events if the admin service is momentarily slow
        executor.setQueueCapacity(5000);
        // Thread name prefix for easier debugging
        executor.setThreadNamePrefix("EventSender-");
        // Reject policy: Instead of dropping, run on caller thread (slows down engine slightly rather than losing data, though ideally we don't want to block engine either. CallerRuns is a trade-off. If queue is full, the engine thread will block to process it.)
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
