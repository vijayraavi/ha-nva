package com.microsoft.azure.practices.nvadaemon;

import com.google.common.base.Preconditions;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.leader.LeaderSelector;
import org.apache.curator.framework.recipes.leader.LeaderSelectorListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import com.microsoft.azure.practices.nvadaemon.NvaMonitor.NvaMonitorException;

public class NvaLeaderSelectorListenerAdapter
    extends LeaderSelectorListenerAdapter implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(NvaLeaderSelectorListenerAdapter.class);
    private final LeaderSelector leaderSelector;
    private final NvaDaemonConfig config;
    //private final ReentrantLock lock = new ReentrantLock();
    //private final Condition shutdown = lock.newCondition();
    //private final Condition canShutdown = lock.newCondition();
    private final AtomicReference<Future<Void>> ourTask = new AtomicReference<>(null);
    private final NvaMonitor nvaMonitor;
    private volatile boolean isRunning = false;

    public NvaLeaderSelectorListenerAdapter(NvaDaemonConfig config, CuratorFramework client) {
        super();
        Preconditions.checkNotNull(config, "config cannot be null");
        Preconditions.checkNotNull(client, "client cannot be null");
        this.config = config;
        leaderSelector = new LeaderSelector(client, config.getLeaderSelectorPath(), this);
        leaderSelector.autoRequeue();
        this.nvaMonitor = new NvaMonitor(this.config);
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
//        Future<Void> task = ourTask.get();
//        if (task != null) {
//            task.cancel(true);
//        }
        try {
            nvaMonitor.close();
        } catch (IOException e) {
            log.error("Error closing nvaMonitor", e);
        }
//        lock.lock();
//        try {
//            //shutdown.signal();
//            canShutdown.await();
//        } catch (InterruptedException e) {
//            log.warn("NvaLeaderSelectorListenerAdapter::close interrupted");
//            Thread.currentThread().interrupt();
//        } finally {
//            lock.unlock();
            leaderSelector.close();
//        }

        log.debug("NvaLeaderSelectorListenerAdapter::close() complete");
    }

    @Override
    public void takeLeadership(CuratorFramework curatorFramework) throws Exception {
        // We are the leader now, so we need to swap over the DNS entry or whatever
        // and not relinquish control until we are interrupted (i.e. we are being
        // preempted as the leader).
        // We may need to print some debugging info every X minutes or so saying we
        // are still the leader, but I think ZK will take care of that.
//        NvaMonitor nvaMonitor = new NvaMonitor(config);
        //lock.lock();
        log.info("Leadership acquired");
        try {
            log.info("Starting nvaMonitor");
            // See if we can catch any exceptions from our monitor.
            Future<Void> task = nvaMonitor.start();
            ourTask.set(task);
            task.get();
            //shutdown.await();
            log.debug("task.get() returned");
        } catch (InterruptedException e) {
            log.info("takeLeadership interrupted");
            Thread.currentThread().interrupt();
            throw e;
        } catch (NvaMonitorException e){
            log.error("Error creating NvaMonitor", e);
            throw e;
        } catch (ExecutionException e) { //catch (Exception e) {
            // This should happen when there is an exception starting the nvaMonitor.  We need to
            // relinquish leadership so someone else can try.  However, if this is a configuration
            // issue, it will just bounce from leader to leader, so we need to log it.
            log.error("Error executing NvaMonitor", e);
            throw e;
        } finally {
            //lock.unlock();
//            try {
//                nvaMonitor.close();
//            } catch (IOException e) {
//                log.error("Error closing nvaMonitor", e);
//            }
            this.nvaMonitor.stop();
        }

//        if (isRunning) {
//            if (!leaderSelector.requeue()) {
//                log.warn("Error re-queuing selector");
//            }
//        } else {
//            // Signal that we can shutdown
//            log.debug("canShutdown.signal()");
//            lock.lock();
//            try {
//                canShutdown.signal();
//            } finally {
//                lock.unlock();
//            }
//
//            log.debug("canShutdown.signal() complete");
//        }
    }
}
