package com.microsoft.azure.practices.nvadaemon;

import com.google.common.base.Preconditions;
import com.microsoft.azure.practices.nvadaemon.monitor.Monitor;
import com.microsoft.azure.practices.nvadaemon.monitor.ScheduledMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class NvaMonitor implements Closeable {
    private final Logger log = LoggerFactory.getLogger(NvaMonitor.class);
    private final NvaDaemonConfig config;
    private final ExecutorService executorService;
    //private final AtomicReference<Future<?>> ourTask = new AtomicReference<>(null);
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition shutdown = lock.newCondition();

    private volatile boolean isRunning = false;

    public final class NvaMonitorException extends Exception {
        public NvaMonitorException() {
            super();
        }

        public NvaMonitorException(String message) {
            super(message);
        }

        public NvaMonitorException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    public NvaMonitor(NvaDaemonConfig config) {
        Preconditions.checkNotNull(config, "config cannot be null");
        this.config = config;
        this.executorService =
            Executors.newSingleThreadExecutor();
    }

    private class ScheduledMonitorCallable extends MonitorCallable<ScheduledMonitor> {
        public ScheduledMonitorCallable(ScheduledMonitor monitor) {
            super(monitor);
        }

        @Override
        protected void await() throws InterruptedException
        {
            log.debug("ScheduledMonitorCallable.await()");
            if (!shutdown.await(monitor.getTime(), monitor.getUnit())) {
                log.debug("Probe waiting time elapsed.  Looping");
                if (!monitor.probe()) {
                    monitor.execute();
                }
            } else {
                log.debug("Monitor shutdown signal received");
            }

            log.debug("ScheduledMonitorCallable.await() complete");
        }
    }

    private class MonitorCallable<T extends Monitor> implements Callable<Void> {
        protected T monitor;

        public MonitorCallable(T monitor) {
            Preconditions.checkNotNull(monitor, "monitor cannot be null");
            this.monitor = monitor;
        }

        protected void await() throws InterruptedException {
            log.debug("MonitorCallable.await()");
            shutdown.await();
            log.debug("MonitorCallable.await() complete");
        }

        @Override
        public Void call() throws Exception {
            log.debug("Starting monitor task");
            //ScheduledMonitor monitor = new ProbeMonitor2();
            try {
                monitor.init(config.getAll());
                while (isRunning) {
                    lock.lock();
                    try {
                        log.debug("Waiting on signal");
                        await();
                    } catch (InterruptedException e) {
                        log.warn("Monitor lock interrupted", e);
                        Thread.currentThread().interrupt();
                    } finally {
                        lock.unlock();
                    }
                }
//            } catch (Exception e) {
//                log.error("Exception in monitor: ", e);
            } finally {
                monitor.close();
            }

            log.info("Monitor task shutdown complete");
            return null;
        }
    }

    // We probably need a custom exception type.
    private Callable<Void> createMonitorCallable(String className) throws NvaMonitorException {
        Preconditions.checkNotNull(className, "className cannot be null");
        // We are going to assume this is driven by config so we will create it with reflection.
        // We may need to change this later.
        Callable<Void> result = null;
        Exception innerException = null;
        try {
            Class<?> clazz = getClass()
                .getClassLoader()
                .loadClass(className);
            Constructor<?> ctor = clazz.getConstructor();
            if (ScheduledMonitor.class.isAssignableFrom(clazz)) {
                result = new ScheduledMonitorCallable((ScheduledMonitor) ctor.newInstance());
            } else if (Monitor.class.isAssignableFrom(clazz)) {
                result = new MonitorCallable((Monitor) ctor.newInstance());
            } else {
                innerException = new ClassCastException(
                    "Class " + className + " does not implement Monitor or ScheduledMonitor");
            }
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            log.error("Could not create instance of " + className, e);
            innerException = e;
        } catch (ClassNotFoundException e) {
            log.error("Class " + className + " was not found", e);
            innerException = e;
        } catch (NoSuchMethodException e) {
            log.error("No default constructor found for " + className, e);
            innerException = e;
        }

        if (innerException != null) {
            throw new NvaMonitorException("Error creating monitor type: " + className, innerException);
        }

        return result;
    }

    //public synchronized void start() throws Exception {
    public synchronized Future<Void> start() throws NvaMonitorException {
        Preconditions.checkState(!executorService.isShutdown(), "Already started");
        Callable<Void> monitor =
            //createMonitorCallable("com.microsoft.azure.practices.nvadaemon.ProbeMonitor");
            createMonitorCallable(config.getMonitorClass());
        isRunning = true;
        Future<Void> task = executorService.submit(
            monitor
        );
        //ourTask.set(task);
        return task;
    }

    public synchronized void stop() {
        log.info("Stopping NvaMonitor task");
        isRunning = false;
        lock.lock();
        try {
            log.debug("Signalling NvaMonitor condition");
            shutdown.signal();
            log.debug("Signalled NvaMonitor condition");
        } finally {
            lock.unlock();
        }
    }

    @Override
    public synchronized void close() throws IOException {
        if (this.isRunning) {
            stop();
        }

        //ourTask.set(null);
        executorService.shutdown();
        try {
            executorService.awaitTermination(10000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            log.warn("Interrupted waiting for NvaMonitor task", e);
            Thread.currentThread().interrupt();
        }

        log.info("NvaMonitor task stopped");
    }
}
