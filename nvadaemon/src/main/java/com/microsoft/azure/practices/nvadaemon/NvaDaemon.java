package com.microsoft.azure.practices.nvadaemon;

import com.google.common.base.Preconditions;
import org.apache.commons.daemon.Daemon;
import org.apache.commons.daemon.DaemonContext;
import org.apache.commons.daemon.DaemonInitException;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.utils.CloseableExecutorService;
import org.apache.curator.utils.ThreadUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class NvaDaemon implements Daemon {
    private static final Logger LOG = LoggerFactory.getLogger(NvaDaemon.class);
    private static final String ZK_NAMESPACE = "nvadaemon";
    private volatile boolean stopped;
    private NvaDaemonConfig config;
    private CuratorFramework client;
    private ReentrantLock lock = new ReentrantLock();
    private Condition shutdown = lock.newCondition();

    private final CloseableExecutorService executorService;
    private final AtomicReference<Future<?>> ourTask = new AtomicReference<>(null);
    private static final ThreadFactory defaultThreadFactory = ThreadUtils.newThreadFactory("NvaDaemon");

    public NvaDaemon(NvaDaemonConfig config) {
        Preconditions.checkNotNull(config, "config cannot be null");
        this.config = config;
        this.executorService =
            new CloseableExecutorService(Executors.newSingleThreadExecutor(defaultThreadFactory), true);
    }

    private void createClient(NvaDaemonConfig config) throws InterruptedException {
        LOG.debug("Creating CuratorFramework");
        client = CuratorFrameworkFactory.builder()
            .connectString(config.getConnectionString())
            .retryPolicy(new ExponentialBackoffRetry(1000, 10))
            .namespace(ZK_NAMESPACE)
            .build();
        LOG.debug("Starting CuratorFramework");
        client.start();
        LOG.debug("Waiting for Zookeeper connection");
        client.blockUntilConnected();
        LOG.debug("Connected to Zookeeper");
    }

    @Override
    public void init(DaemonContext daemonContext) throws InterruptedException, DaemonInitException {
    }

    @Override
    public void start() throws Exception {
        LOG.debug("Starting daemon thread");
        Preconditions.checkState(!executorService.isShutdown(), "Already started");
        createClient(config);
        internalStart();
    }

    private synchronized void internalStart() {
        Future<Void> task = executorService.submit(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                NvaLeaderSelectorListenerAdapter adapter = null;
                try {
                    adapter = new NvaLeaderSelectorListenerAdapter(client, "/leader-election");
                    LOG.debug("Starting leader selector adapter");
                    adapter.start();
                    LOG.debug("Leader selector adapter started");
                    while (!stopped) {
                        lock.lock();
                        try {
                            LOG.debug("Waiting on signal");
                            shutdown.await();
                        } catch (InterruptedException e) {
                            LOG.warn("lock interrupted", e);
                        } finally {
                            lock.unlock();
                        }
                    }
                } finally {
                    LOG.info("Closing leader selector adapter");
                    if (adapter != null) {
                        adapter.close();
                    }
                }

                LOG.info("Daemon task shutdown complete");
                //countDownLatch.countDown();
                return null;
            }
        });

        ourTask.set(task);
    }

    @Override
    public synchronized void stop() throws Exception {
        LOG.info("Stopping daemon");
        stopped = true;
        lock.lock();
        try {
            LOG.debug("Signalling condition");
            shutdown.signal();
            LOG.debug("Signalled condition");
        } finally {
            lock.unlock();
        }
        try {
            LOG.debug("Joining daemon thread");
            Future<?> task = ourTask.get();
            if (task != null) {
                LOG.debug("Waiting for task completion");
                task.get();
                LOG.debug("Task completed");
                ourTask.set(null);
            }
            executorService.close();
        } catch (InterruptedException e) {
            LOG.warn("Interrupted joining daemon thread: " + e.getMessage());
            throw e;
        }

        LOG.debug("Closing CuratorFramework");
        client.close();
        LOG.debug("CuratorFramework closed");
        LOG.info("Daemon stopped");
    }

    @Override
    public void destroy() {
    }
}