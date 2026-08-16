package com.quant.trade.marketdata.foundation.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 数据底座后台执行配置（Repair R1 §二.9）：线程池与轮询参数配置化。
 * worker.enabled=false（测试）时调度空转，执行仍可经 BackfillWorkerService.claimAndExecuteOne 直接驱动。
 */
@Configuration
@EnableScheduling
public class FoundationWorkerConfig {

    @Bean("dataFoundationWorkerExecutor")
    public ThreadPoolTaskExecutor dataFoundationWorkerExecutor(
            @Value("${qta.data-foundation.worker.concurrency:1}") int concurrency) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(Math.max(1, concurrency));
        executor.setMaxPoolSize(Math.max(1, concurrency));
        executor.setQueueCapacity(16);
        executor.setThreadNamePrefix("mdf-worker-");
        executor.initialize();
        return executor;
    }
}
