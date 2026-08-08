package com.testforge.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Hands work to a background executor from a service that wants to stay on the request thread.
 *
 * <h2>Why this exists</h2>
 * {@code @Async} is implemented with a proxy: the annotation only takes effect when the call arrives
 * from <em>outside</em> the bean. A service that annotates its own long-running method and then calls
 * {@code this.doWork()} bypasses the proxy entirely and runs it inline — silently. That failure mode is
 * particularly nasty because nothing errors: the endpoint simply blocks for the full duration of the
 * job, the "202 Accepted, poll for progress" contract becomes a lie, and the response arrives with the
 * work already finished. A load test configured for two minutes held its HTTP request open for two
 * minutes and returned COMPLETED, so there was never anything to poll and no way to stop it.
 *
 * <p>Routing the hand-off through this separate bean makes the call cross a proxy boundary, so the
 * executor is actually used. It deliberately takes a plain {@link Runnable} rather than referencing the
 * calling services, which keeps it free of the circular dependency that a per-service runner would
 * introduce.
 *
 * <p>The two methods target different pools on purpose: a long load test must not occupy a worker that
 * queued analysis jobs are waiting on, and vice versa.
 */
@Component
public class AsyncJobLauncher {

    private static final Logger log = LoggerFactory.getLogger(AsyncJobLauncher.class);

    /** Runs an analysis pipeline job on the analysis pool. */
    @Async("analysisExecutor")
    public void launchAnalysisJob(String jobName, Runnable job) {
        execute(jobName, job);
    }

    /** Runs a load-test job on the dedicated load-test pool. */
    @Async("loadTestExecutor")
    public void launchLoadTestJob(String jobName, Runnable job) {
        execute(jobName, job);
    }

    private void execute(String jobName, Runnable job) {
        try {
            job.run();
        } catch (Exception ex) {
            // The job is responsible for persisting its own FAILED state — there is no HTTP response left
            // to report through. This is a last-resort log so a bug in that handling is still visible.
            log.error("Background job '{}' threw an unhandled exception: {}", jobName, ex.getMessage(), ex);
        }
    }
}
