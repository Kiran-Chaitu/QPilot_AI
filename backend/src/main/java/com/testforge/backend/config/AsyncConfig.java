package com.testforge.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;

/**
 * Central home for background-processing infrastructure. Long-running work (chunked-upload
 * assembly/extraction, AI analysis pipeline runs, future git-clone/crawl jobs) must never block
 * an HTTP request thread — each such job is submitted to one of the dedicated executors below and
 * the controller returns immediately with a "processing" status that the frontend polls.
 *
 * Two separate pools are used deliberately: upload/extraction work is CPU/I-O bound (disk-heavy),
 * while AI analysis work is network-bound (waiting on the AI provider). Keeping them separate means
 * a burst of large uploads can't starve in-flight AI analysis jobs, and vice versa.
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig implements AsyncConfigurer {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    @Bean(name = "uploadProcessingExecutor")
    public Executor uploadProcessingExecutor() {
        return buildExecutor("upload-proc-", 2, 8, 50);
    }

    @Bean(name = "analysisExecutor")
    public Executor analysisExecutor() {
        return buildExecutor("analysis-", 2, 6, 50);
    }

    /** Default executor for any @Async method that doesn't name a specific pool. */
    @Override
    public Executor getAsyncExecutor() {
        return buildExecutor("async-", 2, 4, 25);
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        // @Async void methods swallow exceptions by default unless a handler is registered; this
        // is a last-resort safety net logger only — the async methods themselves are responsible
        // for catching failures and persisting a FAILED status so the frontend can reflect it.
        return (Throwable ex, Method method, Object... params) ->
                log.error("Uncaught exception in async method {}: {}", method.getName(), ex.getMessage(), ex);
    }

    private ThreadPoolTaskExecutor buildExecutor(String prefix, int core, int max, int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix(prefix);
        executor.setCorePoolSize(core);
        executor.setMaxPoolSize(max);
        executor.setQueueCapacity(queueCapacity);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
