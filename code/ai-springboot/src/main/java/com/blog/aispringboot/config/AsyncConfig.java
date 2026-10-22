package com.blog.aispringboot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步提炼线程池与定时任务开关。
 *
 * <p>线程数刻意压得很低：提炼要调用一次大模型，并发太高既烧钱又容易触发限流。
 * 队列满时用 {@link ThreadPoolExecutor.CallerRunsPolicy}，宁可让调度线程自己跑，
 * 也不静默丢任务。
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

    public static final String MEMORY_EXECUTOR = "memoryExtractionExecutor";

    /** 向量库调用专用线程池：超时后由调用方放弃等待，不占用聊天线程 */
    public static final String VECTOR_EXECUTOR = "vectorStoreExecutor";

    @Bean(name = MEMORY_EXECUTOR)
    public ThreadPoolTaskExecutor memoryExtractionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("memory-extract-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /**
     * Milvus 调用隔离池。
     *
     * <p>Milvus SDK 在连接不可用时会自行重试几十次，调用线程可能被占用几分钟。
     * 这里不排队（SynchronousQueue）：池满时直接拒绝，让调用方立即降级，而不是
     * 把请求堆在队列里。线程数很小，避免故障时无限创建线程。
     */
    @Bean(name = VECTOR_EXECUTOR)
    public ThreadPoolTaskExecutor vectorStoreExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("vector-store-");
        executor.setDaemon(true);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}
