package com.microsoft.azure.practices.nvadaemon;

import com.google.common.base.Preconditions;
import com.microsoft.azure.practices.nvadaemon.config.ConfigurationException;
import com.microsoft.azure.practices.nvadaemon.config.NvaDaemonConfiguration;
import com.microsoft.azure.practices.nvadaemon.config.ZookeeperConfiguration;
import org.apache.commons.daemon.Daemon;
import org.apache.commons.daemon.DaemonContext;
import org.apache.commons.daemon.DaemonInitException;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class NvaDaemon implements Daemon {
    private static final Logger log = LoggerFactory.getLogger(NvaDaemon.class);
    private static final String ZK_NAMESPACE = "nvadaemon";
    private volatile boolean stopped;
    private NvaDaemonConfiguration configuration;
    private CuratorFramework client;
    private ReentrantLock lock = new ReentrantLock();
    private Condition shutdown = lock.newCondition();

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final AtomicReference<Future<?>> ourTask = new AtomicReference<>(null);

    public NvaDaemon() {
    }

    private void createClient() throws InterruptedException {
        log.debug("Creating CuratorFramework");
        ZookeeperConfiguration zookeeperConfiguration =
            this.configuration.getZookeeperConfiguration();
        client = CuratorFrameworkFactory.builder()
            .connectString(zookeeperConfiguration.getConnectionString())
            .retryPolicy(
                new ExponentialBackoffRetry(zookeeperConfiguration.getRetrySleepTime(),
                    zookeeperConfiguration.getNumberOfRetries()))
            .namespace(ZK_NAMESPACE)
            .build();
        log.debug("Starting CuratorFramework");
        client.start();
        log.debug("Waiting for Zookeeper connection");
        client.blockUntilConnected();
        log.debug("Connected to Zookeeper");
    }

    @Override
    public void init(DaemonContext daemonContext) throws DaemonInitException {
        Preconditions.checkNotNull(daemonContext, "daemonContext cannot be null");
        try {
            this.configuration = NvaDaemonConfiguration.parseArguments(
                daemonContext.getArguments());
        } catch (ConfigurationException e) {
            throw new DaemonInitException("Error processing command line arguments", e);
        }
    }

    @Override
    public void start() throws Exception {
        log.debug("Starting daemon thread");
        Preconditions.checkState(!executorService.isShutdown(), "Already started");
        createClient();
        internalStart();
    }

    private synchronized void internalStart() {
        if (ourTask.get() != null) {
            log.warn("internalStart called more than once");
            return;
        }

        Future<Void> task = executorService.submit(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                NvaLeaderSelectorListenerAdapter adapter = null;
                try {
                    adapter = new NvaLeaderSelectorListenerAdapter(
                        configuration, client);

                    log.debug("Starting leader selector adapter");
                    adapter.start();
                    log.debug("Leader selector adapter started");
                    while (!stopped) {
                        lock.lock();
                        try {
                            log.debug("Waiting on signal");
                            shutdown.awaitUninterruptibly();
                        } finally {
                            lock.unlock();
                        }
                    }
                } finally {
                    log.info("Closing leader selector adapter");
                    if (adapter != null) {
                        adapter.close();
                    }
                }

                log.info("Daemon task shutdown complete");
                return null;
            }
        });

        ourTask.set(task);
    }

    @Override
    public synchronized void stop() throws Exception {
        log.info("Stopping daemon");
        stopped = true;
        lock.lock();
        try {
            log.debug("Signalling condition");
            shutdown.signal();
            log.debug("Signalled condition");
        } finally {
            lock.unlock();
        }
        try {
            log.debug("Shutting down executorService");
            executorService.shutdown();
            // If this times out, force a shutdown.
            if (!executorService.awaitTermination(
                this.configuration.getDaemonConfiguration().getShutdownAwaitTime(),
                TimeUnit.MILLISECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            log.warn("Interrupted joining daemon thread: " + e.getMessage());
            Thread.currentThread().interrupt();
        } finally {
            log.debug("Closing CuratorFramework");
            this.client.close();
            log.debug("CuratorFramework closed");
        }

        log.info("Daemon stopped");
    }

    @Override
    public void destroy() {
        ourTask.set(null);
    }
}