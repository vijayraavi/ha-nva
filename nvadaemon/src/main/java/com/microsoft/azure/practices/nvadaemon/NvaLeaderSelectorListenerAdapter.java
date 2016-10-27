package com.microsoft.azure.practices.nvadaemon;

import com.google.common.base.Preconditions;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.leader.LeaderSelector;
import org.apache.curator.framework.recipes.leader.LeaderSelectorListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class NvaLeaderSelectorListenerAdapter
    extends LeaderSelectorListenerAdapter implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(NvaLeaderSelectorListenerAdapter.class);
    private final LeaderSelector leaderSelector;
    private final NvaDaemonConfig config;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition shutdown = lock.newCondition();
    private final Condition canShutdown = lock.newCondition();
    private volatile boolean isRunning = false;

    public NvaLeaderSelectorListenerAdapter(NvaDaemonConfig config, CuratorFramework client) {
        super();
        Preconditions.checkNotNull(config, "config cannot be null");
        Preconditions.checkNotNull(client, "client cannot be null");
        this.config = config;
        leaderSelector = new LeaderSelector(client, config.getLeaderSelectorPath(), this);
    }

    public synchronized void start() {
        log.debug("NvaLeaderSelectorListenerAdapter::start()");
        isRunning = true;
        leaderSelector.start();
        log.debug("NvaLeaderSelectorListenerAdapter::start() complete");
    }

    public synchronized void close() {
        log.debug("NvaLeaderSelectorListenerAdapter::close()");
        isRunning = false;
        lock.lock();
        try {
            shutdown.signal();
            canShutdown.await();
        } catch (InterruptedException e) {
            log.warn("NvaLeaderSelectorListenerAdapter::close interrupted");
            Thread.currentThread().interrupt();
        } finally {
            lock.unlock();
            leaderSelector.close();
        }

        log.debug("NvaLeaderSelectorListenerAdapter::close() complete");
    }

    @Override
    public void takeLeadership(CuratorFramework curatorFramework) {
        // We are the leader now, so we need to swap over the DNS entry or whatever
        // and not relinquish control until we are interrupted (i.e. we are being
        // preempted as the leader).
        // We may need to print some debugging info every X minutes or so saying we
        // are still the leader, but I think ZK will take care of that.
        NvaMonitor nvaMonitor = new NvaMonitor(config);
        lock.lock();
        try {
            log.info("Leadership acquired");
            log.info("Starting nvaMonitor");
            nvaMonitor.start();
            shutdown.await();
            log.debug("Shutdown condition signalled");
        } catch (InterruptedException e) {
            log.info("takeLeadership interrupted");
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            // This should happen when there is an exception starting the nvaMonitor.  We need to
            // relinquish leadership so someone else can try.  However, if this is a configuration
            // issue, it will just bounce from leader to leader, so we need to log it.
            log.error("Error starting NvaMonitor", e);
        } finally {
            lock.unlock();
            try {
                nvaMonitor.close();
            } catch (IOException e) {
                log.error("Error closing nvaMonitor", e);
            }
        }

        if (isRunning) {
            if (!leaderSelector.requeue()) {
                log.warn("Error re-queuing selector");
            }
        } else {
            // Signal that we can shutdown
            log.debug("canShutdown.signal()");
            lock.lock();
            try {
                canShutdown.signal();
            } finally {
                lock.unlock();
            }

            log.debug("canShutdown.signal() complete");
        }
    }
}
