package com.microsoft.azure.practices.nvadaemon;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.leader.LeaderSelector;
import org.apache.curator.framework.recipes.leader.LeaderSelectorListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class NvaLeaderSelectorListenerAdapter
    extends LeaderSelectorListenerAdapter implements Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(NvaLeaderSelectorListenerAdapter.class);
    private final LeaderSelector leaderSelector;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition shutdown = lock.newCondition();
    private final CountDownLatch canShutdown = new CountDownLatch(1);

    public NvaLeaderSelectorListenerAdapter(CuratorFramework client, String path) {
        leaderSelector = new LeaderSelector(client, path, this);
        leaderSelector.autoRequeue();
    }

    public void start() {
        LOG.debug("NvaLeaderSelectorListenerAdapter::start()");
        leaderSelector.start();
        LOG.debug("NvaLeaderSelectorListenerAdapter::start() complete");
    }

    public void close() {
        LOG.debug("NvaLeaderSelectorListenerAdapter::close()");
        lock.lock();
        try {
            shutdown.signal();
        } finally {
            lock.unlock();
        }

        try {
            LOG.debug("Waiting for takeLeadership() to complete");
            canShutdown.await();
        } catch (InterruptedException e) {
            LOG.debug("canShutdown interrupted: " + e);

        }
        leaderSelector.close();
        LOG.debug("NvaLeaderSelectorListenerAdapter::close() complete");
    }

    @Override
    public void takeLeadership(CuratorFramework curatorFramework) throws Exception {
        // We are the leader now, so we need to swap over the DNS entry or whatever
        // and not relinquish control until we are interrupted (i.e. we are being
        // preempted as the leader).
        // We may need to print some debugging info every X minutes or so saying we
        // are still the leader, but I think ZK will take care of that.
        ProbeMonitor monitor = new ProbeMonitor();
        lock.lock();
        try {
            LOG.info("Leadership acquired");
            LOG.info("Starting monitor");
            monitor.start();
            shutdown.await();
            LOG.debug("Shutdown condition signalled");
        } catch (InterruptedException e) {
            LOG.info("takeLeadership interrupted");
            Thread.currentThread().interrupt();
        } finally {
            lock.unlock();
            monitor.close();
        }

        canShutdown.countDown();
    }
}
