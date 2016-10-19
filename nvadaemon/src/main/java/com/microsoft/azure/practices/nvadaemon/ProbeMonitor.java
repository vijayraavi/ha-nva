package com.microsoft.azure.practices.nvadaemon;

import com.google.common.base.Preconditions;
import org.apache.curator.utils.CloseableExecutorService;
import org.apache.curator.utils.ThreadUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class ProbeMonitor implements Closeable {
    private final Logger LOG = LoggerFactory.getLogger(getClass());
    private final CloseableExecutorService executorService;
    private final AtomicReference<Future<?>> ourTask = new AtomicReference<>(null);
    private static final ThreadFactory defaultThreadFactory =
        ThreadUtils.newThreadFactory("ProbeMonitor");
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition shutdown = lock.newCondition();

    private final int probeDelay = 3000;
    private volatile boolean isRunning = false;

    public ProbeMonitor() {
        this.executorService =
            new CloseableExecutorService(Executors.newSingleThreadExecutor(defaultThreadFactory), true);
    }

    public synchronized void start() {
        Preconditions.checkState(!executorService.isShutdown(), "Already started");
        isRunning = true;
        Future<Void> task = executorService.submit(new Callable<Void>() {
            private int failures = 0;

            private boolean probe() {
                return ++failures < 5;
            }

            private void execute() {
                LOG.info("Probe failure.  Executing failure action.");
                failures = 0;
                // Simulate long running action
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    LOG.error("Long running execute interrupted");
                }
            }

            @Override
            public Void call() throws Exception {
                LOG.debug("Starting monitor task");
                while (isRunning) {
                    lock.lock();
                    try {
                        LOG.debug("Waiting on signal");
                        //shutdown.await();
                        if (!shutdown.await(probeDelay, TimeUnit.MILLISECONDS)) {
                            LOG.debug("Probe waiting time elapsed.  Looping");
                            if (!probe()) {
                                execute();
                            }
                        } else {
                            LOG.debug("Monitor shutdown signal received");
                        }
                    } catch (InterruptedException e) {
                        LOG.warn("Monitor lock interrupted", e);
                    } finally {
                        lock.unlock();
                    }
                }

                LOG.info("Monitor task shutdown complete");
                return null;
            }
        });
        ourTask.set(task);
    }

    @Override
    public synchronized void close() throws IOException {
        LOG.info("Stopping monitor task");
        isRunning = false;
        lock.lock();
        try {
            LOG.debug("Signalling monitor condition");
            shutdown.signal();
            LOG.debug("Signalled monitor condition");
        } finally {
            lock.unlock();
        }

        try {
            Future<?> task = ourTask.get();
            if (task != null) {
                LOG.debug("Waiting for monitor task completion");
                try {
                    task.get();
                } catch (ExecutionException e) {
                    LOG.warn("Exception waiting for monitor task to complete: " + e.getMessage());
                }
                LOG.debug("Monitor task completed");
                ourTask.set(null);
            }
            executorService.close();
        } catch (InterruptedException e) {
            LOG.warn("Interrupted waiting for monitor task: ");
            e.printStackTrace();
        }
    }
}
