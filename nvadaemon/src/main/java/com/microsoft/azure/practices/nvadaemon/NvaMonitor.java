package com.microsoft.azure.practices.nvadaemon;

import com.google.common.base.Preconditions;
import com.microsoft.azure.practices.nvadaemon.config.DaemonConfiguration;
import com.microsoft.azure.practices.nvadaemon.config.MonitorConfiguration;
import com.microsoft.azure.practices.nvadaemon.monitor.Monitor;
import com.microsoft.azure.practices.nvadaemon.monitor.ScheduledMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class NvaMonitor implements AutoCloseable {
    private final Logger log = LoggerFactory.getLogger(NvaMonitor.class);
    private final DaemonConfiguration configuration;
    private final ExecutorService executorService;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition shutdown = lock.newCondition();

    private volatile boolean isRunning = false;

    public static final class NvaMonitorException extends Exception {
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

    public NvaMonitor(DaemonConfiguration configuration) {
        this.configuration = Preconditions.checkNotNull(configuration,
            "configuration cannot be null");
        this.executorService =
            Executors.newSingleThreadExecutor();
    }

    private class ScheduledMonitorCallable<T extends ScheduledMonitor> extends MonitorCallable<T> {
//        public ScheduledMonitorCallable(T monitor,
//                                        MonitorConfiguration monitorConfiguration) {
//            super(monitor, monitorConfiguration);
        public ScheduledMonitorCallable(T monitor) {
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
        //protected MonitorConfiguration monitorConfiguration;

        //public MonitorCallable(T monitor, MonitorConfiguration monitorConfiguration) {
        public MonitorCallable(T monitor) {
            this.monitor = Preconditions.checkNotNull(monitor, "monitor cannot be null");
//            this.monitorConfiguration = Preconditions.checkNotNull(monitorConfiguration,
//                "monitorConfiguration cannot be null");
        }

        protected void await() throws InterruptedException {
            log.debug("MonitorCallable.await()");
            shutdown.await();
            log.debug("MonitorCallable.await() complete");
        }

        @Override
        public Void call() throws Exception {
            log.debug("Starting monitor task");
            //monitor.init(config.getAll());
            //monitor.init(this.monitorConfiguration);
            monitor.init();
            try {
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
            } finally {
                monitor.close();
            }

            log.info("Monitor task shutdown complete");
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Callable<Void> createMonitorCallable(MonitorConfiguration monitorConfiguration)
        throws NvaMonitorException {
        Preconditions.checkNotNull(monitorConfiguration, "monitorConfiguration cannot be null");
        String className = monitorConfiguration.getMonitorClass();

        Callable<Void> result = null;
        Exception innerException = null;
        try {
            Class<?> clazz = getClass()
                .getClassLoader()
                .loadClass(className);
            Constructor<?> ctor = clazz.getConstructor(MonitorConfiguration.class);
            if (ScheduledMonitor.class.isAssignableFrom(clazz)) {
                result = new ScheduledMonitorCallable(
                    //(ScheduledMonitor) ctor.newInstance(), monitorConfiguration);
                    (ScheduledMonitor) ctor.newInstance(monitorConfiguration));
            } else if (Monitor.class.isAssignableFrom(clazz)) {
                result = new MonitorCallable(
                    //(Monitor) ctor.newInstance(), monitorConfiguration);
                    (Monitor) ctor.newInstance(monitorConfiguration));
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

    public synchronized Future<Void> start() throws NvaMonitorException {
        Preconditions.checkState(!executorService.isShutdown(), "Already started");
        // We need to eventually support multiple monitors, but for now, just grab the
        // first one.
        MonitorConfiguration monitorConfiguration =
            this.configuration.getMonitors().get(0);
        Callable<Void> monitor = createMonitorCallable(monitorConfiguration);
        isRunning = true;
        Future<Void> task = executorService.submit(
            monitor
        );

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
    public synchronized void close() throws Exception {
        if (this.isRunning) {
            stop();
        }

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
