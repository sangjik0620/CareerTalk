package com.careertalk.interview.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "analysisExecutor")
    public Executor analysisExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(4);      // 평상시 유지 스레드
        exec.setMaxPoolSize(8);       // 피크 시 최대
        exec.setQueueCapacity(200);   // 대기열
        exec.setThreadNamePrefix("analysis-");
        exec.initialize();
        return exec;
    }
}