package com.microsoft.azure.practices.nvadaemon;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.leader.LeaderSelector;
import org.apache.curator.framework.recipes.leader.LeaderSelectorListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;

public class NvaLeaderSelectorListenerAdapter
    extends LeaderSelectorListenerAdapter implements Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(NvaLeaderSelectorListenerAdapter.class);
    //private final CuratorFramework client;
    private final LeaderSelector leaderSelector;

    public NvaLeaderSelectorListenerAdapter(CuratorFramework client, String path) {
//        this.client = CuratorFrameworkFactory.newClient(
//                "", new ExponentialBackoffRetry(1000, 10)
//        );

        leaderSelector = new LeaderSelector(client, path, this);
        leaderSelector.autoRequeue();
//        leaderSelector.start();
    }

    public void start() {
        LOG.debug("NvaLeaderSelectorListenerAdapter::start()");
        leaderSelector.start();
        LOG.debug("NvaLeaderSelectorListenerAdapter::start() complete");
    }

    public void close() {
        LOG.debug("NvaLeaderSelectorListenerAdapter::close()");
        synchronized (this) {
            notify();
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
        synchronized (this) {
            try {
                //while (true) {
//            synchronized (this) {
//                try {
//                    System.out.println("I'm the leader!");
//                    // Maybe do this?
//                    // Thread.currentThread().join();
//                    wait();
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }
//            }
                // This may be better so we don't need synch block
                //try {
                System.out.println("I'm the leader!");
                //Thread.currentThread().join();
                wait();
                System.out.println("Joined!");
//                } catch (InterruptedException e) {
//                    System.out.println("Oh no!");
//                    Thread.currentThread().interrupt();
//                }
                //}
            } catch (InterruptedException e) {
                //System.out.println("Oh no!");
                LOG.info("takeLeadership interrupted");
                Thread.currentThread().interrupt();
            } finally {
                LOG.info("Relinquishing leadership");
            }
        }
    }
}
