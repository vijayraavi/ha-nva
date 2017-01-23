package com.microsoft.azure.practices.nvadaemon;

import com.google.common.base.Preconditions;
import org.apache.commons.daemon.Daemon;
import org.apache.commons.daemon.DaemonContext;
import org.apache.commons.daemon.DaemonController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);
    private static final ReentrantLock lock = new ReentrantLock();
    private static final Condition shutdown = lock.newCondition();
    private static final Condition shutdownComplete = lock.newCondition();

    private void runDaemon(Daemon daemon, DaemonContext context) throws Exception {
        Preconditions.checkNotNull(daemon, "daemon cannot be null");
        Preconditions.checkNotNull(context, "context cannot be null");
        daemon.init(context);
        try {
            daemon.start();
            log.info("Waiting for shutdown condition");
            lock.lock();
            try {
                shutdown.awaitUninterruptibly();
            } finally {
                lock.unlock();
            }

            log.info("Shutdown signal received");
        } catch (Exception e) {
            log.error("Unexpected exception, exiting abnormally", e);
            throw e;
        } finally {
            daemon.stop();
            daemon.destroy();
        }
    }

    public static void main(final String[] args) {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable)-> {
            log.error("Uncaught exception", throwable);
        });

        Runtime.getRuntime().addShutdownHook(new Thread(
            () -> {
                lock.lock();
                try {
                    log.info("JVM is shutting down.");
                    // If there are not any waiters, it means there was an exception
                    // initializing/starting the daemon, so we will deadlock if we await.
                    if (lock.hasWaiters(shutdown)) {
                        shutdown.signal();
                        log.info("Sent shutdown signal");
                        shutdownComplete.awaitUninterruptibly();
                        log.info("Main shutdown complete.");
                    }
                } finally {
                    lock.unlock();
                }
        }));
        DaemonContext context = new DaemonContext() {
            public DaemonController getController() {
                return null;
            }

            public String[] getArguments() {
                return args;
            }
        };

        Daemon daemon = new NvaDaemon();
        Main main = new Main();
        try {
            main.runDaemon(daemon, context);
        } catch (Exception e) {
            log.error("Error running daemon", e);
        } finally {
            lock.lock();
            try {
                shutdownComplete.signal();
            } finally {
                lock.unlock();
            }
        }
    }
}
